/**
 * 회원 탈퇴 API 의 주고받는 모양 — 계약 `docs/features/member-withdrawal-contract.md`, 서버 `com.community.withdrawal.dto`(`WithdrawalView`·`WithdrawalRequest`·`WithdrawalResult`)와 같은 모양.
 * 규칙의 정본은 `docs/domain/commerce.md` 17-1(탈퇴 신청·탈퇴 예약). 시각은 ISO 글자(서버 `Instant`), 금액·포인트는 숫자(서버 `long`).
 */

/** 탈퇴 상태 — `NONE` 신청 전 · `SCHEDULED` 탈퇴 진행 중(남은 일이 끝나면 배치가 완료) */
export type WithdrawalState = 'NONE' | 'SCHEDULED';

/**
 * 남은 일(끝나야 탈퇴가 완료되는 것, 17-1) —
 *  `GROUP_BUY_ORDER` 진행 중 공구 주문 · `RETURN_REQUEST` 열린 취소·반품 요청 · `PAYMENT_CANCEL` 끝나지 않은 결제 취소(환불) ·
 *  `POINT_CHARGE_REFUND` 진행 중 충전 환불 · `POINT_SHOP_REFUND` 심사 중 포인트샵 환불 신청 · `BIZ_APPLICATION` 「기다리기」를 고른 광고·핫딜 ·
 *  `DELIVERED_RETURN_WINDOW` 「반품 기한까지 기다리기」를 고른 배송완료 주문
 */
export type WithdrawalPendingCode = 'GROUP_BUY_ORDER' | 'RETURN_REQUEST' | 'PAYMENT_CANCEL' | 'POINT_CHARGE_REFUND' | 'POINT_SHOP_REFUND' | 'BIZ_APPLICATION' | 'DELIVERED_RETURN_WINDOW';

/** 남은 일 한 건 — 건마다 링크(`link`)와, 언제쯤 끝나는지 알면 그 시각(`waitUntil`) */
export interface WithdrawalPendingItem {
    code: WithdrawalPendingCode;
    /** 서버가 주는 식별 글(주문번호·신청 번호 등) — 없으면 null(서버 `WithdrawalPending.label`). 화면은 가공하지 않는다 */
    label: string | null;
    link: string | null;
    waitUntil: string | null;
}

/** 사라지는 것 — 적립(무상) 포인트 · 포인트샵 아이템 · 등급·출석 */
export interface WithdrawalLosses {
    freePoints: number;
    /** 가진 아이템 수(소멸) */
    items: number;
    /** 그중 산 지 7일 안이고 쓰지 않아 환불 신청할 수 있는 것 */
    refundableItems: number;
    /** 지금 등급 이름(없으면 null) */
    gradeName: string | null;
    /** 연속 출석 일수 */
    attendanceStreak: number;
}

/**
 * 충전(유상) 포인트 — 「환불받고 탈퇴」/「포기하고 탈퇴」를 고르는 근거(17-1, 9-3).
 *  `refundEstimate` = 충전 포인트 − `fee`. `fee` 는 충전 7일이 지난 묶음의 10%(사유는 탈퇴가 아니라 「청약철회 기간 경과」).
 */
export interface WithdrawalMoney {
    paidPoints: number;
    refundEstimate: number;
    fee: number;
    /** 수수료가 붙은 묶음의 포인트 합(안내용) */
    feeBasePoints: number;
    feeRatePercent: number;
}

/** 배송완료 7일(단순 변심 반품 기한) 안의 주문 — 「반품 기한까지 기다리기」(기본) / 「지금 탈퇴」를 고른다(D2) */
export interface WithdrawalDeliveredOrder {
    orderNo: string;
    link: string;
    deliveredAt: string;
    returnDeadline: string;
}

/** 진행 중 광고·핫딜 신청 건(사업자 회원) — 「끝날 때까지 기다리기」(기본) / 「포기하고 지금 탈퇴」 */
export interface WithdrawalBizRunning {
    id: string;
    kind: 'AD' | 'HOTDEAL';
    /** 서버가 주는 이름(광고 제목·핫딜 글 제목) */
    label: string;
    link: string;
    endsAt: string;
    /** 포기하면 잃는 금액(남은 기간분) */
    forfeitAmount: number;
}

