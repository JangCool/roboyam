/** cron(6칸·5칸)을 사람 말로 옮기기 위한 모양 — 글은 `util-batch-messages.ts` 가 붙인다. 읽을 수 없는 식은 null */
export type CronShape =
    | { kind: 'daily'; hh: string; mm: string }
    | { kind: 'weekly'; hh: string; mm: string; dow: number }
    | { kind: 'monthly'; hh: string; mm: string; day: number }
    | { kind: 'hourly'; mm: string }
    | { kind: 'everyMinutes'; n: number };

const DOW_NAMES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

/** 요청이 이만큼 `PENDING` 에 머물면 「배치를 켠 서버가 아직 집지 않았다」고 알린다 */
const PENDING_STUCK_MS = 30_000;

/** 배치 화면(배치 관리·배치 로그·실행 상세)이 함께 쓰는 순수 규칙 — 결과·단계·요청 딱지의 색과 아이콘, cron 읽기, 요청 상태 판정. 문구는 `util-batch-messages.ts`. */
export default class UtilBatch {
    /** 목록·상세가 대기·실행 중인 것을 다시 읽는 간격(ms) */
    static readonly POLL_MS = 3000;

    /**
     * 그 배치가 지금 바쁜가 — 실행 중이거나 아직 끝나지 않은 요청(`PENDING`·`RUNNING`)이 있다. 이 동안 「지금 실행」을 잠근다(서버도 409 `BATCH_RUNNING`).
     * @param {{ running: boolean; pendingRequest: unknown | null }} job - 배치 한 줄.
     * @returns {boolean} 바쁘면 true.
     */
    static isBusy(job: { running: boolean; pendingRequest: unknown | null }): boolean {
        return job.running || job.pendingRequest !== null;
    }

    /**
     * 「파라미터 지정 실행」 단추를 둘 배치인가 — 받는 파라미터가 있거나, 단계가 둘 이상이고 그중 골라 돌릴 수 있는 단계가 있을 때.
     * @param {{ params: unknown[]; steps: { selectable: boolean }[] }} job - 배치 한 줄.
     * @returns {boolean} 둘 배치면 true.
     */
    static hasRunOptions(job: { params: unknown[]; steps: { selectable: boolean }[] }): boolean {
        return job.params.length > 0 || (job.steps.length > 1 && job.steps.some((s) => s.selectable));
    }

    /**
     * 요청이 너무 오래 대기 중인가 — 배치를 켠 서버(`roboyam.batch.enabled`)가 없거나 멈췄을 수 있다.
     * @param {{ status: string; requestedAt: string } | null | undefined} request - 요청 한 줄.
     * @param {number} now - 지금(ms).
     * @returns {boolean} `PENDING` 이 30초를 넘었으면 true.
     */
    static isStuck(request: { status: string; requestedAt: string } | null | undefined, now: number): boolean {
        return request?.status === 'PENDING' && now - new Date(request.requestedAt).getTime() > PENDING_STUCK_MS;
    }

    /**
     * 요청이 아직 끝나지 않았나(`PENDING`·`RUNNING`).
     * @param {{ status: string } | null | undefined} request - 요청 한 줄.
     * @returns {boolean} 끝나지 않았으면 true.
     */
    static isRequestActive(request: { status: string } | null | undefined): boolean {
        return request?.status === 'PENDING' || request?.status === 'RUNNING';
    }

    /**
     * 단계 딱지의 색 — `PENDING`(안 돎)과 `SKIP`(건너뜀)은 회색이다.
     * @param {string | null | undefined} status - 단계 상태(`PENDING` · `RUNNING` · `OK` · `FAIL` · `SKIP`).
     * @returns {'success' | 'danger' | 'info' | 'secondary'} PrimeVue Tag 의 severity.
     */
    static stepSeverity(status: string | null | undefined): 'success' | 'danger' | 'info' | 'secondary' {
        return status === 'PENDING' || status === 'SKIP' ? 'secondary' : UtilBatch.statusSeverity(status);
    }

    /**
     * 단계 상태 아이콘.
     * @param {string | null | undefined} status - 단계 상태.
     * @returns {string} primeicons 클래스.
     */
    static stepIcon(status: string | null | undefined): string {
        if (status === 'PENDING') return 'pi pi-circle';
        if (status === 'SKIP') return 'pi pi-forward';
        return UtilBatch.statusIcon(status) ?? 'pi pi-circle';
    }

    /**
     * 요청 상태 띠의 색.
     * @param {string | null | undefined} status - 요청 상태(`PENDING` · `RUNNING` · `DONE` · `REJECTED`).
     * @returns {'info' | 'warn' | 'success' | 'error' | 'secondary'} PrimeVue Message 의 severity.
     */
    static requestSeverity(status: string | null | undefined): 'info' | 'warn' | 'success' | 'error' | 'secondary' {
        if (status === 'PENDING') return 'warn';
        if (status === 'RUNNING') return 'info';
        if (status === 'DONE') return 'success';
        if (status === 'REJECTED') return 'error';
        return 'secondary';
    }

