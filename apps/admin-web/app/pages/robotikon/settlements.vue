<template>
    <!--
        로보티콘 › 작가 정산(`/robotikon/settlements`) — 유료 팩 판매의 작가 몫(판매액 − 환불 − 수수료)을 달마다 모아 작가에게 따로(현금) 지급한다(계약 emoticon-contract 가정 4·5).
        실제 API(S4 — 서버 `EmoticonSettlementAdminController`). 예정 금액은 조회 때 서버가 계산하고, 확정하면 굳는다. 정산 줄 ID(`yyyyMM-작가회원번호`)는 받은 값을 그대로 넘긴다.
        달 고르기(판매 달 — 지급은 다음 달 정산일, 기본은 지급을 앞둔 달) · 합계 칸(판매 · 환불 · 부가세 · 수수료 · 원천징수 · 지급 · 다음 달로 넘김) · 표.
        한 줄: [고르기] · 작가 · 개인/사업자/정산 정보 없음 · 판매 수 · 판매액 · 환불 · 수수료 · 원천징수 · 지급액 · 이월(받음/넘김 · 음수면 차감) · 상태(예정/확정/지급 완료/보류) · 정산 계좌(은행 이름 · 가림 · [보기]).
        금액 칸은 음수일 수 있다(확정 뒤 환불로 판매보다 환불이 많은 달 — 빼는 칸은 부호가 뒤집힌다, 음수 작가 몫은 다음 달에서 뺀다).
        처리: 고른 줄 [확정](예정이면서 확정 가능 시각이 지난 줄만 고를 수 있다 — 한 번에 여러 줄, 200줄까지). 서버가 맞지 않는 줄을 건너뛰면 까닭(`skippedItems`)을 표 위에 줄마다 보인다
          (찾을 수 없음 · 예정 아님 · 환불 기간 · 앞 달 미확정 · 정산 정보 없음). 서랍에서 [보류](사유)·[보류 풀기](보류 전 상태로 — 확정했던 줄은 확정)·[지급 완료 기록](이체일·메모 — 실제 이체는 사이트 밖).
        정산 계좌 전체 보기: 사유 대화상자(`MemberReasonDialog` — 탈퇴 회원 연락처 열람과 같은 모양) → 정산 줄 주소 `POST …/settlements/{id}/account/reveal` → 서버가 감사·처리 기록(`ACCOUNT_VIEWED`)을 남기고
          10분만 준다 → 저절로 가린다. 값은 이 화면 메모리에만 둔다. 열람 뒤 서랍을 다시 읽어 처리 기록 줄을 보인다.
        권한: 화면·처리 `emoticon.settlement`(메뉴) · 계좌 전체 보기 `emoticon.payout.pii`(서버 `Permission`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('emoticon.settlementsTitle')" :subtitle="t('emoticon.settlementsSubtitle')" />

        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <div class="flex flex-wrap items-center gap-3">
                <label :for="ids.month" class="text-[13px] text-muted-color">{{ t('emoticon.filter.month') }}</label>
                <Select v-model="month" :input-id="ids.month" :options="monthOptions" option-label="label" option-value="value" :loading="loading" class="w-56" @update:model-value="selectMonth" />
                <span v-if="list" class="text-xs text-muted-color">{{ t('emoticon.monthPayoutLine', { date: payoutDateText, day: list.rules.settlementDay }) }}</span>
            </div>
            <dl v-if="list" class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-7">
                <div v-for="cell in totalCells" :key="cell.key" class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ cell.label }}</dt>
                    <dd class="m-0 mt-0.5 text-[13px] font-semibold tabular-nums" :class="cell.tone">{{ cell.value }}</dd>
                </div>
            </dl>
            <ul v-if="list" class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settlementRuleLine', { rate: list.rules.feeRatePercent, refundDays: list.rules.refundDays, minimum: list.rules.payoutMinimum.toLocaleString(locale) }) }}</span></li>
                <li class="flex items-start gap-2"><i class="pi pi-send mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settlementTransferNote') }}</span></li>
                <li class="flex items-start gap-2" data-testid="emoticon-settlement-business-day"><i class="pi pi-calendar mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('emoticon.settlementBusinessDayNote') }}</span></li>
            </ul>
        </div>

        <Message v-if="skippedLines.length" severity="warn" closable data-testid="emoticon-settlement-skipped" @close="skipped = []">
            <div class="flex flex-col gap-1 text-[13px]">
                <b class="font-semibold">{{ t('emoticon.skippedTitle', { count: skippedLines.length }) }}</b>
                <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
                    <li v-for="line in skippedLines" :key="line.id">{{ line.text }}</li>
                </ul>
            </div>
        </Message>

        <div v-if="canManage && selectedIds.length" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) bg-(--p-content-hover-background) px-3 py-2">
            <span class="text-[13px]">{{ t('emoticon.selectedCount', { count: selectedIds.length }) }}</span>
            <Button type="button" :label="t('emoticon.actConfirmSelected')" icon="pi pi-check" size="small" :loading="working === 'confirm'" :disabled="working !== null" @click="confirmSelected" />
            <Button type="button" :label="t('emoticon.clearSelection')" size="small" severity="secondary" variant="text" :disabled="working !== null" @click="selectedIds = []" />
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="rows" data-key="id" :loading="loading" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable row-hover class="min-w-0 cursor-pointer" @row-click="onRowClick">
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('emoticon.forbidden') : t('emoticon.settlementsLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="loading" @retry="load(month)" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-wallet" :title="t('emoticon.settlementsEmpty')" />
                </template>

                <Column v-if="canManage" style="width: 3rem">
                    <template #header>
                        <Checkbox :model-value="allSelected" binary :disabled="!confirmableIds.length" :aria-label="t('emoticon.selectAllConfirmable')" @update:model-value="toggleAll" />
                    </template>
                    <template #body="{ data }">
                        <Checkbox
                            :model-value="selectedIds.includes(data.id)"
                            binary
                            :disabled="!confirmable(data)"
                            :aria-label="t('emoticon.selectRow', { name: data.creator.nickname })"
                            :title="confirmable(data) ? undefined : t('emoticon.notConfirmable')"
                            @click.stop
                            @update:model-value="(v: boolean) => toggleRow(data.id, v)"
                        />
                    </template>
                </Column>

                <Column :header="t('emoticon.col.creator')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <NuxtLink v-if="!data.creator.withdrawn" :to="UtilEmoticon.memberLink(data.creator.memberNo)" class="text-[13px] text-primary hover:underline" @click.stop>{{ data.creator.nickname }}</NuxtLink>
                        <span v-else class="text-[13px] text-muted-color">{{ data.creator.nickname }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.payeeType')" style="min-width: 5.5rem">
                    <template #body="{ data }">
                        <span class="text-[13px]" :class="data.payeeType ? '' : 'text-amber-700 dark:text-amber-400'">{{ UtilEmoticonMessages.payeeType(t, data.payeeType) }}</span>
                    </template>
                </Column>

                <Column v-for="col in moneyColumns" :key="col.key" :header="col.label" style="min-width: 6.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums" :class="col.tone(data)">{{ col.value(data) }}</span>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.carry')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right text-[12px] tabular-nums">
                            <div v-if="data.carriedIn > 0" class="text-muted-color">{{ t('emoticon.carriedInShort', { amount: data.carriedIn.toLocaleString(locale) }) }}</div>
                            <div v-else-if="data.carriedIn < 0" class="text-amber-700 dark:text-amber-400">{{ t('emoticon.carriedInDeductShort', { amount: (-data.carriedIn).toLocaleString(locale) }) }}</div>
                            <div v-if="data.carriedOut > 0" class="text-amber-700 dark:text-amber-400">{{ t('emoticon.carriedOutShort', { amount: data.carriedOut.toLocaleString(locale) }) }}</div>
                            <div v-else-if="data.carriedOut < 0" class="text-amber-700 dark:text-amber-400">{{ t('emoticon.carriedOutDeductShort', { amount: (-data.carriedOut).toLocaleString(locale) }) }}</div>
                            <span v-if="!data.carriedIn && !data.carriedOut" class="text-muted-color">—</span>
                        </div>
                    </template>
                </Column>

                <Column :header="t('emoticon.col.status')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag :value="t(`emoticon.settlementStatus.${data.status}`)" :severity="UtilEmoticon.settlementSeverity(data.status)" />
                    </template>
                </Column>

                <Column :header="t('emoticon.col.account')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span v-if="!data.account" class="text-[12px] text-red-700 dark:text-red-400">{{ t('emoticon.accountMissing') }}</span>
                        <div v-else-if="revealedOf(data.id)" class="flex flex-col text-[12px]">
                            <span>{{ bankName(revealedOf(data.id)!.bank) }} <code>{{ revealedOf(data.id)!.number }}</code></span>
                            <span class="text-muted-color">{{ revealedOf(data.id)!.holder }}</span>
                        </div>
                        <div v-else class="flex items-center gap-1.5 text-[12px]">
                            <span class="min-w-0 truncate">{{ bankName(data.account.bank) }} <code class="text-muted-color">{{ data.account.maskedNumber }}</code> · {{ data.account.holderMasked }}</span>
                            <Button v-if="canReveal" type="button" icon="pi pi-eye" size="small" severity="secondary" variant="text" :aria-label="t('emoticon.accountReveal')" :title="t('emoticon.accountReveal')" @click.stop="openReveal(data)" />
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <EmoticonSettlementDrawer
            v-model:visible="drawerOpen"
            :summary="selected"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="working !== null"
            :can-manage="canManage"
            :can-reveal="canReveal"
            :revealed="detail ? revealedOf(detail.id) : null"
            :reveal-remaining="detail ? remainingOf(detail.id) : 0"
            :now="now"
            @retry="loadDetail"
            @confirm="confirmOne"
            @hold="openHold"
            @unhold="unhold"
            @paid="openPaid"
            @reveal="detail && openReveal(detail)"
            @mask="detail && mask(detail.id)"
        />

        <EmoticonReasonDialog
            v-model:visible="holdOpen"
            :title="t('emoticon.holdTitle', { name: detail?.creator.nickname ?? '' })"
            :description="t('emoticon.holdBody')"
            :label="t('emoticon.holdReason')"
            :confirm-label="t('emoticon.actHold')"
            :presets="holdPresets"
            :busy="working === 'hold'"
            :error="dialogError"
            :field-error="dialogFieldError"
            @submit="submitHold"
        />

        <MemberReasonDialog
            v-model:visible="revealOpen"
            :title="t('emoticon.revealTitle', { name: revealTarget?.creator.nickname ?? '' })"
            :label="t('emoticon.revealReason')"
            :submit-label="t('emoticon.accountReveal')"
            submit-icon="pi pi-eye"
            :notice="t('emoticon.revealNotice')"
            :busy="working === 'reveal'"
            :error="dialogError"
            :field-error="dialogFieldError"
            @submit="submitReveal"
        />

        <Dialog v-model:visible="paidOpen" modal :draggable="false" :closable="working !== 'paid'" :header="t('emoticon.paidTitle', { name: detail?.creator.nickname ?? '' })" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }">
            <form :id="ids.paidForm" class="flex flex-col gap-4" novalidate @submit.prevent="submitPaid">
                <p v-if="detail" class="m-0 text-sm leading-relaxed">{{ t('emoticon.paidBody', { amount: detail.payoutAmount.toLocaleString(locale) }) }}</p>
                <!-- 이체일은 평일 업무일만(후속 결정 4) — 달력은 토·일을 막고, 공휴일은 목록 API 가 없어 서버 `400 fields.transferDate` 문구를 칸 아래에 보인다 -->
                <LabeledField :label="t('emoticon.paidDate')" :help="t('emoticon.paidDateBusinessDay')" :error="paidDateError">
                    <template #default="{ id, describedBy, invalid }">
                        <AppDatePicker v-model="paidDate" :input-id="id" :invalid="invalid" :described-by="describedBy" :disabled-days="WEEKEND_DAYS" @update:model-value="onPaidDateChange" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('emoticon.paidMemo')" :hint="t('emoticon.paidMemoHint')" :error="paidFieldErrors.memo ?? ''">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="paidMemo" rows="2" auto-resize maxlength="200" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
                <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" />{{ t('emoticon.paidNotice') }}</p>
                <Message v-if="dialogError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ dialogError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="working === 'paid'" @click="paidOpen = false" />
                <Button type="submit" :form="ids.paidForm" :label="t('emoticon.actPaid')" icon="pi pi-check" :loading="working === 'paid'" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { EmoticonReasonPreset } from '~/components/emoticon/EmoticonReasonDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonPayoutAccountReveal, EmoticonSettlementBulkResult, EmoticonSettlementDetail, EmoticonSettlementList, EmoticonSettlementRow } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('emoticon.settlementsTitle') });

const api = useEmoticonAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();
const ids = { month: useId(), paidForm: useId() };

/** 확정·보류·지급 기록(메뉴와 같은 `emoticon.settlement`) · 계좌 전체 보기 `emoticon.payout.pii` */
const canManage = computed(() => can('emoticon.settlement'));
const canReveal = computed(() => can('emoticon.payout.pii'));

const month = ref<string | null>(typeof route.query.month === 'string' ? route.query.month : null);
const list = ref<EmoticonSettlementList | null>(null);
const rows = computed(() => list.value?.items ?? []);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

/** 지금 시각 — 확정 가능 판정·계좌 가림 남은 시간. 1초마다 */
const now = ref(Date.now());
const timer = setInterval(() => {
    now.value = Date.now();
    for (const [id, r] of Object.entries(revealed.value)) if (Date.parse(r.expiresAt) <= now.value) mask(id);
}, 1000);
onScopeDispose(() => clearInterval(timer));

function monthLabel(value: string): string {
    return t('emoticon.monthLabel', UtilEmoticon.monthParts(value));
}
const monthOptions = computed(() => (list.value?.months ?? (month.value ? [month.value] : [])).map((value) => ({ value, label: monthLabel(value) })));
const payoutDateText = computed(() => rows.value[0]?.payoutDate ?? '—');

/** 부호 붙은 원(음수면 −) — 확정 뒤 환불로 음수가 된 달 */
function won(value: number): string {
    return UtilEmoticonMessages.signedWon(t, value, locale.value);
}
/** 빼는 금액(양수 −, 음수 +, 0 은 —) */
function minus(value: number): string {
    return UtilEmoticonMessages.deduction(t, value, locale.value, '—');
}

const { localizedName } = useLocalizedName();
/** 은행 코드 → 이름 */
function bankName(code: string): string {
    return UtilEmoticonMessages.bankName(code, localizedName);
}

const totalCells = computed(() => {
    const s = list.value?.totals;
    if (!s) return [];
    return [
        { key: 'sales', label: t('emoticon.calc.sales'), value: won(s.salesAmount), tone: '' },
        { key: 'refund', label: t('emoticon.calc.refund'), value: won(s.refundAmount), tone: s.refundAmount ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color' },
        { key: 'vat', label: t('emoticon.calc.vat'), value: won(s.vatAmount), tone: 'text-muted-color' },
        { key: 'fee', label: t('emoticon.calc.fee'), value: won(s.feeAmount), tone: '' },
        { key: 'withholding', label: t('emoticon.calc.withholding'), value: won(s.withholdingAmount), tone: '' },
        { key: 'payout', label: t('emoticon.calc.payout'), value: won(s.payoutAmount), tone: 'text-primary' },
        { key: 'carriedOut', label: t('emoticon.calc.carriedOut'), value: won(s.carriedOut), tone: s.carriedOut ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color' },
    ];
});

const moneyColumns = computed(() => [
    { key: 'salesCount', label: t('emoticon.col.salesCount'), value: (r: EmoticonSettlementRow) => r.salesCount.toLocaleString(locale.value), tone: () => '' },
    { key: 'sales', label: t('emoticon.col.salesAmount'), value: (r: EmoticonSettlementRow) => won(r.salesAmount), tone: () => '' },
    { key: 'refund', label: t('emoticon.col.refundAmount'), value: (r: EmoticonSettlementRow) => minus(r.refundAmount), tone: (r: EmoticonSettlementRow) => (r.refundAmount ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color') },
    { key: 'vat', label: t('emoticon.col.vat'), value: (r: EmoticonSettlementRow) => minus(r.vatAmount), tone: () => 'text-muted-color' },
    { key: 'fee', label: t('emoticon.col.fee'), value: (r: EmoticonSettlementRow) => minus(r.feeAmount), tone: () => 'text-muted-color' },
    { key: 'withholding', label: t('emoticon.col.withholding'), value: (r: EmoticonSettlementRow) => minus(r.withholdingAmount), tone: () => 'text-muted-color' },
    { key: 'payout', label: t('emoticon.col.payout'), value: (r: EmoticonSettlementRow) => won(r.payoutAmount), tone: (r: EmoticonSettlementRow) => (r.payoutAmount ? 'font-semibold' : 'text-muted-color') },
]);

let ticket = 0;

async function load(target: string | null): Promise<void> {
    const mine = ++ticket;
    loading.value = true;
    loadError.value = null;
    try {
        const found = await api.settlements(target);
        if (mine !== ticket) return;
        list.value = found;
        month.value = found.month;
        selectedIds.value = selectedIds.value.filter((id) => found.items.some((r) => r.id === id && confirmable(r)));
    } catch (e) {
        if (mine !== ticket) return;
        list.value = null;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) loading.value = false;
    }
}

function selectMonth(value: string): void {
    selectedIds.value = [];
    skipped.value = [];
    void router.replace({ query: { ...route.query, month: value, id: undefined } });
    void load(value);
}

// ── 고르기·확정 ─
const selectedIds = ref<string[]>([]);

function confirmable(row: EmoticonSettlementRow): boolean {
    return row.status === 'ESTIMATED' && now.value >= Date.parse(row.closesAt);
}
const confirmableIds = computed(() => rows.value.filter(confirmable).map((r) => r.id));
const allSelected = computed(() => confirmableIds.value.length > 0 && confirmableIds.value.every((id) => selectedIds.value.includes(id)));

function toggleRow(id: string, on: boolean): void {
    selectedIds.value = on ? [...new Set([...selectedIds.value, id])] : selectedIds.value.filter((x) => x !== id);
}
function toggleAll(on: boolean): void {
    selectedIds.value = on ? [...confirmableIds.value] : [];
}

const working = ref<'confirm' | 'hold' | 'unhold' | 'paid' | 'reveal' | null>(null);
const dialogError = ref('');
const dialogFieldError = ref('');

/** 마지막 확정에서 서버가 건너뛴 줄(까닭 코드) — 표 위 안내. 닫거나 다음 확정 때 지운다 */
const skipped = ref<EmoticonSettlementBulkResult['skippedItems']>([]);
/** 건너뛴 줄 한 줄씩 — 작가 이름은 지금 표에서 찾는다(다른 달 줄이면 정산 번호) */
const skippedLines = computed(() =>
    skipped.value.map((s) => {
        const name = rows.value.find((r) => r.id === s.id)?.creator.nickname ?? s.id;
        return { id: s.id, text: t('emoticon.skippedLine', { name, reason: t(`emoticon.skipReason.${s.reason}`) }) };
    }),
);

async function runConfirm(targetIds: string[]): Promise<void> {
    if (!targetIds.length || working.value) return;
    const total = rows.value.filter((r) => targetIds.includes(r.id)).reduce((sum, r) => sum + r.payoutAmount, 0);
    const ok = await confirm({
        title: t('emoticon.confirmTitle', { count: targetIds.length }),
        message: t('emoticon.confirmBody', { amount: total.toLocaleString(locale.value) }),
        confirmLabel: t('emoticon.actConfirm'),
        cancelLabel: t('common.cancel'),
    });
    if (!ok) return;
    working.value = 'confirm';
    skipped.value = [];
    try {
        const res = await api.confirmSettlements([...targetIds]);
        skipped.value = res.skippedItems;
        toast.success(res.skipped ? t('emoticon.confirmedSkipped', { confirmed: res.confirmed, skipped: res.skipped }) : t('emoticon.confirmed', { confirmed: res.confirmed }));
        selectedIds.value = [];
        void load(month.value);
        if (drawerOpen.value) void loadDetail();
    } catch (e) {
        // `400 fields.ids` — 0개·200개 넘음(서버 문구)
        const error = toApiError(e);
        toast.error(`${t('emoticon.actionFailed')} — ${error.field('ids') ?? error.message}`, 8000);
    } finally {
        working.value = null;
    }
}

function confirmSelected(): void {
    void runConfirm(selectedIds.value.filter((id) => confirmableIds.value.includes(id)));
}

function confirmOne(): void {
    if (detail.value) void runConfirm([detail.value.id]);
}

// ── 서랍 ─
const drawerOpen = ref(false);
const selected = ref<EmoticonSettlementRow | null>(null);
const selectedId = ref<string | null>(null);
const detail = ref<EmoticonSettlementDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
let detailTicket = 0;

async function loadDetail(): Promise<void> {
    const id = selectedId.value;
    if (!id) return;
    const mine = ++detailTicket;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.settlement(id);
        if (mine === detailTicket) detail.value = found;
    } catch (e) {
        if (mine === detailTicket) detailError.value = toApiError(e);
    } finally {
        if (mine === detailTicket) detailLoading.value = false;
    }
}

function openId(id: string, row: EmoticonSettlementRow | null): void {
    selectedId.value = id;
    selected.value = row;
    detail.value = null;
    drawerOpen.value = true;
    if (route.query.id !== id) void router.replace({ query: { ...route.query, id } });
    void loadDetail();
}

function onRowClick(e: DataTableRowClickEvent): void {
    const target = e.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input, .p-checkbox')) return;
    const row = e.data as EmoticonSettlementRow;
    openId(row.id, row);
}

watch(drawerOpen, (visible) => {
    if (!visible && route.query.id) void router.replace({ query: { ...route.query, id: undefined } });
});

onMounted(() => {
    void load(month.value);
    if (typeof route.query.id === 'string' && route.query.id) openId(route.query.id, null);
});

/** 처리 뒤 — 서랍은 새 상세로, 표는 다시 읽기 */
function afterAction(next: EmoticonSettlementDetail): void {
    detail.value = next;
    void load(month.value);
}

/** 상태가 이미 바뀐 줄(409)·없는 줄(404) — 토스트 후 다시 읽는다 */
function handleStale(e: ApiError): boolean {
    if (e.status !== 404 && e.status !== 409) return false;
    toast.show(e.message, { life: 6000 });
    void load(month.value);
    void loadDetail();
    return true;
}

// 보류
const holdOpen = ref(false);
const holdPresets = computed<EmoticonReasonPreset[]>(() =>
    (['ACCOUNT', 'RIGHTS', 'TAX'] as const).map((code) => ({ code, label: t(`emoticon.holdPreset.${code}`), text: t(`emoticon.holdPresetText.${code}`) })),
);

function openHold(): void {
    if (!detail.value || working.value) return;
    dialogError.value = '';
    dialogFieldError.value = '';
    holdOpen.value = true;
}

async function submitHold(reason: string): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    working.value = 'hold';
    dialogError.value = '';
    dialogFieldError.value = '';
    try {
        afterAction(await api.holdSettlement(target.id, reason));
        holdOpen.value = false;
        toast.success(t('emoticon.held', { name: target.creator.nickname }));
    } catch (e) {
        const error = toApiError(e);
        if (handleStale(error)) holdOpen.value = false;
        else if (error.field('reason')) dialogFieldError.value = error.field('reason')!;
        else dialogError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = null;
    }
}

async function unhold(): Promise<void> {
    const target = detail.value;
    if (!target || working.value) return;
    const ok = await confirm({ title: t('emoticon.unholdTitle', { name: target.creator.nickname }), message: t('emoticon.unholdBody'), confirmLabel: t('emoticon.actUnhold'), cancelLabel: t('common.cancel') });
    if (!ok) return;
    working.value = 'unhold';
    try {
        afterAction(await api.unholdSettlement(target.id));
        toast.success(t('emoticon.unheld', { name: target.creator.nickname }));
    } catch (e) {
        const error = toApiError(e);
        if (!handleStale(error)) toast.error(`${t('emoticon.actionFailed')} — ${error.message}`, 8000);
    } finally {
        working.value = null;
    }
}

// 지급 완료 기록
const paidOpen = ref(false);
const paidDate = ref('');
const paidMemo = ref('');
const paidSubmitted = ref(false);
const paidFieldErrors = ref<Record<string, string>>({});

/** 달력에서 막는 요일 — 일(0)·토(6). 공휴일은 서버가 막는다(`400 fields.transferDate`) */
const WEEKEND_DAYS = [0, 6];

function kstToday(): string {
    return new Date(Date.now() + 9 * 3_600_000).toISOString().slice(0, 10);
}

/** 달력 날짜 글자(`YYYY-MM-DD`)가 토·일인가 — 입력칸에 직접 쓴 날도 다시 본다(시간대와 무관한 달력 날짜로 센다) */
function weekend(ymd: string): boolean {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
    if (!match) return false;
    const day = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]))).getUTCDay();
    return WEEKEND_DAYS.includes(day);
}

