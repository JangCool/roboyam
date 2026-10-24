<template>
    <!--
        게시글 관리 — 모든 게시판의 글을 찾아 숨기기·숨김 해제·삭제·복구·이동·공지 지정·고정을 하고, 글의 조치 기록과 찬반 토론을 한 화면에서 본다.
        계약 `docs/features/post-admin-contract.md`(community `admin/app/pages/posts` 의 동작·구성을 로보얌 글 상태·권한·admin-web 모양으로 옮김 — 소모임·출하·예약 발행·글 유형은 뺐다).
        - 조회 카드: 검색 한 칸(`#번호`·15~19자리 숫자 = 글 번호, 그 밖 제목·본문·작성자 2자 이상) · 게시판 → 말머리(한 줄) · 상태 탭(전체/공개/숨김/삭제 + 건수 — 상태 조건만 뺀 건수).
          목록(회원 관리와 같은 자리): 정렬은 표의 「작성일」 머리(내림차순 = 최신순 · 오름차순 = 오래된순 — 서버가 이 둘만 안다) · 표 위 툴바에 건수·정렬 안내 · 쪽 넘김의 쪽 크기(10/20/50, 서버 상한 50).
          **모두 주소 쿼리**(`?q=&board=&prefix=&state=&sort=&page=&size=&post=`)라 새로고침·공유해도 그대로다. 조건·정렬·쪽 크기를 바꾸면 1쪽으로(replace), 쪽만 바꾸면 push. `page` 는 1부터(API 는 0부터).
        - 표(DataTable, 서버가 한 쪽씩): 체크(공개·숨김 글, `post.manage`) · 번호(복사) · 게시판 · 말머리·칩(공지/고정/19금/토론)·제목 · 작성자(비회원·탈퇴) · 상태 알약(숨김 까닭·시각, 삭제 예정·파기) · 신고 대기 · 추천/댓글/조회 · 작성일 · ⋯ 조치 메뉴.
          줄·제목을 누르면 글 서랍(`PostDetailDrawer`, 주소 `?post=` — replace, 목록을 다시 받지 않는다)이 열린다. 이전/다음은 지금 쪽의 줄 순서. 운영자 알림 링크 `/posts?post={id}` 도 같은 서랍을 연다.
        - 조치: 사유 대화창(`PostReasonDialog` — 숨기기는 「작성자에게 알림」) · 이동 대화창(`PostMoveDialog` — 게시판 → 말머리, 「고정은 풀려요」) · 공지 지정·고정(`PIN_LIMIT` 은 대화창 안 서버 문구).
          `409 POST_STATE_INVALID`·`404 POST_NOT_FOUND`·`409 POST_PURGED`(그 사이 바뀜)는 토스트 + 새로 받기. 복구는 `post.restore` 가 있을 때만.
        - 일괄: 줄을 고르면 아래에 일괄 줄 — 숨기기·숨김 해제·삭제(삭제는 `post.bulk-delete`)를 사유 하나로 → 결과 대화창(글마다 처리됨/못 함 + 까닭).
        - 글의 찬반 토론: 서랍의 토론 구획(상태 요약) + [토론 설정](`DebateSettingsDialog`) + [의견 관리](`DebateOpinionsDrawer`) — 하이라이트 화면과 같은 재사용 부품(`useDebateActions`).
        - 권한: 보기 `post.read`(메뉴) — 조치 기록·토론 보기는 된다. `post.manage` 가 없으면 읽기 전용 안내 + 체크·조치 없음. 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('post.title')" :subtitle="t('post.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('post.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <!-- 조건 — 회원 관리 조회 카드(`MemberSearchPanel`)·포인트 내역과 같은 구조: 맨 윗줄 검색 칸 → 라벨 열 + 입력 줄(`FilterRow`) → 구분선 아래 가운데 「검색」·「초기화」.
             검색어는 「검색」(또는 Enter)을 눌러야 주소로 가고, 게시판·말머리·상태·정렬은 고르는 즉시 주소로 간다(전과 같다). -->
        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('post.searchLabel')" novalidate @submit.prevent="submitSearch">
            <!-- ① 맨 윗줄 — 검색 칸 -->
            <div class="flex flex-wrap items-center gap-2">
                <InputText
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilPost.SEARCH_MAX"
                    :placeholder="t('post.searchPlaceholder')"
                    :aria-label="t('post.searchLabel')"
                    :aria-describedby="`${uid}-search-help`"
                    :invalid="Boolean(searchError)"
                    autocomplete="off"
                    class="min-w-0 flex-1 basis-56"
                />
                <Button v-if="filters.q" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('post.searchClear')" @click="clearSearch" />
            </div>
            <p v-if="searchError" role="alert" class="-mt-1 text-xs text-red-600 dark:text-red-400">{{ searchError }}</p>
            <p v-else :id="`${uid}-search-help`" class="-mt-1 text-xs text-muted-color">{{ t('post.searchHelp') }}</p>

            <!-- ② 조건 줄 -->
            <!-- 게시판 → 말머리를 한 줄에(회원 관리 「상태 변경」 줄처럼 한 줄 여러 칸). 라벨은 게시판 칸에 붙고 말머리 칸은 자기 aria-label 을 쓴다 -->
            <FilterRow :label="t('post.filterBoard')" :label-for="`${uid}-board`">
                <Select :model-value="filters.board || ALL" :input-id="`${uid}-board`" :options="boardFilterOptions" option-label="label" option-value="value" filter :loading="optionsLoading" class="w-56" @update:model-value="setBoard" />
                <Select
                    :model-value="filters.prefix || ALL"
                    :input-id="`${uid}-prefix`"
                    :options="prefixFilterOptions"
                    option-label="label"
                    option-value="value"
                    :disabled="!filters.board"
                    :title="filters.board ? undefined : t('post.filterPrefixNeedBoard')"
                    :aria-label="t('post.filterPrefix')"
                    class="w-44"
                    @update:model-value="setPrefix"
                />
                <p v-if="optionsError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
                    {{ optionsError }}
                    <Button type="button" :label="t('post.optionsRetry')" size="small" severity="secondary" variant="text" @click="loadOptions" />
                </p>
            </FilterRow>

            <FilterRow :label="t('post.stateLabel')">
                <template #default="{ labelId }">
                    <SelectButton
                        :model-value="filters.state"
                        :options="stateOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :aria-labelledby="labelId"
                        class="post-tabs"
                        @update:model-value="setState"
                    />
                </template>
            </FilterRow>

            <!-- ③ 맨 아래 줄 — 가운데 「검색」·「초기화」 -->
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy && !refreshing" />
                <Button type="button" :label="t('post.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </div>
        </form>

        <div class="flex min-w-0 flex-col gap-2">
            <!-- 툴바 — 「전체 N건」·정렬 안내·읽기 전용 안내(회원 관리 표 위 툴바와 같은 자리) -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="view" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('post.total', { count: view.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ sortText }}</p>
                <p v-if="!canManage" class="flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-lock" aria-hidden="true" />{{ t('post.readOnly') }}
                </p>
            </div>

            <!-- 표 -->
            <div class="post-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="items"
                    data-key="id"
                    lazy
                    paginator
                    :first="(filters.page - 1) * filters.size"
                    :rows="filters.size"
                    :total-records="view?.total ?? 0"
                    :rows-per-page-options="[...UtilPost.PAGE_SIZES]"
                    :loading="busy && !refreshing"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    row-hover
                    scrollable
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    sort-field="createdAt"
                    :sort-order="filters.sort === 'oldest' ? 1 : -1"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="onRowClick"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('post.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('post.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-file" :title="t('post.empty')" :description="t('post.emptyHint')" />
                    </template>

                    <Column v-if="canManage" style="width: 2.75rem">
                        <template #header>
                            <Checkbox binary :model-value="allSelected" :indeterminate="selection.length > 0 && !allSelected" :disabled="selectableIds.length === 0 || acting" :aria-label="t('post.selectAll')" @update:model-value="toggleAll" />
                        </template>
                        <template #body="{ data }">
                            <span data-post-cell @click.stop>
                                <Checkbox
                                    v-if="UtilPost.isSelectable(data)"
                                    binary
                                    :model-value="selection.includes(data.id)"
                                    :disabled="acting"
                                    :aria-label="t('post.selectRow', { title: data.title })"
                                    @update:model-value="toggle(data.id)"
                                />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('post.col.id')" style="min-width: 9.5rem">
                        <template #body="{ data }">
                            <button
                                v-tooltip.top="t('content.copyPostId')"
                                type="button"
                                class="inline-flex items-center gap-1 rounded px-1 text-[12px] tabular-nums text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                                :aria-label="t('content.copyPostIdLabel', { id: data.id })"
                                @click.stop="copyId(data.id)"
                            >
                                <i class="pi pi-copy text-[10px]" aria-hidden="true" />{{ data.id }}
                            </button>
                        </template>
                    </Column>

                    <Column :header="t('post.col.board')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('post.col.title')" style="min-width: 18rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                <span v-if="data.prefix" class="shrink-0 text-xs text-muted-color">[{{ data.prefix.name }}]</span>
                                <Tag v-if="data.notice" :value="t('post.chip.notice')" severity="info" class="text-[11px]!" />
                                <Tag v-if="data.pinned" :value="t('post.chip.pinned')" icon="pi pi-thumbtack" severity="info" class="text-[11px]!" />
                                <Tag v-if="data.adult" :value="t('post.chip.adult')" severity="danger" class="text-[11px]!" />
                                <Tag v-if="data.debate" :value="t('post.chip.debate')" icon="pi pi-comments" severity="secondary" class="text-[11px]!" />
                                <button
                                    type="button"
                                    class="min-w-0 text-left text-[13px] font-medium break-words [overflow-wrap:anywhere] hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                    :class="data.state === 'DELETED' ? 'text-muted-color line-through' : ''"
                                    @click.stop="openPost(data)"
                                >
                                    {{ data.title }}
                                </button>
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('post.col.author')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :class="data.author.withdrawn ? 'text-muted-color' : ''">{{ UtilPostMessages.author(t, data) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('post.col.state')" style="min-width: 8.5rem">
                        <template #body="{ data }">
                            <PostStateTag :item="data" lines />
                        </template>
                    </Column>

                    <Column :header="t('post.col.reports')" style="min-width: 5rem">
                        <template #body="{ data }">
                            <Tag v-if="data.openReports > 0" :value="t('post.reportsPending', { n: data.openReports })" icon="pi pi-flag" severity="danger" class="text-[11px]!" />
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>

                    <Column :header="t('post.col.stats')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span class="inline-flex items-center gap-2 text-[12px] tabular-nums text-muted-color" :aria-label="t('post.stats', { likes: data.likeCount, comments: data.commentCount, views: data.viewCount })">
                                <span><i class="pi pi-thumbs-up mr-0.5 text-[10px]" aria-hidden="true" />{{ data.likeCount }}</span>
                                <span><i class="pi pi-comment mr-0.5 text-[10px]" aria-hidden="true" />{{ data.commentCount }}</span>
                                <span><i class="pi pi-eye mr-0.5 text-[10px]" aria-hidden="true" />{{ data.viewCount }}</span>
                            </span>
                        </template>
                    </Column>

                    <Column field="createdAt" sortable :header="t('post.col.createdAt')" style="min-width: 7.5rem">
                        <template #body="{ data }">
                            <time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time>
                        </template>
                    </Column>

                    <Column style="width: 3rem">
                        <template #header><span class="sr-only">{{ t('post.col.actions') }}</span></template>
                        <template #body="{ data }">
                            <Button
                                v-if="UtilPost.hasAction(actionsOf(data))"
                                type="button"
                                icon="pi pi-ellipsis-h"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :disabled="acting"
                                :aria-label="t('post.actionsMenu', { title: data.title })"
                                aria-haspopup="menu"
                                @click.stop="openRowMenu($event, data)"
                            />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <!-- 일괄 줄 — 목록 아래, 화면 바닥에 붙는다 -->
        <div
            v-if="canManage && selection.length > 0"
            class="sticky bottom-3 z-20 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-primary-200 bg-primary-50 px-3 py-2 shadow-lg dark:border-primary-800 dark:bg-primary-950"
            role="region"
            :aria-label="t('post.bulkRegion')"
            data-post-keep-drawer
        >
            <b class="text-sm">{{ t('post.selected', { n: selection.length }) }}</b>
            <span class="min-w-0 flex-1 basis-60 text-xs leading-relaxed text-muted-color max-sm:hidden">{{ t('post.bulkLead') }}</span>
            <span class="ml-auto flex flex-wrap gap-2">
                <Button
                    v-for="action in bulkActions"
                    :key="action"
                    type="button"
                    :label="t(`post.bulkAction.${action}`, { n: bulkCount(action) })"
                    size="small"
                    :severity="action === 'UNHIDE' ? undefined : 'danger'"
                    :disabled="acting || bulkCount(action) === 0"
                    @click="openBulk(action)"
                />
                <Button type="button" :label="t('post.bulkClear')" size="small" severity="secondary" variant="outlined" :disabled="acting" @click="selection = []" />
            </span>
        </div>

        <Menu ref="rowMenu" :model="rowMenuItems" popup />

        <PostReasonDialog v-model:visible="reasonOpen" :kind="reasonKind" :item="reasonTarget" :busy="acting" :error="reasonError" :field-error="reasonFieldError" @submit="submitReason" />
        <PostMoveDialog
            v-model:visible="moveOpen"
            :item="moveTarget"
            :boards="boards"
            :options-loading="optionsLoading"
            :options-error="optionsError"
            :busy="acting"
            :error="moveError"
            :field-errors="moveFieldErrors"
            @submit="submitMove"
            @retry-options="loadOptions"
        />
        <PostBulkDialog
            v-model:visible="bulkOpen"
            :action="bulkAction"
            :count="bulkTargetIds.length"
            :skipped="selection.length - bulkTargetIds.length"
            :busy="acting"
            :error="bulkError"
            :field-error="bulkFieldError"
            :result="bulkResult"
            :titles="bulkTitles"
            @submit="submitBulk"
        />

        <PostDetailDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail.detail.value"
            :loading="detail.loading.value"
            :error="detail.error.value"
            :actions="drawerActions"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            :debate="debate"
            :debate-loading="debateLoading"
            :debate-error="debateError"
            :can-manage="canManage"
            :can-restore="canRestore"
            @retry="detail.reload(false)"
            @act="onDrawerAct"
            @prev="neighbors.prev && openPost(neighbors.prev)"
            @next="neighbors.next && openPost(neighbors.next)"
            @debate-settings="debateSettingsOpen = true"
            @debate-opinions="opinionsOpen = true"
            @debate-retry="loadDebate()"
            @closed="onDrawerClosed"
        />

        <DebateSettingsDialog v-model:visible="debateSettingsOpen" :post-id="detail.openId.value" :title="drawerItem?.title ?? ''" :actions="debateSettingsActions" :can-manage="canManage" @changed="onDebateChanged" />
        <DebateOpinionsDrawer v-model:visible="opinionsOpen" :target="opinionsTarget" :actions="opinionActions" :can-read="true" :can-manage="canManage" @changed="loadDebate(true)" />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { ApiError } from '~/composables/common/useApiClient';
import type { DebateItem } from '~/composables/content/api/useDebateApi.types';
import type { DebateDrawerTarget } from '~/components/debate/DebateOpinionsDrawer.types';
import type { AdminPostItem, AdminPostListView, AdminPostBoardOption, PostBulkAction, PostBulkResult, PostStateFilter } from '~/composables/post/api/usePostApi.types';
import UtilPost, { type PostActionKind, type PostFilters } from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('post.title') });

const api = usePostApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const uid = useId();

/** 권한(정의: util-permission-catalog.ts) — 감추는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('post.manage'));
const canRestore = computed(() => can('post.restore'));
const canBulkDelete = computed(() => can('post.bulk-delete'));

function actionsOf(item: AdminPostItem) {
    return UtilPost.actionsOf(item, { manage: canManage.value, restore: canRestore.value });
}

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/posts';
/** 「전체」 선택지의 값 — 빈 글자는 Select 가 "고르지 않음"으로 읽어 이름이 보이지 않는다 */
const ALL = '__all__';

const filters = computed<PostFilters>(() => UtilPost.parse(route.query));

/**
 * 필터를 주소에 싣는다(서랍이 열려 있으면 그 글 `post` 는 그대로). 쪽 이동은 push, 조건 변경은 replace.
 * `post` 는 주소가 아니라 서랍 상태로 정한다 — 서랍 밖(조건·쪽 단추)을 누르면 서랍이 먼저 닫히는데(pointerdown) 주소는 아직 옛 값이라, 주소를 읽으면 닫힌 글이 다시 열린다.
 */
function apply(next: PostFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilPost.key(next) === UtilPost.key(filters.value)) return;
    const post = drawerOpen.value ? detail.openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilPost.toQuery(next), ...(post ? { post } : {}) } });
}

