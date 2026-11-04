import type { GroupCategoryRef, GroupPerson } from '~/composables/group/api/useGroupAdminApi.types';
import type { DealView } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { SanctionLine, SanctionPreview, SanctionWithReport } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 신고 관리 API(`/api/admin/reports…`)가 주고받는 모양 — 계약 `docs/features/report-admin-contract.md` 「API」 그대로(서버 `server/admin` 의 신고 관리 DTO 와 같은 칸 이름).
 * ID 는 모두 문자열, 시각은 UTC ISO 글자. 신고는 **대상(글·댓글)별 사건**으로 묶인다 — 대상마다 열린(PENDING·IN_REVIEW) 사건은 하나.
 * 계약에 모양이 적히지 않은 칸(처리 기록 `events` 한 줄, 닫힌 사건의 `reasonCode`·`note`·`falseReport`)은 게시글 관리 조치 기록과 같은 꼴로 가정했다 — 표시는 없으면 비운다.
 *
 * **소모임 대상(`GROUP`, 서버 V95 — `AdminReportQueryService`·`ReportCaseActionService` 와 맞췄다)**:
 *   - `target`: `postId`·`commentId` null · `groupId` = 소모임 ID · `title` = 지금 소모임 이름 · `excerpt` = 소개 앞 80자 · `state` = `PUBLIC`(열림)·`INACTIVE`(비활성)·`DELETED`(해산·없음) · 숨김 칸 null.
 *   - `board` = 그 소모임의 게시판 · `author` = 신고 당시 모임장 · `content` = `TEXT` 소개 · 스냅샷 `title` = 신고 당시 이름, `body` = 소개, `authorName` = 모임장 · 상세 `group` = 지금 소모임 값.
 *   - 조치는 `DEACTIVATE`(소모임 비활성화 — 관리자 소모임 관리의 비활성화와 같은 서비스, 모임장 알림, 비활성 기록에 이 사건, **글 1~200자**)와 무시뿐. 사유 코드는 소모임 사유 4종(섞으면 400 `fields.reasonCode`).
 *   - 목록 거르기 `targetType=GROUP` · `groupId`(그 소모임 사건), 제목 검색(`searchField=TITLE`)은 지금 소모임 이름도 찾는다. 자동 숨김은 없다.
 */

/** 사건 상태 — 대기 · 검토 중(누가 열어 봄) · 처리됨(조치) · 무시 */
export type ReportCaseStatus = 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED';

/** 상태 거르기 — `OPEN`(기본) = 대기 + 검토 중 */
export type ReportStatusFilter = 'OPEN' | 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'DISMISSED' | 'ALL';

export type ReportTargetType = 'POST' | 'COMMENT' | 'GROUP';

/**
 * 신고 사유 코드 — 글·댓글은 게시판 신고 정책(`BoardReportReason`)과 같은 6종, 소모임은 홍보·영업 · 오프라인 위험 · 종목 추천·유료 모집 · 기타(서버 `ReportReasons.GROUP_ALL`).
 * 핫딜 글에서만 보이는 두 사유 `DEAL_FALSE_PRICE`(가격·정보가 사실과 달라요) · `DEAL_HIDDEN_AD`(광고·홍보를 숨겼어요)가 더 있다(hotdeal-contract — 서버 `ReportReasons`, 일반 글·댓글이면 400 `fields.reason`).
 * 조치 사유 코드도 사건 대상의 체계를 따른다.
 */
export type ReportReasonCode = 'SPAM_AD' | 'ABUSE' | 'OBSCENE' | 'ILLEGAL_FILMING' | 'RIGHTS' | 'PROMOTION' | 'OFFLINE_RISK' | 'STOCK_RECOMMENDATION' | 'DEAL_FALSE_PRICE' | 'DEAL_HIDDEN_AD' | 'OTHER';

/** 검색 대상 — 대상 글 제목(기본) · 대상 작성자 · 사건 번호 */
export type ReportSearchField = 'TITLE' | 'AUTHOR' | 'CASE_NO';

/** 정렬 — 마지막 신고 최신(기본) · 오래된 · 신고 많은 순 */
export type ReportSort = 'newest' | 'oldest' | 'most';

/** 대상의 지금 상태 — `INACTIVE` 는 비활성화된 소모임(소모임 대상만) */
export type ReportTargetState = 'PUBLIC' | 'HIDDEN' | 'DELETED' | 'INACTIVE';

/** 숨김 까닭 — 신고 자동 숨김(REPORTED) · 불법촬영물 즉시 숨김(ILLEGAL) · 권리침해 임시조치(RIGHTS) · 운영자 숨김(STAFF) */
export type ReportHiddenReason = 'REPORTED' | 'ILLEGAL' | 'RIGHTS' | 'STAFF';

/**
 * 조치 — 숨김 · 삭제 · 자동 숨김 확정 · 소모임 비활성화(소모임 대상만) · 핫딜 종료(핫딜 글 대상만) · 무시.
 * `DEAL_END`(hotdeal-contract 「신고 사건 조치」): 그 글의 딜을 운영진 종료로 바꾸고(작성자 알림에 처리 글이 사유로 간다) 사건을 처리됨으로 닫는다(처리 글 1~200자, 글은 숨기지 않는다 — 핫딜 글이 아니면 `409 REPORT_ACTION_INVALID`).
 */
export type ReportCaseAction = 'HIDE' | 'DELETE' | 'KEEP_HIDDEN' | 'DEACTIVATE' | 'DEAL_END' | 'SANCTION_ONLY' | 'DISMISS';

