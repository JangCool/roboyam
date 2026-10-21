<template>
    <!--
        날짜 입력칸 — PrimeVue `DatePicker` 위에 "값은 달력 날짜 글자" 약속을 얹었다(community admin `DateField`, hamonica3 frontend-guide 10-2).
        값(`v-model`)은 `YYYY-MM-DD` 글자이고 비면 ''. 달력에서 고른 날 그대로이며 **시간대로 환산하지 않는다**(어느 PC 에서도 같은 글자) — 서비스 날짜(통계 기간 등)처럼 시간대와 무관한 날짜용이다.
        **보이는 모양**은 표시 값의 날짜 형식(`2026.10.02` · `2026-10-02` · `02/10/2026` · `10/02/2026`)을 따르고, 표시 값이 바뀌면 즉시 바뀐다(`useDisplay().setDisplay`).
        값(글자)은 형식과 무관하게 늘 `YYYY-MM-DD` 다. 순간(시각)을 고르는 쓰임은 `AppDateTimePicker` 를 쓴다.
    -->
    <DatePicker
        :key="formatKey"
        :model-value="asDate"
        :input-id="inputId"
        :date-format="dateFormat"
        :show-button-bar="showButtonBar"
        :min-date="minDate"
        :max-date="maxDate"
        :size="size"
        :disabled="disabled"
        :readonly="readonly"
        :invalid="invalid"
        :placeholder="placeholder ?? defaultPlaceholder"
        :input-class="inputClass"
        :pt="inputPt"
        fluid
        @update:model-value="onUpdate"
    />
</template>

<script setup lang="ts">
import UtilDatePicker from '#layers/display-time/utils/util-date-picker';

/** 값(달력 날짜 글자 `YYYY-MM-DD`) — `v-model`. 비어 있으면 '' */
const model = defineModel<string>({ required: true });

interface Props {
    /** 고를 수 있는 가장 이른 날(`YYYY-MM-DD`) */
    min?: string;
    /** 고를 수 있는 가장 늦은 날(`YYYY-MM-DD`) */
    max?: string;
    inputId?: string;
    /** 입력칸 크기(PrimeVue DatePicker 의 size) */
    size?: 'small' | 'large';
    disabled?: boolean;
    /** 읽기 전용 — 값은 보이고 고르지 못한다(폼 제출에는 포함된다 — 비활성과 다르다) */
    readonly?: boolean;
    invalid?: boolean;
    placeholder?: string;
    inputClass?: string;
    describedBy?: string;
    /** 하단 버튼 줄(오늘·지우기) */
    showButtonBar?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    min: undefined,
    max: undefined,
    inputId: undefined,
    size: undefined,
    disabled: false,
    readonly: false,
    invalid: false,
    placeholder: undefined,
    inputClass: undefined,
    describedBy: undefined,
    showButtonBar: true,
});

/** 표시 값 — 날짜 형식을 따른다(시간대는 쓰지 않는다 — 값이 순간이 아니라 달력 날짜 글자다) */
const values = useDisplayValues();

const dateFormat = computed(() => UtilDatePicker.dateFormat(values.value));
const defaultPlaceholder = computed(() => UtilDatePicker.placeholder(values.value, false));
/** 형식이 바뀌면 고르개를 새로 그린다(입력 중이던 글자가 낡은 형식으로 남지 않게) */
const formatKey = computed(() => dateFormat.value);

/** 안내 문구 연결 — DatePicker 도 aria-describedby 를 prop 으로 받지 않아 입력 요소(pcInputText)로 직접 건다 */
const inputPt = computed(() => (props.describedBy ? { pcInputText: { root: { 'aria-describedby': props.describedBy } } } : undefined));

/** 글자 → 고르개가 읽는 Date. 읽을 수 없으면 null */
const asDate = computed<Date | null>(() => UtilDatePicker.toCalendarDate(model.value));
const minDate = computed<Date | undefined>(() => UtilDatePicker.toCalendarDate(props.min) ?? undefined);
const maxDate = computed<Date | undefined>(() => UtilDatePicker.toCalendarDate(props.max) ?? undefined);

/** 고르개가 준 값 → 글자. Date 면 달력에서 고른 날 그대로, 글자(시험 대역 등)면 그대로 */
function onUpdate(value: Date | Date[] | (Date | null)[] | null | undefined | string): void {
    if (value === null || value === undefined || Array.isArray(value)) {
        model.value = '';
        return;
    }
    model.value = typeof value === 'string' ? value : UtilDatePicker.fromCalendarDate(value);
}
</script>
