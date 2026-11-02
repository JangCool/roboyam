// MOCK: 광고 성과 API(`GET /api/admin/ads/{id}/stats` · `/api/admin/ads/stats` · `/api/admin/ads/stats/bookings`)의 목업 — `useAdApi.ts` 가 부른다.
// 서버가 붙으면 이 폴더(`ad/api/mock/`)와 함께 지운다. admin-web 에는 공용 `mock-util.ts` 가 없어 스위치 읽기·지연을 이 파일 안에 둔다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   ad-stats-empty      숫자 모두 0(「아직 집계된 노출이 없어요」)
//   ad-stats-fail       세 API 모두 500(ErrorState → 다시 시도)
//   ad-stats-paused     한 건 성과: 기간 가운데 광고주 일시 정지 2일 + 긴급 내림 1일(그날 10:00~18:00) 띠
//   ad-stats-versions   한 건 성과: 소재 버전 2개(승인된 버전이 하나뿐이면 기간 가운데에 버전 2를 지어 붙인다)
//   ad-stats-hotdeal    한 건 성과: 자리가 HOTDEAL_TOP 하나뿐
//   기본 = 유형마다 자리 1~4개(배너 3~4개) · 버전 = 실제 소재 중 승인된 것(없으면 1개).
//
// ── 목업 규칙(가정) ─
//   · 장부는 실제 API, 숫자만 목업: 건별 표·광고주 순위는 실제 `list`(유형 8개를 0쪽 100줄씩 불러 합친다 — 유형마다 100건 넘는 장부는 앞 100건만) 중 시작한 건,
//     한 건 성과는 실제 `detail`(기간·상태·내림·소재 버전)에 숫자를 붙인다. 장부에 시작한 건이 하나도 없으면 가짜 줄 10개(`mock-ad-*`, 서랍은 404).
//   · 숫자는 건 ID·날짜·자리로 씨앗을 둔 결정적 의사 난수(새로 고쳐도 같다). 유형별 하루 기준 노출 × 건 계수(0.55~1.45) × 주말 0.82 × 날 흔들림(±25%).
//   · 오늘은 지난 시간만큼만(「집계 중」). 정지·내림 중인 시간은 0 — 내린 건(`TAKEN_DOWN`)은 줄의 `lastActionAt`(상세는 `takedown.at`)부터 지금까지 멈춘 것으로 본다. 취소 건은 `lastActionAt` 에서 끝난다.
//   · 장부 줄은 30초 동안 다시 부르지 않는다(거르기를 바꿀 때마다 8번씩 부르지 않게).

import { ApiError } from '~/composables/common/useApiClient';
import type {
    AdApplicationStats,
    AdBookingDetail,
    AdBookingPage,
    AdBookingQuery,
    AdBookingRow,
    AdStatAdvertiserRow,
    AdStatCounts,
    AdStatDay,
    AdStatDayState,
    AdStatDowntime,
    AdStatPlacement,
    AdStatPlacementRow,
    AdStatRange,
    AdStatsBookingPage,
    AdStatsBookingQuery,
    AdStatsBookingRow,
    AdStatsOverview,
    AdStatsQuery,
    AdStatTypeRow,
    AdStatVersionRow,
    AdType,
} from '~/composables/ad/api/useAdApi.types';
import UtilAd from '~/utils/util-ad';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplication from '~/utils/util-biz-application';

/** 목업이 실제로 부르는 장부 API — `useAdApi` 가 넘긴다 */
export interface AdStatsLedger {
    list: (query: AdBookingQuery) => Promise<AdBookingPage>;
    detail: (id: string) => Promise<AdBookingDetail>;
}

const DAY_MS = 86_400_000;
const LEDGER_TTL_MS = 30_000;

/* ── 작은 도구(user-web `mock-util.ts` 와 같은 동작) ── */

function mockFlags(): Set<string> {
    const out = new Set<string>();
    if (typeof window === 'undefined') return out;
    for (const value of new URLSearchParams(window.location.search).getAll('mock')) {
        for (const name of value.split(',')) if (name.trim()) out.add(name.trim());
    }
    return out;
}

function mockDelay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 400));
}

