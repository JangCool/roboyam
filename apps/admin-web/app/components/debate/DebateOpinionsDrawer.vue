<template>
    <!--
        찬반 토론 의견(찬성·반대·중립) 관리 서랍 — **글 번호**(`target.postId`)로 그 글의 입장 배지가 붙은 원댓글을 입장 탭으로 보고(고정 먼저 → 추천 많은 순 → 최근), 고정하거나 숨긴다.
        토론은 게시글 기능이라(post-debate-contract) 하이라이트 화면과 (다음 작업) 게시글 관리가 이 서랍을 같이 쓴다. 서버는 `/api/admin/debates/{postId}/opinions…`·`/pin-requests…`.
        중립 탭은 서버 안내대로 `neutralEnabled || neutral.total > 0` 일 때만(중립을 끈 뒤에도 쌓인 중립 의견은 다룰 수 있게).
        숨김은 신고 숨김과 같은 칸(까닭 STAFF)이라 공개 댓글 목록에 「숨긴 댓글」 자리만 남고 글의 댓글 수가 1 줄어든다. 숨김 풀기는 **운영자 숨김만** 풀린다(신고로 숨긴 의견은 여기서 풀지 않는다).
        고정은 이 목록과 공개 토론 의견 목록의 맨 위를 정한다. 직접 고정하면 그 의견의 대기 고정 요청은 서버가 승인으로 닫는다(요청 탭도 다시 읽는다). 글쓴이에게 알리지 않는다.
        「고정 요청」 탭: 의견을 쓴 회원이 보낸 대기 요청(오래된 것부터) — 줄마다 입장 배지·글쓴이·요청 시각·의견 미리보기 + [고정](승인) · [거절](그 자리에 사유 0~100자 선택 입력을 펼친다).
          처리 뒤 요청 목록·의견 목록을 다시 읽고 `changed` 로 부른 화면의 목록 수도 다시 읽게 한다. `404 PIN_REQUEST_NOT_FOUND`·`409 PIN_REQUEST_DECIDED`·`409 OPINION_UNAVAILABLE` 은 서버 문구 토스트 + 다시 읽기, `400 fields.reason` 은 사유 칸 아래.
          요청 목록 읽기는 `post.read`(`canRead`)라 보기 권한이면 이 탭이 보이고, [고정]·[거절] 단추는 처리 권한(`post.manage`)일 때만 보인다.
        서버 호출은 페이지가 준 `actions`(`useDebateOpinionActions`)로만 한다. 바꾸면 차례가 바뀔 수 있어 그 쪽을 다시 읽는다.
        처리 권한(`post.manage`)이 없으면 단추 없이 읽기만 한다.
        입장 색은 공개 사이트와 같은 토큰(`main.css` 「토론 입장 색」): 탭 글자 `stance-*-text`, 줄의 배지 `stance-*-soft` — 찬성 파랑 · 반대 빨강 · 중립 초록, 늘 글자와 함께.
    -->
    <Drawer v-model:visible="open" position="right" :header="t('debate.opinionsTitle')" :style="{ width: '44rem', maxWidth: '100vw' }">
        <div v-if="target" class="flex flex-col gap-3">
            <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                <p class="m-0 text-sm font-medium break-words">{{ target.title }}</p>
                <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                    <Tag :value="debateOpen ? t('debate.debateOpen') : t('debate.debateClosed')" :severity="debateOpen ? 'success' : 'secondary'" class="text-[11px]!" />
                    <span class="tabular-nums">#{{ target.postId }}</span>
                    <span>{{ t('debate.opinionsLead') }}</span>
                </div>
            </div>

            <p v-if="!canManage" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('debate.opinionsReadOnly') }}</p>

            <SelectButton v-model="tab" :options="tabOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('debate.opinionsTabs')" class="self-start" @update:model-value="onTab">
                <template #option="{ option }">
                    <span class="font-semibold tabular-nums" :class="option.value === REQUESTS ? '' : UtilDebate.stanceTextClass(option.value)">{{ option.label }}</span>
                </template>
            </SelectButton>

            <!-- 고정 요청 탭 -->
            <template v-if="tab === REQUESTS">
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('debate.pinRequestsLead') }}</p>
                <div v-if="requestsLoading && !requests" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="4rem" />
                </div>
                <ErrorState v-else-if="requestsError && !requests" :title="t('debate.pinRequestsLoadFailed')" :description="requestsError" retry :retrying="requestsLoading" @retry="loadRequests" />
                <template v-else-if="requests">
                    <EmptyState v-if="requests.items.length === 0" icon="pi pi-thumbtack" :title="t('debate.pinRequestsEmpty')" />
                    <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="requestsLoading">
                        <li v-for="r in requests.items" :key="r.id" class="flex flex-col gap-1.5 py-2.5">
                            <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                                <span v-if="r.opinion" class="inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[11px] font-semibold leading-tight" :class="UtilDebate.stanceSoftClass(r.opinion.stance)">{{ t(`debate.stance.${r.opinion.stance}`) }}</span>
                                <span class="font-medium text-color">{{ r.opinion ? r.opinion.authorName : t('debate.opinionGone') }}</span>
                                <span aria-hidden="true">·</span>
                                <time :datetime="r.createdAt" :title="time.full(r.createdAt)">{{ t('debate.pinRequestedAt', { at: time.listLabel(r.createdAt) }) }}</time>
                                <template v-if="r.opinion">
                                    <span aria-hidden="true">·</span>
                                    <span class="tabular-nums">{{ t('debate.opinionStats', { likes: r.opinion.likeCount, dislikes: r.opinion.dislikeCount, replies: r.opinion.replyCount }) }}</span>
                                </template>
                            </div>
                            <p v-if="r.opinion" class="m-0 text-sm leading-relaxed break-words whitespace-pre-line">{{ r.opinion.bodyPreview }}</p>

                            <!-- 거절 사유 — 그 자리에 펼친다 -->
                            <form v-if="rejecting === r.id" class="flex flex-col gap-2 rounded-lg border border-(--line) p-3" novalidate @submit.prevent="reject(r)">
                                <LabeledField :label="t('debate.rejectReasonLabel')" :hint="t('debate.counter', { n: UtilDebate.length(reason), max: UtilDebate.REJECT_REASON_MAX })" :help="t('debate.rejectReasonHelp')" :error="reasonErrorText">
                                    <template #default="{ id, describedBy, invalid }">
                                        <Textarea :id="id" v-model="reason" name="reason" rows="2" auto-resize fluid :disabled="busy !== ''" :invalid="invalid" :aria-describedby="describedBy" />
                                    </template>
                                </LabeledField>
                                <div class="flex flex-wrap justify-end gap-1">
                                    <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" :disabled="busy !== ''" @click="cancelReject" />
                                    <Button type="submit" :label="t('debate.rejectConfirm')" size="small" severity="danger" :loading="busy === `reject:${r.id}`" :disabled="(busy !== '' && busy !== `reject:${r.id}`) || UtilDebate.rejectReasonTooLong(reason)" />
                                </div>
                            </form>
                            <div v-else-if="canManage" class="flex flex-wrap gap-1">
                                <Button
                                    type="button"
                                    :label="t('debate.pin')"
                                    icon="pi pi-thumbtack"
                                    size="small"
                                    severity="secondary"
                                    variant="outlined"
                                    :loading="busy === `approve:${r.id}`"
                                    :disabled="busy !== '' && busy !== `approve:${r.id}`"
                                    :aria-label="t('debate.pinRequestApproveOf', { name: r.opinion?.authorName ?? '' })"
                                    @click="approve(r)"
                                />
                                <Button type="button" :label="t('debate.pinRequestReject')" icon="pi pi-times" size="small" severity="danger" variant="text" :disabled="busy !== ''" :aria-label="t('debate.pinRequestRejectOf', { name: r.opinion?.authorName ?? '' })" @click="startReject(r)" />
                            </div>
                        </li>
                    </ul>
                </template>
            </template>

            <!-- 입장 탭 -->
            <template v-else>
                <div v-if="loading && !data" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 4" :key="n" height="4rem" />
                </div>
                <ErrorState v-else-if="loadError && !data" :title="t('debate.opinionsLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
                <template v-else-if="data">
                    <EmptyState v-if="page.items.length === 0" icon="pi pi-comments" :title="t('debate.opinionsEmpty')" />
                    <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-busy="loading">
                        <li v-for="op in page.items" :key="op.commentId" class="flex flex-col gap-1.5 py-2.5" :class="op.hidden ? 'opacity-70' : ''">
                            <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                                <span class="inline-flex shrink-0 items-center rounded px-1.5 py-0.5 text-[11px] font-semibold leading-tight" :class="UtilDebate.stanceSoftClass(op.stance)">{{ t(`debate.stance.${op.stance}`) }}</span>
                                <Tag v-if="op.pinned" :value="t('debate.pinnedTag')" icon="pi pi-thumbtack" severity="info" class="text-[11px]!" />
                                <Tag v-if="op.pinRequestId" :value="t('debate.pinRequestPendingTag')" icon="pi pi-clock" severity="secondary" class="text-[11px]!" />
                                <Tag v-if="op.hidden" :value="t(`debate.hiddenReason.${op.hiddenReason ?? 'STAFF'}`)" icon="pi pi-eye-slash" severity="warn" class="text-[11px]!" />
                                <span class="font-medium text-color">{{ op.authorKind === 'GUEST' ? t('content.guestAuthor', { name: op.authorName }) : op.authorName }}</span>
                                <span aria-hidden="true">·</span>
                                <time :datetime="op.createdAt" :title="time.full(op.createdAt)">{{ time.listLabel(op.createdAt) }}</time>
                                <span aria-hidden="true">·</span>
                                <span class="tabular-nums">{{ t('debate.opinionStats', { likes: op.likeCount, dislikes: op.dislikeCount, replies: op.replyCount }) }}</span>
                            </div>
                            <p class="m-0 text-sm leading-relaxed break-words whitespace-pre-line">{{ op.bodyPreview }}</p>
                            <div v-if="canManage" class="flex flex-wrap gap-1">
                                <Button
                                    type="button"
                                    :label="op.pinned ? t('debate.unpin') : t('debate.pin')"
                                    icon="pi pi-thumbtack"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    :loading="busy === `pin:${op.commentId}`"
                                    :disabled="busy !== '' && busy !== `pin:${op.commentId}`"
                                    @click="togglePin(op)"
                                />
                                <Button
                                    v-if="!op.hidden"
                                    type="button"
                                    :label="t('debate.hideOpinion')"
                                    icon="pi pi-eye-slash"
                                    size="small"
                                    severity="danger"
                                    variant="text"
                                    :loading="busy === `hide:${op.commentId}`"
                                    :disabled="busy !== '' && busy !== `hide:${op.commentId}`"
                                    @click="toggleHide(op)"
                                />
                                <Button
                                    v-else-if="op.hiddenReason === 'STAFF'"
                                    type="button"
                                    :label="t('debate.unhideOpinion')"
                                    icon="pi pi-eye"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    :loading="busy === `hide:${op.commentId}`"
                                    :disabled="busy !== '' && busy !== `hide:${op.commentId}`"
                                    @click="toggleHide(op)"
                                />
                                <span v-else class="self-center text-xs text-muted-color">{{ t('debate.reportHiddenNote') }}</span>
                            </div>
                        </li>
                    </ul>

                    <div v-if="page.total > page.size || pageNo > 1" class="flex items-center justify-between gap-2 text-xs text-muted-color">
                        <span class="tabular-nums">{{ t('debate.opinionsPage', { page: pageNo, total: page.total }) }}</span>
                        <div class="flex gap-1">
                            <Button type="button" icon="pi pi-angle-left" :aria-label="t('common.prev')" size="small" severity="secondary" variant="text" :disabled="pageNo <= 1 || loading" @click="go(pageNo - 1)" />
                            <Button type="button" icon="pi pi-angle-right" :aria-label="t('common.next')" size="small" severity="secondary" variant="text" :disabled="!page.hasNext || loading" @click="go(pageNo + 1)" />
                        </div>
                    </div>
                </template>
            </template>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { DebateOpinion, DebateOpinionListView, DebateOpinionPage, DebatePinRequest, DebatePinRequestListView, DebateStance } from '~/composables/content/api/useDebateApi.types';
