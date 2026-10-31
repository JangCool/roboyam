export interface BoardFeedTarget {
    /** `adsHidden`·`allowAdult` 를 본다 */
    board: object | null;
    firstPage: boolean;
    search: boolean;
    /** 일반 글 수(공지·고정 제외) */
    rows: number;
}
