/**
 * 회원 탈퇴 관리(관리자) API 의 요청·응답 모양 — 서버 `MemberWithdrawalAdminView`·`MemberWithdrawalAdminRequest`(server/admin `withdrawal/dto`)와 같은 모양. 값은 서버 표 `member_withdrawals`(V132)·
 * `WithdrawalStatus`·`WithdrawalPendingCode`·`WithdrawalChoices` 와 같다(모임 선택 `DISSOLVE` 는 서버가 `DISBAND` 로 바꿔 준다).
 * 정책의 정본: `docs/features/member-withdrawal-contract.md`, `docs/domain/commerce.md` 17절. 시각은 UTC ISO, ID 는 문자열.
 *
 * 개인정보: 완료된 탈퇴의 닉네임·이메일은 서버에 없다(익명화) — `nickname` 은 진행 중·취소 건에만 온다. 완료 건의 연락용 이메일은 기존 분리 보관 API(`useWithdrawnContactApi`)로만 연다.
 */

/** 탈퇴 신청 기록의 상태 — 서버 `WithdrawalStatus` */
export type WithdrawalStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELED';

/** 남은 일 종류 — 서버 `WithdrawalPendingCode` */
export type WithdrawalPendingCode = 'GROUP_BUY_ORDER' | 'RETURN_REQUEST' | 'PAYMENT_CANCEL' | 'POINT_CHARGE_REFUND' | 'POINT_SHOP_REFUND' | 'BIZ_APPLICATION' | 'DELIVERED_RETURN_WINDOW';

/** 탈퇴 사유 6종(`member_withdrawals.reason`) — 고르지 않으면 null */
export type WithdrawalReason = 'NOT_USING' | 'PRIVACY' | 'INCONVENIENT' | 'OTHER_SERVICE' | 'REJOIN' | 'OTHER';

/** 신청 경로 — 회원 본인 · 운영자 강제 */
export type WithdrawalSource = 'MEMBER' | 'OPERATOR';

/** 운영자 강제 탈퇴의 사유 종류 — 서버 `MemberWithdrawalAdminRequest.Force.reasonKind` */
export type ForceWithdrawReasonKind = 'UNDER_14' | 'POLICY_VIOLATION' | 'ON_BEHALF' | 'OTHER';

/** 회원이 고른 것 — 서버 `WithdrawalChoices` 를 정규화한 값이라 늘 값이 있다(해당 없는 상황이어도 기본값: 환불 · 기다리기 · 기다리기) */
export interface WithdrawalChoicesView {
    /** 충전 포인트 — 환불받고 탈퇴 · 포기 */
    paidPointChoice: 'REFUND' | 'FORFEIT';
    /** 배송완료 7일 안 주문 — 반품 기한까지 기다리기 · 지금 탈퇴 */
    deliveredChoice: 'WAIT' | 'NOW';
    /** 진행 중 광고·핫딜 — 끝날 때까지 기다리기 · 남은 기간 포기 */
    bizChoice: 'WAIT' | 'FORFEIT';
    /** 모임장인 소모임별 처리 — `group_choices` */
    groupChoices: WithdrawalGroupChoice[];
}

export interface WithdrawalGroupChoice {
    groupId: string;
    groupName: string;
    /** 넘기기(대상 멤버) · 해산 · 자동 위임 */
    choice: 'TRANSFER' | 'DISBAND' | 'AUTO';
    /** `TRANSFER` 일 때 넘길 멤버의 닉네임(탈퇴 완료 뒤에는 null 일 수 있다) */
    transferTo: string | null;
}

/** 남은 일 한 건 — 서버 `MemberWithdrawalAdminView.PendingItem`(`WithdrawalPending` + 관리자 화면이 연결할 대상 ID `refId`) */
export interface WithdrawalPendingItem {
    code: WithdrawalPendingCode;
    /** 식별 글(주문번호·신청 번호 등). 화면은 가공하지 않는다 */
    label: string;
    /** 관리자 화면 링크를 만들 대상 ID(주문·요청·결제·환불 신청·광고 신청 건의 ID). 모르면 null */
    refId: string | null;
    /** 언제쯤 끝나는지 알면 그 시각(반품 기한·광고 끝), 모르면 null */
    waitUntil: string | null;
}

