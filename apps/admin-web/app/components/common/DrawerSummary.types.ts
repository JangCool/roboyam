/** 서랍 요약 영역(`DrawerSummary`)의 칸 하나 */
export interface DrawerSummaryItem {
    key: string;
    label: string;
    /** 글자 값 — 칸 슬롯(`#item-<key>`)을 주면 그 슬롯이 대신 그린다 */
    value?: string;
    /** 강조 글자(`UtilBizApplication.EMPH.*`) 또는 흐림(`text-muted-color`) */
    valueClass?: string;
    /** 고정폭 글자(ID 등) */
    code?: boolean;
    /** 잘렸을 때 마우스를 올리면 보이는 전체 값 */
    title?: string;
    /** 한 줄 전체를 쓴다(긴 값) */
    wide?: boolean;
}
