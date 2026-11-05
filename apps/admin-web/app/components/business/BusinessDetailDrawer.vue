<template>
    <!--
        사업자 상세 서랍 — 목록에서 줄을 누르면 연다(`?id=`). 01-business.md 7-1 B.
        구획: ① 신원(가린 값 + 평문 보기) ② 국세청 상태조회 ③ 사업자등록증 사본(열람 기록) ④ 서비스 카드 ⑤ 처리 기록 Timeline. 맨 아래 처리 단추.
        ④ 서비스 카드: 「서비스만 정지」(`suspendCode != null`)면 사유 칸, 신원이 승인이면 카드마다 [서비스 정지]·[서비스 해제](2026-11-02).
        [승인] 은 계속사업자일 때만(조회 불가면 부모가 사유를 받는다), 휴·폐업이면 잠그고 이유를 적는다. 서버는 부르지 않는다 — 동작은 emit.
        ③ 아래 인쇄본·팩스 접수 기록(부모 `intake` 값, 동작은 `intake` 이벤트): 전자 팩스 파일 줄 · 종이 접수 줄 · [접수 기록](`business.manage`).
          「우편·팩스로 보냄」(상세 `certificateDelivery`) 신원인데 받은 것이 없으면 경고 + [승인] 잠금(서버도 409). 반려·탈퇴 90일이 지났는데 보관 중인 인쇄본은 「폐기 기한 지남」.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.name }}</h2>
                    <Tag :value="t(`business.status.${head.status}`)" :severity="UtilBusiness.statusSeverity(head.status)" />
                </div>
            </div>
        </template>

        <!-- 요약 — 등록번호(가린 값) · 신청자 · 신청 시각 -->
        <DrawerSummary v-if="head" :label="t('drawerSummary.label')" :items="summaryItems" />

        <ErrorState v-if="error" :title="t('business.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <Message v-if="detail.rejection" severity="warn" :closable="false">
                <span class="text-[13px]">{{ t('business.rejectedNote', { code: t(`business.rejectCode.${detail.rejection.code}`) }) }} — {{ detail.rejection.text }}</span>
            </Message>
            <Message v-if="detail.suspension" severity="error" :closable="false">
                <span class="text-[13px]">{{ t('business.suspendedNote', { code: t(`business.suspendCode.${detail.suspension.code}`), at: time.shortDateTime(detail.suspension.at) }) }} — {{ detail.suspension.text }}</span>
            </Message>

            <BusinessIdentitySection :detail="detail" :reveal="reveal" :can-reveal="canReveal" :busy="busy" @reveal="emit('reveal')" @hide="emit('hide')" />

            <BusinessNtsCard :nts="detail.nts" :can-recheck="mode === 'review' && canManage && detail.status === 'PENDING'" :busy="busy" @recheck="emit('recheck')" />

            <!-- ③ 사업자등록증 사본 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-cert`">
                <h3 :id="`${titleId}-cert`" class="m-0 text-sm font-semibold">{{ t('business.secCertificate') }}</h3>
                <div v-if="detail.certificate" class="flex items-center gap-3 rounded-lg border border-(--line) px-3 py-2.5">
                    <div class="flex h-16 w-12 shrink-0 items-center justify-center rounded border border-(--line) bg-(--p-content-hover-background) text-muted-color" aria-hidden="true"><i class="pi pi-file-pdf text-xl" /></div>
                    <div class="flex min-w-0 flex-1 flex-col gap-0.5 text-[13px]">
                        <span class="truncate font-medium">{{ detail.certificate.fileName }}</span>
                        <span class="text-[11px] text-muted-color">{{ UtilBizApplication.fileSize(detail.certificate.size) }} · {{ time.shortDateTime(detail.certificate.uploadedAt) }}</span>
                        <span class="text-[11px] text-muted-color">{{ detail.certificate.lastViewedAt ? t('business.certLastViewed', { who: detail.certificate.lastViewedBy, at: time.shortDateTime(detail.certificate.lastViewedAt) }) : t('business.certNeverViewed') }}</span>
                    </div>
                    <Button type="button" :label="t('business.certOpen')" icon="pi pi-external-link" size="small" severity="secondary" variant="outlined" :loading="busy" @click="emit('openCert')" />
                </div>
                <p v-else class="m-0 text-[13px] text-muted-color">{{ t('business.certNone') }}</p>
                <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-eye mt-0.5 text-xs" aria-hidden="true" />{{ t('business.certViewLog') }}</p>

                <!-- 인쇄본·팩스로 받은 등록증 -->
                <template v-if="intake?.bundle">
                    <Message v-if="certAwaiting" severity="warn" :closable="false" icon="pi pi-envelope" data-testid="doc-intake-cert-awaiting">
                        <span class="text-[13px]">{{ t('docIntake.cert.awaiting') }}</span>
                    </Message>
                    <p v-else-if="detail.certificateDelivery === 'MAIL_FAX'" class="m-0 flex items-center gap-1.5 text-xs text-green-700 dark:text-green-400"><i class="pi pi-check-circle text-xs" aria-hidden="true" />{{ t('docIntake.cert.received') }}</p>
                    <Message v-if="disposeOverdue" severity="error" :closable="false" icon="pi pi-exclamation-triangle"><span class="text-[13px]">{{ t('docIntake.cert.overdueNote') }}</span></Message>
                    <!-- 전자 팩스로 받은 예전 등록증 파일(이름·크기 — 지금 등록증은 위 사본 카드에서 연다) -->
                    <ul v-if="earlierFaxFiles.length" class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                        <li v-for="f in earlierFaxFiles" :key="f.id" class="flex items-center gap-3 px-3 py-2 text-[13px]">
                            <i :class="f.mimeType === 'application/pdf' ? 'pi pi-file-pdf' : 'pi pi-image'" class="text-muted-color" aria-hidden="true" />
                            <div class="flex min-w-0 flex-1 flex-col">
                                <span class="inline-flex min-w-0 items-center gap-2">
                                    <span class="truncate font-medium">{{ f.fileName }}</span>
                                    <Tag :value="t('docIntake.efax')" severity="info" icon="pi pi-print" />
                                </span>
                                <span class="text-[11px] text-muted-color">{{ UtilBizApplication.fileSize(f.size) }} · {{ time.shortDateTime(f.receivedAt) }} · {{ f.receivedBy ?? '—' }}</span>
                            </div>
                        </li>
                    </ul>
                    <DocIntakeSection
                        doc="CERTIFICATE"
                        :host="intake"
                        :files="certScanFiles"
                        :editable="canManage && detail.status !== 'WITHDRAWN'"
                        :handleable="canManage"
                        :dispose-due-at="disposeDueAt"
                        @intake="(e) => emit('intake', e)"
                    />
                </template>
            </section>

            <!-- ④ 서비스 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-svc`">
                <h3 :id="`${titleId}-svc`" class="m-0 text-sm font-semibold">{{ t('business.secServices') }}</h3>
                <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <div v-for="s in detail.serviceCards" :key="s.service" class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5" :data-testid="`business-service-${s.service}`">
                        <div class="flex items-center justify-between gap-2">
                            <span class="text-[13px] font-semibold">{{ t(`business.service.${s.service}`) }}</span>
                            <Tag :value="serviceOnlySuspended(s) ? t('business.serviceOnlySuspended') : t(`business.serviceStatus.${s.status}`)" :severity="UtilBusiness.serviceSeverity(s.status)" />
                        </div>
                        <span class="text-xs text-muted-color">{{ t('business.serviceCounts', { live: s.liveCount, pending: s.pendingCount }) }}</span>
                        <!-- 서비스만 정지(신원은 승인) — 사유 코드 · 시각 · 운영자 + 사유 글 -->
                        <div v-if="serviceOnlySuspended(s)" class="flex flex-col gap-0.5 rounded-md bg-red-50 px-2 py-1.5 text-xs dark:bg-red-950/30">
                            <span class="font-medium text-red-700 dark:text-red-400">{{ t('business.svcSuspendedMeta', { code: s.suspendCode ? t(`business.suspendCode.${s.suspendCode}`) : '—', at: s.suspendedAt ? time.shortDateTime(s.suspendedAt) : '—', who: s.suspendedByName ?? '—' }) }}</span>
                            <span v-if="s.suspendText" class="[overflow-wrap:anywhere]">{{ s.suspendText }}</span>
                        </div>
                        <!-- 서비스별 정지·해제 — 사업자 목록 화면, 신원이 승인일 때만(신원 정지가 우선이라 그때는 감춘다). 서버가 영향 건수(`impact`)를 주는 카드만 -->
                        <div v-if="serviceAction(s)" class="flex justify-end">
                            <Button
                                v-if="serviceAction(s) === 'suspend'"
                                type="button"
                                :label="t('business.actSvcSuspend')"
                                icon="pi pi-pause-circle"
                                size="small"
                                severity="danger"
                                variant="text"
                                :disabled="busy"
                                :data-testid="`business-service-suspend-${s.service}`"
                                @click="emit('serviceSuspend', s.service)"
                            />
                            <Button
                                v-else
                                type="button"
                                :label="t('business.actSvcResume')"
                                icon="pi pi-replay"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :disabled="busy"
                                :data-testid="`business-service-resume-${s.service}`"
                                @click="emit('serviceResume', s.service)"
                            />
                        </div>
                    </div>
                </div>
            </section>

            <!-- ⑤ 처리 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-hist`">
                <h3 :id="`${titleId}-hist`" class="m-0 text-sm font-semibold">{{ t('business.secHistory') }}</h3>
                <Timeline :value="history" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                    <template #content="{ item }">
                        <div class="flex flex-col pb-3">
                            <span class="font-medium">{{ t(`business.action.${item.action}`) }}<template v-if="item.service"> ({{ t(`business.serviceShort.${item.service}`) }})</template> <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                            <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                            <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ item.note }}</span>
                        </div>
                    </template>
                </Timeline>
            </section>
        </div>

        <!-- 심사 화면: 심사 중이면 승인·반려, 이미 심사가 끝난 건(옛 알림 링크)은 사업자 목록으로. 목록 화면: 승인이면 정지, 정지면 정지 해제, 심사 중이면 인증 심사로 -->
        <!-- 삭제(`business.delete`): 어느 화면·상태든 맨 왼쪽 작은 단추. 진행·예약·결제 대기 건이 보이면 잠그고 까닭을 말한다(지난 거래는 서버가 409 로 막는다) -->
        <template v-if="footer || canDelete" #footer>
            <div class="flex flex-col gap-2">
                <p v-if="canDelete && hasLiveDealings" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('business.deleteLocked') }}</p>
                <template v-if="footer === 'review'">
                    <p v-if="certAwaiting" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400"><i class="pi pi-lock" aria-hidden="true" />{{ t('docIntake.cert.approveLocked') }}</p>
                    <p v-if="ntsRule === 'LOCKED_MISMATCH'" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400"><i class="pi pi-lock" aria-hidden="true" />{{ t('business.approveLockedMismatch') }}</p>
                    <p v-else-if="ntsRule === 'LOCKED_STATUS'" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400"><i class="pi pi-lock" aria-hidden="true" />{{ t('business.approveLocked', { status: t(`business.nts.${detail!.nts.status}`) }) }}</p>
                    <p v-else-if="ntsRule === 'NEEDS_REASON_UNCHECKED'" class="m-0 flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400"><i class="pi pi-info-circle" aria-hidden="true" />{{ t('business.approveNeedsReasonUnchecked') }}</p>
                    <p v-else-if="ntsRule === 'NEEDS_REASON_UNAVAILABLE'" class="m-0 flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400"><i class="pi pi-info-circle" aria-hidden="true" />{{ t('business.approveNeedsReason') }}</p>
                </template>
                <p v-else-if="footer === 'toReview' || footer === 'toDirectory'" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle" aria-hidden="true" />{{ footer === 'toReview' ? t('business.pendingElsewhere') : t('business.doneElsewhere') }}</p>
                <div class="flex flex-wrap justify-end gap-2">
                    <Button
                        v-if="canDelete && detail"
                        type="button"
                        class="mr-auto"
                        :label="t('business.actDelete')"
                        icon="pi pi-trash"
                        size="small"
                        severity="danger"
                        variant="text"
                        :disabled="busy || hasLiveDealings"
                        data-testid="business-delete"
                        @click="emit('remove')"
                    />
                    <template v-if="footer === 'review'">
                        <Button type="button" :label="t('business.actReject')" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                        <Button type="button" :label="t('business.actApprove')" icon="pi pi-check" :disabled="busy || approvability === 'LOCKED' || certAwaiting" @click="emit('approve')" />
                    </template>
                    <Button v-else-if="footer === 'suspend'" type="button" :label="t('business.actSuspend')" icon="pi pi-ban" severity="danger" variant="outlined" :disabled="busy" @click="emit('suspend')" />
                    <Button v-else-if="footer === 'unsuspend'" type="button" :label="t('business.actUnsuspend')" icon="pi pi-replay" :disabled="busy" @click="emit('unsuspend')" />
                    <Button v-else-if="footer === 'toReview'" as="router-link" :to="`/business-reviews?id=${encodeURIComponent(detail!.id)}`" :label="t('business.goReview')" icon="pi pi-arrow-right" icon-pos="right" size="small" severity="secondary" variant="outlined" />
                    <Button v-else-if="footer === 'toDirectory'" as="router-link" :to="`/businesses?status=ALL&id=${encodeURIComponent(detail!.id)}`" :label="t('business.goDirectory')" icon="pi pi-arrow-right" icon-pos="right" size="small" severity="secondary" variant="outlined" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { BusinessDetail, BusinessReveal, BusinessRow, BusinessService, BusinessServiceCard } from '~/composables/business/api/useBusinessApi.types';
import type { BusinessScreenMode } from '~/components/business/BusinessDetailDrawer.types';
import type { DocIntakeEvent, DocIntakeHost, DocScanFile } from '~/composables/docintake/useDocIntake.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBusiness from '~/utils/util-business';
import UtilDocIntake from '~/utils/util-doc-intake';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 어느 화면의 서랍인가 — 인증 심사(`review`: 국세청 다시 조회·승인·반려) · 사업자 목록(`directory`: 정지·정지 해제) */
    mode?: BusinessScreenMode;
    summary?: BusinessRow | null;
    detail?: BusinessDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    busy?: boolean;
    canManage?: boolean;
    canReveal?: boolean;
    canUnsuspend?: boolean;
    /** 사업자 삭제(`business.delete`) */
    canDelete?: boolean;
    reveal?: BusinessReveal | null;
    /** 등록증 인쇄본·팩스 접수 기록 — 부모의 `useDocIntake().host` */
    intake?: DocIntakeHost | null;
}

const props = withDefaults(defineProps<Props>(), {
    mode: 'review',
    summary: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    canManage: false,
    canReveal: false,
    canUnsuspend: false,
    canDelete: false,
    reveal: null,
    intake: null,
});
const emit = defineEmits<{
    retry: [];
    approve: [];
    reject: [];
    suspend: [];
    unsuspend: [];
    /** 서비스만 정지(권한 = 신원 정지 `business.manage`) */
    serviceSuspend: [service: BusinessService];
    /** 서비스만 정지 해제(권한 = 신원 정지 해제 `business.unsuspend`) */
    serviceResume: [service: BusinessService];
    recheck: [];
    reveal: [];
    hide: [];
    openCert: [];
    remove: [];
    /** 등록증 인쇄본·팩스 접수 기록 */
    intake: [event: DocIntakeEvent];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const head = computed(() => props.detail ?? props.summary);
/** 요약 칸 — 등록번호(가린 값) · 신청자 · 신청 시각(일자 강조) */
const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    return [
        { key: 'regNo', label: t('business.regNo'), value: h.maskedRegNo, code: true },
        { key: 'applicant', label: t('business.col.applicant'), value: h.applicant.nickname },
        { key: 'appliedAt', label: t('business.col.appliedAt'), value: time.value.shortDateTime(h.appliedAt), valueClass: UtilBizApplication.EMPH.date },
    ];
});
const ntsRule = computed(() => (props.detail ? UtilBusiness.ntsRule(props.detail.nts) : 'LOCKED_STATUS'));
const approvability = computed(() => (props.detail ? UtilBusiness.approvability(props.detail.nts) : 'LOCKED'));
/** 진행·예약·결제 대기 건이 있나 — 있으면 삭제 단추를 잠근다(정지·탈퇴로 정리) */
const hasLiveDealings = computed(() => {
    const d = props.detail;
    if (!d) return false;
    return d.impact.awaitingPayment + d.impact.scheduled + d.impact.running > 0 || d.serviceCards.some((s) => s.liveCount + s.pendingCount > 0);
});
/**
 * 「서비스만 정지」 — 서버가 사유 코드를 준 줄(`suspendCode != null`). 신원 정지를 따라간 줄은 `SUSPENDED` 라도 코드가 null 이라 가른다.
 * 신원 상태로 가르지 않는다 — 신원이 정지 중이어도, 신원 정지를 푼 뒤에도 서비스만 정지된 줄은 남는다.
 */
function serviceOnlySuspended(card: BusinessServiceCard): boolean {
    return card.status === 'SUSPENDED' && card.suspendCode != null;
}

/** 서비스 카드 단추 — 사업자 목록 화면 · 신원 승인(신원 정지가 우선이라 그때는 감춘다)만. 이용 중 → 정지(`business.manage`), 서비스만 정지 → 해제(`business.unsuspend`) */
function serviceAction(card: BusinessServiceCard): 'suspend' | 'resume' | null {
    if (props.mode !== 'directory' || props.detail?.status !== 'APPROVED') return null;
    if (card.status === 'APPROVED') return props.canManage ? 'suspend' : null;
    if (serviceOnlySuspended(card)) return props.canUnsuspend ? 'resume' : null;
    return null;
}

/** 「우편·팩스로 보냄」인데 아직 받은 것이 없다 — [승인] 을 잠근다(부모 `approve` 도 막는다, 서버 409 `BUSINESS_CERT_NOT_RECEIVED`) */
const certAwaiting = computed(() => {
    const d = props.detail;
    return d ? UtilDocIntake.certificateAwaiting(d.certificateDelivery, d.certificateReceived) : false;
});
/** 전자 팩스 파일 중 지금 등록증이 아닌 것 — 지금 등록증은 위 사본 카드에 이미 있다 */
const earlierFaxFiles = computed(() => (props.intake?.bundle?.faxFiles ?? []).filter((f) => f.id !== props.detail?.certificate?.id));
/** 등록증 인쇄본 폐기 기한(반려·탈퇴 처리 기록 + 90일) */
const disposeDueAt = computed(() => (props.detail ? UtilDocIntake.certificateDisposeDue(props.detail.status, props.detail.history) : null));
const disposeOverdue = computed(() => (props.intake?.bundle?.intakes ?? []).some((i) => UtilDocIntake.disposeOverdue(i, disposeDueAt.value)));
/** 폐기 스캔본 후보 — 그 사업자의 등록증 파일(회원이 올린 것 + 전자 팩스, 등록증은 선택) */
const certScanFiles = computed<DocScanFile[]>(() => UtilDocIntake.certificateScanFiles(props.intake?.bundle, props.detail?.certificate ?? null, t('docIntake.cert.earlierFile')));

/** 최신이 위 */
const history = computed(() => [...(props.detail?.history ?? [])].sort((a, b) => b.at.localeCompare(a.at)));

/** 서랍 아래 칸 — 화면과 상태·권한으로 고른다(없으면 칸을 그리지 않는다) */
const footer = computed<'review' | 'suspend' | 'unsuspend' | 'toReview' | 'toDirectory' | null>(() => {
    const d = props.detail;
    if (!d) return null;
    if (props.mode === 'review') {
        if (d.status !== 'PENDING') return 'toDirectory';
        return props.canManage ? 'review' : null;
    }
    if (d.status === 'PENDING') return 'toReview';
    if (d.status === 'APPROVED') return props.canManage ? 'suspend' : null;
    if (d.status === 'SUSPENDED') return props.canUnsuspend ? 'unsuspend' : null;
    return null;
});
</script>
