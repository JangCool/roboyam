<template>
    <!--
        소모임 분류 화면의 왼쪽 목록(community `GroupCategoryList`) — 분류 줄마다 [▲▼](설정 권한이 있을 때만, 누르면 바로 저장)와 줄 전체 단추(이름 · 게시판 수 · 소모임 수 · 투자 고지 딱지).
        줄을 누르면 오른쪽 패널이 그 분류가 된다(저장 전 변경 확인은 부르는 쪽 — 여기서는 `select` 만 올린다). 게시판이 0개인 분류는 점선 줄과 「칩에 나오지 않아요」.
        community 와 다른 점: 손잡이 끌어 놓기·Alt+↑↓ 는 옮기지 않았다(게시판 관리의 순서 단추와 같은 ▲▼ 만). 서버를 부르지 않는다.
    -->
    <AppGadget :title="t('group.categoryListTitle', { n: categories.length })">
        <ul class="m-0 flex list-none flex-col gap-1.5 p-0" :aria-label="t('group.categoryListLabel')">
            <li
                v-for="(category, index) in categories"
                :key="category.id"
                class="flex items-stretch gap-1 rounded-lg border p-1"
                :class="[category.id === selectedId ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', category.boardCount === 0 ? 'border-dashed' : '']"
            >
                <div v-if="canEdit" class="flex shrink-0 flex-col justify-center">
                    <Button type="button" icon="pi pi-angle-up" size="small" severity="secondary" variant="text" class="h-6! w-7!" :aria-label="t('group.categoryMoveUp', { name: category.name })" :disabled="busy || index === 0" @click="emit('move', category.id, -1)" />
                    <Button type="button" icon="pi pi-angle-down" size="small" severity="secondary" variant="text" class="h-6! w-7!" :aria-label="t('group.categoryMoveDown', { name: category.name })" :disabled="busy || index === categories.length - 1" @click="emit('move', category.id, 1)" />
                </div>
                <button
                    type="button"
                    class="min-w-0 flex-1 cursor-pointer rounded-md border-0 bg-transparent px-2 py-1.5 text-left text-color focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary disabled:cursor-default"
                    :aria-current="category.id === selectedId ? 'true' : undefined"
                    :disabled="busy"
                    @click="emit('select', category.id)"
                >
                    <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span class="min-w-0 text-sm font-semibold [overflow-wrap:anywhere]" :class="category.id === selectedId ? 'text-primary' : ''">{{ category.name }}</span>
                        <GroupTag v-if="category.investmentNotice" :value="t('group.categoryInvest')" severity="warn" />
                    </span>
                    <span class="mt-0.5 block text-xs text-muted-color">{{ UtilGroupMessages.categoryMeta(t, category.boardCount, category.groupCount, locale) }}</span>
                </button>
            </li>
        </ul>
        <Message v-if="orderError" severity="error" variant="simple" size="small" class="mt-2" role="alert">{{ orderError }}</Message>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupCategoryItem } from '~/composables/group/api/useGroupCategoryApi.types';
import UtilGroupMessages from '~/utils/util-group-messages';

interface Props {
    categories: GroupCategoryItem[];
    selectedId: string;
    /** 서버를 부르는 중 */
    busy?: boolean;
    /** 순서를 바꿀 수 있다(`group.settings`) */
    canEdit?: boolean;
    /** 순서 바꾸기를 서버가 거절했다 — 원래 순서로 돌렸다는 안내 */
    orderError?: string;
}

withDefaults(defineProps<Props>(), { busy: false, canEdit: false, orderError: '' });
const emit = defineEmits<{ select: [id: string]; move: [id: string, step: -1 | 1] }>();

const { t, locale } = useI18n();
</script>
