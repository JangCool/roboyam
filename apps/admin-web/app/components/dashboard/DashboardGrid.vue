<template>
    <!--
        대시보드 격자 — 운영자 구성(48칸 · 행 12px · 간격 16px, `UtilDashboardLayout`)대로 가젯을 그린다. **라이브러리를 쓰지 않는다**(홈 격자 `GadgetGrid` 와 같은 방식):
        가로는 칸 수로 나눈 비율, 세로는 행 × (행 높이 + 간격)의 절대 위치. 가젯 상자 높이는 `h` 로 고정이고 내용이 넘치면 가젯 안에서 스크롤한다(`AppGadget fill`).
        · 넓은 화면(lg 이상): 구성 그대로. 좁은 화면: 읽는 순서((y, x))로 한 열, 편집 불가.
        · 보기: 권한이 빠진 가젯은 그리지 않고 그 자리를 위로 당긴다. 편집: 「권한이 없어 보이지 않는 가젯」으로 흐리게 그려 뺄 수 있게.
        · 편집(`editing`): 가젯마다 위에 덮는 손잡이 층 — 아무 데나 잡고 끌면 옮기기, 오른쪽 아래 모서리를 끌면 크기, 머리 오른쪽 ⚙ 설정 · ✕ 빼기.
          끄는 동안 놓일 자리(점선)와 밀려날 다른 가젯을 바로 보인다(`UtilDashboardLayout.setBox` — 부딪친 가젯은 밀리고 위로 당긴다). 가젯 안 링크·단추는 눌리지 않는다(덮개 + `inert`).
          키보드: 손잡이 층에 초점 → 방향키 옮기기(위·아래는 위·아래 가젯과 자리 바꿈) · Shift+방향키 크기. 바뀐 자리는 화면 읽기에 알린다.
        요소 순서는 읽는 순서라 Tab 순서가 보이는 순서와 같다. 바뀐 구성은 `update:gadgets` 로 알린다(저장은 페이지).
    -->
    <div ref="root" class="relative min-w-0" :class="wide ? '' : 'flex flex-col gap-4'" :style="wide ? { height: `${UtilDashboardLayout.px(bottom)}px` } : undefined" data-testid="dashboard-grid">
        <div
            v-for="cell in cells"
            :key="cell.item.id"
            class="min-w-0"
            :class="[
                wide ? 'absolute' : 'relative',
                drag?.id === cell.item.id ? (drag.kind === 'move' ? 'z-20 opacity-90 shadow-lg' : 'z-20') : 'motion-safe:transition-[top,left,width,height] motion-safe:duration-150',
            ]"
            :style="cellStyle(cell)"
            :data-gadget-id="cell.item.id"
        >
            <!-- 배경색: 칸에 카드 바탕 변수(`--p-card-background`)를 걸어 그 가젯 카드만 칠한다(색은 아래 style — 밝은·어두운 테마) -->
            <div class="dash-tint h-full" :class="cell.allowed ? '' : 'opacity-60'" :data-color="tintOf(cell)" :style="{ '--tint': UtilDashboardLayout.colorHex(tintOf(cell)) ?? undefined }" :inert="editing || undefined">
                <DashboardGadgetRenderer v-if="cell.allowed" :gadget="cell.item" />
                <AppGadget v-else :title="cell.title" fill class="h-full">
                    <EmptyState icon="pi pi-lock" :title="t('dashboard.editForbiddenTitle')" :description="t('dashboard.editForbiddenBody')" />
                </AppGadget>
            </div>

            <!-- 편집 손잡이 층 -->
            <div
                v-if="editing && wide"
                :ref="(el) => bindHandle(cell.item.id, el)"
                class="dash-edit absolute inset-0 z-10 cursor-move touch-none rounded-xl select-none"
                :class="drag?.id === cell.item.id ? 'dash-edit-on' : ''"
                tabindex="0"
                role="group"
                :aria-label="t('dashboard.editHandle', { title: cell.title })"
                :aria-describedby="hintId"
                @pointerdown="onDown($event, cell.item, 'move')"
                @keydown="onKey($event, cell.item)"
            >
                <div class="absolute top-2 right-2 left-2 flex items-center gap-1.5">
                    <span class="inline-flex min-w-0 items-center gap-1.5 rounded-md bg-surface-0/90 px-2 py-1 text-[12px] leading-none shadow-sm dark:bg-surface-900/90">
                        <i class="pi pi-arrows-alt shrink-0 text-[11px] text-primary" aria-hidden="true" />
                        <span class="truncate font-medium">{{ cell.title }}</span>
                        <span class="shrink-0 text-muted-color tabular-nums">{{ t('dashboard.editSize', sizeOf(cell.item)) }}</span>
                    </span>
                    <span class="ml-auto flex shrink-0 items-center gap-1">
                        <Button
                            v-if="cell.allowed"
                            type="button"
                            icon="pi pi-cog"
                            size="small"
                            severity="secondary"
                            class="size-7!"
                            :aria-label="t('dashboard.editSettings', { title: cell.title })"
                            @pointerdown.stop
                            @click.stop="emit('settings', cell.item.id)"
                        />
                        <Button
                            type="button"
                            icon="pi pi-times"
                            size="small"
                            severity="danger"
                            class="size-7!"
                            :aria-label="t('dashboard.editRemove', { title: cell.title })"
                            @pointerdown.stop
                            @click.stop="emit('remove', cell.item.id)"
                        />
                    </span>
                </div>
                <span class="dash-edit-resize" aria-hidden="true" @pointerdown.stop="onDown($event, cell.item, 'size')" />
            </div>
        </div>

        <!-- 끄는 동안 놓일 자리 -->
        <div v-if="ghost" class="pointer-events-none absolute rounded-xl border-2 border-dashed border-primary bg-primary-50/40 dark:bg-primary-950/30" :style="ghost" aria-hidden="true" />

        <p :id="hintId" class="sr-only">{{ t('dashboard.editKeysHint') }}</p>
        <p class="sr-only" aria-live="polite">{{ announce }}</p>
    </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance, CSSProperties } from 'vue';
