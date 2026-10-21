import { ApiError } from '~/composables/common/useApiClient';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/**
 * 잡은 오류(`catch (e)`)를 `ApiError` 로 바꿔 주는 함수를 돌려준다 — 화면이 서버 `message` 를 그대로 보이려고 쓴다(`forms.md` 서버 오류).
 * `ApiError` 면 그대로, 아니면(네트워크 끊김 등 예상 밖 오류) `NETWORK` 오류로 감싼다. 문구는 지금 언어다.
 * ⚠ 컴포넌트 setup 에서 부른다(번역 함수를 그때 붙잡는다). 돌려받은 함수는 `await` 뒤·콜백 안에서 써도 된다.
 *
 * @returns 오류를 `ApiError` 로 바꾸는 함수.
 */
export function useApiErrorOf(): (e: unknown) => ApiError {
    const t = useTranslate();
    return (e) => (e instanceof ApiError ? e : new ApiError({ status: 0, code: 'NETWORK', message: UtilAdminAuthMessages.fallback(t, 0, 'NETWORK') }));
}
