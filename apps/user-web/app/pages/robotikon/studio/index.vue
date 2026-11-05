<template>
    <div class="content-card mx-auto flex w-full max-w-3xl flex-col gap-4">
        <NuxtLink :to="UtilEmoticon.PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('emoticon.shopTitle') }}
        </NuxtLink>
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="text-lg font-bold">{{ t('emoticon.studioTitle') }}</h1>
            <div class="flex gap-2">
                <Button as="router-link" :to="UtilEmoticon.GUIDE_PATH" :label="t('emoticon.guideTitle')" icon="pi pi-book" size="small" severity="secondary" variant="outlined" />
                <Button as="router-link" :to="UtilEmoticon.STUDIO_NEW_PATH" :label="t('emoticon.newPack')" icon="pi pi-plus" size="small" data-testid="emoticon-studio-new" />
            </div>
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')" data-testid="emoticon-studio-loading">
            <div class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 3" :key="n" class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !data || !rules" :title="t('emoticon.studioLoadFailed')" :description="loadError" retry data-testid="emoticon-studio-error" @retry="load" />

        <template v-else>
            <!-- 받은 수 · 받은 보상 · 오늘 보상 / 하루 상한 -->
            <section class="grid grid-cols-3 gap-2 panel-card p-4" :aria-label="t('emoticon.studioSummary')" data-testid="emoticon-studio-summary">
                <div>
                    <p class="m-0 text-xs text-muted-color">{{ t('emoticon.summaryAcquires') }}</p>
                    <p class="m-0 mt-0.5 text-xl font-bold tabular-nums">{{ data.acquireTotal.toLocaleString() }}</p>
                </div>
                <div>
                    <p class="m-0 text-xs text-muted-color">{{ t('emoticon.summaryRewards') }}</p>
                    <p class="m-0 mt-0.5 text-xl font-bold tabular-nums text-primary">{{ UtilPoint.points(data.rewardTotal) }}</p>
                </div>
                <div>
                    <p class="m-0 text-xs text-muted-color">{{ t('emoticon.summaryToday') }}</p>
                    <p class="m-0 mt-0.5 text-xl font-bold tabular-nums">{{ UtilPoint.points(data.rewardToday) }}</p>
                    <p class="m-0 text-[0.6875rem] text-muted-color">{{ t('emoticon.summaryCap', { cap: UtilPoint.points(rules.rewardDailyCap) }) }}</p>
                </div>
                <p class="col-span-3 m-0 text-xs leading-relaxed text-muted-color">
                    {{ t('emoticon.studioRewardNote', { points: UtilPoint.points(rules.rewardPerAcquire) }) }}
                    <NuxtLink to="/me/points" class="text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('point.history') }}</NuxtLink>
                </p>
            </section>

            <!-- 내 팩 -->
            <section class="panel-card" :aria-labelledby="`${uid}-packs`" data-testid="emoticon-studio-packs">
                <h2 :id="`${uid}-packs`" class="m-0 px-4 pt-4 text-sm font-semibold">{{ t('emoticon.studioPacks') }}</h2>
                <p v-if="data.pendingCount >= rules.maxPending" class="m-0 mx-4 mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" role="status">
                    {{ t('emoticon.pendingFull', { max: rules.maxPending }) }}
                </p>
                <EmptyState v-if="data.packs.length === 0" :title="t('emoticon.studioEmpty')" :description="t('emoticon.studioEmptyText')" icon="pi pi-palette" data-testid="emoticon-studio-empty">
                    <Button as="router-link" :to="UtilEmoticon.STUDIO_NEW_PATH" :label="t('emoticon.newPack')" icon="pi pi-plus" />
                </EmptyState>
                <ul v-else class="m-0 mt-2 list-none p-0">
                    <li v-for="pack in data.packs" :key="pack.id" class="flex flex-wrap items-start gap-3 border-t border-surface px-4 py-3" data-testid="emoticon-studio-row">
                        <EmoticonPackCover :src="pack.coverUrl" size="md" />
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                                <span class="min-w-0 break-words">{{ pack.name || t('emoticon.untitled') }}</span>
                                <EmoticonStatusTag :status="pack.status" />
                                <!-- 가격 — 운영자가 정한 값, 유료 희망인데 아직 안 정했으면 「유료 희망 — 가격 심사 중」 -->
                                <PointShopTag v-if="pack.price > 0" tone="primary">{{ UtilEmoticonMessages.won(t, pack.price) }}</PointShopTag>
                                <PointShopTag v-else-if="pack.saleWish === 'PAID'" tone="warn">{{ t('emoticon.paidWishPending') }}</PointShopTag>
                                <PointShopTag v-else>{{ t('emoticon.free') }}</PointShopTag>
                                <PointShopTag v-if="pack.revision?.status === 'PENDING'" tone="warn">{{ t('emoticon.revisionPending') }}</PointShopTag>
                                <PointShopTag v-else-if="pack.revision?.status === 'REJECTED'" tone="danger">{{ t('emoticon.revisionRejected') }}</PointShopTag>
                            </p>
                            <p class="m-0 mt-0.5 text-xs text-muted-color">
                                {{
                                    pack.price > 0
                                        ? t('emoticon.studioRowMetaPaid', { count: pack.count, sold: pack.acquireCount.toLocaleString(), sales: UtilEmoticonMessages.won(t, pack.salesTotal) })
                                        : t('emoticon.studioRowMeta', { count: pack.count, acquires: pack.acquireCount.toLocaleString(), rewards: UtilPoint.points(pack.rewardTotal) })
                                }}
                                · <time :datetime="pack.updatedAt">{{ time.listLabel(pack.updatedAt) }}</time>
                            </p>
                            <p v-if="pack.status === 'REJECTED' && pack.rejectReason" class="m-0 mt-1 text-xs leading-relaxed text-red-700 dark:text-red-400">{{ t('emoticon.rejectReason', { reason: pack.rejectReason }) }}</p>
                            <p v-if="pack.status === 'SUSPENDED' && pack.suspendReason" class="m-0 mt-1 text-xs leading-relaxed text-red-700 dark:text-red-400">{{ t('emoticon.suspendReason', { reason: pack.suspendReason }) }}</p>
                            <p v-if="pack.revision?.status === 'REJECTED' && pack.revision.rejectReason" class="m-0 mt-1 text-xs leading-relaxed text-red-700 dark:text-red-400">{{ t('emoticon.rejectReason', { reason: pack.revision.rejectReason }) }}</p>
                            <p v-if="pack.revision?.status === 'PENDING'" class="m-0 mt-1 text-xs text-muted-color">{{ t('emoticon.revisionPendingText') }}</p>
                            <!-- 운영자의 가격 변경(유료↔무료 포함) — 이미 산 사람·지난 정산은 그대로 -->
                            <p v-if="pack.lastPriceChange" class="m-0 mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color" data-testid="emoticon-studio-price-change">
                                <i class="pi pi-tag text-[0.625rem]" aria-hidden="true" />
                                <span>{{ t('emoticon.priceChangedLine', { from: UtilEmoticonMessages.price(t, pack.lastPriceChange.from), to: UtilEmoticonMessages.price(t, pack.lastPriceChange.to) }) }}</span>
                                <span v-if="pack.lastPriceChange.reason">· {{ pack.lastPriceChange.reason }}</span>
                                <time :datetime="pack.lastPriceChange.changedAt">· {{ time.listLabel(pack.lastPriceChange.changedAt) }}</time>
                            </p>
                        </div>
                        <div class="flex shrink-0 gap-2 max-sm:w-full max-sm:justify-end">
                            <Button v-if="UtilEmoticon.listed(pack.status)" as="router-link" :to="UtilEmoticon.packPath(pack.id)" :label="t('emoticon.viewInShop')" size="small" severity="secondary" variant="text" />
                            <Button as="router-link" :to="UtilEmoticon.studioPackPath(pack.id)" :label="UtilEmoticon.editable(pack) ? t('common.edit') : t('emoticon.view')" size="small" severity="secondary" variant="outlined" />
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 최근 보상 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-rewards`" data-testid="emoticon-studio-rewards">
                <h2 :id="`${uid}-rewards`" class="m-0 text-sm font-semibold">{{ t('emoticon.recentRewards') }}</h2>
                <p v-if="data.recentRewards.length === 0" class="m-0 mt-2 text-sm text-muted-color">{{ t('emoticon.recentRewardsEmpty') }}</p>
                <ul v-else class="m-0 mt-2 list-none p-0">
                    <li v-for="r in data.recentRewards" :key="r.id" class="flex items-center justify-between gap-3 border-t border-surface py-2 text-sm first:border-t-0">
                        <span class="min-w-0 truncate">{{ t('emoticon.rewardLine', { name: r.packName }) }}</span>
                        <span class="flex shrink-0 items-center gap-2">
                            <time :datetime="r.createdAt" class="text-xs text-muted-color">{{ time.listLabel(r.createdAt) }}</time>
                            <span class="font-semibold tabular-nums text-primary">+{{ UtilPoint.points(r.points) }}</span>
                        </span>
                    </li>
                </ul>
            </section>

            <!-- 유료 판매 — 이번 달 · 정산 정보 · 월별 정산(계약 가정 4·5) -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-sales`" data-testid="emoticon-studio-sales">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${uid}-sales`" class="m-0 text-sm font-semibold">{{ t('emoticon.salesTitle', { month: monthLabel(data.sales.month) }) }}</h2>
                    <NuxtLink :to="UtilEmoticon.PAYOUT_PATH" class="inline-flex items-center gap-1 rounded text-xs hover:underline focus-visible:outline-2 focus-visible:outline-primary" :class="payout?.registered ? 'text-muted-color' : 'font-semibold text-primary'" data-testid="emoticon-studio-payout-link">
                        <i class="pi pi-wallet text-[0.6875rem]" aria-hidden="true" />
                        {{ payout?.registered ? t('emoticon.payoutRegisteredLine', { bank: bankLabel, account: payout.accountMasked ?? '' }) : t('emoticon.payoutMissingLine') }}
                    </NuxtLink>
                </div>
                <dl class="m-0 mt-3 grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-4 lg:grid-cols-7">
                    <div v-for="cell in salesCells" :key="cell.key">
                        <dt class="text-xs text-muted-color">{{ cell.label }}</dt>
                        <dd class="m-0 mt-0.5 font-bold tabular-nums" :class="cell.tone">{{ cell.value }}</dd>
                    </div>
                </dl>
                <p v-if="data.sales.expectedPayout < 0" class="m-0 mt-2 text-xs leading-relaxed text-amber-700 dark:text-amber-400" data-testid="emoticon-studio-negative-note">{{ t('emoticon.expectedPayoutNegativeNote') }}</p>
                <p class="m-0 mt-2 text-xs leading-relaxed text-muted-color">{{ t('emoticon.settlementRule', ruleArgs) }}</p>

                <h3 class="m-0 mt-4 text-xs font-semibold text-muted-color">{{ t('emoticon.settlementsTitle') }}</h3>
                <p v-if="settlements.length === 0" class="m-0 mt-2 text-sm text-muted-color">{{ t('emoticon.settlementsEmpty') }}</p>
                <div v-else class="mt-2 overflow-x-auto">
                    <table class="w-full min-w-[48rem] border-collapse text-sm" data-testid="emoticon-studio-settlements">
                        <thead>
                            <tr class="text-left text-xs text-muted-color">
                                <th scope="col" class="py-1.5 pr-3 font-semibold">{{ t('emoticon.colMonth') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colSales') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colRefund') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colVat') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colBase') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colFee') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colWithholding') }}</th>
                                <th scope="col" class="py-1.5 pr-3 text-right font-semibold">{{ t('emoticon.colPayout') }}</th>
                                <th scope="col" class="py-1.5 pr-3 font-semibold">{{ t('emoticon.colStatus') }}</th>
                                <th scope="col" class="py-1.5 font-semibold">{{ t('emoticon.colPayDate') }}</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="s in settlements" :key="s.id" class="border-t border-surface align-top">
                                <td class="py-2 pr-3 whitespace-nowrap">{{ monthLabel(s.month) }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ UtilEmoticonMessages.won(t, s.salesAmount) }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ s.refundAmount ? `−${UtilEmoticonMessages.won(t, s.refundAmount)}` : '—' }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ UtilEmoticonMessages.deduction(t, s.vatAmount) }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ UtilEmoticonMessages.signedWon(t, s.baseAmount) }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ UtilEmoticonMessages.deduction(t, s.feeAmount) }}</td>
                                <td class="py-2 pr-3 text-right tabular-nums">{{ UtilEmoticonMessages.deduction(t, s.withholdingAmount) }}</td>
                                <td class="py-2 pr-3 text-right font-semibold tabular-nums">
                                    {{ UtilEmoticonMessages.won(t, s.payoutAmount) }}
                                    <span v-if="s.carriedIn > 0" class="block text-[0.6875rem] font-normal text-muted-color">{{ t('emoticon.carriedIn', { amount: UtilEmoticonMessages.won(t, s.carriedIn) }) }}</span>
                                    <span v-else-if="s.carriedIn < 0" class="block text-[0.6875rem] font-normal text-amber-700 dark:text-amber-400">{{ t('emoticon.carriedInDeducted', { amount: UtilEmoticonMessages.won(t, -s.carriedIn) }) }}</span>
                                </td>
                                <td class="py-2 pr-3">
                                    <PointShopTag :tone="UtilEmoticon.settlementTone(s.status)">{{ UtilEmoticonMessages.settlement(t, s.status) }}</PointShopTag>
                                    <span v-if="s.carriedOver && s.carriedOut < 0" class="mt-0.5 block text-[0.6875rem] text-amber-700 dark:text-amber-400">{{ t('emoticon.deductNextMonth', { amount: UtilEmoticonMessages.won(t, -s.carriedOut) }) }}</span>
                                    <span v-else-if="s.carriedOver" class="mt-0.5 block text-[0.6875rem] text-muted-color">{{ t('emoticon.carriedOver', { min: UtilEmoticonMessages.won(t, rules.settlementMin) }) }}</span>
                                    <span v-if="s.status === 'HELD' && s.holdReason" class="mt-0.5 block max-w-56 text-[0.6875rem] text-red-700 dark:text-red-400">{{ s.holdReason }}</span>
                                </td>
                                <td class="py-2 whitespace-nowrap tabular-nums">{{ s.payDate ?? '—' }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonPayoutAccount, EmoticonSettlement, EmoticonStudioOverview } from '~/composables/emoticon/api/useEmoticonStudioApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';
