<template>
    <!--
        작가 정산 서랍 — 작가 정산 화면(`/robotikon/settlements`)에서 줄을 누르면 연다(`?id=`). 서버는 부르지 않는다 — 동작은 emit, 부모가 확인·사유·지급 기록 대화상자를 띄운다.
        위에서 아래로: 요약(작가 · 개인/사업자/정산 정보 없음 · 판매 달 · 지급 예정일 · 확정 시각) · 보류/지급 완료 기록
        · 금액 계산(판매액 − 환불 − 부가세 = 정산 기준 − 수수료(줄의 수수료율) = 작가 몫 + 이월 받음 − 원천징수 = 지급액 / 다음 달로 넘김 · 음수면 다음 달에서 차감)
          금액은 서버가 계산한 그대로(음수 가능 — 확정 뒤 환불). 정산 정보가 없는 예정 줄(`payeeType` null)의 원천징수는 개인 기준 어림값이라 그렇게 적는다.
        · 그 달의 팩별 내역 · 정산 계좌(가린 값 · [전체 보기] — `emoticon.payout.pii`, 사유 뒤 정해진 시간만 보이고 저절로 가린다) · 처리 기록.
        아래(`emoticon.settlement`): 예정이면 [보류]·[확정](환불 기간이 끝난 달만) · 확정이면 [보류]·[지급 완료 기록](지급할 금액이 있을 때) · 보류면 [보류 풀기]
          (+ 정산 정보 없이 탈퇴한 작가의 보류면 [포기 처리] — V148, 사유 대화상자는 부모). 지급 완료·포기 줄은 더 할 일이 없다.
        포기(`WAIVED`) 줄: 위 안내(작가 동의로 포기 · 운영자 포기 처리 — 처리 기록의 `WAIVED` 로 가른다) · 금액 계산 끝에 「포기한 금액」 · 요약의 시각은 「포기 시각」.
        보류한 사람이 없으면(`hold.by` null) 시스템 보류(작가 강제 탈퇴)로 보인다.
        계좌 전체 값은 부모 메모리에만 있다 — 토스트·로그·주소에 싣지 않는다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ t('emoticon.settlementTitleOf', { name: head.creator.nickname, ...UtilEmoticon.monthParts(head.month) }) }}</h2>
                <div class="flex flex-wrap items-center gap-1.5">
                    <Tag :value="t(`emoticon.settlementStatus.${head.status}`)" :severity="UtilEmoticon.settlementSeverity(head.status)" />
                    <Tag :value="UtilEmoticonMessages.payeeType(t, head.payeeType)" :severity="head.payeeType ? 'secondary' : 'warn'" />
                </div>
            </div>
        </template>

        <DrawerSummary v-if="head" :label="t('drawerSummary.label')" :items="summaryItems">
            <template #item-creator>
                <NuxtLink v-if="!head.creator.withdrawn" :to="UtilEmoticon.memberLink(head.creator.memberNo)" class="text-primary hover:underline">{{ head.creator.nickname }}</NuxtLink>
                <span v-else class="text-muted-color">{{ head.creator.nickname }}</span>
            </template>
        </DrawerSummary>

        <ErrorState v-if="error" :title="t('emoticon.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <Message v-if="detail.hold" severity="error" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.holdNote', { by: detail.hold.by ?? t('emoticon.settlementBySystem'), at: time.shortDateTime(detail.hold.at) }) }} — {{ detail.hold.reason }}</span>
            </Message>
            <Message v-if="detail.status === 'WAIVED'" severity="warn" :closable="false" data-testid="emoticon-settlement-waived">
                <span class="text-[13px]">{{ waivedNote }}</span>
            </Message>
            <Message v-if="detail.paid" severity="success" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.paidNote', { date: detail.paid.transferDate, by: detail.paid.by ?? t('emoticon.settlementByUnknown') }) }}<template v-if="detail.paid.memo"> — {{ detail.paid.memo }}</template></span>
            </Message>
            <Message v-if="detail.creator.withdrawn && detail.status !== 'WAIVED'" severity="warn" :closable="false">
                <span class="text-[13px]">{{ detail.account ? t('emoticon.settlementWithdrawnNote') : t('emoticon.settlementWithdrawnNoAccountNote') }}</span>
            </Message>
            <Message v-if="detail.status === 'ESTIMATED' && !closable" severity="info" :closable="false">
                <span class="text-[13px]">{{ t('emoticon.notClosedNote', { at: time.shortDateTime(detail.closesAt) }) }}</span>
            </Message>

            <!-- 금액 계산 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-calc`">
                <h3 :id="`${titleId}-calc`" class="m-0 text-sm font-semibold">{{ t('emoticon.secCalc') }}</h3>
                <dl class="m-0 flex flex-col rounded-lg border border-(--line) text-[13px]">
                    <div v-for="line in calcLines" :key="line.key" class="flex items-baseline justify-between gap-3 border-b border-(--line) px-3 py-2 last:border-b-0" :class="line.strong ? 'bg-(--p-content-hover-background) font-semibold' : ''">
                        <dt class="min-w-0">
                            {{ line.label }}
                            <span v-if="line.note" class="ml-1 text-[11px] font-normal text-muted-color">{{ line.note }}</span>
                        </dt>
                        <dd class="m-0 shrink-0 tabular-nums" :class="line.tone">{{ line.value }}</dd>
                    </div>
                </dl>
                <p v-if="detail.payeeType === 'BUSINESS'" class="m-0 text-xs text-muted-color">{{ t('emoticon.businessInvoiceNote') }}</p>
            </section>

            <!-- 팩별 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-packs`">
                <h3 :id="`${titleId}-packs`" class="m-0 text-sm font-semibold">{{ t('emoticon.secSettlementPacks') }}</h3>
                <p v-if="!detail.packs.length" class="m-0 text-[13px] text-muted-color">{{ t('emoticon.settlementNoSales') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="p in detail.packs" :key="p.packId" class="flex items-center gap-3 rounded-lg border border-(--line) px-3 py-2">
                        <EmoticonPackCover :src="p.coverUrl" :alt="p.packName" :size="36" />
                        <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                            <span class="truncate text-[13px] font-medium">{{ p.packName }}</span>
                            <span class="text-[11px] tabular-nums text-muted-color">{{ t('emoticon.packLine', { price: p.price.toLocaleString(locale), count: p.salesCount, refunds: p.refundCount }) }}</span>
                        </div>
                        <div class="shrink-0 text-right">
                            <div class="text-[13px] tabular-nums">{{ won(p.salesAmount) }}</div>
                            <div v-if="p.refundAmount" class="text-[11px] tabular-nums text-amber-700 dark:text-amber-400">−{{ won(p.refundAmount) }}</div>
                        </div>
                    </li>
                </ul>
            </section>

            <!-- 정산 계좌 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-account`">
                <h3 :id="`${titleId}-account`" class="m-0 text-sm font-semibold">{{ t('emoticon.secAccount') }}</h3>
                <p v-if="!detail.account" class="m-0 text-[13px] text-red-700 dark:text-red-400">{{ t('emoticon.accountMissing') }}</p>
                <div v-else-if="revealed" class="flex flex-col gap-1 rounded-lg border border-amber-300 px-3 py-2 dark:border-amber-800">
                    <div class="flex flex-wrap items-center gap-2 text-[13px]">
                        <span>{{ bankName(revealed.bank) }}</span>
                        <code class="rounded bg-surface-100 px-1.5 py-0.5 text-[12px] dark:bg-surface-800">{{ revealed.number }}</code>
                        <span>{{ revealed.holder }}</span>
                        <span v-if="revealed.businessNumber" class="text-muted-color">{{ t('emoticon.accountBusinessNumber', { number: revealed.businessNumber }) }}</span>
                        <Button type="button" :label="t('emoticon.accountMask')" icon="pi pi-eye-slash" size="small" severity="secondary" variant="text" @click="emit('mask')" />
                    </div>
                    <span class="text-xs tabular-nums text-muted-color" aria-live="off">{{ t('emoticon.accountMasksIn', { time: time.duration(revealRemaining) }) }}</span>
                </div>
                <div v-else class="flex flex-wrap items-center gap-2 text-[13px]">
                    <span>{{ bankName(detail.account.bank) }}</span>
                    <code class="text-[12px] text-muted-color">{{ detail.account.maskedNumber }}</code>
                    <span class="text-muted-color">{{ detail.account.holderMasked }}</span>
                    <Button v-if="canReveal" type="button" :label="t('emoticon.accountReveal')" icon="pi pi-eye" size="small" severity="secondary" variant="outlined" @click="emit('reveal')" />
                    <span v-else class="inline-flex items-center gap-1 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('emoticon.accountNoPermission') }}</span>
                </div>
                <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-eye mt-0.5 text-xs" aria-hidden="true" />{{ t('emoticon.accountViewLog') }}</p>
            </section>

            <!-- 처리 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-events`">
                <h3 :id="`${titleId}-events`" class="m-0 text-sm font-semibold">{{ t('emoticon.secEvents') }}</h3>
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                    <li v-for="(ev, i) in detail.events" :key="`${ev.action}-${ev.at}-${i}`" class="flex flex-col gap-0.5 text-[13px]">
                        <span>
                            <b class="font-medium">{{ t(`emoticon.settlementAction.${ev.action}`) }}</b>
                            <span class="text-muted-color"> · {{ eventBy(ev) }} · {{ time.shortDateTime(ev.at) }}</span>
                        </span>
                        <span v-if="ev.action === 'WAIVED'" class="text-xs text-amber-700 dark:text-amber-400">{{ waivedEventLine(ev) }}</span>
                        <span v-if="ev.note" class="text-xs text-muted-color [overflow-wrap:anywhere]">{{ ev.note }}</span>
                    </li>
                </ul>
            </section>
        </div>

        <template v-if="detail && canManage && detail.status !== 'PAID' && detail.status !== 'WAIVED'" #footer>
            <div class="flex flex-wrap items-center justify-end gap-2">
                <template v-if="detail.status === 'HELD'">
                    <!-- 포기 처리(V148) — 정산 정보 없이 탈퇴한 작가의 보류만. 판정은 서버(`409 EMOTICON_SETTLEMENT_NOT_WAIVABLE`) -->
                    <Button v-if="UtilEmoticon.settlementWaivable(detail)" type="button" :label="t('emoticon.actWaive')" icon="pi pi-ban" severity="danger" variant="outlined" :disabled="busy" data-testid="emoticon-settlement-waive" @click="emit('waive')" />
                    <Button type="button" :label="t('emoticon.actUnhold')" icon="pi pi-replay" :loading="busy" :disabled="busy" @click="emit('unhold')" />
                </template>
                <template v-else>
                    <Button type="button" :label="t('emoticon.actHold')" icon="pi pi-pause" severity="danger" variant="outlined" :disabled="busy" @click="emit('hold')" />
                    <Button v-if="detail.status === 'ESTIMATED'" type="button" :label="t('emoticon.actConfirm')" icon="pi pi-check" :loading="busy" :disabled="busy || !closable" @click="emit('confirm')" />
                    <Button v-else-if="detail.payoutAmount > 0" type="button" :label="t('emoticon.actPaid')" icon="pi pi-send" :disabled="busy" @click="emit('paid')" />
                    <span v-else class="text-xs text-muted-color">{{ t('emoticon.carriedNothingToPay') }}</span>
                </template>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonPayoutAccountReveal, EmoticonSettlementDetail, EmoticonSettlementEvent, EmoticonSettlementRow } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    summary?: EmoticonSettlementRow | null;
    detail?: EmoticonSettlementDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    /** 확정·보류·지급 기록(`emoticon.settlement`) */
    canManage?: boolean;
    /** 계좌 전체 보기(`emoticon.payout.pii`) */
    canReveal?: boolean;
    /** 열람한 계좌 — 부모 메모리 */
    revealed?: EmoticonPayoutAccountReveal | null;
    /** 다시 가릴 때까지 남은 초 */
    revealRemaining?: number;
    /** 지금 시각(ms) — 확정 가능 판정 */
    now?: number;
}

