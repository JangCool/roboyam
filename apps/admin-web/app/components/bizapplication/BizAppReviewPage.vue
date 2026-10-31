<template>
    <!--
        사업자 신청 건 심사 화면 한 벌 — 광고 › 광고 심사(`/ad-reviews`, domain AD) · 핫딜 › 핫딜 심사(`/hotdeal-reviews`, domain HOTDEAL)가 `domain` 만 바꿔 쓴다(04-ia-review 안 C).
        서버는 엔진 하나(`/api/admin/biz-applications`)라 화면도 하나이고, 목록·건수·종류 거르기가 그 도메인 안으로 좁혀진다(00-overview.md ★-1~★-5, 02-ad.md 7-4).
        · 탭(큐, 건수): 심사 대기 · 결제 대기 · 입금 확인 대기 · 입금 기한 지남 · 예약·진행 · 환불 심사 · 종료/반려/취소. 종류(그 도메인 것만) · 찾기(번호·사업자).
        · Splitter 3단: 왼쪽 목록 · 가운데 신청 내용 + 점검표 + 상태별 처리 · 오른쪽 미리보기. 고른 건은 주소 `?id=` 에 남는다(`?queue=` 로 탭도). 좁은 화면은 위아래로 쌓는다.
        · 처리: 승인(결제 방식·금액·기한·기간 옮기기·남은 자리) · 반려(코드+글, 결제 전이라 환불 없음) · 수동 결제로 전환 · 계약서 사본(한 장씩 올림 · [보기] 열람 기록) + 입금 확인 → 예약 확정
          · 입금 기한 연장/취소 · 환불 승인/거절 · 수동 환불 송금 완료 표시.
        권한: 화면 조회는 메뉴 항목의 권한(`ad.read`·`hotdeal.read` — 셸이 거른다) · 심사·결제 처리 `ad.manage`·`hotdeal.manage`(그 도메인 것) · 환불 승인·거절·송금 `deal-order.refund`(서버 `Permission`).
        다른 도메인 건을 `?id=` 로 열면 서버가 403 을 준다(권한 없음 안내). 관리자 알림(`AD_REVIEW_PENDING`·`HOTDEAL_REVIEW_PENDING`·`*_REFUND_REQUESTED`)의 링크가 `?id=` 로 들어온다.
        서버 호출은 `useBizApplicationApi`(V112) — 화면 한 벌을 두 주소가 같이 쓰려고 컴포넌트가 직접 부른다(페이지 파일은 `domain` 만 넘긴다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t(`bizApplication.domainTitle.${domain}`)" :subtitle="t(`bizApplication.domainSubtitle.${domain}`)" />

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('bizApplication.search')" @submit.prevent="search">
            <div class="flex items-center gap-2">
                <span :id="kindLabelId" class="text-[13px] text-muted-color">{{ t('bizApplication.kindLabel') }}</span>
                <FilterOptionGroup v-model="kind" :options="kindOptions" :labelledby="kindLabelId" />
            </div>
            <label class="sr-only" :for="qId">{{ t('bizApplication.search') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('bizApplication.searchPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="queue" scrollable class="min-w-0" @update:value="(v: string | number) => selectQueue(String(v) as BizApplicationQueue)">
            <TabList :aria-label="t('bizApplication.queueTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">
                        {{ tab.label }}
                        <Badge v-if="tab.urgent && tab.count > 0" :value="tab.count" :severity="tab.value === 'DEPOSIT_OVERDUE' || tab.value === 'REFUND' ? 'danger' : 'info'" size="small" />
                        <span v-else class="text-[11px] font-normal tabular-nums text-muted-color">{{ counts ? tab.count : '—' }}</span>
                    </span>
                </Tab>
            </TabList>
        </Tabs>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <Splitter :key="narrow ? 'v' : 'h'" :layout="narrow ? 'vertical' : 'horizontal'" class="border-0" :style="{ height: narrow ? '200vh' : 'calc(100vh - 16rem)', minHeight: '32rem' }">
                <SplitterPanel :size="24" :min-size="16" class="min-h-0 overflow-hidden">
                    <BizAppQueueList :items="rows" :queue="queue" :selected-id="selectedId" :loading="busy" :error="loadError" :now="now" @select="select" @retry="reload" />
                </SplitterPanel>
                <SplitterPanel :size="46" :min-size="30" class="min-h-0 overflow-y-auto">
                    <ErrorState
                        v-if="detailError"
                        :title="detailError.status === 403 ? t('bizApplication.otherDomain') : t('bizApplication.detailLoadFailed')"
                        :description="detailError.message"
                        :code="detailError.status === 404 || detailError.status === 403 ? String(detailError.status) : '!'"
                        :retry="detailError.status !== 404 && detailError.status !== 403"
                        :retrying="detailLoading"
                        @retry="loadDetail"
                    />
                    <div v-else-if="detailLoading && !detail" class="flex flex-col gap-3 p-4" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 7" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
                    </div>
                    <EmptyState v-else-if="!detail" icon="pi pi-arrow-left" :title="t('bizApplication.pickOne')" />
                    <BizAppReviewPanel
                        v-else
                        :key="detail.id"
                        :detail="detail"
                        :can-manage="can(screen.manage)"
                        :can-refund="can(UtilBizApplication.REFUND_PERMISSION)"
                        :busy="working !== null"
                        :error="panelError"
                        :field-errors="panelFields"
                        :now="now"
                        @approve="openApprove"
                        @reject="openDialog('reject')"
                        @switch-manual="openSwitch"
                        @cancel="openDialog('cancel')"
                        @upload="(files) => uploadContracts(files)"
                        @replace="(b) => uploadContracts([b.file], b.id)"
                        @save-deposit="saveDeposit"
                        @confirm="confirmBooking"
                        @extend="openExtend"
                        @approve-refund="openRefund"
                        @deny-refund="openDialog('denyRefund')"
                        @mark-transferred="markTransferred"
                        @view-contract="openContract"
                    />
                </SplitterPanel>
                <SplitterPanel :size="30" :min-size="20" class="min-h-0 overflow-y-auto">
                    <BizAppPreview v-if="detail" :content="detail.content" :ad-type="detail.adType" />
                </SplitterPanel>
            </Splitter>
        </div>

        <template v-if="detail">
            <BizAppApproveDialog
                v-model:visible="dialogs.approve"
                :detail="detail"
                :availability="availability"
                :checking="checking"
                :busy="working === 'approve'"
                :error="dialogError"
                :field-errors="dialogFields"
                @period="checkPeriod"
                @submit="submitApprove"
            />
            <BizRejectDialog
                v-model:visible="dialogs.reject"
                :title="t('bizApplication.rejectTitle', { no: detail.no })"
                :description="t('bizApplication.rejectBody')"
                :confirm-label="t('bizApplication.actReject')"
                :codes="rejectCodes"
                :busy="working === 'reject'"
                :error="dialogError"
                :field-errors="dialogFields"
                @submit="submitReject"
            >
                <Message severity="info" :closable="false"><span class="text-[13px]">{{ t('bizApplication.rejectNoRefund') }}</span></Message>
            </BizRejectDialog>
            <BizReasonDialog
                v-model:visible="dialogs.switchManual"
                :title="t('bizApplication.switchTitle')"
                :description="t('bizApplication.switchBody')"
                :confirm-label="t('bizApplication.actSwitchManual')"
                :busy="working === 'switchManual'"
                :blocked="!dateDraft"
                :error="dialogError"
                @submit="submitSwitch"
            >
                <LabeledField :label="t('bizApplication.depositDeadlineDate')" :help="t('bizApplication.depositDeadlineHelp', { days: UtilBizApplication.DEPOSIT_DEADLINE_DAYS })">
                    <template #default="{ id, describedBy }">
                        <AppDatePicker v-model="dateDraft" :input-id="id" :min="UtilBizApplication.kstToday()" :described-by="describedBy" />
                    </template>
                </LabeledField>
            </BizReasonDialog>
            <BizReasonDialog
                v-model:visible="dialogs.extend"
                :title="t('bizApplication.extendTitle')"
                :description="t('bizApplication.extendBody')"
                :confirm-label="t('bizApplication.actExtend')"
                :busy="working === 'extend'"
                :blocked="!dateDraft"
                :error="dialogError"
                @submit="submitExtend"
            >
                <LabeledField :label="t('bizApplication.depositDeadlineDate')">
                    <template #default="{ id, describedBy }">
                        <AppDatePicker v-model="dateDraft" :input-id="id" :min="UtilBizApplication.kstToday()" :described-by="describedBy" />
                    </template>
                </LabeledField>
            </BizReasonDialog>
            <BizReasonDialog
                v-model:visible="dialogs.cancel"
                :title="t('bizApplication.cancelTitle', { no: detail.no })"
                :description="t('bizApplication.cancelBody')"
                :confirm-label="t('bizApplication.actCancel')"
                tone="danger"
                :busy="working === 'cancel'"
                :error="dialogError"
                @submit="submitCancel"
            />
            <BizReasonDialog
                v-if="detail.refund"
                v-model:visible="dialogs.refund"
                :title="t('bizApplication.refundApproveTitle')"
                :description="t('bizApplication.refundApproveBody')"
                :confirm-label="t('bizApplication.actApproveRefund')"
                tone="danger"
                :busy="working === 'refund'"
                :blocked="refundAmount === null || refundAmount < 0 || refundAmount > detail.refund.paidAmount"
                :error="dialogError"
                @submit="submitRefund"
            >
                <LabeledField :label="t('bizApplication.refundAmount')" :help="t('bizApplication.refundAmountHelp', { proposed: won(detail.refund.proposedAmount), paid: won(detail.refund.paidAmount) })" :error="dialogFields.amount ?? ''">
                    <template #default="{ id, describedBy, invalid }">
                        <InputNumber v-model="refundAmount" :input-id="id" :min="0" :max="detail.refund.paidAmount" :suffix="t('bizApplication.wonSuffix')" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </BizReasonDialog>
            <BizReasonDialog
                v-model:visible="dialogs.denyRefund"
                :title="t('bizApplication.refundDenyTitle')"
                :description="t('bizApplication.refundDenyBody')"
                :confirm-label="t('bizApplication.actDenyRefund')"
                :busy="working === 'denyRefund'"
                :error="dialogError"
                @submit="submitDeny"
            />
        </template>

        <Dialog v-model:visible="contractOpen" modal :draggable="false" :header="contractName || t('bizApplication.contract.title')" :style="{ width: 'min(40rem, calc(100vw - 2rem))' }">
            <div class="flex flex-col items-center gap-2">
                <iframe v-if="contractUrl && contractMime === 'application/pdf'" :src="contractUrl" :title="contractName" class="h-[70vh] w-full rounded border border-(--line)" />
                <img v-else-if="contractUrl" :src="contractUrl" :alt="contractName" class="max-h-[70vh] w-auto rounded border border-(--line)" />
                <p class="m-0 text-xs text-muted-color">{{ t('bizApplication.contract.viewLog') }}</p>
            </div>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    BizApplicationDetail,
    BizApplicationKind,
    BizApplicationQueue,
    BizApplicationRow,
    BizApproveRequest,
    BizAvailability,
    BizDepositRequest,
    BizDomain,
    BizPeriod,
    BizRejectCode,
} from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { BIZ_APPLICATION_QUEUES, BIZ_DOMAIN_KINDS, BIZ_REJECT_CODES } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

type Work = 'approve' | 'reject' | 'switchManual' | 'cancel' | 'upload' | 'deposit' | 'confirm' | 'extend' | 'refund' | 'denyRefund' | 'transfer' | 'contract';

interface Props {
    /** 이 화면이 다루는 도메인 — 목록·건수·종류 거르기·처리 권한이 여기로 정해진다 */
    domain: BizDomain;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
useSeoMeta({ title: () => t(`bizApplication.domainTitle.${props.domain}`) });

/** 이 도메인의 주소·권한 */
const screen = computed(() => UtilBizApplication.DOMAIN_SCREENS[props.domain]);

const api = useBizApplicationApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const kindLabelId = useId();
const qId = useId();
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const URGENT: readonly BizApplicationQueue[] = ['REVIEW', 'DEPOSIT', 'DEPOSIT_OVERDUE', 'REFUND'];
const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
const queue = ref<BizApplicationQueue>((BIZ_APPLICATION_QUEUES as readonly string[]).includes(queryString('queue')) ? (queryString('queue') as BizApplicationQueue) : 'REVIEW');
const kind = ref<string>('');
const q = ref('');
const applied = ref<{ kind?: BizApplicationKind; q?: string }>({});
/** 종류 거르기 — 이 도메인의 종류만(다른 도메인 종류는 서버가 400) */
const kindOptions = computed(() => [{ value: '', label: t('common.all') }, ...BIZ_DOMAIN_KINDS[props.domain].map((k) => ({ value: k, label: t(`bizApplication.kindShort.${k}`) }))]);
const rejectCodes = computed(() => BIZ_REJECT_CODES.map((c) => ({ value: c, label: t(`bizApplication.rejectCode.${c}`) })));

const rows = ref<BizApplicationRow[]>([]);
const counts = ref<Record<BizApplicationQueue, number> | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => BIZ_APPLICATION_QUEUES.map((k) => ({ value: k, label: t(`bizApplication.queue.${k}`), count: counts.value?.[k] ?? 0, urgent: URGENT.includes(k) })));

/** 기한 글자를 1분마다 다시 그린다 */
const now = ref(Date.now());
let clock: ReturnType<typeof setInterval> | null = null;

/** 좁은 화면(1024px 미만)은 위아래로 쌓는다 */
const narrow = ref(false);
let media: MediaQueryList | null = null;
const onMedia = () => (narrow.value = Boolean(media?.matches));

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ ...applied.value, domain: props.domain, queue: queue.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        counts.value = found.counts;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    applied.value = { kind: (kind.value || undefined) as BizApplicationKind | undefined, q: q.value.trim() || undefined };
    void reload();
}

function selectQueue(value: BizApplicationQueue): void {
    queue.value = value;
    void router.replace({ query: { ...route.query, queue: value } });
    void reload();
}

// ── 고른 건 ──
const selectedId = ref<string | null>(queryString('id') || null);
const detail = ref<BizApplicationDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);

let dseq = 0;
async function loadDetail(): Promise<void> {
    const id = selectedId.value;
    if (!id) return;
    const mine = ++dseq;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.detail(id);
        if (mine === dseq) detail.value = found;
    } catch (e) {
        if (mine !== dseq) return;
        detail.value = null;
        detailError.value = toApiError(e);
    } finally {
        if (mine === dseq) detailLoading.value = false;
    }
}

