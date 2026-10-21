import type { Role } from '~/composables/auth/api/useAuthApi.types';

/**
 * 공개 사이트의 사용자 메뉴가 보이는 "관리자 앱" 링크 — 보일지 정하고 주소를 거른다 (docs/api-admin-ops.md 7절).
 *
 * 서버가 `/api/auth/me` 의 `adminUrl` 에 주소를 싣는 것은 관리자 앱에 들어갈 수 있는 운영자·운영진뿐이다(그 밖에는 null).
 * 화면은 그 값을 그대로 `href` 에 넣지 않고 한 번 더 거른다 — 역할이 운영진·운영자일 때만, 그리고 `http://`·`https://` 주소일 때만 쓴다
 * (`javascript:` · `data:` · 상대 경로 · 공백·제어 문자가 섞인 값 · 주소에 아이디·비밀번호가 붙은 값은 링크로 만들지 않는다).
 * 링크에 보이는 이름과 스크린리더 이름은 글자라 `i18n/locales/{ko,en}/shell.ts` 의 `shell.adminApp`·`shell.adminAppNewTab` 을 컴포넌트가 `t` 로 읽는다.
 */
export default class UtilAdminLink {
    /** primeicons 클래스 */
    static readonly ICON = 'pi pi-shield';

    /** 새 탭으로 여는 링크에 붙이는 `rel` — 열린 쪽이 이 창을 건드리지 못하고 출처도 넘기지 않는다 */
    static readonly REL = 'noopener noreferrer';

    /** 이보다 긴 주소는 의심스러워 쓰지 않는다 */
    static readonly MAX_LENGTH = 2048;

    /**
     * 서버가 준 관리자 앱 주소가 링크로 써도 되는 값인지 보고 돌려줍니다.
     * @param {unknown} value - `adminUrl`(문자열 또는 null · 없음).
     * @returns {string | null} 앞뒤 공백을 뗀 `http(s)://…` 주소. 아니면 null.
     */
    static safeUrl(value: unknown): string | null {
        if (typeof value !== 'string') return null;
        const url = value.trim();
        if (url.length === 0 || url.length > UtilAdminLink.MAX_LENGTH) return null;
        // 공백·제어 문자·역슬래시는 브라우저가 다르게 읽을 수 있어 어디에 있어도 거절
        // eslint-disable-next-line no-control-regex
        if (/[\u0000- \u007f\\]/.test(url)) return null;
        if (!/^https?:\/\//i.test(url)) return null;
        try {
            const parsed = new URL(url);
            if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null;
            if (!parsed.hostname) return null;
            if (parsed.username || parsed.password) return null;
        } catch {
            return null;
        }
        return url;
    }

    /**
     * 사용자 메뉴에 "관리자 앱" 링크를 보일 주소를 정합니다 — 운영진·운영자에게만, 거른 주소로만.
     * @param {Role | null | undefined} role - 로그인한 사람의 역할. 로그인하지 않았으면 null.
     * @param {unknown} value - 서버가 준 `adminUrl`.
     * @returns {string | null} 링크의 주소. 보이지 않을 때는 null.
     */
    static resolve(role: Role | null | undefined, value: unknown): string | null {
        return role === 'STAFF' || role === 'ADMIN' ? UtilAdminLink.safeUrl(value) : null;
    }
}
