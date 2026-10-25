import type { CommentLikeResult, CommentListResponse, CommentSort, CommentView, CommentWriteRequest } from '~/composables/board/api/useCommentApi.types';
import type { CommentKind, DebateStance, PinRequestReviewList, StanceChangeResult } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 글 보기의 댓글 목록 하나 — 목록·정렬·구분·쓰기·고치기·지우기·추천·비추천 + 찬반 토론(투표·@ 지정·의견 고정 요청·요청 확인).
 * 글 상세는 이것을 둘 쓴다: 댓글 구획(`GENERAL` 고정) · 토론 구획(`OPINION` 에서 시작, 칩으로 한 입장). 서버 호출은 `useCommentApi`·`useDebateApi` 한 겹이고 상태는 이 화면의 `ref` 다.
 * 쓰기·고치기·지우기·투표·요청 승인/거절이 끝나면 목록을 다시 받는다(답글 자리·베스트·고정 차례·구분별 수·내 입장을 서버가 정하므로 화면이 짐작해 고치지 않는다).
 * 추천·비추천·내 고정 요청은 응답 값으로 그 댓글만 고친다. 실패는 `ApiError` 를 그대로 던진다 — 폼이 `fields` 를 칸 아래에 붙인다.
 */
export type CommentLoadState = 'loading' | 'ready' | 'error';

export function usePostComments(postId: MaybeRef<string>, initialKind: CommentKind) {
    const api = useCommentApi();
    const debateApi = useDebateApi();
    const t = useTranslate();

    const data = ref<CommentListResponse | null>(null);
    const state = ref<CommentLoadState>('loading');
    const errorMessage = ref('');
    /** 처음 정렬은 보기 설정의 「댓글 정렬」(기본 오래된순) — 화면에서 바꾼 정렬은 이 글에서만 쓴다 */
    const sort = ref<CommentSort>(useViewSettingsValues().value.commentOrder);
    /** 구분 — 글이 바뀌면 처음 값으로 돌아간다 */
    const kind = ref<CommentKind>(initialKind);

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

    // 다른 글로 가면 지난 글의 목록을 비우고 구분을 처음으로 — 목록은 페이지가 글을 받은 뒤 부른다
    watch(
        () => toValue(postId),
        () => {
            request += 1;
            abort?.abort();
            data.value = null;
            state.value = 'loading';
            kind.value = initialKind;
        },
    );

    /**
     * 새 댓글·답글 — `opinionStance` 를 주면 토론 의견(`opinion: true` + 내가 아는 지금 입장 — 다른 창에서 바꿨으면 409 `STANCE_LOCKED`).
     * 비우면 일반 댓글(입장이 있어도 배지 없음). 답글은 서버가 원댓글을 따른다.
     */
    async function create(
        content: string,
        contentFormat: ContentFormat,
        target: { parentId: string | null; replyToId: string | null },
        guest: GuestCredentials | null,
        opinionStance: DebateStance | null = null,
    ): Promise<CommentView> {
        const body: CommentWriteRequest = {
            content,
            contentFormat,
            parentId: target.parentId,
            replyToId: target.replyToId,
            guest,
            ...(opinionStance ? { opinion: true, stance: opinionStance } : {}),
        };
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

    /** 이 목록에서 그 댓글(원댓글·답글·베스트)을 모두 고친다 */
    function patch(id: string, change: Partial<CommentView>): void {
        const list = data.value;
        if (!list) return;
        const apply = (c: CommentView) => {
            if (c.id === id) Object.assign(c, change);
        };
        list.threads.forEach((thread) => {
            apply(thread);
            thread.replies.forEach(apply);
        });
        list.best.forEach(apply);
    }

    function applyReaction(id: string, result: CommentLikeResult): void {
        patch(id, { likeCount: result.likeCount, liked: result.liked, dislikeCount: result.dislikeCount, disliked: result.disliked });
    }

    /** 추천(취소 없음 — 비추천해 둔 댓글이면 서버가 비추천을 푼다) */
    async function like(id: string): Promise<void> {
        applyReaction(id, await api.like(id));
    }

    /** 비추천(추천해 둔 댓글이면 서버가 추천을 푼다) */
    async function dislike(id: string): Promise<void> {
        applyReaction(id, await api.dislike(id));
    }

    /** 비추천 취소 */
    async function undislike(id: string): Promise<void> {
        applyReaction(id, await api.undislike(id));
    }

    /** 입장 고르기(투표) — 처음 고르기·바꾸기 모두. 바꾸면 기존 의견도 옮겨 가므로(소급) 목록을 다시 받아 내 입장·표·배지를 맞춘다 */
    async function vote(stance: DebateStance): Promise<StanceChangeResult> {
        const result = await debateApi.vote(toValue(postId), stance);
        await load();
        return result;
    }

    /** @ 지정 후보 */
    function commenters(q: string, signal?: AbortSignal) {
        return debateApi.commenters(toValue(postId), q, signal);
    }

    /** 내 의견 고정 요청 — 그 댓글의 `pinRequest` 를 대기로 */
    async function requestPin(commentId: string): Promise<void> {
        const created = await debateApi.requestPin(commentId);
        patch(commentId, { pinRequest: { requestId: created.id, status: 'PENDING', rejectReason: null } });
    }

    /** 내 대기 고정 요청 취소 */
    async function cancelPin(commentId: string): Promise<void> {
        await debateApi.cancelPin(commentId);
        patch(commentId, { pinRequest: null });
    }

    /** 내가 확인할 대기 고정 요청 */
    function pinRequests(): Promise<PinRequestReviewList> {
        return debateApi.pinRequests(toValue(postId));
    }

    /** 고정 요청 승인 — 고정 차례·대기 수가 바뀌므로 목록을 다시 받는다 */
    async function approvePin(id: string): Promise<void> {
        await debateApi.approvePin(id);
        await load();
    }

    /** 고정 요청 거절(사유 선택) — 대기 수가 바뀌므로 목록을 다시 받는다 */
    async function rejectPin(id: string, reason: string | null): Promise<void> {
        await debateApi.rejectPin(id, { reason });
        await load();
    }

    onScopeDispose(() => abort?.abort());

    return {
        data,
        state,
        errorMessage,
        sort,
        kind,
        load,
        setSort,
        setKind,
        create,
        update,
        remove,
        like,
        dislike,
        undislike,
        vote,
        commenters,
        requestPin,
        cancelPin,
        pinRequests,
        approvePin,
        rejectPin,
    };
}
