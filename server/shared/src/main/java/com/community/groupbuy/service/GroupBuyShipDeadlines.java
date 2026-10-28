package com.community.groupbuy.service;

import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 발송 기한(결정 51 — 전자상거래법 제15조 ①) — 대시보드 「발송 기한 지남」·주문 목록 거르기 {@code SHIP_OVERDUE}·1시간 점검 워커가 같은 식을 쓴다.
 *
 * <ul>
 *   <li>마감 후 발주: 공구의 발송 예정일(지연 공지로 바뀐 날) 다음 날 0시(KST)가 기한.</li>
 *   <li>즉시 발송: 결제(확정) 시각부터 {@code ship_within_business_days} 영업일의 끝(주말·설정 휴일 건너뜀). 그 뒤 발송 지연 공지가 있었으면 공지의 새 예정일 다음 날 0시.</li>
 *   <li>대상은 발송 전 주문(CONFIRMED·PREPARING). 성사 전 JOINED 는 아직 발송할 수 없어 뺀다.</li>
 * </ul>
 */
@Component
public class GroupBuyShipDeadlines {

    /** 발송 전 주문 한 줄과 그 기한 */
    public record Due(long participationId, long groupBuyId, Instant dueAt) {
    }

    private final JdbcTemplate jdbc;
    private final GroupBuyStore groups;

    public GroupBuyShipDeadlines(DataSource dataSource, GroupBuyStore groups) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.groups = groups;
    }

    /** 주문 하나의 발송 기한(발송 전 주문이 아니면 null) */
    public Instant dueAt(long participationId) {
        List<Due> rows = query(" AND pa.id = ?", participationId);
        return rows.isEmpty() ? null : rows.get(0).dueAt();
    }

    /** 기한이 {@code now} 이전인 발송 전 주문(오래된 기한 차례, 최대 {@code limit}) */
    public List<Due> overdue(Instant now, int limit) {
        List<Due> out = new ArrayList<>();
        for (Due d : query("")) {
            if (d.dueAt() != null && d.dueAt().isBefore(now)) {
                out.add(d);
            }
        }
        out.sort((a, b) -> a.dueAt().compareTo(b.dueAt()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private List<Due> query(String where, Object... args) {
        List<LocalDate> holidays = groups.settings().holidays();
        return jdbc.query("SELECT pa.id, pa.group_buy_id, g.fulfillment, g.ship_planned_at, g.ship_within_business_days, COALESCE(pa.confirmed_at, pa.joined_at), "
                + "(SELECT n.new_planned_at FROM group_buy_delay_notices n WHERE n.group_buy_id = g.id AND n.created_at >= COALESCE(pa.confirmed_at, pa.joined_at) "
                + "ORDER BY n.created_at DESC LIMIT 1) FROM group_buy_participations pa JOIN group_buys g ON g.id = pa.group_buy_id "
                + "WHERE pa.status IN ('CONFIRMED', 'PREPARING')" + where, (rs, i) -> {
            Date planned = rs.getDate(4);
            Timestamp paid = rs.getTimestamp(6);
            Date delayed = rs.getDate(7);
            Instant due;
            if (delayed != null) {
                due = dayAfter(delayed.toLocalDate());
            } else if ("AFTER_CLOSE".equals(rs.getString(3))) {
                due = planned == null ? null : dayAfter(planned.toLocalDate());
            } else {
                int days = rs.getObject(5) == null ? 3 : rs.getInt(5);
                due = paid == null ? null : GroupBuyBusinessDays.endOfBusinessDays(paid.toInstant(), days, holidays);
            }
            return new Due(rs.getLong(1), rs.getLong(2), due);
        }, args);
    }

    private static Instant dayAfter(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }
}
