<template>
    <!-- 머리·탭·목록·쪽 번호를 한 흰 카드에 — 인기글(`/popular`)·묶음 메뉴 화면과 같은 카드(`gadget-card` 머리 없음)·같은 제목 머리(`PageTitleHead`) -->
    <div class="gadget-card" data-headless data-testid="achu-card">
        <!-- 머리: 「아추!」 로고 글꼴 그림(글자는 화면 읽기용) · 고른 기간이 무엇을 보여 주는지 한 줄 설명 -->
        <PageTitleHead :title="t('achu.name')" :description="UtilAchuMessages.subtitle(t, period)" divider title-testid="achu-title">
            <template #title>
                <span class="inline-flex items-center">
                    <svg :viewBox="`0 0 ${art.w} ${art.h}`" :style="{ height: `${art.em}em`, width: 'auto' }" class="block fill-current overflow-visible" aria-hidden="true" focusable="false"><path :d="art.d" stroke="currentColor" :stroke-width="art.sw ?? 0" stroke-linejoin="round" /></svg>
                    <span class="sr-only">{{ t('achu.name') }} — {{ t('achu.fullName') }}</span>
                </span>
            </template>
        </PageTitleHead>

        <!-- 기간 탭 — 주소의 `?period=` 와 같이 움직인다(누르면 주소가 바뀌고 1쪽으로). 고른 탭만 Tab 키로 닿고, 화살표·Home·End 로 초점을 옮긴 뒤 Enter·Space 로 고른다 -->
        <div class="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1">
            <div role="tablist" :aria-label="t('achu.periodTabs', { name: t('achu.name') })" class="flex shrink-0 gap-3.5" data-testid="achu-period-tabs" @keydown="onTabKeydown">
                <button
                    v-for="p in UtilAchu.PERIODS"
                    :id="UtilAchu.tabId(uid, p)"
                    :key="p"
                    type="button"
                    role="tab"
                    :data-period="p"
                    :aria-selected="p === period"
                    aria-controls="achu-list"
                    :tabindex="p === period ? 0 : -1"
                    :class="[TAB, p === period ? TAB_ON : TAB_OFF]"
                    @click="changePeriod(p)"
                >
                    {{ UtilAchuMessages.periodLabel(t, p) }}
                </button>
            </div>
            <!-- 그 기간의 범위(오늘 날짜 · 이번 주 · 이번 달) — 서버가 서비스 시간대 달력으로 정해 준 값 -->
            <p v-if="rangeNote" class="m-0 text-xs text-muted-color" data-testid="achu-range">{{ rangeNote }}</p>
        </div>

        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <div id="achu-list" class="mt-2" role="tabpanel" :aria-labelledby="UtilAchu.tabId(uid, period)" :aria-busy="status === 'loading'">
            <div v-if="status === 'loading'" role="status" :aria-label="t('achu.loading', { name: periodName })"><FeedSkeleton /></div>

            <ErrorState v-else-if="status === 'error'" :title="t('achu.loadFailed', { name: periodName })" :description="errorMessage" retry @retry="load" />

            <template v-else-if="result">
                <template v-if="items.length === 0">
                    <EmptyState v-if="page > 1" :title="t('feed.pageEmptyTitle')" icon="pi pi-file">
                        <Button as="router-link" :to="pageLink(1)" :label="t('feed.firstPage')" />
                    </EmptyState>
                    <EmptyState v-else :title="empty.title" :description="empty.text" icon="pi pi-star">
                        <!-- 오늘 고른 글이 없으면 이번 주로 이어 준다 -->
                        <Button v-if="period === 'DAY'" as="router-link" :to="{ path: '/achu', query: UtilAchu.build(1, 'WEEK') }" :label="t('achu.seeWeek')" />
                        <Button v-else as="router-link" to="/boards" :label="t('feed.browseBoards')" severity="secondary" />
                    </EmptyState>
                </template>

                <template v-else>
                    <!--
                        글 줄 — 홈 「아추!」 가젯 모바일 목록형 줄과 같은 틀(사용자 2026-11-19): 줄마다 선 · 위아래 10px · 왼쪽 사진 칸(64 × 48, 4:3) +
                        제목 한 줄(살짝 굵게 500 · 댓글 수) + 정보 줄(게시판 · 시간 …… 추천). 사진 = 운영자가 올린 대표 이미지 → 글 썸네일, 없으면 게시판 목록 이모지 → 게시판 색 옅은 면 + 첫 글자(`GadgetThumb`, 홈 목록형과 같다)
                    -->
                    <ul class="m-0 list-none p-0" data-testid="achu-rows">
                        <li v-for="h in items" :key="h.id" class="border-b border-surface last:border-b-0">
                            <FoldedRow :reason="foldOf(h)" kind="post" class="py-2.5 text-sm" hide-class="pb-2">
                                <NuxtLink :to="`/posts/${h.post.id}`" class="group flex items-start gap-3 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="achu-row">
                                    <GadgetThumb :src="imageOf(h)" :variant="variantOf(h)" ratio="4 / 3" :letter="UtilAchu.letterOf(boardName(h))" :letter-color="h.post.board.color" :emoji="h.post.board.emoji ?? null" class="w-16 shrink-0" />
                                    <div class="min-w-0 flex-1">
                                        <p class="m-0 line-clamp-1 break-words text-[0.9375rem] font-medium leading-snug group-hover:underline">
                                            <span>{{ h.title ?? h.post.title }}</span>
                                            <span v-if="h.post.commentCount > 0" class="ml-1 text-xs font-semibold text-primary">
                                                <span class="sr-only">{{ t('home.commentCount', { n: h.post.commentCount }) }}</span><span aria-hidden="true">{{ h.post.commentCount }}</span>
                                            </span>
                                        </p>
                                        <p class="m-0 mt-1.5 flex items-center gap-x-1 text-xs text-muted-color">
                                            <span class="min-w-0 truncate">{{ boardName(h) }}</span>
                                            <span aria-hidden="true">·</span>
                                            <PostListDate :iso="h.post.createdAt" :mode="display.dateDisplay" class="shrink-0" />
                                            <PostRowStats :row="h.post" :views="false" class="ml-auto" />
                                        </p>
                                    </div>
                                </NuxtLink>
                            </FoldedRow>
                        </li>
                    </ul>

                    <BoardPager :page="result.page" :total-pages="result.totalPages" :to="pageLink" />
                </template>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AchuItem, AchuListResponse, AchuPeriod } from '~/composables/highlight/api/useHighlightApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAchu from '~/utils/util-achu';
