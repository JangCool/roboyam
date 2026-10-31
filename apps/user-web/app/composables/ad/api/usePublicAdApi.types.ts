/** 공개 광고 노출 API(`/api/ads/**`) 모양 — 계약 `docs/features/ad-contract.md` 「공개 노출 목업」 */

/** 상품 이름 그대로(서버 `BizProduct`) */
export type PublicAdType = 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER' | 'WING_BANNER' | 'TEXT_LINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';

/** 광고 배너 가젯(`AD`)이 고르는 배너 넷 — 날개 배너는 날개 자리 전용 */
export type BannerAdType = Extract<PublicAdType, 'WIDE_BANNER' | 'STRIP_BANNER' | 'SQUARE_BANNER' | 'SMALL_BANNER'>;

export type ImageAdType = BannerAdType | 'WING_BANNER';

export interface PublicAdDeal {
    postId: string;
    title: string;
    shopName: string;
    dealPrice: number;
    listPrice: number | null;
    thumbnailUrl: string | null;
    prefixName: string | null;
}

export interface PublicAd {
    id: string;
    type: PublicAdType;
    /** 소재 버전 — 노출·클릭 집계 단위 */
    version: number;
    imageUrl: string | null;
    text: string | null;
    /** 광고주 주소 그대로 · 핫딜 노출은 `/posts/{id}` */
    linkUrl: string;
    altText: string;
    /** 1~10 — 화면이 가중 무작위로 고른다 */
    weight: number;
    /** 핫딜 두 유형만 */
    deal: PublicAdDeal | null;
    /** 광고주 표시 이름(사업자 상호 또는 광고주 이름) — 없으면 null */
    advertiserName: string | null;
}

export interface PublicAdsQuery {
    types: PublicAdType[];
    /** 게시판 맥락 호출(목록 한 줄·글 보기·핫딜)만 — 가젯·날개는 넘기지 않는다 */
    board?: string;
    prefix?: string;
}

export interface PublicAdsResponse {
    items: PublicAd[];
}

export type PostInlineAdType = 'TEXT_LINE' | 'STRIP_BANNER' | 'SQUARE_BANNER';

/** 글 카드와 댓글 카드 사이 · 본문 바로 아래(추천 줄 위) */
export type PostInlinePosition = 'BETWEEN_CARDS' | 'AFTER_BODY';

export interface PostInlinePlacement {
    enabled: boolean;
    adType: PostInlineAdType;
    position: PostInlinePosition;
}

export type WingScreen = 'HOME' | 'PAGE' | 'BOARD' | 'POST';

export interface WingPlacement {
    left: boolean;
    right: boolean;
    screens: WingScreen[];
}

export interface BoardListPlacement {
    enabled: boolean;
    /** 일반 글(공지·고정 제외) 몇 번째 뒤 — 1~20 */
    after: number;
}

export interface PublicAdPlacements {
    postInline: PostInlinePlacement;
    wings: WingPlacement;
    boardList: BoardListPlacement;
}

export interface AdImpressionItem {
    adId: string;
    version: number;
    placement: string;
}

export interface AdImpressionsRequest {
    items: AdImpressionItem[];
}

export interface AdClickRequest {
    version: number;
    placement: string;
}
