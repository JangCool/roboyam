<template>
    <!--
        홈 격자 — 운영자가 관리자 홈 배치 편집기에서 정한 배치(테마마다 칸 수 12·24 + 가젯마다 x·y·w·h, 공유 레이어 `UtilHomeLayout`)대로 가젯을 그린다. 모든 테마 홈 템플릿이 이것 하나를 쓴다(템플릿은 겉모양만).
        **라이브러리를 쓰지 않는다** — CSS grid: 칸 `repeat(N, minmax(0, 1fr))` · 행 `grid-auto-rows` 15px · 간격 16px(편집기와 같은 고정값), 가젯은 `grid-column: x+1 / span w` · `grid-row: y+1 / span 행`.
        모양은 홈 자기 폭과 창 폭으로 고른다(`UtilHomeGrid.mode`):
          · `full` 데스크톱(lg 이상) + 홈 폭 52rem 이상 — 운영자 배치 그대로
          · `half` 태블릿(md~lg) 또는 사이드 메뉴 때문에 홈 폭이 좁은 데스크톱 — 칸 수 반, 반 폭 이하 가젯 둘은 나란히·나머지는 전체 폭(`UtilHomeGrid.half`)
          · `stack` 모바일(md 미만) 또는 홈 폭 36rem 미만 — 한 열, 간격은 테마 역할 변수 `--gadget-gap`(카드가 화면 끝까지 닿는 모바일 모양 그대로).
            순서는 템플릿의 한 열 모양(`mobile.order`): `reading` (y, x) · `columns` 본문 열 먼저(`UtilHomeGrid.byColumns`). 한 열인지는 아래 가젯에 알린다(`provideHomeGadgetStacked` — 가젯의 한 열 모양: 가로로 미는 줄 등)
        **h 는 최소 높이**: 내용이 길면 칸이 늘어난다 — 카드 내용 높이(ResizeObserver)로 차지할 행을 늘리고 위로 당겨(`UtilHomeGrid.place`) 같은 열 아래 가젯만 밀린다(겹치지 않는다).
          칸은 차지한 행을 끝까지 차지하지만(`min-height` = 차지한 행 — 다음 행 가젯이 그 아래에서 시작한다) **카드(상자)는 늘리지 않는다 — 상자 높이 = 내용 높이**(사용자 결정 2026-10-31).
          운영자 h 가 내용보다 크거나 행 올림(31px 단위)으로 남는 칸은 상자 밖 바탕으로 남는다 — 상자 안에 빈 공간이 생기지 않는다(위·아래·좌·우 안 여백이 늘 같다).
        가젯의 PC/모바일 노출(`desktop`·`mobile`, lg 경계)·비어서 감춤은 `UtilHomeGadget.isVisible` 그대로이고, 숨긴 가젯의 자리는 당겨 메운다. 요소 순서는 읽는 순서((y, x))라 화면 읽기·Tab 순서가 보이는 순서와 같다.
        같은 행에 같은 h 로 나란히 둔 가젯은 그중 가장 긴 가젯 높이만큼 **칸**을 함께 잡는다(`UtilHomeGrid.place` 「같은 줄 맞추기」 — 다음 행이 그 행에서 가장 긴 것 아래에서 시작) — 상자 아래 끝은 내용대로 달라진다. 데이터는 받기만 한다(조회는 페이지).
        **관리자 미리보기 편집**(`useAdminPreview().editing` — 관리자 테마 화면의 iframe 안, 사용자 결정 2026-10-28: 편집도 실제 화면에서): 가젯마다 위에 덮는 손잡이 층을 얹는다 —
          가운데를 끌면 옮기기, 오른쪽 아래 모서리를 끌면 크기, 오른쪽 위 ×는 빼기, 그 옆 ⚙(8종 전부 — 내용·모양·노출)는 관리자 설정 창 열기(`settings`). 끄는 동안 놓일 칸을 이 격자의 칸·행 크기로 계산해 자리 표시를 그리고, 놓으면 관리자에게 알린다
          (`postToAdminPreview` — 놓은 칸 `place` · 칸·행 `size` · `remove`). 실제 계산(최소 크기·겹침·위로 당기기)과 저장은 관리자 편집기가 하고, 바뀐 배치를 다시 보내면 이 격자가 그린다.
          편집 중에는 PC 에서 숨는 가젯(모바일만)도 흐리게 그린다(자리를 고칠 수 있게). 공개 화면(편집 아님)에는 아무 영향이 없다.
          편집 중에는 칸마다 옅은 점선 윤곽(예약된 칸)을 늘 그려 상자(내용 높이)와 구분한다. 크기 꼬리표(`w×h`)는 내용에 필요한 행 수(잰 내용 높이 → `UtilHomeLayout.rowsFor`)와 비교해
          운영자 h 가 작으면 빨강 「더 줄이면 내용이 잘려요 — 최소 n칸」, 크면 주황 「n칸 줄여도 돼요」(상자 밖 바탕이 남는다)를 늘 보인다 — 모서리를 끄는 동안은 놓일 h 로 실시간(`UtilHomeGrid.sizeHint`). 저장은 막지 않는다.
        **게시판 레이아웃**(2026-10-30, `BoardLayoutGrid` 가 `provideBoardLayoutBody` 로 본문을 넘길 때만): 고정 칸 「게시판 본문」(`BOARD_BODY`) 자리에 게시판 목록(·글 보기) 화면 내용을 그대로 그린다(높이는 내용대로).
          한 열에서는 본문을 먼저(본문 위에 통째로 놓인 가젯은 그 위 그대로 — `UtilHomeGrid.bodyFirst`). 편집 손잡이는 옮기기·크기만(× · ⚙ 없음).
    -->
    <div ref="root" class="min-w-0" :class="[mode === 'stack' ? 'gadget-stack' : 'grid items-start', editing ? 'relative' : '']" :style="gridStyle" :data-grid-mode="mode" data-testid="home-grid">
        <div
            v-for="cell in cells"
            :key="cell.gadget.id"
            :ref="(el) => bind(cell.gadget.id, el)"
            :data-gadget-id="cell.gadget.id"
            class="flex min-w-0 flex-col empty:hidden"
            :class="editing ? ['relative', drag?.id === cell.gadget.id && drag.kind === 'move' ? 'z-20 opacity-80' : '', cell.gadget.desktop ? '' : 'opacity-50'] : ''"
            :style="[cellStyle(cell), dragStyle(cell)]"
        >
            <!-- 게시판 레이아웃의 고정 칸 — 게시판 목록(·글 보기) 화면 내용 그대로(`provideBoardLayoutBody`) -->
            <div v-if="cell.gadget.type === BOARD_BODY" class="min-w-0" data-testid="board-layout-body"><BodyOutlet /></div>
            <HomeGadgetRenderer v-else :gadget="cell.gadget" />
            <!-- 관리자 미리보기 편집 손잡이 층 — 가운데 끌기 = 옮기기 · 오른쪽 아래 = 크기 · 오른쪽 위 × = 빼기 · ⚙ = 내용 설정(게시판 본문은 옮기기·크기만) -->
            <div
                v-if="editing"
                data-home-edit
                class="home-edit absolute inset-0 z-10 cursor-move select-none"
                :class="drag?.id === cell.gadget.id ? 'home-edit-on' : ''"
                :data-size="hintOf(cell)?.kind"
                @pointerdown="onDown($event, cell, 'move')"
            >
                <span class="home-edit-badge" :data-size="hintOf(cell)?.kind" data-testid="home-edit-badge">
                    <template v-if="cell.gadget.type === BOARD_BODY">{{ t('home.editBoardBody') }} · </template>{{ t('home.editSize', sizeOf(cell))
                    }}<template v-if="!cell.gadget.desktop"> · {{ t('home.editMobileOnly') }}</template>
                    <template v-if="hintOf(cell)"> · {{ hintOf(cell)!.kind === 'small' ? t('home.editTooSmall', { n: hintOf(cell)!.need }) : t('home.editTooLarge', { n: hintOf(cell)!.spare }) }}</template>
                </span>
                <button
                    v-if="UtilAdminPreview.settable(cell.gadget.type)"
                    type="button"
                    class="home-edit-remove home-edit-settings"
                    :aria-label="t('home.editSettings', { title: cell.gadget.title })"
                    @pointerdown.stop
                    @click.stop="openSettings(cell.gadget.id)"
                >
                    <i class="pi pi-cog text-xs" aria-hidden="true" />
                </button>
                <button
                    v-if="UtilAdminPreview.removable(cell.gadget.type)"
                    type="button"
                    class="home-edit-remove"
                    :aria-label="t('home.editRemove', { title: cell.gadget.title })"
                    @pointerdown.stop
                    @click.stop="remove(cell.gadget.id)"
                >
                    <i class="pi pi-times text-xs" aria-hidden="true" />
                </button>
                <span class="home-edit-resize" :aria-label="t('home.editResize')" @pointerdown.stop="onDown($event, cell, 'size')" />
            </div>
        </div>
        <!-- 끄는 동안 놓일 자리 -->
        <div v-if="editing && drag && ghost" class="home-edit-ghost pointer-events-none absolute" :style="ghost" aria-hidden="true" />
    </div>
