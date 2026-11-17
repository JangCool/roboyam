import type { AdPriceRulesView } from '~/composables/ad/api/useAdApi.types';
import type { AdminTrialFormOptions, TrialFormOptionSource, TrialPriceTerms } from '~/composables/trial/api/useTrialApi.types';

/** 계약 기본값(trial-group-contract — 설정을 못 읽으면) */
const DEFAULT_GIVE_LIMIT = 50000;
const DEFAULT_REVIEW_DAYS = 14;

/**
 * 체험단 모집 서랍의 고를 거리 — 광고주(보관 아닌 것, 이름 차례) · 사용기 게시판 후보 · 제공형 상한·사용기 기한 처음 값(체험단 설정) ·
 * 요금표 체험단 줄(만들기 서랍의 선결제 예상 금액 — 광고 기본 설정 「상품 가격」과 같은 `GET /api/admin/ads/price-rules`, `ad.read`).
 * 모집 목록(`/ads/trials`)과 모집 상세(`/ads/trials/{id}`)의 서랍이 같이 쓴다. 서버에 고를 거리 API 가 따로 없어 화면이 네 API 로 모은다(하나가 막혀도 나머지로 그린다 — forms.md 2-2).
 * 게시판 목록은 `board.read`, 광고주 목록은 `ad.read` 가 있어야 받는다 — `trial.manage` 만 가진 운영자는 403 이라 빈 후보로 그리고 `unavailable` 로 서랍이 안내한다(상한·기한은 계약 기본값).
 * 사용기 게시판: 관리자 게시판 목록의 「체험단 사용기 받기」(`trialReviewsEnabled`)를 켠 활성 일반 게시판만(판정은 서버 — 안 켠 게시판이면 400).
 * ⚠ setup 에서 부른다.
 */
export function useTrialFormOptions() {
    const advertiserApi = useAdAdvertiserApi();
    const boardApi = useBoardApi();
    const trialApi = useTrialApi();
    const adApi = useAdApi();
    const options = ref<AdminTrialFormOptions | null>(null);
    let pending: Promise<void> | null = null;

    async function fetchAll(): Promise<void> {
        const [advertisers, boards, settings, prices] = await Promise.allSettled([
            advertiserApi.list({ state: 'ACTIVE', sort: 'name', dir: 'asc', page: 0, size: 100 }),
            boardApi.list(),
            trialApi.settings(),
            adApi.priceRules(),
        ]);
        const reviewBoards = boards.status === 'fulfilled' ? boards.value.boards.filter((b) => b.active && b.kind === 'GENERAL' && b.trialReviewsEnabled === true) : [];
        options.value = {
            advertisers: advertisers.status === 'fulfilled' ? advertisers.value.items.map((a) => ({ id: a.id, name: a.name })) : [],
            reviewBoards: reviewBoards.map((b) => ({ id: b.id, slug: b.slug, name: b.name })),
            givePriceLimit: settings.status === 'fulfilled' ? settings.value.givePriceLimit : DEFAULT_GIVE_LIMIT,
            defaultReviewDays: settings.status === 'fulfilled' ? settings.value.reviewDays : DEFAULT_REVIEW_DAYS,
            trialPrice: prices.status === 'fulfilled' ? trialTermsOf(prices.value) : null,
            unavailable: ([['advertisers', advertisers], ['boards', boards], ['settings', settings], ['prices', prices]] as const)
                .filter(([, r]) => r.status === 'rejected')
                .map(([k]): TrialFormOptionSource => k),
        };
    }

    /** 요금표의 체험단 줄 → 요금(줄이 없으면 null — 서버는 TRIAL 줄이 늘 있다고 본다) */
    function trialTermsOf(view: AdPriceRulesView): TrialPriceTerms | null {
        const rule = view.rules.find((r) => r.product === 'TRIAL');
        return rule ? { priceUnit: rule.priceUnit ?? 'PER_PERSON', unitPrice: rule.unitPrice, shippingFeePerPerson: rule.shippingFeePerPerson ?? 0 } : null;
    }

    /** 한 번만 받는다(서랍을 다시 열어도 그대로) */
    function ensure(): Promise<void> {
        if (options.value) return Promise.resolve();
        pending ??= fetchAll().finally(() => (pending = null));
        return pending;
    }

    return { options, ensure };
}