import UtilAchuMessages from '~/utils/util-achu-messages';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 아추!(아이로빗 추천) — `/achu?period=&page=`(`docs/features/achu-page-contract.md`). 운영자가 하이라이트로 **고른 날** 기준 오늘·이번 주·이번 달의 글을 쪽 번호로 본다.
 * 인기글(`/popular`, 반응 순위)과 따로다 — 인기글 페이지는 그대로 둔다. 주소의 `period`·`page` 가 화면 상태다(틀린 값은 일간·1쪽, 기본값은 주소에서 뺀다).
 * API 는 `useHighlightApi().list`(`GET /api/highlights` — 홈 하이라이트, 고른 날 = 노출 시작 또는 만든 날). 메뉴 BUILTIN `ACHU`(`/achu`)가 이곳이다.
 */
const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useHighlightApi();
const { user } = useAuth();
const { localizedName } = useLocalizedName();
const overrides = useBlockOverrides();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();

const uid = useId();
const art = UtilAchu.TITLE_ART;
const page = computed(() => UtilAchu.parsePage(route.query));
const period = computed<AchuPeriod>(() => UtilAchu.parsePeriod(route.query));
const periodName = computed(() => UtilAchuMessages.periodLabel(t, period.value));

const TAB = 'relative cursor-pointer whitespace-nowrap inline-flex min-h-8 items-center border-b-2 text-[0.8125rem] leading-none after:absolute after:inset-x-0 after:-inset-y-2 after:content-[""] focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary';
const TAB_ON = 'border-primary font-bold text-color';
const TAB_OFF = 'border-transparent text-muted-color hover:text-color';

const status = ref<'loading' | 'ready' | 'error'>('loading');
const result = ref<AchuListResponse | null>(null);
const errorMessage = ref('');

const items = computed<AchuItem[]>(() => result.value?.items ?? []);
/** 탭 옆 범위 — 받은 응답이 보는 기간의 것일 때만 */
const rangeNote = computed(() => (result.value?.period === period.value ? UtilAchuMessages.rangeNote(t, period.value, result.value) : ''));
const empty = computed(() => UtilAchuMessages.empty(t, period.value));
const summary = computed(() => UtilAchuMessages.summary(t, periodName.value, status.value, result.value?.totalCount ?? 0));

/** 대표 이미지 — 운영자가 올린 것, 없으면 글 썸네일 */
function imageOf(h: AchuItem): string | null {
    return h.imageUrl ?? h.post.thumbnailUrl;
}

function boardName(h: AchuItem): string {
    return localizedName(h.post.board.name, h.post.board.nameEn);
}

/** 사진이 없을 때 빈 썸네일 모양 — 뉴스 게시판 글은 뉴스 면(이모지·글자가 없을 때만 쓰인다) */
function variantOf(h: AchuItem): 'news' | 'default' {
    return UtilBoard.isNews(h.post.board?.slug) ? 'news' : 'default';
}

function foldOf(h: AchuItem) {
    return overrides.blockedOf(h.post.author.memberId, h.post.blocked);
}

function pageLink(target: number) {
    return { path: '/achu', query: UtilAchu.build(target, period.value) };
}

function changePeriod(next: AchuPeriod) {
    if (next === period.value) return;
    void router.push({ path: '/achu', query: UtilAchu.build(1, next) });
}

/** 화살표·Home·End 로 초점만 옮긴다(눌러야 목록을 받으므로 초점만으로는 고르지 않는다) */
function onTabKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement | null;
    const raw = target?.closest<HTMLElement>('[data-period]')?.dataset.period;
    const current = UtilAchu.PERIODS.find((p) => p === raw);
    if (!current) return;
    const next = UtilAchu.movedTo(current, event.key);
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
        const res = await api.list({ period: period.value, page: page.value, size: UtilAchu.PAGE_SIZE }, controller.signal);
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
    title: () => UtilAchuMessages.seoTitle(t, period.value, page.value),
    description: () => t('achu.seoDescription'),
});
</script>
