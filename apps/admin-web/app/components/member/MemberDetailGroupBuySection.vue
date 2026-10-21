<template>
    <!--
        회원 상세 — 공동구매 구획(읽기 전용, 후보 계약 — 그누보드7 쇼핑 데이터를 공동구매로 대응). roboyam 은 쇼핑몰이 아니라 공동구매가 있는 커뮤니티라 찜·장바구니·상품문의·주소록은 만들지 않았다.
        참여한 공구·주문 건수(진행 중·배송 중·완료·취소/환불), 최근 주문 5건, 최근 배송지(community 처럼 주문 단위 스냅샷이라 「주문 N의 배송지」, 기본 배송지 개념 없음).
        주소·전화는 `member.pii` 가 없으면 가려서 온다. 공동구매 기능이 만들어지면 항목이 바뀔 수 있다.
    -->
    <div v-if="detail.groupBuy" class="flex flex-col gap-5">
        <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
            <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
            <span>{{ t('member.detail.groupBuy.candidateNote') }}</span>
        </p>

        <section :aria-labelledby="summaryId">
            <h3 :id="summaryId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.groupBuy.summaryTitle') }}</h3>
            <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-3">
                <div v-for="item in summary" :key="item.key" class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ item.label }}</dt>
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ item.value.toLocaleString(locale) }}</dd>
                </div>
            </dl>
        </section>

        <section :aria-labelledby="ordersId">
            <h3 :id="ordersId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.groupBuy.ordersTitle') }}</h3>
            <p v-if="!detail.groupBuy.recentOrders.length" class="text-[13px] text-muted-color">{{ t('member.detail.groupBuy.ordersNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="o in detail.groupBuy.recentOrders" :key="o.orderNo" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2.5">
                    <div class="flex min-w-0 flex-wrap items-center gap-2">
                        <span class="min-w-0 truncate text-[13px] font-semibold">{{ o.dealTitle }}</span>
                        <MemberDetailTag class="ml-auto" :value="t(`member.detail.groupBuy.status.${o.status}`)" :icon="UtilMember.orderStatusIcon(o.status)" :severity="UtilMember.orderStatusSeverity(o.status)" />
                    </div>
                    <p class="text-xs text-muted-color tabular-nums">
                        <code>{{ o.orderNo }}</code>
                        · {{ t('member.detail.groupBuy.quantity', { count: o.quantity }) }} · {{ t('member.detail.groupBuy.amount', { amount: o.totalAmount.toLocaleString(locale) }) }} · {{ time.full(o.orderedAt) }}
                    </p>
                </li>
            </ul>
        </section>

        <section :aria-labelledby="shippingId">
            <h3 :id="shippingId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.groupBuy.shippingTitle') }}</h3>
            <p v-if="!detail.groupBuy.recentOrders.length" class="text-[13px] text-muted-color">{{ t('member.detail.groupBuy.shippingNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col p-0">
                <li v-for="o in detail.groupBuy.recentOrders" :key="o.orderNo" class="flex flex-col gap-0.5 border-b border-(--line) py-2.5 text-[13px] last:border-b-0">
                    <p class="text-xs text-muted-color">{{ t('member.detail.groupBuy.shippingOf', { orderNo: o.orderNo }) }}</p>
                    <template v-if="o.shipping">
                        <p>{{ o.shipping.recipient }}</p>
                        <p class="break-words">{{ o.shipping.address }}</p>
                        <p class="tabular-nums">{{ o.shipping.phone }}</p>
                        <p v-if="detail.piiMasked" class="inline-flex items-center gap-1 text-[11px] text-muted-color">
                            <i class="pi pi-eye-slash" aria-hidden="true" />
                            <span>{{ t('member.detail.maskedSuffix') }}</span>
                        </p>
                    </template>
                    <p v-else class="text-muted-color">{{ t('member.detail.groupBuy.shippingErased') }}</p>
                </li>
            </ul>
        </section>
    </div>
    <EmptyState v-else icon="pi pi-shopping-bag" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.groupBuy')" />
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const summaryId = useId();
const ordersId = useId();
const shippingId = useId();

const summary = computed(() => {
    const g = props.detail.groupBuy;
    return [
        { key: 'dealCount', label: t('member.detail.groupBuy.dealCount'), value: g?.dealCount ?? 0 },
        { key: 'orderCount', label: t('member.detail.groupBuy.orderCount'), value: g?.orderCount ?? 0 },
        { key: 'inProgressCount', label: t('member.detail.groupBuy.inProgressCount'), value: g?.inProgressCount ?? 0 },
        { key: 'shippingCount', label: t('member.detail.groupBuy.shippingCount'), value: g?.shippingCount ?? 0 },
        { key: 'completedCount', label: t('member.detail.groupBuy.completedCount'), value: g?.completedCount ?? 0 },
        { key: 'canceledCount', label: t('member.detail.groupBuy.canceledCount'), value: g?.canceledCount ?? 0 },
    ];
});
</script>
