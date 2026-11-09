package com.community.cache.dto.response;

/** 이 가젯 유형을 놓은 곳 한 줄 — 곳 HOME · GADGET_PAGE · BOARD_LAYOUT, 쪽 이름 · 경로(없으면 null) · 놓인 수 */
public record CacheGadgetPlacementView(String place, String name, String path, int count) {
}
