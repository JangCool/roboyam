import type { VisitCounts, VisitRange } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

/** 기간 빠른 선택 — 오늘 · 어제 · 최근 7일(오늘 포함) · 최근 30일(오늘 포함) */
export type VisitRangePreset = 'TODAY' | 'YESTERDAY' | 'LAST7' | 'LAST30';

const YMD = /^\d{4}-\d{2}-\d{2}$/;
const YM = /^\d{4}-(0[1-9]|1[0-2])$/;
const PRESET_DAYS: Record<Exclude<VisitRangePreset, 'TODAY' | 'YESTERDAY'>, number> = { LAST7: 7, LAST30: 30 };

/**
 * 방문 분석 화면(`/analytics/visits`)의 기간·숫자 규칙 — 입력만 보고 답하는 순수 함수. 숫자 자체는 서버(집계 표)가 정하고 화면은 비율·증감·기간만 계산한다.
 * 날짜는 한국 날짜(`YYYY-MM-DD`, 서비스 하루 — 집계 표의 `day` 와 같다). 주소 쿼리 `from`·`to` 가 정본이다.
 */
export default class UtilVisitAnalytics {
    /** 화면 주소 */
    static readonly PATH = '/analytics/visits';
    /** 고를 수 있는 가장 긴 기간(일) — 서버도 같은 값으로 막는다(400) */
    static readonly MAX_DAYS = 90;
    /** 빠른 선택 차례 */
    static readonly PRESETS: readonly VisitRangePreset[] = ['TODAY', 'YESTERDAY', 'LAST7', 'LAST30'];
    /** 기본 기간 */
    static readonly DEFAULT_PRESET: VisitRangePreset = 'LAST7';
    /** 페이지 순위 한 쪽 줄 수 */
    static readonly PAGE_SIZE = 20;

    /** 오늘(한국 날짜) */
    static today(now = Date.now()): string {
        return UtilBizApplication.kstToday(now);
    }

    /** 빠른 선택의 기간 */
    static presetRange(kind: VisitRangePreset, now = Date.now()): VisitRange {
        const today = UtilVisitAnalytics.today(now);
        if (kind === 'TODAY') return { from: today, to: today };
        if (kind === 'YESTERDAY') {
            const y = UtilBizApplication.addDays(today, -1);
            return { from: y, to: y };
        }
        return { from: UtilBizApplication.addDays(today, -(PRESET_DAYS[kind] - 1)), to: today };
    }

    /** 이 기간이 어느 빠른 선택과 같은가(단추 켜짐 표시) — 없으면 null(직접 고름) */
    static presetOf(range: VisitRange, now = Date.now()): VisitRangePreset | null {
        for (const kind of UtilVisitAnalytics.PRESETS) {
            const r = UtilVisitAnalytics.presetRange(kind, now);
            if (r.from === range.from && r.to === range.to) return kind;
        }
        return null;
    }

