<template>
    <!--
        광고주 상세 서랍 — 광고주 관리(`/ad-advertisers`)에서 줄을 누르면 연다(`?id=`).
        탭: 기본(사업자 연결 · 연락처 가린 값 + [연락처 보기] · 광고 건 수 · 메모) · 광고 건(→ 일정표 서랍 `/ads?type=&id=`) · 성과(최근 30일 — 광고 성과 화면과 같은 차트, 「광고 성과에서 자세히」) · 결제(결제·환불 합계) · 기록(연락처 열람 기록 · 처리 기록).
        [연락처 보기](`ad.contact.reveal`) → 부모가 사유 대화상자를 열고 서버가 열람을 기록한다. 평문은 10분 카운트다운과 함께 보이고 끝나면 저절로 가린다(사업자 신원 평문 보기와 같은 모양).
        사업자 광고주는 이름·등록번호가 사업자 정보에서 오고(고칠 수 없음) 보관·삭제가 없다 — 사업자 화면의 정지·탈퇴를 따른다(「사업자 목록에서 열기」).
        맨 아래(`ad.manage`): [고치기] · [보관]/[보관 풀기](직접 등록 — 끝나지 않은 건이 있으면 막음) · [삭제](직접 등록 + 광고 건 0). 서버는 부르지 않는다 — 동작은 emit.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.name }}</h2>
                    <Tag :value="t(`adAdvertiser.kind.${head.kind}`)" severity="secondary" />
                    <Tag :value="t(`adAdvertiser.badge.${badge}`)" :severity="UtilAdAdvertiser.badgeSeverity(badge!)" />
                </div>
                <span class="truncate text-[12px] text-muted-color">
                    <code class="text-[11px]">{{ head.id }}</code>
                    <template v-if="head.business"> · {{ head.business.maskedRegNo }}</template>
                    · {{ t('adAdvertiser.createdOn', { date: time.serviceDate(UtilBizApplication.kstDate(head.createdAt)) }) }}
                </span>
                <NuxtLink v-if="head.business" :to="{ path: '/businesses', query: { id: head.business.id } }" class="inline-flex w-fit items-center gap-1 text-[12px] font-medium text-(--p-primary-color) hover:underline">
                    <i class="pi pi-external-link text-[11px]" aria-hidden="true" />{{ t('adAdvertiser.openBusiness') }}
                </NuxtLink>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('adAdvertiser.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <template v-else>
            <Message v-if="detail.state === 'ARCHIVED'" severity="secondary" :closable="false" class="mb-3"><span class="text-[13px]">{{ t('adAdvertiser.archivedNote') }}</span></Message>
            <Message v-else-if="businessNote" :severity="badge === 'BIZ_SUSPENDED' ? 'error' : 'warn'" :closable="false" class="mb-3"><span class="text-[13px]">{{ businessNote }}</span></Message>

            <Tabs v-model:value="tab" scrollable>
                <TabList>
                    <Tab value="basic">{{ t('adAdvertiser.tabBasic') }}</Tab>
                    <Tab value="bookings">{{ t('adAdvertiser.tabBookings') }} <span class="text-[11px] text-muted-color">{{ detail.counts.total }}</span></Tab>
                    <Tab value="stats">{{ t('adAdvertiser.tabStats') }}</Tab>
                    <Tab value="money">{{ t('adAdvertiser.tabMoney') }}</Tab>
                    <Tab value="history">{{ t('adAdvertiser.tabHistory') }}</Tab>
                </TabList>
                <TabPanels class="px-0">
                    <TabPanel value="basic">
                        <div class="flex flex-col gap-4">
                            <!-- 사업자 연결 -->
                            <section v-if="detail.business" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-biz`">
                                <h3 :id="`${titleId}-biz`" class="m-0 text-sm font-semibold">{{ t('adAdvertiser.secBusiness') }}</h3>
                                <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                                    <dt class="text-muted-color">{{ t('adAdvertiser.bizName') }}</dt>
                                    <dd class="m-0">{{ detail.business.name }}</dd>
                                    <dt class="text-muted-color">{{ t('business.regNo') }}</dt>
                                    <dd class="m-0 tabular-nums">{{ detail.business.maskedRegNo }}</dd>
                                    <dt class="text-muted-color">{{ t('adAdvertiser.bizStatus') }}</dt>
                                    <dd class="m-0 flex flex-wrap items-center gap-1.5">
                                        <Tag :value="t(`business.status.${detail.business.status}`)" :severity="UtilBusiness.statusSeverity(detail.business.status)" />
                                        <span class="text-xs text-muted-color">{{ t('adAdvertiser.adService') }}</span>
                                        <Tag :value="t(`business.serviceStatus.${detail.business.adService}`)" severity="secondary" />
                                    </dd>
                                </dl>
                                <p class="m-0 text-xs text-muted-color">{{ t('adAdvertiser.bizSourceNote') }}</p>
                            </section>

                            <!-- 연락처 -->
                            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-contact`">
                                <div class="flex flex-wrap items-center justify-between gap-2">
                                    <h3 :id="`${titleId}-contact`" class="m-0 text-sm font-semibold">{{ detail.kind === 'BUSINESS' ? t('adAdvertiser.adContact') : t('adAdvertiser.contactSection') }}</h3>
                                    <div v-if="shown" class="inline-flex items-center gap-2">
                                        <Tag :value="t('business.revealLeft', { time: UtilBusiness.clock(countdown.remaining.value) })" severity="warn" icon="pi pi-clock" />
                                        <Button type="button" :label="t('business.hideNow')" icon="pi pi-eye-slash" size="small" severity="secondary" variant="text" @click="emit('hide')" />
                                    </div>
                                    <Button v-else-if="hasContact && canReveal" type="button" :label="t('adAdvertiser.reveal')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" :loading="revealBusy" @click="emit('reveal')" />
                                    <span v-else-if="hasContact" class="text-xs text-muted-color">{{ t('adAdvertiser.revealNoPermission') }}</span>
                                </div>
                                <dl v-if="hasContact" class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] select-text" :class="shown ? 'bg-amber-50/60 dark:bg-amber-950/20' : ''">
                                    <template v-for="r in contactRows" :key="r.key">
                                        <dt class="text-muted-color">{{ r.label }}</dt>
                                        <dd class="m-0 min-w-0 [overflow-wrap:anywhere]" :class="shown ? 'font-medium' : 'tabular-nums'">{{ r.value ?? '—' }}</dd>
                                    </template>
                                </dl>
                                <p v-else class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-2.5 text-[13px] text-muted-color">{{ t('adAdvertiser.noContact') }}</p>
                                <p v-if="detail.contactSource === 'BUSINESS'" class="m-0 text-xs text-muted-color">{{ t('adAdvertiser.contactFromBusiness') }}</p>
                                <p v-if="shown" class="m-0 text-xs text-muted-color">{{ t('business.revealLogged') }}</p>
                            </section>

                            <!-- 광고 건 수 -->
                            <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-4">
                                <div v-for="c in countCells" :key="c.key" class="flex flex-col gap-0.5 rounded-xl border border-(--line) px-3 py-2">
                                    <dt class="text-xs text-muted-color">{{ c.label }}</dt>
                                    <dd class="m-0 text-base font-semibold tabular-nums" :class="c.tone">{{ c.value }}</dd>
                                </div>
                            </dl>

                            <section class="flex flex-col gap-1">
                                <h3 class="m-0 text-sm font-semibold">{{ t('ad.memo') }}</h3>
                                <p class="m-0 text-[13px] whitespace-pre-line [overflow-wrap:anywhere]" :class="detail.memo ? '' : 'text-muted-color'">{{ detail.memo ?? t('adAdvertiser.noMemo') }}</p>
                            </section>
                        </div>
                    </TabPanel>

                    <TabPanel value="bookings">
                        <p v-if="!detail.bookings.length" class="m-0 py-4 text-center text-[13px] text-muted-color">{{ t('adAdvertiser.noBookings') }}</p>
                        <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                            <li v-for="b in detail.bookings" :key="b.id">
                                <NuxtLink :to="{ path: '/ads', query: { type: b.type, id: b.id } }" class="flex flex-wrap items-center gap-x-3 gap-y-1 py-2.5 text-[13px] hover:bg-(--p-content-hover-background)">
                                    <span class="flex min-w-0 flex-1 flex-col">
                                        <span class="font-medium">{{ t(`bizApplication.adType.${b.type}`) }} <span class="text-[11px] font-normal text-muted-color">· {{ t(`ad.sourceKind.${b.source}`) }}</span></span>
                                        <span class="text-[12px] tabular-nums text-muted-color">{{ UtilBizApplicationMessages.period(t, time, b.period) }}</span>
                                    </span>
                                    <Tag :value="t(UtilAd.statusKey(b))" :severity="UtilAd.rowSeverity(b)" />
                                    <span class="w-28 text-right tabular-nums">
                                        {{ b.paid ? won(b.paid) : '—' }}
                                        <span v-if="b.refunded" class="block text-[11px] text-red-600 dark:text-red-400">{{ t('adAdvertiser.refundedShort', { amount: won(b.refunded) }) }}</span>
                                    </span>
                                    <i class="pi pi-angle-right text-xs text-muted-color" aria-hidden="true" />
                                </NuxtLink>
                            </li>
                        </ul>
                        <p v-if="detail.counts.total > detail.bookings.length" class="m-0 mt-2 text-xs text-muted-color">{{ t('adAdvertiser.bookingsCut', { n: detail.bookings.length, total: detail.counts.total }) }}</p>
                    </TabPanel>

                    <TabPanel value="stats">
                        <ErrorState v-if="statsError" :title="t('ad.statLoadFailed')" :description="statsError.message" :retry="true" :retrying="statsLoading" @retry="emit('stats-retry')" />
                        <div v-else class="flex flex-col gap-3">
                            <div class="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-color">
                                <span class="tabular-nums">{{ t('adAdvertiser.statsRange', { from: time.serviceDate(statsRange.from), to: time.serviceDate(statsRange.to), n: UtilAdAdvertiser.STATS_DAYS }) }}</span>
                                <NuxtLink :to="UtilAdAdvertiser.performanceLink(detail.id, statsRange)" class="inline-flex items-center gap-1 font-medium text-(--p-primary-color) hover:underline">
                                    {{ t('adAdvertiser.openPerformance') }}<i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                                </NuxtLink>
                            </div>
                            <AdStatsTotals :counts="stats?.totals ?? null" :bookings="stats?.totals.bookings ?? 0" />
                            <div v-if="statsLoading || !stats" class="h-56 rounded-xl bg-(--p-content-hover-background)" role="status" :aria-label="t('common.loading')" />
                            <template v-else-if="stats.totals.impressions === 0">
                                <p class="m-0 py-6 text-center text-[13px] text-muted-color">{{ t('adAdvertiser.statsEmpty') }}</p>
                            </template>
                            <template v-else>
                                <AdStatsChart :days="stats.days" />
                                <table v-if="stats.byType.length" class="w-full border-collapse text-[13px]">
                                    <caption class="sr-only">{{ t('ad.statByType') }}</caption>
                                    <thead>
                                        <tr class="text-left text-xs text-muted-color">
                                            <th scope="col" class="py-1.5 font-medium">{{ t('ad.statCol.type') }}</th>
                                            <th scope="col" class="py-1.5 text-right font-medium">{{ t('ad.statImpressions') }}</th>
                                            <th scope="col" class="py-1.5 text-right font-medium">{{ t('ad.statClicks') }}</th>
                                            <th scope="col" class="py-1.5 text-right font-medium">{{ t('ad.statCtr') }}</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="r in stats.byType" :key="r.type" class="border-t border-(--line)">
                                            <td class="py-1.5">{{ t(`bizApplication.adType.${r.type}`) }} <span class="text-[11px] text-muted-color">{{ t('adAdvertiser.bookingsN', { n: r.bookings }) }}</span></td>
                                            <td class="py-1.5 text-right tabular-nums">{{ UtilAdStats.num(r.impressions, locale) }}</td>
                                            <td class="py-1.5 text-right tabular-nums">{{ UtilAdStats.num(r.clicks, locale) }}</td>
                                            <td class="py-1.5 text-right tabular-nums">{{ UtilAdStats.ctrText(r, locale) }}</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </template>
                            <p class="m-0 text-xs text-muted-color">{{ t('ad.statCountRule') }}</p>
                        </div>
                    </TabPanel>

                    <TabPanel value="money">
                        <dl class="m-0 grid grid-cols-[9rem_1fr] gap-x-3 gap-y-2 text-[13px]">
                            <dt class="text-muted-color">{{ t('adAdvertiser.moneyPaid') }}</dt>
                            <dd class="m-0 font-semibold tabular-nums">{{ won(detail.money.paid) }}</dd>
                            <dt class="pl-3 text-muted-color">{{ t('bizApplication.method.ONLINE') }}</dt>
                            <dd class="m-0 tabular-nums">{{ won(detail.money.online) }}</dd>
                            <dt class="pl-3 text-muted-color">{{ t('bizApplication.method.MANUAL') }}</dt>
                            <dd class="m-0 tabular-nums">{{ won(detail.money.manual) }}</dd>
                            <dt class="text-muted-color">{{ t('adAdvertiser.moneyRefunded') }}</dt>
                            <dd class="m-0 tabular-nums" :class="detail.money.refunded ? 'text-red-600 dark:text-red-400' : ''">{{ detail.money.refunded ? `− ${won(detail.money.refunded)}` : won(0) }}</dd>
                            <dt class="border-t border-(--line) pt-2 font-medium">{{ t('adAdvertiser.moneyNet') }}</dt>
                            <dd class="m-0 border-t border-(--line) pt-2 font-semibold tabular-nums">{{ won(detail.money.paid - detail.money.refunded) }}</dd>
                        </dl>
                        <p class="m-0 mt-3 text-xs text-muted-color">{{ t('adAdvertiser.moneyNote') }}</p>
                    </TabPanel>

                    <TabPanel value="history">
                        <div class="flex flex-col gap-5">
                            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-views`">
                                <h3 :id="`${titleId}-views`" class="m-0 text-sm font-semibold">{{ t('adAdvertiser.secViews') }} <span class="text-[11px] font-normal text-muted-color">{{ t('adAdvertiser.viewsHint') }}</span></h3>
                                <p v-if="!detail.views.length" class="m-0 text-[13px] text-muted-color">{{ t('adAdvertiser.noViews') }}</p>
                                <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0 text-[13px]">
                                    <li v-for="(v, i) in detail.views" :key="`${v.at}-${i}`" class="flex flex-col gap-0.5 py-2">
                                        <span><span class="font-medium">{{ v.actor }}</span> <span class="text-[11px] text-muted-color">· {{ time.shortDateTime(v.at) }}</span></span>
                                        <span class="text-xs [overflow-wrap:anywhere]">{{ v.reason }}</span>
                                    </li>
                                </ul>
                            </section>
                            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-events`">
                                <h3 :id="`${titleId}-events`" class="m-0 text-sm font-semibold">{{ t('adAdvertiser.secEvents') }}</h3>
                                <p v-if="!detail.history.length" class="m-0 text-[13px] text-muted-color">{{ t('adAdvertiser.noEvents') }}</p>
                                <Timeline v-else :value="detail.history" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                                    <template #content="{ item }">
                                        <div class="flex flex-col pb-3">
                                            <span class="font-medium">{{ t(`adAdvertiser.action.${item.action}`) }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                                            <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                                            <span v-if="item.fields.length" class="text-xs">{{ item.fields.map((f: string) => t(`adAdvertiser.field.${f}`)).join(', ') }}</span>
                                        </div>
                                    </template>
                                </Timeline>
                            </section>
                        </div>
                    </TabPanel>
                </TabPanels>
            </Tabs>
        </template>

        <template v-if="detail && canManage" #footer>
            <div class="flex flex-wrap items-center justify-end gap-2">
                <span v-if="detail.kind === 'ADVERTISER' && !detail.deletable && !detail.archivable && detail.state === 'ACTIVE'" class="mr-auto text-xs text-muted-color">{{ t('adAdvertiser.archiveBlocked') }}</span>
                <Button v-if="detail.deletable" type="button" :label="t('common.delete')" icon="pi pi-trash" severity="danger" variant="text" :disabled="busy" @click="emit('remove')" />
                <template v-if="detail.kind === 'ADVERTISER'">
                    <Button v-if="detail.state === 'ARCHIVED'" type="button" :label="t('adAdvertiser.actUnarchive')" icon="pi pi-replay" severity="secondary" variant="outlined" :disabled="busy" @click="emit('unarchive')" />
                    <Button v-else-if="!detail.deletable" type="button" :label="t('adAdvertiser.actArchive')" icon="pi pi-inbox" severity="secondary" variant="outlined" :disabled="busy || !detail.archivable" @click="emit('archive')" />
                </template>
                <Button type="button" :label="t('adAdvertiser.actEdit')" icon="pi pi-pencil" :disabled="busy" @click="emit('edit')" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { AdAdvertiserDetail, AdAdvertiserRow } from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdAdvertiserRevealed, AdStatRange, AdStatsOverview } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdAdvertiser from '~/utils/util-ad-advertiser';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';
