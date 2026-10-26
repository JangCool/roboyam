<template>
    <!--
        포인트 상점 › 구매 내역(`/point-shop/purchases`) — 모든 회원의 포인트 상점 구매를 조건으로 찾아보고, 셀프 환불 조건 밖의 구매를 예외 환불한다(commerce.md 7-3, 계약 commerce-stage1 「1-2 화면 목업」).
        실제 서버(`usePointShopAdminApi`)를 부른다. 구매 번호 내림차순, 서버 쪽 나누기(page 0 · size 20 기본, 상한 100).
        권한: 조회 `pointshop.read`(메뉴), 예외 환불 `pointshop.refund`(SENSITIVE — 없으면 단추가 없다). 권한 코드는 `util-permission-catalog.ts`.
        조회 조건(기간 · 상태 · 종류 · 회원)은 「검색」(또는 Enter)을 눌렀을 때만 서버로 가고, 쪽은 마지막으로 검색한 조건으로 다시 묻는다(서버 쪽 나누기). 기간 시작 값은 최근 1개월이고 「초기화」도 그 기간으로. 날짜는 표시 시간대의 하루 경계(UTC ISO 순간)로 바꿔 보낸다 — 서버의 `to` 는 미만이라 끝 날의 다음 날 0시를 보낸다.
        한 줄: 구매 시각 · 회원(닉네임을 누르면 그 회원만) · 품목(산 때 이름·코드, 아바타는 썸네일 조각) · 가격(산 때) · 사용(장착·사용·「프로필로 고름」 시각) · 셀프 환불(가능 ~기한 / 불가 + 이유: 사용함 · 기한 지남) · 상태(완료 / 환불됨 + 셀프·예외, 처리자) · [예외 환불].
        셀프 환불 판정은 서버가 계산해 준다(`selfRefund` — 기한은 **지금** 설정 값으로 본다는 가정, 계약 미확정).
        [예외 환불](`pointshop.refund`, 완료 줄만) → `PointShopRefundDialog`(사유 5~200자) → 확인 대화상자 → 저장 → 토스트 → 지금 쪽 다시 읽기. 서버가 감사(돈 관련 5년)를 남긴다.
        `409 POINT_SHOP_ALREADY_REFUNDED`(다른 운영자·회원이 먼저)·`409 POINT_SHOP_REFUND_PENDING`(대기 중인 환불 신청이 있다)은 대화상자를 닫고 토스트로 알린 뒤 다시 읽는다.
        정지 중 환불 신청(계약 「1-2 정지 중 환불 신청」): 거르기 「환불 신청」(전체·대기·수락·거절) — 주소 `?refundRequest=PENDING`(운영자 알림 `POINT_SHOP_REFUND_REQUESTED` 의 링크)로 들어오면 그 거르기로 바로 검색한다.
        대기 줄은 「환불 신청 대기 · 신청 시각」 딱지와 [수락]·[거절](`pointshop.refund`) — 대기 줄에는 [예외 환불]이 없다. [수락] → 확인창(회원·품목·돌려줄 포인트 · 신청 순간 판정) → 토스트 → 다시 읽기,
        [거절] → `PointShopRefundDialog mode="reject"`(사유 5~200자) → 토스트 → 다시 읽기. `409 POINT_SHOP_REFUND_REQUEST_NOT_PENDING`·`404 POINT_SHOP_REFUND_REQUEST_NOT_FOUND` 는 서버 문구 토스트 + 다시 읽기.
        결정한 줄: 수락은 환불 종류 `REQUEST`(「신청 수락 · 처리자」), 거절은 「신청 거절: 사유」. 조건이 틀리면 서버가 `400 fields.<조건>` — 그 줄 아래에 서버 문구.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('pointShop.purchasesTitle')" :subtitle="t('pointShop.purchasesSubtitle')" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('pointShop.filter.searchLabel')" @submit.prevent="search">
            <FilterRow :label="t('pointShop.filter.period')" :error="errorOf('from', 'to')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('pointShop.filter.from')" :to-label="t('pointShop.filter.to')" />
            </FilterRow>

            <FilterRow :label="t('pointShop.filter.status')" :error="errorOf('status')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.status" :options="statusOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow :label="t('pointShop.filter.kind')" :error="errorOf('kind')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.kind" :options="kindOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow :label="t('pointShop.filter.refundRequest')" :error="errorOf('refundRequest')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.refundRequest" :options="requestOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow :label="t('pointShop.filter.member')" :label-for="ids.q" :error="errorOf('q')">
                <InputText :id="ids.q" v-model="filter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('pointShop.filter.memberPlaceholder')" class="w-full max-w-sm" />
            </FilterRow>

            <p v-if="otherErrors" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ otherErrors }}</p>

            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('pointShop.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('pointShop.forbidden') : t('pointShop.purchasesLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="hasSearchErrors" icon="pi pi-exclamation-circle" :title="t('pointShop.filterRejected')" :description="t('pointShop.filterRejectedHint')" />
                    <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('pointShop.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearAll" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-shopping-bag" :title="t('pointShop.purchasesEmpty')" :description="t('pointShop.purchasesEmptyHint')" />
                </template>

                <Column field="createdAt" :header="t('pointShop.col.purchasedAt')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.createdAt) }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.member')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <span v-if="data.member.withdrawn || !data.member.id" class="block truncate text-[13px] text-muted-color">{{ data.member.nickname }}</span>
                            <button
                                v-else
                                type="button"
                                class="block max-w-full cursor-pointer truncate text-left text-[13px] hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                :title="t('pointShop.onlyThisMember', { name: data.member.nickname })"
                                :aria-label="t('pointShop.onlyThisMember', { name: data.member.nickname })"
                                @click="onlyMember(data)"
                            >
                                {{ data.member.nickname }}
                            </button>
                            <code v-if="data.member.id" class="block truncate text-[11px] tabular-nums text-muted-color">{{ data.member.id }}</code>
                        </div>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.item')" style="min-width: 13rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 items-center gap-2">
                        <!-- 아바타 구매는 썸네일 조각 -->
                        <PointShopAvatarThumb v-if="data.avatarId" :avatar-id="data.avatarId" size="sm" />
                        <div class="min-w-0">
                            <div class="truncate text-[13px]">{{ data.itemName }}</div>
                            <div class="truncate text-[11px] text-muted-color">
                                <code>{{ data.itemCode }}</code> · {{ t(`pointShop.kind.${data.kind}`) }}<template v-if="data.durationDays !== null"> · {{ t('pointShop.days', { days: data.durationDays }) }}</template>
                            </div>
                        </div>
                        </div>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.price')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ t('pointShop.pricePoint', { price: data.pricePoint.toLocaleString(locale) }) }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.used')" style="min-width: 8.5rem">
                    <template #body="{ data }">
                        <div v-if="data.firstUsedAt" class="min-w-0">
                            <div class="text-[13px]">{{ usedLabel(data.kind) }}</div>
                            <div class="text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(data.firstUsedAt) }}</div>
                        </div>
                        <span v-else class="text-[13px] text-muted-color">{{ t('pointShop.notUsed') }}</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.selfRefund')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div v-if="data.selfRefund" class="flex min-w-0 flex-col items-start gap-0.5">
                            <Tag :value="data.selfRefund.allowed ? t('pointShop.selfRefund.allowed') : t('pointShop.selfRefund.blocked')" :severity="data.selfRefund.allowed ? 'success' : 'secondary'" />
                            <span class="text-[11px] tabular-nums text-muted-color">
                                {{ data.selfRefund.allowed ? t('pointShop.selfRefund.until', { until: time.shortDateTime(data.selfRefund.until) }) : data.selfRefund.blockedBy ? UtilPointShopMessages.selfRefundBlock(t, data.selfRefund.blockedBy) : '' }}
                            </span>
                        </div>
                        <span v-else class="text-[13px] text-muted-color">—</span>
                    </template>
                </Column>

                <Column :header="t('pointShop.col.status')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-col items-start gap-0.5">
                            <Tag v-if="data.status === 'COMPLETED'" :value="t('pointShop.status.COMPLETED')" severity="info" />
                            <Tag v-else :value="t('pointShop.status.REFUNDED')" severity="warn" />
                            <template v-if="data.refund">
                                <span class="text-[11px] text-muted-color" :title="data.refund.reason ?? undefined">{{ refundKindText(data) }} · {{ time.shortDateTime(data.refund.refundedAt) }}</span>
                                <span v-if="data.refund.reason" class="max-w-[14rem] truncate text-[11px] text-muted-color" :title="data.refund.reason">{{ data.refund.reason }}</span>
                            </template>
                            <!-- 정지 중 환불 신청 — 대기 딱지 / 거절 사유(수락은 위 환불 종류 REQUEST 로 보인다) -->
                            <template v-if="data.refundRequest">
                                <Tag v-if="data.refundRequest.status === 'PENDING'" :value="t('pointShop.request.pendingTag')" icon="pi pi-hourglass" severity="danger" />
                                <span v-if="data.refundRequest.status === 'PENDING'" class="text-[11px] tabular-nums text-muted-color">{{ t('pointShop.request.requestedLine', { at: time.shortDateTime(data.refundRequest.requestedAt) }) }}</span>
                                <span v-else-if="data.refundRequest.status === 'REJECTED'" class="max-w-[14rem] truncate text-[11px] text-muted-color" :title="data.refundRequest.rejectReason ?? undefined">
                                    {{ t('pointShop.request.rejectedLine', { reason: data.refundRequest.rejectReason ?? '—' }) }}
                                </span>
                            </template>
                        </div>
                    </template>
                </Column>

                <Column v-if="canRefund" :header="t('pointShop.col.actions')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <!-- 대기 중인 신청이 있는 구매는 예외 환불이 아니라 신청 수락·거절로 처리한다(서버도 예외 환불을 409 POINT_SHOP_REFUND_PENDING 으로 막는다) -->
                        <div v-if="data.refundRequest?.status === 'PENDING'" class="flex flex-wrap gap-1">
                            <Button type="button" :label="t('pointShop.request.approve')" size="small" :loading="decidingId === data.refundRequest.id" :disabled="decidingId !== null" @click="approve(data)" />
                            <Button type="button" :label="t('pointShop.request.reject')" size="small" severity="secondary" variant="outlined" :disabled="decidingId !== null" @click="openReject(data)" />
                        </div>
                        <Button v-else-if="data.status === 'COMPLETED'" type="button" :label="t('pointShop.refund.open')" size="small" severity="danger" variant="outlined" @click="openRefund(data)" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <PointShopRefundDialog v-if="canRefund" v-model:visible="refundOpen" :purchase="refundTarget" :busy="refundBusy" :error="refundError" :field-errors="refundFieldErrors" @submit="onRefund" />
        <PointShopRefundDialog v-if="canRefund" v-model:visible="rejectOpen" mode="reject" :purchase="rejectTarget" :busy="rejectBusy" :error="rejectError" :field-errors="rejectFieldErrors" @submit="onReject" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointShopItemKind, PointShopPurchaseQuery, PointShopPurchaseRow, PointShopPurchaseStatus, PointShopRefundRequestStatus } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import { POINT_SHOP_REFUND_REQUEST_STATUSES } from '~/composables/pointshop/api/usePointShopAdminApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilMember from '~/utils/util-member';
