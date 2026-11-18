/** 접근 설정을 받는 중인가 — `idle` 아직 안 물어봄 */
export type AdultBoardLoadStatus = 'idle' | 'loading' | 'ready' | 'error';

/**
 * 「핀 필요」 알림 — 어느 API 호출이든 `403 ADULT_BOARD_PIN_REQUIRED` 를 받으면 `useApiClient` 가 번호를 올린다(`ADULT_BOARD_PIN_DEMAND_STATE_KEY`).
 * `lockedUntil` 은 틀림 잠금의 끝 시각(ISO, 없으면 null).
 */
export interface AdultBoardPinDemand {
    seq: number;
    lockedUntil: string | null;
}
