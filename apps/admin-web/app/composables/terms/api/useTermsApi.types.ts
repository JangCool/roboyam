/**
 * 관리자 약관 API 의 요청·응답 모양 — 서버 `server/admin` 의 약관 컨트롤러(`docs/features/terms-contract.md` 「API」 관리자 표)와 같다.
 * 날짜 `effectiveDate` 는 `YYYY-MM-DD`(사이트 시간대의 달력 날짜), 시각(`publishedAt`·`updatedAt`)은 ISO-8601.
 */

/** 약관 종류 — 본문의 `kind`. 경로에는 소문자(`terms`·`privacy`)로 들어간다(`useTermsApi`) */
export type TermsKind = 'TERMS' | 'PRIVACY';

/** 게시본 상태 — 지금 약관 · 시행 예정 · 지난 */
export type TermsVersionStatus = 'CURRENT' | 'UPCOMING' | 'PAST';

/** 게시본 요약(개요의 지금·시행 예정) */
export interface TermsVersionRef {
    version: number;
    effectiveDate: string;
    publishedAt: string;
}

/** 개요의 종류 한 줄 */
export interface TermsKindOverview {
    kind: TermsKind;
    current: TermsVersionRef | null;
    upcoming: TermsVersionRef | null;
    /** 초안이 없으면 null */
    draftUpdatedAt: string | null;
    versionCount: number;
}

/** `GET /api/admin/terms` */
export interface TermsOverview {
    /** 가입·로그인 화면에 약관 보기 링크 표시 */
    showLinks: boolean;
    kinds: TermsKindOverview[];
}

/** 초안 — 종류마다 하나 */
export interface TermsDraft {
    /** 서버가 정화한 HTML */
    body: string;
    updatedAt: string;
    /** 운영자 표시 이름(없으면 null) */
    updatedBy: string | null;
}

/** 게시본 한 줄(이력 표) */
export interface TermsVersionItem {
    version: number;
    effectiveDate: string;
    publishedAt: string;
    /** 운영자 표시 이름(없으면 null) */
    publishedBy: string | null;
    /** 변경 요지(0~200자, 없으면 null 또는 빈 글자) */
    changeNote: string | null;
    status: TermsVersionStatus;
}

/** `GET /api/admin/terms/{kind}/versions/{version}` · 게시 응답 */
export interface TermsVersionDetail extends TermsVersionItem {
    /** 서버가 정화한 HTML */
    body: string;
}

/** `GET /api/admin/terms/{kind}` — 게시본은 최신 버전 먼저 */
export interface TermsKindDetail {
    kind: TermsKind;
    draft: TermsDraft | null;
    versions: TermsVersionItem[];
}

/** `PUT /api/admin/terms/{kind}/draft` */
export interface TermsDraftSaveRequest {
    body: string;
}

/** `POST /api/admin/terms/{kind}/publish` */
export interface TermsPublishRequest {
    effectiveDate: string;
    changeNote?: string;
}

/** `PUT /api/admin/terms/settings` 요청·응답 */
export interface TermsSettings {
    showLinks: boolean;
}
