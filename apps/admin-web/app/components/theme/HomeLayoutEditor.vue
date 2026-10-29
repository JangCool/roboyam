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
        `theme`·`page` 가 바뀌면 그 구성을 다시 읽는다(저장하지 않은 변경 확인은 화면이 `dirty` 이벤트를 받아 먼저 한다). 화면을 나갈 때는 여기서 묻는다.
        ⚙ 설정 창(`GadgetSettingsDialog`): 공구(개수·정렬) · 글 목록 · 갤러리 · 게시판 카드(대상 게시판 · 개수 · 정렬) — 고친 값은 배치와 함께 저장한다(서버가 검사·저장, 400 `fields.gadgets[i].config.*`).
          실제 화면은 저장 전에는 이미 받은 줄 안에서만 차례·개수·게시판을 바꿔 보인다. 꾸미기 편집은 범위 밖. 권한: 바꾸기·저장 `site.manage`(없으면 도구·저장 줄이 없고 iframe 의 손잡이 일도 받지 않는다). 막는 것은 서버다.
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
                        <Button type="button" :label="word('resetDefault')" icon="pi pi-replay" severity="secondary" variant="outlined" size="small" :disabled="saving" @click="resetToDefault" />
                        <Button type="button" :label="t('homeLayout.add')" icon="pi pi-plus" size="small" :disabled="saving" @click="addOpen = true" />
                    </div>
                </div>
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

        <!-- 가젯 내용 설정(⚙) — 공구 · 글 목록 · 갤러리 · 게시판 카드 -->
        <GadgetSettingsDialog v-model:visible="settingsOpen" :gadget="settingsGadget ?? null" :in-page="Boolean(page)" :folder="page ? { name: page.folderName, boards: page.folderBoards } : null" @apply="applySettings" />
    </div>
</template>

<script setup lang="ts">
import type { HomeGridColumns } from '#layers/home-layout/utils/util-home-layout';
import UtilHomeLayout from '#layers/home-layout/utils/util-home-layout';
import type { ApiError } from '~/composables/common/useApiClient';
import type { HomeGadgetConfig, HomeLayoutBody, HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetPage } from '~/composables/page/api/useGadgetPageApi.types';
import UtilGadgetConfig from '~/utils/util-gadget-config';
import type { HomeEditorItem } from '~/utils/util-home-layout-editor';
import UtilHomeLayoutEditor from '~/utils/util-home-layout-editor';

/** 편집기가 읽고 저장하는 배치(홈 `HomeLayoutView` · 가젯 페이지 `GadgetPageLayoutView` 의 같은 칸) */
type EditorView = HomeLayoutBody & { updatedAt: string | null; updatedBy: string | null };

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
}
const props = withDefaults(defineProps<Props>(), { hidden: false, page: null });
const emit = defineEmits<{
    (e: 'dirty', value: boolean): void;
    /** 편집 중인 홈 구성(저장 전 포함) — 화면의 실제 화면(공개 사이트 iframe)이 바로 따른다 */
    (e: 'change', value: HomeLayoutBody): void;
}>();

const { t } = useI18n();
const api = useHomeLayoutApi();
const pageApi = useGadgetPageApi();
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
/** 편집하는 가젯 페이지 ID(홈이면 null) */
const pageId = computed(() => props.page?.id ?? null);
/** 문구 마디 — 홈 · 페이지(`homeLayout.<key>` · `homeLayout.page_<key>`) */
function word(key: string, values?: Record<string, unknown>): string {
    return t(pageId.value ? `homeLayout.page_${key}` : `homeLayout.${key}`, values ?? {});
}

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref<ApiError | null>(null);
const view = ref<EditorView | null>(null);

const gadgets = ref<HomeLayoutGadget[]>([]);
const columns = ref<HomeGridColumns>(UtilHomeLayout.DEFAULT_COLUMNS);
const items = ref<HomeEditorItem[]>([]);
const announcement = ref('');

