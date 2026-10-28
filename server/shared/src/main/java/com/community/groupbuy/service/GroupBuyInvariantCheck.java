package com.community.groupbuy.service;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 공구 불변식 검사(commerce.md 18절 「불변식 추가」, 계약 결정 28·39·65·77·80) — <b>한 SQL</b>(같은 스냅샷)로 어긋난 줄만 돌려준다(읽기만). 비면 맞다.
 *
 * <ol>
 *   <li>공구 {@code current_qty} = 결제 확정된 주문의 <b>MAIN</b> 단품 줄 판매 수량 Σ(qty − canceled − restocked)(결제 중·만료 주문은 빼고 — 추가 구성상품은 공구 수량에 들지 않는다, 결정 88)</li>
 *   <li>공구 {@code reserved_qty} = 결제 중 주문의 MAIN 줄 수량 Σ</li>
 *   <li>단품별 {@code current_qty}·{@code reserved_qty} = 그 단품 줄의 같은 합(MAIN·ADDON 모두)</li>
 *   <li>Σ MAIN 단품 current = 공구 current, Σ MAIN 단품 reserved = 공구 reserved(단품 최대 합과 공구 최대는 관계 없다)</li>
 *   <li>공구 값을 적은 수량 이동 줄의 마지막(ID 순) 이후 값 = 지금 공구 값, 단품 값을 적은 마지막 줄 = 지금 단품 값</li>
 *   <li>모집을 시작한 공구: {@code max_qty = initial_max_qty + ΣADD − ΣREDUCE}(공구 값을 적은 줄만 — 추가 구성상품 입고는 빼고), {@code added_qty = ΣADD}</li>
 *   <li>받아들인 결제(이상 없음): 결제 − 취소됨 − 아직 끝나지 않은 취소 − 계좌 송금으로 돌린 취소 = 현금 − 환불 누계 − 보상 누계(결정 39·74)</li>
 *   <li>반품비 추가 결제(결정 68): 결제 − 취소됨 − 끝나지 않은 취소 = 요청의 낸 금액 − 돌려준 금액</li>
 * </ol>
 * 대상 공구를 주면 그 공구들만 본다(시험·운영자 점검), 비면 전체.
 */
@Component
public class GroupBuyInvariantCheck {

    /** 어긋난 한 줄 — {@code check} 이름, 대상(공구·단품·주문) ID, 기대값, 실제값 */
    public record Violation(String check, long targetId, long expected, long actual) {
    }

