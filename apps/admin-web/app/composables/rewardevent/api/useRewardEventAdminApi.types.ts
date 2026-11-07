/**
 * 보상 이벤트(관리자) — 서버와 주고받는 모양. 서버 정본: shared `com.community.rewardevent.dto`(요청 `dto/request/*`, 응답 `dto/response/*View`, 공용 `RewardTierDto`·`RewardLineDto` 등).
 * 번호(이벤트·등수·보상 줄·받는 사람·회원·품목·팩)는 모두 서버 숫자 문자열, 시각은 ISO 순간 글자.
 * 「사이트 팝업(이벤트·프로모션 알림 창)」과 다른 업무다 — 이 업무는 늘 「보상 이벤트」(`RewardEvent`)라고 부른다.
 *
 * 상태 흐름: 준비(DRAFT) → 시작 → 진행 중(ACTIVE) → 마감(CLOSED — 기간 끝·인원 다 참·운영자 마감) → 지급 끝(DONE). 지급 전이면 취소(CANCELED).
 * 방식: 선착순 자동(FIRST_COME — 그 사건이 일어난 순간 서버가 자동 지급) · 당첨자 선정(SELECTION — 직접 입력·댓글 추첨) · 일괄 지급(BULK — 전체 또는 조건).
 * 보상 줄: 무상 포인트 · 포인트 상점 아이템 · 로보티콘 팩(운영·무료 팩만) · 직접 전달(사이트가 주지 않고 운영자가 따로 보낸 뒤 「전달 완료」 표시).
 */
import type { PointShopItemKind, PointShopUsageType } from '~/composables/pointshop/api/usePointShopAdminApi.types';

// ── 열거 ─────────────────────────────────────────────────

export type RewardEventStatus = 'DRAFT' | 'ACTIVE' | 'CLOSED' | 'DONE' | 'CANCELED';
/** 목록 탭 차례 */
export const REWARD_EVENT_STATUSES: readonly RewardEventStatus[] = ['DRAFT', 'ACTIVE', 'CLOSED', 'DONE', 'CANCELED'];

export type RewardEventMode = 'FIRST_COME' | 'SELECTION' | 'BULK';
export const REWARD_EVENT_MODES: readonly RewardEventMode[] = ['FIRST_COME', 'SELECTION', 'BULK'];

/** 선착순 기준 사건 — 본인인증 완료(권장, 1인 1계정) · 이메일 인증(가입 완료 — 부계정으로 여러 번 받을 수 있다) */
export type RewardEventTrigger = 'IDENTITY_VERIFIED' | 'EMAIL_VERIFIED';
export const REWARD_EVENT_TRIGGERS: readonly RewardEventTrigger[] = ['IDENTITY_VERIFIED', 'EMAIL_VERIFIED'];

/** 당첨자 후보를 모으는 방법(여럿 같이) — 직접 입력·검색 · 글 댓글 작성자 중 추첨 */
export type RewardEventCandidateSource = 'MANUAL' | 'COMMENT_DRAW';
export const REWARD_EVENT_CANDIDATE_SOURCES: readonly RewardEventCandidateSource[] = ['MANUAL', 'COMMENT_DRAW'];

/** 일괄 지급 대상 — 전체 정상 회원 · 조건 */
export type RewardEventBulkScope = 'ALL' | 'CONDITION';

export type RewardKind = 'POINT' | 'SHOP_ITEM' | 'EMOTICON_PACK' | 'MANUAL';
export const REWARD_KINDS: readonly RewardKind[] = ['POINT', 'SHOP_ITEM', 'EMOTICON_PACK', 'MANUAL'];

/** 받는 사람 한 명의 지급 상태 — 대기 · 완료(건너뜀 줄 포함) · 일부 실패 · 실패 · 건너뜀(모든 줄) · 회수됨 */
export type RewardRecipientStatus = 'PENDING' | 'GRANTED' | 'PARTIAL' | 'FAILED' | 'SKIPPED' | 'REVOKED';
export const REWARD_RECIPIENT_STATUSES: readonly RewardRecipientStatus[] = ['PENDING', 'GRANTED', 'PARTIAL', 'FAILED', 'SKIPPED', 'REVOKED'];

