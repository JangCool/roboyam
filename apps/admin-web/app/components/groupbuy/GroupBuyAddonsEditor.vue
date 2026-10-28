<template>
    <!--
        공구 「추가 구성상품」 구획 본문(결정 88 — 고시 원칙 ⑦ 의 별개 상품, 단품 종류 ADDON). 줄마다 이미지 한 장(선택) · 이름 · 가격 · 한도(필수) · 차례 · 빼기. 20개까지.
        회원은 본 상품과 함께만 담는다(단독 구매 불가). 공구 최대·성사·1인 한도는 본 상품(MAIN)만 세고 추가 구성상품은 자기 한도만 센다. 고시 양식은 상품마다 따로 필수.
        서버 저장 모양(`addons[{name, price, maxQty, imageKey}]`)에 판매 여부 칸이 없어 끄기 대신 줄을 뺀다. 이름은 다른 추가 구성상품·조합 이름(`mainNames`)과 겹치면 안 된다.
        모집 뒤(`locked`)에는 구성·가격이 잠긴다(읽기 전용 + 확정·결제 중). 한도는 수량 화면에서 추가 입고·줄이기.
        이미지 올리기는 부르는 쪽이 준 `upload` 로 한다(서버를 모른다). 서버 오류는 `errors.addons`(구획 단위).
    -->
    <div class="flex flex-col gap-2">
        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ locked ? t('groupBuy.addonLockedHelp') : t('groupBuy.addonHelp') }}</p>
        <p v-if="errors.addons" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.addons }}</p>
        <p v-if="!model.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.addonNone') }}</p>

        <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
            <li v-for="(row, index) in model" :key="row.key" class="flex flex-wrap items-start gap-3 rounded-lg border border-(--line) px-3 py-2">
                <!-- 이미지 한 장 -->
                <div class="flex w-20 shrink-0 flex-col items-center gap-1">
                    <img v-if="row.image" :src="row.image.thumbUrl" :alt="t('groupBuy.addonImageAlt', { name: row.name || index + 1 })" class="aspect-square w-20 rounded-md border border-(--line) object-cover" />
                    <label v-else-if="!ro" class="flex aspect-square w-20 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-md border border-dashed border-(--line) text-muted-color hover:border-primary hover:text-primary">
                        <i :class="uploading === row.key ? 'pi pi-spin pi-spinner' : 'pi pi-image'" aria-hidden="true" />
                        <span class="text-[11px]">{{ t('groupBuy.addonImageAdd') }}</span>
                        <input type="file" class="sr-only" accept="image/jpeg,image/png,image/webp" :disabled="uploading !== null" @change="(e: Event) => onPick(index, e)" />
                    </label>
                    <span v-else class="flex aspect-square w-20 items-center justify-center rounded-md border border-(--line) text-muted-color"><i class="pi pi-image" aria-hidden="true" /></span>
                    <Button v-if="row.image && !ro" type="button" :label="t('groupBuy.addonImageRemove')" size="small" severity="secondary" variant="text" class="px-1! py-0! text-[11px]!" @click="patch(index, { image: null })" />
                </div>

                <div class="grid min-w-0 flex-1 gap-2 sm:grid-cols-[minmax(10rem,1fr)_8rem_7rem]">
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-name-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonName') }}</label>
                        <InputText :id="`${baseId}-name-${row.key}`" :model-value="row.name" :maxlength="UtilGroupBuy.SKU.addonNameMax" :placeholder="t('groupBuy.addonNamePlaceholder')" :readonly="ro" :invalid="!!nameError(index)" fluid @update:model-value="(v: string | undefined) => patch(index, { name: v ?? '' })" />
                        <p v-if="nameError(index)" class="m-0 text-xs text-red-600 dark:text-red-400">{{ nameError(index) }}</p>
                    </div>
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-price-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonPrice') }}</label>
                        <InputText :id="`${baseId}-price-${row.key}`" :model-value="row.price" inputmode="numeric" :readonly="ro" :invalid="!!row.price.trim() && UtilGroupBuy.int(row.price) === null" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { price: v ?? '' })" />
                    </div>
                    <div class="flex flex-col gap-1">
                        <label :for="`${baseId}-max-${row.key}`" class="text-xs font-medium">{{ t('groupBuy.addonMax') }}</label>
                        <InputText :id="`${baseId}-max-${row.key}`" :model-value="row.maxQty" inputmode="numeric" :readonly="ro" :invalid="!!row.maxQty.trim() && UtilGroupBuy.int(row.maxQty) === null" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { maxQty: v ?? '' })" />
                    </div>
                    <p v-if="locked" class="m-0 text-xs tabular-nums text-muted-color sm:col-span-3">{{ t('groupBuy.addonSoldLine', { current: row.currentQty, reserved: row.reservedQty }) }}</p>
                    <p v-for="(message, i) in imageErrors[row.key] ?? []" :key="i" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400 sm:col-span-3">{{ message }}</p>
                </div>

                <div v-if="!ro" class="flex items-center gap-1 self-center">
                    <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.addonMoveUp', { n: index + 1 })" :disabled="index === 0" @click="move(index, -1)" />
                    <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.addonMoveDown', { n: index + 1 })" :disabled="index >= model.length - 1" @click="move(index, 1)" />
                    <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.addonRemove', { n: index + 1 })" @click="remove(index)" />
                </div>
            </li>
        </ul>

        <div v-if="!ro">
            <Button type="button" :label="t('groupBuy.addonAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="model.length >= UtilGroupBuy.SKU.addonMax" @click="add" />
            <span v-if="model.length >= UtilGroupBuy.SKU.addonMax" class="ml-2 text-xs text-muted-color">{{ t('groupBuy.addonMaxReached', { max: UtilGroupBuy.SKU.addonMax }) }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyAddonRow } from '~/components/groupbuy/GroupBuySkuEditor.types';
