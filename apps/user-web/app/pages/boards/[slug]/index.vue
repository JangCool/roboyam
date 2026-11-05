<template>
    <!--
        게시판 목록 `/boards/{slug}?prefix=&sort=&q=&page=` — 주소의 쿼리가 화면 상태다.
        머리(이름·설명·안내문·투자 고지·쓰기 잠금·19금 안내·글쓰기) → 말머리 탭(전체 + 사용 중 말머리) → 정렬·보기 전환·게시판 안 검색 → 목록(LIST·CARD·GALLERY) → 쪽 번호.
        핫딜 게시판(`kind DEAL` — `hotdeal-contract.md`): 정렬이 최신·인기·마감 임박 `SelectButton` + 「종료 숨기기」 `ToggleSwitch`(기본 켬, 주소 `?sort=popular|ending&hideEnded=false`),
          말머리 아래 쇼핑몰 칩(전체 + 서버 `stores` 상위 8몰, 주소 `?store=`). 목록은 가격 줄 목록형(`HotdealPostList`)·카드/갤러리형(`HotdealPostGrid` — 게시판 모양·보기 전환은 그대로), 의심 글은 서버가 아래로 보낸다.
          비면 「진행 중인 핫딜이 없어요 · 종료 글 보기」(쇼핑몰을 골라 비었으면 「전체 쇼핑몰 보기」). 글쓰기가 막혔으면(새싹·비로그인·이메일 미인증) 단추를 흐리게 + 까닭 툴팁(누르면 안내·로그인).
        community 대비: 직접홍보·지킴이·한 줄 광고·커서 이어 보기·목록 캐시를 뺐다. 검색을 별도 화면 대신 게시판 안 검색칸으로 했다(`q`).
          비회원 글쓰기(`writeMin = GUEST`)가 있어 비회원도 글쓰기 단추가 켜질 수 있다. 날짜는 관리자 기본 설정의 날짜 표시 방식(표준형·유동형)을 따른다.
        가정: 게시판 정보·판정(canWrite·reason)·유효 정책은 `GET /api/boards/{slug}` 한 번에 온다. 고정 공지는 첫 쪽·말머리/검색 없음일 때만 위에(최대 pinnedMax).
          19금 글은 목록에 「19」 표시와 함께 보이고 글 보기에서 성인 확인으로 가린다(community 는 서버가 목록에서 뺀다 — 성인 인증이 아직 없다).
          꺼진 게시판은 `403 BOARD_INACTIVE` 로 온다고 가정한다.
        게시판 제재·스텝 처리(board-sanction-contract · board-staff-action-contract): 매니저·스텝 본인에게만 머리 아래
          「게시판 운영 중 · 오늘 남은 처리 글 n · 댓글 n · 제재 n · [처리 내역]」(`viewer.staffTools` — 할 수 있는 처리만, [처리 내역] → `/boards/{slug}/actions`, 그 위 탭으로 제재 내역),
          운영자 회원 계정(`staffTools.operator` — 모든 게시판)이면 「운영자 계정으로 관리 중 · 하루 한도 없음 · [처리 내역]」,
          이 게시판에서 쓰기 제한 중이면 「…까지 글·댓글 쓰기가 제한돼요 · 사유 · [자세히]」(`viewer.writeRestriction`)이고 글쓰기 단추는 흐리게(누르면 같은 한 줄).
          이 게시판에서 받고 확인하지 않은 경고가 있으면 「이 게시판에서 경고를 받았어요 · 사유 · [자세히] · [확인했어요]」(`viewer.pendingWarnings`, 쓰기는 막지 않는다).
    -->
    <div>
        <!-- 게시판 레이아웃(테마마다 하나 — 켜져 있으면 이 화면 내용이 격자의 「게시판 본문」 칸에 들어가고 둘레에 가젯, 아니면 그대로. 2026-10-30) -->
        <BoardLayoutFrame screen="LIST" :board="boardState === 'notfound' || boardState === 'inactive' ? null : slug">
            <ErrorState v-if="boardState === 'notfound'" code="404" :title="t('board.notFoundTitle')" :description="t('board.notFoundText')">
                <Button as="router-link" to="/" :label="t('board.goHome')" />
            </ErrorState>
            <ErrorState v-else-if="boardState === 'inactive'" :title="t('board.inactiveTitle')" :description="boardError">
                <Button as="router-link" to="/" :label="t('board.goHome')" />
            </ErrorState>
            <ErrorState v-else-if="boardState === 'error' && !view" :title="t('board.loadFailed')" :description="boardError" retry @retry="retryAll" />

            <!-- 게시판 머리·목록·쪽 번호를 한 카드에 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 다음식·네이버식은 흰 카드다(`main.css` `content-card`) -->
            <div v-else class="content-card" data-testid="board-card">
                <!-- 머리 — 화면에 보이는 동안은 떠 있는 글쓰기 단추를 숨긴다(`boardHead` 를 지켜본다) -->
                <header ref="boardHead" class="flex items-start gap-3">
                    <div class="min-w-0 flex-1">
                        <h1 class="break-words text-lg font-bold leading-tight" :style="UtilBoard.textStyle(view?.color)" data-testid="board-title">
                            <template v-if="view">{{ localizedName(view.name, view.nameEn) }}</template>
                            <span v-else class="inline-block h-5 w-28 animate-pulse rounded bg-surface-200 align-middle dark:bg-surface-800" :aria-label="t('board.loadingName')" />
                        </h1>
                        <p v-if="view?.description" class="mt-0.5 text-xs text-muted-color">{{ view.description }}</p>
                    </div>
                    <!-- 핫딜 게시판에서 막힌 글쓰기는 까닭을 툴팁으로(단추는 눌리고 누르면 안내·로그인) -->
                    <Button
                        v-if="writeButton !== 'hidden'"
                        v-tooltip.bottom="writeButton === 'disabled' && isDeal ? writeBlockedText : undefined"
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

                <!-- 게시판 운영 줄 — 그 게시판 매니저·스텝 본인에게만(운영자 회원 계정은 모든 게시판). 오늘 남은 처리(글 · 댓글 · 제재 — 할 수 있는 것만, 운영자는 「하루 한도 없음」) · [처리 내역] -->
                <p v-if="staffTools" class="m-0 mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg bg-surface-100 px-3 py-2 text-xs dark:bg-surface-800/60" data-testid="board-staff-bar">
                    <i class="pi pi-shield text-[0.6875rem] text-muted-color" aria-hidden="true" />
                    <span class="font-semibold" data-testid="board-staff-bar-role">{{ staffBarLabel }}</span>
                    <template v-if="staffRemaining">
                        <span aria-hidden="true" class="text-muted-color">·</span>
                        <span class="tabular-nums" :class="UtilBoardStaffActionMessages.anyExhausted(staffTools) ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="board-staff-remaining">
                            {{ staffRemaining }}
                        </span>
                    </template>
                    <NuxtLink :to="UtilBoardStaffAction.listPath(slug)" class="ml-auto font-semibold text-primary underline-offset-2 hover:underline" data-testid="board-staff-actions-link">
                        {{ t('boardStaffAction.listLink') }}
                    </NuxtLink>
                </p>

                <!-- 이 게시판에서 쓰기 제한 중(게시판 제재) — 글쓰기 단추 자리의 안내(끝 시각·사유·[자세히]) -->
                <BoardWriteRestrictionNotice v-if="writeRestriction" :restriction="writeRestriction" class="mt-3" />

                <!-- 이 게시판에서 받고 확인하지 않은 경고(게시판 제재) — 사유·[자세히]·[확인했어요](전부 확인) -->
                <BoardWarningNotice v-if="pendingWarnings.length" :warnings="pendingWarnings" :busy="warningAckBusy" :error="warningAckError" class="mt-3" @acknowledge="acknowledgeWarnings" />

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

                <!-- 말머리 탭 — 전체 + 사용 중 말머리(공통 말머리 포함), 서버가 준 차례. 넘치면 양끝에 그라데이션 화살표(모바일도 — 누르면 한 쪽씩 넘어간다, 손가락 밀기도 된다) -->
                <nav v-if="view && view.prefixes.length" class="-mx-4 mt-3" :aria-label="t('board.prefixNav')">
                    <AppScrollRow mode="ARROWS" touch scroller-class="px-4 py-0.5">
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
                    </AppScrollRow>
                </nav>

                <!-- 핫딜 쇼핑몰 칩 — 말머리 아래, 전체 + 상위 8몰(서버 `stores`, 글 수 차례). 고른 몰이 상위 8에 없으면 끝에 붙인다. 주소 `?store=` -->
                <nav v-if="isDeal && storeChips.length > 1" class="no-scrollbar -mx-4 mt-1 overflow-x-auto px-4 py-0.5" :aria-label="t('hotdeal.storeNav')" data-testid="hotdeal-store-chips">
                    <ul class="m-0 flex list-none gap-1.5 p-0 py-1">
                        <li v-for="chip in storeChips" :key="chip.name || '-'">
                            <NuxtLink
                                :to="dealLink({ store: chip.name, page: 1 })"
                                class="inline-flex h-7 items-center gap-1 whitespace-nowrap rounded-md border px-2.5 text-xs focus-visible:outline-2 focus-visible:outline-primary"
                                :class="dealQuery.store === chip.name ? 'border-primary bg-primary/10 font-semibold text-primary' : 'border-surface text-muted-color hover:text-color'"
                                :aria-current="dealQuery.store === chip.name ? 'page' : undefined"
                            >
                                {{ chip.label }}<span v-if="chip.count !== null" class="tabular-nums opacity-70">{{ chip.count }}</span>
                            </NuxtLink>
                        </li>
                    </ul>
                </nav>

                <!-- 정렬 · 보기 전환 · 게시판 안 검색 — 핫딜 게시판은 정렬(최신·인기·마감 임박) + 「종료 숨기기」 -->
                <div class="mt-2 flex flex-wrap items-center gap-2 border-b border-surface pb-2">
                    <template v-if="isDeal">
                        <SelectButton
                            :model-value="dealQuery.sort"
                            :options="dealSortOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            size="small"
                            :aria-label="t('hotdeal.sortLabel')"
                            data-testid="hotdeal-sort"
                            @update:model-value="onDealSort"
                        />
                        <label class="inline-flex cursor-pointer items-center gap-1.5 text-xs text-muted-color">
                            <ToggleSwitch :model-value="dealQuery.hideEnded" :input-id="hideEndedId" data-testid="hotdeal-hide-ended" @update:model-value="onHideEnded(Boolean($event))" />
                            <span>{{ t('hotdeal.hideEnded') }}</span>
                        </label>
                    </template>
                    <div v-else class="flex gap-1 text-xs" role="group" :aria-label="t('board.sortLabel')">
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

                <AdHotdealPlacement v-if="hotdeal.placement.value" :ads="hotdeal.candidates.value" :placement="hotdeal.placement.value" :prefix-name="hotdeal.prefixName.value" />

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
                        <!-- 핫딜 게시판 — 쇼핑몰을 골라 비었으면 「전체 쇼핑몰」, 종료 글을 숨겨서 비었으면 「종료 글 보기」 -->
                        <EmptyState v-else-if="isDeal && dealQuery.store" :title="t('hotdeal.storeEmptyTitle', { name: dealQuery.store })" icon="pi pi-shop" data-testid="hotdeal-store-empty">
                            <Button as="router-link" :to="dealLink({ store: '', page: 1 })" :label="t('hotdeal.storeAll')" severity="secondary" variant="outlined" />
                        </EmptyState>
                        <EmptyState v-else-if="isDeal && dealQuery.hideEnded" :title="t('hotdeal.emptyTitle')" icon="pi pi-tag" data-testid="hotdeal-empty">
                            <Button as="router-link" :to="dealLink({ hideEnded: false, page: 1 })" :label="t('hotdeal.emptyShowEnded')" severity="secondary" variant="outlined" />
                        </EmptyState>
                        <EmptyState v-else-if="isDeal" :title="t('hotdeal.emptyAllTitle')" :description="writeButton === 'ready' ? t('hotdeal.emptyAllText') : ''" icon="pi pi-tag">
                            <Button v-if="writeButton === 'ready'" type="button" :label="t('shell.write')" @click="onWrite" />
                        </EmptyState>
                        <EmptyState v-else :title="t('board.emptyTitle')":description="writeButton === 'ready' ? t('board.emptyText') : t('board.emptyReadOnly')" icon="pi pi-file">
                            <Button v-if="writeButton === 'ready'" type="button" :label="t('shell.write')" @click="onWrite" />
                        </EmptyState>
                    </template>

                    <template v-else-if="list">
                        <component :is="listComponent" v-bind="listExtra" :rows="list.items" :pinned="list.pinned" :date-display="display.dateDisplay" :new-post-hours="display.newPostHours" :feed-ad="feedAd" />
                        <BoardPager :page="list.page" :total-pages="list.totalPages" :to="pageLink" />
                    </template>
                </div>
            </div>
        </BoardLayoutFrame>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { DealPostListQuery, PostListQuery, PostListResponse, PostSort } from '~/composables/board/api/usePostApi.types';
