<template>
    <!--
        핫딜 한 줄(가격 줄 목록형) — 핫딜 게시판 목록(`HotdealPostList`)과 가젯 `HOT_DEALS`(`compact`)가 같이 쓴다. 링크는 줄 전체, 끝난 딜(종료·기간 지남)은 흐리게.
        · 목록(사용자 시안 확정 2026-11-08 — 기존 핫딜 UI 는 두고 칩·추천·조회만 바꿨다. 칩 위치 설정 `chipPosition` 과 상관없이 이 모양 고정):
            윗줄(줄 폭 전체, 사진보다 위) = 왼쪽 칩[딜 배지(광고(사업자 글)/제휴·수수료/종료·품절·기간 종료/품절 알림 N/얼마 안 남음/마감 임박/HOT/링크 바뀜·가격 수정) + 말머리(없으면 흐린 「일반」)·처리 상태·19]
                     | 얇은 세로선 | 상점 아이콘 + 몰 이름 · 오른쪽 끝 추천·조회(`PostRowStats` — 차례·강조 기준·색은 운영자 설정)
            → 사진(왼쪽) + 제목·[댓글 수] / 할인율 · 딜가 굵게 · 정상가 취소선 · 배송비 / 작성자(사업자 글은 상호 + 「사업자」) · 시각 · 댓글
        · `compact`(가젯): 사진 · 배지 · 제목 / 몰 이름 · 가격 한 줄 — 말머리·메타 줄·수정 이력 배지·「배송비 모름」 없이 좁게(이전 모양 그대로)
    -->
    <NuxtLink
        v-if="!compact"
        :to="`/posts/${row.id}`"
        class="block px-4 py-3 hover:bg-surface-50 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-1 dark:hover:bg-surface-900"
        :class="over ? 'opacity-55' : ''"
        :data-deal-view="deal?.view"
        data-testid="hotdeal-row"
    >
        <div class="mb-1.5 flex items-center justify-between gap-2" data-testid="hotdeal-row-top">
            <div class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
                <PostRowChips :row="row" general>
                    <template v-if="deal" #default><HotdealBadges :deal="deal" /></template>
                </PostRowChips>
                <template v-if="deal?.storeName">
                    <span class="h-[11px] w-px shrink-0 bg-hairline-strong" aria-hidden="true" />
                    <span class="inline-flex min-w-0 items-center gap-1 text-xs leading-none text-muted-color" data-testid="hotdeal-row-store">
                        <i class="pi pi-shop text-[0.6875rem]" aria-hidden="true" />
                        <span class="sr-only">{{ t('hotdeal.storeNav') }}</span>
                        <span class="max-w-[10rem] truncate">{{ deal.storeName }}</span>
                    </span>
                </template>
            </div>
            <PostRowStats :row="row" class="ml-auto" />
        </div>
        <div class="flex items-start gap-3">
            <HotdealThumb :url="deal?.image?.url ?? row.thumbnailUrl" class="size-16 md:size-[4.5rem]" />
            <div class="min-w-0 flex-1">
                <p class="line-clamp-2 text-sm leading-snug">
                    <PostRowTitle :row="row" :new-post-hours="newPostHours" :show-prefix="false" :chips="false" />
                </p>
                <p v-if="deal" class="mt-1 flex min-w-0 flex-wrap items-baseline gap-x-2">
                    <HotdealPriceLine :deal="deal" shipping />
                </p>
                <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                    <PostAuthorLabel :author="author" avatar />
                    <span aria-hidden="true">·</span>
                    <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                    <span aria-hidden="true">·</span>
                    <span>{{ t('hotdeal.commentsShort', { n: row.commentCount }) }}</span>
                </p>
            </div>
        </div>
    </NuxtLink>
    <NuxtLink
        v-else
        :to="`/posts/${row.id}`"
        class="flex items-start gap-3 py-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
        :class="over ? 'opacity-55' : ''"
        :data-deal-view="deal?.view"
        data-testid="hotdeal-row"
    >
        <HotdealThumb :url="deal?.image?.url ?? row.thumbnailUrl" class="size-12" />
        <div class="min-w-0 flex-1">
            <p class="line-clamp-1 text-sm leading-snug">
                <HotdealBadges v-if="deal" :deal="deal" :revision="false" class="mr-1" />
                <PostRowTitle :row="row" :new-post-hours="newPostHours" :show-prefix="false" :pinned-mark="false" :comment-count="false" />
            </p>
            <p v-if="deal" class="mt-1 flex min-w-0 flex-wrap items-baseline gap-x-2">
                <span class="max-w-[10rem] truncate text-xs text-muted-color">{{ deal.storeName }}</span>
                <HotdealPriceLine :deal="deal" shipping compact />
            </p>
        </div>
    </NuxtLink>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';

interface Props {
    row: PostRow;
    newPostHours: number;
    dateDisplay?: BoardDateDisplay;
    /** 가젯용 좁은 줄 — 말머리·작성자·추천·조회·시각 줄을 뺀다 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { dateDisplay: 'STANDARD', compact: false });

const { t } = useI18n();
const deal = computed(() => props.row.deal ?? null);
const over = computed(() => (deal.value ? UtilHotdeal.isOver(deal.value.view) : false));
/** 사업자 글이면 상호 + 「사업자」 */
const author = computed(() => UtilHotdeal.authorOf(props.row.author, deal.value));
</script>
