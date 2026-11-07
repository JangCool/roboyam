import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { RewardEventOperatorRef, RewardLine, RewardMemberRef, RewardRun } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEvent, { type RewardEventProblem } from '~/utils/util-reward-event';

/**
 * 보상 이벤트 관리 화면의 **계산이 낀 문구**(보상 요약·칸 문제·실행 한 줄) — 상수 문구는 컴포넌트가 `t('rewardEvent.…')` 로 부른다(`i18n.md` 3절).
 */
export default class UtilRewardEventMessages {
    /** 회원 이름 — 회원 줄이 없으면(서버 `nickname` null) 「알 수 없는 회원」 */
    static memberName(t: Translate, m: Pick<RewardMemberRef, 'nickname'>): string {
        return m.nickname ?? t('rewardEvent.memberGone');
    }

    /** 운영자 이름 — 없으면(선착순·일괄 자동) 「자동」, 지운 운영자(서버 `name` null)면 「알 수 없는 운영자」 */
    static operatorName(t: Translate, op: RewardEventOperatorRef | null): string {
        if (!op) return t('rewardEvent.auto');
        return op.name ?? t('rewardEvent.operatorGone');
    }

    /** 숫자 + P(로캘 구분자) */
    static points(t: Translate, n: number, locale: string): string {
        return t('rewardEvent.pointsValue', { n: n.toLocaleString(locale) });
    }

    /**
     * 보상 줄 하나의 짧은 이름 — 「1,000P」 · 「닉네임 변경권 ×2」 · 「로보티콘 팩 로보얌 기본 팩」 · 「커피 기프티콘(직접 전달)」.
     * @param {Translate} t - 번역 함수.
     * @param {RewardLine} line - 보상 줄.
     * @param {string} locale - 숫자 표기 언어.
     */
    static line(t: Translate, line: RewardLine, locale: string): string {
        if (line.kind === 'POINT') return UtilRewardEventMessages.points(t, line.points ?? 0, locale);
        if (line.kind === 'SHOP_ITEM') {
            const name = line.itemName ?? t('rewardEvent.reward.itemUnset');
            return (line.quantity ?? 1) > 1 ? t('rewardEvent.reward.itemQty', { name, n: line.quantity ?? 1 }) : name;
        }
        if (line.kind === 'EMOTICON_PACK') return t('rewardEvent.reward.packLine', { name: line.packName ?? t('rewardEvent.reward.packUnset') });
        return t('rewardEvent.reward.manualLine', { name: line.manualName || t('rewardEvent.reward.manualUnset') });
    }

    /** 보상 줄 묶음 요약 — 「1,000P + 한정 프로필 테두리」 */
    static summary(t: Translate, lines: readonly RewardLine[], locale: string): string {
        if (!lines.length) return '—';
        return lines.map((l) => UtilRewardEventMessages.line(t, l, locale)).join(' + ');
    }

    /** 등수가 여럿이면 「1등 3명: …」 줄들, 하나면 요약만 */
    static tiersSummary(t: Translate, tiers: readonly { name: string; quota: number; rewards: RewardLine[] }[], locale: string, withQuota: boolean): string {
        if (tiers.length === 1) return UtilRewardEventMessages.summary(t, tiers[0]!.rewards, locale);
        return tiers.map((tier) => (withQuota ? t('rewardEvent.tierSummary', { name: tier.name, n: tier.quota.toLocaleString(locale), rewards: UtilRewardEventMessages.summary(t, tier.rewards, locale) }) : `${tier.name}: ${UtilRewardEventMessages.summary(t, tier.rewards, locale)}`)).join(' · ');
    }

    /**
     * 칸 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {string} path - 칸 경로(`tiers[0].rewards[1].points`).
     * @param {RewardEventProblem} problem - 문제.
     * @param {string} locale - 숫자 표기 언어.
     */
    static problem(t: Translate, path: string, problem: RewardEventProblem, locale: string): string {
        if (problem === 'REQUIRED') {
            if (path.endsWith('.itemId')) return t('rewardEvent.problem.itemRequired');
            if (path.endsWith('.packId')) return t('rewardEvent.problem.packRequired');
            if (path.endsWith('.rewards')) return t('rewardEvent.problem.rewardsRequired');
            if (path.endsWith('.postRef')) return t('rewardEvent.problem.postRequired');
            return t('rewardEvent.problem.required');
        }
        if (problem === 'TOO_LONG') {
            const max = path === 'name' ? UtilRewardEvent.NAME_MAX : path === 'memo' ? UtilRewardEvent.MEMO_MAX : path.endsWith('.manualName') ? UtilRewardEvent.MANUAL_NAME_MAX : path.endsWith('.manualMethod') ? UtilRewardEvent.MANUAL_METHOD_MAX : UtilRewardEvent.TIER_NAME_MAX;
            return t('rewardEvent.problem.tooLong', { max });
        }
        if (problem === 'OUT_OF_RANGE') {
            const max = path.endsWith('.points') ? UtilRewardEvent.POINT_MAX : path.endsWith('.quantity') ? UtilRewardEvent.QUANTITY_MAX : path.endsWith('activeWithinDays') ? UtilRewardEvent.ACTIVE_DAYS_MAX : UtilRewardEvent.LIMIT_MAX;
            return t('rewardEvent.problem.outOfRange', { min: 1, max: max.toLocaleString(locale) });
        }
        if (problem === 'END_BEFORE_START') return t('rewardEvent.problem.endBeforeStart');
        if (problem === 'URL_FORMAT') return t('rewardEvent.problem.urlFormat');
        if (problem === 'SOURCE_REQUIRED') return t('rewardEvent.problem.sourceRequired');
        return t('rewardEvent.problem.packNotSelectable');
    }

    /** 지급 실행 한 줄의 결과 — 「성공 10 · 실패 1 · 건너뜀 0」 */
    static runResult(t: Translate, run: RewardRun, locale: string): string {
        if (run.kind === 'REVOKE') return t('rewardEvent.run.revokeResult', { n: run.success.toLocaleString(locale), kept: run.skipped.toLocaleString(locale) });
        return t('rewardEvent.run.result', { ok: run.success.toLocaleString(locale), failed: run.failed.toLocaleString(locale), skipped: run.skipped.toLocaleString(locale) });
    }

    /** 사유 칸 문제(5~200자) */
    static reasonProblem(t: Translate, reason: string): string {
        const n = reason.trim().length;
        if (!n) return t('rewardEvent.problem.reasonRequired');
        if (n < UtilRewardEvent.REASON_MIN || n > UtilRewardEvent.REASON_MAX) return t('rewardEvent.problem.reasonLength', { min: UtilRewardEvent.REASON_MIN, max: UtilRewardEvent.REASON_MAX });
        return '';
    }
}
