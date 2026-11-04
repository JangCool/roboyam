<template>
    <!--
        게시판 제재 화면의 탭 — 「이력」(`/board-sanctions`) · 「반복 검토」(`/board-sanctions/reviews`, 열린 건 수 배지). 두 화면이 같은 머리로 쓴다.
        탭마다 주소가 따로라 각 탭의 거르기·쪽·서랍(`?boardSanction=`·`?review=`)이 섞이지 않는다. 탭을 누르면 그 화면으로 간다(조건 없이 처음 상태).
        열린 건 수는 부르는 화면이 받아 내려 준다(`GET /api/admin/board-sanction-reviews/summary`) — 아직 모르면 배지를 그리지 않는다.
    -->
    <Tabs :value="active" scrollable class="min-w-0" @update:value="(v: string | number) => go(String(v))">
        <TabList :aria-label="t('boardSanction.tabsLabel')">
            <Tab value="history">{{ t('boardSanction.tab.history') }}</Tab>
            <Tab value="reviews">
                <span class="inline-flex items-center gap-1.5">
                    {{ t('boardSanction.tab.reviews') }}
                    <Badge v-if="openCount !== null && openCount > 0" :value="openCount.toLocaleString(locale)" severity="warn" size="small" :aria-label="t('boardSanction.openBadge', { n: openCount })" />
                </span>
            </Tab>
        </TabList>
    </Tabs>
</template>

<script setup lang="ts">
import type { BoardSanctionTab } from '~/utils/util-board-sanction';

interface Props {
    active: BoardSanctionTab;
    /** 열린 반복 검토 수 — 아직 모르면 null */
    openCount?: number | null;
}

const props = withDefaults(defineProps<Props>(), { openCount: null });

const { t, locale } = useI18n();

function go(tab: string): void {
    if (tab === props.active) return;
    void navigateTo(tab === 'reviews' ? '/board-sanctions/reviews' : '/board-sanctions');
}
</script>
