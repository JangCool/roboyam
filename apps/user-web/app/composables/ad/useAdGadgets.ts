import type { Ref } from 'vue';
import type { AdGadgetData, AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
// MOCK:
import { mockAdGadgets } from '~/composables/ad/api/mock/ad-mock';
import { mockFlags } from '~/composables/common/mock/mock-util';
import UtilAd from '~/utils/util-ad';

/** 광고 가젯(`AD`·`AD_TEXT_LINE`)에 후보를 채운다 — 받는 중이면 `ads = null`, 후보가 없으면 `empty = true` */
export function useAdGadgets(source: Readonly<Ref<HomeGadgetsResponse | null>>, placement: Extract<AdPlacement, 'HOME' | 'PAGE' | 'BOARD'>): Readonly<Ref<HomeGadgetsResponse | null>> {
    const ads = usePublicAds();

    // MOCK: 서버 연결 때 `const withMock = source;` 로
    const flagged = mockFlags().has('ad-gadgets');
    const withMock = computed(() => (source.value && flagged ? mockAdGadgets(source.value, placement) : source.value));

    const types = computed(() => UtilAd.gadgetTypes(withMock.value?.gadgets ?? []));
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
        const res = withMock.value;
        if (!res || !res.gadgets.some((g) => UtilAd.isGadgetKind(g.type))) return res;
        return { ...res, gadgets: res.gadgets.map(fill) };
    });
}
