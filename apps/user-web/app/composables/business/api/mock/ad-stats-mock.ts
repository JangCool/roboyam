// MOCK: 광고 성과 API 의 목업 — `useBusinessApi.stats`·`adStatsSummary` 가 부른다. 서버가 붙으면 이 파일을 지운다(설계 노트 「광고 성과 — 목업 설계」 2026-11-02).
//
// ── API 후보(정본 후보 — 화면 확정 뒤 계약 `docs/features/ad-performance-contract.md` 로 옮긴다) ─
//   GET /api/business/applications/{id}/stats?from=YYYY-MM-DD&to=YYYY-MM-DD → `AdApplicationStats`(본인 건만 — 없으면 404 `BIZ_APPLICATION_NOT_FOUND`)
//       from·to 가 빠지면 시작 날짜 ~ min(끝 날짜, 오늘). days = 기간 안 모든 날짜(0 포함) · byPlacement = 0 인 자리는 빼고 노출 차례 · byVersion = 버전 차례 ·
//       downtimes = 기간과 겹치는 정지·내림 구간(처리 기록에서 계산 — 정지·내림 중에는 공개 노출에 안 나가 집계 0)
//   GET /api/business/ads/stats-summary → `BizAdStatsSummary`(진행 중 · 정지 중이고 노출 자리가 있는 건 최대 5개, 최근 7일 = 오늘 포함 · spark 오래된 차례)
//
// ── 장부는 실제, 숫자만 목업 ─
//   · 한 건 성과: 실제 상세(`GET /api/business/applications/{id}`)를 받아 종류·광고 유형·핫딜 자리·내림 까닭을 본 뒤 숫자를 만든다(그 건이 없으면 실제 404 가 그대로 난다).
//   · 센터 요약: 실제 목록(`bucket=RUNNING`)에서 진행 중(SCHEDULED·RUNNING) · 일시 정지 중(TAKEN_DOWN·ADVERTISER_PAUSE, 끝 날짜 전)이고 노출 자리가 있는 건만 골라 숫자를 붙인다
//     (운영 긴급 내림·사업자 정지·중단 건은 뺀다 — 가정).
//   · 숫자는 건 ID 를 씨앗으로 한 결정적 의사 난수(새로 고쳐도 같다). 오늘은 한국 시각으로 지난 만큼만(「오늘은 집계 중」). 지금 정지·내림 중이면 그 시각부터 0.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   ad-stats-empty      숫자 모두 0(「아직 집계된 노출이 없어요」)          ad-stats-fail      성과 읽기 실패(500 → 다시 시도)
//   ad-stats-paused     기간 가운데 일시 정지 2일 + 긴급 내림 1일 띠         ad-stats-versions  소재 버전 2개(가운데 날부터 v2 = 지금 버전)
//   ad-stats-hotdeal    자리 HOTDEAL_TOP 하나뿐
//   기본 = 자리 3~4개(광고 유형별) · 버전 1개. 센터 요약에도 empty·fail 이 먹는다.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    AdApplicationStats,
    AdStatCounts,
    AdStatDay,
    AdStatDowntime,
    AdStatPlacement,
    AdStatPlacementRow,
    AdStatVersionRow,
    BizAdStatsSummary,
    BizAdStatsSummaryItem,
    BizApplicationDetail,
    BizApplicationPage,
    BizApplicationSummary,
    BizTakedownCause,
} from '~/composables/business/api/useBusinessApi.types';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBusiness from '~/utils/util-business';

/** 광고 유형·핫딜 자리별로 나가는 집계 자리(목업 가정 — 실제는 노출 자리 설정을 따른다)와 하루 기본 노출 */
const PLACEMENTS: Record<string, { placements: AdStatPlacement[]; base: number }> = {
    WIDE: { placements: ['HOME', 'PAGE', 'BOARD'], base: 1800 },
    STRIP: { placements: ['BOARD', 'POST_INLINE', 'PAGE', 'HOME'], base: 1400 },
    SQUARE: { placements: ['HOME', 'POST_INLINE', 'PAGE'], base: 1100 },
    SMALL: { placements: ['BOARD', 'PAGE', 'HOME'], base: 700 },
    WING: { placements: ['WING_LEFT', 'WING_RIGHT', 'BOARD', 'PAGE'], base: 900 },
    TEXT_LINE: { placements: ['BOARD_LIST', 'BOARD', 'POST_INLINE'], base: 650 },
    HOME_TOP: { placements: ['HOTDEAL_TOP'], base: 850 },
    CATEGORY_TOP: { placements: ['HOTDEAL_CATEGORY'], base: 420 },
};

