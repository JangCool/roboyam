import {
    mockSiteBasicGet,
    mockSiteBasicSave,
    mockSiteDisplayGet,
    mockSiteDisplaySave,
    mockSiteIconClear,
    mockSiteIconGet,
    mockSiteIconSvgSet,
    mockSiteIconUpload,
    mockSiteLogoClear,
    mockSiteLogoFileSet,
    mockSiteLogoSvgSet,
    mockSiteLogoUpload,
    mockSiteOgImageClear,
    mockSiteOgImageGet,
    mockSiteOgImageUpload,
} from '~/composables/site/api/mock/site-settings-mock';
import type {
    SiteBasicRequest,
    SiteBasicResponse,
    SiteDisplayDefaultsRequest,
    SiteDisplayDefaultsResponse,
    SiteIconResponse,
    SiteIconSvgRequest,
    SiteLogoFileRequest,
    SiteLogoFileUploadResponse,
    SiteLogoResponse,
    SiteLogoSvgRequest,
    SiteOgImageResponse,
} from '~/composables/site/api/useSiteSettingsApi.types';

/**
 * 사이트 설정 API — 서버 주소 문자열(`/api/admin/site…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 값은 일반 화면의 `ref`).
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET /api/admin/site/basic` → 200 `{ siteName, tagline|null, logoUrl|null }`  (`site.read`)
 *   - `PUT /api/admin/site/basic` `{ siteName, tagline|null }` → 200 저장된 모양  (`site.manage`)
 *       · 400 VALIDATION(`fields.siteName` 1~50자·`<` `>` 제어 문자 없음, `fields.tagline` 0~100자·`<` `>` 없음) · 403 PERMISSION_DENIED
 *       · 서버 `SiteSettingEntity`(`site_settings`, 행 하나) 의 `siteName`·`tagline` 과 `updatedBy`·`updatedAt`. 공개 주소(`publicUrl`)는 이 계약에 넣지 않았다(소셜 콜백·메일 링크의 기준 — 변경 정책 미정).
 *   - 로고(저장 단추 없이 즉시 적용, `site.manage`) — 바이트와 "로고로 삼기"를 두 걸음으로 가른다(폼 JSON 과 multipart 를 한 API 가 받지 않는다):
 *       · `POST /api/admin/files` multipart `file`·`purpose=SITE_LOGO` → 201 `{ fileId }` · 400 VALIDATION(`fields.file` — PNG·JPG·GIF·WEBP 2MB 이하, 첫 바이트로 판별)
 *       · `PUT /api/admin/site/logo` `{ fileId }` → 200 `{ logoUrl }`(`/api/files/…` 주소) · 400 VALIDATION(`fields.fileId` 없는 파일)
 *       · `PUT /api/admin/site/logo/svg` `{ svgSource }` → 200 `{ logoUrl }`(서버가 만든 SVG data URI — 화면은 `<img src>` 에만 넣고 `v-html` 을 쓰지 않는다) · 400 VALIDATION(`fields.svgSource` 비었음·100,000자 초과·`<svg` 없음. `xmlns` 가 없으면 서버가 넣는다)
 *       · `DELETE /api/admin/site/logo` → 200 `{ logoUrl: null }`
 *       ⚠ 이 로고는 관리자 화면 상단과 **공개 사이트 로고 자리**가 같이 쓴다 — 올린 그림 주소(`/api/files/…`)는 비로그인에도 열려야 하고, 공개 쪽 응답(`GET /api/site/terms` 등)과 관리자 `me` 응답이 `logoUrl` 을 함께 준다.
 *   - 사이트 아이콘(탭 아이콘·홈 화면·알림 아이콘용, 저장 단추 없이 즉시 적용, 쓰기 `site.manage`) — ⚠ 아래 다섯 줄은 **확정 전 후보**다. 로고와 달리 서버가 그림을 여러 크기로 줄여 저장하므로 바이트를 곧바로 `POST` 한다(두 걸음이 아니다):
 *       · `GET /api/admin/site/icon` → 200 `{ custom, previewUrl|null, defaultPreviewUrl }`  (`site.read`)
 *       · `POST /api/admin/site/icon` multipart `file` → 200 위와 같은 모양 · 400 VALIDATION(`fields.file` — PNG 서명 아님 → 2MB 초과 → 못 읽음 → 한 변 4000px 초과 → 정사각 아님 → 한 변 512px 미만 순으로 첫 걸림 하나, 지금 크기·용량을 문구에 포함) · `fields.site`(사이트 설정이 아직 없음)
 *       · `PUT /api/admin/site/icon/svg` `{ svgSource }` → 200 위와 같은 모양 · 400 VALIDATION(`fields.svgSource` — 비었음·100,000자 초과·`<svg` 없음은 로고와 같고, **정사각 여부·위험 요소(스크립트·외부 링크)·그릴 수 있는지는 서버가 판정**하고 이유를 문구로 준다). SVG 는 서버가 PNG 로 바꿔 저장한다
 *       · `DELETE /api/admin/site/icon` → 200 `{ custom: false, … }`. 이미 기본이면 그대로 200
 *   - 링크 미리보기 이미지(`og:image`, 카톡·SNS 에 주소를 붙일 때 보이는 그림, 저장 단추 없이 즉시 적용, 쓰기 `site.manage`) — ⚠ 아래 세 줄도 **확정 전 후보**다:
 *       · `GET /api/admin/site/og-image` → 200 `{ configured, url|null, width|null, height|null, defaultUrl }`  (`site.read`)
 *       · `POST /api/admin/site/og-image` multipart `file` → 200 위와 같은 모양 · 400 VALIDATION(`fields.file` — PNG 서명 아님 → 2MB 초과 → 못 읽음 → 4000px 초과 → **비율 1200:630 ±2% 아님(자르지 않고 거절)** → 가로 1200px 미만 순. 비율이 크기 부족보다 먼저) · `fields.site`
 *       · `DELETE /api/admin/site/og-image` → 200 `{ configured: false, … }`
 *   - `GET /api/admin/site/display-defaults` → 200 `{ values: { language, zoneId, timeFormat, dateFormat } }`  (`site.read`)
 *   - `PUT /api/admin/site/display-defaults` `{ values }` → 200 저장된 모양  (`site.manage`) · 400 VALIDATION(`fields.language|zoneId|timeFormat|dateFormat`)
 *       ⚠ 서버에 이 값을 둘 곳이 아직 없다. 이 값은 개인 설정 탭의 "기본 설정"(내 표시 형식을 쓰지 않는 운영자의 표시 값)이므로 `GET /api/admin/profile/display` 의 `inherited` 가 이 값을 돌려줘야 한다.
 *       공개 사이트 회원의 사이트 기본 표시 값과 같은 값인지(한 곳에서 정하는지)도 미정이다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/site-settings-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    basic: '/api/admin/site/basic',
    displayDefaults: '/api/admin/site/display-defaults',
    logo: '/api/admin/site/logo',
    logoSvg: '/api/admin/site/logo/svg',
    files: '/api/admin/files',
    icon: '/api/admin/site/icon',
    iconSvg: '/api/admin/site/icon/svg',
    ogImage: '/api/admin/site/og-image',
};

/** 로고 그림을 올릴 때 서버에 알리는 용도 이름 */
const LOGO_PURPOSE = 'SITE_LOGO';

