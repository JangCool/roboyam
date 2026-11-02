/**
 * 회원 핫딜(1차) API 가 주고받는 모양 — 계약 `docs/features/hotdeal-contract.md` 「데이터 모양」(두 앱이 같은 이름을 쓴다).
 * 글 줄·글 상세의 `deal` 칸(`PostRow.deal`·`PostDetail.deal`)과 글쓰기·고치기 본문의 `deal` 도 이 모양이다(게시판 타입이 이 파일을 import 한다 — 이 파일은 다른 업무를 import 하지 않는다).
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO. 금액은 원(정수).
 * 할인율·제휴 강제·종료 판정·인기(HOT)는 서버가 정한다(화면은 미리보기만).
 */

/** 저장 상태 — 진행 · 종료 */
export type DealStatus = 'ACTIVE' | 'ENDED';
/** 읽을 때 계산한 상태 — `FLAGGED` 종료 요청이 문턱 이상이고 작성자가 「아직 진행 중」을 누르기 전 · `EXPIRED` 마감(`endsAt`, 마감 없으면 등록 + `autoExpireDays`)이 지남 */
export type DealView = 'ACTIVE' | 'FLAGGED' | 'ENDED' | 'EXPIRED';
/** 종료한 사람 — 작성자 · 운영진(게시판 매니저·스텝·`hotdeal.manage`) · 회원 알림으로 자동 종료(배치) */
export type DealEndedBy = 'AUTHOR' | 'MANAGER' | 'AUTO';
/** 재고 표시 — 모름 · 재고 있음 · 얼마 안 남음 · 품절(품절 저장 = 작성자 종료) */
export type DealStock = 'UNKNOWN' | 'IN_STOCK' | 'LOW' | 'SOLD_OUT';
/** 종료 요청 사유 — 품절 · 가격 변경 · 링크 안 열림(자유 글 없음) */
export type DealEndReason = 'SOLD_OUT' | 'PRICE_CHANGED' | 'LINK_DEAD';
/** 가격 기준(선택지) — 표시가 · 쿠폰가 · 카드 할인가 · 회원가 · 해외 직구(관부가세 포함 여부는 `dutyIncluded`). 메모는 `priceBasis` */
export type DealPriceBasis = 'LISTED' | 'COUPON' | 'CARD' | 'MEMBER' | 'OVERSEAS';
/** 링크 규칙 — 차단 · 제휴(제휴 표시 자동 켬). 규칙이 없으면 null(대형몰 표시만 있을 수 있다) */
export type DomainRule = 'BLOCK' | 'AFFILIATE';
/** 딜 출처 — 회원 · 사업자(2차 — 예약된 사업자 핫딜이 시작 시각에 글로 올라간다. 「광고」 표시 + 상호, 작성자에게도 고치기 없음) */
export type DealSource = 'MEMBER' | 'BUSINESS';
/** 상품 이미지의 출처 — 링크 OG 자동 · 글쓴이가 올림 */
export type DealImageSource = 'OG' | 'UPLOAD';
/**
 * 제휴 링크 방식(D5 — 핫딜 설정 `affiliateMode`) — `MEMBER` 개인 제휴 허용(기본, 초기 운영) · `SITE` 사이트 제휴(개인 제휴 링크를 받지 않고, 구매 링크를 「사이트 제휴 몰」 표로 바꾼다)
 */
export type AffiliateMode = 'MEMBER' | 'SITE';
/**
 * 글의 제휴 표시 종류 — **지금 방식으로 서버가 계산**한다(저장값이 아니다). `MEMBER` 작성자 개인 제휴(「제휴·수수료」) · `SITE` 로보얌 사이트 제휴(「제휴」).
 * `SITE` 모드면 사이트 제휴 몰 글 = `SITE`, 그 밖(기존 개인 제휴 글 포함) = null. `MEMBER` 모드면 `affiliate` 글 = `MEMBER`
 */
export type DealAffiliateKind = 'MEMBER' | 'SITE';

/** 목록 정렬(게시판 목록·가젯 `HOT_DEALS` 공통) — 최신 · 인기(추천) · 마감 임박 */
export type DealSort = 'LATEST' | 'POPULAR' | 'ENDING_SOON';

