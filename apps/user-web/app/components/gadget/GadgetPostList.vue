<template>
    <!--
        글 목록 가젯(`POST_LIST`) — 게시판·정렬·개수는 서버가 정해 `data.items` 로 준다(화면은 자르지 않는다). 줄 모양·꾸미기는 `GadgetPostRow` 가 지킨다.
        두 열(`columns: 2`)은 순위 번호가 보이면 세로로 먼저 채우고(1~5위 왼쪽, 6~10위 오른쪽 — `gadget-cols2`), 그 밖(최신순 등)은 가로로 채운다(왼쪽 → 오른쪽, 위 → 아래 —
        맨 위 줄이 가장 최근, 세로로 채우면 읽는 순서를 알기 어려웠다, 2026-11-06 사용자). 첫 글을 크게 하면 가로로(큰 글이 두 칸). 칸이 좁으면 한 열.
        인기글 기간 탭(`data.periods` — 실시간·어제·이번 주, 사용자 결정 2026-10-28): 기간별 순위를 한 번에 받아 탭으로 바꿔 그린다(가젯은 서버를 부르지 않는다). 탭은 `/popular` 화면과 같은 이름이다.
        한 열(모바일, 격자 `stack` — 모든 테마): 두 열을 쓰지 않고 한 열. 기간 탭은 알약이 아닌 작은 글자 탭(고른 탭만 굵게 + 밑줄, 2026-11-05 사용자 결정) — 모바일은 누르기 쉽게 높이 32px.
        첫 글 작은 사진 줄(`lead: THUMB`): 두 열이면 두 칸을 차지하고 아래 구분선(home-news-contract).
        모바일 쪽 넘기기(`mobilePages` 2~3, home-news-contract): 한 열이면 받은 글을 앞에서부터 `mobilePageSize` 개씩 쪽으로 나눠 가로로 밀어 넘긴다(쪽 시작에 스냅 — `gadget-scroll-row`).
        아래 동그라미(쪽 수만큼, 지금 쪽 강조 — 누르면 그 쪽으로, 밀면 따라간다). 쪽들은 가로 flex 라 가장 긴 쪽 높이로 같다. 첫 글 사진 줄은 첫 쪽에만. 인기글 기간 탭이 있으면 쪽 넘기기는 쓰지 않는다(순위 쪽이 따로 있다).
        community 와 다른 점: 글 사이 광고(`inlineAd`)·채운 줄 표시(`fills`)는 옮기지 않았다.
    -->
    <GadgetFrame :gadget="gadget" :empty="gadget.empty || (!periods && items.length === 0)" :empty-icon="cfg.sort === 'POPULAR' ? 'pi pi-chart-line' : 'pi pi-file'">
        <div v-if="periods" class="mb-2.5 flex items-center gap-3">
            <div role="tablist" :aria-label="t('home.periodTabs')" class="flex gap-3.5" data-testid="home-period-tabs">
                <button
                    v-for="key in periodKeys"
                    :id="`${uid}-tab-${key}`"
                    :key="key"
                    type="button"
                    role="tab"
                    :aria-selected="key === period"
                    :aria-controls="`${uid}-panel`"
                    class="inline-flex shrink-0 items-center border-b-2 text-xs focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    :class="[key === period ? 'border-primary font-bold text-color' : 'border-transparent text-muted-color hover:text-color', stacked ? 'min-h-8' : 'min-h-6']"
                    @click="period = key"
                >
                    {{ UtilFeedMessages.periodLabel(t, key) }}
                </button>
            </div>
            <!-- 순위 쪽(보배드림식) — 기간마다 10개씩, 1~10 · 11~20 -->
            <div v-if="pageCount > 1" role="group" :aria-label="t('home.rankPages')" class="ml-auto flex shrink-0 items-center gap-0.5" data-testid="home-rank-pages">
                <button
                    v-for="p in pageCount"
                    :key="p"
                    type="button"
                    :aria-pressed="p - 1 === page"
                    :aria-label="t('home.rankRangeLabel', rangeOf(p - 1))"
                    class="inline-flex items-center rounded px-1.5 text-[0.6875rem] tabular-nums focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                    :class="[p - 1 === page ? 'bg-surface-100 font-bold text-color dark:bg-surface-800' : 'text-muted-color hover:text-color', stacked ? 'min-h-8' : 'min-h-6']"
                    data-testid="home-rank-page"
                    @click="page = p - 1"
                >
                    {{ t('home.rankRange', rangeOf(p - 1)) }}
                </button>
            </div>
        </div>
        <!-- 빈 상태 안내와 같은 위·아래 균형(`data-grid-float`) -->
        <p v-if="periods && items.length === 0" :id="`${uid}-panel`" role="tabpanel" class="m-0 py-6 text-center text-sm text-muted-color" data-grid-float data-testid="home-period-empty">{{ t('home.periodEmpty') }}</p>
        <!-- 모바일 쪽 넘기기 -->
        <div v-else-if="pages" data-testid="home-post-pages">
            <div ref="pager" class="gadget-scroll-row" :style="PAGE" role="group" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" @scroll.passive="onPagerScroll">
                <ul v-for="(pg, p) in pages" :key="p" class="m-0 list-none p-0" data-testid="home-post-page">
                    <GadgetPostRow
                        v-for="(row, i) in pg"
                        :key="row.id"
                        :row="row"
                        :cfg="cfg"
                        :rank="ranked ? p * cfg.mobilePageSize + i + 1 : null"
                        :hero="hero && p === 0 && i === 0"
                        :lead-thumb="leadThumb && p === 0 && i === 0"
                        :class="leadThumb && p === 0 && i === 0 ? LEAD_THUMB_CLASS : ''"
                    />
                </ul>
            </div>
            <div role="group" :aria-label="t('home.pageDots', { name: gadget.title })" class="mt-1 flex justify-center gap-0.5" data-testid="home-post-page-dots">
                <button
                    v-for="(_, p) in pages"
                    :key="p"
                    type="button"
                    class="grid size-6 place-items-center rounded-full focus-visible:outline-2 focus-visible:outline-primary"
                    :aria-label="t('home.pageDot', { n: p + 1, total: pages.length })"
                    :aria-current="p === pageNow ? 'true' : undefined"
                    data-testid="home-post-page-dot"
                    @click="goPage(p)"
                >
                    <span class="block size-1.5 rounded-full" :class="p === pageNow ? 'bg-primary' : 'bg-surface-300 dark:bg-surface-600'" aria-hidden="true" />
                </button>
            </div>
        </div>
        <ul
            v-else
            :id="periods ? `${uid}-panel` : undefined"
            :role="periods ? 'tabpanel' : undefined"
            :aria-labelledby="periods ? `${uid}-tab-${period}` : undefined"
            class="m-0 list-none p-0"
            :class="listClass"
            :style="listStyle"
            data-testid="home-post-list"
        >
            <GadgetPostRow
                v-for="(row, i) in shown"
                :key="row.id"
                :row="row"
                :cfg="cfg"
                :rank="ranked ? page * RANK_PAGE + i + 1 : null"
                :hero="hero && i === 0"
                :lead-thumb="leadThumb && i === 0"
                :dense="!!periods"
                :class="[(hero || leadThumb) && i === 0 ? 'col-span-full' : '', leadThumb && i === 0 ? LEAD_THUMB_CLASS : '', i === leftColumnEnd ? '@min-[28rem]:border-b-0!' : '']"
            />
        </ul>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import UtilFeedMessages from '~/utils/util-feed-messages';