function failIfAsked(flags: Set<string>): void {
    if (flags.has('ad-stats-fail')) throw new ApiError({ status: 500, code: 'INTERNAL', message: '서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' });
}

/** 글자 → 0~1 결정적 난수(FNV-1a + mulberry32 한 걸음) */
function rand(...parts: (string | number)[]): number {
    const s = parts.join('|');
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) {
        h ^= s.charCodeAt(i);
        h = Math.imul(h, 16777619);
    }
    let a = (h + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    a = (t ^ (t >>> 14)) >>> 0;
    return a / 4294967296;
}

const between = (r: number, lo: number, hi: number) => lo + r * (hi - lo);

/** 유형별 하루 기준 노출 · 클릭률 범위(%) · 노출 자리 */
const BASE: Record<AdType, { imp: number; ctr: [number, number]; places: AdStatPlacement[] }> = {
    WIDE_BANNER: { imp: 1800, ctr: [0.4, 1.2], places: ['HOME', 'PAGE', 'BOARD'] },
    STRIP_BANNER: { imp: 1300, ctr: [0.3, 1.0], places: ['HOME', 'PAGE', 'BOARD', 'POST_INLINE'] },
    SQUARE_BANNER: { imp: 950, ctr: [0.5, 1.4], places: ['HOME', 'PAGE', 'POST_INLINE'] },
    SMALL_BANNER: { imp: 650, ctr: [0.3, 0.9], places: ['HOME', 'PAGE', 'BOARD'] },
    WING_BANNER: { imp: 700, ctr: [0.2, 0.7], places: ['WING_LEFT', 'WING_RIGHT'] },
    TEXT_LINE: { imp: 520, ctr: [0.8, 2.2], places: ['BOARD_LIST', 'POST_INLINE', 'HOME'] },
    HOTDEAL_TOP: { imp: 420, ctr: [2.0, 5.0], places: ['HOTDEAL_TOP'] },
    HOTDEAL_CATEGORY: { imp: 260, ctr: [2.5, 6.0], places: ['HOTDEAL_CATEGORY'] },
};

/** 숫자를 지을 때 보는 건 모양 */
interface Model {
    id: string;
    type: AdType;
    startAt: number;
    /** 노출이 끝나는 시각(끝 시각 · 취소 시각 중 이른 것) */
    endAt: number;
    downtimes: AdStatDowntime[];
    places: AdStatPlacement[];
}

function modelOf(row: AdBookingRow, downtimes: AdStatDowntime[], places = BASE[row.type].places): Model {
    const end = Date.parse(row.period.endAt);
    return {
        id: row.id,
        type: row.type,
        startAt: Date.parse(row.period.startAt),
        endAt: row.status === 'CANCELED' ? Math.min(end, Date.parse(row.lastActionAt)) : end,
        downtimes,
        places,
    };
}

/** 줄만 보고 아는 내림 — 내린 건은 마지막 처리 시각부터 지금까지 */
function rowDowntimes(row: AdBookingRow): AdStatDowntime[] {
    return row.status === 'TAKEN_DOWN' && row.takedownCause ? [{ from: row.lastActionAt, to: null, cause: row.takedownCause }] : [];
}

