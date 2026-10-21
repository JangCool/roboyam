/**
 * 앱이 시작할 때 로그인 상태를 먼저 받는다 (`useAdminAuthApi().me()`).
 *
 * - 라우트 가드가 첫 화면을 정하기 전에 "누구인지"를 알아야 한다(깜빡이며 로그인으로 튕기는 것을 막는다).
 * - **CSRF 쿠키(`XSRF-TOKEN`)도 이 호출에서 내려온다.** 이후 POST 는 이 쿠키를 헤더에 실어야 통과한다.
 * - 사이트 이름(`siteName`)도 이 응답에 실려 와서 로그인 화면의 로고·탭 제목이 쓴다.
 *
 * 플러그인이 끝나야 첫 이동(미들웨어)이 시작된다. 서버에 닿지 못해도 앱은 뜬다 — 비로그인으로 보고,
 * 로그인을 시도하면 그때 오류를 보여 준다.
 */
export default defineNuxtPlugin(async () => {
    const { refresh } = useAdminAuth();
    try {
        await refresh();
    } catch {
        /* loaded 는 true 로 남는다. 화면이 각자 서버 오류를 보여 준다 */
    }
});
