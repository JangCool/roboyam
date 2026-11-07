package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealEndService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 작성자 상태 API — 작성자 종료 · 「아직 진행 중」(keep 한도·관리자 알림) · 작성자 되돌리기(D2 — 서버 단계 고정 규칙 11: AUTHOR 종료만 · 24시간 경계 · 1번 · 운영진/자동 종료는 되돌릴 수 없음 ·
 * 품절 → 모름 · 기간이 지났으면 「마감을 고쳐 주세요」) · 운영진 재개 공용 길(의심·keep·요청 리셋).
 */
class HotdealStateTest extends HotdealStage2TestBase {

    @Test
    void 작성자만_종료하고_끝난_딜은_다시_종료할_수_없다() {
        long author = member();
        long post = newDeal(author);
        assertThat(error(() -> states.end(Long.toString(post), member())).getErrorCode()).isEqualTo("FORBIDDEN");
        PostDealView ended = states.end(Long.toString(post), author);
        assertThat(ended.status()).isEqualTo("ENDED");
        assertThat(ended.endedBy()).isEqualTo("AUTHOR");
        assertThat(ended.reopenUntil()).isEqualTo(ended.endedAt().plus(Duration.ofHours(24)));
        assertThat(error(() -> states.end(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_ENDED");
    }

    @Test
    void 기간_종료된_딜은_작성자가_종료하지_않는다() {
        long author = member();
        long post = newDeal(author);
        clock.set(clock.instant().plus(Duration.ofDays(30)).plusSeconds(1));
        assertThat(error(() -> states.end(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_ENDED");
    }

    @Test
    void keep_은_의심일_때만_한도까지_하고_회차를_새로_센다() {
        long author = member();
        long post = newDeal(author);
        assertThat(error(() -> states.keep(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_NOT_FLAGGED");
        requestBy(post, 3);
        assertThat(error(() -> states.keep(Long.toString(post), member())).getErrorCode()).isEqualTo("FORBIDDEN");

        PostDealView kept = states.keep(Long.toString(post), author);
        assertThat(kept.view()).isEqualTo("ACTIVE");
        assertThat(kept.keepCount()).isEqualTo(1);
        assertThat(kept.keepMax()).isEqualTo(2);
        assertThat(kept.flaggedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, post)).isZero();

        // keep 뒤의 새 회차 — 새 요청이 문턱만큼 모여야 다시 의심
        clock.set(clock.instant().plusSeconds(1));
        requestBy(post, 2);
        assertThat(dealRow(post).get("flagged_at")).isNull();
        requestBy(post, 1);
        assertThat(dealRow(post).get("flagged_at")).isNotNull();
        assertThat(notices(author, "DEAL_FLAGGED", post)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, post)).isEqualTo(3);
    }

    @Test
    void keep_한도를_다_쓰면_409_이고_다시_의심이_되면_운영자에게_알린다() {
        setting("keep_max_count", 1);
        long operator = hotdealOperator();
        long author = member();
        long post = newDeal(author);
        requestBy(post, 3);
        assertThat(adminNotices(operator, "HOTDEAL_KEEP_LIMIT")).isZero(); // 아직 keep 이 남았다
        states.keep(Long.toString(post), author);
        clock.set(clock.instant().plusSeconds(1));
        requestBy(post, 3);
        assertThat(adminNotices(operator, "HOTDEAL_KEEP_LIMIT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT link_path FROM admin_notifications WHERE operator_id = ? AND event_code = 'HOTDEAL_KEEP_LIMIT'", String.class, operator))
                .isEqualTo("/hotdeals?view=ALL&deal=" + post);
        assertThat(error(() -> states.keep(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_KEEP_LIMIT");
    }

    @Test
    void keep_한도_0_은_없음() {
        setting("keep_max_count", 0);
        long author = member();
        long post = newDeal(author);
        for (int round = 0; round < 3; round++) {
            clock.set(clock.instant().plusSeconds(1));
            requestBy(post, 3);
            states.keep(Long.toString(post), author);
        }
        assertThat(dealRow(post)).containsEntry("keep_count", 3);
    }

    @Test
    void 작성자_되돌리기는_24시간_안_한_번() {
        long author = member();
        long post = newDeal(author);
        assertThat(error(() -> states.reopen(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_NOT_ENDED");
        PostDealView ended = states.end(Long.toString(post), author);
        clock.set(ended.endedAt().plus(Duration.ofHours(24)).minusSeconds(1));
        PostDealView reopened = states.reopen(Long.toString(post), author);
        assertThat(reopened.status()).isEqualTo("ACTIVE");
        assertThat(reopened.endedBy()).isNull();
        assertThat(dealRow(post)).containsEntry("reopen_used", true);

        PostDealView again = states.end(Long.toString(post), author);
        assertThat(again.reopenUntil()).isNull(); // 이미 썼다
        assertThat(error(() -> states.reopen(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_REOPEN_USED");
    }

    @Test
    void 되돌리기_기한은_종료_뒤_정확히_24시간에_닫힌다() {
        long author = member();
        long post = newDeal(author);
        PostDealView ended = states.end(Long.toString(post), author);
        clock.set(ended.endedAt().plus(Duration.ofHours(24)));
        assertThat(error(() -> states.reopen(Long.toString(post), author)).getErrorCode()).isEqualTo("DEAL_REOPEN_EXPIRED");
        assertThat(error(() -> states.reopen(Long.toString(post), member())).getErrorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void 운영진_종료와_자동_종료는_작성자가_되돌릴_수_없다() {
        long author = member();
        long byManager = newDeal(author);
        assertThat(ends.endByManager(byManager, hotdealOperator(), "가격이 바뀌었어요")).isEqualTo(DealEndService.Outcome.CHANGED);
        assertThat(error(() -> states.reopen(Long.toString(byManager), author)).getErrorCode()).isEqualTo("DEAL_REOPEN_EXPIRED");
        assertThat(notices(author, "DEAL_MANAGER_ENDED", byManager)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->>'note' FROM member_notifications WHERE user_id = ? AND type = 'DEAL_MANAGER_ENDED' AND post_id = ?",
                String.class, author, byManager)).isEqualTo("가격이 바뀌었어요");
        assertThat(ends.endByManager(byManager, hotdealOperator(), null)).isEqualTo(DealEndService.Outcome.STATE_CHANGED);

        long auto = newDeal(member());
        requestBy(auto, 3);
        afterFlag(auto, Duration.ofHours(12));
        assertThat(ends.autoEnd(auto, clock.instant())).isTrue();
        long autoAuthor = jdbc.queryForObject("SELECT author_id FROM post_deals WHERE post_id = ?", Long.class, auto);
        assertThat(error(() -> states.reopen(Long.toString(auto), autoAuthor)).getErrorCode()).isEqualTo("DEAL_REOPEN_EXPIRED");
    }

    @Test
    void 품절로_끝낸_딜을_되돌리면_재고는_모름() {
        long author = member();
        long post = writeId(author, withStock(deal(url("s" + uniq() + ".shop.example.com")), "SOLD_OUT"));
        assertThat(dealRow(post)).containsEntry("status", "ENDED").containsEntry("ended_by", "AUTHOR");
        PostDealView reopened = states.reopen(Long.toString(post), author);
        assertThat(reopened.stockState()).isEqualTo("UNKNOWN");
    }

    @Test
    void 되돌렸는데_기간이_지났으면_마감을_고쳐_달라고_알린다() {
        long author = member();
        long post = newDeal(author);
        clock.set(clock.instant().plus(Duration.ofDays(29)).plus(Duration.ofHours(12)));
        states.end(Long.toString(post), author);
        clock.set(clock.instant().plus(Duration.ofHours(12)).plusSeconds(1)); // 등록 + 30일 지남, 종료 뒤 24시간 안
        PostDealView reopened = states.reopen(Long.toString(post), author);
        assertThat(reopened.view()).isEqualTo("EXPIRED");
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", post)).isEqualTo(1);

        long fresh = newDeal(author);
        states.end(Long.toString(fresh), author);
        states.reopen(Long.toString(fresh), author);
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", fresh)).isZero();
    }

    @Test
    void 운영진_재개는_의심_keep_요청을_리셋하고_기간이_지났으면_알린다() {
        long author = member();
        long post = newDeal(author);
        requestBy(post, 3);
        states.keep(Long.toString(post), author);
        clock.set(clock.instant().plusSeconds(1));
        requestBy(post, 3);
        ends.endByManager(post, hotdealOperator(), null);
        assertThat(ends.reopenByManager(post, 0L, "OPERATOR", null)).isEqualTo(DealEndService.Outcome.CHANGED);
        assertThat(dealRow(post)).containsEntry("status", "ACTIVE").containsEntry("keep_count", 0).containsEntry("flagged_at", null).containsEntry("kept_at", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ?", Integer.class, post)).isZero();
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", post)).isZero();
        assertThat(ends.reopenByManager(post, 0L, "OPERATOR", null)).isEqualTo(DealEndService.Outcome.STATE_CHANGED);
        assertThat(ends.reopenByManager(ids.nextId(), 0L, "OPERATOR", null)).isEqualTo(DealEndService.Outcome.NOT_FOUND);

        long expired = newDeal(author);
        ends.endByManager(expired, hotdealOperator(), null);
        // SQL 로 남김: 과거·미래 시각을 바로 세운다 — 엔티티 팩터리·메서드는 지금 시각으로만 쓴다
        jdbc.update("UPDATE post_deals SET created_at = ? WHERE post_id = ?", Timestamp.from(clock.instant().minus(Duration.ofDays(31))), expired);
        ends.reopenByManager(expired, 0L, "OPERATOR", null);
        assertThat(notices(author, "DEAL_REOPENED_FIX_ENDS", expired)).isEqualTo(1);
    }

    @Test
    void 자동_종료는_유예가_지나야_하고_keep_한_딜은_건너뛴다() {
        long post = newDeal(member());
        requestBy(post, 3);
        afterFlag(post, Duration.ofHours(12).minusSeconds(1));
        assertThat(ends.autoEnd(post, clock.instant())).isFalse();
        afterFlag(post, Duration.ofHours(12));
        assertThat(ends.autoEnd(post, clock.instant())).isTrue();
        assertThat(dealRow(post)).containsEntry("ended_by", "AUTO");
        Instant now = clock.instant();
        assertThat(ends.autoEnd(post, now)).isFalse();
    }

    @Test
    void 회원_알림함에_딜_알림이_글_제목과_사유로_그려지고_그_글로_간다() {
        long author = member();
        long post = newDeal(author);
        requestBy(post, 3);
        ends.endByManager(post, hotdealOperator(), "판매처에서 품절됐어요");
        java.util.List<com.community.membernotification.dto.response.MemberNotificationItemView> items = notifications.list(author, null, 10, null).items();
        String title = jdbc.queryForObject("SELECT title FROM posts WHERE id = ?", String.class, post);
        var ended = items.stream().filter(i -> "DEAL_MANAGER_ENDED".equals(i.type())).findFirst().orElseThrow();
        assertThat(ended.title()).contains(title);
        assertThat(ended.body()).isEqualTo("판매처에서 품절됐어요");
        assertThat(ended.link()).isEqualTo("/posts/" + post);
        var flagged = items.stream().filter(i -> "DEAL_FLAGGED".equals(i.type())).findFirst().orElseThrow();
        assertThat(flagged.title()).contains(title);
        assertThat(flagged.body()).isNotBlank();
        assertThat(flagged.category()).isEqualTo("system");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.community.membernotification.service.MemberNotificationService notifications;
}
