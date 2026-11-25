<template>
    <!--
        홈 격자 — 운영자가 관리자 홈 배치 편집기에서 정한 배치(테마마다 칸 수 12·24 + 가젯마다 x·y·w·h, 공유 레이어 `UtilHomeLayout`)대로 가젯을 그린다. 모든 테마 홈 템플릿이 이것 하나를 쓴다(템플릿은 겉모양만).
        **라이브러리를 쓰지 않는다** — 가로는 칸 수로 나눈 비율(간격 16px — 편집기와 같은 고정값), 세로는 칸마다 절대 위치(`UtilGadgetGrid.place` 의 위 끝·높이 px). 한 열(`stack`)만 흐름 배치.
        모양은 홈 자기 폭과 창 폭으로 고른다(`UtilGadgetGrid.mode`):
          · `full` 데스크톱(lg 이상) + 홈 폭 52rem 이상 — 운영자 배치 그대로
          · `half` 태블릿(md~lg) 또는 사이드 메뉴 때문에 홈 폭이 좁은 데스크톱 — 칸 수 반, 반 폭 이하 가젯 둘은 나란히·나머지는 전체 폭(`UtilGadgetGrid.half`)
          · `stack` 모바일(md 미만) 또는 홈 폭 36rem 미만 — 한 열, 간격은 테마 역할 변수 `--gadget-gap`(카드가 화면 끝까지 닿는 모바일 모양 그대로).
            순서는 템플릿의 한 열 모양(`mobile.order`): `reading` (y, x) · `columns` 본문 열 먼저 · `beside` 오른쪽 열을 본문 열 사이에 끼워 넣기(공유 레이어 `UtilHomeStack` — 관리자 편집기의 「모바일 순서」 목록과 같은 계산).
            운영자가 모바일 순서를 따로 정했으면(배치 `mobileOrder`, home-news-contract 「모바일 순서」) 그 차례가 먼저이고, 목록에 없는 가젯은 위 계산 차례대로 뒤에 붙는다. 한 열인지는 아래 가젯에 알린다(`provideGadgetStacked` — 가젯의 한 열 모양: 가로로 미는 줄 등)
        **px 배치**: 칸 높이 = `UtilGadgetGrid.boxHeight`(운영자 h 의 px 가 잰 내용보다 한 칸(31px) 이상 크면 그 px, 아니면 내용 높이), 위 끝 = 열이 겹치는 앞선 가젯 아래 끝 + 16px(`place`). 게시판 본문 칸은 내용 높이만.
        가젯의 PC/모바일 노출(`desktop`·`mobile`, lg 경계)·비어서 감춤은 `UtilGadget.isVisible` 그대로이고, 숨긴 가젯의 자리는 당겨 메운다. 요소 순서는 읽는 순서((y, x))라 화면 읽기·Tab 순서가 보이는 순서와 같다.
        같은 y·같은 h 로 나란히 둔 가젯은 상자 높이 중 가장 큰 값으로 함께 맞추고 위 끝도 맞춘다(「같은 줄 맞추기」 — 짧은 열의 맨 아래 가젯은 늘려 붙인다). 데이터는 받기만 한다(조회는 페이지).
        **관리자 미리보기 편집**(`useAdminPreview().editing` — 관리자 테마 화면의 iframe 안, 사용자 결정 2026-10-28: 편집도 실제 화면에서): 가젯마다 위에 덮는 손잡이 층을 얹는다 —
          가운데를 끌면 옮기기, 오른쪽 아래 모서리를 끌면 크기, 오른쪽 위 ×는 빼기, 그 옆 ⚙(8종 전부 — 내용·모양·노출)는 관리자 설정 창 열기(`settings`). 끄는 동안 놓일 칸을 이 격자의 칸·행 크기로 계산해 자리 표시를 그리고, 놓으면 관리자에게 알린다
          (`postToAdminPreview` — 놓은 칸 `place` · 칸·행 `size` · `remove`). 실제 계산(최소 크기·겹침·위로 당기기)과 저장은 관리자 편집기가 하고, 바뀐 배치를 다시 보내면 이 격자가 그린다.
          편집 중에도 기기 노출은 공개 화면과 같다 — PC 미리보기에 「모바일만」 가젯은 그리지 않는다(2026-11-25 사용자 결정, 모바일 가젯은 「모바일」 미리보기에서 고친다). 공개 화면(편집 아님)에는 아무 영향이 없다.
          편집 중에는 칸마다 옅은 점선 윤곽을 그리고, 크기 꼬리표는 최적 칸 수보다 작으면 빨강·크면 주황 알림을 늘 보인다(`UtilGadgetGrid.sizeHint` — 저장은 막지 않는다).
          **한 열(관리자 「모바일」 미리보기, 사용자 2026-11-15 — PC 처럼 실제 화면에서)**: 모바일에 보이는 가젯만 그리고, 같은 점선 손잡이 층에서 위아래로 끌면 놓을 자리 줄을 보이고 놓으면 `order`(그 가젯 앞 `before`, 맨 뒤 null)를 알린다.
          ↑·↓ 단추로 한 칸씩도 옮기고 ⚙ · × 는 PC 와 같다. 크기 손잡이는 없다(크기는 PC 배치). 꼬리표는 「모바일 n번째」.
        **게시판 레이아웃**(2026-10-30, `BoardLayoutGrid` 가 `provideBoardLayoutBody` 로 본문을 넘길 때만): 고정 칸 「게시판 본문」(`BOARD_BODY`) 자리에 게시판 목록(·글 보기) 화면 내용을 그대로 그린다(높이는 내용대로).
          한 열에서는 본문을 먼저(본문 위에 통째로 놓인 가젯은 그 위 그대로 — `UtilHomeStack.bodyFirst`, 모바일 순서를 정했으면 그 차례가 먼저). 편집 손잡이는 옮기기·크기만(× · ⚙ 없음).
    -->
    <div ref="root" class="min-w-0" :class="mode === 'stack' ? ['gadget-stack', editing ? 'relative' : ''] : 'relative'" :style="gridStyle" :data-grid-mode="mode" data-testid="home-grid">
        <div
            v-for="(cell, index) in cells"
            :key="cell.gadget.id"
            :ref="(el) => bind(cell.gadget.id, el)"
            :data-gadget-id="cell.gadget.id"
            :data-cache-area="UtilCacheArea.gadgetArea(cell.gadget.type)"
            :data-grid-stretched="stretchedOf(cell)"
            :data-grid-height="mode === 'stack' ? undefined : Math.round(cell.height)"
            class="flex min-w-0 flex-col empty:hidden [&>*]:grow"
            :class="editing ? ['relative', drag?.id === cell.gadget.id && drag.kind !== 'size' ? 'z-20 opacity-80' : ''] : ''"
            :style="[cellStyle(cell), dragStyle(cell)]"
        >
            <!-- 게시판 레이아웃의 고정 칸 — 게시판 목록(·글 보기) 화면 내용 그대로(`provideBoardLayoutBody`) -->
            <div v-if="cell.gadget.type === BOARD_BODY" class="min-w-0" data-testid="board-layout-body"><BodyOutlet /></div>
            <GadgetRenderer v-else :gadget="cell.gadget" />
            <!-- 관리자 미리보기 편집 손잡이 층 — 가운데 끌기 = 옮기기 · 오른쪽 아래 = 크기 · 오른쪽 위 × = 빼기 · ⚙ = 내용 설정(게시판 본문은 옮기기·크기만) -->
            <div
                v-if="editing"
                data-home-edit
                class="home-edit absolute inset-0 z-10 cursor-move select-none"
                :class="drag?.id === cell.gadget.id ? 'home-edit-on' : ''"
                :data-size="hintOf(cell)?.kind"
                @pointerdown="onDown($event, cell, 'move')"
            >
                <span v-if="mode === 'stack'" class="home-edit-badge" data-testid="home-edit-badge">
                    <template v-if="cell.gadget.type === BOARD_BODY">{{ t('home.editBoardBody') }} · </template>{{ t('home.editOrder', { n: index + 1 }) }}
                </span>
                <span v-else class="home-edit-badge" :data-size="hintOf(cell)?.kind" data-testid="home-edit-badge">
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
                <template v-if="mode === 'stack'">
                    <!-- 한 열 — 한 칸씩 위·아래(끌기와 같은 `order`) -->
                    <button
                        type="button"
                        class="home-edit-remove home-edit-up"
                        :disabled="index === 0"
                        :aria-label="t('home.editMoveUp', { title: cell.gadget.title })"
                        @pointerdown.stop
                        @click.stop="step(index, -1)"
                    >
                        <i class="pi pi-arrow-up text-xs" aria-hidden="true" />
                    </button>
                    <button
                        type="button"
                        class="home-edit-remove home-edit-down"
                        :disabled="index === cells.length - 1"
                        :aria-label="t('home.editMoveDown', { title: cell.gadget.title })"
                        @pointerdown.stop
                        @click.stop="step(index, 1)"
                    >
                        <i class="pi pi-arrow-down text-xs" aria-hidden="true" />
                    </button>
                </template>
                <span v-else class="home-edit-resize" :aria-label="t('home.editResize')" @pointerdown.stop="onDown($event, cell, 'size')" />
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
import UtilHomeStack from '#layers/home-layout/utils/util-home-stack';
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import type { GadgetGridCell, GadgetGridSizeHint } from '~/utils/util-gadget-grid';
import UtilGadget from '~/utils/util-gadget';
import UtilGadgetGrid from '~/utils/util-gadget-grid';
import UtilAdminPreview, { ADMIN_PREVIEW_LAYOUT, BOARD_BODY } from '~/utils/util-admin-preview';
import UtilBusiness from '~/utils/util-business';
import UtilCacheArea from '~/utils/util-cache-area';

