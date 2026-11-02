<template>
    <!--
        광고 건 상세 서랍 — 일정표 목록에서 줄을 누르면 연다(`?id=`). 02-ad.md 7-1.
        탭: 기본 · 소재(버전 목록 — 심사 승인 때 고정, 교체는 새 버전, 옛 버전 보존 — A2) · 일정 · 성과 · 결제(온라인 결제 정보 또는 계약서 사본·입금 기록) · 처리 기록.
        성과(`AdBookingStats`): 건 기간 전체의 합계·날짜별 차트(정지·내림 띠)·자리별·소재 버전별. 탭을 처음 열 때 `stats` 를 내고(부모가 그 건에 한 번만 받는다), 시작 전이면 안내 한 줄만.
        맨 아래: [긴급 내림](진행·예약, `ad.takedown`) · [되돌리기](긴급 내린 건, `ad.restore`). 머리는 광고주 이름·상태만 — 본문 맨 위 요약 영역(`AdDrawerSummary`, 탭 위 고정)에 유형·기간·결제 방식·금액과 「광고 심사에서 열기」(건의 도메인 심사 화면 `?id=`)·「광고주 관리에서 열기」.
        처리 기록 코드는 신청 건 것(`UtilBizApplicationMessages` 로 번역). 서버는 부르지 않는다 — 동작은 emit.
        광고주 요청 정지·중단(biz-pause-contract): 내림 줄에 까닭 「광고주 요청(일시 정지)」·「광고주 요청(중단)」 + 누가(요청 경로·메모) · 정지 횟수 · 누적 멈춘 시간 · 끝 안내.
          [되돌리기]는 긴급 내림만 — 대신 정지·다시 켜기·중단은 「광고 심사에서 열기」로 가서 한다.
        결제 탭 맨 아래: 환불 합계 「결제 · 환불 · 남은 환불 가능」(상세 `refundSummary` — 결제 뒤만). [추가 환불](클레임)은 「광고 심사에서 열기」로 가서 한다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.advertiser.name }}</h2>
                    <Tag :value="t(UtilAd.statusKey(head))" :severity="UtilAd.rowSeverity(head)" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('ad.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <template v-else>
            <!-- 요약 — 탭 위에 고정(어느 탭에서나 보인다) -->
            <AdDrawerSummary :label="t('ad.summary')" :items="summaryItems">
                <template #links>
                    <NuxtLink :to="reviewTo" class="inline-flex items-center gap-1 font-medium text-(--p-primary-color) hover:underline">
                        <i class="pi pi-external-link text-[11px]" aria-hidden="true" />{{ t(`ad.openInReview.${detail.domain}`) }}
                    </NuxtLink>
                    <NuxtLink v-if="detail.advertiser.id" :to="{ path: '/ad-advertisers', query: { id: detail.advertiser.id } }" class="inline-flex items-center gap-1 font-medium text-(--p-primary-color) hover:underline">
                        <i class="pi pi-id-card text-[11px]" aria-hidden="true" />{{ t('ad.openAdvertiser') }}
                    </NuxtLink>
                </template>
            </AdDrawerSummary>

            <Message v-if="detail.takedown" :severity="pauseState === 'PAUSED' ? 'warn' : advertiser ? 'secondary' : 'error'" :closable="false" class="mb-3">
                <span class="flex flex-col gap-0.5 text-[13px]">
                    <span v-if="advertiser" class="[overflow-wrap:anywhere]">
                        {{ t('ad.takenDownNote', { at: time.shortDateTime(detail.takedown.at), cause: t(`bizApplication.takedownCause.${detail.takedown.cause}`) }) }} ·
                        {{ UtilBizApplicationMessages.pauseWho(t, detail.takedown.by, detail.takedown.channel, detail.takedown.memo) }}
                    </span>
                    <span v-else>
                        {{ t('ad.takenDownNote', { at: time.shortDateTime(detail.takedown.at), cause: t(`bizApplication.takedownCause.${detail.takedown.cause}`) }) }}<template v-if="detail.takedown.by"> · {{ detail.takedown.by }}</template><template v-if="detail.takedown.reason"> — {{ detail.takedown.reason }}</template>
                    </span>
                    <span v-if="advertiser && detail.pause" class="tabular-nums">{{ t('ad.pauseLine', { used: detail.pause.used, limit: detail.pause.limit, total: detail.pause.pausedMinutes > 0 ? UtilBizApplicationMessages.minutes(t, detail.pause.pausedMinutes) : t('bizApplication.pause.totalNone') }) }}</span>
                    <span v-if="pauseState === 'PAUSED'">{{ ended ? t('bizApplication.pause.endedWhilePaused') : t('bizApplication.pause.noRefundIfEnds', { end: time.full(detail.period.endAt) }) }}</span>
                    <span v-else-if="pauseState === 'STOPPED'">{{ t('bizApplication.pause.stoppedNote') }}</span>
                    <span v-if="advertiser && !ended && pauseState === 'PAUSED'" class="text-xs">{{ t('ad.pauseProxyHint') }}</span>
                    <span v-if="detail.takedown.mail" class="text-xs">{{ t(`ad.takedownMail.${detail.takedown.mail}`) }}</span>
                </span>
            </Message>
            <Tabs v-model:value="tab" scrollable>
                <TabList>
                    <Tab value="basic">{{ t('ad.tabBasic') }}</Tab>
                    <Tab value="creative">{{ t('ad.tabCreative') }} <span class="text-[11px] text-muted-color">{{ detail.creatives.length }}</span></Tab>
                    <Tab value="schedule">{{ t('ad.tabSchedule') }}</Tab>
                    <Tab value="stats">{{ t('ad.tabStats') }}</Tab>
                    <Tab value="payment">{{ t('ad.tabPayment') }}</Tab>
                    <Tab value="history">{{ t('ad.tabHistory') }}</Tab>
                </TabList>
                <TabPanels class="px-0">
                    <TabPanel value="basic">
                        <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-2 text-[13px]">
                            <dt class="text-muted-color">{{ t('ad.advertiser') }}</dt>
                            <dd class="m-0">{{ detail.advertiser.name }} <Tag :value="t(`ad.advertiserKind.${detail.advertiser.kind}`)" severity="secondary" class="ml-1" /><span v-if="detail.advertiser.maskedRegNo" class="ml-1 text-[11px] text-muted-color">{{ detail.advertiser.maskedRegNo }}</span></dd>
                            <dt class="text-muted-color">{{ t('ad.contact') }}</dt>
                            <dd class="m-0 tabular-nums">{{ detail.advertiser.contactMasked ?? '—' }}</dd>
                            <dt class="text-muted-color">{{ t('ad.type') }}</dt>
                            <dd class="m-0">{{ t(`bizApplication.adType.${detail.type}`) }}</dd>
                            <dt class="text-muted-color">{{ t('ad.weight') }}</dt>
                            <dd class="m-0 tabular-nums">{{ detail.weight }}</dd>
                            <dt class="text-muted-color">{{ t('ad.source') }}</dt>
                            <dd class="m-0">{{ t(`ad.sourceKind.${detail.source}`) }}<code v-if="detail.applicationNo" class="ml-1 text-[11px]">{{ detail.applicationNo }}</code></dd>
                            <template v-if="detail.registrant">
                                <dt class="text-muted-color">{{ t('ad.registrant') }}</dt>
                                <dd class="m-0">{{ detail.registrant.name }}<Tag v-if="detail.registrant.me" :value="t('ad.registrantMe')" severity="secondary" class="ml-1" /></dd>
                            </template>
                            <dt class="text-muted-color">{{ t('ad.amount') }}</dt>
                            <dd class="m-0 tabular-nums">
                                {{ won(detail.amount.total) }} <span class="text-[11px] text-muted-color">({{ t('bizApplication.supplyVat', { supply: won(detail.amount.supply), vat: won(detail.amount.vat) }) }})</span>
                                <span v-if="detail.proposedSupply !== null && detail.status === 'SUBMITTED'" class="block text-[11px] text-muted-color">{{ t('ad.proposedSupply', { supply: won(detail.proposedSupply) }) }}</span>
                            </dd>
                            <template v-if="detail.memo">
                                <dt class="text-muted-color">{{ t('ad.memo') }}</dt>
                                <dd class="m-0">{{ detail.memo }}</dd>
                            </template>
                        </dl>
                    </TabPanel>
                    <TabPanel value="creative">
                        <ul class="m-0 flex list-none flex-col gap-3 p-0">
                            <li v-for="c in creativesDesc" :key="c.version" class="flex flex-col gap-2 rounded-lg border px-3 py-2.5" :class="c.current ? 'border-(--p-primary-color)' : 'border-(--line) opacity-75'">
                                <div class="flex items-center justify-between gap-2 text-[13px]">
                                    <span class="font-semibold">{{ t('ad.versionN', { n: c.version }) }}</span>
                                    <Tag :value="c.current ? t('ad.versionCurrent') : t('ad.versionOld')" :severity="c.current ? 'success' : 'secondary'" />
                                </div>
                                <img v-if="c.imageUrl" :src="c.imageUrl" :alt="c.altText ?? ''" class="h-auto max-w-full rounded" />
                                <p v-if="c.text" class="m-0 rounded bg-(--p-content-hover-background) px-2.5 py-1.5 text-[13px]">{{ c.text }}</p>
                                <span class="text-[11px] text-muted-color [overflow-wrap:anywhere]">{{ c.linkUrl }} · {{ c.approvedAt ? t('ad.approvedBy', { who: c.approvedBy, at: time.shortDateTime(c.approvedAt) }) : t('ad.notReviewedYet') }}</span>
                            </li>
                        </ul>
                        <p class="m-0 mt-2 text-xs text-muted-color">{{ t('ad.versionNote') }}</p>
                    </TabPanel>
                    <TabPanel value="schedule">
                        <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-2 text-[13px]">
                            <dt class="text-muted-color">{{ t('ad.start') }}</dt>
                            <dd class="m-0 tabular-nums">{{ time.full(detail.period.startAt) }}</dd>
                            <dt class="text-muted-color">{{ t('ad.end') }}</dt>
                            <dd class="m-0 tabular-nums">{{ time.full(detail.period.endAt) }} <span class="text-[11px] text-muted-color">{{ t('ad.endExclusive') }}</span></dd>
                            <dt class="text-muted-color">{{ t('ad.length') }}</dt>
                            <dd class="m-0">{{ t('bizApplication.daysN', { n: UtilBizApplication.days(detail.period) }) }}</dd>
                        </dl>
                    </TabPanel>
                    <TabPanel value="stats">
                        <AdBookingStats v-if="started" :stats="stats" :loading="statsLoading" :error="statsError" @retry="emit('stats-retry')" />
                        <p v-else class="m-0 text-[13px] text-muted-color">{{ t('ad.statNotStarted') }}</p>
                    </TabPanel>
                    <TabPanel value="payment">
                        <div class="flex flex-col gap-3">
                            <template v-if="detail.paymentMethod === 'ONLINE'">
                                <p v-if="detail.onlinePayment" class="m-0 text-[13px]">{{ t('bizApplication.paidOnline', { at: time.shortDateTime(detail.onlinePayment.paidAt), tx: detail.onlinePayment.pgTxId }) }}</p>
                                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('ad.notPaidYet') }}</p>
                            </template>
                            <template v-else>
                                <h3 class="m-0 text-[13px] font-semibold">{{ t('bizApplication.contract.title') }}</h3>
                                <BizContractFiles :files="detail.contracts" />
                                <h3 class="m-0 text-[13px] font-semibold">{{ t('bizApplication.deposit.title') }}</h3>
                                <p v-if="detail.deposit" class="m-0 text-[13px]">
                                    {{ t('bizApplication.depositLine', { amount: won(detail.deposit.amount), date: time.serviceDate(detail.deposit.depositedOn), name: detail.deposit.depositorName, who: detail.deposit.confirmedBy, at: time.shortDateTime(detail.deposit.confirmedAt) }) }}
                                    <span v-if="detail.deposit.mismatchReason" class="block text-xs text-amber-700 dark:text-amber-400">{{ t('ad.mismatchReason', { reason: detail.deposit.mismatchReason }) }}</span>
                                </p>
                                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('ad.noDeposit') }}</p>
                                <p v-if="detail.status === 'SUBMITTED' || detail.status === 'AWAITING_DEPOSIT'" class="m-0 text-xs text-muted-color">{{ t('ad.goApplication') }}</p>
                            </template>
                            <template v-if="detail.refund">
                                <h3 class="m-0 text-[13px] font-semibold">{{ t('bizApplication.refundTitle') }} <span class="font-normal text-muted-color">· {{ t(`bizApplication.refundType.${detail.refund.type}`) }} · {{ t(`bizApplication.refundStatus.${detail.refund.status}`) }}</span></h3>
                                <p class="m-0 text-[13px] tabular-nums">
                                    <template v-if="detail.refund.approvedAmount !== null">{{ t('bizApplication.refundApprovedLine', { amount: won(detail.refund.approvedAmount) }) }}</template>
                                    <template v-else>{{ t('bizApplication.refundCalc', { paid: won(detail.refund.paidAmount), used: detail.refund.usedDays, total: detail.refund.totalDays, proposed: won(detail.refund.proposedAmount) }) }}</template>
                                </p>
                                <!-- 광고주 요청 중단 — 제안 내역(일할 − 중도 해지 수수료 = 제안액, 서버 환불 줄 값 그대로) -->
                                <p v-if="detail.refund.type === 'STOP_PRORATED' && detail.refund.proratedAmount !== null" class="m-0 text-xs tabular-nums text-muted-color [overflow-wrap:anywhere]">
                                    {{ UtilBizApplicationMessages.stopBreakdown(t, detail.refund.proratedAmount, detail.refund.feeRate ?? 0, detail.refund.feeAmount ?? 0, detail.refund.proposedAmount, locale) }}
                                    <template v-if="detail.refund.basisAt"> · {{ t('bizApplication.stopFee.basisAt', { at: time.shortDateTime(detail.refund.basisAt) }) }}</template>
                                </p>
                            </template>
                            <!-- 환불 합계(추가 환불 클레임) — 추가 환불은 심사 화면에서 -->
                            <div v-if="detail.refundSummary" class="flex flex-col gap-1 border-t border-(--line) pt-3">
                                <BizRefundSummaryLine :summary="detail.refundSummary" />
                                <p v-if="detail.refundSummary.refundable > 0" class="m-0 text-xs text-muted-color">{{ t('bizApplication.claim.drawerHint') }}</p>
                            </div>
                        </div>
                    </TabPanel>
                    <TabPanel value="history">
                        <Timeline :value="historyDesc" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                            <template #content="{ item }">
                                <div class="flex flex-col pb-3">
                                    <span class="font-medium">{{ UtilBizApplicationMessages.historyAction(t, item.action) }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                                    <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                                    <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ UtilBizApplicationMessages.historyNote(t, item.action, item.note, locale) }}</span>
                                </div>
                            </template>
                        </Timeline>
                    </TabPanel>
                </TabPanels>
            </Tabs>
        </template>

        <template v-if="detail && (canTakedownNow || canRestoreNow)" #footer>
            <div class="flex justify-end gap-2">
                <Button v-if="canRestoreNow" type="button" :label="t('ad.actRestore')" icon="pi pi-replay" :disabled="busy" @click="emit('restore')" />
                <Button v-if="canTakedownNow" type="button" :label="t('ad.actTakedown')" icon="pi pi-power-off" severity="danger" variant="outlined" :disabled="busy" @click="emit('takedown')" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { AdDrawerSummaryItem } from '~/components/ad/AdDrawerSummary.types';
