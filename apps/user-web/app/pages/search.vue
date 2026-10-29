<template>
    <!-- 검색 막대·필터·결과를 한 카드에 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 카드 테마는 흰 카드다(`main.css` `content-card`) -->
    <div class="content-card" data-testid="search-card">
        <!-- 검색 막대 — 모바일은 ‹ · 입력 · 검색 단추가 위에 붙어 따라온다, 데스크톱은 본문 맨 위 -->
        <form
            role="search"
            :aria-label="t('search.formLabel')"
            novalidate
            class="sticky top-12 z-20 -mx-4 -mt-4 border-b border-surface bg-surface-0 px-2 py-1.5 md:static md:mx-0 md:mt-0 md:max-w-2xl md:border-0 md:bg-transparent md:p-0 dark:bg-surface-900 dark:md:bg-transparent"
            @submit.prevent="submit"
        >
            <div class="flex items-center gap-2">
                <button
                    type="button"
                    class="flex size-10 shrink-0 items-center justify-center rounded-full text-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary md:hidden dark:hover:bg-surface-800"
                    :aria-label="t('search.back')"
                    @click="goBack"
                >
                    <i class="pi pi-chevron-left" aria-hidden="true" />
                </button>

                <div class="relative min-w-0 flex-1">
                    <label :for="inputId" class="sr-only">{{ t('search.inputLabel') }}</label>
                    <InputText
                        :id="inputId"
                        ref="inputEl"
                        v-model="input"
                        type="text"
                        name="q"
                        inputmode="search"
                        enterkeyhint="search"
                        autocomplete="off"
                        autocapitalize="off"
                        spellcheck="false"
                        :placeholder="placeholder"
                        fluid
                        class="pr-9!"
                        :invalid="Boolean(inputError)"
                        :aria-invalid="inputError ? 'true' : undefined"
                        :aria-describedby="inputError ? errorId : undefined"
                        data-testid="search-input"
                        @input="inputError = ''"
                    />
                    <button
                        v-if="input"
                        type="button"
                        class="absolute right-1 top-1/2 grid size-8 -translate-y-1/2 place-items-center rounded-full text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="t('search.clear')"
                        @click="clearInput"
                    >
                        <i class="pi pi-times-circle" aria-hidden="true" />
                    </button>
                </div>

                <Button type="submit" :label="t('common.search')" size="small" class="shrink-0" data-testid="search-submit" />
            </div>
            <!-- 입력 검사(2글자 미만·50자 초과)와 서버 400 `fields.q` 가 같은 자리에 보인다 -->
            <p v-if="inputError" :id="errorId" role="alert" class="px-3 pb-0.5 pt-1 text-xs text-red-600 md:px-0 dark:text-red-400" data-testid="search-input-error">{{ inputError }}</p>
        </form>

        <!-- 범위 · 게시판 · 정렬 -->
        <template v-if="showFilters">
            <nav :aria-label="t('search.scopeLabel')" class="no-scrollbar -mx-4 flex gap-1.5 overflow-x-auto whitespace-nowrap px-4 py-2.5 md:mx-0 md:px-0">
                <NuxtLink v-for="s in scopes" :key="s.value" :to="to({ scope: s.value })" :class="chipClass(state.scope === s.value)" :aria-current="state.scope === s.value ? 'true' : undefined">
                    {{ s.label }}
                </NuxtLink>
            </nav>
            <div class="flex items-center justify-between gap-3 border-b border-surface pb-1.5">
                <div class="min-w-0">
                    <label :for="boardSelectId" class="sr-only">{{ t('search.boardLabel') }}</label>
                    <select
                        :id="boardSelectId"
                        :value="state.board"
                        class="max-w-full truncate rounded-md border border-surface bg-surface-0 px-2 py-1 text-sm text-color focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-900"
                        data-testid="search-board"
                        @change="onBoardChange"
                    >
                        <option v-for="o in boardOptions" :key="o.value" :value="o.value" :selected="o.value === state.board">{{ o.label }}</option>
                    </select>
                </div>
                <nav :aria-label="t('search.orderLabel')" class="flex gap-4 text-sm">
                    <NuxtLink
                        v-for="o in orders"
                        :key="o.value"
                        :to="to({ order: o.value })"
                        :class="[
                            'rounded py-0.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary max-md:-my-1 max-md:inline-flex max-md:min-h-8 max-md:items-center',
                            state.order === o.value ? 'font-semibold text-color' : 'text-muted-color',
                        ]"
                        :aria-current="state.order === o.value ? 'true' : undefined"
                    >
                        {{ o.label }}
                    </NuxtLink>
                </nav>
            </div>
        </template>

        <!-- 검색 진입(검색어 없음) · 입력이 검사에 걸린 주소: 안내 + 최근 검색어 -->
        <section v-if="showEntry" class="mt-3" :aria-label="t('search.recentTitle')" data-testid="search-entry">
            <p class="rounded-md bg-surface-100 px-3 py-2 text-sm text-muted-color dark:bg-surface-800">{{ t('search.entryHint') }}</p>

            <div class="mt-4 flex items-center justify-between">
                <h2 class="text-xs font-normal text-muted-color">{{ t('search.recentTitle') }}</h2>
                <button v-if="recentTerms.length" type="button" class="rounded px-1 text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="recent.clear()">
                    {{ t('search.recentClear') }}
                </button>
            </div>

            <ul v-if="recentTerms.length" class="m-0 mt-1 list-none p-0" data-testid="search-recent">
                <li v-for="term in recentTerms" :key="term" class="flex items-center border-b border-surface last:border-b-0">
                    <NuxtLink :to="to({ q: term })" class="flex min-w-0 flex-1 items-center gap-2 rounded py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                        <i class="pi pi-history text-sm text-muted-color" aria-hidden="true" />
                        <span class="truncate">{{ term }}</span>
                    </NuxtLink>
                    <button
                        type="button"
                        class="grid size-10 shrink-0 place-items-center rounded-full text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                        :aria-label="t('search.recentRemove', { q: term })"
                        @click="recent.remove(term)"
                    >
                        <i class="pi pi-times text-xs" aria-hidden="true" />
                    </button>
                </li>
            </ul>
            <EmptyState v-else :title="t('search.recentEmptyTitle')" :description="t('search.recentEmptyText')" icon="pi pi-search" />

            <p class="mt-3 text-xs text-muted-color">{{ t('search.recentNote') }}</p>
        </section>

        <!-- 결과 개수·더 있는지를 스크린리더가 읽는다(결과가 있을 때만 눈에도 보인다) -->
        <p role="status" aria-live="polite" :class="items.length ? 'mt-3 break-words text-xs text-muted-color' : 'sr-only'" data-testid="search-summary">{{ summary }}</p>

        <!-- 결과 -->
        <div v-if="!showEntry" :aria-busy="status === 'loading'">
            <div v-if="status === 'loading'"><FeedSkeleton /></div>

            <!-- 검색이 3초를 넘겨 중단(503 SEARCH_TIMEOUT) — 서버 문구 그대로 + 더 구체적으로 -->
            <ErrorState v-else-if="status === 'timeout'" :title="errorMessage" :description="t('search.timeoutHint')" retry @retry="load" />

            <!-- 게시판 주소가 잘못됐다(400 fields.board — 없는·꺼진 게시판) -->
            <ErrorState v-else-if="status === 'board-invalid'" :title="t('search.boardInvalidTitle')" :description="t('search.boardInvalidText')">
                <Button as="router-link" :to="to({ board: '' })" :label="t('search.searchAllBoards')" />
            </ErrorState>

            <ErrorState v-else-if="status === 'error'" :title="t('search.errorTitle')" :description="errorMessage" retry @retry="load" />

            <template v-else-if="status === 'ready'">
                <template v-if="items.length === 0">
                    <EmptyState v-if="state.page > 1" :title="t('search.pageEmptyTitle')" :description="t('search.pageEmptyText')" icon="pi pi-file">
                        <Button as="router-link" :to="to({ page: 1 })" :label="t('feed.firstPage')" />
                    </EmptyState>
                    <EmptyState v-else :title="t('search.emptyTitle', { q: state.q })" :description="t('search.emptyText')" icon="pi pi-search" class="[overflow-wrap:anywhere]" data-testid="search-empty">
                        <template v-if="wordSuggestions.length">
                            <p class="basis-full text-xs text-muted-color">{{ t('search.suggestWords') }}</p>
                            <Button v-for="s in wordSuggestions" :key="s" as="router-link" :to="to({ q: s })" :label="s" size="small" severity="secondary" variant="outlined" />
                        </template>
                        <template v-if="relaxSuggestions.length">
                            <p class="basis-full pt-2 text-xs text-muted-color">{{ t('search.suggestRelax') }}</p>
                            <Button v-for="s in relaxSuggestions" :key="s.key" as="router-link" :to="s.to" :label="s.label" size="small" severity="secondary" variant="outlined" />
                        </template>
                    </EmptyState>
                </template>

                <template v-else>
                    <ul class="-mx-4 m-0 list-none p-0 md:mx-0" data-testid="search-results">
                        <FeedPostItem
                            v-for="row in items"
                            :key="row.id"
                            :row="row"
                            :tokens="tokens"
                            :snippet="row.snippet"
                            :date-display="display.dateDisplay"
                            :new-post-hours="display.newPostHours"
                        />
                    </ul>

                    <!-- 데스크톱: 쪽 번호(서버가 주는 totalPages 는 최대 10) -->
                    <BoardPager v-if="desktop && result && result.totalPages" :page="result.page ?? state.page" :total-pages="result.totalPages" :to="(page: number) => to({ page })" />

                    <!-- 모바일: 더보기(커서). 데스크톱은 마지막 쪽(10쪽) 뒤에도 더 있을 때만 -->
                    <div v-if="canLoadMore" class="py-4 text-center">
                        <Button type="button" :label="t('search.more')" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="search-more" @click="loadMore" />
                        <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
                    </div>

                    <BoardInvestmentNotice v-if="hasInvestmentBoard" class="mt-2" />
                </template>

                <p class="mt-4 text-center text-xs text-muted-color">{{ t('search.hiddenNote') }}</p>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { SearchOrder, SearchResponse, SearchRow, SearchScope } from '~/composables/search/api/useSearchApi.types';
