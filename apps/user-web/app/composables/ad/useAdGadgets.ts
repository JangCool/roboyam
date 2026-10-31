import type { Ref } from 'vue';
import type { AdGadgetData, AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilAd from '~/utils/util-ad';

/**
 * 광고 가젯(`AD`·`AD_TEXT_LINE`)에 후보를 채운다 — 받는 중이면 `ads = null`, 후보가 없으면 `empty = true`.
 * 서버는 광고 가젯의 `data` 를 null 로 보낸다. 가젯 호출은 `board` 를 넘기지 않는다(게시판 레이아웃 가젯도).
 */
export function useAdGadgets(source: Readonly<Ref<HomeGadgetsResponse | null>>, placement: Extract<AdPlacement, 'HOME' | 'PAGE' | 'BOARD'>): Readonly<Ref<HomeGadgetsResponse | null>> {
    const ads = usePublicAds();

    const types = computed(() => UtilAd.gadgetTypes(source.value?.gadgets ?? []));
    watch(
        () => types.value.join(','),
        () => {
            if (types.value.length) void ads.ensure(types.value);
        },
        { immediate: true },
    );

    function fill(gadget: HomeGadget): HomeGadget {
        const type = UtilAd.gadgetSlotType(gadget);
        if (!type) return gadget;
        const list = ads.candidates(type);
        const data: AdGadgetData = { ads: list ?? null, placement };
        return { ...gadget, data, empty: list !== undefined && list.length === 0 };
    }

    return computed(() => {
        const res = source.value;
        if (!res || !res.gadgets.some((g) => UtilAd.isGadgetKind(g.type))) return res;
        return { ...res, gadgets: res.gadgets.map(fill) };
    });
}
