package com.community.groupbuy.repository.master;

import com.community.groupbuy.entity.GroupBuyStatus;
import com.community.groupbuy.entity.SkuKind;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 공구({@code group_buys} — 상품 칸은 {@code group_buy_products} 를 이어 읽는다, 결정 89)·공구 단품·수정 이력·상태 이력·지연 공지·공구 설정 읽기·쓰기. 수량 열은 여기서 바꾸지 않는다({@link GroupBuyQtyStore} — 수량을 바꾸는 유일한 길).
 * 상태 전이는 한 문장 조건부 UPDATE(이긴 쪽만, commerce.md 3절).
 *
 * <p>JDBC 인 이유: 조건부 전이·{@code FOR UPDATE}·{@code RETURNING} 을 쓰고, 결제 확정 트랜잭션(결제 모듈 — JDBC)과 한 연결에서 돌고, 시험이 스프링 문맥 없이 조립한다
 * (계약 「패키지」 — 관리자 목록 조회의 JPA + QueryDSL 은 화면 API 와 함께 2b-4).
 */
@Repository
public class GroupBuyStore {

    /** 공구 + 상품 칸(이름·설명·정상가·청약철회 제한은 상품 것 — 결정 89) */
    static final String COLUMNS = "g.id, g.product_id, p.category_id, g.title, p.name AS product_name, p.summary, p.spec, p.description, p.normal_price, "
            + "p.normal_price_basis, p.normal_price_source, g.group_price, g.shipping_policy_code, g.shipping_fee, g.free_shipping_over, g.return_shipping_fee, "
            + "g.return_fee_mode, g.min_qty, g.max_qty, g.initial_max_qty, g.added_qty, g.max_qty_per_user, g.current_qty, g.reserved_qty, g.start_at, g.end_at, "
            + "g.extension_mode, g.extended_until, g.closing_at, g.close_reason, g.fulfillment, g.ship_planned_at, g.ship_within_business_days, p.withdrawal_restriction, "
            + "g.return_policy, g.status, g.published_at, g.round_root_id, g.round_no, g.round_limit_shared, g.revision_no, g.version, g.created_by, g.created_at, g.updated_at";
    static final String FROM = " FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id";

    /** 공구 한 줄(상품 칸 포함) */
    public record GroupBuyRow(long id, long productId, long categoryId, String title, String productName, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                              String normalPriceSource, long groupPrice, String shippingPolicyCode, Long shippingFee, Long freeShippingOver, Long returnShippingFee,
                              String returnFeeMode, int minQty, int maxQty, Integer initialMaxQty, int addedQty, Integer maxQtyPerUser, int currentQty, int reservedQty,
                              Instant startAt, Instant endAt, String extensionMode, Instant extendedUntil, Instant closingAt, String closeReason, String fulfillment,
                              LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String withdrawalRestriction, String returnPolicy, GroupBuyStatus status,
                              Instant publishedAt, long roundRootId, int roundNo, boolean roundLimitShared, int revisionNo, int version, Long createdBy, Instant createdAt,
                              Instant updatedAt) {

        /** 모집 마감 시각(연장됐으면 연장 끝) */
        public Instant deadline() {
            return extendedUntil != null ? extendedUntil : endAt;
        }

        public boolean immediate() {
            return "IMMEDIATE".equals(fulfillment);
        }
    }

    /**
     * 공구 단품 한 줄(결정 88·89) — {@code kind} MAIN 본 상품 / ADDON 추가 구성상품, {@code productSkuId} 상품 단품, {@code valueIds} 축 값 조합(MAIN, 축 차례),
     * {@code maxQty} null = 단품 한도 없음, {@code supplyPrice} 공급가(null = 아직 없음 — 모집 시작 때 필수, 회원 응답에 싣지 않는다).
     */
    public record SkuRow(long id, long groupBuyId, SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty,
                         Integer initialMaxQty, int currentQty, int reservedQty, boolean onSale, Long supplyPrice, int sortOrder) {

        public boolean main() {
            return kind == SkuKind.MAIN;
        }

        /** 단가 — MAIN = 공구가 + 추가금, ADDON = 자기 가격 */
        public long unitPrice(long groupPrice) {
            return main() ? Math.addExact(groupPrice, extraPrice) : addonPrice;
        }
    }

