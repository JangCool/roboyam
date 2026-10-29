<template>
    <div class="flex flex-col gap-4" data-testid="group-buys">
        <header class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="m-0 text-xl font-bold">{{ term('GROUP_BUY') }}</h1>
            <Button v-if="user" as="router-link" :to="UtilCommerce.ORDERS_PATH" :label="t('commerce.ordersTitle')" icon="pi pi-receipt" size="small" severity="secondary" variant="outlined" data-testid="group-buys-my-orders" />
        </header>

        <!-- 거르기 줄(구분 탭 · 카테고리 · 개수 · 정렬) + 상품 격자를 한 카드에 — 기본 테마는 카드가 보이지 않고, 카드 테마는 흰 카드다(`main.css` `content-card`) -->
        <div class="content-card" data-testid="group-buys-card">
            <!-- 구분 탭 — 주소의 `?phase=` 가 상태다 -->
            <div role="group" :aria-label="t('groupBuy.phaseLabel')" class="flex gap-5 border-b border-hairline" data-testid="group-buys-phase">
                <button
                    v-for="opt in phaseOptions"
                    :key="opt.value"
                    type="button"
                    class="-mb-px inline-flex min-h-11 items-center border-b-2 text-[0.9375rem] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="phase === opt.value ? 'border-(--p-text-color) font-bold text-color' : 'border-transparent text-muted-color hover:text-color'"
                    :aria-pressed="phase === opt.value"
                    @click="setPhase(opt.value)"
                >
                    {{ opt.label }}
                </button>
            </div>

            <!-- 카테고리 거르기(결정 89) — 주소의 `?category=`. 위 카테고리를 고르면 그 아래 카테고리 칩이 한 줄 더 나온다(위 단 = 아래 단 포함) -->
            <nav v-if="categories.length" class="mt-3 flex flex-col gap-2" :aria-label="t('groupBuy.categoryFilter')" data-testid="group-buys-category">
                <div class="no-scrollbar -mx-1 flex gap-1.5 overflow-x-auto px-1 py-0.5">
                    <button type="button" :class="[CHIP, chipClass(!category)]" :aria-pressed="!category" @click="setCategory(null)">{{ t('groupBuy.categoryAll') }}</button>
                    <button v-for="c in categories" :key="c.id" type="button" :class="[CHIP, chipClass(topOf(category) === c.id)]" :aria-pressed="topOf(category) === c.id" @click="setCategory(c.id)">{{ c.name }}</button>
                </div>
                <div v-if="openTop && openTop.children.length" class="no-scrollbar -mx-1 flex gap-x-3 gap-y-1 overflow-x-auto px-1">
                    <button type="button" :class="[SUB_CHIP, subChipClass(category === openTop.id)]" :aria-pressed="category === openTop.id" @click="setCategory(openTop.id)">{{ t('groupBuy.categoryAllOf', { name: openTop.name }) }}</button>
                    <button v-for="c in openTop.children" :key="c.id" type="button" :class="[SUB_CHIP, subChipClass(category === c.id)]" :aria-pressed="category === c.id" @click="setCategory(c.id)">{{ c.name }}</button>
                </div>
            </nav>

            <!-- 개수 · 정렬 줄 — 정렬은 불러온 목록 안에서(주소 `?sort=`, 서버 정렬은 없다) -->
            <div class="mt-3 flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-y border-hairline py-2.5" data-testid="group-buys-toolbar">
                <p class="m-0 text-[0.8125rem] tabular-nums text-muted-color" aria-live="polite" data-testid="group-buys-count">
                    <template v-if="status === 'ready'">{{ t('groupBuy.listCount', { count: total }) }}</template>
                </p>
                <div role="group" :aria-label="t('groupBuy.sortLabel')" class="no-scrollbar -mx-1 flex items-center overflow-x-auto px-1" data-testid="group-buys-sort">
                    <template v-for="(s, i) in UtilGroupBuy.SORTS" :key="s">
                        <span v-if="i > 0" class="mx-2 h-2.5 w-px shrink-0 bg-hairline-strong" aria-hidden="true" />
                        <button
                            type="button"
                            class="min-h-8 shrink-0 whitespace-nowrap text-[0.8125rem] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                            :class="sort === s ? 'font-bold text-color' : 'text-muted-color hover:text-color'"
                            :aria-pressed="sort === s"
                            :data-sort="s"
                            @click="setSort(s)"
                        >
                            {{ t(`groupBuy.sort.${s}`) }}
                        </button>
                    </template>
                </div>
            </div>
            <p v-if="sort !== 'DEFAULT' && status === 'ready' && items.length < total" class="m-0 mt-2 text-xs text-muted-color" data-testid="group-buys-sort-note">{{ t('groupBuy.sortLoadedOnly') }}</p>

            <div v-if="status === 'loading'" :class="[GRID, 'mt-5']" role="status" :aria-label="t('common.loading')" data-testid="group-buys-loading">
                <div v-for="n in 8" :key="n" class="flex flex-col gap-2.5">
                    <div class="gadget-thumb aspect-square animate-pulse bg-surface-200 dark:bg-surface-800" />
                    <div class="h-4 w-4/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="h-5 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </div>
            <ErrorState v-else-if="status === 'error'" :title="t('groupBuy.listFailed')" :description="loadError" retry data-testid="group-buys-error" @retry="load(false)" />
            <EmptyState
                v-else-if="items.length === 0"
                :title="t(`groupBuy.empty.${phase}`)"
                :description="t('groupBuy.emptyText')"
                icon="pi pi-shopping-bag"
                data-testid="group-buys-empty"
            />
            <template v-else>
                <ul :class="[GRID, 'm-0 mt-5 list-none p-0']" data-testid="group-buys-items">
                    <li v-for="gb in sorted" :key="gb.id" :class="gb.phase === 'ENDED' ? 'opacity-85' : ''">
                        <GroupBuyCard :gb="gb" />
                    </li>
                </ul>
                <div v-if="items.length < total" class="mt-6 flex justify-center">
                    <Button type="button" :label="t('common.more')" icon="pi pi-angle-down" icon-pos="right" severity="secondary" variant="outlined" class="min-w-48" :loading="moreBusy" data-testid="group-buys-more" @click="loadMore" />
                </div>
                <Message v-if="moreError" severity="error" size="small" variant="simple" :closable="false" class="mt-2" role="alert">{{ moreError }}</Message>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyListItem, GroupBuyPhase, GroupBuyCategoryNode } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilCommerce from '~/utils/util-commerce';
