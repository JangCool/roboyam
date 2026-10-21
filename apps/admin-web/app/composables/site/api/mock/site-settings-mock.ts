// MOCK: 사이트 설정 API(`GET·PUT /api/admin/site/basic`, `GET·PUT /api/admin/site/display-defaults`, 로고 `POST /api/admin/files`·`PUT·DELETE /api/admin/site/logo`)의 목업 — `useSiteSettingsApi.ts` 가 이 파일의 함수를 부른다. 서버가 붙으면 `site/api/mock/` 와 함께 지운다.
// 이 파일이 **사이트 값의 원천**이다 — 다른 목업이 같은 값을 읽는다:
//   · 표시 기본값 = 개인 설정 탭의 "기본 설정"(제품 기준) → `profile-mock.ts` 가 `mockSiteDisplayValues()` 로 읽는다. 일반 화면에서 저장하면 `/me?tab=personal` 의 기본 설정 값이 바뀐다.
//   · 사이트 이름·로고 → 로그인 응답(`me`)의 `siteName`·`siteLogoUrl` 은 이제 실제 서버가 준다(로고는 서버가 아직 늘 null). 이 목업의 이름·로고는 일반 화면 안에서만 보이고, 저장하면 그 방문 동안 `useAdminAuth` 의 상단 표시를 바꾼다(새로고침하면 서버 값으로 돌아온다).
//   · 사이트 아이콘·링크 미리보기 이미지 → 이 파일 안에서만 쓴다(일반 화면의 두 줄). 그림이 커서(2MB 까지) 이름·로고 저장과 따로 localStorage(`roboyam:mock-site-images`)에 둔다 — 못 적으면 이번 방문 동안만 남는다.
// 값은 메모리 + 이 브라우저의 localStorage(`roboyam:mock-site-settings`)에 둔다 — 서버라면 `site_settings` 행이다. 서버가 붙으면 이 저장도 함께 지운다.
//
// ── 처음 값 ─ 사이트 이름 `SAMPLE` · 한 줄 소개 없음 · 표시 기본값 한국어 / Asia/Seoul / 24시간 / `YYYY-MM-DD`
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   site-error        사이트 설정을 읽는 요청(기본 정보·표시 기본값 둘 다)이 `500 INTERNAL` 로 실패(구획이 오류 상태와 다시 시도를 보인다)
//   site-save-error   저장 요청(둘 다)이 `500 INTERNAL` 로 실패(구획 안 오류 문구)
//
// ── 서버 규칙 흉내 ─
//   권한: 읽기 `site.read` · 저장 `site.manage` — 없으면 `403 PERMISSION_DENIED`(실제 로그인한 운영자의 권한 기준).
//   로고: 올리기는 PNG·JPG·GIF·WEBP 2MB 이하(`fields.file`), SVG 는 소스 글로만 100,000자 이하·`<svg` 포함(`fields.svgSource`) — 파일이 아니라 소스로 받는 이유는 SVG 가 스크립트를 품을 수 있는 문서라서다. 즉시 적용(저장 단추 없음), 권한은 저장과 같은 `site.manage`.
//   아이콘(`GET·POST·DELETE /api/admin/site/icon`, SVG `PUT …/icon/svg`): PNG 만, 2MB 이하, 정사각, 한 변 512~4000px — 서버가 파일 첫 바이트로 순서대로 걸러 첫 문제 하나를 `fields.file` 로 준다(빈 파일 → 형식 → 용량 → 읽기 → 너무 큼 → 정사각 아님 → 너무 작음, 지금 크기 포함).
//     SVG 는 소스 글로만(`fields.svgSource`): 로고와 같은 검사 + 서버 몫인 정사각 여부·`<script>` 요소(목업은 아주 단순하게만 흉내 낸다 — 진짜 판정은 서버). 올리면 서버가 PNG 로 바꿔 저장하지만 목업은 SVG 그림 그대로 둔다.
//   링크 미리보기 이미지(`GET·POST·DELETE /api/admin/site/og-image`): PNG 만, 2MB 이하, 가로 1200~4000px(세로도 4000 이하), 비율 1200:630 ±2% — 안 맞으면 자르지 않고 거절(비율 검사가 크기 부족 검사보다 먼저). 서버가 1200×630 으로 맞춰 저장하지만 목업은 올린 그림 그대로 두고 1200×630 이라 답한다.
//   기본 정보: 사이트 이름 1~50자(앞뒤 공백 뗀 뒤)·`<` `>` 제어 문자 없음(`fields.siteName`), 한 줄 소개 0~100자·`<` `>` 없음(`fields.tagline`). 비운 소개는 null.
//   표시 기본값: 시간대·시간 형식·날짜 형식·언어가 허용된 값이어야 한다(`UtilDisplayValues.sanitize`, 틀리면 `400 VALIDATION` + `fields.zoneId`).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockCurrentAdmin } from '~/composables/auth/api/mock/admin-auth-mock';
import type {
    DisplayValues,
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
import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import UtilSiteImage from '~/utils/util-site-image';
import UtilSiteSetting from '~/utils/util-site-setting';

const STORAGE_KEY = 'roboyam:mock-site-settings';

interface Stored {
    siteName: string;
    tagline: string | null;
    /** 서버라면 `/api/files/{fileId}` 주소 또는 서버가 만든 SVG data URI — 목업은 둘 다 data URI 로 든다 */
    logoUrl: string | null;
    display: DisplayValues;
}

/** 서버가 처음 주는 값(설치 직후) */
function initial(): Stored {
    return { siteName: 'SAMPLE', tagline: null, logoUrl: null, display: { language: 'ko', zoneId: 'Asia/Seoul', timeFormat: 'H24', dateFormat: 'YMD_DASH' } };
}

/** 올려 둔 그림(`fileId` → data URI) — 서버라면 파일 저장소. 목업은 메모리에만 둔다(새로고침하면 사라진다) */
const uploadedFiles = new Map<string, string>();

let memory: Stored | null = null;

function read(): Stored {
    if (memory) return memory;
    let stored: Stored = initial();
    try {
        const raw = window.localStorage.getItem(STORAGE_KEY);
        if (raw) {
            const parsed = JSON.parse(raw) as Partial<Stored>;
            const display = UtilDisplayValues.sanitize(parsed.display);
            stored = {
                siteName: typeof parsed.siteName === 'string' && parsed.siteName ? parsed.siteName : stored.siteName,
                tagline: typeof parsed.tagline === 'string' && parsed.tagline ? parsed.tagline : null,
                logoUrl: typeof parsed.logoUrl === 'string' && parsed.logoUrl.startsWith('data:image/') ? parsed.logoUrl : null,
                display: display ?? stored.display,
            };
        }
    } catch {
        /* 사생활 보호 모드 등 — 처음 값으로 */
    }
    memory = stored;
    return stored;
}

function write(next: Stored): void {
    memory = next;
    try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
        /* 못 적으면 이번 방문 동안만 메모리에 */
    }
}