/** 어떻게 대상이 되었나 — 선착순 · 직접 · 댓글 추첨 · 조건 · 전체 */
export type RewardRecipientMethod = 'FIRST_COME' | 'MANUAL' | 'COMMENT_DRAW' | 'CONDITION' | 'ALL';

/** 보상 줄 하나의 결과 — 대기 · 성공 · 실패 · 건너뜀 · 회수됨 · (직접 전달) 전달 대기 · 전달 완료 */
export type RewardLineResultStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'REVOKED' | 'DELIVERY_PENDING' | 'DELIVERED';

/**
 * 줄 결과 사유 코드 — 화면이 `rewardEvent.reason.<코드>` 로 문구를 고른다.
 * 실패: 포인트 동결 회원 · 탈퇴 회원 · 정지 회원 · 품목 판매 중단·삭제 · 서버 오류 / 건너뜀: 이미 가진 영구 아이템 · 이미 받은 팩
 * 회수: 이미 쓴 아이템(회수 못 함) · 포인트를 0P 까지만 회수 · 직접 전달은 기록만
 */
export type RewardLineReason =
    | 'POINT_FROZEN'
    | 'WITHDRAWN'
    | 'SUSPENDED'
    | 'ITEM_UNAVAILABLE'
    | 'SERVER_ERROR'
    | 'ALREADY_OWNED'
    | 'PACK_ALREADY_OWNED'
    | 'ITEM_USED'
    | 'POINT_PARTIAL'
    | 'MANUAL_RECORD_ONLY';

/** 지급 실행 한 번의 종류 — 운영자 지급 · 실패만 다시 · 회수 · 선착순 자동(날마다 묶음) */
export type RewardRunKind = 'GRANT' | 'RETRY' | 'REVOKE' | 'AUTO';
export type RewardRunStatus = 'RUNNING' | 'DONE';

export type RewardEventAuditAction =
    | 'CREATE'
    | 'UPDATE'
    | 'START'
    | 'CLOSE'
    | 'AUTO_CLOSE'
    | 'DRAW'
    | 'RECIPIENT_ADD'
    | 'RECIPIENT_REMOVE'
    | 'GRANT'
    | 'RETRY'
    | 'REVOKE'
    | 'DELIVERED'
    | 'CANCEL';

/**
 * 고치기 범위 — 서버가 상태로 정해 준다(서버 `RewardEditScope`).
 *  · FULL(준비): 모든 칸
 *  · ACTIVE(진행 중): 이름·메모·안내 글·끝 시각·인원(선착순 인원 / 등수 인원 — 이미 받은 수 아래로는 못 줄인다)
 *  · CLOSED(마감): 메모·안내 글·등수 인원(지급 전 당첨자를 더 뽑을 수 있게)
 *  · MEMO(지급 끝·취소): 메모만
 */
export type RewardEventEditScope = 'FULL' | 'ACTIVE' | 'CLOSED' | 'MEMO';

// ── 설정 ─────────────────────────────────────────────────

export interface RewardEventFirstComeConfig {
    trigger: RewardEventTrigger;
    /** 인원 1~100,000 — 차면 자동 마감 */
    limit: number;
}

export interface RewardEventCommentDrawConfig {
    /** 글 주소 또는 번호(운영자가 넣은 그대로) */
    postRef: string;
    /** 서버가 찾은 글 — 미리 보기 뒤 채워진다 */
    postId: string | null;
    postTitle: string | null;
    /** 댓글 기간(ISO 순간, 양 끝 포함) — 서버는 비우면 이벤트 기간으로 채운다(화면은 늘 채워 보낸다), 응답은 늘 채워져 온다 */
    from: string;
    to: string;
    /** 제외 — 글 작성자(기본 켬). 운영자 회원 계정은 공통 `excludeOperators`, 정지·탈퇴는 늘 제외 */
    excludeAuthor: boolean;
}

