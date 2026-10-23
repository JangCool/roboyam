import type {
    LoginSecuritySaveRequest,
    LoginSecurityView,
    MfaEnrollmentConfirmResponse,
    MfaEnrollmentView,
    MfaMeView,
    MfaRecoveryCodesResponse,
    MfaTrustedDeviceView,
    MfaVerifyRequest,
    MfaVerifyResponse,
    OperatorMfaView,
} from '~/composables/mfa/api/useMfaApi.types';

/**
 * 관리자 2차 인증(TOTP) API — 서버 주소 문자열(`/api/admin/auth/mfa/…`·`/api/admin/mfa/…`·`/api/admin/login-security`·`/api/admin/operators/{id}/mfa`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-3」(서버 `AdminMfaAuthController`·`AdminMfaController`·`LoginSecurityController`·`OperatorMfaController`), 정책: `docs/domain/commerce.md` 13-3.
 * 응답은 모두 `Cache-Control: no-store`, 쓰기는 CSRF. 오류는 모두 `ApiError`(useApiClient)로 던져진다 — 오류 본문의 `remainingAttempts` 는 `ApiError.extras.remainingAttempts` 다.
 *
 * 로그인 2단계(「2차 대기」 세션 — 로그인 아님, 5분). 대기가 없거나 끝났거나 단계가 다르면 401 `MFA_PENDING_EXPIRED`(코드가 `UNAUTHENTICATED` 가 아니라 공통 클라이언트가 로그인으로 보내지 않는다)
 *   - 로그인·`me`·비밀번호 재설정 응답의 `mfa: { stage: VERIFY|ENROLL|GRACE, graceEndsAt?, trustDays }`(없으면 null). VERIFY·ENROLL 이면 `authenticated:false`
 *   - `POST /api/admin/auth/mfa/verify`(VERIFY) `{ code? | recoveryCode?, rememberDevice }` → 200 me(+ 복구 코드면 `recoveryCodesLeft`). 신뢰 기기 쿠키는 서버가 HttpOnly 로 심는다
 *       400 `MFA_CODE_INVALID`(+`remainingAttempts`) · 400 `MFA_CODE_REUSED` · 400 `RECOVERY_CODE_INVALID` · 400 `VALIDATION`(`fields.code|recoveryCode`) · 429 `MFA_LOCKED`(+`retryAfterSeconds`)
 *   - `POST /api/admin/auth/mfa/cancel` → 204(대기 세션만 버린다)
 *   - 강제 등록(ENROLL): `POST /api/admin/auth/mfa/enrollment` → `MfaEnrollmentView` · `…/enrollment/confirm` `{ code }` → `{ recoveryCodes[10], me }`(로그인 완료)
 * 내 2차 인증(로그인 세션)
 *   - 자발 등록: `POST /api/admin/mfa/enrollment`(이미 등록 409 `MFA_ALREADY_ENROLLED`) · `…/enrollment/confirm` `{ code }` → `{ recoveryCodes[10] }`(400 `MFA_ENROLLMENT_NOT_STARTED`). 다시 등록 확정도 이 주소다
 *   - `GET /api/admin/mfa/me` → `MfaMeView`
 *   - `POST /api/admin/mfa/recovery-codes` `{ code }`(현재 OTP) → `{ recoveryCodes }`(옛 코드 무효, 미등록 409 `MFA_NOT_ENROLLED`)
 *   - `POST /api/admin/mfa/reenroll` `{ password }` → 등록 시작과 같은 응답 · 400 `INVALID_CURRENT_PASSWORD`. 확정하면 예전 OTP·복구 코드·신뢰 기기 모두 무효
 *   - `GET /api/admin/mfa/trusted-devices` → `MfaTrustedDeviceView[]` · `DELETE …/{id}` → 204(404 `MFA_DEVICE_NOT_FOUND`) · `DELETE /api/admin/mfa/trusted-devices` → 204(모두)
 * 설정(루트만 — 권한 코드로 위임하지 않는다)
 *   - `GET /api/admin/login-security` → `LoginSecurityView` · `PUT` `LoginSecuritySaveRequest` → 같은 모양
 *       403 `PERMISSION_DENIED`(루트 아님) · 409 `MFA_ROOT_NOT_ENROLLED` · 400 `VALIDATION`(`fields.graceDays|trustMonths|maxFailures`)
 * 운영자 상세(읽기는 `operator.read`, 초기화는 루트)
 *   - `GET /api/admin/operators/{id}/mfa` → `OperatorMfaView` · `DELETE /api/admin/operators/{id}/mfa` → 204(본인 409 `MFA_RESET_SELF`, 404)
 *
 * 비밀키·코드·복구 코드는 로그에 남기지 않는다.
 */
