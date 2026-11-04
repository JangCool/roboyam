import type { WeekHourCell } from '#layers/chart/components/WeekHourHeatmap.types';
import type { VisitTimeCell } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

/** 요일·시간대에서 볼 숫자 */
export type VisitTimeMetric = 'visitors' | 'pageViews';

/** 가장 붐비는·한산한 칸 */
export interface VisitTimePeak {
    weekday: number;
    hour: number;
    /** 평균 */
    value: number;
}

/**
 * 요일·시간대 계산 — 입력만 보고 답하는 순수 함수. 서버는 칸마다 **합과 더한 칸 수(`days`)** 를 주고, 평균은 화면이 나눈다.
 * 오늘의 지금 시간(집계 중)과 그 뒤는 서버가 칸에 넣지 않는다(`days` 도 세지 않음) — 덜 찬 칸이 평균을 끌어내리지 않게.
 */
export default class UtilVisitTime {
    /** 칸의 평균 — 더한 칸이 없으면 null */
    static avg(cell: VisitTimeCell, metric: VisitTimeMetric): number | null {
        return UtilVisitAnalytics.average(cell[metric], cell.days);
    }

    /** 히트맵 칸(줄 = 월~일 0~6, 열 = 0~23시, 값 = 평균) */
    static heatCells(cells: VisitTimeCell[], metric: VisitTimeMetric): WeekHourCell[] {
        return cells.map((c) => ({ row: c.weekday - 1, col: c.hour, value: UtilVisitTime.avg(c, metric) }));
    }

    /**
     * 가장 붐비는(`max`)·한산한(`min`) 칸 — 평균이 있는 칸만. 모두 0 이면(방문 없음) null.
     * 한산한 때는 평균이 0 보다 큰 칸 가운데 가장 작은 칸(밤새 0 인 칸만 고르지 않게) — 0 보다 큰 칸이 없으면 null.
     */
    static peak(cells: VisitTimeCell[], metric: VisitTimeMetric, kind: 'max' | 'min'): VisitTimePeak | null {
        let best: VisitTimePeak | null = null;
        for (const c of cells) {
            const v = UtilVisitTime.avg(c, metric);
            if (v === null || v <= 0) continue;
            if (!best || (kind === 'max' ? v > best.value : v < best.value)) best = { weekday: c.weekday, hour: c.hour, value: v };
        }
        return best;
    }

    /** 시간대별 평균(0~23시) — 그 시의 모든 요일 합 ÷ 모든 요일 칸 수. 칸이 없으면 null */
    static hourAverages(cells: VisitTimeCell[], metric: VisitTimeMetric): (number | null)[] {
        return Array.from({ length: 24 }, (_, hour) => {
            const row = cells.filter((c) => c.hour === hour);
            return UtilVisitAnalytics.average(
                row.reduce((a, c) => a + c[metric], 0),
                row.reduce((a, c) => a + c.days, 0),
            );
        });
    }

    /** 요일별 하루 평균(월~일) — 그 요일 24칸 평균의 합(= 그 요일 하루 평균). 칸이 하나도 없으면 null */
    static weekdayAverages(cells: VisitTimeCell[], metric: VisitTimeMetric): (number | null)[] {
        return Array.from({ length: 7 }, (_, i) => {
            const avgs = cells.filter((c) => c.weekday === i + 1).map((c) => UtilVisitTime.avg(c, metric));
            if (avgs.every((v) => v === null)) return null;
            return avgs.reduce<number>((a, v) => a + (v ?? 0), 0);
        });
    }
}
