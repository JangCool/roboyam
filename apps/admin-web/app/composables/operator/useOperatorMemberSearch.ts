import type { OperatorMemberSearchCandidate } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import { OPERATOR_MEMBER_SEARCH_MIN, type OperatorMemberSearchView } from '~/composables/operator/useOperatorMemberSearch.types';

/**
 * 운영자 서랍 「사이트 계정」 탭의 회원 검색 흐름 — 이메일이 달라도 회원을 찾아 운영자에 잇는다(2026-11-24, `useOperatorMemberAccountApi.searchCandidates`).
 *  - `search(operatorId, q)`: 다듬은 말이 2자 미만이면 부르지 않고 결과를 비운다. 늦게 온 앞 응답은 버린다(차례표).
 *  - `refresh()`: 마지막 검색을 다시 한다(연결 실패 뒤 자격이 바뀌었을 때).
 *  - `reset()`: 다른 운영자로 바뀌거나 이은 뒤 비운다.
 * 상태는 화면마다 새로 — 운영자 관리 페이지가 부르고 서랍·탭으로 `view` 를 내려준다. ⚠ 컴포넌트 setup 에서 부른다.
 */
export function useOperatorMemberSearch() {
    const api = useOperatorMemberAccountApi();
    const errorOf = useApiErrorOf();

    const operatorId = ref('');
    const query = ref('');
    const items = ref<OperatorMemberSearchCandidate[] | null>(null);
    const loading = ref(false);
    const error = ref('');
    let ticket = 0;

    function reset(): void {
        ticket++;
        operatorId.value = '';
        query.value = '';
        items.value = null;
        loading.value = false;
        error.value = '';
    }

    async function search(targetOperatorId: string, raw: string): Promise<void> {
        const q = raw.trim();
        const mine = ++ticket;
        operatorId.value = targetOperatorId;
        error.value = '';
        if (q.length < OPERATOR_MEMBER_SEARCH_MIN) {
            query.value = '';
            items.value = null;
            loading.value = false;
            return;
        }
        query.value = q;
        loading.value = true;
        try {
            const page = await api.searchCandidates(targetOperatorId, q);
            if (mine !== ticket) return;
            items.value = page.items;
        } catch (e) {
            if (mine !== ticket) return;
            const failure = errorOf(e);
            items.value = null;
            error.value = failure.field('q') || failure.message;
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    async function refresh(): Promise<void> {
        if (!operatorId.value || !query.value) return;
        await search(operatorId.value, query.value);
    }

    const view = computed<OperatorMemberSearchView>(() => ({
        query: query.value,
        items: items.value,
        loading: loading.value,
        error: error.value,
    }));

    return { view, search, refresh, reset };
}
