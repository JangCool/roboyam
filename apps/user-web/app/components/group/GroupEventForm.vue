<template>
    <!-- 일정 만들기·수정 폼 — 제목 · 시작 · (선택) 끝 · 진행 방식 · 장소(오프라인) · 설명 · 오프라인 개인 책임 고지와 확인 체크. 상태·검사·제출은 `useGroupEventForm`, 이 컴포넌트는 칸을 그리고 `submit`·`cancel` 만 올린다 -->
    <form ref="root" class="flex flex-col gap-5" novalidate :aria-label="kind === 'create' ? t('groupEvent.createTitle') : t('groupEvent.editTitle')" data-testid="event-form" @submit.prevent="emit('submit')">
        <!-- 칸에 붙일 수 없는 제출 실패(일정 수 한도·끝난/취소된 일정·요청 제한·권한·네트워크). 입력은 그대로 남아 있다 -->
        <GroupNotice v-if="failure" tone="danger" :title="failure.title" data-testid="event-failure">{{ failure.text }}</GroupNotice>

        <!-- 제목 -->
        <LabeledField :label="t('groupEvent.titleLabel')" :error="errors.title">
            <template #aside>
                <span class="text-xs tabular-nums" :class="titleCount > UtilGroupEvent.TITLE_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: titleCount, max: UtilGroupEvent.TITLE_MAX }) }}</span>
            </template>
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="form.title"
                    autocomplete="off"
                    :placeholder="t('groupEvent.titlePlaceholder')"
                    :disabled="submitting"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-field="title"
                    data-testid="event-title"
                />
            </template>
        </LabeledField>

        <!-- 시작 — 표시 시간대로 고른다(공용 `AppDateTimePicker`, 아래에 시간대 이름표). 이미 시작한 일정은 읽기 전용 -->
        <LabeledField :label="t('groupEvent.startLabel')" :error="errors.startsAt" :help="startLocked ? t('groupEvent.startLocked') : t('groupEvent.startLimitHelp', { days: limits.maxDaysAhead })">
            <template #default="{ id, describedBy, invalid }">
                <div data-field="startsAt">
                    <AppDateTimePicker
                        v-model="form.startsAt"
                        :input-id="id"
                        :min="startLocked ? undefined : minStart"
                        :max="startLocked ? undefined : maxStart"
                        :readonly="startLocked"
                        :disabled="submitting"
                        :invalid="invalid"
                        :described-by="describedBy"
                        :show-button-bar="false"
                        data-testid="event-starts-at"
                    />
                </div>
            </template>
        </LabeledField>

        <!-- 끝(선택) — 시작 뒤 · 길이 한도 안 -->
        <LabeledField :label="t('groupEvent.endLabel')" :hint="t('group.optional')" :error="errors.endsAt" :help="`${t('groupEvent.endHelp')} ${t('groupEvent.endLimitHelp', { days: limits.maxLengthDays })}`">
            <template #default="{ id, describedBy, invalid }">
                <div data-field="endsAt">
                    <AppDateTimePicker
                        v-model="form.endsAt"
                        :input-id="id"
                        :min="minEnd"
                        :max="maxEnd"
                        :disabled="submitting"
                        :invalid="invalid"
                        :described-by="describedBy"
                        :show-zone="false"
                        data-testid="event-ends-at"
                    />
                </div>
            </template>
        </LabeledField>

        <!-- 진행 방식 — 진짜 라디오를 카드 모양으로(화살표 키·스크린리더가 그대로 동작한다) -->
        <fieldset class="m-0 min-w-0 border-0 p-0" :disabled="submitting" data-field="mode">
            <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('groupEvent.modeLabel') }}</legend>
            <div class="grid grid-cols-2 gap-2">
                <label
                    v-for="option in modeOptions"
                    :key="option.value"
                    :for="`${uid}-mode-${option.value}`"
                    class="flex min-h-11 cursor-pointer items-center justify-center gap-1.5 rounded-lg border px-3 text-sm has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-1 has-[:focus-visible]:outline-primary"
                    :class="form.mode === option.value ? 'border-primary bg-primary font-semibold text-primary-contrast' : 'border-surface bg-surface-0 hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800'"
                    :data-testid="`event-mode-${option.value}`"
                >
                    <input :id="`${uid}-mode-${option.value}`" v-model="form.mode" type="radio" class="sr-only" :name="`${uid}-mode`" :value="option.value" />
                    <i v-if="form.mode === option.value" class="pi pi-check text-xs" aria-hidden="true" />
                    {{ option.label }}
                </label>
            </div>
            <p v-if="errors.mode" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ errors.mode }}</p>
            <p v-if="form.mode === 'ONLINE'" class="mt-1.5 text-xs leading-relaxed text-muted-color">{{ t('groupEvent.onlineHint') }}</p>
        </fieldset>

        <!-- 장소 — 오프라인만 -->
        <LabeledField v-if="form.mode === 'OFFLINE'" :label="t('groupEvent.placeLabel')" :help="t('groupEvent.placeHelp')" :error="errors.place">
            <template #aside>
                <span class="text-xs tabular-nums" :class="placeCount > UtilGroupEvent.PLACE_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: placeCount, max: UtilGroupEvent.PLACE_MAX }) }}</span>
            </template>
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    v-model="form.place"
                    autocomplete="off"
                    :placeholder="t('groupEvent.placePlaceholder')"
                    :disabled="submitting"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-field="place"
                    data-testid="event-place"
                />
            </template>
        </LabeledField>

        <!-- 설명 -->
        <LabeledField :label="t('groupEvent.descriptionLabel')" :hint="t('group.optional')" :error="errors.description">
            <template #aside>
                <span class="text-xs tabular-nums" :class="descriptionCount > UtilGroupEvent.DESCRIPTION_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: descriptionCount, max: UtilGroupEvent.DESCRIPTION_MAX }) }}</span>
            </template>
            <template #default="{ id, describedBy, invalid }">
                <Textarea
                    :id="id"
                    v-model="form.description"
                    rows="4"
                    auto-resize
                    :placeholder="t('groupEvent.descriptionPlaceholder')"
                    :disabled="submitting"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-field="description"
                    data-testid="event-description"
                />
            </template>
        </LabeledField>

        <!-- 오프라인 — 개인 책임 고지는 늘 보이고, 확인 체크는 새로 오프라인이 될 때만(만들기 · 온라인 → 오프라인 수정) -->
        <template v-if="form.mode === 'OFFLINE'">
            <GroupNotice tone="warn" :title="t('groupEvent.offlineBoxTitle')" data-testid="event-offline-box">{{ t('groupEvent.offlineBoxText') }}</GroupNotice>
            <div v-if="ackRequired" data-field="offlineAck">
                <div class="flex items-start gap-2.5">
                    <Checkbox v-model="form.offlineAck" binary :input-id="`${uid}-ack`" :disabled="submitting" :invalid="Boolean(errors.offlineAck)" data-testid="event-offline-ack" />
                    <label :for="`${uid}-ack`" class="cursor-pointer text-sm leading-snug">{{ t('groupEvent.offlineAck') }}</label>
                </div>
                <p v-if="errors.offlineAck" role="alert" class="mt-1 pl-7 text-xs text-red-600 dark:text-red-400">{{ errors.offlineAck }}</p>
            </div>
        </template>

        <div class="flex flex-col gap-2">
            <p class="m-0 text-xs text-muted-color">{{ kind === 'create' ? t('groupEvent.createNotice') : t('groupEvent.editNotice') }}</p>
            <div class="flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" :disabled="submitting" data-testid="event-cancel" @click="emit('cancel')" />
                <Button type="submit" :label="kind === 'create' ? t('groupEvent.createSubmit') : t('groupEvent.editSubmit')" :loading="submitting" class="flex-1" data-testid="event-submit" />
            </div>
        </div>
    </form>
