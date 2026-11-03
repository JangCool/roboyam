import type { WithdrawalResult } from '~/composables/account/api/useWithdrawalApi.types';

/**
 * 방금 끝난 탈퇴의 결과 — 탈퇴 화면(`/settings/account/withdraw`)이 즉시 완료 응답을 넣고, 완료 화면(`/withdrawn`)이 읽는다.
 * 완료 뒤에는 세션이 끝나 서버에 다시 물을 수 없어서 화면이 들고 간다. 새로고침·직접 열기면 `null` 이고 완료 화면은 일반 안내로 그린다.
 */
export function useWithdrawalDone() {
    const result = useState<WithdrawalResult | null>('withdrawal-done', () => null);
    return { result };
}
