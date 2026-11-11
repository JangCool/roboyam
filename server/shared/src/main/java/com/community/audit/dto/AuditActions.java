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
    /** 캐시 관리(cache-admin-contract) — 지금 갱신 · 전체 갱신 · TTL 변경 · 캐시 켜기/끄기 · 전체 캐시 켜기/끄기 */
    public static final String CACHE_REFRESH = "CACHE_REFRESH";
    public static final String CACHE_REFRESH_ALL = "CACHE_REFRESH_ALL";
    public static final String CACHE_TTL_CHANGE = "CACHE_TTL_CHANGE";
    public static final String CACHE_ENABLED_CHANGE = "CACHE_ENABLED_CHANGE";
    public static final String CACHE_GLOBAL_ENABLED_CHANGE = "CACHE_GLOBAL_ENABLED_CHANGE";
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
    /** 공지 띠 설정 저장(설정 > 일반 「공지 띠」) — 대상 SITE/1, 바뀐 칸의 {@code before}/{@code after}(바뀐 때만) */
    public static final String SITE_NOTICE_BAR_UPDATE = "SITE_NOTICE_BAR_UPDATE";
    /** 팝업 만들기(콘텐츠 > 팝업 관리, site-popup-contract) — 대상 SITE_POPUP + 팝업 ID, {@code after} 칸 값 */
    public static final String SITE_POPUP_CREATE = "SITE_POPUP_CREATE";
    /** 팝업 고치기 — 바뀐 칸의 {@code before}/{@code after}(바뀐 때만) */
    public static final String SITE_POPUP_UPDATE = "SITE_POPUP_UPDATE";
    /** 팝업 지우기 — {@code before} 칸 값 */
    public static final String SITE_POPUP_DELETE = "SITE_POPUP_DELETE";
    /** 팝업 차례 바꾸기 — 대상 SITE_POPUP + {@code order}, before/after ID 차례(바뀐 때만) */
    public static final String SITE_POPUP_ORDER = "SITE_POPUP_ORDER";
    /** 팝업 노출 켜기·끄기 — {@code from}/{@code to}(바뀐 때만) */
    public static final String SITE_POPUP_VISIBLE = "SITE_POPUP_VISIBLE";
    /** 팝업 여러 창 배치 바꾸기(사이트 전체 하나) — 대상 SITE_POPUP + {@code layout}, {@code from}/{@code to}(바뀐 때만) */
    public static final String SITE_POPUP_LAYOUT = "SITE_POPUP_LAYOUT";
    /** 오픈소스 라이선스 안내 문구 바꾸기(설정 > 운영·약관 「오픈소스 라이선스」, oss-licenses-contract) — 대상 OSS_NOTICE + {@code intro}, {@code from}/{@code to}(바뀐 때만) */
    public static final String OSS_NOTICE_INTRO_UPDATE = "OSS_NOTICE_INTRO_UPDATE";
    /** 오픈소스 라이선스 직접 추가 항목 만들기 — 대상 OSS_NOTICE + 항목 ID, {@code after} 칸 값(전문은 글자 수만) */
    public static final String OSS_NOTICE_ENTRY_CREATE = "OSS_NOTICE_ENTRY_CREATE";
    /** 직접 추가 항목 고치기 — 바뀐 칸의 {@code before}/{@code after}(전문은 글자 수만, 바뀐 때만) */
    public static final String OSS_NOTICE_ENTRY_UPDATE = "OSS_NOTICE_ENTRY_UPDATE";
    /** 직접 추가 항목 숨기기·보이기 — {@code from}/{@code to}(바뀐 때만) */
    public static final String OSS_NOTICE_ENTRY_HIDDEN = "OSS_NOTICE_ENTRY_HIDDEN";
    /** 직접 추가 항목 지우기 — 지운 칸 값({@code before}) */
    public static final String OSS_NOTICE_ENTRY_DELETE = "OSS_NOTICE_ENTRY_DELETE";
    /** 운영 주체 칸 저장(설정 > 일반 「운영 주체」, 단계는 그대로) — 대상 SITE/1, 바뀐 칸의 {@code before}/{@code after}(이메일은 가림, 바뀐 때만) */
    public static final String SITE_OPERATOR_UPDATE = "SITE_OPERATOR_UPDATE";
    /** 운영 주체 단계 전환(개인 ↔ 개인사업자 ↔ 법인) — 대상 SITE/1, {@code stage} 전후 + 바뀐 칸의 {@code before}/{@code after}. 전환 이력 한 줄도 남긴다 */
    public static final String SITE_OPERATOR_STAGE_CHANGE = "SITE_OPERATOR_STAGE_CHANGE";
    /** 테마 하나의 홈 구성 저장 — 대상 SITE/1, {@code theme} + 칸 수·가젯 수 전후와 넣은·뺀 가젯 수 */
    public static final String HOME_LAYOUT_UPDATE = "HOME_LAYOUT_UPDATE";
    /** 가젯 페이지 만들기 — 대상 GADGET_PAGE/id, {@code key}·{@code name}·{@code folderId} */
    public static final String GADGET_PAGE_CREATE = "GADGET_PAGE_CREATE";
    /** 가젯 페이지 정보 고치기 — 바뀐 칸의 {@code before}/{@code after}(바뀐 때만) */
    public static final String GADGET_PAGE_UPDATE = "GADGET_PAGE_UPDATE";
    /** 가젯 페이지 지우기 — {@code key}·{@code name} · 비운 메뉴 그룹 키 */
    public static final String GADGET_PAGE_DELETE = "GADGET_PAGE_DELETE";
    /** 가젯 페이지 하나의 테마 하나 배치 저장 — {@code theme} + 칸 수·가젯 수 전후와 넣은·뺀 가젯 수 */
    public static final String GADGET_PAGE_LAYOUT_UPDATE = "GADGET_PAGE_LAYOUT_UPDATE";
    /** 테마 하나의 게시판 레이아웃 저장 — 대상 SITE/1, {@code theme} + 칸 수·가젯 수·적용 화면 전후와 넣은·뺀·바꾼 가젯 수 */
    public static final String BOARD_LAYOUT_UPDATE = "BOARD_LAYOUT_UPDATE";
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
    // ── 아바타 올리기 심사(docs/features/commerce-motion-avatar-contract.md) — 대상 AVATAR_UPLOAD + 신청 ID, detail 에 회원 ID·사유 ──
    /** 아바타 올리기 승인 — 변경권 사용, 회원의 내가 올린 아바타가 되고 바로 프로필 아바타(앞 그림은 교체) — {@code animated}·{@code ticketUsed} */
    public static final String AVATAR_UPLOAD_APPROVE = "AVATAR_UPLOAD_APPROVE";
    /** 아바타 올리기 반려 — 변경권 반환, {@code reason}·{@code ticketReturned} */
    public static final String AVATAR_UPLOAD_REJECT = "AVATAR_UPLOAD_REJECT";
    /** 내가 올린 아바타 해제 — 변경권 반환 없음, 쓰는 중이었으면 마지막 정지 아바타로 — {@code reason}·{@code wasInUse} */
    public static final String AVATAR_UPLOAD_REVOKE = "AVATAR_UPLOAD_REVOKE";
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
    /** 글의 처리 상태 바꾸기(V166 운영 게시판 — 버그 신고·기능 요청) — 대상 POST + 글 ID, {@code from}/{@code to}(PostProcessStatus 이름). 같은 값이면 남기지 않는다 */
    public static final String POST_PROCESS_STATUS = "POST_PROCESS_STATUS";
    // ── 공지 관리(docs/features/notice-admin-contract.md) — 공지 게시판 글, 대상 POST + 글 ID ──
    /** 공지 쓰기(작성자 = 운영자 회원 계정) — {@code boardId}·{@code authorUserId}·{@code prefixId}·{@code title}·{@code pinned} */
    public static final String NOTICE_CREATE = "NOTICE_CREATE";
    /** 공지 고치기(작성자는 그대로) — 바뀐 칸 {@code before}/{@code after}(prefixId·title·body·pinned — body 는 바뀌었는지만) */
    public static final String NOTICE_UPDATE = "NOTICE_UPDATE";
    /** 공지 지우기(사유·작성자 알림·포인트 회수 없음 — 게시글 관리 삭제와 같은 「지운 글」) — 고정이 풀렸으면 {@code unpinned} */
    public static final String NOTICE_DELETE = "NOTICE_DELETE";
    public static final String NOTICE_PIN = "NOTICE_PIN";
    public static final String NOTICE_UNPIN = "NOTICE_UNPIN";
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
    // ── 회원 제재·이의제기(docs/features/member-sanction-contract.md) — 대상 MEMBER + 회원 ID ──
    /** 사이트 제재 부여 — {@code sanctionId}·{@code sanctionNo}·{@code kind}·{@code warningNo}·{@code skipped}·{@code endsAt}·{@code reason}·{@code reportCaseId}·대체해 끝낸 정지 {@code replacedSanctionId}·끝낸 게시판 지정 {@code boardStaffEnded} */
    public static final String SANCTION_ISSUE = "SANCTION_ISSUE";
    /** 진행 중 정지·영구 정지 해제 — {@code sanctionId}·{@code sanctionNo}·{@code kind}·해제 사유 {@code reason} */
    public static final String SANCTION_LIFT = "SANCTION_LIFT";
    /** 이의제기 인용(제재 무효) — {@code appealId}·{@code appealNo}·{@code sanctionId}·{@code kind}·{@code answer}·진행 중이던 정지를 풀었으면 {@code released} */
    public static final String APPEAL_ACCEPT = "APPEAL_ACCEPT";
    /** 이의제기 기각 — {@code appealId}·{@code appealNo}·{@code sanctionId}·{@code kind}·{@code answer} */
    public static final String APPEAL_REJECT = "APPEAL_REJECT";
    // ── 게시판 제재(docs/features/board-sanction-contract.md) — 운영자의 처리만(매니저·스텝의 처리는 board_staff_actions). 대상 MEMBER + 회원 ID ──
    /** 운영자의 게시판 쓰기 제한 해제 — {@code boardSanctionId}·{@code boardSanctionNo}·{@code boardId}·{@code kind}·해제 사유 {@code reason} */
    public static final String BOARD_SANCTION_LIFT = "BOARD_SANCTION_LIFT";
    /** 운영자의 게시판 제재 무효 — {@code boardSanctionId}·{@code boardSanctionNo}·{@code boardId}·{@code kind}·{@code reason}·진행 중 제한이 풀렸는지 {@code released}·같이 인용한 이의제기 {@code appealAccepted} */
    public static final String BOARD_SANCTION_VOID = "BOARD_SANCTION_VOID";
    /** 반복 검토 닫기 — {@code reviewId}·{@code reviewNo}·{@code boardId}·{@code result}(KEPT · SITE_SANCTIONED · VOIDED)·{@code memo}·{@code siteSanctionId}·{@code voidedBoardSanctionIds} */
    public static final String BOARD_SANCTION_REVIEW_CLOSE = "BOARD_SANCTION_REVIEW_CLOSE";
    /** 게시판 스텝 처리 되돌리기(운영자 — board-staff-action-contract 정책 6) — 대상 POST + 글 ID, {@code boardStaffActionId}·{@code kind}(POST_HIDE · POST_DELETE · COMMENT_DELETE)·{@code boardId}·{@code commentId}·처리한 회원 {@code actorUserId}·{@code reason}·{@code restoredPoints} */
    public static final String BOARD_STAFF_ACTION_RESTORE = "BOARD_STAFF_ACTION_RESTORE";
    // ── 운영자 회원 계정(docs/features/operator-member-account-contract.md 정책 11) — 「운영자 X 가 회원 계정 Y 로」: 행위자 OPERATOR(운영자 ID), detail.viaUserId = 그 회원 ──
    /** 연결(대상 OPERATOR + 운영자 ID) — {@code userId}·끝낸 게시판 담당 {@code releasedBoards} */
    public static final String OPERATOR_MEMBER_LINK = "OPERATOR_MEMBER_LINK";
    /** 해제(대상 OPERATOR + 운영자 ID) — {@code userId} */
    public static final String OPERATOR_MEMBER_UNLINK = "OPERATOR_MEMBER_UNLINK";
    /** 사이트에서 글 숨기기(대상 POST + 글 ID) — {@code viaUserId}·{@code boardId}·{@code postId}·{@code reasonCode}·{@code boardStaffActionId} */
    public static final String SITE_POST_HIDE = "SITE_POST_HIDE";
    /** 사이트에서 글 삭제(대상 POST) — 위와 같은 칸 + {@code revokedPoints} */
    public static final String SITE_POST_DELETE = "SITE_POST_DELETE";
    /** 사이트에서 댓글 삭제(대상 COMMENT + 댓글 ID) — {@code viaUserId}·{@code boardId}·{@code postId}·{@code commentId}·{@code reasonCode}·{@code boardStaffActionId} */
    public static final String SITE_COMMENT_DELETE = "SITE_COMMENT_DELETE";
    /** 사이트에서 게시판 제재(대상 MEMBER + 받은 회원) — {@code viaUserId}·{@code boardId}·{@code postId}·{@code commentId}·{@code reasonCode}·{@code kind}·{@code boardSanctionId} */
    public static final String SITE_BOARD_SANCTION = "SITE_BOARD_SANCTION";
    /** 사이트에서 게시판 쓰기 제한 해제(대상 MEMBER) — {@code viaUserId}·{@code boardId}·{@code boardSanctionId}·{@code reason} */
    public static final String SITE_BOARD_SANCTION_LIFT = "SITE_BOARD_SANCTION_LIFT";
    /** 사이트에서 처리 되돌리기(대상 POST + 글 ID) — {@code viaUserId}·{@code boardId}·{@code postId}·{@code commentId}·{@code reasonCode}·{@code boardStaffActionId}·{@code reason} */
    public static final String SITE_ACTION_RESTORE = "SITE_ACTION_RESTORE";
    /** 탈퇴 회원 연락처 열람 — 대상 MEMBER, 사유 {@code reason}(이메일은 싣지 않는다). 감사 보관 분류는 개인정보 열람(0-5 와 합칠 때) */
    public static final String WITHDRAWN_CONTACT_REVEAL = "WITHDRAWN_CONTACT_REVEAL";
    /**
     * 익명 글·댓글 작성자 열람(anonymous-board-contract 정책 10) — 대상 POST(글) 또는 COMMENT(댓글 — {@code postId} 함께), 사유 코드 {@code reason}(LEGAL_REQUEST · RIGHTS_REPORT ·
     * ABUSE · OTHER) · 메모 {@code memo}(감사 상세에만). 열린 회원 ID 는 싣지 않는다(감사를 읽는 사람에게 봉인이 풀리지 않게). 보관 분류는 개인정보 열람(2년)
     */
    public static final String ANONYMOUS_AUTHOR_REVEAL = "ANONYMOUS_AUTHOR_REVEAL";
    // ── 회원 탈퇴(docs/features/member-withdrawal-contract.md) ──
    /** 탈퇴 신청(회원) — 대상 MEMBER, {@code withdrawalId}·{@code result}(COMPLETED · SCHEDULED)·{@code pendingCount}·고른 것·사유 코드(메모 없음) */
    public static final String MEMBER_WITHDRAW_REQUEST = "MEMBER_WITHDRAW_REQUEST";
    /** 탈퇴 완료(익명화) — 대상 MEMBER, 포기 동의 금액({@code forfeitPaidPoints}·{@code forfeitBizAmount})·{@code forfeitedFree}·{@code hasPaymentHistory}·{@code rejoinUntil}. 돈 포기 증빙이라 돈 관련(5년) */
    public static final String MEMBER_WITHDRAW = "MEMBER_WITHDRAW";
    /** 탈퇴 취소 — 대상 MEMBER, {@code withdrawalId}. 회원 본인(행위자 MEMBER) 또는 운영자 대리 취소(행위자 OPERATOR + {@code onBehalf}·{@code requestChannel}·{@code memo}) */
    public static final String MEMBER_WITHDRAW_CANCEL = "MEMBER_WITHDRAW_CANCEL";
    /**
     * 운영자 강제 탈퇴(member-withdrawal-contract 「관리자」) — 대상 MEMBER, {@code withdrawalId}·{@code result}(COMPLETED · SCHEDULED)·{@code pendingCount}·{@code reasonKind}·운영 메모 {@code memo}.
     * 회원 동의 없이 포인트 소멸·계정 종료를 일으킨 결정이라 완료 기록과 같은 돈 관련(5년)
     */
    public static final String MEMBER_WITHDRAW_FORCE = "MEMBER_WITHDRAW_FORCE";
    /** 포트원 설정 저장 — 대상 PORTONE_SETTINGS, 바꾼 칸 이름 {@code fields}(값 없음) */
    public static final String PORTONE_SETTINGS_UPDATE = "PORTONE_SETTINGS_UPDATE";
    /** 포트원 설정 칸 지우기 — 대상 PORTONE_SETTINGS, 칸 이름 {@code field} */
    public static final String PORTONE_SETTINGS_DELETE = "PORTONE_SETTINGS_DELETE";
    /** 공공데이터포털 설정 저장(business-contract 「국세청 진위확인 · 공공데이터 설정」) — 대상 PUBLIC_DATA_SETTINGS/nts, 바꾼 칸 이름 {@code fields}(값 없음). 일반 */
    public static final String PUBLIC_DATA_SETTINGS_UPDATE = "PUBLIC_DATA_SETTINGS_UPDATE";
    /** 공공데이터포털 서비스 키 지우기 — 대상 PUBLIC_DATA_SETTINGS/nts, 칸 이름 {@code field}. 일반 */
    public static final String PUBLIC_DATA_SETTINGS_DELETE = "PUBLIC_DATA_SETTINGS_DELETE";
    /**
     * 공공데이터 API 주소 저장(사용자 지시 2026-11-05) — 대상 PUBLIC_DATA_SETTINGS/{@code nts}·{@code holiday}, 바꾼 칸 {@code fields} + 전후 값 {@code before}·{@code after}
     * (주소·요청 글자 틀·칸 이름 — 비밀이 아니다. 인증키는 글자 틀에 넣지 않고 {serviceKey} 자리로만 둔다). 일반
     */
    public static final String PUBLIC_DATA_ENDPOINT_UPDATE = "PUBLIC_DATA_ENDPOINT_UPDATE";
    /** 공공데이터 API 주소 기본값으로 되돌리기 — 대상 PUBLIC_DATA_SETTINGS/{@code nts}·{@code holiday}, 되돌리기 전 값 {@code before}. 일반 */
    public static final String PUBLIC_DATA_ENDPOINT_RESET = "PUBLIC_DATA_ENDPOINT_RESET";

    // ── 사이트 쉬는 날(휴일 관리 /settings/holidays — commerce.md 영업일 · emoticon-contract 결정 4) — 대상 SITE_HOLIDAY ──
    /** 회사 휴무일 더하기 — 대상 SITE_HOLIDAY + 줄 ID, {@code date}·{@code name}. 일반 */
    public static final String SITE_HOLIDAY_CREATE = "SITE_HOLIDAY_CREATE";
    /** 회사 휴무일 지우기 — 대상 SITE_HOLIDAY + 줄 ID, {@code date}·{@code name}. 일반 */
    public static final String SITE_HOLIDAY_DELETE = "SITE_HOLIDAY_DELETE";
    /** 공공데이터 공휴일 「지금 받기」(운영자) — 대상 SITE_HOLIDAY/{@code sync}, {@code outcome}·{@code appliedYears}·{@code failedYears}·{@code changed}. 일반(예약 실행은 배치 실행 로그가 남긴다) */
    public static final String SITE_HOLIDAY_SYNC = "SITE_HOLIDAY_SYNC";

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

    // ── 로보티콘 S1(docs/features/emoticon-contract.md) — 대상 EMOTICON_REVIEW·EMOTICON_PACK·EMOTICON_SETTINGS ──
    /** 심사 승인 — 대상 EMOTICON_REVIEW + 심사 건 ID, {@code packId}·{@code kind}(FIRST·UPDATE)·{@code version}·{@code price}(S1 은 0) */
    public static final String EMOTICON_REVIEW_APPROVE = "EMOTICON_REVIEW_APPROVE";
    /** 심사 반려 — 대상 EMOTICON_REVIEW + 심사 건 ID, {@code packId}·{@code kind}·{@code version}·{@code reason} */
    public static final String EMOTICON_REVIEW_REJECT = "EMOTICON_REVIEW_REJECT";
    /** 판매 중단 — 대상 EMOTICON_PACK + 팩 ID, {@code reason} */
    public static final String EMOTICON_PACK_SUSPEND = "EMOTICON_PACK_SUSPEND";
    /** 다시 판매 — 대상 EMOTICON_PACK + 팩 ID */
    public static final String EMOTICON_PACK_RESUME = "EMOTICON_PACK_RESUME";
    /**
     * 작가 탈퇴로 새 판매를 멈춤(시스템 — 탈퇴 완료 트랜잭션, 후속 결정 6) — 대상 EMOTICON_PACK + 팩 ID, {@code creator}·{@code from}(멈추기 전 상태 APPROVED·SUSPENDED)·
     * {@code fromKind}(운영자 판매 중단이었으면 OPERATOR)·{@code fromReason}(그 사유). 탈퇴 작가의 대기 심사 건은 {@link #EMOTICON_REVIEW_REJECT}(시스템, {@code cause CREATOR_WITHDRAWN})
     */
    public static final String EMOTICON_PACK_CREATOR_WITHDRAWN = "EMOTICON_PACK_CREATOR_WITHDRAWN";
    /** 운영 팩 만들기 — 대상 EMOTICON_PACK + 팩 ID, {@code name}·{@code count} */
    public static final String EMOTICON_PACK_CREATE = "EMOTICON_PACK_CREATE";
    /** 운영 팩 고치기(새 판) — 대상 EMOTICON_PACK + 팩 ID, {@code version}·{@code before}/{@code after}(이름·개수) */
    public static final String EMOTICON_PACK_UPDATE = "EMOTICON_PACK_UPDATE";
    /** 설정 저장 — 대상 EMOTICON_SETTINGS + 1, 바뀐 칸만 before/after(S2 부터 유료 판매·수수료·가격 단계·정산·환불 칸 — 돈 관련 5년) */
    public static final String EMOTICON_SETTINGS_UPDATE = "EMOTICON_SETTINGS_UPDATE";
    /** 판매 중 작가 팩 가격 바꾸기(유료↔무료 · 단계 — S2) — 대상 EMOTICON_PACK + 팩 ID, {@code from}·{@code to}·{@code reason}. 돈 관련(5년) */
    public static final String EMOTICON_PACK_PRICE = "EMOTICON_PACK_PRICE";
    /** 작가 정산 계좌 원문 보기(emoticon.payout.pii — S2) — 대상 EMOTICON_PAYOUT_ACCOUNT + 회원 번호, {@code reason}(값은 싣지 않는다). 개인정보 열람(2년) */
    public static final String EMOTICON_PAYOUT_REVEAL = "EMOTICON_PAYOUT_REVEAL";
    /** 유료 팩 판매 예외 환불(emoticon.sales — S3) — 대상 EMOTICON_PURCHASE + 구매 ID, {@code packId}·{@code buyer}·{@code amount}·{@code reason}·{@code used}·{@code withinSelfRefund}. 돈 관련(5년) */
    public static final String EMOTICON_SALE_REFUND = "EMOTICON_SALE_REFUND";
    // ── 로보티콘 S4 작가 정산(emoticon.settlement) — 대상 EMOTICON_SETTLEMENT + 정산 줄 ID(yyyyMM-작가회원번호). 돈 관련(5년) ──
    /** 정산 확정(금액 굳힘) — {@code creator}·{@code month}·{@code salesAmount}·{@code refundAmount}·{@code shareAmount}·{@code carriedIn}·{@code withholdingAmount}·{@code payoutAmount}·{@code carriedOut}·{@code payeeType}·{@code feeRatePercent} */
    public static final String EMOTICON_SETTLEMENT_CONFIRM = "EMOTICON_SETTLEMENT_CONFIRM";
    /** 정산 보류 — {@code creator}·{@code month}·{@code from}(예정·확정)·{@code reason} */
    public static final String EMOTICON_SETTLEMENT_HOLD = "EMOTICON_SETTLEMENT_HOLD";
    /** 정산 보류 풀기 — {@code creator}·{@code month}·{@code to}(예정·확정) */
    public static final String EMOTICON_SETTLEMENT_UNHOLD = "EMOTICON_SETTLEMENT_UNHOLD";
    /** 지급 완료 기록(이체는 사이트 밖) — {@code creator}·{@code month}·{@code payoutAmount}·{@code transferDate}·{@code memo} */
    public static final String EMOTICON_SETTLEMENT_PAID = "EMOTICON_SETTLEMENT_PAID";
    /**
     * 정산 포기(V148 — 정산 정보 없이 탈퇴한 작가의 남은 수익, 줄마다) — {@code creator}·{@code month}·{@code amount}(그 줄에서 포기한 금액)·{@code from}(예정·보류)·{@code basis}
     * ({@code MEMBER_CONSENT} 탈퇴 때 회원 동의 — 행위자 SYSTEM, {@code consentedAmount}·{@code consentedAt} · {@code OPERATOR} 운영자 포기 처리 — {@code reason})
     */
    public static final String EMOTICON_SETTLEMENT_WAIVE = "EMOTICON_SETTLEMENT_WAIVE";
    /**
     * 수동 지급 기록(V149 — 정산 정보 없이 탈퇴한 작가에게 은행에서 직접 보낸 뒤, 줄마다 · 계좌는 싣지 않는다) — {@code creator}·{@code month}·{@code from}(예정·보류)·{@code to}
     * (지급 완료 · 지급액 0 이면 확정)·{@code payoutAmount}·{@code withholdingAmount}·{@code carriedOut}·{@code payeeType}(개인)·{@code transferDate}·{@code memo}·{@code reason}
     */
    public static final String EMOTICON_SETTLEMENT_MANUAL_PAID = "EMOTICON_SETTLEMENT_MANUAL_PAID";

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
    /** 관리자 공구 문의 목록·상세 열람(비밀글 본문 — 2b-6). 개인정보 열람 */
    public static final String GROUPBUY_QNA_VIEW = "GROUPBUY_QNA_VIEW";
    /** 공구 문의 숨김·숨김 풀기 — {@code action}(HIDE·UNHIDE)·{@code reason}. 일반 */
    public static final String GROUPBUY_QNA_HIDE = "GROUPBUY_QNA_HIDE";
    /** 공구 문의 답변 수정(이전 내용은 답변 이력) — 글자 수. 일반 */
    public static final String GROUPBUY_QNA_ANSWER_EDIT = "GROUPBUY_QNA_ANSWER_EDIT";
    /** 공구 리뷰 숨김·숨김 풀기 — {@code action}·{@code reasonCode}·{@code memo}(낮은 별점은 사유가 아니다). 일반 */
    public static final String GROUPBUY_REVIEW_HIDE = "GROUPBUY_REVIEW_HIDE";
    /** 공구 리뷰 공식 답글 수정 — 글자 수. 일반 */
    public static final String GROUPBUY_REVIEW_REPLY_EDIT = "GROUPBUY_REVIEW_REPLY_EDIT";
    /** 주소 검색 설정 저장(address-search-contract) — 대상 ADDRESS_SEARCH/settings, 바뀐 칸의 {@code before}/{@code after}(바뀐 때만). 일반 */
    public static final String ADDRESS_SEARCH_SETTINGS_UPDATE = "ADDRESS_SEARCH_SETTINGS_UPDATE";
    /** 주소 DB 적재 결과 — 대상 ADDRESS_SEARCH/적재 ID, 성공은 {@code files}·{@code entries}·{@code jibun}·{@code english}, 실패는 {@code reason}(문구 키). 일반 */
    public static final String ADDRESS_DB_IMPORT = "ADDRESS_DB_IMPORT";
    // ── 사업자 신원(business-contract, V111) — 대상 BUSINESS + 신원 ID. detail 에 등록번호·대표자·연락처·주소 평문을 싣지 않는다(상호·끝 4자리·사유만) ──
    /** 사업자 승인 — {@code companyName}·{@code ntsStatus}. 일반 */
    public static final String BUSINESS_APPROVE = "BUSINESS_APPROVE";
    /** 사업자 반려 — {@code code}·{@code text}. 일반 */
    public static final String BUSINESS_REJECT = "BUSINESS_REJECT";
    /** 사업자 정지(H2) — {@code code}·{@code text}·진행 건 처리 {@code pendingAction}·{@code scheduledAction}·{@code runningAction}. 민감 */
    public static final String BUSINESS_SUSPEND = "BUSINESS_SUSPEND";
    /** 사업자 정지 해제 — {@code restore}(진행 중이던 건 처리)·{@code note}. 민감 */
    public static final String BUSINESS_UNSUSPEND = "BUSINESS_UNSUSPEND";
    /** 사업자 서비스만 정지(대상 BUSINESS · 신원 ID — 2026-11-02) — {@code service}·{@code code}·{@code text}·{@code pendingAction}·{@code scheduledAction}·{@code runningAction}. 민감 */
    public static final String BUSINESS_SERVICE_SUSPEND = "BUSINESS_SERVICE_SUSPEND";
    /** 사업자 서비스만 정지 해제(대상 BUSINESS · 신원 ID) — {@code service}·{@code restore}·{@code note}. 민감 */
    public static final String BUSINESS_SERVICE_RESUME = "BUSINESS_SERVICE_RESUME";
    /** 사업자 신원 평문 열람(B3) — {@code reason}. 개인정보 열람 */
    public static final String BUSINESS_REVEAL = "BUSINESS_REVEAL";
    /** 사업자등록증 사본 열람(B2). 개인정보 열람 */
    public static final String BUSINESS_CERT_VIEW = "BUSINESS_CERT_VIEW";
    /** 국세청 상태조회 다시 하기(B1) — {@code status}. 일반 */
    public static final String BUSINESS_NTS_CHECK = "BUSINESS_NTS_CHECK";
    /** 국세청 조회 불가인데 사유를 적고 승인(B1) — {@code reason}. 민감 */
    public static final String BUSINESS_NTS_OVERRIDE = "BUSINESS_NTS_OVERRIDE";
    /** 사업자 삭제(신청 건이 없는 신원만) — {@code companyName}·{@code bizNoLast4}·{@code status}(지우기 전 상태)·{@code reReview}·{@code reason}. 평문·암호문은 싣지 않는다. 일반 */
    public static final String BUSINESS_DELETE = "BUSINESS_DELETE";
    // ── 사업자 서류 종이 접수 기록(biz-doc-intake-contract, V140) — 사업자등록증은 대상 BUSINESS · 신원 ID. detail 에 개인정보를 싣지 않는다(방식·날짜·처리·사유·파일 이름만) ──
    /** 등록증 인쇄본·종이 팩스 접수 — {@code intakeId}·{@code channel}·{@code receivedOn}·{@code pages}·{@code handling}. 일반 */
    public static final String BUSINESS_PAPER_RECEIVE = "BUSINESS_PAPER_RECEIVE";
    /** 등록증 인쇄본 처리 바꾸기 — {@code intakeId}·{@code from}·{@code to}. 일반 */
    public static final String BUSINESS_PAPER_HANDLING = "BUSINESS_PAPER_HANDLING";
    /** 등록증 인쇄본 폐기 — {@code intakeId}·{@code disposedOn}·{@code reason}·{@code scanFileId}. 일반 */
    public static final String BUSINESS_PAPER_DISPOSE = "BUSINESS_PAPER_DISPOSE";
    /** 등록증 접수 기록 잘못 기록 — {@code intakeId}·{@code reason}. 일반 */
    public static final String BUSINESS_PAPER_VOID = "BUSINESS_PAPER_VOID";
    /** 운영자의 등록증 사본 올리기(전자 팩스·스캔) — {@code fileId}·{@code fileName}·{@code channel}·{@code size}. 일반 */
    public static final String BUSINESS_CERT_UPLOAD = "BUSINESS_CERT_UPLOAD";
    // ── 사업자 신청 건(biz-application-contract, V112) — 대상 BIZ_APPLICATION + 신청 건 ID. detail 에 개인정보를 싣지 않는다(금액·기간·사유·파일 이름만) ──
    /** 승인 — {@code method}·{@code supply}·{@code total}·기간·{@code deadlineAt}. 돈 관련(금액 확정) */
    public static final String BIZ_APP_APPROVE = "BIZ_APP_APPROVE";
    /** 반려 — {@code code}·{@code text}. 일반 */
    public static final String BIZ_APP_REJECT = "BIZ_APP_REJECT";
    /** 수동 결제로 전환 — {@code deadlineAt}·{@code reason}. 일반 */
    public static final String BIZ_APP_MANUAL_SWITCH = "BIZ_APP_MANUAL_SWITCH";
    /** 계약서 사본 올리기·바꾸기 — {@code files}(이름)·{@code replaced}. 돈 관련(계약 기록 5년) */
    public static final String BIZ_APP_CONTRACT_UPLOAD = "BIZ_APP_CONTRACT_UPLOAD";
    /** 계약서 사본 열람 — {@code fileId}·{@code fileName}. 개인정보 열람 */
    public static final String BIZ_APP_CONTRACT_VIEW = "BIZ_APP_CONTRACT_VIEW";
    /** 계약서 인쇄본·종이 팩스 접수(V140) — {@code intakeId}·{@code channel}·{@code receivedOn}·{@code pages}·{@code handling}. 돈 관련(계약 기록 5년) */
    public static final String BIZ_APP_PAPER_RECEIVE = "BIZ_APP_PAPER_RECEIVE";
    /** 계약서 인쇄본 처리 바꾸기 — {@code intakeId}·{@code from}·{@code to}. 돈 관련 */
    public static final String BIZ_APP_PAPER_HANDLING = "BIZ_APP_PAPER_HANDLING";
    /** 계약서 인쇄본 폐기 — {@code intakeId}·{@code disposedOn}·{@code reason}·{@code scanFileId}. 돈 관련 */
    public static final String BIZ_APP_PAPER_DISPOSE = "BIZ_APP_PAPER_DISPOSE";
    /** 계약서 접수 기록 잘못 기록 — {@code intakeId}·{@code reason}. 돈 관련 */
    public static final String BIZ_APP_PAPER_VOID = "BIZ_APP_PAPER_VOID";
    /** 입금 확인 기록 — {@code amount}·{@code depositedOn}·{@code approvedTotal}·{@code mismatchReason}. 돈 관련(5년) */
    public static final String BIZ_DEPOSIT_CONFIRM = "BIZ_DEPOSIT_CONFIRM";
    /** 수동 예약 확정 — {@code contracts}·{@code deposit}. 돈 관련 */
    public static final String BIZ_APP_CONFIRM = "BIZ_APP_CONFIRM";
    /** 운영자 취소 — {@code from}·{@code reason}·{@code refund}. 돈 관련 */
    public static final String BIZ_APP_CANCEL = "BIZ_APP_CANCEL";
    /** 환불 승인 — {@code refundId}·{@code amount}·{@code proposed}·{@code reason}. 돈 관련 */
    public static final String BIZ_APP_REFUND_APPROVE = "BIZ_APP_REFUND_APPROVE";
    /** 환불 거절 — {@code refundId}·{@code reason}. 돈 관련 */
    public static final String BIZ_APP_REFUND_DENY = "BIZ_APP_REFUND_DENY";
    /** 수동 환불 송금 완료 표시 — {@code refundId}·{@code amount}·{@code note}. 돈 관련 */
    public static final String BIZ_APP_REFUND_TRANSFER = "BIZ_APP_REFUND_TRANSFER";
    /** 승인 때 요금표와 다른 공급가 — {@code quoteSupply}·{@code supply}·{@code reason}. 돈 관련 */
    public static final String BIZ_APP_PRICE_ADJUST = "BIZ_APP_PRICE_ADJUST";
    /** 입금 기한 바꾸기 — {@code from}·{@code to}. 일반 */
    public static final String BIZ_APP_DEPOSIT_DEADLINE = "BIZ_APP_DEPOSIT_DEADLINE";
    /** 운영자 등록(광고 일정표 수동 등록 → 심사 대기, 04-ia-review §2) — {@code type}·기간·{@code proposedSupply}·{@code weight}·{@code advertiser}(ID)·{@code contracts}(미리 붙인 장수). 돈 관련(계약 시작 기록) */
    public static final String BIZ_APP_OPERATOR_CREATE = "BIZ_APP_OPERATOR_CREATE";
    /** 운영자가 광고주 대신 일시 정지(V127 — biz-pause-contract 규칙 11) — {@code channel}·{@code memo}·{@code pauseCount}. 일반 */
    public static final String BIZ_APP_PAUSE = "BIZ_APP_PAUSE";
    /** 운영자가 광고주 대신 다시 켜기 — {@code channel}·{@code memo}·{@code pausedMinutes}. 일반 */
    public static final String BIZ_APP_RESUME = "BIZ_APP_RESUME";
    /** 운영자가 광고주 대신 중단(일할 환불 심사 대기 — 수수료) — {@code channel}·{@code memo}·{@code fromPause}·{@code refundFrom}·{@code prorated}·{@code feeRate}·{@code fee}·{@code proposed}. 돈 관련 */
    public static final String BIZ_APP_STOP = "BIZ_APP_STOP";
    /** 운영자 추가 환불(클레임 — V128, 닫힌·끝난 건의 남은 금액을 바로 승인) — {@code refundId}·{@code amount}·{@code reason}·{@code method}·{@code refundableBefore}. 돈 관련 */
    public static final String BIZ_APP_REFUND_CLAIM = "BIZ_APP_REFUND_CLAIM";
    // ── 광고 일정표(ad-contract, V115) — 긴급 내림·되돌리기는 대상 BIZ_APPLICATION, 광고주는 대상 AD_ADVERTISER + 광고주 ID. detail 에 연락처 평문을 싣지 않는다 ──
    /** 계정 없는 광고주 만들기 — {@code name}·{@code businessId}(연결했으면). 일반 */
    public static final String AD_ADVERTISER_CREATE = "AD_ADVERTISER_CREATE";
    /** 광고주 고치기(광고주 관리 — ad-advertiser-contract 규칙 7·10) — {@code fields}(바뀐 칸 이름만, 값 없음)·{@code businessId}(사업자 광고주면). 일반 */
    public static final String AD_ADVERTISER_UPDATE = "AD_ADVERTISER_UPDATE";
    /** 직접 등록 광고주 보관 — 운영자 등록의 광고주 찾기에서 빠진다. 일반 */
    public static final String AD_ADVERTISER_ARCHIVE = "AD_ADVERTISER_ARCHIVE";
    /** 직접 등록 광고주 보관 풀기. 일반 */
    public static final String AD_ADVERTISER_UNARCHIVE = "AD_ADVERTISER_UNARCHIVE";
    /** 직접 등록 광고주 삭제(신청 건 0 — 광고주 줄·열람 기록·처리 기록을 지운다) — {@code name}. 일반 */
    public static final String AD_ADVERTISER_DELETE = "AD_ADVERTISER_DELETE";
    /** 광고주 연락처 평문 열람 — {@code reason}. 개인정보 열람 */
    public static final String AD_CONTACT_REVEAL = "AD_CONTACT_REVEAL";
    /** 긴급 내림 — {@code reason}·{@code notifyMail}·{@code phase}. 일반(돈은 되돌리기 보상이 움직인다) */
    public static final String AD_TAKEDOWN = "AD_TAKEDOWN";
    /** 긴급 내림 되돌리기 — {@code compensation}(EXTEND·PRORATE_REFUND)·{@code reason}·{@code lostMinutes}·{@code endAt}·{@code refundAmount}. 돈 관련 */
    public static final String AD_RESTORE = "AD_RESTORE";
    /** 광고 노출 자리 설정 저장(글 보기 중간·날개·목록 한 줄, 대상 AD_PLACEMENTS · 1) — 바뀐 칸 {@code before}·{@code after}(처음 저장이면 기본값에서). 일반 */
    public static final String AD_PLACEMENT_UPDATE = "AD_PLACEMENT_UPDATE";
    /** 통계 수집 설정 저장(대상 STATS_COLLECTION · 1, site-analytics-contract) — 바뀐 칸 {@code before}·{@code after}. 일반 */
    public static final String STATS_COLLECTION_UPDATE = "STATS_COLLECTION_UPDATE";
    // ── 회원 핫딜 1차(hotdeal-contract 「권한·정책」 감사 — 모두 일반) ──
    /** 핫딜 운영진 종료(대상 POST · 글 ID) — {@code reason}·{@code before}(그때 상태)·{@code boardId}, 신고 사건 조치면 {@code reportCaseId}. 일반 */
    public static final String HOTDEAL_END = "HOTDEAL_END";
    /** 핫딜 운영진 재개(대상 POST · 글 ID — 의심·종료 요청 리셋) — {@code reason}·{@code endedBy}(누가 끝냈었나)·{@code boardId}. 일반 */
    public static final String HOTDEAL_REOPEN = "HOTDEAL_REOPEN";
    /** 사업자 핫딜 글 운영자 [유지](대상 POST · 글 ID — 확인 필요를 풀고 종료 요청 셈을 새 회차로, biz-hotdeal 결정 11) — {@code reason}·{@code boardId}. 일반 */
    public static final String HOTDEAL_KEEP = "HOTDEAL_KEEP";
    /** 링크 규칙 추가(대상 HOTDEAL_DOMAIN · 규칙 ID) — {@code after}({@code domain}·{@code rule}·{@code common}·{@code keyParams}·{@code note}). 일반 */
    public static final String HOTDEAL_DOMAIN_CREATE = "HOTDEAL_DOMAIN_CREATE";
    /** 링크 규칙 고치기 — 바뀐 칸 {@code before}·{@code after}. 일반 */
    public static final String HOTDEAL_DOMAIN_UPDATE = "HOTDEAL_DOMAIN_UPDATE";
    /** 링크 규칙 지우기 — {@code before}(지운 줄). 일반 */
    public static final String HOTDEAL_DOMAIN_DELETE = "HOTDEAL_DOMAIN_DELETE";
    /** 위장 홍보 의심 후보 무시(대상 HOTDEAL_SUSPICION · 후보 ID) — {@code kind}·{@code host}·{@code note}. 일반 */
    public static final String HOTDEAL_SUSPICION_DISMISS = "HOTDEAL_SUSPICION_DISMISS";
    /** 핫딜 설정 저장(대상 HOTDEAL_SETTINGS · 1) — 바뀐 칸 {@code before}·{@code after}(제휴 링크 방식 전환 {@code affiliateMode} 도 여기). 일반 */
    public static final String HOTDEAL_SETTINGS_UPDATE = "HOTDEAL_SETTINGS_UPDATE";
    /** 사이트 제휴 몰 추가(대상 HOTDEAL_SITE_AFFILIATE · 몰 ID) — {@code after}. 일반 */
    public static final String HOTDEAL_SITE_AFFILIATE_CREATE = "HOTDEAL_SITE_AFFILIATE_CREATE";
    /** 사이트 제휴 몰 고치기·켬/끔 — 바뀐 칸 {@code before}·{@code after}. 일반 */
    public static final String HOTDEAL_SITE_AFFILIATE_UPDATE = "HOTDEAL_SITE_AFFILIATE_UPDATE";
    /** 사이트 제휴 몰 지우기 — {@code before}. 일반 */
    public static final String HOTDEAL_SITE_AFFILIATE_DELETE = "HOTDEAL_SITE_AFFILIATE_DELETE";

    // ── 보상 이벤트(V163 — event-admin-contract 「알림·감사」) — 대상 REWARD_EVENT + 이벤트 ID. 지급·재시도·회수는 돈 관련(5년), 나머지는 일반 ──
    /** 만듦 — {@code name}·{@code mode}·{@code tiers}(등수 수)·{@code lines}(보상 줄 수) */
    public static final String REWARD_EVENT_CREATE = "REWARD_EVENT_CREATE";
    /** 고침 — 바뀐 칸 {@code before}/{@code after}(이름·메모·안내 글·기간·인원·등수 구성) */
    public static final String REWARD_EVENT_UPDATE = "REWARD_EVENT_UPDATE";
    /** 시작(준비 → 진행 중) — {@code startAt}·{@code endAt} */
    public static final String REWARD_EVENT_START = "REWARD_EVENT_START";
    /** 운영자 마감 */
    public static final String REWARD_EVENT_CLOSE = "REWARD_EVENT_CLOSE";
    /** 자동 마감(시스템) — {@code cause} PERIOD(끝 시각 지남 — 다음 쓰기 때 고정) · FULL(선착순 인원 참) */
    public static final String REWARD_EVENT_AUTO_CLOSE = "REWARD_EVENT_AUTO_CLOSE";
    /** 댓글 추첨 — {@code drawId}·{@code tierId}·{@code postId}·{@code candidateCount}·{@code picked}·{@code seed} */
    public static final String REWARD_EVENT_DRAW = "REWARD_EVENT_DRAW";
    /** 당첨자 추가 — {@code tierId}·{@code count}·{@code userIds} */
    public static final String REWARD_EVENT_RECIPIENT_ADD = "REWARD_EVENT_RECIPIENT_ADD";
    /** 당첨자 빼기(지급 전) — {@code recipientId}·{@code userId}·{@code tierId} */
    public static final String REWARD_EVENT_RECIPIENT_REMOVE = "REWARD_EVENT_RECIPIENT_REMOVE";
    /** 지급 실행(시작 · 끝) — {@code runId}·{@code phase}(START·DONE)·{@code targetCount}·{@code success}·{@code failed}·{@code skipped}·{@code points}·{@code items}·{@code large}. 돈 관련(5년) */
    public static final String REWARD_EVENT_GRANT = "REWARD_EVENT_GRANT";
    /** 실패만 다시 지급(시작 · 끝) — 지급과 같은 칸. 돈 관련(5년) */
    public static final String REWARD_EVENT_RETRY = "REWARD_EVENT_RETRY";
    /** 회수 — {@code runId}·{@code count}·{@code points}(회수한 양)·{@code items}·{@code reason}. 돈 관련(5년) */
    public static final String REWARD_EVENT_REVOKE = "REWARD_EVENT_REVOKE";
    /** 직접 전달 완료 — {@code recipientId}·{@code userId}·{@code lineId}·{@code memo} */
    public static final String REWARD_EVENT_DELIVERED = "REWARD_EVENT_DELIVERED";
    /** 취소(지급 전) — {@code reason} */
    public static final String REWARD_EVENT_CANCEL = "REWARD_EVENT_CANCEL";

    // AI 회원(docs/features/ai-members-contract.md 정책 14) — 대상 AI_MEMBER · AI_DRAFT · AI_MEMBER_SETTINGS · GROUP
    /** AI 회원 만들기(로그인할 수 없는 회원 계정 + 확장 줄) — {@code userId}·{@code provider}·{@code model}·{@code relation} */
    public static final String AI_MEMBER_CREATE = "AI_MEMBER_CREATE";
    /** AI 회원 고치기 — {@code before}/{@code after}(모델·페르소나 일부·활동 창·게시판)·{@code relation}(from/to/kind)·{@code avatarChanged} */
    public static final String AI_MEMBER_UPDATE = "AI_MEMBER_UPDATE";
    /** AI 회원 켜기·끄기 — {@code from}/{@code to} */
    public static final String AI_MEMBER_ENABLE = "AI_MEMBER_ENABLE";
    /** 대기함 게시(수정 후 게시 포함) — {@code kind}·{@code aiMemberId}·{@code edited} */
    public static final String AI_DRAFT_PUBLISH = "AI_DRAFT_PUBLISH";
    /** 대기함 버림 — {@code kind}·{@code aiMemberId} */
    public static final String AI_DRAFT_DISCARD = "AI_DRAFT_DISCARD";
    /** AI 회원 설정 저장 — {@code before}/{@code after} */
    public static final String AI_MEMBER_SETTINGS_UPDATE = "AI_MEMBER_SETTINGS_UPDATE";
    /** 전체 정지 켜기·끄기 — {@code from}/{@code to} */
    public static final String AI_MEMBER_STOP = "AI_MEMBER_STOP";
    /** 제공사 API 키 저장 — {@code provider}·{@code replaced}(값 없음) */
    public static final String AI_PROVIDER_KEY_UPDATE = "AI_PROVIDER_KEY_UPDATE";
    /** 소모임을 AI 전용으로 지정·해제 — {@code from}/{@code to} */
    public static final String AI_GROUP_AI_ONLY = "AI_GROUP_AI_ONLY";
    /** 모델 목록에 모델 직접 추가(V189) — {@code provider}·{@code modelId}·{@code label}·{@code priceInput}·{@code priceOutput}·{@code freeTier} */
    public static final String AI_MODEL_CREATE = "AI_MODEL_CREATE";
    /** 모델 사용 켜기·끄기 · 기본 정하기 — {@code provider}·{@code modelId}·{@code change}(ENABLED·DEFAULT)·{@code from}/{@code to}(기본이면 옛 기본 모델 ID) */
    public static final String AI_MODEL_UPDATE = "AI_MODEL_UPDATE";
    /** 모델 지우기(쓰는 계정이 없을 때만) — {@code provider}·{@code modelId} */
    public static final String AI_MODEL_DELETE = "AI_MODEL_DELETE";
    /** 프롬프트 설정 저장(V190 — 지시문·분위기·뉴스 글·글 모양) — {@code before}/{@code after}(지시문은 글자 수와 기본값 여부만 — 바뀐 칸 이름은 {@code changed}) */
    public static final String AI_PROMPT_UPDATE = "AI_PROMPT_UPDATE";

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
