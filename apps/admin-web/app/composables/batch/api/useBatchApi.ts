import type {
    BatchFailurePage,
    BatchFailureQuery,
    BatchJobView,
    BatchLogPage,
    BatchLogQuery,
    BatchRunDetailView,
    BatchRunRequest,
    BatchRunRequestView,
    BatchScheduleRequest,
    BatchToggleRequest,
} from '~/composables/batch/api/useBatchApi.types';

/**
 * 배치 API — 서버 주소 문자열(`/api/admin/batch-…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `BatchController`, `docs/features/batch-contract.md` 「서버 구현 결과」): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET  /api/admin/batch-jobs` → 200 `BatchJobView[]` (`batch.read`, 카탈로그 순서. 이름·설명·마지막 실행 요약·파라미터·단계 이름은 서버가 요청 언어로 풀어 준다)
 *   - `PUT  /api/admin/batch-jobs/{jobName}` `{ enabled }` → 200 **바뀐 목록 전체** (`batch.manage`)
 *   - `PUT  /api/admin/batch-jobs/{jobName}/schedule` `{ schedule }` → 200 **바뀐 목록 전체** (`batch.manage`). 문법은 배치의 `source` 가 정한다 —
 *       `JAVA` Spring 6칸(초 분 시 일 월 요일) · `POSTGRESQL` pg_cron 5칸(분 시 일 월 요일). 틀린 식은 400 `VALIDATION` `fields.schedule`(서버 문구가 어느 문법인지 알려 준다)
 *   - `POST /api/admin/batch-jobs/{jobName}/run` `{ params?, steps? }`(본문 선택) → **202 `BatchRunRequestView`** (`batch.run`). 요청 표에 줄을 넣고 바로 돌아온다 —
 *       배치를 켠 서버(`roboyam.batch.enabled`)가 집어 실행한다(DB 배치는 그 서버가 프로시저를 `CALL` 한다). 꺼져 있어도 돈다.
 *       오류 순서: 404 `BATCH_NOT_FOUND` → 409 `BATCH_UNAVAILABLE` → 409 `BATCH_RUNNING`(실행 중이거나 끝나지 않은 요청이 있음) → 400 `VALIDATION`
 *       (`fields.params.<이름>` · `fields.steps` · 정의에 없는 파라미터 이름은 `fields.params` 한 칸)
 *   - `GET  /api/admin/batch-requests/{requestId}` → 200 `BatchRunRequestView` (`batch.read`) · 404 `BATCH_REQUEST_NOT_FOUND`.
 *       DB 배치의 요청은 실행이 끝날 때까지 `RUNNING` 이고 `runId` 가 없을 수 있다(실행 링크는 끝난 뒤 생긴다)
 *   - `GET  /api/admin/batch-logs?jobName&status&trigger&from&to&page&size` → 200 `{ items, total, page(0부터), size }` (`batch.read`, 시작 시각 최근순, 쪽 크기 1~100)
 *   - `GET  /api/admin/batch-logs/{logId}` → 200 `BatchRunDetailView` (`batch.read`) · 404 `BATCH_LOG_NOT_FOUND`
 *   - `GET  /api/admin/batch-logs/{logId}/failures?resolved&page&size` → 200 `{ items: BatchRunFailureView[], total, page, size }` (`batch.read`)
 *   - `POST /api/admin/batch-logs/{logId}/resume` → 202 `BatchRunRequestView` (`batch.run`) · 409 `BATCH_NOT_RESUMABLE` → 409 `BATCH_RUNNING`
 *   - `POST /api/admin/batch-logs/{logId}/retry-failures` → 202 `BatchRunRequestView` (`batch.run`) · 409 `BATCH_NO_FAILURES` → 409 `BATCH_RUNNING`
 * 목록은 Java 배치와 DB 배치(`source: 'POSTGRESQL'`, pg_cron)가 한 목록이다. DB 배치는 pg_cron 이 없거나 미등록이면 `available=false` 이고, 그때 켜기/끄기·주기 변경·지금 실행은 409 `BATCH_UNAVAILABLE`.
 * 공통 오류: 403 `PERMISSION_DENIED`. 로그 조건이 틀리면 400 `VALIDATION`(`fields` 가 없을 수 있다. 기간이 거꾸로면 `fields.to`).
 * 202 응답도 `useApiClient` 가 성공으로 받아 `data`(요청 줄)만 돌려준다.
 */
