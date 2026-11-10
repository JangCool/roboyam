<template>
    <!--
        제재 내역 `/boards/{slug}/sanctions?scope=ALL|ACTIVE|ENDED&page=` — 그 게시판 매니저·스텝만(board-sanction-contract 「화면 > user-web」).
        매니저 = 그 게시판 전체(처리자 닉네임 + 매니저/스텝, 진행 중 쓰기 제한 [해제] + 사유) · 스텝 = 내가 준 것만(처리자 칸 없음, 해제 없음).
        매니저·스텝이 아니면(목록이 `404 BOARD_STAFF_NOT_FOUND`) 404 안내 — 누가 운영진인지 새지 않게 다른 화면과 같은 「찾을 수 없어요」.
        범위 거르기(전체·진행 중·끝남) + 쪽 번호. 주소의 쿼리가 화면 상태다. 「진행 중」은 진행 중인 쓰기 제한만이다(경고 제외 — 결정 11).
    -->
    <div class="content-card">
        <div v-if="state === 'loading' && !page" role="status" :aria-label="t('boardSanction.listLoading')" data-testid="board-sanctions-loading">
            <div class="h-6 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-4 h-9 w-64 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 4" :key="n" class="mt-3 h-20 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('boardSanction.notFoundTitle')" :description="t('boardSanction.notFoundText')" data-testid="board-sanctions-notfound">
            <Button as="router-link" :to="`/boards/${slug}`" :label="t('boardSanction.backToBoard')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'error' && !page" :title="t('boardSanction.listFailed')" :description="errorMessage" retry data-testid="board-sanctions-error" @retry="load" />

        <template v-else-if="page && board">
            <BoardStaffTabs :slug="slug" :board-name="localizedName(board.name, board.nameEn)" active="sanctions" />
            <header class="mt-3 flex flex-wrap items-start gap-x-3 gap-y-1">
                <div class="min-w-0 flex-1">
                    <h1 class="m-0 text-lg font-bold">{{ t('boardSanction.listTitle') }}</h1>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ page.manager ? t('boardSanction.listHintManager') : t('boardSanction.listHintStaff') }}</p>
                </div>
                <span v-if="staffTools?.abilities.sanction" class="text-xs tabular-nums text-muted-color" data-testid="board-sanctions-quota">{{ UtilBoardSanctionMessages.quotaLine(t, staffTools.sanction) }}</span>
            </header>

            <!-- 범위 거르기 -->
            <div class="mt-4 flex flex-wrap items-center gap-2 border-b border-surface pb-3">
                <SelectButton
                    :model-value="scope"
                    :options="scopeOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    size="small"
                    :aria-label="t('boardSanction.scopeLabel')"
                    data-testid="board-sanctions-scope"
                    @update:model-value="onScope"
                />
                <span class="ml-auto text-xs tabular-nums text-muted-color">{{ t('boardSanction.totalCount', { n: page.totalCount }) }}</span>
            </div>

            <div :aria-busy="state === 'loading'">
                <ErrorState v-if="state === 'error'" :title="t('boardSanction.listFailed')" :description="errorMessage" retry @retry="load" />
                <EmptyState v-else-if="page.items.length === 0" :title="scope === 'ALL' ? t('boardSanction.emptyTitle') : t('boardSanction.emptyScopeTitle')" icon="pi pi-shield" data-testid="board-sanctions-empty" />

                <ul v-else class="m-0 list-none p-0" :class="state === 'loading' ? 'opacity-60' : ''">
                    <li v-for="row in page.items" :key="row.id" class="border-b border-surface py-3" :data-status="row.status" data-testid="board-sanction-row">
                        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                            <span :class="[TAG, UtilSanction.toneClass(row.status === 'VOIDED' ? 'gray' : UtilBoardSanction.kindTone(row.kind))]">
                                <span class="relative top-[-0.09em]">{{ UtilBoardSanctionMessages.kindLabel(t, row.kind) }}</span>
                            </span>
                            <span :class="[TAG, UtilSanction.toneClass(UtilBoardSanction.statusTone(row.kind, row.status))]">
                                <span class="relative top-[-0.09em]">{{ UtilBoardSanctionMessages.statusLabel(t, row.kind, row.status) }}</span>
                            </span>
                            <!-- 봉인된 익명 글 작성자(`target.sealed` — anonymous-board-contract 정책 9)는 닉네임 대신 익명 아이콘 + 「익명 글 작성자」 -->
                            <b class="flex min-w-0 items-center gap-1 text-sm font-semibold"><AnonymousAuthorIcon v-if="row.target.sealed" class="text-muted-color" /><span class="truncate">{{ UtilBoardSanctionMessages.targetName(t, row.target) }}</span></b>
                            <span class="ml-auto text-xs tabular-nums text-muted-color">#{{ row.no }} · {{ time.date(row.createdAt) }}</span>
                        </p>
                        <p class="mb-0 mt-1.5 break-words text-sm leading-snug [overflow-wrap:anywhere]" :class="row.status === 'VOIDED' ? 'text-muted-color line-through decoration-surface-400' : ''">
                            {{ UtilBoardSanctionMessages.reasonLine(t, row.reasonCode, row.reasonText) }}
                        </p>
                        <p class="m-0 mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs text-muted-color">
                            <span v-if="row.endsAt">{{ t('sanction.periodLine', { period: UtilSanctionMessages.period(row.startsAt, row.endsAt, time) }) }}</span>
                            <!-- 처리자 — 매니저에게만 온다 -->
                            <span v-if="row.issuer" data-testid="board-sanction-issuer">
                                {{ UtilBoardSanctionMessages.issuerLine(t, row.issuer) }}
                            </span>
                            <NuxtLink v-if="row.source" :to="sourceLink(row.source)" class="text-primary hover:underline">{{ row.source.commentId ? t('boardSanction.sourceComment') : t('boardSanction.sourcePost') }}</NuxtLink>
                        </p>
                        <p v-if="row.lift" class="mb-0 mt-1.5 rounded-lg bg-surface-100 px-2.5 py-1.5 text-xs dark:bg-surface-800/60" data-testid="board-sanction-lift">
                            {{ t('boardSanction.liftedLine', { date: UtilSanctionMessages.releaseText(row.lift.at, time), reason: row.lift.reason }) }}
                        </p>
                        <div v-if="row.canLift" class="mt-2">
                            <Button type="button" :label="t('boardSanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" data-testid="board-sanction-lift-open" @click="openLift(row)" />
                        </div>
                    </li>
                </ul>
                <BoardPager :page="page.page" :total-pages="page.totalPages" :to="pageLink" />
            </div>
        </template>

        <!-- 해제(매니저) — 사유 필수 -->
        <Dialog v-model:visible="liftOpen" modal :header="t('boardSanction.liftTitle')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false">
            <form v-if="liftTarget" class="flex flex-col gap-3" novalidate @submit.prevent="submitLift">
                <p class="m-0 text-sm">{{ liftTarget.target.sealed ? t('boardSanction.liftTextSealed', { kind: UtilBoardSanctionMessages.kindLabel(t, liftTarget.kind) }) : t('boardSanction.liftText', { name: liftTarget.target.nickname ?? '', kind: UtilBoardSanctionMessages.kindLabel(t, liftTarget.kind) }) }}</p>
                <LabeledField :label="t('boardSanction.liftReasonLabel')" :error="liftReasonError">
                    <template #aside>
                        <span class="text-xs tabular-nums text-muted-color">{{ t('boardSanction.textCounter', { n: UtilBoardSanction.textLength(liftReason), max: UtilBoardSanction.LIFT_REASON_MAX }) }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="liftReason" rows="3" auto-resize fluid :disabled="lifting" :invalid="invalid" :aria-describedby="describedBy" data-testid="board-sanction-lift-reason" />
                    </template>
                </LabeledField>
                <p class="m-0 text-xs text-muted-color">{{ t('boardSanction.liftNotice') }}</p>
                <Message v-if="liftError" severity="error" variant="simple" size="small">{{ liftError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="liftOpen = false" />
                    <Button type="submit" :label="t('boardSanction.liftSubmit')" :loading="lifting" data-testid="board-sanction-lift-submit" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { BoardSanctionScope, BoardStaffSanctionPage, BoardStaffSanctionRow } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * 게시판 제재 내역(매니저·스텝) — 흐름: 게시판 읽기 → 목록 한 쪽(운영진이 아니면 `404 BOARD_STAFF_NOT_FOUND`) → (매니저) [해제] 대화창.
 * 한 화면의 값이라 페이지 `ref` 로 둔다. 서버는 `useBoardApi`·`useBoardStaffApi`.
 */
definePageMeta({ middleware: 'auth' });

const SCOPES: readonly BoardSanctionScope[] = ['ALL', 'ACTIVE', 'ENDED'];
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
const scope = computed(() => UtilBoardSanction.scopeOf(route.query.scope));
const pageNo = computed(() => UtilBoardSanction.pageOf(route.query.page));
const scopeOptions = computed(() =>
    SCOPES.map((value) => ({ value, label: t(value === 'ALL' ? 'boardSanction.scopeAll' : value === 'ACTIVE' ? 'boardSanction.scopeActive' : 'boardSanction.scopeEnded') })),
);

type State = 'loading' | 'ready' | 'notfound' | 'error';
const state = ref<State>('loading');
const errorMessage = ref('');
const board = ref<BoardDetail | null>(null);
const page = ref<BoardStaffSanctionPage | null>(null);
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
        // 운영진인지는 목록이 가른다(`404 BOARD_STAFF_NOT_FOUND`) — `viewer.staffTools` 는 제재를 줄 수 있는 사람에게만 와서, 자리만 있고 역할 권한이 없는 매니저·스텝도 내역은 본다
        const res = await staffApi.list(b.id, { scope: scope.value, page: pageNo.value }, controller.signal);
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

function link(change: { scope?: BoardSanctionScope; page?: number }) {
    const nextScope = change.scope ?? scope.value;
    const nextPage = change.page ?? pageNo.value;
    return { path: UtilBoardSanction.listPath(slug.value), query: { ...(nextScope !== 'ALL' ? { scope: nextScope } : {}), ...(nextPage > 1 ? { page: String(nextPage) } : {}) } };
}
function pageLink(n: number) {
    return link({ page: n });
}
function onScope(value: BoardSanctionScope | null): void {
    if (value) void router.push(link({ scope: value, page: 1 }));
}
function sourceLink(source: NonNullable<BoardStaffSanctionRow['source']>): string {
    return source.commentId ? `/posts/${source.postId}#comment-${source.commentId}` : `/posts/${source.postId}`;
}

watch(
    () => [slug.value, scope.value, pageNo.value] as const,
    () => void load(),
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

/* ── 해제(매니저) ── */
const liftOpen = ref(false);
const liftTarget = ref<BoardStaffSanctionRow | null>(null);
const liftReason = ref('');
const liftTried = ref(false);
const lifting = ref(false);
const liftError = ref('');
const liftFieldError = ref('');
const liftReasonError = computed(() => {
    if (liftFieldError.value) return liftFieldError.value;
    const problem = UtilBoardSanction.liftReasonProblem(liftReason.value);
    if (problem === 'TOO_LONG') return t('boardSanction.textTooLong', { n: UtilBoardSanction.textLength(liftReason.value), max: UtilBoardSanction.LIFT_REASON_MAX });
    return liftTried.value && problem === 'EMPTY' ? t('boardSanction.liftReasonRequired') : '';
});

function openLift(row: BoardStaffSanctionRow): void {
    liftTarget.value = row;
    liftReason.value = '';
    liftTried.value = false;
    liftError.value = '';
    liftFieldError.value = '';
    liftOpen.value = true;
}

async function submitLift(): Promise<void> {
    const row = liftTarget.value;
    liftTried.value = true;
    if (!row || lifting.value || UtilBoardSanction.liftReasonProblem(liftReason.value)) return;
    lifting.value = true;
    liftError.value = '';
    liftFieldError.value = '';
    try {
        const updated = await staffApi.lift(row.id, { reason: liftReason.value.trim() });
        if (page.value) page.value = { ...page.value, items: page.value.items.map((r) => (r.id === updated.id ? updated : r)) };
        liftOpen.value = false;
        toast.success(updated.target.sealed ? t('boardSanction.liftedToastSealed') : t('boardSanction.liftedToast', { name: updated.target.nickname ?? '' }));
    } catch (e) {
        if (e instanceof ApiError && e.field('reason')) liftFieldError.value = e.field('reason') ?? '';
        else if (e instanceof ApiError && (e.code === 'BOARD_SANCTION_NOT_LIFTABLE' || e.status === 403 || e.status === 404)) {
            // 그새 끝났거나 해제됐거나 권한이 바뀌었다 — 닫고 알린 뒤 목록을 다시 받는다
            liftOpen.value = false;
            toast.error(e.message, 8000);
            void load();
        } else liftError.value = UtilBoardMessages.of(t, e);
    } finally {
        lifting.value = false;
    }
}

useSeoMeta({
    title: () => (board.value ? `${t('boardSanction.listTitle')} · ${localizedName(board.value.name, board.value.nameEn)}` : t('boardSanction.listTitle')),
    robots: 'noindex',
});
</script>