export interface RewardEventSelectionConfig {
    sources: RewardEventCandidateSource[];
    /** `COMMENT_DRAW` 를 고르면 있다 */
    commentDraw: RewardEventCommentDrawConfig | null;
}

export interface RewardEventBulkConfig {
    scope: RewardEventBulkScope;
    /** 이 등급 서열 이상(없으면 조건 없음) */
    minGradeLevel: number | null;
    /** 가입일 범위(달력 날짜 `YYYY-MM-DD`, 표시 시간대) */
    joinedFrom: string | null;
    joinedTo: string | null;
    /** 본인인증 한 회원만 */
    identityVerifiedOnly: boolean;
    /** 최근 N일 안에 로그인(1~365, 없으면 조건 없음) */
    activeWithinDays: number | null;
}

/** 보상 줄 — 종류마다 쓰는 칸이 다르다(안 쓰는 칸은 null) */
export interface RewardLine {
    /** 서버 번호(숫자 문자열). 준비 상태에서 고치면 모든 줄이 새 번호로 바뀐다(요청의 값은 임시여도 된다) */
    id: string;
    kind: RewardKind;
    /** POINT — 1~100,000 */
    points: number | null;
    /** SHOP_ITEM — 품목 번호와 고를 때의 이름·종류·사용 방식·기간 사본 */
    itemId: string | null;
    itemName: string | null;
    itemKind: PointShopItemKind | null;
    itemUsage: PointShopUsageType | null;
    itemDurationDays: number | null;
    /** SHOP_ITEM 소모형 수량 1~10(영구·기간제는 1) */
    quantity: number | null;
    /** EMOTICON_PACK — 운영·무료 팩만 */
    packId: string | null;
    packName: string | null;
    /** MANUAL — 보상 이름(예 「커피 기프티콘」)·전달 방법 메모(개인정보 금지) */
    manualName: string | null;
    manualMethod: string | null;
}

/** 등수(보상 묶음) — 선착순·일괄은 하나, 당첨자 선정은 여럿 */
export interface RewardTier {
    /** 서버 번호(숫자 문자열). 새 등수는 화면 임시 번호(`new-tier-…`) — 준비 상태 고치기에서 서버 번호면 그 등수를 남긴다 */
    id: string;
    /** 등수 이름(예 「1등」) 1~20자 */
    name: string;
    /** 인원 — 선착순은 서버가 `firstCome.limit` 로, 일괄은 0 으로 맞춘다(대상은 지급 때 정해진다) */
    quota: number;
    rewards: RewardLine[];
}

/** 만들기·고치기 본문. 고치기는 `editScope` 밖의 칸을 바꾸면 `409 REWARD_EVENT_LOCKED_FIELD` */
export interface RewardEventSaveRequest {
    /** 1~60자 — 회원 알림·포인트 내역에 보인다 */
    name: string;
    /** 운영 메모(내부용) 0~500자 */
    memo: string;
    startAt: string;
    endAt: string;
    /** 안내 글 주소(공지 글 링크) — 당첨 발표는 공지 글로 한다 */
    noticeUrl: string;
    mode: RewardEventMode;
    firstCome: RewardEventFirstComeConfig | null;
    selection: RewardEventSelectionConfig | null;
    bulk: RewardEventBulkConfig | null;
    /** 운영자 회원 계정 제외(기본 켬). 정지·탈퇴는 늘 제외, 같은 회원은 이벤트당 한 번 */
    excludeOperators: boolean;
    tiers: RewardTier[];
}

// ── 조회 ─────────────────────────────────────────────────