/**
 * 상품 이미지 — `url` 은 늘 **우리 서버 주소**(D1: OG 이미지는 가로 600px JPEG 사본 한 장(WebP 원본은 작으면 그대로), 외부 이미지 직접 링크 금지).
 * `imageId` 는 올린 이미지(`POST /api/images` 의 id)일 때만. `originUrl`(OG 사본의 원본 주소)은 **공개 응답에서 늘 null**(관리자 상세만) — 보낼 때도 서버는 보지 않는다.
 * 글 저장 때 서버가 확인한다: `OG` 면 `url` 이 우리 OG 사본 주소, `UPLOAD` 면 `imageId` 가 그 회원이 올린 이미지(아니면 `400 fields."deal.image"`)
 */
export interface DealImage {
    url: string;
    source: DealImageSource;
    imageId?: string | null;
    originUrl?: string | null;
}

/** 게시판 관리 자리 — 그 글 게시판의 매니저 · 스텝(사용자 결정 2026-11-01) */
export type DealModeratorRole = 'MANAGER' | 'STAFF';

/**
 * 글 상세 `PostDetail.dealModeration` — 보는 사람이 그 게시판 매니저·스텝(정지 아님)이고 핫딜 글이면 객체, 아니면 null.
 * 작성자 본인이 매니저여도 작성자 조치(`mine`·`reopenUntil`)와 별개로 실린다. 막는 것은 서버다(누를 때마다 다시 판정 → `403 DEAL_MODERATE_FORBIDDEN`)
 */
export interface DealModeration {
    role: DealModeratorRole;
    /** [핫딜 종료] — 진행·의심·기간 지남 */
    canEnd: boolean;
    /** [다시 진행 중으로] — 종료(ENDED) */
    canReopen: boolean;
}

/** 종료 요청 집계 — 요청자 신원은 누구에게도 보이지 않는다(개수·사유별 개수만). `mine` 은 내가 보낸 사유(안 보냈으면 null) */
export interface DealEndRequests {
    total: number;
    bySoldOut: number;
    byPriceChanged: number;
    byLinkDead: number;
    mine: DealEndReason | null;
}

/** 딜 칸 수정 이력 요약(D3 — 이력 6개월 `post_deal_revisions`) — 가격(딜가·정상가 — 배송비 제외)을 고친 횟수 · 링크 호스트가 바뀐 적이 있다 · 마지막으로 딜 칸을 고친 시각 */
export interface DealRevision {
    priceEdits: number;
    linkChanged: boolean;
    lastEditedAt: string | null;
}