const PATH = {
    authRoot: '/api/admin/auth/mfa',
    verify: () => `${PATH.authRoot}/verify`,
    cancel: () => `${PATH.authRoot}/cancel`,
    forcedEnrollment: () => `${PATH.authRoot}/enrollment`,
    forcedEnrollmentConfirm: () => `${PATH.authRoot}/enrollment/confirm`,
    root: '/api/admin/mfa',
    enrollment: () => `${PATH.root}/enrollment`,
    enrollmentConfirm: () => `${PATH.root}/enrollment/confirm`,
    me: () => `${PATH.root}/me`,
    recoveryCodes: () => `${PATH.root}/recovery-codes`,
    reenroll: () => `${PATH.root}/reenroll`,
    trustedDevices: () => `${PATH.root}/trusted-devices`,
    trustedDevice: (id: string) => `${PATH.root}/trusted-devices/${encodeURIComponent(id)}`,
    loginSecurity: '/api/admin/login-security',
    operatorMfa: (operatorId: string) => `/api/admin/operators/${encodeURIComponent(operatorId)}/mfa`,
};

export function useMfaApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        verify: (body: MfaVerifyRequest): Promise<MfaVerifyResponse> => client.post<MfaVerifyResponse>(PATH.verify(), body),

        cancel: (): Promise<void> => client.post<void>(PATH.cancel()),

        /** 강제 등록 시작 — 2차 대기 세션(ENROLL) */
        startForcedEnrollment: (): Promise<MfaEnrollmentView> => client.post<MfaEnrollmentView>(PATH.forcedEnrollment()),

        /** 강제 등록 확정 — 응답의 `me` 로 로그인이 끝난다 */
        confirmForcedEnrollment: (code: string): Promise<MfaEnrollmentConfirmResponse> => client.post<MfaEnrollmentConfirmResponse>(PATH.forcedEnrollmentConfirm(), { code }),

        /** 자발 등록 시작 — 로그인 세션(유예 중 [지금 등록] · 내 정보) */
        startEnrollment: (): Promise<MfaEnrollmentView> => client.post<MfaEnrollmentView>(PATH.enrollment()),

        /** 자발 등록·다시 등록 확정 — `me` 없음 */
        confirmEnrollment: (code: string): Promise<MfaEnrollmentConfirmResponse> => client.post<MfaEnrollmentConfirmResponse>(PATH.enrollmentConfirm(), { code }),

        me: (): Promise<MfaMeView> => client.get<MfaMeView>(PATH.me()),

        regenerateRecoveryCodes: (code: string): Promise<MfaRecoveryCodesResponse> => client.post<MfaRecoveryCodesResponse>(PATH.recoveryCodes(), { code }),

        reenroll: (password: string): Promise<MfaEnrollmentView> => client.post<MfaEnrollmentView>(PATH.reenroll(), { password }),

        trustedDevices: (): Promise<MfaTrustedDeviceView[]> => client.get<MfaTrustedDeviceView[]>(PATH.trustedDevices()),

        revokeDevice: (id: string): Promise<void> => client.del<void>(PATH.trustedDevice(id)),

        revokeAllDevices: (): Promise<void> => client.del<void>(PATH.trustedDevices()),

        loginSecurity: (): Promise<LoginSecurityView> => client.get<LoginSecurityView>(PATH.loginSecurity),

        saveLoginSecurity: (body: LoginSecuritySaveRequest): Promise<LoginSecurityView> => client.put<LoginSecurityView>(PATH.loginSecurity, body),

        operatorMfa: (operatorId: string): Promise<OperatorMfaView> => client.get<OperatorMfaView>(PATH.operatorMfa(operatorId)),

        resetOperatorMfa: (operatorId: string): Promise<void> => client.del<void>(PATH.operatorMfa(operatorId)),
    };
}
