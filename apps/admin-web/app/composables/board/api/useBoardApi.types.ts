/**
 * 게시판 관리 API 가 주고받는 모양 — 서버(`server/admin` `board/dto/response`·`request`, shared `board/dto/*Values`)와 맞춘 계약이다(2026-10-22 서버 연결). 시각은 UTC ISO 글자(`Instant`), 정책 칸의 `null` 은 응답에 그대로 온다. community `docs/api-board-settings.md`(정책 열·`board_defaults`)와 `docs/api-content.md` 3절(게시판·말머리)을 roboyam 에 맞게 옮겼다.
 *
 * community 와 다른 점:
 *  · 글쓰기·댓글 최소 자격은 고정 enum(SEEDLING..VETERAN)이 아니라 `'GUEST'`(비회원 포함 모두 — 게시판별로만) · **등급 목록의 `gradeKey`** · `'STAFF'`(운영진·그 게시판 매니저/스텝만), 댓글은 `'OFF'` 도 받는다.
 *  · 「지킴이」 → **게시판 매니저·스텝**(`docs/domain/operator-permissions.md` "게시판 매니저 · 스텝"). 한도 칸 이름은 `staff*`.
 *  · 기본 설정에 그누보드 항목(날짜 표시 방식·목록 글 수·삭제 첨부 정리)과 댓글 공개 시간·베스트 댓글 기준을 한 화면 한 번 저장으로 합쳤다.
 *  · 게시판 순서는 이 화면이 정한다(roboyam 관리자 앱에 메뉴 구성 화면이 없다 — 가정). 글 없는 게시판은 지울 수 있다(가정, 권한 `board.delete`).
 */

import type { GradeKey } from '~/composables/grade/api/useGradeApi.types';

/** 최소 자격의 「비회원 포함 모두」 값 — 게시판별로만 고른다(기본 설정은 회원부터). 핫딜·직접홍보 게시판은 받지 않는다 */
export const BOARD_MIN_GUEST = 'GUEST';
/** 최소 자격의 「운영진만」 값 — 운영진과 그 게시판의 매니저·스텝만 */
export const BOARD_MIN_STAFF = 'STAFF';
/** 댓글 최소 자격의 「끔」 값 — 새 댓글을 받지 않는다(기존 댓글은 보인다) */
export const BOARD_MIN_OFF = 'OFF';

/** 글쓰기 최소 자격 — 등급 키 또는 `STAFF` */
export type BoardWriteMin = typeof BOARD_MIN_GUEST | GradeKey | typeof BOARD_MIN_STAFF;
/** 댓글 최소 자격 — 등급 키 · `STAFF` · `OFF` */
export type BoardCommentMin = typeof BOARD_MIN_GUEST | GradeKey | typeof BOARD_MIN_STAFF | typeof BOARD_MIN_OFF;

