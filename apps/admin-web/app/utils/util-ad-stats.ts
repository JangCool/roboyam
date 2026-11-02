import type { AdBookingRow, AdStatCounts, AdStatDayState, AdStatRange, AdStatsSort, AdType } from '~/composables/ad/api/useAdApi.types';
import { AD_STATS_SORTS } from '~/composables/ad/api/useAdApi.types';
import UtilAd from '~/utils/util-ad';
import UtilBizApplication from '~/utils/util-biz-application';

/** 운영자 성과 화면의 거르기(주소 쿼리 `from`·`to`·`type`·`advertiserId`·`sort`·`dir`) */
export interface AdStatsFilter {
    from: string;
    to: string;
    type: AdType | null;
    advertiserId: string | null;
    sort: AdStatsSort;
    dir: 'asc' | 'desc';
}

/** 차트 띠 — 이어진 정지·내림 날 묶음(날짜 차례 번호, 양끝 포함) */
export interface AdStatBand {
    state: Exclude<AdStatDayState, 'SHOWING'>;
    start: number;
    end: number;
}

const YMD = /^\d{4}-\d{2}-\d{2}$/;

/**
 * 광고 성과 숫자 규칙 — 입력만 보고 답하는 순수 함수. 숫자 자체는 서버(`ad_daily_stats` 집계)가 정하고 화면은 클릭률·표기·기간만 계산한다.
 */
export default class UtilAdStats {
    /** 운영자 성과 기본 기간 — 오늘 포함 최근 7일(오늘은 「집계 중」) */
    static readonly DEFAULT_DAYS = 7;