function select(id: string): void {
    if (selectedId.value !== id) detail.value = null;
    selectedId.value = id;
    panelError.value = '';
    panelFields.value = {};
    void router.replace({ query: { ...route.query, id } });
    void loadDetail();
}

// ── 동작 ──
const working = ref<Work | null>(null);
const dialogError = ref('');
const dialogFields = ref<Record<string, string>>({});
/** 대화상자 없는 처리(입금 패널)의 오류 */
const panelError = ref('');
const panelFields = ref<Record<string, string>>({});
const dialogs = reactive({ approve: false, reject: false, switchManual: false, extend: false, cancel: false, refund: false, denyRefund: false });

function openDialog(name: keyof typeof dialogs): void {
    dialogError.value = '';
    dialogFields.value = {};
    dialogs[name] = true;
}

/** 동작 하나 — 성공하면 상세·목록을 새로. 409 는 서버 문구 + 다시 읽기. 대화상자가 있으면 그 안에, 없으면 패널(또는 토스트)에 오류 */
async function run(work: Work, call: () => Promise<BizApplicationDetail>, done: string, dialog?: keyof typeof dialogs): Promise<void> {
    working.value = work;
    dialogError.value = '';
    dialogFields.value = {};
    panelError.value = '';
    panelFields.value = {};
    try {
        detail.value = await call();
        toast.success(done);
        if (dialog) dialogs[dialog] = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 409) void loadDetail();
        const fields = failure.code === 'VALIDATION' && failure.fields ? failure.fields : null;
        if (dialog) {
            if (fields) dialogFields.value = fields;
            else dialogError.value = failure.message;
        } else if (fields) panelFields.value = fields;
        else if (work === 'confirm' || work === 'deposit' || work === 'upload') panelError.value = failure.message;
        else toast.error(failure.message, 8000);
    } finally {
        working.value = null;
    }
}

