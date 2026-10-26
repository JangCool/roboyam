import type { MyGroupRequest, MyGroupsResponse } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 내 소모임(`GET /api/me/groups`) — 내 소모임 화면과 목록 화면의 데스크톱 요약이 쓴다. 부를 때마다 새 상태다(두 화면이 동시에 보이지 않는다).
 * 다시 받을 때 이미 보이는 것이 있으면 그대로 두고 실패만 토스트로 알린다. 개설 신청 취소(`cancelRequest`)는 성공하면 그 줄을 뺀다.
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export type MyGroupsStatus = 'idle' | 'loading' | 'ready' | 'error';

export function useMyGroups() {
    const api = useGroupApi();
    const toast = useAppToast();
    const t = useTranslate();

    const status = ref<MyGroupsStatus>('idle');
    const data = ref<MyGroupsResponse | null>(null);
    const errorMessage = ref('');
    const cancelling = ref<string[]>([]);

    let seq = 0;
    let abort: AbortController | null = null;

    async function load(): Promise<void> {
        const mine = ++seq;
        abort?.abort();
        const controller = new AbortController();
        abort = controller;
        if (!data.value) status.value = 'loading';
        errorMessage.value = '';
        try {
            const res = await api.mine(controller.signal);
            if (mine !== seq) return;
            data.value = res;
            status.value = 'ready';
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            if (data.value) {
                toast.error(UtilBoardMessages.of(t, e), 8000);
                status.value = 'ready';
                return;
            }
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
        }
    }

    /** 내 승인 대기 개설 신청을 거둔다 — 이미 처리됐으면(404) 목록이 낡았으니 새로 받는다 */
    async function cancelRequest(id: string): Promise<boolean> {
        if (cancelling.value.includes(id)) return false;
        cancelling.value = [...cancelling.value, id];
        try {
            await api.cancelRequest(id);
            const cur = data.value;
            if (cur) data.value = { ...cur, requests: cur.requests.filter((r: MyGroupRequest) => r.id !== id) };
            toast.success(t('group.myCanceledToast'));
            return true;
        } catch (e) {
            toast.error(UtilBoardMessages.of(t, e), 8000);
            if (e instanceof ApiError && e.status === 404) void load();
            return false;
        } finally {
            cancelling.value = cancelling.value.filter((x) => x !== id);
        }
    }

    function stop(): void {
        seq++;
        abort?.abort();
    }

    return { status, data, errorMessage, cancelling, load, cancelRequest, stop };
}
