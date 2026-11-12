/**
 * 모든 달력의 쉬는 날 표시(`@irobit/display-time` 의 `useHolidays`)가 받을 곳을 넣는다 — 회원 앱은 `GET /api/holidays`(공휴일만, 비로그인도 받는다).
 *
 * - 기다리지 않는다 — 달력을 열 때(또는 inline 달력이 그려질 때) 그 해를 받는다. 못 받아도 달력은 표시 없이 그대로다.
 * - **설치 전(`pending`)에는 받지 않는다** — `/api/**` 가 `503 NOT_INSTALLED` 다(그때는 받는 함수를 빼 둔다). 설치가 끝나면 그때 넣는다.
 */
export default defineNuxtPlugin(() => {
    const install = useInstallState();
    const api = useHolidayApi();
    const source = useHolidaySource();

    watch(
        install.phase,
        (phase) => source.setLoader(phase === 'pending' ? null : (from, to) => api.calendar(from, to)),
        { immediate: true },
    );
});
