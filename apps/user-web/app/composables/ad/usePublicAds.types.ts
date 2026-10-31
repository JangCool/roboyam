import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';

/**
 * 광고가 어디서 보였나(노출·클릭 집계의 `placement`) — 홈 가젯 · 가젯 페이지 가젯 · 게시판 레이아웃 가젯 · 글 중간 · 핫딜 홈 상단 · 핫딜 말머리 상단
 */
export type AdPlacement = 'HOME' | 'PAGE' | 'BOARD' | 'POST_INLINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY';

/**
 * 광고 가젯(`AD`·`AD_TEXT_LINE`)의 `data` — 서버 홈 응답이 아니라 **화면이 채운다**(`useAdGadgets`): 광고는 홈 응답과 따로 사본 캐시(20초)라서
 * 홈·가젯 페이지·게시판 레이아웃·글 중간이 같은 길(`usePublicAds`)로 후보를 받는다.
 */
export interface AdGadgetData {
    /** 그 유형의 후보 — null 이면 아직 받는 중(자리를 비율 틀로 잡아 둔다) */
    ads: PublicAd[] | null;
    placement: AdPlacement;
}
