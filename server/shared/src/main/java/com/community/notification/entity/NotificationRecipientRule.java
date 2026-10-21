package com.community.notification.entity;

/** 알림을 누가 받는가 — 이벤트마다 코드에 고정된 규칙이다(화면에서 바꾸지 않는다: 규칙을 바꾸면 보면 안 될 알림을 받을 수 있다) */
public enum NotificationRecipientRule {

    /** 대상 운영자 본인({@code NotificationContext#targetOperatorId}) */
    SELF,
    /** 지정한 권한 중 하나라도 가진 역할을 받은 운영자 + 최고 관리자(루트) — 활성(승인·사용 중·잠기지 않음) 운영자만 */
    PERMISSION_HOLDERS,
    /** 최고 관리자(루트)만 */
    ROOT
}
