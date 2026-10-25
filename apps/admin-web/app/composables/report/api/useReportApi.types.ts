/**
 * 신고 관리 API(`/api/admin/reports…`)가 주고받는 모양 — 계약 `docs/features/report-admin-contract.md` 「API」 그대로(서버 `server/admin` 의 신고 관리 DTO 와 같은 칸 이름).
 * ID 는 모두 문자열, 시각은 UTC ISO 글자. 신고는 **대상(글·댓글)별 사건**으로 묶인다 — 대상마다 열린(PENDING·IN_REVIEW) 사건은 하나.
 * 계약에 모양이 적히지 않은 칸(처리 기록 `events` 한 줄, 닫힌 사건의 `reasonCode`·`note`·`falseReport`)은 게시글 관리 조치 기록과 같은 꼴로 가정했다 — 표시는 없으면 비운다.
 */

/** 사건 상태 — 대기 · 검토 중(누가 열어 봄) · 처리됨(조치) · 무시 */
export type ReportCaseStatus = 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED';

/** 상태 거르기 — `OPEN`(기본) = 대기 + 검토 중 */
export type ReportStatusFilter = 'OPEN' | 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ALL';

export type ReportTargetType = 'POST' | 'COMMENT';

/** 신고 사유 코드 — 게시판 신고 정책(`BoardReportReason`)과 같은 체계. 조치 사유 코드도 같다 */
export type ReportReasonCode = 'SPAM_AD' | 'ABUSE' | 'OBSCENE' | 'ILLEGAL_FILMING' | 'RIGHTS' | 'OTHER';

/** 검색 대상 — 대상 글 제목(기본) · 대상 작성자 · 사건 번호 */
export type ReportSearchField = 'TITLE' | 'AUTHOR' | 'CASE_NO';

/** 정렬 — 마지막 신고 최신(기본) · 오래된 · 신고 많은 순 */
export type ReportSort = 'newest' | 'oldest' | 'most';

/** 대상의 지금 상태 */
export type ReportTargetState = 'PUBLIC' | 'HIDDEN' | 'DELETED';

/** 숨김 까닭 — 신고 자동 숨김(REPORTED) · 불법촬영물 즉시 숨김(ILLEGAL) · 권리침해 임시조치(RIGHTS) · 운영자 숨김(STAFF) */
export type ReportHiddenReason = 'REPORTED' | 'ILLEGAL' | 'RIGHTS' | 'STAFF';

/** 조치 — 숨김 · 삭제 · 자동 숨김 확정 · 무시 */
export type ReportCaseAction = 'HIDE' | 'DELETE' | 'KEEP_HIDDEN' | 'DISMISS';

/** `POST /{id}/resolve` 의 조치 */
export type ReportResolveAction = Exclude<ReportCaseAction, 'DISMISS'>;

/** 사건 안 신고 한 건의 상태 — 조치로 닫힘 ACCEPTED, 허위 표시 무시 REJECTED, 그냥 무시 DISMISSED */
export type ReportEntryStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DISMISSED';

/** 처리 기록 종류 */
export type ReportEventType = 'REPORTED' | 'AUTO_HIDDEN' | 'REVIEW_STARTED' | 'RESOLVED' | 'DISMISSED' | 'REOPENED';

export interface ReportTarget {
    postId: string;
    /** 댓글 대상이면 댓글 ID */
    commentId: string | null;
    /** 대상 글 제목(댓글이면 그 댓글이 달린 글 제목). 원문을 파기한 대상은 null — 화면은 「원문 파기됨」 */
    title: string | null;
    /** 대상 본문 앞 80자. 원문을 파기한 대상은 null */
    excerpt: string | null;
    state: ReportTargetState;
    hiddenReason: ReportHiddenReason | null;
    /** 권리침해 임시조치면 다시 보이는 시각 */
    hiddenUntil: string | null;
}

export interface ReportBoardRef {
    id: string;
    name: string;
    /** 영문명 — 서버가 주면 쓴다(계약에는 없다) */
    nameEn?: string | null;
}

/** 대상 작성자 — 비회원은 `id` null, `name` 이 비회원 이름 */
export interface ReportAuthorRef {
    kind: 'MEMBER' | 'GUEST';
    id: string | null;
    name: string;
    withdrawn: boolean;
}

export interface ReportOperatorRef {
    id: string;
    name: string;
}

export interface ReportReasonCount {
    code: ReportReasonCode;
    count: number;
}

