import type { TermsKind, TermsNotice } from '~/composables/terms/api/useTermsApi.types';

/**
 * 공개 약관 화면의 순수 규칙 — 화면 주소, `?version=N` 읽기, 변경 안내 띠의 닫기 기억(localStorage 열쇠).
 * ⚠ 서비스 이름(용어)의 `UtilSiteTerms` 와 다른 기능이다.
 */
export default class UtilTerms {
    /** 띠를 닫은 버전을 기억하는 localStorage 열쇠의 앞부분 — 뒤에 종류(`TERMS`·`PRIVACY`)가 붙고 값은 닫은 버전 번호 */
    static readonly NOTICE_STORAGE_PREFIX = 'roboyam:terms-notice:';

    /**
     * 종류의 공개 화면 주소.
     * @param {TermsKind} kind - 약관 종류.
     * @returns {string} `/terms` 또는 `/privacy`.
     */
    static pathOf(kind: TermsKind): string {
        return kind === 'TERMS' ? '/terms' : '/privacy';
    }

    /**
     * 특정 버전 화면 주소 — 버전이 없으면 지금 약관 화면.
     * @param {TermsKind} kind - 약관 종류.
     * @param {number | null} version - 버전 번호.
     * @returns {{ path: string, query?: { version: string } }} `NuxtLink` 의 `to`.
     */
    static versionTo(kind: TermsKind, version: number | null): { path: string; query?: { version: string } } {
        return version === null ? { path: UtilTerms.pathOf(kind) } : { path: UtilTerms.pathOf(kind), query: { version: String(version) } };
    }

    /**
     * 변경 안내의 [보기] 주소 — 시행 예정이면 그 버전, 최근 시행이면 지금 약관.
     * @param {TermsKind} kind - 약관 종류.
     * @param {TermsNotice} notice - 변경 안내.
     * @returns {{ path: string, query?: { version: string } }} `NuxtLink` 의 `to`.
     */
    static noticeTo(kind: TermsKind, notice: TermsNotice): { path: string; query?: { version: string } } {
        return UtilTerms.versionTo(kind, notice.upcoming ? notice.version : null);
    }

    /**
     * 주소의 `?version=` 값 — 1 이상의 정수만 받는다. 없거나 맞지 않으면 null(지금 약관을 보인다).
     * @param {unknown} raw - `route.query.version`(글자·글자 목록·없음).
     * @returns {number | null} 버전 번호.
     */
    static parseVersion(raw: unknown): number | null {
        const value = Array.isArray(raw) ? raw[0] : raw;
        if (typeof value !== 'string' || !/^[1-9]\d{0,8}$/.test(value)) return null;
        return Number(value);
    }

    /**
     * 띠를 닫은 기억 열쇠.
     * @param {TermsKind} kind - 약관 종류.
     * @returns {string} `roboyam:terms-notice:TERMS` 꼴.
     */
    static noticeStorageKey(kind: TermsKind): string {
        return `${UtilTerms.NOTICE_STORAGE_PREFIX}${kind}`;
    }
}