/** 숫자 — 사람 단위(받는 사람 상태) + 줄 단위(건너뛴 보상 줄) */
export interface RewardEventStats {
    /** 대상 수(선착순은 지금까지 받은 사람, 일괄은 지급 때 정해진 대상) */
    targets: number;
    pending: number;
    granted: number;
    partial: number;
    failed: number;
    skipped: number;
    revoked: number;
    /** 건너뛴 보상 줄(이미 가진 아이템 등) — 사람은 완료로 센다 */
    skippedLines: number;
    /** 지금까지 지급한 포인트 합(회수분 뺌) · 아이템(상점 아이템 + 팩) 수 */
    grantedPoints: number;
    grantedItems: number;
    /** 직접 전달 — 대기 · 완료 */
    manualPending: number;
    manualDelivered: number;
}

/** 서버가 상태·권한 밖의 조건으로 정한 동작(권한은 화면이 따로 본다) */
export interface RewardEventActions {
    editScope: RewardEventEditScope;
    canStart: boolean;
    canClose: boolean;
    canDraw: boolean;
    canGrant: boolean;
    canRetry: boolean;
    canCancel: boolean;
    /** 당첨자 추가 — 직접 입력이 후보 방법일 때 */
    canAddRecipients: boolean;
    /** 취소가 막힌 이유 — 지급이 하나라도 있으면 HAS_GRANTS(취소 대신 회수) */
    cancelBlockedReason: 'HAS_GRANTS' | null;
    /** 지급 실행이 막힌 이유(상태가 맞는데 못 할 때) — 대기 대상 없음 */
    grantBlockedReason: 'NO_PENDING' | 'NOT_CLOSED' | 'RUNNING' | null;
}

export interface RewardEventOperatorRef {
    id: string;
    /** 지금 운영자 이름 — 지운 운영자면 null */
    name: string | null;
}

export interface RewardEventRow {
    id: string;
    /** 번호(사람이 읽는 차례) */
    no: number;
    name: string;
    mode: RewardEventMode;
    status: RewardEventStatus;
    startAt: string;
    endAt: string;
    /** 보상 요약용 — 등수와 보상 줄(목록은 화면이 「1,000P + 한정 테두리」로 줄인다) */
    tiers: RewardTier[];
    /** 선착순 인원(목록의 「412 / 1,000」) */
    firstComeLimit: number | null;
    stats: RewardEventStats;
    createdBy: RewardEventOperatorRef;
    createdAt: string;
}

export interface RewardEventPage {
    items: RewardEventRow[];
    total: number;
    /** 탭 건수 — 검색어·방식을 적용한 상태별 건수 */
    counts: Record<RewardEventStatus, number>;
}

export interface RewardEventQuery {
    status?: RewardEventStatus;
    mode?: RewardEventMode;
    q?: string;
    page: number;
    size: number;
}

export interface RewardEventDetail extends RewardEventSaveRequest {
    id: string;
    no: number;
    status: RewardEventStatus;
    stats: RewardEventStats;
    /** 등수별 지금 받는 사람(당첨자) 수 — 추첨·추가 대화상자의 남은 자리 */
    recipientsByTier: Record<string, number>;
    actions: RewardEventActions;
    /** 지금 진행 중인 지급 실행(일괄 대량 — 진행률). 없으면 null */
    runningRun: RewardRun | null;
    cancelReason: string | null;
    canceledAt: string | null;
    createdBy: RewardEventOperatorRef;
    createdAt: string;
    updatedAt: string;
}

// ── 받는 사람 ─────────────────────────────────────────────

export interface RewardMemberRef {
    /** 회원 번호(숫자 문자열) */
    id: string;
    /** 회원 줄을 찾지 못하면(지운 회원) null — 그때 `withdrawn` 은 true */
    nickname: string | null;
    /** 이메일 — `member.pii` 가 없으면 서버가 가려 준다 */
    email: string | null;
    withdrawn: boolean;
}

