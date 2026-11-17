import type { AdminTrialFormOptions } from '~/composables/trial/api/useTrialApi.types';

/** 계약 기본값(trial-group-contract — 설정을 못 읽으면) */
const DEFAULT_GIVE_LIMIT = 50000;
const DEFAULT_REVIEW_DAYS = 14;

/**
 * 체험단 모집 서랍의 고를 거리 — 광고주(보관 아닌 것, 이름 차례) · 사용기 게시판 후보 · 제공형 상한·사용기 기한 처음 값(체험단 설정).
 * 모집 목록(`/ads/trials`)과 모집 상세(`/ads/trials/{id}`)의 서랍이 같이 쓴다. 서버에 고를 거리 API 가 따로 없어 화면이 세 API 로 모은다(하나가 막혀도 나머지로 그린다 — forms.md 2-2).
 * 사용기 게시판: 관리자 게시판 목록에 「체험단 사용기 받기」(`trialReviewsEnabled`) 칸이 오면 켠 것만, 없으면 활성 일반 게시판 전부(판정은 서버 — 안 켠 게시판이면 400).
 * ⚠ setup 에서 부른다.
 */
export function useTrialFormOptions() {
    const advertiserApi = useAdAdvertiserApi();
    const boardApi = useBoardApi();
    const trialApi = useTrialApi();
    const options = ref<AdminTrialFormOptions | null>(null);
    let pending: Promise<void> | null = null;

    async function fetchAll(): Promise<void> {
        const [advertisers, boards, settings] = await Promise.allSettled([
            advertiserApi.list({ state: 'ACTIVE', sort: 'name', dir: 'asc', page: 0, size: 100 }),
            boardApi.list(),
            trialApi.settings(),
        ]);
        const boardRows = boards.status === 'fulfilled' ? boards.value.boards.filter((b) => b.active && b.kind === 'GENERAL') : [];
        const flagged = boardRows.filter((b) => (b as { trialReviewsEnabled?: boolean }).trialReviewsEnabled === true);
        const hasFlag = boardRows.some((b) => (b as { trialReviewsEnabled?: boolean }).trialReviewsEnabled !== undefined);
        options.value = {
            advertisers: advertisers.status === 'fulfilled' ? advertisers.value.items.map((a) => ({ id: a.id, name: a.name })) : [],
            reviewBoards: (hasFlag ? flagged : boardRows).map((b) => ({ id: b.id, slug: b.slug, name: b.name })),
            givePriceLimit: settings.status === 'fulfilled' ? settings.value.givePriceLimit : DEFAULT_GIVE_LIMIT,
            defaultReviewDays: settings.status === 'fulfilled' ? settings.value.reviewDays : DEFAULT_REVIEW_DAYS,
        };
    }

    /** 한 번만 받는다(서랍을 다시 열어도 그대로) */
    function ensure(): Promise<void> {
        if (options.value) return Promise.resolve();
        pending ??= fetchAll().finally(() => (pending = null));
        return pending;
    }

    return { options, ensure };
}
