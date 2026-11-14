<template>
    <!--
        모바일 순서(home-news-contract 「모바일 순서」, 2026-11-15) — 레이아웃 편집기(`GadgetLayoutEditor` — 홈·가젯 페이지·게시판 공통)의 도구 줄 아래 칸.
        휴대폰 한 열에 보이는 가젯(가젯 노출 「모바일」이 켜진 것 + 게시판 본문)을 지금 차례대로 보인다 — 운영자가 정한 차례(`mobileOrder`)가 있으면 그것, 없으면 공개 화면과 같은 계산(공유 레이어 `UtilHomeStack`).
        차례 바꾸기: 줄 왼쪽 손잡이(☰)를 끌어 다른 줄 위·아래에 놓기 · 손잡이에서 Alt+↑/↓ · 줄 오른쪽 ↑·↓ 단추 → `move(id, 자리)`(편집기가 보이는 차례 그대로 `mobileOrder` 를 채운다).
        [PC 배치 따르기] → `follow`(= null). 자동일 때는 「PC 배치를 따르는 중」이고 단추가 꺼진다. [휴대폰 폭으로 보기] → `update:preview`(실제 화면 iframe 을 390px 폭으로 — 끌기 손잡이는 PC 폭에서만).
        목록은 접어 둔 채 시작한다(실제 화면을 밀어내지 않게). 계산·저장은 편집기 몫이고 이 부품은 받은 줄을 그리고 알리기만 한다.
    -->
    <section class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2" :aria-labelledby="`${baseId}-title`" data-testid="mobile-order">
        <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <span :id="`${baseId}-title`" class="text-sm font-medium">{{ t('homeLayout.mobileOrder') }}</span>
            <Tag :value="custom ? t('homeLayout.mobileOrderCustom') : t('homeLayout.mobileOrderAuto')" :severity="custom ? 'info' : 'secondary'" data-testid="mobile-order-state" />
            <span class="flex-1" />
            <Button
                type="button"
                :label="t('homeLayout.mobileOrderPreview')"
                icon="pi pi-mobile"
                size="small"
                :severity="preview ? undefined : 'secondary'"
                :variant="preview ? undefined : 'outlined'"
                :aria-pressed="preview"
                data-testid="mobile-order-preview"
                @click="emit('update:preview', !preview)"
            />
            <Button
                v-if="!disabled"
                type="button"
                :label="t('homeLayout.mobileOrderFollow')"
                icon="pi pi-replay"
                size="small"
                severity="secondary"
                variant="outlined"
                :disabled="!custom"
                data-testid="mobile-order-follow"
                @click="follow"
            />
            <Button
                type="button"
                :label="open ? t('homeLayout.mobileOrderHide') : t('homeLayout.mobileOrderShow')"
                :icon="open ? 'pi pi-chevron-up' : 'pi pi-chevron-down'"
                icon-pos="right"
                size="small"
                severity="secondary"
                variant="text"
                :aria-expanded="open"
                :aria-controls="`${baseId}-list`"
                data-testid="mobile-order-toggle"
                @click="open = !open"
            />
        </div>
        <p v-if="preview" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.mobileOrderPreviewNote') }}</p>

        <div v-show="open" :id="`${baseId}-list`" class="flex flex-col gap-2">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.mobileOrderHint') }}</p>
            <p v-if="rows.length === 0" class="m-0 text-sm text-muted-color">{{ t('homeLayout.mobileOrderEmpty') }}</p>
            <ol v-else class="m-0 flex list-none flex-col gap-1 p-0" :aria-labelledby="`${baseId}-title`">
                <li
                    v-for="(row, index) in rows"
                    :key="row.id"
                    :ref="(el) => setRowEl(row.id, el)"
                    class="relative"
                    @dragover="(e: DragEvent) => onOver(e, row.id)"
                    @drop="(e: DragEvent) => onDrop(e, row.id)"
                >
                    <span v-if="mark?.id === row.id" class="pointer-events-none absolute inset-x-0 z-10 h-0.5 rounded bg-primary" :class="mark.after ? '-bottom-[3px]' : '-top-[3px]'" />
                    <div class="flex min-w-0 items-center gap-2 rounded-lg border border-(--line) bg-(--p-content-background) px-1.5 py-1" :class="dragId === row.id ? 'opacity-40' : ''">
                        <button
                            v-if="!disabled"
                            type="button"
                            draggable="true"
                            class="grid size-7 shrink-0 cursor-grab place-items-center rounded-md border-0 bg-transparent text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary active:cursor-grabbing dark:hover:bg-surface-800"
                            :aria-label="t('homeLayout.mobileOrderHandle', { title: row.title })"
                            aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown"
                            @dragstart="(e: DragEvent) => onStart(e, row.id)"
                            @dragend="reset"
                            @keydown="(e: KeyboardEvent) => onKey(e, row.id, index)"
                        >
                            <i class="pi pi-bars text-xs" aria-hidden="true" />
                        </button>
                        <span class="w-5 shrink-0 text-right text-xs tabular-nums text-muted-color">{{ index + 1 }}</span>
                        <i :class="`pi ${row.icon}`" class="shrink-0 text-xs text-muted-color" aria-hidden="true" />
                        <span class="min-w-0 flex-1 truncate text-sm">
                            {{ row.title }}
                            <span class="ml-1 text-xs text-muted-color">{{ row.kind }}</span>
                        </span>
                        <Tag v-if="row.mobileOnly" :value="t('homeLayout.mobileOrderMobileOnly')" severity="secondary" class="shrink-0" />
                        <template v-if="!disabled">
                            <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('homeLayout.mobileOrderUp', { title: row.title })" :disabled="index === 0" @click="move(row.id, index - 1)" />
                            <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('homeLayout.mobileOrderDown', { title: row.title })" :disabled="index === rows.length - 1" @click="move(row.id, index + 1)" />
                        </template>
                    </div>
                </li>
            </ol>
        </div>
        <p class="sr-only" role="status" aria-live="polite">{{ live }}</p>
    </section>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue';

