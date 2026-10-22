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
    SiteThemeRequest,
    SiteThemeResponse,
} from '~/composables/site/api/useSiteSettingsApi.types';

/**
 * 사이트 설정 API — 서버 주소 문자열(`/api/admin/site…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 값은 일반 화면의 `ref`).
 *
 * 계약(서버 `com.community.site.controller.*`·`media.controller.FileController`, `docs/features/site-settings-contract.md`): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET /api/admin/site/basic` → 200 `{ siteName, tagline|null, logoUrl|null }`  (`site.read`)
 *   - `PUT /api/admin/site/basic` `{ siteName, tagline|null }` → 200 저장된 모양  (`site.manage`)
 *       · 400 VALIDATION(`fields.siteName` 1~50자·`<` `>` 제어 문자 없음, `fields.tagline` 0~100자·`<` `>` 없음, 둘 다 한꺼번에) · 403 PERMISSION_DENIED
 *   - 로고(저장 단추 없이 즉시 적용, `site.manage`) — 바이트와 "로고로 삼기"를 두 걸음으로 가른다(폼 JSON 과 multipart 를 한 API 가 받지 않는다):
 *       · `POST /api/admin/files` multipart `file`·`purpose=SITE_LOGO` → **201** `{ fileId }` · 400 VALIDATION(`fields.file`·`fields.purpose`)
 *       · `PUT /api/admin/site/logo` `{ fileId }` → 200 `{ logoUrl }`(`/media/…` 상대 주소) · 400 VALIDATION(`fields.fileId` 없는 파일)
 *       · `PUT /api/admin/site/logo/svg` `{ svgSource }` → 200 `{ logoUrl }`(서버가 만든 SVG data URI — 화면은 `<img src>` 에만 넣고 `v-html` 을 쓰지 않는다) · 400 VALIDATION(`fields.svgSource` — 위험 요소는 정화하지 않고 거절)
 *       · `DELETE /api/admin/site/logo` → 200 `{ logoUrl: null }`
 *       ⚠ 이 로고는 관리자 화면 상단(`me` 의 `siteLogoUrl`)과 공개 사이트 로고 자리가 같이 쓴다.
 *   - 사이트 아이콘(저장 단추 없이 즉시 적용) — 서버가 그림을 여러 크기로 다시 만들어 저장하므로 바이트를 곧바로 `POST` 한다(한 걸음):
 *       · `GET /api/admin/site/icon` → 200 `{ custom, previewUrl|null, defaultPreviewUrl }`  (`site.read`)
 *       · `POST /api/admin/site/icon` multipart `file` → 200 위와 같은 모양 · 400 VALIDATION(`fields.file` — 첫 걸림 하나, 지금 크기·용량 포함)  (`site.manage`)
 *       · `PUT /api/admin/site/icon/svg` `{ svgSource }` → 200 위와 같은 모양 · 400 VALIDATION(`fields.svgSource` — 정사각 여부·위험 요소·그릴 수 있는지는 서버가 판정)
 *       · `DELETE /api/admin/site/icon` → 200 `{ custom: false, … }`. 이미 기본이어도 200
 *   - 링크 미리보기 이미지(`og:image`, 저장 단추 없이 즉시 적용):
 *       · `GET /api/admin/site/og-image` → 200 `{ configured, url|null, width|null, height|null, defaultUrl }`  (`site.read`) — `width`·`height` 는 올린 그림 크기가 아니라 서버가 맞춘 1200×630 이다
 *       · `POST /api/admin/site/og-image` multipart `file` → 200 위와 같은 모양 · 400 VALIDATION(`fields.file` — 비율 1200:630 ±2% 아니면 자르지 않고 거절)  (`site.manage`)
 *       · `DELETE /api/admin/site/og-image` → 200 `{ configured: false, … }`
 *   - `GET /api/admin/site/display-defaults` → 200 `{ values: { language, zoneId, timeFormat, dateFormat } }`  (`site.read`)
 *   - `PUT /api/admin/site/display-defaults` `{ values }` → 200 저장된 모양  (`site.manage`) · 400 VALIDATION(`fields.language|zoneId|timeFormat|dateFormat`, 시간대는 JDK 가 아는 IANA 이름만)
 *       이 값이 개인 설정 탭의 "기본 설정"(`GET /api/admin/profile/display` 의 `inherited`)이다 — 서버가 요청마다 읽어 주므로 저장 뒤 내 표시 값을 다시 받으면 따라온다(`general.vue` 가 저장 직후 `useMyDisplay().load()`).
 *   - `GET /api/admin/site/theme` → 200 `{ theme, themes: string[] }`  (`site.read`) — `themes` 는 고를 수 있는 id(보일 순서). 이름은 화면이 id 로 i18n 키를 찾는다
 *   - `PUT /api/admin/site/theme` `{ theme }` → 200 같은 모양  (`site.manage`) · 400 VALIDATION(`fields.theme` 목록 밖·빈 값, `fields.site` 설정 행 없음)
 *       공개 사이트(`GET /api/site/theme`)는 서버 사본(20초) + 브라우저 캐시(60초) 뒤에 따라온다. 같은 값 저장은 감사·알림을 남기지 않는다.
 *
 * 그 밖의 오류: 503 `STORAGE_UNAVAILABLE`(저장소에 쓰지 못함) · 503 `IMAGE_BUSY`(이미지 동시 처리 자리 없음) · 413 `FILE_TOO_LARGE`(multipart 10MB 초과; 2MB 초과는 `fields.file` 문구로 안내) · 403 `PERMISSION_DENIED`.
 * 이미지 주소(`logoUrl`·`previewUrl`·`url`·`defaultPreviewUrl`·`defaultUrl`)는 `/media/…` 상대 주소다 — 운영은 같은 도메인에서 서빙되고, 개발 서버는 `nuxt.config.ts` 의 `/media` 프록시가 잇는다.
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
    theme: '/api/admin/site/theme',
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
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 사이트 기본 정보(이름·한 줄 소개·로고) */
        basic: (): Promise<SiteBasicResponse> => client.get<SiteBasicResponse>(PATH.basic),

        /** 사이트 기본 정보(이름·한 줄 소개)를 저장한다 — 저장된 모양을 돌려준다. 로고는 따로(즉시 적용) */
        saveBasic: (body: SiteBasicRequest): Promise<SiteBasicResponse> => client.put<SiteBasicResponse>(PATH.basic, body),

        /** 로고 그림 파일을 올린다 — 받은 `fileId` 로 `setLogoFile` 을 부른다(바이트와 로고 지정은 두 걸음) */
        uploadLogoFile: (file: File): Promise<SiteLogoFileUploadResponse> => client.post<SiteLogoFileUploadResponse>(PATH.files, logoForm(file)),

        /** 올려 둔 그림을 로고로 삼는다 */
        setLogoFile: (body: SiteLogoFileRequest): Promise<SiteLogoResponse> => client.put<SiteLogoResponse>(PATH.logo, body),

        /** SVG 소스를 로고로 삼는다 */
        setLogoSvg: (body: SiteLogoSvgRequest): Promise<SiteLogoResponse> => client.put<SiteLogoResponse>(PATH.logoSvg, body),

        /** 로고를 뗀다 — 이름 글자만 보이는 기본으로 돌아간다 */
        clearLogo: (): Promise<SiteLogoResponse> => client.del<SiteLogoResponse>(PATH.logo),

        /** 사이트 아이콘 상태(올려 둔 것이 있는지, 미리보기·기본 아이콘 주소) */
        icon: (): Promise<SiteIconResponse> => client.get<SiteIconResponse>(PATH.icon),

        /** 아이콘 PNG 를 올린다 — 바로 적용되고 바뀐 상태를 돌려준다. 거절 문구는 `fields.file` */
        uploadIcon: (file: File): Promise<SiteIconResponse> => client.post<SiteIconResponse>(PATH.icon, imageForm(file)),

        /** SVG 소스를 아이콘으로 삼는다 — 서버가 PNG 로 바꿔 저장한다. 거절 문구는 `fields.svgSource` */
        setIconSvg: (body: SiteIconSvgRequest): Promise<SiteIconResponse> => client.put<SiteIconResponse>(PATH.iconSvg, body),

        /** 올려 둔 아이콘을 지우고 기본 아이콘으로 되돌린다 */
        clearIcon: (): Promise<SiteIconResponse> => client.del<SiteIconResponse>(PATH.icon),

        /** 링크 미리보기 이미지 상태 */
        ogImage: (): Promise<SiteOgImageResponse> => client.get<SiteOgImageResponse>(PATH.ogImage),

        /** 링크 미리보기 이미지 PNG 를 올린다 — 바로 적용되고 바뀐 상태를 돌려준다. 거절 문구는 `fields.file` */
        uploadOgImage: (file: File): Promise<SiteOgImageResponse> => client.post<SiteOgImageResponse>(PATH.ogImage, imageForm(file)),

        /** 올려 둔 링크 미리보기 이미지를 지우고 기본 이미지로 되돌린다 */
        clearOgImage: (): Promise<SiteOgImageResponse> => client.del<SiteOgImageResponse>(PATH.ogImage),

        /** 표시 기본값(언어·시간대·시간 형식·날짜 형식) */
        displayDefaults: (): Promise<SiteDisplayDefaultsResponse> => client.get<SiteDisplayDefaultsResponse>(PATH.displayDefaults),

        /** 표시 기본값을 저장한다 — 저장된 모양을 돌려준다 */
        saveDisplayDefaults: (body: SiteDisplayDefaultsRequest): Promise<SiteDisplayDefaultsResponse> => client.put<SiteDisplayDefaultsResponse>(PATH.displayDefaults, body),

        /** 공개 사이트 테마(지금 값과 고를 수 있는 id 목록) */
        theme: (): Promise<SiteThemeResponse> => client.get<SiteThemeResponse>(PATH.theme),

        /** 공개 사이트 테마를 저장한다 — 저장된 모양을 돌려준다 */
        saveTheme: (body: SiteThemeRequest): Promise<SiteThemeResponse> => client.put<SiteThemeResponse>(PATH.theme, body),
    };
}
