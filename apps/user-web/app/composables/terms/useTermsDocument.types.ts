import type { TermsVersionStatus } from '~/composables/terms/api/useTermsApi.types';

/** 화면에 그리는 본문 하나 — 지금 약관 또는 `?version=N` 의 게시본 */
export interface TermsShown {
    version: number;
    effectiveDate: string;
    publishedAt: string;
    changeNote: string | null;
    status: TermsVersionStatus;
    /** 서버가 정화한 HTML */
    body: string;
}
