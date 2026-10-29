<template>
    <!--
        공구 상세의 구매 리뷰 구역 — 계약 「리뷰 규칙」(2b-6, 가정). 같은 상품의 다른 차수 리뷰도 함께(카드마다 「n차 구매」).
        · 요약: 평균 별점 · 리뷰 수 · 별점 분포(숨긴 리뷰는 세지 않는다 — 서버 값). 보상 없음 안내 한 줄.
        · 정렬(최신 · 별점 높은 · 낮은 · 사진 먼저) · 「사진 리뷰만」 · 10개씩 「더 보기」.
        · 내 리뷰: 고치기(30일 안 — 서버 `actions.edit`) · 지우기. 남의 리뷰: 신고(대상 `GROUP_BUY_REVIEW`). 쓰기는 내 주문에서(산 회원만).
        부르는 화면은 로그인 회원이 바뀌면 `:key` 로 이 구역을 새로 그린다.
    -->
    <section id="reviews" :aria-labelledby="titleId" class="flex flex-col gap-3 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-buy-reviews">
        <h2 :id="titleId" class="m-0 text-sm font-semibold">
            {{ t('groupBuyReview.title') }}<span v-if="summary" class="ml-1.5 font-normal tabular-nums text-muted-color">{{ t('groupBuyReview.count', { count: summary.count }) }}</span>
        </h2>

        <!-- 요약 -->
        <div v-if="summary && summary.count > 0" class="flex flex-wrap items-center gap-x-6 gap-y-3" data-testid="group-buy-reviews-summary">
            <div class="flex flex-col items-center gap-1">
                <span class="text-3xl font-bold tabular-nums" :aria-label="t('groupBuyReview.averageLabel', { value: summary.average ?? 0 })">{{ summary.average?.toFixed(1) }}</span>
                <GroupBuyStars :value="summary.average ?? 0" size="text-sm" />
            </div>
            <ul class="m-0 flex min-w-48 flex-1 list-none flex-col gap-1 p-0" :aria-label="t('groupBuyReview.distributionLabel')">
                <li v-for="star in STARS" :key="star" class="flex items-center gap-2 text-xs">
                    <span class="w-7 shrink-0 tabular-nums text-muted-color">{{ t('groupBuyReview.distributionRow', { star }) }}</span>
                    <span class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                        <span class="block h-full rounded-full bg-amber-500" :style="{ width: `${percent(summary.distribution[star])}%` }" />
                    </span>
                    <span class="w-8 shrink-0 text-right tabular-nums text-muted-color">{{ summary.distribution[star] }}</span>
                </li>
            </ul>
        </div>
        <!-- 사진 모아보기 — 누르면 「사진 리뷰만」 -->
        <div v-if="summary && summary.photos.length > 0" class="flex flex-col gap-1.5" data-testid="group-buy-reviews-photos">
            <span class="text-xs font-medium text-muted-color">{{ t('groupBuyReview.photoStrip', { count: summary.photoCount }) }}</span>
            <div class="flex gap-2 overflow-x-auto pb-1">
                <button
                    v-for="(p, i) in summary.photos"
                    :key="`${p.reviewId}-${i}`"
                    type="button"
                    class="size-20 shrink-0 overflow-hidden rounded-lg border border-surface focus-visible:outline-2 focus-visible:outline-primary"
                    :aria-label="t('groupBuyReview.photoStripOpen')"
                    @click="changePhotoOnly(true)"
                >
                    <img :src="p.url" alt="" class="size-full object-cover" loading="lazy" />
                </button>
            </div>
        </div>
        <p class="m-0 text-xs text-muted-color"><i class="pi pi-info-circle mr-1 text-xs" aria-hidden="true" />{{ t('groupBuyReview.scopeNote') }} {{ t('groupBuyReview.rewardNote') }}</p>

        <!-- 정렬 · 사진 리뷰만 -->
        <div v-if="summary && summary.count > 0" class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <label class="sr-only" :for="sortId">{{ t('groupBuyReview.sortLabel') }}</label>
            <Select :input-id="sortId" :model-value="sort" :options="sortOptions" option-label="label" option-value="value" size="small" class="w-40" @update:model-value="(v: GroupBuyReviewSort) => changeSort(v)" />
            <div class="flex items-center gap-1.5">
                <Checkbox :model-value="photoOnly" binary :input-id="photoId" @update:model-value="(v: boolean) => changePhotoOnly(v)" />
                <label :for="photoId" class="text-sm">{{ t('groupBuyReview.photoOnly', { count: summary.photoCount }) }}</label>
            </div>
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-20 animate-pulse rounded-lg bg-surface-100 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('groupBuyReview.loadFailed')" :description="loadError" retry @retry="load(false)" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-star" :title="photoOnly ? t('groupBuyReview.emptyPhoto') : t('groupBuyReview.empty')" :description="photoOnly ? '' : t('groupBuyReview.emptyText')" />
        <ul v-else class="m-0 flex list-none flex-col divide-y divide-surface p-0" data-testid="group-buy-reviews-list">
            <li v-for="review in items" :key="review.id" class="py-3 first:pt-0 last:pb-0">
                <GroupBuyReviewCard :review="review" :busy="busyId === review.id" @edit="openEdit(review)" @delete="remove(review)" @report="openReport(review)" />
            </li>
        </ul>
        <Button v-if="status === 'ready' && page?.hasMore" type="button" :label="t('groupBuyReview.more')" severity="secondary" variant="outlined" size="small" class="self-center" :loading="moreBusy" @click="load(true)" />

        <GroupBuyReviewDialog
            v-if="editing"
            :key="editing.id"
            v-model:visible="editOpen"
            :sku-name="editing.skuName"
            :round-no="editing.roundNo"
            :review="editing"
            :upload="api.uploadPhoto"
            :busy="saving"
            :error="saveError"
            :field-errors="saveFields"
            @submit="submitEdit"
        />
        <BoardReportDialog v-model:visible="reportOpen" target="GROUP_BUY_REVIEW" :loading="reporting" :error="reportError" :field-error="reportFieldError" :summary="reportSummary" @submit="submitReport" />
    </section>
