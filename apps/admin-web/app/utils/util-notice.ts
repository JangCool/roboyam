import type { LocationQuery } from 'vue-router';
import type { NoticeBoardRef, NoticeGlobal, NoticeQuery, NoticeWriter } from '~/composables/notice/api/useNoticeApi.types';
import type { PostStateFilter } from '~/composables/post/api/usePostApi.types';

/** 공지 목록 필터(주소 쿼리 `?q=&state=&prefix=&page=`) — `page` 는 1부터 */
export interface NoticeFilters {
    q: string;
    state: PostStateFilter;
    prefix: string;
    page: number;
}

const STATE_FILTERS: readonly PostStateFilter[] = ['ALL', 'PUBLIC', 'HIDDEN', 'DELETED'];

/** 전체 공지 「걸어 둘 기간」 — 끝 없음(풀 때까지) · 7일 · 30일 · 날짜 고르기 */
export type NoticePeriod = 'NONE' | 'D7' | 'D30' | 'DATE';

const PERIODS: readonly NoticePeriod[] = ['NONE', 'D7', 'D30', 'DATE'];
const DAY_MS = 86_400_000;
/** 날짜 고르기는 한국 시각 기준(관리자 앱의 다른 날짜 칸과 같다) */
const KST_OFFSET_MS = 9 * 3_600_000;

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

/**
 * 공지 관리(`/notices`) — 주소 ↔ 필터, 제목 규칙. 서버 규칙(notice-admin-contract): 제목 1~100자, 본문은 공개 글쓰기와 같은 규칙(서버 판정).
 */
export default class UtilNotice {
    /** 한 쪽 줄 수(쪽 크기 고르기는 두지 않는다 — 공지는 많지 않다) */
    static readonly PAGE_SIZE = 20;
    /** 제목 최대 글자 수 */
    static readonly TITLE_MAX = 100;
    static readonly SEARCH_MAX = 100;
    static readonly STATE_FILTERS = STATE_FILTERS;

    static emptyFilters(): NoticeFilters {
        return { q: '', state: 'ALL', prefix: '', page: 1 };
    }

    /** 주소의 쿼리를 필터로 — 모르는 값은 기본값 */
    static parse(query: LocationQuery): NoticeFilters {
        const state = first(query.state).toUpperCase() as PostStateFilter;
        const page = Number.parseInt(first(query.page), 10);
        return {
            q: Array.from(first(query.q).trim()).slice(0, UtilNotice.SEARCH_MAX).join(''),
            state: STATE_FILTERS.includes(state) ? state : 'ALL',
            prefix: first(query.prefix).trim(),
            page: Number.isFinite(page) && page >= 1 ? page : 1,
        };
    }

    /** 필터를 주소의 쿼리로 — 기본값은 싣지 않는다 */
    static toQuery(f: NoticeFilters): Record<string, string> {
        const query: Record<string, string> = {};
        if (f.q) query.q = f.q;
        if (f.state !== 'ALL') query.state = f.state;
        if (f.prefix) query.prefix = f.prefix;
        if (f.page > 1) query.page = String(f.page);
        return query;
    }

    /** API 조건 — 쪽은 0부터 */
    static toApi(f: NoticeFilters): NoticeQuery {
        return { q: f.q, state: f.state, prefix: f.prefix, page: f.page - 1, size: UtilNotice.PAGE_SIZE };
    }

    /** 필터가 같은지 가르는 열쇠 */
    static key(f: NoticeFilters): string {
        return JSON.stringify(UtilNotice.toQuery(f));
    }

    /** 필터의 일부를 바꾼다 — 쪽 말고 다른 것을 바꾸면 1쪽으로 */
    static change(f: NoticeFilters, patch: Partial<NoticeFilters>): NoticeFilters {
        const next: NoticeFilters = { ...f, ...patch };
        if (patch.page === undefined) next.page = 1;
        return next;
    }

    static hasConditions(f: NoticeFilters): boolean {
        return f.q !== '' || f.state !== 'ALL' || f.prefix !== '';
    }

    static pageCount(total: number, size: number): number {
        return Math.max(1, Math.ceil(total / Math.max(1, size)));
    }

    /** 제목 글자 수(앞뒤 공백 뺀, 낱글자 기준) */
    static titleLength(title: string): number {
        return Array.from(title.trim()).length;
    }

    // ── 전체 공지 기간(서버 규칙: 지금보다 뒤, 366일 안) ──

    static readonly PERIODS = PERIODS;
    /** 고를 수 있는 가장 먼 날 — 오늘부터 이 날 수 안 */
    static readonly PERIOD_MAX_DAYS = 365;

    /** 한국 시각의 오늘(`YYYY-MM-DD`) */
    static kstToday(now = Date.now()): string {
        return new Date(now + KST_OFFSET_MS).toISOString().slice(0, 10);
    }

    /** 한국 시각 날짜에 날 수를 더한다 */
    static kstAddDays(ymd: string, days: number): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d! + days)).toISOString().slice(0, 10);
    }

    /** 그 날(한국 시각)의 마지막 초 23:59:59(UTC ISO) */
    static kstEndOfDay(ymd: string): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d! + 1) - KST_OFFSET_MS - 1000).toISOString();
    }

    /** 저장된 끝 시각을 날짜 칸 값으로 — 그 순간의 한국 시각 날짜 */
    static kstDateOf(iso: string): string {
        return new Date(Date.parse(iso) + KST_OFFSET_MS).toISOString().slice(0, 10);
    }

    /**
     * 기간 고르기 → 보낼 끝 시각(UTC ISO, 끝 없음 null). 7일·30일은 지금부터, 날짜는 그날 끝까지.
     * 저장된 값을 그대로 둔 날짜면(`original` 의 날짜와 같음) 저장된 값을 그대로 보낸다 — 서버는 지난 값도 그대로면 받는다.
     */
    static expiresAt(period: NoticePeriod, date: string, original: string | null, now = Date.now()): string | null {
        if (period === 'NONE') return null;
        if (period === 'D7') return new Date(now + 7 * DAY_MS).toISOString();
        if (period === 'D30') return new Date(now + 30 * DAY_MS).toISOString();
        if (!date) return null;
        if (original && UtilNotice.kstDateOf(original) === date) return original;
        return UtilNotice.kstEndOfDay(date);
    }

    /** 「뺄 게시판 N개」 — 하나씩 뺀 게시판 + (소모임 전체를 뺐으면 지금 소모임 게시판 수) */
    static excludedCount(g: NoticeGlobal, groupBoards: readonly NoticeBoardRef[]): number {
        if (!g.excludeAllGroupBoards) return g.excludedBoardIds.length;
        const groupIds = new Set(groupBoards.map((b) => b.id));
        return g.excludedBoardIds.filter((id) => !groupIds.has(id)).length + groupBoards.length;
    }

    /** 내 운영자 회원 계정으로 공지를 쓸 수 있다 — 연결됐고 멈추지 않았다 */
    static canWrite(writer: NoticeWriter | null | undefined): boolean {
        return writer?.state === 'LINKED';
    }
}
