package com.community.withdrawal.repository.master;

import com.community.withdrawal.dto.WithdrawalChoices;
import com.community.withdrawal.entity.WithdrawalSource;
import com.community.withdrawal.entity.WithdrawalStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 탈퇴 신청 기록 표({@code member_withdrawals}, shared V132·V134) — 신청·완료·취소와 자동 완료 배치가 쓴다. 개인정보는 없다(회원 번호·고른 것·금액·시각·운영 메모).
 * JDBC 인 이유: 탈퇴 트랜잭션의 다른 정리(JDBC)와 같은 연결에서 쓰고, 배치가 스프링 데이터 없이 훑는다. 관리자 목록·요약 읽기는 admin 의 {@code MemberWithdrawalAdminStore}
 * 가 {@link #COLUMNS}·{@link #row} 를 같이 쓴다.
 */
@Repository
public class MemberWithdrawalStore {

    /**
     * 한 줄 — {@code groupChoicesJson}·{@code pendingJson} 은 저장한 JSON 글 그대로.
     *
     * @param source           신청 경로(회원 본인 · 운영자 강제)
     * @param forceReasonKind  운영자 강제 탈퇴의 사유 종류(회원 신청이면 null) — {@code forceMemo}·{@code forceOperatorId} 도 같다
     * @param cancelChannel    운영자 대리 취소의 요청 경로(회원 본인 취소·취소 아님이면 null) — {@code cancelMemo}·{@code cancelOperatorId} 도 같다
     * @param forfeitEmoticonEarnings 로보티콘 수익 포기 동의 금액(V148 — 정산 정보 없이 수익이 남은 작가가 동의했을 때만, 동의 시각은 {@code forfeitConsentedAt})
     */
    public record Row(long id, long userId, WithdrawalStatus status, WithdrawalSource source, String priorStatus, String reason, String reasonNote, WithdrawalChoices choices,
                      String groupChoicesJson, Long forfeitPaidPoints, Long forfeitRefundEstimate, Long forfeitBizAmount, Instant forfeitConsentedAt, String pendingJson,
                      int pendingCount, Instant expectedCompleteAt, Instant rejoinUntil, Boolean hasPaymentHistory, Instant progressNotifiedAt, Instant requestedAt,
                      Instant completedAt, Instant canceledAt, String forceReasonKind, String forceMemo, Long forceOperatorId, String cancelChannel, String cancelMemo,
                      Long cancelOperatorId, Long forfeitEmoticonEarnings) {

        public boolean scheduled() {
            return status == WithdrawalStatus.SCHEDULED;
        }
    }

    /** 새 신청 줄 — {@code force} 는 운영자 강제 탈퇴일 때만(회원 신청이면 null) */
    public record NewRow(long id, long userId, WithdrawalStatus status, WithdrawalSource source, String priorStatus, String reason, String reasonNote, WithdrawalChoices choices,
                         String groupChoicesJson, Long forfeitPaidPoints, Long forfeitRefundEstimate, Long forfeitBizAmount, Instant forfeitConsentedAt, String pendingJson,
                         int pendingCount, Instant expectedCompleteAt, Instant rejoinUntil, Boolean hasPaymentHistory, Instant requestedAt, Force force,
                         Long forfeitEmoticonEarnings) {
    }

    /** 운영자 강제 탈퇴 정보 */
    public record Force(String reasonKind, String memo, long operatorId) {
    }

    /** 운영자 대리 취소 정보 */
    public record OperatorCancel(String channel, String memo, long operatorId) {
    }

    public static final String COLUMNS = "id, user_id, status, source, prior_status, reason, reason_note, paid_point_choice, delivered_choice, biz_choice, group_choices::text, "
            + "forfeit_paid_points, forfeit_refund_estimate, forfeit_biz_amount, forfeit_consented_at, pending_snapshot::text, pending_count, expected_complete_at, rejoin_until, "
            + "has_payment_history, progress_notified_at, requested_at, completed_at, canceled_at, force_reason_kind, force_memo, force_operator_id, cancel_channel, cancel_memo, "
            + "cancel_operator_id, forfeit_emoticon_earnings";

    private final JdbcTemplate jdbc;

    public MemberWithdrawalStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void insert(NewRow r) {
        Force force = r.force();
        jdbc.update("INSERT INTO member_withdrawals (id, user_id, status, source, prior_status, reason, reason_note, paid_point_choice, delivered_choice, biz_choice, group_choices, "
                        + "forfeit_paid_points, forfeit_refund_estimate, forfeit_biz_amount, forfeit_consented_at, pending_snapshot, pending_count, expected_complete_at, "
                        + "rejoin_until, has_payment_history, requested_at, completed_at, force_reason_kind, force_memo, force_operator_id, forfeit_emoticon_earnings) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.userId(), r.status().name(), r.source().name(), r.priorStatus(), r.reason(), r.reasonNote(), r.choices().paidPointChoice(), r.choices().deliveredChoice(),
                r.choices().bizChoice(), r.groupChoicesJson(), r.forfeitPaidPoints(), r.forfeitRefundEstimate(), r.forfeitBizAmount(), ts(r.forfeitConsentedAt()), r.pendingJson(),
                r.pendingCount(), ts(r.expectedCompleteAt()), ts(r.rejoinUntil()), r.hasPaymentHistory(), ts(r.requestedAt()),
                r.status() == WithdrawalStatus.COMPLETED ? ts(r.requestedAt()) : null, force == null ? null : force.reasonKind(), force == null ? null : force.memo(),
                force == null ? null : force.operatorId(), r.forfeitEmoticonEarnings());
    }

    public Optional<Row> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_withdrawals WHERE id = ?", MemberWithdrawalStore::row, id).stream().findFirst();
    }

    /** 그 회원의 진행 중 신청(회원당 하나) */
    public Optional<Row> findScheduled(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_withdrawals WHERE user_id = ? AND status = 'SCHEDULED'", MemberWithdrawalStore::row, userId).stream().findFirst();
    }

    /** 그 회원의 가장 최근 신청(진행 중·완료·취소 무관) — 신청한 적 없으면 비어 있다 */
    public Optional<Row> findLatest(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_withdrawals WHERE user_id = ? ORDER BY requested_at DESC, id DESC LIMIT 1", MemberWithdrawalStore::row, userId)
                .stream().findFirst();
    }

    /** 진행 중 신청을 잠가 읽는다(배치·완료가 같은 줄을 두 번 처리하지 않게) */
    public Optional<Row> lockScheduled(long userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM member_withdrawals WHERE user_id = ? AND status = 'SCHEDULED' FOR UPDATE", MemberWithdrawalStore::row, userId).stream()
                .findFirst();
    }

    /** 진행 중 신청 ID — 신청 시각 차례(배치의 덩어리 읽기, {@code afterId} 다음부터) */
    public List<Long> scheduledIds(long afterId, int limit) {
        return jdbc.queryForList("SELECT id FROM member_withdrawals WHERE status = 'SCHEDULED' AND id > ? ORDER BY id LIMIT ?", Long.class, afterId, limit);
    }

    /** 남은 일 스냅샷을 고친다(진행 중 신청만) */
    public void updatePending(long id, String pendingJson, int pendingCount, Instant expectedCompleteAt) {
        jdbc.update("UPDATE member_withdrawals SET pending_snapshot = ?::jsonb, pending_count = ?, expected_complete_at = ? WHERE id = ? AND status = 'SCHEDULED'", pendingJson,
                pendingCount, ts(expectedCompleteAt), id);
    }

    /** 완료 — 진행 중이었을 때만 바뀐다. 남은 일 스냅샷은 마지막 모습 그대로 둔다(관리자 화면이 「기다렸던 일」을 보인다), 남은 수는 0 */
    public boolean complete(long id, Instant now, Instant rejoinUntil, boolean hasPaymentHistory, Long forfeitBizAmount) {
        return jdbc.update("UPDATE member_withdrawals SET status = 'COMPLETED', completed_at = ?, rejoin_until = ?, has_payment_history = ?, pending_count = 0, "
                + "forfeit_biz_amount = COALESCE(?, forfeit_biz_amount) WHERE id = ? AND status = 'SCHEDULED'", ts(now), ts(rejoinUntil), hasPaymentHistory, forfeitBizAmount, id) > 0;
    }

    /** 회원 본인 취소 — 진행 중이었을 때만 바뀐다. 스냅샷은 그대로, 남은 수는 0 */
    public boolean cancel(long id, Instant now) {
        return jdbc.update("UPDATE member_withdrawals SET status = 'CANCELED', canceled_at = ?, pending_count = 0 WHERE id = ? AND status = 'SCHEDULED'", ts(now), id) > 0;
    }

    /** 운영자 대리 취소 — 회원 본인 취소와 같되 요청 경로·메모·운영자를 남긴다 */
    public boolean cancelByOperator(long id, Instant now, OperatorCancel by) {
        return jdbc.update("UPDATE member_withdrawals SET status = 'CANCELED', canceled_at = ?, pending_count = 0, cancel_channel = ?, cancel_memo = ?, cancel_operator_id = ? "
                + "WHERE id = ? AND status = 'SCHEDULED'", ts(now), by.channel(), by.memo(), by.operatorId(), id) > 0;
    }

    public void markProgressNotified(long id, Instant now) {
        jdbc.update("UPDATE member_withdrawals SET progress_notified_at = ? WHERE id = ?", ts(now), id);
    }

    /** {@link #COLUMNS} 차례로 읽은 줄 → {@link Row} */
    public static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getLong("id"), rs.getLong("user_id"), WithdrawalStatus.valueOf(rs.getString("status")), WithdrawalSource.valueOf(rs.getString("source")),
                rs.getString("prior_status"), rs.getString("reason"), rs.getString("reason_note"),
                new WithdrawalChoices(rs.getString("paid_point_choice"), rs.getString("delivered_choice"), rs.getString("biz_choice")), rs.getString("group_choices"),
                longOrNull(rs, "forfeit_paid_points"), longOrNull(rs, "forfeit_refund_estimate"), longOrNull(rs, "forfeit_biz_amount"), at(rs, "forfeit_consented_at"),
                rs.getString("pending_snapshot"), rs.getInt("pending_count"), at(rs, "expected_complete_at"), at(rs, "rejoin_until"), (Boolean) rs.getObject("has_payment_history"),
                at(rs, "progress_notified_at"), at(rs, "requested_at"), at(rs, "completed_at"), at(rs, "canceled_at"), rs.getString("force_reason_kind"), rs.getString("force_memo"),
                longOrNull(rs, "force_operator_id"), rs.getString("cancel_channel"), rs.getString("cancel_memo"), longOrNull(rs, "cancel_operator_id"),
                longOrNull(rs, "forfeit_emoticon_earnings"));
    }

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant at(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime ts(Instant at) {
        return at == null ? null : OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