import UtilPointShop from '~/utils/util-point-shop';
import UtilPointShopMessages from '~/utils/util-point-shop-messages';

/** 화면 조건(draft) — 「검색」을 눌러야 서버로 간다. 날짜는 달력 글자(`YYYY-MM-DD`), 상태·종류 '' = 전체 */
interface PurchaseFilter {
    from: string;
    to: string;
    status: string;
    /** 환불 신청 상태 '' = 전체 */
    refundRequest: string;
    kind: string;
    q: string;
}
type AppliedQuery = Omit<PointShopPurchaseQuery, 'page' | 'size'>;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('pointShop.purchasesTitle') });

const api = usePointShopAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const route = useRoute();

/** 예외 환불 권한(`util-permission-catalog.ts`, SENSITIVE) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canRefund = computed(() => can('pointshop.refund'));

const ids = { q: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const statusOptions = computed(() => [{ value: '', label: t('common.all') }, ...(['COMPLETED', 'REFUNDED'] as const).map((status) => ({ value: status, label: t(`pointShop.status.${status}`) }))]);
const requestOptions = computed(() => [{ value: '', label: t('common.all') }, ...POINT_SHOP_REFUND_REQUEST_STATUSES.map((status) => ({ value: status, label: t(`pointShop.request.status.${status}`) }))]);
const kindOptions = computed(() => [{ value: '', label: t('common.all') }, ...UtilPointShop.KINDS.map((kind) => ({ value: kind, label: t(`pointShop.kind.${kind}`) }))]);

/** 기본 기간 — 표시 시간대의 오늘 기준 최근 1개월 */
function defaultFilter(): PurchaseFilter {
    const p = time.value.parts(new Date().toISOString());
    const today = p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    const range = UtilDateRange.fill('MONTH1', today);
    return { from: range?.from ?? '', to: range?.to ?? '', status: '', kind: '', refundRequest: '', q: '' };
}

