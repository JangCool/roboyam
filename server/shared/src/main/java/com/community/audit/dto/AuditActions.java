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
    /** 테마 하나의 메뉴 설정 저장 — 대상 SITE/1, {@code theme} + 바뀐 칸의 {@code before}/{@code after} */
    public static final String SITE_THEME_MENU_UPDATE = "SITE_THEME_MENU_UPDATE";
    /** 테마 하나의 홈 구성 저장 — 대상 SITE/1, {@code theme} + 칸 수·가젯 수 전후와 넣은·뺀 가젯 수 */
    public static final String HOME_LAYOUT_UPDATE = "HOME_LAYOUT_UPDATE";
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
    /** 회원 이벤트(사용·앱 알림·이메일·회원이 끌 수 있음) 저장 — 바뀐 칸만 */
    public static final String MEMBER_EVENT_UPDATE = "MEMBER_EVENT_UPDATE";
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
    // ── 관리자 2차 인증(TOTP, docs/features/commerce-foundation-contract.md 「0-3」) — 비밀키·코드·복구 코드·신뢰 기기 쿠키 값은 detail 에 싣지 않는다 ──
    /** OTP 등록 확정(강제 등록·자발 등록) — 대상 OPERATOR(본인) */
    public static final String MFA_ENROLL = "MFA_ENROLL";
    /** OTP 다시 등록 확정 — 옛 비밀키·복구 코드·신뢰 기기 무효, {@code trustedDevicesRevoked} */
    public static final String MFA_REENROLL = "MFA_REENROLL";
    /** 복구 코드 새로 만들기 — 옛 코드 모두 무효 */
    public static final String MFA_RECOVERY_CODES_REGENERATE = "MFA_RECOVERY_CODES_REGENERATE";
    /** 2차 인증 코드 실패 — {@code reason}(CODE_INVALID · CODE_REUSED · RECOVERY_CODE_INVALID), {@code stage}(LOGIN · ENROLL · RECOVERY_CODES) */
    public static final String MFA_FAILURE = "MFA_FAILURE";
    /** 복구 코드로 로그인 — {@code recoveryCodesLeft} */
    public static final String MFA_RECOVERY_CODE_USED = "MFA_RECOVERY_CODE_USED";
    /** 신뢰 기기 끊기 — {@code count}, {@code all}, 끊은 까닭 {@code cause}(SELF · PASSWORD_CHANGE · PASSWORD_RESET · REENROLL · ACCOUNT_LOCKED · MFA_RESET) */
    public static final String MFA_TRUSTED_DEVICE_REVOKE = "MFA_TRUSTED_DEVICE_REVOKE";
    /** OTP 초기화 — {@code via} = ADMIN(루트가 화면에서) · SERVER_COMMAND(설치 폴더의 초기화 명령, DB 함수 admin_mfa_reset 이 SYSTEM 으로 남긴다) */
    public static final String MFA_RESET = "MFA_RESET";
    /** 로그인 보안 설정(사용·유예 일수·기기 신뢰 개월·실패 한도) 저장 — 대상 LOGIN_SECURITY, 바뀐 칸만 before/after */
    public static final String MFA_SETTINGS_UPDATE = "MFA_SETTINGS_UPDATE";
    // ── 커머스 0-5(돈 요청 보호·감사 보관, docs/features/commerce-foundation-contract.md 「0-5」) ──
    /** 커머스 설정 › 요청 제한(요청 종류별 1분 한도) 저장 — 대상 COMMERCE_SETTINGS/{@code request-limits}, 바뀐 종류만 before/after */
    public static final String COMMERCE_REQUEST_LIMIT_UPDATE = "COMMERCE_REQUEST_LIMIT_UPDATE";
    /** 감사 보관 기간(분류별 일수) 저장 — 최고 관리자만, 대상 AUDIT_RETENTION/{@code audit-log-retention}, 바뀐 분류만 before/after. 보관 분류 MONEY(5년) */
    public static final String AUDIT_RETENTION_UPDATE = "AUDIT_RETENTION_UPDATE";
    /** 약관 새 버전 게시 — 대상 TERMS(대상 ID = 종류 TERMS·PRIVACY), after {@code version}·{@code effectiveDate} (docs/features/terms-contract.md) */
    public static final String TERMS_PUBLISH = "TERMS_PUBLISH";
    /** 가입·로그인 화면 약관 「보기」 링크 표시 저장 — 대상 SITE, 바뀐 때만 before/after {@code showLinks} */
    public static final String TERMS_LINKS_UPDATE = "TERMS_LINKS_UPDATE";
    // ── 닉네임 그림 검토(docs/features/member-social-contract.md 「닉네임 그림」) — 대상 NICKNAME_IMAGE + 신청 ID, detail 에 회원 ID·사유 ──
    /** 닉네임 그림 승인 — 회원의 지금 그림이 된다(앞 그림은 교체) */
    public static final String NICKNAME_IMAGE_APPROVE = "NICKNAME_IMAGE_APPROVE";
    /** 닉네임 그림 반려 — {@code reason} */
    public static final String NICKNAME_IMAGE_REJECT = "NICKNAME_IMAGE_REJECT";
    /** 적용 중인 닉네임 그림 해제 — {@code reason} */
    public static final String NICKNAME_IMAGE_REVOKE = "NICKNAME_IMAGE_REVOKE";
    /** 닉네임 그림 신청 조건(방식·최소 등급·일수·활동·포인트·이메일 인증) 저장 — 대상 NICKNAME_IMAGE_SETTINGS, 바뀐 칸만 before/after */
    public static final String NICKNAME_IMAGE_SETTINGS_UPDATE = "NICKNAME_IMAGE_SETTINGS_UPDATE";
    // ── 인기글·하이라이트·토론(docs/features/popular-highlight-contract.md) ──
    /** 인기글 설정 저장 — 대상 POPULAR_SETTINGS + {@code popular}, 바뀐 칸만 before/after, 다시 계산 요청 여부 {@code recomputeRequested} */
    public static final String POPULAR_SETTINGS_UPDATE = "POPULAR_SETTINGS_UPDATE";
    /** 인기글에서 빼기 — 대상 POST + 글 ID, {@code reason}·{@code memo} */
    public static final String POPULAR_EXCLUDE = "POPULAR_EXCLUDE";
    /** 인기글에 다시 넣기 — 대상 POST + 글 ID, 뺐던 {@code reason} */
    public static final String POPULAR_RESTORE = "POPULAR_RESTORE";
    /** 하이라이트 만들기 — 대상 HIGHLIGHT + 하이라이트 ID, {@code postId}·칸 값 */
    public static final String HIGHLIGHT_CREATE = "HIGHLIGHT_CREATE";
    /** 하이라이트 고치기 — 바뀐 칸만 before/after */
    public static final String HIGHLIGHT_UPDATE = "HIGHLIGHT_UPDATE";
    /** 하이라이트 지우기 — {@code postId} */
    public static final String HIGHLIGHT_DELETE = "HIGHLIGHT_DELETE";
    /** 하이라이트 순서 바꾸기 — 대상 HIGHLIGHT + {@code order}, before/after ID 차례 */
    public static final String HIGHLIGHT_ORDER = "HIGHLIGHT_ORDER";
    /** 하이라이트 노출 켜기·끄기 — from/to */
    public static final String HIGHLIGHT_VISIBLE = "HIGHLIGHT_VISIBLE";
    /** 토론 의견 고정·고정 해제·숨김·숨김 해제(이름은 예전 하이라이트 의견 관리 그대로 — 기록 호환) — 대상 COMMENT + 댓글 ID, {@code postId}·{@code stance} */
    public static final String HIGHLIGHT_OPINION_PIN = "HIGHLIGHT_OPINION_PIN";
    public static final String HIGHLIGHT_OPINION_UNPIN = "HIGHLIGHT_OPINION_UNPIN";
    public static final String HIGHLIGHT_OPINION_HIDE = "HIGHLIGHT_OPINION_HIDE";
    public static final String HIGHLIGHT_OPINION_UNHIDE = "HIGHLIGHT_OPINION_UNHIDE";
    /** 의견 고정 요청 승인·거절(운영자) — 대상 COMMENT + 댓글 ID, {@code postId}·{@code requestId}·{@code requesterId}·거절이면 {@code reason} */
    public static final String HIGHLIGHT_PIN_REQUEST_APPROVE = "HIGHLIGHT_PIN_REQUEST_APPROVE";
    public static final String HIGHLIGHT_PIN_REQUEST_REJECT = "HIGHLIGHT_PIN_REQUEST_REJECT";
    // ── 글 찬반 토론(docs/features/post-debate-contract.md) ──
    /** 글의 토론 열기(관리자 토론 PUT · 하이라이트 만들기·고치기) — 대상 POST + 글 ID, {@code after}(neutralEnabled·stanceChangeAllowed·openedBy), 하이라이트에서 했으면 {@code via HIGHLIGHT} */
    public static final String DEBATE_OPEN = "DEBATE_OPEN";
    /** 글의 토론 설정 바꾸기 — 바뀐 칸만 before/after */
    public static final String DEBATE_UPDATE = "DEBATE_UPDATE";
    /** 글의 토론 닫기(행 지움 — 입장·의견·고정은 그대로) — {@code before} */
    public static final String DEBATE_CLOSE = "DEBATE_CLOSE";
    // ── 게시글 관리(docs/features/post-admin-contract.md) — 대상 POST + 글 ID, {@code reason}(운영자가 적은 사유)과 조치별 덧붙임 ──
    /** 숨기기 — {@code notifyAuthor} */
    public static final String POST_HIDE = "POST_HIDE";
    /** 숨김 해제 — 풀기 전 {@code hiddenReason} */
    public static final String POST_UNHIDE = "POST_UNHIDE";
    /** 삭제 — 회수한 포인트 {@code revokedPoints} */
    public static final String POST_DELETE = "POST_DELETE";
    /** 복구 — 되돌려 준 포인트 {@code restoredPoints} */
    public static final String POST_RESTORE = "POST_RESTORE";
    /** 이동 — {@code from}/{@code to}(boardId·prefixId) */
    public static final String POST_MOVE = "POST_MOVE";
    /** 공지 지정(공통 「공지」 말머리 + 고정) — {@code from}/{@code to}(prefixId) */
    public static final String POST_NOTICE = "POST_NOTICE";
    public static final String POST_PIN = "POST_PIN";
    public static final String POST_UNPIN = "POST_UNPIN";
    /** 일괄 숨김·해제·삭제 — 대상 POST + {@code bulk}, {@code action}·{@code done}·{@code failed}(글 ID 목록) */
    public static final String POST_BULK = "POST_BULK";
    /** 글의 투표 지우기(표가 있어도 — docs/features/post-poll-contract.md) — {@code reason}·{@code pollId}·{@code question}·{@code voterCount}(누가 골랐는지는 남기지 않는다) */
    public static final String POST_POLL_DELETE = "POST_POLL_DELETE";
    // ── 신고 관리(docs/features/report-admin-contract.md) — 대상 REPORT_CASE + 사건 ID ──
    /** 사건 조치(숨김·삭제·자동 숨김 확정) — {@code caseNo}·{@code action}·{@code reasonCode}·{@code note}·{@code targetType}·{@code targetId}·{@code reportCount} */
    public static final String REPORT_RESOLVE = "REPORT_RESOLVE";
    /** 사건 무시 — {@code caseNo}·{@code falseReport}·{@code reasonCode}·{@code note}·자동 숨김을 풀었으면 {@code restored} */
    public static final String REPORT_DISMISS = "REPORT_DISMISS";
    /** 일괄 무시 — 대상 REPORT_CASE + {@code bulk}, {@code falseReport}·{@code note}·{@code done}·{@code failed}(사건 ID 목록) */
    public static final String REPORT_BULK_DISMISS = "REPORT_BULK_DISMISS";
    /** 무시한 사건 다시 열기 — {@code caseNo} */
    public static final String REPORT_REOPEN = "REPORT_REOPEN";

    // ── 커머스 0-4 (본인인증·기능 제한·탈퇴 대비) ──
    /** 기능 제한 부여 — 대상 MEMBER, {@code restrictionId}·{@code source}(OPERATOR · AUTO — 자동은 행위자 SYSTEM)·{@code type}·{@code until}(무기한이면 {@code indefinite})·{@code autoRule} */
    public static final String MEMBER_RESTRICTION_GRANT = "MEMBER_RESTRICTION_GRANT";
    /** 기능 제한 해제 — 대상 MEMBER, {@code restrictionId}·{@code type}·해제 사유 {@code reason} */
    public static final String MEMBER_RESTRICTION_LIFT = "MEMBER_RESTRICTION_LIFT";
    /** 탈퇴 회원 연락처 열람 — 대상 MEMBER, 사유 {@code reason}(이메일은 싣지 않는다). 감사 보관 분류는 개인정보 열람(0-5 와 합칠 때) */
    public static final String WITHDRAWN_CONTACT_REVEAL = "WITHDRAWN_CONTACT_REVEAL";
    /** 포트원 설정 저장 — 대상 PORTONE_SETTINGS, 바꾼 칸 이름 {@code fields}(값 없음) */
    public static final String PORTONE_SETTINGS_UPDATE = "PORTONE_SETTINGS_UPDATE";
    /** 포트원 설정 칸 지우기 — 대상 PORTONE_SETTINGS, 칸 이름 {@code field} */
    public static final String PORTONE_SETTINGS_DELETE = "PORTONE_SETTINGS_DELETE";

    // ── 커머스 1-2 포인트 상점(commerce-stage1-contract 「B」) — 대상 POINT_SHOP_ITEM·POINT_SHOP_AVATARS·POINT_SHOP_SETTINGS·POINT_SHOP_PURCHASE ──
    /** 품목 만들기 — 대상 POINT_SHOP_ITEM + 품목 ID, {@code code}·{@code kind}·{@code after}(칸 값) */
    public static final String POINT_SHOP_ITEM_CREATE = "POINT_SHOP_ITEM_CREATE";
    /** 품목 고치기 — 바뀐 칸만 {@code before}/{@code after} */
    public static final String POINT_SHOP_ITEM_UPDATE = "POINT_SHOP_ITEM_UPDATE";
    /** 아바타 묶음 가격 — 대상 POINT_SHOP_AVATARS + 범위, {@code scope}·{@code categories}|{@code itemCount}·{@code matched}·{@code changed}·{@code price} */
    public static final String POINT_SHOP_AVATAR_PRICE = "POINT_SHOP_AVATAR_PRICE";
    /** 아바타 묶음 판매 켜기·끄기 — {@code scope}·{@code matched}·{@code changed}·{@code active} */
    public static final String POINT_SHOP_AVATAR_SALE = "POINT_SHOP_AVATAR_SALE";
    /** 포인트 상점 설정(셀프 환불 기한) — {@code before}/{@code after} */
    public static final String POINT_SHOP_SETTINGS_UPDATE = "POINT_SHOP_SETTINGS_UPDATE";
    /** 구매 예외 환불(운영자 pointshop.refund) — 대상 POINT_SHOP_PURCHASE + 구매 ID, {@code userId}·{@code itemId}·{@code itemCode}·{@code refundedPoints}·{@code reason}·{@code selfRefundable}. 돈 관련(5년) */
    public static final String POINT_SHOP_REFUND = "POINT_SHOP_REFUND";
    /** 정지 중 환불 신청 거절(운영자 pointshop.refund — V87) — 대상 POINT_SHOP_PURCHASE + 구매 ID, {@code requestId}·{@code userId}·{@code itemId}·{@code itemCode}·{@code reason}. 돈 관련(5년). 수락은 {@link #POINT_SHOP_REFUND}({@code kind REQUEST}) */
    public static final String POINT_SHOP_REFUND_REJECT = "POINT_SHOP_REFUND_REJECT";

    // ── 소모임 분류(docs/features/group-contract.md) — 대상 GROUP_CATEGORY + 분류 ID(순서는 order) ──
    /** 분류 만들기 — {@code after.name} */
    public static final String GROUP_CATEGORY_CREATE = "GROUP_CATEGORY_CREATE";
    /** 분류 저장 — 바뀐 칸만 {@code before}/{@code after}({@code name}·{@code boards}(게시판 slug, 할당 순서)), 다른 분류에서 옮겨 온 게시판 {@code moved}(「slug (이전 분류)」) */
    public static final String GROUP_CATEGORY_UPDATE = "GROUP_CATEGORY_UPDATE";
    /** 분류 순서 — {@code before}/{@code after} 의 {@code order}(분류 이름) */
    public static final String GROUP_CATEGORY_ORDER = "GROUP_CATEGORY_ORDER";
    /** 분류 지우기 — {@code deleted}(이름), 「분류 없음」이 된 게시판 {@code boards}(slug) */
    public static final String GROUP_CATEGORY_DELETE = "GROUP_CATEGORY_DELETE";

    // ── 소모임(docs/features/group-contract.md 1단계) — 대상 GROUP + 소모임 ID, 설정은 GROUP_SETTINGS + 구획 ──
    /** 개설 승인 — {@code name}·{@code requesterId} */
    public static final String GROUP_APPROVE = "GROUP_APPROVE";
    /** 개설 반려 — {@code name}·{@code reason}(신청자에게 간 사유) */
    public static final String GROUP_REJECT = "GROUP_REJECT";
    /** 비활성화 — {@code name}·{@code reason}(모임장에게 간 사유) */
    public static final String GROUP_DEACTIVATE = "GROUP_DEACTIVATE";
    /** 다시 열기 — {@code name}·{@code memo}(운영자만 보는 메모, 없으면 빈 글자) */
    public static final String GROUP_REACTIVATE = "GROUP_REACTIVATE";
    /** 개설 규칙 저장 — 바뀐 칸만 {@code before}/{@code after}(creationMode·minGrade·maxPerOwner) */
    public static final String GROUP_CREATION_RULE_UPDATE = "GROUP_CREATION_RULE_UPDATE";
    /** 매니저 규칙 저장 — 바뀐 칸만 {@code before}/{@code after}(managersEnabled·limitDefault·limitMax) */
    public static final String GROUP_MANAGER_RULE_UPDATE = "GROUP_MANAGER_RULE_UPDATE";
    /** 공개 범위 설정 저장 — 바뀐 칸만 {@code before}/{@code after}(visibilityEnabled·cooldownDays·retryHours·rejectLimit·dailyLimit·textMax) */
    public static final String GROUP_VISIBILITY_RULE_UPDATE = "GROUP_VISIBILITY_RULE_UPDATE";
    /** 운영 한도 저장 — 바뀐 칸만 {@code before}/{@code after}(여덟 칸) */
    public static final String GROUP_POLICY_RULE_UPDATE = "GROUP_POLICY_RULE_UPDATE";
    // ── 커머스 2a 결제 모듈(commerce-payment-contract 결정 12·17 — 관리자 API 2a-3) — 대상 PAYMENT_ORDER·PAYMENT_CANCEL·RECONCILIATION_ISSUE·PAYMENT_RECONCILE_DAY ──
    /** 대사 불일치 처리 — 대상 RECONCILIATION_ISSUE + 이슈 ID, {@code type}·{@code paymentId}·{@code note}(사유). 돈 관련(5년) */
    public static final String RECONCILE_ISSUE_RESOLVE = "RECONCILE_ISSUE_RESOLVE";
    /** 멈춘(GAVE_UP) 취소 다시 시도 — 대상 PAYMENT_CANCEL + 취소 ID, {@code paymentId}·{@code amount}·{@code note}. 돈 관련 */
    public static final String PAYMENT_CANCEL_RETRY = "PAYMENT_CANCEL_RETRY";
    /** 멈춘 취소를 수동 처리 완료로 닫기 — 대상 PAYMENT_CANCEL + 취소 ID, {@code paymentId}·{@code amount}·{@code note}. 돈 관련 */
    public static final String PAYMENT_CANCEL_MANUAL = "PAYMENT_CANCEL_MANUAL";
    /** 포트원에서 다시 확인(확정 코드를 관리자가 부름) — 대상 PAYMENT_ORDER + 결제 주문 ID, {@code paymentId}·{@code outcome}·{@code note}. 돈 관련 */
    public static final String PAYMENT_RECHECK = "PAYMENT_RECHECK";
    /** 결제 주문 상세 열기(회원 번호·닉네임이 보인다) — 대상 PAYMENT_ORDER + 결제 주문 ID. 개인정보 열람(2년) */
    public static final String PAYMENT_ORDER_VIEW = "PAYMENT_ORDER_VIEW";
    /** 이 날 다시 대사(대사 배치 요청 — 사유 없음) — 대상 PAYMENT_RECONCILE_DAY + KST 날짜. 일반(돈을 움직이지 않는다) */
    public static final String PAYMENT_RECONCILE_RERUN = "PAYMENT_RECONCILE_RERUN";
    // ── 커머스 2b 공구(commerce-groupbuy-contract 「감사·오류 코드 후보」) — 대상 GROUP_BUY·SHIPPING_POLICY ──
    /** 공구 등록(DRAFT) — 대상 GROUP_BUY + 공구 ID, {@code title}·{@code roundNo}. 일반 */
    public static final String GROUPBUY_CREATE = "GROUPBUY_CREATE";
    /** 공구 수정 — DRAFT 는 모든 칸, 모집 뒤는 설명·이미지만, {@code revisionNo}. 일반 */
    public static final String GROUPBUY_UPDATE = "GROUPBUY_UPDATE";
    /** 모집 시작(공개 — 시작 시각에 모집) — {@code startAt}·{@code endAt}. 일반 */
    public static final String GROUPBUY_START = "GROUPBUY_START";
    /** 조기 마감 — {@code reason}. 일반 */
    public static final String GROUPBUY_CLOSE_EARLY = "GROUPBUY_CLOSE_EARLY";
    /** 공구 중단 — {@code reason}·환불 대상 건수·금액·이미 발송 건수. 돈 관련 */
    public static final String GROUPBUY_CANCEL = "GROUPBUY_CANCEL";
    /** 추가 입고 — {@code skuId}·{@code qty}·{@code reason}·{@code before}/{@code after}(최대). 돈 관련 */
    public static final String GROUPBUY_STOCK_ADD = "GROUPBUY_STOCK_ADD";
    /** 수량 줄이기 — {@code skuId}·{@code qty}·{@code reason}·{@code before}/{@code after}. 돈 관련 */
    public static final String GROUPBUY_STOCK_REDUCE = "GROUPBUY_STOCK_REDUCE";
    /** 추가 차수 만들기 — 대상 새 공구, {@code sourceId}·{@code roundNo}. 돈 관련 */
    public static final String GROUPBUY_ROUND_CREATE = "GROUPBUY_ROUND_CREATE";
    /** 발송 지연 공지 — {@code from}/{@code to}(예정일)·{@code reason}. 일반 */
    public static final String GROUPBUY_DELAY_NOTICE = "GROUPBUY_DELAY_NOTICE";
    /** 공구 공급가 고침(모집 뒤 — 결정 89) — {@code skuId}·{@code before}/{@code after}·{@code marginConfirmed}. 돈 관련 */
    public static final String GROUPBUY_SUPPLY_PRICE = "GROUPBUY_SUPPLY_PRICE";
    /** 상품 등록·수정(결정 89) — 대상 GROUP_BUY_PRODUCT + 상품 ID, {@code stage}·{@code name}. 일반 */
    public static final String GROUPBUY_PRODUCT_SAVE = "GROUPBUY_PRODUCT_SAVE";
    /** 공구 카테고리 등록·수정(결정 89) — 대상 GROUP_BUY_CATEGORY + ID, {@code name}·{@code hidden}. 일반 */
    public static final String GROUPBUY_CATEGORY_SAVE = "GROUPBUY_CATEGORY_SAVE";
    /** 공구 옵션 종류 저장(2b-1 사용자 결정) — 대상 GROUP_BUY_OPTION_TYPE + 코드(목록 통째면 ALL), {@code codes} 또는 {@code name}·{@code hidden}. 일반 */
    public static final String GROUPBUY_OPTION_TYPE_SAVE = "GROUPBUY_OPTION_TYPE_SAVE";
    // ── 2b-3 공구 발송·요청·환불 — 대상 GROUP_BUY_ORDER(주문 ID)·GROUP_BUY_REQUEST(요청 ID)·GROUP_BUY(공구 ID)·COMMERCE_BANK_TRANSFER·COMMERCE_CARRIER ──
    /** 발주·준비중·발송·배송완료·송장 정정 — {@code step}·건수. 일반 */
    public static final String GROUPBUY_FULFILLMENT = "GROUPBUY_FULFILLMENT";
    /** 운영 환불(요청 없이 — 결정 32)·승인 대기 승인 — {@code amount}·{@code reason}·{@code sellerFault}. 돈 관련 */
    public static final String GROUPBUY_REFUND = "GROUPBUY_REFUND";
    /** 취소·반품 요청 승인·완료(환불) — {@code amount}·{@code previewAmount}·{@code compensation}. 돈 관련 */
    public static final String GROUPBUY_REQUEST_APPROVE = "GROUPBUY_REQUEST_APPROVE";
    /** 요청 거절 — {@code reasonCode}·근거 사진 수(계약·청약철회 기록 5년 — 결정 55). 돈 관련 */
    public static final String GROUPBUY_REQUEST_REJECT = "GROUPBUY_REQUEST_REJECT";
    /** 반품 처리 방법(다시 판매 포함 — 결정 15) — 줄마다 방법·수량. 돈 관련 */
    public static final String GROUPBUY_RETURN_DISPOSE = "GROUPBUY_RETURN_DISPOSE";
    /** 단품 공급 불가 처리(결정 53) — {@code skuId}·환불 건수·선택을 묻는 건수. 돈 관련 */
    public static final String GROUPBUY_SUPPLY_FAILURE = "GROUPBUY_SUPPLY_FAILURE";
    /** 계좌 송금 전환·송금 완료(결정 6·67) — {@code step}·{@code amount}(계좌는 싣지 않는다). 돈 관련 */
    public static final String GROUPBUY_BANK_TRANSFER = "GROUPBUY_BANK_TRANSFER";
    /** 배송지 열람(사유 — 10-9) — {@code reason}. 개인정보 열람 */
    public static final String GROUPBUY_ADDRESS_REVEAL = "GROUPBUY_ADDRESS_REVEAL";
    /** 배송 명단 내보내기 — {@code rowCount}·{@code reason}(평문 없음). 개인정보 열람 */
    public static final String GROUPBUY_SHIPPING_EXPORT = "GROUPBUY_SHIPPING_EXPORT";
    /** 택배사 목록 저장(결정 11) — {@code codes}. 일반 */
    public static final String COMMERCE_CARRIER_SAVE = "COMMERCE_CARRIER_SAVE";
    /** 배송비 정책 줄 추가(예약) — 대상 SHIPPING_POLICY + 줄 ID, {@code code}·{@code effectiveFrom}·{@code before}/{@code after}. 돈 관련 */
    public static final String COMMERCE_SHIPPING_POLICY_CREATE = "COMMERCE_SHIPPING_POLICY_CREATE";
    // ── 2b-4 판매 스위치·사업자 정보·공구 설정·주문 조회 — 대상 COMMERCE_SETTINGS(sales·business-info·group-buy-settings)·SHIPPING_POLICY·GROUP_BUY_ORDER ──
    /** 판매 스위치 켜기·끄기(commerce.md 20절) — {@code before}·{@code after}·{@code reason}. 돈 관련 */
    public static final String COMMERCE_SALES_TOGGLE = "COMMERCE_SALES_TOGGLE";
    /** 운영 체크 항목 체크·해제 — {@code key}·{@code checked}. 일반 */
    public static final String COMMERCE_SALES_CHECKLIST = "COMMERCE_SALES_CHECKLIST";
    /** 사업자 정보 저장(결정 18) — 바뀐 칸 이름 {@code fields}. 일반 */
    public static final String COMMERCE_BUSINESS_INFO_UPDATE = "COMMERCE_BUSINESS_INFO_UPDATE";
    /** 공구 설정 저장(결정 10) — 바뀐 칸만 {@code before}/{@code after}. 돈 관련(문턱·보상 상한·선점 시간) */
    public static final String GROUPBUY_SETTINGS_UPDATE = "GROUPBUY_SETTINGS_UPDATE";
    /** 아직 시작하지 않은 배송비 정책 줄 지우기 — 대상 SHIPPING_POLICY + 줄 ID, {@code code}·{@code effectiveFrom}. 돈 관련 */
    public static final String COMMERCE_SHIPPING_POLICY_DELETE = "COMMERCE_SHIPPING_POLICY_DELETE";
    /** 공구 주문 운영 메모 저장 — 글자 수만. 일반 */
    public static final String GROUPBUY_ORDER_MEMO = "GROUPBUY_ORDER_MEMO";
    /** 관리자 공구 주문 상세 열람(회원 번호·닉네임 — 결제 주문 상세와 같다). 개인정보 열람 */
    public static final String GROUPBUY_ORDER_VIEW = "GROUPBUY_ORDER_VIEW";

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
