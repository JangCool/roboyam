<template>
    <!--
        공지 관리 — 공개 사이트 공지 게시판(slug `notice`)의 글을 쓰고·고치고·지우고·고정한다. 새 저장소가 아니라 그 게시판 글이다.
        서버 호출은 `useNoticeApi`. 계약 `docs/features/notice-admin-contract.md`(화면 확정 2026-11-07).
        - 머리: [새 공지](`post.manage` — 내 운영자 회원 계정이 없거나 멈췄으면 꺼짐 + 까닭 툴팁).
        - 요약 상자: 공지 게시판(이름·켜짐·고정 n/한도 · [게시판 설정] `/boards/notice` · [공개 사이트에서 보기]) · 작성 계정(내 운영자 회원 계정 닉네임, 없으면 경고 띠) · 공지 띠(설정 > 일반 `#notice-bar`).
          공지 게시판이 없거나 꺼져 있으면 목록 대신 안내 + [게시판 관리로].
        - 조회 줄: 제목 검색(「검색」·Enter) · 상태(전체/공개/숨김/삭제) · 말머리. **주소 쿼리** `?q=&state=&prefix=&page=`(조건을 바꾸면 1쪽으로 replace, 쪽은 push).
        - 표: 고정(고정 글이 먼저) · 번호 · 말머리 · 제목 · 작성자(운영자 회원 계정 닉네임 + 운영자 이름) · 상태 알약(`PostStateTag`) · 조회/댓글 · 작성일 · ⋯ 메뉴.
          ⋯: 수정 · 고정/고정 해제(한도가 차면 꺼짐) · 삭제(확인창 — 사유 없음) · 게시글 관리에서 보기(`/posts?post=` — 조치 기록·숨김·복구는 거기서) · 공개 사이트에서 보기.
          지운 글 줄은 흐리고 ⋯ 에 「게시글 관리에서 복구」. 줄·제목을 누르면 수정 화면(`/notices/{id}`).
        - 권한: 보기 `post.read`(메뉴) · 쓰기·고치기·지우기·고정 `post.manage`(없으면 읽기 전용 안내, 새 공지·조치 없음). 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notice.title')" :subtitle="t('notice.subtitle')">
            <template v-if="canManage && boardReady" #actions>
                <span v-tooltip.bottom="newBlockedReason || undefined" class="inline-flex">
                    <Button type="button" icon="pi pi-plus" :label="t('notice.new')" size="small" :disabled="!!newBlockedReason" @click="router.push('/notices/new')" />
                </span>
            </template>
        </PageTitle>

        <!-- 컨텍스트(게시판·작성 계정·공지 띠) -->
        <div v-if="contextLoading && !context" class="grid gap-3 md:grid-cols-3" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 3" :key="n" height="5.5rem" />
        </div>
        <ErrorState v-else-if="contextError" :title="t('notice.contextFailed')" :description="contextError.message" retry :retrying="contextLoading" @retry="loadContext()" />
        <EmptyState v-else-if="context && !boardReady" icon="pi pi-megaphone" :title="context.board ? t('notice.boardOffTitle') : t('notice.boardMissingTitle')" :description="context.board ? t('notice.boardOffHint') : t('notice.boardMissingHint')">
            <Button as="router-link" :to="context.board ? boardSettingsPath : '/boards'" :label="t('notice.toBoards')" size="small" severity="secondary" variant="outlined" />
        </EmptyState>

        <template v-else-if="context && context.board">
            <!-- 요약 상자 -->
            <section class="grid gap-3 md:grid-cols-3" :aria-label="t('notice.summaryLabel')">
                <div class="flex min-w-0 flex-col gap-1.5 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
                    <p class="m-0 text-xs text-muted-color">{{ t('notice.boardLabel') }}</p>
                    <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1 text-[13px]">
                        <b>{{ localizedName(context.board.name, context.board.nameEn) }}</b>
                        <span class="text-muted-color">({{ context.board.slug }})</span>
                        <Tag :value="t('notice.boardOn')" severity="success" class="text-[11px]!" />
                        <span class="inline-flex items-center gap-1 tabular-nums" :aria-label="t('notice.pinnedLabel', { n: context.board.pinnedCount, max: context.board.pinnedMax })">
                            <i class="pi pi-thumbtack text-[11px] text-muted-color" aria-hidden="true" />{{ t('notice.pinnedCount', { n: context.board.pinnedCount, max: context.board.pinnedMax }) }}
                        </span>
                    </p>
                    <p class="m-0 flex flex-wrap gap-x-3 gap-y-1 text-xs">
                        <NuxtLink :to="boardSettingsPath" class="font-medium text-primary hover:underline">{{ t('notice.boardSettings') }}</NuxtLink>
                        <a v-if="context.board.publicUrl" :href="context.board.publicUrl" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1 font-medium text-primary hover:underline">
                            {{ t('notice.viewPublic') }}<i class="pi pi-external-link text-[10px]" aria-hidden="true" />
                        </a>
                    </p>
                </div>

                <div class="flex min-w-0 flex-col gap-1.5 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
                    <p class="m-0 text-xs text-muted-color">{{ t('notice.writerLabel') }}</p>
                    <i18n-t v-if="canWrite" keypath="notice.writerLine" tag="p" scope="global" class="m-0 text-[13px]">
                        <template #nickname><b>{{ context.writer.nickname }}</b></template>
                    </i18n-t>
                    <p v-else class="m-0 flex items-center gap-1.5 text-[13px] text-amber-700 dark:text-amber-400">
                        <i class="pi pi-exclamation-triangle text-[12px]" aria-hidden="true" />{{ t(context.writer.state === 'PAUSED' ? 'notice.writerPaused' : 'notice.writerNone') }}
                    </p>
                    <NuxtLink to="/settings/operators" class="self-start text-xs font-medium text-primary hover:underline">{{ t('notice.writerManage') }}</NuxtLink>
                </div>

                <div class="flex min-w-0 flex-col gap-1.5 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
                    <p class="m-0 text-xs text-muted-color">{{ t('notice.barLabel') }}</p>
                    <p class="m-0 text-[13px]">{{ noticeBarText }}</p>
                    <NuxtLink to="/settings/general#notice-bar" class="self-start text-xs font-medium text-primary hover:underline">{{ t('notice.barSettings') }}</NuxtLink>
                </div>
            </section>

            <NoticeWriterWarning v-if="canManage" :writer="context.writer" />

            <!-- 조회 줄 -->
            <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('notice.searchLabel')" novalidate @submit.prevent="submitSearch">
                <div class="flex flex-wrap items-center gap-2">
                    <InputText v-model="searchText" type="search" :maxlength="UtilNotice.SEARCH_MAX" :placeholder="t('notice.searchPlaceholder')" :aria-label="t('notice.searchLabel')" autocomplete="off" class="min-w-0 flex-1 basis-56" />
                    <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                    <Button v-if="hasConditions" type="button" :label="t('notice.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                </div>
                <FilterRow :label="t('notice.stateLabel')">
                    <template #default="{ labelId }">
                        <FilterOptionGroup :model-value="filters.state" :options="stateOptions" :labelledby="labelId" @update:model-value="(v) => setState(v as PostStateFilter)" />
                    </template>
                </FilterRow>
                <FilterRow :label="t('notice.prefixLabel')" :label-for="`${uid}-prefix`">
                    <Select :model-value="filters.prefix || ALL" :input-id="`${uid}-prefix`" :options="prefixOptions" option-label="label" option-value="value" class="w-48" @update:model-value="setPrefix" />
                </FilterRow>
            </form>

            <div class="flex min-w-0 flex-col gap-2">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <p v-if="view" class="m-0 text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('notice.total', { count: view.total.toLocaleString(locale) }) }}</p>
                    <p class="m-0 text-xs text-muted-color">{{ t('notice.sortHint') }}</p>
                    <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                        <i class="pi pi-lock" aria-hidden="true" />{{ t('notice.readOnly') }}
                    </p>
                </div>

                <div class="notice-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                    <DataTable
                        :value="items"
                        data-key="id"
                        lazy
                        paginator
                        :first="(filters.page - 1) * UtilNotice.PAGE_SIZE"
                        :rows="UtilNotice.PAGE_SIZE"
                        :total-records="view?.total ?? 0"
                        :loading="busy"
                        :size="prefs.density === 'compact' ? 'small' : undefined"
                        row-hover
                        scrollable
                        paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink"
                        :row-class="rowClass"
                        class="min-w-0"
                        @page="onPage"
                        @row-click="onRowClick"
                    >
                        <template #empty>
                            <ErrorState v-if="loadError" :title="t('notice.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                            <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                            <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                                <Button type="button" :label="t('notice.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                            </EmptyState>
                            <EmptyState v-else icon="pi pi-megaphone" :title="t('notice.empty')" :description="t('notice.emptyHint')">
                                <Button v-if="canManage && !newBlockedReason" type="button" icon="pi pi-plus" :label="t('notice.new')" size="small" @click="router.push('/notices/new')" />
                            </EmptyState>
                        </template>

                        <Column style="width: 2.5rem">
                            <template #header><span class="sr-only">{{ t('notice.col.pinned') }}</span></template>
                            <template #body="{ data }">
                                <i v-if="data.pinned" v-tooltip.top="t('notice.pinnedTip')" class="pi pi-thumbtack text-[13px] text-primary" role="img" :aria-label="t('notice.pinnedTip')" />
                            </template>
                        </Column>

                        <Column :header="t('notice.col.id')" style="min-width: 5rem">
                            <template #body="{ data }">
                                <span class="text-[12px] tabular-nums text-muted-color">{{ data.id }}</span>
                            </template>
                        </Column>

                        <Column :header="t('notice.col.prefix')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <Tag v-if="data.prefix" :value="data.prefix.name" severity="secondary" class="text-[11px]!" />
                                <span v-else class="text-[12px] text-muted-color">—</span>
                            </template>
                        </Column>

                        <Column :header="t('notice.col.title')" style="min-width: 18rem">
                            <template #body="{ data }">
                                <button
                                    type="button"
                                    class="min-w-0 text-left text-[13px] font-medium break-words [overflow-wrap:anywhere] hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                    :class="data.state === 'DELETED' ? 'text-muted-color line-through' : ''"
                                    @click.stop="openEdit(data)"
                                >
                                    {{ data.title }}
                                </button>
                            </template>
                        </Column>

                        <Column :header="t('notice.col.author')" style="min-width: 9rem">
                            <template #body="{ data }">
                                <span class="flex min-w-0 flex-col">
                                    <span class="truncate text-[13px]">{{ data.author.name }}</span>
                                    <span v-if="data.author.operatorName" class="truncate text-[11px] text-muted-color">{{ t('notice.operatorName', { name: data.author.operatorName }) }}</span>
                                </span>
                            </template>
                        </Column>

                        <Column :header="t('notice.col.state')" style="min-width: 8.5rem">
                            <template #body="{ data }">
                                <PostStateTag :item="data" lines />
                            </template>
                        </Column>

                        <Column :header="t('notice.col.stats')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <span class="inline-flex items-center gap-2 text-[12px] tabular-nums text-muted-color" :aria-label="t('notice.stats', { views: data.viewCount, comments: data.commentCount })">
                                    <span><i class="pi pi-eye mr-0.5 text-[10px]" aria-hidden="true" />{{ data.viewCount }}</span>
                                    <span><i class="pi pi-comment mr-0.5 text-[10px]" aria-hidden="true" />{{ data.commentCount }}</span>
                                </span>
                            </template>
                        </Column>

                        <Column :header="t('notice.col.createdAt')" style="min-width: 7.5rem">
                            <template #body="{ data }">
                                <time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time>
                            </template>
                        </Column>

                        <Column style="width: 3rem">
                            <template #header><span class="sr-only">{{ t('notice.col.actions') }}</span></template>
                            <template #body="{ data }">
                                <Button
                                    type="button"
                                    icon="pi pi-ellipsis-h"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    rounded
                                    :disabled="acting"
                                    :aria-label="t('notice.actionsMenu', { title: data.title })"
                                    aria-haspopup="menu"
                                    @click.stop="openRowMenu($event, data)"
                                />
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </div>
        </template>

        <Menu ref="rowMenu" :model="rowMenuItems" popup />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { ApiError } from '~/composables/common/useApiClient';
import type { NoticeContext, NoticeItem, NoticeListView } from '~/composables/notice/api/useNoticeApi.types';
import type { PostStateFilter } from '~/composables/post/api/usePostApi.types';
import UtilNotice, { type NoticeFilters } from '~/utils/util-notice';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('notice.title') });

