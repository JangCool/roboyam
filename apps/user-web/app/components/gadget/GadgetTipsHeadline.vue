<template>
    <!--
        팁·강좌·사용기 「이번 주 추천」 가젯(`TIPS_HEADLINE` — `/tips` 배치에만, tips-contract 「/tips 레이아웃 편집」) — 큰 카드 1 + 작은 카드 4(홈 하이라이트와 같은 크기, 사용자 2026-11-07).
        `/tips` 화면이 보이는 첫 이 가젯을 제목 카드 안 굵은 밑줄 아래에 넣는다(편집 중이면 격자에 그대로). 데이터는 화면이 나눠 주는 `useTipsShared`(머리 몫 `GET /api/tips`) — `/tips` 밖이면 그리지 않는다.
        칸 나누기는 가젯 폭(`@container`): 넓으면(36rem~) 큰 카드 왼쪽 · 작은 카드 오른쪽 세로 목록, 좁으면 위아래 + 작은 카드는 옆으로 민다(`relative` — 안의 절대 위치 글자가 문서를 넓히지 않게).
    -->
    <GadgetFrame v-if="tips" :gadget="gadget" :empty="false">
        <div class="@container min-w-0" :aria-busy="tips.overviewStatus.value === 'loading'" data-testid="tips-headline">
            <div v-if="tips.overviewStatus.value === 'loading'" class="grid gap-4 @min-[36rem]:grid-cols-[minmax(0,23.5rem)_minmax(0,1fr)] @min-[36rem]:gap-x-7" aria-hidden="true">
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

            <ErrorState v-else-if="tips.overviewStatus.value === 'error'" :title="t('tips.loadFailed')" :description="tips.overviewError.value" retry @retry="tips.loadOverview" />

            <EmptyState v-else-if="!lead" :title="t('tips.emptyTitle')" :description="t('tips.emptyText')" icon="pi pi-star" />

            <div v-else class="grid gap-4 @min-[36rem]:grid-cols-[minmax(0,23.5rem)_minmax(0,1fr)] @min-[36rem]:gap-x-7">
                <!-- 큰 카드 -->
                <FoldedRow :reason="tips.foldOf(lead)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${lead.id}`" class="group block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="tips-lead">
                        <!-- 가젯 머리가 이미 「이번 주 추천」이라 사진 위 꼬리표는 두지 않는다 -->
                        <GadgetThumb :src="lead.thumbnailUrl" ratio="16 / 9" class="w-full" />
                        <p class="mt-3.5 line-clamp-2 break-words text-lg font-bold leading-snug tracking-tight group-hover:underline">{{ lead.title }}</p>
                        <p v-if="lead.excerpt" class="mt-1.5 line-clamp-1 text-sm text-muted-color" data-testid="tips-lead-excerpt">{{ lead.excerpt }}</p>
                        <p class="mt-2 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                            <span class="font-semibold" :class="tips.boardStyle(lead) ? '' : 'text-color'" :style="tips.boardStyle(lead)">{{ tips.boardName(lead) }}</span>
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
                    :aria-label="t('home.scrollRowLabel', { name: gadget.title || t('tips.headlineTitle') })"
                    data-testid="tips-side"
                >
                    <li v-for="row in side" :key="row.id" class="w-[68%] shrink-0 snap-start @min-[36rem]:w-auto @min-[36rem]:border-b @min-[36rem]:border-surface @min-[36rem]:first:-mt-3 @min-[36rem]:last:border-b-0">
                        <FoldedRow :reason="tips.foldOf(row)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                            <NuxtLink
                                :to="`/posts/${row.id}`"
                                class="group flex flex-col gap-2 rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary @min-[36rem]:flex-row @min-[36rem]:items-center @min-[36rem]:gap-3.5 @min-[36rem]:py-2.5"
                                data-testid="tips-side-card"
                            >
                                <GadgetThumb :src="row.thumbnailUrl" ratio="16 / 10" class="w-full @min-[36rem]:w-20 @min-[36rem]:shrink-0" />
                                <div class="min-w-0 flex-1">
                                    <p class="line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug group-hover:underline">{{ row.title }}</p>
                                    <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                        <span class="font-semibold" :class="tips.boardStyle(row) ? '' : 'text-color'" :style="tips.boardStyle(row)">{{ tips.boardName(row) }}</span>
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

const lead = computed(() => tips?.headline.value[0] ?? null);
const side = computed(() => tips?.headline.value.slice(1, 5) ?? []);
</script>
