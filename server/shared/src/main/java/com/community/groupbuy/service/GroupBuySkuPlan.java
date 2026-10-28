package com.community.groupbuy.service;

import com.community.groupbuy.dto.GroupBuyDraft;
import com.community.groupbuy.entity.SkuKind;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.AxisRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 등록 값의 옵션 축·값·단품 계획(결정 88) — 축 0~3개·축마다 값 1~30개·조합(MAIN 단품) 최대 {@value #COMBINATIONS_MAX}개·추가 구성상품 최대 {@value #ADDONS_MAX}개.
 * MAIN 단품은 축 값 조합마다 하나(축이 없으면 하나 — 이름 = 상품 이름), 이름은 값을 「 / 」로 이은 것. 조합 설정을 적지 않은 조합은 추가금 0·한도 없음·판매.
 * 단품 이름은 공구 안에서 유일(추가 구성상품 이름이 조합 이름·다른 추가상품과 겹치면 안 된다).
 */
final class GroupBuySkuPlan {

    static final int AXES_MAX = 3;
    static final int VALUES_MAX = 30;
    static final int COMBINATIONS_MAX = 200;
    static final int ADDONS_MAX = 20;
    static final int NAME_MAX = 30;
    static final int ADDON_NAME_MAX = 100;

    /** 계획한 축 */
    record PlannedAxis(String name, List<String> values) {
    }

    /** 계획한 단품 — MAIN 은 {@code valueNames}(축 차례), ADDON 은 빈 목록 */
    record PlannedSku(SkuKind kind, List<String> valueNames, String name, long extraPrice, Long addonPrice, Integer maxQty, boolean onSale, String imageKey) {
    }

    final List<PlannedAxis> axes;
    final List<PlannedSku> skus;

    private GroupBuySkuPlan(List<PlannedAxis> axes, List<PlannedSku> skus) {
        this.axes = axes;
        this.skus = skus;
    }

    /** 등록 값에서 — 문제가 있으면 {@code f} 에 칸 오류를 넣고 null */
    static GroupBuySkuPlan of(GroupBuyDraft d, Map<String, MessageRef> f) {
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
        // 조합 설정(값 이름 목록 → 설정)
        Map<List<String>, GroupBuyDraft.SkuDraft> settings = new HashMap<>();
        for (GroupBuyDraft.SkuDraft s : d.skus() == null ? List.<GroupBuyDraft.SkuDraft>of() : d.skus()) {
            List<String> key = s == null || s.values() == null ? null : s.values().stream().map(v -> v == null ? "" : v.strip()).toList();
            if (key == null || !validCombination(axes, key) || settings.put(key, s) != null || s.extraPrice() < 0 || (s.maxQty() != null && s.maxQty() < 1)) {
                f.put("skus", new MessageRef("groupbuy.admin.skus.invalid"));
                return null;
            }
        }
        List<PlannedSku> skus = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (List<String> combination : combinations(axes)) {
            GroupBuyDraft.SkuDraft s = settings.get(combination);
            String name = combination.isEmpty() ? d.productName().strip() : String.join(" / ", combination);
            names.add(name);
            skus.add(new PlannedSku(SkuKind.MAIN, combination, name, s == null ? 0 : s.extraPrice(), null, s == null ? null : s.maxQty(), s == null || s.onSale(), null));
        }
        if (skus.stream().noneMatch(PlannedSku::onSale)) {
            f.put("skus", new MessageRef("groupbuy.admin.skus.noneOnSale"));
            return null;
        }
        List<GroupBuyDraft.AddonDraft> addons = d.addons() == null ? List.of() : d.addons();
        if (addons.size() > ADDONS_MAX) {
            f.put("addons", new MessageRef("groupbuy.admin.addons.invalid"));
            return null;
        }
        for (GroupBuyDraft.AddonDraft a : addons) {
            if (a == null || blankOrLonger(a.name(), ADDON_NAME_MAX) || !names.add(a.name().strip()) || a.price() < 1 || (a.maxQty() != null && a.maxQty() < 1)
                    || (a.imageKey() != null && a.imageKey().length() > 300)) {
                f.put("addons", new MessageRef("groupbuy.admin.addons.invalid")); // 이름이 단품과 겹치면 안 된다
                return null;
            }
            skus.add(new PlannedSku(SkuKind.ADDON, List.of(), a.name().strip(), 0, a.price(), a.maxQty(), true,
                    a.imageKey() == null || a.imageKey().isBlank() ? null : a.imageKey().strip()));
        }
        return new GroupBuySkuPlan(List.copyOf(axes), List.copyOf(skus));
    }

    /** 등록 값의 옵션 부분 */
    record DraftParts(List<GroupBuyDraft.AxisDraft> axes, List<GroupBuyDraft.SkuDraft> skus, List<GroupBuyDraft.AddonDraft> addons) {
    }

    /** 저장된 공구에서 등록 값의 옵션 부분을 다시 만든다(추가 차수 복사 — 한도는 처음 수량) */
    static DraftParts draftParts(List<AxisRow> axes, List<SkuRow> skus) {
        Map<Long, String> valueNames = new HashMap<>();
        axes.forEach(a -> a.values().forEach(v -> valueNames.put(v.id(), v.value())));
        List<GroupBuyDraft.AxisDraft> axisDrafts = axes.stream().map(a -> new GroupBuyDraft.AxisDraft(a.name(),
                a.values().stream().map(GroupBuyStore.ValueRow::value).toList())).toList();
        List<GroupBuyDraft.SkuDraft> skuDrafts = skus.stream().filter(SkuRow::main).map(s -> new GroupBuyDraft.SkuDraft(
                s.valueIds().stream().map(valueNames::get).toList(), s.extraPrice(), baseMax(s), s.onSale())).toList();
        List<GroupBuyDraft.AddonDraft> addonDrafts = skus.stream().filter(s -> !s.main()).map(s -> new GroupBuyDraft.AddonDraft(s.name(), s.addonPrice(), baseMax(s),
                s.imageKey())).toList();
        return new DraftParts(axisDrafts, skuDrafts, addonDrafts);
    }

    /** 이 계획이 저장된 축·단품과 같은가(모집 뒤 잠금 — 축·값·조합·추가금·한도·판매 여부·추가상품, 결정 88) */
    boolean sameAs(List<AxisRow> savedAxes, List<SkuRow> savedSkus) {
        if (savedAxes.size() != axes.size() || savedSkus.size() != skus.size()) {
            return false;
        }
        for (int i = 0; i < axes.size(); i++) {
            AxisRow a = savedAxes.get(i);
            if (!a.name().equals(axes.get(i).name()) || !a.values().stream().map(GroupBuyStore.ValueRow::value).toList().equals(axes.get(i).values())) {
                return false;
            }
        }
        Map<String, SkuRow> byName = new HashMap<>();
        savedSkus.forEach(s -> byName.put(s.name(), s));
        for (PlannedSku p : skus) {
            SkuRow s = byName.get(p.name());
            if (s == null || s.kind() != p.kind() || s.extraPrice() != p.extraPrice() || !Objects.equals(s.addonPrice(), p.addonPrice()) || s.onSale() != p.onSale()
                    || (!Objects.equals(baseMax(s), p.maxQty()) && !Objects.equals(s.maxQty(), p.maxQty()))) {
                return false;
            }
        }
        return true;
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
