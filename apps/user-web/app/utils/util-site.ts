import { ApiError } from '~/composables/common/useApiClient';
import type { SiteInput } from '~/composables/install/api/useInstallApi.types';

/** 사이트 입력칸 이름 */
export type SiteField = 'name' | 'publicUrl' | 'tagline';

/** 사이트 입력칸별 오류 문구 */
export type SiteFieldErrors = Partial<Record<SiteField, string>>;

/**
 * 사이트 이름·공개 주소(설치 마법사 3단계 · 운영자 설정 화면)의 순수 로직 — 입력 규칙·요청 만들기·콜백 주소·서버 오류 가르기.
 * 입력 규칙은 docs/api-install.md 의 `site` 설명과 같다(서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버).
 * 화면에 보이는 문구는 `util-site-messages.ts` 에 있다.
 */

/** 입력칸의 문제 — 비어 있음 · 너무 김 · http(s) 로 시작하지 않음 · 경로·쿼리·끝 `/` 가 있음 · 그 밖의 형식 오류 · 넣을 수 없는 글자(`<` `>` 제어 문자, 한 줄 소개) */
export type SiteProblem = 'REQUIRED' | 'LENGTH' | 'SCHEME' | 'PATH' | 'FORMAT' | 'CHARS';
export type SiteProblems = Partial<Record<SiteField, SiteProblem>>;

/** 사이트 입력칸의 값 — 공개 주소는 앞뒤 공백을 자르기 전 그대로 든다 */
export interface SiteForm {
    name: string;
    publicUrl: string;
    /** 한 줄 소개(슬로건, 선택) — 비어 있어도 된다 */
    tagline: string;
}

/** 호스트(이름·IPv4·`[IPv6]`) + 선택 포트 — 사용자 정보(`@`)·경로는 허용하지 않는다 */
const HOST_AND_PORT = /^(\[[0-9A-Fa-f:.]+\]|[^\s:@\\[\]]+)(?::(\d{1,5}))?$/;

export default class UtilSite {
    /** 사이트 이름 최대 글자 수(앞뒤 공백을 자른 뒤) */
    static readonly NAME_MAX = 50;
    /** 한 줄 소개(슬로건) 최대 글자 수(앞뒤 공백을 자른 뒤, 코드포인트) — 서버와 같다 */
    static readonly TAGLINE_MAX = 100;
    /** 소셜 로그인 콜백 주소의 경로(공급자 이름이 뒤에 붙는다) — 서버가 만드는 주소와 같다 */
    static readonly CALLBACK_PATH = '/login/oauth2/code/';

    /**
     * 지금 접속한 주소(`location.origin`) — 마법사가 공개 주소를 미리 채울 때 쓴다. 브라우저가 아니면 빈 문자열.
     * @returns {string} `http(s)://호스트[:포트]` 꼴의 주소. 알 수 없으면 빈 문자열.
     */
    static currentOrigin(): string {
        return typeof window !== 'undefined' ? (window.location?.origin ?? '') : '';
    }

    /**
     * 사이트 이름의 문제 — 앞뒤 공백을 자른 뒤 1~50자.
     * @param {string} value - 입력한 사이트 이름.
     * @returns {SiteProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `LENGTH`.
     */
    static name(value: string): SiteProblem | null {
        const v = value.trim();
        if (!v) return 'REQUIRED';
        if (v.length > UtilSite.NAME_MAX) return 'LENGTH';
        return null;
    }

