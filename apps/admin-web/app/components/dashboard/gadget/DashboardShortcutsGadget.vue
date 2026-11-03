<template>
    <!--
        바로가기 — 운영자가 고른 관리자 화면(최대 12, 이 가젯 설정 `config.links`)을 단추로. 고르는 곳은 화면 편집의 ⚙ 설정.
        권한이 빠졌거나 메뉴에서 없어진 화면은 그리지 않는다(감추는 것은 편의 — 화면은 셸이 다시 막는다). 서버를 부르지 않는다. 여러 개 둘 수 있다.
    -->
    <DashboardGadgetFrame :title="title" :refreshable="false" ready :empty="!links.length" empty-icon="pi pi-link" :empty-title="t('dashboard.shortcutsEmpty')" :empty-body="t('dashboard.shortcutsEmptyBody')">
        <ul class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,9rem),1fr))] gap-2 p-0">
            <li v-for="l in links" :key="l.to" class="min-w-0">
                <NuxtLink :to="l.to" class="flex h-full min-w-0 items-center gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px] transition-colors hover:border-primary hover:bg-surface-50 dark:hover:bg-surface-900">
                    <i :class="l.icon ?? 'pi pi-arrow-right'" class="shrink-0 text-xs text-primary" aria-hidden="true" />
                    <span class="min-w-0 truncate" :title="t(l.labelKey)">{{ t(l.labelKey) }}</span>
                </NuxtLink>
            </li>
        </ul>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { can } = useAdminPermission();

const links = computed(() => {
    const choices = new Map(UtilDashboardLayout.shortcutChoices(can).map((c) => [c.to, c]));
    return (props.gadget.config.links ?? []).flatMap((to) => {
        const c = choices.get(to);
        return c ? [c] : [];
    });
});
</script>
