<template>
    <!--
        게시판 목록 `/boards/{slug}?prefix=&sort=&q=&page=` — 주소의 쿼리가 화면 상태다.
        머리(이름·설명·안내문·투자 고지·쓰기 잠금·19금 안내·글쓰기) → 말머리 탭(전체 + 사용 중 말머리) → 정렬·보기 전환·게시판 안 검색 → 목록(LIST·CARD·GALLERY) → 쪽 번호.
        community 대비: 핫딜·직접홍보·지킴이·한 줄 광고·커서 이어 보기·목록 캐시를 뺐다. 검색을 별도 화면 대신 게시판 안 검색칸으로 했다(`q`).
          비회원 글쓰기(`writeMin = GUEST`)가 있어 비회원도 글쓰기 단추가 켜질 수 있다. 날짜는 관리자 기본 설정의 날짜 표시 방식(표준형·유동형)을 따른다.
        가정: 게시판 정보·판정(canWrite·reason)·유효 정책은 `GET /api/boards/{slug}` 한 번에 온다. 고정 공지는 첫 쪽·말머리/검색 없음일 때만 위에(최대 pinnedMax).
          19금 글은 목록에 「19」 표시와 함께 보이고 글 보기에서 성인 확인으로 가린다(community 는 서버가 목록에서 뺀다 — 성인 인증이 아직 없다).
          꺼진 게시판은 `403 BOARD_INACTIVE` 로 온다고 가정한다.
    -->
    <div>
        <ErrorState v-if="boardState === 'notfound'" code="404" :title="t('board.notFoundTitle')" :description="t('board.notFoundText')">
            <Button as="router-link" to="/" :label="t('board.goHome')" />
        </ErrorState>
        <ErrorState v-else-if="boardState === 'inactive'" :title="t('board.inactiveTitle')" :description="boardError">
            <Button as="router-link" to="/" :label="t('board.goHome')" />
        </ErrorState>
        <ErrorState v-else-if="boardState === 'error' && !view" :title="t('board.loadFailed')" :description="boardError" retry @retry="retryAll" />

        <!-- 게시판 머리·목록·쪽 번호를 한 카드에 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 다음식·네이버식은 흰 카드다(`main.css` `content-card`) -->
        <div v-else class="content-card" data-testid="board-card">
            <!-- 머리 -->
            <header class="flex items-start gap-3">
                <div class="min-w-0 flex-1">
                    <h1 class="break-words text-lg font-bold leading-tight" :style="UtilBoard.textStyle(view?.color)" data-testid="board-title">
                        <template v-if="view">{{ localizedName(view.name, view.nameEn) }}</template>
                        <span v-else class="inline-block h-5 w-28 animate-pulse rounded bg-surface-200 align-middle dark:bg-surface-800" :aria-label="t('board.loadingName')" />
                    </h1>
                    <p v-if="view?.description" class="mt-0.5 text-xs text-muted-color">{{ view.description }}</p>
                </div>
                <Button
                    v-if="writeButton !== 'hidden'"
                    type="button"
                    :label="t('shell.write')"
                    icon="pi pi-pencil"
                    size="small"
                    :severity="writeButton === 'disabled' ? 'secondary' : undefined"
                    :aria-disabled="writeButton === 'disabled' ? 'true' : undefined"
                    :class="writeButton === 'disabled' ? 'opacity-60' : ''"
                    :disabled="writeButton === 'loading'"
                    data-testid="board-write"
                    @click="onWrite"
                />
            </header>

            <!-- 쓰기 잠금 · 글쓰기가 막힌 까닭(등급 미달 등) — 판정은 서버, 이 안내는 편의 -->
            <Message v-if="lockNotice" severity="warn" :closable="false" size="small" class="mt-3" data-testid="board-lock">{{ lockNotice }}</Message>
            <Message v-else-if="blockedNotice" severity="info" :closable="false" size="small" class="mt-3" data-testid="board-write-blocked">{{ blockedNotice }}</Message>

            <!-- 게시판 안내문(운영자가 적은 평문 — 줄바꿈 유지, HTML 로 해석하지 않는다) -->
            <p v-if="detail?.introText" class="mt-3 whitespace-pre-line rounded-lg bg-surface-100 px-3 py-2 text-sm leading-relaxed dark:bg-surface-800/60" data-testid="board-intro">{{ detail.introText }}</p>

            <BoardInvestmentNotice v-if="view?.investmentNotice" class="mt-3" />

            <!-- 19금 글을 허용하는 게시판 — 가정: 성인 인증이 아직 없어 안내만 하고, 글 보기에서 확인 단추로 가린다 -->
            <p v-if="view?.allowAdult" class="mt-3 flex items-center gap-1.5 text-xs text-muted-color" data-testid="board-adult">
                <i class="pi pi-info-circle" aria-hidden="true" />{{ t('board.adultNotice') }}
            </p>

            <!-- 말머리 탭 — 전체 + 사용 중 말머리(공통 말머리 포함), 서버가 준 차례 -->
            <nav v-if="view && view.prefixes.length" class="-mx-4 mt-3 overflow-x-auto px-4" :aria-label="t('board.prefixNav')">
                <ul class="m-0 flex list-none gap-1.5 p-0 py-1">
                    <li v-for="chip in prefixChips" :key="chip.id">
                        <NuxtLink
                            :to="listLink({ prefix: chip.id, page: 1 })"
                            class="inline-flex h-8 items-center whitespace-nowrap rounded-full border px-3 text-sm focus-visible:outline-2 focus-visible:outline-primary"
                            :class="query.prefix === chip.id ? 'border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-100 dark:bg-surface-100 dark:text-surface-900' : 'border-surface text-muted-color hover:text-color'"
                            :aria-current="query.prefix === chip.id ? 'page' : undefined"
                        >
                            {{ chip.name }}
                        </NuxtLink>
                    </li>
                </ul>
            </nav>

            <!-- 정렬 · 보기 전환 · 게시판 안 검색 -->
            <div class="mt-2 flex flex-wrap items-center gap-2 border-b border-surface pb-2">
                <div class="flex gap-1 text-xs" role="group" :aria-label="t('board.sortLabel')">
                    <NuxtLink
                        v-for="s in SORTS"
                        :key="s"
                        :to="listLink({ sort: s, page: 1 })"
                        :class="pillClass(query.sort === s)"
                        :aria-current="query.sort === s ? 'true' : undefined"
                    >
                        {{ t(s === 'LATEST' ? 'board.sortLatest' : 'board.sortOldest') }}
                    </NuxtLink>
                </div>
                <div v-if="canToggleView" class="flex gap-1 text-xs" role="group" :aria-label="t('board.viewLabel')">
                    <button v-for="m in VIEW_MODES" :key="m" type="button" :class="pillClass(activeView === m)" :aria-pressed="activeView === m" @click="onView(m)">
                        <i :class="m === 'LIST' ? 'pi pi-list' : 'pi pi-th-large'" class="mr-1 text-[0.625rem]" aria-hidden="true" />{{ t(m === 'LIST' ? 'board.viewList' : 'board.viewGallery') }}
                    </button>
                </div>
                <form class="ml-auto flex w-full items-center gap-1 sm:w-auto" role="search" @submit.prevent="onSearch">
                    <IconField class="min-w-0 flex-1 sm:w-56">
                        <InputIcon class="pi pi-search" />
                        <InputText v-model="searchText" type="search" size="small" :placeholder="t('board.searchPlaceholder')" :aria-label="t('board.searchLabel')" fluid />
                    </IconField>
                    <Button type="submit" :label="t('common.search')" size="small" severity="secondary" variant="outlined" />
                </form>
            </div>
            <p v-if="query.q" class="mt-2 flex items-center gap-2 text-xs text-muted-color" data-testid="board-search-summary">
                {{ t('board.searchSummary', { q: query.q, n: list?.totalCount ?? 0 }) }}
                <NuxtLink :to="listLink({ q: '', page: 1 })" class="font-semibold text-primary underline-offset-2 hover:underline">{{ t('board.searchClear') }}</NuxtLink>
            </p>

            <!-- 목록 -->
            <div :aria-busy="listState === 'loading'">
                <div v-if="listState === 'loading'" class="-mx-4 md:mx-0" role="status" :aria-label="t('board.loadingList')">
                    <div v-for="n in 7" :key="n" class="border-b border-surface px-4 py-3.5 md:px-0">
                        <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
                        <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    </div>
                </div>

                <ErrorState v-else-if="listState === 'error'" :title="t('board.listFailed')" :description="listError" retry @retry="loadList" />

                <template v-else-if="isEmpty">
                    <EmptyState v-if="query.q" :title="t('board.searchEmptyTitle')" :description="t('board.searchEmptyText')" icon="pi pi-search">
                        <Button as="router-link" :to="listLink({ q: '', page: 1 })" :label="t('board.searchClear')" severity="secondary" variant="outlined" />
                    </EmptyState>
                    <EmptyState v-else-if="query.page > 1" :title="t('board.pageEmptyTitle')" icon="pi pi-file">
                        <Button as="router-link" :to="listLink({ page: 1 })" :label="t('board.firstPage')" />
                    </EmptyState>
                    <EmptyState v-else-if="query.prefix" :title="t('board.prefixEmptyTitle', { name: activePrefixName })" :description="t('board.prefixEmptyText')" icon="pi pi-filter">
                        <Button as="router-link" :to="listLink({ prefix: '', page: 1 })" :label="t('board.allPosts')" severity="secondary" variant="outlined" />
                    </EmptyState>
                    <EmptyState v-else :title="t('board.emptyTitle')" :description="writeButton === 'ready' ? t('board.emptyText') : t('board.emptyReadOnly')" icon="pi pi-file">
                        <Button v-if="writeButton === 'ready'" type="button" :label="t('shell.write')" @click="onWrite" />
                    </EmptyState>
                </template>

                <template v-else-if="list">
                    <component :is="listComponent" :rows="list.items" :pinned="list.pinned" :date-display="display.dateDisplay" :new-post-hours="display.newPostHours" />
                    <BoardPager :page="list.page" :total-pages="list.totalPages" :to="pageLink" />
                </template>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { PostListQuery, PostListResponse, PostSort } from '~/composables/board/api/usePostApi.types';
