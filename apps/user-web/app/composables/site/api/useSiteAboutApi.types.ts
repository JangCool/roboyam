/**
 * 공개 사이트 소개 API(`GET /api/site/about`)가 주는 모양 — 서버 응답과 같다.
 */

/** `GET /api/site/about` 응답(비로그인) */
export interface SiteAboutResponse {
    /** 서버가 정화한 소개 HTML — 소개가 없으면 null */
    html: string | null;
}
