<template>
    <!-- 비었을 때 껍데기의 기본 빈 안내 대신 「광고 문의」를 그린다(`:empty="false"`) -->
    <HomeGadgetFrame :gadget="gadget" :empty="false">
        <AdSlot
            :type="adType"
            :ads="data.ads"
            :placement="data.placement"
            :keep-empty="gadget.style.empty.keep"
            :empty-message="gadget.style.empty.message"
            :empty-text="gadget.style.empty.text"
        />
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilAd from '~/utils/util-ad';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const adType = computed(() => UtilAd.gadgetAdType(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
</script>
