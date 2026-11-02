package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.dto.response.BizDealPostView;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.service.BizHotdealPublisher;
import com.community.hotdeal.repository.master.DealQueries;
import com.community.hotdeal.service.DealEndService;
import com.community.hotdeal.service.DealModeration;
import com.community.hotdeal.service.DealSettingsReader;
import com.community.hotdeal.service.DealStateService;
import com.community.permission.entity.Permission;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사업자 핫딜 미확정 세 가지(biz-hotdeal-contract 결정 10~12, 사용자 결정 2026-11-02) — 핫딜 서비스만 정지면 게시 실패·풀면 다시 게시 · 사업자 글은 종료 요청으로 자동 종료하지 않고
 * 운영자 확인([종료]·[유지]) · 운영진 종료·재개는 신청 건 처리 기록 + 사업자 알림(환불 없음).
 */
class BizHotdealReviewTest extends BizHotdealTestBase {

    @Autowired
    private DealEndService ends;
    @Autowired
    private DealStateService states;
    @Autowired
    private DealModeration moderation;
    @Autowired
    private DealSettingsReader dealSettings;
    @Autowired
    private DealQueries dealQueries;
    @Autowired
    private com.community.post.service.PostQueryService postQueries;

    // ── 결정 10 핫딜 서비스 정지 ────────────────────────────────────────────