function setState(state: PostStateFilter): void {
    apply(UtilPost.change(filters.value, { state }));
}
function setBoard(value: string): void {
    apply(UtilPost.change(filters.value, { board: value === ALL ? '' : value }));
}
function setPrefix(value: string): void {
    apply(UtilPost.change(filters.value, { prefix: value === ALL ? '' : value }));
}
/** 쪽 넘김 — 쪽만 바꾸면 push, 쪽 크기를 바꾸면 1쪽으로(replace) */
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilPost.change(filters.value, { size: e.rows }));
    else apply(UtilPost.change(filters.value, { page: e.page + 1 }), 'push');
}
/** 「작성일」 머리 — 오름차순 = 오래된순, 내림차순 = 최신순(서버가 아는 정렬은 이 둘뿐). 1쪽으로 */
function onSort(e: { sortOrder?: number | null }): void {
    apply(UtilPost.change(filters.value, { sort: e.sortOrder === 1 ? 'oldest' : 'newest' }));
}
function resetFilters(): void {
    searchText.value = '';
    searchError.value = '';
    apply(UtilPost.clear(filters.value));
}

const hasConditions = computed(() => UtilPost.hasConditions(filters.value));

/* ── 검색 ── */

const searchText = ref(filters.value.q);
const searchError = ref('');
watch(
    () => filters.value.q,
    (q) => {
        searchText.value = q;
        searchError.value = '';
    },
);

