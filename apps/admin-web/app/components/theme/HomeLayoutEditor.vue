<template>
    <!--
        홈 구성 편집 도구 — 테마 화면(`pages/settings/theme/[[tab]].vue`)의 「레이아웃 › 홈」이 쓴다(사용자 결정 2026-10-28: 홈 구성은 테마 아래 · **편집도 실제 화면에서** 한다).
        가젯을 끌어 옮기고 크기를 바꾸고 빼는 일은 공개 사이트 iframe 안의 실제 홈 격자에서 한다(`ThemeLivePreview` → user-web `HomeGadgetGrid` 편집 모드). 이 부품은 그 배치의 **주인**이다:
          · 테마의 홈 구성을 읽고(`useHomeLayoutApi`) 편집 중인 값(칸 수 · 가젯마다 x·y·w·h · 가젯 목록)을 들고, 바뀔 때마다 `change` 로 화면에 알린다(화면이 iframe 에 보낸다).
          · iframe 이 알리는 일(`applyOp` — 놓은 칸 `place` · 칸·행 `size` · `remove` · ⚙ `settings`)을 받아 계산한다 — 최소 크기는 종류별(`UtilHomeLayoutEditor.KINDS`), 부딪친 가젯은 밀리고 늘 위로 당긴 모양(`UtilHomeLayoutEditor.setBox`).
          · 도구 줄: 격자 칸 수 12/24(바꾸면 위치·폭 2배/반 — `UtilHomeLayout.convert`) · 테마 기본 배치로(저장해야 적용) · 가젯 넣기(종류 8종 — 맨 아래 기본 크기).
          · 저장(`PUT /api/admin/home-layouts/{theme}` — 아래 붙는 저장 줄) · 되돌리기(마지막 저장으로).
        `hidden` 이면 도구 줄을 숨긴다(화면이 「보기」일 때) — 저장 줄과 편집 중인 값은 그대로다. 실제 화면은 기본 슬롯으로 받아 도구 줄과 저장 줄 사이에 둔다.
        **가젯 페이지**(`page` — 사용자 결정 2026-10-29): 주면 홈 대신 그 페이지의 이 테마 배치를 편집한다(`useGadgetPageApi` — 읽기·저장·「처음 배치로」). 모양·손잡이·도구 줄은 홈과 같다.
        **게시판 레이아웃**(`board` — 2026-10-30, `useBoardLayoutApi`): 테마마다 하나, 모든 게시판에 같이 쓴다. 고정 칸 「게시판 본문」(`BOARD_BODY`)이 꼭 하나 있다(본문 = 종류가 `BOARD_BODY` 인 가젯 — ID 는 서버가 정한다) —
          옮기기·크기만 되고 빼기·⚙·두 번째 넣기는 없다(`UtilBoardLayout.ensureBody`). 도구 줄에 적용 화면(게시판 목록 · 글 보기 켜고 끄기 — `screens` 로 화면에 알린다) · [추천 배치](본문 + 오른쪽 열, 묻고 바꾼다)가 더 붙고,
          「테마 기본 배치로」는 본문만 전체 폭(지금 공개 화면과 같다). 새로 넣은 가젯의 대상 게시판 처음 값은 「지금 보는 게시판」(`UtilGadgetConfig.initial(…, 'BOARD')`).
        `theme`·`page`·`board` 가 바뀌면 그 구성을 다시 읽는다(저장하지 않은 변경 확인은 화면이 `dirty` 이벤트를 받아 먼저 한다). 화면을 나갈 때는 여기서 묻는다.
        ⚙ 설정 창(`GadgetSettingsDialog` — 8종 전부, 2026-10-30): 「내용」(종류별 설정) · 「모양」(제목·꾸미기 `style`) · 「노출」(PC/모바일 · 모바일 접기 · 비었을 때) — 고친 값은 배치와 함께 저장한다
          (저장 요청 `gadgets[i]` = `{ id, type, title, desktop, mobile, style, config }` — `style`·`config` 는 마지막 저장 값과 다를 때만 싣고 아니면 null(그대로 두기). 서버가 검사·저장).
          저장 실패 400 의 `fields.gadgets[i].*` 는 그 가젯의 ⚙ 창(탭 점·칸 아래)과 저장 줄(어느 가젯인지)에, 나머지 칸 오류(`layout.items` · `screens.*` 등)는 저장 줄에 보인다(`UtilHomeLayoutEditor.saveErrors`).
          실제 화면은 저장 전에는 이미 받은 줄 안에서만 차례·개수·게시판을 바꿔 보이고, 제목·꾸미기·노출·바로가기·안내 글은 그대로 그린다.
          창은 관리자 응답의 꾸미기·설정(칸 전부)으로 채운다. 권한: 바꾸기·저장 `site.manage`(없으면 도구·저장 줄이 없고 iframe 의 손잡이 일도 받지 않는다). 막는 것은 서버다.
    -->
    <div class="flex flex-col gap-4">
        <div v-show="!hidden" class="flex flex-col gap-2" data-testid="home-layout-tools">
            <div v-if="status === 'loading' && !view" class="flex gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2rem" width="10rem" />
                <Skeleton height="2rem" width="14rem" />
            </div>

            <Message v-else-if="status === 'error'" severity="error" size="small" :closable="false">
                <span class="flex flex-wrap items-center gap-2">
                    {{ word('loadFailed') }} — {{ loadError?.message }}
                    <Button v-if="loadError?.status !== 403" type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="reload" />
                </span>
            </Message>

            <template v-else-if="view">
                <div class="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-(--line) px-3 py-2">
                    <span :id="`${baseId}-cols`" class="text-sm font-medium">{{ t('homeLayout.columns') }}</span>
                    <SelectButton
                        :model-value="columns"
                        :options="columnOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :disabled="!canManage || saving"
                        :aria-labelledby="`${baseId}-cols`"
                        :aria-describedby="`${baseId}-hint`"
                        @update:model-value="(v: HomeGridColumns) => changeColumns(v)"
                    />
                    <p :id="`${baseId}-hint`" class="m-0 min-w-0 flex-1 basis-64 text-xs leading-relaxed text-muted-color">{{ canManage ? t('homeLayout.liveEditHint') : word('readOnly') }}</p>
                    <div v-if="canManage" class="flex flex-wrap items-center gap-2">
                        <Button
                            v-if="board"
                            type="button"
                            :label="t('homeLayout.board_recommended')"
                            icon="pi pi-objects-column"
                            severity="secondary"
                            variant="outlined"
                            size="small"
                            :disabled="saving"
                            data-testid="board-layout-recommended"
                            @click="applyRecommended"
                        />
                        <Button type="button" :label="word('resetDefault')" icon="pi pi-replay" severity="secondary" variant="outlined" size="small" :disabled="saving" @click="resetToDefault" />
                        <Button type="button" :label="t('homeLayout.add')" icon="pi pi-plus" size="small" :disabled="saving" @click="addOpen = true" />
                    </div>
                </div>
                <!-- 게시판 레이아웃 — 적용 화면(끈 화면은 지금처럼 본문만) -->
                <fieldset v-if="board" class="m-0 flex flex-wrap items-center gap-x-5 gap-y-2 rounded-lg border border-(--line) px-3 py-2" data-testid="board-layout-screens">
                    <legend class="sr-only">{{ t('homeLayout.board_screens') }}</legend>
                    <span class="text-sm font-medium" aria-hidden="true">{{ t('homeLayout.board_screens') }}</span>
                    <div v-for="s in UtilBoardLayout.SCREENS" :key="s" class="flex items-center gap-2">
                        <ToggleSwitch
                            :model-value="UtilBoardLayout.applies(screens, s)"
                            :input-id="`${baseId}-screen-${s}`"
                            :disabled="!canManage || saving"
                            @update:model-value="(v: boolean) => setScreen(s, v)"
                        />
                        <label :for="`${baseId}-screen-${s}`" class="cursor-pointer text-sm">{{ t(`homeLayout.board_screen_${s}`) }}</label>
                    </div>
                    <p class="m-0 min-w-0 flex-1 basis-64 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.board_screensHint') }}</p>
                </fieldset>
                <p class="m-0 text-xs text-muted-color">
                    {{ view.updatedAt ? t('homeLayout.lastSaved', { at: time.full(view.updatedAt), by: view.updatedBy ?? '—' }) : word('neverSaved') }}
                </p>
            </template>
            <p class="sr-only" aria-live="polite">{{ announcement }}</p>
        </div>

        <!-- 실제 화면(화면이 넣는다) — 도구 줄과 저장 줄 사이 -->
        <slot />

        <StickySaveBar v-if="canManage && view" revertable :dirty="dirty" :saving="saving" :error="saveError" @save="save" @revert="revert" />

        <!-- 가젯 넣기 -->
        <Dialog v-model:visible="addOpen" modal :header="t('homeLayout.addTitle')" :draggable="false" :style="{ width: '36rem', maxWidth: '92vw' }">
            <p class="m-0 mb-3 text-sm leading-relaxed text-muted-color">{{ t('homeLayout.addLead') }}</p>
            <ul class="m-0 grid list-none grid-cols-1 gap-2 p-0 sm:grid-cols-2">
                <li v-for="spec in UtilHomeLayoutEditor.KINDS" :key="spec.kind">
                    <button
                        type="button"
                        class="flex w-full items-start gap-3 rounded-lg border border-(--line) bg-transparent p-3 text-left hover:border-primary hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        @click="add(spec.kind)"
                    >
                        <span class="grid size-8 shrink-0 place-items-center rounded-md bg-primary-50 text-primary dark:bg-primary-950"><i :class="`pi ${spec.icon}`" aria-hidden="true" /></span>
                        <span class="flex min-w-0 flex-col gap-0.5">
                            <span class="text-sm font-medium">{{ t(`homeLayout.kind.${spec.kind}`) }}</span>
                            <span class="text-xs leading-relaxed text-muted-color">{{ t(`homeLayout.kindDesc.${spec.kind}`) }}</span>
                        </span>
                    </button>
                </li>
            </ul>
        </Dialog>

        <!-- 가젯 설정(⚙) — 8종 전부: 내용 · 모양 · 노출 -->
        <GadgetSettingsDialog
            v-model:visible="settingsOpen"
            :gadget="settingsGadget ?? null"
            :server-errors="settingsId ? (serverErrors[settingsId] ?? null) : null"
            :place="place"
            :folder="page ? { name: page.folderName, boards: page.folderBoards } : null"
            @apply="applySettings"
        />
    </div>
