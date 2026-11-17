import type { PurchaseSource } from '~/composables/board/api/usePostApi.types';

/** `PostPurchaseSourceField` 의 값 — 구입 경로 + 연결할 체험단 줄 */
export interface PurchaseSourceValue {
    source: PurchaseSource | null;
    trialParticipantId: string | null;
}

/** 고를 수 있는 내 뽑힌 체험단 */
export interface PurchaseTrialOption {
    participantId: string;
    label: string;
}

/** 빈 값 — 쓰는 곳은 `{ ...EMPTY_PURCHASE_SOURCE }` 로 복사해 쓴다 */
export const EMPTY_PURCHASE_SOURCE: Readonly<PurchaseSourceValue> = { source: null, trialParticipantId: null };
