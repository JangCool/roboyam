<template>
    <!--
        조회 조건의 선택 묶음 — 한 줄에 늘어놓은 라디오(하나 고르기) 또는 체크박스(여럿 고르기). `FilterRow` 의 입력 슬롯에 둔다.
        여럿 고르기에서 `allLabel` 을 주면 맨 앞에 「전체」 체크가 나온다: 개별을 하나도 안 골랐을 때(= 거르지 않음) 켜져 있고, 켜면 개별 선택이 모두 풀리며, 개별을 고르면 꺼진다.
        개별을 전부 고르면 "전체"와 같으므로 선택을 비운다(= 전체). 라디오의 「전체」는 부르는 쪽이 값 ''인 선택지로 넣는다.
        값(`v-model`): 하나 고르기는 글자 하나, 여럿 고르기는 글자 배열(비면 전체).
    -->
    <div class="flex flex-wrap items-center gap-x-4 gap-y-1.5" :role="multiple ? 'group' : 'radiogroup'" :aria-labelledby="labelledby">
        <div v-if="multiple && allLabel" class="flex items-center gap-1.5">
            <Checkbox :key="`all-${nonce}`" binary :model-value="list.length === 0" :input-id="`${uid}-all`" @update:model-value="onAll" />
            <label :for="`${uid}-all`" class="cursor-pointer text-[13px]">{{ allLabel }}</label>
        </div>
        <div v-for="(option, index) in options" :key="option.value" class="flex items-center gap-1.5">
            <Checkbox v-if="multiple" :key="`${option.value}-${nonce}`" binary :model-value="list.includes(option.value)" :input-id="`${uid}-${index}`" @update:model-value="toggle(option.value, index)" />
            <RadioButton v-else :model-value="model" :value="option.value" :name="uid" :input-id="`${uid}-${index}`" @update:model-value="(value: string) => (model = value)" />
            <label :for="`${uid}-${index}`" class="cursor-pointer text-[13px]">{{ option.label }}</label>
        </div>
    </div>
</template>

<script setup lang="ts">
interface FilterOption {
    value: string;
    label: string;
}

/** 고른 값 — 하나 고르기는 글자, 여럿 고르기는 글자 배열 */
const model = defineModel<string | string[]>({ required: true });

interface Props {
    options: FilterOption[];
    /** true 면 체크박스(여럿), 아니면 라디오(하나) */
    multiple?: boolean;
    /** 여럿 고르기의 「전체」 체크 글자. 없으면 「전체」 체크를 두지 않는다 */
    allLabel?: string;
    /** 이 묶음의 이름이 되는 라벨의 id(`FilterRow` 슬롯의 `labelId`) */
    labelledby?: string;
}

const props = withDefaults(defineProps<Props>(), { multiple: false, allLabel: '', labelledby: undefined });

const uid = useId();

/** 여럿 고르기의 지금 선택 */
const list = computed<string[]>(() => (Array.isArray(model.value) ? model.value : []));

/**
 * 체크박스는 눌린 순간 화면 상태를 먼저 바꾼다. 우리가 그 결과를 받아들이지 않으면(이미 켜진 「전체」를 끄려는 경우, 개별을 다 골라 전체로 접히는 경우) 값은 그대로인데 체크 모양만 어긋난다.
 * 그때 해당 체크박스를 다시 그려 값과 맞추고 포커스를 되돌린다(키보드 사용자가 자리를 잃지 않게).
 */
const nonce = ref(0);
function resync(inputId: string): void {
    nonce.value += 1;
    void nextTick(() => document.getElementById(inputId)?.focus());
}

function onAll(on: boolean): void {
    if (on) model.value = [];
    else resync(`${uid}-all`);
}

/** 하나를 켜고 끈다. 선택지 차례를 지키고, 전부 골랐으면 전체(빈 배열)로 접는다 */
function toggle(value: string, index: number): void {
    const next = new Set(list.value);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    const ordered = props.options.map((o) => o.value).filter((v) => next.has(v));
    const collapse = !!props.allLabel && ordered.length === props.options.length;
    model.value = collapse ? [] : ordered;
    if (collapse) resync(`${uid}-${index}`);
}
</script>
