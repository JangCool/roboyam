<template>
    <!--
        로보티콘 › 판매 내역(`/robotikon/sales`) — 유료 로보티콘 팩의 실결제(원)·환불 기록을 찾아보고, 셀프 환불 조건 밖의 구매를 운영자가 예외 환불한다(계약 emoticon-contract 「가격과 결제」·가정 3).
        실제 API(S3 — `EmoticonSalesAdminController`). 팩 이름은 산 때의 이름(사본), 대표 그림은 지금 판. 작가·구매 회원의 프로필이 없으면 닉네임 대신 「(닉네임 없음)」.
        `settlementMonth`(S4) = 그 판매가 들어간 확정된 정산 달 — 있으면 환불 대화상자에 「그 달 정산은 확정됐고 환불액은 이번 달 정산에서 뺀다」를 한 줄 더 알린다(확정 전이면 null — 그 달 예정에서 빠진다).
        조회 조건(기간 · 팩 · 상태)은 [검색](또는 Enter)을 눌렀을 때만 간다. 기간 시작 값은 최근 1개월. 날짜는 표시 시간대의 하루 경계 → UTC ISO(끝은 다음 날 0시 미만). `?packId=` 로 들어오면(팩 서랍의 링크) 그 팩으로 바로 찾는다.
        한 줄: 결제 시각 · 팩 · 작가(회원 관리 링크) · 구매 회원(회원 관리 링크) · 금액 · 결제 번호(결제 화면 `/payments/orders?payment=` 링크 — 주문 종류 `ROBOTIKON_PACK`) · 사용 여부 · 환불 상태(셀프 환불 가능 ~기한 / 불가 이유 · 환불됨 셀프·예외).
        [예외 환불](`emoticon.sales`, 결제 완료 줄만): 이미 쓴 구매면 먼저 경고 확인창 → 사유 대화상자(자주 쓰는 사유 + 직접 입력, 5~200자) → 토스트 → 지금 쪽 다시 읽기. 정산에 이미 모인 판매면 대화상자에 한 줄 더 알린다.
        이미 환불된 구매(409)·없는 구매(404)는 토스트 + 다시 읽기.
        권한: 화면·예외 환불 `emoticon.sales`(메뉴 — 서버 `Permission.EMOTICON_SALES`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('emoticon.salesTitle')" :subtitle="t('emoticon.salesSubtitle')" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('emoticon.filter.salesLabel')" @submit.prevent="search">
            <FilterRow :label="t('emoticon.filter.period')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('emoticon.filter.from')" :to-label="t('emoticon.filter.to')" />
            </FilterRow>
            <FilterRow :label="t('emoticon.filter.pack')" :label-for="ids.pack">
                <Select v-model="filter.packId" :input-id="ids.pack" :options="packOptions" option-label="label" option-value="value" :loading="packsLoading" class="w-full max-w-sm" />
            </FilterRow>
            <FilterRow :label="t('emoticon.filter.saleStatus')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.status" :options="statusOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('emoticon.resetFilter')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

        <p v-if="!loadError && total > 0" class="m-0 text-[13px] text-muted-color">
            {{ t('emoticon.salesSummary', { count: total.toLocaleString(locale), amount: paidAmount.toLocaleString(locale) }) }}
        </p>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="id"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100]"
                :loading="busy"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                class="min-w-0"
                @page="onPage"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('emoticon.forbidden') : t('emoticon.salesLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-receipt" :title="t('emoticon.salesEmpty')" />
                </template>

                <Column :header="t('emoticon.col.paidAt')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.paidAt) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.name')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 items-center gap-2">
                            <EmoticonPackCover :src="data.coverUrl" :alt="data.packName" :size="32" />
                            <NuxtLink :to="{ path: '/robotikon/packs', query: { id: data.packId } }" class="min-w-0 truncate text-[13px] hover:underline">{{ data.packName }}</NuxtLink>
                        </div>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.creator')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <NuxtLink v-if="!data.creator.withdrawn" :to="UtilEmoticon.memberLink(data.creator.memberNo)" class="text-[13px] text-primary hover:underline">{{ nicknameOf(data.creator) }}</NuxtLink>
                        <span v-else class="text-[13px] text-muted-color">{{ nicknameOf(data.creator) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.buyer')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span v-if="data.buyer.withdrawn" class="text-[13px] text-muted-color">{{ nicknameOf(data.buyer) }}</span>
                        <NuxtLink v-else :to="UtilEmoticon.memberLink(data.buyer.memberNo)" class="text-[13px] text-primary hover:underline">{{ nicknameOf(data.buyer) }}</NuxtLink>
                        <code class="block text-[11px] tabular-nums text-muted-color">{{ data.buyer.memberNo }}</code>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.amount')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums" :class="{ 'text-muted-color line-through': data.status === 'REFUNDED' }">{{ t('emoticon.wonValue', { amount: data.amount.toLocaleString(locale) }) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.payment')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <NuxtLink :to="{ path: '/payments/orders', query: { payment: data.paymentId } }" class="text-[12px] text-primary hover:underline" :title="t('emoticon.openPayment')"><code>{{ data.paymentId }}</code></NuxtLink>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.used')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <div v-if="data.firstUsedAt" class="min-w-0">
                            <div class="text-[13px]">{{ t('emoticon.used') }}</div>
                            <div class="text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(data.firstUsedAt) }}</div>
                        </div>
                        <span v-else class="text-[13px] text-muted-color">{{ t('emoticon.notUsed') }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.refund')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col items-start gap-0.5">
                            <template v-if="data.status === 'PAID'">
                                <Tag :value="t('emoticon.saleStatus.PAID')" severity="info" />
                                <span v-if="data.selfRefund" class="text-[11px] tabular-nums text-muted-color">{{ selfRefundText(data) }}</span>
                            </template>
                            <template v-else>
                                <Tag :value="t('emoticon.saleStatus.REFUNDED')" severity="warn" />
                                <span v-if="data.refund" class="text-[11px] text-muted-color">{{ t(`emoticon.refundKind.${data.refund.kind}`) }} · {{ time.shortDateTime(data.refund.at) }}<template v-if="data.refund.by"> · {{ data.refund.by }}</template></span>
                                <span v-if="data.refund?.reason" class="max-w-[14rem] truncate text-[11px] text-muted-color" :title="data.refund.reason">{{ data.refund.reason }}</span>
                            </template>
                        </div>
                    </template>
                </Column>

                <Column v-if="canRefund" :header="t('emoticon.col.actions')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <Button v-if="data.status === 'PAID'" type="button" :label="t('emoticon.actRefund')" size="small" severity="danger" variant="outlined" :disabled="refundBusy" @click="openRefund(data)" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <EmoticonReasonDialog
            v-model:visible="refundOpen"
            :title="t('emoticon.refundTitle', { name: refundTarget?.packName ?? '', buyer: refundTarget ? nicknameOf(refundTarget.buyer) : '' })"
            :description="refundTarget ? t('emoticon.refundBody', { amount: refundTarget.amount.toLocaleString(locale) }) : ''"
            :label="t('emoticon.refundReason')"
            :confirm-label="t('emoticon.actRefund')"
            :presets="refundPresets"
            :busy="refundBusy"
            :error="refundError"
            :field-error="refundFieldError"
            @submit="submitRefund"
        >
            <Message v-if="refundTarget?.firstUsedAt" severity="warn" :closable="false"><span class="text-[13px]">{{ t('emoticon.refundUsedNote', { at: time.shortDateTime(refundTarget.firstUsedAt) }) }}</span></Message>
            <Message v-if="refundTarget?.settlementMonth" severity="info" :closable="false"><span class="text-[13px]">{{ t('emoticon.refundSettledNote', monthParams(refundTarget.settlementMonth)) }}</span></Message>
        </EmoticonReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonReasonPreset } from '~/components/emoticon/EmoticonReasonDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonSaleMemberRef, EmoticonSaleQuery, EmoticonSaleRow, EmoticonSaleStatus } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import { EMOTICON_SALE_STATUSES } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilMember from '~/utils/util-member';

interface SaleFilter {
    from: string;
    to: string;
    packId: string;
    status: string;
}

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('emoticon.salesTitle') });

