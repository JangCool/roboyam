/** 상품 상세 탭 하나 — `GroupBuyDetailTabs` */
export interface GroupBuyDetailTab {
    /** 구역 요소의 id(주소 해시) */
    id: string;
    label: string;
    /** 탭 옆 (수) — 모르면 null */
    count?: number | null;
}
