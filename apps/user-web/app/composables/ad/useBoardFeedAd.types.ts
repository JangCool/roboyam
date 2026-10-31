/** 게시판 목록 한 줄 광고를 정할 목록 상태(`useBoardFeedAd`) */
export interface BoardFeedTarget {
    /** 게시판 정보(요약 또는 자세한 정보) — `adsHidden`·`allowAdult` 를 본다 */
    board: object | null;
    /** 첫 쪽인가 */
    firstPage: boolean;
    /** 검색 결과인가 */
    search: boolean;
    /** 이 쪽의 일반 글 수(공지·고정 제외) */
    rows: number;
}