/** 글에 붙은 딜 — 글 줄·글 상세의 `deal` */
export interface PostDeal {
    source: DealSource;
    /**
     * 사업자 글(`BUSINESS`)이면 사업자 상호 — 작성자 자리·카드 첫 줄 광고 고지에 쓴다. 회원 글·옛 응답은 null·없음.
     * `applicationId` 는 그 글의 신청 건 — **보는 사람이 그 사업자(작성자)일 때만** 싣는다(카드의 [일시 정지]·「중단·환불은 신청 건에서」 — 목업 2026-11-02 제안, 없으면 단추 없음)
     */
    business?: { name: string; applicationId?: string | null } | null;
    /** 쇼핑몰 이름 1~40자 */
    storeName: string;
    /** 상품 링크 ≤1000자 */
    productUrl: string;
    /** 판정한 호스트(소문자·www. 뗌) */
    productHost: string;
    /** 리디렉션을 따라간 최종 호스트 — `productHost` 와 같거나 모르면 null */
    finalHost: string | null;
    normalPrice: number | null;
    /** 쿠폰 적용 뒤 실제 결제가(배송비 제외) */
    dealPrice: number;
    /** 읽을 때 계산(정상가 > 딜가 일 때 내림 정수 %) */
    discountRate: number | null;
    /** 0 무료 · null 모름 · 그 밖 금액 */
    shippingFee: number | null;
    couponNote: string | null;
    /** 가격 기준 선택지 — 없으면 null */
    priceBasisKind: DealPriceBasis | null;
    /** 해외 직구(`OVERSEAS`) 가격에 관부가세가 들어 있다 — 그 밖에는 false */
    dutyIncluded: boolean;
    /** 가격 기준 메모 ≤100자 */
    priceBasis: string | null;
    endsAt: string | null;
    /** 마감이 없을 때 기간 종료로 바뀌는 시각(등록 + `autoExpireDays`) — 마감이 있거나 설정이 꺼졌으면 null */
    autoExpireAt: string | null;
    stockState: DealStock;
    /** 제휴 링크(제휴 표시) */
    affiliate: boolean;
    image: DealImage | null;
    status: DealStatus;
    view: DealView;
    endedBy: DealEndedBy | null;
    endedAt: string | null;
    /** 작성자 되돌리기 기한(D2) — 작성자가 직접 종료했고 아직 되돌리지 않았고 종료 뒤 24시간 안일 때만. 그 밖은 null */
    reopenUntil: string | null;
    endRequests: DealEndRequests;
    flaggedAt: string | null;
    /** 의심 상태면 자동 종료 예정 시각(`flaggedAt` + 유예) */
    autoEndAt: string | null;
    /** 작성자가 「아직 진행 중이에요」를 누른 횟수 */
    keepCount: number;
    /** 「아직 진행 중이에요」 최대 횟수(설정 `keepMaxCount`, 0 = 제한 없음) — 다 쓰면 단추 대신 「운영자 확인 중」 */
    keepMax: number;
    /** 딜 칸 수정 이력 요약(D3) */
    revision: DealRevision;
    /** 추천 ≥ `hotLikeThreshold`(서버 판정) */
    hot: boolean;
    /** 진행 중인 같은 딜(같은 `product_url_key`) 글 수 — 이 글 제외 */
    sameDealCount: number;
    /**
     * 구매하기 주소 — 늘 서버 경유 `/api/deals/{postId}/go`(D5 — 서버가 클릭 시점의 `affiliateMode`·사이트 제휴 몰로 바꿔 302). 화면은 이 주소를 그대로 링크에 쓴다(`productUrl` 로 직접 보내지 않는다)
     */
    buyUrl: string;
    /** 제휴 표시 종류(지금 방식으로 서버가 계산) — 배지·카드 첫 줄이 이것으로 가른다. null 이면 표시 없음 */
    affiliateKind: DealAffiliateKind | null;
    /** `affiliateKind === 'SITE'` 일 때 사이트 제휴 프로그램 이름(그 밖 null) */
    siteProgramName: string | null;
}

/** 글쓰기·고치기 본문의 `deal` — 할인율·호스트·상태는 서버가 정한다 */
export interface DealInput {
    storeName: string;
    productUrl: string;
    normalPrice: number | null;
    dealPrice: number;
    shippingFee: number | null;
    couponNote: string | null;
    priceBasisKind: DealPriceBasis | null;
    /** `OVERSEAS` 가 아니면 false 로 보낸다 */
    dutyIncluded: boolean;
    priceBasis: string | null;
    endsAt: string | null;
    stockState: DealStock;
    affiliate: boolean;
    image: DealImage | null;
}

/** 링크 OG 가져오기 결과 — 가져옴 · 이미지 없음 · 가져오지 못함 · 설정으로 꺼짐(`ogFetchEnabled` 끔) */
export type DealOgStatus = 'OK' | 'NONE' | 'FAILED' | 'OFF';

/** 같은 딜 한 줄 — 글쓰기의 「진행 중인 같은 딜 N건」·글 보기의 「같은 딜」 묶음(줄마다 가격 차) */
export interface DealSameRef {
    postId: string;
    title: string;
    dealPrice: number;
    /** 0 무료 · null 모름 */
    shippingFee: number | null;
    createdAt: string;
}

