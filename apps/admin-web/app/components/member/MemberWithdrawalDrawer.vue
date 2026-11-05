<template>
    <!--
        탈퇴 기록 상세 서랍 — 탈퇴 관리(`/members/withdrawals`)에서 줄을 누르면 연다(`?id=`). 열리는 쪽은 설정의 "서랍 위치"(`prefs.drawerPosition`), 폭 44rem.
        머리: 닉네임(완료 건은 「탈퇴한 회원」) · 상태 · 신청 경로. 요약 영역(`DrawerSummary`): 회원 번호 · 신청 시각 · 예상 완료/완료 시각 · 재가입 가능일 + 「회원 관리에서 보기」.
        본문 차례: 10일 넘김 경고 → 남은 일(건마다 관리자 화면 링크 — `MemberWithdrawalPendingList`) → 회원이 고른 것 → 포기 동의 금액·시각 → 사유·메모(운영자 강제면 사유 종류·운영 메모·처리한 운영자) → 처리 이력(신청·안내 메일·완료·취소)
        → 완료 건이고 결제 이력이 있으면 기존 「연락용 이메일 열람」 블록(`MemberWithdrawnContactBlock`, `member.withdrawn.contact`) 재사용.
        진행 중 + 회원 신청 건은 주의 동작 줄 [대리 취소](`member.withdraw`, 사용자 결정 2026-11-04 — 회원이 고객센터·전화로 요청한 경우만). 운영자 강제 탈퇴 건은 단추 대신 「대리 취소 불가」 한 줄.
        서버 호출은 하지 않는다 — 부모가 받아 내려 주고 열람·다시 받기·대리 취소는 이벤트로 올린다.
    -->
    <Drawer v-model:visible="open" class="member-withdrawal-drawer" :modal="false" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head ? head.nickname ?? t('memberWithdrawal.withdrawnMember') : t('memberWithdrawal.drawerTitle') }}</h2>
                    <template v-if="head">
                        <Tag :value="t(`memberWithdrawal.status.${head.status}`)" :icon="UtilMemberWithdrawal.statusIcon(head.status)" :severity="UtilMemberWithdrawal.statusSeverity(head.status)" />
                        <Tag :value="t(`memberWithdrawal.source.${head.source}`)" :icon="head.source === 'OPERATOR' ? 'pi pi-user-minus' : 'pi pi-user'" :severity="head.source === 'OPERATOR' ? 'danger' : 'secondary'" />
                    </template>
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('memberWithdrawal.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <div v-else class="flex flex-col gap-4">
            <DrawerSummary :label="t('drawerSummary.label')" :items="summaryItems" class="mb-0!">
                <template #links>
                    <NuxtLink v-if="detail.status !== 'COMPLETED'" :to="{ path: '/members', query: { memberNo: detail.memberNo } }" class="inline-flex items-center gap-1 font-medium text-(--p-primary-color) hover:underline">
                        <i class="pi pi-external-link text-[11px]" aria-hidden="true" />{{ t('memberWithdrawal.openMember') }}
                    </NuxtLink>
                </template>
            </DrawerSummary>

            <Message v-if="overdue" severity="warn" :closable="false" size="small">
                {{ t('memberWithdrawal.summary.overdueNote', { days: UtilMemberWithdrawal.OVERDUE_DAYS }) }} · {{ t('memberWithdrawal.overdueDays', { days: UtilMemberWithdrawal.daysSince(detail.requestedAt) }) }}
            </Message>
<!-- 진행 중 건의 주의 동작 줄 — 회원 신청 건만 [대리 취소](권한 없으면 안내만), 운영자 강제 건은 불가 이유 한 줄 -->
            <div v-if="detail.status === 'SCHEDULED'" class="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-dashed px-3 py-2.5" :class="canOperatorCancel ? 'border-amber-300 dark:border-amber-800' : 'border-(--line)'">
                <span class="inline-flex min-w-0 items-start gap-1.5 text-[13px] text-muted-color">
                    <i :class="canOperatorCancel ? 'pi pi-headphones mt-0.5 text-amber-700 dark:text-amber-400' : 'pi pi-lock mt-0.5'" aria-hidden="true" />
                    <span>{{ canOperatorCancel ? t('memberWithdrawal.cancelPolicyNote') : t('memberWithdrawal.cancelNotMemberRequested') }}</span>
                </span>
                <Button v-if="canOperatorCancel && canCancel" type="button" :label="t('memberWithdrawal.cancel.action')" icon="pi pi-undo" size="small" severity="warn" variant="outlined" :loading="cancelBusy" data-testid="withdrawal-operator-cancel" @click="emit('cancel')" />
            </div>

            <!-- 남은 일 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-pending`">
                <h3 :id="`${titleId}-pending`" class="m-0 flex items-center gap-1.5 text-sm font-semibold">
                    {{ detail.status === 'SCHEDULED' ? t('memberWithdrawal.secPending') : t('memberWithdrawal.secPendingSnapshot') }}
                    <span class="text-[11px] font-normal tabular-nums text-muted-color">{{ detail.pending.length }}</span>
                </h3>
                <MemberWithdrawalPendingList :items="detail.pending" :empty-text="detail.status === 'SCHEDULED' ? t('memberWithdrawal.pendingAllDone') : ''" />
            </section>

            <!-- 회원이 고른 것 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-choices`">
                <h3 :id="`${titleId}-choices`" class="m-0 text-sm font-semibold">{{ t('memberWithdrawal.secChoices') }}</h3>
                <div class="rounded-lg border border-(--line) px-3 py-2.5">
                    <MemberWithdrawalChoices :choices="detail.choices" />
                </div>
            </section>

            <!-- 포기 동의 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-forfeit`">
                <h3 :id="`${titleId}-forfeit`" class="m-0 text-sm font-semibold">{{ t('memberWithdrawal.secForfeit') }}</h3>
                <p v-if="!hasForfeit" class="m-0 text-[13px] text-muted-color">{{ t('memberWithdrawal.forfeit.none') }}</p>
                <dl v-else class="m-0 grid grid-cols-[8.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                    <template v-if="detail.forfeit.paidPoints">
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forfeit.paidPoints') }}</dt>
                        <dd class="m-0 tabular-nums" :class="EMPH.refund">{{ t('memberWithdrawal.forfeit.won', { amount: detail.forfeit.paidPoints.toLocaleString(locale) }) }}</dd>
                    </template>
                    <template v-if="detail.forfeit.refundEstimate">
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forfeit.refundEstimate') }}</dt>
                        <dd class="m-0 tabular-nums" :class="EMPH.refund">{{ t('memberWithdrawal.forfeit.won', { amount: detail.forfeit.refundEstimate.toLocaleString(locale) }) }}</dd>
                    </template>
                    <template v-if="detail.forfeit.bizAmount">
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forfeit.bizAmount') }}</dt>
                        <dd class="m-0 tabular-nums" :class="EMPH.refund">{{ t('memberWithdrawal.forfeit.won', { amount: detail.forfeit.bizAmount.toLocaleString(locale) }) }}</dd>
                    </template>
                    <!-- 로보티콘 작가 — 정산 정보 없이 남은 수익을 받지 않기로 동의(V148, 원천징수 전 금액) -->
                    <template v-if="detail.forfeit.emoticonEarnings">
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forfeit.emoticonEarnings') }}</dt>
                        <dd class="m-0 tabular-nums" :class="EMPH.refund" data-testid="withdrawal-forfeit-emoticon">
                            {{ t('memberWithdrawal.forfeit.won', { amount: detail.forfeit.emoticonEarnings.toLocaleString(locale) }) }}
                            <span class="ml-1 text-[11px] font-normal text-muted-color">{{ t('memberWithdrawal.forfeit.emoticonEarningsNote') }}</span>
                        </dd>
                    </template>
                    <dt class="text-muted-color">{{ t('memberWithdrawal.forfeit.consentedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ detail.forfeit.consentedAt ? time.full(detail.forfeit.consentedAt) : '—' }}</dd>
                </dl>
            </section>

            <!-- 사유·메모 / 운영자 강제 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reason`">
                <h3 :id="`${titleId}-reason`" class="m-0 text-sm font-semibold">{{ detail.force ? t('memberWithdrawal.secForce') : t('memberWithdrawal.secReason') }}</h3>
                <dl class="m-0 grid grid-cols-[8.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                    <template v-if="detail.force">
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forceReasonKind') }}</dt>
                        <dd class="m-0 inline-flex items-center gap-1.5"><i :class="UtilMemberWithdrawal.forceReasonIcon(detail.force.reasonKind)" aria-hidden="true" />{{ t(`memberWithdrawal.force.reasonKind.${detail.force.reasonKind}`) }}</dd>
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forceMemo') }}</dt>
                        <dd class="m-0 break-words">{{ detail.force.memo }}</dd>
                        <dt class="text-muted-color">{{ t('memberWithdrawal.forceBy') }}</dt>
                        <dd class="m-0">{{ detail.force.operatorName ?? '—' }}</dd>
                    </template>
                    <template v-else>
                        <dt class="text-muted-color">{{ t('memberWithdrawal.block.reason') }}</dt>
                        <dd class="m-0" :class="detail.reason ? '' : 'text-muted-color'">{{ t(`memberWithdrawal.reason.${UtilMemberWithdrawal.reasonKey(detail.reason)}`) }}</dd>
                        <dt class="text-muted-color">{{ t('memberWithdrawal.reasonNote') }}</dt>
                        <dd class="m-0 break-words" :class="detail.reasonNote ? '' : 'text-muted-color'">{{ detail.reasonNote ?? '—' }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('memberWithdrawal.priorStatus') }}</dt>
                    <dd class="m-0"><Tag :value="t(`member.status.${detail.priorStatus}`)" :icon="UtilMember.statusIcon(detail.priorStatus)" :severity="UtilMember.statusSeverity(detail.priorStatus)" /></dd>
                </dl>
            </section>

            <!-- 운영자 대리 취소 — 취소 건 중 운영자가 회원 요청으로 취소한 것만 -->
            <section v-if="detail.cancel" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-cancel`">
                <h3 :id="`${titleId}-cancel`" class="m-0 text-sm font-semibold">{{ t('memberWithdrawal.secCancel') }}</h3>
                <dl class="m-0 grid grid-cols-[8.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('memberWithdrawal.cancelChannel') }}</dt>
                    <dd class="m-0 inline-flex items-center gap-1.5"><i :class="UtilMemberWithdrawal.cancelChannelIcon(detail.cancel.requestChannel)" aria-hidden="true" />{{ t(`memberWithdrawal.cancel.channel.${detail.cancel.requestChannel}`) }}</dd>
                    <dt class="text-muted-color">{{ t('memberWithdrawal.cancelMemo') }}</dt>
                    <dd class="m-0 break-words">{{ detail.cancel.memo }}</dd>
                    <dt class="text-muted-color">{{ t('memberWithdrawal.cancelBy') }}</dt>
                    <dd class="m-0">{{ detail.cancel.operatorName ?? '—' }}</dd>
                </dl>
            </section>

            <!-- 처리 이력 — 광고·핫딜 신청 건 「처리 기록」과 같은 세로 타임라인(최신이 위), 단계 이름은 태그 색 그대로 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-events`">
                <h3 :id="`${titleId}-events`" class="m-0 text-sm font-semibold">{{ t('memberWithdrawal.secEvents') }}</h3>
                <Timeline :value="detail.events" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                    <template #content="{ item }">
                        <div class="flex flex-col items-start gap-0.5 pb-3">
                            <span class="flex flex-wrap items-center gap-x-1.5">
                                <MemberDetailTag :value="t(`memberWithdrawal.event.${item.kind}`)" :icon="UtilMemberWithdrawal.eventIcon(item.kind)" :severity="UtilMemberWithdrawal.eventSeverity(item.kind)" />
                                <span v-if="item.operatorName" class="text-muted-color">· {{ item.operatorName }}</span>
                            </span>
                            <span class="text-[11px] text-muted-color tabular-nums">{{ time.shortDateTime(item.at) }}</span>
                            <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ item.note }}</span>
                        </div>
                    </template>
                </Timeline>
            </section>

            <!-- 완료 건 + 결제 이력 → 분리 보관한 연락용 이메일(기존 블록 재사용) -->
            <MemberWithdrawnContactBlock v-if="detail.status === 'COMPLETED' && detail.hasPaymentHistory" :view="withdrawnContact" @reveal="emit('reveal-contact')" @mask="emit('mask-contact')" @retry="emit('retry-contact')" />
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { WithdrawnContactView } from '~/components/member/MemberDetailContent.types';
import type { MemberWithdrawalDetail, MemberWithdrawalRow } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilMember from '~/utils/util-member';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 누른 목록 줄 — 머리를 바로 그린다. 주소로 열면 null */
    summary: MemberWithdrawalRow | null;
    detail: MemberWithdrawalDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 완료 건의 연락용 이메일 블록 — 부모가 `useWithdrawnContact` 로 받는다 */
    withdrawnContact?: WithdrawnContactView | null;
    /** [대리 취소]를 보일 권한(`member.withdraw`) — 없으면 안내 글만 */
    canCancel?: boolean;
    cancelBusy?: boolean;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, withdrawnContact: null, canCancel: false, cancelBusy: false });
