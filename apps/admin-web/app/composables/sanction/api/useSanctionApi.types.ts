/**
 * 관리자 제재 API(`/api/admin/sanctions…`)가 주고받는 모양 — 서버 `server/admin` `com.community.sanction.dto`(`SanctionLineView`·`SanctionPreviewView`·`SanctionOptionView`·`SanctionTargetView`·`SanctionListView`·요청 DTO)와 같은 칸 이름. 계약 `docs/features/member-sanction-contract.md`,
 * 모양의 근거는 community `docs/api-sanction.md` 3절(`SanctionLine`·`preview`·`options[].state`)을 아이로빗 운영자 권한에 맞춘 것이다:
 *   - community 의 「최고 관리자만」(`requiresAdmin`·`403 ADMIN_ONLY`)은 아이로빗 권한 `sanction.ban`(영구 정지·단계 건너뛰기·영구 정지 해제)으로 바꿨다 → `requiresBan`·`403 PERMISSION_DENIED`.
 *   - 운영자는 `users` 가 아니라 따로라 「운영진 계정」 막힘(`TARGET_STAFF`·`SANCTION_TARGET_NOT_ALLOWED`)은 없다.
 *   - 게시판 한정 제재(`BOARD_SANCTION`)는 이번 범위가 아니다(결정 1).
 *   - 이의제기 마감(`appealDeadline` = 시작 + 30일, 결정 3)을 더했다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 회원 ID 는 회원 관리의 `memberNo` 와 같은 값이다.
 */

/** 제재 단계 — 경고(1회째 rank 1 · 2회째 rank 2) → 정지 3일(3) → 7일(4) → 30일(5) → 영구 정지(6) */
export type SanctionKind = 'WARNING' | 'SUSPEND_3' | 'SUSPEND_7' | 'SUSPEND_30' | 'BAN';

/** 제재 상태 — 유효·진행 중 · 기간 끝 또는 영구 정지로 대체 · 운영자 해제(횟수에 남음) · 이의제기 인용으로 무효(횟수에서 빠짐) */
export type SanctionStatus = 'ACTIVE' | 'ENDED' | 'LIFTED' | 'VOIDED';

/** 목록 범위 — 전체 · 진행 중인 정지·영구 정지 · 끝남(기간 끝·해제·무효) */
export type SanctionScope = 'ALL' | 'ACTIVE' | 'CLOSED';

/** 검색 대상 — 회원 닉네임(2자 이상, 부분 일치) · 제재 번호(숫자) */
export type SanctionSearchField = 'NICKNAME' | 'SANCTION_NO';

export type SanctionSort = 'newest' | 'oldest';

/** 이의제기 상태 */
export type AppealStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

/**
 * 단계 선택지의 상태 — 다음 단계(권장) · 건너뛰기(`sanction.ban` + `skipStep` + 근거) · 이미 지난 단계라 못 줌 · 이 회원에게는 못 줌.
 * 지금 운영자가 못 하는 선택지도 `state` 는 그대로 오고 `requiresBan` 으로 알린다(화면이 권한을 보고 비활성으로 보인다).
 */
export type SanctionOptionState = 'NEXT' | 'SKIP' | 'LOWER' | 'BLOCKED';

/**
 * 제재를 줄 수 없는(또는 영구 정지만 되는) 까닭 — 이미 영구 정지 · 정지 중(영구 정지만 가능) ·
 * 봉인된 익명 글 작성자가 대상 보호로 막힘(`UNAVAILABLE` — 신원을 짐작할 수 없게 까닭을 뭉뚱그린다, 모든 단계를 줄 수 없다. 부여는 409 `ANONYMOUS_TARGET_UNAVAILABLE`)
 */
export type SanctionBlockedReason = 'ALREADY_BANNED' | 'SUSPENDED' | 'UNAVAILABLE';

/**
 * 제재 대상 회원 — `status` 는 회원 관리의 계정 상태와 같은 값(ACTIVE·SUSPENDED·BANNED …).
 * **익명 글 작성자에게 준 제재(「이 글 작성자」 — anonymous-board-contract 정책 9)는 `sealed: true`** 이고 `id`·`nickname`(그 밖 칸)은 null 이다 — 화면은 「익명 글 작성자(봉인)」(`UtilSanctionMessages.userName`).
 * 열람 권한이 있는 운영자도 제재 화면에서는 봉인 그대로다(신원은 열람 창으로만)
 */
export interface SanctionUserRef {
    id: string | null;
    nickname: string | null;
    /** 봉인된 익명 글 작성자 — 옛 응답(칸 없음)은 false */
    sealed?: boolean;
    /** 등급 키 — 모르면 null */
    grade: string | null;
    status: string;
}