/** 보낼 때 정한다(글자마다 서버를 부르지 않는다). 한 글자면 보내지 않고 알린다. 비우고 보내면 검색을 푼다 */
function submitSearch(): void {
    const parsed = UtilPost.parseSearch(searchText.value);
    if (parsed.kind === 'TOO_SHORT') {
        searchError.value = t('post.searchTooShort');
        return;
    }
    searchError.value = '';
    apply(UtilPost.change(filters.value, { q: parsed.text }));
}
function clearSearch(): void {
    searchText.value = '';
    searchError.value = '';
    apply(UtilPost.change(filters.value, { q: '' }));
}

/* ── 게시판·말머리 선택지 ── */

const boards = ref<AdminPostBoardOption[]>([]);
const optionsLoading = ref(false);
const optionsError = ref('');

async function loadOptions(): Promise<void> {
    optionsLoading.value = true;
    optionsError.value = '';
    try {
        boards.value = (await api.boardOptions()).boards;
    } catch (e) {
        // 선택지만 비운다 — 목록은 게시판 조건 없이도 본다
        optionsError.value = `${t('post.optionsFailed')} ${errorOf(e).message}`;
    } finally {
        optionsLoading.value = false;
    }
}

/** 게시판 필터 — 켜진 게시판. 주소의 게시판이 선택지에 없으면(꺼진 게시판·아직 못 받음) 번호 그대로 보여 푸는 길을 남긴다 */
const boardFilterOptions = computed(() => {
    const list = [{ value: ALL, label: t('post.filterAll') }, ...boards.value.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) }))];
    const id = filters.value.board;
    if (id && !list.some((o) => o.value === id)) list.push({ value: id, label: `#${id}` });
    return list;
});

