/**
 * 회원 핫딜(1차) 관리자 API 가 주고받는 모양 — 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」(두 앱이 같은 이름을 쓴다) + 관리자 쪽 덧붙임.
 * 서버 관리자 DTO 는 이 칸 이름·null 규칙 그대로다(서버·화면 공용 API 메모 0절). 계약에 없는 관리자 덧붙임은 칸마다 「(관리자 덧붙임)」으로 적었다.
 * 링크 규칙 · 의심 후보 · 핫딜 설정의 모양은 각 API 옆 `.types.ts` 에 있고, 공통 이름(DealView · DomainRule …)은 여기서 가져간다.
 */

/** 저장 상태 — 진행 · 종료 */
export type DealStatus = 'ACTIVE' | 'ENDED';

/** 읽을 때 계산하는 상태 — FLAGGED = 종료 요청 ≥ 문턱이고 작성자 「아직 진행 중」 전, EXPIRED = `endsAt` 지남(마감 없으면 등록 + `autoExpireDays`, 저장은 ACTIVE) */
export type DealView = 'ACTIVE' | 'FLAGGED' | 'ENDED' | 'EXPIRED';

/** 누가 종료했나(ENDED 일 때) — 작성자 · 운영진(핫딜 담당·게시판 매니저·스텝) · 자동(의심 유예 지남) */
export type DealEndedBy = 'AUTHOR' | 'MANAGER' | 'AUTO';

export type DealStock = 'UNKNOWN' | 'IN_STOCK' | 'LOW' | 'SOLD_OUT';

/** 종료 요청 사유 3개(자유 글 없음) */
export type DealEndReason = 'SOLD_OUT' | 'PRICE_CHANGED' | 'LINK_DEAD';

/** 가격 기준 — 표시가 · 쿠폰가 · 카드 할인가 · 회원가 · 해외 직구(05 §2-6) */
export type DealPriceBasis = 'LISTED' | 'COUPON' | 'CARD' | 'MEMBER' | 'OVERSEAS';

/** 링크 규칙 — 차단 · 제휴 · 없음(null — 대형몰 표시만) */
export type DomainRule = 'BLOCK' | 'AFFILIATE' | null;

/** 제휴 링크 방식(D5) — 개인 제휴 허용(`MEMBER`, 기본 — 초기 운영) · 사이트 제휴(`SITE` — 개인 제휴 받지 않음, 구매 링크를 사이트 제휴 몰 표로 바꾼다) */
export type AffiliateMode = 'MEMBER' | 'SITE';

/** 지금 방식으로 계산한 구매 링크의 제휴 종류 — 개인 제휴(작성자) · 사이트 제휴(로보얌) · 없음 */
export type DealAffiliateKind = 'MEMBER' | 'SITE' | null;

/** 상품 이미지 — 링크 OG 사본(`OG` — 우리 서버에 저장한 600px JPEG, D1) 또는 글쓴이가 올림(`UPLOAD`) */
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

/** 딜 칸 수정 요약(D3) — 가격 수정 횟수 · 링크 호스트가 바뀐 적 있음 · 마지막 수정 */
export interface DealRevisionSummary {
    priceEdits: number;
    linkChanged: boolean;
    lastEditedAt: string | null;
}

/** 딜을 올린 쪽 — 회원 핫딜 · 사업자 핫딜(2차 — 예약된 사업자 신청 건이 시작 시각에 올린 글, 「광고」 + 상호) */
export type DealSource = 'MEMBER' | 'BUSINESS';
export const DEAL_SOURCES: readonly DealSource[] = ['MEMBER', 'BUSINESS'];

/** 사업자 핫딜 글의 사업자 — 상호와 그 글을 올린 신청 건(`/hotdeal-reviews?id=`) */
export interface DealBusinessRef {
    name: string;
    applicationId: string;
}

