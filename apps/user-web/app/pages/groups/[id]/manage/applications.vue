<template>
    <!--
        가입 신청 `/groups/{id}/manage/applications` — 오래된 신청이 위. 줄마다 닉네임(누르면 프로필)·「강퇴된 적 있는 회원」·신청일과 한마디·[거절]/[승인].
        승인은 정원을 확인한다(`409 GROUP_FULL` — 정원이 찼으면 승인 단추가 꺼지고 정원 수정 길을 보인다). 거절은 사유(선택, 100자 — 적었을 때만 신청자에게 전달) 시트.
        두 사람이 동시에 처리했으면 늦은 쪽이 `404 GROUP_APPLICATION_NOT_FOUND` — 그 줄을 빼고 알린다.
        모임장과 `APPLICATIONS` 매니저가 들어온다(맡지 않은 매니저는 틀이 「모임장이 맡기지 않은 일이에요」). 열어 둔 채 맡은 일이 빠지면(`403 GROUP_MANAGER_PERMISSION_DENIED`)
        토스트 + 위 띠 + 단추가 꺼지고 목록은 남는다. 정원 수정 링크는 모임장(`canEditGroup`)에게만.
    -->
    <GroupManageShell
        :title="items.length > 0 ? t('group.applicationsTitleCount', { n: items.length }) : t('group.rowApplications')"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        permission="APPLICATIONS"
        @retry="load()"
    >
        <!-- 열어 둔 채 모임장이 이 일을 거두어 갔다 — 목록은 그대로 두고 단추만 끈다 -->
        <p v-if="revoked" class="mb-3 rounded-lg bg-amber-100 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status" data-testid="group-applications-revoked">
            {{ t('groupManager.revoked') }}
        </p>
        <div v-if="listStatus === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="listStatus === 'error'" :title="t('group.applicationsError')" :description="listError" retry @retry="loadList()" />

        <template v-else>
            <div v-if="full && items.length > 0" class="mb-3 rounded-lg bg-amber-100 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status" data-testid="group-applications-full">
                <b>{{ t('group.fullTitle') }}</b>
                {{ t('group.fullText') }}
                <NuxtLink v-if="group?.permissions.canEditGroup" :to="UtilGroup.managePath(id, 'edit')" class="whitespace-nowrap font-semibold underline">{{ t('group.fullLink') }}</NuxtLink>
            </div>

            <EmptyState v-if="items.length === 0" :title="t('group.applicationsEmptyTitle')" :description="t('group.applicationsEmptyText')" icon="pi pi-user-plus" data-testid="group-applications-empty" />

            <template v-else>
                <p class="flex items-baseline justify-between gap-2 px-1 pb-1.5 text-sm font-semibold">
                    <span>{{ t('group.slotsLeft', { n: Math.max(0, capacity - memberCount) }) }}</span>
                    <small class="text-xs font-normal text-muted-color">{{ t('group.capacityStat', { members: memberCount, capacity }) }}</small>
                </p>
                <ul class="m-0 list-none overflow-hidden panel-card p-0" data-testid="group-applications-list">
                    <li v-for="row in items" :key="row.user.id ?? row.appliedAt" class="border-b border-surface px-4 py-3 last:border-b-0" data-testid="group-application">
                        <p class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
                            <PostAuthorLabel :author="UtilGroup.authorOf(row.user, t('group.withdrawn'))" link strong />
                            <GroupTag v-if="row.kickedBefore" tone="amber">{{ t('group.kickedBefore') }}</GroupTag>
                        </p>
                        <p class="mt-0.5 break-words text-xs text-muted-color [overflow-wrap:anywhere]">{{ UtilGroupMessages.applicationMeta(t, row.appliedAt, row.message, time) }}</p>
                        <div class="mt-2 flex justify-end gap-2">
                            <Button
                                type="button"
                                :label="t('group.reject')"
                                size="small"
                                severity="secondary"
                                variant="outlined"
                                :disabled="busyId !== null || !row.user.id || !canAct"
                                :aria-label="t('group.rejectAria', { name: row.user.nickname })"
                                @click="openReject(row)"
                            />
                            <Button
                                type="button"
                                :label="t('group.approve')"
                                size="small"
                                :loading="busyAction === 'approve' && busyId === row.user.id"
                                :disabled="busyId !== null || !row.user.id || !canAct || full"
                                :aria-label="t('group.approveAria', { name: row.user.nickname })"
                                @click="approve(row)"
                            />
                        </div>
                    </li>
                </ul>
                <p class="mt-3 px-1 text-xs text-muted-color">{{ t('group.applicationsProfileHint') }}</p>
            </template>
        </template>

        <!-- 거절 시트 — 사유는 선택(100자) -->
        <AppSheet v-model:visible="rejectOpen" :title="rejectTarget ? t('group.rejectTitle', { name: rejectTarget.user.nickname }) : t('group.reject')" :description="t('group.rejectDescription')">
            <form class="mt-3 px-5" novalidate @submit.prevent="submitReject">
                <LabeledField :label="t('group.rejectLabel')" :hint="t('group.optional')" :help="t('group.rejectHelp')" :error="rejectShownError">
                    <template #aside>
                        <small class="text-xs tabular-nums" :class="reasonTooLong ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: reasonLength, max: UtilGroup.REASON_MAX }) }}</small>
                    </template>
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <Textarea :id="fieldId" v-model="reason" name="reason" rows="3" fluid class="resize-none" :invalid="invalid" :disabled="busyAction === 'reject'" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <div class="mt-4 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="busyAction === 'reject'" @click="rejectOpen = false" />
                    <Button type="submit" :label="t('group.rejectConfirm')" severity="danger" class="flex-1" :loading="busyAction === 'reject'" :disabled="reasonTooLong || busyAction === 'reject'" />
                </div>
            </form>
        </AppSheet>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupApplication } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilGroupManager from '~/utils/util-group-manager';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.rowApplications'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { state, errorMessage, group, load, block, recheck } = useGroupManage(id, 'APPLICATIONS');
