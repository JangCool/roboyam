<template>
    <!--
        사업자 센터 홈 `/business` — 상태가 하나뿐인 화면이라 탭 대신 「지금 할 일 하나」를 맨 위에 둔다(§6, 01 §7-2 F).
        미신청 = 안내 + [사업자 신청] · 심사 중 = 확인 중 안내 + 가린 요약 · 반려 = 사유 + [다시 신청] · 정지 = 사유 + 문의 안내 ·
        승인 = (재심사 중이면 안내) + 할 일 카드(결제 대기 — 기한·[결제하기], 입금 확인 대기) + 서비스 카드(광고·핫딜) + 사업자 정보 요약 + 최근 신청 건.
        사업자 신원은 서버(`GET /api/business/me`), 신청 건 목록은 아직 목업(`useBusinessApi` — 신청 건 백엔드에서 붙인다).
        승인인데 한 서비스만 정지면(2026-11-02 — `services[]`) 그 서비스 안내 + 사유, 그 서비스 카드 [신청하기] 잠금.
        「진행 중 광고」 카드(광고 성과 2026-11-02 — `useBusinessApi.adStatsSummary`): 진행 중·일시 정지 중·내려짐(긴급 내림·사업자/서비스 정지 — 사용자 결정 2026-11-02, 광고주 중단 제외)이고 노출 자리가 있는 건 최대 5개 — 상태 딱지·종류·제목 ·
          최근 7일(오늘 포함) 노출·클릭·클릭률 · 7일 미니 막대(`BusinessStatsChart compact` — 오늘 빗금, 정지 날 띠) · 줄을 누르면 상세의 성과 칸(`#performance`). 없으면 카드를 감춘다.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="business-home">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="m-0 text-lg font-bold">{{ t('business.home.title') }}</h1>
            <Button
                v-if="profile?.status === 'APPROVED'"
                as="router-link"
                :to="UtilBusiness.APPLICATIONS_PATH"
                :label="t('business.list.title')"
                icon="pi pi-list"
                size="small"
                severity="secondary"
                variant="outlined"
            />
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !profile" :title="t('business.home.loadFailed')" :description="loadError" retry @retry="load" />

        <!-- 미신청 -->
        <section v-else-if="profile.status === 'NONE'" class="panel-card flex flex-col gap-4 p-5" data-testid="business-home-none">
            <div class="flex items-start gap-3">
                <span class="grid size-11 shrink-0 place-items-center rounded-full bg-(--p-highlight-background) text-primary" aria-hidden="true"><i class="pi pi-briefcase text-lg" /></span>
                <div class="min-w-0">
                    <h2 class="m-0 text-base font-bold">{{ t('business.home.noneTitle') }}</h2>
                    <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('business.home.noneText') }}</p>
                </div>
            </div>
            <ol class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="(step, i) in noneSteps" :key="step" class="flex items-start gap-2.5 text-sm">
                    <span class="grid size-6 shrink-0 place-items-center rounded-full bg-surface-100 text-xs font-bold dark:bg-surface-800" aria-hidden="true">{{ i + 1 }}</span>
                    <span class="pt-0.5">{{ step }}</span>
                </li>
            </ol>
            <div>
                <Button as="router-link" :to="UtilBusiness.APPLY_PATH" :label="t('business.home.applyCta')" icon="pi pi-arrow-right" icon-pos="right" data-testid="business-home-apply" />
            </div>
        </section>

        <!-- 심사 중 -->
        <template v-else-if="profile.status === 'PENDING'">
            <Message severity="info" :closable="false" data-testid="business-home-pending">
                <p class="m-0 font-semibold">{{ t('business.home.pendingTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.home.pendingText') }}</p>
            </Message>
            <BusinessProfileSummary :profile="profile" />
        </template>

        <!-- 반려 -->
        <template v-else-if="profile.status === 'REJECTED'">
            <Message severity="warn" :closable="false" data-testid="business-home-rejected">
                <p class="m-0 font-semibold">{{ t('business.home.rejectedTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.home.rejectedText') }}</p>
            </Message>
            <section class="panel-card flex flex-col gap-3 p-4">
                <p class="m-0 flex flex-wrap items-center gap-1.5 text-sm font-semibold">
                    {{ t('business.home.reason') }}
                    <PointShopTag v-if="profile.rejectCode" tone="danger">{{ t(`business.rejectCode.${profile.rejectCode}`) }}</PointShopTag>
                </p>
                <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ profile.rejectReason }}</p>
                <div>
                    <Button as="router-link" :to="UtilBusiness.APPLY_PATH" :label="t('business.home.reapplyCta')" icon="pi pi-refresh" />
                </div>
            </section>
        </template>

        <!-- 정지 -->
        <template v-else-if="profile.status === 'SUSPENDED'">
            <Message severity="error" :closable="false" data-testid="business-home-suspended">
                <p class="m-0 font-semibold">{{ t('business.home.suspendedTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.home.suspendedText') }}</p>
            </Message>
            <section class="panel-card flex flex-col gap-2 p-4">
                <p class="m-0 text-sm font-semibold">{{ t('business.home.reason') }}</p>
                <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ profile.suspendReason }}</p>
                <p class="m-0 text-xs text-muted-color">{{ t('business.home.contactHelp') }}</p>
            </section>
            <BusinessProfileSummary :profile="profile" />
        </template>

        <!-- 승인 -->
        <template v-else>
            <Message v-if="profile.reReview" severity="warn" :closable="false" data-testid="business-home-rereview">
                <p class="m-0 font-semibold">{{ t('business.home.reReviewTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.home.reReviewText') }}</p>
            </Message>

            <!-- 서비스만 정지(신원은 승인) — 신원 정지(`business-home-suspended`)보다 가볍게: 그 서비스 새 신청만 막히고 다른 서비스는 그대로 -->
            <Message v-for="s in suspendedServices" :key="s.service" severity="warn" :closable="false" :data-testid="`business-home-service-suspended-${s.service}`">
                <p class="m-0 font-semibold">{{ t(`business.home.serviceSuspendedTitle.${s.service}`) }}</p>
                <p class="m-0 mt-1 text-sm">
                    {{ t(`business.home.serviceSuspendedText.${s.service}`) }}
                    <template v-if="suspendedServices.length === 1">{{ t(`business.home.serviceSuspendedOtherOk.${s.service}`) }}</template>
                </p>
                <p v-if="s.suspendReason" class="m-0 mt-2 text-sm">
                    <span class="font-semibold">{{ t('business.home.reason') }}</span> · <span class="whitespace-pre-line">{{ s.suspendReason }}</span>
                </p>
                <p class="m-0 mt-1 text-xs opacity-80">
                    <template v-if="s.suspendedAt">{{ t('business.home.serviceSuspendedAt', { at: time.full(s.suspendedAt) }) }} · </template>{{ t('business.home.contactHelp') }}
                </p>
            </Message>

            <!-- 할 일 — 맨 위 -->
            <section class="panel-card flex flex-col gap-3 p-4" :aria-labelledby="`${uid}-todo`" data-testid="business-home-todo">
                <h2 :id="`${uid}-todo`" class="m-0 text-sm font-semibold">{{ t('business.home.todoTitle') }}</h2>
                <div v-if="appsStatus === 'loading'" class="h-16 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                <p v-else-if="appsStatus === 'error'" class="m-0 flex flex-wrap items-center gap-2 text-sm text-muted-color" role="alert">
                    {{ t('business.list.loadFailed') }}
                    <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="loadApplications" />
                </p>
                <p v-else-if="!awaitingPayment.length && !awaitingDeposit.length" class="m-0 flex items-center gap-2 text-sm text-muted-color">
                    <i class="pi pi-check-circle text-green-600" aria-hidden="true" />{{ t('business.home.todoNone') }}
                </p>
                <template v-else>
                    <div v-if="awaitingPayment.length" class="flex flex-col gap-2 rounded-lg border border-amber-300 bg-amber-50/70 p-3 dark:border-amber-800 dark:bg-amber-950/30">
                        <p class="m-0 text-sm font-bold">{{ t('business.home.todoPayment', { count: awaitingPayment.length }) }}</p>
                        <p class="m-0 text-xs text-muted-color">{{ t('business.home.todoPaymentHelp') }}</p>
                        <ul class="m-0 flex list-none flex-col gap-2 p-0">
                            <li v-for="item in awaitingPayment" :key="item.id" class="flex flex-wrap items-center gap-x-3 gap-y-1.5 rounded-md bg-card p-2.5">
                                <div class="min-w-0 flex-1">
                                    <p class="m-0 truncate text-sm font-semibold">{{ item.title }}</p>
                                    <p class="m-0 text-xs tabular-nums">{{ UtilBusinessMessages.won(t, item.amount.total) }}</p>
                                    <BusinessDeadline v-if="item.paymentDueAt" :due-at="item.paymentDueAt" kind="PAYMENT" />
                                </div>
                                <Button as="router-link" :to="{ path: UtilBusiness.applicationPath(item.id), query: { pay: '1' } }" :label="t('business.home.payNow')" size="small" data-testid="business-home-pay" />
                            </li>
                        </ul>
                    </div>
                    <div v-if="awaitingDeposit.length" class="flex flex-col gap-2 rounded-lg border border-hairline p-3">
                        <p class="m-0 text-sm font-bold">{{ t('business.home.todoDeposit', { count: awaitingDeposit.length }) }}</p>
                        <p class="m-0 text-xs text-muted-color">{{ t('business.home.todoDepositHelp') }}</p>
                        <ul class="m-0 flex list-none flex-col gap-2 p-0">
                            <li v-for="item in awaitingDeposit" :key="item.id" class="flex flex-wrap items-center gap-x-3 gap-y-1.5">
                                <div class="min-w-0 flex-1">
                                    <p class="m-0 truncate text-sm font-semibold">{{ item.title }}</p>
                                    <BusinessDeadline v-if="item.depositDueAt" :due-at="item.depositDueAt" kind="DEPOSIT" />
                                </div>
                                <Button as="router-link" :to="UtilBusiness.applicationPath(item.id)" :label="t('business.home.viewDetail')" size="small" severity="secondary" variant="outlined" />
                            </li>
                        </ul>
                    </div>
                </template>
            </section>

            <!-- 진행 중 광고(광고 성과) — 진행 중·정지 중·내려진 건 최대 5개의 최근 7일. 없으면 카드를 감춘다(받는 중에도 감춘다 — 깜빡임 없이) -->
            <section v-if="adStatsStatus === 'error' || (adStatsStatus === 'ready' && adStats?.items.length)" class="panel-card flex flex-col gap-3 p-4" :aria-labelledby="`${uid}-adstats`" data-testid="business-home-ad-stats">
                <div class="flex flex-col gap-0.5">
                    <h2 :id="`${uid}-adstats`" class="m-0 text-sm font-semibold">{{ t('business.stats.summaryTitle') }}</h2>
                    <p class="m-0 text-xs text-muted-color">{{ t('business.stats.summaryHelp') }}<template v-if="adStats"> · {{ t('business.stats.asOf', { at: time.full(adStats.asOf) }) }}</template></p>
                </div>
                <p v-if="adStatsStatus === 'error'" class="m-0 flex flex-wrap items-center gap-2 text-sm text-muted-color" role="alert">
                    {{ t('business.stats.summaryLoadFailed') }}
                    <Button type="button" :label="t('common.retry')" size="small" variant="text" class="p-0!" @click="loadAdStats" />
                </p>
                <ul v-else-if="adStats" class="m-0 flex list-none flex-col p-0">
                    <li v-for="item in adStats.items" :key="item.id" class="border-t border-hairline first:border-t-0">
                        <NuxtLink
                            :to="{ path: UtilBusiness.applicationPath(item.id), hash: `#${UtilAdStats.ANCHOR}` }"
                            class="flex items-center gap-3 rounded py-2.5 hover:bg-surface-50 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800/60"
                            data-testid="business-home-ad-stats-row"
                        >
                            <div class="flex min-w-0 flex-1 flex-col gap-1">
                                <div class="flex flex-wrap items-center gap-1.5">
                                    <BusinessStatusTag :status="item.status" :phase="item.phase" :cause="item.takedownCause" :end-date="item.endDate" />
                                    <span class="text-xs text-muted-color">{{ adStatsKind(item) }}</span>
                                </div>
                                <p class="m-0 truncate text-sm font-semibold">{{ item.title }}</p>
                                <p class="m-0 text-xs tabular-nums text-muted-color">
                                    {{ t('business.stats.summaryCounts', { impressions: UtilAdStats.count(item.last7.impressions), clicks: UtilAdStats.count(item.last7.clicks), ctr: UtilAdStats.ctrText(item.last7) }) }}
                                </p>
                            </div>
                            <div class="w-20 shrink-0 sm:w-28">
                                <BusinessStatsChart :days="item.spark" :today="today" compact />
                            </div>
                            <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                        </NuxtLink>
                    </li>
                </ul>
            </section>

            <!-- 서비스 -->
            <section :aria-labelledby="`${uid}-services`">
                <h2 :id="`${uid}-services`" class="m-0 mb-2 text-sm font-semibold">{{ t('business.home.servicesTitle') }}</h2>
                <div class="grid gap-3 sm:grid-cols-2">
                    <div v-for="service in services" :key="service.testid" class="panel-card flex flex-col gap-2 p-4" :data-testid="service.testid">
                        <p class="m-0 flex flex-wrap items-center gap-2 text-sm font-bold">
                            <i :class="[service.icon, service.closed || service.suspended ? 'text-muted-color' : 'text-primary']" aria-hidden="true" />{{ service.label }}
                            <PointShopTag v-if="service.suspended" tone="danger">{{ t('business.home.serviceSuspendedTag') }}</PointShopTag>
                            <PointShopTag v-else-if="service.closed" tone="neutral">{{ t('business.home.serviceDealClosed') }}</PointShopTag>
                        </p>
                        <p class="m-0 flex-1 text-xs leading-relaxed text-muted-color">
                            {{ service.suspended ? t('business.home.serviceSuspendedCardText') : service.closed ? t('business.home.serviceDealClosedText') : service.text }}
                        </p>
                        <div>
                            <Button v-if="service.closed || service.suspended" type="button" :label="t('business.home.serviceApply')" size="small" disabled />
                            <Button v-else as="router-link" :to="service.to" :label="t('business.home.serviceApply')" size="small" />
                        </div>
                    </div>
                </div>
            </section>

            <BusinessProfileSummary :profile="profile">
                <template #actions>
                    <Button as="router-link" :to="UtilBusiness.APPLY_PATH" :label="t('business.home.editInfo')" size="small" severity="secondary" variant="outlined" />
                </template>
            </BusinessProfileSummary>

            <!-- 최근 신청 건 -->
            <section :aria-labelledby="`${uid}-recent`" class="flex flex-col gap-2">
                <div class="flex items-center justify-between gap-2">
                    <h2 :id="`${uid}-recent`" class="m-0 text-sm font-semibold">{{ t('business.home.recentTitle') }}</h2>
                    <NuxtLink :to="UtilBusiness.APPLICATIONS_PATH" class="inline-flex min-h-8 items-center gap-1 rounded text-sm text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        {{ t('business.home.viewAll') }}<i class="pi pi-angle-right text-xs" aria-hidden="true" />
                    </NuxtLink>
                </div>
                <EmptyState v-if="appsStatus === 'ready' && !recent.length" class="panel-card" :title="t('business.home.recentEmpty')" :description="t('business.home.recentEmptyText')" icon="pi pi-megaphone" />
                <ul v-else-if="appsStatus === 'ready'" class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="item in recent" :key="item.id"><BusinessApplicationCard :item="item" /></li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BizAdStatsSummary, BizAdStatsSummaryItem, BizApplicationSummary, BusinessProfile, BusinessServiceKind } from '~/composables/business/api/useBusinessApi.types';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

