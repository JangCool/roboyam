/**
 * 보기 설정을 서버에서 받는다 — 앱이 시작할 때, 그리고 **로그인한 사람이 바뀔 때마다**(로그인·로그아웃·다른 계정). 서버 값이 이긴다(`useViewSettings().load`).
 *  - 파일 이름 차례로 `auth.client.ts` 다음이라 로그인 상태가 이미 정해져 있다. 나중에 로그인하면 `user` 를 지켜보다가 그때 받는다.
 *  - **앱 시작을 막지 않는다** — 첫 화면은 이 브라우저의 값(`view-theme.client.ts`)으로 그리고 서버 값이 오면 바뀐 부분만 다시 그린다.
 *  - 로그아웃하면 서버와의 연결 상태만 비우고 이 브라우저의 마지막 값은 남긴다.
 */
export default defineNuxtPlugin(() => {
    const { user } = useAuth();
    const settings = useViewSettings();

    watch(
        () => user.value?.id ?? null,
        (id) => {
            settings.reset();
            if (id) void settings.load();
        },
        { immediate: true },
    );
});
