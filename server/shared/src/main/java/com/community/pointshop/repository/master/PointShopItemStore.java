package com.community.pointshop.repository.master;

import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopSettingEntity;
import com.community.pointshop.entity.PointShopUsageType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 포인트 상점 품목·설정 읽기·쓰기 — {@code com.community.pointshop.service} 만 부른다.
 *
 * <p>Native SQL(JDBC)인 이유: (1) 구매의 <b>재고 차감</b>은 {@code UPDATE … SET stock = stock - 1 WHERE id = ? AND stock > 0} 한 문장으로 한 명만 이긴다(마지막 하나를 둘이 동시에 사도
 * 하나만 — commerce.md 7-2). (2) 구매·환불은 포인트 사용·사용 취소({@code PointLedgerWriter}, JDBC)와 같은 트랜잭션·같은 연결에서 잠금 차례(재고 → 잔액)를 지켜야 한다.
 * (3) 시험이 스프링 문맥 없이 실제 PostgreSQL 위에서 서비스를 손으로 조립한다(1-1 시험 방식). 엔티티({@code PointShopItemEntity})는 읽기 전용 매핑이다. 트랜잭션은 부르는 서비스가 연다.
 */
@Repository
public class PointShopItemStore {

    /** 품목 한 줄(만든·고친 운영자 이름 포함 — 시스템(마이그레이션)이거나 지워진 운영자면 null) */
    public record ItemRow(long id, String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description,
                          String descriptionEn, String color, String borderStyle, String avatarId, int price, Integer durationDays, Integer stock, Instant saleStartAt,
                          Instant saleEndAt, boolean active, int sortOrder, Long createdBy, String createdByName, Instant createdAt, Long updatedBy,
                          String updatedByName, Instant updatedAt) {
    }

    /** 새로 넣거나 고칠 값(정체 칸 code·kind·avatar_id 는 넣을 때만 쓴다) */
    public record ItemValues(String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description, String descriptionEn,
                             String color, String borderStyle, String avatarId, int price, Integer durationDays, Integer stock, Instant saleStartAt, Instant saleEndAt,
                             boolean active, int sortOrder) {
    }

    /** 설정 행(마지막으로 저장한 운영자 이름 포함) */
    public record SettingsRow(int refundDays, Instant updatedAt, Long updatedBy, String updatedByName) {
    }

    private static final String COLUMNS = "i.id, i.code, i.kind, i.usage_type, i.name, i.name_en, i.description, i.description_en, i.color, i.border_style, i.avatar_id, "
            + "i.price_point, i.duration_days, i.stock, i.sale_start_at, i.sale_end_at, i.active, i.sort_order, i.created_by, c.operator_name, i.created_at, i.updated_by, "
            + "u.operator_name, i.updated_at";
    private static final String FROM = " FROM point_shop_items i LEFT JOIN operators c ON c.id = i.created_by LEFT JOIN operators u ON u.id = i.updated_by";
    private static final String ORDER = " ORDER BY i.sort_order, i.id";

    private final JdbcTemplate jdbc;

    public PointShopItemStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 품목 읽기 ───────────────────────────────────────────────────────────

