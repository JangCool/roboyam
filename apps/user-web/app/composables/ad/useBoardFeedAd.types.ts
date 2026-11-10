export interface BoardFeedTarget {
    /** 게시판 slug — 게시판 맥락 호출이라 `board` 로 넘긴다 */
    slug: string;
    /** `adsHidden`·`allowAdult`·`kind`(소모임 게시판 `GROUP` 이면 애드센스를 넣지 않는다) 를 본다 */
    board: { adsHidden?: boolean; allowAdult?: boolean; kind?: string } | null;
    firstPage: boolean;
    search: boolean;
    /** 일반 글 수(공지·고정 제외) */
    rows: number;
}

/** 애드센스 인피드를 물을 목록 — 장부 한 줄 광고와 같은 목록 + 목록형(LIST)인가 */
export interface BoardFeedAdSenseTarget extends BoardFeedTarget {
    /** 목록형(LIST)이고 핫딜 게시판이 아님 — 카드형·갤러리형·핫딜 목록·핫딜 격자에는 애드센스를 넣지 않는다 */
    listStyle: boolean;
}
