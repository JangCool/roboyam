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

    /** 첫 게시본의 최소 공지 일수(내일) */
    static readonly NOTICE_DAYS_FIRST = 1;
    /** 개정(일반)의 최소 공지 일수 */
    static readonly NOTICE_DAYS_REVISION = 7;
    /** 회원에게 불리한 변경의 최소 공지 일수 */
    static readonly NOTICE_DAYS_UNFAVORABLE = 30;

    /**
     * 시행일까지 둘 최소 일수 — 불리한 변경 30일 · 개정 7일 · 첫 게시본 1일(내일).
     * 화면 편의다 — 판정은 서버(같은 규칙, 어기면 `fields.effectiveDate` 에 `terms.effectiveDate.revision`·`.unfavorable` 문구).
     * @param {boolean} revision - 그 종류에 이미 게시본이 있는가(개정).
     * @param {boolean} unfavorable - 회원에게 불리한 변경인가.
     * @returns {number} 오늘부터 더할 일수.
     */
    static noticeDays(revision: boolean, unfavorable: boolean): number {
        if (unfavorable) return UtilTerms.NOTICE_DAYS_UNFAVORABLE;
        return revision ? UtilTerms.NOTICE_DAYS_REVISION : UtilTerms.NOTICE_DAYS_FIRST;
    }

    /**
     * 시행일로 고를 수 있는 가장 이른 날 — 오늘 + 공지 일수(`noticeDays`, 기본 1 = 내일)와 그 종류의 마지막 게시본 시행일 중 늦은 날(마지막 시행일과 같은 날 허용).
     * 서버 규칙: 시행일은 내일(사이트 시간대) 이후이고 마지막 게시본 시행일 이후여야 한다(오늘 시행은 받지 않는다). 화면의 오늘은 편의이며 판정은 서버다.
     * @param {string} today - 오늘(`YYYY-MM-DD`).
     * @param {string | null | undefined} lastEffectiveDate - 마지막 게시본 시행일(없으면 null).
     * @param {number} days - 오늘부터 더할 일수(기본 1).
     * @returns {string} `YYYY-MM-DD`.
     */
    static minEffectiveDate(today: string, lastEffectiveDate: string | null | undefined, days = 1): string {
        const [y, mo, d] = today.split('-').map(Number);
        const next = new Date(Date.UTC(y!, mo! - 1, d! + days));
        const earliest = UtilTerms.ymd(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate());
        return lastEffectiveDate && lastEffectiveDate > earliest ? lastEffectiveDate : earliest;
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
