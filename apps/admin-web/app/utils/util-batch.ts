/** cron(6칸·5칸)을 사람 말로 옮기기 위한 모양 — 글은 `util-batch-messages.ts` 가 붙인다. 읽을 수 없는 식은 null */
export type CronShape =
    | { kind: 'daily'; hh: string; mm: string }
    | { kind: 'weekly'; hh: string; mm: string; dow: number }
    | { kind: 'monthly'; hh: string; mm: string; day: number }
    | { kind: 'hourly'; mm: string }
    | { kind: 'everyMinutes'; n: number };

const DOW_NAMES = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];

/** 배치 화면(배치 관리·배치 로그)이 함께 쓰는 순수 규칙 — 실행 결과 딱지의 색과 아이콘, cron 읽기. 문구는 `util-batch-messages.ts`. */
export default class UtilBatch {
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