import type { DebateDrawerTarget, DebateOpinionActions } from '~/components/debate/DebateOpinionsDrawer.types';
import UtilDebate from '~/utils/util-debate';

interface Props {
    /** 의견을 볼 글 — 없으면 비어 있다 */
    target?: DebateDrawerTarget | null;
    actions: DebateOpinionActions;
    /** 보기 권한(`post.read`) — 「고정 요청」 탭 */
    canRead?: boolean;
    /** 처리 권한(`post.manage`) — 의견 고정·숨김, 요청 [고정]·[거절] */
    canManage?: boolean;
}

const props = withDefaults(defineProps<Props>(), { target: null, canRead: false, canManage: false });
const emit = defineEmits<{ changed: [] }>();
const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();

/** 「고정 요청」 탭 값 */
const REQUESTS = 'REQUESTS' as const;
type Tab = DebateStance | typeof REQUESTS;
/** 이미 처리됐거나 사라진 요청·의견 — 서버 문구를 보이고 다시 읽는다 */
const STALE_REQUEST_CODES: ReadonlySet<string> = new Set(['PIN_REQUEST_NOT_FOUND', 'PIN_REQUEST_DECIDED', 'OPINION_UNAVAILABLE', 'DEBATE_OPINION_NOT_FOUND']);

