<template>
    <!--
        리뷰 한 장 — 별점 · 산 단품 · 「n차 구매」 · 닉네임·날짜(수정됨) · 본문 · 사진 · 판매자 답글. 내 리뷰가 숨김이면 사유를 보인다.
        동작(고치기·지우기·신고)은 서버가 정한 `actions` 만 보고 emit 으로 올린다. 공구 상세 리뷰 구역과 내 리뷰가 같이 쓴다.
    -->
    <article class="flex flex-col gap-2" :data-testid="`group-buy-review-${review.id}`">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
            <GroupBuyStars :value="review.rating" size="text-sm" />
            <span class="font-medium text-(--p-text-color)">{{ review.skuName }}</span>
            <PointShopTag v-if="review.roundNo > 0" tone="neutral">{{ t('groupBuyReview.roundBought', { n: review.roundNo }) }}</PointShopTag>
            <PointShopTag v-if="review.mine" tone="primary">{{ t('groupBuyReview.mine') }}</PointShopTag>
            <span class="text-muted-color">{{ review.nickname ?? t('groupBuyReview.withdrawn') }} · {{ time.shortDate(review.createdAt) }}<template v-if="review.updatedAt"> · {{ t('groupBuyReview.editedMark') }}</template></span>
            <div v-if="review.actions.edit || review.actions.delete || review.actions.report" class="ml-auto flex gap-0.5" role="group" :aria-label="t('groupBuyReview.actionsLabel')">
                <Button v-if="review.actions.edit" type="button" size="small" variant="text" severity="secondary" :label="t('groupBuyReview.actEdit')" :disabled="busy" @click="emit('edit')" />
                <Button v-if="review.actions.delete" type="button" size="small" variant="text" severity="danger" :label="t('groupBuyReview.actDelete')" :loading="busy" @click="emit('delete')" />
                <Button v-if="review.actions.report" type="button" size="small" variant="text" severity="secondary" icon="pi pi-flag" :label="t('groupBuyReview.actReport')" @click="emit('report')" />
            </div>
        </div>

        <Message v-if="review.hidden" severity="warn" size="small" :closable="false">
            {{ t('groupBuyReview.hiddenMine', { reason: t(`groupBuyReview.hideReason.${review.hidden.reasonCode}`) }) }}<template v-if="review.hidden.memo"> — {{ review.hidden.memo }}</template>
        </Message>

        <p class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ review.body }}</p>

        <div v-if="review.photos.length" class="flex flex-wrap gap-2">
            <a v-for="(p, i) in review.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener noreferrer" class="block size-20 overflow-hidden rounded-lg border border-surface">
                <img :src="p.url" :alt="t('groupBuyReview.photoAlt', { n: i + 1 })" class="size-full object-cover" loading="lazy" />
            </a>
        </div>

        <div v-if="review.reply" class="flex flex-col gap-1 rounded-lg bg-surface-50 px-3 py-2 dark:bg-surface-800/60">
            <span class="text-xs font-semibold text-primary">
                <i class="pi pi-reply mr-1 text-[11px]" aria-hidden="true" />{{ t('groupBuyReview.replyTitle') }}
                <span class="ml-1 font-normal text-muted-color">{{ time.shortDate(review.reply.repliedAt) }}<template v-if="review.reply.edited"> · {{ t('groupBuyReview.replyEdited') }}</template></span>
            </span>
            <p class="m-0 whitespace-pre-line text-sm leading-relaxed [overflow-wrap:anywhere]">{{ review.reply.body }}</p>
        </div>
    </article>
</template>

<script setup lang="ts">
import type { GroupBuyReview } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';

withDefaults(defineProps<{ review: GroupBuyReview; busy?: boolean }>(), { busy: false });
const emit = defineEmits<{ edit: []; delete: []; report: [] }>();
const { t } = useI18n();
const time = useDisplayTime();
</script>