export interface WithdrawalBusiness {
    running: WithdrawalBizRunning[];
    /** 포기하면 잃는 금액 합 */
    forfeitTotal: number;
    /** 이미 취소돼 환불을 기다리는 금액 합 — 포기와 무관하게 환불된다(따로 보인다) */
    refundWaitingTotal: number;
}

/** 모임장을 넘길 수 있는 멤버 */
export interface WithdrawalGroupCandidate {
    id: string;
    nickname: string;
    role: 'MANAGER' | 'MEMBER';
}

/** 내가 모임장인 소모임 — 「넘기기」/「해산」을 고르고, 고르지 않으면 자동 위임(매니저 → 가장 오래된 멤버), 멤버가 없으면 자동 해산(D6) */
export interface WithdrawalGroupLed {
    id: string;
    name: string;
    link: string;
    memberCount: number;
    candidates: WithdrawalGroupCandidate[];
    /** 고르지 않으면 넘겨받을 사람. 멤버가 없으면 null(자동 해산) */
    autoDelegate: WithdrawalGroupCandidate | null;
}

/** 재가입 — 같은 이메일·소셜 계정·본인인증으로 다시 가입할 수 있는 날(30일, 제재 중이면 제재 끝나는 날 중 늦은 쪽 — D3) */
export interface WithdrawalRejoin {
    availableAt: string;
    /** 기능 제한·정지 때문에 30일보다 늦춰졌는가 */
    extendedByRestriction: boolean;
    restrictionEndsAt: string | null;
    /** 제한 사유(서버 문구) */
    restrictionReason: string | null;
}

/** 본인 확인 — 비밀번호가 있으면 비밀번호 1회, 소셜 전용 회원은 최근 10분 안 로그인 */
export interface WithdrawalVerification {
    method: 'PASSWORD' | 'RECENT_LOGIN';
    /** `RECENT_LOGIN` 일 때 최근 10분 안에 로그인했는가 */
    recentLoginOk: boolean;
}

/** 남는 것 — 글·댓글 수(「탈퇴한 회원」으로 남는다), 거래 이력(있으면 연락용 이메일 분리 보관 — 결제 외에 로보티콘 판매 수익이 있던 작가도 true, V148) */
export interface WithdrawalRemains {
    posts: number;
    comments: number;
    hasPaymentHistory: boolean;
    /** 거래 기록·연락용 이메일 보관 기간(년) */
    retentionYears: number;
}

/**
 * 로보티콘 작가 요약(V148 — 계약 `emoticon-contract.md` 「탈퇴 작가 미확정 정리」) — 작가(만든 팩이 하나라도 있거나 정산 정보를 등록함)만 온다.
 *  탈퇴 완료 때 판매 중인 팩의 새 판매가 멈추고(가진 회원은 계속 쓴다), 심사 대기 건은 반려로 닫힌다. 남은 수익은 정산 정보가 있으면 최소 지급액과 상관없이 다음 지급일에 한 번에 지급.
 */
export interface WithdrawalEmoticonCreator {
    /** 판매 중인 내 팩 수 — 탈퇴하면 새 판매가 멈춘다 */
    onSalePacks: number;
    /** 심사 대기 건 수 — 탈퇴하면 반려로 닫힌다 */
    pendingReviews: number;
    /** 아직 받지 못한 수익(원, **원천징수 전** — 예정·확정·보류 + 넘긴 금액, 양수만, 최소 지급액과 상관없이 셈) */
    unpaidEarnings: number;
    /** 정산 정보(계좌 등) 등록 여부 */
    payoutAccountRegistered: boolean;
    /** 수익이 있는데 정산 정보가 없다 — 「정산 정보 등록」 또는 「남은 수익 포기 동의」를 골라야 탈퇴된다(아니면 `409 WITHDRAWAL_EMOTICON_EARNINGS_UNPAID`) */
    decisionRequired: boolean;
}

