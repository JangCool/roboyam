<template>
    <!--
        메뉴 트리 편집 목록 — 공개 사이트 메뉴와 관리자 사이트 메뉴가 함께 쓴다. 노드를 트리 차례로 한 줄씩 늘어놓고(단계는 들여쓰기), 줄 내용은 `row` 슬롯이 그린다.
        순서·단계 바꾸기:
          · 줄 앞 손잡이를 **끌어** 다른 줄의 위쪽(앞)·아래쪽(뒤)에 놓거나, 담을 수 있는 줄(폴더·묶음)의 가운데에 놓으면 그 안 맨 끝으로 들어간다. 놓을 수 없는 자리는 빨간 표시 + 이유.
          · 손잡이에 초점을 두고 **Alt+↑↓**(순서) · **Alt+←**(한 단계 밖, 부모 바로 뒤) · **Alt+→**(바로 위 폴더 안 맨 끝). 옮긴 뒤에도 같은 손잡이에 초점이 남는다.
          · 손잡이를 **누르면** 같은 네 가지가 든 메뉴가 열린다(터치 화면·드래그가 어려울 때).
        어디에 놓을 수 있는지(단계·부모 종류)는 부르는 쪽의 `check` 가 정한다 — 여기는 옮기기를 묻고 알릴 뿐이다.
    -->
    <div class="flex min-w-0 flex-col gap-1.5">
        <p v-if="dragKey !== null" class="sticky top-14 z-20 m-0 rounded-md border px-3 py-1.5 text-xs" :class="mark && !mark.allowed ? 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300' : 'border-(--line) bg-surface-50 text-muted-color dark:bg-surface-900'" role="status">
            {{ hint }}
        </p>
        <p class="sr-only" role="status" aria-live="polite">{{ live }}</p>

        <ul class="m-0 flex list-none flex-col gap-1 p-0" :aria-label="label">
            <li
                v-for="row in rows"
                :key="row.key"
                :ref="(el) => setRowEl(row.key, el)"
                class="relative"
                :style="{ paddingLeft: indentOf(row) }"
                @dragover="(e: DragEvent) => onOver(e, row)"
                @drop="(e: DragEvent) => onDrop(e, row)"
            >
                <span v-if="mark?.key === row.key && mark.where !== 'INSIDE'" class="pointer-events-none absolute right-0 z-10 h-0.5 rounded" :class="[mark.where === 'BEFORE' ? '-top-[3px]' : '-bottom-[3px]', mark.allowed ? 'bg-primary' : 'bg-red-500']" :style="{ left: indentOf(row) }" />
                <div
                    class="flex min-w-0 items-start gap-1.5 rounded-lg border border-(--line) bg-(--p-content-background) px-1.5 py-1.5 transition-shadow"
                    :class="[dragKey === row.key ? 'opacity-40' : '', mark?.key === row.key && mark.where === 'INSIDE' ? (mark.allowed ? 'ring-2 ring-primary' : 'ring-2 ring-red-500') : '']"
                >
                    <button
                        v-if="!disabled"
                        :id="handleId(row.key)"
                        type="button"
                        draggable="true"
                        class="grid size-7 shrink-0 cursor-grab place-items-center rounded-md text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary active:cursor-grabbing dark:hover:bg-surface-800"
                        :aria-label="t('menuConfig.handleLabel', { name: row.name })"
                        aria-haspopup="menu"
                        aria-keyshortcuts="Alt+ArrowUp Alt+ArrowDown Alt+ArrowLeft Alt+ArrowRight"
                        @dragstart="(e: DragEvent) => onStart(e, row)"
                        @dragend="reset"
                        @keydown="(e: KeyboardEvent) => onKey(e, row)"
                        @click="(e: MouseEvent) => openMenu(e, row)"
                    >
                        <i class="pi pi-bars text-xs" aria-hidden="true" />
                    </button>
                    <div class="min-w-0 flex-1">
                        <slot name="row" :row="row" />
                    </div>
                </div>
            </li>
        </ul>

        <Menu ref="moveMenu" :model="menuItems" popup />
    </div>
</template>

<script setup lang="ts" generic="T extends MenuTreeEditorRow">
import type { MenuItem } from 'primevue/menuitem';
import type { MenuTreeEditorRow } from '~/components/menu/MenuTreeEditor.types';
import type { MenuDropWhere, MenuMoveAction, MenuMoveOp } from '~/utils/util-menu-tree';

