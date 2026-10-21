<template>
    <!--
        체크 목록 팝오버 — 단추를 누르면 제목·설명 아래 스크롤되는 체크박스 목록이 뜬다. 「컬럼 선택」(표에 보일 칸)과 「검색편집」(화면에 보일 조건 줄)이 같은 모양이라 하나로 쓴다.
        `required` 항목은 늘 켜져 있고 비활성이다. 값(`v-model`)은 켠 항목의 키 배열(항목 차례). Esc 로 닫고 포커스는 단추로 돌아간다(PrimeVue Popover).
        `resetLabel` 을 주면 목록 아래 「기본값」 단추가 나오고 누르면 `reset` 이 올라간다(기본 선택이 무엇인지는 부르는 쪽이 안다).
    -->
    <div>
        <Button type="button" :label="buttonLabel" :icon="icon" size="small" :class="white ? 'check-list-white' : undefined" severity="secondary" variant="outlined" aria-haspopup="dialog" :aria-expanded="open" @click="toggle" />
        <Popover ref="panel" :aria-labelledby="titleId" :pt="{ content: { class: 'p-0!' } }" @show="onShow" @hide="open = false">
            <div class="flex w-64 flex-col">
                <div class="border-b border-(--line) px-4 py-3">
                    <h3 :id="titleId" class="text-sm font-semibold">{{ title }}</h3>
                    <p v-if="description" class="mt-0.5 text-xs text-muted-color">{{ description }}</p>
                </div>
                <div class="max-h-64 overflow-y-auto px-4 py-2" role="group" :aria-labelledby="titleId">
                    <div v-for="(item, index) in items" :key="item.key" class="flex items-center gap-2.5 py-1.5">
                        <Checkbox binary :model-value="model.includes(item.key)" :input-id="`${uid}-${index}`" :disabled="item.required" @update:model-value="change(item.key, $event)" />
                        <label :for="`${uid}-${index}`" class="text-[13px]" :class="item.required ? 'cursor-not-allowed text-muted-color' : 'cursor-pointer'">{{ item.label }}</label>
                    </div>
                </div>
                <div v-if="resetLabel" class="border-t border-(--line) px-2 py-1.5">
                    <Button type="button" :label="resetLabel" size="small" severity="secondary" variant="text" @click="emit('reset')" />
                </div>
            </div>
        </Popover>
    </div>
</template>

<script setup lang="ts">
interface CheckListItem {
    key: string;
    label: string;
    /** 필수 — 늘 켜져 있고 끌 수 없다 */
    required?: boolean;
}

/** 켠 항목의 키들 */
const model = defineModel<string[]>({ required: true });

interface Props {
    items: CheckListItem[];
    buttonLabel: string;
    /** primeicons 클래스 */
    icon?: string;
    title: string;
    description?: string;
    /** 「기본값」 단추 글자. 없으면 단추를 두지 않는다 */
    resetLabel?: string;
    /** 팝오버가 단추 아래 어느 쪽에 맞춰 열리는가 — `right` 는 오른쪽 끝을 단추의 오른쪽 끝에 맞춘다(화면 오른쪽 끝에 놓인 단추용) */
    align?: 'left' | 'right';
    /** true 면 단추를 하얀 서브 단추로 그린다(콘텐츠 면 + 진한 테두리 — hamonica3 `.btn-white`) — 표 툴바의 「컬럼 선택」처럼 페이지 배경 위에 얹히는 자리 */
    white?: boolean;
}

const props = withDefaults(defineProps<Props>(), { icon: 'pi pi-sliders-h', description: '', resetLabel: '', align: 'left', white: false });
const emit = defineEmits<{ reset: [] }>();

const uid = useId();
const titleId = useId();
const panel = ref<{ toggle: (event: Event) => void; container?: HTMLElement | null } | null>(null);
const open = ref(false);
const trigger = ref<HTMLElement | null>(null);

/** 열린 뒤 `align="right"` 면 팝오버를 단추 오른쪽 끝에 맞춘다(PrimeVue 는 왼쪽 끝에 맞춰 연다) */
function onShow(): void {
    open.value = true;
    if (props.align !== 'right') return;
    void nextTick(() => {
        const container = panel.value?.container;
        const button = trigger.value;
        if (!container || !button) return;
        const target = button.getBoundingClientRect();
        const width = container.getBoundingClientRect().width;
        const left = Math.max(8, target.right + window.scrollX - width);
        container.style.left = `${left}px`;
    });
}

function toggle(event: Event): void {
    trigger.value = event.currentTarget as HTMLElement | null;
    panel.value?.toggle(event);
}

/** 항목 하나를 켜고 끈다 — 항목 차례를 지키고 필수 항목은 늘 넣는다 */
function change(key: string, on: boolean): void {
    const next = new Set(model.value);
    if (on) next.add(key);
    else next.delete(key);
    model.value = props.items.filter((item) => item.required || next.has(item.key)).map((item) => item.key);
}
</script>

<style scoped>
/*
 * 하얀 서브 단추(hamonica3 btn-white 와 같은 모양) — PrimeVue severity=secondary variant=outlined 를 바탕으로 이 단추 범위에서만 면·선·글자를 토큰으로 다시 정한다.
 *  · 면: 콘텐츠 면(라이트 흰색 · 다크 어두운 면) — 표 카드와 같은 면이라 페이지 배경 위에서 떠 보인다. 선: --line-strong. 글자: --p-text-color.
 *  · 호버: 한 단계 옅은 회색 면(surface-100 · 다크 800), 눌림: 한 단계 더(200 · 700). 팝오버가 열린 동안(aria-expanded=true)은 눌림 면 + 진한 테두리(surface-500 · 다크 400)로 닫힌 때와 구분한다.
 *  · 면은 PrimeVue 의 outlined 규칙(transparent)보다 우선해야 해서 변수가 아니라 직접 지정한다(스타일 층 밖 규칙이 이긴다). 초점 링은 PrimeVue 기본(--p-focus-ring-*) 그대로.
 */
.check-list-white {
    --p-button-outlined-secondary-color: var(--p-text-color);
    --p-button-outlined-secondary-border-color: var(--line-strong);
    background: var(--p-content-background);
}
.check-list-white:not(:disabled):hover {
    background: light-dark(var(--p-surface-100), var(--p-surface-800));
}
.check-list-white:not(:disabled):active,
.check-list-white[aria-expanded='true'] {
    background: light-dark(var(--p-surface-200), var(--p-surface-700));
}
.check-list-white[aria-expanded='true'] {
    border-color: light-dark(var(--p-surface-500), var(--p-surface-400));
}
</style>
