import type { GroupEventRow, GroupEventScope } from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent from '~/utils/util-group-event';

/**
 * 소모임 일정 목록 — `GET /api/groups/{id}/events?scope=&page=`(쪽 번호로 더보기, community `useGroupEvents`, `docs/api-groups.md` 9절).
 * 상세의 일정 탭(`scope: 'upcoming'`)과 지난 일정 화면(`'past'`)이 쓴다. 화면마다 새 `ref` 다(공유 상태가 아니다).
 *
 * - `status`: `idle`(아직 안 받음) · `loading` · `ready` · `error` · `locked`(`403 GROUP_NOT_MEMBER` — 멤버가 아니다). 「더보기」 실패는 목록을 두고 `moreError` 만 채운다.
 * - **다시 받는 때**: 부르는 쪽이 `load()` 한다(소모임·멤버 여부·로그인한 사람이 바뀔 때). 멤버가 아닌 줄 알면 `lock()` 으로 요청 없이 잠근다.
 * - `onGone`: 소모임이 없어졌거나 비활성이 됐거나 멤버가 아니게 됐다는 답 — 부르는 쪽이 상세를 다시 받는다. `errorCode` 로 지난 일정 화면이 갈래를 가른다.
 * - 경쟁 요청: 새 요청이 오면 앞 요청을 끊고, 늦게 온 옛 결과는 요청 번호로 버린다.
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export type GroupEventsStatus = 'idle' | 'loading' | 'ready' | 'error' | 'locked';

export interface GroupEventsOptions {
    groupId: MaybeRef<string>;
    scope: GroupEventScope;
    onGone?: () => void;
}

export function useGroupEvents(options: GroupEventsOptions) {
    const api = useGroupApi();
    const t = useTranslate();

    const items = ref<GroupEventRow[]>([]);
    const status = ref<GroupEventsStatus>('idle');
    const errorMessage = ref('');
    const errorCode = ref('');
    const page = ref(1);
    const hasNext = ref(false);
    const total = ref(0);
    const loadingMore = ref(false);
    const moreError = ref('');

    let controller: AbortController | null = null;
    let request = 0;

    function reset(): void {
        items.value = [];
        page.value = 1;
        hasNext.value = false;
        total.value = 0;
        errorMessage.value = '';
        errorCode.value = '';
        moreError.value = '';
        loadingMore.value = false;
    }

    /** 첫 쪽부터 (다시) 받는다 */
    async function load(): Promise<void> {
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        reset();
        status.value = 'loading';
        try {
            const result = await api.events(toValue(options.groupId), { scope: options.scope, page: 1, size: UtilGroupEvent.PAGE_SIZE }, controller.signal);
            if (mine !== request) return;
            items.value = result.items;
            page.value = result.page;
            hasNext.value = result.hasNext;
            total.value = result.total;
            status.value = 'ready';
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            errorCode.value = e instanceof ApiError ? e.code : '';
            if (UtilGroup.isCode(e, 'GROUP_NOT_MEMBER')) status.value = 'locked';
            else {
                errorMessage.value = UtilBoardMessages.of(t, e);
                status.value = 'error';
            }
            if (UtilGroupEvent.isGone(e)) options.onGone?.();
        }
    }

    /** 다음 쪽 — 실패해도 보이는 목록은 그대로. 쪽이 겹쳐도 같은 일정이 두 번 나오지 않게 거른다 */
    async function loadMore(): Promise<void> {
        if (status.value !== 'ready' || !hasNext.value || loadingMore.value || !controller) return;
        const mine = request;
        loadingMore.value = true;
        moreError.value = '';
        try {
            const result = await api.events(toValue(options.groupId), { scope: options.scope, page: page.value + 1, size: UtilGroupEvent.PAGE_SIZE }, controller.signal);
            if (mine !== request) return;
            const have = new Set(items.value.map((row) => row.id));
            items.value = [...items.value, ...result.items.filter((row) => !have.has(row.id))];
            page.value = result.page;
            hasNext.value = result.hasNext;
            total.value = result.total;
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            moreError.value = t('group.moreFailed', { reason: UtilBoardMessages.of(t, e) });
        } finally {
            if (mine === request) loadingMore.value = false;
        }
    }

    /** 멤버가 아니다 — 요청 없이 잠근다 */
    function lock(): void {
        request++;
        controller?.abort();
        controller = null;
        reset();
        status.value = 'locked';
    }

    /** 보던 것을 버린다(다른 탭으로 갔다가 오면 새로 받는다) */
    function stop(): void {
        request++;
        controller?.abort();
        controller = null;
        status.value = 'idle';
    }

    onBeforeUnmount(stop);

    return { items, status, errorMessage, errorCode, hasNext, total, loadingMore, moreError, load, loadMore, lock, stop };
}