/** 목록 한 줄 — 편집기가 차례대로 준다 */
export interface GadgetMobileOrderRow {
    /** 가젯 ID(새로 넣은 가젯은 임시 ID) */
    id: string;
    /** 가젯 제목 */
    title: string;
    /** 종류 이름(번역한 값) */
    kind: string;
    /** PrimeIcons 이름(`pi-…`) */
    icon: string;
    /** PC 에서는 숨는 가젯인가(모바일 전용) */
    mobileOnly: boolean;
}

interface Props {
    /** 보이는 차례대로의 줄 */
    rows: GadgetMobileOrderRow[];
    /** 운영자가 정한 차례인가(아니면 PC 배치를 따르는 중) */
    custom?: boolean;
    /** 바꿀 수 없는가(권한 없음·저장 중) — 손잡이·단추를 숨긴다 */
    disabled?: boolean;
    /** 실제 화면을 휴대폰 폭으로 보는가 */
    preview?: boolean;
}
const props = withDefaults(defineProps<Props>(), { custom: false, disabled: false, preview: false });
const emit = defineEmits<{
    /** 가젯을 보이는 차례의 `to` 자리(0부터)로 옮긴다 */
    (e: 'move', id: string, to: number): void;
    /** PC 배치 따르기(모바일 순서 = null) */
    (e: 'follow'): void;
    (e: 'update:preview', value: boolean): void;
}>();

const { t } = useI18n();
const baseId = useId();

const open = ref(false);
const live = ref('');

/* ── 끌어 놓기(HTML 끌기 — 메뉴 편집기 `MenuTreeEditor` 와 같은 방식) ── */
const dragId = ref<string | null>(null);
/** 놓일 자리 표시 — 그 줄의 위(`after` 거짓)·아래 */
const mark = ref<{ id: string; after: boolean } | null>(null);
const rowEls = new Map<string, HTMLElement>();

function setRowEl(id: string, el: Element | ComponentPublicInstance | null): void {
    if (el instanceof HTMLElement) rowEls.set(id, el);
    else rowEls.delete(id);
}

function titleOf(id: string): string {
    return props.rows.find((r) => r.id === id)?.title ?? '';
}

function move(id: string, to: number): void {
    if (props.disabled || to < 0 || to >= props.rows.length) return;
    emit('move', id, to);
    live.value = t('homeLayout.mobileOrderMoved', { title: titleOf(id), n: to + 1 });
}

function follow(): void {
    emit('follow');
    live.value = t('homeLayout.mobileOrderFollowed');
}

function onStart(event: DragEvent, id: string): void {
    dragId.value = id;
    mark.value = null;
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', id);
        const el = rowEls.get(id);
        if (el) event.dataTransfer.setDragImage(el, 16, 16);
    }
}

function onOver(event: DragEvent, id: string): void {
    if (dragId.value === null) return;
    event.preventDefault();
    if (id === dragId.value) {
        mark.value = null;
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        return;
    }
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const after = (event.clientY - box.top) / Math.max(box.height, 1) >= 0.5;
    if (mark.value?.id !== id || mark.value.after !== after) mark.value = { id, after };
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
}

function onDrop(event: DragEvent, id: string): void {
    event.preventDefault();
    const from = dragId.value;
    const target = mark.value;
    reset();
    if (from === null || !target || target.id !== id) return;
    // 끈 줄을 뺀 차례에서 놓은 줄의 앞·뒤 자리
    const rest = props.rows.map((r) => r.id).filter((v) => v !== from);
    const at = rest.indexOf(id) + (target.after ? 1 : 0);
    move(from, at);
}

function reset(): void {
    dragId.value = null;
    mark.value = null;
}

/** 손잡이에서 Alt+↑/↓ — 한 칸 옮기고 손잡이에 초점을 둔다 */
function onKey(event: KeyboardEvent, id: string, index: number): void {
    if (!event.altKey || (event.key !== 'ArrowUp' && event.key !== 'ArrowDown')) return;
    event.preventDefault();
    move(id, event.key === 'ArrowUp' ? index - 1 : index + 1);
    void nextTick(() => rowEls.get(id)?.querySelector<HTMLElement>('button[draggable]')?.focus());
}
</script>
