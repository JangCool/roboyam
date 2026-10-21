/**
 * 표시 값(시간대·시간 형식·날짜 형식)을 앱이 시작할 때, 그리고 **로그인한 사람이 바뀔 때마다**(로그인·로그아웃·다른 계정) 서버에서 받아
 * 공유 레이어(`@roboyam/display-time`)의 상태에 채운다(`useDisplaySettings().follow()` → `GET /api/display/effective`).
 *
 * - 파일 이름이 `auth.client.ts` 다음이라 이 플러그인이 돌 때는 로그인 상태(`user`)가 이미 정해져 있다 — 이 사람의 기억값을 **바로**(동기로) 쓰고,
 *   기억값의 주인이 다른 사람이면 버리고 시스템 폴백으로 시작한다(`UtilDisplaySettings`). 로그인 화면에서 나중에 로그인하는 경우는 `user` 를 지켜보다가 그때 다시 받는다.
 * - **앱 시작을 막지 않는다** — 기다리지 않고 흘려보낸다. 첫 화면은 기억값(또는 폴백)으로 그리고 서버 값이 오면 바뀐 부분만 다시 그린다.
 * - 설치 전(`pending`)에는 받지 않는다 — 설치가 끝나면(상태가 바뀌면) 그때 받는다.
 * - 서버에 닿지 못하거나 서버에 이 API 가 없어 `404` 여도 앱은 뜬다 — 기억값 → 시스템 폴백 그대로 그려지고 오류 화면은 없다.
 */
export default defineNuxtPlugin(() => {
    const { user } = useAuth();
    const install = useInstallState();
    const display = useDisplaySettings();

    watch(
        [() => user.value?.id ?? null, install.phase],
        () => {
            void display.follow();
        },
        { immediate: true },
    );
});