interface Props {
    /** 운영자 배치(읽은 값 — `UtilGadget.sanitize`) */
    layout: HomeLayout;
    /** 가젯 목록 */
    gadgets: Gadget[];
}

const props = withDefaults(defineProps<Props>(), {});

interface Cell extends GadgetGridCell {
    gadget: Gadget;
}

const { t } = useI18n();
const { wide } = useGadgetShared();
/** 보는 사람 — 사업자 전용 계정이면 포인트 게임 가젯을 자리째 감춘다(`UtilGadget.hiddenForViewer`) */
const { user } = useAuth();
const viewer = computed(() => ({ businessAccount: UtilBusiness.isBusinessAccount(user.value?.role) }));
const { editing, home: previewHome } = useAdminPreview();
const md = useMediaQuery(UtilGadgetGrid.MD_QUERY);
/** 게시판 레이아웃이면 본문 그리기(밖이면 null — 본문 칸은 그리지 않는다) */
const boardBody = useBoardLayoutBody();
/** 본문 칸 내용 — 받은 슬롯을 그대로 그린다 */
const BodyOutlet: FunctionalComponent = () => boardBody?.() ?? null;

const root = ref<HTMLElement | null>(null);
/** 홈 자기 폭(px) — 아직 못 쟀으면 0(창 폭으로만 고른다) */
const width = ref(0);
/** 가젯 ID → 잰 칸 높이(px) */
const heights = ref<Record<string, number>>({});

