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
 *   <li>돈 관련(5년): 돈이나 포인트가 움직이는 일 — 지금은 포인트 수동 조정·포인트 상점 예외 환불. 감사 보관 기간을 바꾼 기록도 같이 오래 남긴다(기록을 지우는 범위를 정한 일이라).
 *       환불 실행·대사 불일치 처리·판매 켜기/끄기·충전 환불 승인·송금 완료는 그 기능(1~3단계)이 행위를 더할 때 여기에 넣는다.</li>
 *   <li>개인정보 열람·내려받기(2년): 개인정보가 보이는 화면을 연 일 — 지금은 회원 상세 열람({@code MEMBER_VIEW}, 이메일 전체 보기 포함).
 *       탈퇴 회원 연락처 열람({@code WITHDRAWN_CONTACT_REVEAL}, 0-4)도 여기다. 배송지 열람·배송 명단 내려받기는 그 기능이 더할 때 넣는다.</li>
 *   <li>나머지는 일반 운영(1년).</li>
 * </ul>
 */
public final class AuditRetention {

    private static final Set<String> MONEY = Set.of(
            AuditActions.POINT_ADJUST,
            AuditActions.AUDIT_RETENTION_UPDATE,
            // 포트원(결제·본인인증 PG) 설정 저장·삭제 — 결제를 받는 경로를 바꾸는 일이라 돈 관련(사용자 확정 2026-10-26, commerce-foundation-contract 「0-5」)
            AuditActions.PORTONE_SETTINGS_UPDATE, AuditActions.PORTONE_SETTINGS_DELETE,
            // 포인트 상점 구매 예외 환불 — 포인트가 움직이는 일(commerce-stage1-contract 「B」·「패키지」)
            AuditActions.POINT_SHOP_REFUND,
            // 정지 중 환불 신청 거절 — 돈을 돌려주지 않기로 한 결정이라 돈 관련(commerce-stage1-contract 「1-2 정지 중 환불 신청」)
            AuditActions.POINT_SHOP_REFUND_REJECT,
            // 결제 이상·대사 처리 — 돈이 움직이거나 돈의 기록을 바로잡는 일(commerce-payment-contract 결정 12)
            AuditActions.RECONCILE_ISSUE_RESOLVE, AuditActions.PAYMENT_CANCEL_RETRY, AuditActions.PAYMENT_CANCEL_MANUAL, AuditActions.PAYMENT_RECHECK,
            // 공구 중단(환불)·수량 조정·추가 차수·배송비 정책 — 돈이 움직이거나 판매 조건(값)을 바꾸는 일(commerce-groupbuy-contract 「감사·오류 코드 후보」)
            AuditActions.GROUPBUY_CANCEL, AuditActions.GROUPBUY_SUPPLY_PRICE, AuditActions.GROUPBUY_STOCK_ADD, AuditActions.GROUPBUY_STOCK_REDUCE, AuditActions.GROUPBUY_ROUND_CREATE,
            AuditActions.COMMERCE_SHIPPING_POLICY_CREATE,
            // 공구 환불·요청 승인·거절·반품 처리·공급 불가·계좌 송금 — 돈이 움직이거나 청약철회 기록(commerce-groupbuy-contract 결정 55·70)
            AuditActions.GROUPBUY_REFUND, AuditActions.GROUPBUY_REQUEST_APPROVE, AuditActions.GROUPBUY_REQUEST_REJECT, AuditActions.GROUPBUY_RETURN_DISPOSE,
            AuditActions.GROUPBUY_SUPPLY_FAILURE, AuditActions.GROUPBUY_BANK_TRANSFER,
            // 판매 스위치·공구 설정(문턱·보상 상한)·배송비 정책 줄 지우기 — 판매를 열고 닫거나 돈의 기준을 바꾸는 일(2b-4)
            AuditActions.COMMERCE_SALES_TOGGLE, AuditActions.GROUPBUY_SETTINGS_UPDATE, AuditActions.COMMERCE_SHIPPING_POLICY_DELETE);

