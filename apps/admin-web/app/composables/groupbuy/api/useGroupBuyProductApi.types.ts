/**
 * 공구 상품·카테고리 API 의 요청·응답 모양(결정 89 — 상품과 공구를 나눈다). 2b-1 화면 목업이 제안하는 모양이고 서버는 2b-2 가 정한다(확정 전 가정).
 * 상품 = 이름·요약·사양·설명·이미지·정상가 근거·청약철회 제한·카테고리·고시 양식·옵션 축/값·상품 단품(조합·기본 공급가·판매 여부).
 * 공구는 상품 하나를 골라 판매 조건과 단품별 설정(추가금·한도·판매 여부·공급가)을 더한다. 기본 공급가는 가격 권한이 있을 때만 서버가 준다(없으면 null).
 */
import type { GroupBuyImage, GroupBuyNotice, GroupBuyNoticeInput, GroupBuyOptionAxis, GroupBuyStatus, NormalPriceBasis } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

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

/**
 * 상품 단품 — 운영자가 직접 등록한 아이템 한 줄(사용자 결정 2026-10-28 — 축·값 조합 자동 생성을 대신한다). 단품이 하나면 옵션 없는 상품.
 * `values` 는 상품 `optionNames` 차례의 옵션 값, `valueIds` 는 서버가 그 값으로 만든 축 값 ID(회원 상세의 차례 고르기 · 공구 화면이 쓴다).
 */
export interface GroupBuyProductSku {
    skuId: string;
    /** 옵션 값(옵션 이름이 없으면 빈 배열) */
    values: string[];
    valueIds: string[];
    name: string;
    /** 기본 공급가(선택) — 공구 단품으로 복사해 고친다. 가격 권한이 없으면 null */
    defaultSupplyPrice: number | null;
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
    /** 정상가(원) — 모집 중 공구가 쓰면 잠금 */
    normalPrice: number;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    withdrawalRestriction: string;
    /** 옵션 이름(속성, 0~3 — 예 색상·사이즈). 비면 회원은 단품 목록 하나에서 고른다 */
    optionNames: string[];
    /** 서버가 단품의 옵션 값으로 만든 축(읽기 전용 — 공구 화면·회원 상세가 쓴다) */
    axes: GroupBuyOptionAxis[];
    skus: GroupBuyProductSku[];
    /** 고시 양식(결정 87 — 상품에 붙는다). 적용 단품 `skuNames` 는 이 상품의 단품 이름 */
    notices: GroupBuyNotice[];
    /** 모집 중 공구가 이 상품을 쓰면 이름·정상가·청약철회 제한·옵션 구성 잠금(`409 GROUP_BUY_LOCKED_FIELD` field `name|normalPrice|withdrawalRestriction|options`). 설명·이미지·카테고리·고시는 언제든 고친다 */
    structureLocked: boolean;
    groupBuys: GroupBuyProductUse[];
    version: number;
    updatedAt: string;
}

/** 상품 단품 저장 줄 — 파는 것만 등록한다. 같은 이름 · 같은 옵션 값 묶음 두 줄은 `400 skus` */
export interface GroupBuyProductSkuInput {
    /** 기존 단품이면 그 ID(새 줄 null) — 공구가 쓰는 단품을 빼면 `409 GROUP_BUY_PRODUCT_SKU_IN_USE` */
    skuId: string | null;
    /** 이름(1~100) — 옵션 값이 있으면 화면이 「블랙 / M」으로 채워 보낸다 */
    name: string;
    /** `optionNames` 차례의 값(옵션 이름이 있으면 모두 필수) */
    values: string[];
    defaultSupplyPrice: number | null;
    onSale: boolean;
}

/**
 * `POST /api/admin/group-buy-products` · `PUT /{id}`(서버 2b-2 `ProductDraft`) — 오류 칸 `name`·`categoryId`·`axes`·`skus`·`notices[i]…`.
 * 모집 중 공구가 쓰면 `409 GROUP_BUY_LOCKED_FIELD`(`name|normalPrice|withdrawalRestriction|options`), 공구가 쓰는 단품을 빼면 `409 GROUP_BUY_PRODUCT_SKU_IN_USE {skuNames}`.
 * 고시 양식(`notices`)도 상품 단위로 저장한다 — 2b-2 가 같은 요청인지 따로인지 확정 전(목업은 같은 요청).
 */
export interface GroupBuyProductSaveRequest {
    categoryId: string | null;
    name: string;
    summary: string;
    spec: string;
    description: string;
    normalPrice: number;
    normalPriceBasis: NormalPriceBasis | null;
    normalPriceSource: string;
    withdrawalRestriction: string;
    /** 옵션 이름(0~3) — 사용자 결정 2026-10-28(2b-2 의 `axes[{name, values}]` 를 대신, 확정 전 제안) */
    optionNames: string[];
    skus: GroupBuyProductSkuInput[];
    /** 대표 이미지 `mediaId`(차례대로) */
    images: string[];
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
    defaultSupplyPrice: number | null;
}
