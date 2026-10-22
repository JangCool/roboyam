import type { DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 사이트 설정 API 가 주고받는 모양 — 서버 계약(`/api/admin/site/**`, `docs/features/site-settings-contract.md`)과 같은 모양이다.
 * 사이트 기본 정보는 서버 `SiteSettingEntity`(`site_settings`, 행 하나)의 `siteName`·`tagline`, 설치 마법사가 처음 정하는 값이다(`docs/features/install-contract.md`).
 * 공개 주소(`publicUrl`)는 소셜 로그인 콜백·메일 링크의 기준이라 이 화면 범위가 아니다(변경 정책 미확정).
 */

/** 사이트 기본 정보 — 사이트 이름(1~50자)과 한 줄 소개(슬로건, 0~100자) */
export interface SiteBasicResponse {
    siteName: string;
    /** 한 줄 소개. 없으면 null */
    tagline: string | null;
    /** 로고 — 이미지면 파일 주소, SVG 면 서버가 만든 data URI. 그대로 `<img src>` 에 넣는다. 올리지 않았으면 null(이름 글자만 보인다) */
    logoUrl: string | null;
}

export interface SiteBasicRequest {
    siteName: string;
    /** 비우면 null(문구 없음) */
    tagline: string | null;
}

/**
 * 로고 — 바이트(`POST /api/admin/files`, multipart)와 "그 파일을 로고로 삼기"(JSON)는 두 걸음이다. 폼(JSON)과 바이트를 한 API 가 같이 받지 않는다(hamonica3 와 같다).
 * 로고 변경·제거는 이름·소개와 달리 **저장 단추 없이 즉시 적용**되고, 바뀐 `logoUrl` 만 돌려준다.
 */
export interface SiteLogoFileUploadResponse {
    fileId: string;
}

export interface SiteLogoFileRequest {
    fileId: string;
}

export interface SiteLogoSvgRequest {
    /** SVG 마크업 원문. `xmlns` 가 없으면 서버가 넣는다 */
    svgSource: string;
}

export interface SiteLogoResponse {
    logoUrl: string | null;
}

/**
 * 사이트 아이콘(탭 아이콘·홈 화면·알림 아이콘) — 후보 계약(확정 전). 로고와 달리 **한 걸음**이다: 서버가 그림을 여러 크기로 줄여 저장해야 하므로 바이트(PNG)를 곧바로 `POST` 한다.
 * 올리기·되돌리기는 로고처럼 저장 단추 없이 즉시 적용되고, 바뀐 상태 전체를 돌려준다.
 */
export interface SiteIconResponse {
    /** 올려 둔 아이콘이 있는가. false 면 기본 아이콘이 쓰인다 */
    custom: boolean;
    /** 올려 둔 아이콘의 미리보기 주소(그대로 `<img src>`). `custom=false` 면 null */
    previewUrl: string | null;
    /** 기본 아이콘의 미리보기 주소 — 올려 둔 것이 없을 때 이 그림을 보인다 */
    defaultPreviewUrl: string;
}

export interface SiteIconSvgRequest {
    /** SVG 마크업 원문(정사각이어야 한다 — 판정은 서버). 서버가 PNG 로 바꿔 저장한다 */
    svgSource: string;
}

/**
 * 링크 미리보기 이미지(`og:image` — 카톡·SNS 에 주소를 붙일 때 보이는 그림) — 후보 계약(확정 전). 아이콘과 같은 모양이고 SVG 입구는 없다.
 * 서버가 1200×630 PNG 로 맞춰 저장한다(비율이 맞지 않으면 자르지 않고 거절).
 */
export interface SiteOgImageResponse {
    /** 올려 둔 이미지가 있는가. false 면 기본 이미지가 쓰인다 */
    configured: boolean;
    /** 올려 둔 이미지 주소. `configured=false` 면 null */
    url: string | null;
    /** 저장된 이미지의 가로·세로(px). `configured=false` 면 null */
    width: number | null;
    height: number | null;
    /** 기본 이미지의 주소 — 올려 둔 것이 없을 때 이 그림을 보인다 */
    defaultUrl: string;
}

/**
 * 표시 기본값 — 언어·시간대·시간 형식·날짜 형식. 칸 모양은 공유 레이어(`@roboyam/display-time`)의 `DisplayValues` 와 같다.
 * 이 값이 곧 개인 설정 탭의 "기본 설정"(내 표시 형식을 쓰지 않을 때 따르는 값)이다.
 * ⚠ 서버는 `site_settings` 한 행에 둔다. 개인 설정 쪽 연결(`inherited`)과 공개 회원 값과의 관계는 미정.
 */
export type { DisplayValues };

export interface SiteDisplayDefaultsResponse {
    values: DisplayValues;
}

export interface SiteDisplayDefaultsRequest {
    values: DisplayValues;
}

/**
 * 공개 사이트 테마 — 지금 값과 고를 수 있는 id 목록(보일 순서). 서버 `SiteThemeResponse`(원천 `SiteThemes.IDS`, user-web `util-theme.ts` 의 `THEMES` 와 같은 id).
 * 이름·설명(화면 문구)은 화면이 id 로 i18n 키(`site.themeName_<id>`·`site.themeDesc_<id>`)를 찾는다 — 키가 없는 id 는 id 그대로 보인다.
 */
export interface SiteThemeResponse {
    /** 지금 테마 id(저장된 값이 목록에서 빠졌으면 서버가 `default` 로 본다) */
    theme: string;
    /** 고를 수 있는 테마 id(보일 순서) */
    themes: string[];
}

export interface SiteThemeRequest {
    theme: string;
}
