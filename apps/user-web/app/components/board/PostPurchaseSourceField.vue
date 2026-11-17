<template>
    <!--
        글쓰기 「어떻게 구했나요」(trial-group-contract 「구입 경로」·「구현 결정」 5) — 게시판 `purchaseSourceMode` 가 끔이 아닐 때 `/write` 가 본문 아래에 놓는다.
        고르기: 내 돈으로 샀어요(+ 구매 인증 사진 — 글 저장 뒤 올린다) · 업체에서 받았어요(협찬 — 표시 상자 자동) · 빌려서 써 봤어요 · 뽑힌 체험단 사용기로 쓰기(뽑힌 체험단이 있을 때 — 여럿이면 하나 고르기).
        `REQUIRED` 면 「필수」 표시 + 고르지 않으면 [등록] 뒤 오류, `OPTIONAL` 이면 「고르지 않음」도 있다. 체험단 연결로 열렸으면(`locked`) 체험단으로 고정 — 말머리 [체험단]·표시 상자는 서버가 단다.
        「업체에서 받고 내 돈으로 샀어요를 고르면 표시 의무 위반」 안내를 늘 보인다. 값만 주고받는다(서버 호출 없음).
    -->
    <fieldset class="m-0 flex flex-col gap-2 rounded-lg border border-surface p-3" :aria-describedby="noteId" data-testid="write-purchase-source">
        <legend class="flex items-center gap-1.5 px-1 text-sm font-semibold">
            {{ t('trial.sourceLegend') }}
            <span v-if="required" class="text-xs font-normal text-red-600 dark:text-red-400">{{ t('trial.sourceRequiredMark') }}</span>
        </legend>

        <template v-if="locked">
            <p class="m-0 flex items-center gap-1.5 text-sm"><i class="pi pi-lock text-xs text-muted-color" aria-hidden="true" />{{ t('trial.sourceLocked', { name: lockedLabel }) }}</p>
            <p class="m-0 text-xs text-muted-color">{{ t('trial.sourceTrialAuto') }}</p>
        </template>
        <template v-else>
            <div class="flex flex-col gap-1.5" role="radiogroup">
                <label v-for="opt in options" :key="opt.value ?? 'none'" class="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioButton :model-value="modelValue.source" :value="opt.value" :input-id="`${baseId}-${opt.value ?? 'none'}`" :name="`${baseId}-source`" :disabled="disabled" @update:model-value="pick" />
                    <span>{{ opt.label }}</span>
                </label>
            </div>

            <!-- 뽑힌 체험단이 여럿이면 하나 고르기 -->
            <div v-if="modelValue.source === 'TRIAL' && trials.length > 1" class="pl-6">
                <Select
                    :model-value="modelValue.trialParticipantId"
                    :options="trials"
                    option-label="label"
                    option-value="participantId"
                    size="small"
                    :placeholder="t('trial.sourceTrialPick')"
                    :disabled="disabled"
                    class="w-full max-w-sm"
                    :aria-label="t('trial.sourceTrialPick')"
                    @update:model-value="(v: string | null) => emit('update:modelValue', { ...modelValue, trialParticipantId: v })"
                />
            </div>
            <p v-if="modelValue.source === 'TRIAL'" class="m-0 pl-6 text-xs text-muted-color">{{ t('trial.sourceTrialAuto') }}</p>
            <p v-if="modelValue.source === 'SPONSORED'" class="m-0 pl-6 text-xs text-muted-color">{{ t('trial.sourceSponsoredAuto') }}</p>

            <!-- 내 돈으로 샀어요 — 구매 인증 사진(선택) -->
            <PurchaseProofPicker v-if="modelValue.source === 'SELF' && proofAllowed" :model-value="proof" optional :disabled="disabled" class="ml-6" @update:model-value="(f: File | null) => emit('update:proof', f)" />
        </template>

        <p :id="noteId" class="m-0 flex items-start gap-1.5 text-xs text-amber-700 dark:text-amber-400">
            <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />{{ t('trial.sourceHonestNote') }}
        </p>
        <small v-if="shownError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ shownError }}</small>
    </fieldset>
</template>

<script setup lang="ts">
import type { PurchaseSource } from '~/composables/board/api/usePostApi.types';
import type { PurchaseSourceValue, PurchaseTrialOption } from '~/components/board/PostPurchaseSourceField.types';

interface Props {
    modelValue: PurchaseSourceValue;
    /** 꼭 골라야 함 */
    required?: boolean;
    /** 이 게시판에 쓸 수 있는 내 뽑힌 체험단(없으면 체험단 고르기를 감춘다) */
    trials?: PurchaseTrialOption[];
    /** 체험단 연결로 열렸다 — 바꿀 수 없다 */
    locked?: boolean;
    /** 구매 인증 사진(내 돈으로 샀어요) */
    proof?: File | null;
    /** 사진을 올릴 수 있는가(회원만) */
    proofAllowed?: boolean;
    disabled?: boolean;
    /** 서버 `fields.purchaseSource`·`trialParticipantId` */
    error?: string;
    /** [등록]을 한 번 누른 뒤 — 필수 검사 문구를 보인다 */
    showProblems?: boolean;
}

const props = withDefaults(defineProps<Props>(), { required: false, trials: () => [], locked: false, proof: null, proofAllowed: false, disabled: false, error: '', showProblems: false });
const emit = defineEmits<{ 'update:modelValue': [value: PurchaseSourceValue]; 'update:proof': [file: File | null] }>();

const { t } = useI18n();
const baseId = useId();
const noteId = useId();

const options = computed(() => {
    const out: { value: PurchaseSource | null; label: string }[] = [
        { value: 'SELF', label: t('trial.source.SELF') },
        { value: 'SPONSORED', label: t('trial.source.SPONSORED') },
        { value: 'BORROWED', label: t('trial.source.BORROWED') },
    ];
    if (props.trials.length) out.push({ value: 'TRIAL', label: t('trial.source.TRIAL') });
    if (!props.required) out.push({ value: null, label: t('trial.sourceNone') });
    return out;
});

const lockedLabel = computed(() => props.trials.find((x) => x.participantId === props.modelValue.trialParticipantId)?.label ?? t('trial.sourceLockedFallback'));

function pick(value: PurchaseSource | null): void {
    // 체험단을 고르면 하나뿐일 때 바로 그 줄, 다른 것을 고르면 연결을 푼다
    const trialParticipantId = value === 'TRIAL' ? (props.modelValue.trialParticipantId ?? (props.trials.length === 1 ? props.trials[0]!.participantId : null)) : null;
    emit('update:modelValue', { source: value, trialParticipantId });
    if (value !== 'SELF' && props.proof) emit('update:proof', null);
}

const shownError = computed(() => {
    if (props.error) return props.error;
    if (!props.showProblems || props.locked) return '';
    if (props.required && !props.modelValue.source) return t('trial.sourceRequired');
    if (props.modelValue.source === 'TRIAL' && !props.modelValue.trialParticipantId) return t('trial.sourceTrialRequired');
    return '';
});
</script>