const api = useNoticeApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const { confirm } = useConfirmDialog();
const uid = useId();

/** 권한(정의: util-permission-catalog.ts) — 보기 `post.read`(메뉴), 쓰기·고치기·지우기·고정 `post.manage` */
const canManage = computed(() => can('post.manage'));

const LIST_PATH = '/notices';
/** 「전체」 선택지의 값 — 빈 글자는 Select 가 "고르지 않음"으로 읽는다 */
const ALL = '__all__';

/* ── 컨텍스트(공지 게시판 · 작성 계정 · 공지 띠) ── */

const context = ref<NoticeContext | null>(null);
const contextLoading = ref(false);
const contextError = ref<ApiError | null>(null);

/** 공지 게시판이 있고 켜져 있다 — 아니면 목록 대신 안내 */
const boardReady = computed(() => !!context.value?.board?.active);
const canWrite = computed(() => UtilNotice.canWrite(context.value?.writer));
/** 게시판 설정(관리자 게시판 상세 — 주소는 slug) */
const boardSettingsPath = computed(() => `/boards/${encodeURIComponent(context.value?.board?.slug ?? 'notice')}`);
const pinFull = computed(() => {
    const b = context.value?.board;
    return !!b && b.pinnedCount >= b.pinnedMax;
});
/** [새 공지]를 막는 까닭(툴팁) — 없으면 빈 글자 */
const newBlockedReason = computed(() => (context.value && !canWrite.value ? t('notice.writerMissing') : ''));

