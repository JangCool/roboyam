import type { HolidayDay } from '#layers/display-time/utils/util-holiday';

/**
 * 공휴일 API — 서버 주소 문자열(`/api/holidays`)이 있는 **유일한 곳**이다. 상태는 없다(달력 표시 캐시는 레이어의 `useHolidays`).
 *  - `GET /api/holidays?from=YYYY-MM-DD&to=YYYY-MM-DD` → `HolidayDay[]`(공휴일만, `kind: 'HOLIDAY'` — 회사 휴무일은 오지 않는다). 비로그인도 받는다.
 *    범위는 최대 2년(넘거나 from>to·형식 오류면 400). 서버가 `Cache-Control: public, max-age=300` + `ETag` 로 내려 준다. 같은 날 줄이 둘일 수 있다.
 */
const PATH = {
    holidays: '/api/holidays',
};

export function useHolidayApi() {
    const client = useApiClient();

    return {
        /** 범위(끝 포함)의 공휴일 */
        calendar: (from: string, to: string): Promise<HolidayDay[]> => client.get<HolidayDay[]>(PATH.holidays, { query: { from, to } }),
    };
}
