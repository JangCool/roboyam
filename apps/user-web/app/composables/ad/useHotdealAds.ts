import type { MaybeRef } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HotdealAdTarget } from '~/composables/ad/useHotdealAds.types';
import UtilAd from '~/utils/util-ad';

/**
 * 말머리 상단(`HOTDEAL_CATEGORY`) 신청이 열려 있는지 — 닫혀 있는 동안(사용자 결정 2026-11-02, 서버 V124)은 말머리 칩을 골라도 게시판 상단(`HOTDEAL_TOP`)을 부른다.
 * 다시 열 때는 새 마이그레이션으로 받기를 켜고 이 값을 `true` 로 되돌린다.
 */
const CATEGORY_TOP_OPEN = false;

/**
 * 핫딜 게시판 첫 쪽 위 노출 — 말머리를 고르지 않았으면 `HOTDEAL_TOP`, 골랐으면 `HOTDEAL_CATEGORY`(말머리 상단이 닫혀 있으면 늘 `HOTDEAL_TOP`).
 * 게시판 맥락 호출이라 `board` 를 넘긴다. `adsHidden`·19금 게시판(자리 설정 `adult.enabled` 꺼짐)은 부르지 않는다(서버도 빈 목록).
 */
export function useHotdealAds(target: MaybeRef<HotdealAdTarget | null>) {
    const ads = usePublicAds();
    void ads.ensurePlacements();

    /** 노출을 물을 말머리 — 말머리 상단이 닫혀 있으면 비워 게시판 상단을 묻는다 */
    const prefix = computed(() => (CATEGORY_TOP_OPEN ? (toValue(target)?.prefix ?? '') : ''));

    const placement = computed<Extract<AdPlacement, 'HOTDEAL_TOP' | 'HOTDEAL_CATEGORY'> | null>(() => {
        const t = toValue(target);
        if (!t || !t.firstPage || !t.dealEnabled || UtilAd.boardAdsHidden(t.view, ads.adultEnabled.value)) return null;
        return prefix.value ? 'HOTDEAL_CATEGORY' : 'HOTDEAL_TOP';
    });

    watch(
        () => (placement.value ? `${toValue(target)?.board}:${prefix.value}` : ''),
        (key) => {
            const t = toValue(target);
            if (key && t) void ads.ensureHotdeal(t.board, prefix.value);
        },
        { immediate: true },
    );

    const candidates = computed<PublicAd[] | null>(() => {
        const t = toValue(target);
        if (!placement.value || !t) return null;
        return ads.hotdealCandidates(t.board, prefix.value) ?? null;
    });

    /** 말머리 상단 노출일 때만 고른 칩 이름을 넘긴다 — 게시판 상단 광고에 칩 이름이 붙으면 그 글의 말머리처럼 보인다 */
    const prefixName = computed(() => (placement.value === 'HOTDEAL_CATEGORY' ? (toValue(target)?.prefixName ?? '') : ''));

    return { placement, candidates, prefixName };
}
