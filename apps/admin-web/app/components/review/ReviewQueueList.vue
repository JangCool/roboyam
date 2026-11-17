<template>
    <!--
        확인할 일 목록(trial-group-contract 「구현 결정」 7 — 칭호 관리 「확인할 일」 탭과 「광고 › 체험단」 확인할 일이 같은 부품). 서버를 부르지 않는다 — 줄은 `rows`(서버 `ReviewQueueService.Item`), 처리는 `actions`.
        거르기: 상태(처리할 것 · 처리됨 · 전체) · 종류(전체 + 볼 수 있는 종류, 처리할 것 수 — 서버 `pending`). 한 줄: 종류 딱지 · 대상(칭호 이름·글 제목·모집 제목 + 메모) · 회원 · 때 · 상태 · 처리 단추.
          칭호 신청 — [승인](확인) · [거절](사유 필수 — 회원에게 보인다)
          구매 인증 — 글(→ 공개 글) · [사진](비공개 — 열람 기록) · [승인](보너스 포인트) · [거절](사유 필수)
          반납 확인 — 모집(→ 모집 상세) · [모집에서 반납 받기](→ 모집 상세 입고·발송 탭 — 서버가 반납 사진을 꼭 받으므로 사진을 올리는 그 자리에서 처리)
          파손 신고 — 모집 · 회원 이의(없으면 신고 설명) · [판정](모집 상세에서 신고·사진을 받아 사진 비교 — 보내기 전 ↔ 반납 뒤 ↔ 신고 · 판정표 4종 + 메모)
          협찬 숨김 — 글 · 신고 수 · [확인](협찬으로 고정 + 글쓴이 제재 곁들이기 — 신고 처리와 같은 제재 구획 `ReportSanctionSection` · 무시, 점검 D2)
            익명 글은 봉인 미리보기(`refs.anonymousPostId`)로 단계를 고르고 닉네임 확인 없이 보낸다(서버가 봉인 제재) · `refs.sanctionable` "false"(비회원·탈퇴·파기)면 제재 불가 안내
        사진은 비공개(V243) — `TrialPhotoImage` 가 받아 그린다(파기된 사진은 자리 표시).
        그새 다른 사람이 처리했으면(409·404·403) 토스트 + 다시 받기. 처리 권한이 없으면 단추를 감춘다(`canTitle`·`canTrial` · 반납 받기는 `canReturn` — trial.manage·ad.manage, 점검 D12).
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
                    <template #body="{ data }"><TrialMemberName :member="data.member" :linkable="canReadMember" class="text-[13px]" /></template>
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
                            <Button v-else-if="data.type === 'RETURN_CHECK' && data.refs.trialId && canReturn" as="router-link" :to="{ path: UtilTrial.detailPath(data.refs.trialId), query: { tab: 'shipping' } }" :label="t('trial.queue.openReturn')" icon="pi pi-replay" size="small" />
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
                <TrialPhotoImage v-for="url in photoUrls" :key="url" :url="url" box-class="h-[60vh] max-h-[60vh] w-full" fit="contain" />
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
                            <TrialPhotoImage v-for="p in col.photos" :key="p.id" :url="p.url" :purged="p.purged" box-class="size-16" zoomable />
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

        <!-- 협찬 숨김 확인(점검 D2 — 협찬으로 고정 + 글쓴이 제재 곁들이기) -->
        <Dialog v-model:visible="sponsorOpen" modal :header="t('trial.queue.sponsorTitle')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" :draggable="false" :closable="!busy">
            <form v-if="sponsorTarget" class="flex flex-col gap-3" novalidate @submit.prevent>
                <p class="m-0 text-sm">{{ t('trial.queue.sponsorText', { title: sponsorTarget.title ?? '' }) }}</p>
                <LabeledField :label="t('trial.queue.sponsorNoteLabel')" :hint="t('report.noteCounter', { n: note.trim().length, max: SPONSOR_NOTE_MAX })" :help="t('trial.queue.sponsorNoteHelp')" :error="sponsorFieldErrors.note ?? ''">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="note" rows="2" auto-resize fluid :maxlength="SPONSOR_NOTE_MAX" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" />
                    </template>
                </LabeledField>
                <ReportSanctionSection
                    v-if="sponsorSanction"
                    v-model:with="sponsorAttach.withSanction.value"
                    v-model:step="sponsorAttach.step.value"
                    :sanction="sponsorSanction"
                    :on="sponsorAttach.on.value"
                    :busy="busy"
                    :tried="sponsorTried"
                    :reason-shown="sponsorAttach.reasonShown.value"
                    :reason-touched="sponsorAttach.reasonTouched.value"
                    :reason-error="sponsorAttach.reasonError.value"
                    :step-field-errors="sponsorAttach.stepFieldErrors.value"
                    :with-help="sponsorTarget.refs.anonymous === 'true' ? t('trial.queue.sponsorSanctionHelpAnonymous') : t('trial.queue.sponsorSanctionHelp')"
                    :not-eligible="t('trial.queue.sponsorNoAuthor')"
                    @update:reason="sponsorAttach.onReason"
                    @retry="actions.retrySponsorAuthor()"
                />
                <Message v-if="dialogError" severity="error" variant="simple" size="small">{{ dialogError }}</Message>
                <div class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="sponsorOpen = false" />
                    <Button type="button" :label="t('trial.queue.sponsorDismiss')" severity="secondary" variant="outlined" :loading="busy" @click="submitSponsor(false)" />
                    <Button type="button" :label="sponsorConfirmLabel" severity="danger" :loading="busy" :disabled="!sponsorAttach.ready.value" @click="submitSponsor(true)" />
                </div>
            </form>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { ReviewQueueActions, ReviewQueueDamageView } from '~/components/review/ReviewQueueList.types';