</template>

<script setup lang="ts">
import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { ApiError } from '~/composables/common/useApiClient';
import type { HomeLayoutBody, HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetSettingsPatch } from '~/components/theme/GadgetSettingsDialog.vue';
import type { GadgetPage } from '~/composables/page/api/useGadgetPageApi.types';
import type { BoardLayoutScreen, BoardLayoutScreens } from '~/composables/board/api/useBoardLayoutApi.types';
import type { GadgetPlace } from '~/utils/util-gadget-config';
import UtilBoardLayout from '~/utils/util-board-layout';
import UtilGadgetConfig from '~/utils/util-gadget-config';
import UtilGadgetStyle from '~/utils/util-gadget-style';
import type { HomeEditorItem } from '~/utils/util-home-layout-editor';
import UtilHomeLayoutEditor from '~/utils/util-home-layout-editor';

/** 편집기가 읽고 저장하는 배치(홈 `HomeLayoutView` · 가젯 페이지 `GadgetPageLayoutView` · 게시판 `BoardLayoutView` 의 같은 칸 — 적용 화면은 게시판만) */
type EditorView = HomeLayoutBody & { updatedAt: string | null; updatedBy: string | null; screens?: BoardLayoutScreens };

/** 실제 화면(iframe)의 홈 격자가 알리는 일 — 놓은 칸 · 칸·행 · 빼기 · ⚙ 설정 열기(user-web `AdminPreviewLayoutOp` 와 같은 모양) */
export type HomeLayoutOp =
    | { op: 'place'; id: string; x: number; y: number }
    | { op: 'size'; id: string; w: number; h: number }
    | { op: 'remove'; id: string }
    | { op: 'settings'; id: string };

