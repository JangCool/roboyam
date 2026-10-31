/**
 * 공개 광고 노출 API(`/api/ads/**`)가 주고받는 모양 — **후보(정본 후보)**. 설계 정본 `docs/plans/biz-ad-hotdeal/02-ad.md` §7-7, 쿼리 후보 `docs/features/ad-contract.md` 「공개 노출 쿼리 후보」.
 * 서버는 아직 없다(목업 — `usePublicAdApi.ts`). 유형 이름은 광고 일정표·신청 건 상품 이름 그대로(`WIDE_BANNER` …, 서버 `BizProduct`).
 * ⚠ ID 는 문자열(Snowflake). 화면은 없는 칸·틀린 값에도 깨지지 않게 `UtilAd.readAds` 로 한 번 읽는다(링크가 틀린 광고는 버린다).
 */

/**
 * 광고 유형 8개 — 배너 넷 · 날개 배너 · 한 줄 광고 · 핫딜 홈 상단 · 핫딜 말머리 상단.
 * `WING_BANNER`(날개 배너 — 본문 바깥 좌우 여백의 세로 배너 160×600, 2026-10-31 목업)는 **서버 상품에 아직 없는 새 유형**이다(서버 `BizProduct`·요금표·일정표에 더해야 한다).
 */
export type PublicAdType = 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER' | 'WING_BANNER' | 'TEXT_LINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';

/** 배너 넷(이미지 + 링크 + 대체 글) — 광고 배너 가젯(`AD`)이 고르는 유형. 날개 배너는 날개 자리 전용이라 넣지 않는다 */
export type BannerAdType = Extract<PublicAdType, 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER'>;

/** 이미지 광고(배너 넷 + 날개 배너) — 같은 틀 부품(`AdSlot`)이 그린다 */
export type ImageAdType = BannerAdType | 'WING_BANNER';

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
    /** 광고주 이름(선택 — 게시판 목록 한 줄 광고의 작성자 자리). 서버가 주지 않으면 비운다 */
    advertiserName?: string | null;
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

/** 글 보기 광고 유형 — 한 줄 광고(기본 — 이미지 없이 가볍다) · 띠 배너 · 네모 배너 */
export type PostInlineAdType = 'TEXT_LINE' | 'STRIP_BANNER' | 'SQUARE_BANNER';

/** 글 보기 광고 자리 — 글 카드와 댓글 카드 사이 회색 틈(기본 — 어느 카드에도 들지 않는 한 줄) · 본문 바로 아래(추천 줄 위 — 네모 배너를 두는 선택지) */
export type PostInlinePosition = 'BETWEEN_CARDS' | 'AFTER_BODY';

/** 글 보기 광고 자리 설정(커뮤니티 `inline_after` 자리의 로보얌 몫 — 가젯 격자로 못 그리는 자리) */
export interface PostInlinePlacement {
    enabled: boolean;
    adType: PostInlineAdType;
    position: PostInlinePosition;
}

/** 날개 배너를 둘 화면 — 홈 · 가젯 페이지 · 게시판 목록 · 글 보기(로그인·설정·마이페이지·결제 같은 화면에는 두지 않는다) */
export type WingScreen = 'HOME' | 'PAGE' | 'BOARD' | 'POST';

/** 날개 배너 자리 설정(사이트 단위) — 왼쪽·오른쪽을 따로 켜고, 둘 화면을 고른다 */
export interface WingPlacement {
    left: boolean;
    right: boolean;
    screens: WingScreen[];
}

/** 게시판 목록 한 줄 광고(인피드) 자리 설정 — 일반 글(공지·고정 제외) `after` 번째 뒤에 한 줄(1~20, 기본 5) */
export interface BoardListPlacement {
    enabled: boolean;
    after: number;
}

/** `GET /api/ads/placements` 응답 — 가젯이 아닌 광고 자리 설정 */
export interface PublicAdPlacements {
    postInline: PostInlinePlacement;
    wings: WingPlacement;
    boardList: BoardListPlacement;
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
