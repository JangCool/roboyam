<template>
    <!--
        공용 주소 입력 — 우편번호(찾기 단추) · 기본 주소 · 상세 주소. 배송지처럼 주소를 받는 화면이 같이 쓴다(받는 분·전화 같은 칸은 부르는 쪽 몫).
        「우편번호 찾기」는 시트(`AppSheet` — 모바일은 아래에서 올라오는 시트)를 열고, 관리자 설정(주소 검색 › 제공처, `useAddressSearch`)에 따라 그 안에 그린다:
          · `DAUM` 다음(카카오) 우편번호 화면을 시트 안 상자에 바로 심는다(`useDaumPostcode` — 팝업 차단을 피한다)
          · `INTERNAL` 자체 주소 DB 검색(`AddressSearchPanel`)
          · `DAUM_FALLBACK_INTERNAL` 다음 먼저, 스크립트를 못 불러오면 같은 시트에서 자체 주소 DB 검색으로 바꾼다(한 줄 안내)
        다음만 쓰는데 못 불러오면 시트를 닫고 「열지 못했어요 · 직접 입력」 안내와 함께 우편번호·기본 주소 칸을 연다. 설정을 못 받으면 다음 우편번호로 계속한다.
        시트 아래 「직접 입력」은 늘 있다(찾는 주소가 없을 때). 고르면 우편번호·기본 주소(도로명 먼저)를 채우고 상세 주소 칸으로 간다.
        값은 화면 메모리에만 둔다(주소 — 저장소·주소줄에 남기지 않는다, forms.md 2-1). 칸 오류는 `errors`(`postcode`·`address1`·`address2`).
    -->
    <fieldset class="m-0 flex min-w-0 flex-col gap-3 border-0 p-0" :disabled="disabled">
        <legend v-if="legend" class="mb-1 text-sm font-semibold">{{ legend }}</legend>
        <LabeledField :label="t('address.postcode')" :error="errors.postcode" :help="manual ? t('address.manualHelp') : ''">
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
                        class="w-28 tabular-nums"
                        data-testid="address-postcode"
                        @update:model-value="set('postcode', $event)"
                    />
                    <Button type="button" :label="t('address.search')" icon="pi pi-search" severity="secondary" variant="outlined" :loading="opening" data-testid="address-search" @click="openSearch" />
                </div>
            </template>
        </LabeledField>
        <Message v-if="searchFailed" severity="warn" size="small" :closable="false" role="status">{{ t('address.searchUnavailable') }}</Message>
        <LabeledField :label="t('address.line1')" :error="errors.address1">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    :model-value="modelValue.address1"
                    autocomplete="address-line1"
                    maxlength="100"
                    :readonly="!manual"
                    :placeholder="manual ? '' : t('address.line1Placeholder')"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="address-line1"
                    @update:model-value="set('address1', $event)"
                    @click="manual ? undefined : openSearch()"
                />
            </template>
        </LabeledField>
        <LabeledField :label="t('address.line2')" :error="errors.address2">
            <template #default="{ id, describedBy, invalid }">
                <InputText
                    :id="id"
                    ref="line2"
                    :model-value="modelValue.address2"
                    autocomplete="address-line2"
                    maxlength="100"
                    :placeholder="t('address.line2Placeholder')"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    fluid
                    data-testid="address-line2"
                    @update:model-value="set('address2', $event)"
                />
            </template>
        </LabeledField>

        <!-- 우편번호 찾기 시트 — 닫기는 Esc·바깥 누르기·아래 단추 -->
        <AppSheet v-model:visible="sheetOpen" :title="t('address.search')">
            <div class="flex flex-col gap-3 px-5 pt-3">
                <Message v-if="fellBack" severity="info" size="small" :closable="false" role="status">{{ t('address.fallbackNotice') }}</Message>
                <div v-if="mode === 'loading'" class="flex h-[440px] items-center justify-center" role="status" :aria-label="t('common.loading')">
                    <ProgressSpinner style="width: 2rem; height: 2rem" />
                </div>
                <AddressSearchPanel v-else-if="mode === 'internal'" :key="panelKey" class="h-[440px]" @select="onInternalPick" @manual="toManual" />
                <div v-show="mode === 'daum'" ref="embedBox" class="h-[440px] w-full overflow-hidden rounded-lg border border-surface" data-testid="postcode-embed" />
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <Button type="button" :label="t('address.manualEntry')" icon="pi pi-pencil" severity="secondary" variant="text" size="small" data-testid="address-manual" @click="toManual" />
                    <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" @click="sheetOpen = false" />
                </div>
            </div>
        </AppSheet>
    </fieldset>
