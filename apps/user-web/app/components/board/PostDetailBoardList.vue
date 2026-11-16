<template>
    <!--
        글 상세 아래 게시판 목록(post-detail-board-list-contract) — 댓글 카드 다음 카드. 머리 「{게시판 이름} 글 목록」 → 목록 → 쪽 번호(`BoardPager`).
        목록 부품은 게시판 목록 화면과 같은 고르기(`useBoardListView` — 테마 `postList:<유형>`·layout·보기 전환 기기 선택·핫딜). 지금 글은 강조(`activeId` — `post-row-current`·`aria-current`).
        `COMPACT` 는 휴대폰(md 미만)에서만 촘촘한 한 줄(제목·댓글 수·날짜), PC 는 `LIST` 와 같다. 전체 공지(다른 게시판 글)는 그리지 않고, 고정 공지는 서버가 1쪽일 때만 준다.
        화면이 카드 근처(위아래 600px)에 오면 `near` 를 한 번 올린다 — 받기는 페이지가 컴포저블(`usePostDetailBoardList`)로 한다. 그리기만 한다.
    -->
    <section ref="root" class="content-card scroll-mt-24" :aria-labelledby="headingId" :aria-busy="state === 'loading'" data-testid="post-detail-board-list" :data-mode="mode">
        <!-- 머리 줄 — 위아래 여백·글자는 댓글 카드 머리(`CommentSection` — 위 14 · 아래 12 · 16px 굵게)와 같다(2차 — 사용자 2026-11-16 「머리 줄이 답답하다」) -->
        <div class="border-b border-surface pb-3 pt-3.5">
            <h2 :id="headingId" class="m-0 text-base font-semibold">{{ t('board.detailListTitle', { name: title }) }}</h2>
        </div>

        <div v-if="state === 'idle' || (state === 'loading' && !list)" class="-mx-4 mt-1.5 md:mx-0" role="status" :aria-label="t('board.loadingList')">
            <div v-for="n in 5" :key="n" class="border-b border-surface px-4 py-3 md:px-0">
                <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-2/3'" />
            </div>
        </div>

        <ErrorState v-else-if="state === 'error'" :title="t('board.listFailed')" :description="errorMessage" retry @retry="emit('retry')" />

        <EmptyState v-else-if="list && list.items.length === 0 && list.pinned.length === 0" :title="t('board.emptyTitle')" icon="pi pi-file" />

        <!-- 머리 줄과 첫 줄(지금 글이면 옅은 주 색상 바탕) 사이를 띄운다 -->
        <div v-else-if="list" class="mt-1.5" :class="state === 'loading' ? 'opacity-60 transition-opacity' : ''">
            <!-- 휴대폰 촘촘한 줄(`COMPACT`) — 제목 · [댓글 수] · 날짜 한 줄 -->
            <ul v-if="compactRows" class="-mx-4 m-0 list-none p-0" data-testid="post-detail-board-list-compact">
                <li
                    v-for="row in compactAll"
                    :key="`${pinnedIds.has(row.id) ? 'p' : ''}${row.id}`"
                    class="border-b border-surface"
                    :class="row.id === activeId ? 'post-row-current' : pinnedIds.has(row.id) ? 'bg-surface-100 dark:bg-surface-800/60' : ''"
                >
                    <p v-if="row.hiddenReason" class="m-0 flex items-center gap-1.5 px-4 py-2.5 text-sm text-muted-color">
                        <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                    </p>
                    <FoldedRow v-else :reason="overrides.blockedOf(row.author.memberId, row.blocked)" kind="post" class="px-4 py-2.5 text-sm" hide-class="px-4 pb-2">
                        <NuxtLink
                            :to="UtilPost.link(row.id, linkQuery)"
                            :aria-current="row.id === activeId ? 'page' : undefined"
                            class="flex items-center gap-2 px-4 py-2.5 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                        >
                            <span class="min-w-0 flex-1 truncate" :class="row.id === activeId ? 'font-semibold' : ''">
                                <PostRowTitle :row="row" :new-post-hours="newPostHours" :chips="false" />
                            </span>
                            <span v-if="row.id === activeId" class="sr-only">{{ t('board.detailListCurrent') }}</span>
                            <PostListDate :iso="row.createdAt" :mode="dateDisplay" class="shrink-0 text-xs text-muted-color tabular-nums" />
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ul>
            <component
                :is="listComponent"
                v-else
                v-bind="listExtra"
                :rows="list.items"
                :pinned="list.pinned"
                :global-pinned="[]"
                :date-display="dateDisplay"
                :new-post-hours="newPostHours"
                :chip-position="chipPosition"
                :link-query="linkQuery"
                :active-id="activeId"
            />
            <BoardPager :page="list.page" :total-pages="list.totalPages" :to="pageTo" />
        </div>
    </section>
