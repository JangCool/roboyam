import type { MemberPointKind } from '~/composables/member/api/useMemberApi.types';

/**
 * 포인트 API 가 주고받는 모양 — 서버 `point/dto/response`(`PointLedgerItemView`·`PointLedgerPageView`·`PointSettingsResponse` …)와 같다(`docs/features/point-contract.md`).
 * community `docs/api-point.md` 의 관리자 API 를 roboyam 권한 코드로 옮기고, 전체 원장 조회를 더했다.
 * 종류 이름(`EARN`·`USE`·`REVOKE`)은 회원 상세의 포인트 내역(`MemberPointKind`·`MemberPointEntry`)과 같고, 사유 코드는 community 원장(`point_ledger.reason`)과 같다(가입 포인트 `SIGNUP` 만 roboyam 추가).
 * ⚠ ID 는 문자열이다(Snowflake). 숫자로 바꾸지 않는다.
 */

/** 원장 한 줄의 종류 — 지급 · 사용(포인트 상점 구매) · 회수 · 사용 취소 · 소멸 · 환불(충전 환불 — 3단계). 회원 상세의 포인트 내역과 같은 이름이다 */
export type PointLedgerKind = MemberPointKind;

/**
 * 원장 한 줄의 사유 코드 — community `point_ledger.reason` 그대로 + `SIGNUP`(가입 포인트, 그누보드에서 가져온 roboyam 추가)
 * + 커머스 1-1·1-2(`POINT_SHOP_PURCHASE`·`POINT_SHOP_REFUND`·`REVOKE_DEBT_OFFSET`(미회수 상계)·`EXPIRE`). 거르기 목록에 넣는 것만 적었다 —
 * 3단계·공구 사유(`CHARGE`·`CHARGE_BONUS`·`CHARGE_REFUND`·`GB_POINT_USE` …)는 기능이 생길 때 더한다(서버가 모르는 코드를 줘도 화면은 서버 `reason` 글자를 그대로 보인다).
 * `ADMIN_ADJUST` 는 부호에 따라 `EARN`(지급) 또는 `REVOKE`(차감)다.
 */
export type PointReasonCode =
    | 'ATTENDANCE'
    | 'POST'
    | 'COMMENT'
    | 'POPULAR'
    | 'POST_REVOKE'
    | 'POST_RESTORE'
    | 'COMMENT_REVOKE'
    | 'TICKET_PURCHASE'
    | 'ADMIN_ADJUST'
    | 'DEAL_END_REQUEST'
    | 'LIKE_BONUS'
    | 'LIKE_BONUS_REVOKE'
    | 'SIGNUP'
    | 'POINT_SHOP_PURCHASE'
    | 'POINT_SHOP_REFUND'
    | 'REVOKE_DEBT_OFFSET'
    | 'EXPIRE';

/** 원장 한 줄 — 최신이 위 */
export interface PointLedgerItem {
    entryId: string;
    /** 회원번호 */
    memberId: string;
    /** 지금 닉네임(서버가 줄을 만들 때가 아니라 조회 시점의 이름을 붙인다). 탈퇴한 회원은 원장이 소멸해 나오지 않는다 */
    memberNickname: string;
    kind: PointLedgerKind;
    /** 사유 코드 — 화면이 모르는 코드(3단계 `CHARGE` 등)도 올 수 있다(그대로 두고 `reason` 글자를 보인다) */
    reasonCode: PointReasonCode | string;
    /** 서버가 요청 언어로 주는 사유 이름(출석체크·글 작성 …) — 회원 상세 `MemberPointEntry.reason` 과 같은 뜻 */
    reason: string;
    /** 운영자 조정의 사유(회원에게 공개되는 문구). 그 밖의 줄은 null */
    note: string | null;
    /** 실제 반영된 증감(−는 차감·회수). 잔액이 모자라 덜 회수된 줄은 덜 회수된 값이다. `freeDelta + paidDelta` 와 같다 */
    delta: number;
    /** 그중 적립(무상) 포인트의 증감 / 충전(유상) 포인트의 증감(커머스 1-1) */
    freeDelta: number;
    paidDelta: number;
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
    /** 지금 잔액(합계 = 적립 + 충전) */
    balance: number;
    /** 그중 적립(무상) / 충전(유상) — 조정은 적립 포인트만 바꾼다(차감 예상은 `freeBalance` 기준) */
    freeBalance: number;
    paidBalance: number;
}

