<template>
    <!--
        회원 칭호 관리 머리 — 제목 + 네 탭(주소 링크: 칭호 `/members/titles` · 확인할 일 `/review` · 이달의 작가 뽑기 `/monthly` · 주고 뺀 기록 `/grants`, member-title-contract 「화면」).
        탭 하나가 화면 하나다(각 화면이 자기 서버 호출을 한다). 지금 화면은 `aria-current="page"`. 확인할 일 탭은 심사 권한(`title.review`)이 있을 때만.
    -->
    <div class="flex flex-col gap-3">
        <PageTitle :title="t('memberTitle.title')" :subtitle="t('memberTitle.subtitle')">
            <template v-if="$slots.actions" #actions><slot name="actions" /></template>
        </PageTitle>
        <nav class="flex gap-1 overflow-x-auto border-b border-(--line)" :aria-label="t('memberTitle.tabsLabel')" data-testid="title-admin-tabs">
            <NuxtLink
                v-for="tab in tabs"
                :key="tab.key"
                :to="tab.to"
                :aria-current="tab.key === active ? 'page' : undefined"
                class="-mb-px shrink-0 border-b-2 px-3 py-2 text-sm whitespace-nowrap"
                :class="tab.key === active ? 'border-(--p-primary-color) font-semibold' : 'border-transparent text-muted-color hover:text-(--p-text-color)'"
                :data-testid="`title-admin-tab-${tab.key}`"
            >
                {{ tab.label }}
            </NuxtLink>
        </nav>
    </div>
</template>

<script setup lang="ts">
import UtilTitle from '~/utils/util-title';

type TitleAdminTab = 'titles' | 'review' | 'monthly' | 'grants';

interface Props {
    /** 지금 화면 */
    active: TitleAdminTab;
}

defineProps<Props>();

const { t } = useI18n();
const { can } = useAdminPermission();

const tabs = computed(() =>
    [
        { key: 'titles' as const, label: t('memberTitle.tab.titles'), to: UtilTitle.PATHS.titles, show: true },
        { key: 'review' as const, label: t('memberTitle.tab.review'), to: UtilTitle.PATHS.review, show: can('title.review') },
        { key: 'monthly' as const, label: t('memberTitle.tab.monthly'), to: UtilTitle.PATHS.monthly, show: true },
        { key: 'grants' as const, label: t('memberTitle.tab.grants'), to: UtilTitle.PATHS.grants, show: true },
    ].filter((tab) => tab.show),
);
</script>
