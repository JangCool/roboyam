// MOCK: 사이트 로고 4칸(언어 × 바탕)과 「로고에 사이트 이름이 들어 있음」 — 서버에 칸이 없어(화면 확정 전, 2026-11-06) 이 탭의 메모리에만 둔다.
// 새로 고치면 처음 값으로 돌아가고, 공개 사이트(user-web)에는 닿지 않는다(user-web 은 자기 목업 `composables/site/api/mock/site-logo-mock.ts` 를 쓴다).
// 서버 API 가 생기면 이 파일과 `useSiteSettingsApi` 의 MOCK 줄을 지운다.

import { assetUrl } from 'virtual:static-assets/core';
import type { SiteLogoSetResponse } from '~/composables/site/api/useSiteSettingsApi.types';
import type { SiteLogoSet, SiteLogoVariant } from '~/utils/util-site-logo';

/** 목업 처음 그림 — `public/brand/` 의 아이로빗 그림 로고(아바타 머리 + 글자) */
const DEFAULT_LOGOS: SiteLogoSet = {
    koLight: assetUrl('/brand/irobit-logo-ko-light.svg'),
    koDark: assetUrl('/brand/irobit-logo-ko-dark.svg'),
    enLight: assetUrl('/brand/irobit-logo-en-light.svg'),
    enDark: assetUrl('/brand/irobit-logo-en-dark.svg'),
};

/** 메모리 상태 — 처음 읽을 때 채운다 */
let state: SiteLogoSetResponse | null = null;
/** 올린 파일로 만든 object URL — 그 칸을 바꾸거나 떼면 돌려준다 */
const objectUrls = new Map<SiteLogoVariant, string>();

function copy(value: SiteLogoSetResponse): SiteLogoSetResponse {
    return { logos: { ...value.logos }, nameInLogo: value.nameInLogo };
}

function ensure(serverLogoUrl: string | null = null): SiteLogoSetResponse {
    // 지금의 단일 로고(서버 `logoUrl`)가 있으면 ① 자리로 — 공개 사이트 목업과 같은 규칙
    state ??= { logos: { ...DEFAULT_LOGOS, koLight: serverLogoUrl ?? DEFAULT_LOGOS.koLight }, nameInLogo: true };
    return state;
}

function put(variant: SiteLogoVariant, url: string | null): SiteLogoSetResponse {
    const current = ensure();
    const old = objectUrls.get(variant);
    if (old && old !== url) {
        URL.revokeObjectURL(old);
        objectUrls.delete(variant);
    }
    state = { ...current, logos: { ...current.logos, [variant]: url } };
    return copy(state);
}

/** SVG 소스를 `<img src>` 에 넣을 data URI 로 — 서버는 정화·검사 후 만들어 주지만 목업은 그대로 감싼다(`xmlns` 가 없으면 넣는다) */
function svgDataUri(source: string): string {
    const trimmed = source.trim();
    const withNs = /\sxmlns=/.test(trimmed.slice(0, trimmed.indexOf('>') + 1)) ? trimmed : trimmed.replace(/^<svg\b/i, '<svg xmlns="http://www.w3.org/2000/svg"');
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(withNs)}`;
}

export default class SiteLogoMock {
    static read(serverLogoUrl: string | null): SiteLogoSetResponse {
        return copy(ensure(serverLogoUrl));
    }

    static setFile(variant: SiteLogoVariant, file: File): SiteLogoSetResponse {
        const url = URL.createObjectURL(file);
        const next = put(variant, url);
        objectUrls.set(variant, url);
        return next;
    }

    static setSvg(variant: SiteLogoVariant, source: string): SiteLogoSetResponse {
        return put(variant, svgDataUri(source));
    }

    static clear(variant: SiteLogoVariant): SiteLogoSetResponse {
        return put(variant, null);
    }

    static setNameInLogo(nameInLogo: boolean): SiteLogoSetResponse {
        state = { ...ensure(), nameInLogo };
        return copy(state);
    }
}