const noticeBarText = computed(() => {
    const bar = context.value?.noticeBar;
    if (!bar) return '';
    if (!bar.enabled) return t('notice.barOff');
    return bar.source === 'BOARD' ? t('notice.barBoard', { n: bar.boardCount }) : t('notice.barCustom');
});

async function loadContext(silent = false): Promise<void> {
    contextLoading.value = true;
    if (!silent) contextError.value = null;
    try {
        context.value = await api.context();
        contextError.value = null;
    } catch (e) {
        if (!silent || !context.value) contextError.value = errorOf(e);
    } finally {
        contextLoading.value = false;
    }
}

/* ── 주소 ↔ 필터 ── */

const filters = computed<NoticeFilters>(() => UtilNotice.parse(route.query));
const hasConditions = computed(() => UtilNotice.hasConditions(filters.value));

function apply(next: NoticeFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilNotice.key(next) === UtilNotice.key(filters.value)) return;
    void router[how]({ path: LIST_PATH, query: UtilNotice.toQuery(next) });
}

function setState(state: PostStateFilter): void {
    apply(UtilNotice.change(filters.value, { state }));
}
function setPrefix(value: string): void {
    apply(UtilNotice.change(filters.value, { prefix: value === ALL ? '' : value }));
}
function onPage(e: { page: number }): void {
    apply(UtilNotice.change(filters.value, { page: e.page + 1 }), 'push');
}
function resetFilters(): void {
    searchText.value = '';
    apply(UtilNotice.emptyFilters());
}