    /** 기간의 날 수(양끝 포함) */
    static days(range: VisitRange): number {
        return Math.round((Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`)) / 86_400_000) + 1;
    }

    /** 하루 기간인가(시간별 추이 · 「방문자」 이름) */
    static isSingleDay(range: VisitRange): boolean {
        return range.from === range.to;
    }

    /** 오늘이 낀 기간인가(「집계 중」) */
    static includesToday(range: VisitRange, now = Date.now()): boolean {
        return range.to >= UtilVisitAnalytics.today(now);
    }

    /**
     * 기간을 고른다 — 시작·끝이 바뀌었으면 맞바꾸고, 끝은 오늘을 넘지 않게, 길이는 `MAX_DAYS` 를 넘지 않게(끝을 두고 시작을 당긴다).
     * @returns 고친 기간과, 길이 때문에 잘랐는지.
     */
    static normalize(from: string, to: string, now = Date.now()): { range: VisitRange; clamped: boolean } {
        const today = UtilVisitAnalytics.today(now);
        let a = from;
        let b = to;
        if (a > b) [a, b] = [b, a];
        if (b > today) b = today;
        if (a > b) a = b;
        const earliest = UtilBizApplication.addDays(b, -(UtilVisitAnalytics.MAX_DAYS - 1));
        const clamped = a < earliest;
        return { range: { from: clamped ? earliest : a, to: b }, clamped };
    }

    /** 주소 쿼리 → 기간. 깨졌거나 없으면 기본(최근 7일) */
    static filterOf(query: Record<string, unknown>, now = Date.now()): VisitRange {
        const str = (key: string) => (typeof query[key] === 'string' ? (query[key] as string) : '');
        const from = str('from');
        const to = str('to');
        if (!YMD.test(from) || !YMD.test(to)) return UtilVisitAnalytics.presetRange(UtilVisitAnalytics.DEFAULT_PRESET, now);
        return UtilVisitAnalytics.normalize(from, to, now).range;
    }

    /** 기간 → 주소 쿼리 */
    static queryOf(range: VisitRange): Record<string, string> {
        return { from: range.from, to: range.to };
    }

    /**
     * 다른 화면(대시보드 가젯)에서 「방문자 통계」 쪽으로 오는 주소.
     * @param {VisitRangePreset} preset - 기간.
     * @param {string} path - 쪽 주소(`UtilVisitAnalytics.PAGES.*`, 기본 개요).
     */
    static link(preset: VisitRangePreset = 'TODAY', path: string = UtilVisitAnalytics.PATH, now = Date.now()): string {
        const r = UtilVisitAnalytics.presetRange(preset, now);
        return `${path}?from=${r.from}&to=${r.to}`;
    }

    /** 「방문자 통계」 묶음의 쪽 주소 — 메뉴(`util-admin-menu.ts` 의 `visitStats` 묶음)와 같다 */
    static readonly PAGES = {
        overview: '/analytics/visits',
        pages: '/analytics/visits/pages',
        referrers: '/analytics/visits/referrers',
        flows: '/analytics/visits/flows',
        time: '/analytics/visits/time',
        realtime: '/analytics/visits/realtime',
        environment: '/analytics/visits/environment',
        search: '/analytics/visits/search',
        bots: '/analytics/visits/bots',
        health: '/analytics/visits/health',
        requests: '/analytics/visits/requests',
    } as const;

    /** 기기·봇 종류의 색(CSS 식 — 비율 막대·쌓은 막대가 같은 색) */
    static readonly DEVICE_COLORS: Readonly<Record<string, string>> = {
        MOBILE: 'var(--p-blue-500)',
        DESKTOP: 'var(--p-emerald-500)',
        TABLET: 'var(--p-amber-500)',
    };

    static readonly BOT_COLORS: Readonly<Record<string, string>> = {
        GOOGLE: 'var(--p-blue-500)',
        NAVER: 'var(--p-emerald-500)',
        BING: 'var(--p-sky-400)',
        DAUM: 'var(--p-amber-500)',
        OTHER_CRAWLER: 'var(--p-violet-500)',
        TOOL: 'var(--p-surface-400)',
    };

    /**
     * 정수 비중(%) — 합이 정확히 100 이 되게 큰 나머지 차례로 1씩 더한다(쌓은 막대의 「비중」). 합이 0 이면 모두 0.
     * @param {number[]} values - 값들.
     * @returns {number[]} 같은 차례의 비중.
     */
    static wholeShares(values: number[]): number[] {
        const total = values.reduce((a, b) => a + b, 0);
        if (total <= 0) return values.map(() => 0);
        const raw = values.map((v) => (v / total) * 100);
        const out = raw.map((v) => Math.floor(v));
        let rest = 100 - out.reduce((a, b) => a + b, 0);
        const order = raw.map((v, i) => ({ i, frac: v - Math.floor(v) })).sort((a, b) => b.frac - a.frac);
        for (const { i } of order) {
            if (rest <= 0) break;
            out[i]! += 1;
            rest -= 1;
        }
        return out;
    }

    /** 유입 분류의 색(CSS 식 — 차트 레이어가 풀어 쓴다, 막대·범례·쌓은 막대가 같은 색) */
    static readonly GROUP_COLORS: Readonly<Record<string, string>> = {
        SEARCH: 'var(--p-blue-500)',
        DIRECT: 'var(--p-emerald-500)',
        SOCIAL: 'var(--p-violet-500)',
        OTHER_SITE: 'var(--p-amber-500)',
        CAMPAIGN: 'var(--p-pink-500)',
    };

    /** 요일·시간대 칸의 평균 — 더한 칸이 없으면 null */
    static average(sum: number, days: number): number | null {
        return days > 0 ? sum / days : null;
    }

    /**
     * 증감(%) — 직전 값이 0 이면 null(비교할 수 없음).
     * @returns (지금 − 직전) ÷ 직전 × 100.
     */
    static change(now: number, before: number): number | null {
        if (!Number.isFinite(before) || before <= 0) return null;
        return ((now - before) / before) * 100;
    }

    /** 1인당 페이지뷰 — 방문자 0 이면 null */
    static perVisitor(c: Pick<VisitCounts, 'visitors' | 'pageViews'>): number | null {
        return c.visitors > 0 ? c.pageViews / c.visitors : null;
    }

    /** 신규 비율(%) — 방문자 0 이면 null */
    static newRate(c: Pick<VisitCounts, 'visitors' | 'newVisitors'>): number | null {
        return c.visitors > 0 ? (c.newVisitors / c.visitors) * 100 : null;
    }

    /** 비중(%, 0~100) — 전체가 0 이면 0 */
    static share(part: number, total: number): number {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100, (part / total) * 100));
    }

    /** 숫자 글자 — 천 단위 쉼표 */
    static num(n: number, locale: string): string {
        return new Intl.NumberFormat(UtilVisitAnalytics.intl(locale)).format(n);
    }

    /** 축 눈금용 짧은 숫자 — 1.2만 / 12K */
    static compact(n: number, locale: string): string {
        return new Intl.NumberFormat(UtilVisitAnalytics.intl(locale), { notation: 'compact', maximumFractionDigits: 1 }).format(n);
    }

    /** 소수 글자 — 기본 한 자리, null 이면 `—` */
    static decimal(n: number | null, locale: string, digits = 1): string {
        if (n === null || !Number.isFinite(n)) return '—';
        return new Intl.NumberFormat(UtilVisitAnalytics.intl(locale), { minimumFractionDigits: digits, maximumFractionDigits: digits }).format(n);
    }

    /** 퍼센트 글자 — `12.3%`, null 이면 `—` */
    static percent(n: number | null, locale: string, digits = 1): string {
        if (n === null || !Number.isFinite(n)) return '—';
        return `${UtilVisitAnalytics.decimal(n, locale, digits)}%`;
    }

    // ── 3단계: 달력 주·달(순방문자) · 월별 보기 ──

    /** 월별 보기 주소 — 메뉴에 없고 개요의 「월별 보기 →」로 들어간다 */
    static readonly MONTHLY_PATH = '/analytics/visits/monthly';
    /** 월별 보기에서 고를 수 있는 가장 긴 기간(개월) — 서버도 같은 값으로 막는다(400) */
    static readonly MAX_MONTHS = 36;
    /** 월별 보기 기본 기간(개월, 이번 달 포함) */
    static readonly DEFAULT_MONTHS = 24;
    /** 월별 보기 빠른 선택(개월, 이번 달 포함) */
    static readonly MONTH_PRESETS: readonly number[] = [12, 24, 36];

    /** 이번 달(한국 날짜 `YYYY-MM`) */
    static thisMonth(now = Date.now()): string {
        return UtilVisitAnalytics.today(now).slice(0, 7);
    }

    /** 달 더하기 — `YYYY-MM` ± n */
    static addMonths(ym: string, n: number): string {
        const total = Number(ym.slice(0, 4)) * 12 + (Number(ym.slice(5, 7)) - 1) + n;
        const y = Math.floor(total / 12);
        const m = total - y * 12 + 1;
        return `${y}-${String(m).padStart(2, '0')}`;
    }

    /** 기간의 달 수(양끝 포함) */
    static monthCount(range: { from: string; to: string }): number {
        const a = Number(range.from.slice(0, 4)) * 12 + Number(range.from.slice(5, 7));
        const b = Number(range.to.slice(0, 4)) * 12 + Number(range.to.slice(5, 7));
        return b - a + 1;
    }

    /** 최근 n개월(이번 달 포함) */
    static lastMonths(n: number, now = Date.now()): { from: string; to: string } {
        const to = UtilVisitAnalytics.thisMonth(now);
        return { from: UtilVisitAnalytics.addMonths(to, -(n - 1)), to };
    }

    /**
     * 월 기간을 고른다 — 맞바꾸기 · 끝은 이번 달까지 · 길이는 `MAX_MONTHS` 까지(끝을 두고 시작을 당긴다). 일별 기간의 `normalize` 와 같은 규칙.
     * @returns 고친 기간과, 길이 때문에 잘랐는지.
     */
    static normalizeMonths(from: string, to: string, now = Date.now()): { range: { from: string; to: string }; clamped: boolean } {
        const current = UtilVisitAnalytics.thisMonth(now);
        let a = from;
        let b = to;
        if (a > b) [a, b] = [b, a];
        if (b > current) b = current;
        if (a > b) a = b;
        const earliest = UtilVisitAnalytics.addMonths(b, -(UtilVisitAnalytics.MAX_MONTHS - 1));
        const clamped = a < earliest;
        return { range: { from: clamped ? earliest : a, to: b }, clamped };
    }

    /** 주소 쿼리 → 월 기간(`from`·`to` = `YYYY-MM`). 깨졌거나 없으면 기본(최근 24개월) */
    static monthFilterOf(query: Record<string, unknown>, now = Date.now()): { from: string; to: string } {
        const str = (key: string) => (typeof query[key] === 'string' ? (query[key] as string) : '');
        const from = str('from');
        const to = str('to');
        if (!YM.test(from) || !YM.test(to)) return UtilVisitAnalytics.lastMonths(UtilVisitAnalytics.DEFAULT_MONTHS, now);
        return UtilVisitAnalytics.normalizeMonths(from, to, now).range;
    }

    /** 그 날이 든 주의 월요일(한국 날짜 — 주는 월요일 시작) */
    static weekStart(ymd: string): string {
        const weekday = new Date(`${ymd}T00:00:00Z`).getUTCDay(); // 0 = 일요일
        return UtilBizApplication.addDays(ymd, -((weekday + 6) % 7));
    }

    /** 그 달의 말일 `YYYY-MM-DD` */
    static monthEnd(ym: string): string {
        return UtilBizApplication.addDays(`${UtilVisitAnalytics.addMonths(ym, 1)}-01`, -1);
    }

    /** 달 글자 — 「2026년 11월」 · 「Nov 2026」 */
    static monthLabel(ym: string, locale: string): string {
        return new Intl.DateTimeFormat(UtilVisitAnalytics.intl(locale), { year: 'numeric', month: locale === 'en' ? 'short' : 'long', timeZone: 'UTC' }).format(new Date(`${ym}-01T00:00:00Z`));
    }

    /** 축 눈금용 짧은 달 글자 — 「25.11」 · 「Nov '25」 */
    static monthShort(ym: string, locale: string): string {
        const y = ym.slice(2, 4);
        const m = Number(ym.slice(5, 7));
        if (locale === 'en') return `${new Intl.DateTimeFormat('en-US', { month: 'short', timeZone: 'UTC' }).format(new Date(`${ym}-01T00:00:00Z`))} '${y}`;
        return `${y}.${String(m).padStart(2, '0')}`;
    }

    private static intl(locale: string): string {
        return locale === 'en' ? 'en-US' : 'ko-KR';
    }
}