import type { DealSort, DealStoreChip } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { BoardViewMode } from '~/composables/board/useBoardViewMode';
import HotdealPostGrid from '~/components/hotdeal/HotdealPostGrid.vue';
import HotdealPostList from '~/components/hotdeal/HotdealPostList.vue';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardQuery from '~/utils/util-board-query';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
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
const publicAds = usePublicAds();
const time = useDisplayTime();
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

/* ── 핫딜 게시판(`kind DEAL`) ── */
const isDeal = computed(() => UtilHotdeal.isDealBoard(view.value));
const dealQuery = computed<DealPostListQuery>(() => UtilHotdeal.parseQuery(route.query));
const hideEndedId = useId();
const dealSortOptions = computed(() =>
    UtilHotdeal.SORTS.map((value) => ({ value, label: t(value === 'LATEST' ? 'hotdeal.sortLatest' : value === 'POPULAR' ? 'hotdeal.sortPopular' : 'hotdeal.sortEnding') })),
);
function dealLink(change: Partial<DealPostListQuery>) {
    return { path: `/boards/${slug.value}`, query: UtilHotdeal.buildQuery({ ...dealQuery.value, ...change }) };
}
function onDealSort(value: DealSort | null): void {
    if (value) void router.push(dealLink({ sort: value, page: 1 }));
}
function onHideEnded(value: boolean): void {
    void router.push(dealLink({ hideEnded: value, page: 1 }));
}
/** 쇼핑몰 칩 — 마지막으로 받은 목록의 `stores`(목록을 다시 받는 동안에도 칩은 그대로). 고른 몰이 상위 8에 없으면 끝에 붙인다 */
const dealStores = ref<DealStoreChip[]>([]);
const storeChips = computed(() => {
    const chips: { name: string; label: string; count: number | null }[] = [{ name: '', label: t('common.all'), count: null }, ...dealStores.value.map((s) => ({ name: s.name, label: s.name, count: s.count }))];
    const picked = dealQuery.value.store;
    if (picked && !chips.some((c) => c.name === picked)) chips.push({ name: picked, label: picked, count: null });
    return chips;
});

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
        // 날개 배너 — 광고 숨김·19금(옵션 꺼짐) 게시판이면 그리지 않는다(받기 전에는 `board` 판정을 서버에 맡긴다)
        if (slug.value === wanted) publicAds.setWingContext({ path: route.path, board: wanted, adsHidden: result.adsHidden === true, adult: result.allowAdult === true, group: false });
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
/** 게시판 제재(board-sanction-contract) — 매니저·스텝 본인의 운영 도구 · 보는 나의 이 게시판 쓰기 제한 */
const staffTools = computed(() => detail.value?.viewer.staffTools ?? null);
/** 게시판 운영 줄 앞머리 — 운영자 회원 계정 · 매니저 · 스텝 */
const staffBarLabel = computed(() => {
    const tools = staffTools.value;
    if (tools?.operator) return t('boardStaffAction.barOperator');
    return tools?.manager ? t('boardSanction.staffBarManager') : t('boardSanction.staffBarStaff');
});
/** 게시판 운영 줄의 오늘 남은 처리 — 할 수 있는 것만(비면 줄에서 뺀다). 운영자 회원 계정이면 「하루 한도 없음」 */
const staffRemaining = computed(() => (staffTools.value ? UtilBoardStaffActionMessages.remainingLine(t, staffTools.value) : ''));
const writeRestriction = computed(() => detail.value?.viewer.writeRestriction ?? null);
const pendingWarnings = computed(() => detail.value?.viewer.pendingWarnings ?? []);
const sanctionApi = useSanctionApi();
const warningAckBusy = ref(false);
const warningAckError = ref('');
/** [확인했어요] — 안내에 실린 경고를 모두 확인한다(멱등). 그새 없어진 제재(404)는 확인할 것이 없으니 넘긴다. 다 되면 안내를 뺀다 */
async function acknowledgeWarnings(): Promise<void> {
    const viewer = detail.value?.viewer;
    if (!viewer || warningAckBusy.value) return;
    warningAckBusy.value = true;
    warningAckError.value = '';
    try {
        for (const w of pendingWarnings.value) {
            try {
                await sanctionApi.acknowledgeBoard(w.boardSanctionId);
            } catch (e) {
                if (!(e instanceof ApiError && e.code === 'BOARD_SANCTION_NOT_FOUND')) throw e;
            }
        }
        viewer.pendingWarnings = [];
    } catch (e) {
        warningAckError.value = e instanceof ApiError && e.message ? e.message : t('sanction.ackFailed');
    } finally {
        warningAckBusy.value = false;
    }
}
/** 단추가 꺼진 까닭 중 머리에 미리 알릴 것 — 등급 미달·정지·이메일 미인증(로그인 필요·운영진 전용은 단추가 말한다) */
const blockedNotice = computed(() => {
    const reason = detail.value?.viewer.writeBlockedReason ?? null;
    return reason === 'GRADE_TOO_LOW' || reason === 'ACCOUNT_RESTRICTED' || reason === 'EMAIL_NOT_VERIFIED' ? writeBlockedText.value : '';
});
/** 글쓰기가 막힌 까닭 한 줄 — 핫딜 게시판은 핫딜 문구(「일반 등급부터 핫딜을 올릴 수 있어요」 등) */
const writeBlockedText = computed(() => {
    const reason = detail.value?.viewer.writeBlockedReason ?? null;
    const grade = detail.value?.writeMinGradeLabel ?? null;
    return isDeal.value ? UtilHotdealMessages.writeBlocked(t, reason, grade) : UtilBoardMessages.writeBlocked(t, reason, grade);
});