const canAct = computed(() => Boolean(group.value?.permissions.canHandleApplications) && !group.value?.blockedReason);
/** 열어 둔 채 「가입 신청 처리」를 거두어 갔다(모임장은 해당 없다) */
const revoked = computed(() => state.value === 'ready' && group.value !== null && !group.value.permissions.canHandleApplications);

type ListStatus = 'loading' | 'ready' | 'error';
const listStatus = ref<ListStatus>('loading');
const listError = ref('');
const items = ref<GroupApplication[]>([]);
const capacity = ref(0);
const memberCount = ref(0);
const full = computed(() => capacity.value > 0 && memberCount.value >= capacity.value);

let listSeq = 0;
async function loadList() {
    const mine = ++listSeq;
    listStatus.value = 'loading';
    try {
        const res = await api.applications(id);
        if (mine !== listSeq) return;
        items.value = res.items;
        capacity.value = res.capacity;
        memberCount.value = res.memberCount;
        listStatus.value = 'ready';
    } catch (e) {
        if (mine !== listSeq || UtilGroup.isAborted(e) || UtilGroup.isSessionLost(e)) return;
        listError.value = UtilBoardMessages.of(t, e);
        listStatus.value = 'error';
    }
}
// 들어올 수 있다고 정해진 뒤에 받는다(모임장이 아니면 틀이 안내를 보이고 목록은 받지 않는다)
watch(
    () => state.value === 'ready',
    (ready) => ready && void loadList(),
    { immediate: true },
);
onBeforeUnmount(() => listSeq++);

const busyId = ref<string | null>(null);
const busyAction = ref<'approve' | 'reject' | null>(null);

function removeRow(userId: string) {
    items.value = items.value.filter((r) => r.user.id !== userId);
}

async function onGuardFailure(e: unknown): Promise<boolean> {
    const blocked = UtilGroup.blockedReasonOf(e);
    if (blocked) {
        block(blocked);
        toast.error(UtilBoardMessages.of(t, e), 8000);
        return true;
    }
    // 열어 둔 채 맡은 일이 빠졌다 — 목록은 두고 권한만 다시 맞춘다
    if (UtilGroupManager.isPermissionDenied(e)) {
        toast.error(t('groupManager.revoked'), 8000);
        await recheck();
        return true;
    }
    // 모임장도 매니저도 아니게 됐다(해임·넘김) — 다시 맞추면 「모임장 또는 매니저만」 상태가 된다
    if (UtilGroupManager.isLeaderOnly(e)) {
        toast.error(t('groupManager.demoted'), 8000);
        await recheck();
        return true;
    }
    return false;
}

async function approve(row: GroupApplication) {
    const userId = row.user.id;
    if (!userId || busyId.value !== null || full.value) return;
    busyId.value = userId;
    busyAction.value = 'approve';
    try {
        const res = await api.approve(id, userId);
        removeRow(userId);
        memberCount.value = res.memberCount;
        toast.success(t('group.approvedToast', { name: row.user.nickname, members: res.memberCount, capacity: capacity.value }), 5000);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_APPLICATION_NOT_FOUND')) {
            removeRow(userId);
            toast.show(t('group.applicationGone'), { life: 5000 });
            return;
        }
        if (await onGuardFailure(e)) return;
        // 정원이 찼다 — 단추를 끄고 정원 수정 길을 보인다
        if (UtilGroup.isCode(e, 'GROUP_FULL')) memberCount.value = Math.max(memberCount.value, capacity.value);
        toast.error(UtilGroupMessages.failed(t, 'group.approveFailed', e), 8000);
    } finally {
        busyId.value = null;
        busyAction.value = null;
    }
}

/* ── 거절 시트 ── */
const rejectOpen = ref(false);
const rejectTarget = ref<GroupApplication | null>(null);
const rejectError = ref('');
const reason = ref('');
const reasonLength = computed(() => UtilGroup.trimmedLength(reason.value));
const reasonTooLong = computed(() => reasonLength.value > UtilGroup.REASON_MAX);
const rejectShownError = computed(() => (reasonTooLong.value ? t('group.reasonTooLong', { max: UtilGroup.REASON_MAX }) : rejectError.value));

watch(reason, () => (rejectError.value = ''));

function openReject(row: GroupApplication) {
    if (busyId.value !== null) return;
    rejectTarget.value = row;
    rejectError.value = '';
    reason.value = '';
    rejectOpen.value = true;
}

async function submitReject() {
    const row = rejectTarget.value;
    const userId = row?.user.id;
    if (!row || !userId || busyId.value !== null || reasonTooLong.value) return;
    busyId.value = userId;
    busyAction.value = 'reject';
    rejectError.value = '';
    try {
        await api.reject(id, userId, reason.value.trim() || undefined);
        removeRow(userId);
        rejectOpen.value = false;
        toast.success(t('group.rejectedToast', { name: row.user.nickname }), 5000);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_APPLICATION_NOT_FOUND')) {
            removeRow(userId);
            rejectOpen.value = false;
            toast.show(t('group.applicationGone'), { life: 5000 });
            return;
        }
        if (UtilGroup.blockedReasonOf(e) || UtilGroupManager.isLeaderOnly(e) || UtilGroupManager.isPermissionDenied(e)) {
            rejectOpen.value = false;
            await onGuardFailure(e);
            return;
        }
        rejectError.value = e instanceof ApiError ? (e.field('reason') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        busyId.value = null;
        busyAction.value = null;
    }
}
</script>
