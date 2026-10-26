<template>
    <!--
        소모임 목록 `/groups?category=&sort=&q=` (메뉴 `GROUPS_ALL`) — 분류 칩 · 정렬 · 컴팩트 목록 · 더보기 · 검색 · 만들기. 데스크톱 오른쪽에 내 소모임 요약.
        주소의 쿼리가 화면 상태다(뒤로 가기·공유·새로고침이 같은 결과). 칩·정렬·검색어 지우기는 링크라 주소만 바뀌고, 이상한 값은 기본값으로 돌린다.
        분류 칩은 `GET /api/groups/categories`(관리자가 정한 이름·순서·소모임 수). 분류 목록에 없는 `?category=` 는 목록을 받지 않고 안내 + [전체 보기].
        맨 위 「지금 뜨는 소모임」 슬라이드(6단계, `GET /api/groups/trending`)는 한 번 받아 고정하고 검색 중에는 숨긴다. 아래 목록은 슬라이드를 기다렸다가 그 소모임들을 `exclude` 로 뺀다
        (「내 소모임」 정렬은 빼지 않는다). 슬라이드가 실패해도 목록은 `exclude` 없이 그대로 받고, 두 오류는 따로 보인다.
        community 대비: 사이드의 광고 자리는 로보얌에 광고가 없어 뺐다.
    -->
    <div>
        <header class="flex items-center gap-2">
            <h1 class="min-w-0 flex-1 text-lg font-bold leading-tight">{{ t('group.title') }}</h1>
            <Button
                type="button"
                icon="pi pi-search"
                severity="secondary"
                variant="text"
                rounded
                :aria-label="t('group.search')"
                :aria-expanded="searchVisible"
                :aria-controls="searchId"
                data-testid="group-search-toggle"
                @click="toggleSearch"
            />
            <Button type="button" :label="t('group.create')" size="small" class="shrink-0 whitespace-nowrap" data-testid="group-create" @click="onCreate" />
        </header>

        <!-- 검색 — 이름·소개에서 찾는다(`?q=`). 엔터·검색 단추로 주소가 바뀐다 -->
        <form v-if="searchVisible" :id="searchId" role="search" class="mt-3" data-testid="group-search" @submit.prevent="submitSearch">
            <div class="flex gap-2">
                <InputText
                    ref="searchInput"
                    v-model="draft"
                    type="search"
                    :maxlength="UtilGroup.Q_MAX"
                    enterkeyhint="search"
                    autocomplete="off"
                    fluid
                    class="min-w-0 flex-1"
                    :aria-label="t('group.searchLabel')"
                    :placeholder="t('group.searchPlaceholder')"
                    data-testid="group-search-input"
                />
                <Button type="submit" :label="t('common.search')" severity="secondary" class="shrink-0" data-testid="group-search-submit" />
            </div>
            <p class="mt-1 text-xs text-muted-color">{{ t('group.searchHelp') }}</p>
        </form>

        <div class="mt-3 lg:grid lg:grid-cols-[minmax(0,1fr)_18.75rem] lg:items-start lg:gap-6">
            <div class="min-w-0">
                <!-- 지금 뜨는 소모임 — 가로로 밀어 넘기는 카드 줄(scroll-snap, 자동으로 넘어가지 않는다). 검색 중에는 숨기고, 후보가 없으면(빈 응답) 자리를 통째로 생략한다.
                     카드 전체가 상세로 가는 링크이고 오른쪽 아래 「가입하기」는 모양만이다(링크 안에 단추를 넣지 않는다 — 상세에서 가입한다) -->
                <section
                    v-if="UtilGroup.showsTrending(state) && trendingStatus !== 'idle' && !(trendingStatus === 'ready' && trending.length === 0)"
                    class="mb-1"
                    :aria-labelledby="trendingTitleId"
                    data-testid="group-trending"
                >
                    <h2 :id="trendingTitleId" class="flex items-baseline gap-2 text-sm font-semibold">
                        {{ t('group.trendingTitle') }}
                        <small class="truncate text-xs font-normal text-muted-color">{{ t('group.trendingHint') }}</small>
                    </h2>

                    <div v-if="trendingStatus === 'loading'" class="-mx-4 mt-2 flex gap-3 overflow-hidden px-4 md:mx-0 md:px-0" aria-hidden="true" data-testid="group-trending-loading">
                        <div v-for="n in 3" :key="n" class="h-[8.5rem] w-[16.5rem] shrink-0 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
                    </div>

                    <div
                        v-else-if="trendingStatus === 'error'"
                        class="mt-2 flex items-center justify-between gap-3 rounded-xl border border-surface bg-surface-0 px-3 py-2.5 text-sm dark:bg-surface-900"
                        role="alert"
                        data-testid="group-trending-error"
                    >
                        <span class="min-w-0">
                            <b class="block text-sm">{{ t('group.trendingError') }}</b>
                            <span v-if="trendingErrorMessage" class="block text-xs text-muted-color">{{ trendingErrorMessage }}</span>
                        </span>
                        <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="outlined" class="shrink-0" data-testid="group-trending-retry" @click="loadTrending" />
                    </div>

                    <template v-else>
                        <ul
                            ref="trendingScroller"
                            class="-mx-4 m-0 mt-2 flex list-none snap-x snap-mandatory scroll-pl-4 gap-3 overflow-x-auto px-4 pb-1 [scrollbar-width:none] md:mx-0 md:scroll-pl-0 md:px-0"
                            data-testid="group-trending-list"
                            @scroll.passive="onTrendingScroll"
                        >
                            <li v-for="row in trending" :key="row.id" class="w-[16.5rem] shrink-0 snap-start">
                                <NuxtLink
                                    :to="UtilGroup.detailPath(row.id)"
                                    class="flex h-full flex-col rounded-xl border border-surface bg-surface-0 p-3 hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                                    data-testid="group-trending-card"
                                >
                                    <span class="flex items-center gap-2.5">
                                        <GroupInitial :name="row.name" size="lg" />
                                        <span class="min-w-0 flex-1">
                                            <span class="flex items-center gap-1.5">
                                                <b class="min-w-0 truncate text-sm" data-testid="group-trending-name">{{ row.name }}</b>
                                                <GroupVisibilityChip :visibility="row.visibility" />
                                            </span>
                                            <span class="mt-0.5 flex items-center gap-1.5 text-xs text-muted-color">
                                                <span class="shrink-0">{{ UtilGroupMessages.members(t, row) }}</span>
                                                <GroupTag v-if="row.category" tone="neutral">{{ row.category.name }}</GroupTag>
                                            </span>
                                        </span>
                                    </span>
                                    <span class="mt-2 line-clamp-2 flex-1 break-words text-xs text-surface-600 dark:text-surface-300">{{ row.summary }}</span>
                                    <span class="mt-2 flex items-center justify-between gap-2">
                                        <span v-if="row.stockNotice" class="min-w-0 truncate text-xs font-semibold text-amber-700 dark:text-amber-400">{{ t('group.stockRowNote') }}</span>
                                        <span v-else class="min-w-0 truncate text-xs text-muted-color">{{ UtilGroupMessages.badges(t, row).join(' · ') }}</span>
                                        <span
                                            class="inline-flex min-h-7 shrink-0 items-center rounded-md px-2.5 text-xs font-semibold"
                                            :class="ACTION_CLASS[UtilGroupMessages.rowAction(t, row, true).tone]"
                                            data-testid="group-trending-action"
                                            >{{ UtilGroupMessages.rowAction(t, row, true).label }}</span
                                        >
                                    </span>
                                </NuxtLink>
                            </li>
                        </ul>
                        <!-- 점 — 지금 보이는 카드를 알리고, 누르면 그 카드로 민다(마우스만 쓰는 데스크톱은 스크롤바가 없어 이 점이 길이다) -->
                        <div v-if="trending.length > 1" class="mt-1 flex justify-center" data-testid="group-trending-dots">
                            <button
                                v-for="(row, i) in trending"
                                :key="row.id"
                                type="button"
                                class="grid size-6 place-items-center rounded-full focus-visible:outline-2 focus-visible:outline-primary"
                                :aria-label="t('group.trendingGoTo', { n: i + 1 })"
                                :aria-current="i === trendingActive ? 'true' : undefined"
                                @click="trendingGoTo(i)"
                            >
                                <i class="block size-1.5 rounded-full" :class="i === trendingActive ? 'bg-primary' : 'bg-surface-300 dark:bg-surface-600'" />
                            </button>
                        </div>
                    </template>
                </section>

                <!-- 분류 칩 — 「분류」 머리글 + 가로로 미는 칩(전체 + 분류들). 받는 중에는 칩 자리에 스켈레톤, 못 받았으면 「전체」만 -->
                <div class="-mx-4 flex items-center px-4 py-2.5 md:mx-0 md:px-0" data-testid="group-category-bar">
                    <span class="shrink-0 pr-2 text-xs font-bold text-muted-color" aria-hidden="true">{{ t('group.categoryLabel') }}</span>
                    <nav ref="chipBar" :aria-label="t('group.categoryLabel')" :aria-busy="categoriesStatus === 'loading'" class="relative -mr-4 flex min-w-0 flex-1 gap-1.5 overflow-x-auto whitespace-nowrap pr-4 [scrollbar-width:none] md:mr-0 md:pr-0" data-testid="group-categories">
                        <NuxtLink :to="to({ category: '' })" :class="chipClass(!state.category)" :aria-current="!state.category ? 'true' : undefined" data-testid="group-category-all">
                            <span>{{ t('common.all') }}</span><em v-if="categoryTotal !== null" :class="countClass(!state.category)">{{ categoryTotal }}</em>
                        </NuxtLink>
                        <span v-if="categoriesStatus === 'loading'" class="flex gap-1.5" aria-hidden="true">
                            <i v-for="n in 4" :key="n" class="h-7 w-14 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                        </span>
                        <NuxtLink
                            v-for="c in categories ?? []"
                            :key="c.id"
                            :to="to({ category: c.id })"
                            :class="chipClass(state.category === c.id)"
                            :aria-current="state.category === c.id ? 'true' : undefined"
                            data-testid="group-category"
                        >
                            <span class="max-w-[10rem] truncate">{{ c.name }}</span><em :class="countClass(state.category === c.id)">{{ c.groupCount }}</em>
                        </NuxtLink>
                    </nav>
                </div>

                <!-- 정렬 — 글자만 있는 가로 줄(링크라 `?sort=` 만 바뀐다) -->
                <nav :aria-label="t('group.sortLabel')" class="flex gap-4 overflow-x-auto whitespace-nowrap border-b border-surface text-sm [scrollbar-width:none]" data-testid="group-sorts">
                    <NuxtLink
                        v-for="s in UtilGroup.SORTS"
                        :key="s"
                        :to="to({ sort: s })"
                        class="inline-flex min-h-9 shrink-0 items-center border-b-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        :class="state.sort === s ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color'"
                        :aria-current="state.sort === s ? 'true' : undefined"
                        :data-testid="`group-sort-${s}`"
                    >
                        {{ UtilGroupMessages.sortLabel(t, s) }}
                    </NuxtLink>
                </nav>

                <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

                <!-- 투자 고지 분류: 목적 안내와 투자 고지를 고정한다 -->
                <template v-if="stockFilter">
                    <GroupNotice tone="warn" :title="t('group.stockBannerTitle')" class="mt-3" data-testid="group-stock-banner">{{ t('group.stockBannerText') }}</GroupNotice>
                    <BoardInvestmentNotice class="mt-2" />
                </template>

                <!-- 검색 중: 결과 개수와 검색어 칩(누르면 검색이 지워진다) -->
                <div v-if="state.q" class="mt-3 flex flex-wrap items-center gap-2 text-sm" data-testid="group-search-summary">
                    <b v-if="listStatus === 'ready'">{{ t('group.searchResult', { count: total }) }}</b>
                    <NuxtLink
                        :to="to({ q: '' })"
                        class="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-surface bg-surface-0 px-3 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                        :aria-label="`${t('group.searchClear')}: ${state.q}`"
                        data-testid="group-search-clear"
                    >
                        <span class="max-w-[12rem] truncate">{{ state.q }}</span>
                        <i class="pi pi-times text-xs text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                </div>

                <div class="mt-2" :aria-busy="listStatus === 'loading'">
                    <div v-if="listStatus === 'loading'" class="-mx-4 md:mx-0" aria-hidden="true" data-testid="group-list-loading">
                        <div v-for="n in 7" :key="n" class="flex items-start gap-3 border-b border-surface px-4 py-3 md:px-2">
                            <div class="size-9 shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                            <div class="flex-1 pt-0.5">
                                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-1/2' : 'w-2/5'" />
                                <div class="mt-2 h-3 w-4/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                        </div>
                    </div>

                    <ErrorState v-else-if="listStatus === 'error'" :title="t('group.listError')" :description="errorMessage" retry data-testid="group-list-error" @retry="load" />

                    <ErrorState v-else-if="listStatus === 'category-invalid'" :title="t('group.categoryInvalidTitle')" :description="t('group.categoryInvalidText')" data-testid="group-category-invalid">
                        <Button as="router-link" :to="to({ category: '' })" :label="t('group.viewAll')" />
                    </ErrorState>

                    <!-- 비회원이 「내 소모임」 정렬을 열었다 -->
                    <EmptyState v-else-if="listStatus === 'login-required'" :title="t('group.loginMineTitle')" :description="t('group.loginMineText')" icon="pi pi-lock" data-testid="group-login-required">
                        <Button as="router-link" :to="to({ sort: 'popular' })" :label="t('group.viewAll')" severity="secondary" variant="outlined" />
                        <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" />
                    </EmptyState>

                    <template v-else>
                        <EmptyState v-if="items.length === 0" :title="empty.title" :description="empty.text" :icon="EMPTY_ICON[emptyKind]" data-testid="group-empty">
                            <Button v-if="emptyKind === 'mine'" as="router-link" :to="to({ category: '', sort: 'popular', q: '' })" :label="t('group.browse')" />
                            <template v-else>
                                <Button v-if="emptyKind === 'filtered'" as="router-link" :to="to({ category: '', sort: 'popular', q: '' })" :label="t('group.resetFilter')" severity="secondary" variant="outlined" data-testid="group-reset-filter" />
                                <Button type="button" :label="t('group.createTitle')" data-testid="group-empty-create" @click="onCreate" />
                            </template>
                        </EmptyState>

                        <template v-else>
                            <ul class="-mx-4 m-0 list-none p-0 md:mx-0" data-testid="group-list">
                                <GroupListRow v-for="row in items" :key="row.id" :row="row" />
                            </ul>

                            <!-- 더보기 — 쪽 번호로 이어 붙인다. 실패하면 목록은 그대로 두고 아래에 오류와 다시 시도 -->
                            <div v-if="hasNext" class="py-4 text-center">
                                <p v-if="moreError" class="text-xs text-red-600 dark:text-red-400" role="alert" data-testid="group-more-error">{{ moreError }}</p>
                                <Button
                                    type="button"
                                    :label="moreError ? t('common.retry') : t('common.more')"
                                    severity="secondary"
                                    variant="outlined"
                                    class="mt-2 min-h-11"
                                    :loading="loadingMore"
                                    data-testid="group-more"
                                    @click="loadMore"
                                />
                            </div>
                            <p v-else class="py-4 text-center text-xs text-muted-color" data-testid="group-end">{{ t('group.listEnd') }}</p>

                            <!-- 전체 목록에 투자 고지 게시판의 소모임이 섞여 있으면 투자 고지를 붙인다 -->
                            <BoardInvestmentNotice v-if="mixedStock" class="mt-2" data-testid="group-mixed-notice" />
                        </template>
                    </template>
                </div>
            </div>

            <!-- 데스크톱 오른쪽 — 내 소모임 요약(로그인했을 때만). 받지 못해도 이 카드만 오류를 말한다 -->
            <aside class="max-lg:hidden" :aria-label="t('group.myTitle')">
                <section v-if="user" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-my-summary">
                    <h2 class="text-sm font-semibold">{{ t('group.myTitle') }}</h2>
                    <div v-if="my.status.value === 'idle' || my.status.value === 'loading'" class="mt-3 space-y-2" aria-hidden="true">
                        <div v-for="n in 3" :key="n" class="h-4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-3/4' : 'w-1/2'" />
                    </div>
                    <div v-else-if="my.status.value === 'error'" class="mt-2 text-xs text-muted-color" role="alert">
                        <p>{{ t('group.myError') }}</p>
                        <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="outlined" class="mt-2" @click="my.load" />
                    </div>
                    <template v-else-if="my.data.value">
                        <p v-if="my.data.value.joined.length === 0" class="mt-2 text-xs text-muted-color">{{ t('group.myEmptyTitle') }}</p>
                        <ul v-else class="m-0 mt-1 list-none p-0">
                            <li v-for="g in my.data.value.joined.slice(0, 3)" :key="g.id">
                                <NuxtLink
                                    :to="UtilGroup.detailPath(g.id)"
                                    class="flex items-center justify-between gap-2 rounded px-1 py-1.5 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                                >
                                    <span class="min-w-0 truncate">{{ g.name }}</span>
                                    <GroupTag v-if="g.newPostCount > 0" tone="red">{{ t('group.myNewPosts', { n: g.newPostCount }) }}</GroupTag>
                                </NuxtLink>
                            </li>
                        </ul>
                        <p v-if="my.data.value.applications.length" class="mt-1 truncate text-xs text-muted-color">{{ UtilGroupMessages.pendingSummary(t, my.data.value.applications) }}</p>
                        <NuxtLink :to="UtilGroup.MY_PATH" class="mt-2 block rounded text-xs text-muted-color hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                            {{ t('group.myMoreLink') }} ›
                        </NuxtLink>
                    </template>
                </section>
            </aside>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { GroupRow } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup, { type GroupListState } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilRedirect from '~/utils/util-redirect';