    /**
     * 다시 실행 종류의 아이콘 — 보통 실행은 없다.
     * @param {string | null | undefined} mode - `NORMAL` · `RESUME` · `RETRY_FAILED`.
     * @returns {string | undefined} primeicons 클래스.
     */
    static runModeIcon(mode: string | null | undefined): string | undefined {
        if (mode === 'RESUME') return 'pi pi-step-forward';
        if (mode === 'RETRY_FAILED') return 'pi pi-replay';
        return undefined;
    }

    /**
     * 결과 딱지의 색.
     * @param {string | null | undefined} status - 실행 결과 코드(`RUNNING` · `OK` · `FAIL` · `SKIP`).
     * @returns {'success' | 'danger' | 'info' | 'secondary'} PrimeVue Tag 의 severity. 모르는 값은 secondary.
     */
    static statusSeverity(status: string | null | undefined): 'success' | 'danger' | 'info' | 'secondary' {
        if (status === 'OK') return 'success';
        if (status === 'FAIL') return 'danger';
        if (status === 'RUNNING') return 'info';
        return 'secondary';
    }

    /**
     * 결과를 짚는 아이콘 — 색만으로는 성공과 「할 일 없음」이 곁눈으로 안 갈리고, 색을 못 가리는 사람에게는 아예 같아 보인다.
     * @param {string | null | undefined} status - 실행 결과 코드.
     * @returns {string | undefined} primeicons 클래스. 모르는 값은 undefined.
     */
    static statusIcon(status: string | null | undefined): string | undefined {
        if (status === 'OK') return 'pi pi-check';
        if (status === 'FAIL') return 'pi pi-times';
        if (status === 'SKIP') return 'pi pi-minus';
        if (status === 'RUNNING') return 'pi pi-spin pi-spinner';
        return undefined;
    }

    /**
     * 실행 주체 딱지의 아이콘 — `JAVA` 는 앱 서버, `POSTGRESQL` 은 DB.
     * @param {string | null | undefined} source - 배치의 `source`.
     * @returns {string | undefined} primeicons 클래스. 모르는 값은 undefined.
     */
    static sourceIcon(source: string | null | undefined): string | undefined {
        if (source === 'JAVA') return 'pi pi-server';
        if (source === 'POSTGRESQL') return 'pi pi-database';
        return undefined;
    }

    /**
     * 주기 입력칸에 보일 예시 식 — 매일 04:30. 문법이 source 마다 달라(JAVA 6칸 · POSTGRESQL 5칸) 예시도 다르다.
     * @param {string | null | undefined} source - 배치의 `source`.
     * @returns {string} 예시 cron.
     */
    static scheduleExample(source: string | null | undefined): string {
        return source === 'POSTGRESQL' ? '30 4 * * *' : '0 30 4 * * *';
    }

    /**
     * 서버의 cron 을 흔한 꼴만 읽는다 — 매일·매주 한 요일·매월 하루·매시·N분마다.
     * 두 문법을 모두 읽는다: Spring 6칸 `초 분 시 일 월 요일`(JAVA, `BatchCronPolicy`)과 pg_cron 5칸 `분 시 일 월 요일`(POSTGRESQL).
     * 그 밖의 식(목록·범위·여러 시각·`$`·`N seconds` 등)은 null 이라 화면이 원문만 보인다. 6칸의 초 칸은 `0` 일 때만 읽는다.
     * @param {string | null | undefined} cron - 6칸 또는 5칸 cron 식.
     * @returns {CronShape | null} 읽은 모양. 못 읽으면 null.
     */
    static parseCron(cron: string | null | undefined): CronShape | null {
        const parts = (cron ?? '').trim().split(/\s+/);
        if (parts.length === 6 && parts[0] !== '0') return null;
        const f = parts.length === 6 ? parts.slice(1) : parts;
        if (f.length !== 5) return null;
        const [min, hour, dom, mon, dow] = f as [string, string, string, string, string];
        if (mon !== '*') return null;

        const isNum = (v: string, max: number): boolean => /^\d{1,2}$/.test(v) && Number(v) <= max;
        const pad = (v: string): string => v.padStart(2, '0');

        // */N * * * *  → N분마다
        const step = /^\*\/(\d{1,2})$/.exec(min);
        if (step && hour === '*' && dom === '*' && dow === '*' && Number(step[1]) >= 1) return { kind: 'everyMinutes', n: Number(step[1]) };

        if (!isNum(min, 59)) return null;
        // M * * * *  → 매시 M분
        if (hour === '*' && dom === '*' && dow === '*') return { kind: 'hourly', mm: pad(min) };

        if (!isNum(hour, 23)) return null;
        if (dom === '*' && dow === '*') return { kind: 'daily', hh: pad(hour), mm: pad(min) };
        if (dom === '*' && dow !== '*') {
            const idx = /^\d$/.test(dow) ? Number(dow) % 7 : DOW_NAMES.indexOf(dow.toUpperCase());
            return idx >= 0 && idx <= 6 ? { kind: 'weekly', hh: pad(hour), mm: pad(min), dow: idx } : null;
        }
        if (dow === '*' && isNum(dom, 31) && Number(dom) >= 1) return { kind: 'monthly', hh: pad(hour), mm: pad(min), day: Number(dom) };
        return null;
    }
}
