<template>
    <!--
        소모임 개설 신청 한 장(community `GroupAdminRequestCard`) — 이름·분류 · 가입 방식·정원·오프라인·공개 범위 · (투자 고지 게시판이면) 목적 확인 줄 · 소개(길면 접기) · 신청자·신청일 · [반려][승인].
        승인은 확인 없이 바로(신청자가 모임장이 될 뿐이라 한 번 더 묻지 않는다), 반려는 사유가 필요해 부르는 쪽이 대화상자를 연다. 서버를 부르지 않고 `approve`·`reject` 로 올린다.
        처리 권한이 없으면(`canWrite` false) 두 단추를 막고 이유를 단추 제목에 둔다.
    -->
    <article class="flex h-full flex-col rounded-xl border border-(--line) bg-(--p-content-background) p-4" :aria-labelledby="titleId">
        <div class="flex flex-wrap items-center gap-1.5">
            <h2 :id="titleId" class="min-w-0 text-base font-semibold [overflow-wrap:anywhere]">{{ request.name }}</h2>
            <GroupTag :value="request.category?.name ?? t('group.categoryNone')" />
        </div>
        <p class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-color">
            <span>{{ t(`group.join.${request.joinType}`) }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ t('group.capacity', { n: request.capacity }) }}</span>
            <span aria-hidden="true">·</span>
            <span :class="request.offline ? 'font-semibold text-amber-700 dark:text-amber-400' : ''">{{ request.offline ? t('group.offlineYes') : t('group.offlineNo') }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ t('group.visibilityRequested', { value: t(`group.visibility.${request.visibility}`) }) }}</span>
            <span aria-hidden="true">·</span>
            <span>{{ request.board.name }}</span>
        </p>

        <!-- 투자 고지 게시판 — 운영자가 소개와 규칙을 읽고 목적을 확인한다. 종목 추천·유료 모집은 승인하지 않는다 -->
        <Message v-if="request.stockPurposeAgreed" severity="warn" :closable="false" size="small" class="mt-3">
            <b class="block">{{ t('group.stockLine', { answer: t('common.yes') }) }}</b>
            <span class="block">{{ t('group.stockCheck') }}</span>
        </Message>

        <p :id="descId" class="mt-3 text-sm leading-relaxed whitespace-pre-line [overflow-wrap:anywhere]" :class="expanded ? '' : 'line-clamp-3'">{{ request.description }}</p>
        <Button v-if="long" type="button" variant="link" size="small" class="mt-1 self-start p-0! text-xs" :label="expanded ? t('group.collapse') : t('group.expand')" :aria-expanded="expanded" :aria-controls="descId" @click="expanded = !expanded" />

        <p class="mt-3 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-xs text-muted-color">
            <span>{{ t('group.requester') }}</span>
            <GroupPersonLabel :person="request.requester" />
            <span aria-hidden="true">·</span>
            <span :title="time.full(request.requestedAt)">{{ t('group.requestedOn', { date: time.date(request.requestedAt) }) }}</span>
        </p>

        <div class="mt-auto flex gap-2 pt-4">
            <Button
                type="button"
                :label="t('group.reject')"
                severity="danger"
                variant="outlined"
                class="flex-1"
                :disabled="busy || !canWrite"
                :title="canWrite ? undefined : t('group.noWriteReason')"
                :aria-label="t('group.rejectLabel', { name: request.name })"
                @click="emit('reject')"
            />
            <Button
                type="button"
                :label="t('group.approve')"
                class="flex-1"
                :loading="approving"
                :disabled="busy || !canWrite"
                :title="canWrite ? undefined : t('group.noWriteReason')"
                :aria-label="t('group.approveLabel', { name: request.name })"
                @click="emit('approve')"
            />
        </div>
    </article>
</template>

<script setup lang="ts">
import type { GroupOpenRequest } from '~/composables/group/api/useGroupAdminApi.types';
import UtilGroup from '~/utils/util-group';

interface Props {
    request: GroupOpenRequest;
    /** 어느 신청이든 처리 중 — 두 단추를 잠근다 */
    busy?: boolean;
    /** 이 신청을 승인하는 중 */
    approving?: boolean;
    /** 처리 권한(`group.manage`) */
    canWrite?: boolean;
}

const props = withDefaults(defineProps<Props>(), { busy: false, approving: false, canWrite: false });
const emit = defineEmits<{ approve: []; reject: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const uid = useId();
const titleId = `${uid}-title`;
const descId = `${uid}-desc`;

const expanded = ref(false);
const long = computed(() => UtilGroup.isLong(props.request.description));
</script>
