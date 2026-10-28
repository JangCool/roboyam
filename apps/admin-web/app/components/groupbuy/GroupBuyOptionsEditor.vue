<template>
    <!--
        공구 옵션 편집(이름·추가금·최대 수량) — 옵션이 없으면 주문 줄은 하나다. 같은 이름은 서버가 거절한다(`fields.options[i].name`).
        모집 뒤(`locked`)에는 옵션 구성이 잠긴다 — 칸은 읽기 전용, 줄마다 확정·선점만 더 보인다. 수량 조정은 수량 화면(추가 입고·줄이기).
        최대 수량을 비우면 그 옵션은 한도가 없다(공구 최대만 본다 — 결정 77). 서버 오류는 `errors` 의 `options[i].<칸>` 키로 받는다.
    -->
    <div class="flex flex-col gap-2">
        <p v-if="!model.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.optionsNone') }}</p>
        <div v-else class="overflow-x-auto">
            <table class="w-full min-w-[34rem] border-collapse text-[13px]">
                <thead>
                    <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                        <th class="py-1.5 pr-2 font-normal">{{ t('groupBuy.optionName') }}</th>
                        <th class="w-32 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionExtra') }}</th>
                        <th class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionMax') }}</th>
                        <th v-if="locked" class="w-28 py-1.5 pr-2 font-normal">{{ t('groupBuy.optionSold') }}</th>
                        <th v-if="!locked" class="w-10 py-1.5"><span class="sr-only">{{ t('groupBuy.optionRemove') }}</span></th>
                    </tr>
                </thead>
                <tbody>
                    <tr v-for="(row, index) in model" :key="row.key" class="border-b border-(--line) align-top last:border-b-0">
                        <td class="py-1.5 pr-2">
                            <InputText :model-value="row.name" :aria-label="t('groupBuy.optionNameN', { n: index + 1 })" maxlength="50" :readonly="locked || disabled" :invalid="!!errors[`options[${index}].name`]" class="w-full" @update:model-value="(v: string | undefined) => patch(index, { name: v ?? '' })" />
                            <p v-if="errors[`options[${index}].name`]" role="alert" class="m-0 mt-0.5 text-xs text-red-600 dark:text-red-400">{{ errors[`options[${index}].name`] }}</p>
                        </td>
                        <td class="py-1.5 pr-2">
                            <InputText :model-value="row.extraPrice" :aria-label="t('groupBuy.optionExtraN', { n: index + 1 })" inputmode="numeric" :readonly="locked || disabled" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { extraPrice: v ?? '' })" />
                        </td>
                        <td class="py-1.5 pr-2">
                            <InputText :model-value="row.maxQty" :aria-label="t('groupBuy.optionMaxN', { n: index + 1 })" inputmode="numeric" :placeholder="t('groupBuy.optionUnlimited')" :readonly="locked || disabled" class="w-full text-right tabular-nums" @update:model-value="(v: string | undefined) => patch(index, { maxQty: v ?? '' })" />
                        </td>
                        <td v-if="locked" class="py-1.5 pr-2 text-xs tabular-nums text-muted-color">{{ t('groupBuy.optionSoldLine', { current: row.currentQty, reserved: row.reservedQty }) }}</td>
                        <td v-if="!locked" class="py-1.5 text-right">
                            <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :aria-label="t('groupBuy.optionRemoveN', { n: index + 1 })" :disabled="disabled" @click="remove(index)" />
                        </td>
                    </tr>
                </tbody>
            </table>
        </div>
        <div v-if="!locked && !disabled">
            <Button type="button" :label="t('groupBuy.optionAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="add" />
        </div>
        <p class="m-0 text-xs text-muted-color">{{ locked ? t('groupBuy.optionsLockedHelp') : t('groupBuy.optionsHelp') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyOptionRow } from '~/components/groupbuy/GroupBuyOptionsEditor.types';

const model = defineModel<GroupBuyOptionRow[]>({ required: true });

interface Props {
    /** 모집 뒤 — 구성 잠김 */
    locked?: boolean;
    disabled?: boolean;
    /** 서버 칸 오류(`options[i].name` …) */
    errors?: Record<string, string>;
}

withDefaults(defineProps<Props>(), { locked: false, disabled: false, errors: () => ({}) });

const { t } = useI18n();
let seq = 0;

function patch(index: number, change: Partial<GroupBuyOptionRow>): void {
    model.value = model.value.map((row, i) => (i === index ? { ...row, ...change } : row));
}

function remove(index: number): void {
    model.value = model.value.filter((_, i) => i !== index);
}

function add(): void {
    seq += 1;
    model.value = [...model.value, { key: `new-${Date.now()}-${seq}`, id: null, name: '', extraPrice: '0', maxQty: '', currentQty: 0, reservedQty: 0 }];
}
</script>
