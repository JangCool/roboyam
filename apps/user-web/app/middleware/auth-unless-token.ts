import UtilRedirect from '~/utils/util-redirect';

/**
 * 메일 링크(`?token=`)로 들어오면 로그인 없이 통과시키고, 아니면 `auth` 와 같이 로그인을 요구한다 (원본 community `middleware/auth-unless-token.ts` 와 같다).
 * 이메일 인증·변경 링크는 다른 기기·브라우저에서 열릴 수 있다. 막는 것은 서버다(재발송은 로그인이 필요하다 — 401).
 *
 * 쓰는 곳: `definePageMeta({ middleware: 'auth-unless-token' })` — `/verify-email`
 */
export default defineNuxtRouteMiddleware(async (to) => {
    if (typeof to.query.token === 'string' && to.query.token) return;

    // ⚠ await 앞에서 컴포저블을 부른다 — 뒤에서는 Nuxt 문맥이 없다
    const { ensureLoaded } = useAuth();
    if (await ensureLoaded()) return;

    return navigateTo(UtilRedirect.toLogin(to.fullPath));
});