const PATH = {
    jobs: '/api/admin/batch-jobs',
    job: (jobName: string) => `${PATH.jobs}/${encodeURIComponent(jobName)}`,
    schedule: (jobName: string) => `${PATH.job(jobName)}/schedule`,
    run: (jobName: string) => `${PATH.job(jobName)}/run`,
    request: (requestId: string) => `/api/admin/batch-requests/${encodeURIComponent(requestId)}`,
    logs: '/api/admin/batch-logs',
    log: (logId: string) => `${PATH.logs}/${encodeURIComponent(logId)}`,
    failures: (logId: string) => `${PATH.log(logId)}/failures`,
    resume: (logId: string) => `${PATH.log(logId)}/resume`,
    retryFailures: (logId: string) => `${PATH.log(logId)}/retry-failures`,
};

export function useBatchApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 배치 목록 */
        loadJobs: (): Promise<BatchJobView[]> => client.get<BatchJobView[]>(PATH.jobs),

        /** 실행 이력 한 쪽 — 빈 값은 보내지 않는다(서버가 "거르지 않음"으로 받는다) */
        loadLogs: (query: BatchLogQuery): Promise<BatchLogPage> =>
            client.get<BatchLogPage>(PATH.logs, {
                query: {
                    jobName: query.jobName || undefined,
                    status: query.status || undefined,
                    trigger: query.trigger || undefined,
                    from: query.from || undefined,
                    to: query.to || undefined,
                    page: query.page,
                    size: query.size,
                },
            }),

        /** 켜고 끈다. 바뀐 목록을 그대로 돌려받는다 — 다시 읽을 필요가 없다 */
        toggle: (jobName: string, enabled: boolean): Promise<BatchJobView[]> => client.put<BatchJobView[]>(PATH.job(jobName), { enabled } satisfies BatchToggleRequest),

        /** 주기(cron)를 바꾼다(재기동 없이 반영). 식은 그대로 보낸다 — 판정은 서버가 한다. 바뀐 목록을 돌려받는다 */
        updateSchedule: (jobName: string, schedule: string): Promise<BatchJobView[]> =>
            client.put<BatchJobView[]>(PATH.schedule(jobName), { schedule } satisfies BatchScheduleRequest),

        /**
         * 지금 실행을 요청한다(파라미터 지정 실행 포함) — 요청 표에 줄을 넣고 바로 돌아온다(202). 결과는 목록(`pendingRequest`)이나 `loadRequest` 로 따라간다.
         * 파라미터·단계를 안 주면 기본(모든 단계)이다.
         */
        runNow: (jobName: string, body: BatchRunRequest = {}): Promise<BatchRunRequestView> => client.post<BatchRunRequestView>(PATH.run(jobName), body),

        /** 요청 한 줄을 다시 읽는다 — 대기 → 실행 중 → 끝남(또는 거절)을 따라간다 */
        loadRequest: (requestId: string): Promise<BatchRunRequestView> => client.get<BatchRunRequestView>(PATH.request(requestId)),

        /** 실행 상세(단계별 진행·건수·체크포인트·다시 실행 가능 여부) */
        loadLog: (logId: string): Promise<BatchRunDetailView> => client.get<BatchRunDetailView>(PATH.log(logId)),

        /** 그 실행의 실패 건 한 쪽 — `resolved` 를 안 주면 전체 */
        loadFailures: (logId: string, query: BatchFailureQuery): Promise<BatchFailurePage> =>
            client.get<BatchFailurePage>(PATH.failures(logId), { query: { resolved: query.resolved, page: query.page, size: query.size } }),

        /** 「실패 지점부터 다시」— 같은 파라미터로 체크포인트 다음부터. 요청 표에 줄을 넣고 바로 돌아온다(202) */
        resume: (logId: string): Promise<BatchRunRequestView> => client.post<BatchRunRequestView>(PATH.resume(logId)),

        /** 「실패 건만 다시」— 그 실행의 미해결 실패 건만. 성공한 건은 해결 표시된다. 요청 표에 줄을 넣고 바로 돌아온다(202) */
        retryFailures: (logId: string): Promise<BatchRunRequestView> => client.post<BatchRunRequestView>(PATH.retryFailures(logId)),
    };
}