import UtilBusiness from '~/utils/util-business';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary?: AdAdvertiserRow | null;
    detail?: AdAdvertiserDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    canManage?: boolean;
    canReveal?: boolean;
    /** 지금 보이는 연락처 평문(부모가 받아 둔다) — null 이면 가린 값 */
    reveal?: AdAdvertiserRevealed | null;
    revealBusy?: boolean;
    /** 「성과」 탭 — 부모가 받아 넘긴다 */
    stats?: AdStatsOverview | null;
    statsRange: AdStatRange;
    statsLoading?: boolean;
    statsError?: ApiError | null;
}

const props = withDefaults(defineProps<Props>(), {
    summary: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    canManage: false,
    canReveal: false,
    reveal: null,
    revealBusy: false,
    stats: null,
    statsLoading: false,
    statsError: null,
});
const emit = defineEmits<{ retry: []; reveal: []; hide: []; edit: []; archive: []; unarchive: []; remove: []; stats: []; 'stats-retry': [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const tab = ref('basic');
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const head = computed(() => props.detail ?? props.summary);
const badge = computed(() => (head.value ? UtilAdAdvertiser.badge(head.value) : null));

/** 사업자 상태 때문에 새 광고가 막힌 경우 한 줄 */
const businessNote = computed(() => {
    switch (badge.value) {
        case 'BIZ_SUSPENDED':
            return t('adAdvertiser.noteBizSuspended');
        case 'BIZ_WITHDRAWN':
            return t('adAdvertiser.noteBizWithdrawn');
        case 'BIZ_AD_SUSPENDED':
            return t('adAdvertiser.noteAdSuspended');
        case 'BIZ_REVIEW':
            return t('adAdvertiser.noteBizReview');
        default:
            return '';
    }
});

// ── 연락처 평문 10분 ──
const endsAt = ref(0);
const countdown = useCountdown(endsAt, () => emit('hide'));
watch(
    () => props.reveal?.hideAt,
    (hideAt) => {
        if (hideAt) endsAt.value = Date.parse(hideAt);
        else countdown.stop();
    },
    { immediate: true },
);
const shown = computed(() => Boolean(props.reveal) && countdown.active.value);
const hasContact = computed(() => {
    const m = props.detail?.masked;
    return Boolean(m && (m.contactName || m.contactPhone || m.email));
});
const contactRows = computed(() => {
    const r = shown.value ? props.reveal : null;
    const m = props.detail!.masked;
    return [
        { key: 'name', label: t('ad.contactName'), value: r ? r.contactName : m.contactName },
        { key: 'phone', label: t('ad.contactPhone'), value: r ? r.contactPhone : m.contactPhone },
        { key: 'email', label: t('ad.contactEmail'), value: r ? r.email : m.email },
    ];
});

const countCells = computed(() => {
    const c = props.detail!.counts;
    return [
        { key: 'running', label: t('adAdvertiser.countRunning'), value: c.running, tone: c.running ? 'text-green-700 dark:text-green-400' : '' },
        { key: 'scheduled', label: t('adAdvertiser.countScheduled'), value: c.scheduled, tone: '' },
        { key: 'waiting', label: t('adAdvertiser.countWaiting'), value: c.waiting, tone: c.waiting ? 'text-amber-700 dark:text-amber-400' : '' },
        { key: 'total', label: t('adAdvertiser.countTotal'), value: c.total, tone: '' },
    ];
});

watch(
    () => props.summary?.id ?? props.detail?.id,
    () => (tab.value = 'basic'),
);
// 「성과」 탭을 열 때 부모에 알린다 — 그 광고주에 이미 받았으면 부모가 다시 부르지 않는다
watch(tab, (value) => {
    if (value === 'stats') emit('stats');
});
</script>