/** 탈퇴 진행 중일 때의 정보 */
export interface WithdrawalScheduled {
    requestedAt: string;
    /** 남은 일의 `waitUntil` 중 가장 늦은 것. 모르면 null */
    expectedCompleteAt: string | null;
    pending: WithdrawalPendingItem[];
}

/** `GET /api/me/withdrawal` */
export interface WithdrawalView {
    state: WithdrawalState;
    losses: WithdrawalLosses;
    /** 충전 포인트가 없으면 null */
    money: WithdrawalMoney | null;
    remains: WithdrawalRemains;
    /** 이미 남아 있는 일(선택과 무관하게 기다려야 하는 것) */
    pending: WithdrawalPendingItem[];
    delivered: WithdrawalDeliveredOrder[];
    /** 사업자 회원이 아니거나 진행 중 건이 없으면 null */
    business: WithdrawalBusiness | null;
    groupsLed: WithdrawalGroupLed[];
    rejoin: WithdrawalRejoin;
    verification: WithdrawalVerification;
    /** 고객센터 메일(탈퇴 뒤 반품·환불·하자·글 블라인드 요청 창구) */
    supportEmail: string;
    /** 탈퇴 진행 중일 때만 */
    scheduled: WithdrawalScheduled | null;
    /** 로보티콘 작가 요약 — 작가가 아니면 null */
    emoticonCreator: WithdrawalEmoticonCreator | null;
}

export type WithdrawalReasonCode = 'NOT_USING' | 'PRIVACY' | 'INCONVENIENT' | 'OTHER_SERVICE' | 'REJOIN' | 'OTHER';
export type PaidPointChoice = 'REFUND' | 'FORFEIT';
export type DeliveredChoice = 'WAIT' | 'NOW';
export type BizChoice = 'WAIT' | 'FORFEIT';
export type GroupChoiceAction = 'AUTO' | 'TRANSFER' | 'DISSOLVE';

export interface WithdrawalGroupChoice {
    groupId: string;
    action: GroupChoiceAction;
    /** `TRANSFER` 일 때 넘겨받을 멤버 */
    toMemberId: string | null;
}

/** 포기 동의 — 동의한 금액을 그대로 보낸다(서버가 기록, 5년) */
export interface WithdrawalForfeitConsent {
    paidPoints: number | null;
    refundEstimate: number | null;
    bizForfeit: number | null;
    /** 로보티콘 남은 수익 포기(V148) — 화면이 보인 `emoticonCreator.unpaidEarnings` 그대로. 포기하지 않으면 null */
    emoticonEarnings: number | null;
}

/** `POST /api/me/withdrawal` */
export interface WithdrawalRequest {
    /** 비밀번호가 있는 계정만 */
    password: string | null;
    reason: WithdrawalReasonCode | null;
    reasonNote: string | null;
    paidPointChoice: PaidPointChoice | null;
    deliveredChoice: DeliveredChoice | null;
    bizChoice: BizChoice | null;
    groupChoices: WithdrawalGroupChoice[];
    forfeitConsent: WithdrawalForfeitConsent;
    /**
     * 정산 정보 없이 남은 로보티콘 수익을 받지 않기로 동의(V148) — 서버는 `emoticonCreator.decisionRequired` 일 때만 본다.
     * true 면 `forfeitConsent.emoticonEarnings` 가 서버 금액과 같아야 한다(다르면 `400 fields.forfeitConsent`)
     */
    waiveEmoticonEarnings: boolean;
}

/** `POST /api/me/withdrawal` 의 답 — 즉시 완료면 세션은 끝나 있다 */
export interface WithdrawalResult {
    state: 'COMPLETED' | 'SCHEDULED';
    pending: WithdrawalPendingItem[];
    expectedCompleteAt: string | null;
    /** 완료 화면(`/withdrawn`)에 보일 것 — 세션이 끝난 뒤라 화면이 들고 간다 */
    rejoinAvailableAt: string;
    hasPaymentHistory: boolean;
    retentionYears: number;
    supportEmail: string;
}