/** 포기 동의 금액(`forfeit_*`) — 포기를 고른 것이 없으면 모두 0 */
export interface WithdrawalForfeit {
    paidPoints: number;
    refundEstimate: number;
    bizAmount: number;
    /** 동의 시각 — 포기한 것이 없으면 null */
    consentedAt: string | null;
}

/** 처리 이력 한 줄 — 신청·안내 메일·완료·회원 취소·운영자 대리 취소·강제(운영자) */
export interface WithdrawalEvent {
    kind: 'REQUESTED' | 'PROGRESS_MAILED' | 'COMPLETED' | 'CANCELED' | 'OPERATOR_CANCELED' | 'FORCED';
    at: string;
    /** 운영자가 한 일이면 그 이름(강제 탈퇴·대리 취소) — 시스템·회원이면 null */
    operatorName: string | null;
    /** 덧붙일 말 — `FORCED` 는 강제 탈퇴의 운영 메모, `OPERATOR_CANCELED` 는 대리 취소의 운영 메모, 나머지는 null */
    note: string | null;
}

/** 운영자 대리 취소의 요청 경로(사용자 결정 2026-11-04 — 회원이 고객센터·전화로 취소를 요청한 경우만) */
export type WithdrawalCancelChannel = 'CS_INQUIRY' | 'PHONE' | 'EMAIL' | 'OTHER';

/** 운영자 대리 취소 — `POST /api/admin/member-withdrawals/{id}/cancel`. 진행 중 + 회원이 신청한 탈퇴(`source = MEMBER`)만 */
export interface WithdrawalCancelRequest {
    requestChannel: WithdrawalCancelChannel;
    /** 5~500자(문의 번호·통화 시각 등) */
    memo: string;
    /** 「회원 본인이 취소를 요청했나요?」 확인 — 늘 true 여야 한다 */
    memberConfirmed: true;
}

/** 대리 취소 메모 길이(서버 `MemberWithdrawalAdminService` 와 같은 값) */
export const WITHDRAWAL_CANCEL_MEMO_MIN = 5;
export const WITHDRAWAL_CANCEL_MEMO_MAX = 500;

/** 목록 한 줄 — `GET /api/admin/member-withdrawals` */
export interface MemberWithdrawalRow {
    id: string;
    memberNo: string;
    /** 진행 중·취소 건만. 완료 건은 null(「탈퇴한 회원」) */
    nickname: string | null;
    status: WithdrawalStatus;
    source: WithdrawalSource;
    requestedAt: string;
    /**
     * 남은 일 종류별 개수 — **스냅샷**(`pending_snapshot`): 진행 중 건은 배치가 30분마다 다시 센 것, 완료·취소 건은 끝날 때의 마지막 모습.
     * 상세(`MemberWithdrawalDetail`)는 진행 중이면 지금 다시 센 값이다.
     */
    pendingCounts: Partial<Record<WithdrawalPendingCode, number>>;
    pendingCount: number;
    expectedCompleteAt: string | null;
    /** 10일 안내 메일을 보낸 시각 — 안 보냈으면 null */
    progressNotifiedAt: string | null;
    /** 결제 이력이 있어 완료 때 연락용 이메일을 분리 보관하는가(했는가) */
    hasPaymentHistory: boolean;
    completedAt: string | null;
    canceledAt: string | null;
}

/** 상세 — `GET /api/admin/member-withdrawals/{id}` */
export interface MemberWithdrawalDetail extends MemberWithdrawalRow {
    /** 신청 전 계정 상태(취소하면 돌아갈 상태) */
    priorStatus: 'ACTIVE' | 'SUSPENDED' | 'BANNED';
    reason: WithdrawalReason | null;
    reasonNote: string | null;
    choices: WithdrawalChoicesView;
    forfeit: WithdrawalForfeit;
    pending: WithdrawalPendingItem[];
    /** 재가입 가능 시각 — 완료 건만(진행 중·취소는 null) */
    rejoinUntil: string | null;
    /** 운영자 강제 탈퇴일 때의 사유 — 회원 신청이면 null */
    force: { reasonKind: ForceWithdrawReasonKind; memo: string; operatorName: string | null } | null;
    /** 운영자가 대리 취소했을 때의 요청 경로·메모 — 회원 본인이 취소했거나 취소되지 않았으면 null */
    cancel: { requestChannel: WithdrawalCancelChannel; memo: string; operatorName: string | null } | null;
    events: WithdrawalEvent[];
}

