package com.community.notification.entity;

/**
 * 알림 이벤트의 업무 영역 — 권한의 영역({@code PermissionArea})과 같은 영역에서 「종합」을 빼고 알림 전용 「보안」({@link #SECURITY} — 내 계정의 상태·권한·비밀번호가 바뀐 일)을 더했다.
 * id 는 {@code PermissionArea} 의 id 와 같고(admin-web {@code util-permission-catalog.ts} {@code AREAS}) 보안만 {@code security} 다. 이벤트 관리 화면이 영역별로 묶는다.
 */
public enum NotificationArea {

    CONTENT("content"),
    SAFETY("safety"),
    MEMBER("member"),
    CLUB("club"),
    HOTDEAL("hotdeal"),
    GROUPBUY("groupbuy"),
    BIZ_AD("bizAd"),
    SYSTEM("system"),
    SECURITY("security");

    private final String id;

    NotificationArea(String id) {
        this.id = id;
    }

    /** 화면과 주고받는 영역 식별자 */
    public String id() {
        return id;
    }
}
