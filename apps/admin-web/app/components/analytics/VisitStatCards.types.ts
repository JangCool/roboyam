/** `VisitStatCards` 의 계약 타입 — 카드를 만드는 쪽들(검색어·봇·크롤러·정보주체 요청)이 같이 쓴다 */

/** 카드 하나 */
export interface VisitStatCard {
    key: string;
    label: string;
    /** 큰 글자(이미 숫자 글자로 만든 것) */
    value: string;
    /** 아래 작은 글자 */
    sub?: string;
    /** ⓘ 툴팁 — 없으면 단추를 두지 않는다 */
    help?: string;
    /** 숫자 색 — 주의(노랑)·문제(빨강) */
    tone?: 'warn' | 'danger';
}