/* ── 말머리·보기 ── */
const prefixChips = computed(() => [{ id: '', name: t('common.all') }, ...(view.value?.prefixes ?? []).map((p) => ({ id: p.id, name: p.name }))]);
const activePrefixName = computed(() => view.value?.prefixes.find((p) => p.id === query.value.prefix)?.name ?? '');

const hotdeal = useHotdealAds(
    computed(() =>
        view.value
            ? { board: slug.value, view: view.value, dealEnabled: view.value.dealEnabled === true, prefix: query.value.prefix, prefixName: activePrefixName.value, firstPage: query.value.page === 1 && !query.value.q }
            : null,
    ),
);

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
/** 핫딜 게시판은 가격 줄을 붙인 핫딜 목록(테마 슬롯 없이 하나 — 모양은 역할 변수를 따른다) */
const listComponent = computed(() => (isDeal.value ? (listKind.value === 'LIST' ? HotdealPostList : HotdealPostGrid) : UtilTheme.resolve(theme.value, 'postList', listKind.value)));
const listExtra = computed(() => (isDeal.value && listKind.value !== 'LIST' ? { variant: listKind.value } : {}));

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
    // 핫딜 게시판은 핫딜 조건(정렬·종료 숨기기)을 지키며 말머리·검색·쪽만 바꾼다
    if (isDeal.value) {
        const next: Partial<DealPostListQuery> = {};
        if (change.prefix !== undefined) next.prefix = change.prefix;
        if (change.q !== undefined) next.q = change.q;
        if (change.page !== undefined) next.page = change.page;
        return dealLink(next);
    }
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
    // 로그인이 필요하면 단추는 켜 두고 누르면 로그인 안내 — 핫딜 게시판은 흐리게 + 까닭 툴팁(누르면 그대로 로그인 안내)
    if (reason === 'LOGIN_REQUIRED') return isDeal.value ? 'disabled' : 'ready';
    return 'disabled';
});

