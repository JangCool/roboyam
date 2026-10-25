import type { IdentityConfirmRequest, IdentityStatus, IdentityVerificationStart } from '~/composables/identity/api/useIdentityVerificationApi.types';

/**
 * 본인인증 API — 서버 주소 문자열(`/api/identity-verifications…`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태·인증 흐름은 `useIdentity`).
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-4」(서버 `IdentityVerificationController`). 로그인 필요(401), 본인 것만, `POST` 는 CSRF, 응답은 `no-store`.
 *
 *  - `GET /api/identity-verifications/me` → `IdentityStatus`(`{ verified, verifiedAt, adult, adultFrom }`)
 *  - `POST /api/identity-verifications` → `IdentityVerificationStart`(`{ verificationId, provider, storeId?, channelKey? }` — `FAKE` 면 `storeId`·`channelKey` 칸이 아예 없다).
 *    서버가 인증 번호를 먼저 발급한다(30분 유효). 오류: `409 IDENTITY_ALREADY_VERIFIED` · `503 IDENTITY_NOT_CONFIGURED`(포트원 설정 칸이 빔) · `503 INDEX_KEY_MISSING`
 *  - `POST /api/identity-verifications/{verificationId}/confirm` `{ fake? }` → `IdentityStatus`
 *    오류: `404 IDENTITY_VERIFICATION_NOT_FOUND`(남의 번호 포함) · `410 IDENTITY_VERIFICATION_EXPIRED`(30분 지남·이미 끝난 번호) · `409 IDENTITY_ALREADY_LINKED` ·
 *    `409 IDENTITY_REVERIFY_BLOCKED`(오류 본문 최상위 `retryAfter` → `ApiError.extras`) · `409 IDENTITY_ALREADY_VERIFIED` · `502 IDENTITY_PROVIDER_FAILED` ·
 *    `400 VALIDATION`(가짜 입력 `fields['fake.birthDate'|'fake.personKey']`)
 */
const PATH = {
    root: '/api/identity-verifications',
    me: () => `${PATH.root}/me`,
    confirm: (verificationId: string) => `${PATH.root}/${encodeURIComponent(verificationId)}/confirm`,
};

export function useIdentityVerificationApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 본인인증 상태 */
        me: (): Promise<IdentityStatus> => client.get<IdentityStatus>(PATH.me()),

        /** 인증 시작 — 인증 번호 발급과 지금 쓰는 구현(포트원/가짜) */
        start: (): Promise<IdentityVerificationStart> => client.post<IdentityVerificationStart>(PATH.root),

        /** 인증 완료 — 포트원은 본문 없이(서버가 포트원에 결과를 묻는다), 가짜는 `{ fake }` */
        confirm: (verificationId: string, body: IdentityConfirmRequest): Promise<IdentityStatus> => client.post<IdentityStatus>(PATH.confirm(verificationId), body),
    };
}
