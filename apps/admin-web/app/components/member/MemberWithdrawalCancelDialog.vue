<template>
    <!--
        운영자 대리 취소(회원 요청 시, `member.withdraw`) 대화상자 — 탈퇴 관리 서랍의 [대리 취소]와 회원 상세 「탈퇴 신청」 블록의 [대리 취소]가 같은 모양으로 쓴다(사용자 결정 2026-11-04: 「운영자 취소 있어야지. 회원이 요청한 경우에만」).
        대상은 진행 중 + 회원이 신청한 탈퇴만(운영자 강제 탈퇴는 열지 않는다 — 여는 쪽이 `UtilMemberWithdrawal.canOperatorCancel` 로 거른다).
        위에서 아래로: 대상 · 「회원 본인이 취소를 요청했나요?」 확인 체크(필수) · 요청 경로(고객센터 문의 · 전화 · 이메일 · 기타, 필수) · 운영 메모(필수 5~500자) · 효과 안내(신청 전 상태로 돌아감, 기록은 「취소」) · 감사 안내.
        서버 호출은 부모가 하고 여기는 `submit` 으로 요청 본문을 올린다. 서버 칸 오류(`fieldErrors.memberConfirmed|requestChannel|memo`)는 칸 아래, 그 밖은 폼 아래에 서버 문구 그대로. 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('memberWithdrawal.cancel.title')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm">{{ t('memberWithdrawal.cancel.target', { name: memberName, memberNo }) }}</p>

            <!-- 회원 본인 요청 확인 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('memberWithdrawal.cancel.confirmLabel') }}</legend>
                <label class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5" :class="[confirmed ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']">
                    <Checkbox v-model="confirmed" binary :input-id="`${formId}-confirm`" :disabled="busy" :invalid="Boolean(confirmError)" class="mt-0.5" />
                    <span class="text-sm">{{ t('memberWithdrawal.cancel.confirmText') }}</span>
                </label>
                <p v-if="confirmError" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ confirmError }}</p>
            </fieldset>

            <!-- 요청 경로 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('memberWithdrawal.cancel.channelLabel') }}</legend>
                <div class="flex flex-wrap gap-2">
                    <label
                        v-for="option in UtilMemberWithdrawal.CANCEL_CHANNELS"
                        :key="option"
                        class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-1.5 text-sm"
                        :class="[channel === option ? 'border-primary bg-primary-50 font-semibold dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']"
                    >
                        <RadioButton v-model="channel" :value="option" :name="`${formId}-channel`" :disabled="busy" />
                        <i :class="UtilMemberWithdrawal.cancelChannelIcon(option)" aria-hidden="true" />
                        {{ t(`memberWithdrawal.cancel.channel.${option}`) }}
                    </label>
                </div>
                <p v-if="fieldErrors.requestChannel" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.requestChannel }}</p>
            </fieldset>

            <!-- 운영 메모(필수) -->
            <LabeledField :label="t('memberWithdrawal.cancel.memoLabel')" :hint="t('memberWithdrawal.cancel.memoHint', { min: WITHDRAWAL_CANCEL_MEMO_MIN, max: WITHDRAWAL_CANCEL_MEMO_MAX })" :help="t('memberWithdrawal.cancel.memoHelp')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" name="memo" rows="3" fluid :maxlength="WITHDRAWAL_CANCEL_MEMO_MAX + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>
            <p class="-mt-3 text-right text-[11px] tabular-nums text-muted-color">{{ t('reason.count', { count: memo.trim().length, max: WITHDRAWAL_CANCEL_MEMO_MAX }) }}</p>

            <Message severity="info" size="small" :closable="false">{{ t('memberWithdrawal.cancel.effectNotice', { prior: t(`member.status.${priorStatus}`) }) }}</Message>
            <p class="m-0 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2 text-xs leading-relaxed dark:bg-surface-800">
                <i class="pi pi-shield mt-0.5" aria-hidden="true" />
                <span>{{ t('memberWithdrawal.cancel.auditNotice') }}</span>
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('memberWithdrawal.cancel.submit')" icon="pi pi-undo" severity="warn" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MemberStatus } from '~/composables/member/api/useMemberApi.types';
import { WITHDRAWAL_CANCEL_MEMO_MAX, WITHDRAWAL_CANCEL_MEMO_MIN, type WithdrawalCancelChannel, type WithdrawalCancelRequest } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';

interface Props {
    visible: boolean;
    memberName: string;
    memberNo: string;
    /** 신청 전 상태 — 취소하면 돌아갈 상태(효과 안내에 보인다) */
    priorStatus: MemberStatus;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 거절한 칸 문구 — 키 `memberConfirmed`·`requestChannel`·`memo` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: WithdrawalCancelRequest] }>();

const { t } = useI18n();
const formId = useId();

const confirmed = ref(false);
const channel = ref<WithdrawalCancelChannel>('CS_INQUIRY');
const memo = ref('');
const submitted = ref(false);
const showServerMemo = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        confirmed.value = false;
        channel.value = 'CS_INQUIRY';
        memo.value = '';
        submitted.value = false;
        showServerMemo.value = false;
    },
    { immediate: true },
);
watch(
    () => props.fieldErrors.memo,
    (value) => (showServerMemo.value = Boolean(value)),
);
watch(memo, () => (showServerMemo.value = false));

const confirmError = computed(() => {
    if (props.fieldErrors.memberConfirmed) return props.fieldErrors.memberConfirmed;
    return submitted.value && !confirmed.value ? t('memberWithdrawal.cancel.confirmRequired') : '';
});

const memoError = computed(() => {
    if (showServerMemo.value && props.fieldErrors.memo) return props.fieldErrors.memo;
    if (!submitted.value) return '';
    const length = memo.value.trim().length;
    if (length === 0) return t('memberWithdrawal.cancel.memoRequired');
    if (length < WITHDRAWAL_CANCEL_MEMO_MIN || length > WITHDRAWAL_CANCEL_MEMO_MAX) return t('memberWithdrawal.cancel.memoLength', { min: WITHDRAWAL_CANCEL_MEMO_MIN, max: WITHDRAWAL_CANCEL_MEMO_MAX });
    return '';
});

function submit(): void {
    submitted.value = true;
    showServerMemo.value = false;
    if (!confirmed.value || memoError.value || props.busy) return;
    emit('submit', { requestChannel: channel.value, memo: memo.value.trim(), memberConfirmed: true });
}
</script>
