package com.community.audit.dto;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 감사 기록의 행위 이름 — 운영자 기반(로그인·권한 거부·역할·운영자·초대), 등급 설정, 회원 상세 열람, 배치(켜기/끄기·cron·지금 실행),
 * 사이트 일반 설정(기본 정보·로고·아이콘·링크 미리보기 이미지·표시 기본값), 내 정보(닉네임·내 표시 형식·알림 설정·비밀번호 변경), 로그인 전 계정 흐름(초대 수락·비밀번호 재설정),
 * 관리자 알림(이벤트 설정·팀·알림 도구), 게시판 관리(기본 설정·게시판·순서·운영진·개인정보 보유기간), 메뉴 관리(공개 메뉴 그룹·트리, 관리자 사이트 메뉴), 포인트(수동 조정·정책)가 쓴다.
 * 공개 사이트(api)의 회원 행동(글·댓글·추천·신고)은 감사 기록이 아니라 업무 표에 남는다. 이름은 한 번 정하면 바꾸지 않는다(조회·필터가 기대는 값이다)
 */
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
    public static final String SITE_THEME_UPDATE = "SITE_THEME_UPDATE";
    /** 메일 서버 설정 저장 — 대상 SITE + {@code mail}, 바뀐 칸 이름만({@code fields}). 비밀번호 값·암호문은 싣지 않는다 */
    public static final String MAIL_SETTINGS_UPDATE = "MAIL_SETTINGS_UPDATE";
    /** 메일 서버 시험 발송 — 성공 여부·원인 코드만(받는 주소·서버 응답은 싣지 않는다) */
    public static final String MAIL_SETTINGS_TEST = "MAIL_SETTINGS_TEST";
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";
    public static final String PROFILE_DISPLAY_UPDATE = "PROFILE_DISPLAY_UPDATE";
    public static final String PROFILE_NOTIFICATION_UPDATE = "PROFILE_NOTIFICATION_UPDATE";
    public static final String INVITATION_ACCEPT = "INVITATION_ACCEPT";
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    /** 이름 유일 제약(V30)을 걸기 전에 같은 이름(대소문자 무시)이던 운영자의 이름을 마이그레이션이 고친 일 — 앱이 아니라 SQL 이 SYSTEM 으로 남긴다 */
    public static final String OPERATOR_NAME_DEDUP = "OPERATOR_NAME_DEDUP";
    public static final String NOTIFICATION_EVENT_UPDATE = "NOTIFICATION_EVENT_UPDATE";
    public static final String TEAM_CREATE = "TEAM_CREATE";
    public static final String TEAM_UPDATE = "TEAM_UPDATE";
    public static final String TEAM_DELETE = "TEAM_DELETE";
    public static final String TEAM_MOVE = "TEAM_MOVE";
    public static final String TEAM_MEMBERS_UPDATE = "TEAM_MEMBERS_UPDATE";
    public static final String NOTIFICATION_CHANNEL_CREATE = "NOTIFICATION_CHANNEL_CREATE";
    public static final String NOTIFICATION_CHANNEL_UPDATE = "NOTIFICATION_CHANNEL_UPDATE";
    public static final String NOTIFICATION_CHANNEL_DELETE = "NOTIFICATION_CHANNEL_DELETE";
    public static final String NOTIFICATION_CHANNEL_TOGGLE = "NOTIFICATION_CHANNEL_TOGGLE";
    public static final String NOTIFICATION_CHANNEL_TEST = "NOTIFICATION_CHANNEL_TEST";
    /** 알림 문구(대상 × 채널 × 언어) 만들기·고치기·지우기 — 바뀐 칸 이름만 */
    public static final String NOTIFICATION_MESSAGE_CREATE = "NOTIFICATION_MESSAGE_CREATE";
    public static final String NOTIFICATION_MESSAGE_UPDATE = "NOTIFICATION_MESSAGE_UPDATE";
    public static final String NOTIFICATION_MESSAGE_DELETE = "NOTIFICATION_MESSAGE_DELETE";
    /** 대상의 채널별 「쓸 문구」·특수 상황 규칙 저장 */
    public static final String NOTIFICATION_MESSAGE_TARGET_UPDATE = "NOTIFICATION_MESSAGE_TARGET_UPDATE";
    /** 문구 시험 발송(이메일)·시험 게시(Slack·Mattermost) — 받는 주소는 남기지 않는다 */
    public static final String NOTIFICATION_MESSAGE_TEST = "NOTIFICATION_MESSAGE_TEST";
    /** 알림 템플릿(메일·Slack·Mattermost) 만들기·고치기·지우기 — 바뀐 칸 이름만 */
    public static final String NOTIFICATION_TEMPLATE_CREATE = "NOTIFICATION_TEMPLATE_CREATE";
    public static final String NOTIFICATION_TEMPLATE_UPDATE = "NOTIFICATION_TEMPLATE_UPDATE";
    public static final String NOTIFICATION_TEMPLATE_DELETE = "NOTIFICATION_TEMPLATE_DELETE";
    /** 게시판 기본 설정(일반·신고 정책·보안·알림 화면) 저장 — 바뀐 칸만 */
    public static final String BOARD_DEFAULTS_UPDATE = "BOARD_DEFAULTS_UPDATE";
    public static final String BOARD_CREATE = "BOARD_CREATE";
    /** 게시판 기본 정보·정책·말머리·안내문 저장 — 바뀐 칸만 */
    public static final String BOARD_UPDATE = "BOARD_UPDATE";
    public static final String BOARD_ACTIVE = "BOARD_ACTIVE";
    public static final String BOARD_ORDER = "BOARD_ORDER";
    public static final String BOARD_DELETE = "BOARD_DELETE";
    public static final String BOARD_STAFF_APPOINT = "BOARD_STAFF_APPOINT";
    public static final String BOARD_STAFF_REMOVE = "BOARD_STAFF_REMOVE";
    /** 게시판 개인정보 보유기간 저장 — 바뀐 칸만 */
    public static final String BOARD_PRIVACY_RETENTION_UPDATE = "BOARD_PRIVACY_RETENTION_UPDATE";
    /** 게시판 개인정보 보유기간 「지금 실행」(축 하나) */
    public static final String BOARD_PRIVACY_RUN = "BOARD_PRIVACY_RUN";
    public static final String MENU_SET_CREATE = "MENU_SET_CREATE";
    /** 메뉴 그룹 정보(이름·영문명·위치·사용 여부·설명) 저장 — 바뀐 칸만 */
    public static final String MENU_SET_UPDATE = "MENU_SET_UPDATE";
    public static final String MENU_SET_DELETE = "MENU_SET_DELETE";
    /** 공개 사이트 메뉴 그룹의 트리 전체 교체 — 전·후 트리 요약 */
    public static final String MENU_TREE_UPDATE = "MENU_TREE_UPDATE";
    /** 관리자 사이트 메뉴(두 모드) 전체 교체 — 전·후 트리 요약 */
    public static final String ADMIN_MENU_UPDATE = "ADMIN_MENU_UPDATE";
    /** 회원 포인트 수동 지급·차감 — 대상 MEMBER + 회원 ID, 요청값·반영값·잔액·공개 사유 */
    public static final String POINT_ADJUST = "POINT_ADJUST";
    /** 포인트 정책 저장 — 바뀐 칸만 before/after */
    public static final String POINT_SETTINGS_UPDATE = "POINT_SETTINGS_UPDATE";
    /** 소셜 로그인 공급자 설정 등록·수정 — 대상 SOCIAL_PROVIDER + 공급자(KAKAO …). 켜짐만 before/after, 클라이언트 ID·시크릿은 「바뀜」 표시만(값 없음) */
    public static final String SOCIAL_PROVIDER_UPDATE = "SOCIAL_PROVIDER_UPDATE";
    /** 소셜 로그인 공급자 설정 삭제(그 공급자가 꺼지고 미등록이 된다) */
    public static final String SOCIAL_PROVIDER_DELETE = "SOCIAL_PROVIDER_DELETE";

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