/** 문자열 → 32비트 씨앗(FNV-1a) */
function hash(text: string): number {
    let h = 2166136261;
    for (let i = 0; i < text.length; i++) h = Math.imul(h ^ text.charCodeAt(i), 16777619);
    return h >>> 0;
}

/** 씨앗 하나의 0~1 값(mulberry32 한 걸음) — 같은 글자면 늘 같은 값 */
function rand(key: string): number {
    let t = (hash(key) + 0x6d2b79f5) >>> 0;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}

/** 한국 날짜 + 시각 → ISO 순간 */
function kstAt(ymd: string, hour: number): string {
    return new Date(`${ymd}T${String(hour).padStart(2, '0')}:00:00+09:00`).toISOString();
}

/** 오늘(한국)이 지난 비율 0~1 */
function todayFraction(now: number): number {
    const kstMs = now + 9 * 3_600_000;
    return (kstMs % 86_400_000) / 86_400_000;
}

/** 그날 00~24시 중 멈춘 구간 [from, to) 와 겹치는 몫 0~1 — 아직 멈춰 있으면(to 없음) 지금까지 */
function downFraction(ymd: string, down: AdStatDowntime, now: number): number {
    const start = Date.parse(kstAt(ymd, 0));
    const end = start + 86_400_000;
    const from = Date.parse(down.from);
    const to = down.to ? Date.parse(down.to) : now;
    const overlap = Math.min(end, to) - Math.max(start, from);
    return overlap > 0 ? overlap / 86_400_000 : 0;
}

function kindKey(a: Pick<BizApplicationSummary, 'adType' | 'placement'>): string {
    if (a.adType) return a.adType;
    return a.placement && a.placement !== 'NONE' ? a.placement : 'HOME_TOP';
}

/** 그 건의 지금 정지·내림 — 실제 상세의 까닭·정지 시각·처리 기록에서 */
function currentDowntime(a: BizApplicationDetail): AdStatDowntime | null {
    const cause: BizTakedownCause | null = a.takedownCause ?? null;
    const down = a.status === 'TAKEN_DOWN' || (a.status === 'CANCELED' && cause === 'ADVERTISER_STOP');
    if (!down) return null;
    const last = [...a.history].reverse().find((h) => h.action === 'PAUSED' || h.action === 'STOPPED' || h.action === 'TAKEN_DOWN');
    const from = a.pause?.pausedAt ?? a.pause?.stoppedAt ?? last?.at ?? null;
    return from ? { from, to: null, cause: cause ?? 'EMERGENCY' } : null;
}

/** 스위치 `ad-stats-paused` — 기간 가운데 일시 정지 2일(15시 ~ 다음 날 18시) + 사흘 뒤 긴급 내림 1일(10시 ~ 16시) */
function fakeDowntimes(dates: string[]): AdStatDowntime[] {
    if (dates.length === 0) return [];
    const m = Math.max(0, Math.floor(dates.length / 2) - 2);
    const out: AdStatDowntime[] = [];
    const d0 = dates[m]!;
    const d1 = dates[Math.min(m + 1, dates.length - 1)]!;
    out.push({ from: kstAt(d0, 15), to: d1 === d0 ? kstAt(d0, 20) : kstAt(d1, 18), cause: 'ADVERTISER_PAUSE' });
    const e = dates[m + 3];
    if (e) out.push({ from: kstAt(e, 10), to: kstAt(e, 16), cause: 'EMERGENCY' });
    return out;
}

interface Generated {
    days: AdStatDay[];
    byPlacement: AdStatPlacementRow[];
    byVersion: AdStatVersionRow[];
}

