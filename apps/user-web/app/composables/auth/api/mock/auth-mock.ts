// MOCK: 가입·인증·비밀번호 재설정 API 중 **서버에 아직 없는 것**의 메모리 목업 — `useAuthApi.ts` 가 이 파일의 함수를 부른다.
// 모양(경로·요청·응답·오류 코드)은 community `docs/api-auth.md` 그대로다. 서버가 붙으면 `auth/api/mock/` 와 함께 지운다.
//   POST /api/auth/signup · GET /api/auth/email-available · GET /api/auth/nickname-available
//   POST /api/auth/email/verify · POST /api/auth/password/forgot · GET /api/auth/password/reset/check · POST /api/auth/password/reset
// (인증 메일 다시 보내기 `POST /api/auth/email/resend` 는 실제 서버를 부른다 — 목업이 아니다)
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   signup-fail        가입 제출 실패(500) → 약관 단계 폼 위 오류
//   email-check-fail   이메일 중복 확인 실패 → "확인하지 못했어요"(그래도 다음으로 갈 수 있다)
//   nickname-check-fail 닉네임 확인 실패 → 같은 안내
//   verify-fail        메일 링크 확인 실패(500) → "인증을 확인하지 못했어요" + 다시 시도
//   forgot-fail        재설정 메일 요청 실패(500)
//   reset-check-fail   재설정 링크 확인 실패(500)
//   reset-fail         새 비밀번호 저장 실패(500)
//   reset-same         새 비밀번호가 지금 비밀번호와 같다(`400 SAME_PASSWORD`)
//
// ── 목업 규칙(가정 — 계약 `docs/features/member-account-contract.md` 「목업 가정」) ─
//   · 이메일에 `taken` 이 들어가면 이미 가입된 이메일(`available:false` · 가입 `409 EMAIL_TAKEN`).
//   · 닉네임 `taken`·`사용중` 은 중복(`DUPLICATE` · 가입 `409 NICKNAME_TAKEN`), `admin`·`운영자`·`관리자`·`roboyam` 이 들어가면 예약어(`RESERVED`).
//   · 메일 링크의 토큰 값 — 인증: `expired` → `410 TOKEN_EXPIRED` · `invalid` → `400 TOKEN_INVALID` · `changed` → 이메일 변경 링크(`changed:true`) ·
//     `taken` → `409 EMAIL_TAKEN`(변경 링크), 그 밖의 값은 인증 성공. 재설정: `expired`·`invalid` 는 같은 오류, 그 밖은 유효.
//   · 가입은 **서버 세션을 만들지 못한다** — 응답(`me` 모양)으로 화면 상태에만 로그인한 것처럼 둔다. 그 상태에서 실제 API(알림 등)를 부르면 401 로 로그인 화면으로 간다.
//   · 비밀번호 찾기는 어떤 주소든 같은 202(계정이 있는지 알려 주지 않는다). 형식이 틀릴 때만 `400 VALIDATION fields.email`.
//   · 비밀번호 값은 어디에도 남기지 않는다(비교·저장 없음).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    EmailAvailability,
    EmailVerifyResult,
    MeResponse,
    NicknameAvailability,
    PasswordResetCheck,
    PasswordResetRequest,
    SignupRequest,
} from '~/composables/auth/api/useAuthApi.types';
import UtilValidation from '~/utils/util-validation';

const RESERVED = ['admin', '운영자', '관리자', 'roboyam'];
const TAKEN_NICKNAMES = ['taken', '사용중'];

function emailTaken(email: string): boolean {
    return email.trim().toLowerCase().includes('taken');
}

function nicknameReason(nickname: string): NicknameAvailability['reason'] {
    const problem = UtilValidation.nickname(nickname);
    if (problem === 'CHARS') return 'CHARS';
    if (problem) return 'LENGTH';
    if (RESERVED.some((word) => nickname.toLowerCase().includes(word))) return 'RESERVED';
    if (TAKEN_NICKNAMES.includes(nickname.toLowerCase())) return 'DUPLICATE';
    return null;
}

/** 요청이 끊겼으면(입력이 바뀌어 화면이 앞 확인을 버렸다) 목업도 멈춘다 */
function throwIfAborted(signal?: AbortSignal) {
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
}

export async function mockEmailAvailable(email: string, signal?: AbortSignal): Promise<EmailAvailability> {
    await mockDelay();
    throwIfAborted(signal);
    if (mockFlags().has('email-check-fail')) throw MOCK_ERRORS.internal();
    if (UtilValidation.email(email)) throw MOCK_ERRORS.validation({ email: '이메일 주소 형식을 확인해 주세요.' });
    return { available: !emailTaken(email) };
}

export async function mockNicknameAvailable(nickname: string, signal?: AbortSignal): Promise<NicknameAvailability> {
    await mockDelay();
    throwIfAborted(signal);
    if (mockFlags().has('nickname-check-fail')) throw MOCK_ERRORS.internal();
    const reason = nicknameReason(nickname);
    return { available: reason === null, reason };
}