const mode = computed(() => UtilGadgetGrid.mode({ wide: wide.value, md: md.value, width: width.value }));
/** 템플릿 한 열 모양(`reading`·`columns`·`beside`) */
const stackRule = useGadgetLookOptions().mobile.order;
provideGadgetStacked(computed(() => mode.value === 'stack'));
const columns = computed(() => (mode.value === 'half' ? props.layout.columns / 2 : props.layout.columns));

/**
 * 편집 중 이 미리보기 기기에 보이는 가젯인가 — 한 열(관리자 「모바일」)은 모바일 켬, 그 밖은 공개 화면처럼 lg 이상이면 PC 켬·아니면 모바일 켬.
 * 「모바일만」 가젯을 PC 미리보기에 흐리게 그리던 것을 없앴다(사용자 2026-11-25 — 「모바일 단추를 누르지 않았으면 PC 화면이니 모바일 가젯이 보이면 안 된다」).
 */
function editShown(g: Gadget): boolean {
    if (mode.value === 'stack') return g.mobile;
    return wide.value ? g.desktop : g.mobile;
}

/** 이 폭에서 그릴 가젯의 운영자 자리 */
const visibleItems = computed(() => {
    // 관리자 미리보기 편집 중에도 공개 화면과 같은 노출 — PC 미리보기는 PC 에 보이는 가젯만, 한 열(모바일)은 모바일에 보이는 가젯만(`editShown`).
    // 편집 중에만 더 그리는 것은 비어서 감추는 가젯뿐이다(내용이 생기면 보일 자리를 고칠 수 있게)
    // 게시판 본문은 게시판 레이아웃 안에서 늘 그린다(아는 가젯 종류가 아니라 `isVisible` 이 거른다)
    const shown = new Set(
        props.gadgets
            .filter((g) => (g.type === BOARD_BODY ? Boolean(boardBody) : (editing.value && !g.empty && editShown(g)) || UtilGadget.isVisible(g, wide.value, viewer.value)))
            .map((g) => g.id),
    );
    return props.layout.items.filter((it) => shown.has(it.gadgetId));
});

