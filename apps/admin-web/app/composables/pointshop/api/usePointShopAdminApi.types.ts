/**
 * 포인트 상점 관리자 API 가 주고받는 모양 — 서버 DTO(server/admin `pointshop.dto`) 와 이름·모양이 같다.
 * 정책 원천: `docs/domain/commerce.md` 7절, 계약 `docs/features/commerce-stage1-contract.md` B절 · 「1-2 화면 목업」. 시각은 UTC ISO 다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 품목 종류(결정 9·14) — 1단계는 넷. 아바타(`PROFILE_AVATAR`)는 V86 이 카탈로그로 시드하고 관리자가 새로 만들거나 지울 수 없다 */
export type PointShopItemKind = 'NICKNAME_CHANGE_TICKET' | 'NICKNAME_COLOR' | 'PROFILE_BORDER' | 'PROFILE_AVATAR';

/** 고정 차례(변경권 · 닉네임 색 · 프로필 테두리 · 아바타) — 구매 내역 거르기 차례. 타입에 딸린 상수 */
export const POINT_SHOP_ITEM_KINDS: readonly PointShopItemKind[] = ['NICKNAME_CHANGE_TICKET', 'NICKNAME_COLOR', 'PROFILE_BORDER', 'PROFILE_AVATAR'];

/** 관리자가 [품목 추가]로 만들 수 있는 종류(아바타 제외) — 「품목」 탭 거르기·종류 고르기 차례 */
export const POINT_SHOP_CREATABLE_KINDS: readonly PointShopItemKind[] = ['NICKNAME_CHANGE_TICKET', 'NICKNAME_COLOR', 'PROFILE_BORDER'];

/** 사용 방식 — 영구(기본) · 소모형(쓰면 사라짐, 여러 장) · 기간제(산 순간부터 N일) */
export type PointShopUsageType = 'PERMANENT' | 'CONSUMABLE' | 'PERIOD';

/** 프로필 테두리 팔레트 키 — 서버 CHECK 와 같은 목록(코드 고정). 모양은 `utils/util-point-shop.ts` */
export type PointShopBorderStyle = 'GOLD' | 'SILVER' | 'ROSE' | 'OCEAN' | 'FOREST' | 'NIGHT' | 'RAINBOW';

/** 고정 차례 — 테두리 고르개 차례 */
export const POINT_SHOP_BORDER_STYLES: readonly PointShopBorderStyle[] = ['GOLD', 'SILVER', 'ROSE', 'OCEAN', 'FOREST', 'NIGHT', 'RAINBOW'];

/** 판매 상태(그릴 때 계산 — `UtilPointShop.saleState`) */
export type PointShopSaleState = 'ON_SALE' | 'UPCOMING' | 'ENDED' | 'SOLD_OUT';

/** 품목 한 줄 — `point_shop_items` 칸 전부 + 판 수(`soldCount`) */
export interface PointShopAdminItem {
    id: string;
    /** 유일·불변(영소문자·숫자·하이픈 3~40) */
    code: string;
    /** 만든 뒤 바꿀 수 없다 */
    kind: PointShopItemKind;
    usageType: PointShopUsageType;
    name: string;
    nameEn: string | null;
    description: string;
    descriptionEn: string | null;
    /** `#RRGGBB` — 닉네임 색만(그 밖은 null) */
    color: string | null;
    /** 팔레트 키 — 프로필 테두리만(그 밖은 null) */
    borderStyle: PointShopBorderStyle | null;
    /** 아바타 카탈로그 ID(코드 `avatar-<id>` 의 `<id>`, 그림 `/avatars/roboyam/<id>.svg`) — 아바타만(그 밖은 null), 유일·불변 */
    avatarId: string | null;
    /** 아바타 카탈로그 카테고리 ID(`job` …) — 아바타만(그 밖은 null). 이름은 화면 i18n 키 `pointShop.avatarCategory.<id>` */
    category: string | null;
    /** 1~100,000 */
    pricePoint: number;
    /** 기간제만 1~365(그 밖은 null) */
    durationDays: number | null;
    /** 남은 수. null = 무제한 */
    stock: number | null;
    saleStartAt: string | null;
    saleEndAt: string | null;
    active: boolean;
    sortOrder: number;
    /** 판 수(환불된 구매는 빼고 센다) — 「이미 산 사람」 경고와 재고 칸에 쓴다 */
    soldCount: number;
    createdAt: string;
    /** 만든 운영자 이름 — 시스템(마이그레이션)이면 null */
    createdBy: string | null;
    updatedAt: string;
    updatedBy: string | null;
}

/** `GET /api/admin/point-shop/items?kind=` 응답 — 순서(`sortOrder`) → id 차례 */
export interface PointShopAdminItemList {
    items: PointShopAdminItem[];
}

/** `GET` 목록 조건 — 아바타가 응답 대부분이라 「품목」 탭은 `excludeKind=PROFILE_AVATAR`, 「아바타」 탭은 `kind=PROFILE_AVATAR` 로 따로 받는다. 모르는 이름은 `400 fields.kind|excludeKind` */
export interface PointShopAdminItemQuery {
    kind?: PointShopItemKind;
    excludeKind?: PointShopItemKind;
}