interface Props {
    /** 구성을 편집할 테마 id */
    theme: string;
    /** 도구 줄을 숨기는가(화면이 「보기」일 때) — 저장 줄과 편집 중인 값은 그대로 둔다 */
    hidden?: boolean;
    /** 가젯 페이지 — 주면 그 페이지의 이 테마 배치를 편집한다(없으면 홈) */
    page?: GadgetPage | null;
    /** 게시판 레이아웃을 편집하는가(`page` 보다 뒤 — 둘 다 주지 않는다) */
    board?: boolean;
}
const props = withDefaults(defineProps<Props>(), { hidden: false, page: null, board: false });
const emit = defineEmits<{
    (e: 'dirty', value: boolean): void;
    /** 편집 중인 홈 구성(저장 전 포함) — 화면의 실제 화면(공개 사이트 iframe)이 바로 따른다 */
    (e: 'change', value: HomeLayoutBody): void;
    /** 게시판 레이아웃의 적용 화면(저장 전 포함) — 실제 화면이 따른다 */
    (e: 'screens', value: BoardLayoutScreens): void;
}>();

const { t } = useI18n();
const api = useHomeLayoutApi();
const pageApi = useGadgetPageApi();
const boardApi = useBoardLayoutApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const route = useRoute();

const baseId = useId();
const canManage = computed(() => can('site.manage'));

