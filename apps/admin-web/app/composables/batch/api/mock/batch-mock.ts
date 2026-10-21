// MOCK: 배치 API(`GET /api/admin/batch-jobs`·`batch-logs`, `PUT …/{jobName}`, `POST …/{jobName}/run`)의 메모리 목업 — `useBatchApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `batch/api/mock/` 와 함께 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   batch-error         배치 목록을 읽는 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도 · 배치 로그 화면의 배치 고르개는 비어 있게 둔다)
//   batch-empty         도는 배치가 하나도 없는 상태(목록 화면의 빈 상태)
//   batch-toggle-error  켜고 끄는 요청이 `500 INTERNAL` 로 실패
//   batch-run-error     "지금 실행" 요청이 `500 INTERNAL` 로 실패
//   batch-run-fail      "지금 실행"이 성공으로 돌려받되 그 배치가 `FAIL` 로 끝난다(실패 토스트 확인용)
//   batch-logs-error    실행 이력을 읽는 요청이 `500 INTERNAL` 로 실패
//   batch-logs-empty    실행 이력이 하나도 없는 상태(거른 결과가 없을 때의 빈 상태는 거르기로 확인한다)
//
// ── 흉내 ─
//   자바 배치 셋 + pg_cron 배치 둘. 이름 문구(`i18n`)가 있는 것(우리가 넣은 배치)과 없는 것(고객사가 만든 배치 — 적힌 글자가 그대로 나온다)이 섞여 있다.
//   실행 이력은 최근 한 달치를 만들어 둔다(약 150건). 켜고 끄기·지금 실행은 메모리에만 남아 새로고침하면 처음으로 돌아간다.
//   pg_cron 배치는 켜고 끄기·지금 실행이 안 된다(`runnable: false`, 화면이 스위치를 잠근다).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BatchJobView, BatchLogPage, BatchLogQuery, BatchLogView, BatchStatus, BatchToggleRequest } from '~/composables/batch/api/useBatchApi.types';

const MINUTE = 60_000;
const HOUR = 3_600_000;
const DAY = 86_400_000;

/** 화면이 쓰는 칸 + 목업이 이력을 만들 때 쓰는 칸(실행 한 번의 길이 범위, ms) */
interface MockJob extends BatchJobView {
    duration: [number, number];
}

const JOBS: MockJob[] = [
    {
        jobName: 'audit-retention',
        displayName: '감사 로그 정리',
        description: '보관 기간이 지난 감사 로그를 조각째 치운다.',
        i18n: 'batch.auditRetention',
        descI18n: 'batch.auditRetentionDesc',
        source: 'JAVA',
        schedule: '0 0 3 * * *',
        enabled: true,
        lastRunAt: null,
        lastStatus: null,
        lastMessage: null,
        runnable: true,
        duration: [800, 4200],
    },
    {
        jobName: 'account-expiry',
        displayName: '계정 기간 만료',
        description: '사용 기간이 지난 계정에 만료 표시를 세운다. 막는 것은 로그인이 직접 한다.',
        i18n: 'batch.accountExpiry',
        descI18n: 'batch.accountExpiryDesc',
        source: 'JAVA',
        schedule: '0 10 0 * * *',
        enabled: true,
        lastRunAt: null,
        lastStatus: null,
        lastMessage: null,
        runnable: true,
        duration: [60, 400],
    },
    {
        // 고객사가 만든 배치 — 문구 열쇠가 없어 적힌 글자가 그대로 나온다
        jobName: 'verification-token-cleanup',
        displayName: '이메일 인증 토큰 정리',
        description: '만료된 이메일 인증 토큰을 지운다.',
        i18n: null,
        descI18n: null,
        source: 'JAVA',
        schedule: '0 */30 * * * *',
        enabled: false,
        lastRunAt: null,
        lastStatus: null,
        lastMessage: null,
        runnable: true,
        duration: [20, 220],
    },
    {
        jobName: 'partition-maintenance',
        displayName: 'partition-maintenance',
        description: 'SELECT maintenance.rotate_partitions();',
        i18n: null,
        descI18n: null,
        source: 'POSTGRESQL',
        schedule: '0 1 * * *',
        enabled: true,
        lastRunAt: null,
        lastStatus: null,
        lastMessage: null,
        runnable: false,
        duration: [1200, 9000],
    },
    {
        jobName: 'weekly-vacuum-analyze',
        displayName: 'weekly-vacuum-analyze',
        description: 'VACUUM (ANALYZE);',
        i18n: null,
        descI18n: null,
        source: 'POSTGRESQL',
        schedule: '0 4 * * 0',
        enabled: true,
        lastRunAt: null,
        lastStatus: null,
        lastMessage: null,
        runnable: false,
        duration: [4000, 30000],
    },
];

