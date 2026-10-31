/** 기간 고르기 달력(`BusinessPeriodCalendar`)의 값 — 달력 날짜 글자(`YYYY-MM-DD`, 끝 포함). 아직 끝을 안 골랐으면 `end` 가 '' */
export interface PeriodValue {
    start: string;
    end: string;
}
