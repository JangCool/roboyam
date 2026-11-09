package com.community.cache.dto.response;

import java.util.List;

/** 캐시 관리 화면 한 번에 받는 것 — 위쪽 요약 + 목록. 모양은 admin-web {@code CacheOverviewView} */
public record CacheOverviewView(List<CacheNodeSummaryView> nodes, int pollSeconds, List<CacheView> caches, boolean globalEnabled,
                                List<CacheExclusionView> uncachedGadgets, List<CachePreviewPageView> previewPages, String publicUrl) {
}
