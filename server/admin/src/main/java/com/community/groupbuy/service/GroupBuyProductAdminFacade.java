package com.community.groupbuy.service;

import com.community.commerce.image.GroupBuyImageUploader;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.NoticeDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.dto.request.GroupBuyAdminRequest;
import com.community.groupbuy.dto.response.GroupBuyAdminView;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyNoticeStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductRow;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductSkuRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.media.service.ImageStorage;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 공구 상품·카테고리·옵션 종류(admin-web {@code useGroupBuyProductApi}, 결정 89·「상품 단품 목록이 정본」·「옵션 종류 코드」). 업무는 shared {@link GroupBuyProductService}·
 * {@link GroupBuyNoticeService}. 상품 저장은 상품 → 고시 차례(두 트랜잭션 — 고시가 막히면 상품은 저장된 채 409 를 돌려 화면이 고시만 다시 보낸다).
 * 상품 설명은 서버가 정화한다({@link GroupBuyDescriptionHtml} — 결정 2). 공급가는 {@code commerce.product.price}·{@code commerce.settlement.read} 만 본다.
 */
@Service
public class GroupBuyProductAdminFacade {

    private final JdbcTemplate jdbc;
    private final GroupBuyProductService service;
    private final GroupBuyProductStore store;
    private final GroupBuyNoticeService notices;
    private final GroupBuyNoticeStore noticeStore;
    private final ImageStorage storage;
    private final TransactionTemplate tx;

    public GroupBuyProductAdminFacade(DataSource dataSource, GroupBuyProductService service, GroupBuyProductStore store, GroupBuyNoticeService notices,
                                      GroupBuyNoticeStore noticeStore, ImageStorage storage, PlatformTransactionManager transactions) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.service = service;
        this.store = store;
        this.notices = notices;
        this.noticeStore = noticeStore;
        this.storage = storage;
        this.tx = new TransactionTemplate(transactions);
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    public List<GroupBuyAdminView.Category> categories() {
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query("SELECT category_id, count(*) FROM group_buy_products GROUP BY category_id", rs -> {
            counts.put(rs.getLong(1), rs.getInt(2));
        });
        return service.categories().stream().map(c -> new GroupBuyAdminView.Category(Long.toString(c.id()), c.parentId() == null ? null : Long.toString(c.parentId()),
                c.name(), c.sortOrder(), c.hidden(), counts.getOrDefault(c.id(), 0))).toList();
    }

    /**
     * 통째로 저장 — 배열 차례가 같은 부모 안의 차례. 새 줄은 {@code id} 없이 {@code tempKey}, {@code parent} 는 부모의 ID 또는 새 부모의 {@code tempKey}. 목록에 없는 카테고리는 지운다
     * (상품·아래 단이 있으면 409 {@code GROUP_BUY_CATEGORY_IN_USE}). 2단까지.
     */
    public List<GroupBuyAdminView.Category> saveCategories(AdminActor actor, List<GroupBuyAdminRequest.Category> items) {
        List<GroupBuyAdminRequest.Category> in = items == null ? List.of() : items;
        tx.executeWithoutResult(status -> {
            Map<String, Long> keyToId = new HashMap<>();
            Map<String, Integer> order = new HashMap<>();
            List<Long> keep = new ArrayList<>();
            // 위 단 먼저, 그다음 아래 단(새 부모의 ID 가 먼저 생겨야 한다)
            for (int pass = 0; pass < 2; pass++) {
                for (GroupBuyAdminRequest.Category c : in) {
                    boolean top = c.parent() == null || c.parent().isBlank();
                    if ((pass == 0) != top) {
                        continue;
                    }
                    Long parentId = top ? null : keyToId.containsKey(c.parent()) ? keyToId.get(c.parent()) : AdminIds.field(c.parent(), "parent");
                    String scope = parentId == null ? "" : parentId.toString();
                    int sort = order.merge(scope, 1, Integer::sum) - 1;
                    long id;
                    if (c.id() == null || c.id().isBlank()) {
                        id = service.createCategory(c.name(), parentId, sort, actor.operatorId(), actor.ip());
                        if (Boolean.TRUE.equals(c.hidden())) {
                            service.updateCategory(id, c.name(), sort, true, actor.operatorId(), actor.ip());
                        }
                    } else {
                        id = AdminIds.field(c.id(), "id");
                        jdbc.update("UPDATE group_buy_categories SET parent_id = ? WHERE id = ? AND parent_id IS DISTINCT FROM ?", parentId, id, parentId);
                        service.updateCategory(id, c.name(), sort, Boolean.TRUE.equals(c.hidden()), actor.operatorId(), actor.ip());
                    }
                    keep.add(id);
                    if (c.tempKey() != null) {
                        keyToId.put(c.tempKey(), id);
                    }
                    keyToId.put(Long.toString(id), id);
                }
            }
            for (GroupBuyProductStore.CategoryRow existing : service.categories()) {
                if (keep.contains(existing.id())) {
                    continue;
                }
                Integer used = jdbc.queryForObject("SELECT (SELECT count(*) FROM group_buy_products WHERE category_id = ?) + (SELECT count(*) FROM group_buy_categories "
                        + "WHERE parent_id = ? AND id <> ALL (?::bigint[]))", Integer.class, existing.id(), existing.id(), keep.toArray(new Long[0]));
                if (used != null && used > 0) {
                    throw GroupBuyException.categoryInUse(existing.name());
                }
                jdbc.update("DELETE FROM group_buy_categories WHERE id = ?", existing.id());
            }
        });
        return categories();
    }