const tab = ref<Tab>('PRO');
/** 의견 목록을 읽을 입장 — 요청 탭에 있는 동안은 마지막 입장 */
const stance = ref<DebateStance>('PRO');
const pageNo = ref(1);
const data = ref<DebateOpinionListView | null>(null);
const loading = ref(false);
const loadError = ref('');
const busy = ref('');

const EMPTY_PAGE: DebateOpinionPage = { total: 0, page: 1, size: UtilDebate.OPINION_PAGE_SIZE, hasNext: false, items: [] };
const page = computed<DebateOpinionPage>(() => (data.value ? UtilDebate.pageOf(data.value, stance.value) : EMPTY_PAGE));

/** 지금 토론이 열려 있다 — 받은 목록의 `debate`(옛 응답은 `open`) */
const debateOpen = computed(() => Boolean(data.value?.debate ?? data.value?.open));

/** 중립 탭 — 받은 목록이 있으면 그 값, 없으면 부른 화면의 줄 값으로 */
const showNeutral = computed(() => (data.value ? UtilDebate.showNeutralTab(data.value) : props.target ? props.target.neutralEnabled || (props.target.counts?.neutral ?? 0) > 0 : false));

// ⚠ `computed` 다 — 언어를 바꾸면 다시 계산된다. 수는 받은 목록의 것(받기 전에는 부른 화면 줄의 의견 수)
const tabOptions = computed<{ value: Tab; label: string }[]>(() => {
    const stances = UtilDebate.STANCES.filter((s) => s !== 'NEUTRAL' || showNeutral.value);
    const options: { value: Tab; label: string }[] = stances.map((value) => ({
        value,
        label: t(`debate.stanceTab.${value}`, { n: data.value ? UtilDebate.pageOf(data.value, value).total : (props.target?.counts?.[UtilDebate.countKey(value)] ?? 0) }),
    }));
    if (props.canRead) options.push({ value: REQUESTS, label: t('debate.pinRequestsTab', { n: requests.value?.items.length ?? 0 }) });
    return options;
});