    /** 옵션 축(차례대로)과 값 — {@code code} 옵션 종류, {@code name} 그 종류의 지금 이름 */
    public record AxisRow(long id, String code, String name, int sortOrder, List<ValueRow> values) {
    }

    /** 축 값 */
    public record ValueRow(long id, long axisId, String value, int sortOrder) {
    }

    /** 대표 이미지 한 줄 */
    public record ImageRow(String imageKey, String thumbKey, int sortOrder) {
    }

    /** 공구 설정(1행) — 돈·시각에 닿는 값은 그 트랜잭션 안에서 읽는다(결정 19) */
    public record Settings(int holdMinutes, int demandMaxDays, int firstComeMaxDays, int extensionMinutes, int extensionWindowMinutes, int judgeGraceMinutes,
                           int shipWithinBusinessDays, List<LocalDate> holidays) {
    }

    private final JdbcTemplate jdbc;

    public GroupBuyStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    public Settings settings() {
        return jdbc.queryForObject("SELECT hold_minutes, demand_max_days, first_come_max_days, extension_minutes, extension_window_minutes, judge_grace_minutes, "
                + "ship_within_business_days, holidays FROM group_buy_settings WHERE id = 1", (rs, i) -> new Settings(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                rs.getInt(5), rs.getInt(6), rs.getInt(7), dates(rs.getArray(8))));
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    public Optional<GroupBuyRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE g.id = ?", GroupBuyStore::row, id).stream().findFirst();
    }

    /** 행을 잠그고 읽는다 — 공구 행을 잠그는 일(판정·중단)은 결제 행을 잠그는 일과 같은 트랜잭션에 넣지 않는다(결정 37) */
    public Optional<GroupBuyRow> lock(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE g.id = ? FOR UPDATE OF g", GroupBuyStore::row, id).stream().findFirst();
    }

    /** 새 공구(DRAFT) — 1차면 {@code roundRootId = id} */
    public void insert(long id, NewGroupBuy g, long roundRootId, int roundNo, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO group_buys (id, product_id, title, group_price, shipping_policy_code, min_qty, max_qty, max_qty_per_user, start_at, end_at, extension_mode, "
                        + "fulfillment, ship_planned_at, ship_within_business_days, return_policy, round_root_id, round_no, round_limit_shared, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, g.productId(), g.title(), g.groupPrice(), g.shippingPolicyCode(),
                g.minQty(), g.maxQty(), g.maxQtyPerUser(), ts(g.startAt()), ts(g.endAt()), g.extensionMode(), g.fulfillment(), date(g.shipPlannedAt()),
                g.shipWithinBusinessDays(), g.returnPolicy(), roundRootId, roundNo, g.roundLimitShared(), operatorId, ts(now), ts(now));
    }

    /** 공구 칸(DRAFT 의 모든 칸 — 상품 하나 + 판매 조건) */
    public record NewGroupBuy(long productId, String title, long groupPrice, String shippingPolicyCode, int minQty, int maxQty, Integer maxQtyPerUser, Instant startAt,
                              Instant endAt, String extensionMode, String fulfillment, LocalDate shipPlannedAt, Integer shipWithinBusinessDays, String returnPolicy,
                              boolean roundLimitShared) {
    }

    /** DRAFT 의 모든 칸을 고친다(낙관 잠금 — 버전이 같고 아직 DRAFT 일 때만) */
    public boolean updateDraft(long id, int version, NewGroupBuy g, Instant now) {
        return jdbc.update("UPDATE group_buys SET product_id = ?, title = ?, group_price = ?, shipping_policy_code = ?, min_qty = ?, max_qty = ?, max_qty_per_user = ?, "
                        + "start_at = ?, end_at = ?, extension_mode = ?, fulfillment = ?, ship_planned_at = ?, ship_within_business_days = ?, return_policy = ?, "
                        + "round_limit_shared = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ? AND status = 'DRAFT' AND published_at IS NULL",
                g.productId(), g.title(), g.groupPrice(), g.shippingPolicyCode(), g.minQty(), g.maxQty(), g.maxQtyPerUser(), ts(g.startAt()), ts(g.endAt()),
                g.extensionMode(), g.fulfillment(), date(g.shipPlannedAt()), g.shipWithinBusinessDays(), g.returnPolicy(), g.roundLimitShared(), ts(now), id, version) == 1;
    }