/**
 * 처리 대화창의 조치 — `SANCTION_ONLY`(제재만 — 대상은 그대로 두고 작성자만 제재, member-sanction-contract 결정 4)는 요청에서 `action: 'NONE'` + `sanction` 필수다.
 * 사건의 기록된 조치(`ReportCaseItem.action`)는 `SANCTION_ONLY` 로 남는다(community 와 같다).
 */
export type ReportResolveAction = Exclude<ReportCaseAction, 'DISMISS'>;

/** `POST /{id}/resolve` 요청의 `action` — 「제재만」은 `NONE`(직접 `SANCTION_ONLY` 를 보내면 `400 VALIDATION fields.action`) */
export type ReportResolveRequestAction = Exclude<ReportResolveAction, 'SANCTION_ONLY'> | 'NONE';

/** 사건 안 신고 한 건의 상태 — 조치로 닫힘 ACCEPTED, 허위 표시 무시 REJECTED, 그냥 무시 DISMISSED */
export type ReportEntryStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'DISMISSED';

/** 처리 기록 종류 */
export type ReportEventType = 'REPORTED' | 'AUTO_HIDDEN' | 'REVIEW_STARTED' | 'RESOLVED' | 'DISMISSED' | 'REOPENED';

export interface ReportTarget {
    /** 대상 글(댓글이면 그 댓글이 달린 글). 소모임 대상은 null */
    postId: string | null;
    /** 댓글 대상이면 댓글 ID */
    commentId: string | null;
    /** 소모임 대상이면 소모임 ID, 그 밖 null */
    groupId?: string | null;
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
    /** 대상 게시판 — 소모임 대상은 그 소모임의 게시판(빈 값도 견딘다) */
    board: ReportBoardRef | null;
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
    /**
     * 대상이 핫딜 글(DEAL 게시판 글)이면 그 딜의 지금 상태, 그 밖(일반 글·댓글·소모임)은 null(서버·화면 공용 API 메모 1-9).
     * 있으면 조치 사유에 핫딜 사유 2개가 더해지고 [핫딜 종료](`DEAL_END`)를 누를 수 있다(이미 종료면 없음).
     * 칸이 없는(옛) 응답이면 핫딜 사유 신고가 있는 글 사건을 핫딜 글로 본다(`UtilReport.isDealCase`).
     */
    deal?: { view: DealView } | null;
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
    /** 그 소모임을 대상으로 한 사건만(관리자 소모임 상세 서랍의 「신고 보기」에서 넘어옴) */
    groupId?: string;
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

/**
 * 작성자 카드 — `sanction` 은 작성자의 제재 현황(제재 API `GET /api/admin/sanctions/preview` 와 같은 모양, member-sanction-contract 「API 후보」).
 * 비회원·탈퇴(진행 중 포함) 회원이면 null(서버 `ReportAuthorCardView.sanction`). 서랍 작성자 카드·서랍에서 연 처리 대화창이 이 값을 쓴다.
 */
export interface ReportAuthorCard {
    id: string | null;
    name: string;
    /** 등급 키(회원만) */
    grade: string | null;
    joinedAt: string | null;
    /** 이 작성자 대상 사건의 최근 90일 결과 */
    closedCases: { resolved: number; dismissed: number };
    sanction: SanctionPreview | null;
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
    /** 소모임 사건의 대상 소모임(지금 값) — 글·댓글 사건이거나 소모임 행이 없으면 null(옛 서버는 칸이 없다) */
    group?: ReportGroup | null;
    /** 이 처리(`resolve`)에서 준 제재 — 조치 응답에만 있고, 제재가 없거나 그 밖의 응답이면 null(서버 `ReportCaseDetailView.issuedSanction`) */
    issuedSanction?: SanctionLine | null;
}

/** 소모임 사건의 대상 소모임(서버 `ReportGroupView`) — 신고 당시 이름·소개는 `content.snapshot` */
export interface ReportGroup {
    id: string;
    name: string;
    description: string | null;
    /** ACTIVE · INACTIVE · DELETED 등 소모임 상태 그대로 */
    status: string;
    visibility: 'PUBLIC' | 'PRIVATE' | string;
    joinType: 'OPEN' | 'APPROVAL' | string;
    memberCount: number;
    capacity: number;
    offline: boolean;
    /** 투자 고지 게시판에서 추가 동의를 받은 소모임 */
    stockNotice: boolean;
    category: GroupCategoryRef | null;
    /** 지금 모임장(신고 당시 모임장은 사건의 `author`) */
    leader: GroupPerson | null;
    openedAt: string | null;
    deactivatedAt: string | null;
}

/** `POST /{id}/resolve` */
export interface ReportResolveRequest {
    action: ReportResolveRequestAction;
    reasonCode: ReportReasonCode;
    /** 1~500자 */
    note: string;
    /**
     * 작성자 제재 곁들이기(member-sanction-contract 결정 4) — 대상은 사건 작성자(서버가 정한다), 같은 트랜잭션, 제재에 이 사건이 이어진다. `action: 'NONE'` 이면 필수(`400 fields.sanction`). 칸 오류 키는 `sanction.kind`·`sanction.reason`·`sanction.skipStep`·`sanction.confirmNickname`.
     * 제재 오류(`409 SANCTION_*`·`403`·`400 fields.sanction.*`)가 나면 콘텐츠 조치도 일어나지 않는다. 비회원·탈퇴 작성자는 `404 SANCTION_TARGET_NOT_FOUND`.
     */
    sanction?: SanctionWithReport | null;
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