    @Test
    void 결정10_신원은_승인인데_핫딜_서비스만_정지면_SERVICE_SUSPENDED_실패_다시_승인하면_다음_배치가_올린다() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = booked("NONE");
        hotdealService(b.user(), "SUSPENDED");
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.FAILED);
        assertThat(businessPosts(b.id())).isZero();
        assertThat(result(b.id()).get("fail_reason")).isEqualTo("SERVICE_SUSPENDED");
        assertThat(eventNote(b.id(), "POST_FAILED")).isEqualTo("SERVICE_SUSPENDED");
        assertThat(adminNotices(operator, "HOTDEAL_POST_FAILED")).isEqualTo(1);
        assertThat(adminNoticeArgs(operator, "HOTDEAL_POST_FAILED")).contains("notification.hotdeal.postFailed.reason.SERVICE_SUSPENDED");
        assertThat(memberNotices(b.user(), "POST_FAILED")).isEqualTo(1);
        assertThat(view.detail(b.user(), b.id()).post().failReason()).isEqualTo("SERVICE_SUSPENDED");

        // 아직 정지 — 배치가 고르지 않고(되풀이 알림 없음) 한 건 돌려도 그대로
        runPublishJob();
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.SKIPPED);
        assertThat(result(b.id()).get("status")).isEqualTo("FAILED");
        assertThat(adminNotices(operator, "HOTDEAL_POST_FAILED")).isEqualTo(1);

        // 서비스를 다시 승인 → 다음 배치가 실패 기록을 지우고 다시 판정해 올린다
        hotdealService(b.user(), "APPROVED");
        runPublishJob();
        assertThat(result(b.id()).get("status")).isEqualTo("POSTED");
        assertThat(businessPosts(b.id())).isEqualTo(1);
        assertThat(resultCount(b.id())).isEqualTo(1);
        List<String> events = events(b.id());
        assertThat(events.indexOf("POSTED")).isGreaterThan(events.indexOf("POST_FAILED"));
        assertThat(memberNotices(b.user(), "POSTED")).isEqualTo(1);
        assertThat(view.detail(b.user(), b.id()).post().status()).isEqualTo("POSTED");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        runPublishJob();
        assertThat(businessPosts(b.id())).isEqualTo(1);
    }

    @Test
    void 결정10_신원_정지가_먼저_걸리고_다른_실패_사유는_원인을_풀어도_다시_올리지_않는다() {
        Booked b = booked("NONE");
        jdbc.update("UPDATE business_profiles SET status = 'SUSPENDED', suspend_code = 'OTHER', suspend_text = '시험', suspended_at = now(), suspend_pending_action = 'KEEP', "
                + "suspend_scheduled_action = 'HOLD', suspend_running_action = 'TAKEDOWN_ONLY' WHERE id = ?", profileOf(b.user()));
        hotdealService(b.user(), "SUSPENDED");
        into(b, 1);
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.FAILED);
        assertThat(result(b.id()).get("fail_reason")).isEqualTo("BUSINESS_SUSPENDED");
        jdbc.update("UPDATE business_profiles SET status = 'APPROVED' WHERE id = ?", profileOf(b.user()));
        hotdealService(b.user(), "APPROVED");
        runPublishJob();
        assertThat(publisher.publishDue(b.id())).isEqualTo(BizHotdealPublisher.Outcome.SKIPPED);
        assertThat(result(b.id()).get("status")).isEqualTo("FAILED");
        assertThat(businessPosts(b.id())).isZero();
    }

    // ── 결정 11 종료 요청 → 운영자 확인 ─────────────────────────────────────

    @Test
    void 결정11_사업자_글은_종료_요청이_문턱에_닿아도_자동_종료하지_않고_확인_필요_운영자_사업자_알림() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        flag(postId);
        assertThat(dealRow(postId).get("flagged_at")).isNotNull();
        BizDealPostView v = view.detail(b.user(), b.id()).post();
        assertThat(v.view()).isEqualTo("FLAGGED");
        assertThat(v.autoEndAt()).isNull();
        assertThat(postQueries.detail(Long.toString(postId), null, "127.0.0.1").deal().autoEndAt()).isNull();
        assertThat(events(b.id())).contains("DEAL_REVIEW_NEEDED");
        assertThat(adminNotices(operator, "HOTDEAL_BUSINESS_REVIEW")).isEqualTo(1);
        assertThat(memberNotices(b.user(), "DEAL_REVIEW")).isEqualTo(1);
        assertThat(dealNotices(b.user(), "DEAL_FLAGGED")).isZero(); // 회원 글의 「아직 진행 중을 눌러 주세요」 알림은 가지 않는다
        // 유예가 지나도 자동 종료 배치 후보가 아니고 자동 종료도 하지 않는다
        clock.set(clock.instant().plus(Duration.ofHours(dealSettings.current().endGraceHours() + 1L)));
        assertThat(dealQueries.autoEndCandidates(clock.instant(), 0, 1000)).doesNotContain(postId);
        assertThat(ends.autoEnd(postId, clock.instant())).isFalse();
        assertThat(dealRow(postId).get("status")).isEqualTo("ACTIVE");
        assertThat(events(b.id())).doesNotContain("DEAL_AUTO_ENDED");
        // 사업자는 「아직 진행 중」을 누를 수 없다 — 판단은 운영자
        assertThat(codeOf(() -> states.keep(Long.toString(postId), b.user()))).isEqualTo("DEAL_BUSINESS_POST_LOCKED");
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
    }

    @Test
    void 결정11_운영자_유지는_확인_필요를_풀고_새_회차로_세며_처리_기록과_사업자_알림_환불_없음() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        // 의심이 아니면 유지할 것이 없다
        assertThat(moderation.keep(postId, DealModeration.Actor.operator(operator, true), null)).isEqualTo(DealModeration.Result.STATE_CHANGED);
        flag(postId);
        assertThat(moderation.keep(postId, DealModeration.Actor.operator(operator, false), null)).isEqualTo(DealModeration.Result.FORBIDDEN);
        assertThat(moderation.keep(postId, DealModeration.Actor.operator(operator, true), " 재고 확인함 ")).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(dealRow(postId).get("flagged_at")).isNull();
        assertThat(dealRow(postId).get("kept_at")).isNotNull();
        assertThat(((Number) dealRow(postId).get("keep_count")).intValue()).isZero(); // 작성자 「아직 진행 중」 횟수는 쓰지 않는다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deal_end_requests WHERE post_id = ? AND flagged_period", Integer.class, postId)).isZero(); // 보상 대상 없음
        assertThat(eventNote(b.id(), "DEAL_KEPT_BY_OPERATOR")).isEqualTo("재고 확인함");
        assertThat(memberNotices(b.user(), "DEAL_KEPT")).isEqualTo(1);
        assertThat(view.detail(b.user(), b.id()).post().view()).isEqualTo("ACTIVE");
        assertThat(moderation.keep(postId, DealModeration.Actor.operator(operator, true), null)).isEqualTo(DealModeration.Result.STATE_CHANGED);
        // 새 회차 — 다시 문턱만큼 모이면 다시 확인 필요
        clock.set(clock.instant().plusSeconds(1)); // 새 회차는 유지 시각 뒤의 요청만 센다
        flag(postId);
        assertThat(dealRow(postId).get("flagged_at")).isNotNull();
        assertThat(events(b.id()).stream().filter("DEAL_REVIEW_NEEDED"::equals).count()).isEqualTo(2);
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
    }

    // ── 결정 12 운영진 종료·재개 기록 ───────────────────────────────────────

    @Test
    void 결정12_운영진_종료_재개는_신청_건_처리_기록_사유와_사업자_알림_환불_없음() {
        long operator = operatorWith(Permission.HOTDEAL_MANAGE);
        Booked b = postedBooking("NONE");
        long postId = postOf(b.id());
        DealModeration.Actor actor = DealModeration.Actor.operator(operator, true);
        assertThat(moderation.end(postId, actor, "품절 확인")).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(dealRow(postId).get("ended_by")).isEqualTo("MANAGER");
        assertThat(eventNote(b.id(), "DEAL_ENDED_BY_OPERATOR")).isEqualTo("품절 확인");
        assertThat(jdbc.queryForObject("SELECT actor_type FROM biz_application_events WHERE application_id = ? AND action = 'DEAL_ENDED_BY_OPERATOR'", String.class, b.id()))
                .isEqualTo("OPERATOR");
        assertThat(dealNotices(b.user(), "DEAL_MANAGER_ENDED")).isEqualTo(1); // 사업자(작성자)에게 사유와 함께
        assertThat(moderation.reopen(postId, actor, "다시 판매해요")).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(dealRow(postId).get("status")).isEqualTo("ACTIVE");
        assertThat(eventNote(b.id(), "DEAL_REOPENED_BY_OPERATOR")).isEqualTo("다시 판매해요");
        assertThat(memberNotices(b.user(), "DEAL_REOPENED")).isEqualTo(1);
        // 사유 없는 종료도 기록은 남는다(note 비움)
        assertThat(moderation.end(postId, actor, null)).isEqualTo(DealModeration.Result.CHANGED);
        assertThat(events(b.id()).stream().filter("DEAL_ENDED_BY_OPERATOR"::equals).count()).isEqualTo(2);
        assertThat(eventNote(b.id(), "DEAL_ENDED_BY_OPERATOR")).isNull();
        assertThat(app(b.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refundRows(b.id())).isZero();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 그 사업자의 핫딜 서비스 줄 상태를 바꾼다(서비스별 정지 API 는 아직 없다 — business-contract 「후속」) */
    private void hotdealService(long user, String status) {
        jdbc.update("UPDATE business_services SET status = ? WHERE profile_id = ? AND service = 'HOTDEAL'", status, profileOf(user));
    }

    /** 문턱만큼 새 회원이 종료 요청(품절) */
    private void flag(long postId) {
        int threshold = dealSettings.current().endRequestThreshold();
        for (int i = 0; i < threshold; i++) {
            states.request(Long.toString(postId), requester(), "SOLD_OUT");
        }
    }

    /** 이메일 인증을 마친 일반 등급 회원(종료 요청 자격) */
    private long requester() {
        long id = newMember();
        jdbc.update("UPDATE users SET email_verified_at = now() WHERE id = ?", id);
        String name = Long.toString(id, 36);
        jdbc.update("INSERT INTO profiles (user_id, nickname, grade_key) VALUES (?, ?, 'REGULAR')", id, "r" + name.substring(Math.max(0, name.length() - 11)));
        return id;
    }

    /** 핫딜 회원 알림 수(그 회원 · 종류) */
    private int dealNotices(long user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ?", Integer.class, user, type);
    }

    /** 관리자 알림 인자(문구 참조) 원문 */
    private String adminNoticeArgs(long operator, String event) {
        return jdbc.queryForList("SELECT CAST(args AS text) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", String.class, operator, event).toString();
    }
}