/** 목록·상세의 사건 한 줄(`ReportCaseItem`) */
export interface ReportCaseItem {
    id: string;
    /** 사람이 읽는 사건 번호 */
    caseNo: string | number;
    status: ReportCaseStatus;
    targetType: ReportTargetType;
    target: ReportTarget;
    board: ReportBoardRef;
    author: ReportAuthorRef;
    reportCount: number;
    /** 사유별 신고 수 */
    reasons: ReportReasonCount[];
    firstReportedAt: string;
    lastReportedAt: string;
    /** 검토자(검토 중·닫힌 사건) */
    reviewer: ReportOperatorRef | null;
    closedAt: string | null;
    closedBy: ReportOperatorRef | null;
    action: ReportCaseAction | null;
    /** 닫힌 사건의 조치 사유 코드 — 계약 밖(가정). 없으면 처리 기록의 덧붙임에서 찾는다 */
    reasonCode?: ReportReasonCode | null;
    /** 닫힌 사건의 조치 글 — 계약 밖(가정) */
    note?: string | null;
    /** 허위 신고로 표시한 무시 — 계약 밖(가정) */
    falseReport?: boolean;
}

/** `GET /api/admin/reports` */
export interface ReportCaseListView {
    total: number;
    /** 0부터 */
    page: number;
    size: number;
    items: ReportCaseItem[];
}

/** `GET /api/admin/reports` 조건 — 빈 값은 보내지 않는다 */
export interface ReportCaseQuery {
    status?: ReportStatusFilter;
    targetType?: ReportTargetType;
    reason?: ReportReasonCode;
    searchField?: ReportSearchField;
    q?: string;
    /** 마지막 신고 시작 순간(UTC ISO — 사이트 시간대 그날 00:00:00.000) */
    from?: string;
    /** 마지막 신고 끝 순간(UTC ISO — 사이트 시간대 그날 23:59:59.999) */
    to?: string;
    /** 게시판 ID */
    board?: string;
    /** 그 글과 그 글의 댓글 사건만 */
    postId?: string;
    sort?: ReportSort;
    /** 0부터 */
    page?: number;
    /** 기본 20, 최대 50 */
    size?: number;
}

/** `GET /api/admin/reports/summary` */
export interface ReportSummary {
    pending: number;
    inReview: number;
}

export type ReportContentFormat = 'TEXT' | 'HTML';

/** 신고 당시 스냅샷(첫 신고 때) */
export interface ReportSnapshot {
    title: string | null;
    body: string | null;
    authorName: string | null;
}

/** 대상 원문 — 지운·파기된 대상은 `body` 가 null */
export interface ReportContent {
    format: ReportContentFormat;
    body: string | null;
    snapshot: ReportSnapshot | null;
}

/** 사건 안 신고 한 건 — 신고자 정보는 운영자에게만 */
export interface ReportEntry {
    id: string;
    reporter: { id: string | null; name: string; withdrawn: boolean };
    reason: ReportReasonCode;
    memo: string | null;
    createdAt: string;
    status: ReportEntryStatus;
}

/** 작성자 카드 — 제재(`sanction`)는 커머스 병합 뒤라 지금은 늘 null */
export interface ReportAuthorCard {
    id: string | null;
    name: string;
    /** 등급 키(회원만) */
    grade: string | null;
    joinedAt: string | null;
    /** 이 작성자 대상 사건의 최근 90일 결과 */
    closedCases: { resolved: number; dismissed: number };
    sanction: null;
}

/** 처리 기록 한 줄 — 모양은 가정(게시글 관리 조치 기록과 같은 꼴). `detail` 은 서버가 정한 덧붙임(reasonCode·note·falseReport·action 등) */
export interface ReportEvent {
    id: string;
    type: ReportEventType;
    actor: { kind: 'OPERATOR' | 'SYSTEM' | 'MEMBER'; id: string | null; name: string | null } | null;
    detail: Record<string, unknown> | null;
    createdAt: string;
}

/** `GET /api/admin/reports/{id}` — PENDING 사건은 열람과 함께 IN_REVIEW 가 된다 */
export interface ReportCaseDetail {
    case: ReportCaseItem;
    content: ReportContent;
    reports: ReportEntry[];
    authorCard: ReportAuthorCard;
    events: ReportEvent[];
    /** 공개 사이트의 대상 절대 주소 — 사이트 공개 주소가 없거나 볼 수 없는 대상이면 null */
    publicUrl: string | null;
}

/** `POST /{id}/resolve` */
export interface ReportResolveRequest {
    action: ReportResolveAction;
    reasonCode: ReportReasonCode;
    /** 1~500자 */
    note: string;
}

/** `POST /{id}/dismiss` */
export interface ReportDismissRequest {
    reasonCode?: ReportReasonCode;
    note?: string;
    /** 허위 신고로 표시 — 게시판 정책의 반려 제한이 세는 값 */
    falseReport: boolean;
}

/** `POST /dismiss` — 일괄은 무시만 */
export interface ReportBulkDismissRequest {
    /** 1~50 */
    ids: string[];
    falseReport: boolean;
    note?: string;
}

export interface ReportBulkFailure {
    id: string;
    code: string;
}

export interface ReportBulkResult {
    done: string[];
    failed: ReportBulkFailure[];
}
