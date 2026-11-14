// MOCK: 사이트 로고 4칸(언어 × 바탕)과 「로고에 사이트 이름이 들어 있음」 — 서버(`GET /api/site/info`)가 아직 이 값을 주지 않는다(화면 확정 전, 2026-11-06).
// 서버가 4칸·이름 포함 여부를 주면 이 파일과 `useSiteName` 의 MOCK 줄을 지운다.

import { assetUrl } from 'virtual:static-assets/core';
import type { SiteLogoSet } from '~/utils/util-site-logo';

/** 목업 기본 그림 — `public/brand/` 의 아이로빗 그림 로고(아바타 머리 + 글자) */
const DEFAULT_LOGOS: SiteLogoSet = {
    koLight: assetUrl('/brand/irobit-logo-ko-light.svg'),
    koDark: assetUrl('/brand/irobit-logo-ko-dark.svg'),
    enLight: assetUrl('/brand/irobit-logo-en-light.svg'),
    enDark: assetUrl('/brand/irobit-logo-en-dark.svg'),
};

export default class SiteLogoMock {
    /** 목업 「이름 포함」 — 그림 로고에 이름 글자가 들어 있어 옆 이름 글자를 숨긴다 */
    static readonly NAME_IN_LOGO = true;

    /**
     * 4칸 값 — 운영자가 올린 지금의 단일 로고(`logoUrl`)가 있으면 그것을 ① 자리로 쓴다(기존 동작이 깨지지 않게), 나머지는 목업 그림.
     * @param {string | null} serverLogoUrl - 서버가 준 단일 로고 주소(없으면 null).
     * @returns {SiteLogoSet} 4칸 값.
     */
    static logos(serverLogoUrl: string | null): SiteLogoSet {
        return { ...DEFAULT_LOGOS, koLight: serverLogoUrl ?? DEFAULT_LOGOS.koLight };
    }
}
