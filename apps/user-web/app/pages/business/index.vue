<template>
    <!--
        사업자 센터 홈 `/business` — 상태가 하나뿐인 화면이라 탭 대신 「지금 할 일 하나」를 맨 위에 둔다(§6, 01 §7-2 F).
        미신청 = 안내 + [사업자 신청] · 심사 중 = 확인 중 안내 + 가린 요약 · 반려 = 사유 + [다시 신청] · 정지 = 사유 + 문의 안내 ·
        승인 = (재심사 중이면 안내) + 할 일 카드(결제 대기 — 기한·[결제하기], 입금 확인 대기) + 서비스 카드(광고·핫딜) + 사업자 정보 요약 + 최근 신청 건.
        ⚠ MOCK: 오른쪽 아래 「목업」 상자로 사업자 상태를 바꿔 본다(서버가 붙으면 지운다).
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

            <!-- 서비스 -->
            <section :aria-labelledby="`${uid}-services`">
                <h2 :id="`${uid}-services`" class="m-0 mb-2 text-sm font-semibold">{{ t('business.home.servicesTitle') }}</h2>
                <div class="grid gap-3 sm:grid-cols-2">
                    <div v-for="service in services" :key="service.to" class="panel-card flex flex-col gap-2 p-4" :data-testid="service.testid">
                        <p class="m-0 flex items-center gap-2 text-sm font-bold"><i :class="[service.icon, 'text-primary']" aria-hidden="true" />{{ service.label }}</p>
                        <p class="m-0 flex-1 text-xs leading-relaxed text-muted-color">{{ service.text }}</p>
                        <div>
                            <Button as="router-link" :to="service.to" :label="t('business.home.serviceApply')" size="small" />
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

        <!-- MOCK: 사업자 상태 바꾸기 -->
        <BusinessMockPanel :title="t('business.mock.title')" floating>
            <label :for="`${uid}-mock`" class="text-xs font-medium">{{ t('business.mock.profileStatus') }}</label>
            <Select :model-value="mockChoice" :input-id="`${uid}-mock`" :options="mockOptions" option-label="label" option-value="value" size="small" :disabled="mockBusy" fluid data-testid="business-mock-status" @update:model-value="changeMock" />
        </BusinessMockPanel>
    </div>
</template>

<script setup lang="ts">
import type { BizApplicationSummary, BusinessProfile, BusinessProfileStatus } from '~/composables/business/api/useBusinessApi.types';
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

const profile = ref<BusinessProfile | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const apps = ref<BizApplicationSummary[]>([]);
const appsStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        profile.value = await api.profile();
        status.value = 'ready';
        if (profile.value.status === 'APPROVED') void loadApplications();
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

onMounted(load);

const noneSteps = computed(() => [t('business.home.noneStep1'), t('business.home.noneStep2'), t('business.home.noneStep3')]);

/** 결제 대기 — 기한이 가까운 것부터 */
const awaitingPayment = computed(() =>
    apps.value.filter((a) => a.status === 'AWAITING_PAYMENT').sort((a, b) => Date.parse(a.paymentDueAt ?? '') - Date.parse(b.paymentDueAt ?? '')),
);
const awaitingDeposit = computed(() => apps.value.filter((a) => a.status === 'AWAITING_DEPOSIT'));
const recent = computed(() => apps.value.slice(0, 5));

const services = computed(() => [
    { to: UtilBusiness.AD_NEW_PATH, icon: 'pi pi-megaphone', label: t('business.home.serviceAd'), text: t('business.home.serviceAdText'), testid: 'business-service-ad' },
    { to: UtilBusiness.DEAL_NEW_PATH, icon: 'pi pi-tag', label: t('business.home.serviceDeal'), text: t('business.home.serviceDealText'), testid: 'business-service-deal' },
]);

/* ── MOCK: 상태 바꾸기 ── */
type MockChoice = BusinessProfileStatus | 'APPROVED_RE_REVIEW';
const mockBusy = ref(false);
const mockChoice = computed<MockChoice | null>(() => {
    if (!profile.value) return null;
    return profile.value.status === 'APPROVED' && profile.value.reReview ? 'APPROVED_RE_REVIEW' : profile.value.status;
});
const mockOptions = computed(() =>
    (['NONE', 'PENDING', 'APPROVED', 'APPROVED_RE_REVIEW', 'REJECTED', 'SUSPENDED'] as const).map((value) => ({
        value,
        label: value === 'APPROVED_RE_REVIEW' ? t('business.mock.reReview') : t(`business.profileStatus.${value}`),
    })),
);

async function changeMock(value: MockChoice): Promise<void> {
    mockBusy.value = true;
    try {
        profile.value = value === 'APPROVED_RE_REVIEW' ? await api.mockSetStatus('APPROVED', true) : await api.mockSetStatus(value);
        status.value = 'ready';
        if (profile.value.status === 'APPROVED') void loadApplications();
    } finally {
        mockBusy.value = false;
    }
}
</script>
