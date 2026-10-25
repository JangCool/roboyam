/**
 * 인기글 설정·인기글 관리 API 가 주고받는 모양 — 서버 `server/admin` 의 `popular.dto.*`·`post.dto.response.AdminPostRefView` 와 같다.
 * 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */

/** 글의 지금 상태 — 서버 `AdminPostRow.stateAt`(앞의 것이 이긴다: 지움 → 숨김 → 19금 → 꺼진 게시판 → 공개) */
export type AdminPostState = 'PUBLIC' | 'HIDDEN' | 'DELETED' | 'ADULT' | 'BOARD_OFF';

/** 관리자 화면의 글 한 줄 — 인기글 순위·뺀 글·하이라이트·글 찾기·후보가 같이 쓴다(서버 `AdminPostRefView`) */
export interface AdminPostRef {
    id: string;
    title: string;
    boardId: string;
    boardName: string;
    boardNameEn: string | null;
    prefixName: string | null;
    authorKind: 'MEMBER' | 'GUEST';
    authorId: string | null;
    authorName: string;
    likeCount: number;
    commentCount: number;
    viewCount: number;
    createdAt: string;
    state: AdminPostState;
}

/** 운영자 이름표 — 탈퇴·지워진 운영자는 `name` 이 null */
export interface PopularOperator {
    id: string;
    name: string | null;
}

/** 이번 주의 시작 요일 */
export type PopularWeekStart = 'MONDAY' | 'SUNDAY';

/** 채운 줄의 출처 — 어제 글로 채움(실시간 탭) · 지난주 글로 채움(이번 주 탭). 그 기간 자기 글이면 null */
export type PopularFill = 'YESTERDAY' | 'LAST_WEEK';

// ── 인기글 설정 `GET·PUT /api/admin/popular-settings` ──

/** 설정 11칸 — PUT 요청도 이 모양 그대로(숫자는 JSON 숫자) */
export interface PopularSettingValues {
    likeWeight: number;
    commentWeight: number;
    viewWeight: number;
    gravity: number;
    ageOffsetHours: number;
    minLikes: number;
    minComments: number;
    minViews: number;
    excludeStaffBoards: boolean;
    maxPerAuthor: number;
    weekStart: PopularWeekStart;
}

/** 숫자 칸 이름(9칸) — `limits` 의 키 */
export type PopularNumberField = 'likeWeight' | 'commentWeight' | 'viewWeight' | 'gravity' | 'ageOffsetHours' | 'minLikes' | 'minComments' | 'minViews' | 'maxPerAuthor';

/** 설정 칸 이름(11칸) — 서버 오류 `fields.<칸>` 의 키 */
export type PopularSettingField = keyof PopularSettingValues;

/** 숫자 칸 하나의 범위와 소수 자리 수(0 = 정수) */
export interface PopularLimit {
    min: number;
    max: number;
    decimals: number;
}

/** 지금 실시간 순위 상위 10개 중 한 줄 — 점수는 계산 시점, 추천·댓글·조회는 지금 값 */
export interface PopularPreviewItem {
    rank: number;
    postId: string;
    title: string;
    boardName: string;
    boardNameEn: string | null;
    score: number;
    likeCount: number;
    commentCount: number;
    viewCount: number;
    createdAt: string;
    fill: PopularFill | null;
}

export interface PopularSettingsView {
    settings: PopularSettingValues;
    defaults: PopularSettingValues;
    limits: Record<PopularNumberField, PopularLimit>;
    weekStarts: PopularWeekStart[];
    current: PopularPreviewItem[];
    computedAt: string | null;
    /** 끝나지 않은 다시 계산 요청이 있다 — 「다시 계산 중」 표시, 잠시 뒤 다시 읽는다 */
    recomputePending: boolean;
    /** 이번 저장이 다시 계산을 요청했다(바뀐 칸이 있었다) — GET 은 늘 false */
    recomputeRequested: boolean;
    updatedAt: string | null;
    updatedBy: PopularOperator | null;
}

// ── 인기글 관리 `/api/admin/popular-posts` ──

export type PopularPeriod = 'REALTIME' | 'YESTERDAY' | 'WEEK';

/** 빼기 사유 코드 — 순서는 서버 `reasons` 가 정한다 */
export type PopularExcludeReason = 'INAPPROPRIATE' | 'PROMOTION' | 'MANIPULATION' | 'DISPUTE' | 'OTHER';

/** 순위 한 줄 — 계산 뒤에 뺀 글은 `excluded: true`·`rank: null` 로 자리에 남는다(다시 계산되면 사라진다) */
export interface PopularRankItem {
    rank: number | null;
    snapshotRank: number;
    score: number;
    fill: PopularFill | null;
    excluded: boolean;
    post: AdminPostRef;
}

/** 뺀 글 한 줄 — 기간과 무관. 글이 지워졌거나 볼 수 없으면 `available false`(글 행이 아예 없으면 `post null`) */
export interface PopularExcluded {
    postId: string;
    available: boolean;
    post: AdminPostRef | null;
    reason: PopularExcludeReason;
    memo: string | null;
    excludedBy: PopularOperator | null;
    excludedAt: string;
}

export interface PopularRankingView {
    period: PopularPeriod;
    /** 기간 범위 `[from, to)` — 한국 달력·설정의 주 시작 요일 */
    from: string;
    to: string;
    weekStart: PopularWeekStart;
    computedAt: string | null;
    recomputePending: boolean;
    items: PopularRankItem[];
    /** 최근에 뺀 것부터 200개 */
    excluded: PopularExcluded[];
    reasons: PopularExcludeReason[];
}

/** `POST /api/admin/popular-posts/exclusions` — `OTHER` 는 메모 필수, 메모 100자까지 */
export interface PopularExcludeRequest {
    postId: string;
    reason: PopularExcludeReason;
    memo: string | null;
}
