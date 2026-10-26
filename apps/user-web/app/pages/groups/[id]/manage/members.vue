<template>
    <!--
        멤버 관리 `/groups/{id}/manage/members` — 모임장 → 멤버 순(서버 정렬), 30명씩 더보기. 줄마다 「모임장」·「나」 표시와 가입 시기, 모임장이 아닌 줄에 ⋯.
        ⋯ 시트: 프로필 보기 · 강퇴하기(확인 — 강퇴된 회원은 다시 오려면 신청이 필요하다) · 모임장 넘기기(확인 — 나는 일반 멤버가 되고 소모임 상세로 간다).
        community 대비: 매니저 필터·임명·맡은 일·해임·인원 설정(3단계)은 뺐다.
    -->
    <GroupManageShell
        :title="t('group.rowMembers')"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        @retry="load()"
    >
        <div v-if="listStatus === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 5" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="listStatus === 'error'" :title="t('group.membersError')" :description="listError" retry @retry="loadFirst()" />

        <template v-else>
            <p class="flex items-baseline justify-between gap-2 px-1 pb-1.5 text-sm font-semibold">
                <span>{{ t('group.membersHeading', { n: total }) }}</span>
                <small class="text-xs font-normal text-muted-color">{{ t('group.capacityHeading', { n: group?.capacity ?? 0 }) }}</small>
            </p>
            <ul class="m-0 list-none overflow-hidden rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900" data-testid="group-members-list">
                <li v-for="(member, index) in items" :key="member.user.id ?? `withdrawn-${index}`" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0" data-testid="group-member">
                    <div class="min-w-0 flex-1">
                        <p class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
                            <PostAuthorLabel :author="UtilGroup.authorOf(member.user, t('group.withdrawn'))" link strong />
                            <GroupTag v-if="member.role === 'LEADER'" tone="amber">{{ t('group.roleLeader') }}</GroupTag>
                            <GroupTag v-if="isMe(member)" tone="neutral">{{ t('group.me') }}</GroupTag>
                        </p>
                        <small class="block text-xs text-muted-color">{{ UtilGroupMessages.joinedMonth(t, member.joinedAt, time) }}</small>
                    </div>
                    <Button
                        v-if="hasMenu(member)"
                        type="button"
                        icon="pi pi-ellipsis-h"
                        severity="secondary"
                        variant="text"
                        rounded
                        :aria-label="t('group.memberMenu', { name: member.user.nickname })"
                        aria-haspopup="dialog"
                        data-testid="group-member-menu"
                        @click="openMenu(member)"
                    />
                </li>
            </ul>

            <div v-if="hasNext" class="py-4 text-center">
                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="loadingMore" @click="loadMore" />
                <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
            </div>
        </template>

        <!-- ⋯ 시트 -->
        <AppSheet v-model:visible="menuOpen" :title="menuTarget ? t('group.memberSheetTitle', { name: menuTarget.user.nickname }) : t('group.rowMembers')" :description="menuTarget ? UtilGroupMessages.joinedMonth(t, menuTarget.joinedAt, time) : ''">
            <p v-if="group?.blockedReason" class="m-0 mt-3 px-5 text-xs text-muted-color">{{ UtilGroupMessages.blockedBand(t, group.blockedReason) }}</p>
            <ul class="m-0 mt-3 list-none p-0">
                <li>
                    <button type="button" :class="ITEM" data-testid="member-menu-profile" @click="onAction('profile')">{{ t('group.menuProfile') }}</button>
                </li>
                <li v-if="group?.permissions.canKickMembers">
                    <button type="button" :class="[ITEM, 'text-red-600 dark:text-red-400']" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-kick" @click="onAction('kick')">{{ t('group.menuKick') }}</button>
                </li>
                <li v-if="group?.permissions.canEditGroup">
                    <button type="button" :class="ITEM" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-transfer" @click="onAction('transfer')">
                        {{ t('group.menuTransfer') }}
                        <small class="ml-1 text-xs text-muted-color">{{ t('group.menuTransferHint') }}</small>
                    </button>
                </li>
            </ul>
            <div class="px-5 pt-3">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="menuOpen = false" />
            </div>
        </AppSheet>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupMember } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilProfile from '~/utils/util-profile';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.rowMembers'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { user } = useAuth();
const { state, errorMessage, group, load, block } = useGroupManage(id, 'KICK');

const ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-surface-800';

type ListStatus = 'loading' | 'ready' | 'error';
const listStatus = ref<ListStatus>('loading');
const listError = ref('');
const items = ref<GroupMember[]>([]);
const page = ref(1);
const hasNext = ref(false);
const total = ref(0);
const loadingMore = ref(false);
const moreError = ref('');
let generation = 0;

function appendUnique(base: GroupMember[], more: GroupMember[]): GroupMember[] {
    const have = new Set(base.map((m) => m.user.id).filter((v): v is string => v !== null));
    return [...base, ...more.filter((m) => m.user.id === null || !have.has(m.user.id))];
}