const filter = reactive<PurchaseFilter>(defaultFilter());
const rows = ref<PointShopPurchaseRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const searchErrors = ref<Record<string, string>>({});
const hasSearchErrors = computed(() => Object.keys(searchErrors.value).length > 0);
const FILTER_KEYS = ['from', 'to', 'status', 'kind', 'refundRequest', 'q'];
const otherErrors = computed(() =>
    Object.entries(searchErrors.value)
        .filter(([key]) => !FILTER_KEYS.includes(key))
        .map(([, message]) => message)
        .join(' '),
);

const applied = ref<AppliedQuery>({});
const hasApplied = computed(() => Boolean(applied.value.status || applied.value.kind || applied.value.refundRequest || applied.value.q || applied.value.from || applied.value.to));

function errorOf(...keys: string[]): string {
    return keys
        .map((key) => searchErrors.value[key])
        .filter(Boolean)
        .join(' ');
}

function toQuery(f: PurchaseFilter): AppliedQuery {
    const zoneId = values.value.zoneId;
    const out: AppliedQuery = {};
    const from = f.from ? UtilMember.dayStart(f.from, zoneId) : undefined;
    // 서버의 `to` 는 미만이다 — 끝 날의 마지막 순간(23:59:59.999)에 1ms 를 더해 다음 날 0시를 보낸다
    const dayEnd = f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined;
    const to = dayEnd ? new Date(Date.parse(dayEnd) + 1).toISOString() : undefined;
    if (from) out.from = from;
    if (to) out.to = to;
    if (f.status) out.status = f.status as PointShopPurchaseStatus;
    if (f.kind) out.kind = f.kind as PointShopItemKind;
    if (f.refundRequest) out.refundRequest = f.refundRequest as PointShopRefundRequestStatus;
    if (f.q.trim()) out.q = f.q.trim();
    return out;
}

