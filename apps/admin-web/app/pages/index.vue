<template>
    <!--
        대시보드 — 처리할 일 칸 + 커머스 칸.
        · 처리할 일(04-ia-review Q5 — 통합 「신청 심사」 큐 대신 1인 운영의 「한눈에」): 광고 심사 · 핫딜 심사 · 입금 확인 대기 · 입금 기한 지남 · 환불 심사 · 사업자 인증 대기.
          신청 건 숫자는 권한(`ad.read`·`hotdeal.read`)이 있는 도메인마다 목록 API 를 한 번 불러 받은 큐별 건수(`counts`)이고, 두 도메인을 합친 칸은 도메인별 링크를 따로 단다.
          사업자 인증 대기는 사업자 목록의 상태별 건수 중 심사 중(처음·재신청 + 재심사, `business.read`). 권한이 있는 칸만 보이고, 칸을 누르면 그 화면·큐로 간다.
        · 커머스(commerce.md 19절, 계약 commerce-groupbuy 「화면 목록」): 준비 대기 · 열린 요청(기한 경고) · 결제 이상 · 곧 만료될 선점 · 남은 수량 적은 공구
          · 발송 예정일 지난 발송 전 주문 · 발송 뒤 N일 지난 SHIPPED(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다). 숫자 + 해당 화면 링크.
          숫자는 서버가 그 운영자의 권한 안에서 센다 — 볼 권한이 없는 칸은 `null` 이라 그리지 않는다(칸이 하나도 없으면 빈 화면 안내). 「답변 안 한 Q&A」는 2b-6 에서 더한다.
          서버 호출은 `useCommerceDashboardApi`(지금 목업 — `?mock=dashboard-fail`·`dashboard-empty`·`dashboard-partial`). 커머스 권한이 없고 처리할 일 칸이 있으면 커머스 칸은 그리지 않는다.
    -->
    <section class="flex flex-col gap-4">
        <PageTitle :title="t('dashboard.title')" />

        <AppGadget v-if="anyTodo" :title="t('dashboard.todoTitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :aria-label="t('dashboard.refresh')" :loading="todoLoading" @click="loadTodo" />
            </template>

            <ErrorState v-if="todoError" :title="t('dashboard.todoLoadFailed')" :description="todoError.message" retry :retrying="todoLoading" @retry="loadTodo" />
            <div v-else-if="!todo" class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 6" :key="n" height="5.5rem" border-radius="0.75rem" />
            </div>
            <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3 p-0">
                <li v-for="card in todoCards" :key="card.key">
                    <NuxtLink v-if="card.links.length === 1" :to="card.links[0]!.to" class="flex h-full flex-col gap-1 rounded-xl border px-4 py-3 transition-colors hover:bg-surface-50 dark:hover:bg-surface-900" :class="toneBorder(card.tone)">
                        <span class="flex items-center gap-2 text-[13px] text-muted-color"><i :class="card.icon" class="text-xs" aria-hidden="true" />{{ card.label }}</span>
                        <span class="text-2xl font-semibold tabular-nums" :class="toneText(card.tone, card.count)">{{ card.count }}</span>
                        <span v-if="card.note" class="text-[11px] text-muted-color">{{ card.note }}</span>
                    </NuxtLink>
                    <div v-else class="flex h-full flex-col gap-1 rounded-xl border px-4 py-3" :class="toneBorder(card.tone)">
                        <span class="flex items-center gap-2 text-[13px] text-muted-color"><i :class="card.icon" class="text-xs" aria-hidden="true" />{{ card.label }}</span>
                        <span class="text-2xl font-semibold tabular-nums" :class="toneText(card.tone, card.count)">{{ card.count }}</span>
                        <span v-if="card.note" class="text-[11px] text-muted-color">{{ card.note }}</span>
                        <span class="mt-0.5 flex flex-wrap gap-x-3 gap-y-1 text-[12px]">
                            <NuxtLink v-for="link in card.links" :key="link.to" :to="link.to" class="inline-flex items-center gap-1 text-primary hover:underline">
                                {{ link.label }}<i class="pi pi-angle-right text-[10px]" aria-hidden="true" />
                            </NuxtLink>
                        </span>
                    </div>
                </li>
            </ul>
        </AppGadget>

        <AppGadget v-if="anyCommerce || !anyTodo" :title="t('dashboard.commerceTitle')">
            <template #actions>
                <span v-if="view" class="text-[11px] text-muted-color">{{ t('dashboard.generatedAt', { at: time.clock(view.generatedAt) }) }}</span>
                <Button type="button" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :aria-label="t('dashboard.refresh')" :loading="loading" @click="load" />
            </template>

            <ErrorState v-if="error && !view" :title="t('dashboard.loadFailed')" :description="error.message" retry :retrying="loading" @retry="load" />
            <div v-else-if="!view" class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 7" :key="n" height="5.5rem" border-radius="0.75rem" />
            </div>
            <EmptyState v-else-if="!cards.length" icon="pi pi-chart-bar" :title="t('dashboard.emptyTitle')" :description="t('dashboard.emptyBody')" />
            <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3 p-0">
                <li v-for="card in cards" :key="card.key">
                    <NuxtLink :to="card.to" class="flex h-full flex-col gap-1 rounded-xl border px-4 py-3 transition-colors hover:bg-surface-50 dark:hover:bg-surface-900" :class="toneBorder(card.tone)">
                        <span class="flex items-center gap-2 text-[13px] text-muted-color"><i :class="card.icon" class="text-xs" aria-hidden="true" />{{ card.label }}</span>
                        <span class="text-2xl font-semibold tabular-nums" :class="toneText(card.tone, card.count)">{{ card.count }}</span>
                        <span v-if="card.note" class="text-[11px] text-muted-color">{{ card.note }}</span>
                    </NuxtLink>
                </li>
            </ul>
        </AppGadget>
    </section>
