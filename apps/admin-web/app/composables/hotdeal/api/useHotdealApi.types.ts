/**
 * 회원 핫딜(1차) 관리자 API 가 주고받는 모양 — 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」(두 앱이 같은 이름을 쓴다) + 관리자 쪽 덧붙임.
 * **서버에 아직 없다(목업)** — 이름·칸은 서버 단계에서 확정한다. 계약에 없는 관리자 덧붙임은 칸마다 「(관리자 덧붙임)」으로 적었다.
 * 링크 규칙 · 의심 후보 · 핫딜 설정의 모양은 각 API 옆 `.types.ts` 에 있고, 공통 이름(DealView · DomainRule …)은 여기서 가져간다.
 */

/** 저장 상태 — 진행 · 종료 */
export type DealStatus = 'ACTIVE' | 'ENDED';

/** 읽을 때 계산하는 상태 — FLAGGED = 종료 요청 ≥ 문턱이고 작성자 「아직 진행 중」 전, EXPIRED = `endsAt` 지남(저장은 ACTIVE) */
export type DealView = 'ACTIVE' | 'FLAGGED' | 'ENDED' | 'EXPIRED';

/** 누가 종료했나(ENDED 일 때) — 작성자 · 운영진(핫딜 담당·게시판 매니저·스텝) · 자동(의심 유예 지남) */
export type DealEndedBy = 'AUTHOR' | 'MANAGER' | 'AUTO';

export type DealStock = 'UNKNOWN' | 'IN_STOCK' | 'LOW' | 'SOLD_OUT';

/** 종료 요청 사유 3개(자유 글 없음) */
export type DealEndReason = 'SOLD_OUT' | 'PRICE_CHANGED' | 'LINK_DEAD';

/** 링크 규칙 — 차단 · 제휴 · 없음(null — 대형몰 표시만) */
export type DomainRule = 'BLOCK' | 'AFFILIATE' | null;

/** 상품 이미지 — 링크 OG 자동(`OG`) 또는 글쓴이가 올림(`UPLOAD`) */
export interface DealImage {
    url: string;
    source: 'OG' | 'UPLOAD';
}

/** 종료 요청 수 — 요청자 신원은 누구에게도 보이지 않는다(개수·사유별 개수만). `mine` 은 공개 쪽 칸이라 관리자 응답은 늘 null */
export interface DealEndRequests {
    total: number;
    bySoldOut: number;
    byPriceChanged: number;
    byLinkDead: number;
    mine: DealEndReason | null;
}

/** 글의 딜 정보 — 계약 `PostDeal` 그대로 */
export interface PostDeal {
    source: 'MEMBER';
    /** 쇼핑몰 이름 1~40자 */
    storeName: string;
    productUrl: string;
    /** 링크에서 뗀 호스트(소문자·www. 없음) */
    productHost: string;
    normalPrice: number | null;
    dealPrice: number;
    /** 읽을 때 서버가 계산 — 정상가 > 딜가일 때 정수 % */
    discountRate: number | null;
    /** 0 = 무료, null = 모름 */
    shippingFee: number | null;
    couponNote: string | null;
    priceBasis: string | null;
    startsAt: string | null;
    endsAt: string | null;
    stockState: DealStock;
    affiliate: boolean;
    image: DealImage | null;
    status: DealStatus;
    view: DealView;
    endedBy: DealEndedBy | null;
    endedAt: string | null;
    endRequests: DealEndRequests;
    flaggedAt: string | null;
    /** `flaggedAt` + 유예 시간(유예 0 = 자동 종료 안 함이면 null) */
    autoEndAt: string | null;
    /** 진행 중인 같은 딜(같은 `product_url_key`) 수 — 이 글 제외 */
    sameDealCount: number;
}

export interface HotdealBoardRef {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
}

export interface HotdealPrefixRef {
    id: string;
    name: string;
}

/** 작성자 — 탈퇴하면 `withdrawn`(이름은 서버가 정한 표시 글자) */
export interface HotdealAuthor {
    id: string | null;
    nickname: string;
    withdrawn: boolean;
}

/** 핫딜 글 목록 한 줄(`GET /api/admin/hotdeals`) */
export interface AdminDealRow {
    postId: string;
    title: string;
    board: HotdealBoardRef;
    prefix: HotdealPrefixRef | null;
    author: HotdealAuthor;
    createdAt: string;
    /** 처리 대기 신고 수(글 관리 목록과 같은 뜻) */
    openReports: number;
    deal: PostDeal;
}

/** 상태 고르기 — 진행 · 의심 · 종료 · 기간 지남 · 전체 */
export type DealViewFilter = DealView | 'ALL';

export const DEAL_VIEW_FILTERS: readonly DealViewFilter[] = ['ACTIVE', 'FLAGGED', 'ENDED', 'EXPIRED', 'ALL'];

/** `GET /api/admin/hotdeals?view&board&q&createdFrom&createdTo&page&size` */
export interface AdminDealQuery {
    view: DealViewFilter;
    /** 게시판 ID(빈 글자면 모든 핫딜 게시판) */
    board?: string;
    /** 제목·쇼핑몰·작성자(2자 이상) */
    q?: string;
    createdFrom?: string;
    createdTo?: string;
    /** 0부터 */
    page: number;
    size: number;
}

export interface AdminDealPage {
    total: number;
    page: number;
    size: number;
    items: AdminDealRow[];
    /** 상태별 건수 — 상태 조건만 뺀 같은 조건의 수(관리자 덧붙임) */
    counts: Record<DealViewFilter, number>;
}

/** 같은 딜 한 줄 — 계약 link-check `sameDeals` 와 같은 칸 + 상태(관리자 덧붙임) */
export interface SameDealRef {
    postId: string;
    title: string;
    dealPrice: number;
    createdAt: string;
    view: DealView;
}

/** `GET /api/admin/hotdeals/{postId}` */
export interface AdminDealDetail {
    row: AdminDealRow;
    /** 종료한 운영진·작성자 표시 이름(자동이면 null — 관리자 덧붙임) */
    endedByName: string | null;
    /** 운영진이 종료할 때 적은 사유(관리자 덧붙임) */
    endReason: string | null;
    /** 작성자가 「아직 진행 중」을 누른 시각(`end_flag_cleared_at`, 관리자 덧붙임) */
    keptAt: string | null;
    /** 공개 글 주소(사이트 공개 주소를 정하지 않았으면 null — 글 관리와 같은 뜻) */
    publicUrl: string | null;
    /** 진행 중인 같은 딜 — 최근 것부터 5개까지(전체 수는 `row.deal.sameDealCount`) */
    sameDeals: SameDealRef[];
}

/** `POST /api/admin/hotdeals/{postId}/end` · `/reopen` — 사유는 선택(0~200자, 감사·작성자 알림에 쓴다) */
export interface DealActionRequest {
    reason?: string;
}

/** 핫딜 게시판 고르기(`GET /api/admin/hotdeals/boards` — 관리자 덧붙임): `boards.kind = DEAL` 인 게시판과 말머리 */
export interface HotdealBoardOption extends HotdealBoardRef {
    active: boolean;
    prefixes: HotdealPrefixRef[];
}

export interface HotdealBoardOptionsView {
    boards: HotdealBoardOption[];
}
