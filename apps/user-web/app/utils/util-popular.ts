import type { LocationQuery } from 'vue-router';
import type { PopularFill, PopularFills, PopularListResponse, PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 인기글 전체 화면(`/popular?period=&page=`)의 규칙 — 기간·쪽 쿼리, 채운 줄, 이번 주 범위, 기간 탭의 키보드 이동. 상태도 요청도 없다.
 * 원본(community) `UtilHomePopular`·`UtilHomeQuery` 에서 홈 가젯 몫을 뺐다. 순위 계산은 서버(community `docs/api-popular.md`)다 — 화면은 받은 순서대로 그린다.
 */

/** 서비스 달력의 월·일 */
export interface ServiceMonthDay {
    month: number;
    day: number;
}

const PERIODS: readonly PopularPeriod[] = ['REALTIME', 'YESTERDAY', 'WEEK'];
const FILLS: readonly PopularFill[] = ['YESTERDAY', 'LAST_WEEK'];

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

function monthDayOf(ms: number): ServiceMonthDay {
    const [, month = 0, day = 0] = (UtilTime.serviceDayOf(ms) ?? '').split('-').map(Number);
    return { month, day };
}

export default class UtilPopular {
    static readonly PERIODS: readonly PopularPeriod[] = PERIODS;
    static readonly DEFAULT_PERIOD: PopularPeriod = 'REALTIME';
    /** 한 쪽 글 수 */
    static readonly PAGE_SIZE = 20;
    /** 최신글 전체의 한 번 받는 글 수 */
    static readonly LATEST_PAGE_SIZE = 20;
    static readonly MAX_PAGE = 100;

    /**
     * 주소의 쿼리 → 기간(틀리면 실시간)
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {PopularPeriod} 기간.
     */
    static parsePeriod(query: LocationQuery): PopularPeriod {
        const upper = first(query.period).trim().toUpperCase();
        return PERIODS.find((p) => p === upper) ?? UtilPopular.DEFAULT_PERIOD;
    }

    /**
     * 주소의 쿼리 → 쪽(틀리면 1)
     * @param {LocationQuery} query - 라우트의 쿼리.
     * @returns {number} 1~100.
     */
    static parsePage(query: LocationQuery): number {
        const text = first(query.page).trim();
        if (!/^\d+$/.test(text)) return 1;
        const page = Number(text);
        return page >= 1 ? Math.min(page, UtilPopular.MAX_PAGE) : 1;
    }

    /**
     * 기간·쪽 → 주소의 쿼리(기본값은 뺀다)
     * @param {number} page - 쪽.
     * @param {PopularPeriod} period - 기간.
     * @returns {Record<string, string>} 쿼리.
     */
    static build(page: number, period: PopularPeriod): Record<string, string> {
        const query: Record<string, string> = {};
        if (period !== UtilPopular.DEFAULT_PERIOD) query.period = period;
        if (page > 1) query.page = String(page);
        return query;
    }

    /**
     * 응답의 채운 줄 — 글 ID 모양·알려진 출처만 남긴다
     * @param {unknown} raw - 응답의 `fills`.
     * @returns {PopularFills} 글 ID → 출처.
     */
    static fillsOf(raw: unknown): PopularFills {
        const out: PopularFills = {};
        if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return out;
        for (const [id, fill] of Object.entries(raw as Record<string, unknown>)) {
            const known = FILLS.find((f) => f === fill);
            if (/^\d{1,20}$/.test(id) && known !== undefined) out[id] = known;
        }
        return out;
    }

    /**
     * 글 하나의 채운 줄 출처
     * @param {PopularFills} fills - 채운 줄.
     * @param {string} postId - 글 ID.
     * @returns {PopularFill | null} 출처. 채운 줄이 아니면 null.
     */
    static fillOf(fills: PopularFills, postId: string): PopularFill | null {
        return Object.prototype.hasOwnProperty.call(fills, postId) ? (fills[postId] ?? null) : null;
    }

    /**
     * 이번 주 범위의 첫날·마지막 날(서비스 달력) — `to` 는 다음 주 시작 순간이라 1ms 앞을 마지막 날로 본다
     * @param {Pick<PopularListResponse, 'from' | 'to'> | null} res - 응답.
     * @returns {{ from: ServiceMonthDay; to: ServiceMonthDay } | null} 범위. 없거나 틀리면 null.
     */
    static rangeDays(res: Pick<PopularListResponse, 'from' | 'to'> | null): { from: ServiceMonthDay; to: ServiceMonthDay } | null {
        if (!res?.from || !res.to) return null;
        const from = Date.parse(res.from);
        const to = Date.parse(res.to);
        if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) return null;
        return { from: monthDayOf(from), to: monthDayOf(to - 1) };
    }

    /**
     * 순위 번호의 시작 — 2쪽 첫 글은 21번
     * @param {number} page - 쪽.
     * @param {number} size - 한 쪽 글 수.
     * @returns {number} 첫 글의 순위.
     */
    static rankStart(page: number, size: number): number {
        return (Math.max(1, page) - 1) * size + 1;
    }

    /**
     * 기간 탭 단추의 `id`
     * @param {string} prefix - 화면에서 하나뿐인 접두어(`useId()`).
     * @param {PopularPeriod} period - 기간.
     * @returns {string} id.
     */
    static tabId(prefix: string, period: PopularPeriod): string {
        return `${prefix}-tab-${period.toLowerCase()}`;
    }

    /**
     * 기간 탭에서 화살표·Home·End 로 옮겨 갈 탭
     * @param {PopularPeriod} current - 지금 초점이 있는 탭.
     * @param {string} key - 누른 키.
     * @returns {PopularPeriod | null} 옮겨 갈 탭. 이동 키가 아니면 null.
     */
    static movedTo(current: PopularPeriod, key: string): PopularPeriod | null {
        const at = PERIODS.indexOf(current);
        const last = PERIODS.length - 1;
        switch (key) {
            case 'ArrowRight':
            case 'ArrowDown':
                return PERIODS[at >= last ? 0 : at + 1] ?? null;
            case 'ArrowLeft':
            case 'ArrowUp':
                return PERIODS[at <= 0 ? last : at - 1] ?? null;
            case 'Home':
                return PERIODS[0] ?? null;
            case 'End':
                return PERIODS[last] ?? null;
            default:
                return null;
        }
    }

    /**
     * 이미 받은 목록에 새로 받은 글을 잇는다 — 이미 있는 글은 건너뛴다(최신글 더 보기)
     * @param {readonly T[]} list - 지금 목록.
     * @param {readonly T[]} more - 새로 받은 글.
     * @returns {T[]} 이은 목록.
     */
    static appendNew<T extends { id: string }>(list: readonly T[], more: readonly T[]): T[] {
        const seen = new Set(list.map((row) => row.id));
        return [...list, ...more.filter((row) => !seen.has(row.id))];
    }
}
