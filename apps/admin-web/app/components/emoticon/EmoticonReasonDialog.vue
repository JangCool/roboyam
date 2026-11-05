<template>
    <!--
        사유 대화상자 — 로보티콘 반려(심사 화면)와 판매 중단(팩 관리 화면)이 같이 쓴다. 사유는 작가에게 그대로 보인다.
        위에서 아래로: 안내(`description`) · 자주 쓰는 사유(누르면 아래 칸에 문구가 채워지고 고칠 수 있다) · 사유(필수 5~200자) · 위 슬롯 · 감사 안내 한 줄.
        서버는 부르지 않는다 — 사유는 `submit` 으로 올리고 저장·토스트는 부모 몫이다. 열 때마다 비운다(`BizReasonDialog` 와 같은 모양).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>

            <div v-if="presets.length" class="flex flex-col gap-1.5">
                <span :id="presetLabelId" class="text-sm font-medium">{{ t('emoticon.reason.presets') }}</span>
                <div class="flex flex-wrap gap-1.5" role="group" :aria-labelledby="presetLabelId">
                    <Button
                        v-for="preset in presets"
                        :key="preset.code"
                        type="button"
                        :label="preset.label"
                        size="small"
                        :severity="picked === preset.code ? undefined : 'secondary'"
                        :variant="picked === preset.code ? undefined : 'outlined'"
                        :aria-pressed="picked === preset.code"
                        :disabled="busy"
                        @click="pick(preset)"
                    />
                </div>
            </div>

            <LabeledField :label="label || t('emoticon.reason.label')" :hint="t('emoticon.reason.hint', { min: REASON.min, max: REASON.max })" :help="t('emoticon.reason.help')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="4" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reason.trim().length > REASON.max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ reason.trim().length }}/{{ REASON.max }}</small>
                </template>
            </LabeledField>

            <slot />

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('emoticon.reason.audit') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="confirmLabel" :severity="tone === 'danger' ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { EmoticonReasonPreset } from '~/components/emoticon/EmoticonReasonDialog.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    /** 사유 칸 이름(기본 「사유」) */
    label?: string;
    confirmLabel: string;
    tone?: 'danger' | 'primary';
    presets?: EmoticonReasonPreset[];
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 사유 칸에 준 오류(`fields.reason`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { description: '', label: '', tone: 'danger', presets: () => [], busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();
const presetLabelId = useId();
const REASON = UtilEmoticon.REASON;

const reason = ref('');
const picked = ref<string | null>(null);
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        picked.value = null;
        submitted.value = false;
    },
    { immediate: true },
);

function pick(preset: EmoticonReasonPreset): void {
    picked.value = preset.code;
    reason.value = preset.text;
}

const reasonError = computed(() => {
    const problem = UtilEmoticon.reasonProblem(reason.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) return UtilEmoticonMessages.reasonProblem(t, problem);
    return props.fieldError;
});

function submit(): void {
    submitted.value = true;
    if (props.busy || UtilEmoticon.reasonProblem(reason.value)) return;
    emit('submit', reason.value.trim());
}
</script>
