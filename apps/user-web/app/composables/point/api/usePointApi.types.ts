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
    /** 옛 닉네임 변경권 가격 칸 — 값의 정본은 포인트 상점 품목 가격으로 옮긴다(`commerce-stage1-contract.md` 결정 2 — 서버가 칸을 지울 때까지 실려 온다). 화면은 쓰지 않는다 */
    nicknameTicketPrice: number;
    likeBonus: LikeBonusRule;
}

/** 가장 가까운 소멸 예정 — 그날 소멸될 적립 포인트의 양 */
export interface PointExpiring {
    /** 소멸일 — 한국 시간 서비스 날짜 `YYYY-MM-DD`(브라우저 시계로 계산하지 않는다) */
    date: string;
    amount: number;
}

/**
 * `GET /api/me/points` — `freeBalance`·`paidBalance`·`chargeAvailable`·`expiring`·`revokeDebt` 는 커머스 1-1(`docs/features/commerce-stage1-contract.md` 「A」).
 */
export interface PointSummary {
    /** 합계(= 적립 + 충전) */
    balance: number;
    /** 적립 포인트(무상 — 받은 날부터 1년 뒤 소멸) */
    freeBalance: number;
    /** 충전 포인트(유상 — 소멸 없음). 충전 기능(3단계) 전에는 늘 0 */
    paidBalance: number;
    /** 충전 기능이 열렸나 — false(지금은 늘)면 화면은 적립/충전으로 나누지 않고 「포인트」 하나만 보인다(commerce D13) */
    chargeAvailable: boolean;
    /** 가장 가까운 소멸 예정 — 없으면 null */
    expiring: PointExpiring | null;
    /** 미회수 포인트(0 이상) — 지운 글·댓글로 받은 포인트를 돌려받을 때 모자라 덜 가져간 양. 다음 적립에서 먼저 차감된다 */
    revokeDebt: number;
    /** 닉네임 변경권 보유 수 — 서버는 아직 늘 0(포인트 상점 1-2 서버가 실제 값을 채운다). 프로필 설정은 `MyProfile.nicknameChange.ticketCount` 를 본다 */
    ticketCount: number;
    /** 출석 기능이 있는가 — 없으면(지금은 늘 false) 화면은 「오늘 출석하기」 안내를 그리지 않는다 */
    attendanceAvailable: boolean;
    today: PointToday;
    rules: PointRules;
}

/**
 * 내 포인트 내역 한 줄의 종류 — 적립 · 사용(포인트 상점 구매 — 1-2 전에는 오지 않는다) · 차감·회수 · 사용 취소(포인트 상점 환불) · 소멸(유효기간 만료).
 * 관리자 원장의 `REFUND`(충전 환불)는 충전 기능(3단계)과 함께 더한다.
 */
export type MyPointLedgerKind = 'EARN' | 'USE' | 'REVOKE' | 'CANCEL_USE' | 'EXPIRE';

/**
 * 내 포인트 내역 한 줄의 사유 코드 — 관리자 원장(`PointReasonCode`, admin-web)과 같은 이름 중 **지금 실제로 생기는 것**만 적었다(`point-contract.md` 「적립·회수」).
 * 출석·인기글·포인트 상점(`POINT_SHOP_PURCHASE`·`POINT_SHOP_REFUND`)·핫딜·가입 사유는 기능이 생기면 더한다. 모르는 코드가 와도 화면은 서버 `reason` 글자를 그대로 보인다.
 */
export type MyPointReasonCode =
    | 'POST'
    | 'COMMENT'
    | 'LIKE_BONUS'
    | 'POST_REVOKE'
    | 'POST_RESTORE'
    | 'COMMENT_REVOKE'
    | 'LIKE_BONUS_REVOKE'
    | 'ADMIN_ADJUST'
    // 커머스 1-1·1-2 — 소멸 · 미회수 상계 · 포인트 상점 구매·환불
    | 'EXPIRE'
    | 'REVOKE_DEBT_OFFSET'
    | 'POINT_SHOP_PURCHASE'
    | 'POINT_SHOP_REFUND';

/** 그 줄이 가리키는 글 — 글·댓글 사유만. 지웠거나 숨겼거나 볼 수 없으면 `available=false`, `title=null` */
export interface MyPointLedgerTarget {
    postId: string;
    title: string | null;
    available: boolean;
}

/** `GET /api/me/points/ledger` 한 줄(**서버 연결됨** — server/api `MyPointLedgerItemView`. 관리자 원장 `PointLedgerItem` 에서 회원·운영자 칸을 빼고 글을 더한 모양) */
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
    /** 글·댓글 사유 줄만 — 소멸·사용·사용 취소·미회수 상계·운영 조정 줄은 null */
    target: MyPointLedgerTarget | null;
    /**
     * 사용 취소(`CANCEL_USE`)·글 복구로 돌아온 포인트의 원래 묶음이 이미 만료돼 **30일짜리 새 묶음**으로 돌려준 경우 그 소멸 시각(UTC ISO). 그 밖에는 null.
     */
    restoredUntil: string | null;
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
