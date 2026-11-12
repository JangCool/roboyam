/**
 * 모든 달력의 쉬는 날 표시(`@irobit/display-time` 의 `useHolidays`)가 받을 곳을 넣는다 — 관리자 앱은 `GET /api/admin/holidays/calendar`(공휴일 + 회사 휴무일).
 *
 * 파일 이름이 `auth.client.ts` 보다 뒤라(알파벳 순) 로그인 상태가 정해진 뒤에 돈다. 로그인한 사람이 정해지거나 바뀔 때마다 받는 함수를 다시 넣어 캐시를 비우고,
 * 로그아웃하면 빼 둔다(로그인 전에는 받지 않는다 — 달력은 표시 없이 그대로). 기다리지 않는다 — 달력을 열 때 그 해를 받는다.
 */
export default defineNuxtPlugin(() => {
    const { user } = useAdminAuth();
    const api = useHolidayApi();
    const source = useHolidaySource();

    watch(
        () => user.value?.id ?? null,
        (id) => source.setLoader(id ? (from, to) => api.calendar(from, to) : null),
        { immediate: true },
    );
});
