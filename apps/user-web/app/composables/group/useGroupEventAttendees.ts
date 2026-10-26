import type { GroupEventAttendee } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent from '~/utils/util-group-event';

/**
 * 일정 참석자 — `GET /api/groups/{id}/events/{eventId}/attendees?page=&size=`(참석으로 응답한 지금 멤버, 응답이 최근인 순, community `useGroupEventAttendees`).
 * 일정 상세 화면 하나가 쓴다. `enabled` 가 참이 될 때(일정 상세를 받아 멤버임이 확인된 뒤) 첫 쪽을 받는다.
 * `reload()` 는 보이는 목록을 둔 채 첫 쪽을 조용히 다시 받는다(내 응답이 참석으로 바뀌거나 참석에서 빠졌을 때). 「더보기」 실패는 `moreError` 만.
 */
export interface GroupEventAttendeesOptions {
    groupId: MaybeRef<string>;
    eventId: MaybeRef<string>;
    enabled: MaybeRef<boolean>;
}

export function useGroupEventAttendees(options: GroupEventAttendeesOptions) {
    const api = useGroupApi();
    const t = useTranslate();

    const items = ref<GroupEventAttendee[]>([]);
    const status = ref<'loading' | 'ready' | 'error'>('loading');
    const errorMessage = ref('');
    const page = ref(1);
    const hasNext = ref(false);
    const loadingMore = ref(false);
    const moreError = ref('');

    let request = 0;

    async function fetchFirst(silent: boolean): Promise<void> {
        const mine = ++request;
        if (!silent) {
            items.value = [];
            status.value = 'loading';
        }
        errorMessage.value = '';
        moreError.value = '';
        loadingMore.value = false;
        try {
            const result = await api.eventAttendees(toValue(options.groupId), toValue(options.eventId), { page: 1, size: UtilGroupEvent.ATTENDEE_PAGE_SIZE });
            if (mine !== request) return;
            items.value = result.items;
            page.value = result.page;
            hasNext.value = result.hasNext;
            status.value = 'ready';
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            if (silent && status.value === 'ready') return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
        }
    }

    async function loadMore(): Promise<void> {
        if (status.value !== 'ready' || !hasNext.value || loadingMore.value) return;
        const mine = request;
        loadingMore.value = true;
        moreError.value = '';
        try {
            const result = await api.eventAttendees(toValue(options.groupId), toValue(options.eventId), { page: page.value + 1, size: UtilGroupEvent.ATTENDEE_PAGE_SIZE });
            if (mine !== request) return;
            const have = new Set(items.value.map((item) => item.user.id).filter((id): id is string => id !== null));
            items.value = [...items.value, ...result.items.filter((item) => item.user.id === null || !have.has(item.user.id))];
            page.value = result.page;
            hasNext.value = result.hasNext;
        } catch (e) {
            if (mine !== request) return;
            moreError.value = t('group.moreFailed', { reason: UtilBoardMessages.of(t, e) });
        } finally {
            if (mine === request) loadingMore.value = false;
        }
    }

    watch(
        () => [toValue(options.enabled), toValue(options.groupId), toValue(options.eventId)] as const,
        ([enabled]) => {
            if (enabled) void fetchFirst(false);
            else request++;
        },
        { immediate: true },
    );

    onBeforeUnmount(() => request++);

    return { items, status, errorMessage, hasNext, loadingMore, moreError, load: () => fetchFirst(false), reload: () => fetchFirst(true), loadMore };
}
