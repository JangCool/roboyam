<template>
    <!--
        광고 건 상세 서랍 — 일정표 목록에서 줄을 누르면 연다(`?id=`). 02-ad.md 7-1.
        탭: 기본 · 소재(버전 목록 — 심사 승인 때 고정, 교체는 새 버전, 옛 버전 보존 — A2) · 일정 · 결제(온라인 결제 정보 또는 계약서 사본·입금 기록) · 처리 기록.
        맨 아래: [긴급 내림](진행·예약, `ad.takedown`) · [되돌리기](내린 건). 서버는 부르지 않는다 — 동작은 emit.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.advertiser.name }}</h2>
                    <Tag :value="t(`ad.status.${head.status}`)" :severity="UtilAd.statusSeverity(head.status)" />
                    <Tag :value="t(`bizApplication.method.${head.paymentMethod}`)" severity="secondary" />
                </div>
                <span class="truncate text-[12px] text-muted-color">{{ t(`bizApplication.adType.${head.type}`) }} · {{ UtilBizApplicationMessages.period(t, time, head.period) }}</span>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('ad.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <template v-else>
            <Message v-if="detail.takedown" severity="error" :closable="false" class="mb-3">
                <span class="text-[13px]">{{ t('ad.takenDownNote', { at: time.shortDateTime(detail.takedown.at), by: detail.takedown.by }) }} — {{ detail.takedown.reason }}<template v-if="detail.takedown.mailSent"> · {{ t('ad.mailSent') }}</template></span>
            </Message>
            <Tabs v-model:value="tab" scrollable>
                <TabList>
                    <Tab value="basic">{{ t('ad.tabBasic') }}</Tab>
                    <Tab value="creative">{{ t('ad.tabCreative') }} <span class="text-[11px] text-muted-color">{{ detail.creatives.length }}</span></Tab>
                    <Tab value="schedule">{{ t('ad.tabSchedule') }}</Tab>
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
                            <dt class="text-muted-color">{{ t('ad.amount') }}</dt>
                            <dd class="m-0 tabular-nums">{{ won(detail.amount.total) }} <span class="text-[11px] text-muted-color">({{ t('bizApplication.supplyVat', { supply: won(detail.amount.supply), vat: won(detail.amount.vat) }) }})</span></dd>
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
                                    {{ t('bizApplication.depositLine', { amount: won(detail.deposit.amount), date: time.serviceDate(detail.deposit.depositedOn), name: detail.deposit.depositorName, who: detail.deposit.confirmedBy }) }}
                                    <span v-if="detail.deposit.mismatchReason" class="block text-xs text-amber-700 dark:text-amber-400">{{ t('ad.mismatchReason', { reason: detail.deposit.mismatchReason }) }}</span>
                                </p>
                                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('ad.noDeposit') }}</p>
                                <p v-if="detail.status === 'SUBMITTED' || detail.status === 'AWAITING_DEPOSIT'" class="m-0 text-xs text-muted-color">{{ t('ad.goApplication') }}</p>
                            </template>
                        </div>
                    </TabPanel>
                    <TabPanel value="history">
                        <Timeline :value="historyDesc" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                            <template #content="{ item }">
                                <div class="flex flex-col pb-3">
                                    <span class="font-medium">{{ item.action }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                                    <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                                    <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ item.note }}</span>
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
import type { AdBookingDetail, AdBookingRow } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
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
}

const props = withDefaults(defineProps<Props>(), { summary: null, detail: null, loading: false, error: null, busy: false, canTakedown: false, canRestore: false });
const emit = defineEmits<{ retry: []; takedown: []; restore: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const tab = ref('basic');
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const head = computed(() => props.detail ?? props.summary);
const creativesDesc = computed(() => [...(props.detail?.creatives ?? [])].sort((a, b) => b.version - a.version));
const historyDesc = computed(() => [...(props.detail?.history ?? [])].sort((a, b) => b.at.localeCompare(a.at)));
const canTakedownNow = computed(() => props.canTakedown && Boolean(props.detail) && UtilAd.canTakedown(props.detail!.status));
const canRestoreNow = computed(() => props.canRestore && props.detail?.status === 'TAKEN_DOWN');

watch(
    () => props.summary?.id ?? props.detail?.id,
    () => (tab.value = 'basic'),
);
</script>