let request = 0;
async function load(): Promise<void> {
    const postId = props.target?.postId;
    if (!postId) return;
    const mine = ++request;
    loading.value = true;
    loadError.value = '';
    try {
        const found = await props.actions.load(postId, { stance: stance.value, page: pageNo.value, size: UtilDebate.OPINION_PAGE_SIZE });
        if (mine !== request) return;
        data.value = found;
    } catch (e) {
        if (mine !== request) return;
        loadError.value = e instanceof ApiError ? e.message : t('debate.opinionsLoadFailed');
        if (data.value) toast.error(loadError.value, 8000);
    } finally {
        if (mine === request) loading.value = false;
    }
}

function onTab(next: Tab): void {
    if (next === REQUESTS) {
        void loadRequests();
        return;
    }
    stance.value = next;
    pageNo.value = 1;
    void load();
}

function go(next: number): void {
    pageNo.value = next;
    void load();
}

/** 처리 실패 — 서버 문구 토스트. 이미 바뀐 요청·의견이면 다시 읽고, 글이 사라졌으면 부른 화면이 목록을 다시 읽게 한다 */
function failed(e: unknown): void {
    toast.error(e instanceof ApiError ? e.message : t('debate.opinionActionFailed'), 8000);
    if (!(e instanceof ApiError)) return;
    if (STALE_REQUEST_CODES.has(e.code)) void refreshAll();
    if (e.code === 'POST_NOT_FOUND' || e.code === 'DEBATE_NOT_FOUND') emit('changed');
}