export interface RewardLineResult {
    lineId: string;
    kind: RewardKind;
    status: RewardLineResultStatus;
    reason: RewardLineReason | null;
    /** 처리 시각(지급·실패·회수) */
    at: string | null;
    /** 회수한 포인트(0P 바닥으로 덜 회수했을 수 있다) */
    revokedPoints: number | null;
    /** 직접 전달 완료 기록 */
    delivered: { at: string; operator: RewardEventOperatorRef | null; memo: string } | null;
}

export interface RewardRecipient {
    id: string;
    member: RewardMemberRef;
    tierId: string;
    tierName: string;
    method: RewardRecipientMethod;
    selectedAt: string;
    /** 선정 운영자 — 선착순·일괄 자동은 null(「자동」) */
    selectedBy: RewardEventOperatorRef | null;
    /** 댓글 추첨으로 뽑혔으면 그 추첨 */
    drawId: string | null;
    status: RewardRecipientStatus;
    results: RewardLineResult[];
    /** 회수 사유(회수됨) */
    revokeReason: string | null;
}

export interface RewardRecipientQuery {
    status?: RewardRecipientStatus;
    tierId?: string;
    q?: string;
    page: number;
    size: number;
}

export interface RewardRecipientPage {
    items: RewardRecipient[];
    total: number;
}

/** `POST …/recipients/resolve` — 붙여 넣은 줄(회원 번호·닉네임·이메일) → 찾은 회원 / 못 찾은 줄 / 이미 이 이벤트에 있는 회원 / 제외 대상 */
export interface RewardRecipientResolveResult {
    found: RewardMemberRef[];
    notFound: string[];
    alreadyIn: RewardMemberRef[];
    /** 정지·탈퇴·운영자 회원 계정(제외 옵션) — 넣을 수 없다 */
    excluded: { member: RewardMemberRef; reason: 'SUSPENDED' | 'WITHDRAWN' | 'OPERATOR' }[];
}

export interface RewardRecipientAddRequest {
    tierId: string;
    /** 회원 번호(숫자 문자열) — 한 번에 500명까지(`400 fields.memberIds`) */
    memberIds: string[];
}

// ── 추첨 ─────────────────────────────────────────────────

/** 댓글 후보 미리 보기 — 본문은 댓글 추첨 설정 + 운영자 제외 */
export interface RewardCommentPreviewRequest {
    postRef: string;
    from: string;
    to: string;
    excludeAuthor: boolean;
    excludeOperators: boolean;
}

export interface RewardCommentPreview {
    postId: string;
    postTitle: string;
    authorNickname: string;
    /** 기간 안 댓글 수 · 1인 1회로 센 후보 수 */
    commentCount: number;
    candidateCount: number;
    excluded: { author: number; operators: number; suspended: number; withdrawn: number };
}

export interface RewardDrawRequest {
    tierId: string;
    count: number;
}

export interface RewardDraw {
    id: string;
    at: string;
    operator: RewardEventOperatorRef;
    postId: string;
    /** 지운 글이면 null */
    postTitle: string | null;
    tierId: string;
    tierName: string;
    /** 추첨 때 후보 수(이미 당첨된 회원은 뺀 수) · 뽑은 수 */
    candidateCount: number;
    picked: number;
    /** 추첨 번호(seed, 64비트 — 16자리 16진수 글자) — 같은 후보 목록(회원 번호 오름차순)과 번호면 같은 결과가 나온다 */
    seed: string;
    members: RewardMemberRef[];
}

// ── 일괄 대상 미리 보기 ───────────────────────────────────

export interface RewardTargetPreviewRequest {
    bulk: RewardEventBulkConfig;
    excludeOperators: boolean;
}

export interface RewardTargetPreview {
    count: number;
    /** 늘 빼는 수(정지·탈퇴) · 운영자 회원 계정 */
    excludedSuspended: number;
    excludedOperators: number;
}

// ── 지급 ─────────────────────────────────────────────────