</template>

<script setup lang="ts">
import type { ComponentPublicInstance, CSSProperties, FunctionalComponent } from 'vue';
import type { HomeLayout, HomeLayoutItem } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import type { HomeGridCell, HomeGridSizeHint } from '~/utils/util-home-grid';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilHomeGrid from '~/utils/util-home-grid';
import UtilAdminPreview, { ADMIN_PREVIEW_LAYOUT, BOARD_BODY } from '~/utils/util-admin-preview';

interface Props {
    /** 운영자 배치(읽은 값 — `UtilHomeGadget.sanitize`) */
    layout: HomeLayout;
    /** 가젯 목록 */
    gadgets: HomeGadget[];
}

const props = withDefaults(defineProps<Props>(), {});

interface Cell extends HomeGridCell {
    gadget: HomeGadget;
}

const { t } = useI18n();
const { wide } = useHomeGadgetShared();
const { editing } = useAdminPreview();
const md = useMediaQuery(UtilHomeGrid.MD_QUERY);
/** 게시판 레이아웃이면 본문 그리기(밖이면 null — 본문 칸은 그리지 않는다) */
const boardBody = useBoardLayoutBody();
/** 본문 칸 내용 — 받은 슬롯을 그대로 그린다 */
const BodyOutlet: FunctionalComponent = () => boardBody?.() ?? null;

