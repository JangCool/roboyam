package com.community.audit.dto;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** 감사 기록의 행위 이름 — 운영자 기반(로그인·권한 거부·역할·운영자·초대)과 등급 설정·회원 상세 열람·배치(켜기/끄기·cron·지금 실행)·사이트 일반 설정(기본 정보·로고·아이콘·링크 미리보기 이미지·표시 기본값)·내 정보(닉네임·내 표시 형식·알림 설정·비밀번호 변경)와 로그인 전 계정 흐름(초대 수락·비밀번호 재설정)이 쓴다. 이름은 한 번 정하면 바꾸지 않는다(조회·필터가 기대는 값이다) */
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
    public static final String SITE_BASIC_UPDATE = "SITE_BASIC_UPDATE";
    public static final String SITE_LOGO_CHANGE = "SITE_LOGO_CHANGE";
    public static final String SITE_LOGO_REMOVE = "SITE_LOGO_REMOVE";
    public static final String SITE_ICON_CHANGE = "SITE_ICON_CHANGE";
    public static final String SITE_ICON_RESET = "SITE_ICON_RESET";
    public static final String SITE_OG_CHANGE = "SITE_OG_CHANGE";
    public static final String SITE_OG_RESET = "SITE_OG_RESET";
    public static final String SITE_DISPLAY_UPDATE = "SITE_DISPLAY_UPDATE";
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";
    public static final String PROFILE_DISPLAY_UPDATE = "PROFILE_DISPLAY_UPDATE";
    public static final String PROFILE_NOTIFICATION_UPDATE = "PROFILE_NOTIFICATION_UPDATE";
    public static final String INVITATION_ACCEPT = "INVITATION_ACCEPT";
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String NOTIFICATION_EVENT_UPDATE = "NOTIFICATION_EVENT_UPDATE";

    /** 모든 행위 이름 — 선언한 차례. 감사 로그 조회의 거르기 선택지(작업)가 이 목록을 원천으로 한다(상수를 더하면 자동으로 따라온다) */
    private static final List<String> ALL = collect();

    private AuditActions() {
    }

    public static List<String> all() {
        return ALL;
    }

    private static List<String> collect() {
        List<String> names = new ArrayList<>();
        for (Field field : AuditActions.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                try {
                    names.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("감사 행위 이름을 읽을 수 없다: " + field.getName(), e);
                }
            }
        }
        return List.copyOf(names);
    }
}
