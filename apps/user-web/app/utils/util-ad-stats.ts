import UtilTime, { type TimeValues } from '#layers/display-time/utils/util-time';
import type { AdStatCounts, BizApplicationDetail, BizApplicationKind, DealPlacement } from '~/composables/business/api/useBusinessApi.types';

/** 신청 건 상세의 「성과」 칸 — 칸 없음 · 시작 전 한 줄 · 숫자 */
export type AdStatsSection = 'NONE' | 'UPCOMING' | 'SHOW';

/**
 * 광고 성과(노출·클릭) 화면 계산 — 사업자 신청 건 상세의 「성과」 칸 · 사업자 센터 「진행 중 광고」 카드.
 * 숫자 기준(서버 집계 shared V118 `ad_daily_stats`): 같은 방문자·같은 소재 버전·같은 자리는 한국 하루 노출 1번·클릭 1번, 봇 제외.
 * 클릭은 그날 노출 여부를 보지 않아 클릭률이 드물게 100% 를 넘을 수 있다 — 화면은 그대로 계산해 보인다.
 */
export default class UtilAdStats {
    /** 「성과」 칸이 붙는 자리 — 상세 화면의 `id`(사업자 센터 카드가 `#performance` 로 건너온다) */
    static readonly ANCHOR = 'performance';

    /** 사업자 센터 카드의 최대 줄 수 · 미니 차트 날 수 */
    static readonly SUMMARY_MAX = 5;
    static readonly SPARK_DAYS = 7;
    /** 집계 하루의 시간대(서버 `ServiceZone`) */
    static readonly SERVICE_ZONE = 'Asia/Seoul';

    /** 지금의 한국 날짜(`YYYY-MM-DD`) — 집계 하루는 표시 시간대와 무관하게 늘 한국 달력이다 */
    static today(now: number = Date.now()): string {
        return UtilTime.serviceDayOf(now) ?? '';
    }

    /** 한국 날짜에 일수를 더한다(시간대와 무관한 달력 계산) */
    static addDays(ymd: string, days: number): string {
        const d = new Date(Date.UTC(+ymd.slice(0, 4), +ymd.slice(5, 7) - 1, +ymd.slice(8, 10) + days));
        return d.toISOString().slice(0, 10);
    }

    /** `from` ~ `to`(끝 포함)의 모든 날짜 — 거꾸로면 빈 목록 */
    static dates(from: string, to: string): string[] {
        const out: string[] = [];
        for (let d = from; d <= to && out.length < 400; d = UtilAdStats.addDays(d, 1)) out.push(d);
        return out;
    }

    /** 클릭률(%, 소수 한 자리) — 노출 0 이면 null */
    static ctr(c: AdStatCounts): number | null {
        if (c.impressions <= 0) return null;
        return Math.round((c.clicks / c.impressions) * 1000) / 10;
    }

    /** 클릭률 글자 — 「1.8%」 · 노출 0 이면 「—」 */
    static ctrText(c: AdStatCounts): string {
        const v = UtilAdStats.ctr(c);
        return v === null ? '—' : `${v.toFixed(1)}%`;
    }

    /** 숫자 글자 — 「12,345」(금액 글자 `UtilBusinessMessages.won` 과 같은 자리 구분) */
    static count(n: number): string {
        return Math.max(0, Math.round(n)).toLocaleString('en-US');
    }

    /** 노출 자리가 있는 종류인가 — 광고 둘 · 핫딜 노출 · 노출 자리를 같이 산 사업자 핫딜 글 */
    static exposes(kind: BizApplicationKind, placement: DealPlacement | null): boolean {
        if (kind === 'HOTDEAL_POST') return placement !== null && placement !== 'NONE';
        return true;
    }

    /**
     * 상세의 「성과」 칸을 어떻게 보이나 — 노출 자리가 있고 결제(입금)까지 마친 건만.
     * 시작했으면(진행·정지·내림·끝남·시작 뒤 취소) 숫자, 예약(시작 전)이면 한 줄, 그 밖(결제 전·시작 전 취소·노출 자리 없는 핫딜 글)은 칸 없음.
     * @param {boolean} paid - 처리 기록에 결제·입금 확인이 있다.
     * @param {string} today - 한국 날짜(`UtilAdStats.today`).
     */
    static section(a: Pick<BizApplicationDetail, 'kind' | 'placement' | 'status' | 'phase' | 'startDate'>, paid: boolean, today: string): AdStatsSection {
        if (!paid || !UtilAdStats.exposes(a.kind, a.placement)) return 'NONE';
        if (a.startDate <= today) return a.status === 'SCHEDULED' && a.phase === 'UPCOMING' ? 'UPCOMING' : 'SHOW';
        return a.status === 'SCHEDULED' ? 'UPCOMING' : 'NONE';
    }

    /** 성과를 셀 기간 — 시작 날짜 ~ min(끝 날짜, 오늘) (기간이 30일까지라 고르개 없이 건의 기간 전체) */
    static range(startDate: string, endDate: string, today: string): { from: string; to: string } {
        return { from: startDate, to: endDate < today ? endDate : today };
    }

    /**
     * 차트 아래 짧은 날짜 — 집계 날짜(한국 달력)에 표시 값의 날짜 형식만 입힌다(`10.28` · `10/28` …). 시간대는 늘 한국(표시 시간대로 옮기면 하루가 어긋난다).
     * @param {TimeValues} display - 표시 값(`useDisplayValues()`).
     */
    static shortDay(ymd: string, display: TimeValues): string {
        return UtilTime.of({ ...display, zoneId: UtilAdStats.SERVICE_ZONE }).shortDate(`${ymd}T12:00:00+09:00`);
    }

    /** 노출·클릭 더하기 */
    static sum(rows: readonly AdStatCounts[]): AdStatCounts {
        return rows.reduce<AdStatCounts>((acc, r) => ({ impressions: acc.impressions + r.impressions, clicks: acc.clicks + r.clicks }), { impressions: 0, clicks: 0 });
    }
}