/** 하루 숫자(자리별) + 그날 상태 */
function dayOf(model: Model, ymd: string, now: number, empty: boolean): { state: AdStatDayState; perPlace: AdStatPlacementRow[] } {
    const dayStart = Date.parse(UtilBizApplication.kstMidnight(ymd));
    const dayEnd = dayStart + DAY_MS;
    const open = Math.max(dayStart, model.startAt);
    const close = Math.min(dayEnd, model.endAt, now);
    let live = Math.max(0, close - open);
    let state = 'SHOWING' as AdStatDayState;
    for (const dt of model.downtimes) {
        const from = Date.parse(dt.from);
        const to = dt.to ? Date.parse(dt.to) : Number.POSITIVE_INFINITY;
        if (Math.min(dayEnd, to, now) > Math.max(dayStart, from)) state = dt.cause === 'ADVERTISER_PAUSE' && state !== 'TAKEN_DOWN' ? 'PAUSED' : 'TAKEN_DOWN';
        const cut = Math.min(close, to) - Math.max(open, from);
        if (cut > 0) live -= cut;
    }
    const frac = Math.max(0, live) / DAY_MS;
    if (empty || frac <= 0) return { state, perPlace: model.places.map((placement) => ({ placement, impressions: 0, clicks: 0 })) };

    const base = BASE[model.type];
    const weekday = new Date(`${ymd}T00:00:00Z`).getUTCDay();
    const total = base.imp * between(rand(model.id, 'f'), 0.55, 1.45) * (weekday === 0 || weekday === 6 ? 0.82 : 1) * between(rand(model.id, ymd), 0.75, 1.25) * frac;
    const weights = model.places.map((p, i) => between(rand(model.id, 'w', p), 0.4, 1.6) * (i === 0 ? 1.8 : 1));
    const sum = weights.reduce((a, b) => a + b, 0);
    const ctr = between(rand(model.id, 'ctr'), base.ctr[0], base.ctr[1]) / 100;
    return {
        state,
        perPlace: model.places.map((placement, i) => {
            const impressions = Math.round((total * weights[i]!) / sum);
            return { placement, impressions, clicks: Math.round(impressions * ctr * between(rand(model.id, ymd, placement, 'c'), 0.6, 1.4)) };
        }),
    };
}

/** 건이 노출됐을 수 있는 날짜 범위와 거르기 범위가 겹치는 날짜들 */
function datesIn(model: Model, range: AdStatRange, now: number): string[] {
    const from = UtilBizApplication.kstDate(new Date(model.startAt).toISOString());
    const last = UtilBizApplication.kstDate(new Date(Math.min(model.endAt, now) - 1).toISOString());
    const a = from > range.from ? from : range.from;
    const b = last < range.to ? last : range.to;
    return a <= b ? UtilAdStats.datesOf({ from: a, to: b }) : [];
}

function add(into: AdStatCounts, c: AdStatCounts): void {
    into.impressions += c.impressions;
    into.clicks += c.clicks;
}

function sumOf(rows: readonly AdStatCounts[]): AdStatCounts {
    const out = { impressions: 0, clicks: 0 };
    for (const r of rows) add(out, r);
    return out;
}

/* ── 장부(실제 `list`) ── */

let ledgerCache: { at: number; rows: Promise<AdBookingRow[]> } | null = null;

function ledgerRows(ledger: AdStatsLedger): Promise<AdBookingRow[]> {
    if (ledgerCache && Date.now() - ledgerCache.at < LEDGER_TTL_MS) return ledgerCache.rows;
    const rows = (async () => {
        const pages = await Promise.all(UtilAd.TYPES.map((type) => ledger.list({ type, page: 0, size: 100 })));
        const started = pages.flatMap((p) => p.items).filter((r) => UtilAdStats.started(r));
        return started.length ? started : fakeRows();
    })();
    const entry = { at: Date.now(), rows };
    ledgerCache = entry;
    rows.catch(() => {
        if (ledgerCache === entry) ledgerCache = null;
    });
    return rows;
}

