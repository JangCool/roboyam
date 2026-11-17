<template>
    <!--
        칭호 신청 심사 `/boards/{slug}/titles?status=PENDING|ALL` — 그 게시판 매니저만(member-title-contract 「구현 결정」 5 — 연결된 칭호의 신청만, 자기 신청은 심사 못 한다).
        매니저가 아니면(목록이 404·403) 다른 운영 화면과 같은 「찾을 수 없어요」. 위에 「처리 내역 · 제재 내역 · 칭호 신청」 탭 머리(`BoardStaffTabs`).
        한 줄: 칭호 칩 · 신청한 회원(→ 공개 프로필) · 상태 · 조건 충족 표시 · (심사 중이고 내 신청이 아니면) [승인]·[거절]. 거절은 사유 필수, 승인 사유는 비워도 된다. 승인·거절은 감사 기록에 남는다(서버).
    -->
    <div class="content-card">
        <div v-if="state === 'loading' && !page" role="status" :aria-label="t('memberTitle.loading')" data-testid="board-titles-loading">
            <div class="h-6 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 3" :key="n" class="mt-3 h-20 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('boardSanction.notFoundTitle')" :description="t('boardSanction.notFoundText')" data-testid="board-titles-notfound">
            <Button as="router-link" :to="`/boards/${slug}`" :label="t('boardSanction.backToBoard')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'error' && !page" :title="t('memberTitle.staffLoadFailed')" :description="errorMessage" retry data-testid="board-titles-error" @retry="load" />

        <template v-else-if="page && board">
            <BoardStaffTabs :slug="slug" :board-name="localizedName(board.name, board.nameEn)" active="titles" />
            <header class="mt-3">
                <h1 class="m-0 text-lg font-bold">{{ t('memberTitle.staffTitle') }}</h1>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('memberTitle.staffHint') }}</p>
            </header>

            <div class="mt-4 flex flex-wrap items-center gap-2 border-b border-surface pb-3">
                <SelectButton
                    :model-value="status"
                    :options="statusOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    size="small"
                    :aria-label="t('memberTitle.staffStatusLabel')"
                    data-testid="board-titles-status"
                    @update:model-value="onStatus"
                />
            </div>

            <div :aria-busy="state === 'loading'">
                <ErrorState v-if="state === 'error'" :title="t('memberTitle.staffLoadFailed')" :description="errorMessage" retry @retry="load" />
                <EmptyState v-else-if="page.items.length === 0" :title="status === 'PENDING' ? t('memberTitle.staffEmptyPending') : t('memberTitle.staffEmpty')" icon="pi pi-verified" data-testid="board-titles-empty" />
                <ul v-else class="m-0 list-none p-0" :class="state === 'loading' ? 'opacity-60' : ''">
                    <li v-for="row in page.items" :key="row.id" class="border-b border-surface py-3" :data-status="row.status" data-testid="board-title-row">
                        <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                            <TitleChip :title="{ ...row, tier: null }" />
                            <span :class="[TAG, statusClass(row.status)]"><span class="relative top-[-0.09em]">{{ UtilMemberTitleMessages.status(t, row.status) }}</span></span>
                            <NuxtLink :to="UtilProfile.path(row.member.id)" class="min-w-0 truncate text-sm font-semibold hover:underline">{{ row.member.nickname }}</NuxtLink>
                            <span class="ml-auto text-xs tabular-nums text-muted-color">{{ time.date(row.createdAt) }}</span>
                        </p>
                        <ul class="m-0 mt-2 flex list-none flex-col gap-1 p-0 text-xs">
                            <li v-for="(condition, i) in row.conditions" :key="i" class="flex items-center gap-1.5" :class="condition.met ? '' : 'text-muted-color'">
                                <i :class="condition.met ? 'pi pi-check-circle text-green-600 dark:text-green-400' : 'pi pi-times-circle text-red-600 dark:text-red-400'" class="text-xs" aria-hidden="true" />
                                <span>{{ condition.label }}</span>
                                <span v-if="condition.current !== null && condition.target !== null" class="tabular-nums text-muted-color">({{ condition.current }}/{{ condition.target }})</span>
                                <span class="sr-only">{{ condition.met ? t('memberTitle.conditionMet') : t('memberTitle.conditionNotMet') }}</span>
                            </li>
                        </ul>
                        <p v-if="row.reason" class="mb-0 mt-1.5 rounded-lg bg-surface-100 px-2.5 py-1.5 text-xs dark:bg-surface-800/60">{{ t('memberTitle.reviewReasonLine', { reason: row.reason }) }}</p>
                        <div v-if="row.status === 'PENDING' && !row.mine" class="mt-2 flex gap-2">
                            <Button type="button" :label="t('memberTitle.approve')" icon="pi pi-check" size="small" :data-testid="`board-title-approve-${row.id}`" @click="openReview(row, true)" />
                            <Button type="button" :label="t('memberTitle.reject')" icon="pi pi-times" size="small" severity="secondary" variant="outlined" :data-testid="`board-title-reject-${row.id}`" @click="openReview(row, false)" />
                        </div>
                        <p v-else-if="row.status === 'PENDING'" class="m-0 mt-2 text-xs text-muted-color">{{ t('memberTitle.staffOwnApplication') }}</p>
                    </li>
                </ul>
            </div>
        </template>

        <!-- 승인·거절 — 거절은 사유 필수 -->
        <Dialog v-model:visible="reviewOpen" modal :header="reviewApprove ? t('memberTitle.approveTitle') : t('memberTitle.rejectTitle')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!reviewing">
            <form v-if="reviewTarget" class="flex flex-col gap-3" novalidate @submit.prevent="submitReview">
                <p class="m-0 text-sm">{{ reviewApprove ? t('memberTitle.approveText', { name: reviewTarget.member.nickname, label: reviewTarget.label }) : t('memberTitle.rejectText', { name: reviewTarget.member.nickname, label: reviewTarget.label }) }}</p>
                <LabeledField :label="reviewApprove ? t('memberTitle.approveReasonLabel') : t('memberTitle.rejectReasonLabel')" :error="reasonError">
                    <template #aside>
                        <span class="text-xs tabular-nums text-muted-color">{{ t('boardSanction.textCounter', { n: reason.trim().length, max: UtilMemberTitle.REASON_MAX }) }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="reason" rows="3" auto-resize fluid :disabled="reviewing" :invalid="invalid" :aria-describedby="describedBy" data-testid="board-title-reason" />
                    </template>
                </LabeledField>
                <p v-if="!reviewApprove" class="m-0 text-xs text-muted-color">{{ t('memberTitle.rejectNotice') }}</p>
                <Message v-if="reviewError" severity="error" variant="simple" size="small">{{ reviewError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="reviewing" @click="reviewOpen = false" />
                    <Button type="submit" :label="reviewApprove ? t('memberTitle.approve') : t('memberTitle.reject')" :severity="reviewApprove ? undefined : 'danger'" :loading="reviewing" data-testid="board-title-review-submit" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { BoardTitleApplication, TitleApplicationStatus } from '~/composables/title/api/useTitleApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilMemberTitle from '~/utils/util-member-title';
import UtilMemberTitleMessages from '~/utils/util-member-title-messages';
import UtilProfile from '~/utils/util-profile';

/**
 * 게시판 매니저의 칭호 신청 심사 — 흐름: 게시판 읽기 → 신청 목록(매니저가 아니면 404·403 → 「찾을 수 없어요」) → [승인]·[거절] 대화창.
 * 한 화면의 값이라 페이지 `ref` 로 둔다. 서버는 `useBoardApi`·`useTitleApi`(⚠ 칭호 쪽은 지금 목업).
 */
definePageMeta({ middleware: 'auth' });

type StatusFilter = 'PENDING' | 'ALL';
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const boardApi = useBoardApi();
const titleApi = useTitleApi();
const toast = useAppToast();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();

const slug = computed(() => String(route.params.slug ?? ''));
const status = computed<StatusFilter>(() => (route.query.status === 'ALL' ? 'ALL' : 'PENDING'));
const statusOptions = computed(() => [
    { value: 'PENDING' as const, label: t('memberTitle.staffPending') },
    { value: 'ALL' as const, label: t('common.all') },
]);

const state = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const errorMessage = ref('');
const board = ref<BoardDetail | null>(null);
/** 신청 목록(서버는 배열) — 받기 전은 null */
const page = ref<{ items: BoardTitleApplication[] } | null>(null);

let seq = 0;
let abort: AbortController | null = null;
async function load(): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    state.value = 'loading';
    try {
        const b = board.value?.slug === slug.value ? board.value : await boardApi.board(slug.value, controller.signal);
        if (mine !== seq) return;
        board.value = b;
        const res = await titleApi.staffApplications(slug.value, status.value === 'ALL' ? null : 'PENDING', controller.signal);
        if (mine !== seq) return;
        page.value = { items: res };
        state.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        // 없는 게시판·매니저가 아님 — 다른 운영 화면과 같은 안내(누가 매니저인지 새지 않게)
        if (e instanceof ApiError && (e.status === 404 || e.status === 403 || e.code === 'BOARD_INACTIVE')) {
            page.value = null;
            state.value = 'notfound';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        state.value = 'error';
    }
}

function onStatus(value: StatusFilter | null): void {
    if (value) void router.push({ path: UtilMemberTitle.staffPath(slug.value), query: value === 'ALL' ? { status: 'ALL' } : {} });
}

watch(
    () => [slug.value, status.value] as const,
    () => void load(),
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

function statusClass(s: TitleApplicationStatus): string {
    if (s === 'APPROVED') return 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200';
    if (s === 'REJECTED') return 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200';
    return 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200';
}

/* ── 승인·거절 ── */
const reviewOpen = ref(false);
const reviewTarget = ref<BoardTitleApplication | null>(null);
const reviewApprove = ref(true);
const reason = ref('');
const tried = ref(false);
const reviewing = ref(false);
const reviewError = ref('');
const reasonFieldError = ref('');
const reasonError = computed(() => {
    if (reasonFieldError.value) return reasonFieldError.value;
    const n = reason.value.trim().length;
    if (n > UtilMemberTitle.REASON_MAX) return t('boardSanction.textTooLong', { n, max: UtilMemberTitle.REASON_MAX });
    return tried.value && !reviewApprove.value && n === 0 ? t('memberTitle.rejectReasonRequired') : '';
});

function openReview(row: BoardTitleApplication, approve: boolean): void {
    reviewTarget.value = row;
    reviewApprove.value = approve;
    reason.value = '';
    tried.value = false;
    reviewError.value = '';
    reasonFieldError.value = '';
    reviewOpen.value = true;
}

async function submitReview(): Promise<void> {
    const row = reviewTarget.value;
    tried.value = true;
    if (!row || reviewing.value || reasonError.value) return;
    reviewing.value = true;
    reviewError.value = '';
    reasonFieldError.value = '';
    const body = { reason: reason.value.trim() };
    try {
        if (reviewApprove.value) await titleApi.staffApprove(slug.value, row.id, body);
        else await titleApi.staffReject(slug.value, row.id, body);
        reviewOpen.value = false;
        toast.success(reviewApprove.value ? t('memberTitle.approvedToast', { name: row.member.nickname }) : t('memberTitle.rejectedToast', { name: row.member.nickname }));
        void load();
    } catch (e) {
        if (e instanceof ApiError && e.field('reason')) reasonFieldError.value = e.field('reason') ?? '';
        else if (e instanceof ApiError && (e.status === 409 || e.status === 403 || e.status === 404)) {
            // 그새 다른 사람이 심사했거나 권한이 바뀌었다 — 닫고 알린 뒤 다시 받는다
            reviewOpen.value = false;
            toast.error(e.message, 8000);
            void load();
        } else reviewError.value = UtilBoardMessages.of(t, e);
    } finally {
        reviewing.value = false;
    }
}

useSeoMeta({
    title: () => (board.value ? `${t('memberTitle.staffTitle')} · ${localizedName(board.value.name, board.value.nameEn)}` : t('memberTitle.staffTitle')),
    robots: 'noindex',
});
</script>
