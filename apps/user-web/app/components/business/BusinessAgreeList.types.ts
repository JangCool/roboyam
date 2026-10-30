/** 동의 항목 하나(`BusinessAgreeList`) — 사업자 신청·광고 신청·핫딜 신청 화면이 만든다 */
export interface BusinessAgreeItem {
    key: string;
    label: string;
    detail: string;
    /** 동의한 버전(서버에 저장 — H4) */
    version?: string;
}