import UtilGadget from '~/utils/util-gadget';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();
const uid = useId();

const cfg = computed(() => UtilGadget.postList(props.gadget.config));
/** 기간 탭의 기간별 글 — 없으면 탭 없이 `items` */
const periods = computed(() => UtilGadget.postPeriods(props.gadget.data));
/** 보이는 기간 탭(받은 기간만, 실시간 · 어제 · 이번 주 순) */
const periodKeys = computed(() => (['REALTIME', 'YESTERDAY', 'WEEK'] as const).filter((key) => periods.value?.[key] !== undefined));
/** 고른 기간 — 처음은 글이 있는 첫 기간(실시간 → 어제 → 이번 주, 다 비면 받은 첫 기간). 실시간이 비어 큰 빈 카드가 먼저 보이지 않게 */
const period = ref<PopularPeriod>('REALTIME');
watch(periodKeys, (keys) => {
    if (!keys.length) return;
    if (keys.includes(period.value as (typeof keys)[number]) && (periods.value?.[period.value]?.length ?? 0) > 0) return;
    period.value = keys.find((key) => (periods.value?.[key]?.length ?? 0) > 0) ?? keys[0]!;
}, { immediate: true });

const items = computed(() => (periods.value ? (periods.value[period.value] ?? []) : UtilGadget.postItems(props.gadget.data)));

