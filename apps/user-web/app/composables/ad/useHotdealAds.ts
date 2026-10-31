import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HotdealAdTarget } from '~/composables/ad/useHotdealAds.types';
import UtilAd from '~/utils/util-ad';

/**
 * 핫딜 게시판 첫 쪽 위 노출 — 말머리를 고르지 않았으면 `HOTDEAL_TOP`, 골랐으면 `HOTDEAL_CATEGORY`.
 * 게시판 맥락 호출이라 `board` 를 넘긴다. `adsHidden`·19금 게시판은 부르지 않는다(서버도 빈 목록).
 */
export function useHotdealAds(target: MaybeRef<HotdealAdTarget | null>) {
    const ads = usePublicAds();

    const placement = computed<Extract<AdPlacement, 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY'> | null>(() => {
        const t = toValue(target);
        if (!t || !t.firstPage || !t.dealEnabled || UtilAd.boardAdsHidden(t.view)) return null;
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
