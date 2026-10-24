import type { CommentListResponse, CommentSort, CommentView, CommentWriteRequest } from '~/composables/board/api/useCommentApi.types';
import type { CommentKind, DebateStance } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 글 보기의 댓글 — 목록·정렬·구분(하이라이트 토론의 전체·일반·찬성·반대)·쓰기·고치기·지우기·추천·입장 바꾸기를 한 곳에서 한다.
 * 서버 호출은 `useCommentApi`·`useDebateApi` 한 겹이고 상태는 이 화면의 `ref` 다(공유 상태가 아니다).
 * 쓰기·고치기·지우기·입장 바꾸기가 끝나면 목록을 다시 받는다(답글 자리·베스트·지운 자리·구분별 수·내 입장을 서버가 정하므로 화면이 짐작해 고치지 않는다).
 * 실패는 `ApiError` 를 그대로 던진다 — 폼이 `fields` 를 칸 아래에 붙인다.
 */
export type CommentLoadState = 'loading' | 'ready' | 'error';

export function usePostComments(postId: MaybeRef<string>) {
    const api = useCommentApi();
    const debateApi = useDebateApi();
    const t = useTranslate();

    const data = ref<CommentListResponse | null>(null);
    const state = ref<CommentLoadState>('loading');
    const errorMessage = ref('');
    /** 처음 정렬은 보기 설정의 「댓글 정렬」(기본 오래된순) — 화면에서 바꾼 정렬은 이 글에서만 쓴다 */
    const sort = ref<CommentSort>(useViewSettingsValues().value.commentOrder);
    /** 토론 구분 — 글이 바뀌면 전체로 돌아간다 */
    const kind = ref<CommentKind>('ALL');

    let request = 0;
    let abort: AbortController | null = null;

    async function load(): Promise<void> {
        const mine = ++request;
        abort?.abort();
        abort = new AbortController();
        if (!data.value) state.value = 'loading';
        try {
            const result = await api.list(toValue(postId), sort.value, kind.value, abort.signal);
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

    function setKind(next: CommentKind): void {
        if (kind.value === next) return;
        kind.value = next;
        void load();
    }

    // 다른 글로 가면 구분을 처음(전체)으로 — 목록은 페이지가 글을 받은 뒤 부른다
    watch(
        () => toValue(postId),
        () => {
            kind.value = 'ALL';
        },
    );

    async function create(
        content: string,
        contentFormat: ContentFormat,
        target: { parentId: string | null; replyToId: string | null },
        guest: GuestCredentials | null,
        stance: DebateStance | null = null,
    ): Promise<CommentView> {
        const body: CommentWriteRequest = { content, contentFormat, parentId: target.parentId, replyToId: target.replyToId, guest, ...(stance ? { stance } : {}) };
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

    /** 내 입장 바꾸기 — 바꾼 뒤 목록을 다시 받아 `debate.myStance` 를 맞춘다 */
    async function changeStance(stance: DebateStance): Promise<DebateStance> {
        const result = await debateApi.changeStance(toValue(postId), stance);
        await load();
        return result.stance;
    }

    /** @ 지정 후보 */
    function commenters(q: string, signal?: AbortSignal) {
        return debateApi.commenters(toValue(postId), q, signal);
    }

    onScopeDispose(() => abort?.abort());

    return { data, state, errorMessage, sort, kind, load, setSort, setKind, create, update, remove, like, changeStance, commenters };
}