/**
 * 날짜·자리별 숫자를 만든다 — 자리 합 = 날 합 = 버전 합(같은 숫자를 세 갈래로 묶는다).
 * @param {string} seed - 건 ID.
 * @param {string[]} dates - 기간의 모든 날짜.
 * @param {string} today - 한국 오늘.
 * @param {string | null} versionSplit - 이 날부터 v2(없으면 버전 1개).
 */
function generate(seed: string, key: string, dates: string[], today: string, downtimes: AdStatDowntime[], now: number, empty: boolean, hotdealOnly: boolean, versionSplit: string | null): Generated {
    const conf = hotdealOnly ? PLACEMENTS.HOME_TOP! : (PLACEMENTS[key] ?? PLACEMENTS.SQUARE!);
    const base = conf.base * (0.75 + rand(`${seed}:base`) * 0.6);
    const ctrBase = 0.006 + rand(`${seed}:ctr`) * 0.018;
    const weights = conf.placements.map((p, i) => (i === 0 ? 1 : 0.25 + rand(`${seed}:w:${p}`) * 0.55));
    const placementTotals = new Map<AdStatPlacement, AdStatCounts>(conf.placements.map((p) => [p, { impressions: 0, clicks: 0 }]));
    const versionTotals = new Map<number, AdStatCounts>();
    const days: AdStatDay[] = dates.map((date, i) => {
        // 그날 멈춘 몫 — 정지(광고주)가 걸쳤으면 PAUSED, 그 밖의 내림이면 TAKEN_DOWN
        let down = 0;
        let state: AdStatDay['state'] = 'SHOWING';
        for (const d of downtimes) {
            const f = downFraction(date, d, now);
            if (f <= 0) continue;
            down = Math.max(down, f);
            if (d.cause === 'ADVERTISER_PAUSE') state = state === 'SHOWING' ? 'PAUSED' : state;
            else state = 'TAKEN_DOWN';
        }
        const weekday = new Date(`${date}T00:00:00Z`).getUTCDay();
        const weekFactor = weekday === 0 || weekday === 6 ? 0.82 : 1;
        const warmup = i === 0 ? 0.7 : 1;
        const shown = Math.max(0, (date === today ? todayFraction(now) : 1) - down);
        const version = versionSplit && date >= versionSplit ? 2 : 1;
        const day: AdStatDay = { date, state, impressions: 0, clicks: 0 };
        if (empty) return day;
        conf.placements.forEach((p, pi) => {
            const noise = 0.8 + rand(`${seed}:${date}:${p}:i`) * 0.4;
            const impressions = Math.round(base * weights[pi]! * weekFactor * warmup * noise * shown);
            const ctr = ctrBase * (pi === 0 ? 1 : 0.6 + rand(`${seed}:c:${p}`) * 0.8) * (version === 2 ? 1.25 : 1);
            const clicks = Math.round(impressions * ctr * (0.7 + rand(`${seed}:${date}:${p}:c`) * 0.6));
            day.impressions += impressions;
            day.clicks += clicks;
            const pt = placementTotals.get(p)!;
            pt.impressions += impressions;
            pt.clicks += clicks;
        });
        const vt = versionTotals.get(version) ?? { impressions: 0, clicks: 0 };
        vt.impressions += day.impressions;
        vt.clicks += day.clicks;
        versionTotals.set(version, vt);
        return day;
    });
    const byPlacement = [...placementTotals.entries()]
        .map(([placement, c]) => ({ placement, ...c }))
        .filter((r) => r.impressions > 0 || r.clicks > 0)
        .sort((a, b) => b.impressions - a.impressions);
    const versions = versionSplit ? [1, 2] : [1];
    const byVersion: AdStatVersionRow[] = empty
        ? []
        : versions.map((v) => ({
              version: v,
              approvedAt: v === 1 ? kstAt(UtilAdStats.addDays(dates[0] ?? today, -2), 14) : kstAt(UtilAdStats.addDays(versionSplit!, -1), 16),
              current: v === versions.length,
              ...(versionTotals.get(v) ?? { impressions: 0, clicks: 0 }),
          }));
    return { days, byPlacement, byVersion };
}

