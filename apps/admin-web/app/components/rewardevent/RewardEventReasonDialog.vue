<template>
    <!--
        보상 이벤트 사유 대화상자 — 이벤트 취소와 선택 회수가 같이 쓴다. 사유는 감사 기록에 남고(회수는 회원 포인트 내역에도 보인다 — 가정), 5~200자 필수.
        위에서 아래로: 안내(`description`) · 주의 목록(`notes`) · 위 슬롯(대상 요약 등) · 사유 · 감사 안내.
        서버는 부르지 않는다 — 사유는 `submit` 으로 올리고 저장·토스트는 부모 몫이다. 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed">{{ description }}</p>
            <ul v-if="notes.length" class="m-0 flex list-disc flex-col gap-1 pl-5 text-xs leading-relaxed text-muted-color">
                <li v-for="note in notes" :key="note">{{ note }}</li>
            </ul>

            <slot />

            <LabeledField :label="t('rewardEvent.reasonLabel')" :hint="t('rewardEvent.reasonHint', { min: UtilRewardEvent.REASON_MIN, max: UtilRewardEvent.REASON_MAX })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="3" auto-resize :maxlength="UtilRewardEvent.REASON_MAX + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reason.trim().length > UtilRewardEvent.REASON_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ reason.trim().length }}/{{ UtilRewardEvent.REASON_MAX }}</small>
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('rewardEvent.auditNote') }}
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
import UtilRewardEvent from '~/utils/util-reward-event';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    /** 주의 목록 */
    notes?: string[];
    confirmLabel: string;
    tone?: 'danger' | 'primary';
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 사유 칸에 준 오류(`fields.reason`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { description: '', notes: () => [], tone: 'danger', busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();
const reason = ref('');
const submitted = ref(false);

// 열 때마다 비운다(부수 효과 — 대화상자 입력 초기화)
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const reasonError = computed(() => {
    const problem = UtilRewardEventMessages.reasonProblem(t, reason.value);
    if (problem && (submitted.value || reason.value.trim().length > UtilRewardEvent.REASON_MAX)) return problem;
    return props.fieldError;
});

function submit(): void {
    submitted.value = true;
    if (props.busy || UtilRewardEventMessages.reasonProblem(t, reason.value)) return;
    emit('submit', reason.value.trim());
}
</script>
