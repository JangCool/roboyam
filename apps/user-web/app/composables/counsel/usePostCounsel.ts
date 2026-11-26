import type { PostCounselView } from '~/composables/counsel/api/useCounselApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 글 보기의 AI 연애상담소 칸 하나(ai-love-counsel-contract) — 상담 상태 · AI 상담사 셋의 답 · 고른 답 · 조언 고르기 · AI 답 추천.
 * 페이지가 글을 받은 뒤 「AI 상담소」 말머리 글(`prefix.counsel`)일 때만 `load` 한다(그 밖은 `clear`). 서버 호출은 `useCounselApi`·`useCommentApi` 한 겹이고 상태는 이 화면의 `ref` 다.
 * 회원 답변은 일반 댓글 목록(`usePostComments`) 그대로 — 고르기 결과(`pickedCommentId`)만 이 칸이 갖고 댓글 구획에 내려 준다.
 * 실패는 `ApiError` 를 그대로 던진다(고르기·추천 — 페이지가 서버 문구를 알린다).
 */
export type CounselLoadState = 'idle' | 'loading' | 'ready' | 'error';

export function usePostCounsel(postId: MaybeRef<string>) {
    const api = useCounselApi();
    const commentApi = useCommentApi();
    const t = useTranslate();

    const data = ref<PostCounselView | null>(null);
    const state = ref<CounselLoadState>('idle');
    const errorMessage = ref('');
    /** 고르는 중인 댓글 ID(없으면 빈 글자) */
    const pickBusyId = ref('');

    let request = 0;
    let abort: AbortController | null = null;

    async function load(): Promise<void> {
        const mine = ++request;
        abort?.abort();
        abort = new AbortController();
        if (!data.value) state.value = 'loading';
        try {
            const result = await api.post(toValue(postId), abort.signal);
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

    /** 상담 글이 아니다 — 칸을 비운다 */
    function clear(): void {
        request += 1;
        abort?.abort();
        data.value = null;
        state.value = 'idle';
    }

    // 다른 글로 가면 지난 글의 칸을 비운다 — 다시 받는 것은 페이지가 글을 받은 뒤
    watch(
        () => toValue(postId),
        () => clear(),
    );

    /** 「이 조언 고르기」 — 바뀐 칸으로 갈아 끼운다(채택 표시·고르기 단추는 서버 값 그대로) */
    async function pick(commentId: string): Promise<void> {
        if (pickBusyId.value) return;
        pickBusyId.value = commentId;
        try {
            data.value = await api.pick(toValue(postId), commentId);
        } finally {
            pickBusyId.value = '';
        }
    }

    /** AI 상담사 답 추천 — 응답 값으로 그 답만 고친다 */
    async function like(commentId: string): Promise<void> {
        const result = await commentApi.like(commentId);
        const answer = data.value?.counselors.find((c) => c.comment?.id === commentId)?.comment;
        if (answer) {
            answer.likeCount = result.likeCount;
            answer.liked = result.liked;
        }
    }

    return { data, state, errorMessage, pickBusyId, load, clear, pick, like };
}
