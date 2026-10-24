import type { TermsVersionStatus } from '~/composables/terms/api/useTermsApi.types';

/**
 * 관리자 약관 화면의 순수 규칙 — 게시 대화상자의 미리 검사(판정은 서버 약관 게시 서비스, `docs/features/terms-contract.md` 「시행일 규칙」)와 상태 표시 색.
 */
export default class UtilTerms {
    /** 변경 요지 최대 글자 수 — 서버 게시 요청의 `changeNote` 검사(0~200자)와 같다 */
    static readonly CHANGE_NOTE_MAX = 200;

    /**
     * 연·월·일 숫자를 `YYYY-MM-DD` 로.
     * @param {number} y - 연.
     * @param {number} mo - 월(1~12).
     * @param {number} d - 일.
     * @returns {string} `2026-10-24` 꼴.
     */
    static ymd(y: number, mo: number, d: number): string {
        return `${String(y).padStart(4, '0')}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    }

    /**
     * 시행일로 고를 수 있는 가장 이른 날 — 오늘과 그 종류의 마지막 게시본 시행일 중 늦은 날(같은 날 허용).
     * 서버 규칙: 시행일은 오늘(사이트 시간대) 이후이고 마지막 게시본 시행일 이후여야 한다. 화면의 오늘은 편의이며 판정은 서버다.
     * @param {string} today - 오늘(`YYYY-MM-DD`).
     * @param {string | null | undefined} lastEffectiveDate - 마지막 게시본 시행일(없으면 null).
     * @returns {string} `YYYY-MM-DD`.
     */
    static minEffectiveDate(today: string, lastEffectiveDate: string | null | undefined): string {
        return lastEffectiveDate && lastEffectiveDate > today ? lastEffectiveDate : today;
    }

    /**
     * 상태 뱃지 색(PrimeVue `Tag` severity).
     * @param {TermsVersionStatus} status - 게시본 상태.
     * @returns {'success' | 'info' | 'secondary'} 지금 = 초록, 시행 예정 = 파랑, 지난 = 회색.
     */
    static statusSeverity(status: TermsVersionStatus): 'success' | 'info' | 'secondary' {
        if (status === 'CURRENT') return 'success';
        if (status === 'UPCOMING') return 'info';
        return 'secondary';
    }
}
