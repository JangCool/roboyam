<template>
    <!--
        파라미터 지정 실행 — 배치가 받는 실행 파라미터(`job.params`)와 돌릴 단계(`job.steps` 중 고를 수 있는 것)를 받아 `submit` 으로 내보낸다. 서버 호출은 부르는 쪽(배치 관리 화면)이 한다.
        칸 꼴: `DATE` 달력 날짜(`AppDatePicker` — 값은 `YYYY-MM-DD`, 시간대 환산 없음) · `LONG` 숫자 글자(`inputmode=numeric` — `InputNumber` 는 범위 밖 값을 말없이 고친다) · `STRING` 글.
        판정은 서버가 한다 — 400 `VALIDATION` 의 `fields.params.<이름>` 은 그 칸 아래, `fields.steps` 는 단계 목록 아래, 칸에 붙지 않는 것(정의에 없는 파라미터 이름 `fields.params` 등)은 위쪽에 서버 문구 그대로 붙는다. 열 때마다 빈 칸 · 모든 단계로 시작한다.
        고를 수 없는 단계(`selectable=false`)는 늘 돈다 — 잠근 채 체크해 둔다(확정). 모든 단계를 고른 채면 `steps` 를 보내지 않는다(= 모두).
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('batch.runOptionsTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="job" :id="formId" class="flex flex-col gap-5" novalidate @submit.prevent="submit">
            <div class="flex flex-wrap items-center gap-2">
                <span class="text-sm font-medium">{{ t('batch.scheduleDialogName', { name: job.displayName }) }}</span>
                <BatchSourceTag :source="job.source" />
            </div>

            <!-- 칸에 붙지 않는 서버 오류 — 예: 정의에 없는 파라미터 이름(`fields.params` 한 칸) -->
            <Message v-for="message in strayErrors" :key="message" severity="error" size="small" variant="simple" :closable="false">{{ message }}</Message>

            <section v-if="job.params.length" class="flex flex-col gap-3" :aria-labelledby="`${formId}-params`">
                <h3 :id="`${formId}-params`" class="text-[13px] font-medium text-muted-color">{{ t('batch.paramSection') }}</h3>
                <LabeledField
                    v-for="param in job.params"
                    :key="param.name"
                    :label="param.label"
                    :hint="param.required ? t('batch.paramRequired') : t('batch.paramOptional')"
                    :help="param.help ?? ''"
                    :error="fieldErrors[`params.${param.name}`]"
                >
                    <template #default="{ id, describedBy, invalid }">
                        <AppDatePicker
                            v-if="param.type === 'DATE'"
                            :model-value="values[param.name] ?? ''"
                            :input-id="id"
                            @update:model-value="(v: string) => (values[param.name] = v)" :described-by="describedBy" :invalid="invalid" :disabled="busy" show-button-bar />
                        <InputText
                            v-else
                            :id="id"
                            v-model="values[param.name]"
                            :name="param.name"
                            fluid
                            autocomplete="off"
                            :inputmode="param.type === 'LONG' ? 'numeric' : undefined"
                            :placeholder="param.type === 'LONG' ? t('batch.paramNumberPlaceholder') : undefined"
                            :class="param.type === 'LONG' ? 'tabular-nums' : ''"
                            :disabled="busy"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                        />
                    </template>
                </LabeledField>
            </section>

            <section v-if="showSteps" class="flex flex-col gap-2" :aria-labelledby="`${formId}-steps`" :aria-describedby="`${formId}-steps-help`">
                <h3 :id="`${formId}-steps`" class="text-[13px] font-medium text-muted-color">{{ t('batch.stepSection') }}</h3>
                <ol class="flex flex-col gap-1.5">
                    <li v-for="(step, index) in job.steps" :key="step.name" class="flex items-center gap-2.5">
                        <span class="w-5 text-right text-[11px] tabular-nums text-muted-color">{{ index + 1 }}</span>
                        <Checkbox v-if="step.selectable" v-model="chosen" :value="step.name" :input-id="`${formId}-step-${step.name}`" :disabled="busy" />
                        <Checkbox v-else :model-value="true" binary :input-id="`${formId}-step-${step.name}`" disabled />
                        <label :for="`${formId}-step-${step.name}`" class="text-[13px]" :class="step.selectable ? '' : 'text-muted-color'">{{ step.label }}</label>
                        <span v-if="!step.selectable" v-tooltip.top="t('batch.stepAlwaysTip')" class="text-[11px] text-muted-color underline decoration-dotted">{{ t('batch.stepAlways') }}</span>
                    </li>
                </ol>
                <p :id="`${formId}-steps-help`" class="text-xs leading-relaxed" :class="fieldErrors.steps ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="fieldErrors.steps ? 'alert' : undefined">
                    {{ fieldErrors.steps || t('batch.stepHelp') }}
                </p>
            </section>

            <p class="text-[12px] leading-relaxed text-muted-color">{{ t('batch.runOptionsNote') }}</p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" icon="pi pi-play" :label="t('batch.runOptionsSubmit')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BatchJobView, BatchRunRequest } from '~/composables/batch/api/useBatchApi.types';

interface Props {
    visible: boolean;
    /** 실행할 배치 — 열려 있는 동안 바뀌지 않는 값(열 때의 목록 한 줄) */
    job?: BatchJobView | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    /** 서버가 준 칸별 오류 — `params.<이름>` · `steps` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { job: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: BatchRunRequest] }>();

const { t } = useI18n();
const formId = useId();

const values = ref<Record<string, string>>({});
/** 고른 단계(고를 수 있는 단계 중) */
const chosen = ref<string[]>([]);

const selectable = computed(() => (props.job?.steps ?? []).filter((s) => s.selectable).map((s) => s.name));
/** 단계가 둘 이상이고 그중 고를 수 있는 단계가 있을 때만 단계 고르기를 보인다 */
const showSteps = computed(() => (props.job?.steps.length ?? 0) > 1 && selectable.value.length > 0);
/** 서버 칸 오류 중 이 대화상자의 칸(`params.<이름>` · 보이는 단계 목록 `steps`)에 붙지 않는 것 — 위쪽에 모아 보인다 */
const strayErrors = computed(() => {
    const known = new Set((props.job?.params ?? []).map((p) => `params.${p.name}`));
    if (showSteps.value) known.add('steps');
    return Object.entries(props.fieldErrors)
        .filter(([key]) => !known.has(key))
        .map(([, message]) => message);
});

/** 열 때마다 빈 칸 · 모든 단계로 — 앞 배치에 적던 값이 다음 배치에 딸려 가면 안 된다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        values.value = Object.fromEntries((props.job?.params ?? []).map((p) => [p.name, '']));
        chosen.value = [...selectable.value];
    },
    { immediate: true },
);

function submit(): void {
    if (props.busy || !props.job) return;
    // 빈 칸은 보내지 않는다(서버가 기본값을 쓴다). 판정은 서버가 한다
    const params = Object.fromEntries(Object.entries(values.value).map(([k, v]) => [k, (v ?? '').trim()]).filter(([, v]) => v !== ''));
    const allChosen = selectable.value.every((name) => chosen.value.includes(name));
    const body: BatchRunRequest = {};
    if (Object.keys(params).length) body.params = params;
    if (showSteps.value && !allChosen) body.steps = selectable.value.filter((name) => chosen.value.includes(name));
    emit('submit', body);
}
</script>
