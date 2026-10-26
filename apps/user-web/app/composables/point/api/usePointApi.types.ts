/**
 * 포인트 API 가 주고받는 모양 — 서버 `point/dto/response`(`PointSummaryView`·`PointTodayView`·`PointRulesView` …)와 같다. 계약: `docs/features/point-contract.md`.
 * 셸(헤더 메뉴·햄버거)·내 정보는 잔액·출석 안내를, 포인트 화면(`/me/points`)은 나머지 칸과 출석 달력(`AttendanceMonth`)을 쓴다.
 */

/** 오늘 그 일로 받은 횟수와 하루 상한(0 이면 꺼짐) */
export interface PointCount {
    count: number;
    cap: number;
}

/**
 * 오늘 활동 적립 합과 하루 총 한도(커머스 1-3) — 글·댓글·추천 보너스·출석으로 받은 포인트의 합(P). 인기글 보상·운영 조정·충전 보너스·가입 포인트는 넣지 않는다.
 * `cap` 은 정책 `dailyTotalCap`(0 = 총 한도 없음).
 */
export interface PointTotal {
    amount: number;
    cap: number;
}

/** 오늘(한국 시간) 현황 */
export interface PointToday {
    /** 서버가 정한 한국 시간 오늘 `YYYY-MM-DD` — 브라우저 시계로 계산하지 않는다 */
    date: string;
    /** 오늘(한국 시간) 출석했는가 */
    attended: boolean;
    post: PointCount;
    comment: PointCount;
    /** 추천 보너스(글·댓글 합산) */
    likeBonus: PointCount;
    /** 오늘 활동 적립 합 / 하루 총 한도(커머스 1-3) */
    total: PointTotal;
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

/** 본인인증 보너스(가입 포인트, 커머스 1-3) — 본인인증을 마치면 계정마다 평생 한 번 */
export interface SignupPointRule {
    /** 줄 포인트(정책 `signupPoints`, 0 = 주지 않음) */
    points: number;
    /** 이미 받았나 */
    received: boolean;
}

/** 연속 출석 보너스(커머스 1-3 — roboyam 추가) — 연속 출석이 `days` 의 배수가 될 때마다 `bonus` P 를 더 준다. 둘 중 하나가 0 이면 꺼짐 */
export interface AttendanceStreakRule {
    days: number;
    bonus: number;
}

/** 지급 규칙(관리자 정책 — 저장하면 최대 15초 안에 바뀐다) */
export interface PointRules {
    /** 출석 포인트(하루 한 번) */
    attendance: number;
    /** 연속 출석 보너스(커머스 1-3) */
    attendanceStreak: AttendanceStreakRule;
    post: number;
    comment: number;
    popularReward: PopularRewardRule;
    /** 옛 닉네임 변경권 가격 칸 — 값의 정본은 포인트 상점 품목 가격으로 옮긴다(`commerce-stage1-contract.md` 결정 2 — 서버가 칸을 지울 때까지 실려 온다). 화면은 쓰지 않는다 */
    nicknameTicketPrice: number;
    likeBonus: LikeBonusRule;
    // ── 커머스 1-3 적립 규칙 보강(`docs/domain/commerce.md` 6-2) — 숫자 0 · false = 그 조건 없음
    /** 글 본문 최소 글자 수(서식·공백·그림 뺌) — 게시판에 따라 적용하지 않을 수 있다 */
    postMinLength: number;
    /** 댓글 최소 글자 수 */
    commentMinLength: number;
    /** 내 글에 단 댓글(답글 포함)은 적립 없음 */
    commentOwnPostExcluded: boolean;
    /** 한 글에는 댓글 적립 한 번 */
    commentOncePerPost: boolean;
    /** 포인트를 받은 글·댓글 뒤 이 시간(초) 안에 쓴 것은 적립 없음 */
    writeIntervalSeconds: number;
    /** 활동 적립 하루 총 한도(P, 0 = 없음) — `today.total.cap` 과 같은 값 */
    dailyTotalCap: number;
    signup: SignupPointRule;
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
    /** 출석 기능이 있는가(커머스 1-3 부터 서버는 true) — false 면 화면은 출석 달력·「오늘 출석하기」 안내를 그리지 않는다 */
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
 * 인기글·핫딜 사유는 기능이 생기면 더한다. 모르는 코드가 와도 화면은 서버 `reason` 글자를 그대로 보인다.
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
    | 'POINT_SHOP_REFUND'
    // 커머스 1-3 — 출석 · 연속 출석 보너스 · 가입 포인트(본인인증)
    | 'ATTENDANCE'
    | 'ATTENDANCE_STREAK'
    | 'SIGNUP';

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

/** 출석 달력의 오늘 — 서버가 정한 한국 시간 오늘 */
export interface AttendanceToday {
    /** `YYYY-MM-DD`(한국 시간) */
    date: string;
    attended: boolean;
}

/**
 * `GET /api/me/attendance?month=YYYY-MM` — 한 달의 출석(커머스 1-3, community `docs/api-point.md` 「출석」 + roboyam `streak`).
 * 미래 달은 `days: []`. `streak` 은 보는 달과 상관없이 오늘 또는 어제까지 이어진 연속 출석 일수(끊겼으면 0).
 */
export interface AttendanceMonth {
    /** `YYYY-MM` */
    month: string;
    /** 출석한 날(일 번호, 1~31) */
    days: number[];
    /** 이 달 출석 일수 */
    monthCount: number;
    streak: number;
    today: AttendanceToday;
}

/** `POST /api/me/attendance` — 오늘 출석 결과(커머스 1-3). 동결 중이면 `earned`·`streakBonus` 가 0 이고 출석은 남는다 */
export interface AttendResult {
    /** 출석한 한국 날짜 `YYYY-MM-DD` */
    date: string;
    /** 출석으로 실제 받은 포인트(하루 총 한도·미회수 상계 뒤) */
    earned: number;
    /** 이번 출석으로 받은 연속 출석 보너스(없으면 0) */
    streakBonus: number;
    /** 오늘까지 이어진 연속 출석 일수(1 이상) */
    streak: number;
    /** 반영 뒤 보유 포인트 */
    balance: number;
}