import UtilGroupBuy, { type GroupBuyListSort } from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

/**
 * 공동구매 목록 `/group-buys` — 로그인 없이 볼 수 있다. 쇼핑몰 상품 격자: 구분 탭 진행·예정·종료(`?phase=`) · 카테고리 칩(`?category=`) · 개수·정렬 줄(`?sort=`) · 카드 격자(`GroupBuyCard`).
 * 카드에 1:1 그림(N차·마감 임박·예정 딱지, 다 팔림·종료 덮개)·할인율·공구가(예정 공구도 미리 공개)·정상가·별점·성사형 달성률·딱지·마감 줄.
 * 계약 commerce-groupbuy 화면 목록. 남은 수량·품절·마감 임박은 서버 값이고, 「남은 수량 적음」(남은 10% 이하 또는 5개 이하)만 화면이 그 값으로 판정한다(`UtilGroupBuy.lowStock`).
 * 정렬은 서버에 없어 불러온 목록 안에서만 한다(가정 — 「더보기」 전이면 안내 한 줄). 서버 정렬이 생기면 `api.list` 로 넘긴다.
 */
const { t } = useI18n();
const { term } = useSiteTerms();
const { user } = useAuth();
const route = useRoute();
const router = useRouter();
const api = useGroupBuyApi();
useSeoMeta({ title: () => term('GROUP_BUY') });