import type { DashboardGadgetColor, DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardLayout from '~/utils/util-dashboard-layout';

interface Props {
    /** 구성(위로 당긴 모양) */
    gadgets: DashboardGadgetItem[];
    /** 화면 편집 중 */
    editing?: boolean;
}

const props = withDefaults(defineProps<Props>(), { editing: false });

const emit = defineEmits<{
    (e: 'update:gadgets', value: DashboardGadgetItem[]): void;
    (e: 'settings', id: string): void;
    (e: 'remove', id: string): void;
}>();

interface Cell {
    item: DashboardGadgetItem;
    title: string;
    allowed: boolean;
}

const { t } = useI18n();
const { can } = useAdminPermission();
const hintId = useId();

const COLUMNS = UtilDashboardLayout.COLUMNS;
const GAP = UtilDashboardLayout.GAP;
const UNIT_Y = UtilDashboardLayout.ROW_HEIGHT + GAP;

/** 넓은 화면(lg 이상) — 아니면 한 열 */
const wide = useDashboardWide();

const root = ref<HTMLElement | null>(null);

/** 칸의 배경색 — 권한이 없어 흐리게 보이는 칸은 흰색 */
function tintOf(cell: Cell): DashboardGadgetColor {
    return cell.allowed ? UtilDashboardLayout.colorOf(cell.item) : 'NONE';
}

function titleOf(item: DashboardGadgetItem): string {
    return item.config.title || t(`dashboard.kind.${item.type}`);
}

/* ── 끌기 상태 ── */

interface Drag {
    id: string;
    kind: 'move' | 'size';
    /** 시작할 때의 자리 */
    start: DashboardGadgetItem;
    startX: number;
    startY: number;
    dx: number;
    dy: number;
    /** 끄는 동안의 구성(놓으면 이것이 된다) */
    preview: DashboardGadgetItem[];
}

const drag = ref<Drag | null>(null);

/** 보기: 권한 있는 가젯만 당겨서 · 편집: 전부 그대로 · 끄는 중: 미리 보기 구성 */
const shown = computed<DashboardGadgetItem[]>(() => {
    if (drag.value) return drag.value.preview;
    if (props.editing) return props.gadgets;
    return UtilDashboardLayout.compact(props.gadgets.filter((g) => UtilDashboardLayout.allowed(g.type, can)));
});

const cells = computed<Cell[]>(() =>
    UtilDashboardLayout.byReadingOrder(shown.value).map((item) => ({ item, title: titleOf(item), allowed: UtilDashboardLayout.allowed(item.type, can) })),
);

const bottom = computed(() => UtilDashboardLayout.bottom(shown.value));

function colExpr(n: number): string {
    return `((100% - ${(COLUMNS - 1) * GAP}px) / ${COLUMNS} * ${n} + ${Math.max(0, n - 1) * GAP}px)`;
}

function cellStyle(cell: Cell): CSSProperties {
    const it = cell.item;
    const height = `${UtilDashboardLayout.px(it.h)}px`;
    if (!wide.value) return { height };
    const d = drag.value;
    // 옮기는 가젯은 손가락을 따라간다(시작 자리 + 끈 거리) — 놓일 자리는 점선으로
    if (d && d.id === it.id && d.kind === 'move') {
        const s = d.start;
        return { top: `${s.y * UNIT_Y}px`, left: `calc(${colExpr(s.x)} + ${s.x > 0 ? GAP : 0}px)`, width: `calc(${colExpr(s.w)})`, height: `${UtilDashboardLayout.px(s.h)}px`, transform: `translate(${d.dx}px, ${d.dy}px)` };
    }
    return { top: `${it.y * UNIT_Y}px`, left: `calc(${colExpr(it.x)} + ${it.x > 0 ? GAP : 0}px)`, width: `calc(${colExpr(it.w)})`, height };
}

/** 놓일 자리 표시 */
const ghost = computed<CSSProperties | null>(() => {
    const d = drag.value;
    if (!d || d.kind !== 'move') return null;
    const it = d.preview.find((g) => g.id === d.id);
    if (!it) return null;
    return { top: `${it.y * UNIT_Y}px`, left: `calc(${colExpr(it.x)} + ${it.x > 0 ? GAP : 0}px)`, width: `calc(${colExpr(it.w)})`, height: `${UtilDashboardLayout.px(it.h)}px` };
});

function sizeOf(item: DashboardGadgetItem): { w: number; h: number } {
    return { w: item.w, h: item.h };
}

/** 한 칸의 걸음(px) — 칸 폭 + 간격 */
function unitX(): number {
    const width = root.value?.clientWidth ?? 0;
    return (width - GAP * (COLUMNS - 1)) / COLUMNS + GAP;
}

function onDown(event: PointerEvent, item: DashboardGadgetItem, kind: 'move' | 'size'): void {
    if (event.button !== 0 || !wide.value || !props.editing) return;
    event.preventDefault();
    drag.value = { id: item.id, kind, start: { ...item }, startX: event.clientX, startY: event.clientY, dx: 0, dy: 0, preview: props.gadgets };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp, { once: true });
    window.addEventListener('pointercancel', onCancel, { once: true });
}

