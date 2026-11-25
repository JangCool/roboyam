import type { SiteSearchVerificationRequest, SiteSearchVerificationResponse } from '~/composables/site/api/useSiteSearchVerificationApi.types';

/** 검색 엔진 — 입력칸 이름이자 서버 오류 `fields` 의 이름 */
export type SearchEngine = 'naver' | 'google' | 'bing';

/**
 * 칸의 문제 — 너무 김 · 넣을 수 없는 글자 · 다른 서비스의 태그 · 읽을 수 없는(또는 이 칸과 무관한) 태그.
 * `OTHER_TAG` 는 어느 서비스 태그인지(`other`)를 함께 준다.
 */
export type SearchVerificationIssue = { code: 'LENGTH' | 'CHARS' | 'TAG' } | { code: 'OTHER_TAG'; other: SearchEngine };

/** 편집 중인 값 — 입력칸은 글자로 묶는다(빈 글자 = 넣지 않음) */
export type SearchVerificationForm = Record<SearchEngine, string>;

const ENGINES: readonly SearchEngine[] = ['naver', 'google', 'bing'];
/** 공개 사이트 머리에 넣을 메타 태그 이름 */
const META_NAMES: Readonly<Record<SearchEngine, string>> = {
    naver: 'naver-site-verification',
    google: 'google-site-verification',
    bing: 'msvalidate.01',
};
const VALUE_RE = /^[A-Za-z0-9_-]+$/;
const META_RE = /^<\s*meta\b[\s\S]*>$/i;

function attr(tag: string, name: string): string | null {
    const match = new RegExp(`\\b${name}\\s*=\\s*(["'])([\\s\\S]*?)\\1`, 'i').exec(tag);
    return match ? (match[2] ?? '').trim() : null;
}

/**
 * 검색 엔진 소유 확인 코드(설정 > 일반 「검색 엔진 등록」)의 입력 규칙 — 서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 나중에 서버다(아직 없음).
 * 값: 선택, 영문·숫자·`-`·`_` 1~100자. 사이트에서 복사한 `<meta name="…" content="…" />` 태그 전체를 붙여 넣으면 이 칸 서비스의 태그일 때 `content` 만 뽑는다.
 */
export default class UtilSiteSearchVerification {
    static readonly ENGINES = ENGINES;
    static readonly META_NAMES = META_NAMES;
    static readonly MAX = 100;
    /** 각 서비스의 등록 화면 — 새 창 링크 */
    static readonly SITES: Readonly<Record<SearchEngine, string>> = {
        naver: 'https://searchadvisor.naver.com/',
        google: 'https://search.google.com/search-console',
        bing: 'https://www.bing.com/webmasters',
    };

    /**
     * 붙여 넣은 글자를 칸 값으로 — 이 칸 서비스의 메타 태그면 `content` 값만, 아니면 받은 그대로(다른 서비스 태그는 `issue` 가 알려 준다).
     * @param {SearchEngine} engine - 칸.
     * @param {string} raw - 입력된 글자.
     * @returns {string} 칸에 둘 값.
     */
    static normalize(engine: SearchEngine, raw: string): string {
        const text = raw.trim();
        if (!META_RE.test(text)) return raw;
        const name = attr(text, 'name');
        const content = attr(text, 'content');
        if (name?.toLowerCase() === META_NAMES[engine] && content) return content;
        return raw;
    }

    /**
     * 칸 하나의 문제. 비어 있으면 문제 없음(그 태그를 넣지 않는다).
     * @param {SearchEngine} engine - 칸.
     * @param {string} value - 칸 값.
     * @returns {SearchVerificationIssue | null} 문제, 없으면 null.
     */
    static issue(engine: SearchEngine, value: string): SearchVerificationIssue | null {
        const text = value.trim();
        if (!text) return null;
        if (text.startsWith('<')) {
            const name = attr(text, 'name')?.toLowerCase();
            const other = ENGINES.find((e) => e !== engine && META_NAMES[e] === name);
            return other ? { code: 'OTHER_TAG', other } : { code: 'TAG' };
        }
        if ([...text].length > UtilSiteSearchVerification.MAX) return { code: 'LENGTH' };
        if (!VALUE_RE.test(text)) return { code: 'CHARS' };
        return null;
    }

    /** 서버 값 → 편집용 글자 */
    static form(saved: SiteSearchVerificationResponse): SearchVerificationForm {
        return { naver: saved.naver ?? '', google: saved.google ?? '', bing: saved.bing ?? '' };
    }

    /** 보낼 값 — 앞뒤 공백을 떼고 빈 칸은 null */
    static request(form: SearchVerificationForm): SiteSearchVerificationRequest {
        const clean = (v: string): string | null => v.trim() || null;
        return { naver: clean(form.naver), google: clean(form.google), bing: clean(form.bing) };
    }

    /** 저장된 값과 다른 칸 수 */
    static changes(saved: SiteSearchVerificationResponse, form: SearchVerificationForm): number {
        const next = UtilSiteSearchVerification.request(form);
        return ENGINES.filter((e) => (saved[e] ?? null) !== next[e]).length;
    }
}
