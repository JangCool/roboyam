import type { MemberPointKind } from '~/composables/member/api/useMemberApi.types';

/**
 * 포인트 API 가 주고받는 모양 — 서버 `point/dto/response`(`PointLedgerItemView`·`PointLedgerPageView`·`PointSettingsResponse` …)와 같다(`docs/features/point-contract.md`).
 * community `docs/api-point.md` 의 관리자 API 를 roboyam 권한 코드로 옮기고, 전체 원장 조회를 더했다.
 * 종류 이름(`EARN`·`USE`·`REVOKE`)은 회원 상세의 포인트 내역(`MemberPointKind`·`MemberPointEntry`)과 같고, 사유 코드는 community 원장(`point_ledger.reason`)과 같다(가입 포인트 `SIGNUP` 만 roboyam 추가).
 * ⚠ ID 는 문자열이다(Snowflake). 숫자로 바꾸지 않는다.
 */

/** 원장 한 줄의 종류 — 지급 · 사용(교환권 구매) · 회수. 회원 상세의 포인트 내역과 같은 이름이다 */
export type PointLedgerKind = MemberPointKind;

/**
 * 원장 한 줄의 사유 코드 — community `point_ledger.reason` 그대로 + `SIGNUP`(가입 포인트, 그누보드에서 가져온 roboyam 추가).
 * `ADMIN_ADJUST` 는 부호에 따라 `EARN`(지급) 또는 `REVOKE`(차감)다.
 */
export type PointReasonCode =
    | 'ATTENDANCE'
    | 'POST'
    | 'COMMENT'
    | 'POPULAR'
    | 'POST_REVOKE'
    | 'COMMENT_REVOKE'
    | 'TICKET_PURCHASE'
    | 'ADMIN_ADJUST'
    | 'DEAL_END_REQUEST'
    | 'LIKE_BONUS'
    | 'LIKE_BONUS_REVOKE'
    | 'SIGNUP';

/** 원장 한 줄 — 최신이 위 */
export interface PointLedgerItem {
    entryId: string;
    /** 회원번호 */
    memberId: string;
    /** 지금 닉네임(서버가 줄을 만들 때가 아니라 조회 시점의 이름을 붙인다). 탈퇴한 회원은 원장이 소멸해 나오지 않는다 */
    memberNickname: string;
    kind: PointLedgerKind;
    reasonCode: PointReasonCode;
    /** 서버가 요청 언어로 주는 사유 이름(출석체크·글 작성 …) — 회원 상세 `MemberPointEntry.reason` 과 같은 뜻 */
    reason: string;
    /** 운영자 조정의 사유(회원에게 공개되는 문구). 그 밖의 줄은 null */
    note: string | null;
    /** 실제 반영된 증감(−는 차감·회수). 잔액이 모자라 덜 회수된 줄은 덜 회수된 값이다 */
    delta: number;
    /** 이 줄을 반영한 뒤의 잔액(0 이상) */
    balanceAfter: number;
    /** 운영자 조정을 한 운영자의 닉네임. 자동 지급·회수는 null(화면은 "시스템") */
    actorNickname: string | null;
    createdAt: string;
}

/** 기간 합계 — 같은 조건으로 거른 줄 전체(쪽이 아니라) 기준 */
export interface PointLedgerSummary {
    /** 지급 합계(+ 줄의 합, 0 이상) */
    earned: number;
    /** 차감·회수·사용 합계(− 줄의 크기의 합, 0 이상) */
    spent: number;
    /** 운영자 조정(`ADMIN_ADJUST`) 건수 */
    adjustCount: number;
}

/** `GET /api/admin/points/ledger` 의 조건. 시각은 순간(UTC ISO). 빈 값은 보내지 않는다 */
export interface PointLedgerQuery {
    from?: string;
    to?: string;
    kind?: PointLedgerKind;
    reasons?: PointReasonCode[];
    /** 회원 검색어 — 닉네임·이메일·회원번호 */
    q?: string;
    page: number;
    size: number;
}

export interface PointLedgerPage {
    items: PointLedgerItem[];
    total: number;
    summary: PointLedgerSummary;
}

/** 조정 대상 회원 후보 — `GET /api/admin/points/member-candidates?q=` */
export interface PointMemberCandidate {
    memberId: string;
    nickname: string;
    /** 이메일 — `member.pii` 가 없으면 서버가 가려서 준다. 없으면 null */
    email: string | null;
    /** 지금 잔액 */
    balance: number;
}

/** `POST /api/admin/members/{id}/points` — delta 는 0 이 아닌 정수 −10000~10000, reason 1~100자(회원의 포인트 내역에 공개된다) */
export interface PointAdjustRequest {
    delta: number;
    reason: string;
}

export interface PointAdjustResponse {
    /** 실제 반영된 값 — 차감은 0P 까지라 요청보다 작을 수 있다. 0 이면 원장 줄·알림이 없다 */
    applied: number;
    /** 반영 뒤 잔액 */
    balance: number;
}

/**
 * 포인트 정책 값 — community `point_settings` 13칸 + `signupPoints`(roboyam 추가). `PUT` 은 모두 보낸다.
 * 0 이면 그 항목은 꺼진다(교환권 가격·추천 보너스 문턱은 1 이상).
 */
export interface PointSettingsValues {
    attendancePoints: number;
    postPoints: number;
    postDailyCap: number;
    commentPoints: number;
    commentDailyCap: number;
    popularRewardPoints: number;
    popularRewardTopN: number;
    likeBonusThreshold: number;
    likeBonusPoints: number;
    likeBonusDailyLimit: number;
    dealEndRequestPoints: number;
    dealEndRequestDailyLimit: number;
    signupPoints: number;
    nicknameTicketPrice: number;
}

/** 정책 값의 칸 이름 */
export type PointSettingsField = keyof PointSettingsValues;

/** `GET·PUT /api/admin/settings/points` 의 응답 — 값 + 마지막 변경(처음 상태면 null) */
export interface PointSettingsResponse extends PointSettingsValues {
    updatedAt: string | null;
    updatedBy: string | null;
}