function onMove(event: PointerEvent): void {
    const d = drag.value;
    if (!d) return;
    d.dx = event.clientX - d.startX;
    d.dy = event.clientY - d.startY;
    const ux = unitX();
    const s = d.start;
    if (d.kind === 'move') {
        const x = Math.round((s.x * ux + d.dx) / ux);
        const y = Math.max(0, Math.round((s.y * UNIT_Y + d.dy) / UNIT_Y));
        d.preview = UtilDashboardLayout.setBox(props.gadgets, d.id, { x, y });
    } else {
        const w = Math.round((s.w * ux + d.dx) / ux);
        const h = Math.round((UtilDashboardLayout.px(s.h) + GAP + d.dy) / UNIT_Y);
        d.preview = UtilDashboardLayout.setBox(props.gadgets, d.id, { w, h });
    }
}

function finish(commit: boolean): void {
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
    window.removeEventListener('pointercancel', onCancel);
    const d = drag.value;
    drag.value = null;
    if (!d || !commit) return;
    if (Math.abs(d.dx) < 4 && Math.abs(d.dy) < 4) return;
    if (UtilDashboardLayout.same(d.preview, props.gadgets)) return;
    emit('update:gadgets', d.preview);
    const moved = d.preview.find((g) => g.id === d.id);
    if (moved) say(moved);
}

