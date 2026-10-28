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

/** 공구 MAIN 단품 한 줄 — 상품 단품 하나(키 = `main:` + 상품 단품 ID). 이름은 상품 단품 이름(읽기 전용) */
export interface GroupBuyMainSkuRow {
    key: string;
    id: string | null;
    /** 상품 단품 이름(옵션 없는 단품 하나면 빈 글자 — 상품 이름으로 보인다) */
    name: string;
    /** 상품 단품(결정 89 — 공구는 상품 단품마다 한 줄, 상품 화면에서 새로 만든 조합은 null) */
    productSkuId: string | null;
    /** 축 차례의 값 키 */
    valueKeys: string[];
    extraPrice: string;
    /** 비우면 한도 없음(공구 최대만) */
    maxQty: string;
    onSale: boolean;
    /** 공급가(결정 89 — 상품 화면은 기본 공급가). 빈 글자 = 아직 없음(모집 시작 때 409) */
    supplyPrice: string;
    /** 모집 뒤 표시용(확정·선점) */
    currentQty: number;
    reservedQty: number;
}

/** 상품 단품 한 줄(상품 화면 — 사용자 결정 2026-10-28: 단품 목록이 정본, 옵션 이름은 속성) */
export interface GroupBuyProductSkuRow {
    key: string;
    /** 서버가 자동으로 붙인 단품 ID(새 줄은 null — 저장하면 생긴다) */
    id: string | null;
    /** 직접 적은 이름 — 비우면 옵션 값으로(「블랙 / M」) */
    name: string;
    /** 옵션 종류 차례의 값(옵션이 없으면 빈 배열) */
    values: string[];
    onSale: boolean;
    /** 기본 공급가 글자(빈 글자 = 없음) */
    supplyPrice: string;
}

/** 추가 구성상품(ADDON) 한 줄 */
export interface GroupBuyAddonRow {
    key: string;
    id: string | null;
    /** 고른 다른 상품의 단품(결정 89) — 이름·이미지는 그 상품 것 */
    productSkuId: string | null;
    name: string;
    price: string;
    supplyPrice: string;
    /** 비우면 한도 없음(추가 구성상품은 자기 한도만 센다) */
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