const props = withDefaults(defineProps<Props>(), {
    summary: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    canManage: false,
    canReveal: false,
    revealed: null,
    revealRemaining: 0,
    now: 0,
});
const emit = defineEmits<{ retry: []; confirm: []; hold: []; unhold: []; paid: []; waive: []; reveal: []; mask: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const { localizedName } = useLocalizedName();

/** 은행 코드(가린 계좌·원문 보기) → 이름(모르는 코드는 그대로) */
function bankName(code: string): string {
    return UtilEmoticonMessages.bankName(code, localizedName);
}

/**
 * 처리 기록의 운영자 — null 은 시스템(`CREATED` · 작가 탈퇴의 시스템 보류 `HELD` · 작가 동의 포기 `WAIVED`), 그 밖의 null 은 지워진 운영자.
 * 운영자 포기 처리(`WAIVED` + 동의 시각 없음)의 null 은 지워진 운영자다
 */
function eventBy(ev: EmoticonSettlementEvent): string {
    if (ev.by) return ev.by;
    const system = ev.action === 'CREATED' || ev.action === 'HELD' || (ev.action === 'WAIVED' && ev.consentedAt !== null);
    return system ? t('emoticon.settlementBySystem') : t('emoticon.settlementByUnknown');
}

/** 포기 기록의 한 줄 — 「작가 동의로 포기 · N원 · 동의 시각」 / 「운영자 포기 처리 · N원」(사유는 `note` 줄) */
function waivedEventLine(ev: EmoticonSettlementEvent): string {
    const amount = won(ev.amount ?? 0);
    return ev.consentedAt ? t('emoticon.waivedByConsent', { amount, at: time.value.shortDateTime(ev.consentedAt) }) : t('emoticon.waivedByOperator', { amount });
}

/** 포기 줄 위 안내 — 가장 최근 포기 기록으로 작가 동의 · 운영자 포기 처리를 가른다(기록이 없으면 한 줄 안내만) */
const waivedNote = computed(() => {
    const ev = props.detail?.events.find((e) => e.action === 'WAIVED');
    if (!ev) return t('emoticon.waivedNote');
    if (ev.consentedAt) return t('emoticon.waivedConsentNote', { at: time.value.shortDateTime(ev.consentedAt) });
    return t('emoticon.waivedOperatorNote', { by: eventBy(ev), at: time.value.shortDateTime(ev.at) });
});

const head = computed(() => props.detail ?? props.summary);
const closable = computed(() => (props.detail ? (props.now || Date.now()) >= Date.parse(props.detail.closesAt) : false));

/** 부호 붙은 원(음수면 −) */
function won(value: number): string {
    return UtilEmoticonMessages.signedWon(t, value, locale.value);
}
/** 빼는 금액(양수 −, 음수 +, 0 은 0원) */
function minus(value: number): string {
    return UtilEmoticonMessages.deduction(t, value, locale.value, won(0));
}

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    const payee = UtilEmoticonMessages.payeeType(t, h.payeeType);
    const items: DrawerSummaryItem[] = [
        { key: 'creator', label: t('emoticon.col.creator'), title: h.creator.nickname },
        { key: 'payee', label: t('emoticon.col.payeeType'), value: h.businessNoMasked ? `${payee} · ${h.businessNoMasked}` : payee },
        { key: 'month', label: t('emoticon.col.month'), value: t('emoticon.monthLabel', UtilEmoticon.monthParts(h.month)) },
        { key: 'payoutDate', label: t('emoticon.col.payoutDate'), value: h.payoutDate, valueClass: UtilBizApplication.EMPH.date },
    ];
    // 포기 줄의 `confirmedAt` 은 포기한 시각
    if (h.confirmedAt) items.push({ key: 'confirmedAt', label: h.status === 'WAIVED' ? t('emoticon.waivedAt') : t('emoticon.confirmedAt'), value: time.value.shortDateTime(h.confirmedAt) });
    items.push({ key: 'id', label: t('emoticon.settlementId'), value: h.id, code: true });
    return items;
});

