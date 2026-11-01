/**
 * 회원 핫딜(1차) API 가 주고받는 모양 — 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」(두 앱이 같은 이름을 쓴다).
 * 글 줄·글 상세의 `deal` 칸(`PostRow.deal`·`PostDetail.deal`)과 글쓰기·고치기 본문의 `deal` 도 이 모양이다(게시판 타입이 이 파일을 import 한다 — 이 파일은 다른 업무를 import 하지 않는다).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 금액은 원(정수).
 * 할인율·제휴 강제·종료 판정은 서버가 정한다(화면은 미리보기만).
 */

/** 저장 상태 — 진행 · 종료 */
export type DealStatus = 'ACTIVE' | 'ENDED';
/** 읽을 때 계산한 상태 — `FLAGGED` 종료 요청이 문턱 이상이고 작성자가 「아직 진행 중」을 누르기 전 · `EXPIRED` 마감(`endsAt`)이 지남 */
export type DealView = 'ACTIVE' | 'FLAGGED' | 'ENDED' | 'EXPIRED';
/** 종료한 사람 — 작성자 · 운영진(게시판 매니저·스텝·`hotdeal.manage`) · 회원 알림으로 자동 종료(배치) */
export type DealEndedBy = 'AUTHOR' | 'MANAGER' | 'AUTO';
/** 재고 표시 — 모름 · 재고 있음 · 얼마 안 남음 · 품절 */
export type DealStock = 'UNKNOWN' | 'IN_STOCK' | 'LOW' | 'SOLD_OUT';
/** 종료 요청 사유 — 품절 · 가격 변경 · 링크 안 열림(자유 글 없음) */
export type DealEndReason = 'SOLD_OUT' | 'PRICE_CHANGED' | 'LINK_DEAD';
/** 링크 규칙 — 차단 · 제휴(제휴 표시 자동 켬). 규칙이 없으면 null(대형몰 표시만 있을 수 있다) */
export type DomainRule = 'BLOCK' | 'AFFILIATE';
/** 딜 출처 — 1차는 회원만(사업자 `BUSINESS` 는 2차) */
export type DealSource = 'MEMBER';
/** 상품 이미지의 출처 — 링크 OG 자동 · 글쓴이가 올림 */
export type DealImageSource = 'OG' | 'UPLOAD';

/** 목록 정렬(게시판 목록·가젯 `HOT_DEALS` 공통) — 최신 · 인기(추천) · 마감 임박 */
export type DealSort = 'LATEST' | 'POPULAR' | 'ENDING_SOON';

/** 상품 이미지 — `imageId` 는 올린 이미지(`POST /api/images` 의 id)일 때만(가정: 계약에 없는 칸, 서버가 정하면 따른다) */
export interface DealImage {
    url: string;
    source: DealImageSource;
    imageId?: string | null;
}

/** 종료 요청 집계 — 요청자 신원은 누구에게도 보이지 않는다(개수·사유별 개수만). `mine` 은 내가 보낸 사유(안 보냈으면 null) */
export interface DealEndRequests {
    total: number;
    bySoldOut: number;
    byPriceChanged: number;
    byLinkDead: number;
    mine: DealEndReason | null;
}

/** 글에 붙은 딜 — 글 줄·글 상세의 `deal` */
export interface PostDeal {
    source: DealSource;
    /** 쇼핑몰 이름 1~40자 */
    storeName: string;
    /** 상품 링크 ≤1000자 */
    productUrl: string;
    /** 판정한 호스트(소문자·www. 뗌) */
    productHost: string;
    normalPrice: number | null;
    dealPrice: number;
    /** 읽을 때 계산(정상가 > 딜가 일 때 정수 %) */
    discountRate: number | null;
    /** 0 무료 · null 모름 · 그 밖 금액 */
    shippingFee: number | null;
    couponNote: string | null;
    priceBasis: string | null;
    startsAt: string | null;
    endsAt: string | null;
    stockState: DealStock;
    /** 제휴 링크(제휴 표시) */
    affiliate: boolean;
    image: DealImage | null;
    status: DealStatus;
    view: DealView;
    endedBy: DealEndedBy | null;
    endedAt: string | null;
    endRequests: DealEndRequests;
    flaggedAt: string | null;
    /** 의심 상태면 자동 종료 예정 시각(`flaggedAt` + 유예) */
    autoEndAt: string | null;
    /** 진행 중인 같은 딜(같은 `product_url_key`) 글 수 — 이 글 제외 */
    sameDealCount: number;
}

/** 글쓰기·고치기 본문의 `deal` — 할인율·호스트·상태는 서버가 정한다 */
export interface DealInput {
    storeName: string;
    productUrl: string;
    normalPrice: number | null;
    dealPrice: number;
    shippingFee: number | null;
    couponNote: string | null;
    priceBasis: string | null;
    endsAt: string | null;
    stockState: DealStock;
    affiliate: boolean;
    image: DealImage | null;
}

/** 링크 OG 가져오기 결과 — 가져옴 · 이미지 없음 · 가져오지 못함 */
export type DealOgStatus = 'OK' | 'NONE' | 'FAILED';

/** 같은 딜 한 줄 — 글쓰기의 「진행 중인 같은 딜 N건」·글 보기의 「같은 딜」 묶음 */
export interface DealSameRef {
    postId: string;
    title: string;
    dealPrice: number;
    createdAt: string;
}

/** `GET /api/deals/link-check?url=&board=` — 링크 판정(접미사 일치·더 구체적인 규칙 우선) + OG + 같은 딜 */
export interface DealLinkCheck {
    host: string;
    rule: DomainRule | null;
    /** 대형몰(위장 홍보 후보 계산에서 빠짐) */
    common: boolean;
    /**
     * 단축 주소 차단인가(BLOCK 규칙 중 메모 「단축 주소」 시드) — 화면이 「단축 주소는 원래 주소로 바꿔 주세요」를 따로 보이려고 둔 칸.
     * 가정(계약에 없음): 서버가 규칙 메모를 비교하지 않고 이 칸으로 알려 준다. 없으면 false 로 본다
     */
    shortener?: boolean;
    /** 호스트 → 이름 표로 찾은 쇼핑몰 이름(없으면 null) */
    storeName: string | null;
    og: { status: DealOgStatus; imageUrl: string | null; title: string | null; storeName: string | null };
    sameDeals: DealSameRef[];
}

/** `POST /api/posts/{id}/deal/end-requests` 본문 */
export interface DealEndRequestBody {
    reason: DealEndReason;
}

/** 딜 이미지 올리기 결과 — 가정: 글 이미지 API(`POST /api/images`)를 그대로 쓴다 */
export interface DealImageUpload {
    id: string;
    url: string;
}
