// MOCK: 팁·강좌·사용기 묶음 화면(`/tips`)의 태그 — 서버에 태그가 없다(tips-contract 「미확정」, 2026-11-07). 나머지 칸은 모두 서버 응답 그대로다.
// 서버 응답의 글 줄에 게시판별 태그 묶음에서 1~3개를 지어 붙이고, 머리 몫에 태그 칩(달린 횟수 차례)을 만든다.
// 태그가 정해지면 이 파일과 `useTipsApi` 의 MOCK 줄을 지우고 `useTipsApi.types.ts` 의 태그 칸을 서버 모양에 맞춘다.

import type { TipsOverview, TipsPost, TipsPostPage, TipsTag } from '~/composables/tips/api/useTipsApi.types';

/** 서버가 주는 글 줄 — 태그 없음 */
export type TipsPostWire = Omit<TipsPost, 'tags'>;
/** 서버가 주는 머리 몫 — 태그 없음 */
export interface TipsOverviewWire extends Omit<TipsOverview, 'headline' | 'mostViewed' | 'tags'> {
    headline: TipsPostWire[];
    mostViewed: TipsPostWire[];
}
/** 서버가 주는 최신 글 한 쪽 — 태그 없음 */
export interface TipsPostPageWire extends Omit<TipsPostPage, 'items'> {
    items: TipsPostWire[];
}

const TAG_SIZE = 10;

/** 지어낸 태그 — 게시판마다 묶음 */
const TAG_POOL: Record<string, string[]> = {
    tip: ['ROS2', '파이썬', '아두이노', '라즈베리파이', '초보 가이드', '3D 프린팅', '센서', '모터 제어'],
    review: ['로봇청소기', '교육용 로봇', '드론', 'AI 스피커', '개봉기', '가성비', '장기 사용기'],
};
const TAG_FALLBACK = ['로봇', 'AI', '입문'];

/** 글 ID → 고정된 작은 수(같은 글은 늘 같은 태그) */
function hashOf(id: string): number {
    let h = 0;
    for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
    return h;
}

function poolOf(slug: string): string[] {
    return TAG_POOL[slug] ?? TAG_FALLBACK;
}

function withTags(row: TipsPostWire): TipsPost {
    if (row.hiddenReason || row.adult) return { ...row, tags: [] };
    const h = hashOf(row.id);
    const pool = poolOf(row.board.slug);
    // 1~3개 — 같은 태그가 겹치면 하나로
    const tags = Array.from(new Set(Array.from({ length: (h % 3) + 1 }, (_, i) => pool[(h + i * 7) % pool.length]!)));
    return { ...row, tags };
}

export default class TipsMock {
    /**
     * 머리 몫에 태그를 붙인다 — 칩은 고른 게시판(전체면 묶음 게시판들)의 태그 묶음을, 헤드라인·많이 본 글에 달린 횟수 차례로 위 10개.
     * @param {TipsOverviewWire} res - 서버 응답.
     * @param {string} board - 고른 게시판 slug(빈 글자 = 전체).
     * @returns {TipsOverview} 태그가 붙은 머리 몫.
     */
    static overview(res: TipsOverviewWire, board: string): TipsOverview {
        const headline = res.headline.map(withTags);
        const mostViewed = res.mostViewed.map(withTags);
        const slugs = res.boards.some((b) => b.slug === board) ? [board] : res.boards.map((b) => b.slug);
        const counts = new Map<string, number>();
        for (const slug of slugs) for (const name of poolOf(slug)) counts.set(name, 0);
        for (const row of [...headline, ...mostViewed]) for (const name of row.tags) counts.set(name, (counts.get(name) ?? 0) + 1);
        const tags: TipsTag[] = [...counts.entries()]
            .map(([name, count]) => ({ name, count }))
            .sort((x, y) => y.count - x.count)
            .slice(0, TAG_SIZE);
        return { ...res, headline, mostViewed, tags };
    }

    /**
     * 최신 글 한 쪽에 태그를 붙인다.
     * @param {TipsPostPageWire} res - 서버 응답.
     * @returns {TipsPostPage} 태그가 붙은 한 쪽.
     */
    static posts(res: TipsPostPageWire): TipsPostPage {
        return { ...res, items: res.items.map(withTags) };
    }
}
