import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { BoardFeedAd } from '~/components/board/BoardPostList.types';
import type { BoardFeedTarget } from '~/composables/ad/useBoardFeedAd.types';
import UtilAd from '~/utils/util-ad';

/**
 * 게시판 목록 안 한 줄 광고(인피드 — `TEXT_LINE` 후보 하나) — 사이트 자리 설정 `placements.boardList{ enabled, after }` 대로 일반 글 `after` 번째 뒤(글이 적으면 맨 끝, 0개면 없음)에 한 줄.
 * 넣지 않는 때: 자리 꺼짐 · 첫 쪽이 아님(같은 광고가 쪽마다 되풀이되는 피로·노출 집계 부풀림을 줄인다 — 가정) · 검색 결과 ·
 * 게시판 `adsHidden`(아직 없는 칸 — `UtilAd.boardAdsHidden`) · 19금 글을 받는 게시판(`allowAdult` — 가정: 19금 게시판 광고 제외를 이 칸으로 먼저 막는다) · 후보 없음.
 * 광고는 후보가 바뀔 때만 다시 고른다(보이던 광고가 아직 있으면 그대로). 게시판 목록 화면이 setup 에서 부른다.
 * @param {MaybeRef<BoardFeedTarget | null>} target - 목록 상태(아직 모르면 null).
 * @returns {Readonly<Ref<BoardFeedAd | null>>} 목록 컴포넌트에 넘길 광고 줄.
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