/** 글의 딜 정보 — 계약 `PostDeal` 그대로(`startsAt` 은 1차에서 뺐다) */
export interface PostDeal {
    source: DealSource;
    /** 쇼핑몰 이름 1~40자 */
    storeName: string;
    productUrl: string;
    /** 링크에서 뗀 호스트(소문자·www. 없음) */
    productHost: string;
    /** 리디렉션을 따라간 최종 호스트(같으면 null — 서버 규칙 2) */
    finalHost: string | null;
    normalPrice: number | null;
    dealPrice: number;
    /** 읽을 때 서버가 계산 — 정상가 > 딜가일 때 내림 정수 % */
    discountRate: number | null;
    /** 0 = 무료, null = 모름 */
    shippingFee: number | null;
    couponNote: string | null;
    priceBasisKind: DealPriceBasis | null;
    /** 해외 직구(`OVERSEAS`)일 때 관부가세 포함 */
    dutyIncluded: boolean;
    /** 가격 기준 메모 0~100자 */
    priceBasis: string | null;
    endsAt: string | null;
    /** 마감이 없을 때 등록 + `autoExpireDays` 로 계산한 기간 종료 시각(설정 0 이면 null) */
    autoExpireAt: string | null;
    stockState: DealStock;
    affiliate: boolean;
    image: DealImage | null;
    status: DealStatus;
    view: DealView;
    endedBy: DealEndedBy | null;
    endedAt: string | null;
    /** 작성자 되돌리기 기한(D2) — AUTHOR 종료이고 아직 안 썼을 때만(종료 + 24시간) */
    reopenUntil: string | null;
    endRequests: DealEndRequests;
    flaggedAt: string | null;
    /** `flaggedAt` + 유예 시간(유예 0 = 자동 종료 안 함이면 null) */
    autoEndAt: string | null;
    /** 작성자가 「아직 진행 중」을 누른 횟수 · 최대(설정 `keepMaxCount`, 0 = 없음) */
    keepCount: number;
    keepMax: number;
    revision: DealRevisionSummary;
    /** 추천 ≥ `hotLikeThreshold` */
    hot: boolean;
    /** 진행 중인 같은 딜(같은 `product_url_key`) 수 — 이 글 제외 */
    sameDealCount: number;
    /**
     * 지금 `affiliateMode` 로 계산한 구매 링크 제휴 종류(읽을 때 계산 — 저장된 `productUrl` 은 바꾸지 않고 변환은 `/api/deals/{postId}/go` 클릭 때만).
     * `MEMBER` 방식: 개인 제휴 글이면 `MEMBER`. `SITE` 방식: 상품·최종 호스트가 켠 사이트 제휴 몰이면 `SITE`, 아니면 null(개인 제휴는 떼고 원래 링크).
     */
    affiliateKind: DealAffiliateKind;
    /** `affiliateKind = SITE` 일 때 그 몰의 제휴 프로그램 이름(예 쿠팡 파트너스), 그 밖 null */
    siteProgramName: string | null;
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
    /**
     * 지금 링크 규칙에서 차단된 호스트(상품·최종 호스트 중 — 진행 중(ACTIVE·FLAGGED) 글만, 그 밖 null). 관리자 덧붙임 —
     * 규칙을 나중에 차단으로 바꾸거나 수정 때 재판정에서 걸려도 글은 그대로 두고(서버 규칙 7) 목록 「차단 도메인 진행 중」(`blockedActive`)으로 모은다.
     */
    blockedHost: string | null;
    deal: PostDeal;
    /** 출처(2차 — 없으면 회원 핫딜). `deal.source` 와 같은 값 */
    source?: DealSource;
    /** 사업자 핫딜 글의 사업자(회원 핫딜이면 null) */
    business?: DealBusinessRef | null;
}

/** 상태 고르기 — 진행 · 의심 · 종료 · 기간 지남 · 전체 */
export type DealViewFilter = DealView | 'ALL';

export const DEAL_VIEW_FILTERS: readonly DealViewFilter[] = ['ACTIVE', 'FLAGGED', 'ENDED', 'EXPIRED', 'ALL'];

/** `GET /api/admin/hotdeals?view&board&q&createdFrom&createdTo&blockedActive&source&page&size` */
export interface AdminDealQuery {
    view: DealViewFilter;
    /** 게시판 ID(빈 글자면 모든 핫딜 게시판) */
    board?: string;
    /** 제목·쇼핑몰·작성자(2자 이상) */
    q?: string;
    createdFrom?: string;
    createdTo?: string;
    /** 차단 도메인 진행 중만(`blockedHost` 가 있는 글) */
    blockedActive?: boolean;
    /** 출처만(2차 — 없으면 전체) */
    source?: DealSource;
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
    /** 차단 도메인 진행 중 건수 — 상태·차단 조건을 뺀 같은 조건의 수(거르기 옆 숫자, 관리자 덧붙임) */
    blockedActiveCount: number;
}

