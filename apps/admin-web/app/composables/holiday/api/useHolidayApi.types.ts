/**
 * 휴일 API(`useHolidayApi`)의 모양 — 서버 계약(휴일 관리 · 달력 표시) 그대로.
 * 달력 한 줄(`HolidayDay`)은 공유 레이어(`@roboyam/display-time` 의 `util-holiday`)의 모양을 그대로 쓴다.
 */

/** 휴일 줄의 출처 — `PUBLIC_DATA` 공공데이터(한국천문연구원 특일 정보) · `SEED` 기본값 · `OPERATOR` 운영자가 넣은 회사 휴무일 */
export type HolidaySource = 'PUBLIC_DATA' | 'SEED' | 'OPERATOR';

/** 휴일 관리 목록의 한 줄 */
export interface HolidayRow {
    id: string;
    /** 달력 날짜 글자 `YYYY-MM-DD` */
    date: string;
    name: string;
    source: HolidaySource;
    /** 대체공휴일인가 */
    substitute: boolean;
    /** 넣은 운영자(OPERATOR 줄) — 없으면 null */
    createdBy: string | null;
    /** ISO 시각 */
    createdAt: string;
}

/** 공공데이터 받기 결과 — `NO_KEY` 는 인증키가 없어 받지 않은 것(실패가 아니다) */
export type HolidaySyncOutcome = 'OK' | 'PARTIAL' | 'NO_KEY' | 'FAILED';

/** 마지막 받기 */
export interface HolidaySync {
    /** ISO 시각 */
    ranAt: string;
    outcome: HolidaySyncOutcome;
    appliedYears: number[];
    failedYears: number[];
    /** 바뀐 줄 수 */
    changed: number;
    /** 「지금 받기」를 누른 운영자 — 정기 받기면 null */
    ranBy: string | null;
}

/** `GET /api/admin/holidays?year=` */
export interface HolidayYearView {
    year: number;
    /** 날짜 차례 */
    rows: HolidayRow[];
    sync: HolidaySync | null;
    /** 공공데이터 인증키가 있는가 */
    keyConfigured: boolean;
}

/** `POST /api/admin/holidays` — 회사 휴무일 더하기 */
export interface HolidayCreateRequest {
    /** `YYYY-MM-DD` */
    date: string;
    /** 1~30자 */
    name: string;
}
