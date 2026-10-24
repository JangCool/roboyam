<template>
    <!--
        하이라이트 — 운영자가 고른 글을 하이라이트로 지정해 순서·노출·기간을 정하고, 그 글에 찬성·반대 의견(토론)을 받는다(계약 `docs/features/popular-highlight-contract.md`, community `api-content.md` 1절).
        목록: 순서(↑↓ 한 칸씩 — 전체 ID 를 새 차례로 보낸다) · 대표 이미지 · 제목(없으면 글 제목)·요약 · 상태 딱지(SHOWING·SCHEDULED·EXPIRED·OFF·BLOCKED) · 노출 기간 · 의견 수 · 노출 켜기/끄기 · [의견] · [고치기] · [지우기](확인).
        만들기·고치기는 대화상자(`HighlightFormDialog` — 글 찾기·제목·요약·대표 이미지 올리기·노출·기간·의견 받기). 후보 구획은 추천 많은 글(최근 일수·최소 추천·게시판)을 보여 주고 [하이라이트로 보내기]로 바로 만든다(기본: 노출 켬·기간 없음·맨 끝).
        의견 관리는 오른쪽 서랍(`HighlightOpinionsDrawer` — 찬성·반대 탭, 고정·숨김).
        로보얌 홈은 아직 자리표시라 **공개 홈 표시는 없다**(홈 + 테마 작업 때) — 지금 하이라이트가 하는 일은 그 글의 「하이라이트」 표시와 토론(의견 받기가 켜졌을 때)이다. 화면이 그 사실을 안내한다.
        권한: 보기 `highlight.read`(메뉴), 바꾸기 `highlight.manage` — 없으면 처리 단추가 없고 안내 한 줄(의견은 읽기만). 후보의 게시판 고르개는 게시판 목록을 볼 권한(`board.read`)이 있을 때만.
        사라진 하이라이트(`404 HIGHLIGHT_NOT_FOUND`)·순서 집합 불일치(`fields.ids`)는 서버 문구를 토스트로 보이고 목록을 다시 받는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('highlight.title')" :subtitle="t('highlight.subtitle')">
            <template #actions>
                <Button v-if="canManage" type="button" :label="t('highlight.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                <Button type="button" icon="pi pi-refresh" :label="t('highlight.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <Message severity="info" size="small" :closable="false">{{ t('highlight.homeNotice') }}</Message>

        <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
            <i class="pi pi-lock" aria-hidden="true" />{{ t('highlight.readOnlyHint') }}
        </p>

        <AppGadget :title="t('highlight.listTitle')">
            <template #title-extra>
                <span v-if="list" class="text-xs tabular-nums text-muted-color">{{ t('highlight.countLine', { showing: list.visibleCount, total: list.total }) }}</span>
            </template>

            <div v-if="busy && !list" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="5rem" />
            </div>
            <ErrorState v-else-if="loadError && !list" :title="t('highlight.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
            <template v-else-if="list">
                <EmptyState v-if="list.total === 0" icon="pi pi-bookmark" :title="t('highlight.empty')" :description="t('highlight.emptyHint')">
                    <Button v-if="canManage" type="button" :label="t('highlight.add')" icon="pi pi-plus" size="small" @click="openCreate" />
                </EmptyState>
                <template v-else>
                    <Message v-if="list.visibleCount === 0" severity="warn" size="small" :closable="false" class="mb-3">{{ t('highlight.noneShowing') }}</Message>
                    <ol class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="busy || ordering">
                        <li v-for="(item, index) in list.items" :key="item.id" class="flex flex-col gap-3 py-3 sm:flex-row sm:items-start">
                            <!-- 순서 -->
                            <div class="flex shrink-0 items-center gap-1 sm:flex-col">
                                <span class="w-7 text-center text-sm font-semibold tabular-nums">{{ item.order }}</span>
                                <template v-if="canManage">
                                    <Button type="button" icon="pi pi-angle-up" size="small" severity="secondary" variant="text" :aria-label="t('highlight.moveUp', { title: UtilHighlight.displayTitle(item) })" :disabled="index === 0 || ordering" @click="move(item, -1)" />
                                    <Button type="button" icon="pi pi-angle-down" size="small" severity="secondary" variant="text" :aria-label="t('highlight.moveDown', { title: UtilHighlight.displayTitle(item) })" :disabled="index === list.items.length - 1 || ordering" @click="move(item, 1)" />
                                </template>
                            </div>

                            <!-- 대표 이미지 -->
                            <div class="flex aspect-[16/9] w-28 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-800">
                                <img v-if="item.imageUrl" :src="item.imageUrl" alt="" class="size-full object-cover" loading="lazy" />
                                <i v-else class="pi pi-image text-muted-color" aria-hidden="true" />
                            </div>

                            <!-- 내용 -->
                            <div class="flex min-w-0 flex-1 flex-col gap-1">
                                <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                    <Tag :value="t(`highlight.status.${item.status}`)" :icon="UtilHighlight.statusIcon(item.status)" :severity="UtilHighlight.statusSeverity(item.status)" class="text-[11px]!" />
                                    <span class="min-w-0 break-words text-sm font-semibold [overflow-wrap:anywhere]">{{ UtilHighlight.displayTitle(item) }}</span>
                                    <Tag v-if="item.debate" :value="t('highlight.debateTag')" icon="pi pi-comments" severity="info" class="text-[11px]!" />
                                </div>
                                <p v-if="item.summary" class="m-0 text-xs leading-relaxed break-words text-muted-color">{{ item.summary }}</p>
                                <div class="text-xs">
                                    <ContentPostCell v-if="item.post" :post="item.post" />
                                    <span v-else class="text-muted-color">{{ t('highlight.postGone') }}</span>
                                </div>
                                <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs tabular-nums text-muted-color">
                                    <span><i class="pi pi-calendar mr-1 text-[10px]" aria-hidden="true" />{{ UtilHighlightMessages.period(t, time, item) }}</span>
                                    <i18n-t keypath="highlight.commentCounts" tag="span" scope="global">
                                        <template #all>{{ item.commentCounts.all }}</template>
                                        <template #pro><span class="font-medium stance-pro-text">{{ t('highlight.stanceTab.PRO', { n: item.commentCounts.pro }) }}</span></template>
                                        <template #con><span class="font-medium stance-con-text">{{ t('highlight.stanceTab.CON', { n: item.commentCounts.con }) }}</span></template>
                                        <template #general>{{ item.commentCounts.general }}</template>
                                    </i18n-t>
                                    <span v-if="item.updatedBy">{{ t('highlight.updatedBy', { name: item.updatedBy.name ?? '—', at: time.listLabel(item.updatedAt) }) }}</span>
                                </p>
                            </div>

                            <!-- 동작 -->
                            <div class="flex shrink-0 flex-wrap items-center gap-1 sm:flex-col sm:items-end">
                                <div v-if="canManage" class="flex items-center gap-2">
                                    <label :for="`hl-visible-${item.id}`" class="text-xs text-muted-color">{{ t('highlight.visibleShort') }}</label>
                                    <ToggleSwitch :model-value="item.visible" :input-id="`hl-visible-${item.id}`" :disabled="togglingId === item.id" @update:model-value="(v: boolean) => setVisible(item, v)" />
                                </div>
                                <div class="flex flex-wrap gap-1">
                                    <Button v-if="UtilHighlight.hasDebate(item)" type="button" :label="t('highlight.opinions', { n: UtilHighlight.opinionCount(item.commentCounts) })" icon="pi pi-comments" size="small" severity="secondary" variant="outlined" @click="openOpinions(item)" />
                                    <Button v-if="canManage" type="button" :label="t('common.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" @click="openEdit(item)" />
                                    <Button v-if="canManage" type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="removingId === item.id" @click="remove(item)" />
                                </div>
                            </div>
                        </li>
                    </ol>
                </template>
            </template>
        </AppGadget>

        <!-- 후보 — 추천 많은 글. 고르면 바로 하이라이트가 된다(기본값으로) -->
        <AppGadget :title="t('highlight.candidatesTitle')">
            <p class="mb-3 text-xs leading-relaxed text-muted-color">{{ t('highlight.candidatesLead') }}</p>
            <form class="mb-3 flex flex-wrap items-end gap-3" @submit.prevent="loadCandidates">
                <div class="flex flex-col gap-1">
                    <label :for="ids.days" class="text-xs text-muted-color">{{ t('highlight.daysLabel') }}</label>
                    <Select v-model="candidateQuery.days" :input-id="ids.days" :options="dayOptions" option-label="label" option-value="value" class="w-36" />
                </div>
                <div class="flex flex-col gap-1">
                    <label :for="ids.minLikes" class="text-xs text-muted-color">{{ t('highlight.minLikesLabel') }}</label>
                    <InputText :id="ids.minLikes" v-model="minLikesText" inputmode="numeric" class="w-24 text-right tabular-nums" :invalid="Boolean(minLikesError)" />
                </div>
                <div v-if="canReadBoards" class="flex flex-col gap-1">
                    <label :for="ids.board" class="text-xs text-muted-color">{{ t('highlight.boardLabel') }}</label>
                    <Select v-model="candidateQuery.boardId" :input-id="ids.board" :options="boardOptions" option-label="label" option-value="value" show-clear filter :placeholder="t('common.all')" class="w-48" />
                </div>
                <Button type="submit" :label="t('highlight.findCandidates')" icon="pi pi-search" size="small" :loading="candidatesBusy" :disabled="Boolean(minLikesError)" />
            </form>
            <p v-if="minLikesError" role="alert" class="mb-3 text-xs text-red-600 dark:text-red-400">{{ minLikesError }}</p>

            <ErrorState v-if="candidatesError" :title="t('highlight.candidatesFailed')" :description="candidatesError" retry :retrying="candidatesBusy" @retry="loadCandidates" />
            <EmptyState v-else-if="candidates && candidates.length === 0" icon="pi pi-search" :title="t('highlight.candidatesEmpty')" :description="t('highlight.candidatesEmptyHint')" />
            <p v-else-if="!candidates" class="m-0 text-xs text-muted-color">{{ t('highlight.candidatesIdle') }}</p>
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                <li v-for="post in candidates" :key="post.id" class="flex flex-wrap items-start justify-between gap-3 py-2.5">
                    <div class="flex min-w-0 flex-1 flex-col gap-0.5 text-[13px]">
                        <ContentPostCell :post="post" />
                        <span class="text-xs tabular-nums text-muted-color">{{ t('highlight.candidateStats', { likes: post.likeCount, comments: post.commentCount, views: post.viewCount }) }} · {{ time.listLabel(post.createdAt) }}</span>
                    </div>
                    <Button v-if="canManage" type="button" :label="t('highlight.sendToHighlight')" icon="pi pi-bookmark" size="small" severity="secondary" variant="outlined" :loading="sendingId === post.id" :disabled="sendingId !== null && sendingId !== post.id" @click="sendCandidate(post)" />
                </li>
            </ul>
        </AppGadget>

        <HighlightFormDialog v-model:visible="formOpen" :item="editing" :actions="formActions" :saving="saving" :server-errors="formFieldErrors" :form-error="formError" @save="save" />
        <HighlightOpinionsDrawer v-model:visible="opinionsOpen" :highlight="opinionsTarget" :actions="opinionActions" :can-manage="canManage" @changed="reloadQuietly" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type { HighlightCandidateQuery, HighlightItem, HighlightListView, HighlightSaveRequest } from '~/composables/content/api/useHighlightApi.types';
import type { BoardListItem } from '~/composables/board/api/useBoardApi.types';
import type { HighlightFormActions } from '~/components/highlight/HighlightFormDialog.types';
import type { HighlightOpinionActions } from '~/components/highlight/HighlightOpinionsDrawer.types';
import UtilHighlight from '~/utils/util-highlight';
import UtilHighlightMessages from '~/utils/util-highlight-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('highlight.title') });

const api = useHighlightApi();
const fileApi = useFileApi();
const boardApi = useBoardApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const ids = { days: useId(), minLikes: useId(), board: useId() };

/** 바꾸기 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('highlight.manage'));
const canReadBoards = computed(() => can('board.read'));

/** 이미 바뀐 하이라이트(다른 운영자가 지웠거나 순서가 바뀌었다) — 목록을 다시 받는다 */
const STALE_CODES: ReadonlySet<string> = new Set(['HIGHLIGHT_NOT_FOUND']);

const list = ref<HighlightListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const ordering = ref(false);
const togglingId = ref<string | null>(null);
const removingId = ref<string | null>(null);

onMounted(() => {
    void reload();
    if (canReadBoards.value) void loadBoards();
});

let request = 0;
async function reload(): Promise<void> {
    const mine = ++request;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list();
        if (mine !== request) return;
        list.value = found;
        // 의견 서랍이 열린 하이라이트의 줄도 새 값으로
        if (opinionsTarget.value) opinionsTarget.value = found.items.find((item) => item.id === opinionsTarget.value?.id) ?? opinionsTarget.value;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = errorOf(e);
        if (list.value) toast.error(loadError.value.message, 8000);
    } finally {
        if (mine === request) busy.value = false;
    }
}