/** 같은 입력에 같은 값을 주는 작은 난수 — 새로고침해도 이력이 같게 보이도록 */
function noise(seed: number): number {
    const x = Math.sin(seed * 12.9898) * 43758.5453;
    return x - Math.floor(x);
}

/** 이 배치의 이력 한 건을 만든다 */
function makeLog(job: MockJob, startedMs: number, seed: number): BatchLogView {
    const [lo, hi] = job.duration;
    const took = Math.round(lo + noise(seed + 1) * (hi - lo));
    let status: BatchStatus = 'OK';
    let affectedCount: number | null = null;
    let message: string | null = null;

    if (job.source === 'JAVA') {
        affectedCount = job.jobName === 'account-expiry' && noise(seed + 2) < 0.7 ? 0 : Math.floor(noise(seed + 3) * 240);
        if (affectedCount === 0) {
            status = 'SKIP';
            message = '처리할 대상이 없습니다.';
        } else if (job.jobName === 'audit-retention') {
            message = `${affectedCount}건을 지웠습니다.`;
        } else if (job.jobName === 'account-expiry') {
            message = `${affectedCount}개 계정에 만료 표시를 세웠습니다.`;
        }
    }
    if (job.jobName === 'partition-maintenance' && noise(seed + 4) < 0.14) {
        status = 'FAIL';
        message = 'ERROR: permission denied for schema maintenance';
    }
    if (job.jobName === 'audit-retention' && noise(seed + 5) < 0.05) {
        status = 'FAIL';
        affectedCount = 0;
        message = '데이터베이스 연결 시간이 초과됐습니다.';
    }
    return {
        jobName: job.jobName,
        displayName: job.displayName,
        source: job.source,
        startedAt: new Date(startedMs).toISOString(),
        finishedAt: new Date(startedMs + took).toISOString(),
        status,
        affectedCount,
        message,
    };
}

/** 매일 도는 배치가 도는 시각(UTC 시·분) */
const DAILY_AT: Record<string, [number, number]> = {
    'audit-retention': [3, 0],
    'account-expiry': [0, 10],
    'partition-maintenance': [1, 0],
};

/** 지난 한 달의 이력 — 시작 시각 역순 */
function buildLogs(now: number): BatchLogView[] {
    const out: BatchLogView[] = [];
    const startOfToday = new Date(now);
    startOfToday.setUTCHours(0, 0, 0, 0);
    let seed = 1;

    for (const job of JOBS) {
        if (job.jobName === 'verification-token-cleanup') {
            // 30분마다 — 꺼져 있는 배치라 오늘 0시 전까지만 돌았다
            for (let i = 1; i <= 48; i++) out.push(makeLog(job, startOfToday.getTime() - i * 30 * MINUTE, seed++));
            continue;
        }
        if (job.jobName === 'weekly-vacuum-analyze') {
            for (let w = 0; w < 5; w++) {
                const d = new Date(startOfToday.getTime() - w * 7 * DAY);
                d.setUTCDate(d.getUTCDate() - d.getUTCDay()); // 그 주 일요일
                const started = d.getTime() + 4 * HOUR;
                if (started < now) out.push(makeLog(job, started, seed++));
            }
            continue;
        }
        const [hour, minute] = DAILY_AT[job.jobName] ?? [0, 0];
        for (let day = 0; day < 30; day++) {
            const started = startOfToday.getTime() - day * DAY + hour * HOUR + minute * MINUTE;
            if (started < now) out.push(makeLog(job, started, seed++));
        }
    }
    return out.sort((a, b) => b.startedAt.localeCompare(a.startedAt));
}

