/** `BizPauseDialog` 의 동작 — 운영자가 광고주 대신 일시 정지 · 다시 켜기 · 중단(환불 심사) */
export type BizPauseAction = 'pause' | 'resume' | 'stop';

/** 중단 때 보일 예상 환불(부모가 `UtilBizApplication.stopRefund` 로 계산 — 서버가 다시 계산한다) */
export interface BizPauseRefundEstimate {
    /** 환불 예상액 = 일할 − 수수료 */
    amount: number;
    /** 남은 기간 일할 금액 */
    prorated: number;
    /** 그 건에 고정된 중도 해지 수수료율(% — `pause.feeRate`) */
    feeRate: number;
    /** 중도 해지 수수료 */
    fee: number;
    /** 일할 대상 금액(부가세 포함) */
    base: number;
    usedDays: number;
    totalDays: number;
    /** 계산 기준 시각 — 정지 중이면 멈춘 시각, 아니면 지금 */
    from: string;
    fromPause: boolean;
    /** 사업자 핫딜 글 — 게시비는 돌려주지 않고 노출 자리 몫만 */
    dealPost: boolean;
}
