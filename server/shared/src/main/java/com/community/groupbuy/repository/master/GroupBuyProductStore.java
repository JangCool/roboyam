package com.community.groupbuy.repository.master;

import static com.community.groupbuy.repository.master.GroupBuyStore.instant;
import static com.community.groupbuy.repository.master.GroupBuyStore.longArray;
import static com.community.groupbuy.repository.master.GroupBuyStore.longs;
import static com.community.groupbuy.repository.master.GroupBuyStore.nLong;
import static com.community.groupbuy.repository.master.GroupBuyStore.ts;

import com.community.groupbuy.repository.master.GroupBuyStore.AxisRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ImageRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ValueRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 상품 마스터(결정 89) — 카테고리({@code group_buy_categories})·상품({@code group_buy_products})·상품 이미지·옵션 축/값·상품 단품 읽기·쓰기.
 */
@Repository
public class GroupBuyProductStore {

    /** 카테고리 한 줄 */
    public record CategoryRow(long id, Long parentId, String name, int sortOrder, boolean hidden) {
    }

    /** 상품 한 줄 */
    public record ProductRow(long id, long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                             String normalPriceSource, String withdrawalRestriction, int version, Instant createdAt, Instant updatedAt) {
    }

    /** 상품 단품 — {@code valueIds} 축 값 조합(축 차례), {@code defaultSupplyPrice} null = 기본 공급가 없음 */
    public record ProductSkuRow(long id, long productId, List<Long> valueIds, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
    }

    /** 새 상품 칸 */
    public record NewProduct(long categoryId, String name, String summary, String spec, String description, Long normalPrice, String normalPriceBasis,
                             String normalPriceSource, String withdrawalRestriction) {
    }

    /** 공구 「모집 중」 — 이 상태의 공구가 상품을 쓰면 이름·정상가·구성이 잠긴다(공개된 예정·모집 중·집계 중) */
    private static final String ACTIVE = "g.published_at IS NOT NULL AND g.status IN ('DRAFT', 'RECRUITING', 'CLOSING')";

    private final JdbcTemplate jdbc;

    public GroupBuyProductStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    public Optional<CategoryRow> category(long id) {
        return jdbc.query("SELECT id, parent_id, name, sort_order, hidden FROM group_buy_categories WHERE id = ?", GroupBuyProductStore::category, id).stream()
                .findFirst();
    }

    /** 모든 카테고리(최상위 먼저, 차례대로) */
    public List<CategoryRow> categories() {
        return jdbc.query("SELECT id, parent_id, name, sort_order, hidden FROM group_buy_categories ORDER BY parent_id NULLS FIRST, sort_order, id",
                GroupBuyProductStore::category);
    }

    public void insertCategory(long id, Long parentId, String name, int sortOrder, Instant now) {
        jdbc.update("INSERT INTO group_buy_categories (id, parent_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)", id, parentId, name, sortOrder,
                ts(now), ts(now));
    }

    public boolean updateCategory(long id, String name, int sortOrder, boolean hidden, Instant now) {
        return jdbc.update("UPDATE group_buy_categories SET name = ?, sort_order = ?, hidden = ?, updated_at = ? WHERE id = ?", name, sortOrder, hidden, ts(now), id) == 1;
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    private static final String PRODUCT_COLUMNS = "id, category_id, name, summary, spec, description, normal_price, normal_price_basis, normal_price_source, "
            + "withdrawal_restriction, version, created_at, updated_at";

    public Optional<ProductRow> find(long id) {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + " FROM group_buy_products WHERE id = ?", GroupBuyProductStore::product, id).stream().findFirst();
    }

    /** 상품 행을 잠근다(구성 바꾸기 ∥ 그 상품을 쓰는 공구의 모집 시작 — 모집 시작도 이 행을 잠근다) */
    public Optional<ProductRow> lock(long id) {
        return jdbc.query("SELECT " + PRODUCT_COLUMNS + " FROM group_buy_products WHERE id = ? FOR UPDATE", GroupBuyProductStore::product, id).stream().findFirst();
    }

    public void insert(long id, NewProduct p, Long operatorId, Instant now) {
        jdbc.update("INSERT INTO group_buy_products (id, category_id, name, summary, spec, description, normal_price, normal_price_basis, normal_price_source, "
                        + "withdrawal_restriction, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, p.categoryId(), p.name(), p.summary(),
                p.spec(), p.description(), p.normalPrice(), p.normalPriceBasis(), p.normalPriceSource(), p.withdrawalRestriction(), operatorId, ts(now), ts(now));
    }