    // ── 옵션 종류 ───────────────────────────────────────────────────────────

    public List<GroupBuyAdminView.OptionType> optionTypes() {
        return service.optionTypes().stream().map(t -> new GroupBuyAdminView.OptionType(t.code(), t.name(), t.sortOrder(), t.hidden(), t.productCount(), t.usedValues()))
                .toList();
    }

    public List<GroupBuyAdminView.OptionType> saveOptionTypes(AdminActor actor, List<GroupBuyAdminRequest.OptionType> items) {
        List<GroupBuyProductService.OptionTypeDraft> drafts = new ArrayList<>();
        List<GroupBuyAdminRequest.OptionType> in = items == null ? List.of() : items;
        for (int i = 0; i < in.size(); i++) {
            GroupBuyAdminRequest.OptionType t = in.get(i);
            drafts.add(new GroupBuyProductService.OptionTypeDraft(t.code(), t.name(), i, Boolean.TRUE.equals(t.hidden())));
        }
        service.saveOptionTypes(drafts, actor.operatorId(), actor.ip());
        return optionTypes();
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    public GroupBuyAdminView.Page<GroupBuyAdminView.ProductRow> list(String q, String categoryId, int page, int size) {
        int s = Math.min(100, Math.max(1, size));
        int p = Math.max(1, page);
        StringBuilder where = new StringBuilder(" FROM group_buy_products p WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND p.name ILIKE ?");
            args.add("%" + q.strip().replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (categoryId != null && !categoryId.isBlank()) {
            long c = AdminIds.field(categoryId, "categoryId");
            where.append(" AND (p.category_id = ? OR p.category_id IN (SELECT id FROM group_buy_categories WHERE parent_id = ?))");
            args.add(c);
            args.add(c);
        }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(s);
        args.add((p - 1) * s);
        List<GroupBuyAdminView.ProductRow> rows = jdbc.query("SELECT p.id, p.name, p.category_id, p.updated_at, (SELECT count(*) FROM group_buy_product_skus k "
                + "WHERE k.product_id = p.id), (SELECT count(*) FROM group_buys g WHERE g.product_id = p.id), (SELECT count(*) FROM group_buys g WHERE g.product_id = p.id "
                + "AND g.status IN ('RECRUITING', 'CLOSING')), (SELECT COALESCE(i.thumb_key, i.image_key) FROM group_buy_product_images i WHERE i.product_id = p.id "
                + "ORDER BY i.sort_order LIMIT 1)" + where + " ORDER BY p.updated_at DESC, p.id DESC LIMIT ? OFFSET ?", (rs, i) -> new GroupBuyAdminView.ProductRow(
                Long.toString(rs.getLong(1)), rs.getString(2), Long.toString(rs.getLong(3)), categoryPath(rs.getLong(3)),
                rs.getString(8) == null ? null : storage.publicUrl(rs.getString(8)), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getTimestamp(4).toInstant()),
                args.toArray());
        return new GroupBuyAdminView.Page<>(rows, total == null ? 0 : total);
    }

    public GroupBuyAdminView.ProductDetail detail(AdminActor actor, String idText) {
        long id = AdminIds.parse(idText, GroupBuyException::productNotFound);
        ProductRow p = service.product(id);
        boolean showSupply = actor.root() || actor.can(Permission.COMMERCE_PRODUCT_PRICE) || actor.can(Permission.COMMERCE_SETTLEMENT_READ);
        List<GroupBuyStore.AxisRow> axes = service.axes(id);
        Map<Long, String> valueNames = new HashMap<>();
        axes.forEach(a -> a.values().forEach(v -> valueNames.put(v.id(), v.value())));
        List<ProductSkuRow> skus = service.skus(id);
        List<GroupBuyAdminView.ProductUse> uses = jdbc.query("SELECT g.id, COALESCE(NULLIF(g.title, ''), p.name), g.status, g.round_no FROM group_buys g "
                + "JOIN group_buy_products p ON p.id = g.product_id WHERE g.product_id = ? OR g.id IN (SELECT s.group_buy_id FROM group_buy_skus s "
                + "JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id WHERE ps.product_id = ? AND s.kind = 'ADDON') ORDER BY g.created_at DESC",
                (rs, i) -> new GroupBuyAdminView.ProductUse(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), rs.getInt(4)), id, id);
        Boolean locked = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM group_buys g WHERE g.published_at IS NOT NULL AND g.status IN ('DRAFT', 'RECRUITING', 'CLOSING') "
                + "AND (g.product_id = ? OR g.id IN (SELECT s.group_buy_id FROM group_buy_skus s JOIN group_buy_product_skus ps ON ps.id = s.product_sku_id "
                + "WHERE ps.product_id = ?)))", Boolean.class, id, id);
        return new GroupBuyAdminView.ProductDetail(Long.toString(p.id()), p.name(), Long.toString(p.categoryId()), categoryPath(p.categoryId()), nz(p.summary()),
                nz(p.spec()), nz(p.description()), store.images(id).stream().map(i -> new GroupBuyAdminView.Image(i.imageKey(), storage.publicUrl(i.imageKey()),
                storage.publicUrl(GroupBuyAdminFacade.thumb(i)))).toList(), p.normalPrice() == null ? 0 : p.normalPrice(), p.normalPriceBasis(),
                nz(p.normalPriceSource()), nz(p.withdrawalRestriction()), axes.stream().map(GroupBuyStore.AxisRow::code).toList(),
                axes.stream().map(GroupBuyStore.AxisRow::name).toList(), GroupBuyAdminFacade.axes(axes),
                skus.stream().map(k -> new GroupBuyAdminView.ProductSku(Long.toString(k.id()), k.valueIds().stream().map(v -> valueNames.getOrDefault(v, "")).toList(),
                        k.valueIds().stream().map(String::valueOf).toList(), k.name(), showSupply ? k.defaultSupplyPrice() : null, k.onSale())).toList(),
                noticeStore.notices(id).stream().map(n -> GroupBuyAdminFacade.notice(n, Map.of())).toList(), Boolean.TRUE.equals(locked), uses, p.version(),
                p.updatedAt());
    }

