package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.dto.ProductDraft;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.repository.master.GroupBuyProductStore.ProductSkuRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 옵션 축·값·단품 계획(결정 88·89).
 * <ul>
 *   <li><b>상품</b>({@link #product}): 축 0~3개·축마다 값 1~30개·조합 최대 {@value #COMBINATIONS_MAX}개 — 상품 단품은 조합마다 하나(축이 없으면 하나, 이름 = 상품 이름),
 *       이름은 값을 「 / 」로 이은 것. 조합 설정을 적지 않은 조합은 기본 공급가 없음·판매.</li>
 *   <li><b>공구</b>({@link #group}): 공구 상품의 상품 단품마다 MAIN 공구 단품(설정을 적지 않으면 추가금 0·한도 없음·판매 여부와 공급가는 상품 단품의 기본값),
 *       추가 구성상품은 다른 상품의 단품(최대 {@value #ADDONS_MAX}개). 공구 단품 이름은 공구 안에서 유일.</li>
 * </ul>
 */
final class GroupBuySkuPlan {

    static final int AXES_MAX = 3;
    static final int VALUES_MAX = 30;
    static final int COMBINATIONS_MAX = 200;
    static final int ADDONS_MAX = 20;
    static final int NAME_MAX = 30;

    private GroupBuySkuPlan() {
    }

    /** 계획한 축 */
    record PlannedAxis(String name, List<String> values) {
    }

    /** 계획한 상품 단품(축 차례대로 값 이름) */
    record PlannedProductSku(List<String> valueNames, String name, Long defaultSupplyPrice, boolean onSale) {
    }

    /** 상품의 축·단품 계획 */
    record ProductPlan(List<PlannedAxis> axes, List<PlannedProductSku> skus) {
    }

    /** 계획한 공구 단품 — MAIN 은 공구 상품의 단품, ADDON 은 다른 상품의 단품 */
    record PlannedSku(SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty, boolean onSale,
                      Long supplyPrice) {
    }

    /** 추가 구성상품으로 고른 상품 단품과 그 상품 */
    record AddonSource(ProductSkuRow sku, String productName) {

        /** 공구 단품 이름 — 축 없는 상품이면 상품 이름, 아니면 「상품 이름 (조합)」 */
        String name() {
            return sku.valueIds().isEmpty() ? productName : productName + " (" + sku.name() + ")";
        }
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    /** 상품 등록 값에서 — 문제가 있으면 {@code f} 에 칸 오류를 넣고 null */
    static ProductPlan product(ProductDraft d, String productName, Map<String, MessageRef> f) {
        List<GroupBuyDraft.AxisDraft> axisDrafts = d.axes() == null ? List.of() : d.axes();
        if (axisDrafts.size() > AXES_MAX) {
            f.put("axes", new MessageRef("groupbuy.admin.axes.invalid"));
            return null;
        }
        List<PlannedAxis> axes = new ArrayList<>();
        Set<String> axisNames = new HashSet<>();
        long combinations = 1;
        for (GroupBuyDraft.AxisDraft a : axisDrafts) {
            if (a == null || blankOrLonger(a.name(), NAME_MAX) || !axisNames.add(a.name().strip()) || a.values() == null || a.values().isEmpty()
                    || a.values().size() > VALUES_MAX) {
                f.put("axes", new MessageRef("groupbuy.admin.axes.invalid"));
                return null;
            }
            Set<String> seen = new HashSet<>();
            List<String> values = new ArrayList<>();
            for (String v : a.values()) {
                if (blankOrLonger(v, NAME_MAX) || !seen.add(v.strip())) {
                    f.put("axes", new MessageRef("groupbuy.admin.axes.invalid"));
                    return null;
                }
                values.add(v.strip());
            }
            axes.add(new PlannedAxis(a.name().strip(), List.copyOf(values)));
            combinations *= values.size();
        }
        if (combinations > COMBINATIONS_MAX) {
            f.put("axes", new MessageRef("groupbuy.admin.axes.tooMany"));
            return null;
        }
        Map<List<String>, ProductDraft.SkuDraft> settings = new HashMap<>();
        for (ProductDraft.SkuDraft s : d.skus() == null ? List.<ProductDraft.SkuDraft>of() : d.skus()) {
            List<String> key = s == null || s.values() == null ? null : s.values().stream().map(v -> v == null ? "" : v.strip()).toList();
            if (key == null || !validCombination(axes, key) || settings.put(key, s) != null || (s.defaultSupplyPrice() != null && s.defaultSupplyPrice() < 0)) {
                f.put("skus", new MessageRef("groupbuy.admin.skus.invalid"));
                return null;
            }
        }
        List<PlannedProductSku> skus = new ArrayList<>();
        for (List<String> combination : combinations(axes)) {
            ProductDraft.SkuDraft s = settings.get(combination);
            String name = combination.isEmpty() ? productName : String.join(" / ", combination);
            skus.add(new PlannedProductSku(combination, name, s == null ? null : s.defaultSupplyPrice(), s == null || s.onSale()));
        }
        return new ProductPlan(List.copyOf(axes), List.copyOf(skus));
    }

    // ── 공구 ────────────────────────────────────────────────────────────────

    /**
     * 공구 등록 값에서 — 공구 상품의 상품 단품 {@code mainSkus}(차례대로), 추가 구성상품으로 고른 상품 단품을 찾는 {@code addons}(다른 상품만).
     * 문제가 있으면 {@code f} 에 칸 오류를 넣고 null.
     */
    static List<PlannedSku> group(GroupBuyDraft d, long productId, List<ProductSkuRow> mainSkus, Function<Long, Optional<AddonSource>> addons,
                                  Map<String, MessageRef> f) {
        Map<Long, GroupBuyDraft.SkuDraft> settings = new HashMap<>();
        Set<Long> mainIds = new HashSet<>();
        mainSkus.forEach(s -> mainIds.add(s.id()));
        for (GroupBuyDraft.SkuDraft s : d.skus() == null ? List.<GroupBuyDraft.SkuDraft>of() : d.skus()) {
            if (s == null || !mainIds.contains(s.productSkuId()) || settings.put(s.productSkuId(), s) != null || s.extraPrice() < 0
                    || (s.maxQty() != null && s.maxQty() < 1) || (s.supplyPrice() != null && s.supplyPrice() < 0)) {
                f.put("skus", new MessageRef("groupbuy.admin.skus.invalid"));
                return null;
            }
        }
        List<PlannedSku> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ProductSkuRow p : mainSkus) {
            GroupBuyDraft.SkuDraft s = settings.get(p.id());
            names.add(p.name());
            out.add(new PlannedSku(SkuKind.MAIN, p.id(), p.valueIds(), p.name(), s == null ? 0 : s.extraPrice(), null, s == null ? null : s.maxQty(),
                    s == null ? p.onSale() : s.onSale(), s == null || s.supplyPrice() == null ? p.defaultSupplyPrice() : s.supplyPrice()));
        }
        if (out.stream().noneMatch(PlannedSku::onSale)) {
            f.put("skus", new MessageRef("groupbuy.admin.skus.noneOnSale"));
            return null;
        }
        List<GroupBuyDraft.AddonDraft> addonDrafts = d.addons() == null ? List.of() : d.addons();
        if (addonDrafts.size() > ADDONS_MAX) {
            f.put("addons", new MessageRef("groupbuy.admin.addons.invalid"));
            return null;
        }
        Set<Long> seen = new HashSet<>();
        for (GroupBuyDraft.AddonDraft a : addonDrafts) {
            Optional<AddonSource> source = a == null || !seen.add(a.productSkuId()) ? Optional.empty() : addons.apply(a.productSkuId());
            if (source.isEmpty() || source.get().sku().productId() == productId || !names.add(source.get().name()) || a.price() < 1
                    || (a.maxQty() != null && a.maxQty() < 1) || (a.supplyPrice() != null && a.supplyPrice() < 0)) {
                f.put("addons", new MessageRef("groupbuy.admin.addons.invalid")); // 다른 상품의 단품만, 이름이 겹치면 안 된다
                return null;
            }
            out.add(new PlannedSku(SkuKind.ADDON, a.productSkuId(), List.of(), source.get().name(), 0, a.price(), a.maxQty(), true,
                    a.supplyPrice() == null ? source.get().sku().defaultSupplyPrice() : a.supplyPrice()));
        }
        return List.copyOf(out);
    }

    /** 공구의 옵션 부분(추가 차수 복사 — 한도는 처음 수량, 공급가 그대로) */
    record DraftParts(List<GroupBuyDraft.SkuDraft> skus, List<GroupBuyDraft.AddonDraft> addons) {
    }

    static DraftParts draftParts(List<SkuRow> skus) {
        return new DraftParts(skus.stream().filter(SkuRow::main).map(s -> new GroupBuyDraft.SkuDraft(s.productSkuId(), s.extraPrice(), baseMax(s), s.onSale(),
                s.supplyPrice())).toList(), skus.stream().filter(s -> !s.main()).map(s -> new GroupBuyDraft.AddonDraft(s.productSkuId(), s.addonPrice(), baseMax(s),
                s.supplyPrice())).toList());
    }

    /**
     * 이 계획이 저장된 공구 단품과 같은가(모집 뒤 잠금 — 상품 단품 연결·추가금·가격·한도·판매 여부, 결정 88). 공급가는 보지 않는다(모집 뒤에도 고친다 — 결정 89).
     * 공구에 없는 상품 단품(공구를 만든 뒤 상품에 더한 단품)의 MAIN 계획은 본다 — 판매로 켜면 다르다.
     */
    static boolean sameAs(List<PlannedSku> plan, List<SkuRow> saved) {
        Map<Long, SkuRow> byProductSku = new HashMap<>();
        saved.forEach(s -> byProductSku.put(s.productSkuId(), s));
        int matched = 0;
        for (PlannedSku p : plan) {
            SkuRow s = byProductSku.get(p.productSkuId());
            if (s == null) {
                if (p.kind() == SkuKind.MAIN && !p.onSale()) {
                    continue;
                }
                return false;
            }
            matched++;
            if (s.kind() != p.kind() || s.extraPrice() != p.extraPrice() || !Objects.equals(s.addonPrice(), p.addonPrice()) || s.onSale() != p.onSale()
                    || (!Objects.equals(baseMax(s), p.maxQty()) && !Objects.equals(s.maxQty(), p.maxQty()))) {
                return false;
            }
        }
        return matched == saved.size();
    }

    private static Integer baseMax(SkuRow s) {
        return s.initialMaxQty() == null ? s.maxQty() : s.initialMaxQty();
    }

    private static boolean validCombination(List<PlannedAxis> axes, List<String> key) {
        if (key.size() != axes.size()) {
            return false;
        }
        for (int i = 0; i < key.size(); i++) {
            if (!axes.get(i).values().contains(key.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** 축 값의 모든 조합(축 차례, 값 차례) — 축이 없으면 빈 조합 하나 */
    static List<List<String>> combinations(List<PlannedAxis> axes) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of());
        for (PlannedAxis axis : axes) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : out) {
                for (String value : axis.values()) {
                    List<String> combination = new ArrayList<>(prefix);
                    combination.add(value);
                    next.add(List.copyOf(combination));
                }
            }
            out = next;
        }
        return out;
    }

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }
}
