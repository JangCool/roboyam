<template>
    <div>
        <!-- 머리: 이름 · 고른 기간이 무엇을 보여 주는지 한 줄 설명 -->
        <header>
            <h1 class="break-words text-lg font-bold leading-tight">{{ name }}</h1>
            <p class="mt-0.5 text-xs text-muted-color" data-testid="popular-subtitle">{{ UtilFeedMessages.subtitle(t, period) }}</p>
        </header>

        <!-- 기간 탭 — 주소의 `?period=` 와 같이 움직인다(누르면 주소가 바뀌고 1쪽으로). 고른 탭만 Tab 키로 닿고, 화살표·Home·End 로 초점을 옮긴 뒤 Enter·Space 로 고른다 -->
        <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1">
            <div role="tablist" :aria-label="t('feed.periodTabs', { name })" class="flex shrink-0 gap-1" data-testid="popular-period-tabs" @keydown="onTabKeydown">
                <button
                    v-for="p in UtilPopular.PERIODS"
                    :id="UtilPopular.tabId(uid, p)"
                    :key="p"
                    type="button"
                    role="tab"
                    :data-period="p"
                    :aria-selected="p === period"
                    aria-controls="popular-list"
                    :tabindex="p === period ? 0 : -1"
                    :class="[TAB, p === period ? TAB_ON : TAB_OFF]"
                    @click="changePeriod(p)"
                >
                    {{ UtilFeedMessages.periodLabel(t, p) }}
                </button>
            </div>
            <!-- 이번 주 탭이면 그 주의 범위 — 서버가 서비스 시간대 달력으로 정해 준 값이다 -->
            <p v-if="rangeNote" class="m-0 text-xs text-muted-color" data-testid="popular-range">{{ rangeNote }}</p>
        </div>

        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <div id="popular-list" class="mt-3" role="tabpanel" :aria-labelledby="UtilPopular.tabId(uid, period)" :aria-busy="status === 'loading'">
            <div v-if="status === 'loading'" role="status" :aria-label="t('feed.loading', { name: periodName })"><FeedSkeleton /></div>

            <ErrorState v-else-if="status === 'error'" :title="t('feed.periodLoadFailed', { period: UtilFeedMessages.periodLabel(t, period), name })" :description="errorMessage" retry @retry="load" />

            <template v-else-if="result">
                <template v-if="items.length === 0">
                    <EmptyState v-if="page > 1" :title="t('feed.pageEmptyTitle')" :description="t('feed.pageEmptyText')" icon="pi pi-file">
                        <Button as="router-link" :to="pageLink(1)" :label="t('feed.firstPage')" />
                    </EmptyState>
                    <EmptyState v-else :title="empty.title" :description="empty.text" icon="pi pi-chart-line">
                        <Button as="router-link" to="/boards" :label="t('feed.browseBoards')" />
                    </EmptyState>
                </template>

                <!-- 글 — 번호는 전체 순위 기준으로 이어 붙는다(2쪽의 첫 글은 21번). 어제·지난주 글로 채운 줄은 제목 앞에 작은 표시 -->
                <template v-else>
                    <p v-if="fillNotice" class="mb-2 text-xs text-muted-color" data-testid="popular-fill-notice">{{ fillNotice }}</p>

                    <ul class="-mx-4 m-0 list-none p-0 md:mx-0">
                        <FeedPostItem
                            v-for="(row, i) in items"
                            :key="row.id"
                            :row="row"
                            :rank="startRank + i"
                            :fill="UtilPopular.fillOf(fills, row.id)"
                            :date-display="display.dateDisplay"
                            :new-post-hours="display.newPostHours"
                        />
                    </ul>

                    <!-- 쪽 번호 — 순위 스냅샷(기본 50개)이 끝이다 -->
                    <BoardPager :page="result.page" :total-pages="result.totalPages" :to="pageLink" />

                    <BoardInvestmentNotice v-if="hasInvestmentBoard" class="mt-2" />
                </template>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularListResponse, PopularPeriod } from '~/composables/home/api/useHomeApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilFeedMessages from '~/utils/util-feed-messages';
import UtilPopular from '~/utils/util-popular';

