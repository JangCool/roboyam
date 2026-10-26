import type { IdentityVerifyOutcome } from '~/composables/identity/useIdentity.types';

/** 본인인증이 필요한 용도 — 대화상자 문구가 용도마다 다르다(`identity.gateNeed_<용도>`) */
export type IdentityPurpose = 'CHARGE' | 'GROUP_BUY';

export interface IdentityGateOptions {
    purpose: IdentityPurpose;
    /** 만 19세 이상이어야 하는가(충전·공구 결제 — commerce.md 12-1). 기본 true */
    requireAdult?: boolean;
}

/**
 * 결제 직전 확인 대화상자의 상태 — 그리는 것은 `IdentityGateDialog`(레이아웃에 한 번).
 * - `NEED`: 미인증 — [본인인증 하기]
 * - `MINOR`: 인증했지만 만 19세 미만 — 막는 안내만(19세 생일이 지나면 다시 인증하지 않아도 된다)
 * - `LOAD_ERROR`: 인증 상태를 받지 못했다 — [다시 시도]
 */
export interface IdentityGateState {
    open: boolean;
    purpose: IdentityPurpose;
    requireAdult: boolean;
    mode: 'NEED' | 'MINOR' | 'LOAD_ERROR';
    /** 인증 흐름·상태 다시 받기가 진행 중 */
    busy: boolean;
    /** 마지막 인증 실패(대화상자 안 Message) — 사용자가 창을 닫은 것은 넣지 않는다 */
    failure: Extract<IdentityVerifyOutcome, { kind: 'FAILED' }> | null;
    /** 만 19세가 되는 날(`YYYY-MM-DD`) — `MINOR` 일 때 */
    adultFrom: string | null;
    /** 상태를 받지 못한 이유 — `LOAD_ERROR` 일 때 */
    loadError: string;
}