import type { AdApplicationStats, AdBookingDetail, AdBookingRow } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary?: AdBookingRow | null;
    detail?: AdBookingDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    canTakedown?: boolean;
    canRestore?: boolean;
    /** 「성과」 탭 — 부모가 `stats` 를 받아 넘긴다 */
    stats?: AdApplicationStats | null;
    statsLoading?: boolean;
    statsError?: ApiError | null;
}

const props = withDefaults(defineProps<Props>(), { summary: null, detail: null, loading: false, error: null, busy: false, canTakedown: false, canRestore: false, stats: null, statsLoading: false, statsError: null });
const emit = defineEmits<{ retry: []; takedown: []; restore: []; stats: []; 'stats-retry': [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const tab = ref('basic');
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const head = computed(() => props.detail ?? props.summary);
/** 요약 영역 — 유형 · 기간(일자 강조) · 결제 방식 · 금액(주 색) */
const summaryItems = computed<AdDrawerSummaryItem[]>(() => {
    const d = props.detail;
    if (!d) return [];
    const period = UtilBizApplicationMessages.period(t, time.value, d.period);
    return [
        { key: 'type', label: t('ad.type'), value: t(`bizApplication.adType.${d.type}`) },
        { key: 'period', label: t('ad.col.period'), value: period, valueClass: UtilBizApplication.EMPH.date, title: period },
        { key: 'method', label: t('ad.col.method'), value: t(`bizApplication.method.${d.paymentMethod}`) },
        { key: 'amount', label: t('ad.amount'), value: won(d.amount.total), valueClass: UtilBizApplication.EMPH.money },
    ];
});
/** 「광고 심사에서 열기」 — 건의 도메인 심사 화면 `?id=` */
const reviewTo = computed(() => UtilAd.reviewLink(head.value!));
/** 광고주 요청 정지·중단 — 긴급 내림과 문구·색을 가른다 */
const advertiser = computed(() => UtilBizApplication.isAdvertiserCause(props.detail?.takedown?.cause));
const pauseState = computed(() => (props.detail ? UtilBizApplication.pauseState(props.detail.status, props.detail.takedown?.cause) : null));
const ended = computed(() => Boolean(props.detail && UtilBizApplication.ended(props.detail.period)));
const creativesDesc = computed(() => [...(props.detail?.creatives ?? [])].sort((a, b) => b.version - a.version));
const historyDesc = computed(() => [...(props.detail?.history ?? [])].sort((a, b) => b.at.localeCompare(a.at)));
const canTakedownNow = computed(() => props.canTakedown && Boolean(props.detail) && UtilAd.canTakedown(props.detail!.status));
const canRestoreNow = computed(() => props.canRestore && Boolean(props.detail) && UtilAd.canRestore(props.detail!.status, props.detail!.takedown));
/** 성과가 있을 수 있나 — 결제 전·심사 대기가 아니고 시작했다 */
const started = computed(() => Boolean(props.detail && UtilAdStats.started(props.detail)));

watch(
    () => props.summary?.id ?? props.detail?.id,
    () => (tab.value = 'basic'),
);
// 「성과」 탭을 열 때 부모에 알린다 — 그 건에 이미 받았으면 부모가 다시 부르지 않는다
watch(tab, (value) => {
    if (value === 'stats' && started.value) emit('stats');
});
</script>
