<template>
    <!--
        글 목록 가젯(`POST_LIST`) — 게시판·정렬·개수는 서버가 정해 `data.items` 로 준다(화면은 자르지 않는다). 줄 모양·꾸미기는 `HomeGadgetPostRow` 가 지킨다.
        두 열(`columns: 2`)은 첫 글을 크게 하지 않으면 세로로 먼저 채운다(1~5위 왼쪽, 6~10위 오른쪽 — `gadget-cols2`), 첫 글을 크게 하면 가로로(큰 글이 두 칸). 칸이 좁으면 한 열.
        인기글 기간 탭(`data.periods` — 실시간·어제·이번 주, 사용자 결정 2026-10-28): 기간별 순위를 한 번에 받아 탭으로 바꿔 그린다(가젯은 서버를 부르지 않는다). 탭은 `/popular` 화면과 같은 이름이다.
        한 열(모바일, 격자 `stack` — 모든 테마): 두 열을 쓰지 않고 한 열, 기간 탭은 손가락에 맞게 큰 알약(높이 36px · 가로로 밀 수 있는 줄).
        community 와 다른 점: 글 사이 광고(`inlineAd`)·채운 줄 표시(`fills`)는 옮기지 않았다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || (!periods && items.length === 0)" :empty-icon="cfg.sort === 'POPULAR' ? 'pi pi-chart-line' : 'pi pi-file'">
        <div v-if="periods" role="tablist" :aria-label="t('home.periodTabs')" class="mb-2 flex" :class="stacked ? 'no-scrollbar -mx-1 gap-1.5 overflow-x-auto p-1' : 'gap-1'" data-testid="home-period-tabs">
            <button
                v-for="key in periodKeys"
                :id="`${uid}-tab-${key}`"
                :key="key"
                type="button"
                role="tab"
                :aria-selected="key === period"
                :aria-controls="`${uid}-panel`"
                class="rounded-full font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                :class="[key === period ? 'bg-primary text-primary-contrast' : 'bg-surface-100 text-muted-color hover:text-color dark:bg-surface-800', stacked ? 'h-9 shrink-0 px-4 text-[0.8125rem]' : 'px-3 py-1 text-xs']"
                @click="period = key"
            >
                {{ UtilFeedMessages.periodLabel(t, key) }}
            </button>
        </div>
        <p v-if="periods && items.length === 0" :id="`${uid}-panel`" role="tabpanel" class="m-0 py-6 text-center text-sm text-muted-color">{{ t('home.periodEmpty') }}</p>
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
                v-for="(row, i) in items"
                :key="row.id"
                :row="row"
                :cfg="cfg"
                :rank="ranked ? i + 1 : null"
                :hero="hero && i === 0"
                :class="hero && i === 0 ? 'col-span-full' : ''"
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
const ranked = computed(() => UtilHomeGadget.rankShown(cfg.value));
/** 한 열(모바일)인가 — 그러면 두 열 설정이어도 한 열 */
const stacked = useHomeGadgetStacked();
const hero = computed(() => cfg.value.lead === 'HERO' && items.value.length > 0);

/** 두 열 — 큰 글이 없으면 세로로 먼저(행 수 = 글 수의 절반 올림), 있으면 가로 격자 */
const listClass = computed(() => {
    if (cfg.value.columns !== 2 || stacked.value) return '';
    return hero.value ? 'grid @min-[28rem]:grid-cols-2 @min-[28rem]:gap-x-6' : 'gadget-cols2';
});
const listStyle = computed(() => (cfg.value.columns === 2 && !hero.value && !stacked.value ? { '--gx-rows': String(Math.ceil(items.value.length / 2)) } : undefined));
</script>
