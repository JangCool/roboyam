import type { BusinessServiceStatus, BusinessStatus, NtsBizStatus } from '~/composables/business/api/useBusinessApi.types';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/**
 * 사업자 심사 화면 규칙 — 입력만 보고 답하는 순수 함수(서버 `BusinessReviewPolicy` 예정이 다시 판정한다).
 */
export default class UtilBusiness {
    /** 평문 열람 뒤 자동 가림까지(초) — B3 */
    static readonly REVEAL_SECONDS = 600;

    static statusSeverity(status: BusinessStatus): Severity {
        switch (status) {
            case 'PENDING':
                return 'info';
            case 'APPROVED':
                return 'success';
            case 'REJECTED':
                return 'warn';
            case 'SUSPENDED':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    static serviceSeverity(status: BusinessServiceStatus): Severity {
        switch (status) {
            case 'APPROVED':
                return 'success';
            case 'PENDING':
                return 'info';
            case 'REJECTED':
                return 'warn';
            case 'SUSPENDED':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    static ntsSeverity(status: NtsBizStatus): Severity {
        switch (status) {
            case 'CONTINUING':
                return 'success';
            case 'PAUSED':
                return 'warn';
            case 'CLOSED':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    static ntsIcon(status: NtsBizStatus): string {
        return status === 'CONTINUING' ? 'pi pi-check-circle' : status === 'UNAVAILABLE' ? 'pi pi-question-circle' : 'pi pi-ban';
    }

    /**
     * 승인 가능 여부(B1) — 계속사업자만 바로, 조회 불가는 사유를 적으면, 휴·폐업은 잠금.
     * @returns `OK` · `NEEDS_REASON` · `LOCKED`
     */
    static approvability(status: NtsBizStatus): 'OK' | 'NEEDS_REASON' | 'LOCKED' {
        if (status === 'CONTINUING') return 'OK';
        if (status === 'UNAVAILABLE') return 'NEEDS_REASON';
        return 'LOCKED';
    }

    /** 남은 초 → `9:58` */
    static clock(seconds: number): string {
        const s = Math.max(0, seconds);
        return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    }
}