const root = ref<HTMLElement | null>(null);
/** 홈 자기 폭(px) — 아직 못 쟀으면 0(창 폭으로만 고른다) */
const width = ref(0);
/** 가젯 ID → 잰 칸 높이(px) */
const heights = ref<Record<string, number>>({});

const mode = computed(() => UtilHomeGrid.mode({ wide: wide.value, md: md.value, width: width.value }));
const mobileOrder = useHomeGadgetLookOptions().mobile.order;
provideHomeGadgetStacked(computed(() => mode.value === 'stack'));
const columns = computed(() => (mode.value === 'half' ? props.layout.columns / 2 : props.layout.columns));

/** 이 폭에서 그릴 가젯의 운영자 자리 */
const visibleItems = computed(() => {
    // 관리자 미리보기 편집 중에는 PC 에서 숨는 가젯도 그린다(흐리게 — 자리를 고칠 수 있게)
    // 게시판 본문은 게시판 레이아웃 안에서 늘 그린다(아는 가젯 종류가 아니라 `isVisible` 이 거른다)
    const shown = new Set(
        props.gadgets.filter((g) => (g.type === BOARD_BODY ? Boolean(boardBody) : (editing.value && !g.empty) || UtilHomeGadget.isVisible(g, wide.value))).map((g) => g.id),
    );
    return props.layout.items.filter((it) => shown.has(it.gadgetId));
});