/** 다른 목업이 읽는 사이트 표시 기본값 — 개인 설정 탭의 "기본 설정"(제품 기준). 복사본을 준다 */
export function mockSiteDisplayValues(): DisplayValues {
    return { ...read().display };
}

function denied(): ApiError {
    return new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });
}

/** 로그인한 운영자가 이 권한을 가졌는지 — 아니면 401/403 */
function requirePermission(code: string): void {
    const user = mockCurrentAdmin();
    if (!user) throw MOCK_ERRORS.unauthenticated();
    if (!user.isSuper && !user.permissions.includes(code)) throw denied();
}

function validation(fields: Record<string, string>): ApiError {
    return new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields });
}

/** `GET /api/admin/site/basic` */
export async function mockSiteBasicGet(): Promise<SiteBasicResponse> {
    await mockDelay();
    if (mockFlags().has('site-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.read');
    const { siteName, tagline, logoUrl } = read();
    return { siteName, tagline, logoUrl };
}

/** `PUT /api/admin/site/basic` — 저장된 모양을 돌려준다 */
export async function mockSiteBasicSave(body: SiteBasicRequest): Promise<SiteBasicResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const siteName = typeof body.siteName === 'string' ? body.siteName.trim() : '';
    const tagline = typeof body.tagline === 'string' ? body.tagline.trim() : '';
    const fields: Record<string, string> = {};
    if (UtilSiteSetting.siteName(siteName)) fields.siteName = `사이트 이름은 1~${UtilSiteSetting.NAME_MAX}자로, < 와 > 없이 입력해 주세요.`;
    if (UtilSiteSetting.tagline(tagline)) fields.tagline = `한 줄 소개는 ${UtilSiteSetting.TAGLINE_MAX}자 이하로, < 와 > 없이 입력해 주세요.`;
    if (Object.keys(fields).length > 0) throw validation(fields);
    write({ ...read(), siteName, tagline: tagline || null });
    return { siteName, tagline: tagline || null, logoUrl: read().logoUrl };
}