import UtilPoint from '~/utils/util-point';

/**
 * 로보티콘 스튜디오(내 제작·판매·정산) `/robotikon/studio` — 로그인 필요. 내 팩 목록(상태 칩 — 임시·심사 대기·판매 중·반려(사유)·판매 중단(사유), 새 판 심사 중, 가격),
 * 무료 팩 보상(받은 수·보상 합계·오늘 보상/하루 상한·최근 보상 — 그대로), 유료 판매(이번 달 판매 수·판매액·환불·수수료·정산 예정액, 정산 정보 링크, 정산 규칙 한 줄,
 * 월별 정산 표 — 예정·확정·지급 완료·보류(사유)·이월), 「새 팩 만들기」. 작가 자격은 서버가 신청 때 본다(가정 9).
 * 내 팩·보상 요약은 실제 API(S1), 정산 정보 등록 여부는 실제 API(S2), 이번 달 판매 요약(`sales` — KST 이번 달, 예정 작가 몫은 원천징수 전)·팩 `salesTotal`(환불 뺀 판매액)은 실제 값(S3).
 * 월별 정산은 실제 API(S4) — 이번 달 예정 줄이 맨 앞(위 이번 달 판매와 같은 계산). 확정 뒤 환불로 음수가 된 달·예정액은 「다음 달에서 차감」으로,
 * 넘어온 금액(원천징수 전)이 음수면 「앞 달 … 차감」으로 보인다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('emoticon.studioTitle'), robots: 'noindex' });

const uid = useId();
const api = useEmoticonStudioApi();
const publicApi = useEmoticonApi();
const time = useDisplayTime();

const { localizedName } = useLocalizedName();

const data = ref<EmoticonStudioOverview | null>(null);
const rules = ref<EmoticonRules | null>(null);
const settlements = ref<EmoticonSettlement[]>([]);
const payout = ref<EmoticonPayoutAccount | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');

/** `YYYY-MM` → 「2026년 10월」 */
function monthLabel(month: string): string {
    const [y, m] = month.split('-');
    return t('emoticon.monthLabel', { year: y ?? '', month: Number(m ?? 0) });
}

