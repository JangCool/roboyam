// MOCK: 방문 통계 3단계 — 주·월 순방문자(WAU·MAU)와 월별 보기. 서버에 API 가 없어(화면 확정 전, 2026-11-11) 오늘 날짜로 그럴듯한 숫자를 만든다.
// 같은 날·같은 기간이면 늘 같은 숫자(날짜로 씨앗을 만든다). 일부 기간은 「집계 전」(null) — 세기 시작 전 기간을 흉내 낸다.
// 수집 스위치(`collect`)는 부르는 쪽이 실제 서버의 요약 응답에서 받아 넘긴다(꺼진 화면도 확인할 수 있게).
// 서버 API 가 생기면 이 파일과 `useVisitAnalyticsApi`·`useDashboardGadgetApi` 의 MOCK 줄을 지운다.

import type {
    VisitAudience,
    VisitCollect,
    VisitMonthly,
    VisitMonthlyPoint,
    VisitMonthRange,
    VisitPageRow,
    VisitUniqueCompare,
    VisitUniquePeriod,
    VisitUniques,
    VisitUniquesAudience,
} from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

/** 글자 → 0~1 사이 고정 난수(FNV-1a + mulberry32 한 걸음) */
function rand(key: string): number {
    let h = 2166136261;
    for (let i = 0; i < key.length; i++) {
        h ^= key.charCodeAt(i);
        h = Math.imul(h, 16777619);
    }
    let x = (h + 0x6d2b79f5) | 0;
    x = Math.imul(x ^ (x >>> 15), x | 1);
    x ^= x + Math.imul(x ^ (x >>> 7), x | 61);
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
}

/** 1 ± spread 사이 흔들림 */
function jitter(key: string, spread: number): number {
    return 1 + (rand(key) * 2 - 1) * spread;
}

/** 두 날짜 사이 날 수(b − a) */
function daysBetween(a: string, b: string): number {
    return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);
}

/** 대상별 한 주·한 달 순방문자 크기(끝난 기간 기준) */
const SCALE: Record<VisitAudience, { week: number; month: number }> = {
    VISITOR: { week: 6_800, month: 21_500 },
    MEMBER: { week: 940, month: 2_380 },
};

/** 대상별 「집계 전」인 오래된 칸 수(최근 12칸 중) — 회원은 로그인 시각으로 소급할 수 없어 더 늦게 시작한 것처럼 */
const NOT_YET: Record<VisitAudience, { weeks: number; months: number }> = {
    VISITOR: { weeks: 2, months: 3 },
    MEMBER: { weeks: 5, months: 7 },
};

/** 진행 중인 기간의 지난 몫 → 순방문자 몫(같은 사람이 다시 오므로 날 수에 비례하지 않고 덜 는다) */
function uniqueShare(elapsed: number): number {
    return Math.min(1, Math.pow(Math.max(0, elapsed), 0.72));
}

function periodCount(audience: VisitAudience, kind: 'week' | 'month', start: string, index: number, notYet: number): number | null {
    if (index < notYet) return null;
    const growth = 1 + 0.012 * index;
    return Math.round(SCALE[audience][kind] * growth * jitter(`${audience}|${kind}|${start}`, 0.08));
}