    /** 공개 뒤 공급가만 고쳤다 — 버전을 올린다(낙관 잠금) */
    public boolean touch(long id, int version, Instant now) {
        return jdbc.update("UPDATE group_buys SET version = version + 1, updated_at = ? WHERE id = ? AND version = ?", ts(now), id, version) == 1;
    }

    /** 회원 목록(결정 89 카테고리 필터) — 공개된 공구(중단 제외), 카테고리를 주면 그 카테고리와 그 아래 단, 숨긴 카테고리는 빼고. 새 공구 먼저 */
    public List<Long> publicList(Long categoryId, int limit) {
        return jdbc.queryForList("SELECT g.id FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id JOIN group_buy_categories c ON c.id = p.category_id "
                + "LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE g.published_at IS NOT NULL AND g.status <> 'CANCELED' AND NOT c.hidden "
                + "AND NOT coalesce(pc.hidden, false) AND (?::bigint IS NULL OR c.id = ? OR c.parent_id = ?) ORDER BY g.start_at DESC, g.id DESC LIMIT ?", Long.class,
                categoryId, categoryId, categoryId, limit);
    }

    /** 수정 이력 번호를 하나 올린다(수량 변경·지연 공지 — 표시 기록). 새 번호 */
    public int nextRevision(long id, Instant now) {
        return jdbc.queryForObject("UPDATE group_buys SET revision_no = revision_no + 1, updated_at = ? WHERE id = ? RETURNING revision_no", Integer.class, ts(now), id);
    }

    /** 운영자가 「모집 시작」을 눌렀다(공개 — 시작 시각 전이면 예정 공구) */
    public boolean publish(long id, int version, Instant now) {
        return jdbc.update("UPDATE group_buys SET published_at = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ? AND status = 'DRAFT' "
                + "AND published_at IS NULL", ts(now), ts(now), id, version) == 1;
    }

