export interface BoardFeedTarget {
    /** 게시판 slug — 게시판 맥락 호출이라 `board` 로 넘긴다 */
    slug: string;
    /** `adsHidden`·`allowAdult` 를 본다 */
    board: { adsHidden?: boolean; allowAdult?: boolean } | null;
    firstPage: boolean;
    search: boolean;
    /** 일반 글 수(공지·고정 제외) */
    rows: number;
}
