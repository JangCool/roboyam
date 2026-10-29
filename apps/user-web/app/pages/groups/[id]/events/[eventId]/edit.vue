<template>
    <!--
        소모임 일정 수정 `/groups/{id}/events/{eventId}/edit`(community `…/[eventId]/edit.vue`, `docs/api-groups.md` 9절 「일정 수정」). **모임장·`EVENTS` 매니저**, **끝나지 않은 일정만**.
        일정을 받아 폼을 채운다. 저장은 `PUT` — 모든 칸을 보낸다(비운 끝 시각·설명은 지워진다). 서버는 바뀐 것이 있을 때만 멤버에게 알린다.
        진행 중 일정은 시작이 읽기 전용(서버가 준 시작 시각을 그대로 보낸다), 끝났거나 취소된 일정은 폼 대신 안내. 개인 책임 확인 체크는 온라인 → 오프라인일 때만.
        저장하면 토스트 후 일정 상세로(`replace`). 고치던 내용이 있으면 나갈 때 묻는다.
    -->
    <div class="content-card mx-auto max-w-xl">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('groupEvent.editTitle') }}</span>
        </div>
        <h1 class="text-lg font-bold max-md:hidden">{{ t('groupEvent.editTitle') }}</h1>

        <template v-if="view === 'form' && group">
            <p class="mb-4 text-sm text-muted-color" data-testid="event-group-name">{{ group.name }}</p>
            <GroupEventForm
                ref="formRef"
                :form="form"
                :errors="errors"
                kind="edit"
                :limits="limits"
                :submitting="submitting"
                :failure="failure"
                :start-locked="lockedStartsAt !== null"
                :ack-required="ackRequired"
                :min-start="minStart"
                :max-start="maxStart"
                :min-end="minEnd"
                :max-end="maxEnd"
                @submit="onSubmit"
                @cancel="cancel"
            />
        </template>
        <GroupEventGate
            v-else-if="view !== 'form'"
            class="mt-4"
            :view="view"
            kind="edit"
            :group-id="id"
            :event-id="eventId"
            :group-error="errorMessage"
            :event-error="eventErrorMessage"
            :role="group?.myRole ?? null"
            :blocked="blocked"
            @retry-group="loadGroup"
            @retry-event="loadEvent"
        />
    </div>
</template>

<script setup lang="ts">
import type { GroupEventField } from '~/utils/util-group-event';
import UtilGroupEvent from '~/utils/util-group-event';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const { confirm } = useConfirmDialog();

const id = computed(() => String(route.params.id ?? ''));
const eventId = computed(() => String(route.params.eventId ?? ''));
const {
    group,
    view,
    errorMessage,
    eventErrorMessage,
    blocked,
    limits,
    form,
    errors,
    failure,
    submitting,
    shouldConfirmLeave,
    lockedStartsAt,
    ackRequired,
    minStart,
    maxStart,
    minEnd,
    maxEnd,
    loadGroup,
    loadEvent,
    submit,
} = useGroupEventForm(id, eventId);

const formRef = ref<{ focusField: (field: GroupEventField) => void } | null>(null);

useSeoMeta({ title: () => (group.value ? `${group.value.name} · ${t('groupEvent.editTitle')}` : t('groupEvent.editTitle')), robots: 'noindex' });

async function onSubmit(): Promise<void> {
    const result = await submit();
    if (!result) return;
    if ('invalid' in result) return focus(result.invalid);
    if ('rejected' in result) return result.rejected ? focus(result.rejected) : undefined;
    toast.success(t('groupEvent.editedToast'));
    await router.replace(UtilGroupEvent.eventPath(id.value, eventId.value));
}

async function focus(field: GroupEventField): Promise<void> {
    await nextTick();
    formRef.value?.focusField(field);
}

/** 취소 — 일정 상세로(고치던 내용이 있으면 나갈 때 묻는다) */
function cancel(): void {
    void router.push(UtilGroupEvent.eventPath(id.value, eventId.value));
}

/* ── 저장하지 않고 나가기 ── */
onBeforeRouteLeave(async () => {
    if (!shouldConfirmLeave()) return true;
    return await confirm({ title: t('groupEvent.leaveTitleEdit'), message: t('groupEvent.leaveMessage'), confirmLabel: t('groupEvent.leaveConfirm'), tone: 'danger' });
});
function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (shouldConfirmLeave()) event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));
</script>