import type { ReviewQueueItem, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';
import type { TrialDamageVerdict, TrialPhoto } from '~/composables/trial/api/useTrialApi.types';
import UtilSanctionMessages from '~/utils/util-sanction-messages';
import UtilTrial from '~/utils/util-trial';

const ALL = '__all__';
const TYPES: readonly ReviewQueueType[] = ['TITLE_APPLICATION', 'PURCHASE_PROOF', 'RETURN_CHECK', 'DAMAGE_REPORT', 'SPONSOR_HIDDEN'];
/** 처리할 것으로 보는 상태(서버 `ReviewQueueService` — 파손 신고는 REPORTED·OBJECTED·AWAITING) */
const PENDING_STATES = new Set(['PENDING', 'REPORTED', 'OBJECTED', 'AWAITING']);
/** 상태 글자가 있는 값 — 그 밖(판정 코드)은 판정 이름 */
/** 협찬 숨김 처리 글 최대(서버 `SponsorReviewService` — 300자) */
const SPONSOR_NOTE_MAX = 300;
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
    /** 반납 받기 권한(`trial.manage`·`ad.manage` — 점검 D12, `trial.review` 는 볼 수만 있다) */
    canReturn?: boolean;
    /** 협찬 숨김 확인 대화상자의 글쓴이 제재 현황(`useReviewQueue().sponsorSanction` — `sanction.read` 가 없으면 null) */
    sponsorSanction?: ReportSanctionContext | null;
    /** 회원 이름을 회원 관리로 가는 링크로(`member.read`) */
    canReadMember?: boolean;
    /** 공개 사이트 주소(글 링크) — 비우면 상대 주소 */
    siteOrigin?: string;
    actions: ReviewQueueActions;
}

const props = withDefaults(defineProps<Props>(), { loading: false, loadError: '', type: null, pendingCounts: () => ({}), canTitle: false, canTrial: false, canReturn: false, sponsorSanction: null, canReadMember: false, siteOrigin: '' });
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