const { t } = useI18n();
useSeoMeta({ title: () => t('group.title'), robots: 'noindex' });

const route = useRoute();
const api = useGroupApi();
const { user, loaded } = useAuth();
const { prompt } = useLoginPrompt();
const searchId = useId();

/* ── 분류 ── */
const { items: categories, status: categoriesStatus, loading: categoriesLoading, total: categoryTotal, reload: reloadCategories } = useGroupCategories();
void reloadCategories();

/* ── 주소(상태) ── */
const state = computed<GroupListState>(() => UtilGroup.parseQuery(route.query));
const stateKey = computed(() => UtilGroup.queryKey(state.value));

function to(patch: Partial<GroupListState>) {
    return { path: UtilGroup.LIST_PATH, query: UtilGroup.buildQuery({ ...state.value, ...patch }) };
}

function chipClass(active: boolean): string {
    return [
        'inline-flex shrink-0 items-center rounded-full border px-3 py-0.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        active
            ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-100 dark:bg-surface-100 dark:text-surface-900'
            : 'border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800',
    ].join(' ');
}

function countClass(active: boolean): string {
    return ['ml-1 text-xs font-normal not-italic tabular-nums', active ? 'text-surface-300 dark:text-surface-600' : 'text-muted-color'].join(' ');
}

// 고른 칩이 줄 밖에 있으면 보이게 가로로 민다(scrollIntoView 는 페이지까지 움직여 쓰지 않는다)
const chipBar = ref<HTMLElement | null>(null);
watch(
    () => [state.value.category, categories.value?.length ?? 0] as const,
    async () => {
        await nextTick();
        const box = chipBar.value;
        const on = box?.querySelector<HTMLElement>('[aria-current="true"]');
        if (box && on) box.scrollLeft = on.offsetLeft - (box.clientWidth - on.offsetWidth) / 2;
    },
    { immediate: true, flush: 'post' },
);