const emit = defineEmits<{ retry: []; 'reveal-contact': []; 'mask-contact': []; 'retry-contact': []; cancel: [] }>();

const { t, locale } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const titleId = useId();
const EMPH = UtilBizApplication.EMPH;

const head = computed(() => props.detail ?? props.summary);
const overdue = computed(() => (props.detail ? UtilMemberWithdrawal.isOverdue(props.detail.status, props.detail.requestedAt) : false));
/** 운영자가 대리 취소할 수 있는 건인가(진행 중 + 회원 신청) — 권한과 따로 */
const canOperatorCancel = computed(() => (props.detail ? UtilMemberWithdrawal.canOperatorCancel(props.detail.status, props.detail.source) : false));
const hasForfeit = computed(() => {
    const f = props.detail?.forfeit;
    return !!f && (f.paidPoints > 0 || f.refundEstimate > 0 || f.bizAmount > 0 || f.emoticonEarnings > 0);
});

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const d = props.detail;
    if (!d) return [];
    const items: DrawerSummaryItem[] = [
        { key: 'memberNo', label: t('memberWithdrawal.col.memberNo'), value: d.memberNo, code: true },
        { key: 'requestedAt', label: t('memberWithdrawal.col.requestedAt'), value: time.value.full(d.requestedAt) },
    ];
    if (d.status === 'SCHEDULED') {
        items.push({ key: 'expected', label: t('memberWithdrawal.col.expectedCompleteAt'), value: d.expectedCompleteAt ? time.value.full(d.expectedCompleteAt) : t('memberWithdrawal.block.expectedUnknown'), valueClass: d.expectedCompleteAt ? EMPH.date : 'text-muted-color' });
        items.push({ key: 'rejoin', label: t('memberWithdrawal.rejoinUntilExpected'), value: t('memberWithdrawal.rejoinNote'), valueClass: 'text-muted-color' });
    } else if (d.status === 'COMPLETED') {
        items.push({ key: 'completedAt', label: t('memberWithdrawal.col.completedAt'), value: time.value.full(d.completedAt), valueClass: EMPH.date });
        items.push({ key: 'rejoin', label: t('memberWithdrawal.rejoinUntil'), value: d.rejoinUntil ? time.value.date(d.rejoinUntil) : '—' });
    } else {
        items.push({ key: 'canceledAt', label: t(d.cancel ? 'memberWithdrawal.event.OPERATOR_CANCELED' : 'memberWithdrawal.event.CANCELED'), value: time.value.full(d.canceledAt) });
    }
    items.push({ key: 'mail', label: t('memberWithdrawal.col.progressMail'), value: d.progressNotifiedAt ? time.value.full(d.progressNotifiedAt) : t('memberWithdrawal.progressMailNotSent'), valueClass: d.progressNotifiedAt ? '' : 'text-muted-color' });
    items.push({ key: 'payment', label: t('memberWithdrawal.col.paymentHistory'), value: d.hasPaymentHistory ? t('memberWithdrawal.paymentHistoryYes') : t('memberWithdrawal.paymentHistoryNo'), valueClass: d.hasPaymentHistory ? '' : 'text-muted-color' });
    return items;
});
</script>
