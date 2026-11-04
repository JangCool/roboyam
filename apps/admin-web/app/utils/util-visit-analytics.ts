import type { VisitCounts, VisitRange } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

/** 기간 빠른 선택 — 오늘 · 어제 · 최근 7일(오늘 포함) · 최근 30일(오늘 포함) */
export type VisitRangePreset = 'TODAY' | 'YESTERDAY' | 'LAST7' | 'LAST30';

const YMD = /^\d{4}-\d{2}-\d{2}$/;
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
    } as const;

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

    private static intl(locale: string): string {
        return locale === 'en' ? 'en-US' : 'ko-KR';
    }
}