// 승인 — 기간을 옮기면 남은 자리·요금을 다시 받는다
const availability = ref<BizAvailability | null>(null);
const checking = ref(false);
function openApprove(): void {
    availability.value = null;
    openDialog('approve');
}
async function checkPeriod(period: BizPeriod): Promise<void> {
    const d = detail.value;
    if (!d) return;
    checking.value = true;
    try {
        availability.value = await api.availability(d.id, period);
    } catch (e) {
        dialogError.value = toApiError(e).message;
    } finally {
        checking.value = false;
    }
}
async function submitApprove(body: BizApproveRequest): Promise<void> {
    const d = detail.value;
    if (d) await run('approve', () => api.approve(d.id, body), body.method === 'ONLINE' ? t('bizApplication.approvedOnline') : t('bizApplication.approvedManual'), 'approve');
}

async function submitReject(body: { code: BizRejectCode; text: string }): Promise<void> {
    const d = detail.value;
    if (d) await run('reject', () => api.reject(d.id, body), t('bizApplication.rejected'), 'reject');
}

const dateDraft = ref('');
function openSwitch(): void {
    dateDraft.value = UtilBizApplication.addDays(UtilBizApplication.kstToday(), UtilBizApplication.DEPOSIT_DEADLINE_DAYS);
    openDialog('switchManual');
}
async function submitSwitch(reason: string): Promise<void> {
    const d = detail.value;
    const deadlineAt = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(dateDraft.value, 1));
    if (d) await run('switchManual', () => api.switchToManual(d.id, { deadlineAt, reason }), t('bizApplication.switched'), 'switchManual');
}

