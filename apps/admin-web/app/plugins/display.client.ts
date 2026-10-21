/**
 * 로그인한 관리자의 표시 값(내 표기 ← 제품 기준)을 받아 화면 전체에 채운다 — 언어·시간대·시간/날짜 형식(`@roboyam/display-time` 의 `useDisplay().setDisplay`).
 *
 * 파일 이름이 `auth.client.ts` 보다 뒤라(알파벳 순) 로그인 상태가 정해진 뒤에 돈다. 로그인한 사람이 정해지거나 바뀔 때마다(로그인 · 다른 계정) 한 번 받고, 로그아웃하면 비운다(폴백으로).
 * 받지 못해도 던지지 않는다 — 지금 값(폴백)으로 화면은 그대로 그려진다. 앱 시작을 막지 않으려고 기다리지 않는다.
 * ⚠ 서버가 붙기 전에는 목업(`profile-mock.ts`)이 제품 기준 값을 준다.
 */
export default defineNuxtPlugin(() => {
    const { user } = useAdminAuth();
    const { load, clear } = useMyDisplay();

    watch(
        () => user.value?.id ?? null,
        (id) => {
            if (id) void load();
            else clear();
        },
        { immediate: true },
    );
});
