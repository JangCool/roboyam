package com.community.groupbuy.service;

import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationSeverity;
import com.community.notification.service.NotificationPublisher;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공구 기한 점검(1시간 — 계약 「마이그레이션 계획」 「운영자 기한 점검은 19절 점검 워커가 생길 때까지 2b-4 가 1시간 워커로」). 워커 키 {@value #WORKER_KEY}, admin {@code GroupBuyStateScheduler.deadlineTick}.
 *
 * <ol>
 *   <li><b>P35 요청 처리 기한</b>: 열린 취소(요청한 날부터)·반품(돌려받은 날부터) 요청이 2영업일 지나면 {@code COMMERCE_REFUND_REQUESTED} 경고, 3영업일이면 심각(CRITICAL).
 *       요청·단계마다 한 번 — {@code group_buy_requests.deadline_alert_level} 을 조건부로 올린 쪽만 알린다(다시 돌려도 한 번).</li>
 *   <li><b>결정 51 발송 기한</b>: 기한을 넘긴 발송 전 주문이 있으면 {@code COMMERCE_SHIP_OVERDUE}(하루 한 건 — 묶음 키 KST 날짜).</li>
 *   <li><b>마감 임박 알림</b>: 모집 마감 24시간 안인 공구의 CLOSING_SOON 신청자에게 한 번.</li>
 * </ol>
 * 각 단계는 따로 트랜잭션 — 한 단계가 실패해도 다음 바퀴에 다시 한다.
 */
@Component
public class GroupBuyDeadlineWatch {

    public static final String WORKER_KEY = "groupbuy-deadline";
    /** 마감 임박 알림 창 — 회원 화면 「마감 임박」과 같다 */
    public static final Duration CLOSING_SOON = Duration.ofHours(24);

    /** 한 바퀴 결과 */
    public record Result(int requestWarnings, int overdueOrders, int closingSoon) {

        public int total() {
            return requestWarnings + (overdueOrders > 0 ? 1 : 0) + closingSoon;
        }
    }

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;
    private final GroupBuyShipDeadlines shipDeadlines;
    private final GroupBuyNotifications notifications;
    private final NotificationPublisher operators;
    private final TransactionTemplate tx;
    private final ObjectProvider<Clock> clocks;

    public GroupBuyDeadlineWatch(DataSource dataSource, GroupBuyStore groups, GroupBuyShipDeadlines shipDeadlines, GroupBuyNotifications notifications,
                                 NotificationPublisher operators, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
        this.shipDeadlines = shipDeadlines;
        this.notifications = notifications;
        this.operators = operators;
        this.tx = new TransactionTemplate(transactions);
        this.clocks = clocks;
    }

    public Result runOnce() {
        Instant now = clocks.getIfUnique(Clock::systemUTC).instant();
        Integer warnings = tx.execute(status -> requestDeadlines(now));
        Integer overdue = tx.execute(status -> shipOverdue(now));
        Integer closing = tx.execute(status -> notifications.closingSoon(CLOSING_SOON));
        return new Result(warnings == null ? 0 : warnings, overdue == null ? 0 : overdue, closing == null ? 0 : closing);
    }

    private int requestDeadlines(Instant now) {
        List<LocalDate> holidays = groups.settings().holidays();
        int sent = 0;
        List<Object[]> rows = jdbc.query("SELECT id, type, created_at, received_at, deadline_alert_level FROM group_buy_requests "
                + "WHERE status IN ('REQUESTED', 'APPROVED', 'RECEIVED') AND (deadline_alert_level IS NULL OR deadline_alert_level <> 'CRITICAL') ORDER BY created_at LIMIT 500",
                (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getTimestamp(3), rs.getTimestamp(4), rs.getString(5)});
        for (Object[] r : rows) {
            Timestamp start = "CANCEL".equals(r[1]) ? (Timestamp) r[2] : (Timestamp) r[3];
            if (start == null) {
                continue; // 아직 돌려받지 않은 반품
            }
            GroupBuyBusinessDays.Level level = GroupBuyBusinessDays.deadlineLevel(start.toInstant(), now, holidays);
            if (level == GroupBuyBusinessDays.Level.NONE) {
                continue;
            }
            String previous = (String) r[4];
            if (level.name().equals(previous)) {
                continue;
            }
            int moved = jdbc.update("UPDATE group_buy_requests SET deadline_alert_level = ? WHERE id = ? AND deadline_alert_level IS NOT DISTINCT FROM ?", level.name(), r[0],
                    previous);
            if (moved == 1) {
                operators.publish(AdminNotificationEvent.COMMERCE_REFUND_REQUESTED, NotificationContext.empty().dedupe("deadline:" + r[0] + ":" + level.name())
                        .severity(level == GroupBuyBusinessDays.Level.CRITICAL ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING));
                sent++;
            }
        }
        return sent;
    }

    private int shipOverdue(Instant now) {
        int overdue = shipDeadlines.overdue(now, 10_000).size();
        if (overdue > 0) {
            operators.publish(AdminNotificationEvent.COMMERCE_SHIP_OVERDUE, NotificationContext.empty().dedupe(ServiceTime.dateOf(now).toString()));
        }
        return overdue;
    }
}
