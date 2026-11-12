<template>
    <!--
        팁·강좌·사용기 묶음 `/tips?board=&page=`(매거진형 — 사용자가 시안 B 를 고름, 2026-11-07 화면 확정·서버 연결). 게시판 두 개(팁과강좌·사용기)를 한 화면에 묶는다.
        영역마다 따로 떨어진 카드(홈·가젯 페이지와 같은 `gadget-card` · 카드 사이 `gadget-stack` 간격 · 머리 = 아이콘 + 제목 + 오른쪽 자리, 모양은 가젯 틀 `GadgetFrame` 을 따른다):
          · 카드 1 — 매거진 머리(페이지 제목·설명 + 오른쪽 게시판 칩, 굵은 밑줄 — 제목을 따로 떼지 않는다, 사용자 2026-11-07) 아래 「이번 주 추천」:
            게시판 칩은 전체 · 묶음의 게시판들(링크라 `?board=` 만 바뀌고 1쪽으로). 큰 카드 1 + 작은 카드 4 — 홈 하이라이트와 같은 크기(고르는 기준은 서버 몫 — 최근 7일 추천순).
          · 카드 2 「최신 글」 — 사진 + 제목 + 요약 2줄 + 게시판·작성자·날짜, 쪽 번호.
          · 카드 3 「많이 본 글」 1~5 — 넓으면 카드 2 오른쪽 좁은 칸, 좁으면 카드 2 아래.
          · 카드 4 「태그」 — 카드 3 아래(같은 칸). 칩을 누르면 `/search?q=태그`(태그 거르기가 없어 임시).
        칸 나누기는 화면 폭이 아니라 카드·본문 폭(`@container`)으로 한다 — 사이드 메뉴로 본문이 좁을 때도 맞게. 가로로 미는 상자에는 `relative` 를 둔다(안의 절대 위치 글자가 빠져나와 문서를 넓히지 않게).
        서버 `GET /api/tips`·`GET /api/tips/posts`(`useTipsApi`). ⚠ MOCK: 태그만 서버에 없어 지어낸 값이다(`composables/tips/api/mock/tips-mock.ts`).
    -->
    <div class="gadget-stack @container" data-testid="tips-page">
        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <!-- 카드 1 — 매거진 머리: 페이지 제목·설명 + 오른쪽 게시판 칩(굵은 밑줄로 지면 머리처럼), 그 아래 「이번 주 추천」 헤드라인 -->
        <section class="gadget-card flex flex-col" data-headless :aria-labelledby="headlineId" :aria-busy="overviewStatus === 'loading'" data-testid="tips-headline">
            <!-- 제목 머리 — 묶음 메뉴 화면 공용(`PageTitleHead` — 커뮤니티·핫 로토픽·소모임과 같은 글자 크기·여백) -->
            <PageTitleHead :title="t('tips.title')" :description="t('tips.subtitle')" divider>
                <template #actions>
                <nav :aria-label="t('tips.boardTabs')" class="relative flex min-w-0 shrink-0 gap-1.5 overflow-x-auto py-1 [scrollbar-width:none]" data-testid="tips-board-tabs">
                    <template v-if="boards">
                        <NuxtLink
                            v-for="chip in chips"
                            :key="chip.slug || 'all'"
                            :to="to({ board: chip.slug, page: 1 })"
                            :class="chipClass(chip.slug === board)"
                            :aria-current="chip.slug === board ? 'page' : undefined"
                            :data-testid="`tips-tab-${chip.slug || 'all'}`"
                        >
                            <span class="relative top-[-0.09em]">{{ chip.label }}</span>
                        </NuxtLink>
                    </template>
                    <template v-else-if="overviewStatus === 'loading'">
                        <i v-for="n in 3" :key="n" class="h-7 w-14 shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" aria-hidden="true" />
                    </template>
                </nav>
                </template>
            </PageTitleHead>

            <h2 :id="headlineId" class="m-0 mb-3 mt-4 flex items-center gap-1.5 text-[0.96875rem] font-bold">
                <i class="pi pi-star gadget-icon shrink-0 text-sm" aria-hidden="true" />{{ t('tips.headlineTitle') }}
            </h2>

            <div class="@container min-w-0">
                <div v-if="overviewStatus === 'loading'" class="grid gap-4 @min-[36rem]:grid-cols-[minmax(0,23.5rem)_minmax(0,1fr)] @min-[36rem]:gap-x-7" aria-hidden="true">
                    <div>
                        <div class="aspect-video w-full animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                        <div class="mt-3 h-5 w-4/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                        <div class="mt-2 h-3.5 w-3/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    </div>
                    <div class="hidden flex-col gap-4 @min-[36rem]:flex">
                        <div v-for="n in 4" :key="n" class="flex gap-3.5">
                            <div class="aspect-[16/10] w-20 shrink-0 animate-pulse rounded-md bg-surface-200 dark:bg-surface-800" />
                            <div class="flex-1">
                                <div class="h-4 w-11/12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                                <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                        </div>
                    </div>
                </div>

                <ErrorState v-else-if="overviewStatus === 'error'" :title="t('tips.loadFailed')" :description="overviewError" retry @retry="loadOverview" />

                <EmptyState v-else-if="headline.length === 0" :title="t('tips.emptyTitle')" :description="t('tips.emptyText')" icon="pi pi-star" />

                <div v-else class="grid gap-4 @min-[36rem]:grid-cols-[minmax(0,23.5rem)_minmax(0,1fr)] @min-[36rem]:gap-x-7">
                    <!-- 큰 카드 -->
                    <FoldedRow v-if="lead" :reason="foldOf(lead)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${lead.id}`" class="group block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="tips-lead">
                            <!-- 카드 머리가 이미 「이번 주 추천」이라 사진 위 꼬리표는 두지 않는다 -->
                            <GadgetThumb :src="lead.thumbnailUrl" ratio="16 / 9" class="w-full" />
                            <p class="mt-3.5 line-clamp-2 break-words text-lg font-bold leading-snug tracking-tight group-hover:underline">{{ lead.title }}</p>
                            <p v-if="lead.excerpt" class="mt-1.5 line-clamp-1 text-sm text-muted-color" data-testid="tips-lead-excerpt">{{ lead.excerpt }}</p>
                            <p class="mt-2 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                <span class="font-semibold" :class="boardStyle(lead) ? '' : 'text-color'" :style="boardStyle(lead)">{{ boardName(lead) }}</span>
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.likesShort', { n: lead.likeCount }) }}</span>
                                <span aria-hidden="true">·</span>
                                <span>{{ t('home.commentCount', { n: lead.commentCount }) }}</span>
                            </p>
                        </NuxtLink>
                    </FoldedRow>

                    <!-- 작은 카드 4 — 좁으면 옆으로 밀어 넘기는 줄(사진 위, 카드 안쪽 좌우 여백 끝까지), 넓으면 세로 목록(사진 왼쪽) -->
                    <ul
                        v-if="side.length"
                        class="relative m-0 mx-[calc(-1*var(--gadget-card-pad-x))] flex list-none snap-x snap-mandatory scroll-px-(--gadget-card-pad-x) gap-3 overflow-x-auto px-(--gadget-card-pad-x) pb-1 [scrollbar-width:none] @min-[36rem]:mx-0 @min-[36rem]:flex-col @min-[36rem]:gap-0 @min-[36rem]:overflow-visible @min-[36rem]:px-0 @min-[36rem]:pb-0"
                        :aria-label="t('home.scrollRowLabel', { name: t('tips.headlineTitle') })"
                        data-testid="tips-side"
                    >
                        <li v-for="row in side" :key="row.id" class="w-[68%] shrink-0 snap-start @min-[36rem]:w-auto @min-[36rem]:border-b @min-[36rem]:border-surface @min-[36rem]:first:-mt-3 @min-[36rem]:last:border-b-0">
                            <FoldedRow :reason="foldOf(row)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                                <NuxtLink
                                    :to="`/posts/${row.id}`"
                                    class="group flex flex-col gap-2 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary @min-[36rem]:flex-row @min-[36rem]:items-center @min-[36rem]:gap-3.5 @min-[36rem]:py-2.5"
                                    data-testid="tips-side-card"
                                >
                                    <GadgetThumb :src="row.thumbnailUrl" ratio="16 / 10" class="w-full @min-[36rem]:w-20 @min-[36rem]:shrink-0" />
                                    <div class="min-w-0 flex-1">
                                        <p class="line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug group-hover:underline">{{ row.title }}</p>
                                        <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                            <span class="font-semibold" :class="boardStyle(row) ? '' : 'text-color'" :style="boardStyle(row)">{{ boardName(row) }}</span>
                                            <span aria-hidden="true">·</span>
                                            <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                        </p>
                                    </div>
                                </NuxtLink>
                            </FoldedRow>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <!-- 카드 2·3 — 넓으면 최신 글 왼쪽 · 많이 본 글 오른쪽 좁은 칸, 좁으면 위아래. 많이 본 글을 못 받았으면(머리 몫 오류 — 카드 1 이 알린다) 최신 글만 -->
        <div class="grid items-start gap-(--gadget-gap)" :class="showMostViewed ? '@min-[40rem]:grid-cols-[minmax(0,1fr)_14rem]' : ''">
            <!-- 카드 2 — 최신 글 -->
            <section class="gadget-card flex min-w-0 flex-col" :data-look="look" :aria-labelledby="latestId" :aria-busy="listStatus === 'loading'" data-testid="tips-latest">
                <div :class="headClass">
                    <i class="pi pi-clock gadget-icon shrink-0 text-sm" aria-hidden="true" />
                    <h2 :id="latestId" class="m-0 min-w-0 truncate font-bold" :class="titleClass">{{ t('tips.latestTitle') }}</h2>
                </div>

                <div class="@container min-w-0" :class="bodyClass">
                    <div v-if="listStatus === 'loading'" role="status" :aria-label="t('tips.loading')"><FeedSkeleton :count="6" /></div>

                    <ErrorState v-else-if="listStatus === 'error'" :title="t('tips.loadFailed')" :description="listError" retry @retry="loadPosts" />

                    <template v-else-if="list">
                        <template v-if="list.items.length === 0">
                            <EmptyState v-if="state.page > 1" :title="t('feed.pageEmptyTitle')" icon="pi pi-file">
                                <Button as="router-link" :to="to({ board, page: 1 })" :label="t('feed.firstPage')" />
                            </EmptyState>
                            <EmptyState v-else :title="t('tips.emptyTitle')" :description="t('tips.emptyText')" icon="pi pi-book" />
                        </template>

                        <template v-else>
                            <ul class="-mt-3 m-0 list-none p-0" data-testid="tips-latest-list">
                                <li v-for="row in list.items" :key="row.id" class="border-b border-surface last:border-b-0">
                                    <FoldedRow :reason="foldOf(row)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                                        <!-- 칩·날짜 자리는 섞인 목록의 칩 위치(`listChipPosition`)를 따른다 — 이 목록은 원래 조회·추천 수를 보이지 않는다. 게시판 이름은 메타 줄 글자 그대로 -->
                                        <NuxtLink :to="`/posts/${row.id}`" class="group block py-3.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="tips-latest-row">
                                            <!--
                                                모바일(사용자 시안 확정 2026-11-12): 윗줄 = 게시판 이름(맨 왼쪽) · 말머리 → 썸네일 64×48(그림 표시만) + 제목 → 본문 미리보기(썸네일 아래 줄 폭 전체) →
                                                아랫줄(썸네일 밑까지 줄 폭 전체) = 아바타·닉네임 … 오른쪽 끝 추천 · 시간. 데스크톱은 아래 `PostRowFrame` 그대로
                                            -->
                                            <div class="md:hidden" data-testid="tips-latest-row-mobile">
                                                <PostRowChips :row="row" general class="mb-1.5">
                                                    <span class="shrink-0 text-xs font-semibold" :class="boardStyle(row) ? '' : 'text-color'" :style="boardStyle(row)">{{ boardName(row) }}</span>
                                                    <span class="text-xs text-muted-color" aria-hidden="true">·</span>
                                                </PostRowChips>
                                                <div class="flex items-start gap-2.5">
                                                    <GadgetThumb :src="row.thumbnailUrl" ratio="4 / 3" :label="false" class="w-16 shrink-0" />
                                                    <p class="m-0 line-clamp-2 min-w-0 flex-1 break-words text-[0.9375rem] font-semibold leading-snug group-hover:underline">
                                                        <PostRowTitle :row="row" :new-post-hours="display.newPostHours" :pinned-mark="false" :chips="false" />
                                                    </p>
                                                </div>
                                                <!-- 본문 미리보기 — 썸네일 아래 줄 폭 전체(사용자 2026-11-12) -->
                                                <p v-if="row.excerpt" class="m-0 mt-1.5 line-clamp-2 text-sm leading-relaxed text-muted-color">{{ row.excerpt }}</p>
                                                <p class="mt-2 flex items-center gap-x-1.5 text-xs text-muted-color">
                                                    <PostAuthorLabel :author="row.author" avatar menu class="min-w-0" />
                                                    <!-- 오른쪽 끝 — 추천 · 시간(사용자 2026-11-12 「추천수는 나와야지」) -->
                                                    <span class="ml-auto inline-flex shrink-0 items-center gap-1.5">
                                                        <PostRowStats :row="row" :views="false" />
                                                        <span aria-hidden="true">·</span>
                                                        <PostListDate :iso="row.createdAt" :mode="display.dateDisplay" />
                                                    </span>
                                                </p>
                                            </div>
                                            <PostRowFrame :row="row" :position="display.listChipPosition" :views="false" :likes="false" row-class="items-start gap-3.5" class="max-md:hidden">
                                                <template #start>
                                                    <GadgetThumb :src="row.thumbnailUrl" ratio="4 / 3" class="w-24 shrink-0 @min-[36rem]:w-36" />
                                                </template>
                                                <template #default="{ chips, stats, dateInline }">
                                                    <p class="line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug group-hover:underline">
                                                        <PostRowTitle :row="row" :new-post-hours="display.newPostHours" :pinned-mark="false" :chips="chips" />
                                                    </p>
                                                    <p v-if="row.excerpt" class="mt-1 line-clamp-2 text-sm leading-relaxed text-muted-color" data-testid="tips-latest-excerpt">{{ row.excerpt }}</p>
                                                    <p class="mt-1.5 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                                        <span class="font-semibold" :class="boardStyle(row) ? '' : 'text-color'" :style="boardStyle(row)">{{ boardName(row) }}</span>
                                                        <span aria-hidden="true">·</span>
                                                        <PostAuthorLabel :author="row.author" menu />
                                                        <template v-if="stats">
                                                            <span aria-hidden="true">·</span>
                                                            <PostListDate :iso="row.createdAt" :mode="display.dateDisplay" />
                                                        </template>
                                                        <!-- 「제목 위」 — 날짜만 닉네임(등급) 바로 뒤에(조회·추천은 윗줄 오른쪽 끝) -->
                                                        <template v-if="dateInline">
                                                            <span aria-hidden="true">·</span>
                                                            <PostListDate :iso="row.createdAt" :mode="display.dateDisplay" />
                                                        </template>
                                                    </p>
                                                </template>
                                            </PostRowFrame>
                                        </NuxtLink>
                                    </FoldedRow>
                                </li>
                            </ul>

                            <BoardPager :page="list.page" :total-pages="list.totalPages" :to="(n: number) => to({ board, page: n })" class="pb-0" />
                        </template>
                    </template>
                </div>
            </section>

            <!-- 오른쪽 좁은 칸(좁으면 최신 글 아래) — 카드 3 많이 본 글 · 카드 4 태그 -->
            <div v-if="showMostViewed" class="flex min-w-0 flex-col gap-(--gadget-gap)" data-testid="tips-aside">
                <!-- 카드 3 — 많이 본 글 1~5 -->
                <section class="gadget-card flex min-w-0 flex-col" :data-look="look" :aria-labelledby="mostViewedId" :aria-busy="overviewStatus === 'loading'" data-testid="tips-most-viewed">
                    <div :class="headClass">
                        <i class="pi pi-eye gadget-icon shrink-0 text-sm" aria-hidden="true" />
                        <h2 :id="mostViewedId" class="m-0 min-w-0 truncate font-bold" :class="titleClass">{{ t('tips.mostViewedTitle') }}</h2>
                    </div>

                    <div class="min-w-0" :class="bodyClass">
                        <div v-if="overviewStatus === 'loading'" class="flex flex-col gap-3 py-1" aria-hidden="true">
                            <div v-for="n in 5" :key="n" class="h-4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-11/12' : 'w-3/4'" />
                        </div>
                        <p v-else-if="mostViewed.length === 0" class="m-0 py-2 text-sm text-muted-color">{{ t('tips.mostViewedEmpty') }}</p>
                        <ol v-else class="-mt-2.5 m-0 list-none p-0">
                            <li v-for="(row, i) in mostViewed" :key="row.id" class="flex items-start gap-2.5 border-b border-surface last:border-b-0">
                                <span class="w-4 shrink-0 pt-2.5 text-sm font-bold leading-snug tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">
                                    <span aria-hidden="true">{{ i + 1 }}</span><span class="sr-only">{{ t('feed.rank', { n: i + 1 }) }}</span>
                                </span>
                                <FoldedRow :reason="foldOf(row)" kind="post" class="min-w-0 flex-1 py-2.5 text-sm" hide-class="pb-2">
                                    <NuxtLink :to="`/posts/${row.id}`" class="group block min-w-0 flex-1 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="tips-most-viewed-row">
                                        <p class="line-clamp-2 break-words text-sm leading-snug group-hover:underline">{{ row.title }}</p>
                                        <p class="mt-0.5 text-xs text-muted-color">{{ t('board.viewsShort', { n: row.viewCount }) }}</p>
                                    </NuxtLink>
                                </FoldedRow>
                            </li>
                        </ol>
                    </div>
                </section>

                <!-- 카드 4 — 태그(⚠ MOCK: 지어낸 태그). 칩을 누르면 그 말로 검색(태그 거르기가 없어 임시) -->
                <section v-if="overviewStatus === 'loading' || tags.length" class="gadget-card flex min-w-0 flex-col" :data-look="look" :aria-labelledby="tagsId" :aria-busy="overviewStatus === 'loading'" data-testid="tips-tags">
                    <div :class="headClass">
                        <i class="pi pi-hashtag gadget-icon shrink-0 text-sm" aria-hidden="true" />
                        <h2 :id="tagsId" class="m-0 min-w-0 truncate font-bold" :class="titleClass">{{ t('tips.tagsTitle') }}</h2>
                    </div>

                    <div class="min-w-0" :class="bodyClass">
                        <div v-if="overviewStatus === 'loading'" class="flex flex-wrap gap-2" aria-hidden="true">
                            <i v-for="n in 6" :key="n" class="h-7 w-16 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
                        </div>
                        <ul v-else class="m-0 flex list-none flex-wrap gap-2 p-0">
                            <li v-for="tag in tags" :key="tag.name">
                                <NuxtLink
                                    :to="{ path: '/search', query: UtilSearchQuery.build({ q: tag.name }) }"
                                    class="inline-flex min-h-7 items-center justify-center rounded-full border border-hairline-strong px-3 text-xs leading-none text-color hover:border-(--p-text-color) focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                                    :aria-label="t('tips.tagSearch', { name: tag.name })"
                                    data-testid="tips-tag"
                                >
                                    <span class="relative top-[-0.09em]">#{{ tag.name }}</span>
                                </NuxtLink>
                            </li>
                        </ul>
                    </div>
                </section>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { TipsOverview, TipsPost, TipsPostPage } from '~/composables/tips/api/useTipsApi.types';