/** 원천징수 칸의 안내 — 개인 · 사업자 · 정산 정보 없음(개인 기준 어림값) */
function withholdingNote(d: EmoticonSettlementDetail): string {
    const rate = UtilEmoticon.WITHHOLDING_PERCENT;
    if (d.payeeType === 'BUSINESS') return t('emoticon.calc.withholdingBusiness');
    return d.payeeType === 'INDIVIDUAL' ? t('emoticon.calc.withholdingNote', { rate }) : t('emoticon.calc.withholdingEstimate', { rate });
}

/** 금액 계산 줄 — 서버가 준 값을 그대로 보이고(계산은 서버), 화면은 차례와 부호만 정한다. 확정 뒤 환불로 음수인 칸은 부호가 뒤집혀 보인다 */
const calcLines = computed(() => {
    const d = props.detail;
    if (!d) return [];
    const amber = 'text-amber-700 dark:text-amber-400';
    const out: { key: string; label: string; value: string; note?: string; tone?: string; strong?: boolean }[] = [
        { key: 'sales', label: t('emoticon.calc.sales'), value: won(d.salesAmount), note: t('emoticon.countCase', { count: d.salesCount }) },
        { key: 'refund', label: t('emoticon.calc.refund'), value: minus(d.refundAmount), note: d.refundCount ? t('emoticon.countCase', { count: d.refundCount }) : undefined, tone: d.refundAmount ? amber : 'text-muted-color' },
        { key: 'vat', label: t('emoticon.calc.vat'), value: minus(d.vatAmount), note: t('emoticon.calc.vatNote') },
        { key: 'base', label: t('emoticon.calc.base'), value: won(d.baseAmount), note: t('emoticon.calc.baseNote'), tone: d.baseAmount < 0 ? amber : undefined },
        { key: 'fee', label: t('emoticon.calc.fee'), value: minus(d.feeAmount), note: t('emoticon.calc.feeNote', { rate: d.feeRatePercent }) },
        { key: 'share', label: t('emoticon.calc.share'), value: won(d.shareAmount), note: t('emoticon.calc.shareNote'), tone: d.shareAmount < 0 ? amber : undefined },
    ];
    if (d.carriedIn) out.push({ key: 'carriedIn', label: t('emoticon.calc.carriedIn'), value: d.carriedIn > 0 ? `+${won(d.carriedIn)}` : won(d.carriedIn), note: t('emoticon.calc.carriedInNote'), tone: d.carriedIn < 0 ? amber : undefined });
    out.push({ key: 'withholding', label: t('emoticon.calc.withholding'), value: minus(d.withholdingAmount), note: withholdingNote(d), tone: d.withholdingAmount ? undefined : 'text-muted-color' });
    // 포기 줄(V148) — 지급액 0, 포기한 금액이 끝 줄. 이어지는 음수(받을 돈)가 있으면 그 줄도 보인다
    if (d.status === 'WAIVED') {
        out.push({ key: 'waived', label: t('emoticon.calc.waived'), value: won(d.waivedAmount), note: t('emoticon.calc.waivedNote'), tone: amber, strong: true });
        if (d.carriedOut < 0) out.push({ key: 'carriedOut', label: t('emoticon.calc.carriedOutDeduct'), value: won(-d.carriedOut), note: t('emoticon.calc.carriedOutDeductNote'), tone: amber });
        return out;
    }
    if (d.carriedOut > 0) out.push({ key: 'carriedOut', label: t('emoticon.calc.carriedOut'), value: won(d.carriedOut), note: t('emoticon.calc.carriedOutNote'), tone: amber, strong: true });
    else if (d.carriedOut < 0) out.push({ key: 'carriedOut', label: t('emoticon.calc.carriedOutDeduct'), value: won(-d.carriedOut), note: t('emoticon.calc.carriedOutDeductNote'), tone: amber, strong: true });
    else out.push({ key: 'payout', label: t('emoticon.calc.payout'), value: won(d.payoutAmount), strong: true });
    return out;
});
</script>