function openExtend(): void {
    dateDraft.value = UtilBizApplication.addDays(UtilBizApplication.kstToday(), 3);
    openDialog('extend');
}
async function submitExtend(): Promise<void> {
    const d = detail.value;
    const deadlineAt = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(dateDraft.value, 1));
    if (d) await run('extend', () => api.extendDeposit(d.id, deadlineAt), t('bizApplication.extended'), 'extend');
}

async function submitCancel(reason: string): Promise<void> {
    const d = detail.value;
    if (d) await run('cancel', () => api.cancel(d.id, reason), t('bizApplication.canceled'), 'cancel');
}

/**
 * 계약서 사본 올리기 — 서버 요청 한도(11MB) 때문에 한 장씩 차례로 올린다. 중간에 실패하면 거기서 멈추고(앞 장은 이미 올라감) 상세를 다시 읽는다.
 */
async function uploadContracts(files: File[], replaceId?: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    let done = 0;
    await run(
        'upload',
        async () => {
            let last: BizApplicationDetail | null = null;
            try {
                for (const f of files) {
                    last = await api.uploadContracts(d.id, [f], replaceId);
                    done++;
                    detail.value = last;
                }
            } catch (e) {
                if (done > 0) void loadDetail();
                throw e;
            }
            return last ?? d;
        },
        replaceId ? t('bizApplication.contract.replacedDone') : t('bizApplication.contract.uploaded', { n: files.length }),
    );
}