/* ── 지금 뜨는 소모임 ── */
type TrendingStatus = 'idle' | 'loading' | 'ready' | 'error';

const trendingTitleId = useId();
const trending = ref<GroupRow[]>([]);
const trendingStatus = ref<TrendingStatus>('idle');
const trendingErrorMessage = ref('');
/** 슬라이드 요청이 끝나면(성공·실패 모두) 풀리는 약속 — 목록이 이것을 기다렸다가 `exclude` 를 정한다. 던지지 않는다 */
let trendingDone: Promise<void> | null = null;
let trendingSeq = 0;
let trendingAbort: AbortController | null = null;

/** 슬라이드를 (다시) 받는다 — 앞 요청이 있으면 끊는다. 가중 무작위라 한 번 받으면 분류·정렬을 바꿔도 다시 받지 않는다 */
function loadTrending(): Promise<void> {
    const mine = ++trendingSeq;
    trendingAbort?.abort();
    const controller = new AbortController();
    trendingAbort = controller;
    trendingStatus.value = 'loading';
    trendingErrorMessage.value = '';
    const done = api
        .trending(controller.signal)
        .then((res) => {
            if (mine !== trendingSeq) return;
            trending.value = res.items;
            trendingStatus.value = 'ready';
        })
        .catch((e: unknown) => {
            if (mine !== trendingSeq || UtilGroup.isAborted(e)) return;
            trending.value = [];
            trendingErrorMessage.value = UtilBoardMessages.of(t, e);
            trendingStatus.value = 'error';
        });
    trendingDone = done;
    return done;
}

