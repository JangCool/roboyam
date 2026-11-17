<template>
    <!--
        구매 인증 확인 `/boards/{slug}/purchase-proofs?status=PENDING|ALL` — 그 게시판 매니저만(trial-group-contract 「구매 인증」·「구현 결정」 7 — 자기 게시판 글만, 자기 글은 확인 못 한다).
        매니저가 아니면(목록이 404·403) 다른 운영 화면과 같은 「찾을 수 없어요」. 위에 탭 머리(`BoardStaffTabs` — 「구매 인증」 탭).
        한 줄(서버 `PurchaseProof`): 글 제목(→ 글) · 글쓴이(→ 공개 프로필) · 상태 · 올린 때 · 사진(비공개 — 눌러야 받는다 `…/{id}/photo`, 열람 기록) · (대기 + 내 글 아님) [승인]·[거절](사유 필수 — 글쓴이에게 보인다).
        승인되면 글에 「구매 인증」 칩 + 보너스 포인트(서버). 사진은 확인 뒤 설정 일수(기본 30일)가 지나면 파기된다(파기되면 「사진 보관 기간이 지났어요」).
    -->
    <div class="content-card">
        <div v-if="state === 'loading' && !page" role="status" :aria-label="t('common.loading')">
            <div class="h-6 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 3" :key="n" class="mt-3 h-24 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="state === 'notfound'" code="404" :title="t('boardSanction.notFoundTitle')" :description="t('boardSanction.notFoundText')">
            <Button as="router-link" :to="`/boards/${slug}`" :label="t('boardSanction.backToBoard')" />
        </ErrorState>

        <ErrorState v-else-if="state === 'error' && !page" :title="t('trial.staffLoadFailed')" :description="errorMessage" retry @retry="load" />

        <template v-else-if="page && board">
            <BoardStaffTabs :slug="slug" :board-name="localizedName(board.name, board.nameEn)" active="purchaseProofs" :purchase-proofs="true" />
            <header class="mt-3">
                <h1 class="m-0 text-lg font-bold">{{ t('trial.staffTitle') }}</h1>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('trial.staffHint') }}</p>
            </header>

            <div class="mt-4 flex flex-wrap items-center gap-2 border-b border-surface pb-3">
                <SelectButton :model-value="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('trial.staffStatusLabel')" @update:model-value="onStatus" />
            </div>

            <div :aria-busy="state === 'loading'">
                <ErrorState v-if="state === 'error'" :title="t('trial.staffLoadFailed')" :description="errorMessage" retry @retry="load" />
                <EmptyState v-else-if="page.length === 0" :title="status === 'PENDING' ? t('trial.staffEmptyPending') : t('trial.staffEmpty')" icon="pi pi-receipt" />
                <ul v-else class="m-0 list-none p-0" :class="state === 'loading' ? 'opacity-60' : ''">
                    <li v-for="row in page" :key="row.id" class="flex gap-3 border-b border-surface py-3" :data-status="row.status" data-testid="board-proof-row">
                        <button v-if="!row.purged" type="button" class="grid size-20 shrink-0 place-items-center rounded-lg border border-surface text-muted-color focus-visible:outline-2 focus-visible:outline-primary" :aria-label="t('trial.proofPhotoOpen')" @click="photo = trialApi.staffProofPhotoUrl(slug, row.id)">
                            <i class="pi pi-image text-xl" aria-hidden="true" />
                        </button>
                        <span v-else class="grid size-20 shrink-0 place-items-center rounded-lg bg-surface-100 text-center text-[0.6875rem] text-muted-color dark:bg-surface-800">{{ t('trial.proofPhotoPurged') }}</span>
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                                <span :class="[TAG, statusClass(row.status)]"><span class="relative top-[-0.09em]">{{ t(`trial.proofStatus.${row.status}`) }}</span></span>
                                <NuxtLink v-if="row.author" :to="UtilProfile.path(row.author.id)" class="text-sm font-semibold hover:underline">{{ row.author.nickname }}</NuxtLink>
                                <span class="ml-auto text-xs tabular-nums text-muted-color">{{ time.date(row.createdAt) }}</span>
                            </p>
                            <NuxtLink :to="`/posts/${encodeURIComponent(row.postId)}`" class="mt-1 line-clamp-1 block text-sm hover:underline">{{ row.postTitle }}</NuxtLink>
                            <p v-if="row.reviewedAt" class="m-0 mt-1 text-xs text-muted-color">{{ t('trial.proofReviewedAt', { at: time.date(row.reviewedAt) }) }}</p>
                            <p v-if="row.rejectReason" class="mb-0 mt-1.5 rounded-lg bg-surface-100 px-2.5 py-1.5 text-xs dark:bg-surface-800/60">{{ t('trial.proofRejectLine', { reason: row.rejectReason }) }}</p>
                            <div v-if="row.status === 'PENDING' && row.author?.id !== user?.id" class="mt-2 flex gap-2">
                                <Button type="button" :label="t('trial.proofApprove')" icon="pi pi-check" size="small" @click="openReview(row, true)" />
                                <Button type="button" :label="t('trial.proofReject')" icon="pi pi-times" size="small" severity="secondary" variant="outlined" @click="openReview(row, false)" />
                            </div>
                            <p v-else-if="row.status === 'PENDING'" class="m-0 mt-2 text-xs text-muted-color">{{ t('trial.proofOwn') }}</p>
                        </div>
                    </li>
                </ul>
            </div>
        </template>

        <!-- 사진 크게 -->
        <Dialog :visible="!!photo" modal :header="t('trial.proofPhotoTitle')" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="(v: boolean) => (v ? null : (photo = null))">
            <p class="m-0 mb-2 text-xs text-muted-color">{{ t('trial.proofPhotoPrivate') }}</p>
            <img v-if="photo" :src="photo" :alt="t('trial.proofPhotoTitle')" class="max-h-[70vh] w-full object-contain" />
        </Dialog>

        <!-- 승인·거절 -->
        <Dialog v-model:visible="reviewOpen" modal :header="reviewApprove ? t('trial.proofApproveTitle') : t('trial.proofRejectTitle')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!reviewing">
            <form v-if="reviewTarget" class="flex flex-col gap-3" novalidate @submit.prevent="submitReview">
                <p class="m-0 text-sm">{{ reviewApprove ? t('trial.proofApproveText', { name: reviewTarget.author?.nickname ?? '' }) : t('trial.proofRejectText', { name: reviewTarget.author?.nickname ?? '' }) }}</p>
                <LabeledField v-if="!reviewApprove" :label="t('trial.proofRejectReason')" :error="reasonError">
                    <template #aside>
                        <span class="text-xs tabular-nums text-muted-color">{{ reason.trim().length }}/{{ UtilTrial.REASON_MAX }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="reason" rows="3" auto-resize fluid :maxlength="UtilTrial.REASON_MAX" :disabled="reviewing" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <Message v-if="reviewError" severity="error" variant="simple" size="small">{{ reviewError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="reviewing" @click="reviewOpen = false" />
                    <Button type="submit" :label="reviewApprove ? t('trial.proofApprove') : t('trial.proofReject')" :severity="reviewApprove ? undefined : 'danger'" :loading="reviewing" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PurchaseProof, PurchaseProofStatus } from '~/composables/trial/api/useTrialApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilProfile from '~/utils/util-profile';
import UtilTrial from '~/utils/util-trial';

definePageMeta({ middleware: 'auth' });

type StatusFilter = 'PENDING' | 'ALL';
const TAG = 'inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none';

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const boardApi = useBoardApi();
const trialApi = useTrialApi();
const toast = useAppToast();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const { user } = useAuth();

const slug = computed(() => String(route.params.slug ?? ''));
const status = computed<StatusFilter>(() => (route.query.status === 'ALL' ? 'ALL' : 'PENDING'));
const statusOptions = computed(() => [
    { value: 'PENDING' as const, label: t('trial.proofStatus.PENDING') },
    { value: 'ALL' as const, label: t('common.all') },
]);

const state = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const errorMessage = ref('');
const board = ref<BoardDetail | null>(null);
/** 구매 인증 목록 — 받기 전은 null */
const page = ref<PurchaseProof[] | null>(null);
const photo = ref<string | null>(null);

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
        const res = await trialApi.staffProofs(slug.value, status.value, controller.signal);
        if (mine !== seq) return;
        page.value = res;
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
    if (value) void router.push({ path: UtilTrial.staffPath(slug.value), query: value === 'ALL' ? { status: 'ALL' } : {} });
}

watch(
    () => [slug.value, status.value] as const,
    () => void load(),
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

function statusClass(s: PurchaseProofStatus): string {
    if (s === 'APPROVED') return 'bg-green-100 text-green-900 dark:bg-green-900/40 dark:text-green-200';
    if (s === 'REJECTED') return 'bg-red-100 text-red-900 dark:bg-red-900/40 dark:text-red-200';
    return 'bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200';
}

/* ── 승인·거절 ── */
const reviewOpen = ref(false);
const reviewTarget = ref<PurchaseProof | null>(null);
const reviewApprove = ref(true);
const reason = ref('');
const tried = ref(false);
const reviewing = ref(false);
const reviewError = ref('');
const reasonFieldError = ref('');
const reasonError = computed(() => reasonFieldError.value || (tried.value && !reviewApprove.value && !reason.value.trim() ? t('trial.proofRejectRequired') : ''));

function openReview(row: PurchaseProof, approve: boolean): void {
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
    const name = row.author?.nickname ?? '';
    try {
        if (reviewApprove.value) await trialApi.staffApprove(slug.value, row.id);
        else await trialApi.staffReject(slug.value, row.id, reason.value.trim());
        reviewOpen.value = false;
        toast.success(reviewApprove.value ? t('trial.proofApproved', { name }) : t('trial.proofRejected', { name }));
        void load();
    } catch (e) {
        if (e instanceof ApiError && e.field('reason')) reasonFieldError.value = e.field('reason') ?? '';
        else if (e instanceof ApiError && (e.status === 409 || e.status === 403 || e.status === 404)) {
            // 그새 다른 사람이 확인했거나 권한이 바뀌었다 — 닫고 알린 뒤 다시 받는다
            reviewOpen.value = false;
            toast.error(e.message, 8000);
            void load();
        } else reviewError.value = UtilBoardMessages.of(t, e);
    } finally {
        reviewing.value = false;
    }
}

useSeoMeta({
    title: () => (board.value ? `${t('trial.staffTitle')} · ${localizedName(board.value.name, board.value.nameEn)}` : t('trial.staffTitle')),
    robots: 'noindex',
});
</script>
