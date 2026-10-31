import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HotdealAdTarget } from '~/composables/ad/useHotdealAds.types';
// MOCK: 핫딜 게시판이 아니어도 핫딜 노출을 보는 스위치(`?mock=ad-hotdeal`) — 서버 연결 때 지운다
import { mockFlags } from '~/composables/common/mock/mock-util';

/**
 * 핫딜 목록 위 노출(「핫딜 홈 상단」 `HOTDEAL_TOP` · 「핫딜 말머리 상단」 `HOTDEAL_CATEGORY`) — 핫딜 게시판(`dealEnabled`) 목록의 첫 쪽에서만,
 * 말머리를 고르지 않았으면 홈 상단, 골랐으면 그 말머리 상단 후보를 받는다(`usePublicAds.ensureHotdeal`). 게시판 목록 화면이 setup 에서 부른다.
 * @param {MaybeRef<HotdealAdTarget | null>} target - 게시판 목록 상태(게시판을 아직 모르면 null).
 * @returns 자리(`placement` — 그리지 않으면 null) · 후보(받는 중 null) · 말머리 이름.
 */
export function useHotdealAds(target: MaybeRef<HotdealAdTarget | null>) {
    const ads = usePublicAds();
    // MOCK:
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
