import type { Ref } from 'vue';
import type { AdGadgetData, AdPlacement } from '~/composables/ad/usePublicAds.types';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
// MOCK: 광고 가젯 덧붙이기 스위치(`?mock=ad-gadgets`) — 서버가 광고 가젯 종류를 알면 지운다
import { mockAdGadgets } from '~/composables/ad/api/mock/ad-mock';
import { mockFlags } from '~/composables/common/mock/mock-util';
import UtilAd from '~/utils/util-ad';

/**
 * 가젯 응답(홈 · 가젯 페이지 · 게시판 레이아웃)의 광고 가젯(`AD`·`AD_TEXT_LINE`)에 후보를 채운다 — 서버 홈 응답은 광고 가젯의 `data` 를 비워 보내고(광고는 따로 사본 캐시),
 * 화면이 `usePublicAds` 로 그 유형의 후보를 받아 `data`(`AdGadgetData`)·`empty` 를 정한다:
 *   · 받는 중 → `data.ads = null`, `empty = false`(가젯이 비율 틀로 자리를 잡는다)
 *   · 후보 없음 → `empty = true` — 꾸미기 `style.empty.keep` 이 꺼져 있으면(광고 가젯 처음 값) 격자가 자리째 접고, 켜져 있으면 가젯이 「광고 문의」 안내를 그린다
 * 광고 가젯이 아닌 가젯은 그대로 둔다(같은 객체). 페이지가 setup 에서 부른다.
 * @param {Readonly<Ref<HomeGadgetsResponse | null>>} source - 읽은 응답(`UtilHomeGadget.sanitize` 뒤 — 관리자 미리보기 덮기 포함).
 * @param {AdPlacement} placement - 어디인가(노출 집계의 자리).
 * @returns {Readonly<Ref<HomeGadgetsResponse | null>>} 후보를 채운 응답.
 */
export function useAdGadgets(source: Readonly<Ref<HomeGadgetsResponse | null>>, placement: Extract<AdPlacement, 'HOME' | 'PAGE' | 'BOARD'>): Readonly<Ref<HomeGadgetsResponse | null>> {
    const ads = usePublicAds();

    // MOCK: `?mock=ad-gadgets` — 광고 가젯을 화면 쪽에서 덧붙인다(서버가 아직 AD 종류를 모른다). 서버 연결 때 이 줄을 `const withMock = source;` 로
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
