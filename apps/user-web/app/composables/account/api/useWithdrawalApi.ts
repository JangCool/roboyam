import type { WithdrawalRequest, WithdrawalResult, WithdrawalView } from '~/composables/account/api/useWithdrawalApi.types';

/**
 * 회원 탈퇴 API — 서버 주소 문자열(`/api/me/withdrawal`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-withdrawal-contract.md`. 규칙의 정본은 `docs/domain/commerce.md` 17-1. 서버: `MeWithdrawalController`.
 * 로그인 필요(401), 쓰기는 CSRF. 탈퇴 진행 중(SCHEDULED)에도 읽기·취소는 된다(17-1 — 그 상태에서 로그인하면 남은 일 처리·탈퇴 취소만).
 *
 *  - `GET /api/me/withdrawal` → `WithdrawalView`(사라지는 것·남는 것·남은 일·선택이 필요한 것·재가입 가능일·본인 확인 방식). 탈퇴 완료 회원의 낡은 세션은 401.
 *  - `POST /api/me/withdrawal` `WithdrawalRequest` → `WithdrawalResult`. 즉시 완료(`COMPLETED`)면 서버가 모든 세션을 끝냈다 — 화면은 로그인 상태를 비우고 `/withdrawn` 으로.
 *    오류: `400 VALIDATION`(`fields.reason`·`reasonNote`·`groupChoices`·`password`·`forfeitConsent`) · `401 CURRENT_PASSWORD_WRONG` · `429 LOGIN_LOCKED`(+`retryAfterSeconds`) ·
 *    `401 REAUTH_REQUIRED`(소셜 전용 회원이 최근 10분 안 로그인이 아님) · `409 WITHDRAWAL_ALREADY_SCHEDULED`
 *  - `DELETE /api/me/withdrawal` → `204`(탈퇴 진행 중 취소) · `409 WITHDRAWAL_NOT_SCHEDULED`
 */
const PATH = {
    withdrawal: '/api/me/withdrawal',
};

export function useWithdrawalApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 탈퇴 화면에 보일 것 전부 — 상태가 SCHEDULED 면 `scheduled` 에 남은 일 */
        view: (): Promise<WithdrawalView> => client.get<WithdrawalView>(PATH.withdrawal),

        /** 탈퇴 신청 — 남은 일이 없으면 그 자리에서 완료(세션 끝), 있으면 탈퇴 진행 중 */
        withdraw: (body: WithdrawalRequest): Promise<WithdrawalResult> => client.post<WithdrawalResult>(PATH.withdrawal, body),

        /** 탈퇴 진행 중 취소 — 계정이 보통 상태로 돌아온다 */
        cancel: (): Promise<void> => client.del<void>(PATH.withdrawal),
    };
}