    public GroupBuyAdminView.ProductDetail create(AdminActor actor, GroupBuyAdminRequest.Product body) {
        long id = service.create(draft(body), actor.operatorId(), actor.ip());
        saveNotices(actor, id, body);
        return detail(actor, Long.toString(id));
    }

    public GroupBuyAdminView.ProductDetail update(AdminActor actor, String idText, GroupBuyAdminRequest.Product body) {
        long id = AdminIds.parse(idText, GroupBuyException::productNotFound);
        service.update(id, body == null || body.version() == null ? -1 : body.version(), draft(body), actor.operatorId(), actor.ip());
        saveNotices(actor, id, body);
        return detail(actor, idText);
    }

    /** 추가 구성상품으로 고를 수 있는 다른 상품의 단품 */
    public List<GroupBuyAdminView.AddonSource> addonSources(AdminActor actor, String except) {
        Long skip = except == null || except.isBlank() ? null : AdminIds.field(except, "except");
        boolean showSupply = actor.root() || actor.can(Permission.COMMERCE_PRODUCT_PRICE) || actor.can(Permission.COMMERCE_SETTLEMENT_READ);
        return jdbc.query("SELECT k.id, p.id, p.name, k.name, (SELECT count(*) FROM group_buy_product_skus x WHERE x.product_id = p.id), k.default_supply_price, "
                + "(SELECT COALESCE(i.thumb_key, i.image_key) FROM group_buy_product_images i WHERE i.product_id = p.id ORDER BY i.sort_order LIMIT 1) "
                + "FROM group_buy_product_skus k JOIN group_buy_products p ON p.id = k.product_id WHERE (?::bigint IS NULL OR p.id <> ?) ORDER BY p.name, k.sort_order",
                (rs, i) -> new GroupBuyAdminView.AddonSource(Long.toString(rs.getLong(1)), Long.toString(rs.getLong(2)), rs.getString(3),
                        rs.getInt(5) <= 1 ? rs.getString(3) : rs.getString(3) + " (" + rs.getString(4) + ")", rs.getString(7) == null ? null : storage.publicUrl(rs.getString(7)),
                        showSupply ? (Long) rs.getObject(6) : null), skip, skip);
    }

