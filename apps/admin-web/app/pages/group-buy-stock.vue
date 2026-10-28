<template>
    <!--
        공구 › 수량(`/group-buy-stock?id=`) — 공구 하나의 수량을 본다(계약 결정 15·17·27·28·48·80·84, community A 6절).
        ① 공구 고르기(DRAFT 제외) ② 수량 표: 공구 전체(MAIN 단품 합 — 결정 88) + 조합(MAIN 단품)별 + 추가 구성상품(따로 구분 줄, 공구 합계에 들지 않음) 총량(지금 최대 · 처음 최대 · 「추가 입고 N개」)
           ·확정·선점·남음·반품·다시 판매·품절 표시(다 팔림 / 잠시 품절 / 판매 안 함)·줄이기 하한
        ③ 선점 목록(결제 중 주문 — 남은 시간이 초 단위로 줄어든다) ④ 수량 변동 내역(이동 종류 거르기, 공구·단품 양쪽의 이후 값).
        추가 입고·줄이기: 공구가 모집 중(서버 `canAdjust`)이고 `commerce.stock.adjust` 일 때만. 단품 지정(`skuId` — 한도 없는 단품은 공구 전체로, 판매 끈 조합은 없음), 수량 + 사유 필수.
        MAIN 단품을 움직이면 공구 최대도 같이, 추가 구성상품은 자기 한도만. 줄이기 하한 = max(확정 + 선점, 성사형 min_qty)(단품은 확정 + 선점)을 미리 보이고, 서버가 `409 GROUP_BUY_QTY_BELOW_FLOOR` 를 주면 수량 칸 아래에 서버 문구.
        숫자는 모두 서버 값이다. 서버 호출은 `useGroupBuyAdminApi`(지금 목업 — `?mock=stock-fail`·`stock-floor`·`movements-empty`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.stockTitle')" :subtitle="t('groupBuy.stockSubtitle')" />

        <div class="flex flex-wrap items-center gap-2">
            <label :for="pickId" class="text-sm text-muted-color">{{ t('groupBuy.stockPick') }}</label>
            <Select :input-id="pickId" :model-value="selectedId" :options="choices" option-label="label" option-value="value" filter :loading="choicesLoading" :placeholder="t('groupBuy.stockPickPlaceholder')" class="w-full max-w-md" @update:model-value="pick" />
            <Button v-if="selectedId" type="button" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :aria-label="t('groupBuy.refresh')" :loading="loading" @click="loadAll" />
            <NuxtLink v-if="selectedId" :to="`/group-buys/${encodeURIComponent(selectedId)}`" class="text-xs text-primary hover:underline">{{ t('groupBuy.openGroupBuy') }} ›</NuxtLink>
        </div>
        <Message v-if="choicesError" severity="error" variant="simple" size="small" :closable="false">{{ choicesError }}</Message>

        <EmptyState v-if="!selectedId" icon="pi pi-box" :title="t('groupBuy.stockNone')" :description="t('groupBuy.stockNoneHint')" />

        <template v-else>
            <AppGadget :title="t('groupBuy.stockSummary')">
                <template v-if="view" #title-extra>
                    <Tag :value="t(`groupBuy.status.${view.groupBuy.status}`)" :severity="UtilGroupBuy.statusSeverity(view.groupBuy.status)" />
                </template>
                <ErrorState v-if="loadError && !view" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.stockLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="loadAll" />
                <div v-else-if="!view" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="2.5rem" />
                </div>
                <div v-else class="flex flex-col gap-3">
                    <p class="m-0 text-xs text-muted-color">{{ view.groupBuy.minQty > 0 ? t('groupBuy.stockLeadThreshold', { n: view.groupBuy.minQty }) : t('groupBuy.stockLeadFirstCome') }}</p>
                    <div class="overflow-x-auto">
                        <table class="w-full min-w-[52rem] border-collapse text-[13px]">
                            <thead>
                                <tr class="border-b border-(--line) text-left text-xs text-muted-color">
                                    <th class="py-2 pr-3 font-normal">{{ t('groupBuy.stockColName') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColMax') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColCurrent') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColReserved') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColRemaining') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColReturned') }}</th>
                                    <th class="py-2 pr-3 text-right font-normal">{{ t('groupBuy.stockColRestocked') }}</th>
                                    <th class="py-2 pr-3 font-normal">{{ t('groupBuy.stockColState') }}</th>
                                    <th v-if="adjustable" class="py-2 font-normal"><span class="sr-only">{{ t('groupBuy.stockColActions') }}</span></th>
                                </tr>
                            </thead>
                            <tbody>
                                <template v-for="line in lines" :key="line.skuId ?? 'total'">
                                <!-- 추가 구성상품 앞 구분 줄 — 공구 합계에 들지 않는다 -->
                                <tr v-if="line.skuId === firstAddonId" class="border-b border-(--line)">
                                    <td :colspan="adjustable ? 9 : 8" class="pt-3 pb-1 text-xs font-semibold">{{ t('groupBuy.stockAddonHeading') }} <span class="font-normal text-muted-color">{{ t('groupBuy.stockAddonHeadingHelp') }}</span></td>
                                </tr>
                                <tr class="border-b border-(--line) last:border-b-0" :class="[line.skuId === null ? 'bg-surface-50 font-medium dark:bg-surface-900' : '', line.onSale ? '' : 'text-muted-color']">
                                    <td class="py-2 pr-3">
                                        <template v-if="line.skuId === null">{{ t('groupBuy.stockTotalRow') }}<span v-if="hasSkuLines" class="block text-[11px] font-normal text-muted-color">{{ t('groupBuy.stockTotalMainOnly') }}</span></template>
                                        <GroupBuySkuLabel v-else :name="line.name" :kind="line.kind" />
                                    </td>
                                    <td class="py-2 pr-3 text-right tabular-nums">
                                        <div>{{ line.maxQty ?? t('groupBuy.optionUnlimited') }}</div>
                                        <div v-if="line.initialMaxQty !== null && line.maxQty !== null" class="text-[11px] font-normal text-muted-color">{{ t('groupBuy.stockInitial', { n: line.initialMaxQty }) }}<template v-if="line.addedQty > 0"> · {{ t('groupBuy.stockAdded', { n: line.addedQty }) }}</template></div>
                                    </td>
                                    <td class="py-2 pr-3 text-right tabular-nums">{{ line.currentQty }}</td>
                                    <td class="py-2 pr-3 text-right tabular-nums">{{ line.reservedQty }}</td>
                                    <td class="py-2 pr-3 text-right tabular-nums">{{ line.remainingQty }}</td>
                                    <td class="py-2 pr-3 text-right tabular-nums">{{ line.returnedQty }}</td>
                                    <td class="py-2 pr-3 text-right tabular-nums">{{ line.restockedQty }}</td>
                                    <td class="py-2 pr-3">
                                        <Tag v-if="!line.onSale" :value="t('groupBuy.skuOffSale')" severity="secondary" />
                                        <Tag v-else-if="line.soldOut" :value="t('groupBuy.soldOut')" severity="contrast" />
                                        <Tag v-else-if="line.tempSoldOut" :value="t('groupBuy.tempSoldOut')" severity="warn" />
                                        <span v-else class="text-xs text-muted-color">—</span>
                                        <div v-if="line.onSale" class="text-[11px] font-normal text-muted-color">{{ t('groupBuy.stockFloor', { n: line.reduceFloor }) }}</div>
                                    </td>
                                    <td v-if="adjustable" class="py-2 text-right whitespace-nowrap">
                                        <span v-if="!line.onSale" class="text-[11px] text-muted-color">—</span>
                                        <template v-else-if="line.maxQty !== null">
                                            <Button type="button" :label="t('groupBuy.stockAdd')" size="small" severity="secondary" variant="outlined" :disabled="line.soldOut && line.skuId === null" @click="openAdjust(line, 'ADD')" />
                                            <Button type="button" :label="t('groupBuy.stockReduce')" size="small" severity="secondary" variant="text" class="ml-1" @click="openAdjust(line, 'REDUCE')" />
                                        </template>
                                        <span v-else class="text-[11px] text-muted-color">{{ t('groupBuy.stockUnlimitedSku') }}</span>
                                    </td>
                                </tr>
                                </template>
                            </tbody>
                        </table>
                    </div>
                    <p v-if="!view.canAdjust" class="m-0 text-xs text-muted-color">{{ t('groupBuy.stockNotRecruiting') }}</p>
                    <p v-else-if="!can('commerce.stock.adjust')" class="m-0 text-xs text-muted-color">{{ t('groupBuy.stockNeedPermission') }}</p>
                    <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-muted-color">
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuy.stockNoteReturn') }}</li>
                        <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuy.stockNoteRound') }}</li>
                    </ul>
                </div>
            </AppGadget>

            <AppGadget :title="t('groupBuy.holdsTitle')">
                <p v-if="view && !view.holds.length" class="m-0 text-[13px] text-muted-color">{{ t('groupBuy.holdsEmpty') }}</p>
                <ul v-else-if="view" class="m-0 flex list-none flex-col p-0">
                    <li v-for="h in view.holds" :key="h.orderNo" class="flex flex-wrap items-center gap-x-3 gap-y-0.5 border-b border-(--line) py-2 text-[13px] last:border-b-0">
                        <code class="text-[12px]">{{ h.orderNo }}</code>
                        <PaymentMemberLabel :member="h.member" class="min-w-24" />
                        <span class="min-w-0 flex-1 truncate">{{ h.lines }}</span>
                        <span class="tabular-nums">{{ t('groupBuy.qtyN', { n: h.qty }) }}</span>
                        <Tag :value="holdLeft(h.reservedUntil)" :severity="secondsLeft(h.reservedUntil) <= 60 ? 'danger' : 'info'" icon="pi pi-clock" />
                    </li>
                </ul>
            </AppGadget>

            <AppGadget :title="t('groupBuy.movementsTitle')">
                <template #actions>
                    <label :for="kindId" class="sr-only">{{ t('groupBuy.movementKind') }}</label>
                    <Select :input-id="kindId" v-model="kind" :options="kindOptions" option-label="label" option-value="value" class="w-44" @change="reloadMovements(true)" />
                </template>
                <DataTable :value="movements" data-key="id" lazy paginator :first="mvFirst" :rows="mvSize" :total-records="mvTotal" :loading="mvLoading" scrollable :current-page-report-template="totalTemplate" paginator-template="PrevPageLink PageLinks NextPageLink CurrentPageReport" size="small" @page="onMvPage">
                    <template #empty>
                        <ErrorState v-if="mvError" :title="t('groupBuy.movementsLoadFailed')" :description="mvError.message" retry :retrying="mvLoading" @retry="reloadMovements(false)" />
                        <div v-else-if="mvLoading" class="h-24" aria-hidden="true" />
                        <EmptyState v-else icon="pi pi-history" :title="t('groupBuy.movementsEmpty')" />
                    </template>
                    <Column :header="t('groupBuy.mvColAt')" style="min-width: 9rem"><template #body="{ data }"><span class="text-[12px] tabular-nums">{{ time.full(data.createdAt) }}</span></template></Column>
                    <Column :header="t('groupBuy.mvColKind')" style="min-width: 7rem"><template #body="{ data }"><Tag :value="t(`groupBuy.movement.${data.kind}`)" :severity="mvSeverity(data.kind)" /></template></Column>
                    <Column :header="t('groupBuy.mvColSku')" style="min-width: 7rem"><template #body="{ data }"><GroupBuySkuLabel v-if="data.skuName !== null" :name="data.skuName" :kind="data.skuKind" class="text-[13px]" /><span v-else class="text-[13px]">—</span></template></Column>
                    <Column :header="t('groupBuy.mvColQty')" style="min-width: 4rem"><template #body="{ data }"><span class="tabular-nums">{{ data.qty }}</span></template></Column>
                    <Column :header="t('groupBuy.mvColAfter')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <div class="text-[12px] tabular-nums">{{ t('groupBuy.mvAfterLine', { current: data.currentAfter, reserved: data.reservedAfter, max: data.maxAfter }) }}</div>
                            <div v-if="data.skuCurrentAfter !== null" class="text-[11px] tabular-nums text-muted-color">{{ t('groupBuy.mvSkuAfterLine', { current: data.skuCurrentAfter, reserved: data.skuReservedAfter ?? 0, max: data.skuMaxAfter ?? '∞' }) }}</div>
                        </template>
                    </Column>
                    <Column :header="t('groupBuy.mvColWho')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <div class="text-[12px]">{{ data.actor === 'SYSTEM' ? t('groupBuy.system') : data.actor }}<code v-if="data.orderNo" class="ml-1 text-[11px] text-muted-color">{{ data.orderNo }}</code></div>
                            <div v-if="data.reason" class="text-[11px] text-muted-color [overflow-wrap:anywhere]">{{ data.reason }}</div>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
        </template>

        <PaymentReasonDialog v-model:visible="adjustOpen" :title="adjustDir === 'ADD' ? t('groupBuy.stockAddTitle') : t('groupBuy.stockReduceTitle')" :description="adjustDir === 'ADD' ? t('groupBuy.stockAddBody') : t('groupBuy.stockReduceBody')" :confirm-label="adjustDir === 'ADD' ? t('groupBuy.stockAdd') : t('groupBuy.stockReduce')" :tone="adjustDir === 'REDUCE' ? 'danger' : 'primary'" :busy="adjusting" :error="adjustError" :field-error="adjustReasonError" @submit="submitAdjust">
            <div v-if="adjustLine" class="flex flex-col gap-3">
                <p class="m-0 text-sm">
                    <span class="font-medium">{{ adjustLine.skuId === null ? t('groupBuy.stockTotalRow') : adjustLine.name }}<template v-if="adjustLine.kind === 'ADDON'"> ({{ t('groupBuy.skuAddonTag') }})</template></span>
                    <span class="text-muted-color"> · {{ t('groupBuy.stockAdjustNow', { max: adjustLine.maxQty ?? '∞', current: adjustLine.currentQty, reserved: adjustLine.reservedQty }) }}</span>
                </p>
                <LabeledField :label="t('groupBuy.stockAdjustQty')" :help="adjustDir === 'REDUCE' ? t('groupBuy.stockReduceFloorHelp', { floor: adjustLine.reduceFloor, room: Math.max(0, (adjustLine.maxQty ?? 0) - adjustLine.reduceFloor) }) : adjustLine.kind === 'ADDON' ? t('groupBuy.stockAddAddonHelp') : adjustLine.skuId ? t('groupBuy.stockAddSkuHelp') : ''" :error="adjustQtyError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="adjustQty" inputmode="numeric" :invalid="invalid" :aria-describedby="describedBy" class="w-32 text-right tabular-nums" />
                    </template>
                </LabeledField>
            </div>
        </PaymentReasonDialog>
    </div>
</template>

<script setup lang="ts">
import UtilTime from '#layers/display-time/utils/util-time';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyMovement, GroupBuyStockLine, GroupBuyStockView, QtyMovementKind } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import { QTY_MOVEMENT_KINDS } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuy.stockTitle') });