let logs: BatchLogView[] = buildLogs(Date.now());

/** 목록의 마지막 실행 칸은 이력에서 센다 */
function snapshot(): BatchJobView[] {
    return JOBS.map((job) => {
        const { duration, ...view } = job;
        void duration;
        const last = logs.find((log) => log.jobName === job.jobName);
        return { ...view, lastRunAt: last?.startedAt ?? null, lastStatus: last?.status ?? null, lastMessage: last?.message ?? null };
    });
}

function find(jobName: string): MockJob {
    const job = JOBS.find((j) => j.jobName === jobName);
    if (!job) throw new ApiError({ status: 404, code: 'NOT_FOUND', message: '배치를 찾을 수 없어요.' });
    return job;
}

/** `GET /api/admin/batch-jobs` */
export async function mockBatchJobs(): Promise<BatchJobView[]> {
    await mockDelay();
    if (mockFlags().has('batch-error')) throw MOCK_ERRORS.internal();
    if (mockFlags().has('batch-empty')) return [];
    return snapshot();
}

/** `GET /api/admin/batch-logs` */
export async function mockBatchLogs(query: BatchLogQuery): Promise<BatchLogPage> {
    await mockDelay();
    if (mockFlags().has('batch-logs-error')) throw MOCK_ERRORS.internal();
    const all = mockFlags().has('batch-logs-empty') ? [] : logs;
    const filtered = all.filter((log) => (!query.jobName || log.jobName === query.jobName) && (!query.status || log.status === query.status));
    const size = query.size && query.size > 0 ? query.size : 20;
    const page = query.page && query.page > 0 ? query.page : 0;
    return { items: filtered.slice(page * size, page * size + size), total: filtered.length, page, size };
}

/** `PUT /api/admin/batch-jobs/{jobName}` — 바뀐 목록 전체를 돌려준다 */
export async function mockBatchToggle(jobName: string, body: BatchToggleRequest): Promise<BatchJobView[]> {
    await mockDelay();
    if (mockFlags().has('batch-toggle-error')) throw MOCK_ERRORS.internal();
    const job = find(jobName);
    if (job.source !== 'JAVA') throw new ApiError({ status: 409, code: 'NOT_CONTROLLABLE', message: 'DB 가 돌리는 배치는 여기서 켜고 끌 수 없어요.' });
    job.enabled = body.enabled;
    return snapshot();
}

/** `POST /api/admin/batch-jobs/{jobName}/run` — 꺼져 있어도 돈다. 이력에 한 건을 더하고 바뀐 목록 전체를 돌려준다 */
export async function mockBatchRun(jobName: string): Promise<BatchJobView[]> {
    await mockDelay();
    if (mockFlags().has('batch-run-error')) throw MOCK_ERRORS.internal();
    const job = find(jobName);
    if (!job.runnable) throw new ApiError({ status: 409, code: 'NOT_RUNNABLE', message: 'DB 가 돌리는 배치는 여기서 실행할 수 없어요.' });
    const log = makeLog(job, Date.now(), Math.floor(Date.now() / 1000));
    if (mockFlags().has('batch-run-fail')) {
        log.status = 'FAIL';
        log.message = '데이터베이스 연결 시간이 초과됐습니다.';
    }
    logs = [log, ...logs];
    return snapshot();
}
