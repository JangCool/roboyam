/**
 * 사이트 소개 관리 API(`/api/admin/site/about`)가 주고받는 모양 — 서버 응답과 같다.
 */

/** `GET`·`PUT /api/admin/site/about` 의 `data` */
export interface SiteAboutAdminResponse {
    /** 서버가 정화한 소개 HTML — 소개가 없으면 null */
    html: string | null;
    /** 마지막으로 저장한 때(ISO) — 한 번도 저장하지 않았으면 null */
    updatedAt: string | null;
    /** 사이트 설정의 공개 주소(끝 `/` 없음 기대) — 정하지 않았으면 null. 「공개 페이지 보기」 링크가 쓴다 */
    publicUrl?: string | null;
}

/** `PUT /api/admin/site/about` 본문 — 빈 값은 null(소개 없음 → 공개 푸터 링크가 숨는다) */
export interface SiteAboutRequest {
    html: string | null;
}
