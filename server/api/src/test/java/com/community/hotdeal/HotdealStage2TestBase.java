package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchFailureRecorder;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchLockService;
import com.community.batch.core.BatchParameters;
import com.community.batch.core.BatchRegistry;
import com.community.batch.core.BatchRunRecorder;
import com.community.batch.core.BatchRunner;
import com.community.batch.entity.BatchRequestKind;
import com.community.hotdeal.dto.response.PostDealView;
import com.community.hotdeal.service.DealEndService;
import com.community.hotdeal.service.DealStateService;
import com.community.permission.entity.Permission;
import com.community.testsupport.PointInvariants;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 핫딜 서버 2단계 시험의 바탕 — 종료 요청·keep·되돌리기·보상·배치. 1단계 바탕({@link HotdealApiTestBase})에 상태 API·종료 공용 길·배치 실행기·포인트 확인을 더한다.
 * 종료 요청·의심 설정 칸은 시험이 바꾸면 {@link #restoreStage2} 가 기본값으로 되돌린다.
 */
abstract class HotdealStage2TestBase extends HotdealApiTestBase {

    @Autowired
    protected DealStateService states;
    @Autowired
    protected DealEndService ends;

    @AfterEach
    void restoreStage2() {
        jdbc.update("UPDATE deal_settings SET end_request_threshold = 3, end_grace_hours = 12, end_request_daily_limit = 20, end_request_per_author_limit = 3, "
                + "end_request_per_author_days = 7, keep_max_count = 2, suspicion_min_posts = 3, suspicion_window_days = 7, store_suspicion_members = 3, "
                + "store_suspicion_window_days = 30, store_suspicion_other_ratio_percent = 20 WHERE id = 1");
        dealSettings.invalidate();
    }

    /** 새 핫딜 하나(작성자 = 새 회원이면 {@code author}) — 판매처는 겹치지 않는 호스트 */
    protected long newDeal(long author) {
        return dealBy(author, deal(url("s" + uniq() + ".shop.example.com")));
    }

    /** 같은 작성자가 잇달아 올린다 — 글 작성 간격(게시판 기본 {@code POST_TOO_FAST})은 이 시험의 대상이 아니라 그 회원의 앞 글 시각을 10분 앞으로 민다 */
    protected long dealBy(long author, com.community.hotdeal.dto.request.DealInput input) {
        jdbc.update("UPDATE posts SET created_at = created_at - interval '10 minutes' WHERE author_id = ?", author);
        return writeId(author, input);
    }

    /** 그 딜에 새 회원 {@code n} 명이 종료 요청 — 요청한 회원들 */
    protected List<Long> requestBy(long postId, int n, String reason) {
        List<Long> members = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            long m = member();
            states.request(Long.toString(postId), m, reason);
            members.add(m);
        }
        return members;
    }

    protected List<Long> requestBy(long postId, int n) {
        return requestBy(postId, n, "SOLD_OUT");
    }

    protected PostDealView request(long postId, long member, String reason) {
        return states.request(Long.toString(postId), member, reason);
    }

    /** 회원 알림 수(그 회원 · 종류 · 그 글) */
    protected int notices(long user, String type, long postId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = ? AND post_id = ?", Integer.class, user, type, postId);
        return n == null ? 0 : n;
    }

    /** 관리자 알림 수(그 운영자 · 이벤트) */
    protected int adminNotices(long operator, String event) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM admin_notifications WHERE operator_id = ? AND event_code = ?", Integer.class, operator, event);
        return n == null ? 0 : n;
    }

    /** 핫딜 처리 권한 운영자 */
    protected long hotdealOperator() {
        return operatorWith(Permission.HOTDEAL_MANAGE);
    }

    /** 그 회원의 종료 요청 보상 줄 수 */
    protected int rewards(long user) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'DEAL_END_REQUEST' AND ref_type = 'DEAL_END_REQUEST'",
                Integer.class, user);
        return n == null ? 0 : n;
    }

    protected long balance(long user) {
        List<Long> rows = jdbc.queryForList("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, user);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    /** 포인트 정책의 보상 금액 · 하루 지급 횟수 */
    protected int rewardPoints() {
        return jdbc.queryForObject("SELECT deal_end_request_points FROM point_settings WHERE id = 1", Integer.class);
    }

    protected int rewardDailyLimit() {
        return jdbc.queryForObject("SELECT deal_end_request_daily_limit FROM point_settings WHERE id = 1", Integer.class);
    }

    protected void assertInvariants(List<Long> users) {
        assertThat(PointInvariants.violations(jdbc, users.stream().mapToLong(Long::longValue).toArray())).isEmpty();
    }

    /** 시계를 의심 시각 + {@code hours} 로 */
    protected void afterFlag(long postId, Duration by) {
        Instant flagged = ((java.sql.Timestamp) dealRow(postId).get("flagged_at")).toInstant();
        clock.set(flagged.plus(by));
    }

    /** 배치를 「지금 실행」으로 한 번 돌린다(운영과 같은 실행기 — 결과가 OK·SKIP 이어야 한다) */
    protected BatchRunner.RunReport run(BatchJob job) {
        BatchRunner runner = new BatchRunner(new BatchLockService(dataSource), new BatchRunRecorder(dataSource, transactions, ids, 954),
                new BatchFailureRecorder(dataSource, transactions, ids), BatchRegistry.forJobs(List.of(job)), transactions);
        BatchRunner.RunReport report = runner.runRequested(new BatchRunner.RunRequest(job.definition(), BatchRequestKind.NOW, null, BatchParameters.of(Map.of()), null,
                null, null));
        assertThat(report.runId()).as(String.valueOf(report)).isNotNull();
        Map<String, Object> row = jdbc.queryForMap("SELECT status, error_message, message_key FROM batch_runs WHERE id = ?", report.runId());
        assertThat(row.get("status")).as(String.valueOf(row)).isIn("OK", "SKIP");
        return report;
    }
}