/** 편집하는 테마 */
const theme = computed(() => props.theme);
/** 편집하는 가젯 페이지 ID(홈·게시판이면 null) */
const pageId = computed(() => (props.board ? null : (props.page?.id ?? null)));
/** 게시판 레이아웃인가 */
const board = computed(() => props.board);
/** 가젯이 놓인 자리 — 대상 게시판 범위가 달라진다 */
const place = computed<GadgetPlace>(() => (board.value ? 'BOARD' : pageId.value ? 'PAGE' : 'HOME'));
/** 문구 마디 — 홈 · 페이지 · 게시판(`homeLayout.<key>` · `homeLayout.page_<key>` · `homeLayout.board_<key>`) */
function word(key: string, values?: Record<string, unknown>): string {
    return t(board.value ? `homeLayout.board_${key}` : pageId.value ? `homeLayout.page_${key}` : `homeLayout.${key}`, values ?? {});
}

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref<ApiError | null>(null);
const view = ref<EditorView | null>(null);

const gadgets = ref<HomeLayoutGadget[]>([]);
const columns = ref<HomeGridColumns>(UtilHomeLayout.DEFAULT_COLUMNS);
const items = ref<HomeEditorItem[]>([]);
/** 게시판 레이아웃의 적용 화면(게시판이 아니면 쓰지 않는다) */
const screens = ref<BoardLayoutScreens>(UtilBoardLayout.defaultScreens());
const announcement = ref('');

const saving = ref(false);
const saveError = ref('');
/** 마지막 저장 실패의 가젯별 칸 오류(가젯 ID → 칸 경로 → 문구) — ⚙ 창이 보인다. 그 가젯 설정을 적용하거나 다시 불러오면 지운다 */
const serverErrors = ref<Record<string, Record<string, string>>>({});
const addOpen = ref(false);
let newSeq = 0;

/* ⚙ 설정 창(`GadgetSettingsDialog`) — 고치는 가젯(적용을 눌러야 편집 중인 값에 들어간다) */
const settingsOpen = ref(false);
const settingsId = ref<string | null>(null);
const settingsGadget = computed(() => (settingsId.value ? gadgetOf(settingsId.value) : undefined));

const columnOptions = computed(() => UtilHomeLayout.COLUMN_CHOICES.map((value) => ({ value, label: t('homeLayout.columnsOption', { count: value }) })));