/** 부여·해제·답변한 운영자 — 지워진 운영자면 이름이 null 일 수 있다 */
export interface SanctionActorRef {
    id: string;
    name: string | null;
}

/** 근거 신고 사건 — 신고 처리에서 줬거나(`resolve` 의 `sanction`) 부여 때 이었다(`reportCaseId`) */
export interface SanctionReportRef {
    id: string;
    /** 사람이 읽는 사건 번호(신고 관리의 `caseNo`) */
    caseNo: string | number;
}

/** 제재에 붙은 이의제기 요약 */
export interface SanctionAppealRef {
    id: string;
    no: number;
    status: AppealStatus;
}

/** 제재 한 줄(community `SanctionLine`) — 목록·현황(`preview.history`)·이의제기 상세가 같이 쓴다 */
/**
 * 현황의 진행 중 정지 — 회원 단위 현황에서 봉인 제재 정지(`sealed`)면 `id`·번호·사유·부여자·계기 신고·이의제기를 비워 준다(anonymous-board-contract 2026-11-11 —
 * 그 끈으로 전체 목록의 봉인 줄·계기 익명 글과 이어지지 않게). 풀기는 회원 기준 `liftActive`.
 */
export type SanctionRunningLine = Omit<SanctionLine, 'id'> & { id: string | null };

export interface SanctionLine {
    id: string;
    /** 사람이 읽는 제재 번호 */
    no: number;
    kind: SanctionKind;
    status: SanctionStatus;
    /** 단계 순위(1~6) */
    rank: number;
    /** 경고일 때 유효한 경고 중 몇 번째(1·2), 그 밖 null */
    warningNo: number | null;
    /** 단계를 건너뛴 제재 */
    skipped: boolean;
    user: SanctionUserRef;
    startsAt: string;
    /** 기간 정지만 — 경고·영구 정지는 null */
    endsAt: string | null;
    /** 운영자가 적은 사유(회원에게 그대로 전달된다 — 글자로만 그린다) */
    reason: string;
    issuedBy: SanctionActorRef | null;
    createdAt: string;
    reportCase: SanctionReportRef | null;
    liftedBy: SanctionActorRef | null;
    liftedAt: string | null;
    liftReason: string | null;
    voidedAt: string | null;
    /** 기간 끝·영구 정지로 대체돼 끝난 시각 */
    endedAt: string | null;
    appeal: SanctionAppealRef | null;
    /** 이의제기 마감(시작 + 30일) — 무효면 null */
    appealDeadline: string | null;
    /**
     * 봉인 제재(익명 글·댓글로 준 제재 — anonymous-board-contract 정책 9)인가. 회원 단위 현황(`preview?userId=`)의 `active` 에만 온다 —
     * 회원 단위 화면은 봉인 줄을 이력에서 빼서 받지만, 지금 정지가 봉인 제재 때문이면 `active` 는 그 정지이고 이 칸이 true 다(화면은 「작성자 열람」 안내만, 제재 링크는 그리지 않는다). 옛 응답(칸 없음)은 false
     */
    sealed?: boolean;
}

/** `GET /api/admin/sanctions` 조건 — 빈 값은 보내지 않는다 */
export interface SanctionQuery {
    scope?: SanctionScope;
    kind?: SanctionKind;
    searchField?: SanctionSearchField;
    q?: string;
    /** 부여일 시작 순간(UTC ISO — 사이트 시간대 그날 00:00:00.000) */
    from?: string;
    /** 부여일 끝 순간(UTC ISO — 사이트 시간대 그날 23:59:59.999) */
    to?: string;
    /** 그 회원의 제재만(회원 상세·신고 서랍의 「이 회원 제재 보기」) */
    userId?: string;
    sort?: SanctionSort;
    /** 0부터 */
    page?: number;
    /** 기본 20, 최대 50 */
    size?: number;
}

/** `GET /api/admin/sanctions` — `counts` 는 거르기와 무관한 전체 건수(`all` 은 경고 포함 전부) */
export interface SanctionListView {
    counts: { all: number; active: number; closed: number };
    total: number;
    page: number;
    size: number;
    items: SanctionLine[];
}

/** `GET /api/admin/sanctions/targets?q=` — 부여 대화창의 회원 찾기(닉네임 2자 이상, 최대 10명, 탈퇴 회원 제외) */
export interface SanctionTarget {
    id: string;
    nickname: string;
    grade: string | null;
    status: string;
    /** 유효한 경고 수(0~2) */
    warningCount: number;
}