const prefixFilterOptions = computed(() => {
    const board = boards.value.find((b) => b.id === filters.value.board);
    const list = [{ value: ALL, label: t('post.filterAll') }, ...(board?.prefixes ?? []).map((p) => ({ value: p.id, label: p.name }))];
    const id = filters.value.prefix;
    if (id && !list.some((o) => o.value === id)) list.push({ value: id, label: `#${id}` });
    return list;
});

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const stateOptions = computed(() =>
    UtilPost.STATE_FILTERS.map((value) => {
        const c = view.value?.counts;
        const count = !c ? null : value === 'ALL' ? c.all : value === 'PUBLIC' ? c.public : value === 'HIDDEN' ? c.hidden : c.deleted;
        return { value, label: count === null ? t(`post.state.${value}`) : UtilPostMessages.tab(t, value, count, locale.value) };
    }),
);
/** 툴바의 정렬 안내 — 회원 관리와 같은 꼴(「정렬: 작성일 내림차순」) */
const sortText = computed(() => t('post.sortBy', { field: t('post.col.createdAt'), dir: t(filters.value.sort === 'oldest' ? 'post.sortAsc' : 'post.sortDesc') }));

/* ── 목록 ── */

const view = ref<AdminPostListView | null>(null);
const items = computed(() => view.value?.items ?? []);
const busy = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);

