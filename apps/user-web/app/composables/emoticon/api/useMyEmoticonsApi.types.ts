import type { EmoticonItem, EmoticonPackSource } from '~/composables/emoticon/api/useEmoticonApi.types';

/**
 * 내 로보티콘 API 의 모양(`useMyEmoticonsApi`) — 가진 팩(고르기 창이 읽는다)과 유료 팩 구매 내역·환불.
 * 계약 가정 3(환불 — 산 뒤 7일 안 + 한 번도 쓰지 않음) · 11(무료 팩만 「목록에서 빼기」).
 */

/** 가진 팩 한 개 — 고르기 창의 탭 하나 */
export interface MyEmoticonPack {
    id: string;
    name: string;
    source: EmoticonPackSource;
    /** 작가 닉네임(운영 팩은 null) */
    creatorNickname: string | null;
    coverUrl: string;
    /** 받은(무료)·산(유료) 시각 */
    acquiredAt: string;
    /** 가격(원) — 0 이면 무료로 받은 팩(목록에서 뺄 수 있다), 아니면 산 팩(빼지 않는다 — 환불은 구매 내역) */
    price: number;
    /** 산 팩의 구매 기록 번호(무료 팩은 null) */
    purchaseId: string | null;
    /** 판매가 중단된 팩도 가졌으면 계속 쓴다 */
    suspended: boolean;
    emoticons: EmoticonItem[];
}

/** `GET /api/me/emoticons` — 지금 쓸 수 있는 팩(최근 가진 것 먼저). 없으면 `packs: []` */
export interface MyEmoticons {
    packs: MyEmoticonPack[];
}

/** 구매 상태 — 결제 완료(보유) · 환불됨(결제 취소, 보유 해제) */
export type EmoticonPurchaseStatus = 'PAID' | 'REFUNDED';

/** 구매 내역 한 줄 */
export interface MyEmoticonPurchase {
    id: string;
    packId: string;
    /** 산 때의 팩 이름(사본 — 뒤에 이름이 바뀌어도 그대로) */
    packName: string;
    /** 지금 상점 판의 대표 그림(팩이 판매 중단이어도) */
    coverUrl: string;
    /** 결제 금액(원) */
    amount: number;
    /**
     * 판매자 — 늘 사이트(카카오 방식, 계약 「가격과 결제」). 작가는 `creatorNickname` 으로 따로.
     * 서버는 커머스 사업자 정보의 상호를 싣는다 — 비었으면 null(화면이 `useCommerceInfo`·사이트 이름으로 대신 그린다)
     */
    sellerName: string | null;
    /** 작가 닉네임(운영 팩은 null) */
    creatorNickname: string | null;
    /** 카드 영수증 주소(PG 가 준다 — 없으면 null) */
    receiptUrl: string | null;
    paidAt: string;
    /** 셀프 환불 기한(산 뒤 `refundDays` 일) */
    refundUntil: string;
    /** 그 팩의 로보티콘을 한 번이라도 썼다(글·댓글 저장 때 서버가 본문 토큰으로 정한다) — 쓰면 환불할 수 없다 */
    usedAt: string | null;
    status: EmoticonPurchaseStatus;
    refundedAt: string | null;
    /** 지금 셀프 환불할 수 있는가(서버 판정 — PAID · 안 씀 · 기한 안) */
    refundable: boolean;
}

/** `GET /api/me/emoticon-purchases` — 최근 것 먼저 */
export interface MyEmoticonPurchaseList {
    items: MyEmoticonPurchase[];
}

/**
 * `POST /api/me/emoticon-purchases/{id}/refund` — 결제 취소(전액). 그 팩은 더 이상 쓸 수 없다.
 * 오류 `409 EMOTICON_REFUND_NOT_ALLOWED`(최상위 `blockedBy` `USED|EXPIRED`) · `409 EMOTICON_ALREADY_REFUNDED`
 */
export interface MyEmoticonRefundResult {
    id: string;
    status: 'REFUNDED';
    refundedAt: string;
    canceledAmount: number;
}
