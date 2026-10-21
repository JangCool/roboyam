// MOCK: 운영자 계정 비밀번호 흐름 API(초대 확인·수락, 재설정 승인번호 요청·재설정, 비밀번호 변경)의 메모리 목업 — `useAdminAccountApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `account/api/mock/` 와 함께 지운다. 요청마다 300~800ms 기다리고, 저장하는 것은 없다(새로고침해도 같은 처음 상태).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개, 예: `/invitation?token=x&mock=invitation-expired`) ─
//   (모든 계정 화면 공통)
//   account-error           모든 요청이 `500 INTERNAL` 로 실패
//   account-network         모든 요청이 `NETWORK`(서버에 닿지 못함) 로 실패
//   (초대 수락 `/invitation`)
//   invitation-expired      확인이 `410 INVITATION_EXPIRED`
//   invitation-used         확인이 `409 INVITATION_USED`
//   invitation-canceled     확인이 `410 INVITATION_CANCELED`
//   invitation-invalid      확인이 `404 INVITATION_NOT_FOUND`(없는 토큰)
//   invitation-no-roles     받을 역할이 하나도 없는 초대
//   accept-expired          확인은 통과하고 [계정 만들기] 때 `410 INVITATION_EXPIRED`(그 사이 만료)
//   accept-nickname-taken   [계정 만들기]가 `409 NICKNAME_TAKEN`
//   accept-invalid          [계정 만들기]가 `400 VALIDATION`(`fields.nickname`·`fields.password` 서버 문구)
//   accept-error            [계정 만들기]가 `500 INTERNAL`(칸에 붙지 않는 오류)
//   (비밀번호 재설정 `/forgot-password` — 승인번호는 6자리 숫자, 기본은 **어떤 6자리든 맞는 것으로 친다**)
//   forgot-limited          승인번호 요청이 `429 TOO_MANY_REQUESTS`(+`retryAfterSeconds` 90)
//   forgot-error            승인번호 요청이 `500 INTERNAL`
//   reset-code-wrong        승인번호를 늘 틀린 것으로 본다 — `CERT_CODE_INVALID`("N번 더 시도할 수 있어요")를 5번 겪으면 `CERT_CODE_EXHAUSTED`(다시 받으면 처음부터)
//   reset-code-expired      승인번호가 `CERT_CODE_EXPIRED`
//   reset-code-used         승인번호가 `CERT_CODE_USED`
//   reset-invalid           [비밀번호 바꾸기]가 `400 VALIDATION`(`fields.newPassword` 서버 문구)
//   reset-error             [비밀번호 바꾸기]가 `500 INTERNAL`
//   (내 정보 > 비밀번호 `/me?tab=password`)
//   password-wrong          [비밀번호 바꾸기]가 `400 INVALID_CURRENT_PASSWORD`(현재 비밀번호가 틀림)
//   password-invalid        [비밀번호 바꾸기]가 `400 VALIDATION`(`fields.newPassword` 서버 문구)
//   password-error          [비밀번호 바꾸기]가 `500 INTERNAL`
//
// 서버 응답을 흉내 내는 목업이라 오류 문구는 서버 몫이다 — 번역 키로 옮기지 않았다(`.claude/guides/frontend/i18n.md` 5장).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    InvitationAcceptRequest,
    InvitationCheckResponse,
    PasswordChangeRequest,
    PasswordResetCodeRequest,
    PasswordResetCodeResponse,
    PasswordResetRequest,
} from '~/composables/account/api/useAdminAccountApi.types';
import UtilValidation from '~/utils/util-validation';

const HOUR = 3_600_000;

/** 승인번호 규칙(후보 — hamonica3 `CertService`): 6자리 · 10분 · 5번 틀리면 소진. 다시 받기 간격 60초는 공개 사이트 값 */
const CODE_LENGTH = 6;
const CODE_EXPIRES_MINUTES = 10;
const CODE_MAX_FAILS = 5;
const CODE_COOLDOWN_SECONDS = 60;