const api = useGroupBuyAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const pickId = useId();
const kindId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const selectedId = ref<string>(typeof route.query.id === 'string' ? route.query.id : '');
const choices = ref<{ value: string; label: string }[]>([]);
const choicesLoading = ref(false);
const choicesError = ref('');

async function loadChoices(): Promise<void> {
    choicesLoading.value = true;
    choicesError.value = '';
    try {
        const found = await api.list({ page: 0, size: 100 });
        choices.value = found.items.filter((g) => g.status !== 'DRAFT').map((g) => ({ value: g.id, label: `${g.title}${g.roundNo > 1 ? ` (${t('groupBuy.roundTag', { n: g.roundNo })})` : ''} · ${t(`groupBuy.status.${g.status}`)}` }));
    } catch (e) {
        choicesError.value = toApiError(e).message;
    } finally {
        choicesLoading.value = false;
    }
}

const view = ref<GroupBuyStockView | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 공구 전체(MAIN 합) · MAIN 조합 · 추가 구성상품 차례 */
const lines = computed<GroupBuyStockLine[]>(() => (view.value ? [view.value.total, ...view.value.skus.filter((s) => s.kind === 'MAIN'), ...view.value.skus.filter((s) => s.kind === 'ADDON')] : []));
const firstAddonId = computed(() => view.value?.skus.find((s) => s.kind === 'ADDON')?.skuId ?? '');
const hasSkuLines = computed(() => (view.value?.skus.length ?? 0) > 0);
const adjustable = computed(() => !!view.value?.canAdjust && can('commerce.stock.adjust'));

