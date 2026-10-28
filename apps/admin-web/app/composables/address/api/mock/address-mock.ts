// MOCK: 주소 검색 설정 API 목업 — 서버가 붙으면 이 폴더와 `useAddressSettingsApi.ts` 의 `// MOCK:` 줄을 지운다.
// 값은 메모리에만 둔다(새로고침하면 처음으로). 스위치(`?mock=`, 쉼표로 여럿):
//   address-empty        주소 DB 를 한 번도 올린 적이 없다
//   address-stale        마지막 적재가 오래됐다(갱신 권고)
//   address-load-fail    설정을 못 읽는다(500)
//   address-running      화면을 열 때 이미 적재가 돌고 있다
//   address-import-fail  적재가 「도로명 파일 넣기」 중에 실패한다
//   address-dropped      새 파일 건수가 앞 적재보다 크게 줄어 멈춘다

import type {
    AddressDbLoadPage,
    AddressDbLoadRow,
    AddressImportPhase,
    AddressImportProgress,
    AddressImportStarted,
    AddressSettingsSaveRequest,
    AddressSettingsView,
} from '~/composables/address/api/useAddressSettingsApi.types';
import { ApiError } from '~/composables/common/useApiClient';

const DEFAULT_SCRIPT_URL = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
const STALE_MIN = 7;
const STALE_MAX = 365;
const DAY_MS = 86_400_000;
/** 「한글 전체분」 한 벌의 도로명 주소 줄 수(어림) */
const FULL_ROWS = 6_412_337;

function flags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

function delay(ms = 300 + Math.random() * 400): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function clone<T>(value: T): T {
    return JSON.parse(JSON.stringify(value)) as T;
}

function daysAgo(days: number): string {
    return new Date(Date.now() - days * DAY_MS).toISOString();
}

const internal = () => new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' });

/** 메모리 상태 — 처음 읽을 때 스위치를 보고 한 번 채운다 */
let state: { provider: AddressSettingsView['provider']; scriptUrl: string | null; staleWarnDays: number; updatedAt: string | null; updatedBy: string | null; history: AddressDbLoadRow[] } | null = null;

function ensureState() {
    if (state) return state;
    const f = flags();
    const history: AddressDbLoadRow[] = f.has('address-empty')
        ? []
        : Array.from({ length: 7 }, (_, i) => {
              const age = (f.has('address-stale') ? 92 : 12) + i * 31;
              const month = new Date(Date.now() - age * DAY_MS);
              const ym = `${month.getFullYear()}${String(month.getMonth() + 1).padStart(2, '0')}`;
              const english = i % 3 !== 2;
              return {
                  id: 7 - i,
                  loadedAt: daysAgo(age),
                  sourceLabel: english ? `${ym}_도로명주소 한글_전체분.zip, ${ym}_도로명주소 영문_전체분.zip` : `${ym}_도로명주소 한글_전체분.zip`,
                  entryCount: FULL_ROWS - i * 3_120,
                  englishCount: english ? FULL_ROWS - i * 3_120 - 41 : 0,
                  loadedBy: i % 2 === 0 ? '운영자 김민지' : '최고 관리자',
              };
          });
    state = { provider: 'DAUM', scriptUrl: null, staleWarnDays: 60, updatedAt: daysAgo(40), updatedBy: '최고 관리자', history };
    return state;
}

function view(): AddressSettingsView {
    const s = ensureState();
    const last = s.history[0] ?? null;
    const stale = !last || Date.parse(last.loadedAt) < Date.now() - s.staleWarnDays * DAY_MS;
    return clone({
        provider: s.provider,
        scriptUrl: s.scriptUrl,
        defaultScriptUrl: DEFAULT_SCRIPT_URL,
        staleWarnDays: s.staleWarnDays,
        staleWarnDaysMin: STALE_MIN,
        staleWarnDaysMax: STALE_MAX,
        db: {
            loadedAt: last?.loadedAt ?? null,
            sourceLabel: last?.sourceLabel ?? null,
            entryCount: last?.entryCount ?? null,
            englishCount: last?.englishCount ?? null,
            stale,
        },
        updatedAt: s.updatedAt,
        updatedBy: s.updatedBy,
    });
}

export async function mockAddressSettings(): Promise<AddressSettingsView> {
    await delay();
    if (flags().has('address-load-fail')) throw internal();
    return view();
}

export async function mockAddressSaveSettings(body: AddressSettingsSaveRequest): Promise<AddressSettingsView> {
    await delay();
    const fields: Record<string, string> = {};
    const url = body.scriptUrl?.trim() || null;
    if (url && !/^https:\/\/[^\s]+$/.test(url)) fields.scriptUrl = 'https:// 로 시작하는 주소를 입력해 주세요.';
    if (url && url.length > 500) fields.scriptUrl = '주소는 500자까지예요.';
    if (!Number.isInteger(body.staleWarnDays) || body.staleWarnDays < STALE_MIN || body.staleWarnDays > STALE_MAX) {
        fields.staleWarnDays = `${STALE_MIN}~${STALE_MAX}일 사이로 입력해 주세요.`;
    }
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields });
    const s = ensureState();
    s.provider = body.provider;
    s.scriptUrl = url;
    s.staleWarnDays = body.staleWarnDays;
    s.updatedAt = new Date().toISOString();
    s.updatedBy = '나(목업)';
    return view();
}

