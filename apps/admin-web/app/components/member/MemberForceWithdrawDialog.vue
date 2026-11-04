<template>
    <!--
        강제 탈퇴(운영자, `member.withdraw` — SENSITIVE) 대화상자 — 회원 상세 서랍의 위험 동작 [강제 탈퇴]가 연다(member-withdrawal-contract 「미확정 — 관리자 화면」, 분석 문서 5절 6번).
        위에서 아래로: 대상 · 이 회원의 남은 일·돈(미리보기 — 부모가 `useMemberWithdrawal.loadPreview` 로 받아 내려 준다) · 「남은 일이 있으면 진행 중으로 두고 끝나면 완료된다(운영자도 환불·주문을 건너뛸 수 없다)」 안내 ·
        회원에게 유리한 기본값으로 고정된 선택값 안내 · 사유 종류(만 14세 미만 확인 · 운영 정책 위반 · 본인 요청 대행 · 기타) · 운영 메모(필수 5~500자) · 감사 안내.
        운영자가 강제한 탈퇴는 회원이 요청해도 대리 취소할 수 없다(감사 안내에 적는다 — 대리 취소는 회원 신청 건만). 서버 호출은 부모가 하고 여기는 `submit` 으로 요청 본문을 올린다. 서버 칸 오류(`fieldErrors.reasonKind|memo`)는 칸 아래, 그 밖은 폼 아래에 서버 문구 그대로. 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('memberWithdrawal.force.title')" :style="{ width: 'min(38rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm">{{ t('memberWithdrawal.force.target', { name: memberName, memberNo }) }}</p>

            <!-- 남은 일·돈 미리보기 -->
            <section class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" :aria-labelledby="`${formId}-preview`">
                <h3 :id="`${formId}-preview`" class="m-0 text-[13px] font-semibold">{{ t('memberWithdrawal.force.previewTitle') }}</h3>
                <ErrorState v-if="previewError" :title="t('memberWithdrawal.force.previewLoadFailed')" :description="previewError.message" :retry="previewError.status !== 403" :retrying="previewLoading" @retry="emit('retry-preview')" />
                <div v-else-if="!preview" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="1rem" />
                </div>
                <template v-else>
                    <p v-if="!preview.pending.length" class="m-0 inline-flex items-center gap-1.5 text-[13px]"><i class="pi pi-check-circle text-green-600 dark:text-green-400" aria-hidden="true" />{{ t('memberWithdrawal.force.previewPendingNone') }}</p>
                    <template v-else>
                        <p class="m-0 inline-flex items-center gap-1.5 text-[13px] font-semibold text-amber-700 dark:text-amber-400"><i class="pi pi-hourglass" aria-hidden="true" />{{ t('memberWithdrawal.force.previewPending', { count: preview.pending.length }) }}</p>
                        <MemberWithdrawalPendingList :items="preview.pending" />
                    </template>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                        <li class="inline-flex items-center gap-1.5"><i class="pi pi-star text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewEarned', { amount: preview.earnedPoints.toLocaleString(locale) }) }}</li>
                        <li class="inline-flex items-center gap-1.5"><i class="pi pi-wallet text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewPaid', { amount: preview.paidPoints.toLocaleString(locale) }) }}</li>
                        <li v-if="preview.activeBizCount" class="inline-flex items-center gap-1.5"><i class="pi pi-megaphone text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewBiz', { count: preview.activeBizCount }) }}</li>
                        <li v-if="preview.ownedGroups.length" class="inline-flex items-center gap-1.5"><i class="pi pi-users text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewGroups', { count: preview.ownedGroups.length }) }}</li>
                        <li v-if="preview.hasPaymentHistory" class="inline-flex items-center gap-1.5"><i class="pi pi-inbox text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewPaymentHistory') }}</li>
                        <li class="inline-flex items-center gap-1.5 tabular-nums"><i class="pi pi-calendar text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.force.previewRejoin', { at: time.date(preview.rejoinUntil) }) }}</li>
                    </ul>
                </template>
            </section>

            <Message severity="warn" size="small" :closable="false">{{ t('memberWithdrawal.force.pendingNotice') }}</Message>
            <p class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-lock mt-0.5" aria-hidden="true" />
                <span>{{ t('memberWithdrawal.force.choicesNote') }}</span>
            </p>

            <!-- 사유 종류 -->
            <fieldset class="m-0 min-w-0 border-0 p-0">
                <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('memberWithdrawal.force.reasonKindLabel') }}</legend>
                <div class="flex flex-col gap-2">
                    <label
                        v-for="option in UtilMemberWithdrawal.FORCE_REASON_KINDS"
                        :key="option"
                        class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5"
                        :class="[reasonKind === option ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', busy ? 'cursor-not-allowed opacity-60' : '']"
                    >
                        <RadioButton v-model="reasonKind" :value="option" :name="`${formId}-kind`" :disabled="busy" class="mt-0.5" />
                        <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                            <span class="inline-flex items-center gap-1.5 text-sm font-semibold"><i :class="UtilMemberWithdrawal.forceReasonIcon(option)" aria-hidden="true" />{{ t(`memberWithdrawal.force.reasonKind.${option}`) }}</span>
                            <span class="text-xs text-muted-color">{{ t(`memberWithdrawal.force.reasonKindDesc.${option}`) }}</span>
                        </span>
                    </label>
                </div>
                <p v-if="fieldErrors.reasonKind" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.reasonKind }}</p>
            </fieldset>

            <!-- 운영 메모(필수) -->
            <LabeledField :label="t('memberWithdrawal.force.memoLabel')" :hint="t('memberWithdrawal.force.memoHint', { min: FORCE_WITHDRAW_MEMO_MIN, max: FORCE_WITHDRAW_MEMO_MAX })" :help="t('memberWithdrawal.force.memoHelp')" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" name="memo" rows="3" fluid :maxlength="FORCE_WITHDRAW_MEMO_MAX + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>
            <p class="-mt-3 text-right text-[11px] tabular-nums text-muted-color">{{ t('reason.count', { count: memo.trim().length, max: FORCE_WITHDRAW_MEMO_MAX }) }}</p>

            <p class="m-0 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2 text-xs leading-relaxed dark:bg-surface-800">
                <i class="pi pi-shield mt-0.5" aria-hidden="true" />
                <span>{{ t('memberWithdrawal.force.auditNotice') }}</span>
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('memberWithdrawal.force.submit')" icon="pi pi-user-minus" severity="danger" :loading="busy" :disabled="!preview" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { FORCE_WITHDRAW_MEMO_MAX, FORCE_WITHDRAW_MEMO_MIN, type ForceWithdrawPreview, type ForceWithdrawReasonKind, type ForceWithdrawRequest } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';