/** 목록 모양 — 목록형 · 카드형 · 갤러리형 */
export type BoardLayout = 'LIST' | 'CARD' | 'GALLERY';
/** 게시판 종류(읽기 전용) — 일반 · 핫딜 · 직접홍보 · 소모임. 만든 뒤 바꿀 수 없고 이 화면에서는 만들지 않는다(일반만 만든다) */
export type BoardKind = 'GENERAL' | 'DEAL' | 'PROMO' | 'GROUP';
/** 표시색 프리셋 12색 — `null` 은 색 없음(자동). 이름 → 색 값은 `UtilBoard.colorVar` 한 곳 */
export type BoardColor = 'BLUE' | 'TEAL' | 'AMBER' | 'PURPLE' | 'GREEN' | 'ORANGE' | 'SLATE' | 'RED' | 'PINK' | 'INDIGO' | 'SKY' | 'BROWN';
/** 글 에디터 수준 — 평문(Textarea) · 간단(굵게·기울임·밑줄·취소선·링크·목록·인용) · 고급(+ 제목·구분선·코드·글자색/배경색·이미지·유튜브). 공개 게시판 계약 「에디터 화면」 */
export type BoardPostEditor = 'NONE' | 'SIMPLE' | 'ADVANCED';
/** 댓글 에디터 수준 — 평문 · 간단 */
export type BoardCommentEditor = 'NONE' | 'SIMPLE';
/** 날짜 표시 방식 — 표준형(최근은 상대 시간, 올해는 월-일, 오래된 글은 연-월-일) · 유동형(모두 상대 시간) */
export type BoardDateDisplay = 'STANDARD' | 'RELATIVE';
/** 포인트 지급 — 포인트 규칙을 따름 · 지급 안 함 */
export type BoardPointMode = 'ON' | 'OFF';
/** 댓글 많은 글(보호 중) 삭제 방식 — 막기 · 예약 삭제 · 바로 삭제 */
export type BoardProtectDelete = 'BLOCK' | 'DELAY' | 'ALLOW';
/** 읽기 전용 잠금 — 새 글만 막기 · 새 글·댓글 막기. `null` = 잠금 없음(기본값이 없는 게시판 전용 칸) */
export type BoardWriteLock = 'POST' | 'POST_COMMENT';
/** 공통 말머리 4개 — 질문 · 후기 · 잡담 · 공지(운영진 전용 고정) */
export type BoardCommonPrefix = 'QUESTION' | 'REVIEW' | 'CHAT' | 'NOTICE';
/** 게시판 담당 역할 — 매니저 · 스텝 */
export type BoardStaffRole = 'MANAGER' | 'STAFF';
/** 담당 지정 자격 미달 사유 — 새싹 등급 · 이메일 미인증 · 정지 · 사업자 · 이미 5개 게시판 담당 · 이미 이 게시판 담당 */
export type BoardStaffIneligible = 'GRADE_TOO_LOW' | 'EMAIL_UNVERIFIED' | 'SUSPENDED' | 'BUSINESS' | 'BOARD_LIMIT' | 'ALREADY_ASSIGNED';

/**
 * 게시판 기본 설정(전역 한 행) — 모든 게시판에 일괄 적용되고, 게시판이 비워 둔(`null`) 정책 칸은 이 값을 따른다.
 * 범위는 `UtilBoard.SPECS` 가 원천이다(서버가 같은 범위로 다시 본다).
 */
export interface BoardDefaults {
    /** 날짜 표시 방식 */
    dateDisplay: BoardDateDisplay;
    writeMin: BoardWriteMin;
    commentMin: BoardCommentMin;
    /** 글 에디터 — 평문(Textarea) · 간단 · 고급. 전체 기본값 `SIMPLE` */
    postEditor: BoardPostEditor;
    /** 에디터 이미지 올리기 허용 — 고급 글 에디터에서만 쓰인다. 전체 기본값 `true` */
    imageUploadAllowed: boolean;
    /** 댓글 에디터 — 평문 · 간단. 전체 기본값 `NONE` */
    commentEditor: BoardCommentEditor;
    /** 글 작성 간격(초) 0~3600 */
    postGapSeconds: number;
    /** 하루 글 수 1~1000 */
    postDailyLimit: number;
    /** 댓글 작성 간격(초) 0~3600 */
    commentGapSeconds: number;
    /** 하루 댓글 수 1~1000 */
    commentDailyLimit: number;
    /** 목록 한 쪽 글 수 10~100 */
    listSize: number;
    /** 고정 공지 최대 0~5 */
    pinnedMax: number;
    indexable: boolean;
    searchable: boolean;
    inHome: boolean;
    /** 삭제된 글의 첨부를 보존 기간 뒤 영구 정리 */
    attachmentPurgeEnabled: boolean;
    /** 삭제 첨부 보존 기간(일) 1~3650 */
    attachmentRetentionDays: number;
    pointMode: BoardPointMode;
    /** 댓글 많은 글 보호 기준(보이는 댓글 수) 0~100, 0 = 끔 */
    commentProtectMin: number;
    /** 보호 중인 글의 수정도 막는다 */
    commentProtectEdit: boolean;
    commentProtectDelete: BoardProtectDelete;
    /** 예약 삭제까지의 시간(분) 5·10·30 */
    commentProtectDelayMinutes: number;
    /** 비회원에게 댓글을 늦게 보이는 시간(분) 0·10·30·60·180 */
    commentGuestDelayMinutes: number;
    /** 베스트 댓글 최소 추천 수 1~100 */
    bestCommentMinLikes: number;
    /** 게시판당 매니저 수 1~10 */
    managerMax: number;
    /** 게시판당 스텝 수 1~10 */
    staffMax: number;
    /** 매니저·스텝 하루 한도: 글 숨기기+삭제 1~50 */
    staffPostDailyLimit: number;
    /** 매니저·스텝 하루 한도: 댓글 삭제 1~100 */
    staffCommentDailyLimit: number;
    /** 매니저·스텝 하루 한도: 제재(경고+쓰기 제한) 1~20 */
    staffSanctionDailyLimit: number;
}

