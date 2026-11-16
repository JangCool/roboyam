import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PointBoardTier, PointBoardTiers, PointBoardTierValues, PointSettingsNumberField } from '~/composables/point/api/usePointApi.types';
import type { PointAdjustMode, PointAmountProblem, PointReasonProblem, PointSettingsProblem } from '~/utils/util-point';
import UtilPoint from '~/utils/util-point';

/**
 * 포인트 화면(정책·내역·조정)의 **계산이 낀 문구** — 문구 본문은 `i18n/locales/{ko,en}/point.ts` 에 있고 여기는 "어느 키를 고를지"와 자리표시자만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`). 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 */
export default class UtilPointMessages {
    /** 칸의 입력 범위 글자 — `0 ~ 10,000` */
    static range(t: Translate, field: PointSettingsNumberField, locale: string): string {
        const [min, max] = UtilPoint.limitsOf(field);
        return t('point.range', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /** 정책 칸 문제를 입력칸 아래 한 줄로 */
    static settingsProblem(t: Translate, problem: PointSettingsProblem, field: PointSettingsNumberField, locale: string): string {
        if (problem === 'REQUIRED') return t('point.problemRequired');
        if (problem === 'NOT_INTEGER') return t('point.problemNotInteger');
        const [min, max] = UtilPoint.limitsOf(field);
        return t('point.problemRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    // ── 게시판 등급(point-board-tier-contract) ─

    /** 등급 칸의 입력 범위 글자 — `0 ~ 10,000` */
    static tierRange(t: Translate, locale: string): string {
        const [min, max] = UtilPoint.TIER_LIMITS;
        return t('point.range', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /** 등급 칸 문제를 칸 아래 한 줄로 */
    static tierProblem(t: Translate, problem: PointSettingsProblem, locale: string): string {
        if (problem === 'REQUIRED') return t('point.problemRequired');
        if (problem === 'NOT_INTEGER') return t('point.problemNotInteger');
        const [min, max] = UtilPoint.TIER_LIMITS;
        return t('point.problemRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /**
     * 등급 하나의 금액·조건을 한 줄로 — 「쓰면 30P · 추천 5개 받으면 +50P · 우수 글 +100P · 100자 이상 · 보너스 24시간 뒤 확정」.
     * 0 인 금액·조건은 뺀다. 보류는 보너스(추천 문턱·우수 글)가 있을 때만. 모두 0 이면 「포인트 없음」.
     * @param likeThreshold - 추천 문턱(정책 `likeBonusThreshold` — 등급이 함께 쓴다)
     */
    static tierSummary(t: Translate, values: PointBoardTierValues, likeThreshold: number, locale: string): string {
        const n = (value: number) => value.toLocaleString(locale);
        const parts: string[] = [];
        if (values.postPoints > 0) parts.push(t('point.tierSummary.write', { n: n(values.postPoints) }));
        if (values.likeBonusPoints > 0) parts.push(t('point.tierSummary.like', { k: n(likeThreshold), n: n(values.likeBonusPoints) }));
        if (values.featuredPoints > 0) parts.push(t('point.tierSummary.featured', { n: n(values.featuredPoints) }));
        if (parts.length === 0) return t('point.tierSummary.none');
        if (values.minLength > 0) parts.push(t('point.tierSummary.minLength', { n: n(values.minLength) }));
        if (values.minImages > 0) parts.push(t('point.tierSummary.minImages', { n: n(values.minImages) }));
        if (values.holdHours > 0 && (values.likeBonusPoints > 0 || values.featuredPoints > 0)) parts.push(t('point.tierSummary.hold', { h: n(values.holdHours) }));
        return parts.join(' · ');
    }

    /** 게시판 설정의 포인트 칸 아래 한 줄 — 끔이면 「포인트를 주지 않아요」, 아니면 「정보 등급 — 쓰면 30P · …」 */
    static boardTierLine(t: Translate, tier: PointBoardTier | null, tiers: PointBoardTiers, likeThreshold: number, locale: string): string {
        if (!tier) return t('point.tierSummary.off');
        return t('point.tierSummary.line', { tier: t(`point.tier.${tier}`), summary: UtilPointMessages.tierSummary(t, tiers[tier], likeThreshold, locale) });
    }

    /** 조정 금액 문제 */
    static amountProblem(t: Translate, problem: PointAmountProblem, locale: string): string {
        if (problem === 'REQUIRED') return t('point.amountRequired');
        if (problem === 'NOT_INTEGER') return t('point.amountNotInteger');
        return t('point.amountRange', { min: UtilPoint.AMOUNT_MIN.toLocaleString(locale), max: UtilPoint.AMOUNT_MAX.toLocaleString(locale) });
    }

    /** 고액 조정 권한이 없는데 문턱(정책 `adjustLargeThreshold`)을 넘었다 — 금액 칸 오류. 문턱 0 은 모든 조정이 고액이다 */
    static largeRequired(t: Translate, threshold: number, locale: string): string {
        if (threshold === 0) return t('point.amountLargeRequiredAll');
        return t('point.amountLargeRequired', { threshold: threshold.toLocaleString(locale) });
    }

    /** 조정 대상의 잔액 — 충전 포인트가 있으면 「적립 N · 충전 N」, 없으면 「잔액 N」 */
    static candidateBalance(t: Translate, candidate: { balance: number; freeBalance: number; paidBalance: number }, locale: string): string {
        if (candidate.paidBalance !== 0) return t('point.adjustBalanceSplit', { free: candidate.freeBalance.toLocaleString(locale), paid: candidate.paidBalance.toLocaleString(locale) });
        return t('point.adjustBalance', { balance: candidate.balance.toLocaleString(locale) });
    }

    /** 조정 사유 문제 */
    static reasonProblem(t: Translate, problem: PointReasonProblem): string {
        return problem === 'REQUIRED' ? t('point.reasonRequired') : t('point.reasonTooLong', { max: UtilPoint.REASON_MAX });
    }

    /** 조정 확인 대화상자 본문 — 차감이 잔액보다 크면 실제로 빠질 양도 함께 말한다 */
    static adjustConfirm(t: Translate, name: string, mode: PointAdjustMode, amount: number, applied: number, locale: string): string {
        if (mode === 'GRANT') return t('point.confirmGrant', { name, amount: amount.toLocaleString(locale) });
        const base = t('point.confirmDeduct', { name, amount: amount.toLocaleString(locale) });
        return -applied !== amount ? `${base} ${t('point.confirmClipped', { applied: Math.abs(applied).toLocaleString(locale) })}` : base;
    }

    /** 조정 결과 토스트 — 서버가 준 실제 반영값(`applied`) 기준 */
    static adjusted(t: Translate, name: string, applied: number, locale: string): string {
        if (applied > 0) return t('point.adjustedGrant', { name, applied: applied.toLocaleString(locale) });
        if (applied < 0) return t('point.adjustedDeduct', { name, applied: Math.abs(applied).toLocaleString(locale) });
        return t('point.adjustedNone', { name });
    }
}
