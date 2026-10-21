import type { RouteLocationRaw } from 'vue-router';

/**
 * 로그인 뒤 돌아갈 곳(`next`) 다루기.
 *
 * ⚠ **같은 사이트 안의 경로만 허용한다** (열린 리다이렉트 방지). `//evil.com`, `https://evil.com`,
 * `/\evil.com`(브라우저가 `//` 로 바꾼다), 탭·줄바꿈이 섞인 것(URL 파서가 지우고 `//` 로 읽는다)을 모두 거른다.
 * 쿼리 값이 배열이거나 비어 있어도 안전하게 `/` 로 돌아간다.
 */

/** 로그인 뒤 다시 보내면 고리가 생기는 곳(게스트 전용 화면) */
const AUTH_PAGES = ['/login'];

export default class UtilRedirect {
    /**
     * 로그인 뒤 돌아갈 곳으로 써도 되는 값인지 봅니다.
     * @param {unknown} value - 검사할 값.
     * @returns {boolean} 같은 사이트 안의 경로이면 true(이때 `string` 으로 좁혀진다).
     */
    static isSafeNext(value: unknown): value is string {
        if (typeof value !== 'string' || value.length === 0 || value.length > 2048) return false;
        if (value[0] !== '/') return false;
        if (value[1] === '/' || value[1] === '\\') return false;
        // 공백·제어 문자·역슬래시는 어디에 있어도 거절
        // eslint-disable-next-line no-control-regex
        if (/[\u0000- \u007f\\]/.test(value)) return false;
        return true;
    }

    /**
     * 안전하면 그 경로, 아니면 `/`
     * @param {unknown} value - 쿼리 `next` 값(배열일 수도 있다).
     * @returns {string} 안전하면 그 경로, 아니면 `/`.
     */
    static safeNext(value: unknown): string {
        const raw = Array.isArray(value) ? value[0] : value;
        if (!UtilRedirect.isSafeNext(raw)) return '/';
        const path = raw.split(/[?#]/)[0] ?? '/';
        // 로그인 화면이 다시 로그인 화면으로 보내는 고리를 끊는다
        if (AUTH_PAGES.some((p) => path === p || path.startsWith(p + '/'))) return '/';
        return raw;
    }

    /**
     * 로그인 화면 주소. 돌아올 곳이 홈이면 `next` 를 붙이지 않는다.
     * @param {string} from - 로그인 뒤 돌아올 곳.
     * @param {Record<string, string>} extra - 로그인 주소에 더 붙일 쿼리. 안 주면 없음.
     * @returns {RouteLocationRaw} 로그인 화면 주소. 돌아올 곳이 홈이면 `next` 를 붙이지 않는다.
     */
    static toLogin(from: string, extra: Record<string, string> = {}): RouteLocationRaw {
        const query: Record<string, string> = { ...extra };
        if (from !== '/' && UtilRedirect.isSafeNext(from)) query.next = from;
        return Object.keys(query).length ? { path: '/login', query } : { path: '/login' };
    }
}
