<template>
    <!--
        날짜 + 시각 입력칸 — PrimeVue `DatePicker` 위에 "값은 순간(ISO-8601 UTC), 보이는 것은 표시 시간대의 벽시계" 약속을 얹었다(hamonica3 frontend-guide 10-2).
        값(`v-model`)은 **순간**(`2026-10-02T05:32:00.000Z` 꼴 글자)이고 비면 ''. 고르개는 표시 값의 시간대로 읽고 쓴다(`UtilDatePicker.toPickerDate`·`fromPickerDate`) —
        브라우저 시간대와 표시 시간대가 달라도 표의 09:00 이 고르개에서도 09:00 이다. 분 단위까지만 다룬다(초는 버린다).
        **보이는 모양**은 표시 값을 따른다: 날짜 형식(`2026.10.02` · `2026-10-02` · `02/10/2026` · `10/02/2026`)과 시간 형식(24시간 · 12시간 오전/오후).
        표시 값이 바뀌면 즉시 바뀐다(`useDisplay().setDisplay`). 어느 시간대로 고르는지 입력 아래에 이름표(`(UTC+09:00) Asia/Seoul`)를 적는다(`show-zone`).
        날짜만 고르는 쓰임(서비스 날짜 등 시간대와 무관한 날짜)은 `AppDatePicker` 를 쓴다.
        입력 안 왼쪽에 달력 아이콘을 둔다(`calendarIcon`, 기본 켬 — PrimeVue `IconField` + `InputIcon`). 아이콘 자리·패딩 규칙은 `assets/date-field-icon.css`(AppDatePicker 와 같이 쓴다).
    -->
    <div class="flex flex-col gap-1">
        <IconField class="app-date-field" :class="{ 'app-date-field--icon': calendarIcon }">
            <InputIcon v-if="calendarIcon" class="pi pi-calendar app-date-icon" aria-hidden="true" />
            <DatePicker
                :key="formatKey"
                :model-value="asDate"
                :input-id="inputId"
                show-time
                :hour-format="hourFormat"
                :date-format="dateFormat"
                :show-button-bar="showButtonBar"
                :step-minute="minuteStep"
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
        </IconField>
        <small v-if="showZone" class="text-xs text-muted-color" data-testid="date-time-zone">{{ zoneText }}</small>
    </div>
</template>

<script setup lang="ts">
import UtilDatePicker from '#layers/display-time/utils/util-date-picker';

/** 값(순간, ISO-8601 UTC 글자) — `v-model`. 비어 있으면 '' */
const model = defineModel<string>({ required: true });

interface Props {
    /** 분을 몇 분 간격으로 고르는가(PrimeVue `stepMinute`) — 모르면 1분 */
    minuteStep?: number;
    /** 고를 수 있는 가장 이른 순간(ISO) */
    min?: string;
    /** 고를 수 있는 가장 늦은 순간(ISO) */
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
    /** 입력 아래에 시간대 이름표를 적는다 */
    showZone?: boolean;
    /** 입력 안 왼쪽에 달력 아이콘(장식 — 눌러도 입력이 눌린다) */
    calendarIcon?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    minuteStep: 1,
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
    showZone: true,
    calendarIcon: true,
});

/** 표시 값 — 날짜·시간 형식과 시간대를 따른다 */
const values = useDisplayValues();
const time = useDisplayTime();

const dateFormat = computed(() => UtilDatePicker.dateFormat(values.value));
const hourFormat = computed(() => UtilDatePicker.hourFormat(values.value));
const defaultPlaceholder = computed(() => UtilDatePicker.placeholder(values.value, true));
/** 형식이 바뀌면 고르개를 새로 그린다(열려 있던 달력·입력 중이던 글자가 낡은 형식으로 남지 않게) */
const formatKey = computed(() => `${dateFormat.value}|${hourFormat.value}`);
const zoneText = computed(() => time.value.zoneLabel());

/** 안내 문구 연결 — DatePicker 도 aria-describedby 를 prop 으로 받지 않아 입력 요소(pcInputText)로 직접 건다 */
const inputPt = computed(() => (props.describedBy ? { pcInputText: { root: { 'aria-describedby': props.describedBy } } } : undefined));

/** 순간 → 고르개가 읽는 Date(표시 시간대의 벽시계 숫자). 읽을 수 없으면 null */
const asDate = computed<Date | null>(() => UtilDatePicker.toPickerDate(model.value, values.value));
const minDate = computed<Date | undefined>(() => UtilDatePicker.toPickerDate(props.min, values.value) ?? undefined);
const maxDate = computed<Date | undefined>(() => UtilDatePicker.toPickerDate(props.max, values.value) ?? undefined);

/** 고르개가 준 값 → 순간. Date 면 표시 시간대의 그 시각으로 읽고, 글자(시험 대역 등)면 그대로 */
function onUpdate(value: Date | Date[] | (Date | null)[] | null | undefined | string): void {
    if (value === null || value === undefined || Array.isArray(value)) {
        model.value = '';
        return;
    }
    if (typeof value === 'string') {
        model.value = value;
        return;
    }
    model.value = UtilDatePicker.fromPickerDate(value, values.value) ?? '';
}
</script>

<style src="../assets/date-field-icon.css"></style>