const bankLabel = computed(() => {
    const bank = UtilEmoticon.bank(payout.value?.bankCode);
    return bank ? localizedName(bank.name, bank.nameEn) : '';
});

const salesCells = computed(() => {
    const s = data.value?.sales;
    if (!s) return [];
    // 확정된 달 판매가 이번 달에 환불되면 정산 기준 이하가 음수일 수 있다 — 예정액이 음수면 「다음 달에서 차감」으로 읽히게(아래 안내 한 줄)
    const expected =
        s.expectedPayout < 0
            ? { value: t('emoticon.deductNextMonth', { amount: UtilEmoticonMessages.won(t, -s.expectedPayout) }), tone: 'text-amber-700 dark:text-amber-400' }
            : { value: UtilEmoticonMessages.won(t, s.expectedPayout), tone: 'text-primary' };
    return [
        { key: 'count', label: t('emoticon.salesCount'), value: t('emoticon.salesCountValue', { count: s.salesCount.toLocaleString() }), tone: '' },
        { key: 'amount', label: t('emoticon.colSales'), value: UtilEmoticonMessages.won(t, s.salesAmount), tone: '' },
        { key: 'refund', label: t('emoticon.colRefund'), value: UtilEmoticonMessages.won(t, s.refundAmount), tone: '' },
        { key: 'vat', label: t('emoticon.colVat'), value: UtilEmoticonMessages.signedWon(t, s.vatAmount), tone: '' },
        { key: 'base', label: t('emoticon.colBase'), value: UtilEmoticonMessages.signedWon(t, s.baseAmount), tone: '' },
        { key: 'fee', label: t('emoticon.colFee'), value: UtilEmoticonMessages.signedWon(t, s.feeAmount), tone: '' },
        { key: 'expected', label: t('emoticon.expectedPayout'), ...expected },
    ];
});

const ruleArgs = computed(() => {
    const r = rules.value;
    return r
        ? { days: r.refundDays, day: r.settlementDay, min: UtilEmoticonMessages.won(t, r.settlementMin), rate: UtilEmoticonMessages.percent(r.feeRate) }
        : { days: 7, day: 10, min: '', rate: '' };
});

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const [overview, r, list, account] = await Promise.all([api.overview(), publicApi.rules(), api.settlements(), api.payoutAccount()]);
        data.value = overview;
        rules.value = r;
        settlements.value = list.items;
        payout.value = account;
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = 'error';
    }
}
onMounted(load);
</script>
