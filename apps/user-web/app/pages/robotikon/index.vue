<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-lg font-bold">{{ t('emoticon.shopTitle') }}</h1>
            <Button v-if="user" as="router-link" :to="UtilEmoticon.MY_PATH" :label="t('emoticon.myTitle')" icon="pi pi-face-smile" size="small" severity="secondary" variant="outlined" data-testid="emoticon-shop-mine" />
        </div>

        <!-- 이모티콘 만들기 — 가이드(누구나) · 내 제작(로그인) -->
        <section class="flex flex-wrap items-center gap-3 panel-card p-4" :aria-labelledby="`${uid}-create`" data-testid="emoticon-shop-create">
            <i class="pi pi-palette grid size-10 shrink-0 place-items-center rounded-full bg-surface-100 text-primary dark:bg-surface-800" aria-hidden="true" />
            <div class="min-w-0 flex-1">
                <h2 :id="`${uid}-create`" class="m-0 text-sm font-semibold">{{ t('emoticon.createTitle') }}</h2>
                <p class="m-0 mt-0.5 text-xs leading-relaxed text-muted-color">{{ t('emoticon.createText') }}</p>
            </div>
            <div class="flex gap-2 max-sm:w-full">
                <Button as="router-link" :to="UtilEmoticon.GUIDE_PATH" :label="t('emoticon.guideTitle')" icon="pi pi-book" size="small" severity="secondary" variant="outlined" class="max-sm:flex-1" data-testid="emoticon-shop-guide" />
                <Button as="router-link" :to="UtilEmoticon.STUDIO_PATH" :label="t('emoticon.studioTitle')" icon="pi pi-pen-to-square" size="small" class="max-sm:flex-1" data-testid="emoticon-shop-studio" />
            </div>
        </section>

        <div class="content-card flex flex-col gap-4" data-testid="emoticon-shop-catalog">
            <!-- 출처 거르기 — 주소의 `?source=` 가 상태다(포인트 상점과 같은 밑줄 탭) -->
            <div role="group" :aria-label="t('emoticon.sourceFilter')" class="no-scrollbar flex gap-5 overflow-x-auto border-b border-hairline" data-testid="emoticon-shop-source">
                <button
                    v-for="opt in sourceOptions"
                    :key="opt.value"
                    type="button"
                    class="-mb-px inline-flex min-h-11 shrink-0 items-center whitespace-nowrap border-b-2 text-[0.9375rem] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="source === opt.value ? 'border-(--p-text-color) font-bold text-color' : 'border-transparent text-muted-color hover:text-color'"
                    :aria-pressed="source === opt.value"
                    @click="setQuery({ source: opt.value === 'ALL' ? undefined : opt.value })"
                >
                    {{ opt.label }}
                </button>
            </div>

            <!-- 정렬 · 검색 -->
            <div class="flex flex-wrap items-center gap-2">
                <!-- 가격 거르기 — 주소의 `?price=` -->
                <div role="group" :aria-label="t('emoticon.priceFilter')" class="flex gap-1" data-testid="emoticon-shop-price">
                    <Button
                        v-for="opt in priceOptions"
                        :key="opt.value"
                        type="button"
                        :label="opt.label"
                        size="small"
                        :severity="price === opt.value ? undefined : 'secondary'"
                        :variant="price === opt.value ? 'outlined' : 'text'"
                        :aria-pressed="price === opt.value"
                        @click="setQuery({ price: opt.value === 'ALL' ? undefined : opt.value })"
                    />
                </div>
                <span class="h-5 w-px bg-surface-200 dark:bg-surface-700" aria-hidden="true" />
                <div role="group" :aria-label="t('emoticon.sortLabel')" class="flex gap-1" data-testid="emoticon-shop-sort">
                    <Button
                        v-for="opt in sortOptions"
                        :key="opt.value"
                        type="button"
                        :label="opt.label"
                        size="small"
                        :severity="sort === opt.value ? undefined : 'secondary'"
                        :variant="sort === opt.value ? 'outlined' : 'text'"
                        :aria-pressed="sort === opt.value"
                        @click="setQuery({ sort: opt.value === 'POPULAR' ? undefined : opt.value })"
                    />
                </div>
                <form class="ml-auto flex min-w-0 gap-1.5 max-sm:w-full" role="search" @submit.prevent="setQuery({ q: keyword.trim() || undefined })">
                    <InputText v-model="keyword" :aria-label="t('emoticon.searchLabel')" :placeholder="t('emoticon.searchPlaceholder')" size="small" class="min-w-0 flex-1 sm:w-56" data-testid="emoticon-shop-search" />
                    <Button type="submit" icon="pi pi-search" :aria-label="t('emoticon.searchLabel')" size="small" severity="secondary" variant="outlined" />
                </form>
            </div>

            <div v-if="status === 'loading'" class="grid grid-cols-2 gap-3 sm:grid-cols-3" role="status" :aria-label="t('emoticon.loading')" data-testid="emoticon-shop-loading">
                <div v-for="n in 6" :key="n" class="h-56 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
            <ErrorState v-else-if="status === 'error'" :title="t('emoticon.loadFailed')" :description="loadError" retry data-testid="emoticon-shop-error" @retry="load" />
            <EmptyState
                v-else-if="!data || data.items.length === 0"
                :title="q ? t('emoticon.searchEmpty', { q }) : t('emoticon.shopEmpty')"
                :description="t('emoticon.shopEmptyText')"
                icon="pi pi-face-smile"
                data-testid="emoticon-shop-empty"
            />
            <ul v-else class="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3" data-testid="emoticon-shop-packs">
                <li v-for="pack in data.items" :key="pack.id">
                    <NuxtLink :to="UtilEmoticon.packPath(pack.id)" class="flex h-full flex-col gap-2 panel-card p-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary" data-testid="emoticon-shop-pack">
                        <EmoticonPackCover :src="pack.coverUrl" size="fill" />
                        <p class="m-0 line-clamp-2 text-sm font-semibold leading-snug">{{ pack.name }}</p>
                        <p class="m-0 truncate text-xs text-muted-color">{{ pack.creator ? pack.creator.nickname : t('emoticon.official') }}</p>
                        <p class="m-0 text-sm font-bold tabular-nums" :class="pack.price > 0 ? 'text-primary' : 'text-green-700 dark:text-green-400'" data-testid="emoticon-shop-price-label">{{ UtilEmoticonMessages.price(t, pack.price) }}</p>
                        <div class="mt-auto flex flex-wrap items-center gap-1">
                            <PointShopTag>{{ t('emoticon.countTag', { count: pack.count }) }}</PointShopTag>
                            <PointShopTag v-if="pack.source === 'OFFICIAL'">{{ t('emoticon.official') }}</PointShopTag>
                            <PointShopTag v-if="pack.acquired" tone="success">{{ t('emoticon.acquired') }}</PointShopTag>
                        </div>
                        <p class="m-0 text-[0.6875rem] tabular-nums text-muted-color">{{ t('emoticon.acquireCount', { count: pack.acquireCount.toLocaleString() }) }}</p>
                    </NuxtLink>
                </li>
            </ul>
            <!-- 다음 쪽(서버 키셋 — 한 쪽 24장) -->
            <div v-if="status === 'ready' && data?.nextCursor" class="flex flex-col items-center gap-1">
                <Button type="button" :label="t('emoticon.shopMore')" size="small" severity="secondary" variant="outlined" :loading="moreLoading" data-testid="emoticon-shop-more" @click="loadMore" />
                <p v-if="moreError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('emoticon.shopMoreFailed', { message: moreError }) }}</p>
            </div>

            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.shopNote') }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonPackList, EmoticonPackQuery, EmoticonPackSort, EmoticonPriceFilter } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonSourceFilter } from '~/utils/util-emoticon';
