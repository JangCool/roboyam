<template>
    <!-- 대시보드 — 지금은 빈 화면이다. 지표 카드·목록은 다음 단계에서 협의해 채운다 -->
    <section>
        <h1 class="text-xl font-semibold">{{ t('dashboard.title') }}</h1>
        <EmptyState class="mt-6" icon="pi pi-chart-bar" :title="t('dashboard.emptyTitle')" :description="t('dashboard.emptyBody')" />
    </section>
</template>

<script setup lang="ts">
const { t } = useI18n();
useSeoMeta({ title: () => t('dashboard.title') });
</script>