import type { BoardViewMode } from '~/composables/board/useBoardViewMode';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardQuery from '~/utils/util-board-query';
import UtilTheme from '~/utils/util-theme';

const SORTS: readonly PostSort[] = ['LATEST', 'OLDEST'];
const VIEW_MODES: readonly BoardViewMode[] = ['LIST', 'GALLERY'];

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const boardApi = useBoardApi();
const toast = useAppToast();
const { user } = useAuth();
const { bySlug } = useBoards();
const current = useCurrentBoard();
const { prompt } = useLoginPrompt();
const { localizedName } = useLocalizedName();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
const viewMode = useBoardViewMode();
const recentBoards = useRecentBoards();
void ensureDisplay();

const slug = computed(() => String(route.params.slug ?? ''));
const query = computed<PostListQuery>(() => UtilBoardQuery.parse(route.query));

/* ── 게시판(머리) ── */
type BoardState = 'loading' | 'ready' | 'notfound' | 'inactive' | 'error';
const detail = ref<BoardDetail | null>(null);
const boardState = ref<BoardState>('loading');
const boardError = ref('');

/** 메뉴가 받아 둔 게시판 요약으로 먼저 그리고, 자세한 정보가 오면 그것으로 바꾼다 */
const view = computed<BoardView | BoardDetail | null>(() => (detail.value?.slug === slug.value ? detail.value : (bySlug(slug.value) ?? null)));