interface Props {
    visible: boolean;
    memberName: string;
    memberNo: string;
    /** 남은 일·돈 미리보기 — 부모가 받는다. 아직 없으면 뼈대 */
    preview?: ForceWithdrawPreview | null;
    previewLoading?: boolean;
    previewError?: ApiError | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 거절한 칸 문구 — 키 `reasonKind`·`memo` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { preview: null, previewLoading: false, previewError: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: ForceWithdrawRequest]; 'retry-preview': [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();

const reasonKind = ref<ForceWithdrawReasonKind>('UNDER_14');
const memo = ref('');
const submitted = ref(false);
const showServerMemo = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reasonKind.value = 'UNDER_14';
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

const memoError = computed(() => {
    if (showServerMemo.value && props.fieldErrors.memo) return props.fieldErrors.memo;
    if (!submitted.value) return '';
    const length = memo.value.trim().length;
    if (length === 0) return t('memberWithdrawal.force.memoRequired');
    if (length < FORCE_WITHDRAW_MEMO_MIN || length > FORCE_WITHDRAW_MEMO_MAX) return t('memberWithdrawal.force.memoLength', { min: FORCE_WITHDRAW_MEMO_MIN, max: FORCE_WITHDRAW_MEMO_MAX });
    return '';
});

function submit(): void {
    submitted.value = true;
    showServerMemo.value = false;
    if (memoError.value || props.busy || !props.preview) return;
    emit('submit', { reasonKind: reasonKind.value, memo: memo.value.trim() });
}
</script>
