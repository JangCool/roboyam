<template>
    <!--
        사업자 상세 서랍 — 목록에서 줄을 누르면 연다(`?id=`). 01-business.md 7-1 B.
        구획: ① 신원(가린 값 + 평문 보기) ② 국세청 상태조회 ③ 사업자등록증 사본(열람 기록) ④ 서비스 카드 ⑤ 처리 기록 Timeline. 맨 아래 처리 단추.
        [승인] 은 계속사업자일 때만(조회 불가면 부모가 사유를 받는다), 휴·폐업이면 잠그고 이유를 적는다. 서버는 부르지 않는다 — 동작은 emit.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '44rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold">{{ head.name }}</h2>
                    <Tag :value="t(`business.status.${head.status}`)" :severity="UtilBusiness.statusSeverity(head.status)" />
                </div>
                <span class="truncate text-[12px] text-muted-color"><code>{{ head.maskedRegNo }}</code> · {{ head.applicant.nickname }} · {{ t('business.appliedAt', { at: time.shortDateTime(head.appliedAt) }) }}</span>
            </div>
        </template>

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
            </section>

            <!-- ④ 서비스 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-svc`">
                <h3 :id="`${titleId}-svc`" class="m-0 text-sm font-semibold">{{ t('business.secServices') }}</h3>
                <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <div v-for="s in detail.serviceCards" :key="s.service" class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5">
                        <div class="flex items-center justify-between gap-2">
                            <span class="text-[13px] font-semibold">{{ t(`business.service.${s.service}`) }}</span>
                            <Tag :value="t(`business.serviceStatus.${s.status}`)" :severity="UtilBusiness.serviceSeverity(s.status)" />
                        </div>
                        <span class="text-xs text-muted-color">{{ t('business.serviceCounts', { live: s.liveCount, pending: s.pendingCount }) }}</span>
                    </div>
                </div>
            </section>

            <!-- ⑤ 처리 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-hist`">
                <h3 :id="`${titleId}-hist`" class="m-0 text-sm font-semibold">{{ t('business.secHistory') }}</h3>
                <Timeline :value="history" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                    <template #content="{ item }">
                        <div class="flex flex-col pb-3">
                            <span class="font-medium">{{ t(`business.action.${item.action}`) }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                            <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                            <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ item.note }}</span>
                        </div>
                    </template>
                </Timeline>
            </section>
        </div>

        <!-- 심사 화면: 심사 중이면 승인·반려, 이미 심사가 끝난 건(옛 알림 링크)은 사업자 목록으로. 목록 화면: 승인이면 정지, 정지면 정지 해제, 심사 중이면 인증 심사로 -->
        <template v-if="footer" #footer>
            <div class="flex flex-col gap-2">
                <template v-if="footer === 'review'">
                    <p v-if="approvability === 'LOCKED'" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400"><i class="pi pi-lock" aria-hidden="true" />{{ t('business.approveLocked', { status: t(`business.nts.${detail!.nts.status}`) }) }}</p>
                    <p v-else-if="approvability === 'NEEDS_REASON'" class="m-0 flex items-center gap-1.5 text-xs text-amber-700 dark:text-amber-400"><i class="pi pi-info-circle" aria-hidden="true" />{{ t('business.approveNeedsReason') }}</p>
                </template>
                <p v-else-if="footer === 'toReview' || footer === 'toDirectory'" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle" aria-hidden="true" />{{ footer === 'toReview' ? t('business.pendingElsewhere') : t('business.doneElsewhere') }}</p>
                <div class="flex flex-wrap justify-end gap-2">
                    <template v-if="footer === 'review'">
                        <Button type="button" :label="t('business.actReject')" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                        <Button type="button" :label="t('business.actApprove')" icon="pi pi-check" :disabled="busy || approvability === 'LOCKED'" @click="emit('approve')" />
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
import type { ApiError } from '~/composables/common/useApiClient';
import type { BusinessDetail, BusinessReveal, BusinessRow } from '~/composables/business/api/useBusinessApi.types';
import type { BusinessScreenMode } from '~/components/business/BusinessDetailDrawer.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBusiness from '~/utils/util-business';

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
    reveal?: BusinessReveal | null;
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
    reveal: null,
});
const emit = defineEmits<{ retry: []; approve: []; reject: []; suspend: []; unsuspend: []; recheck: []; reveal: []; hide: []; openCert: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const head = computed(() => props.detail ?? props.summary);
const approvability = computed(() => (props.detail ? UtilBusiness.approvability(props.detail.nts.status) : 'LOCKED'));
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