/** 카드 오른쪽 아래 표시의 모양 — 가입할 수 있다(파랑 테두리) · 정원 참·마감(꺼진 회색) · 이미 내 상태(글자만). 목록 줄(`GroupListRow`)과 같다 */
const ACTION_CLASS = {
    go: 'border border-primary text-primary',
    off: 'border border-surface bg-surface-100 text-muted-color dark:bg-surface-800',
    note: 'text-muted-color',
} as const;

/** 카드 사이 간격(Tailwind `gap-3` = 0.75rem) */
const TRENDING_GAP_PX = 12;
const trendingScroller = ref<HTMLElement | null>(null);
/** 지금 맨 앞에 보이는 카드 번호(0부터) */
const trendingActive = ref(0);

/** 카드 한 칸의 너비(카드 + 간격) — 아직 그려지기 전이면 0 */
function trendingStep(): number {
    const firstCard = trendingScroller.value?.querySelector<HTMLElement>('li');
    return firstCard ? firstCard.offsetWidth + TRENDING_GAP_PX : 0;
}

function onTrendingScroll() {
    const box = trendingScroller.value;
    if (!box) return;
    const last = trending.value.length - 1;
    // 끝까지 밀었으면 마지막 카드(마지막 카드가 줄 폭보다 좁아 앞 번호로 나오는 것을 막는다)
    if (box.scrollLeft + box.clientWidth >= box.scrollWidth - 2 && box.scrollWidth > box.clientWidth) {
        trendingActive.value = last;
        return;
    }
    const s = trendingStep();
    trendingActive.value = s > 0 ? Math.min(last, Math.max(0, Math.round(box.scrollLeft / s))) : 0;
}