let seq = 0;
let abort: AbortController | null = null;
onBeforeUnmount(() => {
    seq++;
    abort?.abort();
});

/** 지금 필터로 받는다. `silent` 면 보이던 표를 그대로 두고 새로 받고, 실패하면 토스트만 */
async function load(silent = false): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    abort = new AbortController();
    busy.value = true;
    refreshing.value = silent;
    if (!silent) loadError.value = null;
    try {
        const found = await api.list(UtilPost.toApi(filters.value), abort.signal);
        if (mine !== seq) return;
        view.value = found;
        loadError.value = null;
        selection.value = selection.value.filter((id) => selectableIds.value.includes(id));
        // 건수는 있는데 이 쪽이 비었다(큰 쪽 번호·마지막 쪽을 다 처리함) — 마지막 쪽으로
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilPost.change(filters.value, { page: UtilPost.pageCount(found.total, found.size || filters.value.size) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        if (silent && view.value) toast.error(t('post.refreshFailed', { reason: error.message }), 8000);
        else {
            view.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) {
            busy.value = false;
            refreshing.value = false;
        }
    }
}

onMounted(() => void loadOptions());

async function copyId(id: string): Promise<void> {
    try {
        await navigator.clipboard.writeText(id);
        toast.success(t('content.postIdCopied'));
    } catch {
        toast.error(t('content.copyFailed'));
    }
}

/* ── 선택·일괄 ── */

const selection = ref<string[]>([]);
const selectableIds = computed(() => (canManage.value ? items.value.filter((item) => UtilPost.isSelectable(item)).map((item) => item.id) : []));
const allSelected = computed(() => selectableIds.value.length > 0 && selectableIds.value.every((id) => selection.value.includes(id)));
const selectedItems = computed(() => items.value.filter((item) => selection.value.includes(item.id)));
const bulkActions = computed(() => UtilPost.BULK_ACTIONS.filter((action) => action !== 'DELETE' || canBulkDelete.value));

function toggle(id: string): void {
    if (selection.value.includes(id)) selection.value = selection.value.filter((x) => x !== id);
    else if (selectableIds.value.includes(id) && selection.value.length < UtilPost.BULK_MAX) selection.value = [...selection.value, id];
}
function toggleAll(): void {
    selection.value = allSelected.value ? [] : selectableIds.value.slice(0, UtilPost.BULK_MAX);
}
function bulkCount(action: PostBulkAction): number {
    return UtilPost.bulkTargets(action, selectedItems.value).length;
}

const bulkOpen = ref(false);
const bulkAction = ref<PostBulkAction>('HIDE');
const bulkError = ref('');
const bulkFieldError = ref('');
const bulkResult = ref<PostBulkResult | null>(null);
const bulkTitles = ref<Record<string, string>>({});
const bulkTargetIds = computed(() => (bulkResult.value ? [] : UtilPost.bulkTargets(bulkAction.value, selectedItems.value).map((item) => item.id)));

function openBulk(action: PostBulkAction): void {
    if (acting.value || bulkCount(action) === 0) return;
    bulkAction.value = action;
    bulkError.value = '';
    bulkFieldError.value = '';
    bulkResult.value = null;
    bulkOpen.value = true;
}

watch(bulkOpen, (isOpen) => {
    if (!isOpen) bulkResult.value = null;
});

async function submitBulk(payload: { reason: string; notify: boolean }): Promise<void> {
    const targets = UtilPost.bulkTargets(bulkAction.value, selectedItems.value);
    if (acting.value || targets.length === 0) return;
    const action = bulkAction.value;
    acting.value = true;
    bulkError.value = '';
    bulkFieldError.value = '';
    try {
        const result = await api.bulk({ action, ids: targets.map((item) => item.id), reason: payload.reason, notifyAuthor: action === 'HIDE' && payload.notify });
        bulkTitles.value = Object.fromEntries(targets.map((item) => [item.id, item.title]));
        bulkResult.value = { done: result.done ?? [], failed: result.failed ?? [] };
        selection.value = [];
        toast.show(UtilPostMessages.bulkSummary(t, bulkResult.value), { tone: bulkResult.value.failed.length === 0 ? 'success' : 'default', life: 6000 });
        await load(true);
        const openId = detail.openId.value;
        if (openId && targets.some((item) => item.id === openId)) void detail.reload();
    } catch (e) {
        const error = errorOf(e);
        // 고른 글과 쓴 사유는 그대로 둔다
        if (error.fields?.reason) bulkFieldError.value = error.fields.reason;
        else bulkError.value = error.fields?.ids ?? error.fields?.action ?? error.message;
    } finally {
        acting.value = false;
    }
}

/* ── 글 하나의 조치 ── */

/** 조치·일괄이 가는 중 — 둘이 같이 쓴다 */
const acting = ref(false);

const reasonOpen = ref(false);
const reasonKind = ref<Exclude<PostActionKind, 'MOVE'> | null>(null);
const reasonTarget = ref<AdminPostItem | null>(null);
const reasonError = ref('');
const reasonFieldError = ref('');

const moveOpen = ref(false);
const moveTarget = ref<AdminPostItem | null>(null);
const moveError = ref('');
const moveFieldErrors = ref<Record<string, string>>({});

function openAct(item: AdminPostItem, kind: PostActionKind): void {
    if (acting.value) return;
    if (kind === 'MOVE') {
        moveTarget.value = item;
        moveError.value = '';
        moveFieldErrors.value = {};
        if (boards.value.length === 0 && !optionsLoading.value) void loadOptions();
        moveOpen.value = true;
        return;
    }
    reasonTarget.value = item;
    reasonKind.value = kind;
    reasonError.value = '';
    reasonFieldError.value = '';
    reasonOpen.value = true;
}

/** 그 사이 다른 운영자가 바꿨거나 글이 없어졌다 — 알리고 새로 받는다(대화창은 닫는다) */
const STALE_CODES = new Set(['POST_STATE_INVALID', 'POST_NOT_FOUND', 'POST_PURGED']);

type ActOutcome = { done: true } | { done: false; error: string; fields: Record<string, string> };

async function runAct(item: AdminPostItem, kind: PostActionKind, input: { reason: string; notify?: boolean; boardId?: string; prefixId?: string | null }): Promise<ActOutcome> {
    if (acting.value) return { done: false, error: '', fields: {} };
    acting.value = true;
    const reason = input.reason;
    const notified = kind === 'HIDE' && input.notify === true;
    try {
        switch (kind) {
            case 'HIDE':
                await api.hide(item.id, { reason, notifyAuthor: notified });
                break;
            case 'UNHIDE':
                await api.unhide(item.id, { reason });
                break;
            case 'DELETE':
                await api.remove(item.id, { reason });
                break;
            case 'RESTORE':
                await api.restore(item.id, { reason });
                break;
            case 'MOVE':
                await api.move(item.id, { boardId: input.boardId ?? '', prefixId: input.prefixId ?? null, reason });
                break;
            case 'NOTICE':
                await api.notice(item.id, { reason });
                break;
            case 'PIN':
                await api.pin(item.id, { reason });
                break;
            case 'UNPIN':
                await api.unpin(item.id, { reason });
                break;
        }
    } catch (e) {
        const error = errorOf(e);
        if (STALE_CODES.has(error.code)) {
            toast.error(t('post.staleFailed', { reason: error.message }), 8000);
            await refreshAfter(item.id);
            return { done: true };
        }
        // `PIN_LIMIT`·권한 없음 등은 대화창 안 한 줄(서버 문구 그대로) — 쓰던 사유는 그대로 둔다
        if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) return { done: false, error: '', fields: { ...error.fields } };
        return { done: false, error: error.message, fields: {} };
    } finally {
        acting.value = false;
    }
    toast.success(UtilPostMessages.done(t, kind, notified));
    await refreshAfter(item.id);
    return { done: true };
}

