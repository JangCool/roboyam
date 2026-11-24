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

/**
 * 이을 수 있는(또는 지금은 이을 수 없는) 회원 한 명. `GET …/member-account` 의 `candidate`(운영자와 같은 이메일로 가입한 회원 — 화면의 「추천」)와
 * 회원 검색 결과(`OperatorMemberSearchCandidate`)가 같은 모양을 쓴다. 이메일이 달라도 이을 수 있다(2026-11-24).
 */
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
 * `linked` 와 `candidate` 는 함께 오지 않는다 — 이어져 있으면 `linked`, 아니면 `candidate`(운영자와 같은 이메일로 가입한 회원 — 화면의 「추천」. 없으면 null).
 * 같은 이메일이 아닌 회원은 회원 검색(`candidates`)으로 찾아 잇는다.
 */
export interface OperatorMemberAccountView {
    linked: OperatorMemberLinked | null;
    candidate: OperatorMemberCandidate | null;
    powers: OperatorMemberPowers;
}

/**
 * 회원 검색 결과 한 줄(`GET …/member-account/candidates?q=`) — 후보 모양 + 가린 이메일·회원번호.
 * 검색어 `q` 규칙(서버가 판정, 2~100자 — 어기면 400 `fields.q`): 숫자만 = 회원번호 일치 + 숫자 닉네임 부분 일치, `@` 포함 = **이메일 전체 주소 일치만**, 그 밖 = 닉네임 부분 일치.
 * 이 운영자에 이미 이어진 회원과 탈퇴를 마친 회원은 오지 않는다. 정확히 맞는 것 먼저, 그다음 가입 최신순.
 */
export interface OperatorMemberSearchCandidate extends OperatorMemberCandidate {
    /** 회원 관리의 회원번호 — 늘 온다(`userId` 와 같은 값) */
    memberNo: string;
    /** 가린 이메일(예: `ab***@gmail.com` — 관리자 회원 목록과 같은 가림). 이메일이 없는 회원(소셜 가입)은 null */
    maskedEmail: string | null;
}

/** `GET …/member-account/candidates?q=` 의 응답 — 최대 20명 */
export interface OperatorMemberCandidatesResponse {
    items: OperatorMemberSearchCandidate[];
}

/** `PUT` 본문 — 이을 회원(서버가 이을 수 있는지 다시 판정한다 — 이메일은 달라도 된다) */
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

/* ── 새 계정 만들기(운영자 회원 계정을 관리자가 바로 만들고 그 운영자에 잇는다) — 서버 `OperatorMemberCreateRequest`·`OperatorMemberCreateResult`(2026-11-24) ── */

/**
 * 새 계정의 비밀번호를 정하는 방법.
 *  `DIRECT` 만들 때 관리자가 직접 입력(공개 가입과 같은 규칙 — 8~64자·UTF-8 72바이트 이하·영문과 숫자) ·
 *  `MAIL` 그 이메일로 「비밀번호 정하기」 링크를 보낸다(비밀번호 칸 없음 — 링크에서 정할 때까지 로그인 불가)
 */
export type OperatorMemberPasswordMode = 'DIRECT' | 'MAIL';

/** 만들면서 보낸 메일 — `EMAIL_VERIFICATION` 이메일 인증 · `PASSWORD_SETUP` 비밀번호 정하기 링크 */
export type OperatorMemberCreateMail = 'EMAIL_VERIFICATION' | 'PASSWORD_SETUP';

/**
 * `POST /api/admin/operators/{operatorId}/member-account` 본문 — 새 회원 계정을 만들고 이 운영자에 바로 잇는다.
 * 닉네임은 형식(2~12자·허용 글자)과 중복만 보고 **예약어(`UserPolicy.RESERVED_NICKNAME_WORDS`)는 보지 않는다** — 운영자 계정이라 「아이로빗」·「운영자」 같은 이름을 쓸 수 있어야 한다.
 * 이메일은 운영자 이메일과 달라도 된다(이 길은 같은 이메일 규칙(계약 정책 2)의 예외 — 관리자가 직접 만드는 계정이라 본인 확인은 그 주소로 가는 메일(인증 또는 비밀번호 정하기)이 대신한다). 이메일 인증 전 계정을 잇는다(정책 3 의 인증 조건 없음).
 */
export interface OperatorMemberCreateRequest {
    email: string;
    nickname: string;
    passwordMode: OperatorMemberPasswordMode;
    /** `DIRECT` 일 때만 값 — `MAIL` 이면 null. 화면 메모리에만 두고 보낸 뒤 비운다 */
    password: string | null;
}

/** 새 계정 만들기 응답(201) — 만든 회원과 이은 뒤의 사이트 계정 상태 */
export interface OperatorMemberCreateResult {
    /** 만든 회원 ID(= 회원 관리의 회원번호 `memberNo`) */
    userId: string;
    /** 정규화한(소문자) 이메일 — 이 주소로 메일을 보낸다 */
    email: string;
    nickname: string;
    passwordMode: OperatorMemberPasswordMode;
    /** 대기열에 넣은 메일(DIRECT → `EMAIL_VERIFICATION`, MAIL → `PASSWORD_SETUP`) — 커밋 뒤 대기열 워커가 이 주소로 보낸다(잠시 뒤 나간다) */
    mailsSent: OperatorMemberCreateMail[];
    /** 이은 뒤 이 운영자의 사이트 계정 상태(`GET …/member-account` 와 같은 모양 — `linked` 가 채워져 온다) */
    account: OperatorMemberAccountView;
}