export interface MemberWithdrawalPage {
    items: MemberWithdrawalRow[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

/**
 * 목록 조건 — 빈 값은 보내지 않는다. 기간은 UTC ISO 순간의 반열림 `[from, to)` — 화면은 시작 날짜의 0시와 끝 날짜의 **다음 날 0시**(사이트 시간대)를 보낸다.
 * 끝 < 시작이면 서버가 `400 VALIDATION`(`fields.requestedTo|completedTo`).
 */
export interface MemberWithdrawalQuery {
    status?: WithdrawalStatus;
    /** 회원 번호(정확히) */
    memberNo?: string;
    requestedFrom?: string;
    requestedTo?: string;
    completedFrom?: string;
    completedTo?: string;
    /** `<칸>,<asc|desc>` — 칸은 `requestedAt`(기본, desc) · `expectedCompleteAt` · `completedAt` · `canceledAt` · `pendingCount` */
    sort?: string;
    /** 0 부터 */
    page?: number;
    /** 1~100 */
    size?: number;
}

/** 요약 숫자 — `GET /api/admin/member-withdrawals/summary`(기간 조건과 무관) */
export interface MemberWithdrawalSummary {
    scheduled: number;
    completedToday: number;
    completedThisMonth: number;
    /** 신청 뒤 10일이 지났는데 아직 진행 중인 건 */
    overdue: number;
}

/** 사유 분포 한 칸 — `reason` 이 null 이면 「고르지 않음」 */
export interface WithdrawalReasonCount {
    reason: WithdrawalReason | null;
    count: number;
}

/** 사유 분포 — `GET /api/admin/member-withdrawals/reasons?requestedFrom&requestedTo`(신청일 기간을 따른다, 상태 무관) */
export interface MemberWithdrawalReasonStats {
    total: number;
    items: WithdrawalReasonCount[];
}

/** 회원 한 명의 탈퇴 기록(최근 것) — `GET /api/admin/members/{memberId}/withdrawal`. 신청한 적 없으면 404 `WITHDRAWAL_NOT_FOUND` */
export type MemberWithdrawalOfMember = MemberWithdrawalDetail;

/** 강제 탈퇴 미리보기 — `GET /api/admin/members/{memberId}/withdrawal/preview`(남은 일·돈을 확인 대화상자에 보인다) */
export interface ForceWithdrawPreview {
    memberNo: string;
    nickname: string;
    pending: WithdrawalPendingItem[];
    /** 적립 포인트(소멸) */
    earnedPoints: number;
    /** 충전 포인트(환불 대상 — 충전 전이라 늘 0) */
    paidPoints: number;
    /** 진행 중·예약 광고·핫딜 건 수 */
    activeBizCount: number;
    /** 모임장인 ACTIVE 소모임 */
    ownedGroups: { groupId: string; groupName: string }[];
    /** 결제 이력이 있어 완료 때 연락용 이메일을 분리 보관하는가 */
    hasPaymentHistory: boolean;
    /** 완료 때 적용될 재가입 가능일(예상) */
    rejoinUntil: string;
}

/** 강제 탈퇴 — `POST /api/admin/members/{memberId}/withdrawal`. 선택값은 회원에게 유리한 기본값으로 서버가 고정한다(배송 대기·광고 기다리기·충전 환불·모임 자동) */
export interface ForceWithdrawRequest {
    reasonKind: ForceWithdrawReasonKind;
    /** 5~500자 */
    memo: string;
}

/** 강제 탈퇴 결과 — 즉시 완료(`COMPLETED`) 또는 남은 일이 있어 진행 중(`SCHEDULED`) */
export interface ForceWithdrawResult {
    withdrawalId: string;
    status: 'COMPLETED' | 'SCHEDULED';
    pendingCount: number;
    expectedCompleteAt: string | null;
    completedAt: string | null;
}

/** 강제 탈퇴 메모 길이(서버 `MemberWithdrawalAdminService` 와 같은 값) */
export const FORCE_WITHDRAW_MEMO_MIN = 5;
export const FORCE_WITHDRAW_MEMO_MAX = 500;
