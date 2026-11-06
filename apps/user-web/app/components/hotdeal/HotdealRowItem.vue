<template>
    <!--
        핫딜 한 줄(가격 줄 목록형) — 썸네일(작게) · 말머리 · 배지(광고(사업자 글)/제휴·수수료/종료·품절·기간 종료/품절 알림 N/얼마 안 남음/마감 임박/HOT/링크 바뀜·가격 수정) · 제목 · [댓글 수]
          / 몰 이름 · 할인율 · 딜가 굵게 · 정상가 취소선 · 배송비(무료배송 / +n원 / 배송비 모름) / 작성자(사업자 글은 상호 + 「사업자」) · 추천 · 댓글 · 조회 · 시각.
        끝난 딜(종료·기간 지남)은 흐리게. 핫딜 게시판 목록(`HotdealPostList`)과 가젯 `HOT_DEALS`(`compact` — 말머리·메타 줄·수정 이력 배지·「배송비 모름」 없이 한 줄 더 좁게)가 같이 쓴다. 링크는 줄 전체.
    -->
    <NuxtLink
        :to="`/posts/${row.id}`"
        class="flex items-start gap-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
        :class="[over ? 'opacity-55' : '', compact ? 'py-2' : 'px-4 py-3 md:px-1 hover:bg-surface-50 dark:hover:bg-surface-900']"
        :data-deal-view="deal?.view"
        data-testid="hotdeal-row"
    >
        <HotdealThumb :url="deal?.image?.url ?? row.thumbnailUrl" :class="compact ? 'size-12' : 'size-16 md:size-[4.5rem]'" />
        <div class="min-w-0 flex-1">
            <p class="text-sm leading-snug" :class="compact ? 'line-clamp-1' : 'line-clamp-2'">
                <PostPrefixTag v-if="row.prefix && !compact" :prefix="row.prefix" class="mr-1 align-[1px]" />
                <HotdealBadges v-if="deal" :deal="deal" :revision="!compact" class="mr-1" />
                <PostRowTitle :row="row" :new-post-hours="newPostHours" :show-prefix="false" :pinned-mark="!compact" :comment-count="!compact" />
            </p>
            <p v-if="deal" class="mt-1 flex min-w-0 flex-wrap items-baseline gap-x-2">
                <span class="max-w-[10rem] truncate text-xs text-muted-color">{{ deal.storeName }}</span>
                <HotdealPriceLine :deal="deal" shipping :compact="compact" />
            </p>
            <p v-if="!compact" class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                <PostAuthorLabel :author="author" avatar />
                <span aria-hidden="true">·</span>
                <span>{{ t('hotdeal.likesShort', { n: row.likeCount }) }}</span>
                <span aria-hidden="true">·</span>
                <span>{{ t('hotdeal.commentsShort', { n: row.commentCount }) }}</span>
                <span aria-hidden="true">·</span>
                <span>{{ t('hotdeal.viewsShort', { n: row.viewCount.toLocaleString() }) }}</span>
                <span aria-hidden="true">·</span>
                <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
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
