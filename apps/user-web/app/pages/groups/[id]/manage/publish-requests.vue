<template>
    <!--
        공개 요청 처리 `/groups/{id}/manage/publish-requests`(5단계 — community 같은 화면, `docs/api-groups.md` 11-5) — **모임장 또는 「글 공개 처리·되돌리기」(`UNPUBLISH`)를 맡은 매니저**
        (맡지 않은 매니저는 틀이 「모임장이 맡기지 않은 일이에요」). 이용 제한·이메일 인증 전이면 목록은 열리고 공개하기·거절이 꺼진다. 공개 범위 기능이 꺼진 서버면 안내 띠 + 단추가 꺼진다.
        - 목록: 오래된 요청이 위(처리 대기 순), `nextCursor` 를 그대로 `cursor` 로 보내는 「더보기」. 줄마다 닉네임(프로필)·요청 시각·칩(요청 뒤 수정됨·더는 멤버가 아니에요·이전에 n번 거절됨)·
          글 제목(→ 글 상세)·한마디·[거절]/[공개하기].
        - 공개하기: 개인정보 확인 체크가 있는 시트(작성자 알림이 간다). 거절: 사유(선택, 서버 한도 `publishTextMax`) 시트 — 적으면 작성자에게 전달된다. 성공하면 줄이 사라진다.
        - 동시 처리: 이미 처리됨(`409 DECIDED`)·작성자가 취소함(`404 REQUEST_NOT_FOUND`)·글이 사라짐(`404 POST_NOT_FOUND`)·이미 공개는 토스트 + 줄을 거둔다.
        - 열어 둔 채 맡은 일이 빠지면(`403 GROUP_MANAGER_PERMISSION_DENIED`) 토스트 + 위 띠 + 단추 꺼짐(목록은 남는다), 해임당하면 「모임장 또는 매니저만」 상태가 된다.
        - 한 번에 한 건만 처리한다(`busyId` — 다른 줄의 단추도 잠긴다).
    -->
    <GroupManageShell
        :title="UtilGroupVisibilityMessages.requestsTitle(t, listStatus === 'ready' ? total : 0)"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        permission="UNPUBLISH"
        @retry="load()"
    >
        <!-- 열어 둔 채 모임장이 이 일을 거두어 갔다 — 목록은 그대로 두고 단추만 끈다 -->
        <p v-if="revoked" class="mb-3 rounded-lg bg-amber-100 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status" data-testid="group-requests-revoked">
            {{ t('groupManager.revoked') }}
        </p>
        <!-- 공개 범위 기능이 꺼진 서버(처리 권한 칸이 false) — 목록은 비어 오고 단추가 꺼진다 -->
        <p v-else-if="disabled" class="mb-3 rounded-lg bg-surface-100 px-3 py-2.5 text-sm leading-relaxed text-muted-color dark:bg-surface-800" role="status" data-testid="group-requests-disabled">
            {{ t('groupVisibility.requestsDisabled') }}
        </p>

        <div v-if="listStatus === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')" data-testid="group-requests-loading">
            <div v-for="n in 4" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="listStatus === 'error'" :title="t('groupVisibility.requestsError')" :description="listError" retry data-testid="group-requests-error" @retry="loadList()" />

        <template v-else>
            <EmptyState v-if="items.length === 0" :title="t('groupVisibility.requestsEmptyTitle')" :description="t('groupVisibility.requestsEmptyText')" icon="pi pi-send" data-testid="group-requests-empty" />

            <template v-else>
                <ul class="m-0 list-none overflow-hidden panel-card p-0" data-testid="group-requests-list">
                    <li v-for="row in items" :key="row.post.id" class="border-b border-surface px-4 py-3 last:border-b-0" data-testid="group-publish-request">
                        <p class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
                            <PostAuthorLabel :author="UtilGroup.authorOf(row.author, t('group.withdrawn'))" link strong />
                            <time class="text-xs text-muted-color" :datetime="row.requestedAt" :title="time.full(row.requestedAt)" data-testid="group-publish-request-time">{{ time.ago(row.requestedAt) }}</time>
                            <GroupTag v-if="row.editedAfterRequest" tone="amber" data-testid="group-publish-request-edited">{{ t('groupVisibility.chipEdited') }}</GroupTag>
                            <GroupTag v-if="!row.authorStillMember" tone="gray" data-testid="group-publish-request-left">{{ t('groupVisibility.chipLeft') }}</GroupTag>
                            <GroupTag v-if="row.rejectCount > 0" tone="gray" data-testid="group-publish-request-rejected">{{ t('groupVisibility.chipRejected', { n: row.rejectCount }) }}</GroupTag>
                        </p>
                        <NuxtLink
                            :to="`/posts/${encodeURIComponent(row.post.id)}`"
                            class="mt-1 block break-words text-sm font-medium text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary [overflow-wrap:anywhere]"
                            data-testid="group-publish-request-title"
                            >{{ row.post.title }}</NuxtLink
                        >
                        <p
                            v-if="row.note?.trim()"
                            class="m-0 mt-1 whitespace-pre-line break-words text-xs text-muted-color [overflow-wrap:anywhere]"
                            :aria-label="t('groupVisibility.noteAria', { name: row.author.nickname })"
                            data-testid="group-publish-request-note"
                        >
                            {{ row.note.trim() }}
                        </p>
                        <div class="mt-2 flex justify-end gap-2">
                            <Button
                                type="button"
                                :label="t('groupVisibility.reject')"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :disabled="busyId !== null || !canAct"
                                :aria-label="t('groupVisibility.rejectAria', { name: row.author.nickname })"
                                data-testid="group-publish-request-reject"
                                @click="openReject(row)"
                            />
                            <Button
                                type="button"
                                :label="t('groupVisibility.publish')"
                                size="small"
                                :loading="busyAction === 'approve' && busyId === row.post.id"
                                :disabled="busyId !== null || !canAct"
                                :aria-label="t('groupVisibility.approveAria', { name: row.author.nickname })"
                                data-testid="group-publish-request-approve"
                                @click="openApprove(row)"
                            />
                        </div>
                    </li>
                </ul>
                <div v-if="hasNext" class="mt-3 text-center">
                    <Button type="button" :label="t('common.more')" variant="text" severity="secondary" :loading="loadingMore" data-testid="group-requests-more" @click="loadMore" />
                </div>
                <p v-if="moreError" role="alert" class="mt-2 px-1 text-xs text-red-600 dark:text-red-400" data-testid="group-requests-more-error">
                    {{ t('groupVisibility.requestsMoreError') }} · <button type="button" class="underline" @click="loadMore">{{ t('common.retry') }}</button>
                </p>
            </template>
        </template>

        <!-- 공개하기 확인 시트 — 개인정보 확인 체크가 있어야 켜진다 -->
        <GroupPublishSheet v-model:visible="approveOpen" :copy="UtilGroupVisibilityMessages.publishSheet(t)" :busy="busyAction === 'approve'" :error="approveError" @confirm="submitApprove" />
        <!-- 거절 시트 — 사유는 선택, 적으면 작성자에게 전달된다 -->
        <GroupPublishRejectSheet v-model:visible="rejectOpen" :max="textMax" :busy="busyAction === 'reject'" :error="rejectError" @submit="submitReject" />
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupPublishRequestRow } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupVisibility from '~/utils/util-group-visibility';
import UtilGroupVisibilityMessages from '~/utils/util-group-visibility-messages';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('groupVisibility.rowRequests'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { state, errorMessage, group, load, block, recheck } = useGroupManage(id, 'UNPUBLISH');

/** 지금 공개하기·거절을 할 수 있다 — 서버 기능이 켜져 있고(`canDecidePublishRequests`) 쓰기가 막히지 않았다 */
const canAct = computed(() => Boolean(group.value?.permissions.canDecidePublishRequests) && !group.value?.blockedReason);
/** 열어 둔 채 「글 공개 처리·되돌리기」를 거두어 갔다(모임장은 해당 없다) */
const revoked = computed(() => state.value === 'ready' && group.value !== null && !group.value.permissions.canUnpublishPosts);
/** 공개 범위 기능이 꺼진 서버 — 맡은 일은 있는데 처리 권한 칸이 false */
const disabled = computed(() => state.value === 'ready' && group.value !== null && group.value.permissions.canUnpublishPosts && !group.value.permissions.canDecidePublishRequests);

type ListStatus = 'loading' | 'ready' | 'error';
const listStatus = ref<ListStatus>('loading');
const listError = ref('');
const items = ref<GroupPublishRequestRow[]>([]);
const total = ref(0);
/** 거절 사유 글자 수 한도 — 서버가 준 값(관리자 설정), 못 받았으면 100 */
const textMax = ref(UtilGroupVisibility.TEXT_MAX);
const hasNext = ref(false);
/** 불투명 커서 — 만들거나 해석하지 않고 그대로 다음 요청에 보낸다 */
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
const moreError = ref(false);

let listSeq = 0;
async function loadList(): Promise<void> {
    const mine = ++listSeq;
    listStatus.value = 'loading';
    moreError.value = false;
    try {
        const res = await api.publishRequests(id, { size: UtilGroupVisibility.PAGE_SIZE });
        if (mine !== listSeq) return;
        items.value = res.items;
        total.value = res.total;
        textMax.value = UtilGroupVisibility.textMaxOf(res.publishTextMax);
        hasNext.value = res.hasNext;
        nextCursor.value = res.nextCursor;
        listStatus.value = 'ready';
    } catch (e) {
        if (mine !== listSeq || UtilGroup.isAborted(e) || UtilGroup.isSessionLost(e)) return;
        listError.value = UtilBoardMessages.of(t, e);
        listStatus.value = 'error';
    }
}
// 들어올 수 있다고 정해진 뒤에 받는다(권한이 없으면 틀이 안내를 보이고 목록은 받지 않는다)
watch(
    () => state.value === 'ready',
    (ready) => ready && void loadList(),
    { immediate: true },
);
onBeforeUnmount(() => listSeq++);

async function loadMore(): Promise<void> {
    if (!nextCursor.value || loadingMore.value) return;
    loadingMore.value = true;
    moreError.value = false;
    const mine = listSeq;
    try {
        const res = await api.publishRequests(id, { size: UtilGroupVisibility.PAGE_SIZE, cursor: nextCursor.value });
        if (mine !== listSeq) return;
        const have = new Set(items.value.map((r) => r.post.id));
        items.value = [...items.value, ...res.items.filter((r) => !have.has(r.post.id))];
        total.value = res.total;
        hasNext.value = res.hasNext;
        nextCursor.value = res.nextCursor;
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        moreError.value = true;
    } finally {
        loadingMore.value = false;
    }
}

/* ── 처리 ── */

/** 처리가 가는 중인 요청(글 ID) — 한 번에 하나만 */
const busyId = ref<string | null>(null);
const busyAction = ref<'approve' | 'reject' | null>(null);

function removeRow(postId: string): void {
    items.value = items.value.filter((r) => r.post.id !== postId);
    total.value = Math.max(0, total.value - 1);
}

/**
 * 실패를 화면에 맞춘다 — 처리했으면 true(시트를 닫는다), 입력·네트워크 오류처럼 시트에서 다시 시도할 수 있으면 false.
 * 줄을 거두는 실패(이미 처리됨·취소됨·글 없음·이미 공개)는 줄을 지우고 토스트, 권한·자격 실패는 단추를 끄고 토스트.
 */
async function onFailure(postId: string, e: unknown): Promise<boolean> {
    const kind = UtilGroupVisibility.failureKind(e);
    switch (kind) {
        case 'session':
            return true;
        case 'blocked': {
            const reason = UtilGroupVisibility.blockedOf(e);
            if (reason) block(reason);
            toast.error(UtilBoardMessages.of(t, e), 8000);
            return true;
        }
        case 'decided':
        case 'request-gone':
        case 'gone':
        case 'already-public':
            removeRow(postId);
            toast.show(UtilGroupVisibilityMessages.failure(t, kind, e), { life: 5000 });
            return true;
        case 'permission-denied':
            toast.error(t('groupManager.revoked'), 8000);
            await recheck();
            return true;
        case 'leader-only':
        case 'forbidden':
            toast.error(t('groupManager.demoted'), 8000);
            await recheck();
            return true;
        case 'disabled':
            toast.error(UtilBoardMessages.of(t, e), 8000);
            await recheck();
            return true;
        default:
            return false;
    }
}

/* ── 공개하기 시트 ── */
const approveOpen = ref(false);
const approveTarget = ref<GroupPublishRequestRow | null>(null);
const approveError = ref('');

function openApprove(row: GroupPublishRequestRow): void {
    if (busyId.value !== null || !canAct.value) return;
    approveTarget.value = row;
    approveError.value = '';
    approveOpen.value = true;
}

async function submitApprove(): Promise<void> {
    const row = approveTarget.value;
    if (!row || busyId.value !== null) return;
    busyId.value = row.post.id;
    busyAction.value = 'approve';
    approveError.value = '';
    try {
        await api.approvePublishRequest(id, row.post.id);
        removeRow(row.post.id);
        approveOpen.value = false;
        toast.success(t('groupVisibility.publishedToast'), 5000);
    } catch (e) {
        if (await onFailure(row.post.id, e)) {
            approveOpen.value = false;
            return;
        }
        approveError.value = UtilGroupVisibility.fieldOf(e, 'acknowledged') ?? UtilBoardMessages.of(t, e);
    } finally {
        busyId.value = null;
        busyAction.value = null;
    }
}

/* ── 거절 시트 ── */
const rejectOpen = ref(false);
const rejectTarget = ref<GroupPublishRequestRow | null>(null);
const rejectError = ref('');

function openReject(row: GroupPublishRequestRow): void {
    if (busyId.value !== null || !canAct.value) return;
    rejectTarget.value = row;
    rejectError.value = '';
    rejectOpen.value = true;
}

async function submitReject(reason: string): Promise<void> {
    const row = rejectTarget.value;
    if (!row || busyId.value !== null) return;
    busyId.value = row.post.id;
    busyAction.value = 'reject';
    rejectError.value = '';
    try {
        await api.rejectPublishRequest(id, row.post.id, reason || undefined);
        removeRow(row.post.id);
        rejectOpen.value = false;
        toast.success(t('groupVisibility.rejectedToast'), 5000);
    } catch (e) {
        if (await onFailure(row.post.id, e)) {
            rejectOpen.value = false;
            return;
        }
        rejectError.value = UtilGroupVisibility.fieldOf(e, 'reason') ?? UtilBoardMessages.of(t, e);
    } finally {
        busyId.value = null;
        busyAction.value = null;
    }
}
</script>
