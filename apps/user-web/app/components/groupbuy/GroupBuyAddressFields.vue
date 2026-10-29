<template>
    <!--
        배송지 칸 — 받는 분 · 전화 · 우편번호(찾기 단추) · 주소 · 상세 주소 · 배송 메모. 공구 주문서와 내 주문의 배송지 수정이 같이 쓴다.
        우편번호·주소·상세 주소는 공용 주소 입력(`AddressInput` — docs/features/address-search-contract.md)이다: 관리자 설정 › 주소 검색의 제공처에 따라
        다음 우편번호 또는 자체 주소 DB 검색을 시트 안에 띄우고, 못 쓰면 직접 입력으로 연다. 받는 분·전화·메모는 이 부품 몫.
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
        <AddressInput :model-value="address" :errors="addressErrors" :disabled="disabled" :manual-first="manualFirst" @update:model-value="setAddress" />
        <LabeledField :label="t('groupBuy.addrMemo')" :hint="t('groupBuy.addrMemoHint')" :error="errors.memo">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="modelValue.memo" maxlength="50" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('memo', $event)" />
            </template>
        </LabeledField>
    </fieldset>
</template>

<script setup lang="ts">
import type { AddressFieldErrors, AddressValue } from '~/components/address/AddressInput.types';
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

const address = computed<AddressValue>(() => ({ postcode: props.modelValue.postcode, address1: props.modelValue.address1, address2: props.modelValue.address2 }));
const addressErrors = computed<AddressFieldErrors>(() => ({ postcode: props.errors.postcode, address1: props.errors.address1, address2: props.errors.address2 }));

function set(key: keyof GroupBuyAddressInput, value: string | undefined): void {
    emit('update:modelValue', { ...props.modelValue, [key]: value ?? '' });
}

function setAddress(value: AddressValue): void {
    emit('update:modelValue', { ...props.modelValue, postcode: value.postcode, address1: value.address1, address2: value.address2 });
}
</script>
