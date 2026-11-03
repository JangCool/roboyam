<template>
    <!--
        광고 서랍 요약 영역 — 공통 `DrawerSummary` 를 그대로 쓰는 얇은 겹(광고주 관리 서랍 `AdAdvertiserDrawer` · 광고 일정 서랍 `AdBookingDrawer`).
        칸 값의 강조는 부모가 `valueClass` 로 준다(`UtilBizApplication.EMPH`). 링크는 `links` 슬롯.
    -->
    <DrawerSummary :label="label" :items="items">
        <template v-if="$slots.links" #links><slot name="links" /></template>
    </DrawerSummary>
</template>

<script setup lang="ts">
import type { AdDrawerSummaryItem } from '~/components/ad/AdDrawerSummary.types';

interface Props {
    /** 영역 이름(화면 읽기용) */
    label: string;
    items: AdDrawerSummaryItem[];
}

defineProps<Props>();
</script>
