import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { BoardFeedAd } from '~/components/board/BoardPostList.types';
import type { BoardFeedTarget } from '~/composables/ad/useBoardFeedAd.types';
import UtilAd from '~/utils/util-ad';

/**
 * 게시판 목록 안 한 줄 광고 — 일반 글 `after` 번째 뒤(적으면 맨 끝). 첫 쪽·검색 아님·`adsHidden` 아님·19금 게시판 아님일 때만(가정).
 * 보이던 광고가 후보에 남아 있으면 다시 고르지 않는다.
 */
export function useBoardFeedAd(target: MaybeRef<BoardFeedTarget | null>) {
    const ads = usePublicAds();
    void ads.ensurePlacements();

    const placement = computed(() => UtilAd.feedPlacement(ads.placements.value?.boardList));
    const allowed = computed(() => {
        const t = toValue(target);
        if (!t || !placement.value.enabled || !t.firstPage || t.search) return false;
        if (UtilAd.boardAdsHidden(t.board)) return false;
        return !(t.board && 'allowAdult' in t.board && (t.board as { allowAdult?: unknown }).allowAdult === true);
    });

    watch(
        allowed,
        (on) => {
            if (on) void ads.ensure(['TEXT_LINE']);
        },
        { immediate: true },
    );

    const picked = ref<PublicAd | null>(null);
    watch(
        () => ads.candidates('TEXT_LINE'),
        (list) => {
            if (!list) return;
            if (UtilAd.stillIn(picked.value, list)) return;
            picked.value = UtilAd.pick(list);
        },
        { immediate: true },
    );

    return computed<BoardFeedAd | null>(() => {
        const t = toValue(target);
        if (!allowed.value || !t || !picked.value) return null;
        const afterIndex = UtilAd.feedIndex(t.rows, placement.value.after);
        return afterIndex === null ? null : { ad: picked.value, afterIndex };
    });
}
