/** 광고 서랍 요약 영역(`AdDrawerSummary`)의 칸 하나 */
export interface AdDrawerSummaryItem {
    key: string;
    label: string;
    value: string;
    /** 강조 글자(`UtilBizApplication.EMPH.*`) 또는 흐림(`text-muted-color`) */
    valueClass?: string;
    /** 고정폭 글자(ID 등) */
    code?: boolean;
    /** 잘렸을 때 마우스를 올리면 보이는 전체 값 */
    title?: string;
}
