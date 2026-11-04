/**
 * 방문 신호의 모양과 순수 규칙 — 상태도 요청도 없다(저장은 `util-visit-store.ts`, 보내기는 `util-visit-transport.ts`).
 * 신호 모양은 서버 계약 그대로다(`docs/features/site-analytics-contract.md` 「API (확정)」 — 공개 `POST /api/visits`).
 * 이 파일은 `vue`·Nuxt 자동 임포트를 쓰지 않는다.
 */
import type { VisitEnv } from './util-visit-env';

/** 화면 종류 — 서버가 인기 페이지 묶음·제목 찾기에 쓴다. 어느 주소가 어느 종류인지는 앱이 정한다(라우트를 아는 쪽) */
export const VISIT_KINDS = ['HOME', 'BOARD', 'POST', 'GROUP_BUY', 'HOTDEAL', 'GROUP', 'POINT_SHOP', 'SEARCH', 'MY', 'OTHER'] as const;
export type VisitKind = (typeof VISIT_KINDS)[number];

/** 세션 첫 페이지 주소의 캠페인 꼬리표(`utm_source`·`utm_medium`·`utm_campaign`) — 없는 칸은 null */
export interface VisitUtm {
    source: string | null;
    medium: string | null;
    campaign: string | null;
}

/** `POST /api/visits` 본문 */
export interface VisitSignal {
    /** 브라우저의 방문자 ID(UUID) — 서버는 원본 대신 해시만 저장한다 */
    visitorId: string;
    /** 탭의 세션 ID(UUID) — 30분 쉬면 바뀐다 */
    sessionId: string;
    /** 쿼리·해시를 뺀 경로(≤300자) */
    path: string;
    /** 화면 종류 */
    kind: VisitKind;
    /** 같은 세션의 직전 경로 — 세션 첫 페이지면 null */
    prevPath: string | null;
    /** 세션 첫 페이지의 외부 유입 주소(출처 + 경로, 쿼리 뺌) — 같은 사이트·빈 값·세션 첫 페이지가 아니면 null */
    referrer: string | null;
    /** 세션 첫 페이지의 캠페인 꼬리표 — 하나도 없거나 세션 첫 페이지가 아니면 null */
    utm: VisitUtm | null;
    /** 이 방문자 ID 를 이번에 처음 만들었다(그 방문의 첫 신호만 true) */
    newVisitor: boolean;
    /**
     * 환경 분류값(브라우저·OS·화면 너비 구간 — `util-visit-env.ts`) — **세션 첫 페이지**이고 환경 수집(`environment`)이 켜졌을 때만, 아니면 null.
     * 2단계 — 서버는 브라우저·OS 가 허용 목록 밖이면 OTHER, 화면 구간이 목록 밖이면 환경만 세지 않는다. 원본 UA 는 싣지 않는다.
     */
    env: VisitEnv | null;
}

/**
 * 수집 스위치 — 공개 `GET /api/site/info` 의 `analytics` 칸. 브라우저는 `enabled` 이고 방문 항목 하나라도 켜졌을 때만 신호를 보낸다(`isCollecting`).
 * 알림·게시판 집계 스위치는 브라우저와 상관없어 여기 없다.
 */
export interface VisitCollectSwitch {
    enabled: boolean;
    visits: boolean;
    rawEvents: boolean;
    referrers: boolean;
    flows: boolean;
    /** 환경(2단계) — 켜졌으면 세션 첫 페이지 신호에 `env` 를 싣는다. 옛 서버에는 칸이 없다(false) */
    environment: boolean;
}

/** 경로·유입 주소 길이 상한(글자) — 서버 계약 `path ≤300` */
const URL_MAX = 300;

/** 캠페인 꼬리표 한 칸 길이 상한(글자) — 계약에 없는 값이라 넉넉히 자른다(서버가 다시 본다) */
const UTM_MAX = 100;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 글자(코드 포인트) 단위로 자른다 — 한글·이모지가 반으로 갈리지 않게 */
function cut(text: string, max: number): string {
    const chars = Array.from(text);
    return chars.length > max ? chars.slice(0, max).join('') : text;
}

/** 퍼센트 인코딩을 푼다 — 풀 수 없으면 그대로 */
function decode(text: string): string {
    try {
        return decodeURI(text);
    } catch {
        return text;
    }
}

/** 호스트 비교용 — 소문자, 앞의 `www.` 를 뗀다 */
function bareHost(host: string): string {
    const lower = host.toLowerCase();
    return lower.startsWith('www.') ? lower.slice(4) : lower;
}

export default class UtilVisitSignal {
    /** 같은 경로 신호를 한 번으로 치는 간격(ms) — 새로 고침 연타·되돌아오기 */
    static readonly DEDUPE_MS = 2000;

