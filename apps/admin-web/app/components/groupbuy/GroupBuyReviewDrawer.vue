<template>
    <!--
        공구 리뷰 상세 서랍 — 리뷰 목록에서 줄을 누르면 연다(`?review=ID`). 계약 「리뷰 규칙」(2b-6, 가정).
        구획: ① 리뷰(별점·상품 칸·본문·사진·주문 번호 · 작성자가 고침/지움) ② 공식 답글(쓰기·고치기) ③ 작성자 수정 이력 ④ 열린 신고 ⑤ 숨김(사유 코드·메모·누가·언제) + 숨기기/숨김 풀기.
        서버는 부르지 않는다 — 동작은 emit 으로 올린다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '42rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ t('groupBuyReview.secReview') }} #{{ head.id }}</h2>
                    <GroupBuyStarsLabel :value="head.rating" />
                    <Tag v-if="head.hidden" :value="t('groupBuyReview.hidden')" severity="secondary" icon="pi pi-eye-slash" />
                    <Tag v-else-if="head.deleted" :value="t('groupBuyReview.deleted')" severity="secondary" icon="pi pi-trash" />
                    <Tag v-else :value="t('groupBuyReview.visible')" severity="success" />
                    <Tag v-if="head.reportCount > 0" :value="t('groupBuyReview.reports', { count: head.reportCount })" severity="danger" icon="pi pi-flag" />
                </div>
                <span class="truncate text-[12px] text-muted-color">{{ head.product.name }} · {{ head.groupBuy.title }} · {{ t('groupBuyReview.roundN', { n: head.groupBuy.roundNo }) }} · {{ head.member.nickname }}<template v-if="head.member.id"> ({{ head.member.id }})</template></span>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('groupBuyReview.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : error.status === 403 ? '403' : '!'" :retry="error.status !== 404 && error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton width="40%" height="1.25rem" />
            <Skeleton v-for="n in 5" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <!-- ① 리뷰 — 상품 한 칸(주문 서랍과 같은 모양) · 본문 · 사진 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-r`">
                <h3 :id="`${titleId}-r`" class="text-sm font-semibold">{{ t('groupBuyReview.secReview') }}</h3>
                <div class="flex items-start gap-3 rounded-lg border border-(--line) px-3.5 py-3 text-[13px]">
                    <span class="flex size-9 shrink-0 items-center justify-center rounded-md bg-(--p-content-hover-background) text-muted-color" aria-hidden="true"><i class="pi pi-box" /></span>
                    <div class="flex min-w-0 flex-1 flex-col gap-1">
                        <div class="flex items-baseline justify-between gap-3">
                            <span class="min-w-0 font-medium">{{ detail.skuName }}</span>
                            <NuxtLink :to="`/group-buy-orders?order=${encodeURIComponent(detail.orderId)}`" class="shrink-0 text-xs text-primary hover:underline"><code>{{ detail.orderNo }}</code> ›</NuxtLink>
                        </div>
                        <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-color">
                            <span class="tabular-nums">{{ time.shortDateTime(detail.createdAt) }}</span>
                            <template v-if="detail.updatedAt"><span aria-hidden="true">·</span><span>{{ t('groupBuyReview.editedMark') }} {{ time.shortDateTime(detail.updatedAt) }}</span></template>
                        </div>
                    </div>
                </div>
                <p class="m-0 whitespace-pre-line rounded-lg bg-(--p-content-hover-background) px-3 py-2 text-[13px] leading-relaxed [overflow-wrap:anywhere]">{{ detail.body }}</p>
                <div v-if="detail.photos.length" class="flex flex-wrap gap-2">
                    <a v-for="(p, i) in detail.photos" :key="p.id" :href="p.url" target="_blank" rel="noopener noreferrer"><img :src="p.url" :alt="t('groupBuyReview.photoAlt', { n: i + 1 })" class="size-20 rounded-md border border-(--line) object-cover" /></a>
                </div>
                <p v-if="detail.deletedAt" class="m-0 text-xs text-muted-color">{{ t('groupBuyReview.deletedAt', { at: time.shortDateTime(detail.deletedAt) }) }}</p>
            </section>

            <!-- ② 공식 답글 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-a`">
                <h3 :id="`${titleId}-a`" class="text-sm font-semibold">{{ t('groupBuyReview.secReply') }}</h3>
                <GroupBuyQnaAnswerPanel
                    :key="`${detail.id}-${detail.answer?.updatedAt ?? detail.answer?.answeredAt ?? 'none'}`"
                    ns="groupBuyReview"
                    :answer="detail.answer"
                    :can-answer="canManage && !detail.deleted"
                    :blocked-text="detail.deleted ? t('groupBuyReview.replyDeletedBlocked') : ''"
                    :busy="busy === 'reply'"
                    :error="replyError"
                    :field-error="replyFieldError"
                    @submit="(body) => emit('reply', body)"
                />
            </section>

            <!-- ③ 작성자 수정 이력 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-v`">
                <h3 :id="`${titleId}-v`" class="text-sm font-semibold">{{ t('groupBuyReview.secRevisions') }}</h3>
                <p v-if="detail.revisions.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('groupBuyReview.noRevisions') }}</p>
                <ol v-else class="m-0 flex list-none flex-col gap-1 p-0">
                    <li v-for="r in detail.revisions" :key="r.id" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                        <span class="whitespace-pre-line text-[13px] text-muted-color line-through decoration-1 [overflow-wrap:anywhere]">{{ r.body }}</span>
                        <span class="text-[11px] text-muted-color">{{ t('groupBuyReview.revisionLine', { rating: r.rating, photos: r.photoCount, at: time.shortDateTime(r.writtenAt), replacedAt: time.shortDateTime(r.replacedAt) }) }}</span>
                    </li>
                </ol>
            </section>

            <!-- ④ 열린 신고 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-rp`">
                <h3 :id="`${titleId}-rp`" class="text-sm font-semibold">{{ t('groupBuyReview.secReports') }}</h3>
                <p v-if="detail.reports.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('groupBuyReview.noReports') }}</p>
                <template v-else>
                    <div class="flex flex-wrap gap-1.5">
                        <Tag v-for="r in detail.reports" :key="r.reason" :value="`${t(`board.reason.${r.reason}`)} ${r.count}`" severity="danger" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('groupBuyReview.reportsHelp') }}</p>
                </template>
            </section>

            <!-- ⑤ 숨김 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-h`">
                <h3 :id="`${titleId}-h`" class="text-sm font-semibold">{{ t('groupBuyReview.secHidden') }}</h3>
                <div v-if="detail.hiddenInfo" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <span class="font-medium">{{ t(`groupBuyReview.hideReason.${detail.hiddenInfo.reasonCode}`) }}</span>
                    <span v-if="detail.hiddenInfo.memo" class="[overflow-wrap:anywhere]">{{ detail.hiddenInfo.memo }}</span>
                    <span class="text-[11px] text-muted-color">{{ t('groupBuyReview.hiddenLine', { name: detail.hiddenInfo.hiddenBy.name, at: time.shortDateTime(detail.hiddenInfo.hiddenAt) }) }}</span>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('groupBuyReview.lowRatingNote') }}</p>
                <div v-if="canManage && !detail.deleted">
                    <Button v-if="detail.hidden" type="button" size="small" severity="secondary" variant="outlined" icon="pi pi-eye" :label="t('groupBuyReview.actUnhide')" :disabled="busy !== null" @click="emit('unhide')" />
                    <Button v-else type="button" size="small" severity="danger" variant="outlined" icon="pi pi-eye-slash" :label="t('groupBuyReview.actHide')" :disabled="busy !== null" @click="emit('hide')" />
                </div>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyReviewDetail, GroupBuyReviewRow } from '~/composables/groupbuy/api/useGroupBuyReviewAdminApi.types';

interface Props {
    summary: GroupBuyReviewRow | null;
    detail: GroupBuyReviewDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: 'reply' | 'hide' | null;
    /** `commerce.order.manage` — 답글·숨김(가정) */
    canManage?: boolean;
    replyError?: string;
    replyFieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, busy: null, canManage: false, replyError: '', replyFieldError: '' });
const emit = defineEmits<{ retry: []; reply: [body: string]; hide: []; unhide: [] }>();
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const head = computed(() => props.detail ?? props.summary);
</script>