</template>

<script setup lang="ts">
import type { LocationQueryRaw, RouteLocationRaw } from 'vue-router';
import type { BoardDateDisplay, BoardDetail, BoardDetailList } from '~/composables/board/api/useBoardApi.types';
import type { PostDetailBoardListPage, PostDetailBoardListState } from '~/composables/board/usePostDetailBoardList';
import UtilPost from '~/utils/util-post';

interface Props {
    /** 그 글의 게시판(소모임 글이면 소모임 게시판 정보) — 목록 부품 고르기·말머리 모양 */
    board: BoardDetail;
    /** 머리의 이름(게시판 이름 — 소모임 글이면 소모임 이름) */
    title: string;
    /** `LIST` · `COMPACT`(`OFF` 면 페이지가 그리지 않는다) */
    mode: BoardDetailList;
    state: PostDetailBoardListState;
    list: PostDetailBoardListPage | null;
    errorMessage?: string;
    /** 목록의 글 링크에 붙일 문맥 쿼리(쪽 포함) */
    linkQuery: LocationQueryRaw;
    /** 지금 보는 글 */
    activeId: string;
    /** 쪽 번호 주소 — 지금 글 그대로, 목록만 그 쪽 */
    pageTo: (page: number) => RouteLocationRaw;
    dateDisplay: BoardDateDisplay;
    newPostHours: number;
}

const props = withDefaults(defineProps<Props>(), { errorMessage: '' });
const emit = defineEmits<{ (e: 'near'): void; (e: 'retry'): void }>();

const { t } = useI18n();
const headingId = useId();
const desktop = useIsDesktop();
const overrides = useBlockOverrides();

const { listComponent, listExtra, chipPosition } = useBoardListView(() => props.board);
/** 글 줄의 말머리 모양 — 게시판 정보의 `prefixStyle`(게시판 목록 화면과 같다) */
providePrefixStyle(() => props.board.prefixStyle);

/** 휴대폰 촘촘한 줄로 그리는가 — `COMPACT` 이고 md 미만 */
const compactRows = computed(() => props.mode === 'COMPACT' && !desktop.value);
const pinnedIds = computed(() => new Set((props.list?.pinned ?? []).map((p) => p.id)));
const compactAll = computed(() => [...(props.list?.pinned ?? []), ...(props.list?.items ?? [])]);

/* ── 화면이 근처에 오면 한 번 알린다(본문 속도에 영향 없게 — 계약) ── */
const root = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | null = null;
onMounted(() => {
    const el = root.value;
    if (!el) return;
    if (typeof IntersectionObserver === 'undefined') {
        emit('near');
        return;
    }
    observer = new IntersectionObserver(
        (entries) => {
            if (!entries.some((e) => e.isIntersecting)) return;
            observer?.disconnect();
            observer = null;
            emit('near');
        },
        { rootMargin: '600px 0px' },
    );
    observer.observe(el);
});
onBeforeUnmount(() => observer?.disconnect());

/** 쪽을 바꿨을 때 — 목록 카드 맨 위로(창 위에 붙는 막대 아래 — `scroll-mt-24`) */
function scrollToTop(): void {
    root.value?.scrollIntoView({ block: 'start' });
}

defineExpose({ scrollToTop });
</script>