/** SVG 소스를 `<img src>` 에 넣을 data URI 로 — 서버가 하는 일이다. `xmlns` 가 없으면 넣는다(없으면 `<img>` 에서 그림이 안 뜬다). `<img>` 안에서는 스크립트가 돌지 못한다 */
function svgToDataUri(source: string): string {
    const svg = source.toLowerCase().includes('xmlns') ? source : source.replace(/<svg/i, '<svg xmlns="http://www.w3.org/2000/svg"');
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

function readAsDataUri(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(MOCK_ERRORS.internal());
        reader.readAsDataURL(file);
    });
}

/** `POST /api/admin/files` (purpose=SITE_LOGO, multipart) — 형식·용량을 서버가 다시 거른다. 파일 이름은 쓰지 않는다 */
export async function mockSiteLogoUpload(file: File): Promise<SiteLogoFileUploadResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const problem = UtilSiteSetting.logoFile(file);
    if (problem === 'EMPTY') throw validation({ file: '비어 있는 파일이에요.' });
    if (problem === 'TYPE') throw validation({ file: 'PNG·JPG·GIF·WEBP 그림만 올릴 수 있어요.' });
    if (problem === 'SIZE') throw validation({ file: '그림은 2MB 까지 올릴 수 있어요.' });
    const fileId = `mock-file-${Date.now()}-${uploadedFiles.size}`;
    uploadedFiles.set(fileId, await readAsDataUri(file));
    return { fileId };
}

/** `PUT /api/admin/site/logo` — 올려 둔 그림을 로고로 삼는다. 없는 `fileId` 는 400 */
export async function mockSiteLogoFileSet(body: SiteLogoFileRequest): Promise<SiteLogoResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const dataUri = uploadedFiles.get(body.fileId);
    if (!dataUri) throw validation({ fileId: '올린 파일을 찾을 수 없어요. 다시 올려 주세요.' });
    write({ ...read(), logoUrl: dataUri });
    return { logoUrl: dataUri };
}

/** `PUT /api/admin/site/logo/svg` — SVG 소스를 로고로 삼는다(비었음·100,000자 초과·`<svg` 없음은 400) */
export async function mockSiteLogoSvgSet(body: SiteLogoSvgRequest): Promise<SiteLogoResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const source = typeof body.svgSource === 'string' ? body.svgSource.trim() : '';
    const problem = UtilSiteSetting.logoSvg(source);
    if (problem === 'EMPTY') throw validation({ svgSource: 'SVG 소스를 입력해 주세요.' });
    if (problem === 'LENGTH') throw validation({ svgSource: `SVG 소스는 ${UtilSiteSetting.SVG_MAX_LENGTH.toLocaleString('en-US')}자까지예요.` });
    if (problem === 'NOT_SVG') throw validation({ svgSource: '“<svg>” 로 시작하는 마크업이 아니에요.' });
    const dataUri = svgToDataUri(source);
    write({ ...read(), logoUrl: dataUri });
    return { logoUrl: dataUri };
}

