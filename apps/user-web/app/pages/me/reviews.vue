<template>
    <!--
        내 리뷰 `/me/reviews` — 공구 리뷰(계약 「리뷰 규칙」, 2b-6 가정). 새 것이 위, 10개씩 「더 보기」.
        한 장: 공구 제목(상세 리뷰로) · 주문 번호 · 리뷰 카드(숨김이면 사유) · 고칠 수 있는 기한(작성 + 30일) · 고치기 · 지우기.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="my-reviews">
        <h1 class="m-0 text-xl font-bold">{{ t('groupBuyReview.myTitle') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('groupBuyReview.loadFailed')" :description="loadError" retry @retry="load(false)" />
        <EmptyState v-else-if="items.length === 0" icon="pi pi-star" :title="t('groupBuyReview.myEmpty')" :description="t('groupBuyReview.myEmptyText')">
            <Button as="router-link" :to="UtilCommerce.ORDERS_PATH" :label="t('commerce.ordersTitle')" severity="secondary" variant="outlined" />
        </EmptyState>
        <ul v-else class="m-0 flex list-none flex-col gap-3 p-0">
            <li v-for="review in items" :key="review.id" class="flex flex-col gap-2 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
                <div class="flex flex-wrap items-baseline justify-between gap-2 text-sm">
                    <NuxtLink :to="`${UtilGroupBuy.detailPath(review.groupBuy.id)}#reviews`" class="min-w-0 truncate font-semibold hover:underline">{{ review.groupBuy.title }}</NuxtLink>
                    <NuxtLink v-if="review.orderNo" :to="UtilGroupBuy.myOrderPath(review.orderNo)" class="text-xs text-muted-color hover:underline"><code>{{ review.orderNo }}</code></NuxtLink>
                </div>
                <GroupBuyReviewCard :review="review" :busy="busyId === review.id" @edit="openEdit(review)" @delete="remove(review)" />
                <p class="m-0 text-xs text-muted-color">{{ review.actions.edit ? t('groupBuyReview.myEditableUntil', { at: time.shortDateTime(review.editableUntil) }) : t('groupBuyReview.myEditClosed') }}</p>
            </li>
        </ul>
        <Button v-if="status === 'ready' && page?.hasMore" type="button" :label="t('groupBuyReview.more')" severity="secondary" variant="outlined" class="self-center" :loading="moreBusy" @click="load(true)" />

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
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyReviewRequest, MyGroupBuyReview, MyGroupBuyReviewPage } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilCommerce from '~/utils/util-commerce';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
const time = useDisplayTime();
const api = useGroupBuyReviewApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
useSeoMeta({ title: () => t('groupBuyReview.myTitle'), robots: 'noindex' });

const page = ref<MyGroupBuyReviewPage | null>(null);
const items = ref<MyGroupBuyReview[]>([]);
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
        const res = await api.mine(next);
        if (mine !== seq) return;
        page.value = res;
        items.value = more ? [...items.value, ...res.items] : res.items;
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

onMounted(() => void load(false));
onBeforeUnmount(() => seq++);

const editing = ref<MyGroupBuyReview | null>(null);
const editOpen = ref(false);
const saving = ref(false);
const saveError = ref('');
const saveFields = ref<Record<string, string>>({});
const busyId = ref<string | null>(null);

function openEdit(review: MyGroupBuyReview): void {
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
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) saveFields.value = e.fields;
        else saveError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        saving.value = false;
    }
}

async function remove(review: MyGroupBuyReview): Promise<void> {
    if (!(await confirm({ title: t('groupBuyReview.deleteTitle'), message: t('groupBuyReview.deleteText'), confirmLabel: t('groupBuyReview.actDelete'), tone: 'danger' }))) return;
    busyId.value = review.id;
    try {
        await api.remove(review.id);
        items.value = items.value.filter((r) => r.id !== review.id);
        toast.success(t('groupBuyReview.deleted'));
    } catch (e) {
        toast.error(UtilGroupBuyMessages.error(t, e), 8000);
    } finally {
        busyId.value = null;
    }
}
</script>