async function act(op: DebateOpinion, kind: 'pin' | 'hide'): Promise<void> {
    const postId = props.target?.postId;
    if (busy.value || !postId) return;
    busy.value = `${kind}:${op.commentId}`;
    try {
        if (kind === 'pin') await props.actions.pin(postId, op.commentId, !op.pinned);
        else await props.actions.hide(postId, op.commentId, !op.hidden);
        toast.success(t(`debate.opinionDone.${kind}${kind === 'pin' ? (op.pinned ? 'Off' : 'On') : op.hidden ? 'Off' : 'On'}`));
        emit('changed');
        // 직접 고정하면 그 의견의 대기 요청이 승인으로 닫힌다 — 요청 목록도 다시 읽는다
        await refreshAll();
    } catch (e) {
        failed(e);
    } finally {
        busy.value = '';
    }
}

const togglePin = (op: DebateOpinion) => act(op, 'pin');
const toggleHide = (op: DebateOpinion) => act(op, 'hide');

/* ── 고정 요청 ── */
const requests = ref<DebatePinRequestListView | null>(null);
const requestsLoading = ref(false);
const requestsError = ref('');
/** 거절 사유를 펼친 요청 ID */
const rejecting = ref<string | null>(null);
const reason = ref('');
/** 서버가 거절한 사유(`fields.reason`) — 고치면 지운다 */
const reasonServerError = ref('');
const reasonErrorText = computed(() => (UtilDebate.rejectReasonTooLong(reason.value) ? t('debate.tooLong', { max: UtilDebate.REJECT_REASON_MAX }) : reasonServerError.value));
watch(reason, () => (reasonServerError.value = ''));

let requestSeq = 0;
async function loadRequests(): Promise<void> {
    const postId = props.target?.postId;
    if (!postId || !props.canRead) return;
    const mine = ++requestSeq;
    requestsLoading.value = true;
    requestsError.value = '';
    try {
        const found = await props.actions.pinRequests(postId);
        if (mine !== requestSeq) return;
        requests.value = found;
        // 펼쳐 둔 요청이 목록에서 빠졌으면 접는다
        if (rejecting.value && !found.items.some((r) => r.id === rejecting.value)) cancelReject();
    } catch (e) {
        if (mine !== requestSeq) return;
        requestsError.value = e instanceof ApiError ? e.message : t('debate.pinRequestsLoadFailed');
        if (requests.value) toast.error(requestsError.value, 8000);
    } finally {
        if (mine === requestSeq) requestsLoading.value = false;
    }
}

/** 요청 처리·의견 처리 뒤 — 요청 목록과 지금 입장의 의견 쪽을 함께 다시 읽는다 */
async function refreshAll(): Promise<void> {
    await Promise.all([loadRequests(), load()]);
}

async function approve(r: DebatePinRequest): Promise<void> {
    const postId = props.target?.postId;
    if (busy.value || !postId) return;
    busy.value = `approve:${r.id}`;
    try {
        await props.actions.approvePinRequest(postId, r.id);
        toast.success(t('debate.pinRequestDone.approve'));
        emit('changed');
        await refreshAll();
    } catch (e) {
        failed(e);
    } finally {
        busy.value = '';
    }
}

function startReject(r: DebatePinRequest): void {
    rejecting.value = r.id;
    reason.value = '';
    reasonServerError.value = '';
}

function cancelReject(): void {
    rejecting.value = null;
    reason.value = '';
    reasonServerError.value = '';
}

async function reject(r: DebatePinRequest): Promise<void> {
    const postId = props.target?.postId;
    if (busy.value || !postId || UtilDebate.rejectReasonTooLong(reason.value)) return;
    busy.value = `reject:${r.id}`;
    try {
        await props.actions.rejectPinRequest(postId, r.id, UtilDebate.rejectReasonOf(reason.value));
        toast.success(t('debate.pinRequestDone.reject'));
        cancelReject();
        emit('changed');
        await refreshAll();
    } catch (e) {
        // 사유 칸 오류는 칸 아래에(서버 문구 그대로)
        if (e instanceof ApiError && e.fields?.reason) reasonServerError.value = e.fields.reason;
        else failed(e);
    } finally {
        busy.value = '';
    }
}

// 열 때마다 찬성 첫 쪽부터 + 대기 요청(탭 머리의 수)
watch(
    () => [open.value, props.target?.postId] as const,
    ([visible]) => {
        if (!visible || !props.target) return;
        tab.value = 'PRO';
        stance.value = 'PRO';
        pageNo.value = 1;
        data.value = null;
        requests.value = null;
        requestsError.value = '';
        cancelReject();
        void load();
        void loadRequests();
    },
    { immediate: true },
);
</script>
