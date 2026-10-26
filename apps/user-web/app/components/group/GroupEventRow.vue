<template>
    <!-- 일정 한 줄 — 날짜 배지(월·일·요일) / 제목 / 「20:00 · 온라인 · 참석 14명」 / 작은 표시(진행 중·종료·취소됨, 내 응답). 줄 전체가 일정 상세로 가는 링크다.
         「수정」은 링크 안에 넣지 않고 옆에 따로 둔다(`<a>` 안에 `<a>` 를 넣을 수 없다) -->
    <li class="flex items-stretch border-b border-surface last:border-b-0" :data-state="row.state" data-testid="group-event-row">
        <NuxtLink
            :to="UtilGroupEvent.eventPath(groupId, row.id)"
            class="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 dark:hover:bg-surface-800"
        >
            <span v-if="badge" class="grid w-12 shrink-0 justify-items-center rounded-lg bg-surface-100 py-1.5 leading-tight dark:bg-surface-800" aria-hidden="true">
                <small class="text-[0.6875rem] text-muted-color">{{ badge.month }}</small>
                <b class="text-lg">{{ badge.day }}</b>
                <small class="text-[0.6875rem] text-muted-color">{{ badge.weekday }}</small>
            </span>
            <span class="min-w-0 flex-1">
                <!-- 배지는 눈으로만 읽으므로 일시를 글자로도 둔다 -->
                <span class="sr-only">{{ when }}</span>
                <b class="block break-words text-sm leading-snug [overflow-wrap:anywhere]" :class="row.state === 'CANCELED' ? 'font-semibold text-muted-color line-through' : ''" data-testid="group-event-title">{{ row.title }}</b>
                <small class="mt-0.5 block break-words text-xs text-muted-color">{{ summary }}</small>
                <span v-if="stateTag || responseTag" class="mt-1 flex flex-wrap items-center gap-1">
                    <GroupTag v-if="stateTag" :tone="stateTag.tone" data-testid="group-event-state">{{ stateTag.label }}</GroupTag>
                    <GroupTag v-if="responseTag" :tone="responseTag.tone" data-testid="group-event-response">{{ responseTag.label }}</GroupTag>
                </span>
            </span>
            <i v-if="!editable" class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
        </NuxtLink>
        <NuxtLink
            v-if="editable"
            :to="UtilGroupEvent.editPath(groupId, row.id)"
            class="flex min-h-11 shrink-0 items-center px-4 text-sm font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2"
            :aria-label="t('groupEvent.editAria', { title: row.title })"
            data-testid="group-event-edit"
        >{{ t('groupEvent.edit') }}</NuxtLink>
    </li>
</template>

<script setup lang="ts">
import type { GroupEventRow } from '~/composables/group/api/useGroupApi.types';
import UtilGroupEvent from '~/utils/util-group-event';
import UtilGroupEventMessages from '~/utils/util-group-event-messages';

/**
 * 일정 한 줄 — 상세의 일정 탭(다가오는 일정)과 지난 일정 화면이 같이 쓴다(community `GroupEventRow`).
 * 취소된 일정은 제목에 취소선을 긋고 「취소됨」을 붙이며 내 응답은 보이지 않는다. 진행 중이면 「진행 중」.
 * `canEdit`(모임장·`EVENTS` 매니저)이고 끝나지 않은 일정이면 줄 오른쪽에 「수정」 링크를 둔다 — 줄 링크의 `›` 자리를 대신한다.
 */
interface Props {
    groupId: string;
    row: GroupEventRow;
    /** 일정을 관리할 수 있다 — 끝나지 않은 일정에 「수정」 */
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    canEdit: false,
});

const { t } = useI18n();
const time = useDisplayTime();

const badge = computed(() => UtilGroupEvent.dateBadge(props.row.startsAt, time.value));
const when = computed(() => UtilGroupEvent.when(props.row.startsAt, props.row.endsAt, time.value));
const summary = computed(() => UtilGroupEventMessages.rowSummary(t, props.row, time.value));
const stateTag = computed(() => UtilGroupEventMessages.stateTag(t, props.row.state));
const responseTag = computed(() => (UtilGroupEvent.showsResponse(props.row.state) ? UtilGroupEventMessages.responseTag(t, props.row.myResponse) : null));
const editable = computed(() => props.canEdit && UtilGroupEvent.isEditable(props.row.state));
</script>
