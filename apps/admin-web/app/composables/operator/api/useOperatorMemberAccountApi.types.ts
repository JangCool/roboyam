import type { MemberGrade } from '~/composables/member/api/useMemberApi.types';

/**
 * 운영자 회원 계정 API 가 주고받는 모양 — **서버 연결됨(2026-11-05)** — 서버 `OperatorMemberAccountView`·`OperatorMemberAccountRef`. 근거: `docs/features/operator-member-account-contract.md`.
 * 운영자(관리자 앱 계정) 하나에 사이트 회원 계정(`users`) 하나를 1:1 로 잇는다. 이어진 회원 계정은 ① 로보티콘 모든 팩 ② 닉네임 옆 「운영자」 표시 ③ 모든 게시판 관리(이 운영자의 관리자 권한 거울)를 갖는다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다. ⚠ ID 는 문자열이다(Snowflake). 회원 ID(`userId`)는 회원 조회 화면의 회원번호(`memberNo`)와 같은 값이다.
 */

/**
 * 후보 회원을 지금 이을 수 없는 까닭— 여럿일 수 있다.
 *  `EMAIL_NOT_VERIFIED` 이메일 인증 안 됨 · `MEMBER_RESTRICTED` 정지·탈퇴 진행 중 · `BUSINESS_ACCOUNT` 사업자 계정 · `LINKED_TO_OTHER` 이미 다른 운영자에 이어짐(`linkedOperatorName`)
 */
export type OperatorMemberIneligibleReason = 'EMAIL_NOT_VERIFIED' | 'MEMBER_RESTRICTED' | 'BUSINESS_ACCOUNT' | 'LINKED_TO_OTHER';

/**
 * 이어져 있지만 힘이 멈춘 까닭(요청마다 판정 — 계약 정책 6).
 *  `OPERATOR_UNAVAILABLE` 운영자 계정을 쓸 수 없음(승인 안 됨·잠김·만료·사용 중지·기간 밖) · `MEMBER_SUSPENDED` 회원 계정 정지 중
 */
export type OperatorMemberPauseReason = 'OPERATOR_UNAVAILABLE' | 'MEMBER_SUSPENDED';

/**
 * 이어진 회원 계정의 힘 하나. 게시판 관리 힘은 관리자 권한의 거울이다(계약 정책 7).
 *  `ROBOTICON_ALL` 모든 로보티콘 팩 · `OPERATOR_BADGE` 닉네임 옆 운영자 표시 · `POST_MODERATE` 글 숨기기·삭제(`post.manage`) · `COMMENT_DELETE` 댓글 삭제(`post.manage`) ·
 *  `AUTHOR_SANCTION` 작성자 제재(`sanction.manage`) · `ACTION_READ` 처리 내역 보기(`keeper-action.read`) · `ACTION_RESTORE` 처리 되돌리기(`keeper-action.restore`)
 */
export type OperatorMemberPowerKey = 'ROBOTICON_ALL' | 'OPERATOR_BADGE' | 'POST_MODERATE' | 'COMMENT_DELETE' | 'AUTHOR_SANCTION' | 'ACTION_READ' | 'ACTION_RESTORE';

/** 연결한(또는 해제한) 운영자 */
export interface OperatorMemberActor {
    operatorId: string;
    operatorName: string;
}

/** 이 운영자 이메일과 같은 이메일로 가입한 회원 — 연결 안 됨일 때만 온다 */
export interface OperatorMemberCandidate {
    userId: string;
    nickname: string;
    grade: MemberGrade;
    joinedAt: string;
    /** 매니저로 맡은 게시판 수 — 이으면 모두 끝난다(계약 정책 4) */
    boardManagerCount: number;
    /** 스텝으로 지정된 게시판 수 — 이으면 모두 끝난다(계약 정책 4) */
    boardStaffCount: number;
    /** 지금 이을 수 있나 — `ineligibleReasons` 가 비었을 때만 true */
    eligible: boolean;
    ineligibleReasons: OperatorMemberIneligibleReason[];
    /** `LINKED_TO_OTHER` 일 때 그 운영자 이름 — 아니면 null */
    linkedOperatorName: string | null;
}

/** 이어진 회원 계정 */
export interface OperatorMemberLinked {
    userId: string;
    nickname: string;
    grade: MemberGrade;
    /** 이은 시각 — UTC ISO */
    linkedAt: string;
    /** 이은 운영자 — 기록이 없으면 null */
    linkedBy: OperatorMemberActor | null;
    /** 지금 힘이 살아 있나(사이트에서 쓰는 중) — `pausedReasons` 가 비었을 때만 true */
    active: boolean;
    pausedReasons: OperatorMemberPauseReason[];
}

/** 힘 한 줄 */
export interface OperatorMemberPower {
    key: OperatorMemberPowerKey;
    /** 이 힘에 필요한 관리자 권한 코드(`util-permission-catalog.ts`) — 권한과 상관없는 힘(로보티콘·운영자 표시)은 null */
    permission: string | null;
    /** 이 운영자가 그 권한을 가졌나(최고 관리자는 전부 true) — 연결 여부·멈춤과 상관없는 값이다 */
    granted: boolean;
}

/** 힘 목록 */
export interface OperatorMemberPowers {
    items: OperatorMemberPower[];
    /** 게시판 처리의 하루 한도 — 운영자 회원 계정은 한도가 없어 늘 null(계약 정책 7) */
    dailyLimit: number | null;
}

/**
 * `GET|PUT|DELETE /api/admin/operators/{operatorId}/member-account` 의 응답.
 * `linked` 와 `candidate` 는 함께 오지 않는다 — 이어져 있으면 `linked`, 아니면 `candidate`(같은 이메일로 가입한 회원이 없으면 null).
 */
export interface OperatorMemberAccountView {
    linked: OperatorMemberLinked | null;
    candidate: OperatorMemberCandidate | null;
    powers: OperatorMemberPowers;
}

/** `PUT` 본문 — 이을 회원(서버가 후보 조건을 다시 판정한다) */
export interface OperatorMemberLinkRequest {
    userId: string;
}

/** 운영자 목록 한 줄의 이름 옆 딱지(`OperatorView.memberAccount`) */
export interface OperatorMemberAccountRef {
    userId: string;
    nickname: string;
    /** 힘이 살아 있나 — false 면 「멈춤」 */
    active: boolean;
}

// 회원 상세 쪽 표시(`MemberDetail.operatorAccount`)의 모양은 회원 업무가 주인이다 — `useMemberApi.types.ts` 의 `MemberOperatorAccountRef`(업무끼리 순환 import 를 피한다)