    /** 모든 칸을 고친다(낙관 잠금) */
    public boolean update(long id, int version, NewProduct p, Instant now) {
        return jdbc.update("UPDATE group_buy_products SET category_id = ?, name = ?, summary = ?, spec = ?, description = ?, normal_price = ?, normal_price_basis = ?, "
                        + "normal_price_source = ?, withdrawal_restriction = ?, version = version + 1, updated_at = ? WHERE id = ? AND version = ?", p.categoryId(), p.name(),
                p.summary(), p.spec(), p.description(), p.normalPrice(), p.normalPriceBasis(), p.normalPriceSource(), p.withdrawalRestriction(), ts(now), id, version) == 1;
    }

    /** 이 상품(공구 상품 또는 추가 구성상품)을 쓰는 「모집 중」 공구가 있는가 */
    public boolean usedByActiveGroupBuy(long productId) {
        return !activeGroupBuys(productId).isEmpty();
    }

    /** 이 상품을 쓰는 「모집 중」 공구 */
    public List<Long> activeGroupBuys(long productId) {
        return usingGroupBuys(productId, ACTIVE);
    }

    /** 이 상품을 쓰는, 모집을 시작했고 끝나지 않은 공구(상품 내용을 고치면 그 공구 수정 이력에 사본 — 결정 89) */
    public List<Long> runningGroupBuys(long productId) {
        return usingGroupBuys(productId, "g.status IN ('RECRUITING', 'CLOSING', 'CLOSED_SUCCESS', 'ORDERED', 'SHIPPING')");
    }

    /** 이 상품을 쓰는, 공개됐고 끝나지 않은 공구(고시를 비울 수 없다) */
    public List<Long> publishedGroupBuys(long productId) {
        return usingGroupBuys(productId, "g.published_at IS NOT NULL AND g.status NOT IN ('COMPLETED', 'CANCELED', 'CLOSED_FAILED')");
    }

    private List<Long> usingGroupBuys(long productId, String condition) {
        return jdbc.queryForList("SELECT g.id FROM group_buys g WHERE " + condition + " AND (g.product_id = ? OR EXISTS (SELECT 1 FROM group_buy_skus s "
                + "JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id WHERE s.group_buy_id = g.id AND ps.product_id = ?)) ORDER BY g.id", Long.class, productId, productId);
    }

    // ── 이미지 ──────────────────────────────────────────────────────────────

    public List<ImageRow> images(long productId) {
        return jdbc.query("SELECT image_key, thumb_key, sort_order FROM group_buy_product_images WHERE product_id = ? ORDER BY sort_order",
                (rs, i) -> new ImageRow(rs.getString(1), rs.getString(2), rs.getInt(3)), productId);
    }

    public void replaceImages(long productId, List<ImageRow> images, List<Long> ids, Instant now) {
        jdbc.update("DELETE FROM group_buy_product_images WHERE product_id = ?", productId);
        for (int i = 0; i < images.size(); i++) {
            ImageRow image = images.get(i);
            jdbc.update("INSERT INTO group_buy_product_images (id, product_id, image_key, thumb_key, sort_order, created_at) VALUES (?, ?, ?, ?, ?, ?)", ids.get(i),
                    productId, image.imageKey(), image.thumbKey(), i, ts(now));
        }
    }

    // ── 옵션 축·값·상품 단품 ────────────────────────────────────────────────

