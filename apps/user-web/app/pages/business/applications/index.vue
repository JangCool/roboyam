<template>
    <!--
        내 신청 건 `/business/applications` — 상태 SelectButton(전체·심사 중·결제 대기·입금 대기·예약·진행·종료·반려/취소, 건수 함께) + 카드 목록(`DataView` — 모바일 친화 한 열).
        카드: 종류 · 제목 · 기간 · 금액(확정 전이면 「예상」) · 상태 딱지 · 결제/입금 기한. 고른 상태는 주소 `?status=` 에 남긴다(뒤로 와도 그 자리).
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="business-applications">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-2">
                <Button as="router-link" :to="UtilBusiness.CENTER_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.home.title')" />
                <h1 class="m-0 text-lg font-bold">{{ t('business.list.title') }}</h1>
            </div>
            <div class="flex gap-2">
                <Button as="router-link" :to="UtilBusiness.AD_NEW_PATH" :label="t('business.list.newAd')" icon="pi pi-megaphone" size="small" severity="secondary" variant="outlined" />
                <Button as="router-link" :to="UtilBusiness.DEAL_NEW_PATH" :label="t('business.list.newDeal')" icon="pi pi-tag" size="small" severity="secondary" variant="outlined" />
            </div>
        </div>

        <!-- 상태 거르기 — 좁은 화면은 가로로 민다 -->
        <div class="max-w-full overflow-x-auto [scrollbar-width:none]">
            <SelectButton
                :model-value="bucket"
                :options="bucketOptions"
                option-label="label"
                option-value="value"
                :allow-empty="false"
                :aria-label="t('business.list.filterLabel')"
                size="small"
                class="flex-nowrap whitespace-nowrap"
                data-testid="business-applications-filter"
                @update:model-value="changeBucket"
            />
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 4" :key="n" class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('business.list.loadFailed')" :description="loadError" retry @retry="load" />
        <EmptyState
            v-else-if="!page || page.items.length === 0"
            class="panel-card"
            :title="bucket === 'ALL' ? t('business.list.empty') : t('business.list.emptyFiltered')"
            :description="bucket === 'ALL' ? t('business.list.emptyText') : ''"
            icon="pi pi-inbox"
        >
            <Button v-if="bucket === 'ALL'" as="router-link" :to="UtilBusiness.AD_NEW_PATH" :label="t('business.list.newAd')" />
        </EmptyState>
        <DataView v-else :value="page.items" data-key="id" layout="list" class="business-dataview">
            <template #list="{ items }">
                <ul class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="item in items as BizApplicationSummary[]" :key="item.id"><BusinessApplicationCard :item="item" /></li>
                </ul>
            </template>
        </DataView>
    </div>
</template>

<script setup lang="ts">
import type { BizApplicationBucket, BizApplicationPage, BizApplicationSummary } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

/**
 * 내 신청 건 `/business/applications` — 로그인 필요. 사업자 센터 홈의 [내 신청 건]·[전체 보기]가 온다. ⚠ 목업 단계(`useBusinessApi`).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.list.title'), description: () => t('business.list.seoDescription'), robots: 'noindex' });

const route = useRoute();
const router = useRouter();
const api = useBusinessApi();

function bucketOf(value: unknown): BizApplicationBucket {
    return UtilBusiness.BUCKETS.includes(value as BizApplicationBucket) ? (value as BizApplicationBucket) : 'ALL';
}

const bucket = ref<BizApplicationBucket>(bucketOf(route.query.status));
const page = ref<BizApplicationPage | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
let seq = 0;

async function load(): Promise<void> {
    const mine = ++seq;
    status.value = 'loading';
    loadError.value = '';
    try {
        const result = await api.applications(bucket.value);
        if (mine !== seq) return;
        page.value = result;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}
onMounted(load);
onBeforeUnmount(() => seq++);

/** 건수는 마지막으로 받은 묶음별 건수(전체 목록 기준) */
const bucketOptions = computed(() =>
    UtilBusiness.BUCKETS.map((value) => {
        const count = page.value?.counts[value];
        return { value, label: count !== undefined ? `${t(`business.bucket.${value}`)} ${count}` : t(`business.bucket.${value}`) };
    }),
);

function changeBucket(value: BizApplicationBucket): void {
    bucket.value = value;
    void router.replace({ query: { ...route.query, status: value === 'ALL' ? undefined : value } });
    void load();
}
</script>

<style scoped>
/* DataView 의 바탕·테두리를 지운다 — 카드가 각자 면을 가진다(`panel-card`) */
.business-dataview :deep(.p-dataview-content) {
    background: transparent;
}
</style>
