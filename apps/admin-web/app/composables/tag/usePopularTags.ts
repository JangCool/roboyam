import type { TagCount } from '~/composables/tag/api/useTagApi.types';
import { ApiError } from '~/composables/common/useApiClient';

/**
 * 최근 많이 달린 태그 추천 — 가젯 ⚙ 「태그 탭」의 고르기 칸(post-tags-contract: 최근 30일 · 대상 게시판 범위 안 · 달린 수와 함께).
 * 범위(게시판 slug 들)가 바뀌면 다시 받는다 — 늦게 온 옛 결과는 요청 번호로 버린다. 실패해도 직접 입력은 되므로 오류 문구만 들고 있다.
 *
 * @returns `tags` 추천(달린 수 많은 차례) · `loading` · `error` · `load(boards)`(빈 배열 = 전체 게시판).
 */
export function usePopularTags() {
    const tags = ref<TagCount[]>([]);
    const loading = ref(false);
    const error = ref('');

    const api = useTagApi();
    const toApiError = useApiErrorOf();
    let seq = 0;
    let abort: AbortController | null = null;

    async function load(boards: readonly string[]): Promise<void> {
        const mine = ++seq;
        abort?.abort();
        const controller = new AbortController();
        abort = controller;
        loading.value = true;
        error.value = '';
        try {
            const res = await api.popular({ boards: [...boards], days: 30, limit: 30 }, controller.signal);
            if (mine !== seq) return;
            tags.value = (Array.isArray(res) ? res : []).filter((x) => typeof x?.name === 'string' && x.name.trim() !== '');
        } catch (e) {
            if (mine !== seq) return;
            if (e instanceof ApiError && e.code === 'ABORTED') return;
            tags.value = [];
            error.value = toApiError(e).message;
        } finally {
            if (mine === seq) loading.value = false;
        }
    }

    onBeforeUnmount(() => abort?.abort());

    return { tags, loading, error, load };
}
