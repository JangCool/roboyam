import { ApiError } from '~/composables/common/useApiClient';
import type { LoginHistoryItem } from '~/composables/profile/api/useProfileApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/**
 * 내 로그인 이력 — 내 정보의 "로그인 이력" 탭이 보는 상태(`useState`). 서버와 말하는 것은 `useProfileApi` 다.
 *
 * `items`: `undefined` = 아직 안 물어봄 · 배열 = 받았음(비었으면 이력 없음). 탭을 처음 열 때 한 번 받고(`ensureLoaded`) 탭을 오가도 다시 묻지 않는다.
 * 실패하면 `error` 에 담고 `items` 는 그대로 `undefined` 라 다음에 열거나 "다시 시도"(`reload`)로 다시 받는다.
 */
export function useLoginHistory() {
    const t = useTranslate();
    const api = useProfileApi();

    const items = useState<LoginHistoryItem[] | undefined>('admin-login-history', () => undefined);
    const loading = useState<boolean>('admin-login-history-loading', () => false);
    const error = useState<ApiError | null>('admin-login-history-error', () => null);

    async function reload(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            items.value = (await api.loginHistory()).items;
        } catch (e) {
            error.value = e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') });
        } finally {
            loading.value = false;
        }
    }

    /** 아직 안 받았을 때만 받는다 */
    async function ensureLoaded(): Promise<void> {
        if (items.value === undefined) await reload();
    }

    return { items, loading, error, reload, ensureLoaded };
}
