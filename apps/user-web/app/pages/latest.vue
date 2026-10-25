<template>
    <div>
        <!-- 머리: 이름 · 한 줄 설명 — 정렬은 늘 최신순이라 토글이 없다 -->
        <header>
            <h1 class="break-words text-lg font-bold leading-tight">{{ name }}</h1>
            <p class="mt-0.5 text-xs text-muted-color">{{ t('feed.latestSubtitle') }}</p>
        </header>

        <!-- 결과 개수·더 있는지를 스크린리더가 읽는다. 안내 영역은 처음부터 있어야 바뀐 글을 읽어 준다 -->
        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <div class="mt-3" :aria-busy="status === 'loading'">
            <!-- 로딩 -->
            <div v-if="status === 'loading'" role="status" :aria-label="t('feed.loading', { name })"><FeedSkeleton /></div>

            <!-- 오류 -->
            <ErrorState v-else-if="status === 'error'" :title="t('feed.loadFailed', { name })" :description="errorMessage" retry @retry="load" />

            <template v-else>
                <!-- 비어 있음 -->
                <EmptyState v-if="items.length === 0" :title="t('feed.latestEmptyTitle')" :description="t('feed.latestEmptyText')" icon="pi pi-file">
                    <Button as="router-link" to="/boards" :label="t('feed.browseBoards')" />
                </EmptyState>

                <!-- 글 — 번호 없이 게시판 이름만 -->
                <template v-else>
                    <ul class="-mx-4 m-0 list-none p-0 md:mx-0">
                        <FeedPostItem v-for="row in items" :key="row.id" :row="row" :date-display="display.dateDisplay" :new-post-hours="display.newPostHours" />
                    </ul>

                    <!-- 커서로 이어 받는다. 실패해도 앞서 받은 목록은 그대로 두고 이 줄에서 다시 시도한다 -->
                    <div v-if="hasMore" class="py-4 text-center">
                        <Button type="button" :label="moreError ? t('common.retry') : t('common.more')" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="latest-more" @click="loadMore" />
                        <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ t('feed.moreFailed', { message: moreError }) }}</p>
                    </div>

                    <!-- 산업·주식 글이 섞여 있으면 투자 고지를 붙인다 -->
                    <BoardInvestmentNotice v-if="hasInvestmentBoard" class="mt-2" />
                </template>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilFeedMessages from '~/utils/util-feed-messages';
import UtilPopular from '~/utils/util-popular';

/**
 * 최신글 전체 — `/latest`(community `pages/latest.vue`, `docs/api-home.md`「최신글 전체」). 켜진 모든 게시판의 최신글을 「더 보기」(커서)로 이어 붙인다.
 * 응답이 보는 사람마다 달라질 수 있어 캐시하지 않고 들어올 때·보는 사람이 바뀔 때 새로 받는다. 이미 있는 글은 건너뛴다. 늦게 온 옛 결과는 요청 번호로 버린다.
 * API 는 `useHomeApi().latest`(`GET /api/home/latest`). 메뉴 BUILTIN `LATEST`(`/latest`)가 이곳이다. 제목은 서비스 이름 `LATEST`.
 */
const { t } = useI18n();
const api = useHomeApi();
const { user } = useAuth();
const { bySlug, ensureLoaded: ensureBoards } = useBoards();
const { term } = useSiteTerms();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();
void ensureBoards();

const name = computed(() => term('LATEST'));

const status = ref<'loading' | 'ready' | 'error'>('loading');
const items = ref<PostFeedRow[]>([]);
const nextCursor = ref<string | null>(null);
const hasNext = ref(false);
const errorMessage = ref('');
const loadingMore = ref(false);
const moreError = ref('');

const hasMore = computed(() => hasNext.value && nextCursor.value !== null);
const hasInvestmentBoard = computed(() => items.value.some((row) => bySlug(row.board.slug)?.investmentNotice));
const summary = computed(() => UtilFeedMessages.summary(t, name.value, status.value, items.value.length, hasMore.value));

let requestSeq = 0;
let abort: AbortController | null = null;

async function load() {
    const mine = ++requestSeq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    items.value = [];
    nextCursor.value = null;
    hasNext.value = false;
    moreError.value = '';
    loadingMore.value = false;
    status.value = 'loading';
    try {
        const res = await api.latest({ size: UtilPopular.LATEST_PAGE_SIZE }, controller.signal);
        if (mine !== requestSeq) return;
        items.value = UtilPopular.appendNew([], res.items);
        nextCursor.value = res.nextCursor;
        hasNext.value = res.hasNext;
        status.value = 'ready';
    } catch (e) {
        if (mine !== requestSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

async function loadMore() {
    const cursor = nextCursor.value;
    if (!cursor || loadingMore.value) return;
    const mine = requestSeq;
    loadingMore.value = true;
    moreError.value = '';
    try {
        const res = await api.latest({ size: UtilPopular.LATEST_PAGE_SIZE, cursor }, abort?.signal);
        if (mine !== requestSeq) return;
        items.value = UtilPopular.appendNew(items.value, res.items);
        nextCursor.value = res.nextCursor;
        hasNext.value = res.hasNext;
    } catch (e) {
        if (mine !== requestSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        moreError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mine === requestSeq) loadingMore.value = false;
    }
}

// 들어올 때 한 번, 그리고 보는 사람이 바뀔 때(로그인·로그아웃)
watch(
    () => user.value?.id ?? null,
    () => void load(),
    { immediate: true },
);
onBeforeUnmount(() => abort?.abort());

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => name.value, description: () => t('feed.latestSeoDescription', { site: siteLabel.value }), robots: 'noindex' });
</script>
