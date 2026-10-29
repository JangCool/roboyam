<template>
    <div class="mx-auto flex max-w-4xl flex-col gap-4" data-testid="group-buys">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="m-0 text-lg font-bold">{{ term('GROUP_BUY') }}</h1>
            <Button v-if="user" as="router-link" :to="UtilCommerce.ORDERS_PATH" :label="t('commerce.ordersTitle')" icon="pi pi-receipt" size="small" severity="secondary" variant="outlined" data-testid="group-buys-my-orders" />
        </div>

        <!-- 탭 — 주소의 `?phase=` 가 상태다 -->
        <SelectButton
            :model-value="phase"
            :options="phaseOptions"
            option-label="label"
            option-value="value"
            :allow-empty="false"
            size="small"
            :aria-label="t('groupBuy.phaseLabel')"
            data-testid="group-buys-phase"
            @update:model-value="setPhase"
        />

        <!-- 카테고리 거르기(결정 89) — 주소의 `?category=`. 위 카테고리를 고르면 그 아래 카테고리 칩이 한 줄 더 나온다(위 단 = 아래 단 포함) -->
        <nav v-if="categories.length" class="flex flex-col gap-1.5" :aria-label="t('groupBuy.categoryFilter')" data-testid="group-buys-category">
            <div class="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-0.5">
                <button type="button" class="shrink-0 rounded-full border px-3 py-1 text-xs" :class="chipClass(!category)" :aria-pressed="!category" @click="setCategory(null)">{{ t('groupBuy.categoryAll') }}</button>
                <button v-for="c in categories" :key="c.id" type="button" class="shrink-0 rounded-full border px-3 py-1 text-xs" :class="chipClass(topOf(category) === c.id)" :aria-pressed="topOf(category) === c.id" @click="setCategory(c.id)">{{ c.name }}</button>
            </div>
            <div v-if="openTop && openTop.children.length" class="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-0.5">
                <button type="button" class="shrink-0 rounded-full px-2.5 py-0.5 text-[11px]" :class="subChipClass(category === openTop.id)" :aria-pressed="category === openTop.id" @click="setCategory(openTop.id)">{{ t('groupBuy.categoryAllOf', { name: openTop.name }) }}</button>
                <button v-for="c in openTop.children" :key="c.id" type="button" class="shrink-0 rounded-full px-2.5 py-0.5 text-[11px]" :class="subChipClass(category === c.id)" :aria-pressed="category === c.id" @click="setCategory(c.id)">{{ c.name }}</button>
            </div>
        </nav>

        <div v-if="status === 'loading'" class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3" role="status" :aria-label="t('common.loading')" data-testid="group-buys-loading">
            <div v-for="n in 6" :key="n" class="h-72 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
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
            <ul class="m-0 grid list-none grid-cols-1 gap-3 p-0 sm:grid-cols-2 lg:grid-cols-3" data-testid="group-buys-items">
                <li v-for="gb in items" :key="gb.id">
                    <NuxtLink
                        :to="UtilGroupBuy.detailPath(gb.id)"
                        class="flex h-full flex-col gap-2 rounded-xl border border-surface bg-surface-0 p-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-900"
                        :class="gb.phase === 'ENDED' ? 'opacity-80' : ''"
                        data-testid="group-buys-item"
                    >
                        <GroupBuyThumb :src="gb.thumbnailUrl" :alt="gb.title">
                            <span v-if="gb.roundNo > 1" class="absolute left-2 top-2"><PointShopTag tone="primary">{{ t('groupBuy.roundNo', { n: gb.roundNo }) }}</PointShopTag></span>
                        </GroupBuyThumb>
                        <p class="m-0 line-clamp-2 text-sm font-semibold leading-snug">{{ gb.title }}</p>
                        <p class="m-0 flex flex-wrap items-baseline gap-x-2">
                            <span class="text-base font-bold tabular-nums text-primary">{{ gb.priceFrom ? t('groupBuy.priceFrom', { price: UtilGroupBuyMessages.won(t, gb.groupPrice) }) : UtilGroupBuyMessages.won(t, gb.groupPrice) }}</span>
                            <s v-if="gb.normalPrice" class="text-xs tabular-nums text-muted-color">
                                <span class="sr-only">{{ t('groupBuy.normalPrice') }}</span>{{ UtilGroupBuyMessages.won(t, gb.normalPrice) }}
                            </s>
                        </p>
                        <!-- 리뷰 평균 별점·수(2b-6 — 같은 상품의 모든 차수) -->
                        <p v-if="gb.reviews && gb.reviews.count > 0" class="m-0 flex items-center gap-1 text-xs text-muted-color" data-testid="group-buys-item-reviews">
                            <i class="pi pi-star-fill text-[11px] text-amber-500" aria-hidden="true" />
                            <span class="font-semibold tabular-nums text-(--p-text-color)">{{ gb.reviews.average?.toFixed(1) }}</span>
                            <span class="tabular-nums">{{ t('groupBuyReview.countShort', { count: gb.reviews.count }) }}</span>
                        </p>
                        <!-- 성사형 진행률(확정 / 최소) -->
                        <div v-if="gb.minQty > 0 && gb.phase !== 'UPCOMING'" class="flex flex-col gap-1">
                            <ProgressBar :value="UtilGroupBuy.progress(gb.currentQty, gb.minQty) ?? 0" :show-value="false" class="h-1.5" :aria-label="t('groupBuy.progressLabel')" />
                            <span class="text-xs tabular-nums text-muted-color">{{ t('groupBuy.progress', { current: gb.currentQty, min: gb.minQty }) }}</span>
                        </div>
                        <div class="mt-auto flex flex-wrap items-center gap-1">
                            <PointShopTag v-for="tag in tagsOf(gb)" :key="tag.key" :tone="tag.tone">{{ tag.text }}</PointShopTag>
                        </div>
                        <p class="m-0 text-xs tabular-nums text-muted-color">{{ UtilGroupBuyMessages.deadline(t, time, gb) }}</p>
                    </NuxtLink>
                </li>
            </ul>
            <div v-if="items.length < total" class="flex justify-center">
                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="moreBusy" data-testid="group-buys-more" @click="loadMore" />
            </div>
            <Message v-if="moreError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ moreError }}</Message>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyListItem, GroupBuyPhase, GroupBuyCategoryNode } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilCommerce from '~/utils/util-commerce';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

