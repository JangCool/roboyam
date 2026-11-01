import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { AdminDealDetail, HotdealAuthor, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DomainMatch, DomainProblem, HotdealSettingProblem, HotdealSettingSpec, TextProblem } from '~/utils/util-hotdeal';

/** 계산이 낀 핫딜 문구 — 가격·배송·기간·종료 경위·검사 문구(상수 문구는 화면이 `t('hotdeal.…')` 를 바로 부른다) */
export default class UtilHotdealMessages {
    /** 원 단위 금액 — 「12,900원」 */
    static won(t: Translate, locale: string, n: number | null | undefined): string {
        if (n === null || n === undefined) return '—';
        return t('hotdeal.won', { n: n.toLocaleString(locale) });
    }

    /** 배송비 — 0 무료 · null 모름 · 금액 */
    static shipping(t: Translate, locale: string, fee: number | null): string {
        if (fee === null) return t('hotdeal.shippingUnknown');
        if (fee === 0) return t('hotdeal.shippingFree');
        return UtilHotdealMessages.won(t, locale, fee);
    }

    /** 딜 기간 — 시작·마감이 둘 다 없으면 「정하지 않음」 */
    static period(t: Translate, time: DisplayTime, deal: PostDeal): string {
        if (!deal.startsAt && !deal.endsAt) return t('hotdeal.periodNone');
        if (!deal.startsAt) return t('hotdeal.periodUntil', { end: time.shortDateTime(deal.endsAt) });
        if (!deal.endsAt) return t('hotdeal.periodFrom', { start: time.shortDateTime(deal.startsAt) });
        return t('hotdeal.periodRange', { start: time.shortDateTime(deal.startsAt), end: time.shortDateTime(deal.endsAt) });
    }

    /** 작성자 이름 — 탈퇴는 표시 */
    static author(t: Translate, author: HotdealAuthor): string {
        return author.withdrawn ? t('hotdeal.withdrawnName', { name: author.nickname }) : author.nickname;
    }

    /**
     * 상태와 종료 경위 줄 — 종료면 누가·언제(+ 운영진 사유), 의심이면 언제 의심이 됐고 언제 자동 종료되는지, 기간 지남이면 마감 시각, 진행이면 「아직 진행 중」을 누른 시각.
     */
    static stateLines(t: Translate, time: DisplayTime, detail: AdminDealDetail): string[] {
        const deal = detail.row.deal;
        const lines: string[] = [];
        if (deal.view === 'ENDED') {
            const by = deal.endedBy ?? 'AUTO';
            const at = time.full(deal.endedAt);
            if (by === 'AUTO') lines.push(t('hotdeal.endedAuto', { at }));
            else lines.push(t(`hotdeal.endedBy_${by}`, { at, name: detail.endedByName ?? '—' }));
            if (detail.endReason) lines.push(t('hotdeal.endedReason', { reason: detail.endReason }));
        } else if (deal.view === 'FLAGGED') {
            lines.push(t('hotdeal.flaggedAt', { at: time.full(deal.flaggedAt), n: deal.endRequests.total }));
            lines.push(deal.autoEndAt ? t('hotdeal.autoEndAt', { at: time.full(deal.autoEndAt) }) : t('hotdeal.autoEndOff'));
        } else if (deal.view === 'EXPIRED') {
            lines.push(t('hotdeal.expiredAt', { at: time.full(deal.endsAt) }));
        } else if (detail.keptAt) {
            lines.push(t('hotdeal.keptAt', { at: time.full(detail.keptAt) }));
        }
        return lines;
    }

    /** 사유·메모 칸 문구 */
    static textProblem(t: Translate, problem: TextProblem | null, max: number): string {
        return problem === 'TOO_LONG' ? t('hotdeal.textTooLong', { max }) : '';
    }

    static domainProblem(t: Translate, problem: DomainProblem | null): string {
        if (problem === 'REQUIRED') return t('hotdeal.domain.required');
        if (problem === 'INVALID') return t('hotdeal.domain.invalid');
        if (problem === 'TOO_LONG') return t('hotdeal.domain.tooLong');
        return '';
    }

    /** 링크 규칙 시험 결과 한 줄 */
    static matchText(t: Translate, result: DomainMatch): string {
        if (!result.host) return t('hotdeal.domain.testInvalid');
        const m = result.match;
        if (!m) return t('hotdeal.domain.testNone', { host: result.host });
        const via = m.domain === result.host ? t('hotdeal.domain.testExact') : t('hotdeal.domain.testSuffix', { domain: m.domain });
        const rule = m.rule === 'BLOCK' ? t('hotdeal.domain.testBlock') : m.rule === 'AFFILIATE' ? t('hotdeal.domain.testAffiliate') : t('hotdeal.domain.testCommonOnly');
        return `${via} ${rule}`;
    }

    static settingProblem(t: Translate, problem: HotdealSettingProblem | null, spec: HotdealSettingSpec): string {
        if (problem === 'REQUIRED') return t('hotdeal.settings.required');
        if (problem === 'NOT_INTEGER') return t('hotdeal.settings.notInteger');
        if (problem === 'OUT_OF_RANGE') return t('hotdeal.settings.outOfRange', { min: spec.min, max: spec.max });
        return '';
    }
}
