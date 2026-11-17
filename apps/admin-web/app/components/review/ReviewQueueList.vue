<template>
    <!--
        확인할 일 목록(trial-group-contract 「구현 결정」 7 — 칭호 관리 「확인할 일」 탭과 「광고 › 체험단」 확인할 일이 같은 부품). 서버를 부르지 않는다 — 줄은 `rows`(서버 `ReviewQueueService.Item`), 처리는 `actions`.
        거르기: 상태(처리할 것 · 처리됨 · 전체) · 종류(전체 + 볼 수 있는 종류, 처리할 것 수 — 서버 `pending`). 한 줄: 종류 딱지 · 대상(칭호 이름·글 제목·모집 제목 + 메모) · 회원 · 때 · 상태 · 처리 단추.
          칭호 신청 — [승인](확인) · [거절](사유 필수 — 회원에게 보인다)
          구매 인증 — 글(→ 공개 글) · [사진](비공개 — 열람 기록) · [승인](보너스 포인트) · [거절](사유 필수)
          반납 확인 — 모집(→ 모집 상세) · [반납 받음](확인 — 사진은 모집 상세 입고·발송 탭에서)
          파손 신고 — 모집 · 회원 이의(없으면 신고 설명) · [판정](모집 상세에서 신고·사진을 받아 사진 비교 — 보내기 전 ↔ 반납 뒤 ↔ 신고 · 판정표 4종 + 메모)
          협찬 숨김 — 글 · 신고 수 · [확인](협찬 표시 붙이기 — 제재 대상 · 무시)
        그새 다른 사람이 처리했으면(409·404·403) 토스트 + 다시 받기. 처리 권한이 없으면 단추를 감춘다(`canTitle`·`canTrial`).
    -->
    <div class="flex min-w-0 flex-col gap-3">
        <div class="flex flex-wrap items-center gap-2">
            <SelectButton :model-value="status" :options="statusOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('trial.queue.statusLabel')" @update:model-value="onStatus" />
            <Select :model-value="type ?? ALL" :options="typeOptions" option-label="label" option-value="value" size="small" class="w-52" :aria-label="t('trial.queue.typeLabel')" @update:model-value="onType" />
            <span class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('trial.queue.total', { count: rows.length }) }}</span>
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable :value="rows" :data-key="(r: ReviewQueueItem) => `${r.type}:${r.id}`" :loading="loading" scrollable>
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('trial.queue.loadFailed')" :description="loadError" retry :retrying="loading" @retry="actions.reload()" />
                    <div v-else-if="loading" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-check-square" :title="status === 'PENDING' ? t('trial.queue.emptyPending') : t('trial.queue.empty')" />
                </template>
                <Column :header="t('trial.queue.colType')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <Tag :value="t(`trial.queue.type.${data.type}`)" :severity="typeSeverity(data.type)" />
                    </template>
                </Column>
                <Column :header="t('trial.queue.colTarget')" style="min-width: 18rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-0.5 text-[13px]">
                            <a v-if="data.refs.postId" :href="postUrl(data.refs.postId)" target="_blank" rel="noopener" class="line-clamp-2 font-medium text-(--p-primary-color) hover:underline">{{ data.title ?? t('trial.queue.untitled') }}</a>
                            <NuxtLink v-else-if="data.refs.trialId" :to="UtilTrial.detailPath(data.refs.trialId)" class="line-clamp-1 font-medium text-(--p-primary-color) hover:underline">{{ data.title ?? t('trial.queue.untitled') }}</NuxtLink>
                            <span v-else class="font-medium">{{ data.title ?? '' }}</span>
                            <span v-if="data.refs.boardSlug" class="text-[11px] text-muted-color">{{ data.refs.boardSlug }}<template v-if="data.refs.reportCount"> · {{ t('trial.queue.reportCount', { n: data.refs.reportCount }) }}</template></span>
                            <span v-if="data.note" class="line-clamp-2 text-[11px]">{{ t(`trial.queue.noteLabel.${data.type}`, { text: data.note }) }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('trial.queue.colMember')" style="min-width: 8rem">
                    <template #body="{ data }"><span class="text-[13px]">{{ data.member?.nickname ?? '—' }}</span></template>
                </Column>
                <Column :header="t('trial.queue.colAt')" style="min-width: 8rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ data.at ? time.full(data.at) : '—' }}</span></template>
                </Column>
                <Column :header="t('trial.queue.colStatus')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <Tag :value="stateLabel(data)" :severity="isPending(data) ? 'warn' : 'success'" />
                    </template>
                </Column>
                <Column style="min-width: 12rem">
                    <template #body="{ data }">
                        <div v-if="isPending(data) && canAct(data)" class="flex flex-wrap gap-1.5">
                            <template v-if="data.type === 'TITLE_APPLICATION' || data.type === 'PURCHASE_PROOF'">
                                <Button v-if="data.type === 'PURCHASE_PROOF'" type="button" :label="t('trial.queue.photo')" icon="pi pi-image" size="small" severity="secondary" variant="outlined" @click="openPhotos([actions.proofPhotoUrl(data.id)])" />
                                <Button type="button" :label="t('trial.queue.approve')" icon="pi pi-check" size="small" :disabled="busy" @click="approve(data)" />
                                <Button type="button" :label="t('trial.queue.reject')" icon="pi pi-times" size="small" severity="danger" variant="outlined" :disabled="busy" @click="openReject(data)" />
                            </template>
                            <Button v-else-if="data.type === 'RETURN_CHECK'" type="button" :label="t('trial.queue.markReturned')" icon="pi pi-replay" size="small" :disabled="busy" @click="markReturned(data)" />
                            <Button v-else-if="data.type === 'DAMAGE_REPORT'" type="button" :label="t('trial.queue.decide')" icon="pi pi-check-square" size="small" :disabled="busy" :loading="loadingDamage === data.id" @click="openDecide(data)" />
                            <Button v-else-if="data.type === 'SPONSOR_HIDDEN'" type="button" :label="t('trial.queue.sponsorDecide')" icon="pi pi-flag" size="small" :disabled="busy" @click="openSponsor(data)" />
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <!-- 거절 사유(칭호 신청 · 구매 인증) -->
        <MemberReasonDialog
            v-model:visible="rejectOpen"
            :title="rejectTarget?.type === 'PURCHASE_PROOF' ? t('trial.queue.rejectProofTitle') : t('trial.queue.rejectTitleTitle')"
            :label="t('trial.queue.rejectReason')"
            :submit-label="t('trial.queue.reject')"
            submit-icon="pi pi-times"
            danger
            :notice="t('trial.queue.rejectNotice')"
            :min="1"
            :max="rejectTarget?.type === 'PURCHASE_PROOF' ? 300 : 500"
            :busy="busy"
            :error="dialogError"
            :field-error="dialogFieldError"
            @submit="submitReject"
        />

        <!-- 사진 크게 -->
        <Dialog :visible="photoUrls.length > 0" modal :header="t('trial.queue.photoTitle')" :style="{ width: 'min(48rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="(v: boolean) => (v ? null : (photoUrls = []))">
            <p class="m-0 mb-2 text-xs text-muted-color">{{ t('trial.queue.photoPrivate') }}</p>
            <div class="grid gap-2 sm:grid-cols-2">
                <img v-for="url in photoUrls" :key="url" :src="url" alt="" class="max-h-[60vh] w-full rounded object-contain" />
            </div>
        </Dialog>

        <!-- 파손 판정 -->
        <Dialog v-model:visible="decideOpen" modal :header="t('trial.queue.decideTitle')" :style="{ width: 'min(46rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!busy">
            <form v-if="damage" class="flex flex-col gap-4" novalidate @submit.prevent="submitDecide">
                <p class="m-0 whitespace-pre-line text-sm">{{ damage.report.description }}</p>
                <div class="grid gap-3 sm:grid-cols-3">
                    <div v-for="col in photoColumns" :key="col.key" class="flex flex-col gap-1">
                        <span class="text-xs font-medium">{{ col.label }}</span>
                        <div class="flex flex-wrap gap-1.5">
                            <button v-for="p in col.photos" :key="p.id" type="button" class="block size-16 overflow-hidden rounded border border-(--line)" :aria-label="t('trial.queue.photoOpen')" @click="openPhotos(col.photos.map((x) => x.url))">
                                <img :src="p.url" alt="" class="size-full object-cover" />
                            </button>
                            <span v-if="col.photos.length === 0" class="text-xs text-muted-color">{{ t('trial.queue.noPhoto') }}</span>
                        </div>
                    </div>
                </div>
                <p v-if="damage.report.objection" class="m-0 rounded-lg bg-surface-100 px-3 py-2 text-sm dark:bg-surface-800">{{ t('trial.queue.objectionLine', { text: damage.report.objection }) }}</p>
                <p v-else class="m-0 text-xs text-muted-color">{{ t('trial.queue.noObjection', { date: time.full(damage.report.objectionDueAt) }) }}</p>
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1 text-sm font-medium">{{ t('trial.queue.verdictLegend') }}</legend>
                    <label v-for="v in UtilTrial.VERDICTS" :key="v" class="flex cursor-pointer items-start gap-2 rounded-lg border p-2.5 text-sm" :class="verdict === v ? 'border-(--p-primary-color)' : 'border-(--line)'">
                        <RadioButton v-model="verdict" :value="v" :input-id="`${baseId}-v-${v}`" name="trial-verdict" class="mt-0.5" />
                        <span>
                            <span class="font-medium">{{ t(`trial.verdict.${v}`) }}</span>
                            <span class="block text-xs text-muted-color">{{ t(`trial.verdictTable.${v}`) }}</span>
                        </span>
                    </label>
                </fieldset>
                <LabeledField :label="t('trial.queue.decisionNote')" :error="dialogFieldError">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="note" rows="2" auto-resize fluid :maxlength="1000" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" />
                    </template>
                </LabeledField>
                <Message v-if="verdict === 'MEMBER_FAULT' || verdict === 'INTENTIONAL'" severity="warn" variant="simple" size="small">{{ t('trial.queue.contactNotice') }}</Message>
                <Message v-if="dialogError" severity="error" variant="simple" size="small">{{ dialogError }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="decideOpen = false" />
                    <Button type="submit" :label="t('trial.queue.decideSubmit')" :loading="busy" :disabled="!verdict" />
                </div>
            </form>
        </Dialog>

        <!-- 협찬 숨김 확인 -->
        <Dialog v-model:visible="sponsorOpen" modal :header="t('trial.queue.sponsorTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!busy">
            <form v-if="sponsorTarget" class="flex flex-col gap-3" novalidate @submit.prevent>
                <p class="m-0 text-sm">{{ t('trial.queue.sponsorText', { title: sponsorTarget.title ?? '' }) }}</p>
                <LabeledField :label="t('trial.queue.decisionNote')">
                    <template #default="{ id, describedBy }">
                        <Textarea :id="id" v-model="note" rows="2" auto-resize fluid :maxlength="300" :aria-describedby="describedBy" :disabled="busy" />
                    </template>
                </LabeledField>
                <Message v-if="dialogError" severity="error" variant="simple" size="small">{{ dialogError }}</Message>
                <div class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="sponsorOpen = false" />
                    <Button type="button" :label="t('trial.queue.sponsorDismiss')" severity="secondary" variant="outlined" :loading="busy" @click="submitSponsor(false)" />
                    <Button type="button" :label="t('trial.queue.sponsorConfirm')" severity="danger" :loading="busy" @click="submitSponsor(true)" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ReviewQueueActions, ReviewQueueDamageView } from '~/components/review/ReviewQueueList.types';
import type { ReviewQueueItem, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import type { TrialDamageVerdict, TrialPhoto } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';

const ALL = '__all__';
const TYPES: readonly ReviewQueueType[] = ['TITLE_APPLICATION', 'PURCHASE_PROOF', 'RETURN_CHECK', 'DAMAGE_REPORT', 'SPONSOR_HIDDEN'];
/** 처리할 것으로 보는 상태(서버 `ReviewQueueService` — 파손 신고는 REPORTED·OBJECTED·AWAITING) */
const PENDING_STATES = new Set(['PENDING', 'REPORTED', 'OBJECTED', 'AWAITING']);
/** 상태 글자가 있는 값 — 그 밖(판정 코드)은 판정 이름 */
const STATE_KEYS = new Set(['PENDING', 'APPROVED', 'REJECTED', 'RETURNED', 'UNRETURNED', 'REPORTED', 'OBJECTED', 'AWAITING', 'CONFIRMED', 'DISMISSED']);

interface Props {
    rows: ReviewQueueItem[];
    loading?: boolean;
    /** 목록 받기 실패 문구 */
    loadError?: string;
    /** 상태 거르기 */
    status: ReviewQueueStatus;
    /** 종류 거르기(null = 전체) */
    type?: ReviewQueueType | null;
    /** 종류별 처리할 것 수(볼 수 있는 종류만 — 거르기 목록도 이 종류만) */
    pendingCounts?: Partial<Record<ReviewQueueType, number>>;
    /** 칭호 신청 처리 권한(`title.review`) */
    canTitle?: boolean;
    /** 체험단 확인 처리 권한(`trial.review`) */
    canTrial?: boolean;
    /** 공개 사이트 주소(글 링크) — 비우면 상대 주소 */
    siteOrigin?: string;
    actions: ReviewQueueActions;
}

const props = withDefaults(defineProps<Props>(), { loading: false, loadError: '', type: null, pendingCounts: () => ({}), canTitle: false, canTrial: false, siteOrigin: '' });
const emit = defineEmits<{ 'update:status': [status: ReviewQueueStatus]; 'update:type': [type: ReviewQueueType | null] }>();

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { confirm } = useConfirmDialog();
const baseId = useId();

const statusOptions = computed(() => [
    { value: 'PENDING' as const, label: t('trial.queue.pending') },
    { value: 'DONE' as const, label: t('trial.queue.done') },
    { value: 'ALL' as const, label: t('common.all') },
]);
/** 볼 수 있는 종류(서버 `pending` 의 키)만 거르기 목록에 — 아직 받기 전이면 권한으로 */
const typeOptions = computed(() => {
    const visible = Object.keys(props.pendingCounts).length ? TYPES.filter((ty) => ty in props.pendingCounts) : TYPES.filter((ty) => (ty === 'TITLE_APPLICATION' ? props.canTitle : props.canTrial));
    return [
        { value: ALL, label: t('trial.queue.allTypes') },
        ...visible.map((ty) => ({ value: ty as string, label: props.pendingCounts[ty] ? t('trial.queue.typeWithCount', { name: t(`trial.queue.type.${ty}`), n: props.pendingCounts[ty] }) : t(`trial.queue.type.${ty}`) })),
    ];
});

function onStatus(v: ReviewQueueStatus | null): void {
    if (v) emit('update:status', v);
}
function onType(v: string | null): void {
    emit('update:type', !v || v === ALL ? null : (v as ReviewQueueType));
}

function typeSeverity(ty: ReviewQueueType): 'info' | 'warn' | 'danger' | 'secondary' | 'contrast' {
    if (ty === 'DAMAGE_REPORT') return 'danger';
    if (ty === 'SPONSOR_HIDDEN') return 'warn';
    if (ty === 'RETURN_CHECK') return 'contrast';
    if (ty === 'PURCHASE_PROOF') return 'info';
    return 'secondary';
}

function isPending(row: ReviewQueueItem): boolean {
    return PENDING_STATES.has(row.status);
}
function stateLabel(row: ReviewQueueItem): string {
    return STATE_KEYS.has(row.status) ? t(`trial.queue.state.${row.status}`) : t(`trial.verdict.${row.status}`);
}
function canAct(row: ReviewQueueItem): boolean {
    return row.type === 'TITLE_APPLICATION' ? props.canTitle : props.canTrial;
}
function postUrl(postId: string): string {
    return `${props.siteOrigin}/posts/${encodeURIComponent(postId)}`;
}

/* ── 공통: 처리 실행 ── */
const busy = ref(false);
const dialogError = ref('');
const dialogFieldError = ref('');

/** 처리 하나 — 끝났으면(성공 또는 그새 바뀜) true. 그새 바뀌었으면(409·404·403) 알리고 다시 받는다 */
async function run(task: () => Promise<void>, success: string, inDialog: boolean): Promise<boolean> {
    if (busy.value) return false;
    busy.value = true;
    dialogError.value = '';
    dialogFieldError.value = '';
    try {
        await task();
        toast.success(success);
        await props.actions.reload();
        return true;
    } catch (e) {
        const error = errorOf(e);
        if (error.status === 409 || error.status === 404 || error.status === 403) {
            toast.error(error.message, 8000);
            await props.actions.reload();
            return true;
        }
        const field = error.fields?.reason ?? error.fields?.note ?? error.fields?.verdict;
        if (inDialog && field) dialogFieldError.value = field;
        else if (inDialog) dialogError.value = error.message;
        else toast.error(error.message, 8000);
        return false;
    } finally {
        busy.value = false;
    }
}

async function approve(row: ReviewQueueItem): Promise<void> {
    const name = row.member?.nickname ?? '';
    const isTitle = row.type === 'TITLE_APPLICATION';
    const ok = await confirm({
        title: isTitle ? t('trial.queue.approveTitleTitle') : t('trial.queue.approveProofTitle'),
        message: isTitle ? t('trial.queue.approveTitleText', { name, title: row.title ?? '' }) : t('trial.queue.approveProofText', { name }),
        confirmLabel: t('trial.queue.approve'),
    });
    if (!ok) return;
    await run(() => (isTitle ? props.actions.approveTitle(row.id) : props.actions.approveProof(row.id)), t('trial.queue.approved', { name }), false);
}

const rejectOpen = ref(false);
const rejectTarget = ref<ReviewQueueItem | null>(null);
function openReject(row: ReviewQueueItem): void {
    rejectTarget.value = row;
    dialogError.value = '';
    dialogFieldError.value = '';
    rejectOpen.value = true;
}
async function submitReject(reason: string): Promise<void> {
    const row = rejectTarget.value;
    if (!row) return;
    const done = await run(() => (row.type === 'TITLE_APPLICATION' ? props.actions.rejectTitle(row.id, reason) : props.actions.rejectProof(row.id, reason)), t('trial.queue.rejected', { name: row.member?.nickname ?? '' }), true);
    if (done) rejectOpen.value = false;
}

async function markReturned(row: ReviewQueueItem): Promise<void> {
    const ok = await confirm({ title: t('trial.queue.markReturnedTitle'), message: t('trial.queue.markReturnedText', { name: row.member?.nickname ?? '', title: row.title ?? '' }), confirmLabel: t('trial.queue.markReturned') });
    if (!ok) return;
    await run(() => props.actions.markReturned(row.id), t('trial.queue.returnedDone'), false);
}

/* ── 사진 ── */
const photoUrls = ref<string[]>([]);
function openPhotos(urls: string[]): void {
    photoUrls.value = urls;
}

/* ── 파손 판정 ── */
const decideOpen = ref(false);
const damage = ref<ReviewQueueDamageView | null>(null);
const loadingDamage = ref<string | null>(null);
const verdict = ref<TrialDamageVerdict | null>(null);
const note = ref('');
const photoColumns = computed<{ key: string; label: string; photos: TrialPhoto[] }[]>(() =>
    damage.value
        ? [
              { key: 'before', label: t('trial.queue.photoBefore'), photos: damage.value.beforePhotos },
              { key: 'after', label: t('trial.queue.photoAfter'), photos: damage.value.afterPhotos },
              { key: 'report', label: t('trial.queue.photoReport'), photos: damage.value.report.photos },
          ]
        : [],
);
async function openDecide(row: ReviewQueueItem): Promise<void> {
    const trialId = row.refs.trialId;
    if (!trialId || loadingDamage.value) return;
    loadingDamage.value = row.id;
    try {
        damage.value = await props.actions.loadDamage(trialId, row.id);
        verdict.value = null;
        note.value = '';
        dialogError.value = '';
        dialogFieldError.value = '';
        decideOpen.value = true;
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        loadingDamage.value = null;
    }
}
async function submitDecide(): Promise<void> {
    const r = damage.value?.report;
    const v = verdict.value;
    if (!r || !v) return;
    const done = await run(() => props.actions.decideDamage(r.id, v, note.value.trim()), t('trial.queue.decided', { verdict: t(`trial.verdict.${v}`) }), true);
    if (done) decideOpen.value = false;
}

/* ── 협찬 숨김 ── */
const sponsorOpen = ref(false);
const sponsorTarget = ref<ReviewQueueItem | null>(null);
function openSponsor(row: ReviewQueueItem): void {
    sponsorTarget.value = row;
    note.value = '';
    dialogError.value = '';
    sponsorOpen.value = true;
}
async function submitSponsor(confirmIt: boolean): Promise<void> {
    const row = sponsorTarget.value;
    if (!row) return;
    const done = await run(() => props.actions.decideSponsor(row.id, confirmIt, note.value.trim()), confirmIt ? t('trial.queue.sponsorConfirmed') : t('trial.queue.sponsorDismissed'), true);
    if (done) sponsorOpen.value = false;
}
</script>
