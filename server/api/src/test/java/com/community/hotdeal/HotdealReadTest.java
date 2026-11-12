package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.board.dto.response.BoardDetailView;
import com.community.board.service.PublicBoardService;
import com.community.hotdeal.dto.response.DealSameRefView;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealLinkService;
import com.community.post.dto.response.PostListResponse;
import com.community.post.dto.response.PostRowView;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 핫딜 읽기 — 글 상세·목록 줄의 {@code deal}(상태 계산 ENDED &gt; EXPIRED &gt; FLAGGED &gt; ACTIVE · 할인율 · 기간 종료 · HOT · buyUrl), 같은 딜은 공개·진행 중만(규칙 3),
 * 목록 차례(진행 중 → 의심 → 끝남)·종료 숨기기·쇼핑몰 칩·정렬, 게시판 응답 {@code kind}·{@code dealAffiliateMode}·{@code dealRules}·19금 끔(규칙 8), D5 {@code affiliateKind}.
 */
class HotdealReadTest extends HotdealApiTestBase {

    @Autowired
    private PublicBoardService boards;
    @Autowired
    private DealLinkService links;

    @Test
    void 글_상세의_딜은_서버가_계산한다() {
        long id = writeId(member(), deal(url("shop.example.com"), 30_000L, 19_900L, 0L, null));
        PostDealView d = postQueries.detail(Long.toString(id), null, "127.0.0.1").deal();
        assertThat(d.view()).isEqualTo("ACTIVE");
        assertThat(d.status()).isEqualTo("ACTIVE");
        assertThat(d.discountRate()).isEqualTo(33); // (30000-19900)/30000 = 33.6% 내림
        assertThat(d.autoExpireAt()).isNotNull();
        assertThat(d.buyUrl()).isEqualTo("/api/deals/" + id + "/go");
        assertThat(d.source()).isEqualTo("MEMBER");
        assertThat(d.keepMax()).isEqualTo(2);
        assertThat(d.endRequests().total()).isZero();
        assertThat(d.hot()).isFalse();
        assertThat(d.affiliateKind()).isNull();
        assertThat(d.reopenUntil()).isNull();
        assertThat(postQueries.detail(Long.toString(id), null, "127.0.0.1").viewCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 상태_우선순위_ENDED_EXPIRED_FLAGGED_ACTIVE() {
        long flagged = writeId(member(), deal(url("shop.example.com")));
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET flagged_at = now() WHERE post_id = ?", flagged);
        assertThat(view(flagged).view()).isEqualTo("FLAGGED");
        assertThat(view(flagged).autoEndAt()).as("의심 + 유예 12시간").isNotNull();

        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(31))), flagged);
        assertThat(view(flagged).view()).as("마감 없는 딜 30일 — 기간 종료가 의심보다 먼저").isEqualTo("EXPIRED");

        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET status = 'ENDED', ended_by = 'MANAGER', ended_by_role = 'OPERATOR', ended_at = now() WHERE post_id = ?", flagged);
        assertThat(view(flagged).view()).isEqualTo("ENDED");
    }

    @Test
    void 추천이_문턱_이상이면_HOT() {
        long id = writeId(member(), deal(url("shop.example.com")));
        // SQL 로 남김: 글 카운터 칸 — 엔티티 메서드가 없다(운영은 PostCounterQueries)
        jdbc.update("UPDATE posts SET like_count = 10 WHERE id = ?", id);
        assertThat(view(id).hot()).isTrue();
    }

    @Test
    void 같은_딜은_공개_진행_중만_센다() {
        String link = url("same.example.com");
        long a = writeId(member(), deal(link));
        long b = writeId(member(), deal(link + "?utm_source=x"));
        long ended = writeId(member(), withStock(deal(link), "SOLD_OUT"));
        long hidden = writeId(member(), deal(link));
        boardFixture.staffHide(hidden);
        long expired = writeId(member(), deal(link));
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(40))), expired);

        assertThat(view(a).sameDealCount()).isEqualTo(1);
        List<DealSameRefView> same = links.same(Long.toString(a));
        assertThat(same).extracting(DealSameRefView::postId).containsExactly(Long.toString(b));
        assertThat(view(ended).sameDealCount()).as("끝난 글에서 보면 진행 중 둘").isEqualTo(2);
    }

    @Test
    void 목록은_진행_중_의심_끝남_차례이고_종료_숨기기가_기본() {
        String slug = board;
        long active = writeId(member(), deal(url("shop.example.com")));
        long flagged = writeId(member(), deal(url("shop.example.com")));
        long ended = writeId(member(), withStock(deal(url("shop.example.com")), "SOLD_OUT"));
        // SQL 로 남김: 핫딜 칸을 바로 세운다 — 엔티티는 딜 칸 전체(DealValues)로만 만들고 바꾼다(다음 단계에서 픽스처로)
        jdbc.update("UPDATE post_deals SET flagged_at = now() WHERE post_id = ?", flagged);

        PostListResponse shown = postQueries.list(slug, null, "latest", null, 1, null, null, null);
        assertThat(ids(shown)).containsExactly(Long.toString(active), Long.toString(flagged));
        PostListResponse all = postQueries.list(slug, null, "latest", null, 1, null, false, null);
        assertThat(ids(all)).containsExactly(Long.toString(active), Long.toString(flagged), Long.toString(ended));
        assertThat(all.items().get(0).deal()).isNotNull();
        assertThat(all.items().get(2).deal().view()).isEqualTo("ENDED");
    }

    @Test
    void 인기순과_마감_임박순() {
        long older = writeId(member(), withEnds(deal(url("shop.example.com")), Instant.now().plus(Duration.ofDays(5)).toString()));
        long soon = writeId(member(), withEnds(deal(url("shop.example.com")), Instant.now().plus(Duration.ofHours(5)).toString()));
        long open = writeId(member(), deal(url("shop.example.com")));
        // SQL 로 남김: 글 카운터 칸 — 엔티티 메서드가 없다(운영은 PostCounterQueries)
        jdbc.update("UPDATE posts SET like_count = 7 WHERE id = ?", older);
        assertThat(ids(postQueries.list(board, null, "popular", null, 1, null, null, null)).get(0)).isEqualTo(Long.toString(older));
        assertThat(ids(postQueries.list(board, null, "ENDING_SOON", null, 1, null, null, null))).containsExactly(Long.toString(soon), Long.toString(older),
                Long.toString(open));
    }

    @Test
    void 쇼핑몰_칩과_쇼핑몰_거르기() {
        writeId(member(), withStore(deal(url("shop.example.com")), "가나몰"));
        writeId(member(), withStore(deal(url("shop.example.com")), "가나몰"));
        writeId(member(), withStore(deal(url("shop.example.com")), "다라몰"));
        writeId(member(), withStore(withStock(deal(url("shop.example.com")), "SOLD_OUT"), "마바몰"));

        PostListResponse list = postQueries.list(board, null, "latest", null, 1, null, null, null);
        assertThat(list.stores()).extracting("name").containsExactly("가나몰", "다라몰");
        assertThat(list.stores().get(0).count()).isEqualTo(2);
        PostListResponse filtered = postQueries.list(board, null, "latest", null, 1, null, null, "다라몰");
        assertThat(filtered.items()).hasSize(1);
        assertThat(filtered.stores()).as("칩은 쇼핑몰 거르기 전").hasSize(2);
        assertThat(postQueries.list(board, null, "latest", null, 1, null, false, null).stores()).extracting("name").contains("마바몰");
        assertThat(postQueries.list(generalBoard(), null, "LATEST", null, 1, null, null, null).stores()).as("일반 게시판은 칩 없음").isNull();
    }

    @Test
    void 게시판_응답은_kind_와_핫딜_공개_설정을_싣고_19금은_꺼져_있다() {
        // SQL 로 남김: 정책·기본 칸 하나만 바꾸는 도메인 메서드가 없다 — changePolicy·changeBasic 은 묶음 전체를 바꾼다
        jdbc.update("UPDATE boards SET allow_adult = true WHERE slug = ?", board);
        catalog.invalidate();
        BoardDetailView detail = boards.board(board, null);
        assertThat(detail.kind()).isEqualTo("DEAL");
        assertThat(detail.dealEnabled()).isTrue();
        assertThat(detail.allowAdult()).isFalse();
        assertThat(detail.dealAffiliateMode()).isEqualTo("MEMBER");
        assertThat(detail.dealRules().priceMaxWon()).isEqualTo(100_000_000L);
        assertThat(detail.dealRules().discountWarnPercent()).isEqualTo(90);
        assertThat(detail.dealRules().autoExpireDays()).isEqualTo(30);
        assertThat(detail.dealRules().hotLikeThreshold()).isEqualTo(10);

        BoardDetailView general = boards.board(generalBoard(), null);
        assertThat(general.kind()).isEqualTo("GENERAL");
        assertThat(general.dealAffiliateMode()).isNull();
        assertThat(general.dealRules()).isNull();
    }

    @Test
    void SITE_모드의_제휴_표시는_사이트_제휴_몰_글만_SITE_나머지는_없음() {
        String mall = "mall" + uniq() + ".example";
        long site = writeId(member(), deal(url(mall)));
        long personal = writeId(member(), withAffiliate(deal(url("shop.example.com")), true));
        siteStore(mall, "QUERY", null, "aff=irobit");
        setting("affiliate_mode", "SITE");
        assertThat(view(site).affiliateKind()).isEqualTo("SITE");
        assertThat(view(site).siteProgramName()).isEqualTo("시험 제휴");
        assertThat(view(personal).affiliateKind()).as("SITE 에서 기존 개인 제휴 글은 표시 없음(클릭 때 뗀다)").isNull();
        assertThat(view(personal).affiliate()).isTrue();
        setting("affiliate_mode", "MEMBER");
        assertThat(view(personal).affiliateKind()).isEqualTo("MEMBER");
        assertThat(view(site).affiliateKind()).isNull();
    }

    @Test
    void 끝난_딜에서만_미리_채움_진행_중이면_409() {
        long author = member();
        long live = writeId(author, deal(url("shop.example.com")));
        assertThat(error(() -> links.prefill(Long.toString(live), author)).getErrorCode()).isEqualTo("DEAL_NOT_ENDED");
        long ended = writeId(member(), withStock(deal(url("shop.example.com")), "SOLD_OUT"));
        var prefill = links.prefill(Long.toString(ended), author);
        assertThat(prefill.fromPostId()).isEqualTo(Long.toString(ended));
        assertThat(prefill.storeName()).isEqualTo("시험몰");
        assertThat(prefill.productUrl()).startsWith("https://shop.example.com/");
    }

    private PostDealView view(long id) {
        return postQueries.detail(Long.toString(id), null, "127.0.0.1").deal();
    }

    private static List<String> ids(PostListResponse list) {
        return list.items().stream().map(PostRowView::id).toList();
    }

    private static com.community.hotdeal.dto.request.DealInput withStore(com.community.hotdeal.dto.request.DealInput d, String store) {
        return new com.community.hotdeal.dto.request.DealInput(store, d.productUrl(), d.normalPrice(), d.dealPrice(), d.shippingFee(), d.couponNote(), d.priceBasisKind(),
                d.dutyIncluded(), d.priceBasis(), d.endsAt(), d.stockState(), d.affiliate(), d.image());
    }
}