function trendingGoTo(i: number) {
    const box = trendingScroller.value;
    const s = trendingStep();
    if (!box || s <= 0) return;
    // ⚠ 줄(가로)만 민다 — scrollIntoView 는 페이지의 세로 스크롤까지 움직인다
    box.scrollTo({ left: i * s, behavior: 'smooth' });
    trendingActive.value = i;
}

// 슬라이드는 목록과 따로 먼저 시작한다(목록이 겹치지 않게 그 결과를 기다린다)
if (UtilGroup.showsTrending(state.value)) void loadTrending();

// 카드가 바뀌면(다시 받기) 처음으로
watch(trending, () => {
    trendingActive.value = 0;
    if (trendingScroller.value) trendingScroller.value.scrollLeft = 0;
});

/* ── 목록 ── */
type ListStatus = 'loading' | 'ready' | 'error' | 'category-invalid' | 'login-required';

const status = ref<ListStatus>('loading');
const items = ref<GroupRow[]>([]);
const total = ref(0);
const page = ref(1);
const hasNext = ref(false);
const errorMessage = ref('');
const loadingMore = ref(false);
const moreError = ref('');
let seq = 0;
let abort: AbortController | null = null;
/** 이 목록을 받을 때 `exclude` 로 보낸 값 — 2쪽부터도 같은 값을 보낸다(서버가 뺀 집합 위에서 쪽을 나눠, 첫 쪽에만 보내면 겹치거나 건너뛴다) */
let excluded = '';

