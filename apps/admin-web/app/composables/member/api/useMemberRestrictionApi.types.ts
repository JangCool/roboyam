/**
 * 회원 기능 제한 API 의 요청·응답 모양(계약 「0-4」 — 서버 `MemberRestrictionView`·`MemberRestrictionCreateRequest`·`MemberRestrictionLiftRequest`, commerce.md 12-3).
 * 시각은 UTC ISO(`Instant`). ID 는 문자열이다(Snowflake 64비트).
 */

/**
 * 제한 종류 — 계정 전체가 아니라 기능 하나를 막는다(계정 정지와 따로 걸린다).
 * - `POINT_FREEZE` 포인트 동결: 포인트 적립·사용·충전을 막는다 / 그래도 되는 것: 충전 포인트 환불 신청
 * - `GROUP_BUY_BAN` 공동구매 참여 제한: 새 참여를 막는다 / 이미 결제한 주문의 배송·취소·반품·조회
 * - `CHARGE_BAN` 충전 제한: 새 충전을 막는다 / 기존 포인트 사용·환불
 */
export type MemberRestrictionType = 'POINT_FREEZE' | 'GROUP_BUY_BAN' | 'CHARGE_BAN';

/** 상태 — 진행 중 · 기간이 끝남 · 운영자가 해제함(서버가 지금 시각으로 판정해 준다) */
export type MemberRestrictionStatus = 'ACTIVE' | 'EXPIRED' | 'LIFTED';

/** 출처 — 자동 규칙(예: 24시간 안 선점 3번 만료) · 운영자 */
export type MemberRestrictionSource = 'AUTO' | 'OPERATOR';

/** 자동 규칙 코드(서버 `RestrictionAutoRule`, 문자열) — 화면 이름은 `restriction.autoRule.<코드>`(모르는 코드는 코드 그대로). 지금은 공구 선점 반복 만료 하나(commerce.md 10-5) */
export type MemberRestrictionAutoRule = 'GROUP_BUY_HOLD_EXPIRED' | (string & {});

/** 부여·해제한 운영자(서버 `RestrictionActorView`) — 운영자가 지워졌으면 이름이 null */
export interface MemberRestrictionActor {
    id: string;
    name: string | null;
}

/** 제한 한 건 — `GET /api/admin/members/{memberId}/restrictions` 의 한 줄. 진행 중이 먼저, 그다음 최근 것부터 */
export interface MemberRestriction {
    id: string;
    type: MemberRestrictionType;
    status: MemberRestrictionStatus;
    source: MemberRestrictionSource;
    /** 자동 부여의 규칙 코드 — `AUTO` 일 때만 */
    autoRule: MemberRestrictionAutoRule | null;
    /** 회원에게 공개하는 사유(알림·막힌 기능 안내에 보인다). 자동 부여는 서버가 규칙 문구(요청 언어)로 채워 준다 */
    reason: string;
    /** 운영 메모 — 운영자만 본다(회원에게 보내지 않는다). 없으면 null */
    adminMemo: string | null;
    startsAt: string;
    /** 끝나는 순간. 무기한이면 null */
    until: string | null;
    /** 부여한 운영자 — 자동이면 null */
    createdBy: MemberRestrictionActor | null;
    createdAt: string;
    liftedBy: MemberRestrictionActor | null;
    liftedAt: string | null;
    liftReason: string | null;
}

/** 제한 걸기 — `POST …/restrictions`. 시작은 서버의 지금이다 */
export interface MemberRestrictionCreateRequest {
    type: MemberRestrictionType;
    /** 끝나는 순간. 무기한이면 null */
    until: string | null;
    reason: string;
    adminMemo: string | null;
}

/** 제한 해제 — `POST …/restrictions/{id}/lift` */
export interface MemberRestrictionLiftRequest {
    reason: string;
}

/** 공개 사유·해제 사유 길이(서버 `MemberRestrictionService` 와 같은 값 — 화면 검사는 편의) */
export const RESTRICTION_REASON_MIN = 5;
export const RESTRICTION_REASON_MAX = 200;
/** 운영 메모 최대 길이 */
export const RESTRICTION_MEMO_MAX = 1000;
