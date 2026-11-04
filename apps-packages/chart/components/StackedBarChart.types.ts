/**
 * `StackedBarChart` 의 계약 타입 — 앱은 `#layers/chart/components/StackedBarChart.types` 로 import 한다.
 * 레이어는 앱의 i18n 키·숫자 규칙을 모르므로, 문구는 앱이 번역한 글자로, 숫자·칸 글자는 앱의 함수로 넘긴다.
 */

/** 쌓을 계열 하나 — `values` 는 `categories` 와 같은 길이·차례 */
export interface StackedBarSeries {
    key: string;
    name: string;
    /** CSS 색 식(`var(--p-blue-500)` 등) — 레이어가 실제 색으로 풀어 쓴다 */
    color: string;
    values: number[];
}

export interface StackedBarLabels {
    /** 차트 상자(`role="img"`)의 요약 `aria-label` */
    summary: string;
    tableCaption: string;
    /** 숨긴 표 첫 머리(「날짜」) */
    category: string;
    /** 툴팁·숨긴 표의 합 */
    total: string;
    /** 범례 묶음 이름 */
    legend: string;
    /** 집계 중인 칸 뒤에 붙는 말 */
    current: string;
}

export interface StackedBarFormat {
    number: (n: number) => string;
    axis: (n: number) => string;
    /** 툴팁·숨긴 표의 칸 이름 */
    category: (key: string) => string;
    /** x 축의 짧은 칸 이름 */
    shortCategory: (key: string) => string;
}