import { ApiError } from '~/composables/common/useApiClient';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

/**
 * 로보티콘 상점 `/robotikon?source=&price=&sort=&q=` — 누구나 본다(받기·구매만 로그인). 포인트 상점과 따로 둔다(계약 「이름」).
 * 맨 위 「로보티콘 만들기」(제작 가이드·내 제작), 출처 거르기(전체·공식·작가), 가격(전체·무료·유료), 정렬(인기·최신), 검색(이름·태그·작가),
 * 팩 카드(대표 그림·이름·작가·가격 「무료」/「2,500원」·개수·받은·산 수·가짐).
 * 실제 API(`GET /api/emoticons/packs` — 키셋 쪽, 「팩 더 보기」로 이어 받는다).
 */
const { t } = useI18n();
useSeoMeta({ title: () => t('emoticon.shopTitle'), description: () => t('emoticon.seoDescription') });

const uid = useId();
const route = useRoute();
const router = useRouter();
const api = useEmoticonApi();
const { user } = useAuth();

const source = computed<EmoticonSourceFilter>(() => UtilEmoticon.parseSource(route.query.source));
const sort = computed<EmoticonPackSort>(() => UtilEmoticon.parseSort(route.query.sort));
const price = computed<EmoticonPriceFilter | 'ALL'>(() => UtilEmoticon.parsePrice(route.query.price));
const q = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''));
const keyword = ref(q.value);

