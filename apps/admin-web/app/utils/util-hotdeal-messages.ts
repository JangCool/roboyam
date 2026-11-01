import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { AdminDealDetail, AffiliateMode, DealRevisionChange, HotdealAuthor, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealSuspicion } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import type { DomainChainMatch, DomainMatch, DomainProblem, HotdealSettingProblem, HotdealSettingSpec, KeyParamProblem, SiteProgramProblem, SiteQueryProblem, SiteTemplateProblem, TextProblem } from '~/utils/util-hotdeal';
import UtilHotdeal from '~/utils/util-hotdeal';

/** 계산이 낀 핫딜 문구 — 가격·배송·기간·종료 경위·수정 이력 값·검사 문구(상수 문구는 화면이 `t('hotdeal.…')` 를 바로 부른다) */
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

    /** 딜 기간 — 마감이 있으면 「…까지」, 없으면 기간 종료 예정(`autoExpireAt`) 또는 「정하지 않음」 */
    static period(t: Translate, time: DisplayTime, deal: PostDeal): string {
        if (deal.endsAt) return t('hotdeal.periodUntil', { end: time.shortDateTime(deal.endsAt) });
        if (deal.autoExpireAt) return t('hotdeal.periodAutoExpire', { at: time.shortDateTime(deal.autoExpireAt) });
        return t('hotdeal.periodNone');
    }

    /** 작성자 이름 — 탈퇴는 표시 */
    static author(t: Translate, author: HotdealAuthor): string {
        return author.withdrawn ? t('hotdeal.withdrawnName', { name: author.nickname }) : author.nickname;
    }

    /**
     * 상태와 종료 경위 줄 — 종료면 누가·언제(+ 운영진 사유, 작성자 종료면 되돌리기 기한 D2), 의심이면 언제 의심이 됐고 언제 자동 종료되는지, 기간 지남이면 마감 시각,
     * 진행이면 「아직 진행 중」을 누른 시각. 끝에 keep 횟수(`keepCount/keepMax`)를 늘 붙인다.
     */
    static stateLines(t: Translate, time: DisplayTime, detail: AdminDealDetail, now: number = Date.now()): string[] {
        const deal = detail.row.deal;
        const lines: string[] = [];
        if (deal.view === 'ENDED') {
            const by = deal.endedBy ?? 'AUTO';
            const at = time.full(deal.endedAt);
            if (by === 'AUTO') lines.push(t('hotdeal.endedAuto', { at }));
            else lines.push(t(`hotdeal.endedBy_${by}`, { at, name: detail.endedByName ?? '—' }));
            if (detail.endReason) lines.push(t('hotdeal.endedReason', { reason: detail.endReason }));
            if (by === 'AUTHOR') lines.push(UtilHotdeal.authorCanReopen(deal.reopenUntil, now) ? t('hotdeal.authorReopenUntil', { at: time.full(deal.reopenUntil) }) : t('hotdeal.authorReopenGone'));
            else lines.push(t('hotdeal.managerOnlyReopen'));
        } else if (deal.view === 'FLAGGED') {
            lines.push(t('hotdeal.flaggedAt', { at: time.full(deal.flaggedAt), n: deal.endRequests.total }));
            lines.push(deal.autoEndAt ? t('hotdeal.autoEndAt', { at: time.full(deal.autoEndAt) }) : t('hotdeal.autoEndOff'));
        } else if (deal.view === 'EXPIRED') {
            lines.push(deal.endsAt ? t('hotdeal.expiredAt', { at: time.full(deal.endsAt) }) : t('hotdeal.autoExpiredAt', { at: time.full(deal.autoExpireAt) }));
        } else if (detail.keptAt) {
            lines.push(t('hotdeal.keptAt', { at: time.full(detail.keptAt) }));
        }
        if (deal.keepCount > 0 || deal.view === 'FLAGGED') lines.push(UtilHotdealMessages.keepLine(t, deal));
        return lines;
    }

    /** keep 횟수 한 줄 — 한도 없음 · N/M번 · 한도에 닿음(운영자 확인 중) */
    static keepLine(t: Translate, deal: Pick<PostDeal, 'keepCount' | 'keepMax'>): string {
        if (deal.keepMax <= 0) return t('hotdeal.keepCountNoLimit', { n: deal.keepCount });
        if (UtilHotdeal.keepLimitReached(deal.keepCount, deal.keepMax)) return t('hotdeal.keepLimitReached', { n: deal.keepCount, max: deal.keepMax });
        return t('hotdeal.keepCount', { n: deal.keepCount, max: deal.keepMax });
    }

    /** 수정 이력 칸 값 하나 — 칸마다 저장 모양에 맞춰 푼다(없으면 「없음」) */
    static revisionValue(t: Translate, locale: string, time: DisplayTime, field: DealRevisionChange['field'], value: DealRevisionChange['before']): string {
        if (field === 'shippingFee') return value === null || typeof value === 'number' ? UtilHotdealMessages.shipping(t, locale, value) : String(value);
        if (value === null || value === '') return t('hotdeal.revision.none');
        if (field === 'dealPrice' || field === 'normalPrice') return typeof value === 'number' ? UtilHotdealMessages.won(t, locale, value) : String(value);
        if (field === 'endsAt') return typeof value === 'string' ? time.full(value) : String(value);
        if (field === 'stockState') return t(`hotdeal.stockState.${String(value)}`);
        if (field === 'priceBasisKind') return t(`hotdeal.priceBasisKind.${String(value)}`);
        if (field === 'affiliate') return value === true ? t('hotdeal.revision.yes') : t('hotdeal.revision.no');
        return String(value);
    }

    /** 의심 후보 이름 — 회원 후보 「회원 · 판매처」, 판매처 후보 「판매처 (호스트)」 */
    static suspicionTitle(t: Translate, s: DealSuspicion): string {
        return s.kind === 'MEMBER' ? t('hotdeal.suspicion.drawerTitle', { name: s.member.nickname, store: s.storeName }) : t('hotdeal.suspicion.storeDrawerTitle', { store: s.storeName, host: s.host });
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

    static keyParamProblem(t: Translate, problem: KeyParamProblem | null, name: string): string {
        if (problem === 'INVALID') return t('hotdeal.domain.keyParamInvalid', { name });
        if (problem === 'TRACKING') return t('hotdeal.domain.keyParamTracking', { name });
        if (problem === 'DUPLICATE') return t('hotdeal.domain.keyParamDuplicate', { name });
        if (problem === 'TOO_MANY') return t('hotdeal.domain.keyParamTooMany', { max: UtilHotdeal.KEY_PARAMS_MAX });
        return '';
    }

    /** 링크 규칙 시험 결과 한 단계 */
    static matchText(t: Translate, result: DomainMatch): string {
        if (!result.host) return t('hotdeal.domain.testInvalid');
        const m = result.match;
        if (!m) return t('hotdeal.domain.testNone', { host: result.host });
        const via = m.domain === result.host ? t('hotdeal.domain.testExact', { host: result.host }) : t('hotdeal.domain.testSuffix', { host: result.host, domain: m.domain });
        const rule = m.rule === 'BLOCK' ? t('hotdeal.domain.testBlock') : m.rule === 'AFFILIATE' ? t('hotdeal.domain.testAffiliate') : t('hotdeal.domain.testCommonOnly');
        return `${via} ${rule}`;
    }

    /** 체인 전체 판정 한 줄 — 단계가 하나면 그 단계 문구, 여럿이면 「최종 판정」 */
    static chainVerdict(t: Translate, chain: DomainChainMatch): string {
        if (chain.verdict === 'INVALID') return t('hotdeal.domain.testInvalid');
        if (chain.hops.length === 1) return UtilHotdealMessages.matchText(t, chain.hops[0] as DomainMatch);
        const final = chain.hops[chain.hops.length - 1]?.host ?? '—';
        if (chain.verdict === 'BLOCK') return t('hotdeal.domain.chainBlock', { host: chain.decidedBy?.host ?? '—', final });
        if (chain.verdict === 'AFFILIATE') return t('hotdeal.domain.chainAffiliate', { host: chain.decidedBy?.host ?? '—', final });
        return t('hotdeal.domain.chainNone', { final });
    }

    static settingProblem(t: Translate, problem: HotdealSettingProblem | null, spec: HotdealSettingSpec): string {
        if (problem === 'REQUIRED') return t('hotdeal.settings.required');
        if (problem === 'NOT_INTEGER') return t('hotdeal.settings.notInteger');
        if (problem === 'OUT_OF_RANGE') return t('hotdeal.settings.outOfRange', UtilHotdealMessages.rangeParams(spec));
        return '';
    }

    /** 범위 글자 — 금액은 천 단위 쉼표, 0 의 뜻이 있으면 「0 = 없음/끔」 */
    static settingRange(t: Translate, spec: HotdealSettingSpec): string {
        const range = t('hotdeal.settings.range', UtilHotdealMessages.rangeParams(spec));
        return spec.zero ? `${range} · ${t(`hotdeal.settings.zero.${spec.zero}`)}` : range;
    }

    /** 사이트 제휴 몰 — 프로그램 이름 칸 */
    static programProblem(t: Translate, problem: SiteProgramProblem | null): string {
        if (problem === 'REQUIRED') return t('hotdeal.siteAffiliate.programRequired');
        if (problem === 'TOO_LONG') return t('hotdeal.textTooLong', { max: UtilHotdeal.SITE_PROGRAM_MAX });
        return '';
    }

    /** 사이트 제휴 몰 — 링크 틀 칸 */
    static templateProblem(t: Translate, problem: SiteTemplateProblem | null): string {
        if (problem === 'REQUIRED') return t('hotdeal.siteAffiliate.templateRequired');
        if (problem === 'NO_PLACEHOLDER') return t('hotdeal.siteAffiliate.templateNoPlaceholder', { ph: UtilHotdeal.URL_PLACEHOLDER });
        if (problem === 'MULTIPLE_PLACEHOLDERS') return t('hotdeal.siteAffiliate.templateMultiple', { ph: UtilHotdeal.URL_PLACEHOLDER });
        if (problem === 'NOT_URL') return t('hotdeal.siteAffiliate.templateNotUrl');
        if (problem === 'TOO_LONG') return t('hotdeal.textTooLong', { max: UtilHotdeal.SITE_TEMPLATE_MAX });
        return '';
    }

    /** 사이트 제휴 몰 — 붙일 쿼리 칸 */
    static queryProblem(t: Translate, problem: SiteQueryProblem | null): string {
        if (problem === 'REQUIRED') return t('hotdeal.siteAffiliate.queryRequired');
        if (problem === 'INVALID') return t('hotdeal.siteAffiliate.queryInvalid');
        if (problem === 'TOO_LONG') return t('hotdeal.textTooLong', { max: UtilHotdeal.SITE_QUERY_MAX });
        return '';
    }

    /**
     * 서랍의 구매 링크 안내 한 줄 — `SITE` 방식일 때만(개인 제휴 방식이면 빈 글자).
     * 사이트 제휴 몰이면 「사이트 제휴로 변환 — 프로그램」(개인 제휴 글이면 「개인 제휴는 떼요」를 붙인다), 몰이 아닌 개인 제휴 글이면 「지금은 클릭 때 떼어져요」, 그 밖은 「제휴 없이 원래 링크」.
     */
    static buyLinkNote(t: Translate, mode: AffiliateMode, deal: Pick<PostDeal, 'affiliate' | 'affiliateKind' | 'siteProgramName'>): string {
        if (mode !== 'SITE') return '';
        if (deal.affiliateKind === 'SITE') {
            const line = t('hotdeal.buyLinkSite', { program: deal.siteProgramName ?? '—' });
            return deal.affiliate ? `${line} ${t('hotdeal.buyLinkMemberStripped')}` : line;
        }
        return deal.affiliate ? t('hotdeal.buyLinkMemberOff') : t('hotdeal.buyLinkOriginal');
    }

    private static rangeParams(spec: HotdealSettingSpec): { min: string; max: string } {
        const f = (n: number) => (spec.money ? UtilHotdeal.groupDigits(n) : String(n));
        return { min: f(spec.min), max: f(spec.max) };
    }
}