/** `DELETE /api/admin/site/logo` — 로고를 뗀다(기본: 이름 글자만) */
export async function mockSiteLogoClear(): Promise<SiteLogoResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    write({ ...read(), logoUrl: null });
    return { logoUrl: null };
}

/* ── 사이트 아이콘 · 링크 미리보기 이미지 ─────────────── */

const IMAGES_KEY = 'roboyam:mock-site-images';

/** 올려 둔 그림(data URI). 없으면 null = 기본 — 서버라면 `site_settings` 의 아이콘·og 토큰과 이미지 저장소 */
interface StoredImages {
    iconUrl: string | null;
    ogUrl: string | null;
}

/** 서버가 주는 기본 아이콘·기본 링크 이미지 주소 대신 쓰는 단순한 그림(자산 파일 없이) */
const DEFAULT_ICON_URL = `data:image/svg+xml;charset=utf-8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" fill="#64748b"/><circle cx="32" cy="32" r="14" fill="#f1f5f9"/></svg>')}`;
const DEFAULT_OG_URL = `data:image/svg+xml;charset=utf-8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1200 630"><rect width="1200" height="630" fill="#64748b"/><circle cx="600" cy="315" r="110" fill="#f1f5f9"/></svg>')}`;

let imagesMemory: StoredImages | null = null;

function readImages(): StoredImages {
    if (imagesMemory) return imagesMemory;
    let stored: StoredImages = { iconUrl: null, ogUrl: null };
    try {
        const raw = window.localStorage.getItem(IMAGES_KEY);
        if (raw) {
            const parsed = JSON.parse(raw) as Partial<StoredImages>;
            const ok = (v: unknown): v is string => typeof v === 'string' && v.startsWith('data:image/');
            stored = { iconUrl: ok(parsed.iconUrl) ? parsed.iconUrl : null, ogUrl: ok(parsed.ogUrl) ? parsed.ogUrl : null };
        }
    } catch {
        /* 사생활 보호 모드 등 — 처음 값으로 */
    }
    imagesMemory = stored;
    return stored;
}

function writeImages(next: StoredImages): void {
    imagesMemory = next;
    try {
        window.localStorage.setItem(IMAGES_KEY, JSON.stringify(next));
    } catch {
        /* 용량 초과 등 — 이번 방문 동안만 메모리에 */
    }
}

function iconView(): SiteIconResponse {
    const { iconUrl } = readImages();
    return { custom: iconUrl !== null, previewUrl: iconUrl, defaultPreviewUrl: DEFAULT_ICON_URL };
}

function ogView(): SiteOgImageResponse {
    const { ogUrl } = readImages();
    return ogUrl ? { configured: true, url: ogUrl, width: UtilSiteImage.OG_WIDTH, height: UtilSiteImage.OG_HEIGHT, defaultUrl: DEFAULT_OG_URL } : { configured: false, url: null, width: null, height: null, defaultUrl: DEFAULT_OG_URL };
}

