import { ApiError } from '~/composables/common/useApiClient';
import type { DisplayValues, MyDisplayResponse } from '~/composables/profile/api/useProfileApi.types';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/**
 * 내 표시 형식(언어·시간대·시간 형식·날짜 형식) — 관리자 표시 값의 층: **사이트 표시 기본값 ← 내 표시 형식**. 서버와 말하는 것은 `useProfileApi` 다.
 * 이 화면이 읽고 쓰는 것은 "내 표시 형식을 쓴다"(`use`)와 내 값(`values`)이고, 화면에 적용되는 효과값(`use` 면 내 값, 아니면 사이트 표시 기본값 `inherited`)은 공유 레이어(`@roboyam/display-time`)의 `useDisplay().setDisplay` 가 받는다.
 * 레이어는 서버를 모르므로 값을 채우는 일은 앱이 한다: `plugins/display.client.ts` 가 로그인한 사람의 값을 받아 채우고(`apply`), 개인 설정 탭은 저장한 직후 응답의 효과값을 **바로** 채운다(저장했는데 화면이 안 바뀌면 사람은 자기가 뭘 잘못했나 찾는다).
 *
 * `view`: `undefined` = 아직 안 물어봄 · 값 = 받았음. 받기에 실패하면 `error` 에 담고 화면은 지금 값(레이어 폴백)을 그대로 쓴다.
 */
export function useMyDisplay() {
    const t = useTranslate();
    const api = useProfileApi();
    const display = useDisplay();

    const view = useState<MyDisplayResponse | undefined>('admin-my-display', () => undefined);
    const loading = useState<boolean>('admin-my-display-loading', () => false);
    const error = useState<ApiError | null>('admin-my-display-error', () => null);

    function asApiError(e: unknown): ApiError {
        return e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') });
    }

    /** 응답의 효과값을 화면 전체의 표시 값으로 채운다 — 내 표시 형식을 쓰면 내 값, 아니면 사이트 표시 기본값 */
    function apply(res: MyDisplayResponse): void {
        const effective: DisplayValues = res.use ? res.values : res.inherited;
        display.setDisplay(effective);
    }

    /** 서버에서 받는다. 실패하면 던지지 않고 `error` 에 담는다(앱 시작 때도 부르므로 화면을 막지 않는다) */
    async function load(): Promise<void> {
        if (loading.value) return;
        loading.value = true;
        error.value = null;
        try {
            const res = await api.display();
            view.value = res;
            apply(res);
        } catch (e) {
            error.value = asApiError(e);
        } finally {
            loading.value = false;
        }
    }

    /** 저장하고 바로 화면에 적용한다. 실패하면 `ApiError` 를 던진다(칸 오류는 `fields`) */
    async function save(use: boolean, values: DisplayValues): Promise<MyDisplayResponse> {
        let res: MyDisplayResponse;
        try {
            res = await api.saveDisplay({ use, values });
        } catch (e) {
            throw asApiError(e);
        }
        view.value = res;
        apply(res);
        return res;
    }

    /** 로그아웃 — 다른 사람 값이 남지 않게 비우고 레이어 폴백으로 되돌린다 */
    function clear(): void {
        view.value = undefined;
        error.value = null;
        display.resetDisplay();
    }

    return { view, loading, error, load, save, clear };
}
