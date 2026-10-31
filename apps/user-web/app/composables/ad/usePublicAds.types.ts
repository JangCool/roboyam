import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';

/** 노출·클릭 집계의 `placement` */
export type AdPlacement = 'HOME' | 'PAGE' | 'BOARD' | 'POST_INLINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY' | 'WING_LEFT' | 'WING_RIGHT' | 'BOARD_LIST';

/** 광고 가젯의 `data` — 서버가 비워 보내고 화면이 채운다(`useAdGadgets`) */
export interface AdGadgetData {
    /** null = 받는 중(자리를 비율 틀로 잡는다) */
    ads: PublicAd[] | null;
    placement: AdPlacement;
}