/** 올리기 본문 — 바이트는 JSON 이 아니라 multipart 다(Base64 로 부풀리지 않는다) */
function logoForm(file: File): FormData {
    const form = new FormData();
    form.append('file', file);
    form.append('purpose', LOGO_PURPOSE);
    return form;
}

/** 아이콘·링크 미리보기 이미지 올리기 본문 — `file` 한 파트(multipart) */
function imageForm(file: File): FormData {
    const form = new FormData();
    form.append('file', file);
    return form;
}

export function useSiteSettingsApi() {
    // REAL: const client = useApiClient();
    void PATH;
    void logoForm;
    void imageForm;

    return {
        /** 사이트 기본 정보(이름·한 줄 소개·로고) */
        basic: (): Promise<SiteBasicResponse> =>
            // MOCK:
            mockSiteBasicGet(),
        // REAL: client.get<SiteBasicResponse>(PATH.basic),

        /** 사이트 기본 정보(이름·한 줄 소개)를 저장한다 — 저장된 모양을 돌려준다. 로고는 따로(즉시 적용) */
        saveBasic: (body: SiteBasicRequest): Promise<SiteBasicResponse> =>
            // MOCK:
            mockSiteBasicSave(body),
        // REAL: client.put<SiteBasicResponse>(PATH.basic, body),

        /** 로고 그림 파일을 올린다 — 받은 `fileId` 로 `setLogoFile` 을 부른다(바이트와 로고 지정은 두 걸음) */
        uploadLogoFile: (file: File): Promise<SiteLogoFileUploadResponse> =>
            // MOCK:
            mockSiteLogoUpload(file),
        // REAL: client.post<SiteLogoFileUploadResponse>(PATH.files, logoForm(file)),  // FormData: file, purpose=LOGO_PURPOSE

        /** 올려 둔 그림을 로고로 삼는다 */
        setLogoFile: (body: SiteLogoFileRequest): Promise<SiteLogoResponse> =>
            // MOCK:
            mockSiteLogoFileSet(body),
        // REAL: client.put<SiteLogoResponse>(PATH.logo, body),

        /** SVG 소스를 로고로 삼는다 */
        setLogoSvg: (body: SiteLogoSvgRequest): Promise<SiteLogoResponse> =>
            // MOCK:
            mockSiteLogoSvgSet(body),
        // REAL: client.put<SiteLogoResponse>(PATH.logoSvg, body),

        /** 로고를 뗀다 — 이름 글자만 보이는 기본으로 돌아간다 */
        clearLogo: (): Promise<SiteLogoResponse> =>
            // MOCK:
            mockSiteLogoClear(),
        // REAL: client.del<SiteLogoResponse>(PATH.logo),

        /** 사이트 아이콘 상태(올려 둔 것이 있는지, 미리보기·기본 아이콘 주소) */
        icon: (): Promise<SiteIconResponse> =>
            // MOCK:
            mockSiteIconGet(),
        // REAL: client.get<SiteIconResponse>(PATH.icon),

        /** 아이콘 PNG 를 올린다 — 바로 적용되고 바뀐 상태를 돌려준다. 거절 문구는 `fields.file` */
        uploadIcon: (file: File): Promise<SiteIconResponse> =>
            // MOCK:
            mockSiteIconUpload(file),
        // REAL: client.post<SiteIconResponse>(PATH.icon, imageForm(file)),

        /** SVG 소스를 아이콘으로 삼는다 — 서버가 PNG 로 바꿔 저장한다. 거절 문구는 `fields.svgSource` */
        setIconSvg: (body: SiteIconSvgRequest): Promise<SiteIconResponse> =>
            // MOCK:
            mockSiteIconSvgSet(body),
        // REAL: client.put<SiteIconResponse>(PATH.iconSvg, body),

        /** 올려 둔 아이콘을 지우고 기본 아이콘으로 되돌린다 */
        clearIcon: (): Promise<SiteIconResponse> =>
            // MOCK:
            mockSiteIconClear(),
        // REAL: client.del<SiteIconResponse>(PATH.icon),

        /** 링크 미리보기 이미지 상태 */
        ogImage: (): Promise<SiteOgImageResponse> =>
            // MOCK:
            mockSiteOgImageGet(),
        // REAL: client.get<SiteOgImageResponse>(PATH.ogImage),

        /** 링크 미리보기 이미지 PNG 를 올린다 — 바로 적용되고 바뀐 상태를 돌려준다. 거절 문구는 `fields.file` */
        uploadOgImage: (file: File): Promise<SiteOgImageResponse> =>
            // MOCK:
            mockSiteOgImageUpload(file),
        // REAL: client.post<SiteOgImageResponse>(PATH.ogImage, imageForm(file)),

        /** 올려 둔 링크 미리보기 이미지를 지우고 기본 이미지로 되돌린다 */
        clearOgImage: (): Promise<SiteOgImageResponse> =>
            // MOCK:
            mockSiteOgImageClear(),
        // REAL: client.del<SiteOgImageResponse>(PATH.ogImage),

        /** 표시 기본값(언어·시간대·시간 형식·날짜 형식) */
        displayDefaults: (): Promise<SiteDisplayDefaultsResponse> =>
            // MOCK:
            mockSiteDisplayGet(),
        // REAL: client.get<SiteDisplayDefaultsResponse>(PATH.displayDefaults),

        /** 표시 기본값을 저장한다 — 저장된 모양을 돌려준다 */
        saveDisplayDefaults: (body: SiteDisplayDefaultsRequest): Promise<SiteDisplayDefaultsResponse> =>
            // MOCK:
            mockSiteDisplaySave(body),
        // REAL: client.put<SiteDisplayDefaultsResponse>(PATH.displayDefaults, body),
    };
}