async function loadStock(): Promise<void> {
    if (!selectedId.value) return;
    loading.value = true;
    loadError.value = null;
    try {
        view.value = await api.stock(selectedId.value);
    } catch (e) {
        view.value = null;
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function loadAll(): void {
    void loadStock();
    void reloadMovements(true);
}

function pick(id: string): void {
    selectedId.value = id;
    view.value = null;
    void router.replace({ query: { ...route.query, id } });
    loadAll();
}

// ── 선점 남은 시간(1초마다) ──
const now = ref(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
onMounted(() => (timer = setInterval(() => (now.value = Date.now()), 1000)));
onBeforeUnmount(() => {
    if (timer) clearInterval(timer);
});
function secondsLeft(iso: string): number {
    return Math.max(0, Math.ceil((Date.parse(iso) - now.value) / 1000));
}
function holdLeft(iso: string): string {
    const s = secondsLeft(iso);
    return s > 0 ? t('groupBuy.holdLeft', { time: UtilTime.mmss(s) }) : t('groupBuy.holdExpired');
}

// ── 변동 내역 ──
const kind = ref<QtyMovementKind | ''>('');
const kindOptions = computed(() => [{ value: '', label: t('groupBuy.movementAll') }, ...QTY_MOVEMENT_KINDS.map((k) => ({ value: k, label: t(`groupBuy.movement.${k}`) }))]);
const movements = ref<GroupBuyMovement[]>([]);
const mvTotal = ref(0);
const mvFirst = ref(0);
const mvSize = 20;
const mvLoading = ref(false);
const mvError = ref<ApiError | null>(null);

async function reloadMovements(reset: boolean): Promise<void> {
    if (!selectedId.value) return;
    if (reset) mvFirst.value = 0;
    mvLoading.value = true;
    mvError.value = null;
    try {
        const found = await api.movements(selectedId.value, { kind: kind.value || undefined, page: Math.floor(mvFirst.value / mvSize), size: mvSize });
        movements.value = found.items;
        mvTotal.value = found.total;
    } catch (e) {
        movements.value = [];
        mvTotal.value = 0;
        mvError.value = toApiError(e);
    } finally {
        mvLoading.value = false;
    }
}

function onMvPage(e: { first: number }): void {
    mvFirst.value = e.first;
    void reloadMovements(false);
}

function mvSeverity(k: QtyMovementKind): 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    if (k === 'CONFIRM' || k === 'ADD' || k === 'RESTOCK') return 'success';
    if (k === 'RESERVE') return 'info';
    if (k === 'RELEASE') return 'secondary';
    if (k === 'CANCEL' || k === 'REDUCE') return 'warn';
    return 'contrast';
}

// ── 추가 입고·줄이기 ──
const adjustOpen = ref(false);
const adjustDir = ref<'ADD' | 'REDUCE'>('ADD');
const adjustLine = ref<GroupBuyStockLine | null>(null);
const adjustQty = ref('');
const adjusting = ref(false);
const adjustError = ref('');
const adjustReasonError = ref('');
const adjustQtyError = ref('');

function openAdjust(line: GroupBuyStockLine, dir: 'ADD' | 'REDUCE'): void {
    adjustLine.value = line;
    adjustDir.value = dir;
    adjustQty.value = '';
    adjustError.value = '';
    adjustReasonError.value = '';
    adjustQtyError.value = '';
    adjustOpen.value = true;
}

async function submitAdjust(reason: string): Promise<void> {
    const line = adjustLine.value;
    if (!line || !selectedId.value) return;
    const qty = UtilGroupBuy.int(adjustQty.value);
    adjustQtyError.value = qty === null || qty < 1 ? t('groupBuy.stockQtyRequired') : '';
    if (!adjustQtyError.value && adjustDir.value === 'REDUCE' && line.maxQty !== null && qty !== null && line.maxQty - qty < line.reduceFloor) adjustQtyError.value = t('groupBuy.stockBelowFloor', { floor: line.reduceFloor });
    if (adjustQtyError.value || qty === null) return;
    adjusting.value = true;
    adjustError.value = '';
    adjustReasonError.value = '';
    try {
        view.value = await api.adjustStock(selectedId.value, { skuId: line.skuId, direction: adjustDir.value, qty, reason });
        toast.success(adjustDir.value === 'ADD' ? t('groupBuy.stockAdded2', { n: qty }) : t('groupBuy.stockReduced', { n: qty }));
        adjustOpen.value = false;
        void reloadMovements(true);
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'GROUP_BUY_QTY_BELOW_FLOOR') adjustQtyError.value = failure.message;
        else if (failure.field('qty')) adjustQtyError.value = failure.field('qty') ?? '';
        else if (failure.field('reason')) adjustReasonError.value = failure.field('reason') ?? '';
        else adjustError.value = failure.message;
    } finally {
        adjusting.value = false;
    }
}

onMounted(() => {
    void loadChoices();
    if (selectedId.value) loadAll();
});
</script>