const cells = computed<Cell[]>(() => {
    const byId = new Map(props.gadgets.map((g) => [g.id, g]));
    // 모서리를 끄는 동안은 놓일 h 로 바로 그린다(아래 가젯도 따라 밀린다)
    const d = drag.value;
    const live = d && d.kind === 'size' ? visibleItems.value.map((it) => (it.gadgetId === d.id ? { ...it, h: d.target.h } : it)) : visibleItems.value;
    const items = mode.value === 'half' ? UtilGadgetGrid.half(live, props.layout.columns) : live;
    const bodyId = props.gadgets.find((g) => g.type === BOARD_BODY)?.id;
    const placed =
        mode.value === 'stack' ? stackOrder(items).map((it) => ({ ...it, top: 0, height: 0 })) : UtilGadgetGrid.place(items, heights.value, (id) => id === bodyId);
    return placed.flatMap((c) => {
        const gadget = byId.get(c.gadgetId);
        return gadget ? [{ ...c, gadget }] : [];
    });
});

/**
 * 한 열 순서 — 템플릿 한 열 모양으로 계산하고(기본 (y, x) · 게시판 레이아웃이면 본문 먼저), 운영자가 정한 모바일 순서(`layout.mobileOrder`)가 있으면 그 차례를 먼저(`UtilHomeStack.order`).
 * 관리자 미리보기면 `layout` 이 편집 중인(저장 전) 배치라 모바일 순서도 바로 따른다.
 */
function stackOrder(items: readonly HomeLayoutItem[]): HomeLayoutItem[] {
    const body = boardBody ? props.gadgets.find((g) => g.type === BOARD_BODY) : undefined;
    return UtilHomeStack.order(items, props.layout.columns, stackRule, { bodyId: body?.id ?? null, mobileOrder: props.layout.mobileOrder });
}

/** 격자 높이 — px 배치라 가장 아래 가젯의 아래 끝(한 열은 흐름대로) */
const gridStyle = computed<CSSProperties | undefined>(() => (mode.value === 'stack' ? undefined : { height: `${UtilGadgetGrid.totalHeight(cells.value)}px` }));

/** 가로는 칸 수 비율, 세로는 `UtilGadgetGrid.place` 의 px — 카드는 칸을 채운다(`[&>*]:grow`) */
function cellStyle(cell: Cell): CSSProperties | undefined {
    if (mode.value === 'stack') return undefined;
    const cols = columns.value;
    const gap = UtilHomeLayout.GAP;
    const col = `((100% - ${(cols - 1) * gap}px) / ${cols})`;
    return {
        position: 'absolute',
        top: `${cell.top}px`,
        left: `calc(${col} * ${cell.x} + ${cell.x * gap}px)`,
        width: `calc(${col} * ${cell.w} + ${(cell.w - 1) * gap}px)`,
        height: `${cell.height}px`,
    };
}

/* ── 관리자 미리보기 편집 — 끌어 옮기기 · 모서리로 크기 · 빼기(계산·저장은 관리자) ── */

interface Drag {
    id: string;
    /** `order` = 한 열(모바일)에서 위아래 차례 끌기 */
    kind: 'move' | 'size' | 'order';
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
    /** 한 열 끌기 — 놓으면 이 가젯 앞으로(null = 맨 뒤) */
    before: string | null;
}

const drag = ref<Drag | null>(null);
/** 한 칸·한 행의 걸음(px) — 칸 폭 + 간격 · 행 높이 + 간격 */
function steps(): { colW: number; unitX: number; unitY: number } {
    const width = root.value?.clientWidth ?? 0;
    const colW = (width - UtilHomeLayout.GAP * (columns.value - 1)) / columns.value;
    return { colW, unitX: colW + UtilHomeLayout.GAP, unitY: UtilHomeLayout.ROW_HEIGHT + UtilHomeLayout.GAP };
}