/**
 * `POST /api/admin/point-shop/items` · `PUT /api/admin/point-shop/items/{id}` 요청 — 칸 전부.
 * 고칠 때도 `code`·`kind` 를 그대로 싣는다(읽기 전용 칸도 제출에 포함 — `forms.md` 2-1). 서버는 값이 바뀌었으면 `400 fields.code|kind` 로 거절한다.
 * 종류에 맞지 않는 칸(변경권의 색 등)은 null 로 보낸다.
 */
export interface PointShopItemSaveRequest {
    code: string;
    kind: PointShopItemKind;
    usageType: PointShopUsageType;
    name: string;
    nameEn: string | null;
    description: string;
    descriptionEn: string | null;
    color: string | null;
    borderStyle: PointShopBorderStyle | null;
    /** 아바타만(읽기 전용 — 그대로 싣는다, 바꾸면 `400 fields.avatarId`). 그 밖은 null */
    avatarId: string | null;
    pricePoint: number;
    durationDays: number | null;
    stock: number | null;
    saleStartAt: string | null;
    saleEndAt: string | null;
    active: boolean;
    sortOrder: number;
}

/** 저장 칸 이름 — 서버 `fields.<칸>` 키와 같다 */
export type PointShopItemField = keyof PointShopItemSaveRequest;

// ── 구매 내역 ─

export type PointShopPurchaseStatus = 'COMPLETED' | 'REFUNDED';

/** 셀프 환불이 안 되는 이유 — 사용함(`first_used_at` 있음) · 기한 지남 */
export type PointShopSelfRefundBlock = 'USED' | 'EXPIRED';

/** 구매한 회원 */
export interface PointShopPurchaseMember {
    /** 탈퇴 회원이면 null */
    id: string | null;
    nickname: string;
    withdrawn: boolean;
}

/** 셀프 환불 판정(서버 계산 — 완료 줄만). 기한은 **지금** 설정의 환불 기한으로 계산한다(가정 — 계약 미확정) */
export interface PointShopSelfRefund {
    allowed: boolean;
    /** 셀프 환불 기한(구매 시각 + 환불 기한 일수) */
    until: string;
    /** 안 되는 이유. 되면 null */
    blockedBy: PointShopSelfRefundBlock | null;
}

/** 환불 기록(환불된 줄만) */
export interface PointShopPurchaseRefund {
    /** 회원 셀프 환불 · 운영자 예외 환불 · 정지 중 환불 신청을 운영자가 수락(`REQUEST`) */
    kind: 'SELF' | 'EXCEPTION' | 'REQUEST';
    refundedAt: string;
    /** 처리자(운영자 이름) — 예외 환불이면 환불한 운영자, 신청 수락이면 수락한 운영자. 셀프는 null */
    operatorName: string | null;
    /** 예외 환불 사유. 셀프는 null */
    reason: string | null;
    /** 돌려준 포인트 */
    refundedPoints: number;
}

/** 구매 한 줄 — 품목 칸은 **산 때의 사본**(이름·코드·가격·기간) */
export interface PointShopPurchaseRow {
    id: string;
    createdAt: string;
    member: PointShopPurchaseMember;
    itemId: string;
    itemCode: string;
    itemName: string;
    /** 아바타 구매면 카탈로그 ID(썸네일), 그 밖은 null */
    avatarId: string | null;
    kind: PointShopItemKind;
    usageType: PointShopUsageType;
    pricePoint: number;
    durationDays: number | null;
    status: PointShopPurchaseStatus;
    /** 처음 장착(꾸미기)·사용(변경권)·프로필로 고른(아바타) 시각 — 없으면 아직 안 씀 */
    firstUsedAt: string | null;
    /** 완료 줄만. 환불된 줄은 null */
    selfRefund: PointShopSelfRefund | null;
    /** 환불된 줄만. 완료 줄은 null */
    refund: PointShopPurchaseRefund | null;
    /** 정지 중 환불 신청(계약 「1-2 정지 중 환불 신청」) — 신청이 없으면 null */
    refundRequest: PointShopPurchaseRefundRequest | null;
}

/** 환불 신청 상태 — 대기 · 수락(환불됨) · 거절 */
export type PointShopRefundRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 고정 차례 — 거르기 차례. 타입에 딸린 상수 */
export const POINT_SHOP_REFUND_REQUEST_STATUSES: readonly PointShopRefundRequestStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];

/** 구매 줄의 환불 신청 — 조건(안 씀 + 기한 안)은 **신청 순간**에 판정해 고정된다(기다리는 동안 기한이 지나도 수락하면 환불된다) */
export interface PointShopPurchaseRefundRequest {
    id: string;
    status: PointShopRefundRequestStatus;
    requestedAt: string;
    /** 수락·거절한 시각. 대기면 null */
    decidedAt: string | null;
    /** 거절 사유(회원에게 알림으로 간다). 거절이 아니면 null */
    rejectReason: string | null;
}

