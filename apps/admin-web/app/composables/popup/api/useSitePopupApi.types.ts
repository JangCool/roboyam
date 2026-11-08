/**
 * 팝업 관리 API 가 주고받는 모양 — 서버 `/api/admin/site-popups`(계약 `docs/features/site-popup-contract.md`).
 * 「팝업」 = 공개 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창(레이어 팝업). 메뉴 줄 아래 한 줄인 「공지 띠」(site-notice-bar-contract)와 다르다.
 */

/** 팝업 종류 — 이미지(+ 링크) · 텍스트(제목·내용·단추) */
export type SitePopupKind = 'IMAGE' | 'TEXT';

/** 보일 곳 — 홈 화면만 · 모든 화면 */
export type SitePopupTarget = 'HOME' | 'ALL';

/** 보일 기기 — 화면 폭 기준(768px 이상 PC, 미만 모바일 — 공개 사이트 `md` 경계) */
export type SitePopupDevice = 'PC' | 'MOBILE';

/**
 * 닫기 줄 왼쪽의 「보지 않기」 선택지(운영자가 하나 고른다) — 오른쪽 「닫기」는 늘 있다(이번 방문 동안만 닫는다).
 *  · NONE  — 「닫기」만
 *  · TODAY — 「오늘 하루 보지 않기」(이 브라우저, 그날 자정까지)
 *  · WEEK  — 「1주일 동안 보지 않기」(7일)
 *  · NEVER — 「다시 보지 않기」(팝업 내용을 고치면 다시 보인다)
 */
export type SitePopupHideOption = 'NONE' | 'TODAY' | 'WEEK' | 'NEVER';

/** 목록 상태 — 서버가 지금 시각으로 판정한다 */
export type SitePopupStatus = 'SHOWING' | 'SCHEDULED' | 'EXPIRED' | 'OFF';

/** 여러 창 배치(사이트 전체 하나) — PC 에서 창을 가로로 나란히 · 위아래로 쌓기. 모바일은 어느 쪽이든 창을 하나씩 차례로 */
export type SitePopupLayout = 'HORIZONTAL' | 'VERTICAL';

export interface SitePopupEditor {
    id: string;
    name: string | null;
}

/** 팝업 한 개(관리 목록 줄) */
export interface SitePopupItem {
    id: string;
    /** 1부터 — 공개 사이트에서 넘기는 차례 */
    order: number;
    /** 관리용 이름(방문자에게 보이지 않는다) */
    name: string;
    kind: SitePopupKind;
    /** IMAGE — 그림 파일 ID 와 보일 주소 */
    imageFileId: string | null;
    imageUrl: string | null;
    /** IMAGE — 그림 대체 글(화면 읽기 프로그램용) */
    imageAlt: string | null;
    /** 누르면 갈 곳 — IMAGE 는 그림 전체, TEXT 는 아래 단추. `/…` 는 같은 창, `http(s)://` 는 새 창 */
    link: string | null;
    /** TEXT — 제목(선택) · 내용(필수, 줄바꿈 유지, 글자만) · 단추 글자(링크가 있을 때, 비우면 「자세히 보기」) */
    heading: string | null;
    body: string | null;
    buttonLabel: string | null;
    target: SitePopupTarget;
    devices: SitePopupDevice[];
    hideOption: SitePopupHideOption;
    /** 띄울 창 1~3 — 같은 번호끼리 한 창에서 슬라이드(목록 차례), 번호가 다르면 PC 는 나란히·모바일은 차례로 */
    windowNo: number;
    visible: boolean;
    /** 노출 기간(순간 ISO) — 비면 제한 없음 */
    showFrom: string | null;
    showUntil: string | null;
    status: SitePopupStatus;
    updatedAt: string;
    updatedBy: SitePopupEditor | null;
}

/** `GET` 목록 */
export interface SitePopupListView {
    items: SitePopupItem[];
    total: number;
    /** 지금 노출 중(SHOWING) 수 */
    showingCount: number;
    /** 여러 창 배치 */
    layout: SitePopupLayout;
}

/** 만들기·고치기 본문 — 고칠 때도 모든 칸을 보낸다(`imageFileId` null = 그림 없음) */
export interface SitePopupSaveRequest {
    name: string;
    kind: SitePopupKind;
    imageFileId: string | null;
    imageAlt: string | null;
    link: string | null;
    heading: string | null;
    body: string | null;
    buttonLabel: string | null;
    target: SitePopupTarget;
    devices: SitePopupDevice[];
    hideOption: SitePopupHideOption;
    windowNo: number;
    visible: boolean;
    showFrom: string | null;
    showUntil: string | null;
}