/**
 * `POST /api/admin/members/{id}/points` — delta 는 0 이 아닌 정수 −100000~100000(커머스 1-1),
 * 크기가 문턱(`PointSettingsResponse.adjustLargeThreshold`, 기본 10000, 0 = 모든 조정)을 넘으면 `point.adjust.large` 가 필요하다(없으면 403 `POINT_ADJUST_LARGE_REQUIRED`). reason 1~100자(회원의 포인트 내역에 공개된다).
 * **적립(무상) 포인트만** 바꾼다 — +는 1년짜리 적립 묶음, −는 적립 포인트에서만(0P 바닥, 충전 포인트는 건드리지 않는다).
 */
export interface PointAdjustRequest {
    delta: number;
    reason: string;
}

export interface PointAdjustResponse {
    /** 실제 반영된 값 — 차감은 적립 포인트 0P 까지라 요청보다 작을 수 있다. 0 이면 원장 줄·알림이 없다 */
    applied: number;
    /** 반영 뒤 잔액(합계) */
    balance: number;
}

// ── 회원 상세의 포인트 묶음(커머스 1-1) ─

/** 묶음 종류 — 적립(무상) · 충전(유상) */
export type PointLotKind = 'FREE' | 'PAID';

/** 받은 포인트 한 묶음(`point_lots` 한 줄) */
export interface PointLot {
    lotId: string;
    kind: PointLotKind;
    originalAmount: number;
    remainingAmount: number;
    /** 소멸 시각(UTC ISO) — 적립은 늘 있고, 충전은 null(소멸 없음) */
    expiresAt: string | null;
    /** 출처 사유 코드 — `POST`·`COMMENT`·`LIKE_BONUS`·`ADMIN_ADJUST`·`MIGRATION`(기존 잔액 전환)·`POINT_SHOP_REFUND`(사용 취소로 생긴 30일 묶음) 등 */
    sourceReasonCode: string;
    /** 서버가 요청 언어로 준 출처 이름 */
    sourceReason: string;
    /** 사용 취소로 원래 묶음이 만료돼 새로 만든 30일 묶음인가 */
    restored: boolean;
    createdAt: string;
    /** 소멸일이 지났나 */
    expired: boolean;
}

/** 덜 회수된 기록(`point_revoke_shortfalls` 한 줄) — 돌려받을 양(`requested`) 중 실제로 가져간 양(`applied`) */
export interface PointRevokeShortfall {
    id: string;
    refType: 'POST' | 'COMMENT';
    refId: string;
    requested: number;
    applied: number;
    createdAt: string;
}

/** 가장 가까운 소멸 예정 — 그날 소멸될 적립 포인트의 양 */
export interface PointExpiring {
    /** 한국 시간 서비스 날짜 `YYYY-MM-DD` */
    date: string;
    amount: number;
}

/** `GET /api/admin/members/{id}/point-lots?active&page&size` 의 조건 */
export interface PointMemberLotsQuery {
    /** true 면 남은 양이 있고 소멸일이 지나지 않은 묶음만(차감 순서). false 면 전체(최근 받은 순 — 지난 것은 `expired=true`) */
    active: boolean;
    /** 0부터 */
    page: number;
    size: number;
}

/** `GET /api/admin/members/{id}/point-lots` — 잔액 나눔 · 소멸 예정 · 미회수 · 묶음 한 쪽 · 최근 미회수 기록 5건 */
export interface PointMemberLots {
    freeBalance: number;
    paidBalance: number;
    /** 미회수 포인트(0 이상) — 다음 적립에서 먼저 차감된다 */
    revokeDebt: number;
    expiring: PointExpiring | null;
    lots: { items: PointLot[]; total: number };
    /** 최근 것이 먼저, 최대 5건 */
    shortfalls: PointRevokeShortfall[];
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
}

/** 정책 값의 칸 이름 */
export type PointSettingsField = keyof PointSettingsValues;

/** `GET·PUT /api/admin/settings/points` 의 응답 — 값 + 읽기 전용 조정 문턱 + 마지막 변경(처음 상태면 null) */
export interface PointSettingsResponse extends PointSettingsValues {
    /**
     * 수동 조정의 고액 문턱(`point_settings.adjust_large_threshold`, 0~100000, 기본 10000) — |delta| 가 이 값을 넘으면 `point.adjust.large` 가 필요하다(0 = 모든 조정이 고액).
     * 읽기 전용: `PUT` 에는 보내지 않는다(`PointSettingsValues` 에 없다 — 정책 화면 칸은 커머스 1-3).
     */
    adjustLargeThreshold: number;
    updatedAt: string | null;
    updatedBy: string | null;
}
