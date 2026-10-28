package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyProductStore;
import com.community.groupbuy.repository.master.GroupBuyProductStore.CategoryRow;
import com.community.groupbuy.repository.master.GroupBuyProductStore.NewProduct;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductRow;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductSkuRow;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.AxisRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ImageRow;
import com.community.groupbuy.repository.master.GroupBuyStore.ValueRow;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 상품 마스터·카테고리(결정 89 — 같은 상품을 차수·조건을 바꿔 반복해서 판다). 권한({@code commerce.product.manage}·조회 {@code commerce.product.read})은 관리자 컨트롤러(2b-4).
 *
 * <ul>
 *   <li><b>카테고리</b>: 2단(최상위·그 아래 하나), 차례·숨김. 상품마다 하나 필수.</li>
 *   <li><b>상품 수정</b>: 설명·이미지(요약·사양 포함)·카테고리·기본 공급가·기본 판매 여부는 언제든 — 진행 중 공구에도 바로 보이고 그 공구 수정 이력에 사본(정보 정정).
 *       이름·정상가·청약철회 제한·옵션 축/값·단품 구성은 그 상품을 쓰는(공구 상품 또는 추가 구성상품) 공구가 모집 중이면 409 {@code GROUP_BUY_LOCKED_FIELD}.
 *       구성을 바꿀 때 같은 이름의 축 값·같은 조합의 단품은 ID 를 유지하고(공구 단품·고시 연결이 그대로), 공개된 공구가 가리키는 단품을 없애면 409 {@code GROUP_BUY_PRODUCT_SKU_IN_USE}.
 *       공개 전 DRAFT 공구는 상품 구성을 따라간다(없앤 단품은 빠지고, 더한 단품은 기본값 — 추가금 0·한도 없음·기본 판매 여부·기본 공급가 — 으로 들어간다).</li>
 * </ul>
 */
@Service
public class GroupBuyProductService {

    public static final int NAME_MAX = 100;
    public static final int TEXT_MAX = 5000;
    public static final int DESCRIPTION_MAX = 200_000;
    public static final int IMAGES_MAX = 8;
    public static final int CATEGORY_NAME_MAX = 30;
    static final Set<String> PRICE_BASES = Set.of("OFFICIAL_STORE", "PREVIOUS_ROUND", "MARKET", "PREVIOUS_SALE");

    private final GroupBuyProductStore store;
    private final GroupBuyStore groups;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public GroupBuyProductService(GroupBuyProductStore store, GroupBuyStore groups, AuditLogService audit, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                                  ObjectProvider<Clock> clocks) {
        this.store = store;
        this.groups = groups;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 카테고리 ────────────────────────────────────────────────────────────

    /** 카테고리 등록 — {@code parentId} 를 주면 그 아래(최상위 아래만 — 2단) */
    public long createCategory(String name, Long parentId, int sortOrder, Long operatorId, String ip) {
        String n = categoryName(name);
        return tx.execute(status -> {
            if (parentId != null && store.category(parentId).filter(c -> c.parentId() == null).isEmpty()) {
                throw ValidationException.of("parentId", new MessageRef("groupbuy.admin.category.parentInvalid")); // 2단까지
            }
            long id = ids.nextId();
            store.insertCategory(id, parentId, n, sortOrder, now());
            record(operatorId, AuditActions.GROUPBUY_CATEGORY_SAVE, "GROUP_BUY_CATEGORY", id, ip, Map.of("name", n, "stage", "CREATE"));
            return id;
        });
    }

    /** 카테고리 이름·차례·숨김 */
    public void updateCategory(long id, String name, int sortOrder, boolean hidden, Long operatorId, String ip) {
        String n = categoryName(name);
        tx.executeWithoutResult(status -> {
            if (!store.updateCategory(id, n, sortOrder, hidden, now())) {
                throw ValidationException.of("categoryId", new MessageRef("groupbuy.admin.category.invalid"));
            }
            record(operatorId, AuditActions.GROUPBUY_CATEGORY_SAVE, "GROUP_BUY_CATEGORY", id, ip, Map.of("name", n, "hidden", hidden, "stage", "UPDATE"));
        });
    }

    public List<CategoryRow> categories() {
        return store.categories();
    }

    /** 회원 공구 목록(결정 89 카테고리 필터) — 최상위를 고르면 그 아래 단도, 숨긴 카테고리는 뺀다 */
    public List<Long> publicList(Long categoryId, int limit) {
        return groups.publicList(categoryId, Math.max(1, Math.min(limit, 100)));
    }

    private static String categoryName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > CATEGORY_NAME_MAX) {
            throw ValidationException.of("name", new MessageRef("groupbuy.admin.category.invalid"));
        }
        return name.strip();
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    public ProductRow product(long id) {
        return store.find(id).orElseThrow(GroupBuyException::productNotFound);
    }