    /** 공개됐고 시작 시각이 된 DRAFT(상태 워커가 모집을 시작한다) */
    public List<Long> dueToStart(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buys WHERE status = 'DRAFT' AND published_at IS NOT NULL AND start_at <= ? ORDER BY start_at, id LIMIT ?",
                Long.class, ts(now), limit);
    }

    /**
     * DRAFT → RECRUITING — 그 시각에 유효한 배송비 정책 줄의 값을 복사하고 처음 수량을 고정한다(10-2). 공개됐고 시작 시각이 된 DRAFT 만(이긴 쪽만 true).
     */
    public boolean start(long id, ShippingPolicyStore.PolicyRow policy, Instant now) {
        return jdbc.update("UPDATE group_buys SET status = 'RECRUITING', shipping_fee = ?, free_shipping_over = ?, return_shipping_fee = ?, return_fee_mode = ?, "
                        + "initial_max_qty = max_qty, revision_no = 1, updated_at = ? WHERE id = ? AND status = 'DRAFT' AND published_at IS NOT NULL AND start_at <= ?",
                policy.baseFee(), policy.freeShippingOver(), policy.returnFee(), policy.returnFeeMode(), ts(now), id, ts(now)) == 1;
    }

    /** 모집 시작 때 단품의 처음 수량을 고정한다 */
    public void fixSkuInitial(long groupBuyId) {
        jdbc.update("UPDATE group_buy_skus SET initial_max_qty = max_qty WHERE group_buy_id = ?", groupBuyId);
    }

    /** 마감 시각이 지난 모집 중 공구 */
    public List<Long> recruitingPastDeadline(Instant now, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buys WHERE status = 'RECRUITING' AND coalesce(extended_until, end_at) <= ? ORDER BY end_at, id LIMIT ?",
                Long.class, ts(now), limit);
    }

    /** 마감 시각에 연장한다(원래 마감에서 한 번 — 결정 50: 확정과 둘 다 RECRUITING 조건부라 하나만 이긴다) */
    public boolean extend(long id, Instant until, Instant now) {
        return jdbc.update("UPDATE group_buys SET extended_until = ?, updated_at = ? WHERE id = ? AND status = 'RECRUITING' AND extended_until IS NULL AND end_at < ?",
                ts(until), ts(now), id, ts(until)) == 1;
    }

    /** 기간 끝 → CLOSING(closing_at = 마감 시각 또는 연장 끝) */
    public boolean closeTimeUp(long id, Instant now) {
        return jdbc.update("UPDATE group_buys SET status = 'CLOSING', close_reason = 'TIME_UP', closing_at = coalesce(extended_until, end_at), updated_at = ? "
                + "WHERE id = ? AND status = 'RECRUITING' AND coalesce(extended_until, end_at) <= ?", ts(now), id, ts(now)) == 1;
    }

    /** 조기 마감(운영자) → CLOSING(closing_at = 지금) */
    public boolean closeEarly(long id, Instant now) {
        return jdbc.update("UPDATE group_buys SET status = 'CLOSING', close_reason = 'EARLY_CLOSED', closing_at = ?, updated_at = ? WHERE id = ? AND status = 'RECRUITING'",
                ts(now), ts(now), id) == 1;
    }

    /**
     * 다 팔림 조건(공구 별칭 {@code g}) — 공구 최대(MAIN 수량)에 닿았거나, 판매 중인 MAIN 단품이 모두 한도가 있고 모두 확정 = 한도(결정 27·48·78·88).
     * 추가 구성상품은 공구 마감을 정하지 않는다.
     */
    static final String SOLD_OUT_CONDITION = "(g.current_qty = g.max_qty OR NOT EXISTS (SELECT 1 FROM group_buy_skus s WHERE s.group_buy_id = g.id AND s.kind = 'MAIN' "
            + "AND s.on_sale AND (s.max_qty IS NULL OR s.current_qty < s.max_qty)))";

    /** 다 팔림 안전망 대상(10-7 ③ · 결정 78) */
    public List<Long> recruitingSoldOut(int limit) {
        return jdbc.queryForList("SELECT g.id FROM group_buys g WHERE g.status = 'RECRUITING' AND " + SOLD_OUT_CONDITION + " ORDER BY g.id LIMIT ?", Long.class, limit);
    }

    /** 집계 중이고 판정할 때가 된 공구(남은 선점 0 이거나 closing_at + 선점 시간 + 여유가 지남) */
    public List<Long> closingDue(Instant now, int holdMinutes, int graceMinutes, int limit) {
        return jdbc.queryForList("SELECT id FROM group_buys WHERE status = 'CLOSING' AND (reserved_qty = 0 OR closing_at + make_interval(mins => ?) <= ?) "
                + "ORDER BY closing_at, id LIMIT ?", Long.class, holdMinutes + graceMinutes, ts(now), limit);
    }

    /** 판정 결과로 전이(잠근 행을 판단한 뒤 — 결정 37 ①) */
    public boolean judge(long id, boolean success, Instant now) {
        return jdbc.update("UPDATE group_buys SET status = ?, updated_at = ? WHERE id = ? AND status = 'CLOSING'", success ? "CLOSED_SUCCESS" : "CLOSED_FAILED", ts(now),
                id) == 1;
    }

    /** 중단 — 종료 사유 CANCELED(결정 29 ①) */
    public boolean cancel(long id, Instant now) {
        return jdbc.update("UPDATE group_buys SET status = 'CANCELED', close_reason = 'CANCELED', updated_at = ? WHERE id = ? AND status IN "
                + "('RECRUITING', 'CLOSING', 'CLOSED_SUCCESS', 'ORDERED', 'SHIPPING')", ts(now), id) == 1;
    }

    /** 진행 중 주문이 없는 공구를 COMPLETED 로 — 즉시 발송은 CLOSED_SUCCESS, 마감 후 발주는 SHIPPING 에서(결정 9 — 1분 워커의 조건부 sweep). 바꾼 공구 */
    public List<Long> completeFinished(Instant now) {
        return jdbc.queryForList("UPDATE group_buys g SET status = 'COMPLETED', updated_at = ? WHERE ((g.status = 'CLOSED_SUCCESS' AND g.fulfillment = 'IMMEDIATE') "
                + "OR g.status = 'SHIPPING') AND NOT EXISTS (SELECT 1 FROM group_buy_participations p WHERE p.group_buy_id = g.id "
                + "AND p.status IN ('PENDING_PAYMENT', 'JOINED', 'CONFIRMED', 'PREPARING', 'SHIPPED')) RETURNING g.id", Long.class, ts(now));
    }

    public void insertStatusHistory(long id, long groupBuyId, GroupBuyStatus from, GroupBuyStatus to, String reason, Long actorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_status_history (id, group_buy_id, from_status, to_status, reason, actor_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id,
                groupBuyId, from == null ? null : from.name(), to.name(), reason, actorId, ts(now));
    }

    // ── 공구 단품·옵션 축(결정 88·89) ───────────────────────────────────────

    private static final String SKU_COLUMNS = "id, group_buy_id, kind, product_sku_id, value_ids, name, extra_price, addon_price, max_qty, initial_max_qty, current_qty, "
            + "reserved_qty, on_sale, supply_price, sort_order";

    /** 단품 — MAIN 먼저, 차례대로 */
    public List<SkuRow> skus(long groupBuyId) {
        return jdbc.query("SELECT " + SKU_COLUMNS + " FROM group_buy_skus WHERE group_buy_id = ? ORDER BY kind DESC, sort_order, id", GroupBuyStore::sku, groupBuyId);
    }

    /** 공구 상품의 옵션 축과 값(차례대로) */
    public List<AxisRow> axes(long groupBuyId) {
        List<AxisRow> axes = jdbc.query("SELECT a.id, a.option_type_code, t.name, a.sort_order FROM group_buy_option_axes a JOIN group_buy_option_types t ON t.code = a.option_type_code "
                + "JOIN group_buys g ON g.product_id = a.product_id WHERE g.id = ? "
                + "ORDER BY a.sort_order, a.id", (rs, i) -> new AxisRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), new ArrayList<>()), groupBuyId);
        for (AxisRow axis : axes) {
            axis.values().addAll(jdbc.query("SELECT id, axis_id, value, sort_order FROM group_buy_option_values WHERE axis_id = ? ORDER BY sort_order, id",
                    (rs, i) -> new ValueRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), axis.id()));
        }
        return axes;
    }

    /** 새 공구 단품(MAIN 은 추가금, ADDON 은 자기 가격) */
    public void insertSku(long id, long groupBuyId, SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty,
                          boolean onSale, Long supplyPrice, int sortOrder) {
        jdbc.update("INSERT INTO group_buy_skus (id, group_buy_id, kind, product_sku_id, value_ids, name, extra_price, addon_price, max_qty, on_sale, supply_price, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?::bigint[], ?, ?, ?, ?, ?, ?, ?)", id, groupBuyId, kind.name(), productSkuId, longArray(valueIds), name, extraPrice, addonPrice,
                maxQty, onSale, supplyPrice, sortOrder);
    }

    /** DRAFT 의 공구 단품을 모두 지운다(다시 넣는다) */
    public void deleteSkus(long groupBuyId) {
        jdbc.update("DELETE FROM group_buy_skus WHERE group_buy_id = ?", groupBuyId);
    }

    /** 공급가를 고친다(모집 뒤에도 — 결정 89) */
    public void updateSupplyPrice(long skuId, long groupBuyId, Long supplyPrice) {
        jdbc.update("UPDATE group_buy_skus SET supply_price = ? WHERE id = ? AND group_buy_id = ?", supplyPrice, skuId, groupBuyId);
    }

    // ── 수정 이력 ───────────────────────────────────────────────────────────

    /**
     * 지금 공구 상태의 사본을 수정 이력으로 남긴다(표시 기록) — 상품 내용(이름·설명·이미지·옵션 축·고시 — 공구 상품과 추가 구성상품의 상품, 결정 89)·공구 단품(공급가 빼고)·수량.
     */
    public void snapshotRevision(long id, long groupBuyId, int revisionNo, String changeKind, Long editedBy, Instant now) {
        jdbc.update("INSERT INTO group_buy_revisions (id, group_buy_id, revision_no, title, product_name, summary, spec, description, images, max_qty, added_qty, "
                        + "option_axes, skus, notices, change_kind, edited_by, created_at) SELECT ?, g.id, ?, g.title, p.name, p.summary, p.spec, p.description, "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('imageKey', i.image_key, 'thumbKey', i.thumb_key) ORDER BY i.sort_order) FROM group_buy_product_images i "
                        + "WHERE i.product_id = p.id), '[]'::jsonb), g.max_qty, g.added_qty, "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('code', a.option_type_code, 'name', (SELECT t.name FROM group_buy_option_types t WHERE t.code = a.option_type_code), 'values', (SELECT coalesce(jsonb_agg(v.value ORDER BY v.sort_order, v.id), '[]'::jsonb) "
                        + "FROM group_buy_option_values v WHERE v.axis_id = a.id)) ORDER BY a.sort_order, a.id) FROM group_buy_option_axes a WHERE a.product_id = p.id), '[]'::jsonb), "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('skuId', s.id::text, 'productSkuId', s.product_sku_id::text, 'kind', s.kind, 'name', s.name, "
                        + "'extraPrice', s.extra_price, 'addonPrice', s.addon_price, 'maxQty', s.max_qty, 'onSale', s.on_sale) ORDER BY s.kind DESC, s.sort_order, s.id) "
                        + "FROM group_buy_skus s WHERE s.group_buy_id = g.id), '[]'::jsonb), "
                        + "coalesce((SELECT jsonb_agg(jsonb_build_object('productId', n.product_id::text, 'title', n.title, 'categoryCode', n.category_code, "
                        + "'catalogVersion', n.catalog_version, "
                        + "'skus', (SELECT coalesce(jsonb_agg(ps.name ORDER BY ps.sort_order, ps.id), '[]'::jsonb) FROM group_buy_notice_skus x "
                        + "JOIN group_buy_product_skus ps ON ps.id = x.product_sku_id WHERE x.notice_id = n.id), "
                        + "'values', (SELECT coalesce(jsonb_object_agg(v.item_key, jsonb_build_object('value', v.value, 'unavailable', v.unavailable, "
                        + "'reason', v.unavailable_reason)), '{}'::jsonb) FROM group_buy_notice_values v WHERE v.notice_id = n.id AND NOT v.custom), "
                        + "'customItems', (SELECT coalesce(jsonb_agg(jsonb_build_object('name', v.custom_name, 'value', v.value) ORDER BY v.sort_order), '[]'::jsonb) "
                        + "FROM group_buy_notice_values v WHERE v.notice_id = n.id AND v.custom)) ORDER BY n.product_id = p.id DESC, n.product_id, n.sort_order) "
                        + "FROM group_buy_notices n WHERE n.product_id = p.id OR n.product_id IN (SELECT ps.product_id FROM group_buy_skus s "
                        + "JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id WHERE s.group_buy_id = g.id AND s.kind = 'ADDON')), '[]'::jsonb), ?, ?, ? "
                        + "FROM group_buys g JOIN group_buy_products p ON p.id = g.product_id WHERE g.id = ?", id, revisionNo, changeKind, editedBy, ts(now), groupBuyId);
    }

    // ── 차수 ────────────────────────────────────────────────────────────────

    /** 루트에 DRAFT·RECRUITING 차수가 있는가(결정 31) */
    public boolean roundOpen(long roundRootId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buys WHERE round_root_id = ? AND status IN ('DRAFT', 'RECRUITING')", Integer.class, roundRootId);
        return n != null && n > 0;
    }

    public int maxRoundNo(long roundRootId) {
        Integer n = jdbc.queryForObject("SELECT coalesce(max(round_no), 0) FROM group_buys WHERE round_root_id = ?", Integer.class, roundRootId);
        return n == null ? 0 : n;
    }

    /** 루트의 모든 차수 ID(차수 사이 1인 한도 합산 — 결정 24·86) */
    public List<Long> roundIds(long roundRootId) {
        return jdbc.queryForList("SELECT id FROM group_buys WHERE round_root_id = ? ORDER BY round_no", Long.class, roundRootId);
    }

    /** 새 차수 모집 시작 때 알릴 회원 — 루트의 모든 차수의 NEXT_ROUND·RESTOCK·CLOSING_SOON 신청자, 같은 회원 한 번(결정 31) */
    public List<Long> roundWatchers(long roundRootId, long exceptGroupBuyId) {
        return jdbc.queryForList("SELECT DISTINCT w.user_id FROM group_buy_watches w JOIN group_buys g ON g.id = w.group_buy_id WHERE g.round_root_id = ? "
                + "AND g.id <> ? AND w.kind IN ('NEXT_ROUND', 'RESTOCK', 'CLOSING_SOON') ORDER BY w.user_id", Long.class, roundRootId, exceptGroupBuyId);
    }

    // ── 지연 공지 ───────────────────────────────────────────────────────────

    public void insertDelayNotice(long id, long groupBuyId, LocalDate previous, LocalDate next, String reason, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_delay_notices (id, group_buy_id, previous_planned_at, new_planned_at, reason, operator_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, groupBuyId, date(previous), date(next), reason, operatorId, ts(now));
    }

    /** 발송 예정일을 바꾼다(지연 공지 — 모집 뒤에도 이 길로만) */
    public void changeShipPlan(long groupBuyId, LocalDate next, Instant now) {
        jdbc.update("UPDATE group_buys SET ship_planned_at = ?, updated_at = ? WHERE id = ?", date(next), ts(now), groupBuyId);
    }

    /** 그 공구의 가장 이른 지연 공지 시각(없으면 빈 값) — 그 뒤로 발송 전 주문은 승인 없이 전액 취소할 수 있다(결정 52) */
    public Optional<Instant> firstDelayNoticeAt(long groupBuyId) {
        return jdbc.query("SELECT min(created_at) FROM group_buy_delay_notices WHERE group_buy_id = ?", (rs, i) -> instant(rs.getTimestamp(1)), groupBuyId).stream()
                .filter(java.util.Objects::nonNull).findFirst();
    }

    // ── 매핑 ────────────────────────────────────────────────────────────────

    static GroupBuyRow row(ResultSet rs, int i) throws SQLException {
        return new GroupBuyRow(rs.getLong("id"), rs.getLong("product_id"), rs.getLong("category_id"), rs.getString("title"), rs.getString("product_name"), rs.getString("summary"), rs.getString("spec"),
                rs.getString("description"), nLong(rs, "normal_price"), rs.getString("normal_price_basis"), rs.getString("normal_price_source"), rs.getLong("group_price"),
                rs.getString("shipping_policy_code"), nLong(rs, "shipping_fee"), nLong(rs, "free_shipping_over"), nLong(rs, "return_shipping_fee"),
                rs.getString("return_fee_mode"), rs.getInt("min_qty"), rs.getInt("max_qty"), nInt(rs, "initial_max_qty"), rs.getInt("added_qty"),
                nInt(rs, "max_qty_per_user"), rs.getInt("current_qty"), rs.getInt("reserved_qty"), instant(rs.getTimestamp("start_at")), instant(rs.getTimestamp("end_at")),
                rs.getString("extension_mode"), instant(rs.getTimestamp("extended_until")), instant(rs.getTimestamp("closing_at")), rs.getString("close_reason"),
                rs.getString("fulfillment"), rs.getObject("ship_planned_at", LocalDate.class), nInt(rs, "ship_within_business_days"),
                rs.getString("withdrawal_restriction"), rs.getString("return_policy"), GroupBuyStatus.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("published_at")), rs.getLong("round_root_id"), rs.getInt("round_no"), rs.getBoolean("round_limit_shared"), rs.getInt("revision_no"),
                rs.getInt("version"), nLong(rs, "created_by"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    static SkuRow sku(ResultSet rs, int i) throws SQLException {
        return new SkuRow(rs.getLong("id"), rs.getLong("group_buy_id"), SkuKind.valueOf(rs.getString("kind")), rs.getLong("product_sku_id"), longs(rs.getArray("value_ids")),
                rs.getString("name"), rs.getLong("extra_price"), nLong(rs, "addon_price"), nInt(rs, "max_qty"), nInt(rs, "initial_max_qty"), rs.getInt("current_qty"),
                rs.getInt("reserved_qty"), rs.getBoolean("on_sale"), nLong(rs, "supply_price"), rs.getInt("sort_order"));
    }

    static List<Long> longs(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            out.add(((Number) value).longValue());
        }
        return List.copyOf(out);
    }

    /** {@code ?::bigint[]} 로 넘길 배열 글자({1,2,3}) */
    static String longArray(List<Long> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    static List<LocalDate> dates(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        List<LocalDate> out = new ArrayList<>();
        for (Object value : values) {
            out.add(value instanceof Date d ? d.toLocalDate() : LocalDate.parse(value.toString()));
        }
        return out;
    }

    static Long nLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer nInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    static Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    static String marks(Collection<?> values) {
        return values.stream().map(v -> "?").collect(Collectors.joining(", "));
    }
}
