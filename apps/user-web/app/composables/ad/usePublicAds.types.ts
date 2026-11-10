import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdSenseUnitSpec } from '~/utils/util-adsense';

/** 노출·클릭 집계의 `placement` */
export type AdPlacement = 'HOME' | 'PAGE' | 'BOARD' | 'POST_INLINE' | 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY' | 'WING_LEFT' | 'WING_RIGHT' | 'BOARD_LIST';

/**
 * 게시판 화면·글 보기가 알려 주는 날개 맥락 — 날개(`AdWings`)는 이 주소일 때 `board` 로 부르고, 소모임·광고 숨김·19금(옵션 꺼짐)이면 그리지 않는다.
 * 주소만으로는 소모임 글인지·숨김 게시판인지 모른다.
 */
export interface WingContext {
    path: string;
    /** 게시판 slug(`board=` 로 넘긴다) */
    board: string;
    adsHidden: boolean;
    /** 19금 게시판(`allowAdult`) 또는 19금 글(`post.adult`) */
    adult: boolean;
    group: boolean;
    /** 비밀글(`post.secret`) — 장부 광고는 보지 않고 애드센스만 뺀다(`UtilAdSense.contextBlocked`) */
    secret?: boolean;
}

/** 광고 가젯의 `data` — 서버가 비워 보내고 화면이 채운다(`useAdGadgets`) */
export interface AdGadgetData {
    /** null = 받는 중(자리를 비율 틀로 잡는다) */
    ads: PublicAd[] | null;
    placement: AdPlacement;
    /** 장부 후보가 0건인 배너 가젯을 채울 애드센스 단위 — 없으면 null */
    adsense?: AdSenseUnitSpec | null;
    /** 광고가 오지 않았을 때 접은 표시 키(`useAdSense().markUnfilled`) */
    adsenseKey?: string;
}
