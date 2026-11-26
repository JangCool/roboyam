import type { BoardRecommendItem } from '~/composables/board/api/useBoardApi.types';

/**
 * 게시판 목록 위 「추천 글」 카드(board-recommend-contract) — 게시판 하나의 카드 최대 3개.
 * 게시판 정보·글 목록과 **함께**(기다리지 않고) 받아 카드가 목록보다 늦게 끼어들며 자리를 흔드는 일을 줄이고,
 * 이 방문 동안 받은 게시판은 `useState` 에 남겨 다시 들어오면 받은 것으로 먼저 그린 뒤 조용히 새로 받는다.
 * 실패·볼 수 없음(404·403·핀 필요)은 카드 줄을 그리지 않는 것으로 끝낸다 — 게시판 화면의 판정은 게시판 정보가 한다.
 *
 * @param {MaybeRefOrGetter<string | null>} slug - 받을 게시판 slug. null 이면 받지 않는다(핀 입력 중 등 — 다시 slug 가 되면 새로 받는다).
 */
export function useBoardRecommended(slug: MaybeRefOrGetter<string | null>) {
    const api = useBoardApi();
    /** slug → 마지막으로 받은 카드 */
    const cache = useState<Record<string, BoardRecommendItem[]>>('board-recommended', () => ({}));
    const items = ref<BoardRecommendItem[]>([]);

    let ticket = 0;
    let abort: AbortController | null = null;

    async function load(wanted: string): Promise<void> {
        const mine = ++ticket;
        abort?.abort();
        abort = new AbortController();
        try {
            const found = (await api.recommended(wanted, abort.signal)).items ?? [];
            if (mine !== ticket) return;
            cache.value = { ...cache.value, [wanted]: found };
            items.value = found;
        } catch (e) {
            if (mine !== ticket) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            items.value = [];
        }
    }

    watch(
        () => toValue(slug),
        (wanted) => {
            if (!wanted) {
                ticket++;
                abort?.abort();
                return;
            }
            items.value = cache.value[wanted] ?? [];
            void load(wanted);
        },
        { immediate: true },
    );

    onBeforeUnmount(() => abort?.abort());

    return { items };
}
