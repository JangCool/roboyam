package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.request.DealInput;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.PostDetailView;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 핫딜 고치기 — 서버 규칙 7·확정 가정 「고치기에서 링크를 그대로 두면」(지금 규칙이 차단이어도 저장 + warnings · SITE 여도 개인 제휴 칸 오류 없음 · 차단 도메인으로 바꾸면 칸 오류),
 * ENDED 는 딜 칸 거절(409 DEAL_ENDED)·EXPIRED 는 허용, D3(이력 한 줄 · 가격 수정 셈에서 배송비 제외 · 호스트가 바뀌면 「링크 바뀜」+ 운영자 알림), 칸을 빼면 그대로.
 */
class HotdealEditTest extends HotdealApiTestBase {

    @Test
    void 링크를_그대로_두면_지금_차단이어도_저장하고_경고한다() {
        String host = "late" + uniq() + ".example";
        long author = member();
        DealInput original = deal(url(host));
        long id = writeId(author, original);
        domain(host, "BLOCK", false); // 올린 뒤에 차단 규칙이 생겼다

        PostDetailView saved = update(author, id, withPrices(original, 30_000L, 18_900L, 0L));
        assertThat(saved.warnings()).extracting("code").containsExactly("DEAL_LINK_BLOCKED");
        assertThat(saved.warnings().get(0).message()).isNotBlank();
        assertThat(dealRow(id).get("deal_price")).isEqualTo(18_900L);

        PostDetailView kept = update(author, id, null); // 딜 칸을 빼도 지금 규칙으로 다시 본다
        assertThat(kept.warnings()).extracting("code").containsExactly("DEAL_LINK_BLOCKED");
    }

    @Test
    void 차단_도메인으로_바꾸면_칸_오류() {
        String blocked = "nb" + uniq() + ".example";
        domain(blocked, "BLOCK", false);
        long author = member();
        DealInput original = deal(url("shop.example.com"));
        long id = writeId(author, original);
        assertThat(fieldKeys(() -> update(author, id, withUrl(original, url(blocked))))).containsEntry("deal.productUrl", "hotdeal.productUrl.blocked");
    }

    @Test
    void SITE_모드여도_그대로_둔_개인_제휴_링크는_칸_오류가_없고_제휴_표시는_유지한다() {
        long author = member();
        String link = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        DealInput original = deal(link);
        long id = writeId(author, original);
        setting("affiliate_mode", "SITE");
        update(author, id, withPrices(original, 30_000L, 17_000L, 0L));
        assertThat(dealRow(id)).containsEntry("affiliate", true).containsEntry("deal_price", 17_000L);

        String newLink = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        assertThat(fieldKeys(() -> update(author, id, withUrl(original, newLink)))).containsEntry("deal.productUrl", "error.DEAL_MEMBER_AFFILIATE_OFF");
    }