import type { SearchState } from '~/utils/util-search-query';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilSearchMessages from '~/utils/util-search-messages';
import UtilSearchQuery from '~/utils/util-search-query';
import UtilSearchRules from '~/utils/util-search-rules';

/**
 * 검색 — `/search?q=&scope=&board=&order=&page=`(community `pages/search.vue`, `docs/api-board.md`「검색 (2-4)」). 헤더의 돋보기·메뉴 BUILTIN `SEARCH` 가 이곳이다.
 * 주소의 쿼리가 화면의 상태다(뒤로 가기·공유·새로고침이 같은 결과로 돌아온다). 범위·게시판·정렬·쪽은 링크라서 주소만 바뀌고,
 * 검색어는 입력칸에 쓴 것을 **Enter·검색 단추로만** 보낸다(자동 검색·자동완성 없음). 모바일은 더보기(커서), 데스크톱은 쪽 번호(최대 10쪽).
 * 앞 요청이 끝나기 전에 새 요청을 보내면 앞 요청을 끊고, 늦게 온 옛 결과는 요청 번호로 버린다. 방금 본 검색은 스켈레톤 없이 바로 보이고 뒤에서 새로 받는다.
 * API 는 `useSearchApi().search`(`GET /api/search`). 최근 검색어는 이 기기에만.
 */
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useSearchApi();
const { boards, bySlug, ensureLoaded: ensureBoards } = useBoards();
const { localizedName } = useLocalizedName();
const recent = useRecentSearches();
const cache = useSearchCache();
const desktop = useIsDesktop();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();
void ensureBoards();