/**
 * 공동구매 목록 `/group-buys` — 로그인 없이 볼 수 있다. 탭 진행·예정·종료(`?phase=`), 카드에 가격(예정 공구도 미리 공개)·정상가·성사형 진행률·딱지(N차·마감 임박·남은 수량 적음·잠시 구매 불가·다 팔림·종료 사유)·마감 줄.
 * 계약 commerce-groupbuy 화면 목록. 남은 수량·품절·마감 임박은 서버 값이고, 「남은 수량 적음」(남은 10% 이하 또는 5개 이하)만 화면이 그 값으로 판정한다(`UtilGroupBuy.lowStock`).
 */
const { t } = useI18n();
const { term } = useSiteTerms();
const { user } = useAuth();
const route = useRoute();
const router = useRouter();
const time = useDisplayTime();
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

function chipClass(on: boolean): string {
    return on ? 'border-primary bg-primary text-primary-contrast' : 'border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800';
}
function subChipClass(on: boolean): string {
    return on ? 'bg-primary/15 font-semibold text-primary' : 'text-muted-color hover:text-color';
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

type Tone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';

/** 카드 딱지 — 글자와 함께(색만으로 뜻을 가르지 않는다) */
function tagsOf(gb: GroupBuyListItem): { key: string; text: string; tone: Tone }[] {
    const out: { key: string; text: string; tone: Tone }[] = [];
    if (gb.phase === 'UPCOMING') out.push({ key: 'phase', text: t('groupBuy.phase.UPCOMING'), tone: 'primary' });
    if (gb.phase === 'ONGOING') {
        if (gb.status === 'CLOSING') out.push({ key: 'judging', text: t('groupBuy.judgingShort'), tone: 'neutral' });
        else if (gb.stockState === 'SOLD_OUT') out.push({ key: 'stock', text: t('groupBuy.stock.SOLD_OUT'), tone: 'danger' });
        else if (gb.stockState === 'TEMP_SOLD_OUT') out.push({ key: 'stock', text: t('groupBuy.stock.TEMP_SOLD_OUT'), tone: 'warn' });
        else if (UtilGroupBuy.lowStock(gb.remainingQty, gb.maxQty)) out.push({ key: 'low', text: t('groupBuy.remainingFew', { count: gb.remainingQty }), tone: 'warn' });
        if (gb.extendedUntil) out.push({ key: 'extended', text: t('groupBuy.extendedShort'), tone: 'warn' });
        else if (gb.closingSoon && gb.status === 'RECRUITING') out.push({ key: 'soon', text: t('groupBuy.closingSoon'), tone: 'danger' });
    }
    if (gb.phase === 'ENDED' && gb.closeReason) out.push({ key: 'reason', text: t(`groupBuy.closeReason.${gb.closeReason}`), tone: gb.closeReason === 'SOLD_OUT' ? 'success' : 'neutral' });
    out.push({ key: 'kind', text: gb.minQty > 0 ? t('groupBuy.kindMin') : t('groupBuy.kindFirst'), tone: 'neutral' });
    return out;
}
</script>
