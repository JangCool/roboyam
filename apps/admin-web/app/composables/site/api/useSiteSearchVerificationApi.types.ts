/**
 * 검색 엔진 소유 확인 코드 API(`/api/admin/site/search-verification` — 아직 서버 없음, 목업)가 주고받는 모양.
 * 칸마다 메타 태그 `content` 값 하나. null = 그 태그를 넣지 않는다.
 *   - `naver`  → `<meta name="naver-site-verification" content="…">`
 *   - `google` → `<meta name="google-site-verification" content="…">`
 *   - `bing`   → `<meta name="msvalidate.01" content="…">`
 */

/** `GET`·`PUT /api/admin/site/search-verification` 의 `data` */
export interface SiteSearchVerificationResponse {
    naver: string | null;
    google: string | null;
    bing: string | null;
}

/** `PUT /api/admin/site/search-verification` 본문 — 빈 칸은 null(영문·숫자·`-`·`_` 1~100자) */
export type SiteSearchVerificationRequest = SiteSearchVerificationResponse;
