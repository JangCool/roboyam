import UtilRedirect from '~/utils/util-redirect';

/**
 * 로그인이 필요한 화면 — 로그인하지 않았으면 `/login?next=<가려던 곳>` 으로 보낸다 (원본 community `middleware/auth.ts` 와 같다).
 *
 * **이것은 편의이지 보안이 아니다.** 진짜로 막는 것은 서버다(로그인이 필요한 API 는 401).
 * 딥링크로 들어왔다가 로그인하면 그 자리로 돌려보내야 하므로 어디로 가려 했는지 들고 간다(쿼리 포함 — 소셜 연결 결과 `?linked=` 도 남는다).
 * `next` 는 같은 사이트 안의 경로만 허용한다(`UtilRedirect`).
 *
 * 쓰는 곳: `definePageMeta({ middleware: 'auth' })`
 */
export default defineNuxtRouteMiddleware(async (to) => {
    // ⚠ await 앞에서 컴포저블을 부른다 — 뒤에서는 Nuxt 문맥이 없다
    const { ensureLoaded } = useAuth();
    if (await ensureLoaded()) return;

    return navigateTo(UtilRedirect.toLogin(to.fullPath));
});
