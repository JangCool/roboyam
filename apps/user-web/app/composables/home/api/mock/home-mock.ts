// MOCK: 최신글 전체(`GET /api/home/latest`)·인기글 전체(`GET /api/home/popular`)의 목업 — `useHomeApi.ts` 가 부른다. 서버가 붙으면 `home/api/mock/` 와 함께 지운다.
// 글은 실제 공개 게시판 API 로 모은다(`common/mock/board-feed-mock.ts`) — 목업 화면의 글이 실제 글로 열린다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   latest-fail       최신글 읽기 실패(500)            latest-empty    최신글이 없다
//   latest-more-fail  최신글 「더 보기」만 실패
//   popular-fail      인기글 읽기 실패(500)            popular-empty   그 기간의 인기글이 없다
//   popular-anytime   기간을 보지 않고 모은 글 전체로 순위를 만든다(시드 글이 오래돼 탭이 비어 보일 때)
//
// ── 목업 규칙(가정 — community `docs/api-popular.md` 를 줄인 것, 계약 「목업 가정」) ─
//   · 점수 = 추천×3 + 댓글×2 + 조회×0.1 (시간 감쇠 없음), 점수 0·19금 글은 빠진다. 한 글쓴이는 한 순위에 2개까지.
//   · 기간은 서비스 시간대(한국 시간) 달력 — 실시간 = 오늘 올라온 글, 어제 = 어제 올라온 글, 이번 주 = 월요일 0시부터.
//   · 실시간 탭이 5개 미만이면 어제 글로, 이번 주 탭이 5개 미만이면 지난주 글로 채우고 `fills` 에 출처를 단다. 순위는 50개까지.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockBoardFeed, mockCursorStart } from '~/composables/common/mock/board-feed-mock';
import type { PostFeedPage, PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { LatestQuery, PopularFill, PopularFills, PopularListResponse, PopularQuery } from '~/composables/home/api/useHomeApi.types';

const DAY = 24 * 60 * 60 * 1000;
/** 서비스 시간대(한국 시간)의 UTC 차 */
const KST = 9 * 60 * 60 * 1000;
const KEEP = 50;
const FILL_MIN = 5;
const MAX_PER_AUTHOR = 2;

function size(value: number | undefined): number {
    return Math.min(50, Math.max(1, value ?? 20));
}

export async function mockLatest(source: BoardFeedSource, query: LatestQuery): Promise<PostFeedPage> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('latest-fail') || (query.cursor && flags.has('latest-more-fail'))) throw MOCK_ERRORS.internal();
    const rows = flags.has('latest-empty') ? [] : (await mockBoardFeed(source)).rows;
    const start = mockCursorStart(query.cursor);
    const end = start + size(query.size);
    const items = rows.slice(start, end);
    const hasNext = end < rows.length;
    return mockClone({ items, hasNext, nextCursor: hasNext ? String(end) : null });
}

/** 그 순간이 든 서비스 하루의 시작(UTC ms) */
function dayStart(ms: number): number {
    return Math.floor((ms + KST) / DAY) * DAY - KST;
}

/** 이번 주(월요일 시작)의 시작(UTC ms) */
function weekStart(ms: number): number {
    const start = dayStart(ms);
    const weekday = new Date(start + KST).getUTCDay(); // 0 = 일
    return start - ((weekday + 6) % 7) * DAY;
}

function score(row: PostFeedRow): number {
    return row.likeCount * 3 + row.commentCount * 2 + row.viewCount * 0.1;
}

/** 기간 안 후보를 점수 순으로 — 한 글쓴이는 `MAX_PER_AUTHOR` 개까지 */
function ranked(rows: readonly PostFeedRow[], from: number, to: number, skip: ReadonlySet<string> = new Set()): PostFeedRow[] {
    const perAuthor = new Map<string, number>();
    const out: PostFeedRow[] = [];
    const candidates = rows
        .filter((row) => !row.adult && !skip.has(row.id) && score(row) > 0)
        .filter((row) => {
            const at = Date.parse(row.createdAt);
            return at >= from && at < to;
        })
        .sort((a, b) => score(b) - score(a) || Date.parse(b.createdAt) - Date.parse(a.createdAt));
    for (const row of candidates) {
        const author = row.author.memberId ?? `guest:${row.author.name}`;
        const count = perAuthor.get(author) ?? 0;
        if (count >= MAX_PER_AUTHOR) continue;
        perAuthor.set(author, count + 1);
        out.push(row);
    }
    return out;
}

export async function mockPopular(source: BoardFeedSource, query: PopularQuery): Promise<PopularListResponse> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('popular-fail')) throw MOCK_ERRORS.internal();
    const rows = flags.has('popular-empty') ? [] : (await mockBoardFeed(source)).rows;
    const now = Date.now();
    const today = dayStart(now);
    const week = weekStart(now);

    let list: PostFeedRow[];
    const fills: PopularFills = {};
    let from: string | null = null;
    let to: string | null = null;
    const fill = (extra: PostFeedRow[], kind: PopularFill) => {
        for (const row of extra) {
            fills[row.id] = kind;
            list.push(row);
        }
    };

    if (flags.has('popular-anytime')) {
        list = ranked(rows, 0, Number.MAX_SAFE_INTEGER);
    } else if (query.period === 'YESTERDAY') {
        list = ranked(rows, today - DAY, today);
    } else if (query.period === 'WEEK') {
        list = ranked(rows, week, week + 7 * DAY);
        if (list.length < FILL_MIN) fill(ranked(rows, week - 7 * DAY, week, new Set(list.map((r) => r.id))), 'LAST_WEEK');
        from = new Date(week).toISOString();
        to = new Date(week + 7 * DAY).toISOString();
    } else {
        list = ranked(rows, today, today + DAY);
        if (list.length < FILL_MIN) fill(ranked(rows, today - DAY, today, new Set(list.map((r) => r.id))), 'YESTERDAY');
    }
    list = list.slice(0, KEEP);

    const per = Math.min(50, Math.max(1, query.size ?? 20));
    const totalPages = Math.max(1, Math.ceil(list.length / per));
    const page = Math.max(1, query.page);
    const items = list.slice((page - 1) * per, page * per);
    const pageFills: PopularFills = {};
    for (const row of items) if (fills[row.id]) pageFills[row.id] = fills[row.id]!;
    return mockClone({
        pinned: [],
        items,
        page,
        totalPages,
        totalCount: list.length,
        hasNext: page < totalPages,
        nextCursor: null,
        period: query.period,
        fills: pageFills,
        from,
        to,
        weekStart: query.period === 'WEEK' ? 'MONDAY' : null,
    });
}
