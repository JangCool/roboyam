package com.community.menu.dto.request;

/**
 * {@code PUT /api/admin/menu-sets/{key}} — admin-web {@code MenuSetUpdateRequest}(key 는 주소에 있고 바꿀 수 없다). 기본 그룹의 위치는 바꾸지 않는다(보낸 값을 무시).
 *
 * @param enabled 사용 여부(없으면 지금 값)
 */
public record MenuSetUpdateRequest(String name, String nameEn, String placement, Boolean enabled, String description) {
}