    private static final Set<String> PERSONAL_DATA = Set.of(
            AuditActions.MEMBER_VIEW,
            AuditActions.WITHDRAWN_CONTACT_REVEAL,
            // 결제 주문 상세(회원 번호·닉네임 — commerce-payment-contract 결정 17)
            AuditActions.PAYMENT_ORDER_VIEW,
            // 공구 배송지 열람·배송 명단 내보내기(commerce.md 10-9)
            AuditActions.GROUPBUY_ADDRESS_REVEAL, AuditActions.GROUPBUY_SHIPPING_EXPORT,
            // 관리자 공구 주문 상세(회원 번호·닉네임 — 2b-4)
            AuditActions.GROUPBUY_ORDER_VIEW,
            // 관리자 공구 문의 목록·상세(비밀글 본문 — 2b-6)
            AuditActions.GROUPBUY_QNA_VIEW);

    /** 일반 운영으로 <b>정한</b> 행위 — 빠뜨린 것과 구별하려고 나열한다 */
    private static final Set<String> GENERAL = Set.of(
            AuditActions.LOGIN_SUCCESS, AuditActions.LOGIN_FAILURE, AuditActions.LOGOUT, AuditActions.PERMISSION_DENIED,
            AuditActions.ROLE_CREATE, AuditActions.ROLE_UPDATE, AuditActions.ROLE_DELETE, AuditActions.ROLE_PERMISSIONS_CHANGE,
            AuditActions.OPERATOR_CREATE, AuditActions.OPERATOR_UPDATE, AuditActions.OPERATOR_STATUS_CHANGE, AuditActions.OPERATOR_REJECT,
            AuditActions.INVITATION_CREATE, AuditActions.INVITATION_CANCEL, AuditActions.GRADE_UPDATE,
            AuditActions.BATCH_TOGGLE, AuditActions.BATCH_SCHEDULE_CHANGE, AuditActions.BATCH_RUN,
            AuditActions.SITE_BASIC_UPDATE, AuditActions.SITE_LOGO_CHANGE, AuditActions.SITE_LOGO_REMOVE, AuditActions.SITE_ICON_CHANGE, AuditActions.SITE_ICON_RESET,
            AuditActions.SITE_OG_CHANGE, AuditActions.SITE_OG_RESET, AuditActions.SITE_DISPLAY_UPDATE, AuditActions.SITE_THEME_UPDATE,
            AuditActions.SITE_THEME_MENU_UPDATE, AuditActions.HOME_LAYOUT_UPDATE,
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
            AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE,
            // 0-4 회원 기능 제한·해제 — 제재와 같은 운영 일(사용자 확정 2026-10-26)
            AuditActions.MEMBER_RESTRICTION_GRANT, AuditActions.MEMBER_RESTRICTION_LIFT,
            // develop(2026-10-26)의 새 행위 — 약관·닉네임 그림·인기글·하이라이트·토론·게시글·신고 관리. 글 삭제·복구가 포인트를 회수·되돌리지만 목적은 콘텐츠 관리이고
            // 포인트 이동의 기록은 포인트 원장이 가진다 — 일반 운영(commerce-foundation-contract 「0-5」 세션 정리 결정)
            AuditActions.TERMS_PUBLISH, AuditActions.TERMS_LINKS_UPDATE,
            AuditActions.NICKNAME_IMAGE_APPROVE, AuditActions.NICKNAME_IMAGE_REJECT, AuditActions.NICKNAME_IMAGE_REVOKE, AuditActions.NICKNAME_IMAGE_SETTINGS_UPDATE,
            AuditActions.POPULAR_SETTINGS_UPDATE, AuditActions.POPULAR_EXCLUDE, AuditActions.POPULAR_RESTORE,
            AuditActions.HIGHLIGHT_CREATE, AuditActions.HIGHLIGHT_UPDATE, AuditActions.HIGHLIGHT_DELETE, AuditActions.HIGHLIGHT_ORDER, AuditActions.HIGHLIGHT_VISIBLE,
            AuditActions.HIGHLIGHT_OPINION_PIN, AuditActions.HIGHLIGHT_OPINION_UNPIN, AuditActions.HIGHLIGHT_OPINION_HIDE, AuditActions.HIGHLIGHT_OPINION_UNHIDE,
            AuditActions.HIGHLIGHT_PIN_REQUEST_APPROVE, AuditActions.HIGHLIGHT_PIN_REQUEST_REJECT,
            AuditActions.DEBATE_OPEN, AuditActions.DEBATE_UPDATE, AuditActions.DEBATE_CLOSE,
            AuditActions.POST_HIDE, AuditActions.POST_UNHIDE, AuditActions.POST_DELETE, AuditActions.POST_RESTORE, AuditActions.POST_MOVE, AuditActions.POST_NOTICE,
            AuditActions.POST_PIN, AuditActions.POST_UNPIN, AuditActions.POST_BULK, AuditActions.POST_POLL_DELETE,
            AuditActions.REPORT_RESOLVE, AuditActions.REPORT_DISMISS, AuditActions.REPORT_BULK_DISMISS, AuditActions.REPORT_REOPEN,
            // 1-2 포인트 상점 품목 만들기·고치기·아바타 묶음 변경·설정 — 일반 운영(가격을 바꿔도 이미 산 구매는 사본 그대로라 돈이 움직이지 않는다)
            AuditActions.POINT_SHOP_ITEM_CREATE, AuditActions.POINT_SHOP_ITEM_UPDATE, AuditActions.POINT_SHOP_AVATAR_PRICE, AuditActions.POINT_SHOP_AVATAR_SALE,
            AuditActions.POINT_SHOP_SETTINGS_UPDATE,
            // 소모임 분류(이름·게시판 할당·순서) — 게시판 구성과 같은 운영 일
            AuditActions.GROUP_CATEGORY_CREATE, AuditActions.GROUP_CATEGORY_UPDATE, AuditActions.GROUP_CATEGORY_ORDER, AuditActions.GROUP_CATEGORY_DELETE,
            // 소모임 개설 승인·반려·비활성화·다시 열기 · 소모임 설정 네 구획 — 콘텐츠·커뮤니티 운영 일(분류와 같다, 돈·개인정보가 움직이지 않는다)
            AuditActions.GROUP_APPROVE, AuditActions.GROUP_REJECT, AuditActions.GROUP_DEACTIVATE, AuditActions.GROUP_REACTIVATE,
            AuditActions.GROUP_CREATION_RULE_UPDATE, AuditActions.GROUP_MANAGER_RULE_UPDATE, AuditActions.GROUP_VISIBILITY_RULE_UPDATE,
            AuditActions.GROUP_POLICY_RULE_UPDATE,
            // 이 날 다시 대사 — 대사 배치를 다시 돌릴 뿐 돈·기록을 바꾸지 않는다(commerce-payment-contract 확정한 목업 가정 3 — 사유 없이 확인창만)
            AuditActions.PAYMENT_RECONCILE_RERUN,
            // 공구 등록·수정·모집 시작·조기 마감·발송 지연 공지 — 일반 운영(commerce-groupbuy-contract 「감사·오류 코드 후보」 — 돈은 중단·환불이 움직인다)
            AuditActions.GROUPBUY_CREATE, AuditActions.GROUPBUY_UPDATE, AuditActions.GROUPBUY_START, AuditActions.GROUPBUY_CLOSE_EARLY, AuditActions.GROUPBUY_DELAY_NOTICE,
            AuditActions.GROUPBUY_PRODUCT_SAVE, AuditActions.GROUPBUY_CATEGORY_SAVE, AuditActions.GROUPBUY_OPTION_TYPE_SAVE,
            // 발주·발송·배송완료·택배사 — 일반 운영(돈은 환불이 움직인다)
            AuditActions.GROUPBUY_FULFILLMENT, AuditActions.COMMERCE_CARRIER_SAVE,
            // 운영 체크 항목·사업자 정보·주문 메모 — 일반 운영(판매를 켜는 일은 스위치가 돈 관련으로 남긴다)
            AuditActions.COMMERCE_SALES_CHECKLIST, AuditActions.COMMERCE_BUSINESS_INFO_UPDATE, AuditActions.GROUPBUY_ORDER_MEMO,
            // 공구 문의·리뷰 숨김·답변(답글) 수정(2b-6)
            AuditActions.GROUPBUY_QNA_HIDE, AuditActions.GROUPBUY_QNA_ANSWER_EDIT, AuditActions.GROUPBUY_REVIEW_HIDE, AuditActions.GROUPBUY_REVIEW_REPLY_EDIT);

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