function onWrite(): void {
    const target = { path: '/write', query: { board: slug.value } };
    const d = detail.value;
    const reason = d ? d.viewer.writeBlockedReason : user.value ? null : 'LOGIN_REQUIRED';
    if (!reason) return void navigateTo(target);
    if (reason === 'LOGIN_REQUIRED') return prompt('write', router.resolve(target).fullPath);
    // 이 게시판에서 쓰기 제한 중 — 끝 시각을 담은 한 줄(안내 상자는 머리에 이미 있다)
    if (writeRestriction.value) return toast.show(UtilBoardSanctionMessages.restrictionTitle(t, writeRestriction.value, time.value));
    toast.show(isDeal.value ? UtilHotdealMessages.writeBlocked(t, reason, d?.writeMinGradeLabel ?? null) : UtilBoardMessages.writeBlocked(t, reason, d?.writeMinGradeLabel ?? null));
}

/* ── 글 목록 ── */
type ListState = 'loading' | 'ready' | 'error';
const list = ref<PostListResponse | null>(null);
const listState = ref<ListState>('loading');
const listError = ref('');
const isEmpty = computed(() => !list.value || (list.value.items.length === 0 && list.value.pinned.length === 0));

const feedAd = useBoardFeedAd(
    computed(() => (view.value && list.value ? { slug: slug.value, board: view.value, firstPage: query.value.page === 1, search: Boolean(query.value.q), rows: list.value.items.length } : null)),
);

