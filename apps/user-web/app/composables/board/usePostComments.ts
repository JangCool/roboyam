import type { CommentListResponse, CommentSort, CommentView, CommentWriteRequest } from '~/composables/board/api/useCommentApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 글 보기의 댓글 — 목록·정렬·쓰기·고치기·지우기·추천을 한 곳에서 한다. 서버 호출은 `useCommentApi` 한 겹이고 상태는 이 화면의 `ref` 다(공유 상태가 아니다).
 * 쓰기·고치기·지우기가 끝나면 목록을 다시 받는다(답글 자리·베스트·지운 자리를 서버가 정하므로 화면이 짐작해 고치지 않는다).
 * 실패는 `ApiError` 를 그대로 던진다 — 폼이 `fields` 를 칸 아래에 붙인다.
 */
export type CommentLoadState = 'loading' | 'ready' | 'error';

export function usePostComments(postId: MaybeRef<string>) {
    const api = useCommentApi();
    const t = useTranslate();

    const data = ref<CommentListResponse | null>(null);
    const state = ref<CommentLoadState>('loading');
    const errorMessage = ref('');
    const sort = ref<CommentSort>('OLDEST');

    let request = 0;
    let abort: AbortController | null = null;

    async function load(): Promise<void> {
        const mine = ++request;
        abort?.abort();
        abort = new AbortController();
        if (!data.value) state.value = 'loading';
        try {
            const result = await api.list(toValue(postId), sort.value, abort.signal);
            if (mine !== request) return;
            data.value = result;
            state.value = 'ready';
        } catch (e) {
            if (mine !== request) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            errorMessage.value = UtilBoardMessages.of(t, e);
            state.value = 'error';
        }
    }

    function setSort(next: CommentSort): void {
        if (sort.value === next) return;
        sort.value = next;
        void load();
    }

    async function create(content: string, contentFormat: ContentFormat, target: { parentId: string | null; replyToId: string | null }, guest: GuestCredentials | null): Promise<CommentView> {
        const body: CommentWriteRequest = { content, contentFormat, parentId: target.parentId, replyToId: target.replyToId, guest };
        const created = await api.create(toValue(postId), body);
        await load();
        return created;
    }

    async function update(id: string, content: string, contentFormat: ContentFormat, password: string | null): Promise<void> {
        await api.update(id, { content, contentFormat, password });
        await load();
    }

    async function remove(id: string, password: string | null): Promise<void> {
        await api.remove(id, { password });
        await load();
    }

    /** 추천 — 성공하면 그 댓글의 수만 바꾼다(목록을 다시 받지 않는다) */
    async function like(id: string): Promise<void> {
        const result = await api.like(id);
        const list = data.value;
        if (!list) return;
        const patch = (c: CommentView) => (c.id === id ? Object.assign(c, { likeCount: result.likeCount, liked: result.liked }) : c);
        list.threads.forEach((thread) => {
            patch(thread);
            thread.replies.forEach(patch);
        });
        list.best.forEach(patch);
    }

    onScopeDispose(() => abort?.abort());

    return { data, state, errorMessage, sort, load, setSort, create, update, remove, like };
}