const sourceOptions = computed<{ value: EmoticonSourceFilter; label: string }[]>(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'OFFICIAL', label: t('emoticon.official') },
    { value: 'CREATOR', label: t('emoticon.creator') },
]);
const priceOptions = computed<{ value: EmoticonPriceFilter | 'ALL'; label: string }[]>(() => [
    { value: 'ALL', label: t('emoticon.priceAll') },
    { value: 'FREE', label: t('emoticon.free') },
    { value: 'PAID', label: t('emoticon.paid') },
]);
const sortOptions = computed<{ value: EmoticonPackSort; label: string }[]>(() => [
    { value: 'POPULAR', label: t('emoticon.sortPopular') },
    { value: 'LATEST', label: t('emoticon.sortLatest') },
]);

function setQuery(patch: Record<string, string | undefined>): void {
    void router.replace({ query: { ...route.query, ...patch } });
}

const data = ref<EmoticonPackList | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
let seq = 0;

const moreLoading = ref(false);
const moreError = ref('');

function query(cursor?: string): EmoticonPackQuery {
    return { source: source.value === 'ALL' ? undefined : source.value, price: price.value === 'ALL' ? undefined : price.value, sort: sort.value, q: q.value || undefined, cursor };
}

async function load(): Promise<void> {
    const mine = ++seq;
    status.value = 'loading';
    loadError.value = '';
    moreError.value = '';
    moreLoading.value = false;
    try {
        const result = await api.packs(query());
        if (mine !== seq) return;
        data.value = result;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = 'error';
    }
}

/** 다음 쪽을 이어 붙인다 — 거르기가 바뀌면(`seq`) 버린다. 커서가 낡았으면(`400 fields.cursor`) 처음부터 다시 받는다 */
async function loadMore(): Promise<void> {
    const current = data.value;
    if (!current?.nextCursor || moreLoading.value) return;
    const mine = seq;
    moreLoading.value = true;
    moreError.value = '';
    try {
        const result = await api.packs(query(current.nextCursor));
        if (mine !== seq) return;
        const seen = new Set(current.items.map((p) => p.id));
        data.value = { items: [...current.items, ...result.items.filter((p) => !seen.has(p.id))], nextCursor: result.nextCursor };
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.field('cursor')) {
            void load();
            return;
        }
        moreError.value = UtilEmoticonMessages.error(t, e);
    } finally {
        if (mine === seq) moreLoading.value = false;
    }
}

// 거르기·정렬·검색어(주소) · 로그인한 사람(받음 표시)이 바뀌면 다시 받는다
watch([source, price, sort, q, () => user.value?.id ?? null], () => void load(), { immediate: true });
onBeforeUnmount(() => seq++);
</script>