    // ── 안 ──────────────────────────────────────────────────────────────────

    private void saveNotices(AdminActor actor, long productId, GroupBuyAdminRequest.Product body) {
        if (body == null || body.notices() == null) {
            return;
        }
        List<NoticeDraft> drafts = new ArrayList<>();
        for (GroupBuyAdminRequest.Notice n : body.notices()) {
            List<NoticeDraft.Value> values = new ArrayList<>();
            if (n.values() != null) {
                for (GroupBuyAdminRequest.NoticeValue v : n.values()) {
                    values.add(Boolean.TRUE.equals(v.custom()) ? NoticeDraft.Value.custom(v.name(), v.value(), v.sortOrder())
                            : new NoticeDraft.Value(v.itemKey(), v.value(), Boolean.TRUE.equals(v.unavailable()), v.unavailableReason(), false, null, null));
                }
            }
            drafts.add(new NoticeDraft(n.categoryCode(), n.title(), n.skuNames() == null ? List.of() : n.skuNames(), values));
        }
        notices.save(productId, drafts, actor.operatorId(), actor.ip());
    }

    private static ProductDraft draft(GroupBuyAdminRequest.Product body) {
        GroupBuyAdminRequest.Product b = body == null ? new GroupBuyAdminRequest.Product(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
                : body;
        List<ProductDraft.SkuDraft> skus = new ArrayList<>();
        if (b.skus() != null) {
            for (GroupBuyAdminRequest.ProductSku s : b.skus()) {
                skus.add(new ProductDraft.SkuDraft(AdminIds.optional(s.skuId(), "skus"), s.name(), s.values() == null ? List.of() : s.values(), s.defaultSupplyPrice(),
                        !Boolean.FALSE.equals(s.onSale())));
            }
        }
        List<GroupBuyDraft.ImageRef> images = new ArrayList<>();
        if (b.images() != null) {
            for (String key : b.images()) {
                if (!GroupBuyImageUploader.ownKey(key)) {
                    throw ValidationException.of("images", new MessageRef("groupbuy.image.unknown"));
                }
                images.add(new GroupBuyDraft.ImageRef(key, GroupBuyImageUploader.thumbKey(key)));
            }
        }
        return new ProductDraft(AdminIds.optional(b.categoryId(), "categoryId"), b.name(), b.summary(), b.spec(), b.description(), b.normalPrice(), b.normalPriceBasis(),
                b.normalPriceSource(), b.withdrawalRestriction(), b.optionTypeCodes() == null ? List.of() : b.optionTypeCodes(), skus, images);
    }

    private String categoryPath(long categoryId) {
        return jdbc.query("SELECT CASE WHEN pc.name IS NULL THEN c.name ELSE pc.name || ' › ' || c.name END FROM group_buy_categories c "
                + "LEFT JOIN group_buy_categories pc ON pc.id = c.parent_id WHERE c.id = ?", (rs, i) -> rs.getString(1), categoryId).stream().findFirst().orElse("");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 사용하지 않는 칸 경고 막기 */
    static Map<String, Object> none() {
        return new LinkedHashMap<>();
    }
}
