import { ApiError } from '~/composables/common/useApiClient';
import type { ProfileResponse } from '~/composables/profile/api/useProfileApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/**
 * 내 정보 — 내 정보 화면(`/me`)이 보는 상태(`useState`). 서버와 말하는 것은 `useProfileApi` 다.
 *
 * `profile`: `undefined` = 아직 안 물어봄 · 값 = 받았음. 화면이 열릴 때마다 `load()` 로 새로 받는다(다른 곳에서 바뀌었을 수 있다).
 * 닉네임을 저장하면 상단 네비가 보는 로그인 상태(`useAdminAuth().user`)의 닉네임도 **즉시** 바꾼다 — 상단 이름·아바타가 새로고침 없이 따라온다.
 */
export function useProfile() {
    const t = useTranslate();
    const api = useProfileApi();
    const { user } = useAdminAuth();

    const profile = useState<ProfileResponse | undefined>('admin-profile', () => undefined);
    const loading = useState<boolean>('admin-profile-loading', () => false);
    /** 읽기 실패 — 화면이 오류 상태와 다시 시도를 보인다 */
    const error = useState<ApiError | null>('admin-profile-error', () => null);

    /** 어떤 오류든 화면이 보일 `ApiError` 로 바꾼다 — 네트워크 끊김 등 서버 문구가 없는 것은 번역 문구로 대신한다 */
    function asApiError(e: unknown): ApiError {
        return e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') });
    }

    async function load(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            profile.value = await api.get();
        } catch (e) {
            error.value = asApiError(e);
        } finally {
            loading.value = false;
        }
    }

    /**
     * 닉네임을 저장한다. 실패하면 `ApiError` 를 던진다(입력 400 · 중복 409 · 서버 500) — 화면이 코드로 가려 칸 아래/폼 위에 보인다.
     * @param {string} nickname - 새 닉네임.
     */
    async function saveNickname(nickname: string): Promise<void> {
        let saved: ProfileResponse;
        try {
            saved = await api.update({ nickname });
        } catch (e) {
            throw asApiError(e);
        }
        profile.value = saved;
        if (user.value) user.value = { ...user.value, nickname: saved.nickname };
    }

    return { profile, loading, error, load, saveNickname };
}