/** 장부가 비었을 때의 가짜 줄 — 서랍은 404(장부에 없는 ID) */
function fakeRows(now = Date.now()): AdBookingRow[] {
    const names = ['로보마켓', '스마트팩토리랩', '휴머노이드코리아', '드론스쿨', '메카닉스토어', '오토봇부품', '코봇솔루션'];
    const plan: { type: AdType; start: number; days: number; adv: number; down?: AdBookingRow['takedownCause'] }[] = [
        { type: 'WIDE_BANNER', start: -12, days: 30, adv: 0 },
        { type: 'STRIP_BANNER', start: -5, days: 14, adv: 1 },
        { type: 'SQUARE_BANNER', start: -20, days: 14, adv: 2 },
        { type: 'SMALL_BANNER', start: -3, days: 7, adv: 0 },
        { type: 'WING_BANNER', start: -8, days: 30, adv: 3, down: 'ADVERTISER_PAUSE' },
        { type: 'TEXT_LINE', start: -2, days: 7, adv: 4 },
        { type: 'TEXT_LINE', start: -15, days: 30, adv: 5, down: 'EMERGENCY' },
        { type: 'HOTDEAL_TOP', start: -4, days: 7, adv: 6 },
        { type: 'HOTDEAL_CATEGORY', start: -6, days: 7, adv: 1 },
        { type: 'WIDE_BANNER', start: -25, days: 14, adv: 3 },
    ];
    const today = UtilBizApplication.kstToday(now);
    return plan.map((p, i) => {
        const startAt = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(today, p.start));
        const endAt = new Date(Date.parse(startAt) + p.days * DAY_MS).toISOString();
        const ended = Date.parse(endAt) <= now;
        return {
            id: `mock-ad-${i + 1}`,
            type: p.type,
            advertiser: { id: `adv-mock-${p.adv + 1}`, kind: p.adv % 2 ? 'ADVERTISER' : 'BUSINESS', name: names[p.adv]!, businessId: null, maskedRegNo: null, contactMasked: null },
            period: { startAt, endAt },
            status: p.down && !ended ? 'TAKEN_DOWN' : ended ? 'ENDED' : 'RUNNING',
            paymentMethod: i % 3 ? 'ONLINE' : 'MANUAL',
            weight: 5,
            source: i % 3 ? 'APPLICATION' : 'OPERATOR',
            applicationNo: i % 3 ? `BA-MOCK-${String(i + 1).padStart(4, '0')}` : null,
            lastActionAt: new Date(now - (i + 1) * 3 * 3_600_000).toISOString(),
            domain: p.type === 'HOTDEAL_TOP' || p.type === 'HOTDEAL_CATEGORY' ? 'HOTDEAL' : 'AD',
            takedownCause: p.down && !ended ? p.down : null,
        };
    });
}

function filtered(rows: AdBookingRow[], query: AdStatsQuery): AdBookingRow[] {
    return rows.filter((r) => (!query.type || r.type === query.type) && (!query.advertiserId || r.advertiser.id === query.advertiserId));
}

/** 건 하나의 기간 안 합계와 자리별 */
function countsOf(row: AdBookingRow, range: AdStatRange, now: number, empty: boolean): { totals: AdStatCounts; perPlace: Map<AdStatPlacement, AdStatCounts>; perDay: Map<string, AdStatCounts>; touched: boolean } {
    const model = modelOf(row, rowDowntimes(row));
    const dates = datesIn(model, range, now);
    const totals = { impressions: 0, clicks: 0 };
    const perPlace = new Map<AdStatPlacement, AdStatCounts>();
    const perDay = new Map<string, AdStatCounts>();
    for (const ymd of dates) {
        const day = dayOf(model, ymd, now, empty);
        const sum = sumOf(day.perPlace);
        add(totals, sum);
        perDay.set(ymd, sum);
        for (const p of day.perPlace) {
            const into = perPlace.get(p.placement) ?? { impressions: 0, clicks: 0 };
            add(into, p);
            perPlace.set(p.placement, into);
        }
    }
    return { totals, perPlace, perDay, touched: dates.length > 0 };
}

const byImpressions = (a: AdStatCounts, b: AdStatCounts) => b.impressions - a.impressions || b.clicks - a.clicks;

/* ── 운영자 성과 ── */

