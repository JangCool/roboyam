import type { BusinessServiceStatus, BusinessStatus, NtsBizStatus, NtsCheck, NtsValidity } from '~/composables/business/api/useBusinessApi.types';

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

    static validitySeverity(validity: NtsValidity): Severity {
        return validity === 'MATCH' ? 'success' : validity === 'MISMATCH' ? 'danger' : 'secondary';
    }

    static validityIcon(validity: NtsValidity): string {
        return validity === 'MATCH' ? 'pi pi-id-card' : validity === 'MISMATCH' ? 'pi pi-times-circle' : 'pi pi-question-circle';
    }

    /**
     * 승인 판정의 까닭(B1 + 진위확인) — 진위 불일치·휴폐업·없는 번호는 잠금, 상태 조회 불가·진위 조회 불가·진위 미확인(옛 신청)은 사유를 적으면, 둘 다 통과면 바로.
     * 잠금이 사유보다 앞선다(불일치 + 조회 불가 → 잠금).
     */
    static ntsRule(nts: Pick<NtsCheck, 'status' | 'validity'>): 'OK' | 'LOCKED_MISMATCH' | 'LOCKED_STATUS' | 'NEEDS_REASON_UNAVAILABLE' | 'NEEDS_REASON_UNCHECKED' {
        if (nts.validity === 'MISMATCH') return 'LOCKED_MISMATCH';
        if (nts.status === 'PAUSED' || nts.status === 'CLOSED' || nts.status === 'NOT_FOUND') return 'LOCKED_STATUS';
        if (nts.status === 'UNAVAILABLE' || nts.validity === 'UNAVAILABLE') return 'NEEDS_REASON_UNAVAILABLE';
        if (nts.validity === 'UNCHECKED') return 'NEEDS_REASON_UNCHECKED';
        return 'OK';
    }

    /**
     * 승인 가능 여부 — {@link ntsRule} 을 세 갈래로 접는다.
     * @returns `OK` · `NEEDS_REASON` · `LOCKED`
     */
    static approvability(nts: Pick<NtsCheck, 'status' | 'validity'>): 'OK' | 'NEEDS_REASON' | 'LOCKED' {
        const rule = UtilBusiness.ntsRule(nts);
        return rule === 'OK' ? 'OK' : rule.startsWith('LOCKED') ? 'LOCKED' : 'NEEDS_REASON';
    }

    /** 남은 초 → `9:58` */
    static clock(seconds: number): string {
        const s = Math.max(0, seconds);
        return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    }
}
