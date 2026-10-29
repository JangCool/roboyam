<template>
    <!--
        리뷰 한 장 — 위에서 아래로: 별점·날짜 | 할 일 · 작성자 · 산 상품(단품 · n차 구매) · 본문 · 사진 · 판매자 답글(왼쪽 선). 내 리뷰가 숨김이면 사유를 보인다.
        동작(고치기·지우기·신고)은 서버가 정한 `actions` 만 보고 emit 으로 올린다. 공구 상세 리뷰 구역과 내 리뷰가 같이 쓴다.
    -->
    <article class="flex flex-col gap-2.5" :data-testid="`group-buy-review-${review.id}`">
        <div class="flex items-center gap-2">
            <GroupBuyStars :value="review.rating" size="text-sm" />
            <span class="text-xs tabular-nums text-muted-color">{{ time.shortDate(review.createdAt) }}<template v-if="review.updatedAt"> · {{ t('groupBuyReview.editedMark') }}</template></span>
            <div v-if="review.actions.edit || review.actions.delete || review.actions.report" class="ml-auto flex items-center text-xs text-muted-color" role="group" :aria-label="t('groupBuyReview.actionsLabel')">
                <template v-for="(a, i) in actions" :key="a.key">
                    <span v-if="i > 0" class="mx-1.5 h-2.5 w-px bg-surface-300 dark:bg-surface-600" aria-hidden="true" />
                    <button type="button" class="hover:text-(--p-text-color) hover:underline disabled:opacity-50" :class="a.key === 'delete' ? 'hover:text-red-600!' : ''" :disabled="busy" @click="a.run">{{ a.label }}</button>
                </template>
            </div>
        </div>

        <div class="flex flex-wrap items-center gap-x-1.5 text-[13px]">
            <span class="font-medium">{{ review.nickname ?? t('groupBuyReview.withdrawn') }}</span>
            <span v-if="review.mine" class="rounded bg-primary-50 px-1 text-[11px] font-medium text-primary dark:bg-primary-950/40">{{ t('groupBuyReview.mine') }}</span>
        </div>
        <p class="m-0 text-xs text-muted-color">
            {{ t('groupBuyReview.boughtOption', { sku: review.skuName }) }}<template v-if="review.roundNo > 0"> · {{ t('groupBuyReview.roundBought', { n: review.roundNo }) }}</template>
        </p>

        <Message v-if="review.hidden" severity="warn" size="small" :closable="false">
            {{ t('groupBuyReview.hiddenMine', { reason: t(`groupBuyReview.hideReason.${review.hidden.reasonCode}`) }) }}<template v-if="review.hidden.memo"> — {{ review.hidden.memo }}</template>
        </Message>

        <p class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ review.body }}</p>

        <div v-if="review.photos.length" class="flex flex-wrap gap-1.5">
            <a v-for="(p, i) in review.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener noreferrer" class="block size-[4.5rem] overflow-hidden rounded-md bg-surface-100 dark:bg-surface-800">
                <img :src="p.url" :alt="t('groupBuyReview.photoAlt', { n: i + 1 })" class="size-full object-cover" loading="lazy" />
            </a>
        </div>

        <div v-if="review.reply" class="mt-1 flex flex-col gap-1 border-l-2 border-surface-300 py-0.5 pl-3 dark:border-surface-600">
            <span class="text-xs">
                <span class="font-semibold">{{ t('groupBuyReview.replyTitle') }}</span>
                <span class="ml-1.5 tabular-nums text-muted-color">{{ time.shortDate(review.reply.repliedAt) }}<template v-if="review.reply.edited"> · {{ t('groupBuyReview.replyEdited') }}</template></span>
            </span>
            <p class="m-0 whitespace-pre-line text-[13px] leading-relaxed text-muted-color [overflow-wrap:anywhere]">{{ review.reply.body }}</p>
        </div>
    </article>
</template>

<script setup lang="ts">
import type { GroupBuyReview } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';

const props = withDefaults(defineProps<{ review: GroupBuyReview; busy?: boolean }>(), { busy: false });
const emit = defineEmits<{ edit: []; delete: []; report: [] }>();
const { t } = useI18n();
const time = useDisplayTime();

/** 할 일 — 글자 단추로 이어 붙인다(고치기 | 지우기 · 신고) */
const actions = computed(() => {
    const a = props.review.actions;
    const out: { key: string; label: string; run: () => void }[] = [];
    if (a.edit) out.push({ key: 'edit', label: t('groupBuyReview.actEdit'), run: () => emit('edit') });
    if (a.delete) out.push({ key: 'delete', label: t('groupBuyReview.actDelete'), run: () => emit('delete') });
    if (a.report) out.push({ key: 'report', label: t('groupBuyReview.actReport'), run: () => emit('report') });
    return out;
});
</script>
