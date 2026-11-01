package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.request.DealInput;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.post.dto.request.PostWriteRequest;
import com.community.post.dto.response.PostDetailView;
import com.community.zconfiguration.exception.CommonException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 핫딜 글쓰기 — 계약 「필드·검증」(fields."deal.*" 를 다른 칸 오류와 함께 · 0원 딜 가격 기준 필수 · 할인율 경고 가격 기준 필수 · 상한 · 정상가 ≥ 딜가 · 마감 범위 · 배송비 3상태),
 * 쓰기 자격(새싹·미인증), 핫딜/일반 게시판 칸, 링크 판정(차단·체인 최종 호스트 차단·단축 주소), D5 쓰기 규칙(MEMBER 제휴 강제 · SITE 개인 제휴 칸 오류·false 저장),
 * 재고 SOLD_OUT = 작성자 종료(규칙 6), 같은 딜 키 저장.
 */
class HotdealWriteTest extends HotdealApiTestBase {

    @Test
    void 핫딜_게시판은_deal_필수_일반_게시판은_deal_거절() {
        long m = member();
        assertThat(fieldKeys(() -> write(m, board, null))).containsEntry("deal", "hotdeal.deal.required");
        String general = generalBoard();
        assertThat(fieldKeys(() -> write(member(), general, deal(url("shop.example.com"))))).containsEntry("deal", "hotdeal.deal.notAllowed");
    }

    @Test
    void 딜_칸_오류는_다른_글_칸_오류와_함께_나간다() {
        long m = member();
        DealInput bad = new DealInput(" ", "ftp://x", 1_000L, 5_000L, -1L, "가".repeat(101), "WEIRD", false, null, "2020-01-01T00:00:00Z", "MAYBE", false, null);
        Map<String, String> fields = fieldKeys(() -> posts.create(new PostWriteRequest(board, null, "", "TEXT", BODY, false, null, null, null, bad), m, "127.0.0.1"));
        assertThat(fields).containsKeys("title", "deal.storeName", "deal.productUrl", "deal.normalPrice", "deal.shippingFee", "deal.couponNote", "deal.priceBasisKind",
                "deal.endsAt", "deal.stockState");
        assertThat(fields.get("deal.normalPrice")).isEqualTo("hotdeal.price.normalBelowDeal");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_deals WHERE author_id = ?", Integer.class, m)).isZero();
    }

    @Test
    void 영원_딜은_가격_기준이_필수() {
        assertThat(fieldKeys(() -> write(member(), board, deal(url("shop.example.com"), null, 0L, 0L, null)))).containsEntry("deal.priceBasisKind",
                "hotdeal.priceBasis.requiredFree");
        long id = writeId(member(), deal(url("shop.example.com"), null, 0L, 0L, "MEMBER"));
        assertThat(dealRow(id)).containsEntry("deal_price", 0L).containsEntry("price_basis_kind", "MEMBER");
    }

    @Test
    void 할인율이_경고값_이상이면_가격_기준이_필수() {
        assertThat(fieldKeys(() -> write(member(), board, deal(url("shop.example.com"), 100_000L, 10_000L, 0L, null)))).containsEntry("deal.priceBasisKind",
                "hotdeal.priceBasis.requiredDiscount");
        writeId(member(), deal(url("shop.example.com"), 100_000L, 10_001L, 0L, null)); // 89% — 경고 아래
        writeId(member(), deal(url("shop.example.com"), 100_000L, 10_000L, 0L, "COUPON"));
    }

    @Test
    void 금액_상한과_정상가는_딜가_이상() {
        setting("price_max_won", 1_000_000L);
        Map<String, String> fields = fieldKeys(() -> write(member(), board, deal(url("shop.example.com"), 1_000_001L, 1_000_001L, 1_000_001L, "LISTED")));
        assertThat(fields).containsEntry("deal.dealPrice", "hotdeal.price.max").containsEntry("deal.normalPrice", "hotdeal.price.max")
                .containsEntry("deal.shippingFee", "hotdeal.price.max");
        assertThat(fieldKeys(() -> write(member(), board, deal(url("shop.example.com"), 9_000L, 10_000L, null, null)))).containsEntry("deal.normalPrice",
                "hotdeal.price.normalBelowDeal");
        assertThat(fieldKeys(() -> write(member(), board, deal(url("shop.example.com"), null, null, null, null)))).containsEntry("deal.dealPrice",
                "hotdeal.price.dealRequired");
    }

