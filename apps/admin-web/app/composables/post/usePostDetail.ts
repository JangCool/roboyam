import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostDetail } from '~/composables/post/api/usePostApi.types';

/**
 * 게시글 관리의 글 서랍(`PostDetailDrawer`)이 보는 상태 — 글 하나의 본문·조치 기록을 받아 들고 있다. 화면(게시글 관리)이 부를 때마다 새 상태다(`useState` 가 아니다).
 * 서버 호출은 `usePostApi` 한 겹이고 컴포넌트는 이 상태를 props 로 받는다. 다른 글을 연 뒤 늦게 온 앞 요청의 응답은 버린다(번호표).
 * `silent` 로 다시 받으면(조치 뒤) 보이던 내용을 지우지 않고 바꾼다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다(오류를 `ApiError` 로 바꾸는 함수가 번역 함수를 그때 붙잡는다).
 */
export function usePostDetail() {
    const api = usePostApi();
    const errorOf = useApiErrorOf();

    /** 열린 글 번호 — 받기 전에도 있다 */
    const openId = ref<string | null>(null);
    const detail = ref<AdminPostDetail | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let ticket = 0;

    async function open(id: string, options: { silent?: boolean } = {}): Promise<void> {
        const mine = ++ticket;
        openId.value = id;
        if (!options.silent || detail.value?.post.id !== id) detail.value = null;
        loading.value = true;
        error.value = null;
        try {
            const found = await api.get(id);
            if (mine === ticket) detail.value = found;
        } catch (e) {
            if (mine !== ticket) return;
            // 조용히 다시 받다 실패하면 보이던 내용은 그대로 둔다(서랍 안 오류는 처음 받을 때만)
            if (!options.silent || !detail.value) {
                detail.value = null;
                error.value = errorOf(e);
            }
        } finally {
            if (mine === ticket) loading.value = false;
        }
    }

    function reload(silent = true): Promise<void> {
        return openId.value ? open(openId.value, { silent }) : Promise.resolve();
    }

    function close(): void {
        ticket++;
        openId.value = null;
        detail.value = null;
        error.value = null;
        loading.value = false;
    }

    return { openId, detail, loading, error, open, reload, close };
}
