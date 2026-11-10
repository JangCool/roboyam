import type { Ref } from 'vue';
import type { AdGadgetData, AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import type { AdSenseUnitSpec } from '~/utils/util-adsense';
import UtilAd from '~/utils/util-ad';

/**
 * 광고 가젯(`AD`·`AD_TEXT_LINE`)에 후보를 채운다 — 받는 중이면 `ads = null`, 후보가 없으면 `empty = true`.
 * 서버는 광고 가젯의 `data` 를 null 로 보낸다. 가젯 호출은 `board` 를 넘기지 않는다(게시판 레이아웃 가젯도).
 * 애드센스: 배너 가젯(`AD`)의 장부 후보가 0건이고 그 배너 단위 ID 가 있으면 `data.adsense` 로 채우고 비었다고 하지 않는다(한 줄 광고 가젯은 애드센스 없음).
 *   게시판 레이아웃(`BOARD`)은 게시판 화면·글 보기가 알려 준 맥락이 19금·광고 숨김·소모임·비밀글이 아닐 때만(장부 가젯은 지금 규칙 그대로). 광고가 오지 않으면 그 가젯을 빈 것으로 본다(`markUnfilled`).
 */
export function useAdGadgets(source: Readonly<Ref<HomeGadgetsResponse | null>>, placement: Extract<AdPlacement, 'HOME' | 'PAGE' | 'BOARD'>): Readonly<Ref<HomeGadgetsResponse | null>> {
    const ads = usePublicAds();
    const adsense = useAdSense();
    const route = useRoute();

    const types = computed(() => UtilAd.gadgetTypes(source.value?.gadgets ?? []));
    watch(
        () => types.value.join(','),
        () => {
            if (types.value.length) void ads.ensure(types.value);
        },
        { immediate: true },
    );

    /** 장부 후보를 받았는데 0건인 배너 가젯의 애드센스 단위 — 받는 중·후보 있음·한 줄 광고·맥락 막힘·접힘이면 null */
    function adsenseOf(type: string, list: readonly unknown[] | undefined, key: string): AdSenseUnitSpec | null {
        if (!list || list.length > 0 || !UtilAd.isBanner(type)) return null;
        if (placement === 'BOARD' && !adsense.contextAllows(route.path)) return null;
        if (adsense.isUnfilled(key)) return null;
        return adsense.unit(type);
    }

    function fill(gadget: HomeGadget): HomeGadget {
        const type = UtilAd.gadgetSlotType(gadget);
        if (!type) return gadget;
        const list = ads.candidates(type);
        const adsenseKey = `${route.path}|${placement}|${gadget.id}`;
        const spec = adsenseOf(type, list, adsenseKey);
        const data: AdGadgetData = { ads: list ?? null, placement, adsense: spec, adsenseKey };
        return { ...gadget, data, empty: list !== undefined && list.length === 0 && spec === null };
    }

    return computed(() => {
        const res = source.value;
        if (!res || !res.gadgets.some((g) => UtilAd.isGadgetKind(g.type))) return res;
        return { ...res, gadgets: res.gadgets.map(fill) };
    });
}
