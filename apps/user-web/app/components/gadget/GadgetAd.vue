<template>
    <!-- 비었을 때 껍데기의 기본 빈 안내 대신 「광고 문의」를 그린다(`:empty="false"`) -->
    <GadgetFrame :gadget="gadget" :empty="false">
        <!-- 장부 후보가 0건이면 애드센스 단위(`useAdGadgets` 가 채운다). 광고가 오지 않으면 빈 가젯으로 접힌다(「광고 문의」를 켠 가젯은 그것으로) -->
        <AdSenseUnit v-if="data.adsense" :key="data.adsenseKey" :spec="data.adsense" @unfilled="adsense.markUnfilled(data.adsenseKey ?? '')" />
        <AdSlot
            v-else
            :type="adType"
            :ads="data.ads"
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
const adType = computed(() => UtilAd.gadgetAdType(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
</script>
