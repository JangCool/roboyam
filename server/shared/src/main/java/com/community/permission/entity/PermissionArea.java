package com.community.permission.entity;

/**
 * 권한의 업무 영역(대분류) 9개 — 운영자 역할 화면의 탭이다. 목록·차례·id 는 admin-web 의 {@code util-permission-catalog.ts} {@code AREAS} 와 같다.
 * {@link #GENERAL}(종합)은 영역 안에 세부 유형 {@code general}(종합 관리) 하나만 있다.
 */
public enum PermissionArea {

    CONTENT("content"),
    SAFETY("safety"),
    MEMBER("member"),
    CLUB("club"),
    HOTDEAL("hotdeal"),
    GROUPBUY("groupbuy"),
    BIZ_AD("bizAd"),
    SYSTEM("system"),
    GENERAL("general");

    private final String id;

    PermissionArea(String id) {
        this.id = id;
    }

    /** 화면과 주고받는 영역 식별자 */
    public String id() {
        return id;
    }
}