    @Test
    void 배송비는_무료_모름_금액_세_상태로_저장된다() {
        long free = writeId(member(), deal(url("shop.example.com"), null, 10_000L, 0L, null));
        long unknown = writeId(member(), deal(url("shop.example.com"), null, 10_000L, null, null));
        long paid = writeId(member(), deal(url("shop.example.com"), null, 10_000L, 2_500L, null));
        assertThat(dealRow(free).get("shipping_fee")).isEqualTo(0L);
        assertThat(dealRow(unknown).get("shipping_fee")).isNull();
        assertThat(dealRow(paid).get("shipping_fee")).isEqualTo(2_500L);
    }

    @Test
    void 마감은_지금_이후부터_365일_안() {
        Instant now = Instant.now();
        assertThat(fieldKeys(() -> write(member(), board, withEnds(deal(url("shop.example.com")), now.minusSeconds(60).toString())))).containsKey("deal.endsAt");
        assertThat(fieldKeys(() -> write(member(), board, withEnds(deal(url("shop.example.com")), now.plus(Duration.ofDays(366)).toString())))).containsKey("deal.endsAt");
        assertThat(fieldKeys(() -> write(member(), board, withEnds(deal(url("shop.example.com")), "내일")))).containsKey("deal.endsAt");
        long id = writeId(member(), withEnds(deal(url("shop.example.com")), now.plus(Duration.ofDays(3)).toString()));
        assertThat(dealRow(id).get("ends_at")).isNotNull();
    }

