/** `GET /api/site/theme` 응답 — 운영자가 사이트 설정에서 고른 테마 id. 모르는 id 는 화면이 기본 테마로 그린다 */
export interface ThemeResponse {
    theme: string;
}