/** `GET /api/admin/point-shop/purchases?from&to&status&kind&refundRequest&q&page&size` 조건 — 구매 번호 내림차순 */
export interface PointShopPurchaseQuery {
    from?: string;
    to?: string;
    status?: PointShopPurchaseStatus;
    kind?: PointShopItemKind;
    /** 그 상태의 환불 신청이 있는 구매만(모르는 값은 `400 fields.refundRequest`) */
    refundRequest?: PointShopRefundRequestStatus;
    /** 닉네임 또는 회원번호 */
    q?: string;
    page: number;
    size: number;
}

export interface PointShopPurchasePage {
    items: PointShopPurchaseRow[];
    total: number;
    page: number;
    size: number;
}

/** `POST /api/admin/point-shop/purchases/{id}/refund` 요청 — 사유 5~200자(감사 — 돈 관련 5년) */
export interface PointShopRefundRequest {
    reason: string;
}

/** 예외 환불 결과 */
export interface PointShopRefundResult {
    purchaseId: string;
    refundedPoints: number;
}

/** `POST /api/admin/point-shop/refund-requests/{id}/reject` 요청 — 사유 5~200자(`400 fields.reason`, 회원 알림에 실린다) */
export interface PointShopRefundRejectRequest {
    reason: string;
}

/** 환불 신청 수락·거절 결과 — 거절이면 `refundedPoints` 0 */
export interface PointShopRefundRequestDecision {
    requestId: string;
    purchaseId: string;
    status: PointShopRefundRequestStatus;
    refundedPoints: number;
    decidedAt: string;
}

// ── 설정 ─

/** `GET`·`PUT /api/admin/point-shop/settings` 응답 */
export interface PointShopSettingsView {
    /** 셀프 환불 기한 일수(1~30) */
    refundDays: number;
    /** 처음 값(7) — 「기본값 N」 표시 */
    defaultRefundDays: number;
    /** 마지막 저장 시각. 저장한 적 없으면 null */
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/point-shop/settings` 요청 */
export interface PointShopSettingsSaveRequest {
    refundDays: number;
}

// ── 아바타(결정 14 — 묶음 가격·판매) ─

/** 카테고리 하나의 요약 — 무료 카테고리(`basic`·`hobby`)는 품목이 없어 판매 수 0 · 가격 범위 null */
export interface PointShopAvatarCategorySummary {
    /** 카탈로그 카테고리 ID — 이름은 화면 i18n 키 `pointShop.avatarCategory.<id>` */
    category: string;
    free: boolean;
    /** 카탈로그의 아바타 수 */
    total: number;
    /** 판매 중 품목 수 */
    forSale: number;
    /** total − forSale(판매 중단 + 품목 행이 없는 것). 무료는 0 */
    stopped: number;
    minPrice: number | null;
    maxPrice: number | null;
}

/** `GET /api/admin/point-shop/avatars/summary` 응답 — 카테고리는 카탈로그 차례(무료 2 + 유료 12) */
export interface PointShopAvatarSummary {
    categories: PointShopAvatarCategorySummary[];
    totals: { total: number; freeCount: number; paidCount: number; forSale: number; stopped: number; minPrice: number | null; maxPrice: number | null };
}

/** 묶음 범위 — 유료 전체 · 고른 카테고리 · 고른 품목 */
export type PointShopAvatarScope = 'ALL' | 'CATEGORIES' | 'ITEMS';

/** 범위 칸 — `CATEGORIES` 면 `categories`(유료 카테고리 1개 이상), `ITEMS` 면 `itemIds`(아바타 품목 1~400개)만 보낸다(다른 범위에 같이 보내거나 아바타 아닌 품목이 섞이면 `400 fields.categories|itemIds`, 없는 품목 번호면 `404 POINT_SHOP_ITEM_NOT_FOUND`) */
export interface PointShopAvatarTarget {
    scope: PointShopAvatarScope;
    categories?: string[];
    itemIds?: string[];
}

/** `PUT /api/admin/point-shop/avatars/price` 요청 — 가격 1~100,000 */
export interface PointShopAvatarPriceRequest extends PointShopAvatarTarget {
    price: number;
}

/** `POST /api/admin/point-shop/avatars/sale` 요청 — 판매 켜기(true)·끄기(false) */
export interface PointShopAvatarSaleRequest extends PointShopAvatarTarget {
    active: boolean;
}

/** 묶음 변경 결과 — matched = 범위에 든 품목 수, changed = 값이 실제로 바뀐 줄 수(같은 값이면 0) */
export interface PointShopAvatarBulkResult {
    scope: PointShopAvatarScope;
    matched: number;
    changed: number;
    /** 가격 요청이면 새 가격, 판매 요청이면 null */
    price: number | null;
    /** 판매 요청이면 새 값, 가격 요청이면 null */
    active: boolean | null;
}