/** 의견을 바꾼 뒤 수만 맞춘다 — 실패는 조용히 */
function reloadQuietly(): void {
    void reload();
}

/** 처리 실패 — 이미 바뀐 하이라이트면 목록을 다시 받는다 */
function fail(e: unknown): void {
    const error = errorOf(e);
    toast.error(error.fields?.ids ?? error.fields?.visible ?? error.message, 8000);
    if (STALE_CODES.has(error.code) || error.fields?.ids) void reload();
}

async function move(item: HighlightItem, delta: -1 | 1): Promise<void> {
    if (!list.value || ordering.value) return;
    const next = UtilHighlight.moved(
        list.value.items.map((row) => row.id),
        item.id,
        delta,
    );
    if (!next) return;
    ordering.value = true;
    try {
        await api.reorder(next);
        await reload();
    } catch (e) {
        fail(e);
    } finally {
        ordering.value = false;
    }
}

async function setVisible(item: HighlightItem, visible: boolean): Promise<void> {
    if (togglingId.value) return;
    togglingId.value = item.id;
    try {
        const updated = await api.setVisible(item.id, visible);
        replace(updated);
        toast.success(visible ? t('highlight.shown') : t('highlight.hidden'));
        void reload();
    } catch (e) {
        fail(e);
    } finally {
        togglingId.value = null;
    }
}

