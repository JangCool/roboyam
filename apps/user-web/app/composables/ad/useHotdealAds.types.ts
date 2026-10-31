/** 핫딜 노출 자리를 그릴 게시판 목록의 상태(`useHotdealAds`) */
export interface HotdealAdTarget {
    /** 게시판 slug */
    board: string;
    /** 핫딜 게시판인가(게시판 정보 `dealEnabled`) */
    dealEnabled: boolean;
    /** 고른 말머리 ID(없으면 빈 글자) */
    prefix: string;
    /** 고른 말머리 이름(없으면 빈 글자) */
    prefixName: string;
    /** 첫 쪽이고 검색이 아닌가 — 핫딜 노출은 목록 맨 위(첫 쪽)에만 */
    firstPage: boolean;
}