function onDown(event: PointerEvent, cell: Cell, kind: 'move' | 'size'): void {
    if (event.button !== 0) return;
    const stacked = mode.value === 'stack';
    if (stacked && kind === 'size') return;
    const el = observed.get(cell.gadget.id) as HTMLElement | undefined;
    const box = root.value?.getBoundingClientRect();
    if (!el || !box) return;
    event.preventDefault();
    const rect = el.getBoundingClientRect();
    drag.value = {
        id: cell.gadget.id,
        kind: stacked ? 'order' : kind,
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
        before: nextIdOf(cell.gadget.id),
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp, { once: true });
}

function onMove(event: PointerEvent): void {
    const d = drag.value;
    if (!d) return;
    d.dx = event.clientX - d.startX;
    d.dy = event.clientY - d.startY;
    if (d.kind === 'order') {
        d.dx = 0;
        d.before = orderTarget(d);
        return;
    }
    const { unitX, unitY } = steps();
    const cols = columns.value;
    if (d.kind === 'move') {
        const x = Math.min(cols - d.cell.w, Math.max(0, Math.round((d.left + d.dx) / unitX)));
        // px 배치는 행과 1:1 이 아니다 — 놓은 위치의 화면 순서로 운영자 행을 되짚는다(`UtilGadgetGrid.rowAt`)
        const y = UtilGadgetGrid.rowAt(cells.value, { id: d.id, x, w: d.cell.w, top: d.top + d.dy });
        d.target = { x, y, w: d.cell.w, h: d.cell.h };
    } else {
        const w = Math.min(cols - d.cell.x, Math.max(1, Math.round((d.width + d.dx + UtilHomeLayout.GAP) / unitX)));
        // 높이는 운영자 h(행)에서 끈 만큼 — 화면 높이는 내용대로라 h 의 px 에서 시작한다
        const h = Math.min(UtilHomeLayout.MAX_H, Math.max(1, Math.round((UtilHomeLayout.px(d.cell.h) + d.dy + UtilHomeLayout.GAP) / unitY)));
        d.target = { x: d.cell.x, y: d.cell.y, w, h };
    }
}

function onUp(): void {
    window.removeEventListener('pointermove', onMove);
    const d = drag.value;
    drag.value = null;
    if (!d) return;
    const moved = Math.abs(d.dx) > 3 || Math.abs(d.dy) > 3;
    if (!moved) return;
    if (d.kind === 'order') {
        if (d.before !== nextIdOf(d.id)) postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'order', id: d.id, before: d.before });
        return;
    }
    if (d.kind === 'move') {
        if (d.target.x !== d.cell.x || d.target.y !== d.cell.y) postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'place', id: d.id, x: d.target.x, y: d.target.y });
    } else if (d.target.w !== d.cell.w || d.target.h !== d.cell.h) {
        postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'size', id: d.id, w: d.target.w, h: d.target.h });
    }
}

/** 한 열에서 이 가젯 바로 뒤 가젯(없으면 null) — 그 앞에 놓으면 제자리 */
function nextIdOf(id: string): string | null {
    const i = cells.value.findIndex((c) => c.gadget.id === id);
    return cells.value[i + 1]?.gadget.id ?? null;
}

/** 한 열 끌기 — 끄는 카드의 가운데가 지나지 않은 첫 카드(가운데 기준) 앞, 없으면 맨 뒤 */
function orderTarget(d: Drag): string | null {
    const box = root.value?.getBoundingClientRect();
    if (!box) return d.before;
    const center = box.top + d.top + d.dy + d.height / 2;
    for (const c of cells.value) {
        if (c.gadget.id === d.id) continue;
        const el = observed.get(c.gadget.id);
        if (!el) continue;
        const r = el.getBoundingClientRect();
        if (center < r.top + r.height / 2) return c.gadget.id;
    }
    return null;
}

/** ↑·↓ — 한 칸 위(앞 가젯 앞) · 한 칸 아래(다음다음 가젯 앞, 없으면 맨 뒤) */
function step(index: number, by: -1 | 1): void {
    const id = cells.value[index]?.gadget.id;
    if (!id) return;
    if (by < 0) {
        const before = cells.value[index - 1]?.gadget.id;
        if (before) postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'order', id, before });
    } else if (index < cells.value.length - 1) {
        postToAdminPreview({ type: ADMIN_PREVIEW_LAYOUT, op: 'order', id, before: cells.value[index + 2]?.gadget.id ?? null });
    }
}