import type { TipsState } from '~/utils/util-tips';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilSearchQuery from '~/utils/util-search-query';
import UtilTips from '~/utils/util-tips';

/**
 * 팁·강좌·사용기 묶음 화면 — 주소의 `board`·`page` 가 화면의 상태다(틀린 값은 전체·1쪽, 기본값은 주소에서 뺀다).
 * 머리 몫(칩·헤드라인·많이 본 글)은 게시판이나 보는 사람이 바뀔 때, 최신 글은 쪽이 바뀔 때도 새로 받는다. 늦게 온 옛 결과는 요청 번호로 버린다.
 * API 는 `useTipsApi`(`GET /api/tips`·`GET /api/tips/posts` — 태그만 ⚠ MOCK).
 */
const { t } = useI18n();
const route = useRoute();
const api = useTipsApi();
const { user } = useAuth();
const { theme } = useTheme();
const { localizedName } = useLocalizedName();
const overrides = useBlockOverrides();
const { display, ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();

const headlineId = useId();
const latestId = useId();
const mostViewedId = useId();
const tagsId = useId();

/**
 * 카드 머리 모양 — 가젯 틀(`GadgetFrame`)의 두 모양과 같은 클래스. 고르는 짝도 홈 템플릿과 같다(기본 테마 `DefaultHome` = 기본형 `classic`, 그 밖 테마 = 카드형 `card`).
 * 기본형은 머리 아래 선 + 본문 위 여백, 카드형은 높이 44·48 머리 줄.
 */
const look = computed<'classic' | 'card'>(() => (theme.value === 'default' ? 'classic' : 'card'));
const headClass = computed(() => ['gadget-head flex min-w-0 items-center gap-1.5', look.value === 'card' ? 'min-h-11 md:min-h-12' : 'border-b border-surface pb-2'].join(' '));
const titleClass = computed(() => (look.value === 'card' ? 'text-base' : 'text-sm'));
const bodyClass = computed(() => (look.value === 'card' ? 'pt-1' : 'pt-3'));

const state = computed<TipsState>(() => UtilTips.parse(route.query));

const overviewStatus = ref<'loading' | 'ready' | 'error'>('loading');
const overview = ref<TipsOverview | null>(null);
const overviewError = ref('');
const listStatus = ref<'loading' | 'ready' | 'error'>('loading');
const list = ref<TipsPostPage | null>(null);
const listError = ref('');

const boards = computed(() => overview.value?.boards ?? null);
/** 지금 고른 게시판 — 묶음에 없는 slug 는 전체 */
const board = computed(() => UtilTips.knownBoard(state.value.board, boards.value));
const chips = computed(() => [{ slug: '', label: t('common.all') }, ...(boards.value ?? []).map((b) => ({ slug: b.slug, label: localizedName(b.name, b.nameEn) }))]);

const headline = computed(() => overview.value?.headline ?? []);
const lead = computed(() => headline.value[0] ?? null);
const side = computed(() => headline.value.slice(1, 5));
const mostViewed = computed(() => overview.value?.mostViewed ?? []);
const tags = computed(() => overview.value?.tags ?? []);
/** 많이 본 글 카드 — 머리 몫을 못 받았으면 뺀다(오류는 카드 1 이 한 번만 알린다) */
const showMostViewed = computed(() => overviewStatus.value !== 'error');

const summary = computed(() => {
    if (listStatus.value === 'loading') return t('tips.loading');
    if (listStatus.value === 'error') return t('tips.loadFailed');
    return t('tips.summary', { count: list.value?.items.length ?? 0 });
});

function to(next: TipsState) {
    return { path: UtilTips.PATH, query: UtilTips.build(next) };
}

function chipClass(active: boolean): string {
    return [
        'inline-flex min-h-7 shrink-0 items-center justify-center rounded-full border px-3 text-xs font-normal leading-none whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
        active ? 'border-(--p-text-color) bg-(--p-text-color) font-semibold text-(--p-content-background)' : 'border-hairline-strong text-color hover:border-(--p-text-color)',
    ].join(' ');
}

function boardName(row: TipsPost): string {
    return localizedName(row.board.name, row.board.nameEn);
}

function boardStyle(row: TipsPost) {
    return UtilBoard.textStyle(row.board.color);
}

/** 접는 까닭 — 차단한 회원·단어의 글이면 접힌 줄(게시판 글 목록과 같다) */
function foldOf(row: TipsPost) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}