function replace(updated: HighlightItem): void {
    if (!list.value) return;
    list.value = { ...list.value, items: list.value.items.map((row) => (row.id === updated.id ? updated : row)) };
}

async function remove(item: HighlightItem): Promise<void> {
    const ok = await confirm({ title: t('highlight.deleteTitle'), message: t('highlight.deleteMessage', { title: UtilHighlight.displayTitle(item) }), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    removingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('highlight.deleted'));
        await reload();
        if (candidates.value) void loadCandidates();
    } catch (e) {
        fail(e);
    } finally {
        removingId.value = null;
    }
}

/* ── 만들기·고치기 ── */
const formOpen = ref(false);
const editing = ref<HighlightItem | null>(null);
const saving = ref(false);
const formFieldErrors = ref<Record<string, string>>({});
const formError = ref('');

const formActions: HighlightFormActions = {
    searchPosts: (q, signal) => api.searchPosts(q, signal),
    uploadImage: async (file) => (await fileApi.upload(file, 'HIGHLIGHT_IMAGE')).fileId,
};

function openCreate(): void {
    editing.value = null;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

function openEdit(item: HighlightItem): void {
    editing.value = item;
    formFieldErrors.value = {};
    formError.value = '';
    formOpen.value = true;
}

async function save(body: HighlightSaveRequest): Promise<void> {
    if (saving.value) return;
    saving.value = true;
    formFieldErrors.value = {};
    formError.value = '';
    const target = editing.value;
    try {
        if (target) await api.update(target.id, body);
        else await api.create(body);
        formOpen.value = false;
        toast.success(target ? t('highlight.updated') : t('highlight.created'));
        await reload();
        if (!target && candidates.value) void loadCandidates();
    } catch (e) {
        const error = errorOf(e);
        if (error.code === 'HIGHLIGHT_NOT_FOUND') {
            formOpen.value = false;
            fail(error);
        } else if (error.fields && Object.keys(error.fields).length) {
            // 글 오류(`HIGHLIGHT_POST_INVALID`)도 `fields.postId` 로 까닭이 온다
            formFieldErrors.value = { ...error.fields };
            const known = ['postId', 'title', 'summary', 'showUntil', 'showFrom', 'imageFileId'];
            formError.value = Object.entries(error.fields)
                .filter(([key]) => !known.includes(key))
                .map(([, message]) => message)
                .join(' ');
            // 기간 시작(`showFrom`)은 따로 칸이 없어 아래 한 줄로
            if (error.fields.showFrom) formError.value = [formError.value, error.fields.showFrom].filter(Boolean).join(' ');
        } else {
            // 409 HIGHLIGHT_DUPLICATE 등 — 서버 문구 그대로
            formError.value = error.message;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 의견 관리 ── */
const opinionsOpen = ref(false);
const opinionsTarget = ref<HighlightItem | null>(null);

const opinionActions: HighlightOpinionActions = {
    load: (query) => api.opinions(requireTarget(), query),
    pin: (commentId, on) => (on ? api.pin(requireTarget(), commentId) : api.unpin(requireTarget(), commentId)),
    hide: (commentId, on) => (on ? api.hide(requireTarget(), commentId) : api.unhide(requireTarget(), commentId)),
};

function requireTarget(): string {
    return opinionsTarget.value?.id ?? '';
}

function openOpinions(item: HighlightItem): void {
    opinionsTarget.value = item;
    opinionsOpen.value = true;
}

/* ── 후보 ── */
const candidateQuery = ref<HighlightCandidateQuery>({ days: UtilHighlight.DAYS_DEFAULT, minLikes: UtilHighlight.MIN_LIKES_DEFAULT, boardId: null });
const minLikesText = ref(String(UtilHighlight.MIN_LIKES_DEFAULT));
const candidates = ref<AdminPostRef[] | null>(null);
const candidatesBusy = ref(false);
const candidatesError = ref('');
const sendingId = ref<string | null>(null);
const boards = ref<BoardListItem[]>([]);

const dayOptions = computed(() => UtilHighlight.DAY_OPTIONS.map((value) => ({ value, label: UtilHighlightMessages.days(t, value) })));
const boardOptions = computed(() => boards.value.filter((board) => board.active).map((board) => ({ value: board.id, label: localizedName(board.name, board.nameEn) })));
const minLikesError = computed(() => UtilHighlightMessages.candidateProblem(t, UtilHighlight.minLikesProblem(minLikesText.value)));

async function loadBoards(): Promise<void> {
    try {
        boards.value = (await boardApi.list()).boards;
    } catch {
        // 게시판 고르개만 비운다 — 후보는 전체 게시판으로 찾을 수 있다
        boards.value = [];
    }
}

let candidateRequest = 0;
async function loadCandidates(): Promise<void> {
    if (minLikesError.value) return;
    const mine = ++candidateRequest;
    candidatesBusy.value = true;
    candidatesError.value = '';
    try {
        const found = await api.candidates({ ...candidateQuery.value, minLikes: Number(minLikesText.value.trim()) });
        if (mine !== candidateRequest) return;
        candidates.value = found.items;
    } catch (e) {
        if (mine !== candidateRequest) return;
        const error = errorOf(e);
        candidatesError.value = error.fields ? Object.values(error.fields).join(' ') : error.message;
    } finally {
        if (mine === candidateRequest) candidatesBusy.value = false;
    }
}

async function sendCandidate(post: AdminPostRef): Promise<void> {
    if (sendingId.value) return;
    sendingId.value = post.id;
    try {
        await api.create({ postId: post.id, title: null, summary: null, imageFileId: null, visible: null, showFrom: null, showUntil: null, debate: null });
        toast.success(t('highlight.sent', { title: post.title }));
        await reload();
        void loadCandidates();
    } catch (e) {
        const error = errorOf(e);
        toast.error(error.fields?.postId ?? error.message, 8000);
        void loadCandidates();
    } finally {
        sendingId.value = null;
    }
}
</script>