/**
 * 사업자 센터 홈 `/business` — 로그인 필요. 사용자 메뉴의 「사업자 센터/사업자 신청」 줄·마이페이지 바로가기가 온다.
 * 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` ★ · 01 §7-2 F. ⚠ 목업 단계(`useBusinessApi` — 서버 없음).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('business.home.title'), description: () => t('business.home.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const api = useBusinessApi();
const time = useDisplayTime();

const profile = ref<BusinessProfile | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const apps = ref<BizApplicationSummary[]>([]);
const appsStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
/** 사업자 핫딜 글 게시 받기 — 모르면(못 읽음) null 로 두고 단추를 연다(신청 화면이 다시 막는다) */
const dealAccepting = ref<boolean | null>(null);

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        profile.value = await api.profile();
        status.value = 'ready';
        if (profile.value.status === 'APPROVED') {
            void loadApplications();
            void loadDealProduct();
            void loadAdStats();
        }
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}

async function loadApplications(): Promise<void> {
    appsStatus.value = 'loading';
    try {
        apps.value = (await api.applications('ALL')).items;
        appsStatus.value = 'ready';
    } catch {
        appsStatus.value = 'error';
    }
}

async function loadDealProduct(): Promise<void> {
    try {
        dealAccepting.value = (await api.dealProduct()).accepting;
    } catch {
        dealAccepting.value = null;
    }
}

