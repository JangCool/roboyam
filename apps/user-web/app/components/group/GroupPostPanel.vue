<template>
    <!--
        소모임 글 상세의 공개 범위 영역(5단계 — community `PostGroupPanel`, `docs/api-groups.md` 11-6·11-7) — 안내는 보는 사람 모두에게, 단추는 서버가 준 권한(`group.can*`)이 있는 사람에게만.
        모습은 `UtilPostGroupPanel.stateOf` 가 정한다: LEGACY(기능이 꺼진 서버의 옛 패널) · PRIVATE · REQUESTING · REJECTED · GRANTED · GROUP_PUBLIC · MEMBERS_ONLY.
          - 비공개 소모임의 비공개 글: 작성자 [공개 요청하기](한마디·확인 시트) → 요청 중([요청 취소]) → 거절됨(사유·재요청 시간) / 처리자 [공개하기]·[거절]·[직접 공개하기]
          - 개별 공개 글: [비공개로 되돌리기] / 공개 소모임의 글: 처리자만 [멤버 전용으로 돌리기]·[다시 공개하기]
        요청은 페이지가 내려 준 `actions` 로 보낸다. 응답이 값을 다 주지 않으므로 바뀐 칸을 `change` 로 올리고 페이지가 글 상세의 `group` 에 덮어쓴다(글을 다시 받지 않는다).
        요청 중에는 모든 단추를 막는다(`busy`). 실패는 코드 갈래로 — 이미 요청 중이면 요청 중으로, 이미 처리됨·취소됨이면 처리 칸을 거두고, 맡은 일이 빠졌으면 처리 단추를 거둔다.
        쓰기 자격(이메일 미인증·이용 제한·사업자 계정)이 없으면 단추를 끄고 까닭을 말한다.
    -->
    <section class="mt-4 rounded-lg border border-surface px-4 py-3" :aria-label="t('group.postScopeLabel')" data-testid="post-group-panel" :data-panel="state.kind">
        <p class="m-0 flex items-start gap-2 text-sm leading-relaxed" data-testid="post-group-notice">
            <i :class="[notice.icon, 'mt-1 shrink-0 text-muted-color']" aria-hidden="true" />
            <span class="min-w-0">
                <b v-if="notice.title">{{ notice.title }}</b>
                {{ notice.text }}
            </span>
        </p>

        <!-- ── 옛 패널(기능이 꺼진 서버의 비공개 소모임) — 작성자의 「전체 공개로 전환」 · 「비공개로 되돌리기」 ── -->
        <div v-if="state.kind === 'LEGACY' && (state.canPublishDirect || state.canUnpublish)" class="mt-3">
            <Button v-if="state.canPublishDirect" type="button" :label="t('group.publishLabel')" fluid :disabled="busy" data-testid="group-publish" @click="openSheet('legacy')" />
            <Button v-else type="button" :label="t('group.unpublishLabel')" severity="secondary" variant="outlined" fluid :loading="busy" :disabled="busy" data-testid="group-unpublish" @click="unpublish" />
            <p class="m-0 mt-2 text-center text-xs text-muted-color">{{ state.canPublishDirect ? t('group.publishHint') : t('group.unpublishHint') }}</p>
        </div>

        <!-- ── 공개 요청 중 ── -->
        <div v-if="state.kind === 'REQUESTING'" class="mt-3" data-testid="group-requesting">
            <!-- 처리자 — 누가 요청했는지와 한마디, [공개하기]·[거절] -->
            <template v-if="state.canDecide">
                <p class="m-0 flex items-start gap-2 text-sm leading-relaxed" data-testid="group-request-by">
                    <i class="pi pi-send mt-1 shrink-0 text-muted-color" aria-hidden="true" />
                    <span class="min-w-0">
                        <b>{{ t('groupVisibility.requestedBy', { name: authorName ?? t('group.withdrawn') }) }}</b>
                        <template v-if="note"> · <span class="whitespace-pre-line break-words" data-testid="group-request-note">{{ note }}</span></template>
                    </span>
                </p>
                <div class="mt-3 flex gap-2">
                    <Button type="button" :label="t('groupVisibility.publish')" class="flex-1" :disabled="busy || Boolean(blocked)" data-testid="group-approve" @click="openSheet('approve')" />
                    <Button type="button" :label="t('groupVisibility.reject')" severity="secondary" variant="outlined" class="flex-1" :disabled="busy || Boolean(blocked)" data-testid="group-reject" @click="openReject" />
                </div>
            </template>
            <!-- 작성자 — 요청 중 + 요청 시각, [요청 취소] -->
            <template v-else-if="state.canCancel">
                <p class="m-0 flex items-start gap-2 text-sm leading-relaxed" data-testid="group-request-pending">
                    <i class="pi pi-clock mt-1 shrink-0 text-muted-color" aria-hidden="true" />
                    <span class="min-w-0">
                        <b>{{ t('groupVisibility.requestingTitle') }}</b> · {{ t('groupVisibility.requestingText') }}
                        <small v-if="requestedAtText" class="block text-xs text-muted-color" data-testid="group-request-time">{{ requestedAtText }}</small>
                    </span>
                </p>
                <Button
                    type="button"
                    :label="t('groupVisibility.requestCancel')"
                    severity="secondary"
                    variant="outlined"
                    fluid
                    class="mt-3"
                    :loading="busy"
                    :disabled="busy || Boolean(blocked)"
                    data-testid="group-request-cancel"
                    @click="cancelRequest"
                />
            </template>
        </div>

        <!-- ── 거절됨 ── -->
        <div v-if="state.kind === 'REJECTED'" class="mt-3" data-testid="group-rejected">
            <p class="m-0 flex items-start gap-2 text-sm leading-relaxed">
                <i class="pi pi-times-circle mt-1 shrink-0 text-muted-color" aria-hidden="true" />
                <span class="min-w-0">
                    <b>{{ t('groupVisibility.rejectedTitle') }}</b>
                    <small v-if="rejectReason" class="block break-words text-xs text-muted-color" data-testid="group-reject-reason">{{ t('groupVisibility.rejectReason', { reason: rejectReason }) }}</small>
                    <small v-if="mine && state.retry === 'wait' && state.retryHours !== null" class="block text-xs text-muted-color" data-testid="group-retry-wait">{{ t('groupVisibility.retryIn', { n: state.retryHours }) }}</small>
                    <small v-else-if="mine && state.retry === 'limit'" class="block text-xs text-muted-color" data-testid="group-retry-limit">{{ t('groupVisibility.rejectLimitText') }}</small>
                </span>
            </p>
        </div>

        <!-- ── 공개 요청하기(작성자) · 직접 공개하기(처리자) — 요청 전·거절 뒤. 처리자 본인의 글은 요청 없이 직접 공개만 있다 ── -->
        <div v-if="(state.kind === 'PRIVATE' || state.kind === 'REJECTED') && (state.canRequest || state.canPublishDirect)" class="mt-3">
            <Button v-if="state.canRequest" type="button" :label="t('groupVisibility.requestLabel')" fluid :disabled="busy || Boolean(blocked)" data-testid="group-request" @click="openRequest" />
            <Button v-else type="button" :label="t('groupVisibility.directLabel')" fluid :disabled="busy || Boolean(blocked)" data-testid="group-direct" @click="openSheet('direct')" />
            <p class="m-0 mt-2 text-center text-xs text-muted-color">{{ state.canRequest ? t('groupVisibility.requestHint') : t('groupVisibility.directHint') }}</p>
        </div>

        <!-- ── 개별 공개 글 — 공개 전 댓글 안내 + 되돌리기 ── -->
        <div v-if="state.kind === 'GRANTED' && (beforePublicComments || state.canUnpublish)" class="mt-3" data-testid="group-granted">
            <p v-if="beforePublicComments" class="m-0 text-xs text-muted-color" data-testid="group-before-public-comments">{{ t('groupVisibility.beforePublicComments') }}</p>
            <template v-if="state.canUnpublish">
                <Button type="button" :label="t('group.unpublishLabel')" severity="secondary" variant="outlined" fluid class="mt-3" :loading="busy" :disabled="busy" data-testid="group-unpublish" @click="unpublish" />
                <p class="m-0 mt-2 text-center text-xs text-muted-color">{{ t('group.unpublishHint') }}</p>
            </template>
        </div>

        <!-- ── 공개 소모임의 글 — 처리자만 [멤버 전용으로 돌리기] ── -->
        <div v-if="state.kind === 'GROUP_PUBLIC' && state.canMakeMembersOnly" class="mt-3" data-testid="group-public-actions">
            <Button
                type="button"
                :label="t('groupVisibility.makeMembersOnly')"
                severity="secondary"
                variant="outlined"
                fluid
                :loading="busy"
                :disabled="busy || Boolean(blocked)"
                data-testid="group-members-only"
                @click="makeMembersOnly"
            />
            <p class="m-0 mt-2 text-center text-xs text-muted-color">{{ t('groupVisibility.makeMembersOnlyHint') }}</p>
        </div>

        <!-- ── 멤버 전용으로 돌려 둔 글 — 처리자만 [다시 공개하기] ── -->
        <div v-if="state.kind === 'MEMBERS_ONLY'" class="mt-3" data-testid="group-members-only-state">
            <p class="m-0 text-xs text-muted-color">{{ state.canRelease ? t('groupVisibility.membersOnlyProcessorHint') : t('groupVisibility.membersOnlyHint') }}</p>
            <Button v-if="state.canRelease" type="button" :label="t('groupVisibility.release')" fluid class="mt-3" :disabled="busy || Boolean(blocked)" data-testid="group-release" @click="openSheet('release')" />
        </div>

        <!-- 쓰기 자격이 없다 — 단추가 꺼지고 까닭을 말한다 -->
        <p v-if="blocked && hasWriteAction" class="m-0 mt-2 text-center text-xs text-muted-color" role="status" data-testid="group-blocked">
            {{ UtilGroupVisibilityMessages.blockedText(t, blocked, processor) }}
        </p>

        <GroupPublishSheet v-model:visible="publishOpen" :copy="sheetCopy" :ack-help="sheetMode === 'legacy' ? t('group.publishAckHelp') : ''" :busy="busy" :error="publishError" @confirm="confirmPublish" />
        <GroupPublishRejectSheet v-model:visible="rejectOpen" :max="textMax" :busy="busy" :error="rejectError" @submit="sendReject" />

        <!-- 공개 요청 시트 — 한마디(선택, 서버 한도)와 「닉네임과 프로필이 함께 공개되는 것을 확인했어요」 체크(필수). 열 때마다 빈 입력·체크 해제로 시작한다 -->
        <AppSheet v-model:visible="requestOpen" :title="t('groupVisibility.requestSheetTitle')" :description="t('groupVisibility.requestSheetText')">
            <form class="px-5 pt-4" novalidate data-testid="group-request-sheet" @submit.prevent="sendRequest">
                <LabeledField :label="t('groupVisibility.noteLabel')" :hint="t('group.optional')" :error="noteError">
                    <template #aside>
                        <small class="text-xs tabular-nums" :class="noteTooLong ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: noteLength, max: textMax }) }}</small>
                    </template>
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <Textarea
                            :id="fieldId"
                            v-model="noteText"
                            name="note"
                            rows="3"
                            fluid
                            class="resize-none"
                            :placeholder="t('groupVisibility.notePlaceholder')"
                            :invalid="invalid"
                            :disabled="busy"
                            :aria-describedby="describedBy"
                            data-testid="group-request-note-input"
                        />
                    </template>
                </LabeledField>
                <div class="mt-3 flex items-start gap-2.5">
                    <Checkbox v-model="requestAck" binary :input-id="requestAckId" :disabled="busy" data-testid="group-request-ack" />
                    <label :for="requestAckId" class="cursor-pointer text-sm leading-snug">{{ t('groupVisibility.requestAck') }}</label>
                </div>
                <p v-if="requestError" role="alert" class="mt-3 text-sm text-red-600 dark:text-red-400" data-testid="group-request-error">{{ requestError }}</p>
                <div class="mt-5 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="busy" @click="requestOpen = false" />
                    <Button type="submit" :label="t('groupVisibility.requestSend')" class="flex-1" :loading="busy" :disabled="!canSendRequest" data-testid="group-request-send" />
                </div>
            </form>
        </AppSheet>
    </section>
