/**
 * 로보티콘 유료 판매의 정산 계산(계약 가정 4, 카카오 방식) — 제작 가이드의 한 건 예시에만 쓴다.
 * 사이트가 판매자라 부가세는 사이트가 낸다:
 *   정산 기준 = (판매 − 환불) ÷ (1 + 부가세율)  → 원 단위 내림
 *   작가 몫   = 정산 기준 × (1 − 수수료율)     → 원 단위 내림(수수료 = 정산 기준 − 작가 몫)
 *   원천징수  = 개인 작가만 작가 몫 × 원천징수율 → 10원 단위 내림(사업자는 0 — 세금계산서)
 *   지급액    = 작가 몫 − 원천징수
 * 정본은 서버 `EmoticonSettlementMath`(월 합·이월·음수는 서버만 계산한다) — 화면 값은 서버가 준 것을 쓰고, 이 유틸은 안내 예시에만 쓴다. 순수 함수.
 */

/** 계산에 쓰는 설정 값(`EmoticonRules` 의 일부) */
export interface EmoticonSettlementRates {
    vatRate: number;
    feeRate: number;
    withholdingRate: number;
}

/** 계산 결과(원) */
export interface EmoticonSettlementBreakdown {
    /** 판매 − 환불(부가세 포함) */
    net: number;
    /** 부가세(사이트가 낸다) */
    vat: number;
    /** 정산 기준(부가세 제외) */
    base: number;
    /** 수수료 */
    fee: number;
    /** 원천징수 전 작가 몫 */
    share: number;
    /** 원천징수(개인만) */
    withholding: number;
    /** 지급액 */
    payout: number;
}

export default class UtilEmoticonSettlement {
    /**
     * 판매·환불 금액으로 작가 몫을 나눈다.
     * @param {number} sales - 판매액(부가세 포함 결제 금액 합).
     * @param {number} refund - 환불액.
     * @param {EmoticonSettlementRates} rates - 부가세율·수수료율·원천징수율.
     * @param {boolean} individual - 개인 작가인가(원천징수).
     * @returns {EmoticonSettlementBreakdown} 나눈 값.
     * @example UtilEmoticonSettlement.compute(2500, 0, { vatRate: 0.1, feeRate: 0.3, withholdingRate: 0.033 }, true) // base 2272 · share 1590 · withholding 50 · payout 1540
     */
    static compute(sales: number, refund: number, rates: EmoticonSettlementRates, individual: boolean): EmoticonSettlementBreakdown {
        // 비율은 만분율 정수로 바꿔 정수끼리 나눈다(2,200 ÷ 1.1 이 1,999.999… 로 내려가는 부동소수 오차를 막는다)
        const bp = (rate: number) => Math.round(rate * 10_000);
        const net = Math.max(0, sales - refund);
        const base = Math.floor((net * 10_000) / (10_000 + bp(rates.vatRate)));
        const share = Math.floor((base * (10_000 - bp(rates.feeRate))) / 10_000);
        const withholding = individual ? Math.floor((share * bp(rates.withholdingRate)) / 100_000) * 10 : 0;
        return { net, vat: net - base, base, fee: base - share, share, withholding, payout: share - withholding };
    }
}
