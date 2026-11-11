/** `VisitMonthlyTrendChart` 의 계약 타입 — 월별 보기 쪽이 같이 쓴다 */

/** 선으로 볼 숫자 — 순방문자(MAU) · 신규 방문자 · 페이지뷰(막대는 늘 방문자 일별 합) */
export const VISIT_MONTHLY_LINES = ['UNIQUE', 'NEW', 'VIEWS'] as const;
export type VisitMonthlyLine = (typeof VISIT_MONTHLY_LINES)[number];
