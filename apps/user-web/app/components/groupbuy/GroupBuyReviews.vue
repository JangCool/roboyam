<template>
    <!--
        공구 상세의 구매 리뷰 구역 — 계약 「리뷰 규칙」(2b-6, 가정). 같은 상품의 다른 차수 리뷰도 함께(카드마다 「n차 구매」).
        · 머리(네이버 쇼핑 모양): 「리뷰 n」 · 「리뷰 제공 기준 ⓘ」(지난 차수 포함·보상 없음 — 풍선 도움말) · ★ 평균. 숨긴 리뷰는 세지 않는다(서버 값). 그 아래 사진 모아보기.
        · 목록 위 한 줄: 정렬(글자 단추 — 최신 · 별점 높은 · 낮은 · 사진 먼저) | 「사진 리뷰만」. 10개씩 「더 보기」.
        · 내 리뷰: 고치기(30일 안 — 서버 `actions.edit`) · 지우기. 남의 리뷰: 신고(대상 `GROUP_BUY_REVIEW`). 쓰기는 내 주문에서(산 회원만).
        부르는 화면은 로그인 회원이 바뀌면 `:key` 로 이 구역을 새로 그린다.
    -->
    <section id="reviews" :aria-labelledby="titleId" class="flex flex-col gap-5 panel-card p-4 sm:p-6" data-testid="group-buy-reviews">
        <!-- 머리(네이버 쇼핑 모양) — 「리뷰 n」 | 오른쪽 「리뷰 제공 기준 ⓘ」(지난 차수 포함 · 보상 없음), 그 아래 ★ 평균 한 줄 -->
        <div class="flex flex-col gap-2" data-testid="group-buy-reviews-summary">
            <div class="flex items-center justify-between gap-3">
                <h2 :id="titleId" class="m-0 text-lg font-bold">
                    {{ t('groupBuyReview.title') }}<span v-if="summary" class="ml-1.5 tabular-nums">{{ summary.count }}</span>
                </h2>
                <button
                    type="button"
                    class="inline-flex items-center gap-1 text-[13px] text-muted-color hover:text-(--p-text-color) focus-visible:outline-2 focus-visible:outline-primary"
                    aria-haspopup="dialog"
                    @click="(e) => basisPopover?.toggle(e)"
                >
                    {{ t('groupBuyReview.basis') }}<i class="pi pi-info-circle text-xs" aria-hidden="true" />
                </button>
                <Popover ref="basisPopover" :aria-label="t('groupBuyReview.basis')" class="max-w-[min(20rem,calc(100vw-2rem))]">
                    <ul class="m-0 flex list-disc flex-col gap-1 pl-4 text-[13px] leading-relaxed">
                        <li>{{ t('groupBuyReview.scopeNote') }}</li>
                        <li>{{ t('groupBuyReview.hiddenNotCounted') }}</li>
                        <li>{{ t('groupBuyReview.rewardNote') }}</li>
                    </ul>
                </Popover>
            </div>
            <p v-if="summary && summary.count > 0" class="m-0 flex items-center gap-1.5" :aria-label="t('groupBuyReview.averageLabel', { value: summary.average ?? 0 })">
                <i class="pi pi-star-fill text-base text-red-500" aria-hidden="true" />
                <span class="text-lg font-bold tabular-nums" aria-hidden="true">{{ summary.average?.toFixed(2) }}</span>
            </p>
        </div>

        <!-- 사진 모아보기 — 누르면 「사진 리뷰만」 -->
        <div v-if="summary && summary.photos.length > 0" class="flex flex-col gap-2" data-testid="group-buy-reviews-photos">
            <div class="flex items-baseline justify-between gap-2">
                <span class="text-sm font-medium">{{ t('groupBuyReview.photoStrip', { count: summary.photoCount }) }}</span>
                <button type="button" class="text-xs text-muted-color hover:text-(--p-text-color) hover:underline" @click="changePhotoOnly(true)">{{ t('groupBuyReview.photoStripOpen') }} ›</button>
            </div>
            <div class="flex gap-1.5 overflow-x-auto">
                <button
                    v-for="(p, i) in summary.photos"
                    :key="`${p.reviewId}-${i}`"
                    type="button"
                    class="size-[4.5rem] shrink-0 overflow-hidden rounded-md bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-800"
                    :aria-label="t('groupBuyReview.photoStripOpen')"
                    @click="changePhotoOnly(true)"
                >
                    <img :src="p.url" alt="" class="size-full object-cover" loading="lazy" />
                </button>
            </div>
        </div>

        <!-- 정렬(글자 단추) · 사진 리뷰만 — 목록 바로 위 한 줄 -->
        <div v-if="summary && summary.count > 0" class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b border-surface pb-2.5">
            <div class="flex flex-wrap items-center text-[13px]" role="group" :aria-label="t('groupBuyReview.sortLabel')">
                <template v-for="(o, i) in sortOptions" :key="o.value">
                    <span v-if="i > 0" class="mx-2 h-3 w-px bg-surface-300 dark:bg-surface-600" aria-hidden="true" />
                    <button
                        type="button"
                        class="focus-visible:outline-2 focus-visible:outline-primary"
                        :class="sort === o.value ? 'font-semibold text-(--p-text-color)' : 'text-muted-color hover:text-(--p-text-color)'"
                        :aria-pressed="sort === o.value"
                        @click="changeSort(o.value)"
                    >
                        {{ o.label }}
                    </button>
                </template>
            </div>
            <div class="flex items-center gap-1.5">
                <Checkbox :model-value="photoOnly" binary :input-id="photoId" size="small" @update:model-value="(v: boolean) => changePhotoOnly(v)" />
                <label :for="photoId" class="text-[13px] text-muted-color">{{ t('groupBuyReview.photoOnly', { count: summary.photoCount }) }}</label>
            </div>
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-20 animate-pulse rounded-lg bg-surface-100 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('groupBuyReview.loadFailed')" :description="loadError" retry @retry="load(false)" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-star" :title="photoOnly ? t('groupBuyReview.emptyPhoto') : t('groupBuyReview.empty')" :description="photoOnly ? '' : t('groupBuyReview.emptyText')" />
        <ul v-else class="-mt-2 m-0 flex list-none flex-col divide-y divide-surface p-0" data-testid="group-buy-reviews-list">
            <li v-for="review in items" :key="review.id" class="py-5 last:pb-0">
                <GroupBuyReviewCard :review="review" :busy="busyId === review.id" @edit="openEdit(review)" @delete="remove(review)" @report="openReport(review)" />
            </li>
        </ul>
        <Button v-if="status === 'ready' && page?.hasMore" type="button" :label="t('groupBuyReview.more')" severity="secondary" variant="outlined" class="w-full sm:w-auto sm:self-center sm:px-10" :loading="moreBusy" @click="load(true)" />

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

/** 「리뷰 제공 기준」 풍선(지난 차수 포함 · 숨긴 리뷰 제외 · 보상 없음) */
const basisPopover = ref<{ toggle: (event: Event) => void } | null>(null);
const { t } = useI18n();
const api = useGroupBuyReviewApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const titleId = useId();
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
