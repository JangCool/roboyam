import type { PaymentCancelStatus, PaymentStatus, PaymentWebhookResult, ReconcileDayStatus } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilMember from '~/utils/util-member';

/** PrimeVue `Tag` 의 색 */
export type PaymentTagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** 처리 사유의 문제 */
export type PaymentReasonProblem = 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG';

/**
 * 결제 관리 화면(결제 주문·웹훅 수신 내역·결제 이상·대사 불일치)이 같이 쓰는 순수 함수 — 상태 색, 사유 규칙, 금액 글자.
 * 정책 원천: `docs/features/commerce-payment-contract.md`(세션 정리 결정 12 — 처리 사유 5~200자).
 */
export default class UtilPayment {
    /** 처리 사유 5~200자(앞뒤 공백 뗀 유니코드 글자 수 — 서버와 같다) */
    static readonly REASON = { min: 5, max: 200 } as const;

    static statusSeverity(status: PaymentStatus): PaymentTagSeverity {
        if (status === 'PAID') return 'success';
        if (status === 'READY') return 'info';
        if (status === 'PARTIAL_CANCELED') return 'warn';
        return 'secondary';
    }

    static cancelSeverity(status: PaymentCancelStatus): PaymentTagSeverity {
        if (status === 'SUCCEEDED') return 'success';
        if (status === 'GAVE_UP') return 'danger';
        if (status === 'MANUAL') return 'secondary';
        return 'info';
    }

    static webhookSeverity(result: PaymentWebhookResult): PaymentTagSeverity {
        if (result === 'PROCESSED') return 'success';
        if (result === 'REJECTED') return 'danger';
        if (result === 'RETRY') return 'warn';
        return 'secondary';
    }

    static daySeverity(status: ReconcileDayStatus): PaymentTagSeverity {
        if (status === 'DONE') return 'success';
        if (status === 'MISMATCH') return 'warn';
        if (status === 'FAILED') return 'danger';
        if (status === 'RUNNING') return 'info';
        return 'secondary';
    }

    /** 원 금액 글자(천 단위 쉼표) — 단위는 문구가 붙인다 */
    static won(amount: number | null | undefined, locale: string): string {
        return amount === null || amount === undefined ? '—' : amount.toLocaleString(locale);
    }

    /**
     * 기간 칸(달력 글자 `YYYY-MM-DD`)을 서버 조건(UTC ISO 순간, [from, to))으로 — 표시 시간대의 하루 경계, `to` 는 끝 날의 다음 날 0시.
     * @returns 비어 있는 쪽은 넣지 않는다.
     */
    static range(from: string, to: string, zoneId: string): { from?: string; to?: string } {
        const out: { from?: string; to?: string } = {};
        const start = from ? UtilMember.dayStart(from, zoneId) : undefined;
        const end = to ? UtilMember.dayEnd(to, zoneId) : undefined;
        if (start) out.from = start;
        if (end) out.to = new Date(Date.parse(end) + 1).toISOString();
        return out;
    }

    static length(text: string): number {
        return Array.from(text).length;
    }

    static reasonText(text: string): string {
        return text.trim().replace(/\s+/g, ' ');
    }

    static reasonProblem(text: string): PaymentReasonProblem | null {
        const value = UtilPayment.reasonText(text);
        if (!value) return 'REQUIRED';
        const length = UtilPayment.length(value);
        if (length < UtilPayment.REASON.min) return 'TOO_SHORT';
        return length > UtilPayment.REASON.max ? 'TOO_LONG' : null;
    }
}