export async function mockAdStatsOverview(ledger: AdStatsLedger, query: AdStatsQuery): Promise<AdStatsOverview> {
    const flags = mockFlags();
    await mockDelay();
    failIfAsked(flags);
    const all = await ledgerRows(ledger);
    const rows = filtered(all, query);
    const now = Date.now();
    const empty = flags.has('ad-stats-empty');
    const range = { from: query.from, to: query.to };

    const days = new Map<string, AdStatCounts>(UtilAdStats.datesOf(range).map((d) => [d, { impressions: 0, clicks: 0 }]));
    const byType = new Map<AdType, AdStatTypeRow>();
    const byPlace = new Map<AdStatPlacement, AdStatPlacementRow>();
    const byAdv = new Map<string, AdStatAdvertiserRow>();
    const totals = { impressions: 0, clicks: 0, bookings: 0 };

    for (const row of rows) {
        const c = countsOf(row, range, now, empty);
        if (!c.touched) continue;
        const shown = c.totals.impressions > 0 ? 1 : 0;
        add(totals, c.totals);
        totals.bookings += shown;
        for (const [d, v] of c.perDay) add(days.get(d)!, v);
        for (const [placement, v] of c.perPlace) {
            const into = byPlace.get(placement) ?? { placement, impressions: 0, clicks: 0 };
            add(into, v);
            byPlace.set(placement, into);
        }
        const t = byType.get(row.type) ?? { type: row.type, impressions: 0, clicks: 0, bookings: 0 };
        add(t, c.totals);
        t.bookings += shown;
        byType.set(row.type, t);
        const a = byAdv.get(row.advertiser.id) ?? { advertiser: { id: row.advertiser.id, name: row.advertiser.name, kind: row.advertiser.kind }, impressions: 0, clicks: 0, bookings: 0 };
        add(a, c.totals);
        a.bookings += shown;
        byAdv.set(row.advertiser.id, a);
    }

    const found = query.advertiserId ? all.find((r) => r.advertiser.id === query.advertiserId)?.advertiser : null;
    const nonZero = <T extends AdStatCounts>(list: T[]) => list.filter((r) => r.impressions > 0 || r.clicks > 0).sort(byImpressions);
    return {
        asOf: new Date(now).toISOString(),
        range,
        advertiser: found ? { id: found.id, name: found.name, kind: found.kind } : null,
        totals,
        days: [...days].map(([date, v]) => ({ date, ...v })),
        byType: nonZero([...byType.values()]),
        byPlacement: nonZero([...byPlace.values()]),
        topAdvertisers: nonZero([...byAdv.values()]).slice(0, 10),
    };
}

export async function mockAdStatsBookings(ledger: AdStatsLedger, query: AdStatsBookingQuery): Promise<AdStatsBookingPage> {
    const flags = mockFlags();
    await mockDelay();
    failIfAsked(flags);
    const rows = filtered(await ledgerRows(ledger), query);
    const now = Date.now();
    const empty = flags.has('ad-stats-empty');
    const range = { from: query.from, to: query.to };

    const items: AdStatsBookingRow[] = [];
    for (const row of rows) {
        const c = countsOf(row, range, now, empty);
        if (c.touched) items.push({ ...row, ...c.totals });
    }
    const sign = query.dir === 'asc' ? -1 : 1;
    items.sort((a, b) => {
        if (query.sort === 'ctr') {
            const x = UtilAdStats.ctr(a);
            const y = UtilAdStats.ctr(b);
            if (x === null || y === null) return x === y ? 0 : x === null ? 1 : -1; // 노출 0 은 늘 뒤
            return sign * (y - x) || byImpressions(a, b);
        }
        return sign * (b[query.sort] - a[query.sort]) || byImpressions(a, b);
    });
    const size = Math.min(100, Math.max(1, query.size));
    return { items: items.slice(query.page * size, query.page * size + size), total: items.length };
}

/* ── 한 건 성과 ── */