const inputId = useId();
const errorId = `${inputId}-error`;
const boardSelectId = `${inputId}-board`;
const inputEl = ref<{ $el: HTMLInputElement } | null>(null);

const scopes = computed<{ value: SearchScope; label: string }[]>(() => [
    { value: 'all', label: t('search.scopeAll') },
    { value: 'title', label: t('search.scopeTitle') },
    { value: 'body', label: t('search.scopeBody') },
    { value: 'author', label: t('search.scopeAuthor') },
]);
const orders = computed<{ value: SearchOrder; label: string }[]>(() => [
    { value: 'latest', label: t('search.orderLatest') },
    { value: 'oldest', label: t('search.orderOldest') },
]);

/* ── 주소(상태)와 입력칸 ── */
const state = computed(() => UtilSearchQuery.parse(route.query));
const stateKey = computed(() => UtilSearchQuery.key(state.value));
const input = ref('');
const inputError = ref('');

/** 지금 검색으로 가는 주소 — 바꿀 값만 주면 나머지는 그대로, 쪽은 1쪽으로 돌아간다 */
function to(patch: Partial<SearchState>) {
    return { path: '/search', query: UtilSearchQuery.build({ ...state.value, page: 1, ...patch }) };
}

const boardName = computed(() => {
    const b = state.value.board ? bySlug(state.value.board) : undefined;
    return b ? localizedName(b.name, b.nameEn) : '';
});
const placeholder = computed(() => (boardName.value ? t('search.placeholderBoard', { board: boardName.value }) : t('search.placeholder')));