function audienceOf(audience: VisitAudience, today: string): VisitUniquesAudience {
    // 주 — 마지막 칸이 이번 주(월요일 시작)
    const thisWeek = UtilVisitAnalytics.weekStart(today);
    const weekElapsed = (daysBetween(thisWeek, today) + 1) / 7;
    const weeks: VisitUniquePeriod[] = Array.from({ length: 12 }, (_, i) => {
        const start = UtilBizApplication.addDays(thisWeek, -7 * (11 - i));
        const partial = i === 11;
        const full = periodCount(audience, 'week', start, i, NOT_YET[audience].weeks);
        return { start, end: UtilBizApplication.addDays(start, 6), count: full === null ? null : partial ? Math.round(full * uniqueShare(weekElapsed)) : full, partial };
    });

    // 달 — 마지막 칸이 이번 달
    const thisMonth = today.slice(0, 7);
    const monthEndDay = UtilVisitAnalytics.monthEnd(thisMonth);
    const monthElapsed = Number(today.slice(8, 10)) / Number(monthEndDay.slice(8, 10));
    const months: VisitUniquePeriod[] = Array.from({ length: 12 }, (_, i) => {
        const ym = UtilVisitAnalytics.addMonths(thisMonth, -(11 - i));
        const partial = i === 11;
        const full = periodCount(audience, 'month', ym, i, NOT_YET[audience].months);
        return { start: `${ym}-01`, end: UtilVisitAnalytics.monthEnd(ym), count: full === null ? null : partial ? Math.round(full * uniqueShare(monthElapsed)) : full, partial };
    });

    const compare = (list: VisitUniquePeriod[], elapsed: number, kind: string): VisitUniqueCompare => {
        const current = list[list.length - 1]!;
        const previous = list[list.length - 2]!;
        const same = previous.count === null ? null : Math.round(previous.count * uniqueShare(elapsed) * jitter(`${audience}|same|${kind}|${previous.start}`, 0.06));
        return { current, previous, previousSamePoint: same };
    };

    return { week: compare(weeks, weekElapsed, 'week'), month: compare(months, monthElapsed, 'month'), weeks, months };
}

/** 사이트가 문을 연 달처럼 보일 첫 달 — 그 앞 달은 0(수집 전) */
const SITE_START = '2025-03';

/** 월별 기간 많이 본 페이지의 모양(조회수 몫) — 묶음 주소는 서버가 제목을 주지 않는다(null) */
const TOP_PAGES: { path: string; kind: VisitPageRow['kind']; title: string | null; titleEn: string | null; share: number }[] = [
    { path: '/posts/{id}', kind: 'POST', title: null, titleEn: null, share: 0.41 },
    { path: '/', kind: 'HOME', title: '홈', titleEn: 'Home', share: 0.17 },
    { path: '/boards/free', kind: 'BOARD', title: '자유게시판', titleEn: 'Free board', share: 0.08 },
    { path: '/group-buys/{id}', kind: 'GROUP_BUY', title: null, titleEn: null, share: 0.06 },
    { path: '/boards/humor', kind: 'BOARD', title: '유머', titleEn: 'Humor', share: 0.045 },
    { path: '/hotdeals', kind: 'HOTDEAL', title: '핫딜', titleEn: 'Hot deals', share: 0.04 },
    { path: '/users/{id}', kind: 'OTHER', title: null, titleEn: null, share: 0.028 },
    { path: '/groups/{id}', kind: 'GROUP', title: null, titleEn: null, share: 0.022 },
    { path: '/search', kind: 'SEARCH', title: '검색', titleEn: 'Search', share: 0.018 },
    { path: '/{unknown}', kind: 'OTHER', title: null, titleEn: null, share: 0.006 },
];

/** 유입 분류 몫 */
const REFERRER_SHARES = { SEARCH: 0.38, DIRECT: 0.27, SOCIAL: 0.18, OTHER_SITE: 0.12, CAMPAIGN: 0.05 } as const;