function gadgetOf(id: string): HomeLayoutGadget | undefined {
    return gadgets.value.find((g) => g.id === id);
}
function typeOf(id: string): string {
    return gadgetOf(id)?.type ?? '';
}

function kindName(type: string): string {
    return UtilHomeLayoutEditor.isKnown(type) ? t(`homeLayout.kind.${type}`) : t('homeLayout.kind.UNKNOWN');
}

/** 저장 모양 — 저장 본문·바뀜 비교 */
const currentLayout = computed(() => UtilHomeLayoutEditor.toLayout(columns.value, items.value));

const dirty = computed(() => {
    if (!view.value) return false;
    const savedIds = view.value.gadgets
        .map((g) => g.id)
        .sort()
        .join('|');
    const nowIds = gadgets.value
        .map((g) => g.id)
        .sort()
        .join('|');
    const screensChanged = board.value && JSON.stringify(UtilBoardLayout.readScreens(view.value.screens)) !== JSON.stringify(screens.value);
    return savedIds !== nowIds || !UtilHomeLayoutEditor.same(currentLayout.value, view.value.layout) || settingsOf(view.value.gadgets) !== settingsOf(gadgets.value) || screensChanged;
});

/** 바뀜 비교용 — 가젯 ID 차례대로 제목 · 노출 · 꾸미기 · 내용 설정(`UtilGadgetConfig.gadgetKey`) */
function settingsOf(list: readonly HomeLayoutGadget[]): string {
    return JSON.stringify(list.map((g) => [g.id, UtilGadgetConfig.gadgetKey(g)]).sort());
}

/* ── 불러오기 ── */

/** 배치 하나로 채운다 — 게시판 레이아웃이면 본문을 꼭 하나로 고치고 적용 화면도 */
function fill(input: HomeLayoutBody & { screens?: BoardLayoutScreens }): void {
    const body = board.value ? UtilBoardLayout.ensureBody({ ...input, screens: UtilBoardLayout.readScreens(input.screens) }, t('homeLayout.kind.BOARD_BODY')) : input;
    if (board.value) screens.value = UtilBoardLayout.readScreens(input.screens);
    gadgets.value = body.gadgets.map(UtilGadgetConfig.copy);
    const read = UtilHomeLayout.read(
        body.layout,
        body.gadgets.map((g) => g.id),
    );
    columns.value = read.columns;
    items.value = UtilHomeLayoutEditor.toEditor(read, typeOf);
    saveError.value = '';
    serverErrors.value = {};
}

let loadSeq = 0;
async function load(id: string): Promise<void> {
    const mine = ++loadSeq;
    status.value = 'loading';
    loadError.value = null;
    try {
        const res: EditorView = board.value ? await boardApi.get(id) : pageId.value ? await pageApi.layout(pageId.value, id) : await api.get(id);
        if (mine !== loadSeq) return;
        view.value = res;
        fill(res);
        status.value = 'ready';
    } catch (e) {
        if (mine !== loadSeq) return;
        loadError.value = toApiError(e);
        view.value = null;
        status.value = 'error';
    }
}

function reload(): void {
    void load(theme.value);
}

/* ── 편집 ── */

function apply(next: HomeEditorItem[]): void {
    items.value = next;
    saveError.value = '';
}

/** 바뀐 자리를 화면 읽기 프로그램에 알린다 */
function announce(id: string): void {
    const it = items.value.find((i) => i.i === id);
    const g = gadgetOf(id);
    if (it && g) announcement.value = t('homeLayout.announce', { title: g.title, x: it.x, y: it.y, w: it.w, h: it.h });
}

/**
 * 실제 화면의 홈 격자가 알린 일을 계산한다 — 옮기기는 놓은 칸의 「그 행에 있는 가젯 앞」으로, 크기는 최소 크기·칸 수 안으로, 부딪친 가젯은 밀리고 위로 당긴다.
 * @param {HomeLayoutOp} op - 할 일.
 */
