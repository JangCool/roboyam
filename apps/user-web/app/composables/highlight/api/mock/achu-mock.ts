// MOCK: 「아추!」 목록 — 서버(기간별 하이라이트 공개 API)가 없어 실제 최신글을 받아 「운영자가 고른 글」처럼 고른 시각을 붙인다(achu-page-contract).
// 글 링크·게시판·추천 수는 진짜라 눌러 볼 수 있다. 서버가 붙으면 이 폴더와 `useHighlightApi` 의 `// MOCK:` 줄을 지운다.
// 스위치: `?mock=achu-empty`(아무 기간도 고른 글 없음) · `?mock=achu-day-empty`(오늘만 없음) · `?mock=achu-fail`(서버 오류)

import type { PostFeedPage } from '~/composables/board/api/usePostApi.types';
import type { AchuItem, AchuListResponse, AchuPeriod, AchuQuery } from '~/composables/highlight/api/useHighlightApi.types';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';

const HOUR = 3_600_000;
const DAY = 24 * HOUR;
/** 서비스 시간대 — 한국시간(UTC+9) */
const KST = 9 * HOUR;

/** 한국시간 오늘 0시 · 이번 주 월요일 0시 · 이번 달 1일 0시(UTC 밀리초) */
function startOf(period: AchuPeriod, now: number): number {
    const local = new Date(now + KST);
    const day0 = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate()) - KST;
    if (period === 'DAY') return day0;
    if (period === 'WEEK') return day0 - ((local.getUTCDay() + 6) % 7) * DAY;
    return Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), 1) - KST;
}

/** 다음 기간이 시작하는 순간 */
function endOf(period: AchuPeriod, from: number): number {
    if (period === 'DAY') return from + DAY;
    if (period === 'WEEK') return from + 7 * DAY;
    const local = new Date(from + KST);
    return Date.UTC(local.getUTCFullYear(), local.getUTCMonth() + 1, 1) - KST;
}

/**
 * 받은 최신글 차례대로 고른 시각을 나눠 준다 — 앞 3개는 오늘, 다음 4개는 이번 주 앞날들, 다음 8개는 이번 달 앞날들, 나머지는 지난달.
 * (오늘이 월요일·1일이면 앞날이 없어 그 몫은 바로 이전 기간으로 간다 — 목업이라 그대로 둔다)
 */
function pickTimes(count: number, now: number, skipToday: boolean): number[] {
    const day0 = startOf('DAY', now);
    const week0 = startOf('WEEK', now);
    const month0 = startOf('MONTH', now);
    const out: number[] = [];
    for (let i = 0; i < count; i++) {
        if (i < 3) out.push(skipToday ? day0 - (i + 1) * 2 * HOUR : now - (i * 3 + 1) * HOUR);
        else if (i < 7) out.push(Math.max(week0, day0 - DAY) - (i - 3) * 0.7 * DAY + 10 * HOUR);
        else if (i < 15) out.push(Math.max(month0, week0 - DAY) - (i - 7) * 1.3 * DAY + 14 * HOUR);
        else out.push(month0 - (i - 14) * DAY);
    }
    return out;
}

/** 목록 한 쪽 */
export async function achuListMock(query: AchuQuery, latest: (size: number) => Promise<PostFeedPage>): Promise<AchuListResponse> {
    const flags = mockFlags();
    const [feed] = await Promise.all([latest(30), mockDelay()]);
    if (flags.has('achu-fail')) throw MOCK_ERRORS.internal();

    const now = Date.now();
    const from = startOf(query.period, now);
    const to = endOf(query.period, from);
    const times = pickTimes(feed.items.length, now, flags.has('achu-day-empty'));
    const all: AchuItem[] = flags.has('achu-empty')
        ? []
        : feed.items.map((post, i) => ({ id: `mock-h${post.id}`, post, title: null, summary: null, imageUrl: null, pickedAt: new Date(times[i] ?? now).toISOString() }));
    const inPeriod = all
        .filter((h) => {
            const at = Date.parse(h.pickedAt);
            return at >= from && at < to;
        })
        .sort((a, b) => Date.parse(b.pickedAt) - Date.parse(a.pickedAt));

    const size = query.size ?? 20;
    const totalPages = Math.max(1, Math.ceil(inPeriod.length / size));
    const page = Math.min(Math.max(1, query.page), totalPages);
    return {
        items: inPeriod.slice((page - 1) * size, page * size),
        page,
        totalPages,
        totalCount: inPeriod.length,
        hasNext: page < totalPages,
        period: query.period,
        from: new Date(from).toISOString(),
        to: new Date(to).toISOString(),
        weekStart: 'MONDAY',
    };
}