let overviewSeq = 0;
let overviewAbort: AbortController | null = null;
let listSeq = 0;
let listAbort: AbortController | null = null;

async function loadOverview() {
    const mine = ++overviewSeq;
    overviewAbort?.abort();
    const controller = new AbortController();
    overviewAbort = controller;
    overviewStatus.value = 'loading';
    try {
        const res = await api.overview(state.value.board, controller.signal);
        if (mine !== overviewSeq) return;
        overview.value = res;
        overviewStatus.value = 'ready';
    } catch (e) {
        if (mine !== overviewSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        overviewError.value = UtilBoardMessages.of(t, e);
        overviewStatus.value = 'error';
    }
}

async function loadPosts() {
    const mine = ++listSeq;
    listAbort?.abort();
    const controller = new AbortController();
    listAbort = controller;
    list.value = null;
    listStatus.value = 'loading';
    try {
        const res = await api.posts({ board: state.value.board, page: state.value.page }, controller.signal);
        if (mine !== listSeq) return;
        list.value = res;
        listStatus.value = 'ready';
    } catch (e) {
        if (mine !== listSeq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        listError.value = UtilBoardMessages.of(t, e);
        listStatus.value = 'error';
    }
}

// 게시판·보는 사람이 바뀌면 머리 몫도, 쪽만 바뀌면 최신 글만 새로 받는다. 쪽을 바꾸면 맨 위로
watch(
    () => [state.value.board, state.value.page, user.value?.id ?? null] as const,
    (now, before) => {
        if (!before || now[0] !== before[0] || now[2] !== before[2]) void loadOverview();
        void loadPosts();
        if (before && now[1] !== before[1] && typeof window !== 'undefined') window.scrollTo({ top: 0 });
    },
    { immediate: true },
);
onBeforeUnmount(() => {
    overviewAbort?.abort();
    listAbort?.abort();
});

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('tips.title'), description: () => t('tips.seoDescription', { site: siteLabel.value }) });
</script>
