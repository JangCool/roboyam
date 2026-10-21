import { ApiError } from '~/composables/common/useApiClient';
import type {
    InvitationAcceptRequest,
    InvitationCheckResponse,
    PasswordChangeRequest,
    PasswordResetCodeResponse,
    PasswordResetRequest,
} from '~/composables/account/api/useAdminAccountApi.types';
import type { AdminMeResponse } from '~/composables/auth/api/useAdminAuthApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/**
 * 운영자 계정의 비밀번호 흐름(초대 수락 · 비밀번호 재설정 · 비밀번호 변경) — 화면이 부르는 한 겹. 서버와 말하는 것은 `useAdminAccountApi` 다.
 * 상태는 없다(화면마다 따로 쓰고 비밀번호·토큰·승인번호는 그 화면 메모리에만 둔다 — `forms.md` 2-1). 하는 일은 **어떤 오류든 `ApiError` 로 바꿔 던지는 것**뿐이다:
 * 네트워크 끊김처럼 서버 문구가 없는 오류는 번역 문구를 담은 `NETWORK` 로 바꿔, 화면이 `code`·`message` 만 보면 되게 한다(`useProfile` 과 같은 방식).
 */
export function useAdminAccount() {
    const t = useTranslate();
    const api = useAdminAccountApi();

    function asApiError(e: unknown): ApiError {
        return e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') });
    }

    /** 실패하면 `ApiError` 를 던지는 호출로 감싼다 */
    async function run<T>(call: () => Promise<T>): Promise<T> {
        try {
            return await call();
        } catch (e) {
            throw asApiError(e);
        }
    }

    return {
        checkInvitation: (token: string): Promise<InvitationCheckResponse> => run(() => api.checkInvitation(token)),
        acceptInvitation: (body: InvitationAcceptRequest): Promise<void> => run(() => api.acceptInvitation(body)),
        requestPasswordResetCode: (email: string): Promise<PasswordResetCodeResponse> => run(() => api.requestPasswordResetCode({ email })),
        /** 성공하면 서버가 곧바로 로그인시킨 `me` 를 돌려준다 — 화면이 `useAdminAuth().adopt` 로 로그인 상태에 반영한다 */
        resetPassword: (body: PasswordResetRequest): Promise<AdminMeResponse> => run(() => api.resetPassword(body)),
        changePassword: (body: PasswordChangeRequest): Promise<void> => run(() => api.changePassword(body)),
        asApiError,
    };
}
