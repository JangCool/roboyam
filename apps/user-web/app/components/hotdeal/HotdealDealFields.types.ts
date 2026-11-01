import type { DealImage, DealLinkCheck, DealPriceBasis, DealStock } from '~/composables/hotdeal/api/useHotdealApi.types';

/** 배송비 고르기 — 무료(0) · 모름(null) · 금액 */
export type DealShippingMode = 'FREE' | 'UNKNOWN' | 'PAID';

/** 딜 칸의 값 — `HotdealDealFields` 의 `v-model`. 글쓰기·고치기가 같이 쓴다(판정·변환은 `UtilHotdeal`) */
export interface DealFormValues {
    productUrl: string;
    storeName: string;
    /** 글쓴이가 쇼핑몰 이름을 직접 고쳤다 — 링크 판정이 다시 와도 덮어쓰지 않는다 */
    storeNameTouched: boolean;
    dealPrice: number | null;
    normalPrice: number | null;
    shippingMode: DealShippingMode;
    /** `PAID` 일 때만 */
    shippingFee: number | null;
    /** 마감 순간(ISO) — 비면 마감 없음(`AppDateTimePicker` 의 값) */
    endsAt: string;
    stockState: DealStock;
    affiliate: boolean;
    couponNote: string;
    /** 가격 기준 선택지 — 고르지 않았으면 null(0원 딜·할인율 90% 이상이면 필수) */
    priceBasisKind: DealPriceBasis | null;
    /** 해외 직구 「관부가세 포함」 — `OVERSEAS` 가 아니면 보내지 않는다(false) */
    dutyIncluded: boolean;
    /** 가격 기준 메모 */
    priceBasis: string;
    image: DealImage | null;
    /** 글쓴이가 이미지를 지웠거나 직접 올렸다 — 링크 판정의 OG 이미지로 덮어쓰지 않는다 */
    imageTouched: boolean;
}

/** 딜 칸별 오류 — 서버 `fields."deal.*"` 또는 화면 검사 문구. 빈 글자 = 오류 없음. `deal` 은 칸에 붙지 않는 딜 오류(`fields.deal`) */
export interface DealFieldErrors {
    deal: string;
    productUrl: string;
    storeName: string;
    dealPrice: string;
    normalPrice: string;
    shippingFee: string;
    endsAt: string;
    couponNote: string;
    priceBasisKind: string;
    priceBasis: string;
    image: string;
}

/** 링크 판정 진행 — 아직 없음 · 확인 중 · 결과 · 확인 실패(판정 API 자체가 실패) */
export type DealLinkCheckStatus = 'idle' | 'checking' | 'done' | 'error';

/** 딜 칸이 받는 링크 판정 상태(`useDealLinkCheck`) */
export interface DealLinkCheckState {
    status: DealLinkCheckStatus;
    result: DealLinkCheck | null;
    /** 판정 API 오류 문구(`error` 일 때) */
    error: string;
}

/** 딜 칸이 부르는 동작 — 페이지가 API 컴포저블로 만들어 내려 준다(컴포넌트는 서버를 부르지 않는다) */
export interface DealFieldActions {
    /** 상품 이미지 한 장 올리기 → 이미지 */
    upload: (file: File) => Promise<DealImage>;
    /** OG 다시 가져오기(「가져오지 못했어요 · 다시 시도」) */
    retryCheck: () => void;
}
