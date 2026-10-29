package com.community.groupbuy.feedback;

import com.community.groupbuy.service.GroupBuyNotifications;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공구 질문·리뷰 신고(2b-6, 표 {@code group_buy_ugc_reports}) — 회원은 공용 {@code POST /api/reports} 로 보낸다(대상 {@code GROUP_BUY_QUESTION}·{@code GROUP_BUY_REVIEW}).
 * 공용 신고 서비스가 회원 상태·신고 켜짐·사유·설명을 먼저 검사하고 여기로 넘긴다. 기존 {@code reports} 는 게시판이 꼭 있어야 해서 따로 둔다(계약 「2b-6 결과」 세션 결정).
 *
 * <ul>
 *   <li>대상: 볼 수 있는 질문(지우지·숨기지 않은 · 남의 비밀글이 아닌)·리뷰(지우지·숨기지 않은). 그 밖은 404 {@code REPORT_TARGET_NOT_FOUND}.</li>
 *   <li>내 글은 400 {@code CANNOT_REPORT_OWN}, 같은 대상 두 번은 409 {@code ALREADY_REPORTED}.</li>
 *   <li>처리는 운영자 숨김과 같은 길 — 숨기면 그 대상의 열린 신고가 RESOLVED({@link #resolve}).</li>
 * </ul>
 */
@Service
public class GroupBuyUgcReportService {

    public static final String QUESTION = "GROUP_BUY_QUESTION";
    public static final String REVIEW = "GROUP_BUY_REVIEW";
    public static final Set<String> TYPES = Set.of(QUESTION, REVIEW);

    private final JdbcTemplate jdbc;
    private final SnowflakeIdGenerator ids;
    private final GroupBuyNotifications notifications;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyUgcReportService(DataSource dataSource, SnowflakeIdGenerator ids, GroupBuyNotifications notifications, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.ids = ids;
        this.notifications = notifications;
        this.clocks = clocks;
    }

    @Transactional
    public void report(String type, long targetId, long reporterId, String reason, String detail) {
        Long author = QUESTION.equals(type)
                ? jdbc.query("SELECT user_id FROM group_buy_questions WHERE id = ? AND deleted_at IS NULL AND hidden_at IS NULL AND (NOT secret OR user_id = ?)",
                        (rs, i) -> rs.getLong(1), targetId, reporterId).stream().findFirst().orElse(null)
                : jdbc.query("SELECT user_id FROM group_buy_reviews WHERE id = ? AND deleted_at IS NULL AND hidden_at IS NULL", (rs, i) -> rs.getLong(1), targetId).stream()
                        .findFirst().orElse(null);
        if (author == null) {
            throw FeedbackException.reportTargetNotFound();
        }
        if (author == reporterId) {
            throw FeedbackException.cannotReportOwn();
        }
        try {
            jdbc.update("INSERT INTO group_buy_ugc_reports (id, target_type, target_id, reporter_id, reason, detail, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?)",
                    ids.nextId(), type, targetId, reporterId, reason, detail == null || detail.isEmpty() ? null : detail, Timestamp.from(now()));
        } catch (DuplicateKeyException e) {
            throw FeedbackException.alreadyReported();
        }
        notifications.ugcReported(type, targetId);
    }

    /** 숨김과 같은 길 — 그 대상의 열린 신고를 처리됨으로 */
    @Transactional
    public int resolve(String type, long targetId, long operatorId) {
        return jdbc.update("UPDATE group_buy_ugc_reports SET status = 'RESOLVED', resolved_at = ?, resolved_by = ? WHERE target_type = ? AND target_id = ? AND status = 'OPEN'",
                Timestamp.from(now()), operatorId, type, targetId);
    }

    /** 열린 신고 — 사유별 수(많은 것 먼저) */
    @Transactional(readOnly = true)
    public List<FeedbackView.ReportCount> openReasons(String type, long targetId) {
        return jdbc.query("SELECT reason, count(*) FROM group_buy_ugc_reports WHERE target_type = ? AND target_id = ? AND status = 'OPEN' GROUP BY reason "
                + "ORDER BY count(*) DESC, reason", (rs, i) -> new FeedbackView.ReportCount(rs.getString(1), rs.getLong(2)), type, targetId);
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant();
    }
}