    public Optional<ItemRow> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE i.id = ?", PointShopItemStore::itemOf, id).stream().findFirst();
    }

    /** 고치기 전에 — 행을 잠근다(같은 품목의 동시 저장이 전→후 감사를 어긋나게 하지 않게) */
    public Optional<ItemRow> findForUpdate(long id) {
        jdbc.query("SELECT id FROM point_shop_items WHERE id = ? FOR UPDATE", rs -> {
        }, id);
        return find(id);
    }

    public Optional<ItemRow> findByCode(String code) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE i.code = ?", PointShopItemStore::itemOf, code).stream().findFirst();
    }

    /**
     * 목록 — 순서 → ID 차례.
     *
     * @param kind        이 종류만(null = 전부)
     * @param excludeKind 이 종류 빼고(null = 빼지 않음)
     */
    public List<ItemRow> list(PointShopItemKind kind, PointShopItemKind excludeKind) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + FROM + " WHERE true");
        List<Object> args = new ArrayList<>();
        if (kind != null) {
            sql.append(" AND i.kind = ?");
            args.add(kind.name());
        }
        if (excludeKind != null) {
            sql.append(" AND i.kind <> ?");
            args.add(excludeKind.name());
        }
        return jdbc.query(sql + ORDER, PointShopItemStore::itemOf, args.toArray());
    }

    public List<ItemRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE i.id IN (" + marks(ids.size()) + ")" + ORDER, PointShopItemStore::itemOf, ids.toArray());
    }

    /** 아바타 ID → 아바타 품목 */
    public Map<String, ItemRow> avatarItems() {
        Map<String, ItemRow> out = new HashMap<>();
        list(PointShopItemKind.PROFILE_AVATAR, null).forEach(row -> out.put(row.avatarId(), row));
        return out;
    }

    public boolean codeExists(String code) {
        return !jdbc.queryForList("SELECT 1 FROM point_shop_items WHERE code = ?", Integer.class, code).isEmpty();
    }

    /** 품목마다 판 수(환불한 구매는 빼고) */
    public Map<Long, Integer> soldCounts(Collection<Long> itemIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (itemIds.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT item_id, count(*) FROM point_shop_purchases WHERE status = 'COMPLETED' AND item_id IN (" + marks(itemIds.size()) + ") GROUP BY item_id",
                rs -> {
                    out.put(rs.getLong(1), rs.getInt(2));
                }, itemIds.toArray());
        return out;
    }

    // ── 품목 쓰기 ───────────────────────────────────────────────────────────

    public void insert(long id, ItemValues v, long operatorId, Instant now) {
        jdbc.update("INSERT INTO point_shop_items (id, code, kind, usage_type, name, name_en, description, description_en, color, border_style, avatar_id, price_point, "
                + "duration_days, stock, sale_start_at, sale_end_at, active, sort_order, created_by, updated_by, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, v.code(), v.kind().name(), v.usageType().name(), v.name(), v.nameEn(),
                v.description(), v.descriptionEn(), v.color(), v.borderStyle(), v.avatarId(), v.price(), v.durationDays(), v.stock(), ts(v.saleStartAt()),
                ts(v.saleEndAt()), v.active(), v.sortOrder(), operatorId, operatorId, ts(now), ts(now));
    }

    /** 바꿀 수 있는 칸을 모두 바꾼다(정체 칸 code·kind·avatar_id 는 그대로 — 트리거도 막는다) */
    public void update(long id, ItemValues v, long operatorId, Instant now) {
        jdbc.update("UPDATE point_shop_items SET usage_type = ?, name = ?, name_en = ?, description = ?, description_en = ?, color = ?, border_style = ?, price_point = ?, "
                + "duration_days = ?, stock = ?, sale_start_at = ?, sale_end_at = ?, active = ?, sort_order = ?, updated_by = ?, updated_at = ? WHERE id = ?",
                v.usageType().name(), v.name(), v.nameEn(), v.description(), v.descriptionEn(), v.color(), v.borderStyle(), v.price(), v.durationDays(), v.stock(),
                ts(v.saleStartAt()), ts(v.saleEndAt()), v.active(), v.sortOrder(), operatorId, ts(now), id);
    }

    /**
     * 재고를 하나 뺀다(한정 재고만) — 남은 재고가 있을 때만 한 문장으로. 동시에 마지막 하나를 사면 먼저 잠근 쪽만 이기고 나머지는 행 잠금이 풀린 뒤 조건을 다시 봐 0 줄이다.
     *
     * @return 뺐으면 true(무제한 품목은 늘 true — 바꾸지 않는다)
     */
    public boolean takeStock(long id) {
        if (jdbc.update("UPDATE point_shop_items SET stock = stock - 1 WHERE id = ? AND stock IS NOT NULL AND stock > 0", id) == 1) {
            return true;
        }
        return !jdbc.queryForList("SELECT 1 FROM point_shop_items WHERE id = ? AND stock IS NULL", Integer.class, id).isEmpty();
    }

    /** 환불한 구매의 재고를 되돌린다(지금 한정 재고인 품목만 — 무제한으로 바뀌었으면 그대로) */
    public void restoreStock(long id) {
        jdbc.update("UPDATE point_shop_items SET stock = stock + 1 WHERE id = ? AND stock IS NOT NULL", id);
    }

    /** 아바타 품목 묶음 가격 — 범위 안에서 값이 다른 줄만 바꾼다. 바꾼 줄 수 */
    public int setAvatarPrice(Collection<Long> ids, int price, long operatorId, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>(List.of(price, operatorId, ts(now), price));
        args.addAll(ids);
        return jdbc.update("UPDATE point_shop_items SET price_point = ?, updated_by = ?, updated_at = ? WHERE kind = 'PROFILE_AVATAR' AND price_point <> ? AND id IN ("
                + marks(ids.size()) + ")", args.toArray());
    }

    /** 아바타 품목 묶음 판매 켜기·끄기 — 값이 다른 줄만. 바꾼 줄 수 */
    public int setAvatarActive(Collection<Long> ids, boolean active, long operatorId, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>(List.of(active, operatorId, ts(now), active));
        args.addAll(ids);
        return jdbc.update("UPDATE point_shop_items SET active = ?, updated_by = ?, updated_at = ? WHERE kind = 'PROFILE_AVATAR' AND active <> ? AND id IN ("
                + marks(ids.size()) + ")", args.toArray());
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    /** 셀프 환불 기한 일수(행이 없으면 기본값) */
    public int refundDays() {
        List<Integer> rows = jdbc.queryForList("SELECT refund_days FROM point_shop_settings WHERE id = ?", Integer.class, PointShopSettingEntity.ROW_ID);
        return rows.isEmpty() ? PointShopSettingEntity.DEFAULT_REFUND_DAYS : rows.get(0);
    }

    public SettingsRow settings() {
        List<SettingsRow> rows = jdbc.query("SELECT s.refund_days, s.updated_at, s.updated_by, o.operator_name FROM point_shop_settings s "
                + "LEFT JOIN operators o ON o.id = s.updated_by WHERE s.id = ?", (rs, i) -> new SettingsRow(rs.getInt(1), instant(rs, 2), nullableLong(rs, 3),
                rs.getString(4)), PointShopSettingEntity.ROW_ID);
        return rows.isEmpty() ? new SettingsRow(PointShopSettingEntity.DEFAULT_REFUND_DAYS, null, null, null) : rows.get(0);
    }

    /** 설정 행을 잠근 뒤 지금 값(같은 화면의 동시 저장이 전→후 감사를 어긋나게 하지 않게) */
    public int lockRefundDays() {
        jdbc.update("INSERT INTO point_shop_settings (id) VALUES (?) ON CONFLICT (id) DO NOTHING", PointShopSettingEntity.ROW_ID);
        return jdbc.queryForObject("SELECT refund_days FROM point_shop_settings WHERE id = ? FOR UPDATE", Integer.class, PointShopSettingEntity.ROW_ID);
    }

    public void updateRefundDays(int days, long operatorId, Instant now) {
        jdbc.update("UPDATE point_shop_settings SET refund_days = ?, updated_by = ?, updated_at = ? WHERE id = ?", days, operatorId, ts(now), PointShopSettingEntity.ROW_ID);
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    private static ItemRow itemOf(ResultSet rs, int i) throws SQLException {
        return new ItemRow(rs.getLong(1), rs.getString(2), PointShopItemKind.valueOf(rs.getString(3)), PointShopUsageType.valueOf(rs.getString(4)), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getInt(12), nullableInt(rs, 13),
                nullableInt(rs, 14), instant(rs, 15), instant(rs, 16), rs.getBoolean(17), rs.getInt(18), nullableLong(rs, 19), rs.getString(20), instant(rs, 21),
                nullableLong(rs, 22), rs.getString(23), instant(rs, 24));
    }

    /** 자리표시자 n 개({@code ?, ?, …}) */
    static String marks(int n) {
        return String.join(", ", java.util.Collections.nCopies(n, "?"));
    }

    static Timestamp ts(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }

    static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp at = rs.getTimestamp(column);
        return at == null ? null : at.toInstant();
    }

    static Integer nullableInt(ResultSet rs, int column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
