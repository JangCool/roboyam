/** `DashboardStatCard` 의 계약 타입 — 숫자 칸을 만드는 가젯들이 같이 쓴다 */

/** 카드 톤 — `warn` 주황 · `danger` 빨강 */
export type DashboardStatTone = 'normal' | 'warn' | 'danger';

/** 카드의 링크 하나 */
export interface DashboardStatLink {
    label: string;
    to: string;
}

/** 숫자 칸 하나(목록으로 그릴 때) */
export interface DashboardStat {
    key: string;
    label: string;
    count: number | string;
    note?: string;
    icon?: string;
    tone?: DashboardStatTone;
    to?: string;
    links?: DashboardStatLink[];
}