/** 가입 — 위반을 한 번에 모아 `fields` 로(빈 값도 같은 방식), 그다음 중복 */
export async function mockSignup(body: SignupRequest): Promise<MeResponse> {
    await mockDelay();
    if (mockFlags().has('signup-fail')) throw MOCK_ERRORS.internal();

    const fields: Record<string, string> = {};
    const emailProblem = UtilValidation.email(body.email);
    if (emailProblem === 'REQUIRED') fields.email = '이메일을 입력해 주세요.';
    else if (emailProblem) fields.email = '이메일 주소 형식을 확인해 주세요.';
    if (!UtilValidation.password(body.password).valid) fields.password = '비밀번호는 8~64자이고 영문과 숫자를 함께 써야 해요.';
    const reason = nicknameReason(body.nickname);
    if (reason === 'LENGTH' || reason === 'CHARS') fields.nickname = '닉네임은 2~12자, 한글·영문·숫자·밑줄과 글자 사이의 공백만 쓸 수 있어요.';
    else if (reason === 'RESERVED') fields.nickname = '쓸 수 없는 닉네임이에요.';
    if (!body.agreeTerms) fields.agreeTerms = '이용약관에 동의해 주세요.';
    if (!body.agreePrivacy) fields.agreePrivacy = '개인정보처리방침에 동의해 주세요.';
    if (!body.ageConfirmed) fields.ageConfirmed = '만 14세 이상만 가입할 수 있어요.';
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);

    if (emailTaken(body.email)) throw new ApiError({ status: 409, code: 'EMAIL_TAKEN', message: '이미 가입된 이메일이에요.' });
    if (reason === 'DUPLICATE') throw new ApiError({ status: 409, code: 'NICKNAME_TAKEN', message: '이미 쓰고 있는 닉네임이에요. 다른 닉네임을 입력해 주세요.' });

    return {
        authenticated: true,
        user: {
            id: 'mock-signup',
            nickname: body.nickname,
            grade: 'SEEDLING',
            gradeLabel: '새싹',
            role: 'USER',
            email: body.email.trim().toLowerCase(),
            emailVerified: false,
            authMethods: ['EMAIL'],
            businessStatus: null,
        },
        restriction: null,
        adminUrl: null,
    };
}

export async function mockVerifyEmail(token: string): Promise<EmailVerifyResult> {
    await mockDelay();
    if (mockFlags().has('verify-fail')) throw MOCK_ERRORS.internal();
    if (token === 'expired') throw new ApiError({ status: 410, code: 'TOKEN_EXPIRED', message: '링크가 만료됐어요.' });
    if (token === 'invalid' || !token) throw new ApiError({ status: 400, code: 'TOKEN_INVALID', message: '링크가 올바르지 않거나 이미 사용됐어요.' });
    if (token === 'taken') throw new ApiError({ status: 409, code: 'EMAIL_TAKEN', message: '그 사이 다른 회원이 이 이메일을 사용하고 있어요.' });
    return { verified: true, changed: token === 'changed' };
}

export async function mockForgotPassword(email: string): Promise<void> {
    await mockDelay();
    if (mockFlags().has('forgot-fail')) throw MOCK_ERRORS.internal();
    if (UtilValidation.email(email)) throw MOCK_ERRORS.validation({ email: '이메일 주소 형식을 확인해 주세요.' });
    // 가입 여부와 관계없이 202 — 계정이 있는지 알려 주지 않는다
}

function resetTokenProblem(token: string): ApiError | null {
    if (token === 'expired') return new ApiError({ status: 410, code: 'TOKEN_EXPIRED', message: '링크가 만료됐어요.' });
    if (token === 'invalid' || !token) return new ApiError({ status: 400, code: 'TOKEN_INVALID', message: '링크가 올바르지 않거나 이미 사용됐어요.' });
    return null;
}

export async function mockCheckResetToken(token: string): Promise<PasswordResetCheck> {
    await mockDelay();
    if (mockFlags().has('reset-check-fail')) throw MOCK_ERRORS.internal();
    const problem = resetTokenProblem(token);
    if (problem) throw problem;
    return { valid: true };
}

export async function mockResetPassword(body: PasswordResetRequest): Promise<void> {
    await mockDelay();
    if (mockFlags().has('reset-fail')) throw MOCK_ERRORS.internal();
    const problem = resetTokenProblem(body.token);
    if (problem) throw problem;
    if (!UtilValidation.password(body.newPassword).valid) throw MOCK_ERRORS.validation({ newPassword: '비밀번호는 8~64자이고 영문과 숫자를 함께 써야 해요.' });
    if (mockFlags().has('reset-same')) throw new ApiError({ status: 400, code: 'SAME_PASSWORD', message: '지금 쓰는 비밀번호와 같아요. 다른 비밀번호를 입력해 주세요.' });
}