/** 이번 승인번호로 틀린 횟수 — 새로 받으면 0 으로 돌아간다 */
let codeFails = 0;

/** 어느 요청이든 공통 스위치로 먼저 실패시킨다 */
function failCommon(flags: Set<string>): void {
    if (flags.has('account-network')) throw MOCK_ERRORS.network();
    if (flags.has('account-error')) throw MOCK_ERRORS.internal();
}

const tokenError = {
    invitationExpired: () => new ApiError({ status: 410, code: 'INVITATION_EXPIRED', message: '초대가 만료됐어요.' }),
    invitationUsed: () => new ApiError({ status: 409, code: 'INVITATION_USED', message: '이미 사용한 초대예요.' }),
    invitationCanceled: () => new ApiError({ status: 410, code: 'INVITATION_CANCELED', message: '취소된 초대예요.' }),
    invitationNotFound: () => new ApiError({ status: 404, code: 'INVITATION_NOT_FOUND', message: '찾을 수 없는 초대예요.' }),
};

/** 서버가 비밀번호 규칙을 어겼을 때 칸에 붙여 주는 문구(규칙은 공개 사이트 가입 규칙과 같다) */
function passwordRuleMessage(value: string): string | null {
    const check = UtilValidation.password(value);
    if (!value) return '비밀번호를 입력해 주세요.';
    if (!check.length) return `비밀번호는 ${UtilValidation.PASSWORD_MIN}~${UtilValidation.PASSWORD_MAX}자로 입력해 주세요.`;
    if (!check.bytes) return `비밀번호는 UTF-8 ${UtilValidation.PASSWORD_MAX_BYTES}바이트 이하로 입력해 주세요.`;
    if (!check.letterAndDigit) return '비밀번호에는 영문과 숫자를 함께 써 주세요.';
    return null;
}

function validation(fields: Record<string, string>): ApiError {
    return new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 다시 확인해 주세요.', fields });
}

function certError(code: string, message: string): ApiError {
    return new ApiError({ status: 400, code, message });
}

// ── 초대 수락 ─

export async function mockInvitationCheck(token: string): Promise<InvitationCheckResponse> {
    await mockDelay();
    const flags = mockFlags();
    failCommon(flags);
    void token;
    if (flags.has('invitation-expired')) throw tokenError.invitationExpired();
    if (flags.has('invitation-used')) throw tokenError.invitationUsed();
    if (flags.has('invitation-canceled')) throw tokenError.invitationCanceled();
    if (flags.has('invitation-invalid')) throw tokenError.invitationNotFound();
    return {
        email: 'new.operator@example.com',
        roles: flags.has('invitation-no-roles')
            ? []
            : [
                  { roleId: '7001', roleName: '콘텐츠 관리자' },
                  { roleId: '7002', roleName: '회원 지원' },
              ],
        expiresAt: new Date(Date.now() + 61 * HOUR).toISOString(),
    };
}

export async function mockInvitationAccept(body: InvitationAcceptRequest): Promise<void> {
    await mockDelay();
    const flags = mockFlags();
    failCommon(flags);
    if (flags.has('accept-expired')) throw tokenError.invitationExpired();
    if (flags.has('accept-nickname-taken')) throw new ApiError({ status: 409, code: 'NICKNAME_TAKEN', message: '이미 사용 중인 이름이에요.' });
    if (flags.has('accept-invalid')) throw validation({ nickname: '이름은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.', password: '너무 흔한 비밀번호예요. 다른 비밀번호를 써 주세요.' });
    if (flags.has('accept-error')) throw MOCK_ERRORS.internal();

    const fields: Record<string, string> = {};
    if (UtilValidation.nickname(body.nickname)) fields.nickname = '이름은 2~12자의 한글, 영문, 숫자, 밑줄(_)만 쓸 수 있어요.';
    const rule = passwordRuleMessage(body.password);
    if (rule) fields.password = rule;
    if (Object.keys(fields).length) throw validation(fields);
}

