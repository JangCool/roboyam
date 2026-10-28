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

    // ── 옵션 종류(2b-1 사용자 결정 2026-10-28) ─────────────────────────────

    /** 옵션 종류 한 줄 — 코드(영문 대문자 2~20, 만든 뒤 불변)·이름(1~30, 유일)·차례·숨김 */
    public record OptionTypeDraft(String code, String name, int sortOrder, boolean hidden) {
    }

    /** 옵션 종류 목록(쓰는 상품 수·쓴 값 — 자동완성용) */
    public List<GroupBuyProductStore.OptionTypeRow> optionTypes() {
        return store.optionTypes();
    }

    /**
     * 옵션 종류 목록을 통째로 저장한다({@code PUT}) — 없던 코드는 만들고, 있던 코드는 이름·차례·숨김을 고치고(이름을 바꾸면 쓰는 상품의 옵션 이름도 따라간다), 목록에 없는 코드는 지운다.
     *
     * @throws ValidationException {@code optionTypes} — 코드 모양·이름 길이·코드나 이름 겹침
     * @throws GroupBuyException   {@code OPTION_TYPE_IN_USE}(쓰는 상품이 있는 종류를 지우려 했다 — {@code codes})
     */
    public void saveOptionTypes(List<OptionTypeDraft> drafts, Long operatorId, String ip) {
        List<OptionTypeDraft> list = validateOptionTypes(drafts);
        tx.executeWithoutResult(status -> {
            Instant now = now();
            Set<String> keep = new java.util.HashSet<>();
            list.forEach(d -> keep.add(d.code()));
            List<GroupBuyProductStore.OptionTypeRow> current = store.optionTypes();
            List<String> inUse = current.stream().filter(t -> !keep.contains(t.code()) && t.productCount() > 0).map(GroupBuyProductStore.OptionTypeRow::code).toList();
            if (!inUse.isEmpty()) {
                throw GroupBuyException.optionTypeInUse(inUse);
            }
            current.stream().filter(t -> !keep.contains(t.code())).forEach(t -> store.deleteOptionType(t.code()));
            current.stream().filter(t -> keep.contains(t.code())).forEach(t -> store.parkOptionTypeName(t.code()));
            list.forEach(d -> store.upsertOptionType(d.code(), d.name(), d.sortOrder(), d.hidden(), now));
            audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, AuditActions.GROUPBUY_OPTION_TYPE_SAVE,
                    AuditResult.SUCCESS, "GROUP_BUY_OPTION_TYPE", "ALL", ip, Map.of("codes", list.stream().map(OptionTypeDraft::code).toList())));
        });
    }

    /** 옵션 종류 하나를 만들거나 고친다(다른 종류는 그대로) */
    public void saveOptionType(OptionTypeDraft draft, Long operatorId, String ip) {
        OptionTypeDraft d = validateOptionTypes(List.of(draft)).getFirst();
        tx.executeWithoutResult(status -> {
            store.parkOptionTypeName(d.code());
            store.upsertOptionType(d.code(), d.name(), d.sortOrder(), d.hidden(), now());
            audit.record(new AuditEvent(operatorId == null ? AuditActorType.SYSTEM : AuditActorType.OPERATOR, operatorId, AuditActions.GROUPBUY_OPTION_TYPE_SAVE,
                    AuditResult.SUCCESS, "GROUP_BUY_OPTION_TYPE", d.code(), ip, Map.of("name", d.name(), "hidden", d.hidden())));
        });
    }

    private static List<OptionTypeDraft> validateOptionTypes(List<OptionTypeDraft> drafts) {
        Set<String> codes = new java.util.HashSet<>();
        Set<String> names = new java.util.HashSet<>();
        List<OptionTypeDraft> out = new ArrayList<>();
        for (OptionTypeDraft d : drafts == null ? List.<OptionTypeDraft>of() : drafts) {
            if (d == null || d.code() == null || !d.code().matches("[A-Z][A-Z0-9_]{1,19}") || !codes.add(d.code()) || d.name() == null || d.name().isBlank()
                    || d.name().strip().length() > CATEGORY_NAME_MAX || !names.add(d.name().strip())) {
                throw ValidationException.of("optionTypes", new MessageRef("groupbuy.admin.optionTypes.invalid"));
            }
            out.add(new OptionTypeDraft(d.code(), d.name().strip(), d.sortOrder(), d.hidden()));
        }
        return out;
    }

    /** 상품이 고를 수 있는 옵션 종류 — 숨기지 않은 종류 + 이 상품이 이미 쓰는 종류 */
    private Set<String> allowedOptionTypes(Long productId) {
        Set<String> out = new java.util.HashSet<>();
        store.optionTypes().stream().filter(t -> !t.hidden()).forEach(t -> out.add(t.code()));
        if (productId != null) {
            store.axes(productId).forEach(a -> out.add(a.code()));
        }
        return out;
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
    public long create(ProductDraft raw, Long operatorId, String ip) {
        ProductDraft draft = GroupBuyDescriptionHtml.sanitized(raw); // 설명은 서버에서 반드시 정화한다(결정 2 — 2b-4)
        Map<String, MessageRef> f = new LinkedHashMap<>();
        NewProduct p = validate(draft, f);
        GroupBuySkuPlan.ProductPlan plan = p == null ? null : GroupBuySkuPlan.product(draft, allowedOptionTypes(null), f);
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
    public void update(long id, int version, ProductDraft raw, Long operatorId, String ip) {
        ProductDraft draft = GroupBuyDescriptionHtml.sanitized(raw); // 설명은 서버에서 반드시 정화한다(결정 2 — 2b-4)
        Map<String, MessageRef> f = new LinkedHashMap<>();
        NewProduct p = validate(draft, f);
        GroupBuySkuPlan.ProductPlan plan = p == null ? null : GroupBuySkuPlan.product(draft, allowedOptionTypes(id), f);
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

    /** 옵션 종류(차례)·단품(같은 ID 묶음)·단품 이름·값이 같은가(기본 공급가·판매 여부는 구성이 아니다) */
    private static boolean sameStructure(GroupBuySkuPlan.ProductPlan plan, List<AxisRow> axes, List<ProductSkuRow> skus) {
        if (!axes.stream().map(AxisRow::code).toList().equals(plan.axes().stream().map(GroupBuySkuPlan.PlannedAxis::code).toList())
                || skus.size() != plan.skus().size()) {
            return false;
        }
        Map<Long, String> valueNames = valueNames(axes);
        Map<Long, ProductSkuRow> byId = new HashMap<>();
        skus.forEach(s -> byId.put(s.id(), s));
        for (GroupBuySkuPlan.PlannedProductSku planned : plan.skus()) {
            ProductSkuRow saved = planned.skuId() == null ? null : byId.get(planned.skuId());
            if (saved == null || !saved.name().equals(planned.name())
                    || !saved.valueIds().stream().map(valueNames::get).toList().equals(planned.valueNames())) {
                return false;
            }
        }
        return true;
    }

    private static Map<Long, String> valueNames(List<AxisRow> axes) {
        Map<Long, String> out = new HashMap<>();
        axes.forEach(a -> a.values().forEach(v -> out.put(v.id(), v.value())));
        return out;
    }

    /**
     * 계획대로 옵션 축·값·상품 단품을 맞춘다(2b-1 사용자 결정 — 단품 목록이 정본). 단품은 {@code skuId} 로 가리킨 것은 고치고, 새 줄은 넣고, 목록에 없는 기존 단품은 지운다
     * (공개된 공구가 가리키면 409). 옵션 종류(차례)가 같으면 같은 값은 ID 를 유지하고, 다르면 축·값을 다시 만든다. 단품의 값이 바뀌면 공구 단품의 조합 사본도 고친다.
     */
    private void sync(long productId, GroupBuySkuPlan.ProductPlan plan) {
        List<AxisRow> axes = store.axes(productId);
        List<ProductSkuRow> skus = store.skus(productId);
        Map<Long, ProductSkuRow> byId = new HashMap<>();
        skus.forEach(s -> byId.put(s.id(), s));
        Set<Long> kept = new java.util.HashSet<>();
        for (GroupBuySkuPlan.PlannedProductSku planned : plan.skus()) {
            if (planned.skuId() != null) {
                if (!byId.containsKey(planned.skuId())) {
                    throw ValidationException.of("skus", new MessageRef("groupbuy.admin.skus.invalid")); // 이 상품의 단품이 아니다
                }
                kept.add(planned.skuId());
            }
        }
        List<ProductSkuRow> removed = skus.stream().filter(s -> !kept.contains(s.id())).toList();
        List<String> inUse = removed.stream().filter(s -> store.skuInUse(s.id())).map(ProductSkuRow::name).toList();
        if (!inUse.isEmpty()) {
            throw GroupBuyException.productSkuInUse(inUse);
        }
        removed.forEach(s -> {
            store.deleteDraftGroupSkus(s.id()); // 공개 전 DRAFT 공구는 상품 구성을 따라간다
            store.deleteSku(s.id());
        });
        // 이름·값 묶음 유일 — 바뀌는 단품은 먼저 비켜 둔다(두 단품의 이름을 맞바꿔도 되게)
        Map<Long, String> oldValueNames = valueNames(axes);
        for (GroupBuySkuPlan.PlannedProductSku planned : plan.skus()) {
            ProductSkuRow saved = planned.skuId() == null ? null : byId.get(planned.skuId());
            if (saved != null && (!saved.name().equals(planned.name()) || !saved.valueIds().stream().map(oldValueNames::get).toList().equals(planned.valueNames()))) {
                store.updateSku(saved.id(), "#" + saved.id(), List.of(), saved.defaultSupplyPrice(), saved.onSale(), saved.sortOrder());
            }
        }
        // 축·값
        boolean sameAxes = axes.stream().map(AxisRow::code).toList().equals(plan.axes().stream().map(GroupBuySkuPlan.PlannedAxis::code).toList());
        List<Map<String, Long>> valueIds = new ArrayList<>();
        if (!sameAxes) {
            store.deleteAxes(productId);
        }
        for (int i = 0; i < plan.axes().size(); i++) {
            GroupBuySkuPlan.PlannedAxis planned = plan.axes().get(i);
            Map<String, Long> byValue = new HashMap<>();
            long axisId;
            if (sameAxes) {
                AxisRow axis = axes.get(i);
                axisId = axis.id();
                axis.values().forEach(v -> byValue.put(v.value(), v.id()));
                for (ValueRow v : axis.values()) {
                    if (!planned.values().contains(v.value())) {
                        store.deleteValue(v.id()); // 이 값을 쓰는 단품은 이제 없다
                        byValue.remove(v.value());
                    }
                }
            } else {
                axisId = ids.nextId();
                store.insertAxis(axisId, productId, i, planned.code());
            }
            for (int j = 0; j < planned.values().size(); j++) {
                Long existing = byValue.get(planned.values().get(j));
                if (existing != null) {
                    store.moveValue(existing, j);
                } else {
                    long valueId = ids.nextId();
                    store.insertValue(valueId, axisId, productId, j, planned.values().get(j));
                    byValue.put(planned.values().get(j), valueId);
                }
            }
            valueIds.add(byValue);
        }
        // 단품
        for (int i = 0; i < plan.skus().size(); i++) {
            GroupBuySkuPlan.PlannedProductSku planned = plan.skus().get(i);
            List<Long> combination = new ArrayList<>();
            for (int a = 0; a < planned.valueNames().size(); a++) {
                combination.add(valueIds.get(a).get(planned.valueNames().get(a)));
            }
            ProductSkuRow saved = planned.skuId() == null ? null : byId.get(planned.skuId());
            if (saved != null) {
                store.updateSku(saved.id(), planned.name(), combination, planned.defaultSupplyPrice(), planned.onSale(), i);
                if (!saved.valueIds().equals(combination) || !saved.name().equals(planned.name())) {
                    store.updateGroupSkuCopies(saved.id(), combination, planned.name());
                }
            } else {
                long skuId = ids.nextId();
                store.insertSku(skuId, productId, combination, planned.name(), planned.defaultSupplyPrice(), planned.onSale(), i);
                for (long groupBuyId : store.draftGroupBuys(productId)) { // 공개 전 DRAFT 공구에 기본값으로 더한다
                    groups.insertSku(ids.nextId(), groupBuyId, SkuKind.MAIN, skuId, combination, planned.name(), 0, null, null, planned.onSale(),
                            planned.defaultSupplyPrice(), 1000 + i);
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
