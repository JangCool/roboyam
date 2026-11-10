package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.home.dto.response.gadget.HotDealsDataView;
import com.community.home.service.GadgetService;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.post.dto.response.PostFeedRowView;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

/**
 * 서버 단계 고정 규칙 8 — 가젯 {@code HOT_DEALS}(진행 중 ACTIVE 만 · 의심·종료·기간 종료·숨김 제외 · 정렬 셋 · 개수 · 핫딜 게시판 고르기 · 줄의 {@code deal} · 「더 보기」)와
 * 인기글 집계 대상(종료·기간 종료·사업자 글 제외, 의심은 포함 — 배치 후보 함수와 읽을 때 거르기).
 */
class HotdealGadgetPopularTest extends HotdealStage2TestBase {

    @Autowired
    private GadgetService gadgets;
    @Autowired
    private DealQueries dealQueries;

    private ObjectNode config(String sort, int count, String... boards) {
        ObjectNode c = JSON.createObjectNode().put("sort", sort).put("count", count);
        var arr = c.putArray("boards");
        for (String b : boards) {
            arr.add(b);
        }
        return c;
    }

    private List<String> ids(HotDealsDataView d) {
        return d.items().stream().map(PostFeedRowView::id).toList();
    }

    @Test
    void 가젯은_진행_중만_싣고_의심_종료_기간_종료_숨김은_뺀다() {
        long author = member();
        long active = newDeal(author);
        long flagged = newDeal(member());
        requestBy(flagged, 3);
        long ended = newDeal(member());
        states.end(Long.toString(ended), jdbc.queryForObject("SELECT author_id FROM post_deals WHERE post_id = ?", Long.class, ended));
        long expired = newDeal(member());
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minus(Duration.ofDays(31))), expired);
        long hidden = newDeal(member());
        boardFixture.staffHide(hidden);

        HotDealsDataView d = gadgets.hotDeals(config("LATEST", 10, board), null);
        assertThat(ids(d)).containsExactly(Long.toString(active));
        PostFeedRowView row = d.items().get(0);
        assertThat(row.deal()).isNotNull();
        assertThat(row.deal().view()).isEqualTo("ACTIVE");
        assertThat(row.deal().buyUrl()).isEqualTo("/api/deals/" + active + "/go");
        assertThat(row.board().slug()).isEqualTo(board);
        assertThat(d.more().to()).isEqualTo("/boards/" + board);
    }

    @Test
    void 정렬과_개수() {
        setting("same_store_interval_minutes", 0);
        long a = newDeal(member());
        long b = newDeal(member());
        long c = newDeal(member());
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE posts SET like_count = 5 WHERE id = ?", a);
        jdbc.update("UPDATE posts SET like_count = 9 WHERE id = ?", b);
        jdbc.update("UPDATE post_deals SET ends_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().plus(Duration.ofDays(2))), c);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET ends_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().plus(Duration.ofDays(1))), a);

        assertThat(ids(gadgets.hotDeals(config("LATEST", 10, board), null))).containsExactly(Long.toString(c), Long.toString(b), Long.toString(a));
        assertThat(ids(gadgets.hotDeals(config("POPULAR", 10, board), null))).containsExactly(Long.toString(b), Long.toString(a), Long.toString(c));
        // 마감 임박 — 마감 있는 글 먼저(a 내일 · c 모레), 마감 없는 b 는 등록 + 30일
        assertThat(ids(gadgets.hotDeals(config("ENDING_SOON", 10, board), null))).containsExactly(Long.toString(a), Long.toString(c), Long.toString(b));
        assertThat(ids(gadgets.hotDeals(config("LATEST", 2, board), null))).hasSize(2);
        assertThat(ids(gadgets.hotDeals(config("WHATEVER", 99, board), null))).hasSize(3); // 모르는 정렬은 최신 · 개수는 10 까지
    }

    @Test
    void 핫딜_게시판만_고르고_고른_것이_없으면_모든_핫딜_게시판() {
        long here = newDeal(member());
        String other = dealBoard();
        String general = generalBoard();
        HotDealsDataView picked = gadgets.hotDeals(config("LATEST", 10, general, board), null);
        assertThat(ids(picked)).containsExactly(Long.toString(here));
        assertThat(picked.more().to()).isEqualTo("/boards/" + board);
        HotDealsDataView empty = gadgets.hotDeals(config("LATEST", 10, other), null);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.more().to()).isEqualTo("/boards/" + other);
        assertThat(ids(gadgets.hotDeals(config("LATEST", 10, general), null))).isNotEmpty(); // 남는 핫딜 게시판이 없으면 거르지 않는다
    }

    // ── 인기글 ───────────────────────────────────────────────────────────────

    private Set<Long> candidates() {
        Instant now = Instant.now();
        return Set.copyOf(jdbc.queryForList("SELECT post_id FROM popular_rank_candidates(?, ?, ?, false)", Long.class, Timestamp.from(now.minus(Duration.ofDays(1))),
                Timestamp.from(now.plus(Duration.ofDays(1))), Timestamp.from(now)));
    }

    private void popularize(long postId) {
        // SQL 로 남김: 글 카운터 칸 — 엔티티 메서드가 없다(운영은 PostCounterQueries)
        jdbc.update("UPDATE posts SET like_count = 1000, comment_count = 1000, view_count = 100000 WHERE id = ?", postId);
    }

    @Test
    void 인기글은_종료_기간_종료_사업자_글을_빼고_의심은_넣는다() {
        boardFixture.clearGadgetExclusions(board);
        long active = newDeal(member());
        long flagged = newDeal(member());
        requestBy(flagged, 3);
        long ended = newDeal(member());
        ends.endByManager(ended, hotdealOperator(), null);
        long expired = newDeal(member());
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET ends_at = ? WHERE post_id = ?", Timestamp.from(Instant.now().minusSeconds(60)), expired);
        long business = newDeal(member());
        jdbc.update("UPDATE post_deals SET source = 'BUSINESS', biz_application_id = ?, business_name = '시험상사' WHERE post_id = ?", ids.nextId(), business);
        for (long id : List.of(active, flagged, ended, expired, business)) {
            popularize(id);
        }
        Set<Long> c = candidates();
        assertThat(c).contains(active, flagged).doesNotContain(ended, expired, business);
        // 공개 API 의 읽을 때 거르기(스냅샷 뒤에 끝난 딜) — 같은 판정
        assertThat(dealQueries.unrankable(List.of(active, flagged, ended, expired, business), 30, Instant.now())).containsExactlyInAnyOrder(ended, expired, business);
    }
}