const cells = computed<Cell[]>(() => {
    const byId = new Map(props.gadgets.map((g) => [g.id, g]));
    const items = mode.value === 'half' ? UtilHomeGrid.half(visibleItems.value, props.layout.columns) : visibleItems.value;
    const placed = mode.value === 'stack' ? stackOrder(items).map((it) => ({ ...it, rows: it.h })) : UtilHomeGrid.place(items, heights.value);
    return placed.flatMap((c) => {
        const gadget = byId.get(c.gadgetId);
        return gadget ? [{ ...c, gadget }] : [];
    });
});

/** 한 열 순서 — 템플릿이 고른다(기본 (y, x)). 게시판 레이아웃이면 본문을 먼저(본문 위에 통째로 놓인 가젯은 그 위 그대로 — `UtilHomeGrid.bodyFirst`) */
function stackOrder(items: readonly HomeLayoutItem[]): HomeLayoutItem[] {
    const order = mobileOrder === 'columns' ? UtilHomeGrid.byColumns(items, props.layout.columns) : UtilHomeLayout.byReadingOrder(items);
    const body = boardBody ? props.gadgets.find((g) => g.type === BOARD_BODY) : undefined;
    return body ? UtilHomeGrid.bodyFirst(order, body.id) : order;
}

const gridStyle = computed<CSSProperties | undefined>(() =>
    mode.value === 'stack'
        ? undefined
        : {
              gridTemplateColumns: `repeat(${columns.value}, minmax(0, 1fr))`,
              gridAutoRows: `${UtilHomeLayout.ROW_HEIGHT}px`,
              gap: `${UtilHomeLayout.GAP}px`,
          },
);

function cellStyle(cell: Cell): CSSProperties | undefined {
    if (mode.value === 'stack') return undefined;
    return {
        gridColumn: `${cell.x + 1} / span ${cell.w}`,
        gridRow: `${cell.y + 1} / span ${cell.rows}`,
        // 차지한 행을 끝까지 채운다 — 카드 아래 끝이 행 경계에 붙어 아래 가젯과의 간격이 늘 16px 이다(내용 높이에서 멈추면 행 올림만큼 최대 30px 가 더 벌어진다).
        // 같은 줄 맞추기로 함께 늘린 가젯도 그래서 아래 끝이 맞는다
        minHeight: `${UtilHomeLayout.px(cell.rows)}px`,
    };
}

/* ── 관리자 미리보기 편집 — 끌어 옮기기 · 모서리로 크기 · 빼기(계산·저장은 관리자) ── */

interface Drag {
    id: string;
    kind: 'move' | 'size';
    /** 끌기 시작한 칸(운영자 자리) */
    cell: Cell;
    startX: number;
    startY: number;
    dx: number;
    dy: number;
    /** 시작할 때 칸의 격자 안 위치·크기(px) */
    left: number;
    top: number;
    width: number;
    height: number;
    /** 놓일 자리(칸·행) */
    target: { x: number; y: number; w: number; h: number };
}

const drag = ref<Drag | null>(null);
/** 한 칸·한 행의 걸음(px) — 칸 폭 + 간격 · 행 높이 + 간격 */
function steps(): { colW: number; unitX: number; unitY: number } {
    const width = root.value?.clientWidth ?? 0;
    const colW = (width - UtilHomeLayout.GAP * (columns.value - 1)) / columns.value;
    return { colW, unitX: colW + UtilHomeLayout.GAP, unitY: UtilHomeLayout.ROW_HEIGHT + UtilHomeLayout.GAP };
}

