<template>
    <!--
        배송지 칸 — 받는 분 · 전화 · 우편번호(찾기 단추) · 주소 · 상세 주소 · 배송 메모. 공구 주문서와 내 주문의 배송지 수정이 같이 쓴다.
        우편번호 찾기: ⚠ MOCK — 우편번호 서비스(외부 스크립트)를 아직 정하지 않아 단추를 누르면 「열지 못했어요 · 직접 입력」으로 바뀌고 우편번호·주소 칸이 열린다(계약 화면 목록 「실패 시 직접 입력」).
        값은 화면 메모리에만 둔다(평문 배송지 — 저장소·주소줄에 남기지 않는다, forms.md 2-1). 칸 오류는 서버 `fields` 의 칸 이름(`recipient` …)으로 받는다.
    -->
    <fieldset class="m-0 flex flex-col gap-3 border-0 p-0" :disabled="disabled">
        <legend v-if="legend" class="mb-1 text-sm font-semibold">{{ legend }}</legend>
        <div class="grid gap-3 sm:grid-cols-2">
            <LabeledField :label="t('groupBuy.addrRecipient')" :error="errors.recipient">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="modelValue.recipient" autocomplete="name" maxlength="30" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('recipient', $event)" />
                </template>
            </LabeledField>
            <LabeledField :label="t('groupBuy.addrPhone')" :hint="t('groupBuy.addrPhoneHint')" :error="errors.phone">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="modelValue.phone" type="tel" inputmode="tel" autocomplete="tel" maxlength="13" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('phone', $event)" />
                </template>
            </LabeledField>
        </div>
        <LabeledField :label="t('groupBuy.addrPostcode')" :error="errors.postcode" :help="manual ? t('groupBuy.addrManualHelp') : ''">
            <template #default="{ id, describedBy, invalid }">
                <div class="flex gap-2">
                    <InputText
                        :id="id"
                        :model-value="modelValue.postcode"
                        inputmode="numeric"
                        autocomplete="postal-code"
                        maxlength="5"
                        :readonly="!manual"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        class="w-28"
                        @update:model-value="set('postcode', $event)"
                    />
                    <Button type="button" :label="t('groupBuy.addrSearch')" icon="pi pi-search" severity="secondary" variant="outlined" @click="searchPostcode" />
                </div>
            </template>
        </LabeledField>
        <Message v-if="searchFailed" severity="warn" size="small" :closable="false" role="status">{{ t('groupBuy.addrSearchFailed') }}</Message>
        <LabeledField :label="t('groupBuy.addrLine1')" :error="errors.address1">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="modelValue.address1" autocomplete="address-line1" maxlength="100" :readonly="!manual" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('address1', $event)" />
            </template>
        </LabeledField>
        <LabeledField :label="t('groupBuy.addrLine2')" :error="errors.address2">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="modelValue.address2" autocomplete="address-line2" maxlength="100" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('address2', $event)" />
            </template>
        </LabeledField>
        <LabeledField :label="t('groupBuy.addrMemo')" :hint="t('groupBuy.addrMemoHint')" :error="errors.memo">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="modelValue.memo" maxlength="50" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('memo', $event)" />
            </template>
        </LabeledField>
    </fieldset>
</template>

<script setup lang="ts">
import type { GroupBuyAddressInput } from '~/composables/groupbuy/api/useGroupBuyApi.types';

interface Props {
    modelValue: GroupBuyAddressInput;
    /** 칸 이름 → 서버 오류 문구 */
    errors?: Record<string, string>;
    disabled?: boolean;
    legend?: string;
    /** 처음부터 우편번호·주소를 직접 입력하게(이미 값이 있는 배송지 수정) */
    manualFirst?: boolean;
}

const props = withDefaults(defineProps<Props>(), { errors: () => ({}), disabled: false, legend: '', manualFirst: false });
const emit = defineEmits<{ 'update:modelValue': [value: GroupBuyAddressInput] }>();
const { t } = useI18n();

const manual = ref(props.manualFirst);
const searchFailed = ref(false);

function set(key: keyof GroupBuyAddressInput, value: string | undefined): void {
    emit('update:modelValue', { ...props.modelValue, [key]: value ?? '' });
}

/** MOCK: 우편번호 찾기 창을 열지 못한 것으로 — 직접 입력으로 연다 */
function searchPostcode(): void {
    searchFailed.value = true;
    manual.value = true;
}
</script>