    public List<ProductSkuRow> skus(long productId) {
        return store.skus(productId);
    }

    public List<AxisRow> axes(long productId) {
        return store.axes(productId);
    }

    /** 상품 등록 — 새 상품 ID */
    public long create(ProductDraft draft, Long operatorId, String ip) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        NewProduct p = validate(draft, f);
        GroupBuySkuPlan.ProductPlan plan = p == null ? null : GroupBuySkuPlan.product(draft, p.name(), f);
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        return tx.execute(status -> {
            Instant now = now();
            requireCategory(p.categoryId());
            long id = ids.nextId();
            store.insert(id, p, operatorId, now);
            sync(id, plan);
            replaceImages(id, draft.images(), now);
            record(operatorId, AuditActions.GROUPBUY_PRODUCT_SAVE, "GROUP_BUY_PRODUCT", id, ip, Map.of("stage", "CREATE", "name", p.name()));
            return id;
        });
    }

    /**
     * 상품 수정 — 설명·이미지는 언제든(진행 중 공구 수정 이력에 사본), 이름·정상가·청약철회 제한·옵션 구성은 모집 중인 공구가 쓰면 잠금.
     *
     * @throws GroupBuyException {@code VERSION_CONFLICT} · {@code LOCKED_FIELD} · {@code PRODUCT_SKU_IN_USE}
     */
    public void update(long id, int version, ProductDraft draft, Long operatorId, String ip) {
        Map<String, MessageRef> f = new LinkedHashMap<>();
        NewProduct p = validate(draft, f);
        GroupBuySkuPlan.ProductPlan plan = p == null ? null : GroupBuySkuPlan.product(draft, p.name(), f);
        if (!f.isEmpty()) {
            throw new ValidationException(f);
        }
        tx.executeWithoutResult(status -> {
            Instant now = now();
            ProductRow row = store.lock(id).orElseThrow(GroupBuyException::productNotFound);
            if (row.version() != version) {
                throw GroupBuyException.versionConflict();
            }
            requireCategory(p.categoryId());
            boolean structureChanged = !sameStructure(plan, store.axes(id), store.skus(id));
            if (store.usedByActiveGroupBuy(id)) {
                String locked = !row.name().equals(p.name()) ? "name"
                        : !Objects.equals(row.normalPrice(), p.normalPrice()) || !Objects.equals(row.normalPriceBasis(), p.normalPriceBasis()) ? "normalPrice"
                        : !Objects.equals(row.withdrawalRestriction(), p.withdrawalRestriction()) ? "withdrawalRestriction"
                        : structureChanged ? "options" : null;
                if (locked != null) {
                    throw GroupBuyException.lockedField(locked);
                }
            }
            if (!store.update(id, version, p, now)) {
                throw GroupBuyException.versionConflict();
            }
            sync(id, plan);
            replaceImages(id, draft.images(), now);
            for (long groupBuyId : store.runningGroupBuys(id)) { // 정보 정정 — 진행 중 공구의 표시 기록(결정 89)
                groups.snapshotRevision(ids.nextId(), groupBuyId, groups.nextRevision(groupBuyId, now), "CONTENT", operatorId, now);
            }
            record(operatorId, AuditActions.GROUPBUY_PRODUCT_SAVE, "GROUP_BUY_PRODUCT", id, ip, Map.of("stage", "UPDATE", "name", p.name(),
                    "structureChanged", structureChanged));
        });
    }

    private void requireCategory(long categoryId) {
        if (store.category(categoryId).isEmpty()) {
            throw ValidationException.of("categoryId", new MessageRef("groupbuy.admin.category.invalid"));
        }
    }

    private NewProduct validate(ProductDraft d, Map<String, MessageRef> f) {
        if (d.categoryId() == null) {
            f.put("categoryId", new MessageRef("groupbuy.admin.category.required"));
        }
        if (d.name() == null || d.name().isBlank() || d.name().strip().length() > NAME_MAX) {
            f.put("name", new MessageRef("groupbuy.admin.productName.invalid"));
        }
        if (longer(d.summary(), TEXT_MAX) || longer(d.spec(), TEXT_MAX) || longer(d.description(), DESCRIPTION_MAX)) {
            f.put("description", new MessageRef("groupbuy.admin.text.tooLong"));
        }
        if (d.normalPrice() != null && (d.normalPrice() < 1 || d.normalPriceBasis() == null || !PRICE_BASES.contains(d.normalPriceBasis()))) {
            f.put("normalPriceBasis", new MessageRef("groupbuy.admin.normalPrice.basisRequired")); // 정상가는 근거가 있어야 쓴다(결정 58)
        }
        if (longer(d.withdrawalRestriction(), 1000)) {
            f.put("withdrawalRestriction", new MessageRef("groupbuy.admin.text.tooLong"));
        }
        List<GroupBuyDraft.ImageRef> images = d.images() == null ? List.of() : d.images();
        if (images.size() > IMAGES_MAX || images.stream().anyMatch(i -> i == null || i.imageKey() == null || i.imageKey().isBlank() || i.imageKey().length() > 300
                || longer(i.thumbKey(), 300))) {
            f.put("images", new MessageRef("groupbuy.admin.images.invalid"));
        }
        if (!f.isEmpty()) {
            return null;
        }
        return new NewProduct(d.categoryId(), d.name().strip(), trimToNull(d.summary()), trimToNull(d.spec()), d.description(), d.normalPrice(),
                d.normalPrice() == null ? null : d.normalPriceBasis(), trimToNull(d.normalPriceSource()), trimToNull(d.withdrawalRestriction()));
    }

    /** 축 이름·값(차례)·단품 조합·단품 이름이 같은가(기본 공급가·판매 여부는 구성이 아니다) */
    private static boolean sameStructure(GroupBuySkuPlan.ProductPlan plan, List<AxisRow> axes, List<ProductSkuRow> skus) {
        if (axes.size() != plan.axes().size() || skus.size() != plan.skus().size()) {
            return false;
        }
        for (int i = 0; i < axes.size(); i++) {
            if (!axes.get(i).name().equals(plan.axes().get(i).name())
                    || !axes.get(i).values().stream().map(ValueRow::value).toList().equals(plan.axes().get(i).values())) {
                return false;
            }
        }
        Map<List<String>, ProductSkuRow> saved = savedByCombination(axes, skus);
        return plan.skus().stream().allMatch(s -> saved.containsKey(s.valueNames()) && saved.get(s.valueNames()).name().equals(s.name()));
    }

    private static Map<List<String>, ProductSkuRow> savedByCombination(List<AxisRow> axes, List<ProductSkuRow> skus) {
        Map<Long, String> valueNames = new HashMap<>();
        axes.forEach(a -> a.values().forEach(v -> valueNames.put(v.id(), v.value())));
        Map<List<String>, ProductSkuRow> out = new HashMap<>();
        skus.forEach(s -> out.put(s.valueIds().stream().map(valueNames::get).toList(), s));
        return out;
    }

    /**
     * 계획대로 축·값·상품 단품을 맞춘다 — 축 이름(차례)이 같으면 같은 이름의 값·같은 조합의 단품은 ID 를 유지하고 고치기만, 새 것은 넣고, 없어진 것은 지운다
     * (공구가 가리키는 단품이면 409). 축 이름이 다르면 모두 다시 만든다(가리키는 단품이 있으면 409).
     */
    private void sync(long productId, GroupBuySkuPlan.ProductPlan plan) {
        List<AxisRow> axes = store.axes(productId);
        List<ProductSkuRow> skus = store.skus(productId);
        boolean sameAxes = axes.stream().map(AxisRow::name).toList().equals(plan.axes().stream().map(GroupBuySkuPlan.PlannedAxis::name).toList());
        Map<List<String>, ProductSkuRow> saved = sameAxes ? savedByCombination(axes, skus) : Map.of();
        Set<List<String>> planned = new java.util.HashSet<>();
        plan.skus().forEach(s -> planned.add(s.valueNames()));
        List<ProductSkuRow> removed = sameAxes ? saved.entrySet().stream().filter(e -> !planned.contains(e.getKey())).map(Map.Entry::getValue).toList() : skus;
        List<String> inUse = removed.stream().filter(s -> store.skuInUse(s.id())).map(ProductSkuRow::name).toList();
        if (!inUse.isEmpty()) {
            throw GroupBuyException.productSkuInUse(inUse);
        }
        removed.forEach(s -> {
            store.deleteDraftGroupSkus(s.id()); // 공개 전 DRAFT 공구는 상품 구성을 따라간다
            store.deleteSku(s.id());
        });
        List<Map<String, Long>> valueIds = new ArrayList<>();
        if (!sameAxes) {
            store.deleteAxes(productId);
            for (int i = 0; i < plan.axes().size(); i++) {
                long axisId = ids.nextId();
                store.insertAxis(axisId, productId, i, plan.axes().get(i).name());
                Map<String, Long> byValue = new HashMap<>();
                List<String> values = plan.axes().get(i).values();
                for (int j = 0; j < values.size(); j++) {
                    long valueId = ids.nextId();
                    store.insertValue(valueId, axisId, productId, j, values.get(j));
                    byValue.put(values.get(j), valueId);
                }
                valueIds.add(byValue);
            }
        } else {
            for (int i = 0; i < axes.size(); i++) {
                AxisRow axis = axes.get(i);
                Map<String, Long> byValue = new HashMap<>();
                axis.values().forEach(v -> byValue.put(v.value(), v.id()));
                List<String> values = plan.axes().get(i).values();
                for (int j = 0; j < values.size(); j++) {
                    Long existing = byValue.get(values.get(j));
                    if (existing != null) {
                        store.moveValue(existing, j);
                    } else {
                        long valueId = ids.nextId();
                        store.insertValue(valueId, axis.id(), productId, j, values.get(j));
                        byValue.put(values.get(j), valueId);
                    }
                }
                for (ValueRow v : axis.values()) {
                    if (!values.contains(v.value())) {
                        store.deleteValue(v.id()); // 이 값의 단품은 위에서 지웠다
                    }
                }
                valueIds.add(byValue);
            }
        }
        for (int i = 0; i < plan.skus().size(); i++) {
            GroupBuySkuPlan.PlannedProductSku s = plan.skus().get(i);
            ProductSkuRow existing = saved.get(s.valueNames());
            if (existing != null) {
                store.updateSku(existing.id(), s.name(), s.defaultSupplyPrice(), s.onSale(), i);
            } else {
                List<Long> combination = new ArrayList<>();
                for (int a = 0; a < s.valueNames().size(); a++) {
                    combination.add(valueIds.get(a).get(s.valueNames().get(a)));
                }
                long skuId = ids.nextId();
                store.insertSku(skuId, productId, combination, s.name(), s.defaultSupplyPrice(), s.onSale(), i);
                for (long groupBuyId : store.draftGroupBuys(productId)) { // 공개 전 DRAFT 공구에 기본값으로 더한다
                    groups.insertSku(ids.nextId(), groupBuyId, SkuKind.MAIN, skuId, combination, s.name(), 0, null, null, s.onSale(), s.defaultSupplyPrice(), 1000 + i);
                }
            }
        }
    }

    private void replaceImages(long productId, List<GroupBuyDraft.ImageRef> images, Instant now) {
        List<GroupBuyDraft.ImageRef> list = images == null ? List.of() : images;
        store.replaceImages(productId, list.stream().map(i -> new ImageRow(i.imageKey().strip(), trimToNull(i.thumbKey()), 0)).toList(),
                list.stream().map(i -> ids.nextId()).toList(), now);
    }

    private void record(Long operatorId, String action, String targetType, long targetId, String ip, Map<String, Object> detail) {
        audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, action, AuditResult.SUCCESS, targetType,
                Long.toString(targetId), ip, detail));
    }

    private static boolean longer(String value, int max) {
        return value != null && value.length() > max;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
