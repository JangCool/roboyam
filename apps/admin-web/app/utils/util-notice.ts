import type { LocationQuery } from 'vue-router';
import type { NoticeQuery, NoticeWriter } from '~/composables/notice/api/useNoticeApi.types';
import type { PostStateFilter } from '~/composables/post/api/usePostApi.types';

/** 공지 목록 필터(주소 쿼리 `?q=&state=&prefix=&page=`) — `page` 는 1부터 */
export interface NoticeFilters {
    q: string;
    state: PostStateFilter;
    prefix: string;
    page: number;
}

const STATE_FILTERS: readonly PostStateFilter[] = ['ALL', 'PUBLIC', 'HIDDEN', 'DELETED'];

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

    /** 내 운영자 회원 계정으로 공지를 쓸 수 있다 — 연결됐고 멈추지 않았다 */
    static canWrite(writer: NoticeWriter | null | undefined): boolean {
        return writer?.state === 'LINKED';
    }
}