</template>

<script setup lang="ts">
import type { PostGroupInfo } from '~/composables/board/api/usePostApi.types';
import type { GroupManageBlockedReason } from '~/composables/group/api/useGroupApi.types';
import type { GroupPostPanelActions } from '~/components/group/GroupPostPanel.types';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupVisibility from '~/utils/util-group-visibility';
import UtilGroupVisibilityMessages from '~/utils/util-group-visibility-messages';
import UtilPostGroupPanel, { type PanelAction, type PostGroupPatch } from '~/utils/util-post-group-panel';

interface Props {
    group: PostGroupInfo;
    /** 내 글인가(작성자) */
    mine?: boolean;
    /** 글쓴이 닉네임 — 처리 배너·직접 공개 시트의 알림 안내(탈퇴한 회원이면 null) */
    authorName?: string | null;
    actions: GroupPostPanelActions;
}

const props = withDefaults(defineProps<Props>(), {
    mine: false,
    authorName: null,
});

const emit = defineEmits<{
    /** 공개 범위·요청 상태가 바뀌었다 — 페이지가 글 상세의 `group` 에 덮어쓴다 */
    change: [patch: PostGroupPatch];
    /** 로그인이 끊겼다 — 페이지가 로그인 안내를 띄운다 */
    session: [];
}>();

