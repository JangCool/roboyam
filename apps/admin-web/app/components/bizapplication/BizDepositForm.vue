<template>
    <!--
        입금 확인 폼(수동 결제 근거 — ★-2) — 입금액 · 입금일 · 입금자명. 입금액이 계약 금액과 다르면 경고 + 사유 칸.
        신청 건 입금 확인 패널과 광고 수동 등록이 같이 쓴다. 값은 `v-model`(BizDepositDraft)이고 저장 시점은 부모가 정한다.
        칸 오류는 `showErrors` 가 켜졌을 때(부모가 저장을 눌렀을 때) 보이고, 서버 칸 오류(`fieldErrors`)는 늘 보인다.
    -->
    <div class="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2">
        <LabeledField :label="t('bizApplication.deposit.amount')" :help="t('bizApplication.deposit.contractTotal', { amount: won(contractTotal) })" :error="err('amount')">
            <template #default="{ id, describedBy, invalid }">
                <InputNumber :input-id="id" :model-value="model.amount" :min="0" :max="999_999_999" :use-grouping="true" :suffix="t('bizApplication.wonSuffix')" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: number | null) => set({ amount: v })" />
            </template>
        </LabeledField>
        <LabeledField :label="t('bizApplication.deposit.depositedOn')" :error="err('depositedOn')">
            <template #default="{ id, describedBy, invalid }">
                <AppDatePicker :model-value="model.depositedOn" :input-id="id" :max="UtilBizApplication.kstToday()" :disabled="disabled" :invalid="invalid" :described-by="describedBy" @update:model-value="(v: string) => set({ depositedOn: v })" />
            </template>
        </LabeledField>
        <LabeledField :label="t('bizApplication.deposit.depositorName')" :error="err('depositorName')" class="sm:col-span-2">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="model.depositorName" maxlength="50" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => set({ depositorName: v ?? '' })" />
            </template>
        </LabeledField>

        <div v-if="mismatch" class="flex flex-col gap-2 sm:col-span-2">
            <Message severity="warn" :closable="false">
                <span class="text-[13px]">{{ t('bizApplication.deposit.mismatch', { paid: won(model.amount ?? 0), total: won(contractTotal), diff: won(Math.abs((model.amount ?? 0) - contractTotal)) }) }}</span>
            </Message>
            <LabeledField :label="t('bizApplication.deposit.mismatchReason')" :error="err('mismatchReason')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" :model-value="model.mismatchReason" rows="2" auto-resize maxlength="200" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => set({ mismatchReason: v ?? '' })" />
                </template>
            </LabeledField>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilBizApplication, { type BizDepositDraft } from '~/utils/util-biz-application';

const model = defineModel<BizDepositDraft>({ required: true });

interface Props {
    contractTotal: number;
    disabled?: boolean;
    showErrors?: boolean;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { disabled: false, showErrors: false, fieldErrors: () => ({}) });

const { t, locale } = useI18n();
const won = (n: number) => UtilBizApplication.won(n, locale.value);

const mismatch = computed(() => model.value.amount !== null && model.value.amount > 0 && model.value.amount !== props.contractTotal);
const problems = computed(() => UtilBizApplication.depositProblems(model.value, props.contractTotal));

function err(key: keyof BizDepositDraft): string {
    if (props.fieldErrors[key]) return props.fieldErrors[key]!;
    if (!props.showErrors || !problems.value[key]) return '';
    return t(`bizApplication.deposit.required.${key}`);
}

function set(patch: Partial<BizDepositDraft>): void {
    model.value = { ...model.value, ...patch };
}
</script>