    /** 옵션 축과 값(차례대로) */
    public List<AxisRow> axes(long productId) {
        List<AxisRow> axes = jdbc.query("SELECT id, name, sort_order FROM group_buy_option_axes WHERE product_id = ? ORDER BY sort_order, id",
                (rs, i) -> new AxisRow(rs.getLong(1), rs.getString(2), rs.getInt(3), new ArrayList<>()), productId);
        for (AxisRow axis : axes) {
            axis.values().addAll(jdbc.query("SELECT id, axis_id, value, sort_order FROM group_buy_option_values WHERE axis_id = ? ORDER BY sort_order, id",
                    (rs, i) -> new ValueRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), axis.id()));
        }
        return axes;
    }

    public void insertAxis(long id, long productId, int sortOrder, String name) {
        jdbc.update("INSERT INTO group_buy_option_axes (id, product_id, sort_order, name) VALUES (?, ?, ?, ?)", id, productId, sortOrder, name);
    }

    public void deleteAxes(long productId) {
        jdbc.update("DELETE FROM group_buy_option_axes WHERE product_id = ?", productId);
    }

    public void insertValue(long id, long axisId, long productId, int sortOrder, String value) {
        jdbc.update("INSERT INTO group_buy_option_values (id, axis_id, product_id, sort_order, value) VALUES (?, ?, ?, ?, ?)", id, axisId, productId, sortOrder, value);
    }

    public void moveValue(long id, int sortOrder) {
        jdbc.update("UPDATE group_buy_option_values SET sort_order = ? WHERE id = ?", sortOrder, id);
    }

    public void deleteValue(long id) {
        jdbc.update("DELETE FROM group_buy_option_values WHERE id = ?", id);
    }

    public List<ProductSkuRow> skus(long productId) {
        return jdbc.query("SELECT id, product_id, value_ids, name, default_supply_price, on_sale, sort_order FROM group_buy_product_skus WHERE product_id = ? "
                + "ORDER BY sort_order, id", GroupBuyProductStore::sku, productId);
    }

    public Optional<ProductSkuRow> sku(long id) {
        return jdbc.query("SELECT id, product_id, value_ids, name, default_supply_price, on_sale, sort_order FROM group_buy_product_skus WHERE id = ?",
                GroupBuyProductStore::sku, id).stream().findFirst();
    }

    public void insertSku(long id, long productId, List<Long> valueIds, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
        jdbc.update("INSERT INTO group_buy_product_skus (id, product_id, value_ids, name, default_supply_price, on_sale, sort_order) VALUES (?, ?, ?::bigint[], ?, ?, ?, ?)",
                id, productId, longArray(valueIds), name, defaultSupplyPrice, onSale, sortOrder);
    }

    public void updateSku(long id, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
        jdbc.update("UPDATE group_buy_product_skus SET name = ?, default_supply_price = ?, on_sale = ?, sort_order = ? WHERE id = ?", name, defaultSupplyPrice, onSale,
                sortOrder, id);
    }

    public void deleteSku(long id) {
        jdbc.update("DELETE FROM group_buy_product_skus WHERE id = ?", id);
    }

    /** 공개됐거나 모집을 시작한 공구의 단품이 가리키는 상품 단품인가(지울 수 없다 — 공개 전 DRAFT 공구는 상품 구성을 따라간다) */
    public boolean skuInUse(long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM group_buy_skus s JOIN group_buys g ON g.id = s.group_buy_id WHERE s.product_sku_id = ? "
                + "AND (g.published_at IS NOT NULL OR g.status <> 'DRAFT')", Integer.class, id);
        return n != null && n > 0;
    }

    /** 공개 전 DRAFT 공구의 단품 중 이 상품 단품을 가리키는 것을 지운다(상품에서 없앤 단품) */
    public void deleteDraftGroupSkus(long productSkuId) {
        jdbc.update("DELETE FROM group_buy_skus s USING group_buys g WHERE g.id = s.group_buy_id AND s.product_sku_id = ? AND g.status = 'DRAFT' "
                + "AND g.published_at IS NULL", productSkuId);
    }

    /** 이 상품을 파는 공개 전 DRAFT 공구(상품에 더한 단품을 MAIN 단품으로 더한다) */
    public List<Long> draftGroupBuys(long productId) {
        return jdbc.queryForList("SELECT id FROM group_buys WHERE product_id = ? AND status = 'DRAFT' AND published_at IS NULL ORDER BY id", Long.class, productId);
    }

    // ── 매핑 ────────────────────────────────────────────────────────────────

    static CategoryRow category(ResultSet rs, int i) throws SQLException {
        return new CategoryRow(rs.getLong("id"), nLong(rs, "parent_id"), rs.getString("name"), rs.getInt("sort_order"), rs.getBoolean("hidden"));
    }

    static ProductRow product(ResultSet rs, int i) throws SQLException {
        return new ProductRow(rs.getLong("id"), rs.getLong("category_id"), rs.getString("name"), rs.getString("summary"), rs.getString("spec"),
                rs.getString("description"), nLong(rs, "normal_price"), rs.getString("normal_price_basis"), rs.getString("normal_price_source"),
                rs.getString("withdrawal_restriction"), rs.getInt("version"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("updated_at")));
    }

    static ProductSkuRow sku(ResultSet rs, int i) throws SQLException {
        return new ProductSkuRow(rs.getLong("id"), rs.getLong("product_id"), longs(rs.getArray("value_ids")), rs.getString("name"), nLong(rs, "default_supply_price"),
                rs.getBoolean("on_sale"), rs.getInt("sort_order"));
    }
}