</template>

<script setup lang="ts">
import type { BizApplicationQueue, BizDomain } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_DOMAINS } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import type { CommerceDashboardView } from '~/composables/commerce/api/useCommerceDashboardApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilBizApplication from '~/utils/util-biz-application';

type Tone = 'normal' | 'warn' | 'danger';

interface DashboardCard {
    key: string;
    label: string;
    count: number;
    note: string;
    to: string;
    icon: string;
    tone: Tone;
}

/** 처리할 일 칸 — 링크가 하나면 칸 전체가 링크, 둘 이상(도메인을 합친 칸)이면 도메인별 링크를 단다 */
interface TodoCard {
    key: string;
    label: string;
    count: number;
    note: string;
    icon: string;
    tone: Tone;
    links: { label: string; to: string }[];
}

/** 처리할 일 숫자 — 볼 권한이 있는 도메인의 큐별 건수 · 사업자 심사 중 건수(권한 없으면 null) */
interface TodoView {
    domains: Partial<Record<BizDomain, Record<BizApplicationQueue, number>>>;
    businessPending: number | null;
}

const { t } = useI18n();
useSeoMeta({ title: () => t('dashboard.title') });

const api = useCommerceDashboardApi();
const bizApi = useBizApplicationApi();
const businessApi = useBusinessApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();

function toneBorder(tone: Tone): string {
    return tone === 'danger' ? 'border-red-300 dark:border-red-800' : tone === 'warn' ? 'border-amber-300 dark:border-amber-800' : 'border-(--line)';
}
function toneText(tone: Tone, count: number): string {
    if (count <= 0) return '';
    return tone === 'danger' ? 'text-red-600 dark:text-red-400' : tone === 'warn' ? 'text-amber-700 dark:text-amber-400' : '';
}

// ── 처리할 일 ──
/** 신청 건을 볼 수 있는 도메인(조회 권한) */
const todoDomains = computed(() => BIZ_DOMAINS.filter((d) => can(UtilBizApplication.DOMAIN_SCREENS[d].read)));
const canBusiness = computed(() => can('business.read'));
const anyTodo = computed(() => todoDomains.value.length > 0 || canBusiness.value);

const todo = ref<TodoView | null>(null);
const todoLoading = ref(false);
const todoError = ref<ApiError | null>(null);

