import UtilRedirect from '~/utils/util-redirect';

/**
 * 로그인 안 한 사람만 보는 화면(로그인) — 이미 로그인했으면 `/`(또는 `next`)로 보낸다.
 *
 * 쓰는 곳: `definePageMeta({ middleware: 'guest' })`
 */
export default defineNuxtRouteMiddleware(async (to) => {
    const { ensureLoaded } = useAdminAuth();
    if (!(await ensureLoaded())) return;

    return navigateTo(UtilRedirect.safeNext(to.query.next), { replace: true });
});