/** `GET /api/admin/site/icon` */
export async function mockSiteIconGet(): Promise<SiteIconResponse> {
    await mockDelay();
    if (mockFlags().has('site-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.read');
    return iconView();
}

/** `POST /api/admin/site/icon` (multipart `file`) — 파일 첫 바이트로 순서대로 걸러 첫 문제 하나를 `fields.file` 로 준다. 파일 이름은 쓰지 않는다 */
export async function mockSiteIconUpload(file: File): Promise<SiteIconResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const { problem, dims } = await UtilSiteImage.inspect('icon', file);
    const now = dims ? ` (지금 ${dims.width}×${dims.height})` : '';
    if (problem === 'EMPTY') throw validation({ file: '올릴 이미지를 선택해 주세요.' });
    if (problem === 'FORMAT') throw validation({ file: 'PNG 이미지만 올릴 수 있어요. (SVG·JPEG·WebP·GIF 는 안 돼요)' });
    if (problem === 'SIZE') throw validation({ file: `사이트 아이콘은 2MB 이하만 올릴 수 있어요. (지금 ${(Math.ceil((file.size / (1024 * 1024)) * 10) / 10).toFixed(1)}MB)` });
    if (problem === 'CORRUPT') throw validation({ file: '이미지를 읽을 수 없어요. 다른 파일로 올려 주세요.' });
    if (problem === 'TOO_BIG') throw validation({ file: `이미지가 너무 커요. 가로·세로 ${UtilSiteImage.MAX_SIDE}px 이하로 줄여 올려 주세요.${now}` });
    if (problem === 'NOT_SQUARE') throw validation({ file: `정사각형 이미지만 올릴 수 있어요. 가로와 세로가 같아야 해요.${now}` });
    if (problem === 'TOO_SMALL') throw validation({ file: `이미지가 너무 작아요. 한 변이 ${UtilSiteImage.ICON_MIN_SIDE}px 이상이어야 해요.${now}` });
    writeImages({ ...readImages(), iconUrl: await readAsDataUri(file) });
    return iconView();
}

/** 소스에서 숫자 속성 하나를 읽는다(`width="24px"` → 24). `%` 처럼 기준이 없는 값은 null */
function svgNumber(source: string, name: string): number | null {
    const match = new RegExp(`<svg[^>]*\\s${name}\\s*=\\s*["']\\s*([0-9.]+)\\s*(?:px|pt|pc|mm|cm|in|em|ex)?\\s*["']`, 'i').exec(source);
    return match ? Number(match[1]) : null;
}

/** `PUT /api/admin/site/icon/svg` — 로고와 같은 소스 검사 + 서버 몫인 정사각 여부·`<script>`(진짜 서버는 더 많이 본다). 서버가 PNG 로 바꿔 저장하지만 목업은 SVG 그림 그대로 둔다 */
export async function mockSiteIconSvgSet(body: SiteIconSvgRequest): Promise<SiteIconResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const source = typeof body.svgSource === 'string' ? body.svgSource.trim() : '';
    const problem = UtilSiteSetting.logoSvg(source);
    if (problem === 'EMPTY') throw validation({ svgSource: 'SVG 소스를 입력해 주세요.' });
    if (problem === 'LENGTH') throw validation({ svgSource: `SVG 가 너무 길어요. ${UtilSiteSetting.SVG_MAX_LENGTH.toLocaleString('en-US')}자 이하만 올릴 수 있어요. (지금 ${source.length.toLocaleString('en-US')}자)` });
    if (problem === 'NOT_SVG') throw validation({ svgSource: 'SVG 가 아니에요. 가장 바깥 요소가 <svg> 여야 해요.' });
    const viewBox = /<svg[^>]*\sviewBox\s*=\s*["']\s*([-0-9.]+)[\s,]+([-0-9.]+)[\s,]+([0-9.]+)[\s,]+([0-9.]+)\s*["']/i.exec(source);
    const width = viewBox ? Number(viewBox[3]) : svgNumber(source, 'width');
    const height = viewBox ? Number(viewBox[4]) : svgNumber(source, 'height');
    if (width === null || height === null || !(width > 0) || !(height > 0)) throw validation({ svgSource: '정사각인지 알 수 없어요. viewBox 나 width·height 를 넣어 주세요.' });
    if (Math.abs(width - height) / Math.max(width, height) > 0.0001) throw validation({ svgSource: `정사각형 SVG 만 올릴 수 있어요. 가로와 세로가 같아야 해요. (지금 ${width}×${height})` });
    if (/<script[\s>/]/i.test(source)) throw validation({ svgSource: '<script> 요소는 쓸 수 없어요.' });
    writeImages({ ...readImages(), iconUrl: svgToDataUri(source) });
    return iconView();
}