const api = useEmoticonAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const route = useRoute();

/** 예외 환불 권한(후보 — 화면 메뉴와 같은 `emoticon.sales`) */
const canRefund = computed(() => can('emoticon.sales'));

const ids = { pack: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const statusOptions = computed(() => [{ value: '', label: t('common.all') }, ...EMOTICON_SALE_STATUSES.map((value) => ({ value, label: t(`emoticon.saleStatus.${value}`) }))]);

// ── 팩 고르기 — 유료로 판 적 있는 팩(판매 수가 있는 팩)만 ─
const paidPacks = ref<{ id: string; name: string }[]>([]);
const packsLoading = ref(false);
const packOptions = computed(() => [{ value: '', label: t('emoticon.filter.allPacks') }, ...paidPacks.value.map((p) => ({ value: p.id, label: p.name }))]);

async function loadPacks(): Promise<void> {
    packsLoading.value = true;
    try {
        paidPacks.value = (await api.packs({ source: 'CREATOR' })).items.filter((p) => p.salesCount !== null).map((p) => ({ id: p.id, name: p.name }));
    } catch {
        // 고르기 목록만 비고 검색은 된다(조용히)
        paidPacks.value = [];
    } finally {
        packsLoading.value = false;
    }
}

function defaultFilter(): SaleFilter {
    const p = time.value.parts(new Date().toISOString());
    const today = p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    const range = UtilDateRange.fill('MONTH1', today);
    return { from: range?.from ?? '', to: range?.to ?? '', packId: '', status: '' };
}

const filter = reactive<SaleFilter>({ ...defaultFilter(), packId: typeof route.query.packId === 'string' ? route.query.packId : '' });
const rows = ref<EmoticonSaleRow[]>([]);
const total = ref(0);
const paidAmount = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const applied = ref<Omit<EmoticonSaleQuery, 'page' | 'size'>>({});

function toQuery(f: SaleFilter): Omit<EmoticonSaleQuery, 'page' | 'size'> {
    const zoneId = values.value.zoneId;
    const out: Omit<EmoticonSaleQuery, 'page' | 'size'> = {};
    const from = f.from ? UtilMember.dayStart(f.from, zoneId) : undefined;
    // 서버의 `to` 는 미만이다 — 끝 날의 마지막 순간에 1ms 를 더해 다음 날 0시를 보낸다
    const dayEnd = f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined;
    const to = dayEnd ? new Date(Date.parse(dayEnd) + 1).toISOString() : undefined;
    if (from) out.from = from;
    if (to) out.to = to;
    if (f.packId) out.packId = f.packId;
    if (f.status) out.status = f.status as EmoticonSaleStatus;
    return out;
}

let ticket = 0;

async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.sales({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        paidAmount.value = found.paidAmount;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        paidAmount.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    applied.value = toQuery(filter);
    first.value = 0;
    void reload();
}

function resetAll(): void {
    Object.assign(filter, defaultFilter());
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

onMounted(() => {
    void loadPacks();
    search();
});

/** 셀프 환불 칸 — 가능하면 기한, 아니면 막힌 까닭 */
function selfRefundText(row: EmoticonSaleRow): string {
    const s = row.selfRefund;
    if (!s) return '';
    if (s.allowed) return t('emoticon.selfRefundUntil', { until: time.value.shortDateTime(s.until) });
    return s.blockedBy === 'USED' ? t('emoticon.selfRefundBlockedUsed') : t('emoticon.selfRefundBlockedExpired');
}

/** 판매 줄 회원의 이름 — 프로필이 없으면(서버가 null) 「(닉네임 없음)」 */
function nicknameOf(member: EmoticonSaleMemberRef): string {
    return member.nickname ?? t('emoticon.noNickname');
}

function monthParams(month: string): { year: number; month: number } {
    return UtilEmoticon.monthParts(month);
}

// ── 예외 환불 ─
const refundOpen = ref(false);
const refundTarget = ref<EmoticonSaleRow | null>(null);
const refundBusy = ref(false);
const refundError = ref('');
const refundFieldError = ref('');
const refundPresets = computed<EmoticonReasonPreset[]>(() =>
    (['PAYMENT_ERROR', 'MISTAKE', 'PACK_ISSUE'] as const).map((code) => ({ code, label: t(`emoticon.refundPreset.${code}`), text: t(`emoticon.refundPresetText.${code}`) })),
);

async function openRefund(row: EmoticonSaleRow): Promise<void> {
    if (refundBusy.value || !canRefund.value) return;
    // 이미 쓴 구매는 한 번 더 묻는다(셀프 환불은 막히는 경우 — 운영자 판단으로만)
    if (row.firstUsedAt) {
        const ok = await confirm({ title: t('emoticon.refundUsedTitle'), message: t('emoticon.refundUsedConfirm', { at: time.value.shortDateTime(row.firstUsedAt) }), confirmLabel: t('emoticon.refundUsedContinue'), cancelLabel: t('common.cancel'), tone: 'danger' });
        if (!ok) return;
    }
    refundTarget.value = row;
    refundError.value = '';
    refundFieldError.value = '';
    refundOpen.value = true;
}

async function submitRefund(reason: string): Promise<void> {
    const target = refundTarget.value;
    if (!target || refundBusy.value) return;
    refundBusy.value = true;
    refundError.value = '';
    refundFieldError.value = '';
    try {
        await api.refundSale(target.id, reason);
        refundOpen.value = false;
        toast.success(t('emoticon.refunded', { name: target.packName, buyer: nicknameOf(target.buyer) }));
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 404 || error.status === 409) {
            refundOpen.value = false;
            toast.show(error.message, { life: 6000 });
            void reload();
        } else if (error.field('reason')) {
            refundFieldError.value = error.field('reason')!;
        } else {
            refundError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
        }
    } finally {
        refundBusy.value = false;
    }
}
</script>