const { t } = useI18n();
const { user, restriction } = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();

const busy = ref(false);
const now = ref(Date.now());

const state = computed(() => UtilPostGroupPanel.stateOf(props.group, props.mine, now.value));
const textMax = computed(() => UtilGroupVisibility.textMaxOf(props.group.publishTextMax));

/** 열어 둔 채 서버가 막은 쓰기 자격 — 다음에 글을 받을 때까지 단추를 끈다 */
const blockedLocal = ref<GroupManageBlockedReason | null>(null);
const blocked = computed(() => blockedLocal.value ?? UtilPostGroupPanel.blockedOf(user.value, restriction.value));
const hasWriteAction = computed(() => UtilPostGroupPanel.hasWriteAction(state.value));
const processor = computed(() => UtilPostGroupPanel.isProcessor(state.value));

const notice = computed(() => UtilGroupVisibilityMessages.panelNotice(t, state.value.kind, props.group.publicBy, props.group.visibility));
const note = computed(() => UtilPostGroupPanel.noteOf(props.group));
const rejectReason = computed(() => UtilPostGroupPanel.rejectReasonOf(props.group));
const requestedAtText = computed(() => {
    const at = props.group.publishRequest?.requestedAt;
    return at ? time.value.detail(at) : '';
});
const beforePublicComments = computed(() => UtilPostGroupPanel.showsBeforePublicComments(props.group));