export type BoardDefaultsField = keyof BoardDefaults;

/** 게시판마다 덮어쓸 수 있는 정책 칸 — 나머지(날짜 표시·작성 속도·첨부 정리·베스트 댓글·스텝 수)는 기본 설정에만 있다 */
export const BOARD_POLICY_FIELDS = [
    'writeMin',
    'commentMin',
    'postEditor',
    'imageUploadAllowed',
    'commentEditor',
    'listSize',
    'pinnedMax',
    'indexable',
    'searchable',
    'inHome',
    'pointMode',
    'commentProtectMin',
    'commentProtectEdit',
    'commentProtectDelete',
    'commentProtectDelayMinutes',
    'commentGuestDelayMinutes',
    'managerMax',
    'staffPostDailyLimit',
    'staffCommentDailyLimit',
    'staffSanctionDailyLimit',
] as const satisfies readonly BoardDefaultsField[];

export type BoardPolicyField = (typeof BOARD_POLICY_FIELDS)[number];

/** 게시판별 정책 — 칸마다 `null` = 기본값을 따름. 유효값 = 게시판 값 ?? 기본값 */
export type BoardPolicy = { [K in BoardPolicyField]: BoardDefaults[K] | null };

/** 게시판을 가리키는 짧은 모양 — 이름·영문명은 `useLocalizedName` 으로 그린다 */
export interface BoardRef {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
}

