package com.community.audit.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 감사 행위 → 보관 분류 표(docs/domain/commerce.md 13-4). {@link AuditActions} 의 <b>모든 행위가 이 표에 있어야 한다</b> — 새 행위를 더하면 여기에도 한 줄 더한다
 * (테스트 {@code AuditRetentionTest} 가 빠진 행위를 막는다). 표에 없는 행위(과거 기록의 옛 이름 등)는 일반 운영으로 남긴다.
 *
 * <ul>
 *   <li>돈 관련(5년): 돈이나 포인트가 움직이는 일 — 지금은 포인트 수동 조정. 감사 보관 기간을 바꾼 기록도 같이 오래 남긴다(기록을 지우는 범위를 정한 일이라).
 *       환불 실행·대사 불일치 처리·판매 켜기/끄기·충전 환불 승인·송금 완료는 그 기능(1~3단계)이 행위를 더할 때 여기에 넣는다.</li>
 *   <li>개인정보 열람·내려받기(2년): 개인정보가 보이는 화면을 연 일 — 지금은 회원 상세 열람({@code MEMBER_VIEW}, 이메일 전체 보기 포함).
 *       배송지 열람·배송 명단 내려받기·탈퇴 회원 연락처 열람은 그 기능이 더할 때 여기에 넣는다.</li>
 *   <li>나머지는 일반 운영(1년).</li>
 * </ul>
 */
public final class AuditRetention {

    private static final Set<String> MONEY = Set.of(
            AuditActions.POINT_ADJUST,
            AuditActions.AUDIT_RETENTION_UPDATE);

    private static final Set<String> PERSONAL_DATA = Set.of(
            AuditActions.MEMBER_VIEW);