/* ── 시트 ── */
type SheetMode = 'legacy' | 'approve' | 'direct' | 'release';
const sheetMode = ref<SheetMode>('legacy');
const publishOpen = ref(false);
const publishError = ref('');
const rejectOpen = ref(false);
const rejectError = ref('');

const sheetCopy = computed(() => {
    switch (sheetMode.value) {
        case 'approve':
            return UtilGroupVisibilityMessages.publishSheet(t);
        case 'direct':
            // 처리자 본인의 글이면 작성자 알림 안내가 없다
            return UtilGroupVisibilityMessages.directSheet(t, props.mine ? null : props.authorName);
        case 'release':
            return UtilGroupVisibilityMessages.releaseSheet(t);
        default:
            return UtilGroupVisibilityMessages.legacySheet(t);
    }
});

function openSheet(mode: SheetMode): void {
    sheetMode.value = mode;
    publishError.value = '';
    publishOpen.value = true;
}

function openReject(): void {
    rejectError.value = '';
    rejectOpen.value = true;
}

/* ── 요청 시트 ── */
const requestOpen = ref(false);
const requestError = ref('');
const noteText = ref('');
const requestAck = ref(false);
const requestAckId = useId();
const noteLength = computed(() => UtilGroupVisibility.length(noteText.value));
const noteTooLong = computed(() => UtilGroupVisibility.tooLong(noteText.value, textMax.value));
const noteError = computed(() => (noteTooLong.value ? t('groupVisibility.tooLong', { max: textMax.value }) : ''));
const canSendRequest = computed(() => requestAck.value && !noteTooLong.value && !busy.value);