export interface SanctionOption {
    kind: SanctionKind;
    rank: number;
    state: SanctionOptionState;
    /** `sanction.ban` 이 있어야 줄 수 있다(영구 정지·단계 건너뛰기) */
    requiresBan: boolean;
}

/** `GET /api/admin/sanctions/preview?userId=` — 회원의 제재 현황. 없는 회원·탈퇴(진행 중 포함)는 `404 SANCTION_TARGET_NOT_FOUND` */
export interface SanctionPreview {
    user: SanctionUserRef;
    /** 유효한(무효 아닌) 경고 수 0~2 */
    warningCount: number;
    blockedReason: SanctionBlockedReason | null;
    /** 다음 단계 — 이미 영구 정지면 null */
    next: { kind: SanctionKind; rank: number } | null;
    /** 단계 차례(WARNING → BAN) */
    options: SanctionOption[];
    /** 진행 중인 정지·영구 정지 — 없으면 null */
    active: SanctionRunningLine | null;
    /** 이 회원의 제재 전체(최근 것이 위, 최대 30개, 무효 포함) — 회원 단위 현황은 봉인 제재 줄을 빼서 준다 */
    history: SanctionLine[];
    /**
     * 횟수(`warningCount` 등 서버 값)에 들어간 봉인 제재(익명 글로 받은 제재) 수 — 그 줄은 `history` 에 없다.
     * 화면은 0 보다 크면 횟수 옆에 「(봉인 n건 포함)」과 「이력에 보이지 않아요」 안내를 붙인다. 옛 응답(칸 없음)은 0
     */
    sealedCount?: number;
}

/**
 * `POST /api/admin/sanctions` → `201 SanctionLine`.
 * 400 `VALIDATION`(`fields.reason` 1~500자 · `fields.confirmNickname` 영구 정지인데 닉네임이 다름 · `fields.skipStep` 다음 단계보다 높은데 건너뛰기가 아님 · `fields.kind`) ·
 * 403 `PERMISSION_DENIED`(영구 정지·건너뛰기에 `sanction.ban` 없음) · 404 `SANCTION_TARGET_NOT_FOUND` · 409 `SANCTION_ALREADY_BANNED`·`SANCTION_ALREADY_SUSPENDED`·`SANCTION_STEP_TOO_LOW` ·
 * 409 `ANONYMOUS_TARGET_UNAVAILABLE`(봉인된 익명 글 작성자가 대상 보호로 막힘 — 「지금 이 작성자에게는 제재를 걸 수 없어요.」, 현황은 `blockedReason: 'UNAVAILABLE'`)
 */
export interface SanctionCreateRequest {
    /** 대상 회원 — 익명 글·댓글 작성자(봉인)에게 줄 때는 비우고 아래 `anonymousPostId`·`anonymousCommentId` 하나를 보낸다(둘 이상이면 400, 익명 글이 아니면 409) */
    userId?: string;
    /** 이 익명 글의 작성자에게 준다(anonymous-board-contract 「API 계약」 제재) */
    anonymousPostId?: string;
    /** 이 익명 댓글의 작성자에게 준다 */
    anonymousCommentId?: string;
    kind: SanctionKind;
    /** 1~500자 — 회원에게 그대로 전달된다 */
    reason: string;
    /** 다음 단계보다 높은 제재 */
    skipStep: boolean;
    /** 영구 정지면 회원 닉네임을 그대로, 그 밖 null. 봉인된 익명 작성자의 영구 정지는 닉네임을 모르므로 null(가정 — 서버가 봉인 대상이면 확인 칸을 보지 않는다) */
    confirmNickname: string | null;
    /** 근거 신고 사건(연결만 — 사건을 닫지는 않는다) */
    reportCaseId?: string | null;
}

/** `POST /api/admin/sanctions/{id}/lift` → `200 SanctionLine` — 진행 중 정지(`sanction.manage`)·영구 정지(`sanction.ban`)만. 409 `SANCTION_NOT_LIFTABLE` · 404 `SANCTION_NOT_FOUND` */
export interface SanctionLiftRequest {
    /** 1~500자 */
    reason: string;
}

/**
 * 신고 처리(`POST /api/admin/reports/{id}/resolve`)에 곁들이는 작성자 제재 — 대상은 사건의 작성자(서버가 정한다), 사건이 제재에 이어진다(같은 트랜잭션).
 * `reason` 이 없으면(null) 처리 글(`note`)과 같다. 제재 오류(위 409·403·400)가 나면 콘텐츠 조치도 일어나지 않는다.
 */
export interface SanctionWithReport {
    kind: SanctionKind;
    skipStep: boolean;
    confirmNickname: string | null;
    /** 1~500자 — null 이면 처리 글과 같다 */
    reason: string | null;
}
