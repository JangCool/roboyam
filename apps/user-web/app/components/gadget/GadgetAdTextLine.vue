<template>
    <!-- 비었을 때 껍데기의 기본 빈 안내 대신 「광고 문의」를 그린다(`:empty="false"`).
         모양 `look`: BAND(기본) = 글 보기 한 줄 광고처럼 카드·안 여백 없이 옅은 선 띠(줄 높이 32px) · CARD = 다른 가젯처럼 카드(2026-11-06 사용자) -->
    <GadgetFrame :gadget="gadget" :empty="false">
        <div v-if="band" class="rounded-md border border-hairline px-3" data-testid="home-ad-text-band">
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

const lines = computed(() => UtilAd.gadgetLines(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
const band = computed(() => UtilAd.textLineLook(props.gadget.config) === 'BAND');
</script>
