import UtilRedirect from '~/utils/util-redirect';

/**
 * 로그인 가드 — 모든 화면 이동에 걸린다(`.global`). 관리자 앱의 화면은 **기본이 보호 화면**이라 화면마다 따로 적지 않는다.
 *
 *  - 로그인하지 않았으면 `/login?next=<가려던 곳>` 으로 보낸다.
 *  - 로그인 없이 볼 수 있는 화면(`/login`)은 `definePageMeta({ public: true })` 로 표시한다. 그 화면에서 이미 로그인한 사람을 `/` 로 돌려보내는 것은 `guest` 미들웨어다.
 *
 * 상태는 앱이 뜰 때 한 번 받아 두므로(`plugins/auth.client.ts`) 이동마다 서버를 부르지 않는다.
 * **이것은 편의이지 보안이 아니다** — 진짜로 막는 것은 서버(`/api/admin/**` 의 401·403)다. 정적 파일(JS·CSS·이미지)은 라우터를 거치지 않아 영향이 없다.
 */
export default defineNuxtRouteMiddleware(async (to) => {
    if (to.meta.public === true) return;

    // ⚠ await 앞에서 컴포저블을 부른다 — 뒤에서는 Nuxt 문맥이 없다
    const { ensureLoaded } = useAdminAuth();
    if (await ensureLoaded()) return;

    return navigateTo(UtilRedirect.toLogin(to.fullPath), { replace: true });
});
