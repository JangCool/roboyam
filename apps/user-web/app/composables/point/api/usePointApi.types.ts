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

/** 내 포인트 내역 한 줄의 종류 — 적립 · 사용(교환 — 기능이 없어 지금은 오지 않는다) · 차감·회수 */
export type MyPointLedgerKind = 'EARN' | 'USE' | 'REVOKE';

/**
 * 내 포인트 내역 한 줄의 사유 코드 — 관리자 원장(`PointReasonCode`, admin-web)과 같은 이름 중 **지금 실제로 생기는 것**만 적었다(`point-contract.md` 「적립·회수」).
 * 출석·인기글·교환·핫딜·가입 사유는 기능이 생기면 더한다. 모르는 코드가 와도 화면은 서버 `reason` 글자를 그대로 보인다.
 */
export type MyPointReasonCode = 'POST' | 'COMMENT' | 'LIKE_BONUS' | 'POST_REVOKE' | 'COMMENT_REVOKE' | 'LIKE_BONUS_REVOKE' | 'ADMIN_ADJUST';

/** 그 줄이 가리키는 글 — 글·댓글 사유만. 지웠거나 숨겼거나 볼 수 없으면 `available=false`, `title=null` */
export interface MyPointLedgerTarget {
    postId: string;
    title: string | null;
    available: boolean;
}

/** `GET /api/me/points/ledger` 한 줄(**서버에 아직 없다** — 목업. 관리자 원장 `PointLedgerItem` 에서 회원·운영자 칸을 빼고 글을 더한 모양) */
export interface MyPointLedgerItem {
    entryId: string;
    kind: MyPointLedgerKind;
    reasonCode: MyPointReasonCode | string;
    /** 서버가 요청 언어로 푼 사유 이름(글 작성 · 댓글 작성 · 추천 보너스 · 운영 조정 …) — 화면은 그대로 보인다 */
    reason: string;
    /** 운영 조정 사유(회원에게 공개되는 문구). 그 밖의 줄은 null */
    note: string | null;
    /** 실제 반영된 증감(−는 차감·회수) */
    delta: number;
    /** 이 줄을 반영한 뒤의 잔액 */
    balanceAfter: number;
    target: MyPointLedgerTarget | null;
    createdAt: string;
}

/** `GET /api/me/points/ledger` — 최신이 위, 커서 쪽 */
export interface MyPointLedgerPage {
    items: MyPointLedgerItem[];
    hasNext: boolean;
    nextCursor: string | null;
}

/** `GET /api/me/points/ledger` 의 조건 — 빈 값은 보내지 않는다 */
export interface MyPointLedgerQuery {
    /** 없으면 전체 */
    kind?: MyPointLedgerKind;
    /** 1~50(기본 20) */
    size?: number;
    cursor?: string;
}
