/**
 * 포인트 API 가 주고받는 모양 — 서버 `point/dto/response`(`PointSummaryView`·`PointTodayView`·`PointRulesView` …)와 같다. 계약: `docs/features/point-contract.md`.
 * 지금 셸(헤더 메뉴·햄버거)은 잔액·출석 안내만 쓰고, 나머지 칸은 포인트 화면을 만들 때 쓴다.
 */

/** 오늘 그 일로 받은 횟수와 하루 상한(0 이면 꺼짐) */
export interface PointCount {
    count: number;
    cap: number;
}

/** 오늘(한국 시간) 현황 */
export interface PointToday {
    /** 서버가 정한 한국 시간 오늘 `YYYY-MM-DD` — 브라우저 시계로 계산하지 않는다 */
    date: string;
    /** 오늘 출석했는가 — 출석 기능이 없으면 늘 false(`attendanceAvailable`) */
    attended: boolean;
    post: PointCount;
    comment: PointCount;
    /** 추천 보너스(글·댓글 합산) */
    likeBonus: PointCount;
}

/** 인기글 보상 규칙 — 인기글 기능이 아직 없어 지급은 없다 */
export interface PopularRewardRule {
    topN: number;
    points: number;
}

/** 추천 받음 보너스 — 인정된 추천 `threshold` 개에 `points`, 하루 `dailyCap` 번(0 이면 꺼짐) */
export interface LikeBonusRule {
    threshold: number;
    points: number;
    dailyCap: number;
}

/** 지급 규칙(관리자 정책 — 저장하면 최대 15초 안에 바뀐다) */
export interface PointRules {
    /** 출석 포인트(출석 기능은 아직 없다) */
    attendance: number;
    post: number;
    comment: number;
    popularReward: PopularRewardRule;
    /** 닉네임 변경권 가격(교환 기능은 아직 없다) */
    nicknameTicketPrice: number;
    likeBonus: LikeBonusRule;
}

/** `GET /api/me/points` */
export interface PointSummary {
    balance: number;
    /** 닉네임 변경권 보유 수 — 교환 기능이 없어 늘 0 */
    ticketCount: number;
    /** 출석 기능이 있는가 — 없으면(지금은 늘 false) 화면은 「오늘 출석하기」 안내를 그리지 않는다 */
    attendanceAvailable: boolean;
    today: PointToday;
    rules: PointRules;
}