    @Test
    void 종료된_딜은_딜_칸을_고칠_수_없고_기간_종료는_고칠_수_있다() {
        long author = member();
        DealInput original = withStock(deal(url("shop.example.com")), "SOLD_OUT");
        long ended = writeId(author, original);
        assertThat(error(() -> update(author, ended, withStock(original, "IN_STOCK"))).getErrorCode()).isEqualTo("DEAL_ENDED");
        update(author, ended, null); // 딜 칸을 빼고 글만 고치는 것은 된다

        long author2 = member();
        DealInput open = withEnds(deal(url("shop.example.com")), Instant.now().plus(Duration.ofDays(2)).toString());
        long expired = writeId(author2, open);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET ends_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minus(Duration.ofHours(1))), expired);
        assertThat(postQueries.detail(Long.toString(expired), null, "127.0.0.1").deal().view()).isEqualTo("EXPIRED");
        update(author2, expired, withEnds(open, Instant.now().plus(Duration.ofDays(5)).toString()));
        assertThat(postQueries.detail(Long.toString(expired), null, "127.0.0.1").deal().view()).isEqualTo("ACTIVE");
    }

    @Test
    void 처음과_같은_마감은_지났어도_다시_보지_않는다() {
        long author = member();
        DealInput open = withEnds(deal(url("shop.example.com")), Instant.now().plus(Duration.ofDays(2)).toString());
        long id = writeId(author, open);
        Instant past = Instant.now().minus(Duration.ofHours(3)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET ends_at = ? WHERE post_id = ?", Timestamp.from(past), id);
        update(author, id, withPrices(withEnds(open, past.toString()), 30_000L, 15_000L, 0L));
        assertThat(dealRow(id).get("deal_price")).isEqualTo(15_000L);
    }

    @Test
    void 딜_칸이_바뀌면_이력_한_줄_가격_수정_셈은_딜가_정상가만() {
        long author = member();
        DealInput original = deal(url("shop.example.com"));
        long id = writeId(author, original);

        update(author, id, withPrices(original, 30_000L, 19_900L, 3_000L)); // 배송비만
        assertThat(dealRow(id)).containsEntry("price_edit_count", 0).containsEntry("link_changed", false);
        update(author, id, withPrices(original, 35_000L, 18_000L, 3_000L)); // 딜가·정상가
        assertThat(dealRow(id)).containsEntry("price_edit_count", 1);
        update(author, id, withPrices(original, 35_000L, 18_000L, 3_000L)); // 같은 값 — 이력 없음

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT actor_kind, actor_id, changes::text AS changes, price_edit, host_changed FROM post_deal_revisions "
                + "WHERE post_id = ? ORDER BY created_at, id", id);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("actor_kind", "AUTHOR").containsEntry("actor_id", author).containsEntry("price_edit", false);
        assertThat((String) rows.get(0).get("changes")).contains("\"field\": \"shippingFee\"").contains("\"before\": \"0\"").contains("\"after\": \"3000\"");
        assertThat(rows.get(1)).containsEntry("price_edit", true);
        assertThat((String) rows.get(1).get("changes")).contains("dealPrice").contains("normalPrice").doesNotContain("shippingFee");
        PostDetailView view = postQueries.detail(Long.toString(id), null, "127.0.0.1");
        assertThat(view.deal().revision().priceEdits()).isEqualTo(1);
        assertThat(view.deal().revision().linkChanged()).isFalse();
        assertThat(view.deal().revision().lastEditedAt()).isNotNull();
    }

    @Test
    void 판매처_호스트가_바뀌면_링크_바뀜과_운영자_알림() {
        long handler = operatorWith(Permission.HOTDEAL_READ, Permission.HOTDEAL_MANAGE);
        long author = member();
        DealInput original = deal(url("shop-a" + uniq() + ".example"));
        long id = writeId(author, original);
        update(author, id, withUrl(original, url("shop-b" + uniq() + ".example")));

        assertThat(dealRow(id)).containsEntry("link_changed", true);
        assertThat(jdbc.queryForObject("SELECT host_changed FROM post_deal_revisions WHERE post_id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForList("SELECT event_code || ' ' || coalesce(link_path, '') FROM admin_notifications WHERE operator_id = ? AND dedupe_key = ?", String.class,
                handler, Long.toString(id))).containsExactly("HOTDEAL_LINK_HOST_CHANGED /hotdeals?view=ALL&deal=" + id);
        assertThat(postQueries.detail(Long.toString(id), null, "127.0.0.1").deal().revision().linkChanged()).isTrue();
    }

    @Test
    void 단축_제휴에서_같은_몰_직접_링크로_바꾸면_판매처는_그대로라_링크_바뀜이_아니다() {
        long author = member();
        String shortLink = chain("https://coupa.ng/" + uniq(), "coupa.ng", "coupang.com");
        DealInput original = deal(shortLink);
        long id = writeId(author, original);
        update(author, id, withUrl(original, "https://www.coupang.com/vp/products/" + uniq()));
        assertThat(dealRow(id)).containsEntry("link_changed", false).containsEntry("product_host", "coupang.com");
    }

    @Test
    void 재고를_SOLD_OUT_으로_고치면_작성자_종료() {
        long author = member();
        DealInput original = deal(url("shop.example.com"));
        long id = writeId(author, original);
        update(author, id, withStock(original, "SOLD_OUT"));
        assertThat(dealRow(id)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
    }

    @Test
    void 딜_칸을_null_로_보내면_거절() {
        long author = member();
        long id = writeId(author, deal(url("shop.example.com")));
        assertThat(error(() -> posts.update(Long.toString(id), new com.community.post.dto.request.PostUpdateRequest(null, "제목 고침", "TEXT", BODY + " 다시", false, null, null,
                tools.jackson.databind.node.NullNode.getInstance()), author, "127.0.0.1")).getFields()).containsKey("deal");
    }
}
