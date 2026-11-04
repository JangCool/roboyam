<template>
    <!-- 카드 머리의 「자세히 →」 — 「방문자 통계」 개요의 미리보기 카드가 그 쪽(같은 기간)으로 간다 -->
    <NuxtLink :to="to" class="inline-flex items-center gap-1 rounded text-[12px] font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
        {{ t('visitAnalytics.more') }}<i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
    </NuxtLink>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

interface Props {
    to: RouteLocationRaw;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
