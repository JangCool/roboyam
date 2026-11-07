/**
 * 공개 팝업 API 모양 — 서버 `GET /api/site/popups`(비로그인도 받는다, 계약 `docs/features/site-popup-contract.md`).
 * 「팝업」 = 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창. 운영자가 관리자 「팝업 관리」에서 정한다. 메뉴 줄 아래 한 줄인 「공지 띠」와 다르다.
 * 서버는 노출을 켰고 기간 안인 것만 목록 차례대로 준다. 보일 곳(홈/전체)·기기(PC/모바일)·닫음 기록은 화면이 가린다(`UtilSitePopup`).
 */

export type SitePopupKind = 'IMAGE' | 'TEXT';
export type SitePopupTarget = 'HOME' | 'ALL';
export type SitePopupDevice = 'PC' | 'MOBILE';
/** 닫기 줄 왼쪽 선택지 — NONE 이면 「닫기」만 */
export type SitePopupHideOption = 'NONE' | 'TODAY' | 'WEEK' | 'NEVER';

export interface PublicSitePopup {
    id: string;
    /** 내용 표식 — 운영자가 팝업을 고치면 바뀐다(닫음 기록이 남아 있어도 다시 보인다) */
    version: string;
    kind: SitePopupKind;
    imageUrl: string | null;
    imageAlt: string | null;
    /** `/…` 는 같은 창, `http(s)://` 는 새 창 */
    link: string | null;
    heading: string | null;
    body: string | null;
    buttonLabel: string | null;
    target: SitePopupTarget;
    devices: SitePopupDevice[];
    hideOption: SitePopupHideOption;
    /** 띄울 창 1~3 — 같은 번호끼리 한 창에서 슬라이드, 번호가 다르면 PC 는 나란히·모바일은 차례로 */
    windowNo: number;
}

/** 여러 창 배치 — PC 에서 창을 가로로 나란히 · 위아래로 쌓기(모바일은 어느 쪽이든 하나씩 차례로) */
export type SitePopupLayout = 'HORIZONTAL' | 'VERTICAL';

export interface SitePopupsResponse {
    layout: SitePopupLayout;
    items: PublicSitePopup[];
}
