/**
 * `WeekHourHeatmap` 의 계약 타입 — 앱은 `#layers/chart/components/WeekHourHeatmap.types` 로 import 한다.
 * 레이어는 앱의 i18n 키·숫자 규칙을 모르므로, 문구는 앱이 번역한 글자로, 숫자 글자는 앱의 함수로 넘긴다.
 */

/** 칸 하나 — `row` 0~6(위에서 아래, 앱이 정한 요일 차례), `col` 0~23(시). 값이 null 이면 아직 없는 칸(빗금 없이 비움) */
export interface WeekHourCell {
    row: number;
    col: number;
    value: number | null;
}

export interface WeekHourLabels {
    /** 차트 상자(`role="img"`)의 요약 `aria-label` */
    summary: string;
    /** 숨긴 표 */
    tableCaption: string;
    /** 줄 이름(요일) 7개 */
    rows: string[];
    /** 열 이름(시) 24개 — 축은 3시간마다만 보인다 */
    cols: string[];
    /** 툴팁의 값 이름(「평균 방문자」) */
    value: string;
    /** 값이 없는 칸 */
    none: string;
    /** 범례 양 끝 */
    low: string;
    high: string;
}

export interface WeekHourFormat {
    /** 툴팁·숨긴 표의 값 */
    number: (n: number) => string;
}
