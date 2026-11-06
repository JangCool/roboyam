import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostListQuery, PostRow } from '~/composables/board/api/usePostApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';

/**
 * 소모임 상세의 글 탭 — 게시판 목록처럼 쪽 번호(`GET /api/groups/{id}/posts?page=&sort=&q=&prefix=`, 2026-11-06 사용자)와 `POST /api/groups/{id}/seen`.
 * 말머리·목록 모양(목록형·갤러리형·보기 전환)은 소모임 게시판 정보(`GET /api/groups/{id}/board` — 게시판 상세와 같은 모양)로 정하고, 소모임마다 한 번 받는다.
 * 한 화면(소모임 상세)이 쓰는 상태라 `useState` 가 아니라 부를 때마다 새 `ref` 다. 서버 호출이 있어 페이지가 아니라 이 컴포저블에 둔다.
 *
 * - `status`: `idle`(탭을 아직 안 열었다) · `loading` · `ready` · `error`.
 * - **다시 받는 때**: 부르는 쪽이 `load(조건)` 한다(조건·소모임·멤버 여부·로그인한 사람이 바뀔 때 — 비공개 글이 나타나거나 사라진다).
 * - **`seen`**: 멤버가 목록을 **받은 뒤** 소모임마다 한 번만 보낸다(못 받았으면 보내지 않는다 — 보지 못한 것을 봤다고 하지 않는다). 실패는 조용히 넘긴다.
 * - 게시판 정보를 못 받아도 목록은 그린다(말머리·보기 전환 없이 목록형).
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
    const locked = ref(false);
    const privateCount = ref(0);
    const page = ref(1);
    const totalPages = ref(0);
    const totalCount = ref(0);
    /** 소모임 게시판 정보 — 말머리·목록 모양. 못 받았으면 null */
    const board = ref<BoardDetail | null>(null);

    let controller: AbortController | null = null;
    let request = 0;
    /** `seen` 을 이미 보낸 소모임 — 한 화면에서 한 소모임에 한 번만 */
    let seenFor: string | null = null;
    /** 게시판 정보를 받은(받는 중인) 소모임 */
    let boardFor: string | null = null;

    function markSeenOnce(groupId: string): void {
        if (seenFor === groupId) return;
        seenFor = groupId;
        api.markSeen(groupId).catch(() => {
            // 새 글 표시가 한 번 덜 지워질 뿐 — 알리지 않는다
        });
    }

    function loadBoardOnce(groupId: string): void {
        if (boardFor === groupId) return;
        boardFor = groupId;
        board.value = null;
        api.board(groupId)
            .then((detail) => {
                if (boardFor === groupId) board.value = detail;
            })
            .catch(() => {
                // 말머리·보기 전환 없이 목록형으로 그린다 — 다음에 다시 받게 비운다
                if (boardFor === groupId) boardFor = null;
            });
    }

    /** 조건(말머리·정렬·검색·쪽)대로 (다시) 받는다 */
    async function load(query: PostListQuery): Promise<void> {
        const mine = ++request;
        controller?.abort();
        controller = new AbortController();
        const groupId = toValue(options.groupId);
        const member = toValue(options.member);
        loadBoardOnce(groupId);
        status.value = 'loading';
        errorMessage.value = '';
        try {
            const res = await api.posts(groupId, { page: query.page, sort: query.sort, q: query.q || undefined, prefix: query.prefix || undefined }, controller.signal);
            if (mine !== request) return;
            items.value = res.items;
            locked.value = res.locked;
            privateCount.value = Math.max(0, Math.floor(res.privateCount || 0));
            page.value = res.page ?? query.page;
            totalPages.value = res.totalPages ?? 0;
            totalCount.value = res.totalCount ?? res.items.length;
            status.value = 'ready';
            if (member) markSeenOnce(groupId);
        } catch (e) {
            if (mine !== request || UtilGroup.isAborted(e)) return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            status.value = 'error';
            if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND') || UtilGroup.isCode(e, 'GROUP_INACTIVE')) options.onGone?.();
        }
    }

    /** 보던 것을 버린다(다른 탭으로 갔다가 오면 새로 받는다) */
    function stop(): void {
        request++;
        controller?.abort();
        controller = null;
        status.value = 'idle';
    }

    onBeforeUnmount(stop);

    return { items, status, errorMessage, locked, privateCount, page, totalPages, totalCount, board, load, stop };
}
