/**
 * User-Agent 문자열 해석 — 로그인 이력 한 줄에 "브라우저 · 운영체제 · 기기 종류"로 풀어 보이려고 쓴다. 입력만 보고 답하는 순수 함수라 브라우저 없이 시험한다.
 * 클라이언트 힌트(`navigator.userAgentData`)는 쓰지 않는다 — 서버가 저장해 준 **UA 원문**만 본다. 새 의존성은 없다.
 *
 * 규칙(위에서부터 먼저 맞는 것):
 *  · 브라우저 — Edge(`Edg/`·`EdgA/`·`EdgiOS/`·`Edge/`) → Opera(`OPR/`·`Opera`) → Samsung Internet(`SamsungBrowser/`) → Firefox(`Firefox/`·`FxiOS/`) → Chrome(`Chrome/`·`CriOS/`) → Safari(`Version/` + `Safari/`).
 *    Edge·Opera·Samsung 의 UA 는 `Chrome/` 도 들어 있어서 **먼저** 가려야 한다. 모르면 null.
 *  · 운영체제 — Windows → Android → iOS(`iPhone`·`iPad`·`iPod`) → ChromeOS(`CrOS`) → macOS(`Mac OS X`) → Linux(`Linux`·`X11`). Android 가 Linux 보다, iOS 가 macOS 보다 먼저다
 *    (Android UA 에 `Linux` 가, iPad·iPhone UA 에 `like Mac OS X` 가 들어 있다). 모르면 null.
 *  · 기기 — iPad·`Tablet` · Android 인데 `Mobile` 이 없으면 태블릿 / iPhone·iPod·`Mobi` · Android + `Mobile` 이면 모바일 / 위 운영체제가 Windows·macOS·Linux·ChromeOS 면 데스크톱 / 그 밖은 unknown.
 *
 * 한계: 크롬 계열 Chromium 포크(Brave·Whale·Vivaldi 일부 …)는 UA 가 Chrome 과 같아 Chrome 으로 나온다. iPadOS 13+ 의 Safari 는 기본이 "데스크톱 사이트 요청"이라 UA 가 Macintosh 로
 * 위장해 macOS(데스크톱)로 나온다. macOS 버전은 UA 가 `10_15_7` 로 고정돼 있어 보이지 않는다. Windows 11 도 UA 는 `Windows NT 10.0` 이라 "10/11" 로 묶는다.
 */

export type DeviceKind = 'desktop' | 'mobile' | 'tablet' | 'unknown';

export interface ParsedUserAgent {
    /** 브라우저 이름(고유명 그대로) — 모르면 null */
    browser: string | null;
    /** 브라우저 주 버전(`126`) — 모르면 null */
    browserVersion: string | null;
    /** 운영체제 이름 — 모르면 null */
    os: string | null;
    /** 운영체제 버전(`10/11` · `17` · `14`) — 모르거나 UA 에 없으면 null */
    osVersion: string | null;
    device: DeviceKind;
}

/** 원문 UA 를 툴팁 등에 보일 때 자르는 길이(자) */
const MAX_RAW = 300;

/** 첫 번째 캡처 그룹의 주 버전(`126.0.0.0` → `126`) — 없으면 null */
function major(ua: string, pattern: RegExp): string | null {
    const match = pattern.exec(ua);
    const value = match?.[1];
    if (!value) return null;
    return value.split(/[._]/)[0] || null;
}

export default class UtilUserAgent {
    /** 해석한 것이 하나도 없는 값 */
    static readonly UNKNOWN: ParsedUserAgent = { browser: null, browserVersion: null, os: null, osVersion: null, device: 'unknown' };

    /**
     * UA 문자열을 풀어 돌려준다. null·빈 값·모르는 모양은 `UNKNOWN`(모든 칸 null, 기기 unknown).
     * @param {string | null | undefined} agent - User-Agent 원문.
     * @returns {ParsedUserAgent} 브라우저·운영체제·기기 종류.
     */
    static parse(agent: string | null | undefined): ParsedUserAgent {
        const ua = typeof agent === 'string' ? agent.trim() : '';
        if (!ua) return { ...UtilUserAgent.UNKNOWN };

        const { browser, browserVersion } = UtilUserAgent.browserOf(ua);
        const { os, osVersion } = UtilUserAgent.osOf(ua);
        return { browser, browserVersion, os, osVersion, device: UtilUserAgent.deviceOf(ua, os) };
    }