export interface RewardGrantPreview {
    /** 이번에 지급할 대상(대기 중인 받는 사람 — 일괄은 조건으로 지금 미리 센 수) */
    targetCount: number;
    /** 1인당 가장 큰 포인트(등수마다 다르면 가장 큰 것) — 고액 문턱 판정 */
    maxPointsPerPerson: number;
    totalPoints: number;
    /** 상점 아이템·팩 수 */
    itemCount: number;
    /** 직접 전달 수(사이트는 주지 않는다) */
    manualCount: number;
    /** 등수별 내역 */
    tiers: { tierId: string; tierName: string; count: number; points: number; items: number; manual: number }[];
    /** 고액 문턱(포인트 정책 `adjustLargeThreshold`) */
    largeThreshold: number;
    /** 서버 판정 — 1인 포인트 합이 문턱을 넘거나, 일괄 방식에 포인트 보상이 있다(대상 수와 상관없이). true 면 `point.adjust.large` 필요 */
    largeRequired: boolean;
}

export interface RewardGrantRequest {
    /** 멱등 키(이벤트마다 유일, 1~100자) — 같은 키는 처음 실행을 돌려준다 */
    idempotencyKey: string;
}

export interface RewardRevokeRequest {
    /** 받는 사람 번호 — 한 번에 500명까지(`400 fields.recipientIds`) */
    recipientIds: string[];
    /** 사유 5~200자 */
    reason: string;
}

export interface RewardDeliveredRequest {
    lineId: string;
    /** 메모 0~200자(개인정보 금지) */
    memo: string;
}

export interface RewardRun {
    id: string;
    at: string;
    /** 실행 운영자 — 선착순 자동은 null(「자동」) */
    operator: RewardEventOperatorRef | null;
    kind: RewardRunKind;
    status: RewardRunStatus;
    /** 선착순 자동은 날마다 묶는다 — 그 날(`YYYY-MM-DD`, 표시 시간대) */
    day: string | null;
    /** 대상 수 — 일괄 진행 중에는 처음엔 미리 센 수, 대상 만들기가 끝나면 실제 수 */
    targetCount: number;
    success: number;
    failed: number;
    skipped: number;
    /** 지급(회수는 회수한) 포인트 합 · 아이템 수 */
    points: number;
    items: number;
    /** 진행률(대량 나눠 처리 중) — total = max(대상, 처리한 수), 끝났으면 done = total */
    progress: { done: number; total: number };
    /** 회수 사유 */
    reason: string | null;
}

export interface RewardEventAudit {
    id: string;
    at: string;
    /** 자동(선착순 자동 마감 등)은 null */
    operator: RewardEventOperatorRef | null;
    action: RewardEventAuditAction;
    /** 서버 번들(`rewardEvent.audit.*`, 요청 언어)이 적어 주는 한 줄 — 지급·재시도는 시작·끝 두 줄로 온다 */
    detail: string;
}

// ── 보상 고르기용 ─────────────────────────────────────────

/** 포인트 상점 품목(보상으로 고를 수 있는 것) — 기존 `GET /api/admin/point-shop/items` 의 줄에서 필요한 칸만 */
export interface RewardShopItemOption {
    id: string;
    name: string;
    kind: PointShopItemKind;
    usage: PointShopUsageType;
    durationDays: number | null;
    /** 판매 중단 품목도 보상으로 줄 수 있다 — 삭제 품목·아바타는 안 온다 */
    onSale: boolean;
}

/** 로보티콘 팩 — 운영 팩·무료 팩만 고를 수 있다. 작가 유료 팩은 `selectable: false`(정산 때문) */
export interface RewardPackOption {
    id: string;
    name: string;
    source: 'OFFICIAL' | 'CREATOR';
    /** 0 = 무료 */
    price: number;
    selectable: boolean;
}

export interface RewardGradeOption {
    level: number;
    label: string;
}

export interface RewardEventCatalog {
    items: RewardShopItemOption[];
    packs: RewardPackOption[];
    grades: RewardGradeOption[];
    /** 고액 문턱(포인트 정책) */
    largeThreshold: number;
}