    /**
     * 공개 주소의 문제 — `http(s)://호스트[:포트]`, 끝 `/` 없음, 경로·쿼리 없음. 앞뒤 공백은 자르고 본다.
     * @param {string} value - 입력한 공개 주소.
     * @returns {SiteProblem | null} 문제 없으면 null, 있으면 `REQUIRED` · `SCHEME` · `PATH` · `FORMAT`.
     */
    static publicUrl(value: string): SiteProblem | null {
        const v = value.trim();
        if (!v) return 'REQUIRED';
        const scheme = /^https?:\/\//i.exec(v);
        if (!scheme) return 'SCHEME';
        const rest = v.slice(scheme[0].length);
        if (!rest) return 'FORMAT';
        if (/[/?#]/.test(rest)) return 'PATH'; // 끝의 `/` 도 여기다
        const match = HOST_AND_PORT.exec(rest);
        if (!match) return 'FORMAT';
        if (match[2] !== undefined) {
            const port = Number(match[2]);
            if (port < 1 || port > 65535) return 'FORMAT';
        }
        return null;
    }

    /**
     * 한 줄 소개(슬로건)의 문제 — 선택 입력이라 비어도 통과. 앞뒤 공백을 자른 뒤 100자(코드포인트) 이하, `<`·`>`·제어 문자 없음.
     * @param {string} value - 입력한 한 줄 소개.
     * @returns {SiteProblem | null} 문제 없으면 null, 있으면 `LENGTH` · `CHARS`.
     */
    static tagline(value: string): SiteProblem | null {
        const v = value.trim();
        if (!v) return null;
        if (Array.from(v).length > UtilSite.TAGLINE_MAX) return 'LENGTH';
        // eslint-disable-next-line no-control-regex
        if (/[<>\u0000-\u001f\u007f]/.test(v)) return 'CHARS';
        return null;
    }

    /**
     * 저장·설치 전에 입력을 미리 검사한다.
     * @param {SiteForm} form - 입력값.
     * @returns {SiteProblems} 문제가 있는 칸과 까닭. 비어 있으면 통과.
     */
    static validate(form: SiteForm): SiteProblems {
        const problems: SiteProblems = {};
        const name = UtilSite.name(form.name);
        if (name) problems.name = name;
        const url = UtilSite.publicUrl(form.publicUrl);
        if (url) problems.publicUrl = url;
        const tagline = UtilSite.tagline(form.tagline);
        if (tagline) problems.tagline = tagline;
        return problems;
    }

    /**
     * 서버로 보낼 요청 — 앞뒤 공백을 자른다. 한 줄 소개는 비었으면 null(= 없음).
     * @param {SiteForm} form - 입력값.
     * @returns {SiteInput} 요청 본문(`name`·`publicUrl`·`tagline`).
     */
    static toInput(form: SiteForm): SiteInput {
        return { name: form.name.trim(), publicUrl: form.publicUrl.trim(), tagline: form.tagline.trim() || null };
    }

    /**
     * 소셜 로그인 콜백 주소 — 각 사 개발자 콘솔에 등록할 값이다. 공개 주소가 올바르지 않으면 빈 문자열.
     * @param {string} publicUrl - 공개 주소(입력 그대로).
     * @param {string} provider - 공급자 코드(`NAVER` · `KAKAO` · `GOOGLE`, 대소문자 무시).
     * @returns {string} `{공개 주소}/login/oauth2/code/{naver|kakao|google}`. 공개 주소가 올바르지 않으면 빈 문자열.
     */
    static callbackUrl(publicUrl: string, provider: string): string {
        if (UtilSite.publicUrl(publicUrl)) return '';
        return `${publicUrl.trim()}${UtilSite.CALLBACK_PATH}${provider.toLowerCase()}`;
    }

    /**
     * 이 컴퓨터에서만 열리는 주소인가(`localhost`·`127.x.x.x`·`[::1]`) — 메일 링크·소셜 로그인 콜백에 쓰면 다른 사람은 열 수 없다.
     * @param {string} value - 공개 주소(입력 그대로).
     * @returns {boolean} 이 컴퓨터 주소이면 true.
     */
    static isLocal(value: string): boolean {
        return /^https?:\/\/(localhost|127(?:\.\d{1,3}){3}|\[::1\])(?::\d{1,5})?$/i.test(value.trim());
    }

    /**
     * 두 공개 주소가 같은가 — 앞뒤 공백과 대소문자를 가리지 않는다(호스트 이름은 대소문자를 가리지 않는다).
     * @param {string} a - 공개 주소 하나.
     * @param {string} b - 공개 주소 다른 하나.
     * @returns {boolean} 같으면 true.
     */
    static samePublicUrl(a: string, b: string): boolean {
        return a.trim().toLowerCase() === b.trim().toLowerCase();
    }

    /**
     * 입력 오류(`400 VALIDATION`)의 칸별 서버 문구. 마법사 요청이면 키가 `site.name`·`site.publicUrl`·`site.tagline`, 설정 화면 요청이면 `name`·`publicUrl`·`tagline` 이다 — 둘 다 읽는다.
     * @param {unknown} e - 잡은 오류.
     * @returns {SiteFieldErrors | null} 세 칸에 대한 서버 문구. 없으면 null(그때는 다른 방법으로 알린다).
     */
    static fieldErrorsOf(e: unknown): SiteFieldErrors | null {
        if (!(e instanceof ApiError) || e.code !== 'VALIDATION') return null;
        const out: SiteFieldErrors = {};
        const name = e.field('name') ?? e.field('site.name');
        const publicUrl = e.field('publicUrl') ?? e.field('site.publicUrl');
        const tagline = e.field('tagline') ?? e.field('site.tagline');
        if (name) out.name = name;
        if (publicUrl) out.publicUrl = publicUrl;
        if (tagline) out.tagline = tagline;
        return name || publicUrl || tagline ? out : null;
    }
}
