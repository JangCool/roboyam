import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HotdealAdTarget } from '~/composables/ad/useHotdealAds.types';
// MOCK:
import { mockFlags } from '~/composables/common/mock/mock-util';

/** 핫딜 게시판 첫 쪽 위 노출 — 말머리를 고르지 않았으면 `HOTDEAL_TOP`, 골랐으면 `HOTDEAL_CATEGORY` */
export function useHotdealAds(target: MaybeRef<HotdealAdTarget | null>) {
    const ads = usePublicAds();
    // MOCK: `?mock=ad-hotdeal` — 핫딜 게시판이 아니어도 보인다
    const forced = mockFlags().has('ad-hotdeal');

    const placement = computed<Extract<AdPlacement, 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY'> | null>(() => {
        const t = toValue(target);
        if (!t || !t.firstPage || !(t.dealEnabled || forced)) return null;
        return t.prefix ? 'HOTDEAL_CATEGORY' : 'HOTDEAL_TOP';
    });

    watch(
        () => (placement.value ? `${toValue(target)?.board}:${toValue(target)?.prefix}` : ''),
        (key) => {
            const t = toValue(target);
            if (key && t) void ads.ensureHotdeal(t.board, t.prefix);
        },
        { immediate: true },
    );

    const candidates = computed<PublicAd[] | null>(() => {
        const t = toValue(target);
        if (!placement.value || !t) return null;
        return ads.hotdealCandidates(t.board, t.prefix) ?? null;
    });

    const prefixName = computed(() => toValue(target)?.prefixName ?? '');

    return { placement, candidates, prefixName };
}