/** 기간 탭 목록은 10개씩 쪽을 나눈다(설정 개수가 20이면 1~10 · 11~20 — 사용자 결정 2026-11-06, 보배드림식) */
const RANK_PAGE = 10;
const page = ref(0);
const pageCount = computed(() => (periods.value ? Math.ceil(items.value.length / RANK_PAGE) : 1));
watch(period, () => {
    page.value = 0;
});
watch(pageCount, (n) => {
    if (page.value >= n) page.value = 0;
});
/** 그리는 줄 — 기간 탭이면 고른 쪽의 10개, 아니면 전부 */
const shown = computed(() => (periods.value ? items.value.slice(page.value * RANK_PAGE, (page.value + 1) * RANK_PAGE) : items.value));
function rangeOf(p: number) {
    return { from: p * RANK_PAGE + 1, to: Math.min((p + 1) * RANK_PAGE, items.value.length) };
}
const ranked = computed(() => UtilGadget.rankShown(cfg.value));
/** 한 열(모바일)인가 — 그러면 두 열 설정이어도 한 열 */
const stacked = useGadgetStacked();
const hero = computed(() => cfg.value.lead === 'HERO' && shown.value.length > 0);
/** 첫 글만 작은 사진 줄(`lead: THUMB` — 왼쪽 작은 사진 + 제목 + 메타) — 나머지 줄은 설정 그대로 */
const leadThumb = computed(() => cfg.value.lead === 'THUMB' && shown.value.length > 0);
/** 첫 글 사진 줄 아래 구분선 */
const LEAD_THUMB_CLASS = 'mb-2.5 border-b border-surface pb-2.5';
/** 첫 글이 두 칸을 차지하는가(크게 · 작은 사진 줄) — 그러면 두 열은 가로로 채운다 */
const wideFirst = computed(() => hero.value || leadThumb.value);

/** 두 열 — 순위 목록(큰 글 없음)은 세로로 먼저(행 수 = 글 수의 절반 올림), 그 밖은 가로 격자 */
const listClass = computed(() => {
    // 한 열은 줄을 펴지 않는다 — 인기글 줄 간격은 줄 여백으로 고정해 쪽마다 같다(`GadgetPostRow` `dense`, 2026-11-06 사용자)
    if (cfg.value.columns !== 2 || stacked.value) return '';
    return wideFirst.value || !ranked.value ? 'grid @min-[28rem]:grid-cols-2 @min-[28rem]:gap-x-6' : 'gadget-cols2';
});
/** 세로로 채우는 두 열의 왼쪽 열 끝 줄(`last:` 는 오른쪽 열 끝에만 걸린다) — 두 열이 아니면 -1 */
const leftColumnEnd = computed(() => (cfg.value.columns === 2 && !wideFirst.value && ranked.value && !stacked.value && shown.value.length > 1 ? Math.ceil(shown.value.length / 2) - 1 : -1));
const listStyle = computed(() => (cfg.value.columns === 2 && !wideFirst.value && ranked.value && !stacked.value ? { '--gx-rows': String(Math.ceil(shown.value.length / 2)) } : undefined));

/* ── 모바일 쪽 넘기기(`mobilePages` 2~3) ── */

/** 한 쪽 폭 — 카드 글자 폭 전체(다음 쪽은 스냅으로 넘긴다) */
const PAGE = { '--row-item': '100%' };
/**
 * 쪽마다의 글 — 한 열이고 쪽 넘기기를 켰을 때 받은 글을 앞에서부터 `mobilePageSize` 개씩(쪽 수까지 — 나머지 글은 PC 만 보인다).
 * 기간 탭 목록은 순위 쪽이 따로 있어 나누지 않는다. 한 쪽 분량도 안 되면 null(보통 목록)
 */
const pages = computed<PostFeedRow[][] | null>(() => {
    if (!stacked.value || periods.value || cfg.value.mobilePages < 2) return null;
    const size = cfg.value.mobilePageSize;
    const out: PostFeedRow[][] = [];
    for (let p = 0; p < cfg.value.mobilePages; p++) {
        const page = shown.value.slice(p * size, (p + 1) * size);
        if (page.length) out.push(page);
    }
    return out.length > 1 ? out : null;
});
const pager = ref<HTMLElement | null>(null);
const pagerIndex = ref(0);
/** 지금 쪽(쪽 수가 줄면 끝 쪽으로) */
const pageNow = computed(() => Math.min(pagerIndex.value, Math.max(0, (pages.value?.length ?? 1) - 1)));

/** 밀면 지금 쪽을 따라간다 — 쪽 폭이 같아 끝까지 민 거리를 쪽 수로 나눈다 */
function onPagerScroll(): void {
    const el = pager.value;
    const n = pages.value?.length ?? 0;
    if (!el || n < 2) return;
    const max = el.scrollWidth - el.clientWidth;
    pagerIndex.value = max > 0 ? Math.round((el.scrollLeft / max) * (n - 1)) : 0;
}

/** 동그라미를 누르면 그 쪽으로(움직임 줄이기 설정이면 바로) */
function goPage(p: number): void {
    const el = pager.value;
    const first = el?.children[0] as HTMLElement | undefined;
    const target = el?.children[p] as HTMLElement | undefined;
    if (!el || !first || !target) return;
    pagerIndex.value = p;
    const reduce = typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    el.scrollTo({ left: target.offsetLeft - first.offsetLeft, behavior: reduce ? 'auto' : 'smooth' });
}
</script>
