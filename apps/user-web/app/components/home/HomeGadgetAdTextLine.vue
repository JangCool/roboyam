<template>
    <!--
        한 줄 광고 가젯(`AD_TEXT_LINE` — 02 §5-1: 한 줄 N줄) — 설정 `config.count`(1~5, 처음 값 3)줄을 `AdTextLines` 가 그린다(겹치지 않게 비중 가중 무작위).
        후보는 페이지가 `useAdGadgets` 로 채운 `data`(`AdGadgetData`). 처음 꾸미기는 머리 숨김 · 테마 카드(줄마다 「광고」 표시가 있어 제목 없이도 읽힌다).
        비었을 때는 배너 가젯과 같다(유지가 꺼져 있으면 자리째 빠지고, 켜져 있으면 「광고 문의」).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="false">
        <AdTextLines
            :ads="data.ads"
            :count="lines"
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

const lines = computed(() => UtilAd.gadgetLines(props.gadget.config));
const data = computed(() => UtilAd.gadgetData(props.gadget.data));
</script>
