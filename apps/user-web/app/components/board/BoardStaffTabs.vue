<template>
    <!--
        게시판 운영 내역 탭 머리 — 「처리 내역」(`/boards/{slug}/actions`) · 「제재 내역」(`/boards/{slug}/sanctions`) 두 화면 위에 같이 선다(board-staff-action-contract 「화면 > user-web」).
        게시판 이름(→ 게시판) + 두 탭(주소 링크 — 지금 화면은 `aria-current="page"`). 운영진 판정은 각 화면의 목록 응답이 한다.
    -->
    <div class="flex flex-col gap-2">
        <NuxtLink :to="`/boards/${encodeURIComponent(slug)}`" class="self-start text-xs font-semibold text-primary hover:underline">{{ boardName }}</NuxtLink>
        <nav class="flex gap-1 border-b border-surface" :aria-label="t('boardStaffAction.tabsLabel')" data-testid="board-staff-tabs">
            <NuxtLink
                v-for="tab in tabs"
                :key="tab.key"
                :to="tab.to"
                :aria-current="tab.key === active ? 'page' : undefined"
                class="-mb-px border-b-2 px-3 py-2 text-sm"
                :class="tab.key === active ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color hover:text-color'"
                :data-testid="`board-staff-tab-${tab.key}`"
            >
                {{ tab.label }}
            </NuxtLink>
        </nav>
    </div>
</template>

<script setup lang="ts">
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';

interface Props {
    /** 게시판 slug */
    slug: string;
    /** 화면 언어의 게시판 이름 */
    boardName: string;
    /** 지금 화면 */
    active: 'actions' | 'sanctions';
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();

const tabs = computed(() => [
    { key: 'actions' as const, label: t('boardStaffAction.tabActions'), to: UtilBoardStaffAction.listPath(props.slug) },
    { key: 'sanctions' as const, label: t('boardStaffAction.tabSanctions'), to: UtilBoardSanction.listPath(props.slug) },
]);
</script>
