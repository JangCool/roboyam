<template>
    <!--
        반려 대화상자 — 사유 코드(Select) + 글(사업자에게 그대로 간다). 사업자 신원 반려와 신청 건 반려가 같이 쓴다(코드 목록만 다르다).
        아래 슬롯에 「결제 전이라 환불이 없어요」 같은 안내를 둔다. 서버는 부르지 않는다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>

            <LabeledField :label="t('bizApplication.reject.code')" :error="submitted && !code ? t('bizApplication.reject.codeRequired') : (fieldErrors.code ?? '')">
                <template #default="{ id, describedBy, invalid }">
                    <Select v-model="code" :input-id="id" :options="codes" option-label="label" option-value="value" :placeholder="t('bizApplication.reject.codePlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('bizApplication.reject.text')" :hint="t('bizApplication.reason.hint', { min: REASON.min, max: REASON.max })" :help="t('bizApplication.reject.textHelp')" :error="textError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="text" rows="4" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
            </LabeledField>

            <slot />

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="confirmLabel" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts" generic="C extends string">
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    confirmLabel: string;
    codes: { value: C; label: string }[];
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { description: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: { code: C; text: string }] }>();

const { t } = useI18n();
const formId = useId();
const REASON = UtilBizApplication.REASON;

const code = ref<C | null>(null) as Ref<C | null>;
const text = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        code.value = null;
        text.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const textError = computed(() => {
    const problem = UtilBizApplication.reasonProblem(text.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) return t(`bizApplication.reason.${problem}`, { min: REASON.min, max: REASON.max });
    return props.fieldErrors.text ?? '';
});

function submit(): void {
    submitted.value = true;
    if (props.busy || !code.value || UtilBizApplication.reasonProblem(text.value)) return;
    emit('submit', { code: code.value, text: text.value.trim() });
}
</script>