let boardRequest = 0;
async function loadBoard(): Promise<void> {
    const mine = ++boardRequest;
    const wanted = slug.value;
    if (detail.value?.slug !== wanted) detail.value = null;
    boardState.value = 'loading';
    try {
        const result = await boardApi.board(wanted);
        if (mine !== boardRequest) return;
        detail.value = result;
        boardState.value = 'ready';
        current.setWrite({ slug: wanted, canWrite: result.viewer.canWrite, reason: result.viewer.writeBlockedReason });
        // 게시판 모음(`/boards`)의 「최근 방문」 — 열린 게시판만 이 기기에 남긴다
        recentBoards.record(wanted);
    } catch (e) {
        if (mine !== boardRequest) return;
        boardError.value = UtilBoardMessages.of(t, e);
        const code = e instanceof ApiError ? e.code : '';
        boardState.value = code === 'BOARD_NOT_FOUND' ? 'notfound' : code === 'BOARD_INACTIVE' ? 'inactive' : 'error';
    }
}

const lockNotice = computed(() => UtilBoardMessages.lockNotice(t, detail.value?.writeLock));
/** 단추가 꺼진 까닭 중 머리에 미리 알릴 것 — 등급 미달·정지·이메일 미인증(로그인 필요·운영진 전용은 단추가 말한다) */
const blockedNotice = computed(() => {
    const reason = detail.value?.viewer.writeBlockedReason ?? null;
    return reason === 'GRADE_TOO_LOW' || reason === 'ACCOUNT_RESTRICTED' || reason === 'EMAIL_NOT_VERIFIED' ? UtilBoardMessages.writeBlocked(t, reason, detail.value?.writeMinGradeLabel ?? null) : '';
});

/* ── 말머리·보기 ── */
const prefixChips = computed(() => [{ id: '', name: t('common.all') }, ...(view.value?.prefixes ?? []).map((p) => ({ id: p.id, name: p.name }))]);
const activePrefixName = computed(() => view.value?.prefixes.find((p) => p.id === query.value.prefix)?.name ?? '');