interface Props {
    /** 트리 차례의 줄 */
    rows: T[];
    /** 이 옮기기가 막힌 이유(되면 null) */
    check: (key: string, action: MenuMoveAction) => string | null;
    /** 목록 이름(스크린리더) */
    label: string;
    /** 읽기 전용 — 손잡이를 그리지 않는다 */
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { disabled: false });
const emit = defineEmits<{ (e: 'move', key: string, action: MenuMoveAction): void }>();
defineSlots<{ row(props: { row: T }): unknown }>();

const { t } = useI18n();
const idBase = useId();

const handleId = (key: string) => `${idBase}-h-${key}`;
const indentOf = (row: T) => `${(row.depth - 1) * 1.5}rem`;

const rowEls = new Map<string, HTMLElement>();
function setRowEl(key: string, el: unknown) {
    if (el instanceof HTMLElement) rowEls.set(key, el);
    else rowEls.delete(key);
}

// ── 끌어 놓기 ─

const dragKey = ref<string | null>(null);
const mark = ref<{ key: string; where: MenuDropWhere; allowed: boolean; reason: string | null } | null>(null);
const live = ref('');

const nameOf = (key: string | null) => props.rows.find((row) => row.key === key)?.name ?? '';

const hint = computed(() => {
    if (!mark.value) return t('menuConfig.dragHint');
    if (!mark.value.allowed) return mark.value.reason ?? '';
    return t(`menuConfig.drop.${mark.value.where}`, { name: nameOf(mark.value.key) });
});

function onStart(event: DragEvent, row: T) {
    dragKey.value = row.key;
    mark.value = null;
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', row.key);
        const el = rowEls.get(row.key);
        if (el) event.dataTransfer.setDragImage(el, 16, 16);
    }
}

function onOver(event: DragEvent, row: T) {
    if (dragKey.value === null) return;
    event.preventDefault();
    if (row.key === dragKey.value) {
        mark.value = null;
        if (event.dataTransfer) event.dataTransfer.dropEffect = 'none';
        return;
    }
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const ratio = (event.clientY - box.top) / Math.max(box.height, 1);
    let where: MenuDropWhere;
    if (row.container) where = ratio < 0.3 ? 'BEFORE' : ratio > 0.7 ? 'AFTER' : 'INSIDE';
    else where = ratio < 0.5 ? 'BEFORE' : 'AFTER';
    if (mark.value?.key === row.key && mark.value.where === where) {
        if (event.dataTransfer) event.dataTransfer.dropEffect = mark.value.allowed ? 'move' : 'none';
        return;
    }
    const reason = props.check(dragKey.value, { drop: { key: row.key, where } });
    mark.value = { key: row.key, where, allowed: reason === null, reason };
    if (event.dataTransfer) event.dataTransfer.dropEffect = reason === null ? 'move' : 'none';
}

function onDrop(event: DragEvent, row: T) {
    event.preventDefault();
    const key = dragKey.value;
    const target = mark.value;
    reset();
    if (key === null || !target || target.key !== row.key || !target.allowed) return;
    emit('move', key, { drop: { key: target.key, where: target.where } });
    live.value = t('menuConfig.moved', { name: nameOf(key) });
}

function reset() {
    dragKey.value = null;
    mark.value = null;
}

// ── 키보드 · 줄 메뉴 ─

const KEY_OPS: Record<string, MenuMoveOp> = { ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'OUT', ArrowRight: 'IN' };

function runOp(key: string, op: MenuMoveOp) {
    const reason = props.check(key, { op });
    if (reason) {
        live.value = reason;
        return;
    }
    emit('move', key, { op });
    live.value = t('menuConfig.moved', { name: nameOf(key) });
    void nextTick(() => document.getElementById(handleId(key))?.focus());
}

function onKey(event: KeyboardEvent, row: T) {
    if (!event.altKey) return;
    const op = KEY_OPS[event.key];
    if (!op) return;
    event.preventDefault();
    runOp(row.key, op);
}

const moveMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const menuKey = ref<string | null>(null);

const menuItems = computed<MenuItem[]>(() => {
    const key = menuKey.value;
    if (!key) return [];
    const ops: { op: MenuMoveOp; icon: string }[] = [
        { op: 'UP', icon: 'pi pi-arrow-up' },
        { op: 'DOWN', icon: 'pi pi-arrow-down' },
        { op: 'OUT', icon: 'pi pi-arrow-left' },
        { op: 'IN', icon: 'pi pi-arrow-right' },
    ];
    return ops.map(({ op, icon }) => ({ label: t(`menuConfig.op.${op}`), icon, disabled: props.check(key, { op }) !== null, command: () => runOp(key, op) }));
});

function openMenu(event: MouseEvent, row: T) {
    menuKey.value = row.key;
    moveMenu.value?.toggle(event);
}
</script>
