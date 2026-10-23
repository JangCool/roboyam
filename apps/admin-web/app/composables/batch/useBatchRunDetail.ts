import type { ApiError } from '~/composables/common/useApiClient';
import type { BatchJobView, BatchRunDetailView, BatchRunFailureView, BatchRunRequestView } from '~/composables/batch/api/useBatchApi.types';
import type { BatchRunAction } from '~/composables/batch/useBatchRunAction';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

/** 실패 건 거르기 — 미해결(기본) · 해결 · 전체 */
export type BatchFailFilter = 'UNRESOLVED' | 'RESOLVED' | 'ALL';

const FAIL_PAGE_SIZE = 10;

/**
 * 실행 상세 서랍(`BatchRunDetailDrawer`)의 상태·서버 호출·다시 읽기 — 서랍을 붙이는 화면(배치 관리·배치 로그)이 하나 만들어 서랍에 `ctl` 로 넘긴다
 * (부품은 서버를 부르지 않는다 — `components.md`).
 *   - 열린 실행은 주소 쿼리 `?run=<logId>` 와 맞춘다(바꿀 때 `replace`) — 새로 고침·링크 공유 때 다시 열린다. 쿼리가 없어지면 닫힌다.
 *   - `show(id)` 목록에서 연다(되돌아갈 자취를 비운다) · `follow(id)` 서랍 안 링크(이어진 실행·해결한 실행·새 실행)로 내용만 바꾼다(`back()` 으로 앞 실행에) · `close()`.
 *   - 다시 읽기: 따라가는 요청이 끝나지 않았으면 그 요청을, 이 실행이 `RUNNING` 이면 상세를 3초마다 다시 읽는다. 닫거나 화면을 떠나면 멈춘다.
 *   - 「지금 실행」·「파라미터 지정 실행」은 화면의 `useBatchRunAction` 을 그대로 쓴다(목록과 같은 확인·요청·잠금). 받아들여진 요청은 화면이 `track(request)` 로 넘겨 준다.
 *   - 「실패 지점부터 다시」·「실패 건만 다시」는 여기서 묻고 요청한다 → `run.accept`(같은 토스트·같은 알림).
 * 돌려주는 값은 `reactive` 다.
 */