async function loadFirst() {
    const mine = ++generation;
    listStatus.value = 'loading';
    moreError.value = '';
    try {
        const res = await api.members(id, { page: 1, size: UtilGroup.MEMBER_PAGE_SIZE });
        if (mine !== generation) return;
        items.value = res.items;
        page.value = 1;
        hasNext.value = res.hasNext;
        total.value = res.total;
        listStatus.value = 'ready';
    } catch (e) {
        if (mine !== generation || UtilGroup.isAborted(e) || UtilGroup.isSessionLost(e)) return;
        listError.value = UtilBoardMessages.of(t, e);
        listStatus.value = 'error';
    }
}

async function loadMore() {
    if (loadingMore.value || !hasNext.value) return;
    const mine = generation;
    loadingMore.value = true;
    moreError.value = '';
    try {
        const res = await api.members(id, { page: page.value + 1, size: UtilGroup.MEMBER_PAGE_SIZE });
        if (mine !== generation) return;
        items.value = appendUnique(items.value, res.items);
        page.value += 1;
        hasNext.value = res.hasNext;
        total.value = res.total;
    } catch (e) {
        if (mine !== generation || UtilGroup.isSessionLost(e)) return;
        moreError.value = t('group.moreFailed', { reason: UtilBoardMessages.of(t, e) });
    } finally {
        loadingMore.value = false;
    }
}

watch(
    () => state.value === 'ready',
    (ready) => ready && void loadFirst(),
    { immediate: true },
);
onBeforeUnmount(() => generation++);

const isMe = (member: GroupMember) => member.user.id !== null && member.user.id === user.value?.id;

/** ⋯ 는 모임장이 아닌 지금 회원 줄에만(탈퇴한 회원·나 자신은 없다) */
function hasMenu(member: GroupMember): boolean {
    return !member.user.withdrawn && member.user.id !== null && member.role !== 'LEADER' && !isMe(member);
}

function removeMember(userId: string) {
    const before = items.value.length;
    items.value = items.value.filter((m) => m.user.id !== userId);
    if (items.value.length < before) total.value = Math.max(0, total.value - 1);
}

async function onGuardFailure(e: unknown): Promise<boolean> {
    const blocked = UtilGroup.blockedReasonOf(e);
    if (blocked) {
        block(blocked);
        toast.error(UtilBoardMessages.of(t, e), 8000);
        return true;
    }
    if (UtilGroup.isCode(e, 'GROUP_LEADER_ONLY')) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
        await load();
        return true;
    }
    return false;
}

/* ── ⋯ 시트 ── */
const menuOpen = ref(false);
const menuTarget = ref<GroupMember | null>(null);
const acting = ref(false);

function openMenu(member: GroupMember) {
    if (acting.value) return;
    menuTarget.value = member;
    menuOpen.value = true;
}

async function onAction(action: 'profile' | 'kick' | 'transfer') {
    const member = menuTarget.value;
    const userId = member?.user.id;
    menuOpen.value = false;
    if (!member || !userId || acting.value) return;
    const name = member.user.nickname;
    if (action === 'profile') {
        await navigateTo(UtilProfile.path(userId));
        return;
    }
    if (action === 'kick') {
        if (!(await confirm({ title: t('group.kickTitle', { name }), message: t('group.kickMessage'), confirmLabel: t('group.menuKick'), tone: 'danger' }))) return;
        await kick(userId, name);
        return;
    }
    if (!(await confirm({ title: t('group.transferTitle', { name }), message: t('group.transferMessage'), confirmLabel: t('group.menuTransfer'), tone: 'danger' }))) return;
    await transfer(userId);
}

async function kick(userId: string, name: string) {
    acting.value = true;
    try {
        await api.kick(id, userId);
        removeMember(userId);
        toast.success(t('group.kickedToast', { name }), 5000);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_MEMBER_NOT_FOUND')) {
            removeMember(userId);
            toast.show(t('group.memberGone'), { life: 5000 });
            return;
        }
        if (await onGuardFailure(e)) return;
        toast.error(UtilGroupMessages.failed(t, 'group.kickFailed', e), 8000);
    } finally {
        acting.value = false;
    }
}

async function transfer(userId: string) {
    acting.value = true;
    try {
        await api.transferLeader(id, userId);
        toast.success(t('group.transferredToast'), 5000);
        await navigateTo(UtilGroup.detailPath(id), { replace: true });
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_MEMBER_NOT_FOUND')) {
            removeMember(userId);
            toast.show(t('group.memberGone'), { life: 5000 });
            return;
        }
        if (await onGuardFailure(e)) return;
        toast.error(UtilGroupMessages.failed(t, 'group.transferFailed', e), 8000);
    } finally {
        acting.value = false;
    }
}
</script>