function applyOp(op: HomeLayoutOp): void {
    if (!canManage.value || saving.value || !items.value.some((it) => it.i === op.id)) return;
    if (op.op === 'remove') {
        // 고정 칸(게시판 본문)은 뺄 수 없다
        if (UtilHomeLayoutEditor.isFixed(typeOf(op.id))) return;
        remove(op.id);
        return;
    }
    if (op.op === 'settings') {
        openSettings(op.id);
        return;
    }
    apply(UtilHomeLayoutEditor.setBox(items.value, op.id, op.op === 'place' ? { x: op.x, y: op.y } : { w: op.w, h: op.h }, columns.value));
    announce(op.id);
}

function openSettings(id: string): void {
    const g = gadgetOf(id);
    if (!g || !UtilGadgetConfig.settable(g.type)) return;
    settingsId.value = id;
    settingsOpen.value = true;
}

/** 설정 창의 [적용] — 편집 중인 값에 넣는다(꾸미기·내용 설정은 바뀌었을 때만 온다). 그 가젯의 지난 저장 오류는 지운다 */
function applySettings(patch: GadgetSettingsPatch): void {
    const id = settingsId.value;
    if (!id) return;
    gadgets.value = gadgets.value.map((g) =>
        g.id === id ? { ...g, title: patch.title, desktop: patch.desktop, mobile: patch.mobile, ...(patch.style ? { style: patch.style } : {}), ...(patch.config ? { config: patch.config } : {}) } : g,
    );
    saveError.value = '';
    const rest = { ...serverErrors.value };
    delete rest[id];
    serverErrors.value = rest;
    toast.show(t('homeLayout.settingsApplied', { title: patch.title }));
}

function changeColumns(next: HomeGridColumns): void {
    if (!UtilHomeLayout.isColumns(next) || next === columns.value) return;
    apply(UtilHomeLayoutEditor.convert(items.value, columns.value, next, typeOf));
    columns.value = next;
}

function add(kind: string): void {
    newSeq += 1;
    const id = `new-${newSeq}`;
    const title = kindName(kind);
    gadgets.value = [...gadgets.value, { id, type: kind, title, desktop: true, mobile: true, config: UtilGadgetConfig.initial(kind, place.value) }];
    apply(UtilHomeLayoutEditor.add(items.value, id, kind, columns.value));
    addOpen.value = false;
    toast.show(t('homeLayout.added', { title }));
}

function remove(id: string): void {
    const title = gadgetOf(id)?.title ?? '';
    gadgets.value = gadgets.value.filter((g) => g.id !== id);
    apply(UtilHomeLayoutEditor.normalize(items.value.filter((it) => it.i !== id)));
    toast.show(t('homeLayout.removed', { title }));
}

async function resetToDefault(): Promise<void> {
    if (!(await confirm({ title: word('resetTitle'), message: word('resetMessage'), confirmLabel: word('resetConfirm'), cancelLabel: t('common.cancel') }))) return;
    try {
        fill(board.value ? await boardApi.defaults(theme.value) : pageId.value ? await pageApi.initial(pageId.value) : await api.defaults(theme.value));
        toast.show(word('resetDone'));
    } catch (e) {
        toast.error(toApiError(e).message);
    }
}

/** 게시판 레이아웃 [추천 배치] — 본문 + 오른쪽 열(이 게시판 인기글 · 공동구매 · 인기 소모임). 지금 배치를 바꾸기 전에 묻는다(저장해야 적용) */
async function applyRecommended(): Promise<void> {
    if (!board.value || !(await confirm({ title: t('homeLayout.board_recommendedTitle'), message: t('homeLayout.board_recommendedMessage'), confirmLabel: t('homeLayout.board_recommended'), cancelLabel: t('common.cancel') }))) return;
    const bodyId = UtilBoardLayout.bodyOf(gadgets.value)?.id ?? UtilBoardLayout.NEW_BODY_ID;
    const nextId = () => `new-${++newSeq}`;
    const titles = {
        body: gadgetOf(bodyId)?.title ?? t('homeLayout.kind.BOARD_BODY'),
        popular: t('homeLayout.board_seedPopular'),
        groupBuy: t('homeLayout.kind.GROUP_BUY'),
        groups: t('homeLayout.kind.GROUPS'),
    };
    fill(UtilBoardLayout.recommended(titles, columns.value, bodyId, nextId, screens.value));
    toast.show(t('homeLayout.board_recommendedDone'));
}