function onDown(event: PointerEvent, cell: Cell, kind: 'move' | 'size'): void {
    if (event.button !== 0 || mode.value === 'stack') return;
    const el = observed.get(cell.gadget.id) as HTMLElement | undefined;
    const box = root.value?.getBoundingClientRect();
    if (!el || !box) return;
    event.preventDefault();
    const rect = el.getBoundingClientRect();
    drag.value = {
        id: cell.gadget.id,
        kind,
        cell,
        startX: event.clientX,
        startY: event.clientY,
        dx: 0,
        dy: 0,
        left: rect.left - box.left,
        top: rect.top - box.top,
        width: rect.width,
        height: rect.height,
        target: { x: cell.x, y: cell.y, w: cell.w, h: cell.h },
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp, { once: true });
}

function onMove(event: PointerEvent): void {
    const d = drag.value;
    if (!d) return;
    d.dx = event.clientX - d.startX;
    d.dy = event.clientY - d.startY;
    const { unitX, unitY } = steps();
    const cols = columns.value;
    if (d.kind === 'move') {
        const x = Math.min(cols - d.cell.w, Math.max(0, Math.round((d.left + d.dx) / unitX)));
        const y = Math.max(0, Math.round((d.top + d.dy) / unitY));
        d.target = { x, y, w: d.cell.w, h: d.cell.h };
    } else {
        const w = Math.min(cols - d.cell.x, Math.max(1, Math.round((d.width + d.dx + UtilHomeLayout.GAP) / unitX)));
        const h = Math.min(UtilHomeLayout.MAX_H, Math.max(1, Math.round((d.height + d.dy + UtilHomeLayout.GAP) / unitY)));
        d.target = { x: d.cell.x, y: Math.round(d.top / unitY), w, h };
    }
}

function onUp(): void {
    window.removeEventListener('pointermove', onMove);
    const d = drag.value;
    drag.value = null;
    if (!d) return;
    const moved = Math.abs(d.dx) > 3 || Math.abs(d.dy) > 3;
    if (!moved) return;
    if (d.kind === 'move') {
        if (d.target.x !== d.cell.x || d.target.y !== d.cell.y) postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'place', id: d.id, x: d.target.x, y: d.target.y });
    } else if (d.target.w !== d.cell.w || d.target.h !== d.cell.h) {
        postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'size', id: d.id, w: d.target.w, h: d.target.h });
    }
}

/** 꼬리표에 보일 크기 — 모서리를 끄는 중이면 놓일 크기(실시간), 아니면 운영자 크기 */
function sizeOf(cell: Cell): { w: number; h: number } {
    const d = drag.value;
    return d && d.id === cell.gadget.id && d.kind === 'size' ? { w: d.target.w, h: d.target.h } : { w: cell.w, h: cell.h };
}

/** 운영자 h 와 내용에 필요한 행 수 비교(편집 꼬리표 색·문구) — 아직 못 잰 가젯·게시판 본문(보는 게시판마다 길이가 다르다)은 없음 */
function hintOf(cell: Cell): HomeGridSizeHint | null {
    if (cell.gadget.type === BOARD_BODY) return null;
    return UtilHomeGrid.sizeHint(sizeOf(cell).h, heights.value[cell.gadget.id]);
}

function remove(id: string): void {
    postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'remove', id });
}

/** ⚙ — 관리자 설정 창 열기(창은 관리자 응답 값으로 채운다) */
function openSettings(id: string): void {
    postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'settings', id });
}

/** 옮기는 칸은 손가락을 따라간다 */
function dragStyle(cell: Cell): CSSProperties | undefined {
    const d = drag.value;
    if (!d || d.id !== cell.gadget.id || d.kind !== 'move') return undefined;
    return { transform: `translate(${d.dx}px, ${d.dy}px)` };
}

/** 놓일 자리 표시(격자 안 px) */
const ghost = computed<CSSProperties | null>(() => {
    const d = drag.value;
    if (!d) return null;
    const { colW, unitX, unitY } = steps();
    const { x, y, w, h } = d.target;
    const height = d.kind === 'move' ? d.height : Math.max(UtilHomeLayout.px(h), d.height + d.dy);
    return { left: `${x * unitX}px`, top: `${y * unitY}px`, width: `${w * colW + (w - 1) * UtilHomeLayout.GAP}px`, height: `${d.kind === 'move' ? height : UtilHomeLayout.px(h)}px` };
});