import type { GroupBuyImage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyAddonRow[]>({ required: true });

interface Props {
    /** 이미지 한 장을 올리는 함수 — 실패는 `ApiError` 를 던진다 */
    upload: (file: File) => Promise<GroupBuyImage>;
    /** 조합(MAIN 단품) 이름 — 추가 구성상품 이름과 겹치면 안 된다 */
    mainNames?: string[];
    locked?: boolean;
    disabled?: boolean;
    /** 서버 칸 오류(`addons`) */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { mainNames: () => [], locked: false, disabled: false, errors: () => ({}) });

const { t } = useI18n();
const toApiError = useApiErrorOf();
const baseId = useId();
const uploading = ref<string | null>(null);
const imageErrors = ref<Record<string, string[]>>({});
let seq = 0;

const ro = computed(() => props.locked || props.disabled);

const mainNameKeys = computed(() => new Set(props.mainNames.map((n) => UtilGroupBuy.noticeNameKey(n)).filter(Boolean)));

/** 이름 — 앞 줄과 겹침 · 조합 이름과 겹침(공백 무시). 판정은 서버(`addons`) */
function nameError(index: number): string {
    const name = model.value[index]?.name ?? '';
    if (mainNameKeys.value.has(UtilGroupBuy.noticeNameKey(name))) return t('groupBuy.addonNameIsCombo');
    return UtilGroupBuy.repeatedNames(model.value.map((r) => r.name)).includes(index) ? t('groupBuy.addonNameRepeated') : '';
}

function patch(index: number, change: Partial<GroupBuyAddonRow>): void {
    model.value = model.value.map((r, i) => (i === index ? { ...r, ...change } : r));
}

function add(): void {
    if (model.value.length >= UtilGroupBuy.SKU.addonMax) return;
    seq += 1;
    model.value = [...model.value, { key: `addon-${Date.now()}-${seq}`, id: null, name: '', price: '', maxQty: '', image: null, currentQty: 0, reservedQty: 0 }];
}

function remove(index: number): void {
    model.value = model.value.filter((_, i) => i !== index);
}

function move(index: number, delta: number): void {
    const to = index + delta;
    if (to < 0 || to >= model.value.length) return;
    const next = [...model.value];
    [next[index], next[to]] = [next[to]!, next[index]!];
    model.value = next;
}

async function onPick(index: number, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const row = model.value[index];
    if (!file || !row) return;
    uploading.value = row.key;
    imageErrors.value = { ...imageErrors.value, [row.key]: [] };
    try {
        const image = await props.upload(file);
        const at = model.value.findIndex((r) => r.key === row.key);
        if (at >= 0) patch(at, { image });
    } catch (e) {
        imageErrors.value = { ...imageErrors.value, [row.key]: [toApiError(e).message] };
    } finally {
        uploading.value = null;
    }
}
</script>
