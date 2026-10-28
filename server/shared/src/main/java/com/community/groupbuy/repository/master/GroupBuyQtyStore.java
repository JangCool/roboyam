package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.entity.QtyMovementKind;
import java.time.Instant;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공구·단품 <b>수량 열</b>과 수량 이동 줄 — 모두 한 문장 조건부 UPDATE … RETURNING(이후 값을 따로 SELECT 하지 않는다 — 결정 82). 0 행이면 빈 값(조건에 졌다).
 * 부르는 쪽은 {@code GroupBuyQtyService} 하나다(수량을 바꾸는 유일한 길). 잠금 차례는 <b>공구 행 → 단품 행(ID 오름차순, 한 줄씩)</b>.
 * 공구 행의 current·reserved·max 는 MAIN 단품 수량만이다(추가 구성상품은 단품 행만 — 결정 88).
 */
@Repository
public class GroupBuyQtyStore {

    /** 바꾼 뒤의 값 — 단품의 {@code max} 는 한도가 없으면 null */
    public record After(int current, int reserved, Integer max) {
    }

    private final JdbcTemplate jdbc;

    public GroupBuyQtyStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    private Optional<After> one(String sql, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            int max = rs.getInt(3);
            Integer maxOrNull = rs.wasNull() ? null : max; // 한도 없는 단품
            return new After(rs.getInt(1), rs.getInt(2), maxOrNull);
        }, args).stream().findFirst();
    }

    // ── 공구 행(MAIN 수량) ───────────────────────────────────────────────────

    /** 선점 — 모집 중이고 시작 시각이 지났고 마감 전이며 남은 수량이 있을 때만(commerce.md 10-4). {@code qty} 0 이면 상태·기간만 확인하고 행을 잠근다 */
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

    /** 결제된 수량 취소 — {@code requireAcceptingPayment} 면 모집 중·집계 중일 때만(성사형 본인 즉시 취소 — 판정과 하나만 이긴다, 결정 45). {@code qty} 0 이면 상태만 확인 */
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

    /** 추가 입고 — 모집 중이고 다 팔리기 전(잠시 품절 포함)에만(결정 17). {@code qty} 0 이면 조건만 확인하고 행을 잠근다(추가 구성상품 입고) */
    public Optional<After> groupAdd(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET max_qty = max_qty + ?, added_qty = added_qty + ? WHERE id = ? AND status = 'RECRUITING' AND current_qty < max_qty "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, qty, groupBuyId);
    }

    /** 줄이기 — 모집 중이고 하한 = max(확정 + 선점, 성사형 최소 수량, 1) 아래로 내려가지 않을 때만(결정 17). {@code qty} 0 이면 모집 중인지만 */
    public Optional<After> groupReduce(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET max_qty = max_qty - ? WHERE id = ? AND status = 'RECRUITING' AND max_qty - ? >= current_qty + reserved_qty "
                + "AND max_qty - ? >= min_qty AND max_qty - ? >= 1 RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId, qty, qty, qty);
    }

    /** 다 팔리면 그 자리에서 CLOSING(결정 27·48·78·88) — 모집 중일 때만(이긴 쪽만 true) */
    public boolean closeIfSoldOut(long groupBuyId, Instant now) {
        return jdbc.update("UPDATE group_buys g SET status = 'CLOSING', close_reason = 'SOLD_OUT', closing_at = ?, updated_at = ? WHERE g.id = ? AND g.status = 'RECRUITING' "
                + "AND " + GroupBuyStore.SOLD_OUT_CONDITION, ts(now), ts(now), groupBuyId) == 1;
    }

    // ── 단품 행 ─────────────────────────────────────────────────────────────

    /** 단품 선점 — 판매 중이고 한도 안일 때만. 한도 없는 단품도 수량 열은 늘 갱신한다(결정 77) */
    public Optional<After> skuReserve(long skuId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_skus SET reserved_qty = reserved_qty + ? WHERE id = ? AND group_buy_id = ? AND on_sale "
                + "AND (max_qty IS NULL OR current_qty + reserved_qty + ? <= max_qty) RETURNING current_qty, reserved_qty, max_qty", qty, skuId, groupBuyId, qty);
    }

    public Optional<After> skuConfirm(long skuId, int qty) {
        return one("UPDATE group_buy_skus SET reserved_qty = reserved_qty - ?, current_qty = current_qty + ? WHERE id = ? AND reserved_qty >= ? "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, qty, skuId, qty);
    }

    public Optional<After> skuRelease(long skuId, int qty) {
        return one("UPDATE group_buy_skus SET reserved_qty = reserved_qty - ? WHERE id = ? AND reserved_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty, skuId,
                qty);
    }

    public Optional<After> skuCancel(long skuId, int qty) {
        return one("UPDATE group_buy_skus SET current_qty = current_qty - ? WHERE id = ? AND current_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty, skuId, qty);
    }

    /** 단품 추가 입고 — 한도 있는 단품만(결정 80) */
    public Optional<After> skuAdd(long skuId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_skus SET max_qty = max_qty + ? WHERE id = ? AND group_buy_id = ? AND max_qty IS NOT NULL RETURNING current_qty, reserved_qty, max_qty",
                qty, skuId, groupBuyId);
    }

    /** 단품 줄이기 — 하한 = 그 단품의 확정 + 선점(결정 48) */
    public Optional<After> skuReduce(long skuId, long groupBuyId, int qty) {
        return one("UPDATE group_buy_skus SET max_qty = max_qty - ? WHERE id = ? AND group_buy_id = ? AND max_qty IS NOT NULL AND max_qty - ? >= current_qty + reserved_qty "
                + "AND max_qty - ? >= 1 RETURNING current_qty, reserved_qty, max_qty", qty, skuId, groupBuyId, qty, qty);
    }

    /** 단품 다시 판매(확정 감소 — 공구 행 조건은 {@link #groupRestock} 이 먼저) */
    public Optional<After> skuRestock(long skuId, int qty) {
        return one("UPDATE group_buy_skus SET current_qty = current_qty - ? WHERE id = ? AND current_qty >= ? RETURNING current_qty, reserved_qty, max_qty", qty, skuId, qty);
    }

    /** 수량을 바꾸지 않고 지금 값을 읽는다(기록만 하는 이동 RETURN·EXCHANGE_OUT 의 이후 값 — 결정 82: 따로 SELECT 하지 않고 같은 문장으로) */
    public Optional<After> skuTouch(long skuId) {
        return one("UPDATE group_buy_skus SET current_qty = current_qty WHERE id = ? RETURNING current_qty, reserved_qty, max_qty", skuId);
    }

    /** 공급 불가 줄이기(결정 53 — 모집 상태와 관계없이, 하한 = max(확정 + 선점, 성사형 최소, 1) — 최소 수량 CHECK 는 DB 가 지킨다) */
    public Optional<After> groupReduceAnyStatus(long groupBuyId, int qty) {
        return one("UPDATE group_buys SET max_qty = max_qty - ? WHERE id = ? AND max_qty - ? >= current_qty + reserved_qty AND max_qty - ? >= min_qty AND max_qty - ? >= 1 "
                + "RETURNING current_qty, reserved_qty, max_qty", qty, groupBuyId, qty, qty, qty);
    }

    // ── 이동 줄 ─────────────────────────────────────────────────────────────

    /** 수량 이동 한 줄 — 공구 이후 값은 공구 행을 바꾼 이동만({@code group} null 이면 비움), 단품 이후 값은 단품 이동만 */
    public void insertMovement(long id, long groupBuyId, Long skuId, Long participationId, Long itemId, QtyMovementKind kind, int qty, After group, After sku,
                               String reason, Long actorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_qty_movements (id, group_buy_id, sku_id, participation_id, participation_item_id, kind, qty, current_after, reserved_after, "
                        + "max_after, sku_current_after, sku_reserved_after, sku_max_after, reason, actor_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, groupBuyId, skuId, participationId, itemId, kind.name(), qty, group == null ? null : group.current(), group == null ? null : group.reserved(),
                group == null ? null : group.max(), sku == null ? null : sku.current(), sku == null ? null : sku.reserved(), sku == null ? null : sku.max(), reason, actorId,
                ts(now));
    }

    /** 마감 판단 창 안에 MAIN 남은 수량을 늘린 이동이 있었는가(DEMAND 연장 — 결정 25·81) — 공구 행을 바꾼 이동만 */
    public boolean releasedBetween(long groupBuyId, Instant from, Instant to) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buy_qty_movements WHERE group_buy_id = ? AND kind IN ('RELEASE', 'CANCEL', 'RESTOCK', 'ADD') "
                + "AND current_after IS NOT NULL AND created_at >= ? AND created_at <= ?", Integer.class, groupBuyId, ts(from), ts(to));
        return n != null && n > 0;
    }
}
