// MOCK: 내 계정 API(`GET /api/me/account` · `PUT /api/me/password` · `POST /api/me/email-change`)의 메모리 목업 — `useAccountApi.ts` 가 이 파일의 함수를 부른다.
// 모양(경로·요청·응답·오류 코드)은 community `docs/api-account.md` 그대로다. 서버가 붙으면 `account/api/mock/` 와 함께 지운다.
//
// `GET /api/me/account` 목업은 **실제 값으로 만든다** — 비밀번호 유무·공급자는 실제 `GET /api/me/social`, 이메일·인증 여부는 로그인 정보(`/api/auth/me`)에서 오고,
// 서버에 없는 「변경 대기 이메일(`pendingEmail`)」만 이 파일이 메모리로 든다(새로고침하면 사라진다).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   account-fail          내 계정 읽기 실패(500) → 이메일·비밀번호 변경 화면의 오류 상태
//   account-social        비밀번호 없는(소셜 가입) 계정처럼 → 변경 화면 대신 안내
//   account-pending       이미 보낸 이메일 변경 요청이 있다 → 입력 화면 위 안내
//   password-wrong        현재 비밀번호가 틀렸다(`401 CURRENT_PASSWORD_WRONG`) — 비밀번호 변경·이메일 변경 둘 다
//   password-locked       현재 비밀번호를 여러 번 틀려 잠겼다(`429 LOGIN_LOCKED`, 10분) — 둘 다
//   password-change-fail  비밀번호 변경 실패(500) → 폼 위 오류
//   email-send-fail       이메일 변경 메일 보내기 실패(500) → "인증 메일을 보내지 못했어요"
//   email-cooldown        재발송 제한(`429 RESEND_COOLDOWN`, 45초)
//   email-not-allowed     소셜 가입 계정이라 바꿀 수 없다(`409 EMAIL_CHANGE_NOT_ALLOWED`) — 제출 때
//   password-not-set      소셜 가입 계정이라 비밀번호가 없다(`409 PASSWORD_NOT_SET`) — 제출 때
//
// ── 목업 규칙(가정 — 계약 `docs/features/member-account-contract.md` 「목업 가정」) ─
//   · 새 이메일에 `taken` 이 들어가거나 지금 이메일과 같으면 `409 EMAIL_TAKEN`. 형식이 틀리면 `400 VALIDATION fields.newEmail`.
//   · 같은 사람이 60초 안에 다시 보내면 `429 RESEND_COOLDOWN`(남은 초).
//   · 새 비밀번호가 규칙(8~64자·영문+숫자·72바이트)을 어기면 `400 VALIDATION fields.newPassword`, 현재와 같으면 `400 SAME_PASSWORD`.
//   · 목업은 비밀번호를 실제로 바꾸지도, 비교해 보지도 않는다(맞고 틀림은 위 스위치로만). 값은 어디에도 남기지 않는다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AccountView, EmailChangeRequest, PasswordChangeRequest } from '~/composables/account/api/useAccountApi.types';
import type { AuthUser, MeSocialResponse } from '~/composables/auth/api/useAuthApi.types';
import UtilValidation from '~/utils/util-validation';

const RESEND_SECONDS = 60;
const LOCK_SECONDS = 600;

let pendingEmail: string | null = null;
let lastSentAt = 0;

/** `member@example.com` → `m***@example.com` (앞 한 글자만 남긴다) */
function mask(email: string): string {
    const [local = '', domain = ''] = email.split('@');
    return `${local.slice(0, 1)}***@${domain}`;
}

function wrongPassword(): ApiError {
    return new ApiError({ status: 401, code: 'CURRENT_PASSWORD_WRONG', message: '현재 비밀번호가 맞지 않아요.' });
}

function locked(): ApiError {
    return new ApiError({ status: 429, code: 'LOGIN_LOCKED', message: '비밀번호를 여러 번 틀려 잠시 막혔어요. 잠시 후 다시 시도해 주세요.', retryAfterSeconds: LOCK_SECONDS });
}

/** 실제 연결 상태(`/api/me/social`)와 로그인 정보로 내 계정 모양을 만든다 */
export async function mockAccount(loadSocial: () => Promise<MeSocialResponse>, user: AuthUser | null): Promise<AccountView> {
    const flags = mockFlags();
    if (flags.has('account-fail')) {
        await mockDelay();
        throw MOCK_ERRORS.internal();
    }
    const social = await loadSocial();
    if (flags.has('account-pending') && !pendingEmail) pendingEmail = 'n***@example.com';
    const hasPassword = social.hasPassword && !flags.has('account-social');
    return {
        email: user?.email ?? null,
        emailVerified: user?.emailVerified ?? false,
        hasPassword,
        pendingEmail: hasPassword ? pendingEmail : null,
        providers: social.providers,
    };
}

export async function mockChangePassword(body: PasswordChangeRequest): Promise<void> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('password-change-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('password-not-set')) throw new ApiError({ status: 409, code: 'PASSWORD_NOT_SET', message: '소셜 로그인으로 가입한 계정은 비밀번호가 없어요.' });
    if (flags.has('password-locked')) throw locked();

    const fields: Record<string, string> = {};
    if (!body.currentPassword) fields.currentPassword = '현재 비밀번호를 입력해 주세요.';
    if (!UtilValidation.password(body.newPassword).valid) fields.newPassword = '비밀번호는 8~64자이고 영문과 숫자를 함께 써야 해요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    if (flags.has('password-wrong')) throw wrongPassword();
    if (body.currentPassword === body.newPassword) throw new ApiError({ status: 400, code: 'SAME_PASSWORD', message: '지금 쓰는 비밀번호와 같아요. 다른 비밀번호를 입력해 주세요.' });
}

export async function mockRequestEmailChange(body: EmailChangeRequest, currentEmail: string | null): Promise<void> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('email-send-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('email-not-allowed')) throw new ApiError({ status: 409, code: 'EMAIL_CHANGE_NOT_ALLOWED', message: '소셜 로그인으로 가입한 계정은 이메일을 바꿀 수 없어요.' });
    if (flags.has('password-locked')) throw locked();

    const fields: Record<string, string> = {};
    if (UtilValidation.email(body.newEmail)) fields.newEmail = '이메일 주소 형식을 확인해 주세요.';
    if (!body.password) fields.password = '현재 비밀번호를 입력해 주세요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    if (flags.has('password-wrong')) throw wrongPassword();
    const target = body.newEmail.trim().toLowerCase();
    if (target.includes('taken') || target === currentEmail?.toLowerCase()) throw new ApiError({ status: 409, code: 'EMAIL_TAKEN', message: '이미 사용 중인 이메일이에요.' });

    const waited = Math.floor((Date.now() - lastSentAt) / 1000);
    if (flags.has('email-cooldown')) throw new ApiError({ status: 429, code: 'RESEND_COOLDOWN', message: '잠시 후 다시 시도해 주세요.', retryAfterSeconds: 45 });
    if (waited < RESEND_SECONDS) throw new ApiError({ status: 429, code: 'RESEND_COOLDOWN', message: '잠시 후 다시 시도해 주세요.', retryAfterSeconds: RESEND_SECONDS - waited });

    lastSentAt = Date.now();
    pendingEmail = mask(target);
}