/** 오늘(KST)이 토·일이면 그 전 금요일 — 지급 기록 칸의 처음 값(이체일은 오늘이나 그 전이어야 한다) */
function lastWeekday(): string {
    let ymd = kstToday();
    for (let i = 0; i < 2 && weekend(ymd); i++) ymd = new Date(Date.parse(`${ymd}T00:00:00Z`) - 86_400_000).toISOString().slice(0, 10);
    return ymd;
}

/** 이체일 칸 아래 한 줄 — 서버 문구(공휴일·오늘 뒤 등) → 비었음 → 토·일 */
const paidDateError = computed(() => {
    if (paidFieldErrors.value.transferDate) return paidFieldErrors.value.transferDate;
    if (paidSubmitted.value && !paidDate.value) return t('emoticon.paidDateRequired');
    if (paidDate.value && weekend(paidDate.value)) return t('emoticon.paidDateWeekend');
    return '';
});

/** 날을 바꾸면 서버가 준 이체일 문구는 낡았다 — 지운다 */
function onPaidDateChange(): void {
    if (!paidFieldErrors.value.transferDate) return;
    const next = { ...paidFieldErrors.value };
    delete next.transferDate;
    paidFieldErrors.value = next;
}

function openPaid(): void {
    if (!detail.value || working.value) return;
    paidDate.value = lastWeekday();
    paidMemo.value = '';
    paidSubmitted.value = false;
    paidFieldErrors.value = {};
    dialogError.value = '';
    paidOpen.value = true;
}

