import type { BatchJobView, BatchRunRequest, BatchRunRequestView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatchMessages from '~/utils/util-batch-messages';

export interface BatchRunActionOptions {
    /** 요청이 받아들여졌을 때(지금 실행·파라미터 지정 실행·다시 실행 모두) — 화면이 목록·서랍의 대기 표시를 붙인다 */
    onRequested?: (request: BatchRunRequestView) => void;
    /** 서버가 화면 상태가 낡았다고 알렸을 때(409 `BATCH_RUNNING`·`BATCH_UNAVAILABLE`, 404 `BATCH_NOT_FOUND`) — 화면이 다시 읽는다 */
    onStale?: () => void | Promise<void>;
}

/** 낡은 상태 때문에 거절된 코드 — 다시 읽으면 바른 표시가 된다 */
const STALE_CODES = new Set(['BATCH_RUNNING', 'BATCH_UNAVAILABLE', 'BATCH_NOT_FOUND']);

/**
 * 「지금 실행」·「파라미터 지정 실행」 — 배치 관리 목록과 실행 상세 서랍이 **같이 쓰는** 확인 대화상자·요청·잠금 규칙(요청 표 방식, `docs/domain/commerce.md` 15-2).
 * 화면마다 하나 만들고, 파라미터 대화상자(`BatchRunDialog`)는 그 화면이 한 번만 그린다(`dialog` 를 그대로 묶는다).
 *   - `runNow(job)`: 확인을 묻고 요청을 넣는다 → 토스트 「요청했습니다 · 요청 #번호」 → `onRequested`. 거절되면 서버 문구 토스트, 상태가 낡았으면 `onStale`.
 *   - `openOptions(job)` · `submitOptions(body)`: 대화상자. 400 `fields.params.<이름>`·`fields.steps` 는 대화상자 칸 아래에 서버 문구 그대로.
 *   - `accept(name, request)`: 다른 요청(다시 실행)이 받아들여졌을 때 같은 토스트·알림을 쓰려고 연다.
 *   - `lockReason(job)`: 지금 실행이 잠긴 이유(권한 → 사용 불가 → 대기·실행 중). 잠기지 않았으면 undefined.
 * 돌려주는 값은 `reactive` 다 — 템플릿에서 `run.requesting` 처럼 `.value` 없이 읽는다.
 */
export function useBatchRunAction(options: BatchRunActionOptions = {}) {
    const t = useTranslate();
    const api = useBatchApi();
    const toast = useAppToast();
    const errorOf = useApiErrorOf();
    const { confirm } = useConfirmDialog();
    const { can } = useAdminPermission();

    const canRun = computed(() => can('batch.run'));
    /** 요청을 넣는 중인 배치 키(서버가 202 를 줄 때까지) */
    const requesting = ref<string | null>(null);
    /** 파라미터 지정 실행 대화상자 — 열 때의 배치 한 줄을 붙들고, 서버 호출 결과(칸별 오류 포함)를 대화상자 안에 보인다 */
    const dialog = reactive({
        visible: false,
        job: null as BatchJobView | null,
        busy: false,
        error: '',
        fieldErrors: {} as Record<string, string>,
    });

    function lockReason(job: BatchJobView | null | undefined): string | undefined {
        if (!canRun.value) return t('batch.noRunReason');
        if (!job) return undefined;
        if (!job.available || !job.runnable) return job.unavailableReason || t('batch.unavailable');
        if (job.running || job.pendingRequest) return t('batch.runBusyReason');
        return undefined;
    }

    function accept(name: string, request: BatchRunRequestView): void {
        toast.success(`${name} — ${t('batch.requested', { id: request.requestId })}`, 4000);
        options.onRequested?.(request);
    }

    async function runNow(job: BatchJobView): Promise<void> {
        if (lockReason(job)) return;
        // 되돌릴 수 없는 처리(예: 회원 등급 승급)를 돌리므로 한 번 더 묻는다
        const ok = await confirm({
            title: t('batch.runConfirmTitle'),
            message: t('batch.runConfirmMessage', { name: job.displayName }),
            confirmLabel: t('batch.runConfirmLabel'),
        });
        if (!ok) return;
        requesting.value = job.jobName;
        try {
            accept(job.displayName, await api.runNow(job.jobName));
        } catch (e) {
            const error = errorOf(e);
            toast.error(error.message, 8000);
            if (STALE_CODES.has(error.code)) await options.onStale?.();
        } finally {
            requesting.value = null;
        }
    }

    function openOptions(job: BatchJobView): void {
        if (lockReason(job)) return;
        dialog.job = job;
        dialog.error = '';
        dialog.fieldErrors = {};
        dialog.visible = true;
    }

    async function submitOptions(body: BatchRunRequest): Promise<void> {
        const job = dialog.job;
        if (!job) return;
        dialog.busy = true;
        dialog.error = '';
        dialog.fieldErrors = {};
        requesting.value = job.jobName;
        try {
            const request = await api.runNow(job.jobName, body);
            dialog.visible = false;
            accept(job.displayName, request);
        } catch (e) {
            const error = errorOf(e);
            // 400 은 칸(`params.<이름>`)·단계 목록(`steps`) 아래에. 칸에 붙지 않는 오류는 대화상자 안에, 상태가 낡았으면 뒤에서 다시 읽는다
            dialog.fieldErrors = error.fields ?? {};
            dialog.error = error.fields && Object.keys(error.fields).length ? '' : error.message;
            if (STALE_CODES.has(error.code)) await options.onStale?.();
        } finally {
            dialog.busy = false;
            requesting.value = null;
        }
    }

    /** 실행이 끝났을 때의 결과 토스트(성공/실패/할 일 없음 + 서버 요약) — 목록이 「바빴다가 한가해진」 배치를 알릴 때 쓴다 */
    function announceResult(job: BatchJobView): void {
        const message = UtilBatchMessages.runResult(t, job);
        if (job.lastStatus === 'FAIL') toast.error(message, 10000);
        else toast.success(message, 5000);
    }

    return reactive({ canRun, requesting, dialog, lockReason, accept, runNow, openOptions, submitOptions, announceResult });
}

export type BatchRunAction = ReturnType<typeof useBatchRunAction>;