function query(s: GroupListState, pageNo: number) {
    return { category: s.category || undefined, sort: s.sort, q: s.q || undefined, page: pageNo, size: UtilGroup.PAGE_SIZE, exclude: excluded || undefined };
}

/** 처음부터 다시 받는다 — 앞 요청은 끊고, 늦게 온 옛 응답은 요청 번호로 버린다 */
async function load(): Promise<void> {
    const s = state.value;
    const mine = ++seq;
    abort?.abort();
    items.value = [];
    total.value = 0;
    page.value = 1;
    hasNext.value = false;
    errorMessage.value = '';
    moreError.value = '';
    loadingMore.value = false;
    status.value = 'loading';
    // 슬라이드가 보이는 주소인데 아직 받기 전이면(처음 들어왔거나 검색을 막 끝냈다) 함께 시작한다
    if (UtilGroup.showsTrending(s) && trendingStatus.value === 'idle') void loadTrending();
    // 비회원은 「내 소모임」을 부르지 않는다(서버도 401)
    if (s.sort === 'mine' && !user.value) {
        status.value = 'login-required';
        return;
    }
    // 슬라이드를 먼저 받아 그 소모임들을 목록에서 뺀다(겹치지 않게). 슬라이드가 실패하면 빼지 않고 받는다
    excluded = '';
    if (UtilGroup.excludesTrending(s) && trendingDone) {
        await trendingDone;
        if (mine !== seq) return;
        if (trendingStatus.value === 'ready') excluded = UtilGroup.excludeOf(trending.value);
    }
    const controller = new AbortController();
    abort = controller;
    try {
        const res = await api.list(query(s, 1), controller.signal);
        if (mine !== seq) return;
        items.value = res.items;
        total.value = res.total;
        page.value = res.page;
        hasNext.value = res.hasNext;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq || UtilGroup.isAborted(e)) return;
        if (e instanceof ApiError && e.status === 400 && e.field('category')) {
            status.value = 'category-invalid';
            return;
        }
        if (e instanceof ApiError && e.status === 401 && s.sort === 'mine') {
            status.value = 'login-required';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

/** 더보기 — `page` 를 올려 이어 붙인다(같은 ID 는 건너뜀). 실패해도 받은 목록은 그대로 */
async function loadMore(): Promise<void> {
    if (status.value !== 'ready' || !hasNext.value || loadingMore.value) return;
    loadingMore.value = true;
    moreError.value = '';
    const mine = seq;
    try {
        const res = await api.list(query(state.value, page.value + 1), abort?.signal);
        if (mine !== seq) return;
        const have = new Set(items.value.map((row) => row.id));
        items.value = [...items.value, ...res.items.filter((row) => !have.has(row.id))];
        total.value = res.total;
        page.value = res.page;
        hasNext.value = res.hasNext;
    } catch (e) {
        if (mine !== seq || UtilGroup.isAborted(e)) return;
        moreError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mine === seq) loadingMore.value = false;
    }
}

/** 주소에 분류가 있는데 분류 목록을 받는 중이면 기다린다(낡은 목록으로 「없는 분류」를 잘못 띄우지 않게. 못 받으면 서버가 가린다) */
const waitingCategories = computed(() => state.value.category !== '' && categoriesLoading.value);
const unknownCategory = computed(() => categories.value !== null && !waitingCategories.value && !UtilGroup.isKnownCategory(categories.value, state.value.category));
const listStatus = computed<ListStatus>(() => (unknownCategory.value ? 'category-invalid' : status.value));
/** 「내 소모임」 정렬은 로그인 상태를 알아야 한다 */
const waitingAuth = computed(() => state.value.sort === 'mine' && !loaded.value);

watch(
    [stateKey, waitingCategories, unknownCategory, waitingAuth, () => user.value?.id ?? ''],
    () => {
        if (!waitingCategories.value && !waitingAuth.value && !unknownCategory.value) void load();
    },
    { immediate: true },
);

onBeforeUnmount(() => {
    seq++;
    trendingSeq++;
    abort?.abort();
    trendingAbort?.abort();
});

const emptyKind = computed(() => UtilGroup.emptyKind(state.value));
/** 열린 소모임이 모두 위 슬라이드에 나와 아래 목록이 빈 경우(서비스 초기) — 비어 있다고 말하지 않고 위를 가리킨다 */
const empty = computed(() =>
    emptyKind.value === 'none' && UtilGroup.showsTrending(state.value) && trending.value.length > 0
        ? { title: t('group.onlyTrendingTitle'), text: t('group.onlyTrendingText') }
        : UtilGroupMessages.emptyCopy(t, state.value),
);
const EMPTY_ICON = { search: 'pi pi-search', mine: 'pi pi-users', filtered: 'pi pi-filter', none: 'pi pi-users' } as const;
const summary = computed(() => UtilGroupMessages.summary(t, listStatus.value, items.value.length, hasNext.value));

/** 고른 분류에 투자 고지 게시판이 있는가 — 분류 응답의 `investmentNotice`(이름으로 판정하지 않는다) */
const stockFilter = computed(() => Boolean(UtilGroup.categoryById(categories.value, state.value.category)?.investmentNotice));
/** 분류를 안 골랐는데 투자 고지 게시판의 소모임이 목록·슬라이드에 섞여 있다 */
const mixedStock = computed(() => !stockFilter.value && (items.value.some((r) => r.stockNotice) || (UtilGroup.showsTrending(state.value) && trending.value.some((r) => r.stockNotice))));

/* ── 검색 ── */
const draft = ref(state.value.q);
const searchOpen = ref(state.value.q !== '');
const searchInput = ref<{ $el?: HTMLInputElement } | null>(null);
const searchVisible = computed(() => searchOpen.value || state.value.q !== '');

watch(
    () => state.value.q,
    (q) => {
        draft.value = q;
    },
);

async function toggleSearch() {
    if (searchVisible.value) {
        searchOpen.value = false;
        if (state.value.q) await navigateTo(to({ q: '' }));
        return;
    }
    searchOpen.value = true;
    await nextTick();
    searchInput.value?.$el?.focus?.();
}

async function submitSearch() {
    const q = UtilGroup.fromInput(draft.value);
    draft.value = q;
    if (q === state.value.q) return;
    await navigateTo(to({ q }));
}

/* ── 만들기 — 비회원은 로그인 안내 ── */
async function onCreate() {
    if (user.value) {
        await navigateTo(UtilGroup.NEW_PATH);
        return;
    }
    prompt('groupCreate', UtilGroup.NEW_PATH);
}

/* ── 데스크톱 내 소모임 요약 ── */
const my = useMyGroups();
const wide = useMediaQuery('(min-width: 1024px)');
watch(
    [() => user.value?.id, wide],
    ([id, isWide]) => {
        if (id && isWide && my.status.value === 'idle') void my.load();
    },
    { immediate: true },
);
onBeforeUnmount(my.stop);
</script>
