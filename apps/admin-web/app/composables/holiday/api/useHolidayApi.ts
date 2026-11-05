import type { HolidayDay } from '#layers/display-time/utils/util-holiday';
import type { HolidayCreateRequest, HolidayRow, HolidaySync, HolidayYearView } from '~/composables/holiday/api/useHolidayApi.types';

/**
 * 휴일 API — 서버 주소 문자열(`/api/admin/holidays…`)이 있는 **유일한 곳**이다. 상태는 없다(달력 표시 캐시는 레이어의 `useHolidays`).
 * 사이트의 쉬는 날은 서버 표 하나(출처 `PUBLIC_DATA` 공공데이터 · `SEED` 기본값 · `OPERATOR` 회사 휴무일)다:
 *  - `GET    /api/admin/holidays/calendar?from&to` → `HolidayDay[]`  (로그인한 운영자 누구나 — 모든 달력의 표시, `kind` 는 `HOLIDAY`·`COMPANY`)
 *  - `GET    /api/admin/holidays?year=` → `HolidayYearView`  (`site.read`)
 *  - `POST   /api/admin/holidays` `{ date, name }` → `HolidayRow`  (`site.manage` — 회사 휴무일 더하기)
 *      · 오류: `400 VALIDATION`(`fields.date` 없음·그날 회사 휴무일이 이미 있음 · `fields.name` 필수·1~30자)
 *  - `DELETE /api/admin/holidays/{id}` → 204  (`site.manage`) · `404 HOLIDAY_NOT_FOUND` · `409 HOLIDAY_NOT_EDITABLE`(OPERATOR 줄이 아님)
 *  - `POST   /api/admin/holidays/sync` → `HolidaySync`  (`site.manage` — 「지금 받기」, 키가 없으면 `NO_KEY`)
 */
const PATH = {
    root: '/api/admin/holidays',
    calendar: '/api/admin/holidays/calendar',
    sync: '/api/admin/holidays/sync',
    one: (id: string) => `/api/admin/holidays/${encodeURIComponent(id)}`,
};

export function useHolidayApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 달력 표시용 쉬는 날(공휴일 + 회사 휴무일) — 범위 끝 포함 */
        calendar: (from: string, to: string): Promise<HolidayDay[]> => client.get<HolidayDay[]>(PATH.calendar, { query: { from, to } }),

        /** 그 해의 휴일 목록 · 마지막 받기 · 인증키 여부 */
        year: (year: number): Promise<HolidayYearView> => client.get<HolidayYearView>(PATH.root, { query: { year } }),

        /** 회사 휴무일 더하기 */
        create: (body: HolidayCreateRequest): Promise<HolidayRow> => client.post<HolidayRow>(PATH.root, body),

        /** 회사 휴무일 지우기(OPERATOR 줄만) */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /** 공공데이터에서 지금 받기 */
        sync: (): Promise<HolidaySync> => client.post<HolidaySync>(PATH.sync),
    };
}
