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
 *   <li><b>상품</b>({@link #product} — 2b-1 사용자 결정: <b>단품 목록이 정본</b>): 운영자가 단품을 한 줄씩(1~{@value #SKUS_MAX}개, 이름 유일), 옵션 이름 0~{@value #AXES_MAX}개를 두면
 *       단품마다 값 모두 필수·같은 값 묶음 두 줄 금지. 옵션 축의 값은 단품들의 값에서 처음 나온 차례대로 만든다(축마다 최대 {@value #VALUES_MAX}개).</li>
 *   <li><b>공구</b>({@link #group}): 공구 상품의 상품 단품마다 MAIN 공구 단품(설정을 적지 않으면 추가금 0·한도 없음·판매 여부와 공급가는 상품 단품의 기본값),
 *       추가 구성상품은 다른 상품의 단품(최대 {@value #ADDONS_MAX}개). 공구 단품 이름은 공구 안에서 유일.</li>
 * </ul>
 */
final class GroupBuySkuPlan {

    static final int AXES_MAX = 3;
    static final int VALUES_MAX = 30;
    static final int SKUS_MAX = 200;
    static final int ADDONS_MAX = 20;
    static final int NAME_MAX = 30;
    static final int SKU_NAME_MAX = 100;

    private GroupBuySkuPlan() {
    }

    /** 계획한 축 */
    record PlannedAxis(String name, List<String> values) {
    }

    /** 계획한 상품 단품 — {@code skuId} 기존 단품(새 단품이면 null), {@code valueNames} 옵션 이름 차례대로 값 */
    record PlannedProductSku(Long skuId, List<String> valueNames, String name, Long defaultSupplyPrice, boolean onSale) {
    }

    /** 상품의 축·단품 계획 */
    record ProductPlan(List<PlannedAxis> axes, List<PlannedProductSku> skus) {
    }

    /** 계획한 공구 단품 — MAIN 은 공구 상품의 단품, ADDON 은 다른 상품의 단품 */
    record PlannedSku(SkuKind kind, long productSkuId, List<Long> valueIds, String name, long extraPrice, Long addonPrice, Integer maxQty, boolean onSale,
                      Long supplyPrice) {
    }

    /** 추가 구성상품으로 고른 상품 단품과 그 상품({@code single}: 그 상품의 단품이 하나뿐) */
    record AddonSource(ProductSkuRow sku, String productName, boolean single) {

        /** 공구 단품 이름 — 단품이 하나뿐인 상품이면 상품 이름, 아니면 「상품 이름 (단품 이름)」 */
        String name() {
            return single || sku.name().equals(productName) ? productName : productName + " (" + sku.name() + ")";
        }
    }

    // ── 상품 ────────────────────────────────────────────────────────────────

    /** 상품 등록 값에서 — 문제가 있으면 {@code f} 에 칸 오류를 넣고 null */
    static ProductPlan product(ProductDraft d, Map<String, MessageRef> f) {
        List<String> optionNames = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (String n : d.optionNames() == null ? List.<String>of() : d.optionNames()) {
            if (blankOrLonger(n, NAME_MAX) || !seenNames.add(n.strip())) {
                f.put("optionNames", new MessageRef("groupbuy.admin.optionNames.invalid"));
                return null;
            }
            optionNames.add(n.strip());
        }
        if (optionNames.size() > AXES_MAX) {
            f.put("optionNames", new MessageRef("groupbuy.admin.optionNames.invalid"));
            return null;
        }
        List<ProductDraft.SkuDraft> drafts = d.skus() == null ? List.of() : d.skus();
        if (drafts.isEmpty() || drafts.size() > SKUS_MAX) {
            f.put("skus", new MessageRef("groupbuy.admin.skus.invalid"));
            return null;
        }
        Set<String> skuNames = new HashSet<>();
        Set<List<String>> combinations = new HashSet<>();
        Set<Long> skuIds = new HashSet<>();
        List<PlannedProductSku> skus = new ArrayList<>();
        List<java.util.LinkedHashSet<String>> values = new ArrayList<>();
        optionNames.forEach(n -> values.add(new java.util.LinkedHashSet<>()));
        for (ProductDraft.SkuDraft s : drafts) {
            List<String> v = s == null || s.values() == null ? List.of() : s.values().stream().map(x -> x == null ? "" : x.strip()).toList();
            if (s == null || blankOrLonger(s.name(), SKU_NAME_MAX) || !skuNames.add(s.name().strip()) || (s.skuId() != null && !skuIds.add(s.skuId()))
                    || v.size() != optionNames.size() || v.stream().anyMatch(x -> x.isEmpty() || x.length() > NAME_MAX) || (!v.isEmpty() && !combinations.add(v))
                    || (s.defaultSupplyPrice() != null && s.defaultSupplyPrice() < 0)) {
                f.put("skus", new MessageRef("groupbuy.admin.skus.invalid")); // 이름 겹침·값 빠짐·같은 값 묶음
                return null;
            }
            for (int i = 0; i < v.size(); i++) {
                values.get(i).add(v.get(i));
            }
            skus.add(new PlannedProductSku(s.skuId(), v, s.name().strip(), s.defaultSupplyPrice(), s.onSale()));
        }
        if (values.stream().anyMatch(x -> x.size() > VALUES_MAX)) {
            f.put("skus", new MessageRef("groupbuy.admin.skus.invalid"));
            return null;
        }
        List<PlannedAxis> axes = new ArrayList<>();
        for (int i = 0; i < optionNames.size(); i++) {
            axes.add(new PlannedAxis(optionNames.get(i), List.copyOf(values.get(i))));
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

    private static boolean blankOrLonger(String value, int max) {
        return value == null || value.isBlank() || value.strip().length() > max;
    }
}
