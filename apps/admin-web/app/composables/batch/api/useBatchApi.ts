import type { BatchJobView, BatchLogPage, BatchLogQuery, BatchScheduleRequest, BatchToggleRequest } from '~/composables/batch/api/useBatchApi.types';

/**
 * 배치 API — 서버 주소 문자열(`/api/admin/batch-…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `BatchController`, `docs/features/batch-contract.md`): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET  /api/admin/batch-jobs` → 200 `BatchJobView[]` (`batch.read`, 카탈로그 순서. 이름·설명·마지막 실행 요약은 서버가 요청 언어로 풀어 준다)
 *   - `PUT  /api/admin/batch-jobs/{jobName}` `{ enabled }` → 200 **바뀐 목록 전체** (`batch.manage`)
 *   - `PUT  /api/admin/batch-jobs/{jobName}/schedule` `{ schedule }` → 200 **바뀐 목록 전체** (`batch.manage`). 문법은 배치의 `source` 가 정한다 —
 *       `JAVA` Spring 6칸(초 분 시 일 월 요일) · `POSTGRESQL` pg_cron 5칸(분 시 일 월 요일). 틀린 식은 400 `VALIDATION` `fields.schedule`(서버 문구가 어느 문법인지 알려 준다)
 *   - `POST /api/admin/batch-jobs/{jobName}/run` → 200 **바뀐 목록 전체** (`batch.run`). 끝날 때까지 기다린다. 꺼져 있어도 돈다.
 *       일이 실패해도 200 이고 그 배치의 `lastStatus` 가 `FAIL` 이다. 이미 실행 중이면 409 `BATCH_RUNNING`
 *   - `GET  /api/admin/batch-logs?jobName&status&trigger&from&to&page&size` → 200 `{ items, total, page(0부터), size }` (`batch.read`, 시작 시각 최근순, 쪽 크기 1~100)
 * 목록은 Java 배치와 DB 배치(`source: 'POSTGRESQL'`, pg_cron)가 한 목록이다. DB 배치는 pg_cron 이 없거나 미등록이면 `available=false` 이고, 그때 켜기/끄기·주기 변경·지금 실행은 409 `BATCH_UNAVAILABLE`.
 * 오류 코드: 404 `BATCH_NOT_FOUND` · 409 `BATCH_RUNNING`(이미 실행 중) · 409 `BATCH_UNAVAILABLE`(DB 배치를 지금 쓸 수 없음) · 400 `VALIDATION`(주기 — `fields.schedule` / 로그 조건 — `fields` 가 없을 수 있다. 기간이 거꾸로면 `fields.to`) · 403 `PERMISSION_DENIED`.
 *
 * 서버에 있으나 화면이 아직 안 쓰는 것: `GET /batch-logs/{logId}`(상세). 로그 조건 `trigger`·`from`·`to` 는 이 API 가 보낼 수 있으나 화면에 입력칸이 없다.
 */
const PATH = {
    jobs: '/api/admin/batch-jobs',
    job: (jobName: string) => `${PATH.jobs}/${encodeURIComponent(jobName)}`,
    schedule: (jobName: string) => `${PATH.job(jobName)}/schedule`,
    run: (jobName: string) => `${PATH.job(jobName)}/run`,
    logs: '/api/admin/batch-logs',
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

        /** 지금 실행한다. 끝날 때까지 기다리고, 바뀐 목록을 돌려받는다 */
        runNow: (jobName: string): Promise<BatchJobView[]> => client.post<BatchJobView[]>(PATH.run(jobName)),
    };
}