/** 적용 화면 하나 켜고 끄기 */
function setScreen(screen: BoardLayoutScreen, on: boolean): void {
    screens.value = screen === 'LIST' ? { ...screens.value, list: on } : { ...screens.value, post: on };
    saveError.value = '';
}

function revert(): void {
    if (!view.value) return;
    fill(view.value);
}

async function save(): Promise<void> {
    if (!canManage.value || saving.value || !dirty.value) return;
    saving.value = true;
    saveError.value = '';
    serverErrors.value = {};
    // 보낸 차례 — 서버 오류 `gadgets[i]` 를 가젯으로 되짚는다
    const sentIds = gadgets.value.map((g) => g.id);
    try {
        // 가젯마다 `{ id, type, title, desktop, mobile, style, config }` — 꾸미기·설정은 마지막 저장 값과 다를 때만(같거나 없으면 null = 그대로 두기)
        const saved = new Map((view.value?.gadgets ?? []).map((g) => [g.id, g]));
        const body: HomeLayoutBody = {
            layout: currentLayout.value,
            gadgets: gadgets.value.map((g) => {
                const c = UtilGadgetConfig.copy(g);
                const was = saved.get(c.id);
                const style = c.style && (!was || UtilGadgetStyle.key(c.style, c.type) !== UtilGadgetStyle.key(was.style, c.type)) ? c.style : null;
                const config = c.config && (!was || UtilGadgetConfig.key(c.config) !== UtilGadgetConfig.key(was.config)) ? c.config : null;
                return { id: c.id, type: c.type, title: c.title, desktop: c.desktop, mobile: c.mobile, style, config };
            }),
        };
        const res: EditorView = board.value
            ? await boardApi.save(theme.value, { ...body, screens: { ...screens.value } })
            : pageId.value
              ? await pageApi.saveLayout(pageId.value, theme.value, body)
              : await api.save(theme.value, body);
        view.value = res;
        fill(res);
        toast.success(word('saved'));
    } catch (e) {
        const err = toApiError(e);
        const found = UtilHomeLayoutEditor.saveErrors(err.fields, sentIds, (id) => UtilGadgetConfig.settable(typeOf(id)));
        serverErrors.value = found.byGadget;
        const titles = Object.keys(found.byGadget)
            .map((id) => t('homeLayout.saveGadgetTitle', { title: gadgetOf(id)?.title ?? id }))
            .join(', ');
        saveError.value = [err.message || word('saveFailed'), ...found.other, titles ? t('homeLayout.saveGadgetErrors', { titles }) : ''].filter(Boolean).join(' · ');
    } finally {
        saving.value = false;
    }
}

defineExpose({ applyOp });

// 테마·페이지가 바뀌면 그 구성을 읽는다 · 저장하지 않은 변경 여부를 화면에 알린다(바꾸기 전에 화면이 묻는다)
watch([theme, pageId, board], ([id]) => void load(id), { immediate: true });
watch(dirty, (value) => emit('dirty', value), { immediate: true });
watch(screens, (value) => emit('screens', { ...value }), { immediate: true });
watch(
    [currentLayout, gadgets],
    () => {
        if (view.value) emit('change', { layout: currentLayout.value, gadgets: gadgets.value.map(UtilGadgetConfig.copy) });
    },
    { deep: true, immediate: true },
);

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async (to) => {
    if (!dirty.value || to.path === route.path) return true;
    return confirm({ title: t('homeLayout.leaveTitle'), message: t('homeLayout.leaveMessage'), confirmLabel: t('homeLayout.leaveConfirm'), cancelLabel: t('homeLayout.stay'), tone: 'danger' });
});
</script>