    /**
     * 브라우저와 주 버전. Chrome 문자열을 품은 Edge·Opera·Samsung 을 먼저 가린다.
     * @param {string} ua - 비어 있지 않은 UA.
     * @returns {{ browser: string | null; browserVersion: string | null }} 이름·주 버전.
     */
    private static browserOf(ua: string): { browser: string | null; browserVersion: string | null } {
        const edge = major(ua, /\b(?:Edg|EdgA|EdgiOS|Edge)\/(\d+)/);
        if (edge) return { browser: 'Edge', browserVersion: edge };
        const opera = major(ua, /\bOPR\/(\d+)/) ?? major(ua, /\bOpera\/(\d+)/);
        if (opera) return { browser: 'Opera', browserVersion: opera };
        const samsung = major(ua, /\bSamsungBrowser\/(\d+)/);
        if (samsung) return { browser: 'Samsung Internet', browserVersion: samsung };
        const firefox = major(ua, /\b(?:Firefox|FxiOS)\/(\d+)/);
        if (firefox) return { browser: 'Firefox', browserVersion: firefox };
        const chrome = major(ua, /\b(?:Chrome|CriOS)\/(\d+)/);
        if (chrome) return { browser: 'Chrome', browserVersion: chrome };
        // Safari 는 `Version/` 이 따로 있고 Chrome 계열 표식이 없을 때만
        if (/\bSafari\//.test(ua)) return { browser: 'Safari', browserVersion: major(ua, /\bVersion\/(\d+)/) };
        return { browser: null, browserVersion: null };
    }

    /**
     * 운영체제와 버전. Android 를 Linux 보다, iOS 를 macOS 보다 먼저 본다.
     * @param {string} ua - 비어 있지 않은 UA.
     * @returns {{ os: string | null; osVersion: string | null }} 이름·버전.
     */
    private static osOf(ua: string): { os: string | null; osVersion: string | null } {
        const windows = /Windows NT (\d+(?:\.\d+)?)/.exec(ua);
        if (windows) {
            const map: Record<string, string> = { '10.0': '10/11', '6.3': '8.1', '6.2': '8', '6.1': '7' };
            return { os: 'Windows', osVersion: map[windows[1] as string] ?? null };
        }
        if (/Android/.test(ua)) return { os: 'Android', osVersion: major(ua, /Android (\d+)/) };
        if (/\b(?:iPhone|iPad|iPod)\b/.test(ua)) return { os: 'iOS', osVersion: major(ua, /OS (\d+)[._]/) };
        if (/\bCrOS\b/.test(ua)) return { os: 'ChromeOS', osVersion: null };
        if (/Mac OS X|Macintosh/.test(ua)) return { os: 'macOS', osVersion: null };
        if (/\bLinux\b|\bX11\b/.test(ua)) return { os: 'Linux', osVersion: null };
        return { os: null, osVersion: null };
    }

    /**
     * 기기 종류.
     * @param {string} ua - 비어 있지 않은 UA.
     * @param {string | null} os - 풀어낸 운영체제 이름.
     * @returns {DeviceKind} 데스크톱·모바일·태블릿, 모르면 unknown.
     */
    private static deviceOf(ua: string, os: string | null): DeviceKind {
        if (/\biPad\b|\bTablet\b/i.test(ua)) return 'tablet';
        if (os === 'Android') return /\bMobile\b/.test(ua) ? 'mobile' : 'tablet';
        if (/\b(?:iPhone|iPod)\b|\bMobi\b/.test(ua)) return 'mobile';
        if (os === 'Windows' || os === 'macOS' || os === 'Linux' || os === 'ChromeOS') return 'desktop';
        return 'unknown';
    }

    /**
     * 원문 UA 를 툴팁 등에 보이기 좋게 자른다 — 긴 문자열이 레이아웃을 밀지 않게.
     * @param {string | null | undefined} agent - UA 원문.
     * @returns {string} 비었으면 빈 글자, 길면 앞 300자 + `…`.
     */
    static truncate(agent: string | null | undefined): string {
        const ua = typeof agent === 'string' ? agent.trim() : '';
        return ua.length > MAX_RAW ? `${ua.slice(0, MAX_RAW)}…` : ua;
    }

    /**
     * 기기 종류의 아이콘(PrimeIcons) — 장식이라 `aria-hidden`, 의미는 글자가 말한다.
     * @param {DeviceKind} device - 기기 종류.
     * @returns {string} `pi pi-…` 클래스.
     */
    static icon(device: DeviceKind): string {
        if (device === 'desktop') return 'pi pi-desktop';
        if (device === 'mobile') return 'pi pi-mobile';
        if (device === 'tablet') return 'pi pi-tablet';
        return 'pi pi-question-circle';
    }
}
