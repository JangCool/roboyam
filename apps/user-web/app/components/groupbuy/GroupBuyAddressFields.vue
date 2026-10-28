<template>
    <!--
        배송지 칸 — 받는 분 · 전화 · 우편번호(찾기 단추) · 주소 · 상세 주소 · 배송 메모. 공구 주문서와 내 주문의 배송지 수정이 같이 쓴다.
        우편번호 찾기: 다음(카카오) 우편번호 서비스(`usePostcodeSearch`)를 시트(`AppSheet` — 모바일은 아래에서 올라오는 시트) 안에 바로 심는다(팝업 차단을 피한다). 고르면 우편번호·주소를 채우고 상세 주소 칸으로 간다.
        서비스를 불러오지 못하면 「열지 못했어요 · 직접 입력」으로 바뀌고 우편번호·주소 칸이 열린다(계약 화면 목록 「실패 시 직접 입력」).
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
                <InputText :id="id" ref="line2" :model-value="modelValue.address2" autocomplete="address-line2" maxlength="100" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('address2', $event)" />
            </template>
        </LabeledField>
        <LabeledField :label="t('groupBuy.addrMemo')" :hint="t('groupBuy.addrMemoHint')" :error="errors.memo">
            <template #default="{ id, describedBy, invalid }">
                <InputText :id="id" :model-value="modelValue.memo" maxlength="50" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="set('memo', $event)" />
            </template>
        </LabeledField>

        <!-- 우편번호 검색 화면을 심는 시트 — 닫기는 Esc·바깥 누르기·아래 단추 -->
        <AppSheet v-model:visible="searchOpen" :title="t('groupBuy.addrSearch')">
            <div class="flex flex-col gap-3 px-5 pt-3">
                <div ref="embedBox" class="h-[440px] w-full overflow-hidden rounded-lg border border-surface" data-testid="postcode-embed" />
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" @click="searchOpen = false" />
            </div>
        </AppSheet>
    </fieldset>
</template>

<script setup lang="ts">
import type { GroupBuyAddressInput } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PostcodeResult } from '~/composables/groupbuy/usePostcodeSearch';

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

const postcode = usePostcodeSearch();

const manual = ref(props.manualFirst);
const searchFailed = ref(false);
const searchOpen = ref(false);
const embedBox = ref<HTMLElement | null>(null);
const line2 = ref<{ $el: HTMLElement } | null>(null);

function set(key: keyof GroupBuyAddressInput, value: string | undefined): void {
    emit('update:modelValue', { ...props.modelValue, [key]: value ?? '' });
}

/** 우편번호 찾기 — 시트를 열고 그 안에 검색 화면을 심는다. 못 불러오면 시트를 닫고 직접 입력으로 연다 */
async function searchPostcode(): Promise<void> {
    if (props.disabled) return;
    searchFailed.value = false;
    searchOpen.value = true;
    await nextTick();
    // 시트(Dialog)가 본문을 붙이는 데 한 화면 그리기가 더 걸릴 수 있다
    if (!embedBox.value) await new Promise((resolve) => requestAnimationFrame(resolve));
    const box = embedBox.value;
    const ok = box ? await postcode.embed(box, onPick) : false;
    if (ok) return;
    searchOpen.value = false;
    searchFailed.value = true;
    manual.value = true;
}

/** 고른 주소 — 도로명 주소를 먼저 쓴다. 상세 주소는 회원이 넣는다 */
function onPick(result: PostcodeResult): void {
    emit('update:modelValue', { ...props.modelValue, postcode: result.zonecode, address1: result.roadAddress || result.address });
    searchOpen.value = false;
    void nextTick(() => line2.value?.$el?.focus());
}
</script>