function openRequest(): void {
    noteText.value = '';
    requestAck.value = false;
    requestError.value = '';
    requestOpen.value = true;
}

/* ── 결과 반영 ── */

function apply(action: PanelAction, extra: { requestedAt?: string; note?: string } = {}): void {
    now.value = Date.now();
    emit('change', UtilPostGroupPanel.afterAction(action, props.group, props.mine, extra));
}

/** 서버가 「이 일을 할 수 없다」고 했을 때 처리 단추를 거둔다 */
const REVOKED_PATCH: PostGroupPatch = { canDecidePublishRequest: false, canPublish: false, canMakeMembersOnly: false, canReleaseMembersOnly: false };

/**
 * 공통 실패 처리 — 상태가 바뀐 실패는 화면을 맞추고 토스트로 알린다(true — 시트를 닫는다).
 * 입력 오류·네트워크 오류처럼 시트 안에서 다시 시도할 수 있으면 false(부르는 쪽이 시트에 문구를 보인다).
 */
function handleFailure(e: unknown): boolean {
    const kind = UtilGroupVisibility.failureKind(e);
    const message = UtilGroupVisibilityMessages.failure(t, kind, e);
    switch (kind) {
        case 'session':
            emit('session');
            return true;
        case 'blocked':
            blockedLocal.value = UtilGroupVisibility.blockedOf(e);
            toast.error(message, 6000);
            return true;
        case 'exists':
            emit('change', { publishRequest: { status: 'PENDING', requestedAt: props.group.publishRequest?.requestedAt ?? '', note: null, rejectCount: props.group.publishRequest?.rejectCount ?? 0 }, canRequestPublish: false });
            toast.show(message, { life: 6000 });
            return true;
        case 'decided':
            emit('change', { publishRequest: null, canDecidePublishRequest: false });
            toast.show(message, { life: 6000 });
            return true;
        case 'request-gone':
            emit('change', { publishRequest: null, canDecidePublishRequest: false, canRequestPublish: props.mine });
            toast.show(message, { life: 6000 });
            return true;
        case 'retry-later':
        case 'limit':
        case 'rate-limited':
            emit('change', { canRequestPublish: false });
            toast.error(message, 6000);
            return true;
        case 'not-needed':
            // 모임장·매니저는 요청 없이 직접 공개한다 — 단추를 바꾼다
            emit('change', { canRequestPublish: false, canPublish: true });
            toast.show(message, { life: 6000 });
            return true;
        case 'already-public':
            emit('change', { visibility: 'PUBLIC', canRequestPublish: false, canPublish: false, canDecidePublishRequest: false });
            toast.show(message, { life: 6000 });
            return true;
        case 'members-only':
            emit('change', { canRequestPublish: false, canPublish: false });
            toast.error(message, 6000);
            return true;
        case 'required':
            emit('change', { canPublish: false });
            toast.error(message, 6000);
            return true;
        case 'by-group':
            emit('change', { canUnpublish: false, canMakeMembersOnly: false });
            toast.error(message, 6000);
            return true;
        case 'permission-denied':
        case 'leader-only':
        case 'forbidden':
            emit('change', REVOKED_PATCH);
            toast.error(message, 6000);
            return true;
        case 'not-member':
        case 'inactive':
        case 'disabled':
        case 'gone':
        case 'private':
            emit('change', { ...REVOKED_PATCH, canUnpublish: false, canRequestPublish: false });
            toast.error(message, 6000);
            return true;
        default:
            return false;
    }
}

/* ── 작성자 ── */

async function sendRequest(): Promise<void> {
    if (!canSendRequest.value) return;
    busy.value = true;
    requestError.value = '';
    const sent = noteText.value.trim();
    try {
        const res = await props.actions.request(sent);
        requestOpen.value = false;
        apply('requested', { requestedAt: res.requestedAt, note: sent });
        toast.success(t('groupVisibility.requestedToast'));
    } catch (e) {
        if (handleFailure(e)) {
            requestOpen.value = false;
            return;
        }
        requestError.value = UtilGroupVisibility.fieldOf(e, 'note') ?? UtilGroupVisibility.fieldOf(e, 'acknowledged') ?? UtilGroupVisibilityMessages.failure(t, 'other', e);
    } finally {
        busy.value = false;
    }
}