const saving = ref(false);
const saveError = ref('');
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
    return savedIds !== nowIds || !UtilHomeLayoutEditor.same(currentLayout.value, view.value.layout) || configsOf(view.value.gadgets) !== configsOf(gadgets.value);
});

/** 바뀜 비교용 — 가젯 ID 차례대로 내용 설정 */
function configsOf(list: readonly HomeLayoutGadget[]): string {
    return JSON.stringify(
        list
            .filter((g) => g.config)
            .map((g) => [g.id, UtilGadgetConfig.key(g.config)])
            .sort(),
    );
}

function copyConfig(config: HomeGadgetConfig | null | undefined): HomeGadgetConfig | null | undefined {
    return config ? { ...config, boardIds: config.boardIds ? [...config.boardIds] : config.boardIds } : config;
}

/* ── 불러오기 ── */

/** 배치 하나로 채운다 */
function fill(body: HomeLayoutBody): void {
    gadgets.value = body.gadgets.map((g) => ({ ...g, config: copyConfig(g.config) }));
    const read = UtilHomeLayout.read(
        body.layout,
        body.gadgets.map((g) => g.id),
    );
    columns.value = read.columns;
    items.value = UtilHomeLayoutEditor.toEditor(read, typeOf);
    saveError.value = '';
}

let loadSeq = 0;
async function load(id: string): Promise<void> {
    const mine = ++loadSeq;
    status.value = 'loading';
    loadError.value = null;
    try {
        const res: EditorView = pageId.value ? await pageApi.layout(pageId.value, id) : await api.get(id);
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

function applySettings(config: HomeGadgetConfig): void {
    const id = settingsId.value;
    if (!id) return;
    gadgets.value = gadgets.value.map((g) => (g.id === id ? { ...g, config } : g));
    saveError.value = '';
    toast.show(t('homeLayout.settingsApplied', { title: gadgetOf(id)?.title ?? '' }));
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
    gadgets.value = [...gadgets.value, { id, type: kind, title, desktop: true, mobile: true, config: UtilGadgetConfig.initial(kind, Boolean(pageId.value)) }];
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
        fill(pageId.value ? await pageApi.initial(pageId.value) : await api.defaults(theme.value));
        toast.show(word('resetDone'));
    } catch (e) {
        toast.error(toApiError(e).message);
    }
}

function revert(): void {
    if (!view.value) return;
    fill(view.value);
}

async function save(): Promise<void> {
    if (!canManage.value || saving.value || !dirty.value) return;
    saving.value = true;
    saveError.value = '';
    try {
        const body: HomeLayoutBody = { layout: currentLayout.value, gadgets: gadgets.value.map((g) => ({ ...g, config: copyConfig(g.config) })) };
        const res: EditorView = pageId.value ? await pageApi.saveLayout(pageId.value, theme.value, body) : await api.save(theme.value, body);
        view.value = res;
        fill(res);
        toast.success(word('saved'));
    } catch (e) {
        saveError.value = toApiError(e).message || word('saveFailed');
    } finally {
        saving.value = false;
    }
}

defineExpose({ applyOp });

// 테마·페이지가 바뀌면 그 구성을 읽는다 · 저장하지 않은 변경 여부를 화면에 알린다(바꾸기 전에 화면이 묻는다)
watch([theme, pageId], ([id]) => void load(id), { immediate: true });
watch(dirty, (value) => emit('dirty', value), { immediate: true });
watch(
    [currentLayout, gadgets],
    () => {
        if (view.value) emit('change', { layout: currentLayout.value, gadgets: gadgets.value.map((g) => ({ ...g, config: copyConfig(g.config) })) });
    },
    { deep: true, immediate: true },
);

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async (to) => {
    if (!dirty.value || to.path === route.path) return true;
    return confirm({ title: t('homeLayout.leaveTitle'), message: t('homeLayout.leaveMessage'), confirmLabel: t('homeLayout.leaveConfirm'), cancelLabel: t('homeLayout.stay'), tone: 'danger' });
});
</script>