const boardOptions = computed(() => {
    const list = (boards.value ?? []).map((b) => ({ value: b.slug, label: localizedName(b.name, b.nameEn) }));
    const current = state.value.board;
    // 게시판 목록을 아직 못 받았거나 주소의 게시판이 목록에 없어도 지금 고른 것은 보이게 한다
    if (current && !list.some((o) => o.value === current)) list.push({ value: current, label: current });
    return [{ value: '', label: t('search.allBoards') }, ...list];
});

/** 기본값이 아닌 것이 하나라도 있으면 범위·게시판·정렬 줄을 보인다 */
const showFilters = computed(() => Boolean(state.value.q || state.value.board || state.value.scope !== 'all' || state.value.order !== 'latest'));

/* ── 결과 ── */
type Status = 'idle' | 'invalid' | 'loading' | 'ready' | 'error' | 'timeout' | 'board-invalid';
const status = ref<Status>('idle');
const result = ref<SearchResponse | null>(null);
const more = ref<SearchRow[]>([]);
const moreCursor = ref<string | null>(null);
const moreHasNext = ref(false);
const loadingMore = ref(false);
const moreError = ref('');
const errorMessage = ref('');

const items = computed<SearchRow[]>(() => (result.value ? [...result.value.items, ...more.value] : []));
const tokens = computed(() => result.value?.tokens ?? []);
const showEntry = computed(() => status.value === 'idle' || status.value === 'invalid');
const recentTerms = recent.terms;

const hasMore = computed(() => (more.value.length ? moreHasNext.value : (result.value?.hasNext ?? false)));
const canLoadMore = computed(() => {
    const r = result.value;
    if (!r || !hasMore.value) return false;
    if (!desktop.value) return true;
    return r.totalPages !== null && (r.page ?? state.value.page) >= r.totalPages;
});
const hasInvestmentBoard = computed(() => items.value.some((row) => bySlug(row.board.slug)?.investmentNotice));

const summary = computed(() =>
    UtilSearchMessages.summary(t, {
        q: state.value.q,
        status: status.value,
        count: items.value.length,
        page: desktop.value && result.value?.totalPages !== null && result.value ? (result.value.page ?? state.value.page) : null,
        hasMore: hasMore.value,
    }),
);

/** 결과가 없을 때 다른 검색어 — 단어 하나씩, 띄어쓰기를 붙인 것 */
const wordSuggestions = computed(() => {
    const words = UtilSearchRules.distinctWords(state.value.q);
    if (words.length < 2) return [];
    const joined = words.join('');
    return UtilSearchRules.check(joined) === null ? [...words, joined] : words;
});

/** 결과가 없을 때 조건을 넓히는 단추 — 해당하는 것만 */
const relaxSuggestions = computed(() => {
    const out: { key: string; label: string; to: ReturnType<typeof to> }[] = [];
    if (state.value.scope !== 'all') out.push({ key: 'scope', label: t('search.relaxScope'), to: to({ scope: 'all' }) });
    if (state.value.board) out.push({ key: 'board', label: t('search.relaxBoard'), to: to({ board: '' }) });
    return out;
});

let requestSeq = 0;
let abort: AbortController | null = null;

function apply(res: SearchResponse) {
    result.value = res;
    more.value = [];
    moreCursor.value = res.nextCursor;
    moreHasNext.value = res.hasNext;
    status.value = 'ready';
}

