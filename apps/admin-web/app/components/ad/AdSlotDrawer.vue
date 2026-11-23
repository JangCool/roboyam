<template>
    <!--
        광고 자리 드로어(`/ads/slots`) — 미리보기에서 자리(또는 아래 종류 줄)를 누르면 연다. 오른쪽, 바탕을 막지 않는다(`modal=false` · 바깥을 눌러도 닫지 않는다 — 미리보기에서 다른 자리를 누르면 그 종류로 바뀐다).
        광고는 종류마다 관리된다 — 같은 종류의 자리는 같은 목록이고 「나가는 곳」으로 알린다(계약 ad-slot-manager-contract).
        목록: 머리 종류 이름 + [광고 등록](`ad.manage` · 운영자 등록이 되는 종류만) → 나가는 곳 · 진행 중 / 한도 · 빈 자리 애드센스 → 상태 거르기 → (고르면) 「고른 N건 내리기」 → 카드.
          카드(낮게): 제목 한 줄(truncate) + 상태 태그 → 섬네일 전체 폭(종류별 높이, 한 줄 광고·핫딜 자리는 없음) → 네 칸(기간 · 남은 날 · 노출 · 클릭률 — 최근 7일) → 광고주 + [상세] · [내리기].
        상세는 같은 드로어 안에서 바뀐다(두 겹으로 쌓지 않음 — 머리에 뒤로 화살표): 섬네일 → 기본 정보 세 칸 격자 → 성과(최근 7일) → 링크(예시 주소면 경고) → [기존 서랍에서 열기] · [내리기].
        내리기 단추는 `ad.takedown` 이 있고 진행·예약 건만(기존 긴급 내림과 같은 조건). 서버는 부르지 않는다 — 동작은 emit(부른 화면이 사유 대화상자·저장을 맡는다).
    -->
    <Drawer
        :visible="open"
        position="right"
        :modal="false"
        :dismissable="false"
        :aria-labelledby="titleId"
        :style="{ width: '30rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-1.5">
                <Button v-if="detailCard" type="button" icon="pi pi-arrow-left" size="small" severity="secondary" variant="text" rounded :aria-label="t('adSlot.back')" @click="detailId = null" />
                <h2 :id="titleId" class="m-0 min-w-0 flex-1 truncate text-base font-semibold" :title="headTitle">{{ headTitle }}</h2>
                <Button v-if="!detailCard && canRegister" type="button" :label="t('adSlot.register')" icon="pi pi-plus" size="small" @click="emit('register')" />
            </div>
        </template>

        <!-- ── 상세(같은 드로어 안) ── -->
        <div v-if="detailCard" class="flex flex-col gap-4 pt-1">
            <div v-if="thumb && detailCard.imageUrl" class="overflow-hidden rounded-lg bg-surface-100 dark:bg-surface-800">
                <img :src="detailCard.imageUrl" :alt="detailCard.title" class="block w-full" :style="{ height: `${thumb.height * 1.6}px`, objectFit: thumb.fit }" />
            </div>
            <p v-else-if="detailCard.text" class="m-0 rounded-lg bg-(--p-content-hover-background) px-3 py-2 text-[13px]">{{ detailCard.text }}</p>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-basic`">
                <h3 :id="`${titleId}-basic`" class="m-0 text-[13px] font-semibold">{{ t('adSlot.detailBasic') }}</h3>
                <dl class="m-0 grid grid-cols-3 gap-2">
                    <div v-for="f in basicFields" :key="f.key" class="flex min-w-0 flex-col gap-0.5 rounded-lg border border-(--line) px-2.5 py-2" :class="f.wide ? 'col-span-3' : ''">
                        <dt class="text-[11px] text-muted-color">{{ f.label }}</dt>
                        <dd class="m-0 min-w-0 truncate text-[13px] tabular-nums" :title="f.value">
                            <Tag v-if="f.key === 'status'" :value="f.value" :severity="UtilAd.rowSeverity(detailCard)" />
                            <template v-else>{{ f.value }}</template>
                        </dd>
                    </div>
                </dl>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-perf`">
                <h3 :id="`${titleId}-perf`" class="m-0 text-[13px] font-semibold">{{ t('adSlot.perf') }}</h3>
                <dl class="m-0 grid grid-cols-3 gap-2">
                    <div v-for="p in perfCells(detailCard)" :key="p.key" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-2.5 py-2">
                        <dt class="text-[11px] text-muted-color">{{ p.label }}</dt>
                        <dd class="m-0 text-base font-semibold tabular-nums">{{ p.value }}</dd>
                    </div>
                </dl>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-link`">
                <h3 :id="`${titleId}-link`" class="m-0 text-[13px] font-semibold">{{ t('adSlot.link') }}</h3>
                <a v-if="detailCard.linkUrl" :href="detailCard.linkUrl" target="_blank" rel="noopener noreferrer" class="text-[13px] text-(--p-primary-color) hover:underline [overflow-wrap:anywhere]">{{ detailCard.linkUrl }}</a>
                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('adSlot.noLink') }}</p>
                <Message v-if="detailCard.exampleUrl" severity="warn" size="small" :closable="false">{{ t('adSlot.exampleUrl') }}</Message>
            </section>
        </div>

        <!-- ── 목록 ── -->
        <div v-else class="flex flex-col gap-3 pt-1">
            <div v-if="summary" class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                <div class="flex min-w-0 items-start gap-2">
                    <span class="w-16 shrink-0 text-muted-color">{{ t('adSlot.places') }}</span>
                    <span class="flex min-w-0 flex-wrap gap-1">
                        <Tag v-for="p in summary.places" :key="p" :value="t(`ad.statPlacement.${p}`)" severity="secondary" />
                    </span>
                </div>
                <div class="flex items-center gap-2">
                    <span class="w-16 shrink-0 text-muted-color">{{ t('adSlot.running') }}</span>
                    <span class="tabular-nums" :class="summary.running >= summary.limit ? 'font-semibold text-amber-700 dark:text-amber-400' : ''">{{ t('adSlot.runningOfLimit', { running: summary.running, limit: summary.limit }) }}</span>
                </div>
                <div class="flex items-center gap-2">
                    <span class="w-16 shrink-0 text-muted-color">{{ t('adSlot.adsenseLabel') }}</span>
                    <span :class="summary.adsense === 'FILLS' ? 'text-green-700 dark:text-green-400' : summary.adsense === 'PARTIAL' ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">{{ t(`adSlot.adsense.${summary.adsense}`) }}</span>
                </div>
            </div>

            <SelectButton
                :model-value="status ?? 'ALL'"
                :options="filterOptions"
                option-label="label"
                option-value="value"
                :allow-empty="false"
                size="small"
                :aria-label="t('adSlot.filterLabel')"
                class="flex-wrap"
                @update:model-value="(v: string) => setStatus(v)"
            />

            <div v-if="canTakedown && selected.length" class="flex items-center justify-between gap-2 rounded-lg bg-red-50 px-3 py-2 dark:bg-red-950/40">
                <Button type="button" :label="t('adSlot.selectedTakedown', { n: selected.length })" icon="pi pi-power-off" severity="danger" size="small" :disabled="busy" @click="emit('takedown', [...selected])" />
                <Button type="button" :label="t('adSlot.clearSelection')" size="small" severity="secondary" variant="text" @click="selected = []" />
            </div>

            <ErrorState v-if="error" :title="t('adSlot.listFailed')" :description="error.message" :retry="true" :retrying="loading" @retry="emit('retry')" />
            <div v-else-if="!list || loading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="6.5rem" />
            </div>
            <EmptyState v-else-if="!list.items.length" icon="pi pi-megaphone" :title="status ? t('adSlot.listEmptyFiltered') : t('adSlot.listEmpty')">
                <Button v-if="!status && canRegister" type="button" :label="t('adSlot.register')" icon="pi pi-plus" size="small" @click="emit('register')" />
            </EmptyState>

            <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                <li v-for="card in list.items" :key="card.id" class="flex flex-col gap-2 rounded-lg border px-3 py-2.5" :class="selected.includes(card.id) ? 'border-red-400 dark:border-red-500' : 'border-(--line)'">
                    <div class="flex min-w-0 items-center gap-2">
                        <Checkbox v-if="canTakedown && UtilAd.canTakedown(card.status)" v-model="selected" :value="card.id" :input-id="`${titleId}-${card.id}`" :aria-label="t('adSlot.pickCard', { title: card.title })" />
                        <button type="button" class="min-w-0 flex-1 truncate border-0 bg-transparent p-0 text-left text-[13px] font-semibold text-color hover:underline" :title="card.title" @click="detailId = card.id">{{ card.title }}</button>
                        <i v-if="card.exampleUrl" v-tooltip.left="t('adSlot.exampleUrlShort')" class="pi pi-exclamation-triangle shrink-0 text-xs text-amber-600 dark:text-amber-400" :aria-label="t('adSlot.exampleUrlShort')" />
                        <Tag :value="t(UtilAd.statusKey(card))" :severity="UtilAd.rowSeverity(card)" class="shrink-0" />
                    </div>
                    <div v-if="thumb && card.imageUrl" class="overflow-hidden rounded bg-surface-100 dark:bg-surface-800">
                        <img :src="card.imageUrl" alt="" class="block w-full" :style="{ height: `${thumb.height}px`, objectFit: thumb.fit }" loading="lazy" />
                    </div>
                    <dl class="m-0 grid grid-cols-4 gap-1 text-center">
                        <div v-for="c in cardCells(card)" :key="c.key" class="flex min-w-0 flex-col">
                            <dt class="text-[11px] text-muted-color">{{ c.label }}</dt>
                            <dd class="m-0 truncate text-[12px] tabular-nums" :title="c.value">{{ c.value }}</dd>
                        </div>
                    </dl>
                    <div class="flex min-w-0 items-center gap-1">
                        <span class="min-w-0 flex-1 truncate text-[12px] text-muted-color" :title="card.advertiser.name">{{ card.advertiser.name }}</span>
                        <Button type="button" :label="t('adSlot.detail')" size="small" severity="secondary" variant="text" @click="detailId = card.id" />
                        <Button v-if="canTakedown && UtilAd.canTakedown(card.status)" type="button" :label="t('adSlot.takedown')" size="small" severity="danger" variant="text" :disabled="busy" @click="emit('takedown', [card.id])" />
                    </div>
                </li>
            </ul>
        </div>

        <template v-if="detailCard" #footer>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('adSlot.openBooking')" icon="pi pi-external-link" severity="secondary" variant="outlined" size="small" @click="emit('open-booking', detailCard.id)" />
                <Button v-if="canTakedown && UtilAd.canTakedown(detailCard.status)" type="button" :label="t('adSlot.takedown')" icon="pi pi-power-off" severity="danger" size="small" :disabled="busy" @click="emit('takedown', [detailCard.id])" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { AdType } from '~/composables/ad/api/useAdApi.types';
import type { AdSlotCard, AdSlotListView, AdSlotStatusFilter, AdSlotSummary } from '~/composables/ad/api/useAdSlotApi.types';
import { AD_SLOT_STATUS_FILTERS } from '~/composables/ad/api/useAdSlotApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdSlot from '~/utils/util-ad-slot';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 열린 종류 */
    type: AdType | null;
    /** 그 종류의 요약(나가는 곳 · 진행 중 / 한도 · 애드센스) */
    summary?: AdSlotSummary | null;
    list?: AdSlotListView | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 상태 거르기(null = 전체) */
    status?: AdSlotStatusFilter | null;
    /** 내리는 중 */
    busy?: boolean;
    canTakedown?: boolean;
    /** [광고 등록](`ad.manage` 이고 운영자 등록이 되는 종류) */
    canRegister?: boolean;
}

const props = withDefaults(defineProps<Props>(), { summary: null, list: null, loading: false, error: null, status: null, busy: false, canTakedown: false, canRegister: false });
const emit = defineEmits<{ retry: []; status: [status: AdSlotStatusFilter | null]; takedown: [ids: string[]]; register: []; 'open-booking': [id: string] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const titleId = useId();

/** 고른 카드(여러 건 내리기) */
const selected = ref<string[]>([]);
/** 같은 드로어 안에서 연 상세 */
const detailId = ref<string | null>(null);

const detailCard = computed<AdSlotCard | null>(() => (detailId.value ? (props.list?.items.find((c) => c.id === detailId.value) ?? null) : null));
const thumb = computed(() => (props.type ? UtilAdSlot.THUMB[props.type] : null));
const headTitle = computed(() => (detailCard.value ? detailCard.value.title : props.type ? t(`bizApplication.adType.${props.type}`) : t('adSlot.drawerTitle')));

const filterOptions = computed(() => [
    { value: 'ALL', label: `${t('adSlot.filterAll')} ${props.list?.counts.ALL ?? ''}`.trim() },
    ...AD_SLOT_STATUS_FILTERS.map((s) => ({ value: s, label: `${s === 'AWAITING_DEPOSIT' ? t('adSlot.filterAwaiting') : t(`ad.status.${s}`)} ${props.list?.counts[s] ?? ''}`.trim() })), // 대기는 온라인 결제 대기도 함께(계약 「API」)
]);

function setStatus(value: string): void {
    const next = value === 'ALL' ? null : (value as AdSlotStatusFilter);
    if (next !== props.status) emit('status', next);
}

const ctr = (c: AdSlotCard) => UtilAdStats.ctrText({ impressions: c.impressions7d, clicks: c.clicks7d }, locale.value);

/** 카드 네 칸 — 기간(짧은 날짜, 끝은 반열림이라 1ms 앞) · 남은 날 · 노출 · 클릭률(최근 7일) */
function cardCells(c: AdSlotCard): { key: string; label: string; value: string }[] {
    return [
        { key: 'period', label: t('adSlot.cell.period'), value: `${time.value.shortDate(c.period.startAt)}~${time.value.shortDate(UtilAdSlot.lastMoment(c.period.endAt))}` },
        { key: 'remain', label: t('adSlot.cell.remain'), value: t('adSlot.remainDays', { n: c.remainingDays }) },
        { key: 'imp', label: t('adSlot.cell.impressions'), value: UtilAdStats.num(c.impressions7d, locale.value) },
        { key: 'ctr', label: t('adSlot.cell.ctr'), value: ctr(c) },
    ];
}

function perfCells(c: AdSlotCard): { key: string; label: string; value: string }[] {
    return [
        { key: 'imp', label: t('adSlot.cell.impressions'), value: UtilAdStats.num(c.impressions7d, locale.value) },
        { key: 'clicks', label: t('adSlot.cell.clicks'), value: UtilAdStats.num(c.clicks7d, locale.value) },
        { key: 'ctr', label: t('adSlot.cell.ctr'), value: ctr(c) },
    ];
}

/** 상세 기본 정보 — 세 칸 격자(기간·나가는 곳은 한 줄 전체) */
const basicFields = computed<{ key: string; label: string; value: string; wide?: boolean }[]>(() => {
    const c = detailCard.value;
    if (!c) return [];
    return [
        { key: 'advertiser', label: t('adSlot.field.advertiser'), value: `${c.advertiser.name} · ${t(`ad.advertiserKind.${c.advertiser.kind}`)}` },
        { key: 'status', label: t('adSlot.field.status'), value: t(UtilAd.statusKey(c)) },
        { key: 'method', label: t('adSlot.field.method'), value: t(`bizApplication.method.${c.paymentMethod}`) },
        { key: 'amount', label: t('adSlot.field.amount'), value: UtilBizApplicationMessages.won(t, c.amount.total, locale.value) },
        { key: 'remain', label: t('adSlot.cell.remain'), value: t('adSlot.remainDays', { n: c.remainingDays }) },
        { key: 'type', label: t('adSlot.field.type'), value: t(`bizApplication.adType.${c.type}`) },
        { key: 'period', label: t('adSlot.field.period'), value: UtilBizApplicationMessages.period(t, time.value, c.period), wide: true },
        { key: 'places', label: t('adSlot.field.places'), value: (props.summary?.places ?? []).map((p) => t(`ad.statPlacement.${p}`)).join(' · ') || '—', wide: true },
    ];
});

// 다른 종류를 열면 상세·고르기를 처음으로
watch(
    () => props.type,
    () => {
        detailId.value = null;
        selected.value = [];
    },
);
// 목록이 새로 오면(거르기·내림 뒤) 없는 카드·내릴 수 없게 된 카드는 고르기에서 뺀다(외부 데이터 동기화)
watch(
    () => props.list,
    (list) => {
        selected.value = UtilAdSlot.takedownIds(selected.value, list?.items ?? []);
    },
);
// 드로어를 닫으면 상세에서 목록으로
watch(open, (v) => {
    if (!v) detailId.value = null;
});
</script>
