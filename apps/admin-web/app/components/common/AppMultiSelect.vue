<template>
    <!--
        다중 선택 — 화면은 PrimeVue `MultiSelect` 를 직접 쓰지 않고 이것을 쓴다(`components.md`). "전체 선택"(머리의 체크)을 늘 보인다. 선택한 것은 칩으로 보이고 많으면 "N개 선택"으로 줄인다.
        `option` 슬롯으로 항목 모양(비활성 사유 툴팁 등)을 바꿀 수 있다. 비활성 항목(`optionDisabled`)은 "전체 선택"에 들어가지 않는다.
    -->
    <MultiSelect
        v-model="model"
        :options="options"
        :option-label="optionLabel"
        :option-value="optionValue"
        :option-disabled="optionDisabled"
        :option-group-label="optionGroupLabel"
        :option-group-children="optionGroupChildren"
        :input-id="inputId"
        :placeholder="placeholder"
        :disabled="disabled"
        :invalid="invalid"
        :filter="filter"
        :filter-placeholder="filterPlaceholder"
        :aria-label="ariaLabel"
        :empty-message="emptyMessage"
        :empty-filter-message="emptyFilterMessage"
        :selected-items-label="selectedItemsLabel"
        :max-selected-labels="maxSelectedLabels"
        :show-toggle-all="true"
        display="chip"
        fluid
    >
        <template v-if="$slots.option" #option="slotProps">
            <slot name="option" v-bind="slotProps" />
        </template>
    </MultiSelect>
</template>

<script setup lang="ts">
/** 고른 값들(`optionValue` 의 배열) */
const model = defineModel<unknown[]>({ required: true });

interface Props {
    options: unknown[];
    optionLabel?: string;
    optionValue?: string;
    optionDisabled?: string;
    /** 항목을 묶음으로 보일 때 — 묶음 이름 칸과 묶음 안 항목 칸(PrimeVue `optionGroupLabel`·`optionGroupChildren`). `options` 는 묶음의 배열이 된다 */
    optionGroupLabel?: string;
    optionGroupChildren?: string;
    inputId?: string;
    placeholder?: string;
    disabled?: boolean;
    invalid?: boolean;
    /** 항목이 많을 때 찾기 칸을 둔다 */
    filter?: boolean;
    filterPlaceholder?: string;
    ariaLabel?: string;
    emptyMessage?: string;
    emptyFilterMessage?: string;
    /** 이 개수를 넘으면 칩 대신 `selectedItemsLabel` 로 줄인다 */
    maxSelectedLabels?: number;
    /** 줄였을 때의 글자 — `{0}` 자리에 고른 개수가 들어간다(예: `3개 선택`). 번역은 부르는 쪽이 준다 */
    selectedItemsLabel?: string;
}

withDefaults(defineProps<Props>(), {
    optionLabel: 'label',
    optionValue: 'value',
    optionDisabled: undefined,
    optionGroupLabel: undefined,
    optionGroupChildren: undefined,
    inputId: undefined,
    placeholder: undefined,
    disabled: false,
    invalid: false,
    filter: false,
    filterPlaceholder: undefined,
    ariaLabel: undefined,
    emptyMessage: undefined,
    emptyFilterMessage: undefined,
    maxSelectedLabels: 3,
    selectedItemsLabel: undefined,
});
</script>