    /** 일정표 장부에서 성과가 있을 수 없는 상태 — 결제 전·심사 대기 */
    static readonly PRE_START: readonly AdBookingRow['status'][] = ['SUBMITTED', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT'];

    /**
     * 클릭률(%) — 노출 0 이면 null(「—」). 100% 를 넘을 수 있다(클릭은 그날 노출 여부를 보지 않는다).
     * @returns 클릭 ÷ 노출 × 100 또는 null.
     */
    static ctr(c: AdStatCounts): number | null {
        if (c.impressions <= 0) return null;
        return (c.clicks / c.impressions) * 100;
    }

    /** 클릭률 글자 — 소수 한 자리 `3.2%`, 노출 0 이면 `—` */
    static ctrText(c: AdStatCounts, locale: string): string {
        const v = UtilAdStats.ctr(c);
        if (v === null) return '—';
        return `${new Intl.NumberFormat(UtilAdStats.intl(locale), { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(v)}%`;
    }

    /** 숫자 글자 — 천 단위 쉼표 */
    static num(n: number, locale: string): string {
        return new Intl.NumberFormat(UtilAdStats.intl(locale)).format(n);
    }

    /** 축 눈금용 짧은 숫자 — 1.2만 / 12K 처럼 줄인다 */
    static compact(n: number, locale: string): string {
        return new Intl.NumberFormat(UtilAdStats.intl(locale), { notation: 'compact', maximumFractionDigits: 1 }).format(n);
    }

    /** 비중(%) — 전체가 0 이면 0 */
    static share(part: number, total: number): number {
        if (total <= 0) return 0;
        return Math.max(0, Math.min(100, (part / total) * 100));
    }

    /** 숫자가 모두 0 인가(「아직 집계된 노출이 없어요」) */
    static isEmpty(c: AdStatCounts): boolean {
        return c.impressions === 0 && c.clicks === 0;
    }

    /** 시작했나 — 결제 전·심사 대기가 아니고 시작 시각이 지났다(그 전이면 서랍 「성과」 탭은 안내 한 줄) */
    static started(row: Pick<AdBookingRow, 'status' | 'period'>, now = Date.now()): boolean {
        return !UtilAdStats.PRE_START.includes(row.status) && Date.parse(row.period.startAt) <= now;
    }

    /** 건의 기본 범위 — 시작 날짜 ~ min(끝 날짜, 오늘)(KST, 끝 시각은 반열림이라 1ms 앞 날짜). 시작 전이면 null */
    static bookingRange(period: { startAt: string; endAt: string }, now = Date.now()): AdStatRange | null {
        const from = UtilBizApplication.kstDate(period.startAt);
        const end = UtilBizApplication.kstDate(new Date(Date.parse(period.endAt) - 1).toISOString());
        const today = UtilBizApplication.kstToday(now);
        const to = end < today ? end : today;
        return from <= to ? { from, to } : null;
    }

    /** 범위 안 모든 날짜(양끝 포함) */
    static datesOf(range: AdStatRange): string[] {
        const out: string[] = [];
        for (let d = range.from; d <= range.to && out.length < 400; d = UtilBizApplication.addDays(d, 1)) out.push(d);
        return out;
    }

    /** 이어진 정지·내림 날을 띠로 묶는다 */
    static bands(days: readonly { state?: AdStatDayState }[]): AdStatBand[] {
        const out: AdStatBand[] = [];
        days.forEach((d, i) => {
            const state = d.state ?? 'SHOWING';
            if (state === 'SHOWING') return;
            const last = out[out.length - 1];
            if (last && last.state === state && last.end === i - 1) last.end = i;
            else out.push({ state, start: i, end: i });
        });
        return out;
    }

    /**
     * 축 위 끝을 보기 좋은 수로 올린다(1·2·2.5·5·10 × 10ⁿ) — 눈금은 4칸.
     * @returns 축 최댓값(0 이면 4 — 빈 차트도 눈금이 선다).
     */
    static niceMax(max: number, steps = 4): number {
        if (max <= 0) return steps;
        const raw = max / steps;
        const pow = 10 ** Math.floor(Math.log10(raw));
        const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
        return Math.max(steps, Math.ceil(step) * steps);
    }

    /** 축 날짜 글자 `M/D` */
    static shortDay(ymd: string): string {
        const [, m, d] = ymd.split('-');
        return `${Number(m)}/${Number(d)}`;
    }

    /** 운영자 성과 기본 기간 — 오늘 포함 최근 7일 */
    static defaultRange(now = Date.now()): AdStatRange {
        const to = UtilBizApplication.kstToday(now);
        return { from: UtilBizApplication.addDays(to, -(UtilAdStats.DEFAULT_DAYS - 1)), to };
    }

    /**
     * 주소 쿼리 → 거르기. 모르는 값·깨진 날짜는 기본값으로, 시작이 끝보다 늦으면 바꿔 놓는다.
     * @param query - `route.query`.
     */
    static filterOf(query: Record<string, unknown>, now = Date.now()): AdStatsFilter {
        const str = (key: string) => (typeof query[key] === 'string' ? (query[key] as string) : '');
        const def = UtilAdStats.defaultRange(now);
        let from = YMD.test(str('from')) ? str('from') : def.from;
        let to = YMD.test(str('to')) ? str('to') : def.to;
        if (from > to) [from, to] = [to, from];
        const type = (UtilAd.TYPES as readonly string[]).includes(str('type')) ? (str('type') as AdType) : null;
        const sort = (AD_STATS_SORTS as readonly string[]).includes(str('sort')) ? (str('sort') as AdStatsSort) : 'impressions';
        return { from, to, type, advertiserId: str('advertiserId') || null, sort, dir: str('dir') === 'asc' ? 'asc' : 'desc' };
    }

    /** 거르기 → 주소 쿼리(기본값은 빼서 주소를 짧게) */
    static queryOf(filter: AdStatsFilter): Record<string, string> {
        const out: Record<string, string> = { from: filter.from, to: filter.to };
        if (filter.type) out.type = filter.type;
        if (filter.advertiserId) out.advertiserId = filter.advertiserId;
        if (filter.sort !== 'impressions') out.sort = filter.sort;
        if (filter.dir !== 'desc') out.dir = filter.dir;
        return out;
    }

    private static intl(locale: string): string {
        return locale === 'en' ? 'en-US' : 'ko-KR';
    }
}