    private static final String SQL = """
            WITH g AS (SELECT * FROM group_buys WHERE (cardinality(?::bigint[]) = 0 OR id = ANY (?::bigint[]))),
            lines AS (
                SELECT i.group_buy_id, i.sku_id, s.kind,
                       CASE WHEN p.status NOT IN ('PENDING_PAYMENT', 'EXPIRED') THEN i.qty - i.canceled_qty - i.restocked_qty ELSE 0 END AS sold_qty,
                       CASE WHEN p.status = 'PENDING_PAYMENT' THEN i.qty ELSE 0 END AS held_qty
                FROM group_buy_participation_items i JOIN group_buy_participations p ON p.id = i.participation_id
                    JOIN group_buy_skus s ON s.id = i.sku_id
                WHERE i.group_buy_id IN (SELECT id FROM g)),
            sold AS (SELECT group_buy_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines WHERE kind = 'MAIN' GROUP BY group_buy_id),
            sku_sold AS (SELECT sku_id, sum(sold_qty) AS current_qty, sum(held_qty) AS reserved_qty FROM lines GROUP BY sku_id),
            last_move AS (SELECT DISTINCT ON (group_buy_id) group_buy_id, current_after, reserved_after, max_after FROM group_buy_qty_movements
                          WHERE group_buy_id IN (SELECT id FROM g) AND current_after IS NOT NULL ORDER BY group_buy_id, id DESC),
            last_sku_move AS (SELECT DISTINCT ON (sku_id) sku_id, sku_current_after, sku_reserved_after, sku_max_after FROM group_buy_qty_movements
                              WHERE group_buy_id IN (SELECT id FROM g) AND sku_id IS NOT NULL AND sku_current_after IS NOT NULL ORDER BY sku_id, id DESC),
            adjust AS (SELECT group_buy_id, coalesce(sum(qty) FILTER (WHERE kind = 'ADD'), 0) AS added, coalesce(sum(qty) FILTER (WHERE kind = 'REDUCE'), 0) AS reduced
                       FROM group_buy_qty_movements WHERE group_buy_id IN (SELECT id FROM g) AND max_after IS NOT NULL GROUP BY group_buy_id),
            main_sum AS (SELECT group_buy_id, sum(current_qty) AS current_qty, sum(reserved_qty) AS reserved_qty FROM group_buy_skus
                         WHERE group_buy_id IN (SELECT id FROM g) AND kind = 'MAIN' GROUP BY group_buy_id),
            pay AS (SELECT p.id, p.cash_amount - p.refunded_amount - p.compensation_amount AS expected,
                           po.paid_amount - po.canceled_amount - coalesce((SELECT sum(c.amount) FROM payment_cancels c WHERE c.payment_order_id = po.id
                               AND (c.status IN ('PENDING', 'SENDING', 'GAVE_UP') OR (c.status = 'MANUAL' AND c.manual_method = 'BANK_TRANSFER'))), 0) AS actual
                    FROM group_buy_participations p JOIN payment_orders po ON po.order_type = 'GROUP_BUY' AND po.order_ref_id = p.id
                        AND po.anomaly IS NULL AND po.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')
                    WHERE p.group_buy_id IN (SELECT id FROM g)),
            fee AS (SELECT r.id, r.fee_paid_amount - r.fee_refunded_amount AS expected,
                           coalesce((SELECT sum(po.paid_amount - po.canceled_amount - coalesce((SELECT sum(c.amount) FROM payment_cancels c WHERE c.payment_order_id = po.id
                               AND (c.status IN ('PENDING', 'SENDING', 'GAVE_UP') OR (c.status = 'MANUAL' AND c.manual_method = 'BANK_TRANSFER'))), 0))
                               FROM payment_orders po WHERE po.order_type = 'GROUP_BUY_RETURN_FEE' AND po.order_ref_id = r.id AND po.anomaly IS NULL
                               AND po.status IN ('PAID', 'PARTIAL_CANCELED', 'CANCELED')), 0) AS actual
                    FROM group_buy_requests r WHERE r.group_buy_id IN (SELECT id FROM g))
            SELECT 'GROUP_CURRENT', g.id, coalesce(s.current_qty, 0), g.current_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'GROUP_RESERVED', g.id, coalesce(s.reserved_qty, 0), g.reserved_qty FROM g LEFT JOIN sold s ON s.group_buy_id = g.id
                WHERE g.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'SKU_CURRENT', k.id, coalesce(s.current_qty, 0), k.current_qty FROM group_buy_skus k LEFT JOIN sku_sold s ON s.sku_id = k.id
                WHERE k.group_buy_id IN (SELECT id FROM g) AND k.current_qty <> coalesce(s.current_qty, 0)
            UNION ALL
            SELECT 'SKU_RESERVED', k.id, coalesce(s.reserved_qty, 0), k.reserved_qty FROM group_buy_skus k LEFT JOIN sku_sold s ON s.sku_id = k.id
                WHERE k.group_buy_id IN (SELECT id FROM g) AND k.reserved_qty <> coalesce(s.reserved_qty, 0)
            UNION ALL
            SELECT 'MAIN_SUM_CURRENT', g.id, g.current_qty, ms.current_qty FROM g JOIN main_sum ms ON ms.group_buy_id = g.id WHERE ms.current_qty <> g.current_qty
            UNION ALL
            SELECT 'MAIN_SUM_RESERVED', g.id, g.reserved_qty, ms.reserved_qty FROM g JOIN main_sum ms ON ms.group_buy_id = g.id WHERE ms.reserved_qty <> g.reserved_qty
            UNION ALL
            SELECT 'LAST_MOVE_GROUP', g.id, g.current_qty::bigint * 1000000000 + g.reserved_qty::bigint * 100000 + g.max_qty,
                   lm.current_after::bigint * 1000000000 + lm.reserved_after::bigint * 100000 + lm.max_after FROM g JOIN last_move lm ON lm.group_buy_id = g.id
                WHERE (lm.current_after, lm.reserved_after, lm.max_after) <> (g.current_qty, g.reserved_qty, g.max_qty)
            UNION ALL
            SELECT 'LAST_MOVE_SKU', k.id, k.current_qty::bigint * 1000000 + k.reserved_qty, lm.sku_current_after::bigint * 1000000 + lm.sku_reserved_after
                FROM group_buy_skus k JOIN last_sku_move lm ON lm.sku_id = k.id
                WHERE (lm.sku_current_after, lm.sku_reserved_after) <> (k.current_qty, k.reserved_qty) OR lm.sku_max_after IS DISTINCT FROM k.max_qty
            UNION ALL
            SELECT 'MAX_FORMULA', g.id, g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0), g.max_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.max_qty <> g.initial_max_qty + coalesce(a.added, 0) - coalesce(a.reduced, 0)
            UNION ALL
            SELECT 'ADDED_QTY', g.id, coalesce(a.added, 0), g.added_qty FROM g LEFT JOIN adjust a ON a.group_buy_id = g.id
                WHERE g.initial_max_qty IS NOT NULL AND g.added_qty <> coalesce(a.added, 0)
            UNION ALL
            SELECT 'PAYMENT_BALANCE', pay.id, pay.expected, pay.actual FROM pay WHERE pay.expected <> pay.actual
            UNION ALL
            SELECT 'RETURN_FEE_BALANCE', fee.id, fee.expected, fee.actual FROM fee WHERE fee.expected <> fee.actual
            """;

    private final JdbcTemplate jdbc;

    public GroupBuyInvariantCheck(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** 어긋난 줄(비면 맞다) */
    public List<Violation> violations(List<Long> groupBuyIds) {
        Long[] ids = groupBuyIds == null ? new Long[0] : groupBuyIds.toArray(Long[]::new);
        return jdbc.query(SQL, (rs, i) -> new Violation(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)), ids, ids);
    }
}
