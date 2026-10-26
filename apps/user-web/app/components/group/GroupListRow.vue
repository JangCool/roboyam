<template>
    <!-- 소모임 한 줄(컴팩트) — 이름·(모임장)·모집 상태 / 멤버 수·분류·신청제·오프라인·(주식이면 종목 추천 금지) / 한 줄 소개. 줄 전체가 상세로 가는 링크다.
         목록(`/groups`)은 오른쪽에 가입 상태 표시(누르는 단추가 아니다 — 링크 안에 단추를 넣지 않는다), 내 소모임(`/groups/me`)은 `mine` 으로 모임장 표시와 새 글 수를 보인다 -->
    <li class="border-b border-surface last:border-b-0" data-testid="group-row">
        <NuxtLink
            :to="UtilGroup.detailPath(row.id)"
            class="flex items-start gap-3 px-4 py-3 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 dark:hover:bg-surface-800"
        >
            <GroupInitial :name="row.name" />
            <span class="min-w-0 flex-1">
                <span class="flex items-center gap-1.5">
                    <b class="min-w-0 truncate text-sm" data-testid="group-row-name">{{ row.name }}</b>
                    <GroupTag v-if="leader" tone="blue" data-testid="group-row-leader">{{ t('group.roleLeader') }}</GroupTag>
                    <GroupTag :tone="row.recruitState === 'OPEN' ? 'green' : 'gray'">{{ UtilGroupMessages.recruitLabel(t, row.recruitState) }}</GroupTag>
                </span>
                <span class="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted-color">
                    <span>{{ UtilGroupMessages.members(t, row) }}</span>
                    <GroupTag v-if="row.category" tone="neutral">{{ row.category.name }}</GroupTag>
                    <span v-for="b in badges" :key="b" data-testid="group-row-badge">{{ b }}</span>
                    <span v-if="row.stockNotice" class="font-semibold text-amber-700 dark:text-amber-400" data-testid="group-row-stock">{{ t('group.stockRowNote') }}</span>
                </span>
                <span v-if="row.summary" class="mt-1 block truncate text-xs text-surface-600 dark:text-surface-300" data-testid="group-row-summary">{{ row.summary }}</span>
            </span>
            <GroupTag v-if="newPosts > 0" tone="red" class="mt-0.5" data-testid="group-row-new-posts">{{ t('group.myNewPosts', { n: newPosts }) }}</GroupTag>
            <span v-if="!mine" class="mt-0.5 inline-flex min-h-7 shrink-0 items-center rounded-md px-2.5 text-xs font-semibold" :class="ACTION_CLASS[action.tone]" data-testid="group-row-action">{{ action.label }}</span>
            <i v-else class="pi pi-angle-right mt-2 shrink-0 text-muted-color" aria-hidden="true" />
        </NuxtLink>
    </li>
</template>

<script setup lang="ts">
import type { GroupRow } from '~/composables/group/api/useGroupApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

interface Props {
    row: GroupRow;
    /** 내 소모임 줄 — 오른쪽 가입 표시 대신 ›, 모임장이면 「모임장」 */
    mine?: boolean;
    /** 내 소모임의 역할(모임장 표시) */
    role?: string | null;
    /** 새 글 수(2단계부터 서버가 채운다 — 0 이면 그리지 않는다) */
    newPosts?: number;
}

const props = withDefaults(defineProps<Props>(), {
    mine: false,
    role: null,
    newPosts: 0,
});

const { t } = useI18n();

/** 오른쪽 표시의 모양 — 가입할 수 있다(파랑 테두리) · 정원 참·마감(꺼진 회색) · 이미 내 상태(글자만) */
const ACTION_CLASS = {
    go: 'border border-primary text-primary',
    off: 'border border-surface bg-surface-100 text-muted-color dark:bg-surface-800',
    note: 'text-muted-color',
} as const;

const action = computed(() => UtilGroupMessages.rowAction(t, props.row));
const badges = computed(() => UtilGroupMessages.badges(t, props.row));
const leader = computed(() => props.mine && props.role === 'LEADER');
</script>