async function submitPaid(): Promise<void> {
    const target = detail.value;
    paidSubmitted.value = true;
    if (!target || working.value || !paidDate.value || weekend(paidDate.value)) return;
    working.value = 'paid';
    dialogError.value = '';
    paidFieldErrors.value = {};
    try {
        afterAction(await api.markSettlementPaid(target.id, { transferDate: paidDate.value, memo: paidMemo.value.trim() }));
        paidOpen.value = false;
        toast.success(t('emoticon.paidDone', { name: target.creator.nickname }));
    } catch (e) {
        const error = toApiError(e);
        if (handleStale(error)) paidOpen.value = false;
        else if (error.code === 'VALIDATION' && error.fields) paidFieldErrors.value = error.fields;
        else dialogError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = null;
    }
}

// 정산 계좌 전체 보기 — 값은 이 화면 메모리에만, 정해진 시각에 지운다
const revealed = ref<Record<string, EmoticonPayoutAccountReveal>>({});
const revealOpen = ref(false);
const revealTarget = ref<EmoticonSettlementRow | null>(null);

function revealedOf(id: string): EmoticonPayoutAccountReveal | null {
    return revealed.value[id] ?? null;
}
function remainingOf(id: string): number {
    const r = revealed.value[id];
    return r ? Math.max(0, Math.ceil((Date.parse(r.expiresAt) - now.value) / 1000)) : 0;
}
function mask(id: string): void {
    const { [id]: _drop, ...rest } = revealed.value;
    revealed.value = rest;
}

function openReveal(row: EmoticonSettlementRow): void {
    if (!canReveal.value || working.value) return;
    revealTarget.value = row;
    dialogError.value = '';
    dialogFieldError.value = '';
    revealOpen.value = true;
}

async function submitReveal(reason: string): Promise<void> {
    const target = revealTarget.value;
    if (!target || working.value) return;
    working.value = 'reveal';
    dialogError.value = '';
    dialogFieldError.value = '';
    try {
        const found = await api.revealSettlementAccount(target.id, reason);
        revealed.value = { ...revealed.value, [target.id]: found };
        revealOpen.value = false;
        // 서랍을 다시 읽는다 — 정산 줄 원문 보기가 남긴 처리 기록 `ACCOUNT_VIEWED` 가 보인다
        if (drawerOpen.value && detail.value?.id === target.id) void loadDetail();
    } catch (e) {
        const error = toApiError(e);
        if (error.field('reason')) dialogFieldError.value = error.field('reason')!;
        else dialogError.value = `${t('emoticon.actionFailed')} — ${error.message}`;
    } finally {
        working.value = null;
    }
}
</script>
