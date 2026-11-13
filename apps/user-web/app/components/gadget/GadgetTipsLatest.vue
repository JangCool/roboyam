<template>
    <!--
        팁·강좌·사용기 「최신 글」 가젯(`TIPS_LATEST` — `/tips` 배치에만, tips-contract 「/tips 레이아웃 편집」) — 사진 + 제목 + 요약 2줄 + 게시판·작성자·날짜, 쪽 번호(`?board=&page=`).
        데이터는 화면이 나눠 주는 `useTipsShared`(`GET /api/tips/posts`) — `/tips` 밖이면 그리지 않는다.
        모바일 줄(사용자 시안 확정 2026-11-12): 윗줄 = 게시판 이름(맨 왼쪽) · 말머리 → 썸네일 64×48(그림 표시만) + 제목 → 본문 미리보기(썸네일 아래 줄 폭 전체) →
        아랫줄(썸네일 밑까지 줄 폭 전체) = 아바타·닉네임 … 오른쪽 끝 추천 · 시간. 데스크톱은 `PostRowFrame` 그대로.
    -->
    <GadgetFrame v-if="tips" :gadget="gadget" :empty="false">
        <div class="@container min-w-0" :aria-busy="tips.listStatus.value === 'loading'" data-testid="tips-latest">
            <div v-if="tips.listStatus.value === 'loading'" role="status" :aria-label="t('tips.loading')"><FeedSkeleton :count="6" /></div>

            <ErrorState v-else-if="tips.listStatus.value === 'error'" :title="t('tips.loadFailed')" :description="tips.listError.value" retry @retry="tips.loadPosts" />

            <template v-else-if="list">
                <template v-if="list.items.length === 0">
                    <EmptyState v-if="tips.state.value.page > 1" :title="t('feed.pageEmptyTitle')" icon="pi pi-file">
                        <Button as="router-link" :to="tips.to({ board: tips.board.value, page: 1 })" :label="t('feed.firstPage')" />
                    </EmptyState>
                    <EmptyState v-else :title="t('tips.emptyTitle')" :description="t('tips.emptyText')" icon="pi pi-book" />
                </template>

                <template v-else>
                    <ul class="-mt-3 m-0 list-none p-0" data-testid="tips-latest-list">
                        <li v-for="row in list.items" :key="row.id" class="border-b border-surface last:border-b-0">
                            <FoldedRow :reason="tips.foldOf(row)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                                <!-- 칩·날짜 자리는 섞인 목록의 칩 위치(`listChipPosition`)를 따른다 — 이 목록은 원래 조회·추천 수를 보이지 않는다. 게시판 이름은 메타 줄 글자 그대로 -->
                                <NuxtLink :to="`/posts/${row.id}`" class="group block py-3.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="tips-latest-row">
                                    <div class="md:hidden" data-testid="tips-latest-row-mobile">
                                        <PostRowChips :row="row" general class="mb-1.5">
                                            <span class="shrink-0 text-xs font-semibold" :class="tips.boardStyle(row) ? '' : 'text-color'" :style="tips.boardStyle(row)">{{ tips.boardName(row) }}</span>
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
                                            <!-- 시간은 닉네임 바로 오른쪽, 추천은 오른쪽 끝(사용자 2026-11-12 — 게시판 목록과 같게) -->
                                            <span aria-hidden="true">·</span>
                                            <PostListDate :iso="row.createdAt" :mode="display.dateDisplay" class="shrink-0" />
                                            <PostRowStats :row="row" :views="false" class="ml-auto" />
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
                                                <span class="font-semibold" :class="tips.boardStyle(row) ? '' : 'text-color'" :style="tips.boardStyle(row)">{{ tips.boardName(row) }}</span>
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

                    <BoardPager :page="list.page" :total-pages="list.totalPages" :to="(n: number) => tips!.to({ board: tips!.board.value, page: n })" class="pb-0" />
                </template>
            </template>
        </div>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';

interface Props {
    gadget: Gadget;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const tips = useTipsShared();
const { display, ensureLoaded } = useBoardDisplay();
void ensureLoaded();

const list = computed(() => tips?.list.value ?? null);
</script>
