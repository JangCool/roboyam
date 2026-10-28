import type { GroupBuyImage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

// 공구 등록 화면의 「옵션」(축·조합) · 「추가 구성상품」 편집 줄(결정 88). 숫자 칸은 글자로 들고 저장할 때 바꾼다(`InputNumber` 는 범위 밖 값을 말없이 고친다).
// 줄 키는 화면 안에서만 쓴다 — 불러온 줄은 서버 ID, 새 줄은 만든 때 키. 고시 양식의 적용 단품도 이 키로 든다(이름을 고쳐도 따라가게).

/** 축의 값 한 칸 */
export interface GroupBuyAxisValueRow {
    key: string;
    /** 서버 값 ID(새 값은 null) */
    id: string | null;
    name: string;
}

/** 옵션 축 한 줄(예 색상) */
export interface GroupBuyAxisRow {
    key: string;
    id: string | null;
    name: string;
    values: GroupBuyAxisValueRow[];
}

/** MAIN 단품 한 줄 — 축 값 조합 하나(축이 없으면 `valueKeys` 빈 줄 하나). 키는 `main:` + 값 키들(조합이 같으면 줄을 살린다) */
export interface GroupBuyMainSkuRow {
    key: string;
    id: string | null;
    /** 축 차례의 값 키 */
    valueKeys: string[];
    extraPrice: string;
    /** 비우면 한도 없음(공구 최대만) */
    maxQty: string;
    onSale: boolean;
    /** 모집 뒤 표시용(확정·선점) */
    currentQty: number;
    reservedQty: number;
}

/** 추가 구성상품(ADDON) 한 줄 */
export interface GroupBuyAddonRow {
    key: string;
    id: string | null;
    name: string;
    price: string;
    /** 화면은 필수로 받는다(추가 구성상품은 자기 한도만 센다) */
    maxQty: string;
    /** 저장 `imageKey` = 이미지 `mediaId` */
    image: GroupBuyImage | null;
    currentQty: number;
    reservedQty: number;
}

/** 고시 양식 적용 대상 고르기·저장 때 쓰는 단품 한 줄(편집 중 이름) */
export interface GroupBuySkuRef {
    key: string;
    kind: 'MAIN' | 'ADDON';
    /** 단품 이름(MAIN 은 「블랙 / M」, 축 없는 단품 하나는 빈 글자) */
    name: string;
    onSale: boolean;
}
