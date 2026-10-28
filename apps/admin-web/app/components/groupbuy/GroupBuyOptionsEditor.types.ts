/** 옵션 편집 한 줄 — 숫자 칸은 글자로 들고 저장할 때 바꾼다(`InputNumber` 는 범위 밖 값을 말없이 고친다) */
export interface GroupBuyOptionRow {
    /** 화면 안 줄 키(새 줄도 안정된 키) */
    key: string;
    /** 서버 옵션 ID(새 줄은 null) */
    id: string | null;
    name: string;
    extraPrice: string;
    /** 비우면 한도 없음 */
    maxQty: string;
    /** 모집 뒤 표시용(확정·선점) */
    currentQty: number;
    reservedQty: number;
}