/**
 * 인기글 전체 — `/popular?period=&page=`(community `pages/popular.vue`, `docs/api-popular.md` 5절). 기간 탭(실시간·어제·이번 주)의 순위를 쪽 번호로 본다.
 * 주소의 `period`·`page` 가 화면의 상태다(잘못된 값은 실시간·1쪽, 기본값은 주소에서 뺀다). 기간·쪽·보는 사람이 바뀔 때마다 새로 받는다.
 * 어제·지난주 글로 채운 줄은 응답의 `fills` 로 제목 앞에 작은 표시. 이번 주 탭은 응답의 `from`·`to` 로 범위를 탭 옆에 보인다(주 시작 요일은 서버가 정한다).
 * ⚠ 목업 API(`useHomeApi().popular` — 실제 게시판 글을 모아 점수로 줄 세운다, `?mock=popular-anytime` 으로 기간을 무시). 메뉴 BUILTIN `POPULAR`(`/popular`)가 이곳이다.
 * 원본과 달라진 점: 탭 컴포넌트(`PopularPeriodTabs`)는 이 화면에서만 쓰여 화면 안에 두었고, 홈 가젯 몫(머리 안 탭 배치)은 없다.
 */
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useHomeApi();
const { user } = useAuth();
const { bySlug, ensureLoaded: ensureBoards } = useBoards();
const { term } = useSiteTerms();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();
void ensureBoards();

const uid = useId();
const name = computed(() => term('POPULAR'));
const page = computed(() => UtilPopular.parsePage(route.query));
const period = computed<PopularPeriod>(() => UtilPopular.parsePeriod(route.query));
const periodName = computed(() => `${UtilFeedMessages.periodLabel(t, period.value)} ${name.value}`);

const TAB = 'relative cursor-pointer whitespace-nowrap rounded-full px-2.5 py-1 text-[0.8125rem] leading-none after:absolute after:inset-x-0 after:-inset-y-2 after:content-[""] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary';
const TAB_ON = 'bg-primary font-semibold text-primary-contrast';
const TAB_OFF = 'text-muted-color hover:text-color';

const status = ref<'loading' | 'ready' | 'error'>('loading');
const result = ref<PopularListResponse | null>(null);
const errorMessage = ref('');

const items = computed<PostFeedRow[]>(() => result.value?.items ?? []);
const fills = computed(() => UtilPopular.fillsOf(result.value?.fills));
const fillNotice = computed(() => {
    const first = items.value.map((row) => UtilPopular.fillOf(fills.value, row.id)).find((fill) => fill !== null);
    return first ? UtilFeedMessages.fillNotice(t, first) : '';
});
/** 이번 주 탭 옆의 범위 — 받은 응답이 보는 기간의 것일 때만 */
const rangeNote = computed(() => (result.value?.period === period.value ? UtilFeedMessages.rangeNote(t, period.value, result.value) : ''));
const empty = computed(() => UtilFeedMessages.popularEmpty(t, period.value));
const startRank = computed(() => UtilPopular.rankStart(result.value?.page ?? page.value, UtilPopular.PAGE_SIZE));
const hasInvestmentBoard = computed(() => items.value.some((row) => bySlug(row.board.slug)?.investmentNotice));
const summary = computed(() => UtilFeedMessages.summary(t, periodName.value, status.value, items.value.length, result.value?.hasNext ?? false));

function pageLink(target: number) {
    return { path: '/popular', query: UtilPopular.build(target, period.value) };
}

function changePeriod(next: PopularPeriod) {
    if (next === period.value) return;
    void router.push({ path: '/popular', query: UtilPopular.build(1, next) });
}

/** 화살표·Home·End 로 초점만 옮긴다(눌러야 목록을 받으므로 초점만으로는 고르지 않는다) */
function onTabKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;
    const raw = target?.closest<HTMLElement>('[data-period]')?.dataset.period;
    const current = UtilPopular.PERIODS.find((p) => p === raw);
    if (!current) return;
    const next = UtilPopular.movedTo(current, event.key);
    if (!next) return;
    event.preventDefault();
    (event.currentTarget as HTMLElement).querySelector<HTMLElement>(`[data-period="${next}"]`)?.focus();
}

let requestSeq = 0;
let abort: AbortController | null = null;

async function load() {
    const mine = ++requestSeq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    result.value = null;
    status.value = 'loading';
    try {
        const res = await api.popular({ period: period.value, page: page.value, size: UtilPopular.PAGE_SIZE }, controller.signal);
        if (mine !== requestSeq) return;
        result.value = res;
        status.value = 'ready';
    } catch (e) {
        if (mine !== requestSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

// 기간·쪽 · 보는 사람이 바뀔 때 새로 받는다. 쪽을 바꾸면 맨 위로
watch(
    () => [period.value, page.value, user.value?.id ?? null] as const,
    (now, before) => {
        void load();
        if (before && now[1] !== before[1] && typeof window !== 'undefined') window.scrollTo({ top: 0 });
    },
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

useSeoMeta({
    title: () => UtilFeedMessages.popularSeoTitle(t, name.value, period.value, page.value),
    description: () => UtilFeedMessages.popularSeoDescription(t, period.value),
    robots: 'noindex',
});
</script>