/** 조치 뒤 — 목록(상태·고정·건수)과 열린 글(알약·띠·조치 기록)을 새로 받는다 */
async function refreshAfter(id: string): Promise<void> {
    await load(true);
    if (detail.openId.value === id) await detail.reload();
}

async function submitReason(payload: { reason: string; notify: boolean }): Promise<void> {
    const item = reasonTarget.value;
    const kind = reasonKind.value;
    if (!item || !kind) return;
    reasonError.value = '';
    reasonFieldError.value = '';
    const outcome = await runAct(item, kind, payload);
    if (outcome.done) {
        reasonOpen.value = false;
        return;
    }
    reasonFieldError.value = outcome.fields.reason ?? '';
    reasonError.value = outcome.error || (outcome.fields.reason ? '' : Object.values(outcome.fields).join(' '));
}

async function submitMove(payload: { boardId: string; prefixId: string | null; reason: string }): Promise<void> {
    const item = moveTarget.value;
    if (!item) return;
    moveError.value = '';
    moveFieldErrors.value = {};
    const outcome = await runAct(item, 'MOVE', payload);
    if (outcome.done) {
        moveOpen.value = false;
        return;
    }
    moveFieldErrors.value = outcome.fields;
    const known = ['boardId', 'prefixId', 'reason'];
    const other = Object.entries(outcome.fields)
        .filter(([k]) => !known.includes(k))
        .map(([, v]) => v);
    moveError.value = outcome.error || other.join(' ');
}