/** `GET /api/deals/link-check?url=&board=` — 링크 판정(접미사 일치·더 구체적인 규칙 우선, 리디렉션 체인의 모든 호스트) + OG + 같은 딜 */
export interface DealLinkCheck {
    host: string;
    /** 리디렉션을 따라간 최종 호스트(최대 3회) — `host` 와 같거나 따라가지 못했으면 null. 규칙은 체인의 모든 호스트에 적용(차단 > 제휴) */
    finalHost: string | null;
    rule: DomainRule | null;
    /** 대형몰(위장 홍보 후보 계산에서 빠짐) */
    common: boolean;
    /** 단축 주소 차단인가(BLOCK 규칙 중 단축 주소 시드) — 화면이 「단축 주소는 원래 주소로 바꿔 주세요」를 따로 보인다 */
    shortener: boolean;
    /** 호스트 → 이름 표로 찾은 쇼핑몰 이름(없으면 null) */
    storeName: string | null;
    /** `imageUrl` 은 우리 서버 사본(D1) */
    og: { status: DealOgStatus; imageUrl: string | null; title: string | null; storeName: string | null };
    sameDeals: DealSameRef[];
    /**
     * 지금 제휴 링크 방식(D5) — 쓰기 화면은 게시판 응답 `BoardDetail.dealAffiliateMode` 로 먼저 알고, 판정이 오면 이 값이 우선한다(더 새것).
     * `SITE` 이고 `rule === 'AFFILIATE'`(개인 제휴 — 제휴 네트워크 단축·추적 링크, 최종 호스트 포함)면 등록 불가(저장 때 `400 VALIDATION fields."deal.productUrl"`)
     */
    affiliateMode: AffiliateMode;
    /** `SITE` 이고 이 링크(최종 호스트 우선)가 켜진 사이트 제휴 몰이면 그 프로그램 — 쓰기 화면이 「구매 링크는 로보얌 제휴 링크로 바뀝니다」를 보인다. 그 밖 null */
    siteAffiliate: { programName: string } | null;
}

/** `POST /api/posts/{id}/deal/end-requests` 본문 */
export interface DealEndRequestBody {
    reason: DealEndReason;
}

/** 딜 이미지 올리기 결과 — 글 이미지 API(`POST /api/images`)를 그대로 쓴다(`ImageUploadResult` 의 `id`·`url`) */
export interface DealImageUpload {
    id: string;
    url: string;
}

/** 저장은 됐지만 알릴 것 — `POST/PUT /api/posts` 응답 `warnings[]`(예 `DEAL_LINK_BLOCKED` — 수정 저장 때 현재 규칙으로 다시 판정했더니 차단 도메인). 문구는 서버 몫 */
export interface DealWarning {
    code: string;
    message: string;
}

/**
 * `GET /api/deals/prefill?from={postId}` — 끝난 딜(종료·기간 종료)에서 새 딜 올리기. 링크·몰·이미지·제목을 미리 채우고 가격·마감은 비운다.
 * 진행 중인 딜이면 `409 DEAL_NOT_ENDED`. `image` 는 OG 사본일 때만(올린 이미지는 그 글의 것이라 null)
 */
export interface DealPrefill {
    fromPostId: string;
    title: string;
    storeName: string;
    productUrl: string;
    image: DealImage | null;
}

/** 핫딜 목록의 쇼핑몰 칩 한 개 — 목록 응답 `stores`(상위 8몰, 글 수 많은 차례) */
export interface DealStoreChip {
    name: string;
    count: number;
}

/**
 * 핫딜 게시판의 공개 설정 칸(게시판 응답 `BoardDetail.dealRules` — 서버 `DealRulesView`) — 화면의 미리 검사·안내용(최종 판정은 서버).
 * 없으면(옛 응답·핫딜 게시판 아님) 화면은 기본값(`UtilHotdeal.DEFAULT_RULES`)으로 본다
 */
export interface DealRules {
    /** 금액(딜가·정상가·배송비) 상한(원) */
    priceMaxWon: number;
    /** 할인율이 이 % 이상이면 경고 + 가격 기준 필수 */
    discountWarnPercent: number;
    /** 마감 없는 딜이 기간 종료로 바뀌는 일수(0 = 끔) */
    autoExpireDays: number;
    /** 추천이 이 수 이상이면 HOT(판정은 서버 `PostDeal.hot`) */
    hotLikeThreshold: number;
}