/* ── 협찬 숨김(점검 D2) — 확인 = 협찬으로 고정 + 글쓴이 제재 곁들이기(신고 처리와 같은 제재 구획) · 무시 ── */
const sponsorOpen = ref(false);
const sponsorTarget = ref<ReviewQueueItem | null>(null);
const sponsorTried = ref(false);
const sponsorFieldErrors = ref<Record<string, string>>({});
const sponsorAttach = useReportSanctionAttach({
    context: () => props.sponsorSanction,
    allowed: () => props.sponsorSanction !== null,
    only: () => false,
    note: () => note.value,
    fieldErrors: () => sponsorFieldErrors.value,
    tried: () => sponsorTried.value,
});
const sponsorConfirmLabel = computed(() => {
    const kind = sponsorAttach.step.value.kind;
    return sponsorAttach.on.value && kind ? t('report.actWithSanction', { act: t('trial.queue.sponsorConfirm'), kind: UtilSanctionMessages.kindName(t, kind) }) : t('trial.queue.sponsorConfirm');
});
function openSponsor(row: ReviewQueueItem): void {
    sponsorTarget.value = row;
    note.value = '';
    dialogError.value = '';
    sponsorTried.value = false;
    sponsorFieldErrors.value = {};
    props.actions.openSponsorAuthor(row);
    sponsorAttach.reset();
    sponsorOpen.value = true;
}
/** 제재 오류 — 그 사이 작성자 상태가 바뀌었다(대화상자 안에 서버 문구, 쓴 글은 그대로 — 신고 처리와 같은 코드) */
const SANCTION_STALE = new Set(['SANCTION_STEP_TOO_LOW', 'SANCTION_ALREADY_SUSPENDED', 'SANCTION_ALREADY_BANNED', 'SANCTION_TARGET_NOT_FOUND']);
async function submitSponsor(confirmIt: boolean): Promise<void> {
    const row = sponsorTarget.value;
    if (!row || busy.value) return;
    const text = note.value.trim();
    if (!confirmIt) {
        const done = await run(() => props.actions.dismissSponsor(row.id, text), t('trial.queue.sponsorDismissed'), true);
        if (done) sponsorOpen.value = false;
        return;
    }
    sponsorTried.value = true;
    const built = sponsorAttach.build();
    if (!built.valid) return;
    busy.value = true;
    dialogError.value = '';
    sponsorFieldErrors.value = {};
    try {
        const issued = await props.actions.confirmSponsor(row.id, { note: text, sanction: built.sanction });
        toast.success(issued ? t('trial.queue.sponsorConfirmedWithSanction', { sanction: UtilSanctionMessages.issued(t, issued) }) : t('trial.queue.sponsorConfirmed'));
        sponsorOpen.value = false;
        await props.actions.reload();
    } catch (e) {
        const error = errorOf(e);
        if (error.status === 400 && error.fields && Object.keys(error.fields).length) {
            // 칸 오류(처리 글·제재)는 그 칸 아래, 그 밖은 대화상자 안 한 줄 — 확인도 롤백됐다
            sponsorFieldErrors.value = { ...error.fields };
            dialogError.value = Object.entries(error.fields)
                .filter(([k]) => k !== 'note' && k !== 'sanction' && !k.startsWith('sanction.'))
                .map(([, v]) => v)
                .join(' ');
        } else if (SANCTION_STALE.has(error.code)) {
            dialogError.value = error.message;
            props.actions.retrySponsorAuthor();
        } else if (error.status === 409 || error.status === 404) {
            toast.error(error.message, 8000);
            sponsorOpen.value = false;
            await props.actions.reload();
        } else {
            // 403(제재 권한 없음 — 확인도 롤백) 등은 대화상자 안 한 줄
            dialogError.value = error.message;
        }
    } finally {
        busy.value = false;
    }
}
</script>