async function load() {
    const s = state.value;
    const mine = ++requestSeq;
    abort?.abort();
    abort = null;
    more.value = [];
    moreError.value = '';
    loadingMore.value = false;

    if (!s.q) {
        result.value = null;
        status.value = 'idle';
        return;
    }
    // 검색어 검사 — 걸리면 요청을 보내지 않고 입력 아래에 안내한다(주소로 바로 들어온 때도)
    const problem = UtilSearchRules.check(s.q);
    if (problem) {
        result.value = null;
        status.value = 'invalid';
        inputError.value = UtilSearchMessages.problem(t, problem);
        return;
    }

    const controller = new AbortController();
    abort = controller;
    // 데스크톱은 쪽 번호로, 모바일은 첫 묶음 + 더보기(커서)로 — 주소에 2쪽 이후가 있으면 모바일도 그 쪽부터
    const paged = desktop.value || s.page > 1;
    const key = `${UtilSearchQuery.key(s)}|${paged ? 'page' : 'cursor'}`;
    const cached = cache.get(key);
    if (cached) apply(cached);
    else {
        result.value = null;
        status.value = 'loading';
    }

    try {
        const res = await api.search({ q: s.q, scope: s.scope, board: s.board || undefined, order: s.order, page: paged ? s.page : undefined }, controller.signal);
        if (mine !== requestSeq) return;
        cache.set(key, res);
        apply(res);
        // 검색이 실제로 실행돼 결과가 왔을 때만 최근 검색어에 남긴다
        recent.record(s.q);
    } catch (e) {
        if (mine !== requestSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        if (cached) return; // 새로 받지 못해도 보고 있던 결과는 그대로 둔다
        result.value = null;
        if (e instanceof ApiError && e.status === 400 && e.field('q')) {
            status.value = 'invalid';
            inputError.value = e.field('q') ?? '';
            return;
        }
        if (e instanceof ApiError && e.status === 400 && e.field('board')) {
            status.value = 'board-invalid';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = e instanceof ApiError && e.code === 'SEARCH_TIMEOUT' ? 'timeout' : 'error';
    }
}

async function loadMore() {
    const cursor = moreCursor.value;
    if (!result.value || !cursor || loadingMore.value) return;
    loadingMore.value = true;
    moreError.value = '';
    const mine = requestSeq;
    const s = state.value;
    try {
        const res = await api.search({ q: s.q, scope: s.scope, board: s.board || undefined, order: s.order, cursor }, abort?.signal);
        if (mine !== requestSeq) return;
        const seen = new Set(items.value.map((row) => row.id));
        more.value = [...more.value, ...res.items.filter((row) => !seen.has(row.id))];
        moreCursor.value = res.nextCursor;
        moreHasNext.value = res.hasNext;
    } catch (e) {
        if (mine !== requestSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        moreError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mine === requestSeq) loadingMore.value = false;
    }
}

/* ── 입력 ── */
function focusInput() {
    inputEl.value?.$el.focus();
}

function clearInput() {
    input.value = '';
    inputError.value = '';
    focusInput();
}

/** Enter·검색 단추 — 검사에 걸리면 요청을 보내지 않고 입력 아래에 안내한다 */
function submit() {
    const q = UtilSearchRules.normalize(input.value);
    const problem = UtilSearchRules.check(q);
    if (problem) {
        inputError.value = UtilSearchMessages.problem(t, problem);
        focusInput();
        return;
    }
    input.value = q;
    inputError.value = '';
    const target = to({ q });
    // 같은 검색을 다시 눌렀다 — 주소가 안 바뀌니 직접 새로 받는다
    if (router.resolve(target).fullPath === route.fullPath) void load();
    else void router.push(target);
    // 모바일은 키보드를 내려 결과가 보이게 한다
    if (!desktop.value) inputEl.value?.$el.blur();
}

function onBoardChange(event: Event) {
    void router.push(to({ board: (event.target as HTMLSelectElement).value }));
}

function goBack() {
    if (typeof window !== 'undefined' && window.history.state?.back) router.back();
    else void navigateTo('/');
}

function chipClass(active: boolean): string {
    return [
        'shrink-0 rounded-full border px-3 py-0.5 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        active
            ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-100 dark:bg-surface-100 dark:text-surface-900'
            : 'border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800',
    ].join(' ');
}

/* ── 주소가 바뀔 때 ── */
/** 입력칸이 마지막으로 맞춘 검색어 — 범위·정렬만 바뀐 때는 쓰던 입력을 덮어쓰지 않는다 */
let syncedQ: string | null = null;
watch(
    stateKey,
    (_, before) => {
        const s = state.value;
        if (s.q !== syncedQ) {
            input.value = s.q;
            syncedQ = s.q;
        }
        inputError.value = '';
        void load();
        // 쪽·범위·정렬·검색어를 바꾸면 맨 위로(첫 진입은 그대로)
        if (before !== undefined && typeof window !== 'undefined') window.scrollTo({ top: 0 });
    },
    { immediate: true },
);

// 모바일에서 첫 묶음만 받았는데 데스크톱 폭이 되면 쪽 번호를 위해 다시 받는다
watch(desktop, (isDesktop) => {
    if (isDesktop && status.value === 'ready' && result.value && result.value.totalPages === null) void load();
});

onMounted(() => {
    recent.load();
    if (!state.value.q) focusInput();
});
onBeforeUnmount(() => abort?.abort());

/* ── 제목·설명 — 검색 결과 주소는 검색엔진에 노출하지 않는다 ── */
const { siteLabel } = useSiteName();
useSeoMeta({
    title: () => (state.value.q ? t('search.resultTitle', { q: state.value.q }) : t('search.title')),
    description: () => t('search.seoDescription', { site: siteLabel.value }),
    robots: 'noindex',
});
</script>
