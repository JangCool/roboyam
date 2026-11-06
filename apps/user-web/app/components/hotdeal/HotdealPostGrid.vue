<template>
    <!--
        핫딜 게시판 카드형(CARD)·갤러리형(GALLERY) — 일반 카드·갤러리와 같은 격자에 가격 줄(몰 이름 · 할인율 · 딜가 · 정상가 · 배송비 — 갤러리는 「배송비 모름」 생략)과 배지를 붙인다. 메타 줄은 추천 · 조회 · 시각. 끝난 딜은 흐리게.
        카드: 모바일 1열 · 데스크톱 2열(썸네일 왼쪽). 갤러리: 모바일 2열 · 데스크톱 4열(정사각 썸네일). 한 줄 광고는 카드형만(두 열을 가로지르는 한 줄), 갤러리형은 그리지 않는다.
    -->
    <div>
        <BoardPinnedList :rows="pinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 mt-3 grid list-none p-0" :class="variant === 'CARD' ? 'grid-cols-1 gap-3 md:grid-cols-2' : 'grid-cols-2 gap-x-3 gap-y-4 md:grid-cols-4'" data-testid="hotdeal-grid">
            <template v-for="(row, i) in rows" :key="row.id">
                <li class="min-w-0" :class="variant === 'CARD' ? 'overflow-hidden rounded-lg border border-surface bg-surface-0 dark:bg-surface-900' : ''">
                    <p v-if="row.hiddenReason" class="flex items-center gap-1.5 px-4 py-6 text-sm text-muted-color">
                        <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                    </p>
                    <FoldedRow v-else :reason="overrides.blockedOf(row.author.memberId, row.blocked)" kind="post" class="px-4 py-6 text-sm" hide-class="px-3 pb-2">
                        <NuxtLink
                            :to="`/posts/${row.id}`"
                            class="group focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                            :class="[variant === 'CARD' ? 'flex gap-3 p-3' : 'block rounded-lg', row.deal && UtilHotdeal.isOver(row.deal.view) ? 'opacity-55' : '']"
                            data-testid="hotdeal-grid-item"
                        >
                            <HotdealThumb :url="row.deal?.image?.url ?? row.thumbnailUrl" :class="variant === 'CARD' ? 'aspect-square w-24' : 'aspect-square w-full rounded-lg'" />
                            <div class="flex min-w-0 flex-1 flex-col" :class="variant === 'GALLERY' ? 'mt-1.5' : ''">
                                <p class="line-clamp-2 text-sm leading-snug" :class="variant === 'CARD' ? 'font-medium' : ''">
                                    <HotdealBadges v-if="row.deal" :deal="row.deal" class="mr-1" />
                                    <PostRowTitle :row="row" :new-post-hours="newPostHours" :show-prefix="variant === 'CARD'" />
                                </p>
                                <template v-if="row.deal">
                                    <p class="mt-1 truncate text-xs text-muted-color">{{ row.deal.storeName }}</p>
                                    <HotdealPriceLine :deal="row.deal" :size="variant === 'CARD' ? 'md' : 'sm'" shipping :compact="variant === 'GALLERY'" />
                                </template>
                                <p class="flex flex-wrap items-center gap-x-1.5 pt-1 text-xs text-muted-color" :class="variant === 'CARD' ? 'mt-auto' : ''">
                                    <span>{{ t('hotdeal.likesShort', { n: row.likeCount }) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <span>{{ t('hotdeal.viewsShort', { n: row.viewCount.toLocaleString() }) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                </p>
                            </div>
                        </NuxtLink>
                    </FoldedRow>
                </li>
                <li v-if="variant === 'CARD' && UtilAd.feedAdAt(feedAd, i)" class="overflow-hidden rounded-lg border border-surface bg-surface-0 md:col-span-2 dark:bg-surface-900">
                    <AdFeedRow :ad="UtilAd.feedAdAt(feedAd, i)!" variant="card" />
                </li>
            </template>
        </ul>
    </div>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';
import UtilHotdeal from '~/utils/util-hotdeal';

interface Props extends BoardPostListProps {
    variant?: 'CARD' | 'GALLERY';
}

withDefaults(defineProps<Props>(), { feedAd: null, variant: 'CARD' });

const { t } = useI18n();
const overrides = useBlockOverrides();
</script>
