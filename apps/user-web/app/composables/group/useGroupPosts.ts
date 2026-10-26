import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';

/**
 * 소모임 상세의 글 탭 — `GET /api/groups/{id}/posts`(커서로 더보기)와 `POST /api/groups/{id}/seen`(community `useGroupPosts`, `docs/api-groups.md` 8절).
 * 한 화면(소모임 상세)이 쓰는 상태라 `useState` 가 아니라 부를 때마다 새 `ref` 다. 서버 호출이 있어 페이지가 아니라 이 컴포저블에 둔다.
 *
 * - `status`: `idle`(탭을 아직 안 열었다) · `loading` · `ready` · `error`. 「더보기」 실패는 목록을 두고 `moreError` 만 채운다.
 * - **다시 받는 때**: 부르는 쪽이 `load()` 한다(소모임·멤버 여부·로그인한 사람이 바뀔 때 — 비공개 글이 나타나거나 사라진다).
 * - **`seen`**: 멤버가 목록을 **받은 뒤** 소모임마다 한 번만 보낸다(못 받았으면 보내지 않는다 — 보지 못한 것을 봤다고 하지 않는다). 실패는 조용히 넘긴다.
 * - `onGone`: 소모임이 없어졌거나 비활성이 됐다는 답(`GROUP_NOT_FOUND`·`GROUP_INACTIVE`) — 부르는 쪽이 상세를 다시 받는다.
 * - 경쟁 요청: 새 요청이 오면 앞 요청을 끊고, 늦게 온 옛 결과는 요청 번호로 버린다.
 *
 * ⚠ setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다).
 */
export type GroupPostsStatus = 'idle' | 'loading' | 'ready' | 'error';

export interface GroupPostsOptions {
    groupId: MaybeRef<string>;
    /** 멤버(모임장 포함)인가 — `seen` 을 보낼지 가른다 */
    member: MaybeRef<boolean>;
    onGone?: () => void;
}

export function useGroupPosts(options: GroupPostsOptions) {
    const api = useGroupApi();
    const t = useTranslate();

    const items = ref<PostRow[]>([]);
    const status = ref<GroupPostsStatus>('idle');
    const errorMessage = ref('');
    const hasNext = ref(false);
    const nextCursor = ref<string | null>(null);
    const locked = ref(false);
    const privateCount = ref(0);
    const loadingMore = ref(false);
    const moreError = ref('');

    let controller: AbortController | null = null;
    let request = 0;
    /** `seen` 을 이미 보낸 소모임 — 한 화면에서 한 소모임에 한 번만 */
    let seenFor: string | null = null;

    function markSeenOnce(groupId: string): void {
        if (seenFor === groupId) return;
        seenFor = groupId;
        api.markSeen(groupId).catch(() => {
            // 새 글 표시가 한 번 덜 지워질 뿐 — 알리지 않는다
        });
    }

    /** 첫 쪽부터 (다시) 받는다 */
    async function load(): Promise<void> {
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        const groupId = toValue(options.groupId);
        const member = toValue(options.member);
        items.value = [];
        hasNext.value = false;
        nextCursor.value = null;
        locked.value = false;
        privateCount.value = 0;
        status.value = 'loading';
        errorMessage.value = '';
        moreError.value = '';
        loadingMore.value = false;
        try {
            const page = await api.posts(groupId, { size: UtilGroup.POST_PAGE_SIZE }, controller.signal);
            if (mine !== request) return;
            apply(page.items, page);
            status.value = 'ready';
            if (member) markSeenOnce(groupId);
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
            if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND') || UtilGroup.isCode(e, 'GROUP_INACTIVE')) options.onGone?.();
        }
    }

    /** 다음 쪽 — 실패해도 보이는 목록은 그대로. 새 글이 올라와 쪽이 겹쳐도 같은 글이 두 번 나오지 않게 거른다 */
    async function loadMore(): Promise<void> {
        const cursor = nextCursor.value;
        if (status.value !== 'ready' || !hasNext.value || !cursor || loadingMore.value || !controller) return;
        const mine = request;
        loadingMore.value = true;
        moreError.value = '';
        try {
            const page = await api.posts(toValue(options.groupId), { cursor, size: UtilGroup.POST_PAGE_SIZE }, controller.signal);
            if (mine !== request) return;
            const have = new Set(items.value.map((row) => row.id));
            apply([...items.value, ...page.items.filter((row) => !have.has(row.id))], page);
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            moreError.value = t('group.moreFailed', { reason: UtilBoardMessages.of(t, e) });
        } finally {
            if (mine === request) loadingMore.value = false;
        }
    }

    function apply(rows: PostRow[], page: { hasNext: boolean; nextCursor: string | null; locked: boolean; privateCount: number }): void {
        items.value = rows;
        nextCursor.value = page.nextCursor;
        hasNext.value = page.hasNext && Boolean(page.nextCursor);
        locked.value = page.locked;
        privateCount.value = Math.max(0, Math.floor(page.privateCount || 0));
    }

    /** 보던 것을 버린다(다른 탭으로 갔다가 오면 새로 받는다) */
    function stop(): void {
        request++;
        controller?.abort();
        controller = null;
        status.value = 'idle';
    }

    onBeforeUnmount(stop);

    return { items, status, errorMessage, hasNext, loadingMore, moreError, locked, privateCount, load, loadMore, stop };
}