</template>

<script setup lang="ts">
import type { AddressFieldErrors, AddressValue } from '~/components/address/AddressInput.types';
import type { AddressSearchResult } from '~/composables/address/api/useAddressApi.types';
import type { DaumPostcodeResult } from '~/composables/address/useDaumPostcode';

interface Props {
    modelValue: AddressValue;
    /** 칸 이름 → 서버 오류 문구 */
    errors?: AddressFieldErrors;
    disabled?: boolean;
    /** 묶음 제목 — 비우면 그리지 않는다(부르는 쪽이 제목을 따로 둘 때) */
    legend?: string;
    /** 처음부터 우편번호·기본 주소를 직접 입력하게(이미 값이 있는 주소 고치기) */
    manualFirst?: boolean;
}

const props = withDefaults(defineProps<Props>(), { errors: () => ({}), disabled: false, legend: '', manualFirst: false });
const emit = defineEmits<{ 'update:modelValue': [value: AddressValue] }>();
const { t } = useI18n();

const addressSearch = useAddressSearch();
const postcode = useDaumPostcode();

/** 시트 안에 무엇을 그리나 — 설정을 받는 중 / 다음 우편번호 / 자체 주소 DB */
type SheetMode = 'loading' | 'daum' | 'internal';

const manual = ref(props.manualFirst);
const searchFailed = ref(false);
const sheetOpen = ref(false);
const opening = ref(false);
const mode = ref<SheetMode>('loading');
/** 다음을 못 불러와 자체 주소 DB 로 바꿨다 */
const fellBack = ref(false);
/** 열 때마다 검색 패널을 처음 상태로 */
const panelKey = ref(0);
const embedBox = ref<HTMLElement | null>(null);
const line2 = ref<{ $el: HTMLElement } | null>(null);

function set(key: keyof AddressValue, value: string | undefined): void {
    emit('update:modelValue', { ...props.modelValue, [key]: value ?? '' });
}

/** 우편번호 찾기 — 시트를 열고 설정에 따라 다음 우편번호를 심거나 자체 주소 DB 검색을 그린다 */
async function openSearch(): Promise<void> {
    if (props.disabled || opening.value) return;
    opening.value = true;
    searchFailed.value = false;
    fellBack.value = false;
    mode.value = 'loading';
    sheetOpen.value = true;
    try {
        const config = await addressSearch.ensureConfig();
        if (config.provider === 'INTERNAL') {
            showInternal();
            return;
        }
        mode.value = 'daum';
        await nextTick();
        // 시트(Dialog)가 본문을 붙이는 데 한 화면 그리기가 더 걸릴 수 있다
        if (!embedBox.value) await new Promise((resolve) => requestAnimationFrame(resolve));
        const box = embedBox.value;
        const ok = box ? await postcode.embed(box, config.scriptUrl, onDaumPick) : false;
        if (ok || !sheetOpen.value) return;
        if (config.provider === 'DAUM_FALLBACK_INTERNAL') {
            fellBack.value = true;
            showInternal();
            return;
        }
        sheetOpen.value = false;
        searchFailed.value = true;
        manual.value = true;
    } finally {
        opening.value = false;
    }
}

function showInternal(): void {
    panelKey.value += 1;
    mode.value = 'internal';
}

/** 직접 입력 — 시트를 닫고 우편번호·기본 주소 칸을 연다 */
function toManual(): void {
    sheetOpen.value = false;
    manual.value = true;
}

function onDaumPick(result: DaumPostcodeResult): void {
    pick(result.zonecode, result.roadAddress || result.address);
}

function onInternalPick(result: AddressSearchResult): void {
    pick(result.postcode, result.roadAddress || result.jibunAddress);
}

/** 고른 주소 — 도로명 주소를 먼저 쓴다. 상세 주소는 회원이 넣는다 */
function pick(code: string, address1: string): void {
    emit('update:modelValue', { ...props.modelValue, postcode: code, address1 });
    sheetOpen.value = false;
    searchFailed.value = false;
    void nextTick(() => line2.value?.$el?.focus());
}
</script>
