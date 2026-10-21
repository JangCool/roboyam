/**
 * 공개 사이트 정보 API(`GET /api/site/info`)가 주고받는 모양 — 서버 `SiteInfoResponse` 와 같다(`docs/features/site-settings-contract.md` 「공개 읽기 경로 구현 결과」).
 * 이 모양은 서버가 준 **원문**이다(이름·소개는 이스케이프하지 않았다) — 화면은 `UtilSiteInfo.fromResponse` 로 걸러 쓰고, 글자는 텍스트 보간(`{{ }}`)으로만 그린다.
 */

/** 운영자가 올린 사이트 아이콘의 주소들 — 상대 주소(`/media/…`). `version` 은 내용이 바뀌면 바뀐다 */
export interface SiteIconResponse {
    version: string;
    favicon32: string;
    favicon48: string;
    appleTouch: string;
    icon192: string;
    icon512: string;
}

/** 관리자가 정한 표시 기본값 — 값 형태는 공유 레이어의 `DisplayValues` 와 같다(`H24`·`H12`, `YMD_DASH` 등). 설정 행이 없으면 처음 값(ko · Asia/Seoul · H24 · YMD_DASH) */
export interface SiteDisplayDefaultsResponse {
    language: string;
    zoneId: string;
    timeFormat: string;
    dateFormat: string;
}

/** `GET /api/site/info` 응답(비로그인) */
export interface SiteInfoResponse {
    /** 사이트 이름 — 설정 행이 없으면(설치 전) null */
    siteName: string | null;
    /** 한 줄 소개 — 없으면 null */
    tagline: string | null;
    /** 로고 `<img src>` — `/media/…` 상대 주소이거나, SVG 로고면 큰 data URI(최대 약 133KB). 없으면 null */
    logoUrl: string | null;
    /** 올린 아이콘이 없으면 null(기본 아이콘) */
    icon: SiteIconResponse | null;
    /** 링크 미리보기 이미지 — 서버가 `<head>` 에 직접 넣는다(화면은 쓰지 않는다) */
    ogImageUrl: string;
    displayDefaults: SiteDisplayDefaultsResponse;
}