let listRequest = 0;
let listAbort: AbortController | null = null;
async function loadList(): Promise<void> {
    const mine = ++listRequest;
    listAbort?.abort();
    listAbort = new AbortController();
    listState.value = 'loading';
    try {
        // 핫딜 게시판은 핫딜 조건(정렬·종료 숨기기)으로 — 게시판 종류를 아직 모르면 일반 목록으로 받고, 알게 되면 다시 받는다(아래 감시)
        const result = isDeal.value ? await boardApi.dealPosts(slug.value, dealQuery.value, listAbort.signal) : await boardApi.posts(slug.value, query.value, listAbort.signal);
        if (mine !== listRequest) return;
        list.value = result;
        dealStores.value = isDeal.value ? (result.stores ?? []) : [];
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
    () => [slug.value, query.value.prefix, query.value.sort, query.value.q, query.value.page, isDeal.value, dealQuery.value.sort, dealQuery.value.hideEnded, dealQuery.value.store] as const,
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

/* ── 머리가 보이는가 — 창 위에 붙는 셸 막대 아래로 들어가면 안 보인다고 친다. 떠 있는 글쓰기 단추(`WriteFab`)가 이것을 본다 ── */
const boardHead = ref<HTMLElement | null>(null);
let headObserver: IntersectionObserver | null = null;

/** 창 위에 붙는 셸 막대(본문 밖 `sticky`·`fixed` 이고 위가 0)의 가장 큰 높이 — 테마마다 다르다(로보얌은 메뉴 줄까지 붙어 `--shell-header-h` 보다 높다) */
function shellHeaderPx(): number {
    const main = document.getElementById('main-content');
    let max = 0;
    for (const el of document.querySelectorAll<HTMLElement>('header, nav, div')) {
        if (main?.contains(el)) continue;
        const style = getComputedStyle(el);
        if ((style.position === 'sticky' || style.position === 'fixed') && style.top === '0px' && el.offsetHeight < 300) max = Math.max(max, el.offsetHeight);
    }
    return max;
}

watch(boardHead, (el) => {
    headObserver?.disconnect();
    headObserver = null;
    current.setWriteInView(true);
    if (!el) return;
    headObserver = new IntersectionObserver((entries) => current.setWriteInView(Boolean(entries.at(-1)?.isIntersecting)), { rootMargin: `-${Math.round(shellHeaderPx())}px 0px 0px 0px` });
    headObserver.observe(el);
});

onBeforeUnmount(() => {
    listAbort?.abort();
    headObserver?.disconnect();
    current.clear(slug.value);
});

useSeoMeta({
    title: () => (view.value ? `${localizedName(view.value.name, view.value.nameEn)}${query.value.page > 1 ? ` (${t('board.pageN', { n: query.value.page })})` : ''}` : t('board.bundleLabel')),
    robots: () => (boardState.value === 'notfound' || boardState.value === 'inactive' ? 'noindex' : undefined),
});
</script>
