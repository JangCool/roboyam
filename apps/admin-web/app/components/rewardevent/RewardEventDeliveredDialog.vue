<template>
    <!--
        직접 전달 「전달 완료 표시」 — 운영자가 사이트 밖에서 보낸 뒤 당첨자마다 표시한다. 메모(선택, 200자)는 감사 기록에 남는다 — 전화번호·주소 같은 개인정보는 적지 않는다.
        서버는 부르지 않는다 — 메모는 `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('rewardEvent.delivered.title')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm">{{ t('rewardEvent.delivered.lead', { name: memberName, reward: rewardName }) }}</p>
            <LabeledField :label="t('rewardEvent.delivered.memo')" :hint="t('rewardEvent.form.optional')" :help="t('rewardEvent.delivered.memoHelp')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="memo" :maxlength="UtilRewardEvent.DELIVERED_MEMO_MAX" :placeholder="t('rewardEvent.delivered.memoPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('rewardEvent.delivered.submit')" icon="pi pi-check" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilRewardEvent from '~/utils/util-reward-event';

interface Props {
    visible: boolean;
    memberName: string;
    rewardName: string;
    busy?: boolean;
    error?: string;
    /** 서버가 메모 칸에 준 오류(`fields.memo`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [memo: string] }>();

const { t } = useI18n();
const formId = useId();
const memo = ref('');

watch(
    () => props.visible,
    (open) => {
        if (open) memo.value = '';
    },
);

const memoError = computed(() => (memo.value.length > UtilRewardEvent.DELIVERED_MEMO_MAX ? t('rewardEvent.problem.tooLong', { max: UtilRewardEvent.DELIVERED_MEMO_MAX }) : props.fieldError));

function submit(): void {
    if (props.busy || memo.value.length > UtilRewardEvent.DELIVERED_MEMO_MAX) return;
    emit('submit', memo.value.trim());
}
</script>
