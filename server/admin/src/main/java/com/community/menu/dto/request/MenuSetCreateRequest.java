package com.community.menu.dto.request;

/**
 * {@code POST /api/admin/menu-sets} — admin-web {@code MenuSetCreateRequest}. 검사는 서비스가 칸별로 모은다({@code key}·{@code name}·{@code nameEn}·{@code placement}·{@code description}).
 *
 * @param key       코드 {@code ^[a-z][a-z0-9-]*$} 2~30자(만든 뒤 바꿀 수 없다)
 * @param placement {@code SIDE} · {@code MYPAGE} · {@code GROUP_BUY} · {@code CUSTOM}
 * @param enabled   사용 여부(없으면 켬)
 */
public record MenuSetCreateRequest(String key, String name, String nameEn, String placement, Boolean enabled, String description) {
}