/** `GET /api/admin/boards/defaults` */
export interface BoardDefaultsResponse {
    defaults: BoardDefaults;
    /** 게시판 수(꺼진 것 포함) */
    boardCount: number;
    /** 정책을 하나도 따로 정하지 않은(모두 기본값을 따르는) 게시판 수 */
    followingCount: number;
    /** 칸마다 그 칸을 따로 정한 게시판들 — 없으면 칸이 빠진다 */
    overrides: Partial<Record<BoardPolicyField, BoardRef[]>>;
    /** 마지막 저장 시각(UTC ISO) · 저장한 운영자 이름 — 저장한 적 없으면 null */
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/boards/defaults` — 칸 전부를 한 번에 보낸다. 응답은 `BoardDefaultsResponse` */
export type BoardDefaultsSaveRequest = BoardDefaults;

/** 게시판 목록의 한 줄 — `GET /api/admin/boards` */
export interface BoardListItem extends BoardRef {
    description: string;
    kind: BoardKind;
    layout: BoardLayout;
    color: BoardColor | null;
    active: boolean;
    /** 1부터 세는 자리 */
    sortOrder: number;
    /** 삭제되지 않은 글 수(숨김 포함) */
    postCount: number;
    managerCount: number;
    staffCount: number;
    /** 기본값과 다르게 정한 정책 칸 수(`null` 이 아닌 칸) */
    diffCount: number;
    /** 유효 글쓰기·댓글 최소 자격 — 목록이 「비회원 글쓰기·댓글」 딱지를 그린다 */
    writeMin: BoardWriteMin;
    commentMin: BoardCommentMin;
}

export interface BoardListResponse {
    boards: BoardListItem[];
}

/** `POST /api/admin/boards` — 일반 게시판만 만든다. 공통 말머리 4개는 서버가 함께 만든다. 응답 `BoardListItem`(201) */
export interface BoardCreateRequest {
    name: string;
    slug: string;
    description: string;
    layout: BoardLayout;
}

/** `PATCH /api/admin/boards/{id}/active` */
export interface BoardActiveRequest {
    active: boolean;
}

/** `PUT /api/admin/boards/order` — 모든 게시판 ID 의 새 순서(집합이 같아야 한다). 응답 `BoardListResponse` */
export interface BoardOrderRequest {
    ids: string[];
}

/** 말머리 하나 */
export interface BoardPrefix {
    id: string;
    name: string;
    /** 공통 말머리면 그 종류(이름·운영진 전용은 고정), 고유 말머리면 null */
    common: BoardCommonPrefix | null;
    /** 운영진 전용 — 운영진·매니저·스텝만 고른다 */
    staffOnly: boolean;
    /** 표시색 — null = 자동 */
    color: BoardColor | null;
    active: boolean;
    /** 쓰는 글(삭제 포함)·임시저장 수 — 0 이어야 지울 수 있다 */
    usageCount: number;
}

/** `GET /api/admin/boards/by-slug/{slug}` · `GET /api/admin/boards/{id}` — 게시판 설정 화면 한 번에 */
export interface BoardDetail extends BoardRef {
    description: string;
    kind: BoardKind;
    layout: BoardLayout;
    /** 읽는 사람의 보기 전환 단추(목록·카드) — 카드형은 끌 수만 있다 */
    viewToggle: boolean;
    color: BoardColor | null;
    allowAdult: boolean;
    /** 투자 고지 — 켜기만 된다(켠 뒤 끄면 409 `BOARD_INVESTMENT_NOTICE_LOCKED`) */
    investmentNotice: boolean;
    active: boolean;
    postCount: number;
    /** 읽기 전용 잠금 — 기본값이 없는 칸, null = 잠금 없음 */
    writeLock: BoardWriteLock | null;
    /** 게시판 안내문 — 평문 500자(원본은 정화된 HTML·이미지 5장, 에디터는 후속). null = 없음 */
    introText: string | null;
    /** 글쓰기 안내문 — 위와 같다 */
    writeGuideText: string | null;
    policy: BoardPolicy;
    /** 공통 4개 + 고유, 표시 순서대로 */
    prefixes: BoardPrefix[];
    /** 지금 기본 설정 — 「기본값 따름」 칸에 흐리게 보인다 */
    defaults: BoardDefaults;
    diffCount: number;
}

/** 저장할 고유 말머리 — `id` 가 null 이면 새로 만든다. 목록 순서가 표시 순서이고, 빠진 기존 말머리는 지운다(쓰는 글이 있으면 409 `PREFIX_IN_USE`) */
export interface BoardPrefixSaveItem {
    id: string | null;
    name: string;
    staffOnly: boolean;
    color: BoardColor | null;
    active: boolean;
}

/** 공통 말머리는 켜고 끄기만 보낸다(이름·운영진 전용은 고정) */
export interface BoardCommonPrefixSaveItem {
    id: string;
    active: boolean;
}

/**
 * `PUT /api/admin/boards/{id}` — 기본 정보 + 정책 + 말머리를 **한 트랜잭션**으로 저장한다. 응답은 `BoardDetail`.
 * 권한: 기본 정보·말머리 `board.manage`, 정책(잠금·안내문 포함) `board.settings`, 매니저·스텝 한도 칸 `board.staff.limit` — 바뀐 부분의 권한이 없으면 서버가 403.
 */
export interface BoardSaveRequest {
    name: string;
    nameEn: string | null;
    description: string;
    layout: BoardLayout;
    viewToggle: boolean;
    color: BoardColor | null;
    allowAdult: boolean;
    investmentNotice: boolean;
    writeLock: BoardWriteLock | null;
    introText: string | null;
    writeGuideText: string | null;
    policy: BoardPolicy;
    commonPrefixes: BoardCommonPrefixSaveItem[];
    prefixes: BoardPrefixSaveItem[];
}

/** 게시판 매니저·스텝 한 명 — `GET /api/admin/boards/{id}/staff` */
export interface BoardStaffMember {
    memberId: string;
    nickname: string;
    gradeKey: GradeKey;
    role: BoardStaffRole;
    /** 지정 시각(UTC ISO) */
    appointedAt: string;
    /** 지정한 운영자 이름 — 모르면 빈 글자 */
    appointedBy: string;
}

/** `POST /api/admin/boards/{id}/staff` — 응답은 담당 전체(`BoardStaffMember[]`) */
export interface BoardStaffAppointRequest {
    memberId: string;
    role: BoardStaffRole;
}

/** 담당 후보 — `GET /api/admin/boards/staff-candidates?q=&boardId=`(q = 닉네임 일부 · 이메일 전체 일치 · 회원 ID, 최대 20명. `BUSINESS` 사유는 서버가 아직 판정하지 않는다) */
export interface BoardStaffCandidate {
    memberId: string;
    nickname: string;
    /** 가린 이메일 */
    email: string;
    gradeKey: GradeKey;
    /** 지금 맡은 게시판 수(매니저·스텝 합산, 최대 5) */
    boardCount: number;
    /** 자격 미달 사유 — null 이면 지정할 수 있다 */
    ineligible: BoardStaffIneligible | null;
}

// ── 기본 설정 하위 화면(신고 정책·보안·알림·개인정보 보유기간) — 화면마다 자기 GET·PUT ─

/** 기본 설정 하위 화면 — `GET·PUT /api/admin/boards/defaults/{tab}` */
export type BoardDefaultsTab = 'report' | 'security' | 'notifications' | 'privacy';

/** 하위 화면 응답의 공통 모양 — 설정 + 마지막 저장 */
export interface BoardDefaultsTabResponse<T> {
    settings: T;
    /** 마지막 저장 시각(UTC ISO) · 저장한 운영자 이름 — 저장한 적 없으면 null */
    updatedAt: string | null;
    updatedBy: string | null;
}

/**
 * 신고 사유 — 스텝·매니저 처리 사유 코드(`docs/domain/operator-permissions.md` "처리 규칙")와 **같은 코드 체계**를 쓴다.
 * 같은 뜻은 같은 코드: `SPAM_AD`(스팸·광고) · `ABUSE`(비방·인신공격 — 신고 화면 이름은 「욕설·비방」) · `OTHER`(기타).
 * 신고에만 있는 것: `OBSCENE`(음란·선정) · `ILLEGAL_FILMING`(불법촬영물·아동성착취물 — 즉시 숨김) · `RIGHTS`(개인정보 노출·명예훼손 — 임시조치).
 * 스텝 처리에만 있는 것: 투자 권유 · 게시판 주제 불일치(신고 사유로 넣을지는 미확정).
 */
export type BoardReportReason = 'SPAM_AD' | 'ABUSE' | 'OBSCENE' | 'ILLEGAL_FILMING' | 'RIGHTS' | 'OTHER';
/** 자동 숨김 대상 */
export type BoardAutoHideTarget = 'POST' | 'COMMENT' | 'BOTH';
/** 운영자에게 신고 접수를 알리는 단위 — 글·댓글당 최초 1회 · 신고마다 */
export type BoardReportNotifyScope = 'PER_CASE' | 'PER_REPORT';

/**
 * 신고 정책 — `GET·PUT /api/admin/boards/defaults/report`.
 * 화면에 읽기 전용으로만 보이는 고정 규칙(요청에 없다): 자동 숨김은 서로 다른 회원 수로 센다 · 불법촬영물 등 사유는 1건으로 즉시 숨김(끌 수 없음, 전기통신사업법 제22조의5).
 */
export interface BoardReportSettings {
    /** 신고 기능 사용 */
    reportEnabled: boolean;
    /** 자동 숨김 기준(서로 다른 회원 수) 0~100, 0 = 끔 */
    autoHideThreshold: number;
    autoHideTarget: BoardAutoHideTarget;
    /** 자동 숨김 셈에 넣을 최소 등급 — 이보다 낮은 회원의 신고는 접수만 되고 셈에서 빠진다 */
    autoHideMinGrade: string;
    /** 운영자 작성물·공지 글은 자동 숨김에서 뺀다 */
    autoHideExemptStaffPosts: boolean;
    /** 권리침해 신고 임시조치 기간(일) 1~30 — 정보통신망법 제44조의2 */
    rightsTempMeasureDays: number;
    /** 회원 한 명의 하루 신고 수(전체 게시판 합산, 한국시간 0시) 0~100, 0 = 무제한 */
    dailyReportLimit: number;
    /** 신고 간격(초) 0~3600 */
    reportCooldownSeconds: number;
    /** 기간 안 반려 누적 시 신고 차단 — 횟수 0~50(0 = 끔) · 기간(일) 1~365 · 차단 기간(일) 1~90 */
    rejectionLimitCount: number;
    rejectionLimitDays: number;
    rejectionBlockDays: number;
    notifyOperatorScope: BoardReportNotifyScope;
    /** 숨김·삭제·복구 시 작성자에게 사유와 함께 알림 */
    notifyAuthorOnAction: boolean;
    /** 처리 결과를 신고자에게 알림 */
    notifyReporterOnResult: boolean;
}

/** 금지어에 걸렸을 때 — 저장 거부(어떤 단어인지 알려 줌) · 가려서(***) 저장 */
export type BoardBlockedWordsAction = 'BLOCK' | 'MASK';

/** 보안 — `GET·PUT /api/admin/boards/defaults/security` */
export interface BoardSecuritySettings {
    /** 조회수 중복 방지 시간 1~168 — 같은 회원(비회원은 IP)이 이 시간 안에 다시 봐도 1회 */
    viewCountDedupHours: number;
    blockedWordsEnabled: boolean;
    blockedWordsAction: BoardBlockedWordsAction;
    /** 금지어 — 최대 500개, 단어당 1~30자, 중복 없음(대소문자 무시) */
    blockedWords: string[];
    /** 첫 등급 회원의 글·댓글당 외부 링크 수 0~20, 0 = 링크 금지 */
    newMemberLinkLimit: number;
    /** 첫 등급 회원의 글당 이미지 수 0~20 */
    newMemberImageLimit: number;
    /** 같은 내용 글 다시 쓰기 거절 시간(분) 0~1440, 0 = 끔 */
    duplicatePostWindowMinutes: number;
    /** 본문 외부 링크에 rel="nofollow ugc noopener" */
    externalLinkNofollow: boolean;
    /** 외부 링크를 누르면 경고 페이지 */
    externalLinkWarn: boolean;
    /** 비회원 글·댓글에 자동입력 방지 */
    guestCaptchaRequired: boolean;
    /** 비회원 작성자 이름 길이 1~20 */
    guestNameMin: number;
    guestNameMax: number;
    /** 비회원 수정·삭제 비밀번호 최소 길이 4~20 — 서버는 해시로만 저장 */
    guestPasswordMin: number;
    /** 비회원 글·댓글당 외부 링크 수 0~20, 0 = 링크 금지 */
    guestLinkLimit: number;
    /** 비회원 이미지 올리기 */
    guestImageUploadAllowed: boolean;
}

/**
 * 알림(회원 알림에 같이 쓰는 값) — `GET·PUT /api/admin/boards/defaults/notifications`.
 * 이벤트별 칸(옛 `memberDefaults` — 사이트·이메일·회원이 끌 수 있음)은 회원 이벤트(`/api/admin/member-events`, shared V58)로 옮겨져 응답에 없다(서버는 요청에 와도 무시한다).
 */
export interface BoardNotificationSettings {
    /** 같은 글의 새 댓글 알림을 묶는 시간(분) 0~60, 0 = 묶지 않음 */
    groupWindowMinutes: number;
    /** 이메일 조용한 시간(이메일만 미룬다) */
    emailQuietHoursEnabled: boolean;
    /** `HH:mm`(서비스 시간대) */
    emailQuietStart: string;
    emailQuietEnd: string;
    /** 회원 알림 보관 기간(일) 7~365 */
    memberNotificationRetentionDays: number;
}

/** 개인정보 보유기간의 축 */
export type BoardPrivacyAxis = 'ip' | 'guest' | 'deletedContent' | 'reportRecord' | 'staffAction';
/** 비회원 글 처리 방식 — 익명화(이름 ***·비밀번호·IP 파기, 글은 남김) · 삭제(글·답글·댓글 삭제 + 제목·본문·첨부 파기, 되돌릴 수 없음) */
export type BoardGuestRetentionMode = 'ANONYMIZE' | 'DELETE';

/** 개인정보 보유기간 — `GET·PUT /api/admin/boards/defaults/privacy`. 바꾸면 감사 기록 대상이다 */
export interface BoardPrivacySettings {
    /** 전체 스위치 — 파기는 되돌릴 수 없어 기본 꺼짐 */
    retentionEnabled: boolean;
    /** 작성 IP 보유(일) 90~3650 — 비회원 글의 IP 포함 */
    ipRetentionDays: number;
    /** 비회원 글 보유(일) 0~3650, 0 = 처리 안 함 */
    guestRetentionDays: number;
    guestMode: BoardGuestRetentionMode;
    /** 삭제된 글·댓글 원문(휴지통) 보유(일) 1~365 */
    deletedContentRetentionDays: number;
    /** 신고 기록 보유(일) 90~1825 — 지나면 신고자 정보 익명화 */
    reportRecordRetentionDays: number;
    /** 스텝·매니저 처리 기록 보유(일) 90~1825 — 지나면 처리자 외 개인정보 익명화 */
    staffActionRetentionDays: number;
}

/** 개인정보 보유기간 응답 — 설정 + 축별 대상 수(저장된 설정 기준) + 일반 화면의 첨부 보존 기간(같게 두라는 경고용) */
export interface BoardPrivacyResponse extends BoardDefaultsTabResponse<BoardPrivacySettings> {
    /** 축별 대상 수 — 서버가 축 이름을 키로 준다(빠진 축은 0 으로 본다) */
    targets: Partial<Record<BoardPrivacyAxis, number>>;
    attachmentRetentionDays: number;
    /** 마지막 배치 실행 시각 — 없으면 null */
    lastRunAt: string | null;
}

/** 파기 대상 한 줄 — `GET /api/admin/boards/defaults/privacy/targets?axis=`. `id` = `post-<id>`·`comment-<id>`, 댓글의 `title` 은 본문 앞 40자 */
export interface BoardPrivacyTarget {
    id: string;
    title: string;
    boardName: string;
    /** 작성(기록) 시각 UTC ISO */
    createdAt: string;
}

/** 지금 실행 결과 — `POST /api/admin/boards/defaults/privacy/run` `{ axis }` */
export interface BoardPrivacyRunResult {
    axis: BoardPrivacyAxis;
    processed: number;
}