function onUp(): void {
    finish(true);
}
function onCancel(): void {
    finish(false);
}

/* ── 키보드 ── */

const handles = new Map<string, HTMLElement>();
function bindHandle(id: string, el: Element | ComponentPublicInstance | null): void {
    if (el instanceof HTMLElement) handles.set(id, el);
    else handles.delete(id);
}

const announce = ref('');
function say(item: DashboardGadgetItem): void {
    announce.value = t('dashboard.editAnnounce', { title: titleOf(item), ...UtilDashboardLayout.position(item) });
}

const ARROWS: Record<string, 'left' | 'right' | 'up' | 'down'> = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' };

function onKey(event: KeyboardEvent, item: DashboardGadgetItem): void {
    if (event.target !== event.currentTarget) return;
    const dir = ARROWS[event.key];
    if (!dir) return;
    event.preventDefault();
    let next: DashboardGadgetItem[];
    if (event.shiftKey) {
        const box = dir === 'left' ? { w: item.w - 1 } : dir === 'right' ? { w: item.w + 1 } : dir === 'up' ? { h: item.h - 1 } : { h: item.h + 1 };
        next = UtilDashboardLayout.setBox(props.gadgets, item.id, box);
    } else {
        next = UtilDashboardLayout.nudge(props.gadgets, item.id, dir);
    }
    const moved = next.find((g) => g.id === item.id);
    if (!moved || UtilDashboardLayout.same(next, props.gadgets)) {
        if (moved) say(moved);
        return;
    }
    emit('update:gadgets', next);
    say(moved);
    // 읽는 순서가 바뀌면 요소가 옮겨져 초점이 빠질 수 있다 — 같은 가젯 손잡이로 돌려놓는다
    void nextTick(() => handles.get(item.id)?.focus());
}

onBeforeUnmount(() => finish(false));
</script>

<style scoped>
.dash-edit {
    outline: 2px dashed color-mix(in srgb, var(--p-primary-color) 55%, transparent);
    outline-offset: -1px;
    background: color-mix(in srgb, var(--p-content-background) 35%, transparent);
}
.dash-edit:hover,
.dash-edit-on {
    background: color-mix(in srgb, var(--p-primary-color) 6%, transparent);
}
.dash-edit:focus-visible {
    outline: 2px solid var(--p-primary-color);
    outline-offset: 2px;
}
.dash-edit-resize {
    position: absolute;
    right: 2px;
    bottom: 2px;
    width: 18px;
    height: 18px;
    cursor: nwse-resize;
    border-right: 3px solid var(--p-primary-color);
    border-bottom: 3px solid var(--p-primary-color);
    border-bottom-right-radius: 10px;
}

/* 가젯 배경색 — 칸이 기준색(`--tint`, `UtilDashboardLayout.colorHex`)을 걸고, 바탕·선은 그 색을 흰 바탕(밝은 테마)·어두운 면(어두운 테마)에 옅게 섞는다 */
.dash-tint:not([data-color='NONE']) {
    --p-card-background: color-mix(in srgb, var(--tint) 7%, #fff);
    --line: color-mix(in srgb, var(--tint) 22%, #e5e7eb);
}
:global([data-theme='dark']) .dash-tint:not([data-color='NONE']) {
    --p-card-background: color-mix(in srgb, var(--tint) 12%, var(--p-surface-900));
    --line: color-mix(in srgb, var(--tint) 30%, var(--p-surface-700));
}
</style>
