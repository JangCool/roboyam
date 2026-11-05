<template>
    <!--
        처리 내역 `/boards/{slug}/actions?kind=ALL|POST|COMMENT&page=` — 그 게시판 매니저·스텝만(board-staff-action-contract 「화면 > user-web」). 위에 「처리 내역 · 제재 내역」 탭 머리(`BoardStaffTabs`).
        매니저 = 그 게시판 전체(처리자 닉네임 + 매니저/스텝, 되돌리지 않은 처리에 [되돌리기] + 사유 1~200자) · 스텝 = 내가 한 것만(처리자 칸 없음, 되돌리기 없음).
        매니저·스텝이 아니면(목록이 `404 BOARD_STAFF_NOT_FOUND`) 404 안내 — 누가 운영진인지 새지 않게 다른 화면과 같은 「찾을 수 없어요」.
        종류 거르기(전체·글·댓글) + 쪽 번호. 주소의 쿼리가 화면 상태다. 대상 제목·댓글 앞부분·작성자는 처리 때 스냅숏이다.
    -->
    <div class="content-card">
        <div v-if="state === 'loading' && !page" role="status" :aria-label="t('boardStaffAction.listLoading')" data-testid="board-actions-loading">
            <div class="h-6 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-4 h-9 w-64 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 4" :key="n" class="mt-3 h-20 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('boardSanction.notFoundTitle')" :description="t('boardSanction.notFoundText')" data-testid="board-actions-notfound">
            <Button as="router-link" :to="`/boards/${slug}`" :label="t('boardSanction.backToBoard')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'error' && !page" :title="t('boardStaffAction.listFailed')" :description="errorMessage" retry data-testid="board-actions-error" @retry="load" />

        <template v-else-if="page && board">
            <BoardStaffTabs :slug="slug" :board-name="localizedName(board.name, board.nameEn)" active="actions" />
            <header class="mt-3 flex flex-wrap items-start gap-x-3 gap-y-1">
                <div class="min-w-0 flex-1">
                    <h1 class="m-0 text-lg font-bold">{{ t('boardStaffAction.listTitle') }}</h1>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ page.manager ? t('boardStaffAction.listHintManager') : t('boardStaffAction.listHintStaff') }}</p>
                </div>
                <span v-if="staffTools" class="flex flex-col items-end text-xs tabular-nums text-muted-color" data-testid="board-actions-quota">
                    <span v-if="staffTools.abilities.postHide || staffTools.abilities.postDelete">{{ UtilBoardStaffActionMessages.quotaLine(t, 'POST_HIDE', staffTools.post) }}</span>
                    <span v-if="staffTools.abilities.commentDelete">{{ UtilBoardStaffActionMessages.quotaLine(t, 'COMMENT_DELETE', staffTools.comment) }}</span>
                </span>
            </header>

            <!-- 종류 거르기 -->
            <div class="mt-4 flex flex-wrap items-center gap-2 border-b border-surface pb-3">
                <SelectButton
                    :model-value="filter"
                    :options="filterOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    size="small"
                    :aria-label="t('boardStaffAction.filterLabel')"
                    data-testid="board-actions-filter"
                    @update:model-value="onFilter"
                />
                <span class="ml-auto text-xs tabular-nums text-muted-color">{{ t('boardSanction.totalCount', { n: page.totalCount }) }}</span>
            </div>

            <div :aria-busy="state === 'loading'">
                <ErrorState v-if="state === 'error'" :title="t('boardStaffAction.listFailed')" :description="errorMessage" retry @retry="load" />
                <EmptyState
                    v-else-if="page.items.length === 0"
                    :title="filter === 'ALL' ? t('boardStaffAction.emptyTitle') : t('boardStaffAction.emptyFilterTitle')"
                    icon="pi pi-shield"
                    data-testid="board-actions-empty"
                />

                <ul v-else class="m-0 list-none p-0" :class="state === 'loading' ? 'opacity-60' : ''">
                    <li v-for="row in page.items" :key="row.id" class="border-b border-surface py-3" :data-kind="row.kind" data-testid="board-action-row">
                        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span :class="[TAG, UtilSanction.toneClass(row.restored ? 'gray' : UtilBoardStaffAction.kindTone(row.kind))]">
                                <span class="relative top-[-0.09em]">{{ UtilBoardStaffActionMessages.kindLabel(t, row.kind) }}</span>
                            </span>
                            <span v-if="row.restored" :class="[TAG, UtilSanction.toneClass('green')]" data-testid="board-action-restored-badge">
                                <span class="relative top-[-0.09em]">{{ t('boardStaffAction.restoredBadge') }}</span>
                            </span>
                            <span class="text-xs text-muted-color">{{ UtilBoardStaffActionMessages.reasonLabel(t, row.reasonCode) }}</span>
                            <span class="ml-auto text-xs tabular-nums text-muted-color">{{ time.full(row.createdAt) }}</span>
                        </p>
                        <!-- 대상 — 글 제목(댓글이면 그 글) · 댓글 앞부분 -->
                        <NuxtLink
                            :to="targetLink(row)"
                            class="mt-1.5 block break-words text-sm font-semibold leading-snug [overflow-wrap:anywhere] hover:underline"
                            :class="row.restored ? 'text-muted-color' : ''"
                            data-testid="board-action-target"
                        >
                            {{ row.target.title }}
                        </NuxtLink>
                        <p v-if="row.target.excerpt" class="mb-0 mt-1 break-words rounded-lg bg-surface-100 px-2.5 py-1.5 text-sm leading-snug [overflow-wrap:anywhere] dark:bg-surface-800/60">
                            {{ row.target.excerpt }}
                        </p>
                        <p class="m-0 mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-muted-color">
                            <span>{{ t('boardStaffAction.authorLine', { name: row.targetAuthor.nickname }) }}</span>
                            <!-- 처리자 — 매니저에게만 온다 -->
                            <span v-if="row.actor" data-testid="board-action-actor">{{ UtilBoardStaffActionMessages.actorLine(t, row.actor) }}</span>
                            <span v-if="row.restored" data-testid="board-action-restored">{{ UtilBoardStaffActionMessages.restoredLine(t, row.restored.at, time) }}</span>
                        </p>
                        <div v-if="row.canRestore && !row.restored" class="mt-2">
                            <Button type="button" :label="t('boardStaffAction.restore')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" data-testid="board-action-restore-open" @click="openRestore(row)" />
                        </div>
                    </li>
                </ul>
                <BoardPager :page="page.page" :total-pages="page.totalPages" :to="pageLink" />
            </div>
        </template>

        <!-- 되돌리기(매니저) — 사유 1~200자 -->
        <Dialog v-model:visible="restoreOpen" modal :header="t('boardStaffAction.restoreTitle')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false">
            <form v-if="restoreTarget" class="flex flex-col gap-3" novalidate @submit.prevent="submitRestore">
                <p class="m-0 text-sm">{{ UtilBoardStaffActionMessages.restoreText(t, restoreTarget.targetAuthor.nickname, restoreTarget.kind) }}</p>
                <p class="m-0 truncate text-xs text-muted-color">{{ restoreTarget.target.excerpt || restoreTarget.target.title }}</p>
                <LabeledField :label="t('boardStaffAction.restoreReasonLabel')" :error="restoreReasonError">
                    <template #aside>
                        <span class="text-xs tabular-nums text-muted-color">{{ t('boardSanction.textCounter', { n: UtilBoardStaffAction.textLength(restoreReason), max: UtilBoardStaffAction.RESTORE_REASON_MAX }) }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="restoreReason" rows="3" auto-resize fluid :disabled="restoring" :invalid="invalid" :aria-describedby="describedBy" data-testid="board-action-restore-reason" />
                    </template>
                </LabeledField>
                <p class="m-0 text-xs text-muted-color">{{ t('boardStaffAction.restoreNotice') }}</p>
                <Message v-if="restoreError" severity="error" variant="simple" size="small">{{ restoreError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="restoreOpen = false" />
                    <Button type="submit" :label="t('boardStaffAction.restoreSubmit')" :loading="restoring" data-testid="board-action-restore-submit" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { BoardStaffActionFilter, BoardStaffActionPage, BoardStaffActionRow } from '~/composables/board/api/useBoardStaffApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';
import UtilSanction from '~/utils/util-sanction';

/**
 * 게시판 처리 내역(매니저·스텝) — 흐름: 게시판 읽기 → 목록 한 쪽(운영진이 아니면 `404 BOARD_STAFF_NOT_FOUND`) → (매니저) [되돌리기] 대화창.
 * 한 화면의 값이라 페이지 `ref` 로 둔다. 서버는 `useBoardApi`·`useBoardStaffApi`.
 */
definePageMeta({ middleware: 'auth' });

const FILTERS: readonly BoardStaffActionFilter[] = ['ALL', 'POST', 'COMMENT'];
const FILTER_KEY: Record<BoardStaffActionFilter, string> = { ALL: 'boardStaffAction.filterAll', POST: 'boardStaffAction.filterPost', COMMENT: 'boardStaffAction.filterComment' };
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const boardApi = useBoardApi();
const staffApi = useBoardStaffApi();
const toast = useAppToast();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();

const slug = computed(() => String(route.params.slug ?? ''));
const filter = computed(() => UtilBoardStaffAction.filterOf(route.query.kind));
const pageNo = computed(() => UtilBoardSanction.pageOf(route.query.page));
const filterOptions = computed(() => FILTERS.map((value) => ({ value, label: t(FILTER_KEY[value]) })));

type State = 'loading' | 'ready' | 'notfound' | 'error';
const state = ref<State>('loading');
const errorMessage = ref('');
const board = ref<BoardDetail | null>(null);
const page = ref<BoardStaffActionPage | null>(null);
const staffTools = computed(() => board.value?.viewer.staffTools ?? null);

let seq = 0;
let abort: AbortController | null = null;
async function load(): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    state.value = 'loading';
    try {
        // 게시판은 처음(또는 다른 게시판으로 옮겼을 때)만 읽는다 — 거르기·쪽을 바꿀 때는 목록만
        const b = board.value?.slug === slug.value ? board.value : await boardApi.board(slug.value, controller.signal);
        if (mine !== seq) return;
        board.value = b;
        // 운영진인지는 목록이 가른다(`404 BOARD_STAFF_NOT_FOUND`)
        const res = await staffApi.actions(b.id, { kind: filter.value, page: pageNo.value }, controller.signal);
        if (mine !== seq) return;
        page.value = res;
        state.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        // 없는 게시판·매니저·스텝이 아님(404) — 같은 안내
        if (e instanceof ApiError && (e.status === 404 || e.code === 'BOARD_INACTIVE')) {
            page.value = null;
            state.value = 'notfound';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}

function link(change: { kind?: BoardStaffActionFilter; page?: number }) {
    const nextKind = change.kind ?? filter.value;
    const nextPage = change.page ?? pageNo.value;
    return { path: UtilBoardStaffAction.listPath(slug.value), query: { ...(nextKind !== 'ALL' ? { kind: nextKind } : {}), ...(nextPage > 1 ? { page: String(nextPage) } : {}) } };
}
function pageLink(n: number) {
    return link({ page: n });
}
function onFilter(value: BoardStaffActionFilter | null): void {
    if (value) void router.push(link({ kind: value, page: 1 }));
}
function targetLink(row: BoardStaffActionRow): string {
    return row.target.commentId ? `/posts/${row.target.postId}#comment-${row.target.commentId}` : `/posts/${row.target.postId}`;
}

watch(
    () => [slug.value, filter.value, pageNo.value] as const,
    () => void load(),
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

/* ── 되돌리기(매니저) ── */
const restoreOpen = ref(false);
const restoreTarget = ref<BoardStaffActionRow | null>(null);
const restoreReason = ref('');
const restoreTried = ref(false);
const restoring = ref(false);
const restoreError = ref('');
const restoreFieldError = ref('');
const restoreReasonError = computed(() => {
    if (restoreFieldError.value) return restoreFieldError.value;
    const problem = UtilBoardStaffAction.restoreReasonProblem(restoreReason.value);
    if (problem === 'TOO_LONG') return UtilBoardStaffActionMessages.restoreReasonProblem(t, problem, UtilBoardStaffAction.textLength(restoreReason.value));
    return restoreTried.value && problem === 'EMPTY' ? UtilBoardStaffActionMessages.restoreReasonProblem(t, problem, 0) : '';
});

function openRestore(row: BoardStaffActionRow): void {
    restoreTarget.value = row;
    restoreReason.value = '';
    restoreTried.value = false;
    restoreError.value = '';
    restoreFieldError.value = '';
    restoreOpen.value = true;
}

async function submitRestore(): Promise<void> {
    const row = restoreTarget.value;
    restoreTried.value = true;
    if (!row || restoring.value || UtilBoardStaffAction.restoreReasonProblem(restoreReason.value)) return;
    restoring.value = true;
    restoreError.value = '';
    restoreFieldError.value = '';
    try {
        const updated = await staffApi.restore(row.id, { reason: UtilBoardStaffAction.textBody(restoreReason.value) });
        if (page.value) page.value = { ...page.value, items: page.value.items.map((r) => (r.id === updated.id ? updated : r)) };
        restoreOpen.value = false;
        toast.success(t('boardStaffAction.restoredToast'));
    } catch (e) {
        if (e instanceof ApiError && e.field('reason')) restoreFieldError.value = e.field('reason') ?? '';
        else if (UtilBoardStaffAction.restoreStale(e)) {
            // 그새 되돌려졌거나 글 상태가 바뀌었거나(파기 포함) 권한이 바뀌었다 — 닫고 알린 뒤 목록을 다시 받는다
            restoreOpen.value = false;
            toast.error(UtilBoardMessages.of(t, e), 8000);
            void load();
        } else restoreError.value = UtilBoardMessages.of(t, e);
    } finally {
        restoring.value = false;
    }
}

useSeoMeta({
    title: () => (board.value ? `${t('boardStaffAction.listTitle')} · ${localizedName(board.value.name, board.value.nameEn)}` : t('boardStaffAction.listTitle')),
    robots: 'noindex',
});
</script>