export function useBatchRunDetail(run: BatchRunAction) {
    const t = useTranslate();
    const api = useBatchApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();
    const { confirm } = useConfirmDialog();
    const route = useRoute();
    const router = useRouter();

    const logId = ref<string | null>(null);
    /** 서랍 안 링크로 옮겨 오기 전의 실행들(뒤로) */
    const trail = ref<string[]>([]);
    const detail = ref<BatchRunDetailView | null>(null);
    const loading = ref(false);
    const loadError = ref<ApiError | null>(null);
    /** 그 배치의 목록 한 줄 — 파라미터 라벨·체크포인트 재시작 지원·지금 실행 잠금에 쓴다. 못 받아도 상세는 보인다 */
    const job = ref<BatchJobView | null>(null);
    /** 위쪽 띠가 따라가는 요청 — 이 서랍에서 넣었거나, 열 때 이 실행에 대기·진행 중이던 것 */
    const request = ref<BatchRunRequestView | null>(null);
    const acting = ref<'resume' | 'retry' | null>(null);
    const now = ref(Date.now());

    const failures = ref<BatchRunFailureView[]>([]);
    const failTotal = ref(0);
    const failFirst = ref(0);
    const failBusy = ref(false);
    const failError = ref<ApiError | null>(null);
    const failFilter = ref<BatchFailFilter>('UNRESOLVED');

    let pollTimer: ReturnType<typeof setTimeout> | null = null;
    let disposed = false;

    const visible = computed(() => logId.value !== null);

    // ── 주소 쿼리와 맞추기 ──
    function queryRun(): string | null {
        const value = route.query.run;
        return typeof value === 'string' && value ? value : null;
    }
    watch(
        () => route.query.run,
        () => {
            const id = queryRun();
            if (id === logId.value) return;
            if (id) show(id);
            else close();
        },
        { immediate: true },
    );
    watch(logId, (id) => {
        if (disposed || queryRun() === id) return;
        void router.replace({ path: route.path, query: { ...route.query, run: id ?? undefined } });
    });

    onBeforeUnmount(() => {
        disposed = true;
        stopPolling();
    });

    // ── 열고 닫기 ──
    function reset(): void {
        stopPolling();
        detail.value = null;
        loadError.value = null;
        request.value = null;
        failures.value = [];
        failTotal.value = 0;
        failFirst.value = 0;
        failError.value = null;
        failFilter.value = 'UNRESOLVED';
    }

    function openRun(id: string): void {
        logId.value = id;
        reset();
        void load();
    }

    function show(id: string): void {
        trail.value = [];
        if (id === logId.value) return;
        openRun(id);
    }

    function follow(id: string): void {
        if (id === logId.value) return;
        if (logId.value) trail.value = [...trail.value, logId.value];
        openRun(id);
    }

    function back(): void {
        const prev = trail.value[trail.value.length - 1];
        if (!prev) return;
        trail.value = trail.value.slice(0, -1);
        openRun(prev);
    }

    function close(): void {
        logId.value = null;
        trail.value = [];
        reset();
    }

    // ── 읽기 ──
    /** 상세를 받는다. `silent` 는 자동 다시 읽기 — 덮개를 띄우지 않고, 실패해도 지금 내용을 둔다. 실행이 방금 끝났으면 실패 건과 배치 한 줄도 다시 받는다 */
    async function load(silent = false): Promise<void> {
        const id = logId.value;
        if (!id) return;
        if (!silent) {
            loading.value = true;
            loadError.value = null;
        }
        try {
            const wasRunning = detail.value?.status === 'RUNNING';
            const next = await api.loadLog(id);
            if (logId.value !== id) return;
            const justFinished = wasRunning && next.status !== 'RUNNING';
            detail.value = next;
            if (!request.value && next.pendingRequest) request.value = next.pendingRequest;
            if (job.value?.jobName !== next.jobName || justFinished) void loadJob(next.jobName);
            if (!silent || justFinished) void loadFailures();
        } catch (e) {
            if (!silent && logId.value === id) {
                loadError.value = errorOf(e);
                detail.value = null;
            }
        } finally {
            if (!silent && logId.value === id) loading.value = false;
            now.value = Date.now();
            schedulePoll();
        }
    }

    async function loadJob(jobName: string): Promise<void> {
        try {
            const found = (await api.loadJobs()).find((j) => j.jobName === jobName) ?? null;
            if (detail.value?.jobName === jobName) job.value = found;
        } catch {
            // 라벨·지원 여부·지금 실행 잠금만 못 보여 줄 뿐이다
        }
    }

    async function loadFailures(): Promise<void> {
        const d = detail.value;
        if (!d || d.source === 'POSTGRESQL') return;
        failBusy.value = true;
        failError.value = null;
        try {
            const page = await api.loadFailures(d.logId, {
                resolved: failFilter.value === 'ALL' ? undefined : failFilter.value === 'RESOLVED',
                page: Math.floor(failFirst.value / FAIL_PAGE_SIZE),
                size: FAIL_PAGE_SIZE,
            });
            if (detail.value?.logId !== d.logId) return;
            failures.value = page.items;
            failTotal.value = page.total;
        } catch (e) {
            failError.value = errorOf(e);
            failures.value = [];
            failTotal.value = 0;
        } finally {
            failBusy.value = false;
        }
    }

    function setFailFilter(value: BatchFailFilter): void {
        failFilter.value = value;
        failFirst.value = 0;
        void loadFailures();
    }

    function setFailPage(first: number): void {
        failFirst.value = first;
        void loadFailures();
    }

    // ── 다시 읽기 ──
    function schedulePoll(): void {
        stopPolling();
        if (disposed || !logId.value) return;
        if (!UtilBatch.isRequestActive(request.value) && detail.value?.status !== 'RUNNING') return;
        pollTimer = setTimeout(() => {
            pollTimer = null;
            void tick();
        }, UtilBatch.POLL_MS);
    }

    async function tick(): Promise<void> {
        const tracked = request.value;
        if (tracked && UtilBatch.isRequestActive(tracked)) {
            try {
                const next = await api.loadRequest(tracked.requestId);
                if (disposed || request.value?.requestId !== tracked.requestId) return;
                request.value = next;
                if (!UtilBatch.isRequestActive(next)) {
                    // 끝났다 — 원래 실행의 해결 건수·이어 받은 실행·배치의 대기 표시가 바뀌었으니 상세·실패 건·배치 한 줄을 다시 받는다
                    const line = UtilBatchMessages.requestLine(t, next);
                    if (next.status === 'DONE') toast.success(line, 4000);
                    else if (next.status === 'REJECTED') toast.error(next.message ? `${line} — ${next.message}` : line, 8000);
                    if (detail.value) void loadJob(detail.value.jobName);
                    await load(true);
                    void loadFailures();
                    return;
                }
            } catch {
                // 다음 차례에 다시 읽는다
            }
        }
        if (detail.value?.status === 'RUNNING') {
            await load(true);
            return;
        }
        now.value = Date.now();
        schedulePoll();
    }

    function stopPolling(): void {
        if (pollTimer) clearTimeout(pollTimer);
        pollTimer = null;
    }

    // ── 요청 ──
    /** 화면이 받아들여진 요청을 넘겨 준다 — 열린 실행의 배치에 대한 것이면 위쪽 띠가 따라간다(아니면 무시) */
    function track(next: BatchRunRequestView): void {
        const d = detail.value;
        if (!d || next.jobName !== d.jobName) return;
        request.value = next;
        if (job.value?.jobName === next.jobName) job.value = { ...job.value, pendingRequest: next };
        if (next.kind !== 'NOW') detail.value = { ...d, pendingRequest: next };
        now.value = Date.now();
        schedulePoll();
    }

    /** 다시 읽기(머리의 「다시 조회」·오류의 「다시 시도」·낡은 상태) */
    function refresh(): void {
        if (detail.value) void loadJob(detail.value.jobName);
        void load();
    }

    function runNow(): void {
        if (job.value) void run.runNow(job.value);
    }

    function runOptions(): void {
        if (job.value) run.openOptions(job.value);
    }

    /** 다시 실행이 거절당했을 때 — 서버 문구 그대로. 상태가 낡았으면(이미 실행 중·이미 이어 받음·이미 해결) 다시 읽는다 */
    async function onRerunError(e: unknown): Promise<void> {
        const error = errorOf(e);
        toast.error(error.message, 8000);
        if (['BATCH_RUNNING', 'BATCH_NOT_RESUMABLE', 'BATCH_NO_FAILURES', 'BATCH_LOG_NOT_FOUND'].includes(error.code)) {
            await load(true);
            void loadFailures();
        }
    }

    async function resume(): Promise<void> {
        const d = detail.value;
        if (!d || UtilBatchMessages.resumeLockReason(t, d, run.canRun)) return;
        const ok = await confirm({
            title: t('batch.resumeConfirmTitle'),
            message: t('batch.resumeConfirmMessage', { name: d.displayName, id: d.logId, checkpoint: d.checkpoint ?? '—' }),
            confirmLabel: t('batch.rerunConfirmLabel'),
        });
        if (!ok) return;
        acting.value = 'resume';
        try {
            run.accept(d.displayName, await api.resume(d.logId));
        } catch (e) {
            await onRerunError(e);
        } finally {
            acting.value = null;
        }
    }

    async function retry(): Promise<void> {
        const d = detail.value;
        if (!d || UtilBatchMessages.retryLockReason(t, d, run.canRun)) return;
        const ok = await confirm({
            title: t('batch.retryConfirmTitle'),
            message: t('batch.retryConfirmMessage', { name: d.displayName, id: d.logId, n: d.unresolvedFailureCount }),
            confirmLabel: t('batch.rerunConfirmLabel'),
        });
        if (!ok) return;
        acting.value = 'retry';
        try {
            run.accept(d.displayName, await api.retryFailures(d.logId));
        } catch (e) {
            await onRerunError(e);
        } finally {
            acting.value = null;
        }
    }

    return reactive({
        run,
        logId,
        trail,
        visible,
        detail,
        loading,
        loadError,
        job,
        request,
        acting,
        now,
        failures,
        failTotal,
        failFirst,
        failSize: FAIL_PAGE_SIZE,
        failBusy,
        failError,
        failFilter,
        show,
        follow,
        back,
        close,
        refresh,
        loadFailures,
        setFailFilter,
        setFailPage,
        track,
        runNow,
        runOptions,
        resume,
        retry,
    });
}

export type BatchRunDetail = ReturnType<typeof useBatchRunDetail>;
