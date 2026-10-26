/**
 * 포인트 상점 API 가 주고받는 모양 — **서버 연결됨**(server/api `pointshop`). 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」 API 후보(회원, api 서버)와 B절.
 * 응답은 공통 래퍼의 `data` 다.
 * ⚠ ID 는 문자열(Snowflake), 시각은 UTC ISO, 포인트는 정수.
 */

/** 품목 종류 — 1단계는 넷(결정 9·14). 아바타(`PROFILE_AVATAR`, 유료 306종·영구)는 품목 목록에 섞이지 않고 `GET /api/avatars` 로 온다. 화면은 모르는 종류가 와도 이름·가격은 그린다(미리보기만 없다) */
export type PointShopItemKind = 'NICKNAME_CHANGE_TICKET' | 'NICKNAME_COLOR' | 'PROFILE_BORDER' | 'PROFILE_AVATAR';

/** 사용 방식 — 영구(기본, 이미 가졌으면 다시 못 산다) · 소모형(쓰면 사라지고 여러 장 살 수 있다 — 변경권만) · 기간제(산 순간부터 `durationDays`일) */
export type PointShopUsageType = 'PERMANENT' | 'CONSUMABLE' | 'PERIOD';

/** 판매 상태(서버 계산) — 판매 중 · 판매 예정(`saleStartAt` 전) · 판매 끝(`saleEndAt` 지남·사용 안 함) · 품절(재고 0) */
export type PointShopSaleState = 'ON_SALE' | 'UPCOMING' | 'ENDED' | 'SOLD_OUT';

/** 프로필 테두리 팔레트 키 — 서버 CHECK 와 같은 목록(코드 고정). 그리는 법은 `UtilPointShop.BORDERS` */
export type ProfileBorderStyle = 'GOLD' | 'SILVER' | 'ROSE' | 'OCEAN' | 'FOREST' | 'NIGHT' | 'RAINBOW';

/**
 * 작성자 장식 — 글·댓글·목록·쪽지 상대(`PostAuthor.decoration`)와 공개 프로필(`PublicProfile.decoration`)에 붙는 칸(서버 후보).
 * 만료된 장착은 서버가 null 로 준다. 닉네임 색은 원색 `#RRGGBB`(그릴 때 `UtilColor.cssPair` 로 대비 보정), 테두리는 팔레트 키.
 */
export interface AuthorDecoration {
    nicknameColor: string | null;
    profileBorder: ProfileBorderStyle | string | null;
}

/** 이 품목에 대한 내 보유 상태 */
export interface PointShopItemMine {
    /** 영구 아이템을 가지고 있다(환불하지 않은 것) — 다시 살 수 없다 */
    owned: boolean;
    /** 안 쓴 소모형 장수(변경권) */
    count: number;
    /** 기간제 — 지금 가진 기간의 끝(또 사면 이 끝에서 이어 붙는다). 없으면 null */
    periodEndsAt: string | null;
}

/** 품목 한 개 — `GET /api/point-shop/items` 의 한 줄이자 `GET /api/point-shop/items/{id}` 의 응답 */
export interface PointShopItemView {
    id: string;
    /** 바뀌지 않는 코드(예 `nickname-change`) */
    code: string;
    kind: PointShopItemKind | string;
    usageType: PointShopUsageType;
    name: string;
    nameEn: string | null;
    description: string;
    descriptionEn: string | null;
    /** 닉네임 색만 — `#RRGGBB` */
    color: string | null;
    /** 프로필 테두리만 — 팔레트 키 */
    borderStyle: ProfileBorderStyle | string | null;
    /** 프로필 아바타만 — 카탈로그 ID(`avatar_id`) */
    avatarId: string | null;
    /** 프로필 아바타만 — 그림 주소(`<img>` 로만) */
    avatarUrl: string | null;
    /** 가격(P) — 서버가 구매 때 품목에서 다시 읽는다 */
    price: number;
    /** 기간제만(1~365) */
    durationDays: number | null;
    /** 남은 재고 — null 이면 무제한 */
    stock: number | null;
    saleStartAt: string | null;
    saleEndAt: string | null;
    saleState: PointShopSaleState;
    mine: PointShopItemMine;
}

/** `GET /api/point-shop/items?kind=` — 판매 끝난 품목은 오지 않는다(상세 주소로는 열린다) */
export interface PointShopItemList {
    /** 환불할 수 있는 날 수(포인트 상점 설정 `refund_days`, 기본 7) */
    refundDays: number;
    /** 지금 내 포인트(충전을 열기 전이라 하나 — D13) */
    balance: number;
    items: PointShopItemView[];
}

/** 목록 거르기 — 빈 값은 보내지 않는다(전체) */
export interface PointShopItemQuery {
    kind?: PointShopItemKind;
}

/** `POST /api/point-shop/purchases` 요청 — 고지 동의가 없으면 `400 VALIDATION fields.agreeRefundNotice`. 헤더 `Idempotency-Key` 필수 */
export interface PointShopPurchaseRequest {
    itemId: string;
    agreeRefundNotice: boolean;
}

/**
 * `POST /api/point-shop/purchases` 응답.
 * 오류: `401` · `403 EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`·`MEMBER_RESTRICTED`(POINT_FREEZE — 본문 `restriction`)·`PERMISSION_DENIED`(`pointshop.use`) ·
 * `409 POINT_SHOP_NOT_ON_SALE`·`POINT_SHOP_SOLD_OUT`·`POINT_SHOP_ALREADY_OWNED`·`POINT_INSUFFICIENT` · `400 VALIDATION fields.agreeRefundNotice` · `429 REQUEST_RATE_LIMITED`(`Retry-After`) ·
 * 멱등 키 `400 IDEMPOTENCY_KEY_REQUIRED`·`IDEMPOTENCY_KEY_INVALID` · `409 IDEMPOTENCY_KEY_REUSED`·`IDEMPOTENCY_IN_PROGRESS`. 같은 키로 다시 오면 처음 결과(머리글 `Idempotent-Replayed: true`).
 */
export interface PointShopPurchaseResult {
    purchaseId: string;
    userItemId: string;
    /** 산 뒤 내 포인트 */
    balance: number;
    /** 산 뒤의 품목(재고·내 보유 상태가 바뀐 것) */
    item: PointShopItemView;
}
