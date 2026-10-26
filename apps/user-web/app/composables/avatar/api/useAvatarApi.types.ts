/**
 * 프로필 아바타 API 가 주고받는 모양 — **서버 연결됨**. 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」 API 후보 「아바타」(모양은 community `docs/api-avatar.md` 를 로보얌에 맞춤).
 * 아바타 306줄(유료)은 품목 목록(`/api/point-shop/items`)에 섞지 않고 이 응답으로만 온다. 사기는 `POST /api/point-shop/purchases { itemId }` 그대로다.
 */

/** 카테고리 한 개 — 이름은 화면 문구 키(`avatar.category.<id>`, `UtilAvatar.categoryKey`) */
export interface AvatarCategory {
    id: string;
    /** 무료 카테고리(`basic`·`hobby`) */
    free: boolean;
    count: number;
}

/** 아바타 한 개(카탈로그 순서) */
export interface AvatarItem {
    /** 카탈로그 ID(카테고리 접두 포함 — `job-doctor-m`) */
    id: string;
    /** 카탈로그 한글 이름(영문 없음 — 영어 화면도 한글) */
    name: string;
    category: string;
    free: boolean;
    /** 유료만 — 지금 가격(P). 무료는 null */
    price: number | null;
    /** 유료만 — 포인트 상점 품목 ID(구매 요청에 쓴다). 무료는 null */
    itemId: string | null;
    /** 지금 살 수 있는가(품목 `active`) — 무료는 true */
    forSale: boolean;
    /** 가졌는가 — 무료는 늘 true, 유료는 환불하지 않은 보유가 있으면 */
    owned: boolean;
    /** 지금 내 프로필 아바타 */
    selected: boolean;
    /** 유료이고 안 썼고 환불 기한 안(서버 판정) — 고르면 환불할 수 없어 한 번 묻는다 */
    refundable: boolean;
    /** 그림 주소(`/avatars/roboyam/<id>.svg`) — `<img>` 로만 그린다 */
    url: string;
}

/** `GET /api/avatars` — 로그인했을 때만 `balance`·`owned`·`selected`·`refundable` 이 채워진다 */
export interface AvatarCatalog {
    balance: number;
    /** 지금 고른 아바타 ID — 고른 적 없으면 null(`basic-default` 로 그린다) */
    selectedId: string | null;
    refundDays: number;
    categories: AvatarCategory[];
    items: AvatarItem[];
}

/**
 * `PUT /api/me/profile/avatar` 요청 → `MyProfile`(`avatarUrl` 포함). 무료는 누구나(사업자 전용 계정 포함), 유료는 가진 것만. 같은 것을 다시 골라도 200.
 * 오류: `400 VALIDATION fields.avatarId` · `404 AVATAR_NOT_FOUND` · `409 AVATAR_NOT_OWNED` · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`.
 */
export interface AvatarSelectRequest {
    avatarId: string;
}
