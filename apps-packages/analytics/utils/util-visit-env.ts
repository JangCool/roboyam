/**
 * 방문 신호의 환경 분류값(브라우저·OS·화면 너비 구간) — 입력만 보고 답하는 순수 함수. 상태도 요청도 없다.
 * **원본 브라우저 정보(User-Agent)는 신호에 싣지 않는다** — 여기서 분류한 값만 싣는다(서버도 원본 UA 를 저장하지 않는다 — `docs/features/site-analytics-contract.md`).
 * 값은 관리자 화면(`apps/admin-web` `useVisitAnalyticsApi.types.ts` 의 `VISIT_BROWSERS`·`VISIT_OSES`·`VISIT_SCREENS`)과 같다. 기기(모바일·태블릿·데스크톱)는 서버가 UA 로 가른다(이 신호에 없다).
 * 이 파일은 `vue`·Nuxt 자동 임포트를 쓰지 않는다.
 */

export const VISIT_BROWSERS = ['CHROME', 'SAFARI', 'EDGE', 'SAMSUNG', 'WHALE', 'FIREFOX', 'OTHER'] as const;
export type VisitBrowser = (typeof VISIT_BROWSERS)[number];

export const VISIT_OSES = ['WINDOWS', 'MACOS', 'ANDROID', 'IOS', 'LINUX', 'OTHER'] as const;
export type VisitOs = (typeof VISIT_OSES)[number];

/** 화면 너비 구간 — ~480 · ~768 · ~1024 · ~1440 · 1440 넘음 */
export const VISIT_SCREENS = ['W480', 'W768', 'W1024', 'W1440', 'WIDE'] as const;
export type VisitScreen = (typeof VISIT_SCREENS)[number];

/** 신호의 `env` 칸 */
export interface VisitEnv {
    browser: VisitBrowser;
    os: VisitOs;
    screen: VisitScreen;
}

export default class UtilVisitEnv {
    /**
     * 브라우저 — 다른 브라우저가 UA 에 `Chrome`·`Safari` 를 함께 적으므로 좁은 것부터 본다(웨일 → 삼성 → 엣지 → 오페라(기타) → 파이어폭스 → 크롬 → 사파리).
     * @param {string} ua - `navigator.userAgent`.
     * @returns {VisitBrowser} 분류값(모르면 OTHER).
     */
    static browser(ua: string): VisitBrowser {
        if (/Whale\//.test(ua)) return 'WHALE';
        if (/SamsungBrowser\//.test(ua)) return 'SAMSUNG';
        if (/Edg(e|A|iOS)?\//.test(ua)) return 'EDGE';
        if (/OPR\/|Opera|YaBrowser\//.test(ua)) return 'OTHER';
        if (/Firefox\/|FxiOS\//.test(ua)) return 'FIREFOX';
        if (/Chrome\/|CriOS\//.test(ua)) return 'CHROME';
        if (/Safari\//.test(ua) && /Version\//.test(ua)) return 'SAFARI';
        return 'OTHER';
    }

    /**
     * 운영체제 — iPadOS 13 뒤의 아이패드는 UA 가 맥과 같아서 터치 점 수로 가른다.
     * @param {string} ua - `navigator.userAgent`.
     * @param {number} touchPoints - `navigator.maxTouchPoints`(모르면 0).
     * @returns {VisitOs} 분류값(모르면 OTHER).
     */
    static os(ua: string, touchPoints = 0): VisitOs {
        if (/iPhone|iPad|iPod/.test(ua)) return 'IOS';
        if (/Macintosh|Mac OS X/.test(ua)) return touchPoints > 1 ? 'IOS' : 'MACOS';
        if (/Android/.test(ua)) return 'ANDROID';
        if (/Windows/.test(ua)) return 'WINDOWS';
        if (/CrOS/.test(ua)) return 'OTHER';
        if (/Linux|X11/.test(ua)) return 'LINUX';
        return 'OTHER';
    }

    /**
     * 화면 너비 구간 — 브라우저 창의 너비(`window.innerWidth`)만 구간으로. 정확한 값은 싣지 않는다.
     * @param {number} width - 창 너비(px).
     * @returns {VisitScreen} 구간.
     */
    static screen(width: number): VisitScreen {
        if (!Number.isFinite(width) || width <= 480) return 'W480';
        if (width <= 768) return 'W768';
        if (width <= 1024) return 'W1024';
        if (width <= 1440) return 'W1440';
        return 'WIDE';
    }

    /**
     * 지금 브라우저의 분류값 — 신호를 보내는 순간에 한 번 읽는다.
     * @returns {VisitEnv} 분류값.
     */
    static current(): VisitEnv {
        const ua = typeof navigator === 'undefined' ? '' : navigator.userAgent;
        const touch = typeof navigator === 'undefined' ? 0 : navigator.maxTouchPoints || 0;
        const width = typeof window === 'undefined' ? 0 : window.innerWidth;
        return { browser: UtilVisitEnv.browser(ua), os: UtilVisitEnv.os(ua, touch), screen: UtilVisitEnv.screen(width) };
    }
}