/** 신청 건 큐 하나의 칸 — 볼 수 있는 도메인의 건수를 더하고 도메인마다 그 화면·큐로 가는 링크를 단다 */
function queueCard(view: TodoView, key: string, queue: BizApplicationQueue, label: string, note: string, icon: string, tone: (n: number) => Tone, domains: readonly BizDomain[]): TodoCard | null {
    const seen = domains.filter((d) => view.domains[d]);
    if (!seen.length) return null;
    const count = seen.reduce((sum, d) => sum + (view.domains[d]?.[queue] ?? 0), 0);
    const links = seen.map((d) => ({
        label: t('dashboard.todoDomainLink', { domain: t(`bizApplication.domain.${d}`), count: view.domains[d]?.[queue] ?? 0 }),
        to: `${UtilBizApplication.DOMAIN_SCREENS[d].path}?queue=${queue}`,
    }));
    return { key, label, count, note, icon, tone: tone(count), links };
}

const todoCards = computed<TodoCard[]>(() => {
    const v = todo.value;
    if (!v) return [];
    const normal = (): Tone => 'normal';
    const cards: (TodoCard | null)[] = [
        queueCard(v, 'adReview', 'REVIEW', t('dashboard.todoAdReview'), t('dashboard.todoReviewNote'), 'pi pi-megaphone', normal, ['AD']),
        queueCard(v, 'hotdealReview', 'REVIEW', t('dashboard.todoHotdealReview'), t('dashboard.todoReviewNote'), 'pi pi-ticket', normal, ['HOTDEAL']),
        queueCard(v, 'deposit', 'DEPOSIT', t('dashboard.todoDeposit'), t('dashboard.todoDepositNote'), 'pi pi-wallet', normal, BIZ_DOMAINS),
        queueCard(v, 'depositOverdue', 'DEPOSIT_OVERDUE', t('dashboard.todoDepositOverdue'), t('dashboard.todoDepositOverdueNote'), 'pi pi-calendar-times', (n) => (n > 0 ? 'danger' : 'normal'), BIZ_DOMAINS),
        queueCard(v, 'refund', 'REFUND', t('dashboard.todoRefund'), t('dashboard.todoRefundNote'), 'pi pi-replay', (n) => (n > 0 ? 'warn' : 'normal'), BIZ_DOMAINS),
        v.businessPending === null
            ? null
            : { key: 'businessReview', label: t('dashboard.todoBusinessReview'), count: v.businessPending, note: t('dashboard.todoBusinessReviewNote'), icon: 'pi pi-briefcase', tone: 'normal', links: [{ label: t('dashboard.todoBusinessReview'), to: '/business-reviews' }] },
    ];
    return cards.filter((c): c is TodoCard => c !== null);
});

/** 권한 있는 도메인마다 목록 한 번(큐별 건수) + 사업자 목록 한 번(상태별 건수). 403 은 오류가 아니라 그 칸을 빼고, 다른 실패는 칸 전체를 오류로 */
async function loadTodo(): Promise<void> {
    if (!anyTodo.value) return;
    todoLoading.value = true;
    todoError.value = null;
    const domains = todoDomains.value;
    const [bizResults, businessResult] = await Promise.all([
        Promise.allSettled(domains.map((domain) => bizApi.list({ domain, queue: 'REVIEW' }))),
        canBusiness.value ? Promise.allSettled([businessApi.list({ status: 'PENDING', page: 0, size: 1 })]).then((r) => r[0]!) : Promise.resolve(null),
    ]);
    let failure: ApiError | null = null;
    const view: TodoView = { domains: {}, businessPending: null };
    bizResults.forEach((r, i) => {
        if (r.status === 'fulfilled') view.domains[domains[i]!] = r.value.counts;
        else {
            const e = toApiError(r.reason);
            if (e.status !== 403) failure ??= e;
        }
    });
    if (businessResult?.status === 'fulfilled') view.businessPending = businessResult.value.counts.PENDING;
    else if (businessResult?.status === 'rejected') {
        const e = toApiError(businessResult.reason);
        if (e.status !== 403) failure ??= e;
    }
    todoError.value = failure;
    todo.value = view;
    todoLoading.value = false;
}