/* ── 적재 작업 — 시작한 뒤 흐른 시간으로 단계를 정한다 ── */

interface MockJob {
    jobId: string;
    fileNames: string[];
    english: boolean;
    startedAt: number;
    total: number;
    finished: boolean;
}

const jobs = new Map<string, MockJob>();
let runningId: string | null = null;

/** 단계별 끝나는 시각(시작 뒤 ms) */
const PLAN: { phase: AddressImportPhase; until: number }[] = [
    { phase: 'COUNTING', until: 2500 },
    { phase: 'PARSING', until: 14000 },
    { phase: 'MERGING', until: 18000 },
    { phase: 'SWAPPING', until: 19500 },
];

function progressOf(job: MockJob): AddressImportProgress {
    const f = flags();
    const elapsed = Date.now() - job.startedAt;
    const base = { jobId: job.jobId, fileNames: job.fileNames, startedAt: new Date(job.startedAt).toISOString(), startedBy: '나(목업)' };
    const parsingEnd = PLAN[1]!.until;
    const parsingStart = PLAN[0]!.until;

    if (f.has('address-dropped') && elapsed > parsingEnd) {
        finish(job);
        return { ...base, phase: 'FAILED', totalRows: job.total, processedRows: job.total, done: false, failed: true, message: '새 파일의 주소 수(1,204,551건)가 지금 주소 DB(6,409,217건)보다 크게 줄어 적재를 멈췄어요. 파일이 잘렸는지 확인해 주세요. 지금 주소 DB 는 그대로예요.' };
    }
    if (f.has('address-import-fail') && elapsed > parsingStart + 4000) {
        finish(job);
        return { ...base, phase: 'FAILED', totalRows: job.total, processedRows: Math.round(job.total * 0.31), done: false, failed: true, message: 'rnaddrkor_seoul.txt 1,988,204번째 줄의 칸 수가 24개가 아니에요(19개). 「한글 전체분」 파일이 맞는지 확인해 주세요. 지금 주소 DB 는 그대로예요.' };
    }

    const step = PLAN.find((p) => elapsed < p.until);
    if (!step) {
        if (!job.finished) {
            finish(job);
            const s = ensureState();
            s.history.unshift({
                id: (s.history[0]?.id ?? 0) + 1,
                loadedAt: new Date().toISOString(),
                sourceLabel: job.fileNames.join(', '),
                entryCount: job.total,
                englishCount: job.english ? job.total - 37 : 0,
                loadedBy: '나(목업)',
            });
        }
        return { ...base, phase: 'DONE', totalRows: job.total, processedRows: job.total, done: true, failed: false, message: `주소 ${job.total.toLocaleString('ko-KR')}건을 올렸어요.` };
    }
    if (step.phase === 'COUNTING') return { ...base, phase: 'COUNTING', totalRows: -1, processedRows: 0, done: false, failed: false, message: null };
    if (step.phase === 'PARSING') {
        const ratio = (elapsed - parsingStart) / (parsingEnd - parsingStart);
        return { ...base, phase: 'PARSING', totalRows: job.total, processedRows: Math.round(job.total * ratio), done: false, failed: false, message: null };
    }
    return { ...base, phase: step.phase, totalRows: job.total, processedRows: job.total, done: false, failed: false, message: null };
}

function finish(job: MockJob): void {
    job.finished = true;
    if (runningId === job.jobId) runningId = null;
}

function newJob(fileNames: string[], english: boolean, startedAt = Date.now()): MockJob {
    const job: MockJob = { jobId: `job-${Math.random().toString(36).slice(2, 10)}`, fileNames, english, startedAt, total: FULL_ROWS + Math.round(Math.random() * 4000), finished: false };
    jobs.set(job.jobId, job);
    runningId = job.jobId;
    return job;
}

export async function mockAddressStartImport(files: File[]): Promise<AddressImportStarted> {
    // 파일 보내기 — 실제로는 크기만큼 걸린다
    await delay(1200);
    if (runningId) throw new ApiError({ status: 409, code: 'ADDRESS_IMPORT_RUNNING', message: '이미 주소 DB 를 올리고 있어요. 끝난 뒤에 다시 올려 주세요.' });
    const names = files.map((file) => file.name);
    return { jobId: newJob(names, names.length > 1).jobId };
}

export async function mockAddressCurrentImport(): Promise<AddressImportProgress | null> {
    await delay();
    if (!runningId && flags().has('address-running') && jobs.size === 0) {
        newJob(['202610_도로명주소 한글_전체분.zip'], false, Date.now() - 6000);
    }
    const job = runningId ? jobs.get(runningId) : undefined;
    return job ? progressOf(job) : null;
}

export async function mockAddressImportProgress(jobId: string): Promise<AddressImportProgress> {
    await delay(150);
    const job = jobs.get(jobId);
    if (!job) throw new ApiError({ status: 404, code: 'ADDRESS_IMPORT_JOB_NOT_FOUND', message: '적재 작업을 찾지 못했어요. 서버가 다시 시작됐을 수 있어요 — 적재 이력을 확인해 주세요.' });
    return progressOf(job);
}

export async function mockAddressHistory(page: number, size: number): Promise<AddressDbLoadPage> {
    await delay();
    const all = ensureState().history;
    return clone({ items: all.slice(page * size, page * size + size), total: all.length, page, size });
}
