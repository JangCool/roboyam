<template>
    <!-- 신청 건 한 장(사업자 센터 홈의 최근 신청 건 · 내 신청 건 목록) — 종류·상태·제목·기간·금액, 결제/입금 대기면 기한 줄. 누르면 상세 -->
    <NuxtLink
        :to="UtilBusiness.applicationPath(item.id)"
        class="flex gap-3 panel-card p-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
        data-testid="business-application-card"
    >
        <div class="grid size-10 shrink-0 place-items-center rounded-lg bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
            <i :class="[UtilBusiness.isAd(item.kind) ? 'pi pi-megaphone' : 'pi pi-tag', 'text-base']" />
        </div>
        <div class="flex min-w-0 flex-1 flex-col gap-1">
            <div class="flex flex-wrap items-center gap-1.5">
                <BusinessStatusTag :status="item.status" :phase="item.phase" />
                <PointShopTag v-if="item.refundRequested" tone="warn">{{ t('business.list.refundRequested') }}</PointShopTag>
                <span class="text-xs text-muted-color">{{ kindLabel }}</span>
            </div>
            <p class="m-0 truncate text-sm font-semibold">{{ item.title }}</p>
            <p class="m-0 text-xs tabular-nums text-muted-color">{{ periodLabel }}</p>
            <p class="m-0 text-sm tabular-nums">
                <span v-if="!item.amountConfirmed" class="mr-1 text-xs text-muted-color">{{ t('business.list.estimated') }}</span>
                <span class="font-semibold">{{ UtilBusinessMessages.won(t, item.amount.total) }}</span>
                <span class="ml-1.5 text-xs text-muted-color">{{ t(`business.paymentMethod.${item.paymentMethod}`) }}</span>
            </p>
            <BusinessDeadline v-if="item.status === 'AWAITING_PAYMENT' && item.paymentDueAt" :due-at="item.paymentDueAt" kind="PAYMENT" />
            <BusinessDeadline v-else-if="item.status === 'AWAITING_DEPOSIT' && item.depositDueAt" :due-at="item.depositDueAt" kind="DEPOSIT" />
        </div>
        <i class="pi pi-angle-right self-center text-muted-color" aria-hidden="true" />
    </NuxtLink>
</template>

<script setup lang="ts">
import type { BizApplicationSummary } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    item: BizApplicationSummary;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();

/** 「배너 광고 · 넓은 배너」 · 「사업자 핫딜 · 홈 상단」 */
const kindLabel = computed(() => {
    const kind = t(`business.kind.${props.item.kind}`);
    if (props.item.adType && props.item.kind === 'AD_BANNER') return `${kind} · ${t(`business.adType.${props.item.adType}`)}`;
    if (props.item.placement && props.item.placement !== 'NONE') return `${kind} · ${t(`business.placement.${props.item.placement}`)}`;
    return kind;
});

const periodLabel = computed(() =>
    UtilBusinessMessages.period(t, time.value.serviceDate(props.item.startDate), time.value.serviceDate(props.item.endDate), props.item.startDate, props.item.endDate),
);
</script>