/* ── 줄의 ⋯ 메뉴 ── */

const rowMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const rowMenuTarget = ref<AdminPostItem | null>(null);
const rowMenuItems = computed<MenuItem[]>(() => {
    const item = rowMenuTarget.value;
    if (!item) return [];
    return UtilPost.actionList(actionsOf(item)).map((kind) => ({
        label: t(`post.action.${kind}`),
        icon: UtilPost.actionIcon(kind),
        class: UtilPost.isDanger(kind) ? 'post-menu-danger' : undefined,
        command: () => openAct(item, kind),
    }));
});

function openRowMenu(event: Event, item: AdminPostItem): void {
    rowMenuTarget.value = item;
    rowMenu.value?.toggle(event);
}

/* ── 글 서랍 ── */

const detail = usePostDetail();
const drawerOpen = ref(false);

/** 머리를 바로 그릴 글 — 받은 상세, 아직이면 지금 쪽의 줄 */
const drawerItem = computed<AdminPostItem | null>(() => {
    const id = detail.openId.value;
    if (!id) return null;
    return detail.detail.value?.post ?? items.value.find((item) => item.id === id) ?? null;
});
const neighbors = computed(() => UtilPost.neighbors(items.value, detail.openId.value));
const drawerActions = computed(() => (detail.detail.value ? actionsOf(detail.detail.value.post) : UtilPost.noActions()));

function rowClass(data: AdminPostItem): string {
    return drawerOpen.value && data.id === detail.openId.value ? 'post-row-open cursor-pointer' : 'cursor-pointer';
}

/** `?post=` 만 바꾼다(replace) — 목록은 다시 받지 않는다(필터 열쇠에 없다) */
function setPostQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilPost.toQuery(filters.value), ...(id ? { post: id } : {}) } });
}

