import type { AnonymousRevealReason, AnonymousRevealResult, AnonymousTarget } from '~/composables/anonymous/api/useAnonymousAuthorApi.types';
import type { AnonymousRevealView } from '~/composables/anonymous/useAnonymousReveal.types';

/** 칸에 붙는 서버 오류 */
const FIELD_KEYS = ['reason', 'memo'];

/**
 * 익명 작성자 열람(anonymous-board-contract 정책 10)의 흐름 — 게시글 관리 서랍·신고 처리 서랍이 부른다. 서버 호출은 `useAnonymousAuthorApi` 한 겹, 창은 `view` 로 그리고 이벤트로 알린다.
 *  - `canReveal`: 권한 `anonymous-author.reveal` — 없으면 「작성자 열람」 단추를 보이지 않는다(막는 것은 서버).
 *  - `start(target)`: 열면서 칸·결과를 비운다. `submit(reason, memo)`: 성공하면 창 안에 결과(회원 요약)를 둔다(창은 닫지 않는다).
 *    `400 fields.reason|memo` 는 칸 아래, 그 밖(403·404·409)은 창 안 한 줄(서버 문구 그대로, 쓴 메모는 그대로).
 * ⚠ 컴포넌트 setup 에서 부른다. 화면마다 새 상태다.
 */
export function useAnonymousReveal() {
    const api = useAnonymousAuthorApi();
    const errorOf = useApiErrorOf();
    const { can } = useAdminPermission();

    const open = ref(false);
    const target = ref<AnonymousTarget | null>(null);
    const busy = ref(false);
    const error = ref('');
    const fieldErrors = ref<Record<string, string>>({});
    const result = ref<AnonymousRevealResult | null>(null);

    const canReveal = computed(() => can('anonymous-author.reveal'));

    function start(next: AnonymousTarget): void {
        target.value = { ...next };
        busy.value = false;
        error.value = '';
        fieldErrors.value = {};
        result.value = null;
        open.value = true;
    }

    async function submit(reason: AnonymousRevealReason, memo: string): Promise<void> {
        const current = target.value;
        if (!current || busy.value) return;
        busy.value = true;
        error.value = '';
        fieldErrors.value = {};
        try {
            result.value = await api.reveal({
                ...(current.commentId ? { commentId: current.commentId } : { postId: current.postId ?? undefined }),
                reason,
                memo,
            });
        } catch (e) {
            const failure = errorOf(e);
            const fields = failure.status === 400 ? (failure.fields ?? {}) : {};
            const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => FIELD_KEYS.includes(k)));
            fieldErrors.value = mine;
            error.value = Object.keys(mine).length > 0 ? '' : failure.message;
        } finally {
            busy.value = false;
        }
    }

    const view = computed<AnonymousRevealView>(() => ({
        target: target.value,
        busy: busy.value,
        error: error.value,
        fieldErrors: fieldErrors.value,
        result: result.value,
    }));

    return { open, view, canReveal, start, submit };
}
