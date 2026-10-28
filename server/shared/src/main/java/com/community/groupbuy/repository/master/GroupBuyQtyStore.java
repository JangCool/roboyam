package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.QtyMovementKind;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공구·옵션 <b>수량 열</b>과 수량 이동 줄 — 모두 한 문장 조건부 UPDATE … RETURNING(이후 값을 따로 SELECT 하지 않는다 — 결정 82). 0 행이면 빈 값(조건에 졌다).
 * 부르는 쪽은 {@code GroupBuyQtyService} 하나다(수량을 바꾸는 유일한 길). 잠금 차례는 공구 행 → 옵션 행(ID 오름차순, 한 줄씩).
 */
@Repository
public class GroupBuyQtyStore {

    /** 바꾼 뒤의 값 — 옵션의 {@code max} 는 한도가 없으면 null */
    public record After(int current, int reserved, Integer max) {
    }

    private final JdbcTemplate jdbc;

    public GroupBuyQtyStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private Optional<After> one(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            int max = rs.getInt(3);
            Integer maxOrNull = rs.wasNull() ? null : max; // 한도 없는 옵션
            return new After(rs.getInt(1), rs.getInt(2), maxOrNull);
        }, args).stream().findFirst();
    }

    // ── 공구 행 ─────────────────────────────────────────────────────────────

    /** 선점 — 모집 중이고 시작 시각이 지났고 마감 전이며 남은 수량이 있을 때만(commerce.md 10-4) */
    public Optional<After> groupReserve(long groupBuyId, int qty, Instant now) {
        return one("UPDATE group_buys SET reserved_qty = reserved_qty + ? WHERE id = ? AND status = 'RECRUITING' AND start_at <= ? AND ? < coalesce(extended_until, end_at) "
                + "AND current_qty + reserved_qty + ? <= max_qty RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId, ts(now), ts(now), qty);
    }

    /** 결제 확정 — 결제를 받는 상태(모집 중·집계 중)이고 선점이 그만큼 있을 때만(결정 37·82) */
    public Optional<After> groupConfirm(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET reserved_qty = reserved_qty - ?, current_qty = current_qty + ? WHERE id = ? AND status IN ('RECRUITING', 'CLOSING') "
                + "AND reserved_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty, qty, groupBuyId, qty);
    }

    /** 선점 해제 */
    public Optional<After> groupRelease(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET reserved_qty = reserved_qty - ? WHERE id = ? AND reserved_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId,
                qty);
    }

    /** 결제된 수량 취소 — {@code requireAcceptingPayment} 면 모집 중·집계 중일 때만(성사형 본인 즉시 취소 — 판정과 하나만 이긴다, 결정 45) */
    public Optional<After> groupCancel(long groupBuyId, int qty, boolean requireAcceptingPayment) {
        String status = requireAcceptingPayment ? " AND status IN ('RECRUITING', 'CLOSING')" : "";
        return one("UPDATE group_buys SET current_qty = current_qty - ? WHERE id = ? AND current_qty >= ?" + status + " RETURNING current_qty, reserved_qty, max_qty", qty,
                groupBuyId, qty);
    }

    /** 다시 판매 — 모집 중이고 다 팔리기 전에만(결정 15·27 — 마감과 경쟁해도 하나만 이긴다) */
    public Optional<After> groupRestock(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET current_qty = current_qty - ? WHERE id = ? AND status = 'RECRUITING' AND current_qty < max_qty AND current_qty >= ? "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId, qty);
    }

    /** 추가 입고 — 모집 중이고 다 팔리기 전(잠시 품절 포함)에만(결정 17) */
    public Optional<After> groupAdd(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET max_qty = max_qty + ?, added_qty = added_qty + ? WHERE id = ? AND status = 'RECRUITING' AND current_qty < max_qty "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, qty, groupBuyId);
    }

    /** 줄이기 — 모집 중이고 하한 = max(확정 + 선점, 성사형 최소 수량, 1) 아래로 내려가지 않을 때만(결정 17) */
    public Optional<After> groupReduce(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET max_qty = max_qty - ? WHERE id = ? AND status = 'RECRUITING' AND max_qty - ? >= current_qty + reserved_qty "
                + "AND max_qty - ? >= min_qty AND max_qty - ? >= 1 RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId, qty, qty, qty);
    }

    /**
     * 다 팔리면 그 자리에서 CLOSING(결정 27·48·78) — 공구 최대에 닿았거나, 옵션이 있고 모든 옵션에 한도가 있으며 모두 확정 = 한도. 모집 중일 때만(이긴 쪽만 true).
     */
    public boolean closeIfSoldOut(long groupBuyId, Instant now) {
        return jdbc.update("UPDATE group_buys g SET status = 'CLOSING', close_reason = 'SOLD_OUT', closing_at = ?, updated_at = ? WHERE g.id = ? AND g.status = 'RECRUITING' "
                + "AND (g.current_qty = g.max_qty OR (EXISTS (SELECT 1 FROM group_buy_options o WHERE o.group_buy_id = g.id) AND NOT EXISTS (SELECT 1 FROM group_buy_options o "
                + "WHERE o.group_buy_id = g.id AND (o.max_qty IS NULL OR o.current_qty < o.max_qty))))", ts(now), ts(now), groupBuyId) == 1;
    }

    // ── 옵션 행 ─────────────────────────────────────────────────────────────

    /** 옵션 선점 — 한도 없는 옵션도 수량 열은 늘 갱신한다(결정 77) */
    public Optional<After> optionReserve(long optionId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_options SET reserved_qty = reserved_qty + ? WHERE id = ? AND group_buy_id = ? AND (max_qty IS NULL OR current_qty + reserved_qty + ? <= max_qty) "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, optionId, groupBuyId, qty);
    }

    public Optional<After> optionConfirm(long optionId, int qty) {
        return one("UPDATE group_buy_options SET reserved_qty = reserved_qty - ?, current_qty = current_qty + ? WHERE id = ? AND reserved_qty >= ? "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, qty, optionId, qty);
    }

    public Optional<After> optionRelease(long optionId, int qty) {
        return one("UPDATE group_buy_options SET reserved_qty = reserved_qty - ? WHERE id = ? AND reserved_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty,
                optionId, qty);
    }

    public Optional<After> optionCancel(long optionId, int qty) {
        return one("UPDATE group_buy_options SET current_qty = current_qty - ? WHERE id = ? AND current_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty,
                optionId, qty);
    }

    /** 옵션 추가 입고 — 한도 있는 옵션만(결정 80) */
    public Optional<After> optionAdd(long optionId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_options SET max_qty = max_qty + ? WHERE id = ? AND group_buy_id = ? AND max_qty IS NOT NULL RETURNING current_qty, reserved_qty, max_qty",
                qty, optionId, groupBuyId);
    }

    /** 옵션 줄이기 — 하한 = 그 옵션의 확정 + 선점(결정 48) */
    public Optional<After> optionReduce(long optionId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_options SET max_qty = max_qty - ? WHERE id = ? AND group_buy_id = ? AND max_qty IS NOT NULL AND max_qty - ? >= current_qty + reserved_qty "
                + "AND max_qty - ? >= 1 RETURNING current_qty, reserved_qty, max_qty", qty, optionId, groupBuyId, qty, qty);
    }

    // ── 이동 줄 ─────────────────────────────────────────────────────────────

    /** 수량 이동 한 줄 — 공구 이후 값은 늘, 옵션 이후 값은 옵션 이동일 때만 */
    public void insertMovement(long id, long groupBuyId, Long optionId, Long participationId, Long itemId, QtyMovementKind kind, int qty, After group, After option,
                               String reason, Long actorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_qty_movements (id, group_buy_id, option_id, participation_id, participation_item_id, kind, qty, current_after, reserved_after, "
                        + "max_after, option_current_after, option_reserved_after, option_max_after, reason, actor_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupBuyId, optionId, participationId, itemId, kind.name(), qty, group.current(), group.reserved(), group.max(), option == null ? null : option.current(),
                option == null ? null : option.reserved(), option == null ? null : option.max(), reason, actorId, ts(now));
    }

    /** 마감 판단 창 안에 남은 수량을 늘린 이동이 있었는가(DEMAND 연장 — 결정 25·81) */
    public boolean releasedBetween(long groupBuyId, Instant from, Instant to) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buy_qty_movements WHERE group_buy_id = ? AND kind IN ('RELEASE', 'CANCEL', 'RESTOCK', 'ADD') "
                + "AND created_at >= ? AND created_at <= ?", Integer.class, groupBuyId, ts(from), ts(to));
        return n != null && n > 0;
    }
}
