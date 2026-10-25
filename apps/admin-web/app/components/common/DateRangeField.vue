<template>
    <!--
        기간 입력 — 시작 ~ 끝 날짜 고르개(`AppDatePicker`) + 빠른 선택 칩(「오늘 · 1주일 · 1개월 · 3개월 · 6개월 · 1년」). 칩은 누르면 **오늘 기준으로 두 칸을 채우는 일반 버튼**이다(켜고 끄는 토글이 아니다) — 누른 뒤 결과 기간이 입력에 보인다.
        값은 달력 날짜 글자(`YYYY-MM-DD`)이고 비면 ''. 시간대 변환은 하지 않는다 — 서버로 보낼 순간 경계(시작 00:00 ~ 끝 23:59:59.999)는 부르는 쪽이 만든다(`UtilMember.toQuery`). "오늘"은 표시 시간대의 오늘이다.
        `presets` 를 비우면 칩을 두지 않는다. 기본 슬롯은 칩 뒤에 붙는 것(예: 「로그인 이력 없음」 체크)이다. 안쪽 입력의 라벨은 화면에 보이지 않게(`sr-only`) 붙인다. 칸 안 달력 아이콘은 `AppDatePicker` 가 기본으로 그린다.
    -->
    <div class="flex flex-wrap items-center gap-x-2 gap-y-2">
        <div class="w-42">
            <label class="sr-only" :for="fromId">{{ fromLabel }}</label>
            <AppDatePicker v-model="from" :input-id="fromId" :max="to || undefined" :disabled="disabled" />
        </div>
        <span class="text-muted-color" aria-hidden="true">~</span>
        <div class="w-42">
            <label class="sr-only" :for="toId">{{ toLabel }}</label>
            <AppDatePicker v-model="to" :input-id="toId" :min="from || undefined" :disabled="disabled" />
        </div>
        <div v-if="presets.length" class="flex flex-wrap items-center gap-1" role="group" :aria-label="presetsLabel">
            <Button v-for="preset in presets" :key="preset.kind" type="button" :label="preset.label" size="small" severity="secondary" variant="outlined" :disabled="disabled" @click="fill(preset.kind)" />
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
import UtilDateRange, { type DateRangePresetKind } from '~/utils/util-date-range';

/** 시작 날짜(`YYYY-MM-DD`, 비면 '') */
const from = defineModel<string>('from', { required: true });
/** 끝 날짜(`YYYY-MM-DD`, 비면 '') */
const to = defineModel<string>('to', { required: true });

interface DateRangePreset {
    kind: DateRangePresetKind;
    label: string;
}

interface Props {
    /** 빠른 선택 칩 — 글자는 부르는 쪽이 번역해 준다. 빈 배열이면 칩이 없다 */
    presets?: DateRangePreset[];
    /** 칩 묶음의 이름(스크린리더) */
    presetsLabel?: string;
    fromLabel: string;
    toLabel: string;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { presets: () => [], presetsLabel: '', disabled: false });

const fromId = useId();
const toId = useId();
const time = useDisplayTime();

/** 오늘 기준으로 두 칸을 채운다 */
function fill(kind: DateRangePresetKind): void {
    const p = time.value.parts(new Date().toISOString());
    if (!p) return;
    const today = `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}`;
    const range = UtilDateRange.fill(kind, today);
    if (!range) return;
    from.value = range.from;
    to.value = range.to;
}
</script>

