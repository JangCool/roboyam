package com.community.rewardevent.dto.response;

import java.util.List;

/**
 * 보상 고르기용(admin-web {@code RewardEventCatalog}) — 포인트 상점 품목(아바타 제외, 판매 중단 품목도 — {@code onSale}) · 로보티콘 팩(판매 중만, 운영 팩·무료 팩만 {@code selectable}) ·
 * 등급 · 고액 문턱.
 */
public record RewardEventCatalogView(List<Item> items, List<Pack> packs, List<Grade> grades, int largeThreshold) {

    public record Item(String id, String name, String kind, String usage, Integer durationDays, boolean onSale) {
    }

    /** @param source {@code OFFICIAL} · {@code CREATOR} */
    public record Pack(String id, String name, String source, int price, boolean selectable) {
    }

    public record Grade(int level, String label) {
    }
}
