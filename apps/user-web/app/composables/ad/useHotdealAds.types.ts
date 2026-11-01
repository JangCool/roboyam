export interface HotdealAdTarget {
    board: string;
    /** `adsHidden`·`allowAdult` 를 본다 */
    view: { adsHidden?: boolean; allowAdult?: boolean } | null;
    dealEnabled: boolean;
    /** 없으면 빈 글자 */
    prefix: string;
    prefixName: string;
    /** 첫 쪽이고 검색이 아님 */
    firstPage: boolean;
}
