import type { PaymentCheckout } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 로보티콘(코드 이름 emoticon) 공개 API 의 모양(`useEmoticonApi`) — 계약 `docs/features/emoticon-contract.md` 「API 후보」. 서버 DTO 이름은 서버 단계에서 맞춘다.
 * 본문에는 토큰 `:emo-{이모티콘ID}:` 만 저장하고(가정 7) 화면이 카탈로그로 그림을 그린다.
 */

/** 팩 출처 — 운영(로보얌콘·로보얌콘핑크) · 회원 작가 */
export type EmoticonPackSource = 'OFFICIAL' | 'CREATOR';

/** 상점 정렬 — 인기(받은 수) · 최신(승인일) */
export type EmoticonPackSort = 'POPULAR' | 'LATEST';

/** 이모티콘 한 개 — 카탈로그·팩 상세·내 팩이 같이 쓴다 */
export interface EmoticonItem {
    /** 토큰에 들어가는 ID(숫자 글자, 1~19자리) */
    id: string;
    /** 이름표(접근성 `alt`, 1~10자) */
    label: string;
    url: string;
    /** 움직이는 GIF */
    animated: boolean;
}

/** `GET /api/emoticons/catalog` 의 한 줄 — 모든 팩(판매 중이 아니어도)의 모든 이모티콘. 로그인 불필요·캐시 */
export interface EmoticonCatalogItem extends EmoticonItem {
    packId: string;
}

export interface EmoticonCatalog {
    items: EmoticonCatalogItem[];
}

/** `useEmoticonApi().catalog(etag)` 의 결과 — 화면 쪽 모양(서버 응답이 아니다). `catalog: null` = `304`(가진 사본이 그대로 맞다) */
export interface EmoticonCatalogFetch {
    catalog: EmoticonCatalog | null;
    /** 응답의 `ETag`(다음 조건부 요청에 쓴다) — 서버가 주지 않았으면 null */
    etag: string | null;
}

/** 작가 — 운영 팩은 null */
export interface EmoticonCreator {
    memberId: string;
    nickname: string;
}

/** 상점 목록의 팩 한 장 */
export interface EmoticonPackCard {
    id: string;
    name: string;
    source: EmoticonPackSource;
    creator: EmoticonCreator | null;
    /** 대표 그림(200×200 PNG) */
    coverUrl: string;
    /** 이모티콘 수 */
    count: number;
    /** 가격(원) — 0 이면 무료(「받기」), 아니면 실결제(운영자가 정한 가격 단계 중 하나). 포인트로는 사지 않는다. 판매자는 사이트다 */
    price: number;
    /** 받은(무료)·산(유료) 사람 수 */
    acquireCount: number;
    /** 내가 가졌다 — 받았거나 샀다(로그인하지 않았으면 false) */
    acquired: boolean;
    /** 상점에 나온 시각(승인 — 최신순 기준) */
    publishedAt: string;
}

/** 가격 거르기 — 무료 · 유료 */
export type EmoticonPriceFilter = 'FREE' | 'PAID';

/** `GET /api/emoticons/packs?source&price&sort&q&cursor` */
export interface EmoticonPackQuery {
    source?: EmoticonPackSource;
    price?: EmoticonPriceFilter;
    sort?: EmoticonPackSort;
    q?: string;
    cursor?: string;
}

export interface EmoticonPackList {
    items: EmoticonPackCard[];
    /** 다음 쪽 — 없으면 null */
    nextCursor: string | null;
}

/** 팩 판매 상태(상점 기준) — 판매 중 · 운영자 판매 중단(이미 받은 사람은 계속 쓴다) */
export type EmoticonPackSaleState = 'ON_SALE' | 'SUSPENDED';

/** 판매 중단의 종류 — 운영자 판단 · 작가 탈퇴(새 판매만 멈춤 — 후속 결정 6) */
export type EmoticonPackSuspendKind = 'OPERATOR' | 'CREATOR_WITHDRAWN';

/** `GET /api/emoticons/packs/{id}` — 없는 팩·심사 전 팩(남의 것) `404 EMOTICON_PACK_NOT_FOUND` */
export interface EmoticonPackDetail extends EmoticonPackCard {
    description: string;
    tags: string[];
    saleState: EmoticonPackSaleState;
    /** 판매 중단의 종류 — 판매 중이면 null. 판매 중단 팩 상세는 가진 사람(과 작가)에게만 보인다 */
    suspendKind: EmoticonPackSuspendKind | null;
    emoticons: EmoticonItem[];
    /**
     * 보는 사람이 힘이 살아 있는 운영자 회원 계정이고 판매 중 팩이다(서버 `EmoticonPublicView.PackDetail.operatorUsable`) — 가진 팩이어도 true.
     * true 면 구매·받기 대신 「운영자 계정은 판매 중인 모든 팩을 쓸 수 있어요」. `acquired` 는 바뀌지 않는다(정말 가졌는가). 판매 중단 팩은 일반 규칙(가지지 않았으면 404)
     */
    operatorUsable: boolean;
}

/**
 * `POST /api/emoticons/packs/{id}/acquire`(무료 팩만) — 이미 받았으면 같은 결과(멱등). 판매 중단 `409 EMOTICON_PACK_SUSPENDED` · 유료 팩 `409 EMOTICON_PACK_PAID` · 로그인 필요(401).
 * 운영자 회원 계정은 200 이지만 아무것도 바뀌지 않는다 — `acquired` 는 「정말 가졌는가」라 false 일 수 있다(「받음」으로 바꾸지 않는다)
 */
export interface EmoticonAcquireResult {
    packId: string;
    acquired: boolean;
    acquireCount: number;
}

