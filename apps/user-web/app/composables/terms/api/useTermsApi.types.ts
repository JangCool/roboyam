/**
 * 공개 약관 API 의 응답 모양 — 서버 `server/api` 의 공개 약관 컨트롤러(`docs/features/terms-contract.md` 「API」 공개 표)와 같다.
 * 날짜 `effectiveDate` 는 `YYYY-MM-DD`(사이트 시간대의 달력 날짜), 시각 `publishedAt` 은 ISO-8601.
 * ⚠ 서비스 이름(용어 — `useSiteTerms`·`SiteTerms`)과 다른 기능이다. 이름이 겹치지 않게 `Terms*` 로 시작한다.
 */

/** 약관 종류 — 본문의 `kind`. 경로에는 소문자(`terms`·`privacy`)로 들어간다(`useTermsApi`) */
export type TermsKind = 'TERMS' | 'PRIVACY';

/** 게시본 상태 — 지금 약관 · 시행 예정 · 지난 */
export type TermsVersionStatus = 'CURRENT' | 'UPCOMING' | 'PAST';

/** 변경 안내(띠) — 시행 예정 버전이 있거나, 지금 약관이 2번 이상이고 시행된 지 7일 안일 때만 온다(판정은 서버) */
export interface TermsNotice {
    version: number;
    effectiveDate: string;
    /** true = 시행 예정, false = 최근 시행 */
    upcoming: boolean;
    changeNote: string | null;
}

/** 요약의 종류 한 줄 */
export interface TermsSummaryKind {
    kind: TermsKind;
    current: { version: number; effectiveDate: string } | null;
    notice: TermsNotice | null;
}

/** `GET /api/terms/summary` — 가입·로그인 화면의 링크와 기본 레이아웃의 띠가 쓴다 */
export interface TermsSummary {
    /** 가입·로그인 화면에 약관 보기 링크 표시(관리자 옵션) */
    showLinks: boolean;
    kinds: TermsSummaryKind[];
}

/** 지금 약관 */
export interface TermsCurrent {
    version: number;
    effectiveDate: string;
    publishedAt: string;
    changeNote: string | null;
    /** 서버가 정화한 HTML */
    body: string;
}

/** 시행 예정(여럿이면 가장 이른 것) */
export interface TermsUpcoming {
    version: number;
    effectiveDate: string;
    changeNote: string | null;
}

/** 이전 버전 목록 한 줄 */
export interface TermsHistoryItem {
    version: number;
    effectiveDate: string;
    status: TermsVersionStatus;
}

/** `GET /api/terms/{kind}` — 게시본이 없으면 `current` null(빈 상태) */
export interface TermsDocument {
    kind: TermsKind;
    current: TermsCurrent | null;
    upcoming: TermsUpcoming | null;
    history: TermsHistoryItem[];
}

/** `GET /api/terms/{kind}/versions/{version}` */
export interface TermsVersion {
    version: number;
    effectiveDate: string;
    publishedAt: string;
    changeNote: string | null;
    status: TermsVersionStatus;
    /** 서버가 정화한 HTML */
    body: string;
}
