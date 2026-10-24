import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { PopularLimit, PopularPeriod, PopularRankingView } from '~/composables/content/api/usePopularApi.types';
import UtilPopular, { type PopularFieldProblem, type PopularMemoProblem } from '~/utils/util-popular';

/**
 * 인기글 화면의 계산이 낀 문구 — 범위 힌트·칸 문제·기간 범위·빈 순위 안내. 상수 문구는 화면이 `t('popular.*')` 로 부른다.
 */
export default class UtilPopularMessages {
    /** 숫자 한 개를 소수 자리에 맞춰 — 범위 힌트용 */
    private static num(value: number, locale: string): string {
        return value.toLocaleString(locale, { maximumFractionDigits: 3 });
    }

    /** 칸 옆 범위 힌트 — 「0~100 · 소수 3자리」 · 「1~10」 */
    static range(t: Translate, limit: PopularLimit, locale: string): string {
        const range = t('popular.rangeHint', { min: UtilPopularMessages.num(limit.min, locale), max: UtilPopularMessages.num(limit.max, locale) });
        return limit.decimals > 0 ? `${range} · ${t('popular.decimalsHint', { n: limit.decimals })}` : range;
    }

    /** 칸 아래 문제 문구 */
    static problem(t: Translate, problem: PopularFieldProblem, limit: PopularLimit, locale: string): string {
        switch (problem) {
            case 'REQUIRED':
                return t('popular.problemRequired');
            case 'NOT_NUMBER':
                return t('popular.problemNotNumber');
            case 'NOT_INTEGER':
                return t('popular.problemNotInteger');
            case 'TOO_MANY_DECIMALS':
                return t('popular.problemDecimals', { n: limit.decimals });
            case 'ALL_WEIGHTS_ZERO':
                return t('popular.problemAllWeightsZero');
            default:
                return t('popular.problemRange', { min: UtilPopularMessages.num(limit.min, locale), max: UtilPopularMessages.num(limit.max, locale) });
        }
    }

    /** 기간 범위 한 줄 — 「10.24(토)」 · 「10.20(월) ~ 10.26(일)」(끝은 미포함이라 1밀리초 앞 날짜) */
    static periodRange(time: DisplayTime, view: Pick<PopularRankingView, 'from' | 'to'>): string {
        const first = time.dateDay(view.from);
        const last = time.dateDay(UtilPopular.lastInstant(view.to));
        return first === last ? first : `${first} ~ ${last}`;
    }

    /** 기간마다 무엇을 모았는지 한 줄 */
    static periodLead(t: Translate, period: PopularPeriod): string {
        if (period === 'REALTIME') return t('popular.leadRealtime');
        if (period === 'YESTERDAY') return t('popular.leadYesterday');
        return t('popular.leadWeek');
    }

    /** 빈 순위 안내 — 아직 계산 전이면 그것을, 아니면 기간마다 */
    static emptyRanking(t: Translate, period: PopularPeriod, computedAt: string | null): { title: string; text: string } {
        if (computedAt === null) return { title: t('popular.emptyNotComputed'), text: t('popular.emptyNotComputedHint') };
        if (period === 'REALTIME') return { title: t('popular.emptyRealtime'), text: t('popular.emptyRealtimeHint') };
        if (period === 'YESTERDAY') return { title: t('popular.emptyYesterday'), text: t('popular.emptyYesterdayHint') };
        return { title: t('popular.emptyWeek'), text: t('popular.emptyWeekHint') };
    }

    /** 빼기 메모 문제 */
    static memoProblem(t: Translate, problem: PopularMemoProblem | null): string {
        if (problem === 'REQUIRED') return t('popular.memoRequired');
        if (problem === 'TOO_LONG') return t('popular.memoTooLong', { max: UtilPopular.MEMO_MAX });
        return '';
    }

    /** 계산 시각 한 줄 — 「순위 계산 3분 전」 · 「아직 계산 전이에요」 */
    static computedAt(t: Translate, time: DisplayTime, computedAt: string | null): string {
        return computedAt ? t('popular.computedAt', { at: time.ago(computedAt) }) : t('popular.notComputed');
    }
}
