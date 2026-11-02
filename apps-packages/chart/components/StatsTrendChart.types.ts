/**
 * `StatsTrendChart` 의 계약 타입 — 앱은 `#layers/chart/components/StatsTrendChart.types` 로 import 한다.
 * 레이어는 앱의 i18n 키·숫자 규칙을 모르므로, 화면 문구는 앱이 번역한 글자(`StatsTrendLabels`)로, 숫자·날짜 글자는 앱의 함수(`StatsTrendFormat`)로 넘긴다.
 */

/** 그날 상태 — 일부라도 일시 정지가 걸쳤으면 `PAUSED`(주황 띠), 내림·중단이면 `TAKEN_DOWN`(빨강 띠) */
export type StatsTrendDayState = 'SHOWING' | 'PAUSED' | 'TAKEN_DOWN';

/** 차트 날짜 하나(오래된 차례, 빈 날 없이). `state` 가 없으면 띠를 깔지 않는다(운영자 합계) */
export interface StatsTrendDay {
    /** `YYYY-MM-DD` */
    date: string;
    impressions: number;
    clicks: number;
    state?: StatsTrendDayState;
}

/** 앱이 번역해 넘기는 문구 묶음 */
export interface StatsTrendLabels {
    /** 차트 상자(`role="img"`)의 요약 `aria-label` — 기간·합계 등 */
    summary: string;
    /** 초점 칸(`role="group"`)의 이름 — 화살표 안내 */
    keys: string;
    /** 계열 이름(툴팁·숨긴 표 머리) */
    impressions: string;
    clicks: string;
    ctr: string;
    /** 숨긴 표 머리 */
    date: string;
    state: string;
    tableCaption: string;
    /** 오늘 날짜 뒤에 붙는 말(「집계 중」 — 툴팁·숨긴 표) */
    today: string;
    /** 그날 상태 이름(툴팁·숨긴 표) */
    states: Record<StatsTrendDayState, string>;
    /** 범례 */
    legend: string;
    legendImpressions: string;
    legendClicks: string;
    legendPaused: string;
    legendTakenDown: string;
    legendToday: string;
}

/** 앱이 넘기는 글자 함수 — 빠진 것은 레이어 기본(천 단위 쉼표 · 날짜 그대로 · `M/D` · 소수 한 자리 %) */
export interface StatsTrendFormat {
    /** 툴팁·숨긴 표의 숫자 */
    number: (n: number) => string;
    /** 축 눈금의 짧은 숫자(1.2만 · 12K) */
    axis: (n: number) => string;
    /** 툴팁·숨긴 표의 날짜 */
    date: (ymd: string) => string;
    /** x 축의 짧은 날짜 */
    shortDate: (ymd: string) => string;
    /** 클릭률 글자 — 노출 0 이면 「—」 */
    ctr: (c: { impressions: number; clicks: number }) => string;
}