function monthPoint(ym: string, current: string, today: string): VisitMonthlyPoint {
    const partial = ym === current;
    if (ym < SITE_START) return { month: ym, visitorsDailySum: 0, newVisitors: 0, pageViews: 0, uniqueVisitors: null, partial };
    const index = UtilVisitAnalytics.monthCount({ from: SITE_START, to: ym }) - 1;
    const season = 1 + 0.07 * Math.sin(((Number(ym.slice(5, 7)) - 3) / 12) * Math.PI * 2);
    const elapsed = partial ? Number(today.slice(8, 10)) / Number(UtilVisitAnalytics.monthEnd(ym).slice(8, 10)) : 1;
    // 문을 연 첫 달은 작게 시작해 몇 달 동안 가파르게 는다
    const ramp = Math.min(1, 0.35 + index * 0.13);
    const fullSum = 38_000 * ramp * (1 + 0.025 * index) * season * jitter(`m|${ym}`, 0.07);
    const dailySum = Math.round(fullSum * elapsed);
    // 월 순방문자는 최근 12개월만(그 앞은 집계 전 — 세기 시작 전). 한 사람이 그 달 평균 1.8일쯤 온 것처럼(일별 합 ÷ 1.75~2.05)
    const counted = ym > UtilVisitAnalytics.addMonths(current, -12);
    const mau = (fullSum / (1.75 + 0.3 * rand(`u|${ym}`))) * uniqueShare(elapsed);
    return {
        month: ym,
        visitorsDailySum: dailySum,
        newVisitors: Math.round(dailySum * (0.24 + 0.05 * rand(`n|${ym}`)) * (1.4 - 0.4 * ramp)),
        pageViews: Math.round(dailySum * (2.7 + 0.5 * rand(`p|${ym}`))),
        uniqueVisitors: counted ? Math.round(mau) : null,
        partial,
    };
}

export default class VisitAnalyticsMock {
    /** `GET …/visits/uniques` 의 목업 */
    static uniques(collect: VisitCollect, now = Date.now()): VisitUniques {
        const today = UtilVisitAnalytics.today(now);
        return {
            asOf: new Date(now).toISOString(),
            collect,
            audiences: { VISITOR: audienceOf('VISITOR', today), MEMBER: audienceOf('MEMBER', today) },
        };
    }

    /** 대시보드 「방문 현황」 한 줄 — 이번 주·이번 달 순방문자(방문자) */
    static visitorsThisWeekAndMonth(now = Date.now()): { visitorsThisWeek: number | null; visitorsThisMonth: number | null } {
        const v = audienceOf('VISITOR', UtilVisitAnalytics.today(now));
        return { visitorsThisWeek: v.week.current.count, visitorsThisMonth: v.month.current.count };
    }

    /** `GET …/visits/monthly?from&to` 의 목업 — 기간은 서버처럼 고친 값(맞바꾸기·이번 달까지·36개월) */
    static monthly(range: VisitMonthRange, collect: VisitCollect, now = Date.now()): VisitMonthly {
        const today = UtilVisitAnalytics.today(now);
        const current = UtilVisitAnalytics.thisMonth(now);
        const fixed = UtilVisitAnalytics.normalizeMonths(range.from, range.to, now).range;
        const n = UtilVisitAnalytics.monthCount(fixed);
        const months = Array.from({ length: n }, (_, i) => monthPoint(UtilVisitAnalytics.addMonths(fixed.from, i), current, today));
        const totals = months.reduce((a, m) => ({ visitorsDailySum: a.visitorsDailySum + m.visitorsDailySum, newVisitors: a.newVisitors + m.newVisitors, pageViews: a.pageViews + m.pageViews }), { visitorsDailySum: 0, newVisitors: 0, pageViews: 0 });
        const pages: VisitPageRow[] = totals.pageViews
            ? TOP_PAGES.map((p) => ({ path: p.path, kind: p.kind, title: p.title, titleEn: p.titleEn, views: Math.round(totals.pageViews * p.share * jitter(`pg|${p.path}|${fixed.from}|${fixed.to}`, 0.05)) })).sort((a, b) => b.views - a.views)
            : [];
        const sessions = Math.round(totals.visitorsDailySum * 1.18);
        const groups: VisitMonthly['referrers']['groups'] = collect.referrers && sessions ? Object.entries(REFERRER_SHARES).map(([group, share]) => ({ group: group as keyof typeof REFERRER_SHARES, visits: Math.round(sessions * share) })) : [];
        const referrers: VisitMonthly['referrers'] = { total: groups.reduce((a, g) => a + g.visits, 0), groups };
        return { range: fixed, asOf: new Date(now).toISOString(), collect, totals, months, pages, pagesTotalViews: totals.pageViews, referrers };
    }
}
