<template>
    <!-- 핫딜 상태 알약(진행 · 의심 · 종료 · 기간 지남) — 핫딜 글 목록·서랍·의심 후보 서랍이 같이 쓴다 -->
    <Tag :value="t(`hotdeal.view.${view}`)" :icon="UtilHotdeal.viewIcon(view)" :severity="UtilHotdeal.viewSeverity(view)" class="text-[11px]!" />
</template>

<script setup lang="ts">
import type { DealView } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';

interface Props {
    view: DealView;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