/** 같은 딜 한 줄 — 계약 link-check `sameDeals` 와 같은 칸 + 상태(관리자 덧붙임) */
export interface SameDealRef {
    postId: string;
    title: string;
    dealPrice: number;
    createdAt: string;
    view: DealView;
}

/**
 * 누가 종료했나(관리자 상세 덧붙임 — 게시판 매니저·스텝 종료, 2026-11-01): `OPERATOR` 운영자(관리자 화면·신고 조치) · `BOARD_MANAGER`·`BOARD_STAFF` 그 게시판 매니저·스텝(글 보기 「게시판 관리」) · `AUTHOR` 작성자.
 * `row.deal.endedBy` 는 운영자·매니저·스텝을 `MANAGER` 하나로 묶어 두고, 이 칸이 행위자를 가른다
 */
export type AdminDealEndedByRole = 'OPERATOR' | 'BOARD_MANAGER' | 'BOARD_STAFF' | 'AUTHOR';

/** `GET /api/admin/hotdeals/{postId}` */
export interface AdminDealDetail {
    row: AdminDealRow;
    /** 종료한 운영진·작성자 표시 이름(자동이면 null — 관리자 덧붙임) */
    endedByName: string | null;
    /** 종료한 사람의 자리 — 자동 종료·진행 중이면 null. 없으면(옛 응답) 경위는 `row.deal.endedBy` 묶음 문구로 쓴다 */
    endedByRole?: AdminDealEndedByRole | null;
    /** 운영진이 종료할 때 적은 사유(관리자 덧붙임) */
    endReason: string | null;
    /** 작성자가 마지막으로 「아직 진행 중」을 누른 시각(`end_flag_cleared_at`, 관리자 덧붙임) */
    keptAt: string | null;
    /** 공개 글 주소(사이트 공개 주소를 정하지 않았으면 null — 글 관리와 같은 뜻) */
    publicUrl: string | null;
    /** 진행 중인 같은 딜 — 최근 것부터 5개까지(전체 수는 `row.deal.sameDealCount`) */
    sameDeals: SameDealRef[];
    /** 지금 제휴 링크 방식(관리자 덧붙임 — 서랍의 「구매 링크: 사이트 제휴로 변환」 안내용) */
    affiliateMode: AffiliateMode;
}

/** 수정 이력이 적는 딜 칸 — 가격·링크·몰·마감·재고 등(`post_deal_revisions`, D3) */
export type DealRevisionField = 'dealPrice' | 'normalPrice' | 'shippingFee' | 'productUrl' | 'storeName' | 'endsAt' | 'stockState' | 'couponNote' | 'priceBasisKind' | 'priceBasis' | 'dutyIncluded' | 'affiliate' | 'image';

/** 칸 하나의 전·후 — 값은 그 칸의 저장 모양 그대로(금액 정수 · 시각 ISO · 코드 글자 · 불 · 없으면 null) */
export interface DealRevisionChange {
    field: DealRevisionField;
    before: string | number | boolean | null;
    after: string | number | boolean | null;
}

/** 수정 이력 한 줄(`GET /api/admin/hotdeals/{postId}/revisions` — 최근 것부터, 6개월 보존) */
export interface DealRevision {
    id: string;
    at: string;
    /** 누가 — 작성자 · 운영진(핫딜 담당·게시판 매니저·스텝) */
    actor: { kind: 'AUTHOR' | 'MANAGER'; name: string };
    changes: DealRevisionChange[];
    /** 링크 호스트가 바뀐 줄(운영자 알림이 간다) */
    hostChanged: boolean;
}

/** `GET /api/admin/hotdeals/{postId}/revisions` — 계약은 배열만 적었다. 감싼 모양·보존 개월 수는 관리자 덧붙임(2026-11-01 확정) */
export interface DealRevisionListView {
    items: DealRevision[];
    /** 보존 기간(개월) — 6 */
    retentionMonths: number;
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