    /** 일반 운영으로 <b>정한</b> 행위 — 빠뜨린 것과 구별하려고 나열한다 */
    private static final Set<String> GENERAL = Set.of(
            AuditActions.LOGIN_SUCCESS, AuditActions.LOGIN_FAILURE, AuditActions.LOGOUT, AuditActions.PERMISSION_DENIED,
            AuditActions.ROLE_CREATE, AuditActions.ROLE_UPDATE, AuditActions.ROLE_DELETE, AuditActions.ROLE_PERMISSIONS_CHANGE,
            AuditActions.OPERATOR_CREATE, AuditActions.OPERATOR_UPDATE, AuditActions.OPERATOR_STATUS_CHANGE, AuditActions.OPERATOR_REJECT,
            AuditActions.INVITATION_CREATE, AuditActions.INVITATION_CANCEL, AuditActions.GRADE_UPDATE,
            AuditActions.BATCH_TOGGLE, AuditActions.BATCH_SCHEDULE_CHANGE, AuditActions.BATCH_RUN,
            AuditActions.SITE_BASIC_UPDATE, AuditActions.SITE_LOGO_CHANGE, AuditActions.SITE_LOGO_REMOVE, AuditActions.SITE_ICON_CHANGE, AuditActions.SITE_ICON_RESET,
            AuditActions.SITE_OG_CHANGE, AuditActions.SITE_OG_RESET, AuditActions.SITE_DISPLAY_UPDATE, AuditActions.SITE_THEME_UPDATE,
            AuditActions.MAIL_SETTINGS_UPDATE, AuditActions.MAIL_SETTINGS_TEST,
            AuditActions.PROFILE_UPDATE, AuditActions.PROFILE_DISPLAY_UPDATE, AuditActions.PROFILE_NOTIFICATION_UPDATE,
            AuditActions.INVITATION_ACCEPT, AuditActions.PASSWORD_RESET_REQUEST, AuditActions.PASSWORD_RESET, AuditActions.PASSWORD_CHANGE,
            AuditActions.OPERATOR_NAME_DEDUP, AuditActions.NOTIFICATION_EVENT_UPDATE, AuditActions.MEMBER_EVENT_UPDATE,
            AuditActions.TEAM_CREATE, AuditActions.TEAM_UPDATE, AuditActions.TEAM_DELETE, AuditActions.TEAM_MOVE, AuditActions.TEAM_MEMBERS_UPDATE,
            AuditActions.NOTIFICATION_CHANNEL_CREATE, AuditActions.NOTIFICATION_CHANNEL_UPDATE, AuditActions.NOTIFICATION_CHANNEL_DELETE,
            AuditActions.NOTIFICATION_CHANNEL_TOGGLE, AuditActions.NOTIFICATION_CHANNEL_TEST,
            AuditActions.NOTIFICATION_MESSAGE_CREATE, AuditActions.NOTIFICATION_MESSAGE_UPDATE, AuditActions.NOTIFICATION_MESSAGE_DELETE,
            AuditActions.NOTIFICATION_MESSAGE_TARGET_UPDATE, AuditActions.NOTIFICATION_MESSAGE_TEST,
            AuditActions.NOTIFICATION_TEMPLATE_CREATE, AuditActions.NOTIFICATION_TEMPLATE_UPDATE, AuditActions.NOTIFICATION_TEMPLATE_DELETE,
            AuditActions.BOARD_DEFAULTS_UPDATE, AuditActions.BOARD_CREATE, AuditActions.BOARD_UPDATE, AuditActions.BOARD_ACTIVE, AuditActions.BOARD_ORDER,
            AuditActions.BOARD_DELETE, AuditActions.BOARD_STAFF_APPOINT, AuditActions.BOARD_STAFF_REMOVE,
            AuditActions.BOARD_PRIVACY_RETENTION_UPDATE, AuditActions.BOARD_PRIVACY_RUN,
            AuditActions.MENU_SET_CREATE, AuditActions.MENU_SET_UPDATE, AuditActions.MENU_SET_DELETE, AuditActions.MENU_TREE_UPDATE, AuditActions.ADMIN_MENU_UPDATE,
            AuditActions.POINT_SETTINGS_UPDATE, AuditActions.SOCIAL_PROVIDER_UPDATE, AuditActions.SOCIAL_PROVIDER_DELETE,
            AuditActions.MFA_ENROLL, AuditActions.MFA_REENROLL, AuditActions.MFA_RECOVERY_CODES_REGENERATE, AuditActions.MFA_FAILURE,
            AuditActions.MFA_RECOVERY_CODE_USED, AuditActions.MFA_TRUSTED_DEVICE_REVOKE, AuditActions.MFA_RESET, AuditActions.MFA_SETTINGS_UPDATE,
            AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE);

    private static final Map<String, AuditRetentionClass> TABLE = table();

    private AuditRetention() {
    }

    /** 행위의 보관 분류 — 표에 없으면 일반 운영 */
    public static AuditRetentionClass classify(String action) {
        return action == null ? AuditRetentionClass.GENERAL : TABLE.getOrDefault(action, AuditRetentionClass.GENERAL);
    }

    /** 표에 정해 둔 분류(없으면 비어 있다 — 빠진 행위 찾기용) */
    public static Optional<AuditRetentionClass> declared(String action) {
        return Optional.ofNullable(TABLE.get(action));
    }

    private static Map<String, AuditRetentionClass> table() {
        Map<String, AuditRetentionClass> out = new HashMap<>();
        for (Map.Entry<AuditRetentionClass, Set<String>> group : List.of(Map.entry(AuditRetentionClass.GENERAL, GENERAL),
                Map.entry(AuditRetentionClass.PERSONAL_DATA, PERSONAL_DATA), Map.entry(AuditRetentionClass.MONEY, MONEY))) {
            for (String action : group.getValue()) {
                if (out.put(action, group.getKey()) != null) {
                    throw new IllegalStateException("감사 행위가 보관 분류 둘에 있다: " + action);
                }
            }
        }
        return Map.copyOf(out);
    }
}
