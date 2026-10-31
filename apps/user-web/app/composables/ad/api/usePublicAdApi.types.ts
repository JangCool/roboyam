/**
 * 공개 광고 노출 API(`/api/ads/**`)가 주고받는 모양 — **후보(정본 후보)**. 설계 정본 `docs/plans/biz-ad-hotdeal/02-ad.md` §7-7, 쿼리 후보 `docs/features/ad-contract.md` 「공개 노출 쿼리 후보」.
 * 서버는 아직 없다(목업 — `usePublicAdApi.ts`). 유형 이름은 광고 일정표·신청 건 상품 이름 그대로(`WIDE_BANNER` …, 서버 `BizProduct`).
 * ⚠ ID 는 문자열(Snowflake). 화면은 없는 칸·틀린 값에도 깨지지 않게 `UtilAd.readAds` 로 한 번 읽는다(링크가 틀린 광고는 버린다).
 */

/** 광고 유형 7개 — 배너 넷 · 한 줄 광고 · 핫딜 홈 상단 · 핫딜 말머리 상단 */
export type PublicAdType = 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER' | 'TEXT_LINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';

/** 배너 넷(이미지 + 링크 + 대체 글) */
export type BannerAdType = Extract<PublicAdType, 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER'>;

/** 핫딜 노출 두 유형(핫딜 글을 목록 위에 「광고」 표시로) */
export type HotdealAdType = Extract<PublicAdType, 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY'>;

/** 핫딜 노출 광고가 가리키는 핫딜 글(핫딜 노출 유형만 — 나머지는 null) */
export interface PublicAdDeal {
    postId: string;
    title: string;
    shopName: string;
    /** 딜가(원) */
    dealPrice: number;
    /** 정상가(원) — 모르면 null */
    listPrice: number | null;
    thumbnailUrl: string | null;
    /** 그 핫딜 글의 말머리(말머리 상단이면 그 말머리) */
    prefixName: string | null;
}

/**
 * 노출 후보 한 건 — 지금 진행 중(`SCHEDULED` 이고 `start_at <= now < end_at`)인 광고 건 + 최신 **승인된** 소재 버전.
 * 같은 자리 여러 건 중 무엇을 보일지는 화면이 `weight`(1~10) 가중 무작위로 고른다(`UtilAd.pick`). 노출·클릭 집계는 `version`(소재 버전) 단위다(A2).
 */
export interface PublicAd {
    /** 광고 건(신청 건) ID */
    id: string;
    type: PublicAdType;
    /** 소재 버전 — 집계 단위 */
    version: number;
    /** 배너 이미지 주소(배너만) */
    imageUrl: string | null;
    /** 한 줄 광고 문구(40자 이내, 한 줄 광고만) */
    text: string | null;
    /** 누르면 가는 곳 — 배너·한 줄은 광고주 https 주소 그대로(바꾸지 않는다), 핫딜 노출은 그 핫딜 글(`/posts/{id}`) */
    linkUrl: string;
    /** 배너 대체 글(이미지 `alt`) — 한 줄 광고는 문구와 같다 */
    altText: string;
    /** 비중 1~10(기본 5) */
    weight: number;
    deal: PublicAdDeal | null;
}

/** `GET /api/ads` 질의 — 핫딜 말머리 상단은 그 게시판·말머리가 있어야 고른다 */
export interface PublicAdsQuery {
    types: PublicAdType[];
    /** 핫딜 게시판 slug(`HOTDEAL_*` 만) */
    board?: string;
    /** 말머리 ID(`HOTDEAL_CATEGORY` 만) */
    prefix?: string;
}

/** `GET /api/ads?types=…` 응답 — 유형이 섞인 후보 목록(화면이 유형별로 나눈다). 사본 20초 */
export interface PublicAdsResponse {
    items: PublicAd[];
}

/** 글 중간에 놓을 유형 — 배너 하나 또는 한 줄 광고 */
export type PostInlineAdType = BannerAdType | 'TEXT_LINE';

/** 글 보기 중간 자리 설정(커뮤니티 `inline_after` 자리의 로보얌 몫 — 가젯 격자로 못 그리는 유일한 자리) */
export interface PostInlinePlacement {
    enabled: boolean;
    adType: PostInlineAdType;
}

/** `GET /api/ads/placements` 응답 — 가젯이 아닌 광고 자리 설정 */
export interface PublicAdPlacements {
    postInline: PostInlinePlacement;
}

/** 노출 한 번 — 화면에 실제로 보였을 때(절반 이상, 한 자리 한 번) */
export interface AdImpressionItem {
    adId: string;
    version: number;
    /** 어디서 보였나(`AdPlacement`) */
    placement: string;
}

/** `POST /api/ads/impressions` 본문 — 모아 보내기(1초) · 창을 떠날 때 `sendBeacon` */
export interface AdImpressionsRequest {
    items: AdImpressionItem[];
}

/** `POST /api/ads/{id}/clicks` 본문(`sendBeacon`) */
export interface AdClickRequest {
    version: number;
    placement: string;
}