/* ── 진행 중 광고(광고 성과) ── */
const adStats = ref<BizAdStatsSummary | null>(null);
const adStatsStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
/** 오늘(한국 날짜) — 미니 막대의 오늘 빗금 */
const today = UtilAdStats.today();

async function loadAdStats(): Promise<void> {
    adStatsStatus.value = 'loading';
    try {
        adStats.value = await api.adStatsSummary();
        adStatsStatus.value = 'ready';
    } catch {
        adStatsStatus.value = 'error';
    }
}

/** 「배너 광고 · 넓은 배너」 · 「핫딜 노출 · 핫딜 게시판 상단」 */
function adStatsKind(item: BizAdStatsSummaryItem): string {
    const kind = t(`business.kind.${item.kind}`);
    if (item.adType && item.kind === 'AD_BANNER') return `${kind} · ${t(`business.adType.${item.adType}`)}`;
    if (item.placement && item.placement !== 'NONE') return `${kind} · ${t(`business.placement.${item.placement}`)}`;
    return kind;
}

onMounted(load);

const noneSteps = computed(() => [t('business.home.noneStep1'), t('business.home.noneStep2'), t('business.home.noneStep3')]);

/** 결제 대기 — 기한이 가까운 것부터 */
const awaitingPayment = computed(() =>
    apps.value.filter((a) => a.status === 'AWAITING_PAYMENT').sort((a, b) => Date.parse(a.paymentDueAt ?? '') - Date.parse(b.paymentDueAt ?? '')),
);
const awaitingDeposit = computed(() => apps.value.filter((a) => a.status === 'AWAITING_DEPOSIT'));
const recent = computed(() => apps.value.slice(0, 5));

/** 신원은 승인인데 서비스만 정지된 줄(`GET /api/business/me` 의 `services[]`) */
const suspendedServices = computed(() => (profile.value ? UtilBusiness.suspendedServices(profile.value) : []));
const isSuspended = (service: BusinessServiceKind) => suspendedServices.value.some((s) => s.service === service);

const services = computed(() => [
    {
        to: UtilBusiness.AD_NEW_PATH,
        icon: 'pi pi-megaphone',
        label: t('business.home.serviceAd'),
        text: t('business.home.serviceAdText'),
        testid: 'business-service-ad',
        closed: false,
        suspended: isSuspended('AD'),
    },
    {
        to: UtilBusiness.DEAL_NEW_PATH,
        icon: 'pi pi-tag',
        label: t('business.home.serviceDeal'),
        text: t('business.home.serviceDealText'),
        testid: 'business-service-deal',
        closed: dealAccepting.value === false,
        suspended: isSuspended('HOTDEAL'),
    },
]);
</script>