const phase = computed<GroupBuyPhase>(() => UtilGroupBuy.phaseOf(route.query.phase));
const phaseOptions = computed(() => UtilGroupBuy.PHASES.map((value) => ({ value, label: t(`groupBuy.phase.${value}`) })));

function setPhase(value: GroupBuyPhase): void {
    void router.replace({ query: { ...route.query, phase: value === 'ONGOING' ? undefined : value } });
}

// ── 카테고리 거르기(결정 89) ──
const categories = ref<GroupBuyCategoryNode[]>([]);
const category = computed<string | null>(() => (typeof route.query.category === 'string' && route.query.category ? route.query.category : null));
/** 고른 카테고리의 위 단 ID(위 단이면 그 자신) */
function topOf(id: string | null): string | null {
    if (!id) return null;
    return categories.value.find((c) => c.id === id || c.children.some((x) => x.id === id))?.id ?? null;
}
const openTop = computed(() => categories.value.find((c) => c.id === topOf(category.value)) ?? null);

function setCategory(id: string | null): void {
    void router.replace({ query: { ...route.query, category: id ?? undefined } });
}

/** 상품 격자 — 모바일 2열 · 태블릿 3열 · 넓은 화면 4열(쇼핑몰 격자) */
const GRID = 'grid grid-cols-2 gap-x-3 gap-y-7 sm:grid-cols-3 sm:gap-x-4 lg:grid-cols-4';
const CHIP = 'shrink-0 rounded-full border px-3.5 py-1.5 text-[0.8125rem] leading-none focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary';
const SUB_CHIP = 'shrink-0 py-0.5 text-xs focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary';

function chipClass(on: boolean): string {
    return on ? 'border-(--p-text-color) bg-(--p-text-color) font-semibold text-(--p-content-background)' : 'border-hairline-strong text-color hover:border-(--p-text-color)';
}
function subChipClass(on: boolean): string {
    return on ? 'font-bold text-color underline underline-offset-4' : 'text-muted-color hover:text-color';
}

onMounted(async () => {
    try {
        categories.value = await api.categories();
    } catch {
        // 필터만 빠진다 — 목록은 그대로
    }
});

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const items = ref<GroupBuyListItem[]>([]);
const total = ref(0);
const page = ref(0);
const moreBusy = ref(false);
const moreError = ref('');
let seq = 0;

async function load(quiet: boolean): Promise<void> {
    const mine = ++seq;
    if (!quiet) status.value = 'loading';
    loadError.value = '';
    moreError.value = '';
    try {
        const res = await api.list(phase.value, 0, category.value);
        if (mine !== seq) return;
        items.value = res.items;
        total.value = res.total;
        page.value = 0;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = 'error';
    }
}

async function loadMore(): Promise<void> {
    if (moreBusy.value) return;
    const mine = seq;
    moreBusy.value = true;
    moreError.value = '';
    try {
        const res = await api.list(phase.value, page.value + 1, category.value);
        if (mine !== seq) return;
        items.value = [...items.value, ...res.items];
        total.value = res.total;
        page.value = res.page;
    } catch (e) {
        moreError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        moreBusy.value = false;
    }
}

watch([phase, category], () => void load(false), { immediate: true });
onBeforeUnmount(() => seq++);

// ── 정렬(화면 안 — 불러온 목록만, 주소 `?sort=`) ──
const sort = computed<GroupBuyListSort>(() => UtilGroupBuy.sortOf(route.query.sort));
const sorted = computed(() => UtilGroupBuy.sortItems(items.value, sort.value));

function setSort(value: GroupBuyListSort): void {
    void router.replace({ query: { ...route.query, sort: value === 'DEFAULT' ? undefined : value } });
}
</script>