async function cancelRequest(): Promise<void> {
    if (busy.value) return;
    if (!(await confirm(UtilGroupVisibilityMessages.cancelConfirm(t)))) return;
    busy.value = true;
    try {
        await props.actions.cancel();
        apply('canceled');
        toast.success(t('groupVisibility.requestCanceledToast'));
    } catch (e) {
        if (!handleFailure(e)) toast.error(UtilGroupVisibilityMessages.failure(t, 'other', e), 6000);
    } finally {
        busy.value = false;
    }
}

/* ── 처리자·옛 길 — 한 시트가 공개하기(승인)·직접 공개하기·다시 공개하기·(옛) 전체 공개로 전환을 맡는다 ── */

async function confirmPublish(): Promise<void> {
    if (busy.value) return;
    busy.value = true;
    publishError.value = '';
    const mode = sheetMode.value;
    try {
        // 승인은 요청 처리 길, 나머지는 글 공개 범위 길이다
        if (mode === 'approve') await props.actions.approve();
        else await props.actions.setVisibility('PUBLIC', true);
        publishOpen.value = false;
        if (mode === 'release') {
            apply('released');
            toast.success(t('groupVisibility.releasedToast'));
        } else {
            apply(mode === 'approve' ? 'approved' : 'published');
            toast.success(mode === 'legacy' ? t('group.publishedToast') : t('groupVisibility.publishedToast'));
        }
    } catch (e) {
        if (handleFailure(e)) {
            publishOpen.value = false;
            return;
        }
        publishError.value = UtilGroupVisibility.fieldOf(e, 'acknowledged') ?? UtilGroupVisibilityMessages.failure(t, 'other', e);
    } finally {
        busy.value = false;
    }
}

async function sendReject(reason: string): Promise<void> {
    if (busy.value) return;
    busy.value = true;
    rejectError.value = '';
    try {
        const res = await props.actions.reject(reason);
        rejectOpen.value = false;
        const request = props.group.publishRequest;
        emit('change', {
            publishRequest: { status: 'REJECTED', requestedAt: request?.requestedAt ?? '', note: request?.note ?? null, rejectReason: reason || null, rejectCount: res.rejectCount },
            canDecidePublishRequest: false,
        });
        toast.success(t('groupVisibility.rejectedToast'));
    } catch (e) {
        if (handleFailure(e)) {
            rejectOpen.value = false;
            return;
        }
        rejectError.value = UtilGroupVisibility.fieldOf(e, 'reason') ?? UtilGroupVisibilityMessages.failure(t, 'other', e);
    } finally {
        busy.value = false;
    }
}

async function unpublish(): Promise<void> {
    if (busy.value) return;
    if (!(await confirm({ ...UtilGroupMessages.unpublishConfirm(t, props.mine), tone: 'danger' }))) return;
    busy.value = true;
    try {
        await props.actions.setVisibility('PRIVATE', false);
        apply('unpublished');
        toast.success(t('group.unpublishedToast'));
    } catch (e) {
        if (!handleFailure(e)) toast.error(UtilGroupVisibilityMessages.failure(t, 'other', e), 6000);
    } finally {
        busy.value = false;
    }
}

/** 공개 소모임의 글을 멤버 전용으로 돌린다 — 체크 없이 확인 한 번 */
async function makeMembersOnly(): Promise<void> {
    if (busy.value || !state.value.canMakeMembersOnly || blocked.value) return;
    if (!(await confirm({ ...UtilGroupVisibilityMessages.membersOnlyConfirm(t, props.mine), tone: 'danger' }))) return;
    busy.value = true;
    try {
        await props.actions.setVisibility('PRIVATE', false);
        apply('members-only');
        toast.success(t('groupVisibility.membersOnlyToast'));
    } catch (e) {
        if (!handleFailure(e)) toast.error(UtilGroupVisibilityMessages.failure(t, 'other', e), 6000);
    } finally {
        busy.value = false;
    }
}
</script>