export async function mockAdStats(ledger: AdStatsLedger, id: string, range?: AdStatRange): Promise<AdApplicationStats> {
    const flags = mockFlags();
    const [detail] = await Promise.all([ledger.detail(id).catch(async (e: unknown) => {
        // 가짜 줄(장부가 비었을 때)은 상세가 없다 — 줄 모양으로 대신한다
        const fake = id.startsWith('mock-ad-') ? (await ledgerRows(ledger)).find((r) => r.id === id) : undefined;
        if (!fake) throw e;
        return fake;
    }), mockDelay()]);
    failIfAsked(flags);
    const now = Date.now();
    const empty = flags.has('ad-stats-empty');
    const full = detail as Partial<AdBookingDetail> & AdBookingRow;
    const span = range ?? UtilAdStats.bookingRange(detail.period, now);
    const asOf = new Date(now).toISOString();
    if (!span) {
        const start = UtilBizApplication.kstDate(detail.period.startAt);
        return { range: { from: start, to: start }, asOf, totals: { impressions: 0, clicks: 0 }, days: [], byPlacement: [], byVersion: [], downtimes: [] };
    }
    const dates = UtilAdStats.datesOf(span);

    // 내림 — 상세의 `takedown.at`(없으면 줄 기준) + 스위치의 정지·긴급 내림
    const downtimes: AdStatDowntime[] = full.takedown && detail.status === 'TAKEN_DOWN' ? [{ from: full.takedown.at, to: null, cause: full.takedown.cause }] : rowDowntimes(detail);
    if (flags.has('ad-stats-paused') && dates.length >= 2) {
        const k = Math.max(0, Math.floor(dates.length / 2) - 1);
        const pauseTo = dates[k + 2] ? UtilBizApplication.kstMidnight(dates[k + 2]!) : new Date(Date.parse(UtilBizApplication.kstMidnight(dates[k + 1]!)) + DAY_MS).toISOString();
        downtimes.push({ from: UtilBizApplication.kstMidnight(dates[k]!), to: pauseTo, cause: 'ADVERTISER_PAUSE' });
        const em = dates[k + 3] ?? dates[0]!;
        const emStart = Date.parse(UtilBizApplication.kstMidnight(em));
        downtimes.push({ from: new Date(emStart + 10 * 3_600_000).toISOString(), to: new Date(emStart + 18 * 3_600_000).toISOString(), cause: 'EMERGENCY' });
        downtimes.sort((a, b) => a.from.localeCompare(b.from));
    }

    const places: AdStatPlacement[] = flags.has('ad-stats-hotdeal') ? ['HOTDEAL_TOP'] : BASE[detail.type].places;
    const model = modelOf(detail, downtimes, places);

    // 소재 버전 — 승인된 것만, 오래된 차례. 그날 승인된 버전이 그날부터 나간다
    let versions: AdStatVersionRow[] = (full.creatives ?? [])
        .filter((c) => c.approvedAt)
        .sort((a, b) => a.version - b.version)
        .map((c) => ({ version: c.version, approvedAt: c.approvedAt, current: c.current, impressions: 0, clicks: 0 }));
    if (!versions.length) versions = [{ version: 1, approvedAt: null, current: true, impressions: 0, clicks: 0 }];
    if (flags.has('ad-stats-versions') && versions.length < 2) {
        const mid = dates[Math.floor(dates.length / 2)]!;
        const first = versions[0]!;
        versions = [
            { ...first, current: false, approvedAt: first.approvedAt ?? new Date(model.startAt - DAY_MS).toISOString() },
            { version: first.version + 1, approvedAt: new Date(Date.parse(UtilBizApplication.kstMidnight(mid)) + 10 * 3_600_000).toISOString(), current: true, impressions: 0, clicks: 0 },
        ];
    }
    const versionOn = (ymd: string): AdStatVersionRow => {
        let hit = versions[0]!;
        for (const v of versions) if (v.approvedAt && UtilBizApplication.kstDate(v.approvedAt) <= ymd) hit = v;
        return hit;
    };

    const live = new Set(datesIn(model, span, now));
    const perPlace = new Map<AdStatPlacement, AdStatPlacementRow>();
    const days: AdStatDay[] = dates.map((date) => {
        const day = live.has(date) ? dayOf(model, date, now, empty) : { state: 'SHOWING' as const, perPlace: [] };
        const sum = sumOf(day.perPlace);
        for (const p of day.perPlace) {
            const into = perPlace.get(p.placement) ?? { placement: p.placement, impressions: 0, clicks: 0 };
            add(into, p);
            perPlace.set(p.placement, into);
        }
        add(versionOn(date), sum);
        return { date, state: day.state, ...sum };
    });

    const winFrom = Date.parse(UtilBizApplication.kstMidnight(span.from));
    const winTo = Date.parse(UtilBizApplication.kstMidnight(span.to)) + DAY_MS;
    return {
        range: span,
        asOf,
        totals: sumOf(days),
        days,
        byPlacement: [...perPlace.values()].filter((r) => r.impressions > 0 || r.clicks > 0).sort(byImpressions),
        byVersion: versions,
        downtimes: downtimes.filter((d) => Date.parse(d.from) < winTo && (d.to === null || Date.parse(d.to) > winFrom)),
    };
}
