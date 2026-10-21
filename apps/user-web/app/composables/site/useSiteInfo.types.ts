import type { DisplayValues } from '#layers/display-time/utils/util-time';
import type { SiteIconResponse } from '~/composables/site/api/useSiteInfoApi.types';

/**
 * 화면이 쓰는 사이트 정보 — `GET /api/site/info` 응답을 `UtilSiteInfo.fromResponse` 로 걸러 둔 것(쓸 수 없는 칸은 null).
 * 링크 미리보기 이미지(`ogImageUrl`)는 서버가 `<head>` 에 직접 넣으므로 화면 상태에는 두지 않는다.
 */
export interface SiteInfo {
    /** 사이트 이름 — 1~50자. 없으면 null(화면은 `UtilSiteName.DEFAULT`) */
    siteName: string | null;
    /** 한 줄 소개 — 1~100자. 없으면 null(그 줄을 그리지 않는다) */
    tagline: string | null;
    /** 로고 `<img src>` — 상대 주소 또는 이미지 data URI. 없으면 null(이름 글자) */
    logoUrl: string | null;
    /** 올린 아이콘 — 없으면 null */
    icon: SiteIconResponse | null;
    /** 표시 기본값(사용자 개인 값이 없을 때의 폴백) — 못 읽으면 null(시스템 폴백) */
    displayDefaults: DisplayValues | null;
}