/** `POST /api/emoticons/packs/{id}/purchase`(유료 팩) 요청 — 환불 규칙 고지 동의(commerce.md 7-3 과 같다) */
export interface EmoticonPurchaseRequest {
    agreeRefundNotice: true;
}

/**
 * 유료 팩 결제 준비 응답 — 결제 모듈 주문(`payment_orders`, 주문 종류 `ROBOTIKON_PACK`)을 만들고 결제창 값을 준다. 금액은 서버가 팩에서 읽는다.
 * 이후 공용 흐름(`usePaymentCheckout`) — 결제창 → `POST /api/payments/{paymentId}/complete { orderType, orderRefId }` → `PaymentOrderHandler.onPaid` 가 구매 기록·보유를 만든다.
 * 오류 차례(서버 S3): 401 → 정지 `403 ACCOUNT_RESTRICTED` → 이메일 미인증 `403 EMAIL_NOT_VERIFIED` → 본인인증·만 19세 `403 IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE`(+`adultFrom`)
 * → 동의 없음 `400 VALIDATION fields.agreeRefundNotice` → `404 EMOTICON_PACK_NOT_FOUND` → 판매 중단 `409 EMOTICON_PACK_SUSPENDED` → 무료 팩 `409 EMOTICON_PACK_FREE`
 * → 유료 판매 꺼짐 `409 EMOTICON_PAID_SALES_CLOSED` → 이미 가짐(무료일 때 받았다 뺀 회원 포함) `409 EMOTICON_PACK_OWNED`
 */
export interface EmoticonPurchaseStart {
    orderType: 'ROBOTIKON_PACK';
    /** 구매 기록 번호(결제 대기) */
    orderRefId: string;
    expiresAt: string;
    checkout: PaymentCheckout;
    packId: string;
    /** 포트원 구매자(KG이니시스 요구 — 서버가 회원 이메일 등으로 채운다). 가짜 게이트웨이면 null */
    customer: { fullName: string; phoneNumber: string; email: string } | null;
}

/**
 * 팩 규격·보상 설정(관리자 설정 값 — 제작 가이드·업로드 검사가 같은 값을 쓴다, 계약 가정 2·4). 로그인 불필요.
 * API 후보에 없던 칸 — 「API 후보」에 `GET /api/emoticons/rules` 로 더할 것을 보고한다.
 */
export interface EmoticonRules {
    /** 이모티콘 그림 한 변(px) — 200 */
    imageSize: number;
    /** 그림 둘레 투명 여백(px) — 5 */
    imagePadding: number;
    /** 이모티콘 한 개 최대 바이트 — 2MB */
    maxBytes: number;
    /** 권장 바이트 — 300KB */
    recommendedBytes: number;
    /** GIF 최대 프레임 — 24 */
    maxFrames: number;
    /** 팩당 이모티콘 수 */
    minCount: number;
    maxCount: number;
    /** 대표 이미지 최대 바이트 — 1MB(200×200 PNG) */
    coverMaxBytes: number;
    /** 팩 이름 글자 수 */
    nameMin: number;
    nameMax: number;
    /** 이름표 글자 수 */
    labelMin: number;
    labelMax: number;
    /** 태그 최대 수·한 개 최대 글자 */
    maxTags: number;
    tagMax: number;
    /** 설명 최대 글자 */
    descriptionMax: number;
    /** 글·댓글 표시 크기(px) — 100(모바일 80) */
    displaySize: number;
    displaySizeMobile: number;
    /** 고르기 창 크기(px) — 64 */
    pickerSize: number;
    /** 다른 회원이 받을 때마다 작가에게 주는 적립 포인트 — 기본 10 */
    rewardPerAcquire: number;
    /** 작가 하루 보상 상한 — 기본 500 */
    rewardDailyCap: number;
    /** 한 사람 심사 대기 팩 수 상한 — 3 */
    maxPending: number;
    /** 가격 단계(원) — 운영자가 승인 때 이 중에서 고른다(기본 1,500 · 2,500 · 3,500, 계약 「가격은 운영자가 정한다」). 작가는 금액을 고르지 않는다 */
    priceTiers: number[];
    /** 부가세율 — 0.1. 사이트가 판매자라 부가세는 사이트가 내고, 정산 기준 = (판매 − 환불) ÷ (1 + vatRate)(가정 4) */
    vatRate: number;
    /** 수수료율 — 0.3(결제 대행 수수료 포함) — 작가 몫 = 정산 기준 × (1 − feeRate) */
    feeRate: number;
    /** 산 뒤 환불할 수 있는 날 수(그 팩을 한 번도 쓰지 않았을 때) — 7 */
    refundDays: number;
    /** 정산 지급일(다음 달 며칠) — 10 */
    settlementDay: number;
    /** 이보다 적으면 다음 달로 넘긴다(원) — 10,000 */
    settlementMin: number;
    /** 개인 작가 원천징수율 — 0.033(사업소득 3.3%) */
    withholdingRate: number;
    /**
     * 유료 판매가 켜져 있나(운영자 설정, 기본 꺼짐 — S2). 꺼져 있으면 새 유료 판매 희망 신청을 받지 않는다(`400 fields.saleWish` — 임시 저장은 된다).
     * 이미 유료로 나온 팩의 새 판은 그대로 낼 수 있다. 운영 주체가 팔 수 없는 단계(개인·통신판매업 신고번호 없음)면 스위치와 상관없이 false —
     * 팩 상세의 구매 단추도 이 값으로 막는다(서버 `409 EMOTICON_PAID_SALES_CLOSED`)
     */
    paidSalesEnabled: boolean;
}
