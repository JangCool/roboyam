<template>
    <!-- 비었을 때 껍데기의 기본 빈 안내 대신 「광고 문의」를 그린다(`:empty="false"`).
         모양 `look`: BAND(기본) = 글 보기 한 줄 광고처럼 카드·안 여백 없이 옅은 선 띠(줄 높이 32px) · CARD = 다른 가젯처럼 카드(2026-11-06 사용자) -->
    <GadgetFrame :gadget="gadget" :empty="false">
        <!-- 장부 후보가 0건이면 목록 한 줄과 같은 인피드 단위 하나(`useAdGadgets` 가 채운다). 광고가 오지 않으면 빈 가젯으로 접힌다(「광고 문의」를 켠 가젯은 그것으로) -->
        <AdSenseUnit
            v-if="data.adsense"
            :key="data.adsenseKey"
            :spec="data.adsense"
            :min-height="band ? '2rem' : '2.5rem'"
            @unfilled="adsense.markUnfilled(data.adsenseKey ?? '')"
        />
        <div v-else-if="band" class="rounded-md border border-hairline px-3" data-testid="home-ad-text-band">
            <AdTextLines
                :ads="data.ads"
                :count="lines"
                slim
                :placement="data.placement"
                :keep-empty="gadget.style.empty.keep"
                :empty-message="gadget.style.empty.message"
                :empty-text="gadget.style.empty.text"
            />
        </div>
        <AdTextLines
            v-else
            :ads="data.ads"
            :count="lines"
            :placement="data.placement"
            :keep-empty="gadget.style.empty.keep"
            :empty-message="gadget.style.empty.message"
            :empty-text="gadget.style.empty.text"
        />
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});

const adsense = useAdSense();
const lines = computed(() => UtilAd.gadgetLines(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
const band = computed(() => UtilAd.textLineLook(props.gadget.config) === 'BAND');
</script>
