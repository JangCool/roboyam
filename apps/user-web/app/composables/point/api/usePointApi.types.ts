/**
 * 포인트·출석·교환 API 가 주고받는 모양 중 셸(헤더 메뉴·햄버거)의 포인트 한 줄이 쓰는 것만 남긴 단순 버전 — 8단계 계약서(`docs/api-point.md`).
 * 포인트 화면을 만들 때 원본(community)으로 채운다.
 */

/** 지급 규칙(설정값) — 셸은 출석 포인트(`attendance`)만 본다. 나머지 규칙은 포인트 화면이 쓴다 */
export interface PointRules {
    attendance: number;
}

/** 오늘(한국 시간) 현황 — 셸은 출석 여부만 본다 */
export interface PointToday {
    /** 한국 시간 오늘 `YYYY-MM-DD` */
    date: string;
    attended: boolean;
}

/** `GET /api/me/points` */
export interface PointSummary {
    balance: number;
    /** 닉네임 변경권 보유 수 */
    ticketCount: number;
    today: PointToday;
    rules: PointRules;
}
