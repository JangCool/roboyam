<template>
    <!--
        사유·메모 한 칸을 받는 확인 대화상자 — 개설 신청 반려(사유 필수) · 소모임 비활성화(사유 필수) · 다시 열기(메모 선택)가 같이 쓴다. community `ReasonSheet` 를 옮겼다.
        글자 수는 앞뒤 공백을 뗀 유니코드 한 글자씩(서버와 같다). 한도를 넘거나 필수인데 비었으면 [확인]을 눌렀을 때 칸 아래에 알린다. 서버가 거절한 문구(`error`)는 칸 아래에 그대로.
        열 때마다 칸을 비운다. 보내는 중(`busy`)에는 닫히지 않는다. 서버를 부르지 않고 `submit` 으로 올린다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
        <form :id="formId" class="flex flex-col gap-3" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>
            <LabeledField :label="label" :hint="hint" :help="help" :error="shownError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="text" rows="4" auto-resize fluid :placeholder="placeholder" :invalid="invalid" :aria-describedby="describedBy" :aria-required="required" :readonly="busy" />
                </template>
                <template #aside>
                    <span class="text-xs tabular-nums" :class="tooLong ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" aria-live="polite">{{ t('group.reasonCounter', { n: length, max }) }}</span>
                </template>
            </LabeledField>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="confirmLabel" :severity="tone === 'danger' ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilGroup from '~/utils/util-group';

interface Props {
    title: string;
    description?: string;
    label: string;
    hint?: string;
    help?: string;
    placeholder?: string;
    max: number;
    required?: boolean;
    confirmLabel: string;
    tone?: 'danger' | 'primary';
    busy?: boolean;
    /** 서버가 거절한 문구(`fields.reason`·`fields.memo` 또는 서버 문구) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { description: '', hint: '', help: '', placeholder: '', required: false, tone: 'danger', busy: false, error: '' });
const emit = defineEmits<{ submit: [text: string] }>();
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const formId = useId();

const text = ref('');
const tried = ref(false);

const length = computed(() => UtilGroup.trimmedLength(text.value));
const tooLong = computed(() => length.value > props.max);
const localError = computed(() => {
    if (tooLong.value) return t('group.reasonTooLong', { max: props.max });
    if (tried.value && props.required && length.value === 0) return t('group.reasonRequired');
    return '';
});
const shownError = computed(() => localError.value || props.error);

// 열 때마다 비운다
watch(open, (value) => {
    if (!value) return;
    text.value = '';
    tried.value = false;
});

function submit() {
    tried.value = true;
    if (props.busy || !UtilGroup.reasonOk(text.value, props.max, props.required)) return;
    emit('submit', text.value.trim());
}
</script>
