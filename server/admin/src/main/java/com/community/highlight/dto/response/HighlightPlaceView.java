package com.community.highlight.dto.response;

/**
 * {@code GET /api/admin/highlights/places} 의 한 줄 — 하이라이트를 놓을 수 있는 곳.
 *
 * @param key  곳 키({@code home} · {@code tips} · 가젯 페이지 키)
 * @param name 보이는 이름(요청 언어 — 홈 · 팁·강좌·사용기 · 가젯 페이지 이름)
 * @param kind  {@code HOME} · {@code TIPS} · {@code PAGE}(가젯 페이지)
 * @param total 그 곳의 하이라이트 수(노출 여부와 상관없이 — 왼쪽 곳 메뉴에 늘 보인다)
 */
public record HighlightPlaceView(String key, String name, String kind, long total) {
}