</template>

<script setup lang="ts">
import type { GroupEventMode } from '~/composables/group/api/useGroupApi.types';
import UtilGroupEvent, { type GroupEventField, type GroupEventFormState, type GroupEventLimits } from '~/utils/util-group-event';
import UtilGroupEventMessages, { type GroupEventCopy, type GroupEventFormKind } from '~/utils/util-group-event-messages';

/**
 * 소모임 일정 만들기·수정 폼(community `GroupEventForm`) — `/groups/{id}/events/new` 와 `…/{eventId}/edit` 가 같이 쓴다.
 * `form` 은 `useGroupEventForm().form`(reactive)이다 — 칸은 이 객체에 바로 `v-model` 한다(상태의 주인은 컴포저블).
 * 시각은 공용 `AppDateTimePicker`(값 = 순간 ISO, 표시 시간대로 고른다)로 고르고, 고를 수 있는 범위는 서버 일정 한도(`limits`)로 막는다 — 손으로 쳐 넣은 값은 검사가 막는다.
 * 이미 시작한 일정(`startLocked`)은 시작이 읽기 전용이다. 오류는 칸 아래에, 칸에 붙지 않는 실패는 폼 위(`failure`)에.
 */
interface Props {
    form: GroupEventFormState;
    errors: Partial<Record<GroupEventField, string>>;
    kind: GroupEventFormKind;
    limits: GroupEventLimits;
    submitting?: boolean;
    failure?: GroupEventCopy | null;
    startLocked?: boolean;
    ackRequired?: boolean;
    minStart?: string;
    maxStart?: string;
    minEnd?: string;
    maxEnd?: string;
}

const props = withDefaults(defineProps<Props>(), {
    submitting: false,
    failure: null,
    startLocked: false,
    ackRequired: false,
    minStart: undefined,
    maxStart: undefined,
    minEnd: undefined,
    maxEnd: undefined,
});

const emit = defineEmits<{ submit: []; cancel: [] }>();

const { t } = useI18n();
const uid = useId();
const root = ref<HTMLFormElement | null>(null);

const modeOptions = computed<{ value: GroupEventMode; label: string }[]>(() => [
    { value: 'ONLINE', label: UtilGroupEventMessages.modeLabel(t, 'ONLINE') },
    { value: 'OFFLINE', label: UtilGroupEventMessages.modeLabel(t, 'OFFLINE') },
]);

const titleCount = computed(() => UtilGroupEvent.length(props.form.title.trim()));
const placeCount = computed(() => UtilGroupEvent.length(props.form.place.trim()));
const descriptionCount = computed(() => UtilGroupEvent.length(props.form.description.trim()));

/** 칸으로 초점을 옮긴다 — 제출했을 때 첫 오류 칸으로. 묶음이면 안의 첫 입력으로 */
function focusField(field: GroupEventField): void {
    const el = root.value?.querySelector<HTMLElement>(`[data-field="${field}"]`);
    if (!el) return;
    const target = el.matches('input, textarea, button') ? el : el.querySelector<HTMLElement>('input, textarea, button');
    (target ?? el).focus();
}

defineExpose({ focusField });
</script>
