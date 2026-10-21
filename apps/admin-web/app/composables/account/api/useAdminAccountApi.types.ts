/**
 * 운영자 계정의 비밀번호 흐름(초대 수락 · 비밀번호 재설정 · 비밀번호 변경) API 가 주고받는 모양 — 서버 DTO(`InvitationCheckResponse` · `PasswordResetCodeResponse` …)와 같다. 경로·오류 코드는 `useAdminAccountApi.ts` 머리 주석.
 * 비밀번호 재설정 성공 응답은 로그인 응답과 같은 `AdminMeResponse`(`auth/api/useAdminAuthApi.types`)다.
 * ⚠ ID 는 문자열이다(Snowflake 64비트). 시각은 UTC ISO 다. 비밀번호·토큰·승인번호는 화면 메모리에만 두고 URL·저장소·로그·토스트에 남기지 않는다(`forms.md` 2-1).
 *
 * 비밀번호 재설정은 hamonica3 의 비밀번호 찾기와 같은 두 걸음이다 — ① 이메일 → 승인번호를 메일로 ② 승인번호 + 새 비밀번호를 **한 번에** 보낸다.
 * 값(서버 설정값 — hamonica3 `CertService`·`CertEntity` 와 같다): 숫자 6자리 · 유효 10분 · 5번 틀리면 소진(다시 받아야 함) · 다시 받으면 이전 번호는 못 쓴다 · 다시 받기 간격 60초(요청 응답의 `cooldownSeconds`, 너무 잦으면 429 의 `retryAfterSeconds`).
 */

/** 초대로 받을 역할 하나 — 이름은 운영자가 정한 값 그대로 보인다 */
export interface InvitationRoleRef {
    roleId: string;
    roleName: string;
}

/** 초대 확인(`POST /api/admin/auth/invitation/check`) 응답 — 유효한 초대일 때만 온다 */
export interface InvitationCheckResponse {
    /** 초대받은 이메일 — 계정의 로그인 아이디가 된다. 화면에서 읽기 전용 */
    email: string;
    /** 수락하면 받을 역할(비었을 수 있다) */
    roles: InvitationRoleRef[];
    /** 링크가 유효한 마지막 시각 — UTC ISO */
    expiresAt: string;
}

export interface InvitationAcceptRequest {
    token: string;
    /** 운영자 이름(닉네임) — 2~12자. 내 정보 닉네임 규칙과 같다 */
    nickname: string;
    password: string;
}

export interface PasswordResetCodeRequest {
    email: string;
}

/** 승인번호 요청 응답 — **가입 여부와 무관하게 늘 같은 모양**(계정 존재를 알리지 않는다) */
export interface PasswordResetCodeResponse {
    /** 승인번호 자릿수(숫자) */
    codeLength: number;
    /** 승인번호가 유효한 분 */
    expiresInMinutes: number;
    /** 다시 받을 수 있을 때까지의 초 — 화면이 [승인번호 다시 받기] 카운트다운에 쓴다 */
    cooldownSeconds: number;
}

/** 승인번호 + 새 비밀번호를 함께 보낸다 */
export interface PasswordResetRequest {
    email: string;
    certCode: string;
    newPassword: string;
}

export interface PasswordChangeRequest {
    currentPassword: string;
    newPassword: string;
}

/**
 * 초대 링크가 쓸 수 없는 이유 — 서버 오류 `code` 에서 나온다(화면이 `code` 로 가르고 문구는 화면이 고른다).
 * `invalid` 는 모르는·빈·취소된 토큰이다(서버는 취소한 초대를 지우므로 `INVITATION_NOT_FOUND` 로 보인다).
 */
export type TokenProblem = 'invalid' | 'expired' | 'used';