// ── 커머스 ──

const view = ref<CommerceDashboardView | null>(null);
const loading = ref(false);
const error = ref<ApiError | null>(null);

/** 커머스 칸을 볼 권한이 하나라도 있나 — 없으면 부르지 않는다(403 은 오류가 아니다) */
const anyCommerce = computed(() => ['commerce.order.read', 'commerce.product.read', 'commerce.payment.read'].some(can));

const cards = computed<DashboardCard[]>(() => {
    const v = view.value;
    if (!v) return [];
    const out: DashboardCard[] = [];
    if (v.preparingWaiting !== null) out.push({ key: 'preparing', label: t('dashboard.preparing'), count: v.preparingWaiting, note: t('dashboard.preparingNote'), to: '/group-buy-orders?status=CONFIRMED', icon: 'pi pi-box', tone: 'normal' });
    if (v.openRequests !== null) out.push({ key: 'requests', label: t('dashboard.openRequests'), count: v.openRequests.total, note: t('dashboard.openRequestsNote', { warn: v.openRequests.warn, critical: v.openRequests.critical }), to: v.openRequests.warn + v.openRequests.critical > 0 ? '/group-buy-requests?status=ALL&level=WARN' : '/group-buy-requests', icon: 'pi pi-replay', tone: v.openRequests.critical > 0 ? 'danger' : v.openRequests.warn > 0 ? 'warn' : 'normal' });
    if (v.paymentAnomalies !== null) out.push({ key: 'anomalies', label: t('dashboard.paymentAnomalies'), count: v.paymentAnomalies, note: '', to: '/payments/anomalies', icon: 'pi pi-exclamation-triangle', tone: v.paymentAnomalies > 0 ? 'danger' : 'normal' });
    if (v.expiringHolds !== null) out.push({ key: 'holds', label: t('dashboard.expiringHolds'), count: v.expiringHolds, note: t('dashboard.expiringHoldsNote'), to: '/group-buy-stock', icon: 'pi pi-clock', tone: 'normal' });
    if (v.lowStockGroupBuys !== null) out.push({ key: 'lowStock', label: t('dashboard.lowStock'), count: v.lowStockGroupBuys, note: t('dashboard.lowStockNote'), to: '/group-buys?status=RECRUITING', icon: 'pi pi-chart-bar', tone: v.lowStockGroupBuys > 0 ? 'warn' : 'normal' });
    if (v.shipOverdue !== null) out.push({ key: 'overdue', label: t('dashboard.shipOverdue'), count: v.shipOverdue, note: t('dashboard.shipOverdueNote'), to: '/group-buy-orders?alert=SHIP_OVERDUE', icon: 'pi pi-calendar-times', tone: v.shipOverdue > 0 ? 'danger' : 'normal' });
    if (v.deliveryStale !== null) out.push({ key: 'stale', label: t('dashboard.deliveryStale', { days: v.deliveryStale.days }), count: v.deliveryStale.count, note: t('dashboard.deliveryStaleNote'), to: '/group-buy-orders?alert=DELIVERY_STALE', icon: 'pi pi-truck', tone: v.deliveryStale.count > 0 ? 'warn' : 'normal' });
    return out;
});

async function load(): Promise<void> {
    if (!anyCommerce.value) {
        view.value = { preparingWaiting: null, openRequests: null, paymentAnomalies: null, expiringHolds: null, lowStockGroupBuys: null, shipOverdue: null, deliveryStale: null, generatedAt: new Date().toISOString() };
        return;
    }
    loading.value = true;
    error.value = null;
    try {
        view.value = await api.summary();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 403) view.value = { preparingWaiting: null, openRequests: null, paymentAnomalies: null, expiringHolds: null, lowStockGroupBuys: null, shipOverdue: null, deliveryStale: null, generatedAt: new Date().toISOString() };
        else error.value = failure;
    } finally {
        loading.value = false;
    }
}

onMounted(() => {
    void loadTodo();
    void load();
});
</script>
