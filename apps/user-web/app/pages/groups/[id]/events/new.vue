<template>
    <!--
        소모임 일정 만들기 `/groups/{id}/events/new`(community `pages/groups/[id]/events/new.vue`, `docs/api-groups.md` 9절·「화면 (SPA) — 6-3」). **모임장 또는 일정 관리(`EVENTS`)를 맡은 매니저**.
        칸: 제목(60자) · 시작(지금 뒤 · `limits.eventMaxDaysAhead` 일 안) · 끝(선택, 시작 뒤 · `limits.eventMaxLengthDays` 일 안) · 진행 방식 · 장소(오프라인) · 설명(1000자) · 오프라인 확인 체크.
        입구가 막힌 사람에게는 폼 대신 까닭(`GroupEventGate`). 등록하면 「멤버에게 알림이 가요」 토스트 후 일정 상세로(`replace`). 쓰던 내용이 있으면 나갈 때 묻는다.
    -->
    <div class="content-card mx-auto max-w-xl">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('groupEvent.createTitle') }}</span>
        </div>
        <h1 class="text-lg font-bold max-md:hidden">{{ t('groupEvent.createTitle') }}</h1>

        <template v-if="view === 'form' && group">
            <p class="mb-4 text-sm text-muted-color" data-testid="event-group-name">{{ group.name }}</p>
            <GroupEventForm
                ref="formRef"
                :form="form"
                :errors="errors"
                kind="create"
                :limits="limits"
                :submitting="submitting"
                :failure="failure"
                :ack-required="ackRequired"
                :min-start="minStart"
                :max-start="maxStart"
                :min-end="minEnd"
                :max-end="maxEnd"
                @submit="onSubmit"
                @cancel="cancel"
            />
        </template>
        <GroupEventGate v-else-if="view !== 'form'" class="mt-4" :view="view" kind="create" :group-id="id" :group-error="errorMessage" :role="group?.myRole ?? null" :blocked="blocked" @retry-group="loadGroup" />
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
const { group, view, errorMessage, blocked, limits, form, errors, failure, submitting, shouldConfirmLeave, ackRequired, minStart, maxStart, minEnd, maxEnd, loadGroup, submit } = useGroupEventForm(id);

const formRef = ref<{ focusField: (field: GroupEventField) => void } | null>(null);

useSeoMeta({ title: () => (group.value ? `${group.value.name} · ${t('groupEvent.createTitle')}` : t('groupEvent.createTitle')), robots: 'noindex' });

async function onSubmit(): Promise<void> {
    const result = await submit();
    if (!result) return;
    if ('invalid' in result) return focus(result.invalid);
    if ('rejected' in result) return result.rejected ? focus(result.rejected) : undefined;
    toast.success(t('groupEvent.createdToast'));
    await router.replace(UtilGroupEvent.eventPath(id.value, result.saved.id));
}

async function focus(field: GroupEventField): Promise<void> {
    await nextTick();
    formRef.value?.focusField(field);
}

/** 취소 — 소모임의 일정 탭으로(쓰던 내용이 있으면 나갈 때 묻는다) */
function cancel(): void {
    void router.push(UtilGroupEvent.tabPath(id.value));
}

/* ── 저장하지 않고 나가기 ── */
onBeforeRouteLeave(async () => {
    if (!shouldConfirmLeave()) return true;
    return await confirm({ title: t('groupEvent.leaveTitleCreate'), message: t('groupEvent.leaveMessage'), confirmLabel: t('groupEvent.leaveConfirm'), tone: 'danger' });
});
function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (shouldConfirmLeave()) event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));
</script>
