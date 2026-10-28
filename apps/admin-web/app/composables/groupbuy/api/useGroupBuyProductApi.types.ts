/**
 * 공구 상품·카테고리 API 의 요청·응답 모양(결정 89 — 상품과 공구를 나눈다). 2b-1 화면 목업이 제안하는 모양이고 서버는 2b-2 가 정한다(확정 전 가정).
 * 상품 = 이름·요약·사양·설명·이미지·정상가 근거·청약철회 제한·카테고리·고시 양식·옵션 축/값·상품 단품(조합·기본 공급가·판매 여부).
 * 공구는 상품 하나를 골라 판매 조건과 단품별 설정(추가금·한도·판매 여부·공급가)을 더한다. 기본 공급가는 가격 권한이 있을 때만 서버가 준다(없으면 null).
 */
import type { GroupBuyAxisInput, GroupBuyImage, GroupBuyNotice, GroupBuyNoticeInput, GroupBuyOptionAxis, GroupBuyStatus, NormalPriceBasis } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

/** 카테고리 — 2단(위 카테고리 `parentId` null), 차례·숨김. 상품마다 하나 필수(아래 단만 고른다 — 아래 단이 없는 위 카테고리는 그대로) */
export interface GroupBuyCategory {
    id: string;
    parentId: string | null;
    name: string;
    sortOrder: number;
    /** 숨긴 카테고리 — 회원 목록 필터에 안 보인다(상품은 그대로 팔린다) */
    hidden: boolean;
    /** 이 카테고리(아래 단 포함)의 상품 수 — 상품이 있으면 지우지 못한다 */
    productCount: number;
}

/** `PUT /api/admin/group-buys/categories` — 목록 통째로(차례 = 배열 차례, 새 줄은 id null + `tempKey` 로 부모를 가리킨다) */
export interface GroupBuyCategoryInput {
    id: string | null;
    tempKey: string;
    /** 위 카테고리의 id 또는 tempKey(위 단이면 null) */
    parent: string | null;
    name: string;
    hidden: boolean;
}

/** 상품 단품 — 축 값 조합마다 하나(축이 없으면 하나, 이름 = 상품 이름) */
export interface GroupBuyProductSku {
    skuId: string;
    valueIds: string[];
    name: string;
    /** 기본 공급가(선택) — 공구 단품으로 복사해 고친다. 가격 권한이 없으면 null */
    baseSupplyPrice: number | null;
    onSale: boolean;
}

/** 이 상품을 쓰는 공구 */
export interface GroupBuyProductUse {
    id: string;
    title: string;
    status: GroupBuyStatus;
    roundNo: number;
}

export interface GroupBuyProductRow {
    id: string;
    name: string;
    categoryId: string;
    categoryPath: string;
    thumbUrl: string | null;
    skuCount: number;
    /** 쓰는 공구 수 · 그중 모집 중(모집 중이면 옵션 구성 잠금) */
    groupBuyCount: number;
    recruitingCount: number;
    updatedAt: string;
}

export interface GroupBuyProductQuery {
    q?: string;
    categoryId?: string;
    page: number;
    size: number;
}

export interface GroupBuyProductDetail {
    id: string;
    name: string;
    categoryId: string;
    categoryPath: string;
    summary: string;
    spec: string;
    /** 서버가 정화한 HTML */
    description: string;
    images: GroupBuyImage[];
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    withdrawalRestriction: string;
    axes: GroupBuyOptionAxis[];
    skus: GroupBuyProductSku[];
    /** 고시 양식(결정 87 — 상품에 붙는다). 적용 단품 `skuNames` 는 이 상품의 단품 이름 */
    notices: GroupBuyNotice[];
    /** 모집 중 공구가 이 상품을 쓰면 옵션 축·단품 구성 잠금(새 단품은 다음 공구부터) */
    structureLocked: boolean;
    groupBuys: GroupBuyProductUse[];
    version: number;
    updatedAt: string;
}

/** 상품 단품 저장 줄 — 축 차례의 값 이름(적지 않은 조합 = 판매·공급가 없음) */
export interface GroupBuyProductSkuInput {
    values: string[];
    baseSupplyPrice: number | null;
    onSale: boolean;
}

/** `POST /api/admin/group-buy-products` · `PUT /{id}` — 오류 칸 `name`·`categoryId`·`axes`·`skus`·`notices[i]…`, 구성 잠금이면 `409 GROUP_BUY_PRODUCT_STRUCTURE_LOCKED` */
export interface GroupBuyProductSaveRequest {
    name: string;
    categoryId: string | null;
    summary: string;
    spec: string;
    description: string;
    imageIds: string[];
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    withdrawalRestriction: string;
    axes: GroupBuyAxisInput[];
    skus: GroupBuyProductSkuInput[];
    notices: GroupBuyNoticeInput[];
    version: number | null;
}

/** 공구의 추가 구성상품으로 고를 수 있는 다른 상품의 단품 */
export interface GroupBuyAddonSource {
    productSkuId: string;
    productId: string;
    productName: string;
    /** 단품 이름(축 없는 상품이면 상품 이름) */
    name: string;
    imageUrl: string | null;
    baseSupplyPrice: number | null;
}