/** 칸이 내용보다 커서 늘어난 px — 칸 요소의 `data-grid-stretched`(콘솔 스크립트가 읽는다) */
function stretchedOf(cell: Cell): number | undefined {
    const content = heights.value[cell.gadget.id];
    if (mode.value === 'stack' || content === undefined) return undefined;
    const s = cell.height - content;
    return s > 0.5 ? Math.round(s) : undefined;
}

/** 모서리를 끄는 중이면 놓일 크기 */
function sizeOf(cell: Cell): { w: number; h: number } {
    const d = drag.value;
    return d && d.id === cell.gadget.id && d.kind === 'size' ? { w: d.target.w, h: d.target.h } : { w: cell.w, h: cell.h };
}

/** 게시판 본문은 보는 게시판마다 길이가 달라 알림 없음 */
function hintOf(cell: Cell): GadgetGridSizeHint | null {
    if (cell.gadget.type === BOARD_BODY) return null;
    const minH = previewHome.value?.gadgets.find((g) => g.id === cell.gadget.id)?.minH;
    return UtilGadgetGrid.sizeHint(sizeOf(cell).h, heights.value[cell.gadget.id], minH);
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
    if (!d || d.id !== cell.gadget.id || d.kind === 'size') return undefined;
    return { transform: `translate(${d.dx}px, ${d.dy}px)` };
}

/** 놓일 자리 표시(격자 안 px) */
const ghost = computed<CSSProperties | null>(() => {
    const d = drag.value;
    if (!d) return null;
    if (d.kind === 'order') return orderGhost(d);
    const { colW, unitX } = steps();
    const { x, w } = d.target;
    // 크기 끌기의 높이는 놓일 h 의 상자 높이 — 칸도 같은 높이로 바로 그린다
    const top = d.kind === 'move' ? d.top + d.dy : d.top;
    const content = heights.value[d.id];
    const height = d.kind === 'move' ? d.height : content === undefined ? UtilHomeLayout.px(d.target.h) : UtilGadgetGrid.boxHeight(d.target.h, content);
    return { left: `${x * unitX}px`, top: `${top}px`, width: `${w * colW + (w - 1) * UtilHomeLayout.GAP}px`, height: `${height}px` };
});

/** 한 열 끌기의 놓을 자리 줄 — `before` 카드 위 끝(맨 뒤면 마지막 카드 아래 끝), 카드 사이 간격 가운데 */
function orderGhost(d: Drag): CSSProperties | null {
    const box = root.value?.getBoundingClientRect();
    if (!box || !root.value) return null;
    const target = d.before ? observed.get(d.before) : observed.get(cells.value.at(-1)?.gadget.id ?? '');
    if (!target) return null;
    const r = target.getBoundingClientRect();
    const gap = parseFloat(getComputedStyle(root.value).rowGap) || 0;
    const y = d.before ? r.top - box.top - gap / 2 : r.bottom - box.top + gap / 2;
    return { left: '0', right: '0', top: `${y - 2}px`, height: '4px' };
}

onBeforeUnmount(() => window.removeEventListener('pointermove', onMove));

/* ── 높이 재기 — 칸이 아니라 카드의 내용 높이를 잰다(늘어난 높이를 세면 칸이 줄지 못한다) ── */

const observed = new Map<string, Element>();
let cellObserver: ResizeObserver | null = null;
let rootObserver: ResizeObserver | null = null;
let mutationObserver: MutationObserver | null = null;

/** 늘어나는 상자(`data-grid-fill`)의 본래 높이 — 밀린 줄(`data-grid-push`) 위 여백·띄운 덩어리(`data-grid-float`) 여백은 늘어난 빈 곳이라 세지 않는다 */
function naturalHeight(el: Element): number {
    if (!el.hasAttribute('data-grid-fill')) return el.getBoundingClientRect().height;
    const cs = getComputedStyle(el);
    let sum = (parseFloat(cs.paddingTop) || 0) + (parseFloat(cs.paddingBottom) || 0) + (parseFloat(cs.borderTopWidth) || 0) + (parseFloat(cs.borderBottomWidth) || 0);
    let count = 0;
    for (const child of el.children) {
        const ccs = getComputedStyle(child);
        if (ccs.display === 'none' || ccs.position === 'absolute' || ccs.position === 'fixed') continue;
        const h = naturalHeight(child);
        if (h === 0 && !child.hasAttribute('data-grid-fill')) continue;
        const float = child.hasAttribute('data-grid-float');
        const top = float || child.hasAttribute('data-grid-push') ? 0 : parseFloat(ccs.marginTop) || 0;
        sum += h + top + (float ? 0 : parseFloat(ccs.marginBottom) || 0);
        count += 1;
    }
    return sum + (count > 1 ? (count - 1) * (parseFloat(cs.rowGap) || 0) : 0);
}