</template>

<script setup lang="ts">
import type { GroupBuyReview, GroupBuyReviewPage, GroupBuyReviewRequest, GroupBuyReviewSort, GroupBuyReviewStats } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';
import { GROUP_BUY_REVIEW_SORTS } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';
import type { ReportReason } from '~/composables/report/api/useReportApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

const props = defineProps<{ groupBuyId: string }>();
/** 요약(평균·수)을 받으면 — 부르는 화면의 위쪽 별점 링크·탭 (수) */
const emit = defineEmits<{ summary: [stats: GroupBuyReviewStats] }>();

const STARS = [5, 4, 3, 2, 1] as const;
const { t } = useI18n();
const api = useGroupBuyReviewApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const titleId = useId();
const sortId = useId();
const photoId = useId();

const sortOptions = computed(() => GROUP_BUY_REVIEW_SORTS.map((value) => ({ value, label: t(`groupBuyReview.sort.${value}`) })));

/* ── 목록 ── */

const page = ref<GroupBuyReviewPage | null>(null);
const summary = computed(() => page.value?.summary ?? null);
const items = ref<GroupBuyReview[]>([]);
const sort = ref<GroupBuyReviewSort>('LATEST');
const photoOnly = ref(false);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const moreBusy = ref(false);
let seq = 0;

async function load(more: boolean): Promise<void> {
    const mine = ++seq;
    const next = more && page.value ? page.value.page + 1 : 0;
    if (more) moreBusy.value = true;
    else status.value = 'loading';
    try {
        const res = await api.list(props.groupBuyId, sort.value, photoOnly.value, next);
        if (mine !== seq) return;
        page.value = res;
        emit('summary', { average: res.summary.average, count: res.summary.count });
        items.value = more ? [...items.value, ...res.items.filter((r) => !items.value.some((o) => o.id === r.id))] : res.items;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (more) toast.error(UtilGroupBuyMessages.error(t, e));
        else {
            loadError.value = UtilGroupBuyMessages.error(t, e);
            status.value = 'error';
        }
    } finally {
        if (mine === seq) moreBusy.value = false;
    }
}

function changeSort(value: GroupBuyReviewSort): void {
    sort.value = value;
    void load(false);
}

function changePhotoOnly(value: boolean): void {
    photoOnly.value = value;
    void load(false);
}

function percent(n: number): number {
    const total = summary.value?.count ?? 0;
    return total > 0 ? Math.round((n / total) * 100) : 0;
}

onMounted(() => void load(false));
onBeforeUnmount(() => seq++);

/* ── 내 리뷰 고치기 · 지우기 ── */

const editing = ref<GroupBuyReview | null>(null);
const editOpen = ref(false);
const saving = ref(false);
const saveError = ref('');
const saveFields = ref<Record<string, string>>({});
const busyId = ref<string | null>(null);

function openEdit(review: GroupBuyReview): void {
    editing.value = review;
    saveError.value = '';
    saveFields.value = {};
    editOpen.value = true;
}

async function submitEdit(body: GroupBuyReviewRequest): Promise<void> {
    const target = editing.value;
    if (!target) return;
    saving.value = true;
    saveError.value = '';
    saveFields.value = {};
    try {
        const updated = await api.update(target.id, body);
        items.value = items.value.map((r) => (r.id === updated.id ? updated : r));
        editOpen.value = false;
        toast.success(t('groupBuyReview.edited'));
        void load(false);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) saveFields.value = e.fields;
        else saveError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        saving.value = false;
    }
}

async function remove(review: GroupBuyReview): Promise<void> {
    if (!(await confirm({ title: t('groupBuyReview.deleteTitle'), message: t('groupBuyReview.deleteText'), confirmLabel: t('groupBuyReview.actDelete'), tone: 'danger' }))) return;
    busyId.value = review.id;
    try {
        await api.remove(review.id);
        toast.success(t('groupBuyReview.deleted'));
        void load(false);
    } catch (e) {
        toast.error(UtilGroupBuyMessages.error(t, e), 8000);
    } finally {
        busyId.value = null;
    }
}

/* ── 신고 ── */

const reportOpen = ref(false);
const reportTarget = ref<GroupBuyReview | null>(null);
const reporting = ref(false);
const reportError = ref('');
const reportFieldError = ref('');
const reportSummary = computed(() => (reportTarget.value?.body ?? '').slice(0, 80));

function openReport(review: GroupBuyReview): void {
    reportTarget.value = review;
    reportError.value = '';
    reportFieldError.value = '';
    reportOpen.value = true;
}

async function submitReport(value: { reason: ReportReason; detail: string | null }): Promise<void> {
    const target = reportTarget.value;
    if (!target) return;
    reporting.value = true;
    reportError.value = '';
    reportFieldError.value = '';
    try {
        await api.report(target.id, value);
        reportOpen.value = false;
        toast.success(t('groupBuyReview.reported'));
    } catch (e) {
        const field = e instanceof ApiError ? e.field('detail') : null;
        if (field) reportFieldError.value = field;
        else if (e instanceof ApiError && e.status !== 500 && e.status !== 429) {
            // 다시 눌러도 같은 거절(이미 신고·본인 리뷰·정지 등) — 닫고 서버 문구를 알린다
            reportOpen.value = false;
            toast.error(e.message, 8000);
        } else reportError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        reporting.value = false;
    }
}
</script>