onBeforeUnmount(() => window.removeEventListener('pointermove', onMove));

/* ── 높이 재기 — 칸 높이가 아니라 가젯 카드의 내용 높이(카드는 늘지 않아 카드 높이와 같다 — 편집 꼬리표의 크기 알림도 이 값을 쓴다)(카드 자식들의 아래 끝 + 아래 안 여백)를 잰다 → 행을 늘리는 일이 되먹임하지 않고, 내용이 줄면 행도 준다.
       칸 안에서 내용만 줄거나 늘면 칸 크기는 그대로라 카드의 자식(머리·내용·더보기)도 함께 지켜본다 ── */

const observed = new Map<string, Element>();
let cellObserver: ResizeObserver | null = null;
let rootObserver: ResizeObserver | null = null;

/** 가젯 카드(칸의 첫 자식 — `HomeGadgetFrame` 의 section, 블록 흐름이라 자식은 늘어나지 않는다)의 내용 높이(px) */
function contentHeight(cell: HTMLElement): number {
    const card = cell.firstElementChild as HTMLElement | null;
    if (!card || card.hasAttribute('data-home-edit')) return 0;
    // 자식들의 위·아래 끝으로 잰다 — 카드 위 끝에서 재면, 내용을 세로 가운데에 두는 카드(머리 숨김 — `HomeGadgetFrame`)는 늘어난 만큼 위 빈 곳까지 내용으로 쳐서 행이 계속 는다
    let top = Number.POSITIVE_INFINITY;
    let bottom = Number.NEGATIVE_INFINITY;
    for (const child of card.children) {
        const r = child.getBoundingClientRect();
        if (r.height === 0) continue;
        const ccs = getComputedStyle(child);
        top = Math.min(top, r.top - (parseFloat(ccs.marginTop) || 0));
        bottom = Math.max(bottom, r.bottom + (parseFloat(ccs.marginBottom) || 0));
    }
    const cs = getComputedStyle(card);
    const frame = (parseFloat(cs.paddingTop) || 0) + (parseFloat(cs.borderTopWidth) || 0) + (parseFloat(cs.paddingBottom) || 0) + (parseFloat(cs.borderBottomWidth) || 0);
    if (!Number.isFinite(top)) return frame;
    return bottom - top + frame;
}

/** 카드의 자식도 지켜본다(이미 보는 것은 건너뛴다) */
const watchedChildren = new WeakSet<Element>();
function watchChildren(cell: Element): void {
    const card = cell.firstElementChild;
    if (!card || !cellObserver) return;
    for (const child of card.children) {
        if (watchedChildren.has(child)) continue;
        watchedChildren.add(child);
        cellObserver.observe(child);
    }
}

function bind(id: string, el: Element | ComponentPublicInstance | null): void {
    const next = el instanceof Element ? el : null;
    const prev = observed.get(id) ?? null;
    if (prev === next) return;
    if (prev) {
        cellObserver?.unobserve(prev);
        observed.delete(id);
    }
    if (next) {
        observed.set(id, next);
        cellObserver?.observe(next);
    }
}

onMounted(() => {
    if (typeof ResizeObserver === 'undefined') return;
    cellObserver = new ResizeObserver((entries) => {
        const ids = new Set<string>();
        for (const entry of entries) {
            const id = (entry.target.closest('[data-gadget-id]') as HTMLElement | null)?.dataset.gadgetId;
            if (id) ids.add(id);
        }
        let changed = false;
        const nextHeights = { ...heights.value };
        for (const id of ids) {
            const cell = observed.get(id) as HTMLElement | undefined;
            if (!cell) continue;
            watchChildren(cell);
            const px = Math.ceil(contentHeight(cell));
            if (px > 0 && nextHeights[id] !== px) {
                nextHeights[id] = px;
                changed = true;
            }
        }
        if (changed) heights.value = nextHeights;
    });
    for (const el of observed.values()) cellObserver.observe(el);

    rootObserver = new ResizeObserver((entries) => {
        const w = entries[0]?.contentRect.width ?? 0;
        if (Math.round(w) !== width.value) width.value = Math.round(w);
    });
    if (root.value) rootObserver.observe(root.value);
});

