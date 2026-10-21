<template>
    <!--
        배치 주기(cron) 변경 — 지금 주기를 보이고 새 식을 받는다. 서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 식을 내보낸다.
        문법은 배치의 `source` 가 정한다(JAVA 6칸 · POSTGRESQL 5칸) — 여기서는 안내만 하고 식이 맞는지는 판정하지 않는다(서버가 400 `fields.schedule` 로 알린다). 열 때마다 입력칸을 지금 주기로 채운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('batch.scheduleDialogTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="job" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="flex flex-col gap-1.5">
                <div class="flex flex-wrap items-center gap-2">
                    <span class="text-sm font-medium">{{ t('batch.scheduleDialogName', { name: job.displayName }) }}</span>
                    <BatchSourceTag :source="job.source" />
                </div>
                <div class="flex flex-wrap items-baseline gap-x-2 gap-y-0.5 text-[12px] text-muted-color">
                    <span>{{ t('batch.scheduleCurrent') }}</span>
                    <code>{{ job.schedule || '—' }}</code>
                    <span v-if="currentText">· {{ currentText }}</span>
                    <span v-if="job.scheduleZone">· {{ t('batch.scheduleZoneLabel', { zone: job.scheduleZone }) }}</span>
                </div>
            </div>

            <LabeledField :label="t('batch.scheduleNew')" :help="UtilBatchMessages.scheduleHelp(t, job.source)" :error="fieldErrors.schedule">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="schedule" name="schedule" fluid class="font-mono" autocomplete="off" spellcheck="false" :placeholder="UtilBatch.scheduleExample(job.source)" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <div v-if="canResetToDefault" class="-mt-2">
                <Button type="button" v-tooltip.top="t('batch.scheduleDefaultTip', { schedule: job.defaultSchedule })" :label="t('batch.scheduleDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="busy" @click="schedule = job.defaultSchedule" />
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" :disabled="!canSubmit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BatchJobView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

interface Props {
    visible: boolean;
    /** 주기를 바꿀 배치 — 열려 있는 동안 바뀌지 않는 값(열 때의 목록 한 줄) */
    job?: BatchJobView | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    /** 서버가 준 칸별 오류 — `schedule` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { job: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [schedule: string] }>();

const { t } = useI18n();
const formId = useId();

const schedule = ref('');

/** 열 때마다 지금 주기로 채운다 — 앞 배치에 적던 식이 다음 배치에 딸려 가면 안 된다 */
watch(
    () => props.visible,
    (open) => {
        if (open) schedule.value = props.job?.schedule ?? '';
    },
    { immediate: true },
);

const currentText = computed(() => UtilBatchMessages.scheduleText(t, props.job?.schedule));
/** 앞뒤 공백만 걷는다 — 식이 맞는지는 서버가 판정한다 */
const trimmed = computed(() => schedule.value.trim());
const canSubmit = computed(() => !props.busy && trimmed.value !== '' && trimmed.value !== (props.job?.schedule ?? '').trim());
const canResetToDefault = computed(() => !!props.job?.defaultSchedule && trimmed.value !== props.job.defaultSchedule.trim());

function submit(): void {
    if (!canSubmit.value) return;
    emit('submit', trimmed.value);
}
</script>