/** 껍데기 카드는 `naturalHeight`, 게시판 본문 칸(블록 흐름)은 자식들의 위·아래 끝 */
function contentHeight(cell: HTMLElement): number {
    const card = cell.firstElementChild as HTMLElement | null;
    if (!card || card.hasAttribute('data-home-edit')) return 0;
    if (card.hasAttribute('data-grid-fill')) return naturalHeight(card);
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

/** 늘어난 상자는 안 내용이 줄어도 자기 크기가 그대로라 그 안 자식까지 지켜본다 */
const watchedChildren = new WeakSet<Element>();
function watchTree(el: Element): void {
    for (const child of el.children) {
        if (!watchedChildren.has(child)) {
            watchedChildren.add(child);
            cellObserver?.observe(child);
        }
        if (child.hasAttribute('data-grid-fill')) watchTree(child);
    }
}
function watchChildren(cell: Element): void {
    const card = cell.firstElementChild;
    if (!card || !cellObserver) return;
    watchTree(card);
}

function measureInto(id: string, next: Record<string, number>): boolean {
    const cell = observed.get(id) as HTMLElement | undefined;
    if (!cell) return false;
    watchChildren(cell);
    const px = Math.ceil(contentHeight(cell));
    // 1px 이하 흔들림은 무시한다(재기·재배치가 ±1px 로 왕복하지 않게)
    const prev = next[id];
    if (px > 0 && (prev === undefined || Math.abs(px - prev) > 1)) {
        next[id] = px;
        return true;
    }
    // 개발 감시 — 안정된 상태에서 카드가 칸보다 길면 넘침
    if (import.meta.dev && mode.value !== 'stack') {
        const card = cell.firstElementChild as HTMLElement | null;
        const slot = Number(cell.getAttribute('data-grid-height'));
        if (card && Number.isFinite(slot) && slot > 0 && card.getBoundingClientRect().height > slot + 1) {
            console.warn(`[home-grid] 가젯 ${id} 카드(${Math.round(card.getBoundingClientRect().height)}px)가 칸(${slot}px)보다 커요 — 넘침`, card);
        }
    }
    return false;
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
        for (const id of ids) if (measureInto(id, nextHeights)) changed = true;
        if (changed) heights.value = nextHeights;
    });
    for (const el of observed.values()) cellObserver.observe(el);

    // 요소를 갈아 끼우면(빈 상태 ↔ 목록 · 쪽 넘김) 늘어난 상자는 크기가 그대로라 ResizeObserver 가 모른다
    if (typeof MutationObserver !== 'undefined' && root.value) {
        let frame = 0;
        mutationObserver = new MutationObserver(() => {
            if (frame) return;
            frame = window.requestAnimationFrame(() => {
                frame = 0;
                let changed = false;
                const nextHeights = { ...heights.value };
                for (const id of observed.keys()) if (measureInto(id, nextHeights)) changed = true;
                if (changed) heights.value = nextHeights;
            });
        });
        mutationObserver.observe(root.value, { childList: true, subtree: true });
    }

    rootObserver = new ResizeObserver((entries) => {
        const w = entries[0]?.contentRect.width ?? 0;
        if (Math.round(w) !== width.value) width.value = Math.round(w);
    });
    if (root.value) rootObserver.observe(root.value);
});

onBeforeUnmount(() => {
    cellObserver?.disconnect();
    rootObserver?.disconnect();
    mutationObserver?.disconnect();
    mutationObserver = null;
    cellObserver = null;
    rootObserver = null;
    observed.clear();
});
</script>

<style scoped>
/* 관리자 미리보기 편집 손잡이 — 올리면 주 색상 점선 테두리, 끄는 칸은 실선. 색은 주 색상 토큰만 */
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
.home-edit-up {
    right: auto;
    left: 0.5rem;
}
.home-edit-down {
    right: auto;
    left: 2.75rem;
}
.home-edit-remove:disabled {
    cursor: default;
    background-color: var(--p-surface-400);
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
/* 크기 알림(빨강 작음 · 주황 큼) — 아래에 둬야 올림·끄는 중 규칙보다 이긴다 */
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