// ── 비밀번호 재설정 ─

export async function mockPasswordResetCode(body: PasswordResetCodeRequest): Promise<PasswordResetCodeResponse> {
    await mockDelay();
    const flags = mockFlags();
    failCommon(flags);
    if (flags.has('forgot-limited')) throw new ApiError({ status: 429, code: 'TOO_MANY_REQUESTS', message: '요청이 너무 많아요. 잠시 후 다시 시도해 주세요.', retryAfterSeconds: 90 });
    if (flags.has('forgot-error')) throw MOCK_ERRORS.internal();
    // 가입된 이메일인지와 무관하게 늘 같은 답 — 어떤 이메일이든 같은 모양으로 성공한다. 새 번호가 가면 틀린 횟수도 처음부터
    void body;
    codeFails = 0;
    return { codeLength: CODE_LENGTH, expiresInMinutes: CODE_EXPIRES_MINUTES, cooldownSeconds: CODE_COOLDOWN_SECONDS };
}

export async function mockPasswordReset(body: PasswordResetRequest): Promise<void> {
    await mockDelay();
    const flags = mockFlags();
    failCommon(flags);

    // 승인번호 먼저 — 순서는 hamonica3 `CertService.verify` 와 같다(이미 씀 → 만료 → 소진 → 틀림)
    if (flags.has('reset-code-used')) throw certError('CERT_CODE_USED', '이미 사용한 승인번호예요. 다시 받아 주세요.');
    if (flags.has('reset-code-expired')) throw certError('CERT_CODE_EXPIRED', '승인번호가 만료됐어요. 다시 받아 주세요.');
    if (codeFails >= CODE_MAX_FAILS) throw certError('CERT_CODE_EXHAUSTED', '승인번호를 너무 많이 틀렸어요. 다시 받아 주세요.');
    if (flags.has('reset-code-wrong') || !new RegExp(`^[0-9]{${CODE_LENGTH}}$`).test(body.certCode)) {
        codeFails++;
        const left = CODE_MAX_FAILS - codeFails;
        throw left > 0 ? certError('CERT_CODE_INVALID', `승인번호가 올바르지 않아요. ${left}번 더 시도할 수 있어요.`) : certError('CERT_CODE_EXHAUSTED', '승인번호를 너무 많이 틀렸어요. 다시 받아 주세요.');
    }

    if (flags.has('reset-invalid')) throw validation({ newPassword: '이전에 쓰던 비밀번호는 다시 쓸 수 없어요.' });
    if (flags.has('reset-error')) throw MOCK_ERRORS.internal();

    const rule = passwordRuleMessage(body.newPassword);
    if (rule) throw validation({ newPassword: rule });
    codeFails = 0;
}

// ── 내 정보 > 비밀번호 변경 ─

export async function mockPasswordChange(body: PasswordChangeRequest): Promise<void> {
    await mockDelay();
    const flags = mockFlags();
    failCommon(flags);
    // 세션이 끊긴 것(401)과 헷갈리지 않게 현재 비밀번호 틀림은 400 으로 준다(공통 클라이언트는 로그인 상태에서의 401 UNAUTHENTICATED 를 세션 종료로 본다)
    if (flags.has('password-wrong')) throw new ApiError({ status: 400, code: 'INVALID_CURRENT_PASSWORD', message: '현재 비밀번호가 맞지 않아요.' });
    if (flags.has('password-invalid')) throw validation({ newPassword: '현재 비밀번호와 다른 비밀번호를 써 주세요.' });
    if (flags.has('password-error')) throw MOCK_ERRORS.internal();

    const rule = passwordRuleMessage(body.newPassword);
    if (rule) throw validation({ newPassword: rule });
}
