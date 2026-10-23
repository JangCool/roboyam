import type { MeSocialProvider } from '~/composables/auth/api/useAuthApi.types';

/**
 * 내 계정 API 의 주고받는 모양 — community `docs/api-account.md` 와 같다(`docs/features/member-account-contract.md`).
 */

/**
 * 내 계정(`GET /api/me/account`).
 *  - `email`: 소셜 가입자가 이메일을 안 줬으면 null · `emailVerified`: 소셜 가입자는 이메일이 있으면 늘 true
 *  - `hasPassword`: 비밀번호가 있는 계정(이메일 가입자)인가 — false 면 비밀번호·이메일 변경을 쓸 수 없다
 *  - `pendingEmail`: 이메일 변경을 요청했고 새 주소의 링크를 아직 누르지 않았으면 그 새 주소(앞 한 글자만 남기고 가림, `m***@example.com`), 아니면 null
 *  - `providers`: 소셜 공급자 3종 — `GET /api/me/social` 의 줄과 같은 모양
 */
export interface AccountView {
    email: string | null;
    emailVerified: boolean;
    hasPassword: boolean;
    pendingEmail: string | null;
    providers: MeSocialProvider[];
}

/** 비밀번호 변경(`PUT /api/me/password`) */
export interface PasswordChangeRequest {
    currentPassword: string;
    newPassword: string;
}

/** 이메일 변경 인증 메일 보내기(`POST /api/me/email-change`) — 본인 확인으로 현재 비밀번호를 함께 보낸다 */
export interface EmailChangeRequest {
    newEmail: string;
    password: string;
}
