<template>
    <!--
        인기 소모임 가젯(`GROUPS`) — 지금 뜨는 소모임(`GET /api/groups/trending` 과 같은 줄)을 가젯 개수만큼. 이름 첫 글자 상자 · 이름 · 모집 상태 / 멤버 수 · (주식이면 종목 추천 금지) / 한 줄 소개.
        한 열(모바일) + 템플릿이 `mobile.groupsScroll` 을 고르면 가로로 미는 작은 카드 줄(`gadget-scroll-row` — 첫 글자 상자 · 이름 · 멤버 수 · 모집 상태, 한 칸 8.75rem).
        community 는 이 가젯의 `data` 가 null 이고 컴포넌트가 따로 받았지만, 로보얌은 가젯 `data.items` 로 받는다(컴포넌트는 서버를 부르지 않는다 — 가정).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || rows.length === 0" empty-icon="pi pi-users">
        <!-- 한 열 — 가로로 미는 작은 카드 -->
        <ul v-if="scroll" class="gadget-scroll-row" :style="ROW" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" data-testid="home-groups">
            <li v-for="row in rows" :key="row.id" class="flex">
                <NuxtLink
                    :to="UtilGroup.detailPath(row.id)"
                    class="flex w-full min-w-0 flex-col items-start gap-1 rounded-xl border border-surface p-3 hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                    data-testid="home-group-card"
                >
                    <GroupInitial :name="row.name" class="mb-1" />
                    <b class="w-full truncate text-sm">{{ row.name }}</b>
                    <span class="text-xs text-muted-color">{{ UtilGroupMessages.members(t, row) }}</span>
                    <span v-if="row.stockNotice" class="text-[0.6875rem] font-semibold text-amber-700 dark:text-amber-400">{{ t('group.stockRowNote') }}</span>
                    <GroupTag class="mt-auto" :tone="row.recruitState === 'OPEN' ? 'green' : 'gray'">{{ UtilGroupMessages.recruitLabel(t, row.recruitState) }}</GroupTag>
                </NuxtLink>
            </li>
        </ul>
        <ul v-else class="m-0 list-none p-0" data-testid="home-groups">
            <li v-for="row in rows" :key="row.id" class="border-b border-surface last:border-b-0">
                <NuxtLink :to="UtilGroup.detailPath(row.id)" class="flex items-start gap-3 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                    <GroupInitial :name="row.name" />
                    <span class="min-w-0 flex-1">
                        <span class="flex items-center gap-1.5">
                            <b class="min-w-0 truncate text-sm">{{ row.name }}</b>
                            <GroupTag :tone="row.recruitState === 'OPEN' ? 'green' : 'gray'">{{ UtilGroupMessages.recruitLabel(t, row.recruitState) }}</GroupTag>
                        </span>
                        <span class="mt-0.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                            <span>{{ UtilGroupMessages.members(t, row) }}</span>
                            <span v-if="row.stockNotice" class="font-semibold text-amber-700 dark:text-amber-400">{{ t('group.stockRowNote') }}</span>
                        </span>
                        <span v-if="row.summary" class="mt-1 block truncate text-xs text-muted-color">{{ row.summary }}</span>
                    </span>
                </NuxtLink>
            </li>
        </ul>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const rows = computed(() => UtilHomeGadget.groupRows(props.gadget.data, props.gadget.config));

/** 가로로 미는 작은 카드 줄 — 한 열(모바일)이고 템플릿이 골랐을 때만 */
const stacked = useHomeGadgetStacked();
const mobile = useHomeGadgetLookOptions().mobile;
const scroll = computed(() => stacked.value && mobile.groupsScroll);
const ROW = { '--row-item': '8.75rem' };
</script>