/** 보기 전환 단추 — 게시판 설정 `viewToggle` 이 켜져 있고 기본 모양이 목록형·갤러리형일 때만 */
const canToggleView = computed(() => Boolean(view.value?.viewToggle) && view.value?.layout !== 'CARD');
const activeView = computed<BoardViewMode>(() => {
    const chosen = canToggleView.value ? viewMode.chosen(slug.value) : null;
    return chosen ?? (view.value?.layout === 'GALLERY' ? 'GALLERY' : 'LIST');
});
/** 그릴 목록 유형 — 카드형은 전환 없이 늘 카드, 모르는 값은 목록형. 유형은 게시판이 정하고 테마는 그 유형의 생김새만 바꾼다 */
const listKind = computed<'LIST' | 'CARD' | 'GALLERY'>(() => {
    if (view.value?.layout === 'CARD') return 'CARD';
    return activeView.value === 'GALLERY' ? 'GALLERY' : 'LIST';
});
/** 그릴 목록 컴포넌트 — 현재 테마의 `postList:<유형>`, 없으면 기본 테마 것(`UtilTheme.resolve`, 계약 `BoardPostListProps`) */
const { theme } = useTheme();
const listComponent = computed(() => UtilTheme.resolve(theme.value, 'postList', listKind.value));

function onView(mode: BoardViewMode): void {
    viewMode.choose(slug.value, mode);
}

function pillClass(on: boolean): string {
    return [
        'inline-flex items-center rounded-full px-2.5 py-1 focus-visible:outline-2 focus-visible:outline-primary',
        on ? 'bg-surface-900 font-semibold text-surface-0 dark:bg-surface-100 dark:text-surface-900' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
    ].join(' ');
}

function listLink(change: Partial<PostListQuery>) {
    return { path: `/boards/${slug.value}`, query: UtilBoardQuery.build({ ...query.value, ...change }) };
}

function pageLink(page: number) {
    return listLink({ page });
}

/* ── 검색 ── */
const searchText = ref('');
watch(
    () => query.value.q,
    (q) => {
        searchText.value = q;
    },
    { immediate: true },
);
function onSearch(): void {
    void router.push(listLink({ q: searchText.value.trim(), page: 1 }));
}

/* ── 글쓰기 단추 ── */
type WriteButton = 'ready' | 'loading' | 'disabled' | 'hidden';
const writeButton = computed<WriteButton>(() => {
    const d = detail.value;
    if (!d) return boardState.value === 'error' ? 'ready' : 'loading';
    if (d.viewer.canWrite) return 'ready';
    const reason = d.viewer.writeBlockedReason;
    if (reason === 'STAFF_ONLY') return 'hidden';
    // 로그인이 필요하면 단추는 켜 두고 누르면 로그인 안내
    if (reason === 'LOGIN_REQUIRED') return 'ready';
    return 'disabled';
});

function onWrite(): void {
    const target = { path: '/write', query: { board: slug.value } };
    const d = detail.value;
    const reason = d ? d.viewer.writeBlockedReason : user.value ? null : 'LOGIN_REQUIRED';
    if (!reason) return void navigateTo(target);
    if (reason === 'LOGIN_REQUIRED') return prompt('write', router.resolve(target).fullPath);
    toast.show(UtilBoardMessages.writeBlocked(t, reason, d?.writeMinGradeLabel ?? null));
}

/* ── 글 목록 ── */
type ListState = 'loading' | 'ready' | 'error';
const list = ref<PostListResponse | null>(null);
const listState = ref<ListState>('loading');
const listError = ref('');
const isEmpty = computed(() => !list.value || (list.value.items.length === 0 && list.value.pinned.length === 0));

let listRequest = 0;
let listAbort: AbortController | null = null;
async function loadList(): Promise<void> {
    const mine = ++listRequest;
    listAbort?.abort();
    listAbort = new AbortController();
    listState.value = 'loading';
    try {
        const result = await boardApi.posts(slug.value, query.value, listAbort.signal);
        if (mine !== listRequest) return;
        list.value = result;
        listState.value = 'ready';
    } catch (e) {
        if (mine !== listRequest) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        // 게시판 자체가 없거나 꺼졌으면 머리 쪽 판정(loadBoard)이 화면을 바꾼다
        listError.value = UtilBoardMessages.of(t, e);
        listState.value = 'error';
    }
}

function retryAll(): void {
    void loadBoard();
    void loadList();
}

/* ── 주소가 바뀔 때 ── */
watch(
    slug,
    (value) => {
        current.set(value);
        void loadBoard();
    },
    { immediate: true },
);
watch(
    () => [slug.value, query.value.prefix, query.value.sort, query.value.q, query.value.page] as const,
    (now, before) => {
        void loadList();
        if (before && now[0] === before[0]) window.scrollTo({ top: 0 });
    },
    { immediate: true },
);
// 로그인·로그아웃하면 글쓰기 판정이 달라진다
watch(
    () => user.value?.id,
    () => void loadBoard(),
);

onBeforeUnmount(() => {
    listAbort?.abort();
    current.clear(slug.value);
});

useSeoMeta({
    title: () => (view.value ? `${localizedName(view.value.name, view.value.nameEn)}${query.value.page > 1 ? ` (${t('board.pageN', { n: query.value.page })})` : ''}` : t('board.bundleLabel')),
    robots: () => (boardState.value === 'notfound' || boardState.value === 'inactive' ? 'noindex' : undefined),
});
</script>
