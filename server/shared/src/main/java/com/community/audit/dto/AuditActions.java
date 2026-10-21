package com.community.audit.dto;

/** 감사 기록의 행위 이름 — 운영자 기반(로그인·권한 거부·역할·운영자·초대)과 등급 설정·회원 상세 열람·배치(켜기/끄기·cron·지금 실행)가 쓴다. 이름은 한 번 정하면 바꾸지 않는다(조회·필터가 기대는 값이다) */
public final class AuditActions {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGOUT = "LOGOUT";
    public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
    public static final String ROLE_CREATE = "ROLE_CREATE";
    public static final String ROLE_UPDATE = "ROLE_UPDATE";
    public static final String ROLE_DELETE = "ROLE_DELETE";
    public static final String ROLE_PERMISSIONS_CHANGE = "ROLE_PERMISSIONS_CHANGE";
    public static final String OPERATOR_CREATE = "OPERATOR_CREATE";
    public static final String OPERATOR_UPDATE = "OPERATOR_UPDATE";
    public static final String OPERATOR_STATUS_CHANGE = "OPERATOR_STATUS_CHANGE";
    public static final String OPERATOR_REJECT = "OPERATOR_REJECT";
    public static final String INVITATION_CREATE = "INVITATION_CREATE";
    public static final String INVITATION_CANCEL = "INVITATION_CANCEL";
    public static final String GRADE_UPDATE = "GRADE_UPDATE";
    public static final String MEMBER_VIEW = "MEMBER_VIEW";
    public static final String BATCH_TOGGLE = "BATCH_TOGGLE";
    public static final String BATCH_SCHEDULE_CHANGE = "BATCH_SCHEDULE_CHANGE";
    public static final String BATCH_RUN = "BATCH_RUN";

    private AuditActions() {
    }
}