const contractOpen = ref(false);
const contractUrl = ref('');
const contractMime = ref('');
const contractName = ref('');
/** 계약서 사본 열람 — `data:` 주소(그림은 `<img>`, PDF 는 `<iframe>`). 열람 기록이 남으니 상세를 다시 읽는다 */
async function openContract(fileId: string): Promise<void> {
    const d = detail.value;
    if (!d) return;
    working.value = 'contract';
    try {
        const file = await api.contract(d.id, fileId);
        contractUrl.value = file.url;
        contractMime.value = file.mimeType;
        contractName.value = file.fileName;
        contractOpen.value = true;
        void loadDetail();
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        working.value = null;
    }
}

async function markTransferred(): Promise<void> {
    const d = detail.value;
    if (d) await run('transfer', () => api.markTransferred(d.id), t('bizApplication.refundTransferred'));
}

async function saveDeposit(body: BizDepositRequest): Promise<void> {
    const d = detail.value;
    if (d) await run('deposit', () => api.saveDeposit(d.id, body), t('bizApplication.deposit.saved'));
}

async function confirmBooking(): Promise<void> {
    const d = detail.value;
    if (d) await run('confirm', () => api.confirmBooking(d.id), t('bizApplication.confirmed'));
}

const refundAmount = ref<number | null>(0);
function openRefund(): void {
    refundAmount.value = detail.value?.refund?.proposedAmount ?? 0;
    openDialog('refund');
}
async function submitRefund(reason: string): Promise<void> {
    const d = detail.value;
    const amount = refundAmount.value ?? 0;
    if (d) await run('refund', () => api.approveRefund(d.id, { amount, reason }), t('bizApplication.refundApproved'), 'refund');
}

async function submitDeny(reason: string): Promise<void> {
    const d = detail.value;
    if (d) await run('denyRefund', () => api.denyRefund(d.id, reason), t('bizApplication.refundDenied'), 'denyRefund');
}

onMounted(() => {
    media = window.matchMedia('(max-width: 1023px)');
    onMedia();
    media.addEventListener('change', onMedia);
    clock = setInterval(() => (now.value = Date.now()), 60_000);
    void reload();
    if (selectedId.value) void loadDetail();
});
onBeforeUnmount(() => {
    media?.removeEventListener('change', onMedia);
    if (clock) clearInterval(clock);
});
</script>