    @Test
    void 새싹_등급과_이메일_미인증은_핫딜을_쓸_수_없다() {
        assertThat(error(() -> write(member("SEEDLING", true), board, deal(url("shop.example.com")))).getErrorCode()).isIn("GRADE_TOO_LOW");
        assertThat(error(() -> write(member("REGULAR", false), board, deal(url("shop.example.com")))).getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
        jdbc.update("UPDATE boards SET write_min = NULL WHERE slug = ?", board); // 기본값이 첫 등급이어도 핫딜은 새싹을 막는다
        catalog.invalidate();
        assertThat(error(() -> write(member("SEEDLING", true), board, deal(url("shop.example.com")))).getErrorCode()).isEqualTo("GRADE_TOO_LOW");
    }

    @Test
    void 차단_도메인_링크는_등록할_수_없다_단축_주소는_따로_알린다() {
        String blocked = "blk" + uniq() + ".example";
        domain(blocked, "BLOCK", false);
        assertThat(fieldKeys(() -> write(member(), board, deal(url("www." + blocked))))).containsEntry("deal.productUrl", "hotdeal.productUrl.blocked");
        assertThat(fieldKeys(() -> write(member(), board, deal("https://bit.ly/" + uniq())))).containsEntry("deal.productUrl", "hotdeal.productUrl.shortener");
    }

    @Test
    void 리디렉션_체인의_최종_호스트가_차단이면_등록할_수_없다() {
        String blocked = "fin" + uniq() + ".example";
        domain(blocked, "BLOCK", false);
        String link = chain(url("go" + uniq() + ".example"), "go.example", "mid.example", blocked);
        assertThat(fieldKeys(() -> write(member(), board, deal(link)))).containsEntry("deal.productUrl", "hotdeal.productUrl.blocked");
    }

    @Test
    void MEMBER_모드에서_제휴_판정이면_affiliate_를_강제로_켠다() {
        String link = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        long id = writeId(member(), withAffiliate(deal(link), false));
        Map<String, Object> row = dealRow(id);
        assertThat(row).containsEntry("affiliate", true).containsEntry("product_host", "coupa.ng").containsEntry("final_host", "coupang.com");
        PostDetailView view = postQueries.detail(Long.toString(id), null, "127.0.0.1");
        assertThat(view.deal().affiliateKind()).isEqualTo("MEMBER");
        assertThat(view.deal().storeName()).isEqualTo("시험몰");

        long plain = writeId(member(), withAffiliate(deal(url("shop.example.com")), true));
        assertThat(dealRow(plain)).as("제휴 판정이 아니어도 글쓴이가 켜면 켬").containsEntry("affiliate", true);
    }

    @Test
    void SITE_모드는_개인_제휴_링크를_칸_오류로_막고_affiliate_는_false_로_저장() {
        setting("affiliate_mode", "SITE");
        String link = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        assertThat(fieldKeys(() -> write(member(), board, deal(link)))).containsEntry("deal.productUrl", "error.DEAL_MEMBER_AFFILIATE_OFF");
        long id = writeId(member(), withAffiliate(deal(url("shop.example.com")), true));
        assertThat(dealRow(id)).containsEntry("affiliate", false);
    }

    @Test
    void 재고_SOLD_OUT_으로_올리면_작성자_종료다() {
        long author = member();
        long id = writeId(author, withStock(deal(url("shop.example.com")), "SOLD_OUT"));
        assertThat(dealRow(id)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR").containsEntry("ended_by_id", author);
        PostDealView view = postQueries.detail(Long.toString(id), null, "127.0.0.1").deal();
        assertThat(view.view()).isEqualTo("ENDED");
        assertThat(view.reopenUntil()).as("작성자 종료 — 24시간 안 되돌리기 자리").isNotNull();
    }

    @Test
    void 같은_딜_키를_저장한다_추적_파라미터는_빠진다() {
        String base = url("shop.example.com");
        long a = writeId(member(), deal(base + "?utm_source=x"));
        long b = writeId(member(), deal(base.replace("https://", "https://www.") + "/#top"));
        assertThat(dealRow(a).get("product_url_key")).isEqualTo(dealRow(b).get("product_url_key"));
        assertThat(dealRow(a).get("product_url")).as("저장한 링크는 그대로").isEqualTo(base + "?utm_source=x");
    }

    @Test
    void 이미지는_우리_OG_사본_주소만_받는다() {
        DealInput outside = withImage(deal(url("shop.example.com")), new com.community.hotdeal.dto.request.DealImageInput("https://evil.example/a.png", "OG", null, null));
        assertThat(fieldKeys(() -> write(member(), board, outside))).containsEntry("deal.image", "hotdeal.image.invalid");
        long og = ids.nextId();
        jdbc.update("INSERT INTO deal_og_images (id, origin_url, origin_host, storage_key, public_url, content_type, size_bytes, width, height, created_at) "
                + "VALUES (?, 'https://cdn.example/a.png', 'cdn.example', ?, ?, 'image/jpeg', 10, 600, 300, now())", og, "deal-og/t/" + og + ".jpg", "/media/deal-og/t/" + og + ".jpg");
        long id = writeId(member(), withImage(deal(url("shop.example.com")),
                new com.community.hotdeal.dto.request.DealImageInput("/media/deal-og/t/" + og + ".jpg", "OG", null, "https://ignored.example")));
        assertThat(dealRow(id)).containsEntry("image_source", "OG").containsEntry("og_image_id", og);
        assertThat(jdbc.queryForObject("SELECT post_id FROM deal_og_images WHERE id = ?", Long.class, og)).isEqualTo(id);
        PostDealView view = postQueries.detail(Long.toString(id), null, "127.0.0.1").deal();
        assertThat(view.image().url()).isEqualTo("/media/deal-og/t/" + og + ".jpg");
        assertThat(view.image().originUrl()).as("공개 응답에 원본 주소를 싣지 않는다").isNull();
    }

    @Test
    void 쓰기_응답은_빈_경고_목록() {
        assertThat(write(member(), board, deal(url("shop.example.com"))).warnings()).isEmpty();
    }

    @Test
    void 핫딜_게시판은_19금을_켤_수_없다() {
        jdbc.update("UPDATE boards SET allow_adult = true WHERE slug = ?", board);
        catalog.invalidate();
        CommonException e = error(() -> posts.create(new PostWriteRequest(board, null, "제목", "TEXT", BODY, true, null, null, null, deal(url("shop.example.com"))),
                member(), "127.0.0.1"));
        assertThat(e.getFields()).containsKey("adult");
    }
}
