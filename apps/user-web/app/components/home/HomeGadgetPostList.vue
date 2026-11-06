<template>
    <!--
        글 목록 가젯(`POST_LIST`) — 게시판·정렬·개수는 서버가 정해 `data.items` 로 준다(화면은 자르지 않는다). 줄 모양·꾸미기는 `HomeGadgetPostRow` 가 지킨다.
        두 열(`columns: 2`)은 첫 글을 크게 하지 않으면 세로로 먼저 채운다(1~5위 왼쪽, 6~10위 오른쪽 — `gadget-cols2`), 첫 글을 크게 하면 가로로(큰 글이 두 칸). 칸이 좁으면 한 열.
        인기글 기간 탭(`data.periods` — 실시간·어제·이번 주, 사용자 결정 2026-10-28): 기간별 순위를 한 번에 받아 탭으로 바꿔 그린다(가젯은 서버를 부르지 않는다). 탭은 `/popular` 화면과 같은 이름이다.
        한 열(모바일, 격자 `stack` — 모든 테마): 두 열을 쓰지 않고 한 열. 기간 탭은 알약이 아닌 작은 글자 탭(고른 탭만 굵게 + 밑줄, 2026-11-05 사용자 결정) — 모바일은 누르기 쉽게 높이 32px.
        community 와 다른 점: 글 사이 광고(`inlineAd`)·채운 줄 표시(`fills`)는 옮기지 않았다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || (!periods && items.length === 0)" :empty-icon="cfg.sort === 'POPULAR' ? 'pi pi-chart-line' : 'pi pi-file'">
        <div v-if="periods" class="flex items-center gap-3" :class="stacked ? 'mb-1.5' : 'mb-1'">
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
            <HomeGadgetPostRow
                v-for="(row, i) in shown"
                :key="row.id"
                :row="row"
                :cfg="cfg"
                :rank="ranked ? page * RANK_PAGE + i + 1 : null"
                :hero="hero && i === 0"
                :class="[hero && i === 0 ? 'col-span-full' : '', i === leftColumnEnd ? '@min-[28rem]:border-b-0!' : '']"
            />
        </ul>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilFeedMessages from '~/utils/util-feed-messages';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();
const uid = useId();

const cfg = computed(() => UtilHomeGadget.postList(props.gadget.config));
/** 기간 탭의 기간별 글 — 없으면 탭 없이 `items` */
const periods = computed(() => UtilHomeGadget.postPeriods(props.gadget.data));
/** 보이는 기간 탭(받은 기간만, 실시간 · 어제 · 이번 주 순) */
const periodKeys = computed(() => (['REALTIME', 'YESTERDAY', 'WEEK'] as const).filter((key) => periods.value?.[key] !== undefined));
/** 고른 기간 — 처음은 실시간(없으면 받은 첫 기간) */
const period = ref<PopularPeriod>('REALTIME');
watch(periodKeys, (keys) => {
    if (keys.length && !keys.includes(period.value as (typeof keys)[number])) period.value = keys[0]!;
}, { immediate: true });

const items = computed(() => (periods.value ? (periods.value[period.value] ?? []) : UtilHomeGadget.postItems(props.gadget.data)));

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
const ranked = computed(() => UtilHomeGadget.rankShown(cfg.value));
/** 한 열(모바일)인가 — 그러면 두 열 설정이어도 한 열 */
const stacked = useHomeGadgetStacked();
const hero = computed(() => cfg.value.lead === 'HERO' && shown.value.length > 0);

/** 두 열 — 큰 글이 없으면 세로로 먼저(행 수 = 글 수의 절반 올림), 있으면 가로 격자 */
const listClass = computed(() => {
    // 촘촘 한 열 — 상자가 내용보다 크면(옆 가젯과 같은 줄 맞추기) 줄을 상자 높이에 고르게 편다
    if (cfg.value.columns !== 2 || stacked.value) return cfg.value.rowStyle === 'COMPACT' && !stacked.value ? 'flex flex-1 flex-col justify-between' : '';
    return hero.value ? 'grid @min-[28rem]:grid-cols-2 @min-[28rem]:gap-x-6' : 'gadget-cols2';
});
/** 세로로 채우는 두 열의 왼쪽 열 끝 줄(`last:` 는 오른쪽 열 끝에만 걸린다) — 두 열이 아니면 -1 */
const leftColumnEnd = computed(() => (cfg.value.columns === 2 && !hero.value && !stacked.value && shown.value.length > 1 ? Math.ceil(shown.value.length / 2) - 1 : -1));
const listStyle = computed(() => (cfg.value.columns === 2 && !hero.value && !stacked.value ? { '--gx-rows': String(Math.ceil(shown.value.length / 2)) } : undefined));
</script>
