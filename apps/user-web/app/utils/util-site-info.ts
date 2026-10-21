import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import type { SiteIconResponse } from '~/composables/site/api/useSiteInfoApi.types';
import type { SiteInfo } from '~/composables/site/useSiteInfo.types';
import UtilSiteName from '~/utils/util-site-name';

/**
 * 공개 사이트 정보(`GET /api/site/info`)의 순수 규칙 — 서버 응답에서 쓸 수 있는 칸만 걸러 낸다. 상태도 요청도 없다
 * (상태는 `useSiteInfo`, 서버 호출은 `useSiteInfoApi`).
 *
 * 서버가 준 이름·소개는 이스케이프하지 않은 **원문**이다. 화면은 텍스트 보간으로만 그리고(`v-html` 금지), 여기서는 서버 규칙(`SitePolicy`)과 같은 모양만 받는다.
 * 주소(`logoUrl`·아이콘)는 `<img src>`·`<link href>` 에 들어가므로 같은 출처의 상대 주소(`/media/…`)·`http(s)` 주소·이미지 data URI 만 받는다(`javascript:` 등은 버린다).
 */

/** 한 줄 소개 길이 상한 — 서버와 같다(0~100자) */
const TAGLINE_MAX_LENGTH = 100;

/** 주소 길이 상한 — SVG 로고의 data URI 는 최대 약 133KB 라 그보다 넉넉히 */
const URL_MAX_LENGTH = 200_000;

/** 로고 data URI 로 받는 그림 형식 — 서버는 SVG 만 이 모양으로 주지만 형식이 늘어도 그림이면 받는다 */
const IMAGE_DATA_URI = /^data:image\/(?:png|jpeg|gif|webp|svg\+xml);base64,[A-Za-z0-9+/=]+$/;

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 주소 한 개 — 같은 출처 상대 주소(`/…`, `//` 제외) 또는 `http(s)://`. 아니면 null */
function plainUrl(value: unknown): string | null {
    if (typeof value !== 'string') return null;
    const url = value.trim();
    if (url === '' || url.length > URL_MAX_LENGTH || /[\u0000-\u001f\u007f\s]/.test(url)) return null;
    if (url.startsWith('/') && !url.startsWith('//')) return url;
    return /^https?:\/\/[^/]/i.test(url) ? url : null;
}

/** `<head>` 에 넣을 링크 한 개의 모양 — `useHead({ link })` 에 그대로 준다 */
export type SiteHeadLink = { rel: 'icon'; href: string; type: 'image/png'; sizes: string } | { rel: 'apple-touch-icon'; href: string; sizes: string } | { rel: 'manifest'; href: string };

export default class UtilSiteInfo {
    /** 사이트 정보를 담는 공유 상태(`useState`)의 키 — `undefined` 는 아직 안 물어봄, `null` 은 못 받음·쓸 수 없는 응답 */
    static readonly STATE_KEY = 'site-info';

    /**
     * 한 줄 소개로 쓸 수 있는 값인가 — 1~100자, `<`·`>`·제어 문자 없음(서버 `SitePolicy` 와 같다). 앞뒤 공백은 뗀다.
     * @param {unknown} value - 후보 값.
     * @returns {string | null} 앞뒤 공백을 뗀 소개. 없거나 쓸 수 없으면 null.
     */
    static cleanTagline(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        const text = value.trim();
        if (text === '' || Array.from(text).length > TAGLINE_MAX_LENGTH) return null;
        // eslint-disable-next-line no-control-regex
        if (/[<>\u0000-\u001f\u007f]/.test(text)) return null;
        return text;
    }

    /**
     * 로고 주소로 쓸 수 있는 값인가 — 상대 주소·`http(s)` 주소 또는 이미지 data URI(SVG 로고).
     * @param {unknown} value - 후보 값.
     * @returns {string | null} 주소. 없거나 쓸 수 없으면 null.
     */
    static cleanLogoUrl(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        const url = value.trim();
        if (url.length <= URL_MAX_LENGTH && IMAGE_DATA_URI.test(url)) return url;
        return plainUrl(url);
    }

    /**
     * 아이콘 주소 묶음을 읽는다 — 여섯 주소가 모두 쓸 수 있어야 한다(하나라도 깨졌으면 올린 아이콘이 없는 것으로 본다).
     * @param {unknown} raw - 응답의 `icon`.
     * @returns {SiteIconResponse | null} 아이콘. 없거나 깨졌으면 null.
     */
    static cleanIcon(raw: unknown): SiteIconResponse | null {
        if (!isObject(raw)) return null;
        const version = typeof raw.version === 'string' ? raw.version.trim() : '';
        const favicon32 = plainUrl(raw.favicon32);
        const favicon48 = plainUrl(raw.favicon48);
        const appleTouch = plainUrl(raw.appleTouch);
        const icon192 = plainUrl(raw.icon192);
        const icon512 = plainUrl(raw.icon512);
        if (!version || !favicon32 || !favicon48 || !appleTouch || !icon192 || !icon512) return null;
        return { version, favicon32, favicon48, appleTouch, icon192, icon512 };
    }

    /**
     * 서버 응답에서 화면이 쓸 칸만 꺼낸다.
     * @param {unknown} raw - `GET /api/site/info` 응답(`data`).
     * @returns {SiteInfo | null} 걸러 낸 정보. 응답이 객체가 아니면 null.
     */
    static fromResponse(raw: unknown): SiteInfo | null {
        if (!isObject(raw)) return null;
        return {
            siteName: UtilSiteName.clean(raw.siteName),
            tagline: UtilSiteInfo.cleanTagline(raw.tagline),
            logoUrl: UtilSiteInfo.cleanLogoUrl(raw.logoUrl),
            icon: UtilSiteInfo.cleanIcon(raw.icon),
            // 서버 값(`H24`·`YMD_DASH` 등)은 공유 레이어의 표시 값과 같은 이름이라 변환 없이 검사만 한다
            displayDefaults: UtilDisplayValues.sanitize(raw.displayDefaults),
        };
    }

    /**
     * **개발 서버 전용** 탭 아이콘·manifest 링크 — 운영에서는 서버가 `index.html` 의 `<head>` 를 채우므로 화면이 넣지 않는다(`app.vue` 가 `import.meta.dev` 일 때만 부른다).
     * @param {SiteInfo | null | undefined} info - 사이트 정보.
     * @returns {SiteHeadLink[]} 아이콘 링크 3개(32·48·180 `apple-touch-icon`)와 manifest 링크. 올린 아이콘이 없으면 빈 배열(기본 아이콘은 서버가 넣는다).
     */
    static devHeadLinks(info: SiteInfo | null | undefined): SiteHeadLink[] {
        const icon = info?.icon;
        if (!icon) return [];
        return [
            { rel: 'icon', type: 'image/png', sizes: '32x32', href: icon.favicon32 },
            { rel: 'icon', type: 'image/png', sizes: '48x48', href: icon.favicon48 },
            { rel: 'apple-touch-icon', sizes: '180x180', href: icon.appleTouch },
            { rel: 'manifest', href: '/manifest.webmanifest' },
        ];
    }
}
