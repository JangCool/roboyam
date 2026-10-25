import type { ApiError } from '~/composables/common/useApiClient';
import type { ReportCaseDetail } from '~/composables/report/api/useReportApi.types';

/**
 * 신고 관리의 사건 서랍(`ReportDetailDrawer`)이 보는 상태 — 사건 하나의 원문·신고 목록·작성자·처리 기록을 받아 들고 있다. 화면(신고 관리)이 부를 때마다 새 상태다(`useState` 가 아니다).
 * 서버 호출은 `useReportApi` 한 겹이고 컴포넌트는 이 상태를 props 로 받는다. 다른 사건을 연 뒤 늦게 온 앞 요청의 응답은 버린다(번호표).
 * 조치·무시·다시 열기는 서버가 새 상세를 돌려주므로 `set` 으로 바로 바꾼다. `silent` 로 다시 받으면 보이던 내용을 지우지 않고 바꾼다.
 * ⚠ 대기(PENDING) 사건은 열람과 함께 서버가 검토 중으로 바꾼다 — 같은 사건을 괜히 두 번 받지 않는다.
 *
 * ⚠ 컴포넌트 setup 에서 부른다(오류를 `ApiError` 로 바꾸는 함수가 번역 함수를 그때 붙잡는다).
 */
export function useReportDetail() {
    const api = useReportApi();
    const errorOf = useApiErrorOf();

    /** 열린 사건 ID — 받기 전에도 있다 */
    const openId = ref<string | null>(null);
    const detail = ref<ReportCaseDetail | null>(null);
    const loading = ref(false);
    const error = ref<ApiError | null>(null);
    let ticket = 0;

    async function open(id: string, options: { silent?: boolean } = {}): Promise<void> {
        const mine = ++ticket;
        openId.value = id;
        if (!options.silent || detail.value?.case.id !== id) detail.value = null;
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

    /** 서버가 돌려준 새 상세(조치·무시·다시 열기의 응답)로 바꾼다 — 지금 열린 사건일 때만 */
    function set(next: ReportCaseDetail): void {
        if (openId.value !== next.case.id) return;
        ticket++;
        detail.value = next;
        loading.value = false;
        error.value = null;
    }

    function close(): void {
        ticket++;
        openId.value = null;
        detail.value = null;
        error.value = null;
        loading.value = false;
    }

    return { openId, detail, loading, error, open, reload, set, close };
}