/** `DELETE /api/admin/site/icon` — 올려 둔 아이콘을 지운다. 이미 기본이면 그대로 200 */
export async function mockSiteIconClear(): Promise<SiteIconResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    writeImages({ ...readImages(), iconUrl: null });
    return iconView();
}

/** `GET /api/admin/site/og-image` */
export async function mockSiteOgImageGet(): Promise<SiteOgImageResponse> {
    await mockDelay();
    if (mockFlags().has('site-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.read');
    return ogView();
}

/** `POST /api/admin/site/og-image` (multipart `file`) — 비율 검사가 크기 부족 검사보다 먼저다(모양부터 고치게). 맞지 않으면 자르지 않고 거절한다 */
export async function mockSiteOgImageUpload(file: File): Promise<SiteOgImageResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const { problem, dims } = await UtilSiteImage.inspect('og', file);
    const size = dims ? `${dims.width}×${dims.height}` : '';
    if (problem === 'EMPTY') throw validation({ file: '올릴 이미지를 선택해 주세요.' });
    if (problem === 'FORMAT') throw validation({ file: 'PNG 이미지만 올릴 수 있어요. (SVG·JPEG·WebP·GIF 는 안 돼요)' });
    if (problem === 'SIZE') throw validation({ file: `링크 미리보기 이미지는 2MB 이하만 올릴 수 있어요. (지금 ${(Math.ceil((file.size / (1024 * 1024)) * 10) / 10).toFixed(1)}MB)` });
    if (problem === 'CORRUPT') throw validation({ file: '이미지를 읽을 수 없어요. 다른 파일로 올려 주세요.' });
    if (problem === 'TOO_BIG') throw validation({ file: `이미지가 너무 커요. 가로·세로 ${UtilSiteImage.MAX_SIDE}px 이하로 줄여 올려 주세요. (지금 ${size})` });
    if (problem === 'WRONG_RATIO') throw validation({ file: `링크 미리보기 이미지는 ${UtilSiteImage.OG_WIDTH}×${UtilSiteImage.OG_HEIGHT} 비율(가로:세로 약 1.91:1)이어야 해요. 지금 올린 그림은 ${size} 이에요.` });
    if (problem === 'TOO_SMALL') throw validation({ file: `이미지가 너무 작아요. 가로가 ${UtilSiteImage.OG_MIN_WIDTH}px 이상이어야 해요. (지금 ${size})` });
    writeImages({ ...readImages(), ogUrl: await readAsDataUri(file) });
    return ogView();
}

/** `DELETE /api/admin/site/og-image` — 올려 둔 이미지를 지운다. 이미 기본이면 그대로 200 */
export async function mockSiteOgImageClear(): Promise<SiteOgImageResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    writeImages({ ...readImages(), ogUrl: null });
    return ogView();
}

/** `GET /api/admin/site/display-defaults` */
export async function mockSiteDisplayGet(): Promise<SiteDisplayDefaultsResponse> {
    await mockDelay();
    if (mockFlags().has('site-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.read');
    return { values: mockSiteDisplayValues() };
}

/** `PUT /api/admin/site/display-defaults` — 저장된 모양을 돌려준다 */
export async function mockSiteDisplaySave(body: SiteDisplayDefaultsRequest): Promise<SiteDisplayDefaultsResponse> {
    await mockDelay();
    if (mockFlags().has('site-save-error')) throw MOCK_ERRORS.internal();
    requirePermission('site.manage');
    const values = UtilDisplayValues.sanitize(body.values);
    if (!values) throw validation({ zoneId: '쓸 수 없는 시간대예요.' });
    write({ ...read(), display: values });
    return { values: { ...values } };
}