function openPost(item: AdminPostItem): void {
    drawerOpen.value = true;
    if (detail.openId.value !== item.id) void detail.open(item.id);
    setPostQuery(item.id);
}

function onRowClick(e: { originalEvent: Event; data: AdminPostItem }): void {
    openPost(e.data);
}

function onDrawerAct(kind: PostActionKind): void {
    const item = detail.detail.value?.post;
    if (item) openAct(item, kind);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detail.close();
    debate.value = null;
    debateError.value = '';
}

// 서랍이 닫히면(×·Esc·바깥) 주소의 `post` 만 지운다(필터·쪽은 그대로)
watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilPost.parsePost(route.query) !== null) setPostQuery(null);
});

// 주소의 `post`(새로고침·뒤로 가기·알림 링크)를 따라 서랍을 연다
watch(
    () => UtilPost.parsePost(route.query),
    (id) => {
        if (id && (id !== detail.openId.value || !drawerOpen.value)) {
            drawerOpen.value = true;
            if (id !== detail.openId.value) void detail.open(id);
        } else if (!id && drawerOpen.value) {
            drawerOpen.value = false;
        }
    },
    { immediate: true },
);

/* ── 글의 찬반 토론 ── */

const debateSettingsActions = useDebateSettingsActions();
const opinionActions = useDebateOpinionActions();
const debateSettingsOpen = ref(false);
const opinionsOpen = ref(false);
/** 열린 토론(없으면 null) — 글이 `debate` 일 때만 읽는다 */
const debate = ref<DebateItem | null>(null);
const debateLoading = ref(false);
const debateError = ref('');
let debateTicket = 0;

async function loadDebate(silent = false): Promise<void> {
    const post = detail.detail.value?.post;
    const mine = ++debateTicket;
    if (!silent) {
        debate.value = null;
        debateError.value = '';
        debateLoading.value = false;
    }
    if (!post) return;
    if (!post.debate && !silent) return;
    debateLoading.value = true;
    try {
        const found = await debateSettingsActions.get(post.id);
        if (mine === debateTicket) {
            debate.value = found;
            debateError.value = '';
        }
    } catch (e) {
        if (mine !== debateTicket) return;
        const error = errorOf(e);
        if (error.code === 'DEBATE_NOT_FOUND') {
            debate.value = null;
            debateError.value = '';
        } else if (!silent) debateError.value = error.message;
    } finally {
        if (mine === debateTicket) debateLoading.value = false;
    }
}

// 글을 받으면(다른 글 · 토론 열림이 바뀜) 토론 요약을 읽는다
watch(
    () => [detail.detail.value?.post.id ?? null, detail.detail.value?.post.debate ?? null] as const,
    ([id], [prevId]) => void loadDebate(id === prevId && id !== null),
);

/** 토론 설정을 바꿨다 — 요약은 받은 한 줄로, 목록의 토론 칩·글은 조용히 새로 받는다 */
function onDebateChanged(item: DebateItem | null): void {
    debate.value = item;
    debateError.value = '';
    void load(true);
    void detail.reload();
}

const opinionsTarget = computed<DebateDrawerTarget | null>(() => {
    const id = detail.openId.value;
    if (!id) return null;
    return { postId: id, title: drawerItem.value?.title ?? `#${id}`, neutralEnabled: debate.value?.neutralEnabled ?? false, counts: null };
});

// 필터(검색·게시판·말머리·상태·정렬·쪽)가 바뀌면 선택을 비우고 다시 받는다. 처음 한 번도 여기서 받는다(`post` 는 열쇠에 없다).
// ⚠ 맨 끝에 둔다 — 바로 한 번 도는(`immediate`) 감시라 위의 상태(`selection` 등)가 모두 만들어진 뒤여야 한다
watch(
    () => UtilPost.key(filters.value),
    () => {
        selection.value = [];
        void load();
    },
    { immediate: true },
);
</script>

<style scoped>
/* 상태 탭 — 고른 탭을 강조색으로 짚는다(인기글 관리 기간 탭과 같은 모양) */
.post-tabs :deep(.p-togglebutton-checked) {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    border-color: var(--p-primary-color);
}

.post-tabs :deep(.p-togglebutton-content) {
    background: transparent;
    box-shadow: none;
}

/* 서랍에 열린 글의 줄 */
.post-table :deep(tr.post-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>

<style>
/* ⋯ 메뉴의 위험한 조치(숨기기·삭제) — 메뉴는 body 로 떠서 scoped 가 닿지 않는다 */
.p-menu .post-menu-danger .p-menu-item-label,
.p-menu .post-menu-danger .p-menu-item-icon {
    color: var(--p-red-600);
}
</style>
