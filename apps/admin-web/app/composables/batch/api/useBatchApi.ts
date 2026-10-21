import { mockBatchLogs, mockBatchJobs, mockBatchRun, mockBatchToggle } from '~/composables/batch/api/mock/batch-mock';
import type { BatchJobView, BatchLogPage, BatchLogQuery } from '~/composables/batch/api/useBatchApi.types';

/**
 * 배치 API — 서버 주소 문자열(`/api/admin/batch-…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 칸 이름은 hamonica3 의 `BatchController` 와 같다.
 *   - `GET  /api/admin/batch-jobs` → 200 `BatchJobView[]` (자바 스케줄러 + pg_cron 을 한 목록으로)
 *   - `GET  /api/admin/batch-logs?jobName&status&page&size` → 200 `{ items: BatchLogView[], total, page(0부터), size }` (두 곳을 섞어 시작 시각 역순)
 *   - `PUT  /api/admin/batch-jobs/{jobName}` `{ enabled }` → 200 **바뀐 목록 전체** `BatchJobView[]` (pg_cron 은 못 끈다 → 거절 코드 미정)
 *   - `POST /api/admin/batch-jobs/{jobName}/run` → 200 **바뀐 목록 전체**. 끝날 때까지 기다린다(눌렀는데 안 바뀌면 사람은 다시 누른다). 꺼져 있어도 돈다. 실행 결과는 배치 로그에 남는다
 *
 * ⚠ 미정(서버 정책): 보는 사람과 켜고 끄고 실행할 수 있는 사람의 구분(hamonica3 는 `batch.read` / `batch.manage` 권한을 갈랐다. 지금 관리자 앱은 ADMIN·STAFF 모두 들어온다),
 *   로그 보관 기간(hamonica3 는 감사 로그 정리 배치가 따로 있었다), pg_cron 을 이 제품이 쓰는지.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/batch-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    jobs: '/api/admin/batch-jobs',
    job: (jobName: string) => `${PATH.jobs}/${encodeURIComponent(jobName)}`,
    run: (jobName: string) => `${PATH.job(jobName)}/run`,
    logs: '/api/admin/batch-logs',
};

export function useBatchApi() {
    // REAL: const client = useApiClient(); // REAL 로 바꿀 때 BatchToggleRequest 도 import 한다
    void PATH;

    return {
        /** 배치 목록 */
        loadJobs: (): Promise<BatchJobView[]> =>
            // MOCK:
            mockBatchJobs(),
        // REAL: client.get<BatchJobView[]>(PATH.jobs),

        /** 실행 이력 한 쪽 — 빈 값은 보내지 않는다(서버가 "거르지 않음"으로 받는다) */
        loadLogs: (query: BatchLogQuery): Promise<BatchLogPage> =>
            // MOCK:
            mockBatchLogs(query),
        // REAL: client.get<BatchLogPage>(PATH.logs, { query: { jobName: query.jobName || undefined, status: query.status || undefined, page: query.page, size: query.size } }),

        /** 켜고 끈다. 바뀐 목록을 그대로 돌려받는다 — 다시 읽을 필요가 없다 */
        toggle: (jobName: string, enabled: boolean): Promise<BatchJobView[]> =>
            // MOCK:
            mockBatchToggle(jobName, { enabled }),
        // REAL: client.put<BatchJobView[]>(PATH.job(jobName), { enabled } satisfies BatchToggleRequest),

        /** 지금 실행한다. 끝날 때까지 기다리고, 바뀐 목록을 돌려받는다 */
        runNow: (jobName: string): Promise<BatchJobView[]> =>
            // MOCK:
            mockBatchRun(jobName),
        // REAL: client.post<BatchJobView[]>(PATH.run(jobName)),
    };
}