/** 한 건 성과 — `loadDetail` 은 실제 상세 API(장부는 실제) */
export async function mockAdStats(id: string, range: { from: string; to: string } | undefined, loadDetail: () => Promise<BizApplicationDetail>): Promise<AdApplicationStats> {
    const detail = await loadDetail();
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('ad-stats-fail')) throw MOCK_ERRORS.internal();
    const now = Date.now();
    const today = UtilAdStats.today(now);
    const r = range ?? UtilAdStats.range(detail.startDate, detail.endDate, today);
    const dates = UtilAdStats.dates(r.from, r.to);
    const downtimes: AdStatDowntime[] = flags.has('ad-stats-paused') ? fakeDowntimes(dates) : [];
    const current = currentDowntime(detail);
    if (current) downtimes.push(current);
    const versionSplit = flags.has('ad-stats-versions') && dates.length >= 2 ? dates[Math.floor(dates.length / 2)]! : null;
    const g = generate(id, kindKey(detail), dates, today, downtimes, now, flags.has('ad-stats-empty'), flags.has('ad-stats-hotdeal'), versionSplit);
    return mockClone({
        range: r,
        asOf: new Date(now - (now % 600_000)).toISOString(),
        totals: UtilAdStats.sum(g.days),
        days: g.days,
        byPlacement: g.byPlacement,
        byVersion: g.byVersion,
        downtimes: downtimes.sort((a, b) => Date.parse(a.from) - Date.parse(b.from)),
    });
}

/** 센터 요약에 올릴 건인가 — 진행 중 · 일시 정지 중(끝 날짜 전) · 노출 자리 있음 */
function summaryTarget(a: BizApplicationSummary, now: number): boolean {
    if (!UtilAdStats.exposes(a.kind, a.placement)) return false;
    if (a.status === 'SCHEDULED') return a.phase === 'RUNNING';
    return a.status === 'TAKEN_DOWN' && a.takedownCause === 'ADVERTISER_PAUSE' && !UtilBusiness.periodOver(a.endDate, now);
}

/** 사업자 센터 「진행 중 광고」 — `loadRunning` 은 실제 목록 API(`bucket=RUNNING`) */
export async function mockAdStatsSummary(loadRunning: () => Promise<BizApplicationPage>): Promise<BizAdStatsSummary> {
    const page = await loadRunning();
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('ad-stats-fail')) throw MOCK_ERRORS.internal();
    const now = Date.now();
    const today = UtilAdStats.today(now);
    const from = UtilAdStats.addDays(today, -(UtilAdStats.SPARK_DAYS - 1));
    const items: BizAdStatsSummaryItem[] = page.items
        .filter((a) => summaryTarget(a, now))
        .slice(0, UtilAdStats.SUMMARY_MAX)
        .map((a) => {
            const dates = UtilAdStats.dates(from, today);
            // 정지 중이면 정지 시각을 모른다(목록엔 없음) — 어제 낮부터 멈춘 것으로 친다(가정)
            const downtimes: AdStatDowntime[] = a.status === 'TAKEN_DOWN' ? [{ from: kstAt(UtilAdStats.addDays(today, -1), 13), to: null, cause: 'ADVERTISER_PAUSE' }] : [];
            const g = generate(a.id, kindKey(a), dates, today, downtimes, now, flags.has('ad-stats-empty'), flags.has('ad-stats-hotdeal'), null);
            // 시작 전·끝난 뒤 날은 0
            const spark = g.days.map((d) => (d.date < a.startDate || d.date > a.endDate ? { ...d, impressions: 0, clicks: 0 } : d));
            return {
                id: a.id,
                title: a.title,
                kind: a.kind,
                adType: a.adType,
                placement: a.placement,
                status: a.status,
                phase: a.phase,
                takedownCause: a.takedownCause ?? null,
                startDate: a.startDate,
                endDate: a.endDate,
                last7: UtilAdStats.sum(spark),
                spark,
            };
        });
    return mockClone({ asOf: new Date(now - (now % 600_000)).toISOString(), items });
}