onBeforeUnmount(() => {
    cellObserver?.disconnect();
    rootObserver?.disconnect();
    cellObserver = null;
    rootObserver = null;
    observed.clear();
});
</script>

<style scoped>
/* 관리자 미리보기 편집 손잡이 — 올리면 주 색상 점선 테두리, 끄는 칸은 실선. 색은 주 색상 토큰만 */
/* 예약된 칸 — 편집 중 늘 옅은 점선 윤곽(상자는 내용 높이라 칸보다 짧을 수 있다 — 칸과 상자를 구분해 보이게) */
.home-edit {
    border-radius: var(--gadget-card-radius, 0.75rem);
    outline: 2px dashed color-mix(in srgb, var(--p-primary-color) 28%, transparent);
    outline-offset: -2px;
    transition: outline-color 0.12s, background-color 0.12s;
}
.home-edit:hover,
.home-edit-on {
    outline-color: var(--p-primary-color);
    background-color: color-mix(in srgb, var(--p-primary-color) 4%, transparent);
}
.home-edit-on {
    outline-style: solid;
}
.home-edit-badge {
    position: absolute;
    left: 0.5rem;
    bottom: 0.5rem;
    padding: 0.125rem 0.5rem;
    border-radius: 9999px;
    font-size: 0.6875rem;
    font-weight: 600;
    color: var(--p-primary-contrast-color);
    background-color: var(--p-primary-color);
    max-width: calc(100% - 2.5rem);
    opacity: 0;
    transition: opacity 0.12s;
}
.home-edit-remove {
    position: absolute;
    top: 0.5rem;
    right: 0.5rem;
    display: grid;
    place-items: center;
    width: 1.75rem;
    height: 1.75rem;
    border-radius: 9999px;
    color: var(--p-primary-contrast-color);
    background-color: var(--p-primary-color);
    cursor: pointer;
    opacity: 0;
    transition: opacity 0.12s;
}
.home-edit-settings {
    right: 2.75rem;
}
.home-edit-resize {
    position: absolute;
    right: 0.25rem;
    bottom: 0.25rem;
    width: 1rem;
    height: 1rem;
    border-right: 3px solid var(--p-primary-color);
    border-bottom: 3px solid var(--p-primary-color);
    border-bottom-right-radius: 0.25rem;
    cursor: nwse-resize;
    opacity: 0;
    transition: opacity 0.12s;
}
.home-edit:hover > *,
.home-edit-on > * {
    opacity: 1;
}
.home-edit-ghost {
    z-index: 15;
    border: 2px dashed var(--p-primary-color);
    border-radius: var(--gadget-card-radius, 0.75rem);
    background-color: color-mix(in srgb, var(--p-primary-color) 12%, transparent);
}
/* (아래에 둔다 — 올림·끄는 중 규칙보다 이겨 색이 늘 남게) */
/* 크기 알림 — 너무 작음(빨강: 운영자 h < 내용에 필요한 행) · 너무 큼(주황: 남는 칸). 칸 윤곽과 꼬리표를 그 색으로, 꼬리표는 늘 보인다 */
.home-edit[data-size='small'] {
    outline-color: var(--p-red-500);
}
.home-edit[data-size='large'] {
    outline-color: var(--p-amber-500);
}
.home-edit-badge[data-size='small'] {
    background-color: var(--p-red-600);
    color: #fff;
    opacity: 1;
}
.home-edit-badge[data-size='large'] {
    background-color: var(--p-amber-400);
    color: var(--p-surface-900);
    opacity: 1;
}
</style>