/** 검색어는 「검색」(Enter)을 눌러야 주소로 간다 */
const searchText = ref(filters.value.q);
watch(
    () => filters.value.q,
    (q) => {
        searchText.value = q;
    },
);
function submitSearch(): void {
    const next = UtilNotice.change(filters.value, { q: searchText.value.trim() });
    if (UtilNotice.key(next) === UtilNotice.key(filters.value)) void load();
    else apply(next);
}

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const stateOptions = computed(() => UtilNotice.STATE_FILTERS.map((value) => ({ value, label: t(`post.state.${value}`) })));
const prefixOptions = computed(() => {
    const list = [{ value: ALL, label: t('notice.prefixAll') }, ...(context.value?.board?.prefixes ?? []).map((p) => ({ value: p.id, label: p.name }))];
    const id = filters.value.prefix;
    if (id && !list.some((o) => o.value === id)) list.push({ value: id, label: `#${id}` });
    return list;
});

/* ── 목록 ── */

const view = ref<NoticeListView | null>(null);
const items = computed(() => view.value?.items ?? []);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

let seq = 0;
let abort: AbortController | null = null;
onBeforeUnmount(() => {
    seq++;
    abort?.abort();
});

async function load(silent = false): Promise<void> {
    if (!boardReady.value) return;
    const mine = ++seq;
    abort?.abort();
    abort = new AbortController();
    busy.value = !silent;
    if (!silent) loadError.value = null;
    try {
        const found = await api.list(UtilNotice.toApi(filters.value), abort.signal);
        if (mine !== seq) return;
        view.value = found;
        loadError.value = null;
        // 건수는 있는데 이 쪽이 비었다(마지막 쪽의 글을 지움) — 마지막 쪽으로
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilNotice.change(filters.value, { page: UtilNotice.pageCount(found.total, found.size || UtilNotice.PAGE_SIZE) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        if (error.code === 'NOTICE_BOARD_MISSING') {
            // 그 사이 게시판이 꺼졌거나 없어졌다 — 안내로 바꾼다
            void loadContext(true);
            return;
        }
        if (silent && view.value) toast.error(t('notice.refreshFailed', { reason: error.message }), 8000);
        else {
            view.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) busy.value = false;
    }
}

function rowClass(data: NoticeItem): string {
    return data.state === 'DELETED' ? 'notice-row-deleted cursor-pointer' : 'cursor-pointer';
}

function openEdit(item: NoticeItem): void {
    void router.push(`/notices/${encodeURIComponent(item.id)}`);
}
function onRowClick(e: { data: NoticeItem }): void {
    openEdit(e.data);
}

/* ── 조치(고정·삭제) ── */

const acting = ref(false);

async function afterAct(): Promise<void> {
    await Promise.all([load(true), loadContext(true)]);
}

async function togglePin(item: NoticeItem): Promise<void> {
    if (acting.value) return;
    acting.value = true;
    try {
        if (item.pinned) await api.unpin(item.id);
        else await api.pin(item.id);
        toast.success(t(item.pinned ? 'notice.unpinned' : 'notice.pinned'));
    } catch (e) {
        // `PIN_LIMIT`·`POST_STATE_INVALID` 등 — 서버 문구 그대로
        toast.error(t('notice.actFailed', { reason: errorOf(e).message }), 8000);
    } finally {
        acting.value = false;
    }
    await afterAct();
}

async function remove(item: NoticeItem): Promise<void> {
    if (acting.value) return;
    const ok = await confirm({ title: t('notice.deleteTitle'), message: t('notice.deleteMessage'), confirmLabel: t('notice.delete'), tone: 'danger' });
    if (!ok) return;
    acting.value = true;
    try {
        await api.remove(item.id);
        toast.success(t('notice.deleted'));
    } catch (e) {
        toast.error(t('notice.actFailed', { reason: errorOf(e).message }), 8000);
    } finally {
        acting.value = false;
    }
    await afterAct();
}

function openPublic(item: NoticeItem): void {
    if (item.publicUrl) window.open(item.publicUrl, '_blank', 'noopener,noreferrer');
}

/* ── 줄의 ⋯ 메뉴 ── */

const rowMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const rowMenuTarget = ref<NoticeItem | null>(null);
const rowMenuItems = computed<MenuItem[]>(() => {
    const item = rowMenuTarget.value;
    if (!item) return [];
    const deleted = item.state === 'DELETED';
    const list: MenuItem[] = [];
    if (!deleted) list.push({ label: t(canManage.value ? 'notice.edit' : 'notice.view'), icon: canManage.value ? 'pi pi-pencil' : 'pi pi-eye', command: () => openEdit(item) });
    if (canManage.value && !deleted) {
        if (item.pinned) list.push({ label: t('notice.unpin'), icon: 'pi pi-times', command: () => void togglePin(item) });
        else if (item.state === 'PUBLIC') list.push({ label: pinFull.value ? t('notice.pinFull', { max: context.value?.board?.pinnedMax ?? 0 }) : t('notice.pin'), icon: 'pi pi-thumbtack', disabled: pinFull.value, command: () => void togglePin(item) });
        list.push({ label: t('notice.delete'), icon: 'pi pi-trash', class: 'notice-menu-danger', command: () => void remove(item) });
    }
    list.push({ separator: true });
    list.push({ label: t(deleted ? 'notice.restoreInPosts' : 'notice.viewInPosts'), icon: deleted ? 'pi pi-replay' : 'pi pi-file-edit', command: () => void router.push({ path: '/posts', query: { post: item.id } }) });
    if (item.state === 'PUBLIC' && item.publicUrl) list.push({ label: t('notice.viewPublic'), icon: 'pi pi-external-link', command: () => openPublic(item) });
    return list;
});

function openRowMenu(event: Event, item: NoticeItem): void {
    rowMenuTarget.value = item;
    rowMenu.value?.toggle(event);
}

onMounted(async () => {
    await loadContext();
    void load();
});

// 필터(검색·상태·말머리·쪽)가 바뀌면 다시 받는다 — 처음 한 번은 컨텍스트를 받은 뒤 위에서 받는다
watch(
    () => UtilNotice.key(filters.value),
    () => void load(),
);
</script>

<style scoped>
/* 지운 공지 줄 — 흐리게 */
.notice-table :deep(tr.notice-row-deleted) {
    opacity: 0.6;
}
</style>

<style>
/* ⋯ 메뉴의 삭제 — 메뉴는 body 로 떠서 scoped 가 닿지 않는다 */
.p-menu .notice-menu-danger .p-menu-item-label,
.p-menu .notice-menu-danger .p-menu-item-icon {
    color: var(--p-red-600);
}
</style>
