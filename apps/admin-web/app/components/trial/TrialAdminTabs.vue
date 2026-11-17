<template>
    <!--
        광고 › 체험단 머리 — 제목 + 두 탭(주소 링크: 모집 `/ads/trials` · 확인할 일 `/ads/trials/review`, trial-group-contract 「관리자 화면」). 체험단 설정은 메뉴의 다른 줄(`/ads/trials/settings`).
        지금 화면은 `aria-current="page"`. 확인할 일 탭은 확인 권한(`trial.review`)이 있을 때만.
    -->
    <div class="flex flex-col gap-3">
        <PageTitle :title="t('trial.title')" :subtitle="t('trial.subtitle')">
            <template v-if="$slots.actions" #actions><slot name="actions" /></template>
        </PageTitle>
        <nav class="flex gap-1 overflow-x-auto border-b border-(--line)" :aria-label="t('trial.tabsLabel')">
            <NuxtLink
                v-for="tab in tabs"
                :key="tab.key"
                :to="tab.to"
                :aria-current="tab.key === active ? 'page' : undefined"
                class="-mb-px shrink-0 border-b-2 px-3 py-2 text-sm whitespace-nowrap"
                :class="tab.key === active ? 'border-(--p-primary-color) font-semibold' : 'border-transparent text-muted-color hover:text-(--p-text-color)'"
            >
                {{ tab.label }}
            </NuxtLink>
        </nav>
    </div>
</template>

<script setup lang="ts">
import UtilTrial from '~/utils/util-trial';

interface Props {
    /** 지금 화면 */
    active: 'list' | 'review';
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { can } = useAdminPermission();

const tabs = computed(() =>
    [
        { key: 'list' as const, label: t('trial.tab.list'), to: UtilTrial.PATHS.list, show: true },
        { key: 'review' as const, label: t('trial.tab.review'), to: UtilTrial.PATHS.review, show: can('trial.review') },
    ].filter((tab) => tab.show),
);
</script>