    /** 이만큼 신호가 없으면 새 세션(ms) — 30분 */
    static readonly SESSION_IDLE_MS = 30 * 60 * 1000;

    /**
     * 사이트 정보의 `analytics` 칸을 읽는다 — 칸마다 `true` 일 때만 켬.
     * @param {unknown} raw - 응답의 `analytics`.
     * @returns {VisitCollectSwitch | null} 스위치. 칸이 없거나 객체가 아니면 null(옛 서버 — 보내지 않는다).
     */
    static readSwitch(raw: unknown): VisitCollectSwitch | null {
        if (!isRecord(raw)) return null;
        return {
            enabled: raw.enabled === true,
            visits: raw.visits === true,
            rawEvents: raw.rawEvents === true,
            referrers: raw.referrers === true,
            flows: raw.flows === true,
            environment: raw.environment === true,
        };
    }

    /**
     * 신호를 보낼까 — 방문 수집 전체가 켜졌고 방문 항목(방문자·원본·유입·흐름·환경) 하나라도 켜졌을 때. 이용자가 거부한 브라우저인지는 `UtilVisitStore.optedOut` 이 따로 본다.
     * @param {VisitCollectSwitch | null | undefined} sw - 스위치(모르면 null).
     * @returns {boolean} 보내면 true.
     */
    static isCollecting(sw: VisitCollectSwitch | null | undefined): boolean {
        return !!sw && sw.enabled && (sw.visits || sw.rawEvents || sw.referrers || sw.flows || sw.environment);
    }

    /**
     * 신호에 실을 경로 — 쿼리·해시를 떼고, 퍼센트 인코딩을 풀고(한글 주소를 읽을 수 있게), 300자에서 자른다.
     * @param {string} path - 라우트 경로(`/boards/free`).
     * @returns {string} 경로. 비었거나 `/` 로 시작하지 않으면 `/` 를 붙인다.
     */
    static cleanPath(path: string): string {
        const bare = decode(path.split(/[?#]/, 1)[0] ?? '');
        return cut(bare.startsWith('/') ? bare : `/${bare}`, URL_MAX);
    }

    /**
     * 외부 유입 주소 — 같은 사이트(호스트가 같음, `www.` 무시)·빈 값·http(s) 가 아닌 값은 null. 쿼리·해시는 떼고(검색어 등 개인 정보) 출처 + 경로만, 300자에서 자른다.
     * @param {string} referrer - `document.referrer`.
     * @param {string} currentHost - 지금 사이트의 호스트(`location.host`).
     * @returns {string | null} 유입 주소.
     */
    static externalReferrer(referrer: string, currentHost: string): string | null {
        if (!referrer) return null;
        let url: URL;
        try {
            url = new URL(referrer);
        } catch {
            return null;
        }
        if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;
        if (bareHost(url.host) === bareHost(currentHost)) return null;
        return cut(`${url.origin}${decode(url.pathname)}`, URL_MAX);
    }

    /**
     * 주소의 캠페인 꼬리표를 읽는다 — 앞뒤 공백을 떼고 칸마다 100자에서 자른다.
     * @param {string} search - 주소의 쿼리(`?utm_source=…`, 앞의 `?` 는 있어도 없어도 된다).
     * @returns {VisitUtm | null} 꼬리표. 셋 다 없으면 null.
     */
    static readUtm(search: string): VisitUtm | null {
        const params = new URLSearchParams(search);
        const one = (name: string): string | null => {
            const value = params.get(name)?.trim() ?? '';
            return value ? cut(value, UTM_MAX) : null;
        };
        const utm: VisitUtm = { source: one('utm_source'), medium: one('utm_medium'), campaign: one('utm_campaign') };
        return utm.source || utm.medium || utm.campaign ? utm : null;
    }

    /**
     * 같은 경로를 방금(2초 안) 보냈는가 — 그러면 이번 신호는 보내지 않는다.
     * @param {string | null} lastPath - 마지막으로 보낸 경로(없으면 null).
     * @param {number} lastAt - 마지막으로 보낸 시각(ms).
     * @param {string} path - 이번 경로.
     * @param {number} now - 지금(ms).
     * @returns {boolean} 겹치면 true.
     */
    static isDuplicate(lastPath: string | null, lastAt: number, path: string, now: number): boolean {
        return lastPath === path && now - lastAt >= 0 && now - lastAt < UtilVisitSignal.DEDUPE_MS;
    }

    /**
     * 세션이 끝났는가 — 마지막 신호에서 30분이 지났다(시계가 뒤로 갔어도 새 세션).
     * @param {number} lastAt - 마지막 신호 시각(ms).
     * @param {number} now - 지금(ms).
     * @returns {boolean} 끝났으면 true.
     */
    static isSessionOver(lastAt: number, now: number): boolean {
        return now < lastAt || now - lastAt >= UtilVisitSignal.SESSION_IDLE_MS;
    }
}