/** 늦게 도착한 앞 요청의 응답은 버린다(번호표) */
let ticket = 0;

async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    searchErrors.value = {};
    try {
        const found = await api.purchases({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        const error = toApiError(e);
        rows.value = [];
        total.value = 0;
        if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) searchErrors.value = error.fields;
        else loadError.value = error;
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

function clearAll(): void {
    Object.assign(filter, { from: '', to: '', status: '', kind: '', refundRequest: '', q: '' });
    search();
}

/** 「사용」 칸의 말 — 변경권은 사용함, 아바타는 프로필로 고름, 꾸미기는 장착함(모두 `firstUsedAt` — 환불 판정) */
function usedLabel(kind: PointShopItemKind): string {
    if (kind === 'NICKNAME_CHANGE_TICKET') return t('pointShop.usedTicket');
    if (kind === 'PROFILE_AVATAR') return t('pointShop.usedAvatar');
    return t('pointShop.usedEquip');
}

/** 닉네임을 누르면 그 회원의 구매만 — 회원 조건에 회원번호를 넣고 다시 본다 */
function onlyMember(row: PointShopPurchaseRow): void {
    if (!row.member.id) return;
    filter.q = row.member.id;
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

/** 주소의 `?refundRequest=` 가 신청 상태면 그 값(운영자 알림 링크 `?refundRequest=PENDING`) */
function requestFromRoute(): string {
    const value = route.query.refundRequest;
    return typeof value === 'string' && (POINT_SHOP_REFUND_REQUEST_STATUSES as readonly string[]).includes(value) ? value : '';
}

onMounted(() => {
    filter.refundRequest = requestFromRoute();
    search();
});

// 이 화면에 있는 채로 알림 링크를 다시 누르면(주소만 바뀐다) 그 거르기로 다시 검색한다
watch(
    () => route.query.refundRequest,
    () => {
        const next = requestFromRoute();
        if (next && next !== filter.refundRequest) {
            filter.refundRequest = next;
            search();
        }
    },
);

/** 환불 종류 한 줄 — 셀프 · 예외(처리자) · 신청 수락(수락한 운영자) */
function refundKindText(row: PointShopPurchaseRow): string {
    const refund = row.refund;
    if (!refund) return '';
    if (refund.kind === 'SELF') return t('pointShop.refundKind.SELF');
    return t(`pointShop.refundKind.${refund.kind}`, { name: refund.operatorName ?? '—' });
}

// ── 예외 환불 ─
const refundOpen = ref(false);
const refundTarget = ref<PointShopPurchaseRow | null>(null);
const refundBusy = ref(false);
const refundError = ref('');
const refundFieldErrors = ref<Record<string, string>>({});

function openRefund(row: PointShopPurchaseRow): void {
    refundTarget.value = row;
    refundError.value = '';
    refundFieldErrors.value = {};
    refundOpen.value = true;
}

async function onRefund(reason: string): Promise<void> {
    const target = refundTarget.value;
    if (!target || refundBusy.value) return;
    const ok = await confirm({
        title: t('pointShop.refund.confirmTitle'),
        message: t('pointShop.refund.confirmMessage', { name: target.member.nickname, item: target.itemName, points: target.pricePoint.toLocaleString(locale.value) }),
        confirmLabel: t('pointShop.refund.submit'),
        cancelLabel: t('common.cancel'),
        tone: 'danger',
    });
    if (!ok) return;
    refundBusy.value = true;
    refundError.value = '';
    refundFieldErrors.value = {};
    try {
        // 감사 기록(돈 관련 5년)은 서버가 남긴다
        const res = await api.refund(target.id, { reason });
        toast.success(t('pointShop.refund.done', { name: target.member.nickname, points: res.refundedPoints.toLocaleString(locale.value) }));
        refundOpen.value = false;
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'POINT_SHOP_ALREADY_REFUNDED' || error.code === 'POINT_SHOP_REFUND_PENDING') {
            // 다른 운영자·회원이 먼저 환불했다 — 대화상자를 닫고 알린 뒤 지금 상태를 다시 읽는다
            refundOpen.value = false;
            toast.error(error.message, 8000);
            void reload();
            return;
        }
        const fields = error.status === 400 && error.fields ? error.fields : {};
        refundFieldErrors.value = fields;
        const rest = Object.entries(fields)
            .filter(([key]) => key !== 'reason')
            .map(([, message]) => message);
        refundError.value = Object.keys(fields).length ? rest.join(' ') : `${t('pointShop.refund.failed')} — ${error.message}`;
    } finally {
        refundBusy.value = false;
    }
}

// ── 정지 중 환불 신청 — 수락 · 거절 ─
/** 수락 요청 중인 신청 번호 — 그 줄 단추만 돈다 */
const decidingId = ref<string | null>(null);
const rejectOpen = ref(false);
const rejectTarget = ref<PointShopPurchaseRow | null>(null);
const rejectBusy = ref(false);
const rejectError = ref('');
const rejectFieldErrors = ref<Record<string, string>>({});

/** 이미 결정됐거나 없는 신청(다른 운영자가 먼저) — 서버 문구 토스트 + 다시 읽기. 처리했으면 true */
function handleStale(error: ApiError): boolean {
    if (error.code !== 'POINT_SHOP_REFUND_REQUEST_NOT_PENDING' && error.code !== 'POINT_SHOP_REFUND_REQUEST_NOT_FOUND') return false;
    toast.error(error.message, 8000);
    void reload();
    return true;
}

async function approve(row: PointShopPurchaseRow): Promise<void> {
    const request = row.refundRequest;
    if (!request || decidingId.value) return;
    const ok = await confirm({
        title: t('pointShop.request.approveTitle'),
        message: t('pointShop.request.approveMessage', { name: row.member.nickname, item: row.itemName, points: row.pricePoint.toLocaleString(locale.value), at: time.value.full(request.requestedAt) }),
        confirmLabel: t('pointShop.request.approve'),
        cancelLabel: t('common.cancel'),
    });
    if (!ok) return;
    decidingId.value = request.id;
    try {
        // 감사(돈 관련 5년)·회원 알림은 서버가 남긴다
        const res = await api.approveRefundRequest(request.id);
        toast.success(t('pointShop.request.approved', { name: row.member.nickname, points: res.refundedPoints.toLocaleString(locale.value) }));
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (!handleStale(error)) toast.error(`${t('pointShop.request.failed')} — ${error.message}`, 8000);
    } finally {
        decidingId.value = null;
    }
}

function openReject(row: PointShopPurchaseRow): void {
    rejectTarget.value = row;
    rejectError.value = '';
    rejectFieldErrors.value = {};
    rejectOpen.value = true;
}

async function onReject(reason: string): Promise<void> {
    const row = rejectTarget.value;
    const request = row?.refundRequest;
    if (!row || !request || rejectBusy.value) return;
    rejectBusy.value = true;
    rejectError.value = '';
    rejectFieldErrors.value = {};
    try {
        await api.rejectRefundRequest(request.id, { reason });
        toast.success(t('pointShop.request.rejected', { name: row.member.nickname }));
        rejectOpen.value = false;
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (handleStale(error)) {
            rejectOpen.value = false;
            return;
        }
        const fields = error.status === 400 && error.fields ? error.fields : {};
        rejectFieldErrors.value = fields;
        const rest = Object.entries(fields)
            .filter(([key]) => key !== 'reason')
            .map(([, message]) => message);
        rejectError.value = Object.keys(fields).length ? rest.join(' ') : `${t('pointShop.request.failed')} — ${error.message}`;
    } finally {
        rejectBusy.value = false;
    }
}
</script>
