<template>
    <!--
        멤버 관리 `/groups/{id}/manage/members`(`?filter=managers`) — **모임장 또는 「멤버 강퇴」를 맡은 매니저**(맡지 않았으면 틀이 「모임장이 맡기지 않은 일이에요」).
        모임장 → 매니저 → 멤버 순(서버 정렬), 30명씩 더보기. 줄마다 「모임장」·「매니저」·「나」 표시와 가입 시기, 매니저 줄에는 맡은 일 요약(모임장·그 매니저 본인에게만 서버가 준다).
        거르기 칩 [전체 N] [매니저 n/limit](상세에 `managerInfo` 가 있을 때) — 매니저 거르기는 `GET /api/groups/{id}/managers` 로 그리고(보조 글은 임명한 달), 머리에 「매니저 n/limit명」 + [인원 바꾸기](모임장만).
        ⋯ 시트 — 모임장: 프로필 보기 · 매니저로 임명(일반 멤버, 매니저 기능이 켜졌을 때 `canAppointManager`) / 맡은 일 바꾸기(같은 조건)·매니저 해임(매니저) · 강퇴하기 · 모임장 넘기기.
          매니저(`KICK`): 일반 멤버 줄에만 프로필 보기 · 강퇴하기(강퇴 사실은 모임장에게도 알림).
        임명·맡은 일 바꾸기는 **같은 체크 시트**(`PUT …/managers/{userId}` — 임명은 모두 꺼진 채 시작·하나 이상, 맡은 일 바꾸기는 지금 값으로 시작·바뀌고 1개 이상일 때만 저장, 마지막 체크를 풀면 「해임해 주세요」).
          맡은 일 바꾸기는 저장 전에 매니저 목록을 다시 받아 그 사이 해임·탈퇴되지 않았는지 본다(같은 API 가 임명도 하므로 해임당한 사람을 되살리지 않는다).
        인원 설정 시트([−] 숫자 [＋]) — 지금 매니저 수 아래·설정 최대 인원 위로는 못 간다(`PUT …/manager-limit`, 서버 `400 fields.limit` 은 시트 안에).
        정지·이메일 인증 전이면 목록은 열리고 ⋯ 의 쓰기 항목이 꺼진다. 열어 둔 채 「멤버 강퇴」가 빠지면 위 띠 + ⋯ 가 사라진다(목록은 남는다).
    -->
    <GroupManageShell
        :title="t('group.rowMembers')"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        permission="KICK"
        @retry="load()"
    >
        <!-- 열어 둔 채 모임장이 「멤버 강퇴」를 거두어 갔다 — 목록은 그대로 두고 ⋯ 만 거둔다 -->
        <p v-if="revoked" class="mb-3 rounded-lg bg-amber-100 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status" data-testid="group-members-revoked">
            {{ t('groupManager.revoked') }}
        </p>

        <!-- 거르기 — 전체 · 매니저 n/limit -->
        <div v-if="group?.managerInfo" class="mb-2 flex flex-wrap items-center gap-2" role="group" :aria-label="t('groupManager.filterLabel')" data-testid="group-members-filter">
            <button type="button" :class="chipClass(filter === 'all')" :aria-pressed="filter === 'all'" data-testid="group-members-filter-all" @click="setFilter('all')">
                {{ t('groupManager.filterAll', { n: total }) }}
            </button>
            <button type="button" :class="chipClass(filter === 'managers')" :aria-pressed="filter === 'managers'" data-testid="group-members-filter-managers" @click="setFilter('managers')">
                {{ t('groupManager.filterManagers', { count: group.managerInfo.count, limit: group.managerInfo.limit }) }}
            </button>
        </div>

        <!-- 매니저 거르기 -->
        <template v-if="filter === 'managers' && group?.managerInfo">
            <p class="flex items-center justify-between gap-2 px-1 pb-1.5 text-sm font-semibold" data-testid="group-managers-heading">
                <span>{{ t('groupManager.heading', { count: group.managerInfo.count, limit: group.managerInfo.limit }) }}</span>
                <Button
                    v-if="isLeader"
                    type="button"
                    :label="t('groupManager.limitButton')"
                    size="small"
                    variant="outlined"
                    severity="secondary"
                    :disabled="Boolean(group.blockedReason)"
                    :title="group.blockedReason ? UtilGroupManagerMessages.blockedShort(t, group.blockedReason) : undefined"
                    data-testid="group-managers-limit"
                    @click="openLimit"
                />
            </p>
            <p v-if="isLeader && !group.permissions.canAppointManager" class="m-0 mb-2 px-1 text-xs text-muted-color" data-testid="group-managers-off">{{ t('groupManager.appointOff') }}</p>

            <div v-if="managerStatus === 'loading' || managerStatus === 'idle'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <div v-for="n in 3" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
            <ErrorState v-else-if="managerStatus === 'error'" :title="t('groupManager.listError')" :description="managerError" retry data-testid="group-managers-error" @retry="loadManagers()" />
            <EmptyState v-else-if="managerRows.length === 0" :title="t('groupManager.emptyTitle')" :description="t('groupManager.emptyText')" icon="pi pi-users" data-testid="group-managers-empty" />
        </template>

        <!-- 전체 — 불러오는 중 · 실패 -->
        <div v-else-if="listStatus === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 5" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="listStatus === 'error'" :title="t('group.membersError')" :description="listError" retry @retry="loadFirst()" />
        <p v-else class="flex items-baseline justify-between gap-2 px-1 pb-1.5 text-sm font-semibold">
            <span>{{ t('group.membersHeading', { n: total }) }}</span>
            <small class="text-xs font-normal text-muted-color">{{ t('group.capacityHeading', { n: group?.capacity ?? 0 }) }}</small>
        </p>

        <!-- 줄 — 전체와 매니저 거르기가 같은 모양 -->
        <ul v-if="shownRows.length > 0" class="m-0 list-none overflow-hidden panel-card p-0" :data-testid="showingManagers ? 'group-managers-list' : 'group-members-list'">
            <li v-for="(member, index) in shownRows" :key="member.user.id ?? `withdrawn-${index}`" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0" data-testid="group-member">
                <div class="min-w-0 flex-1">
                    <p class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1 text-sm">
                        <PostAuthorLabel :author="UtilGroup.authorOf(member.user, t('group.withdrawn'))" link strong />
                        <GroupTag v-if="member.role === 'LEADER'" tone="amber">{{ t('group.roleLeader') }}</GroupTag>
                        <GroupTag v-if="member.role === 'MANAGER'" tone="sky" data-testid="group-member-manager">{{ t('group.roleManager') }}</GroupTag>
                        <GroupTag v-if="isMe(member)" tone="neutral">{{ t('group.me') }}</GroupTag>
                    </p>
                    <small class="block text-xs text-muted-color">
                        {{ showingManagers ? UtilGroupManagerMessages.appointedLabel(t, member.joinedAt, time) : UtilGroupMessages.joinedMonth(t, member.joinedAt, time) }}
                    </small>
                    <!-- 맡은 일 요약 — 모임장과 그 매니저 본인에게만 서버가 주므로 값이 있을 때만 -->
                    <small v-if="summaryOf(member)" class="mt-0.5 block text-xs text-muted-color" data-testid="group-member-summary">{{ summaryOf(member) }}</small>
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

        <div v-if="!showingManagers && listStatus === 'ready' && hasNext" class="py-4 text-center">
            <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="loadingMore" @click="loadMore" />
            <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
        </div>

        <!-- ⋯ 시트 -->
        <AppSheet v-model:visible="menuOpen" :title="menuTarget ? t('group.memberSheetTitle', { name: menuTarget.user.nickname }) : t('group.rowMembers')" :description="menuTarget ? UtilGroupMessages.joinedMonth(t, menuTarget.joinedAt, time) : ''">
            <p v-if="group?.blockedReason" class="m-0 mt-3 px-5 text-xs text-muted-color">{{ UtilGroupMessages.blockedBand(t, group.blockedReason) }}</p>
            <ul class="m-0 mt-3 list-none p-0">
                <li>
                    <button type="button" :class="ITEM" data-testid="member-menu-profile" @click="onAction('profile')">{{ t('group.menuProfile') }}</button>
                </li>
                <!-- 모임장만 — 매니저 기능이 꺼졌으면 임명·맡은 일 바꾸기만 숨고 해임은 남는다 -->
                <li v-if="isLeader && menuTarget?.role === 'MEMBER' && group?.permissions.canAppointManager">
                    <button type="button" :class="ITEM" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-appoint" @click="onAction('appoint')">{{ t('groupManager.menuAppoint') }}</button>
                </li>
                <li v-if="isLeader && menuTarget?.role === 'MANAGER' && group?.permissions.canAppointManager">
                    <button type="button" :class="ITEM" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-edit" @click="onAction('edit')">{{ t('groupManager.menuEdit') }}</button>
                </li>
                <li v-if="isLeader && menuTarget?.role === 'MANAGER'">
                    <button type="button" :class="ITEM" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-remove" @click="onAction('remove')">{{ t('groupManager.menuRemove') }}</button>
                </li>
                <li v-if="menuCanKick">
                    <button type="button" :class="[ITEM, 'text-red-600 dark:text-red-400']" :disabled="Boolean(group?.blockedReason)" data-testid="member-menu-kick" @click="onAction('kick')">{{ t('group.menuKick') }}</button>
                </li>
                <li v-if="isLeader && group?.permissions.canEditGroup">
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

        <!-- 임명 · 맡은 일 바꾸기 — 같은 체크 시트 -->
        <AppSheet v-model:visible="permOpen" :title="permTitle" :description="permMode === 'appoint' ? t('groupManager.appointDescription') : t('groupManager.editDescription')">
            <form class="mt-3 px-5" novalidate :aria-label="permTitle" data-testid="manager-sheet" @submit.prevent="submitPermissions">
                <ul class="m-0 list-none p-0" data-testid="manager-checks">
                    <li v-for="option in permOptions" :key="option.key" class="border-b border-surface py-2.5 last:border-b-0">
                        <div class="flex items-start gap-2.5">
                            <Checkbox
                                :model-value="permSelected.includes(option.key)"
                                binary
                                :input-id="`${uid}-perm-${option.key}`"
                                :disabled="permBusy"
                                :data-testid="`manager-check-${option.key}`"
                                @update:model-value="permSelected = UtilGroupManager.toggle(permSelected, option.key, Boolean($event))"
                            />
                            <label :for="`${uid}-perm-${option.key}`" class="min-w-0 flex-1 cursor-pointer">
                                <span class="block text-sm font-medium">{{ option.name }}</span>
                                <small class="block text-xs text-muted-color">{{ option.hint }}</small>
                            </label>
                        </div>
                        <p v-if="option.note && permSelected.includes(option.key)" class="m-0 mt-1.5 rounded-lg bg-amber-100 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200">{{ option.note }}</p>
                    </li>
                </ul>

                <!-- 맡지 못하는 일 — 모임장 고정이라는 것을 임명 전에 보인다 -->
                <section class="mt-3 rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800" data-testid="manager-fixed">
                    <h3 class="m-0 text-xs font-semibold text-muted-color">{{ t('groupManager.fixedTitle') }}</h3>
                    <ul class="m-0 mt-1 list-none space-y-0.5 p-0 text-xs text-muted-color">
                        <li v-for="line in UtilGroupManagerMessages.fixedLines(t)" :key="line">{{ line }}</li>
                    </ul>
                </section>

                <!-- 인원 줄 — 임명할 때만 -->
                <div v-if="permMode === 'appoint' && group?.managerInfo" class="mt-3 text-sm" data-testid="manager-slots">
                    <p class="m-0" :class="permFull ? 'font-semibold text-red-600 dark:text-red-400' : ''">{{ UtilGroupManagerMessages.slotsLine(t, group.managerInfo) }}</p>
                    <Button
                        v-if="permFull && group.managerInfo.limit < group.managerInfo.limitMax"
                        type="button"
                        :label="t('groupManager.limitButton')"
                        size="small"
                        variant="text"
                        class="mt-1"
                        data-testid="manager-limit-open"
                        @click="limitFromAppoint"
                    />
                </div>

                <!-- 마지막 체크를 풀었다 — 하나도 맡기지 않으려면 해임 -->
                <p v-if="permCleared" class="m-0 mt-3 text-sm text-muted-color" data-testid="manager-cleared">
                    {{ t('groupManager.clearedText') }}
                    <button type="button" class="ml-1 font-semibold text-red-600 underline focus-visible:outline-2 focus-visible:outline-primary dark:text-red-400" :disabled="permBusy" @click="removeFromSheet">
                        {{ t('groupManager.clearedAction') }}
                    </button>
                </p>
                <p v-else-if="permMode === 'appoint' && permSelected.length === 0" class="m-0 mt-3 text-xs text-muted-color">{{ t('groupManager.pickOne') }}</p>

                <p v-if="permError" role="alert" class="m-0 mt-3 text-xs text-red-600 dark:text-red-400" data-testid="manager-error">{{ permError }}</p>

                <div class="mt-4 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="permBusy" @click="permOpen = false" />
                    <Button
                        type="submit"
                        :label="permMode === 'appoint' ? t('groupManager.appointConfirm') : t('common.save')"
                        class="flex-1"
                        :loading="permBusy"
                        :disabled="!permCanSubmit"
                        :title="group?.blockedReason ? UtilGroupManagerMessages.blockedShort(t, group.blockedReason) : undefined"
                        data-testid="manager-confirm"
                    />
                </div>
            </form>
        </AppSheet>

        <!-- 인원 설정 — [−] 숫자 [＋]. 지금 매니저 수 아래로는 못 줄이고 설정의 최대 인원 위로는 못 늘린다 -->
        <AppSheet
            v-if="group?.managerInfo"
            v-model:visible="limitOpen"
            :title="t('groupManager.limitTitle')"
            :description="t('groupManager.limitDescription', { max: UtilGroupManager.limitRange(group.managerInfo).max })"
        >
            <form class="mt-3 px-5" novalidate :aria-label="t('groupManager.limitTitle')" data-testid="limit-sheet" @submit.prevent="submitLimit">
                <div class="flex items-center justify-center gap-4">
                    <Button
                        type="button"
                        icon="pi pi-minus"
                        rounded
                        severity="secondary"
                        variant="outlined"
                        :aria-label="t('groupManager.limitDown')"
                        :disabled="limitBusy || limitValue <= UtilGroupManager.limitRange(group.managerInfo).min"
                        data-testid="limit-down"
                        @click="limitValue = UtilGroupManager.stepLimit(limitValue, -1, group.managerInfo)"
                    />
                    <output class="min-w-16 text-center text-2xl font-bold tabular-nums" aria-live="polite" data-testid="limit-value">{{ t('groupManager.limitValue', { n: limitValue }) }}</output>
                    <Button
                        type="button"
                        icon="pi pi-plus"
                        rounded
                        severity="secondary"
                        variant="outlined"
                        :aria-label="t('groupManager.limitUp')"
                        :disabled="limitBusy || limitValue >= UtilGroupManager.limitRange(group.managerInfo).max"
                        data-testid="limit-up"
                        @click="limitValue = UtilGroupManager.stepLimit(limitValue, 1, group.managerInfo)"
                    />
                </div>
                <p class="m-0 mt-2 text-center text-xs text-muted-color">{{ t('groupManager.limitNow', { n: group.managerInfo.count }) }}</p>
                <p v-if="limitHint" class="m-0 mt-1 text-center text-xs text-muted-color">{{ limitHint }}</p>
                <p v-if="limitError" role="alert" class="m-0 mt-2 text-center text-xs text-red-600 dark:text-red-400" data-testid="limit-error">{{ limitError }}</p>
                <div class="mt-4 flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="limitBusy" @click="limitOpen = false" />
                    <Button
                        type="submit"
                        :label="t('common.save')"
                        class="flex-1"
                        :loading="limitBusy"
                        :disabled="limitBusy || limitValue === group.managerInfo.limit || Boolean(group.blockedReason)"
                        :title="group.blockedReason ? UtilGroupManagerMessages.blockedShort(t, group.blockedReason) : undefined"
                        data-testid="limit-confirm"
                    />
                </div>
            </form>
        </AppSheet>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupManagerItem, GroupManagerPermission, GroupMember } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupManager from '~/utils/util-group-manager';
import UtilGroupManagerMessages from '~/utils/util-group-manager-messages';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilProfile from '~/utils/util-profile';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.rowMembers'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { user } = useAuth();
const { state, errorMessage, group, load, block, recheck } = useGroupManage(id, 'KICK');

const ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-surface-800';

/** 모임장인가 — 서버의 `myRole`, 옛 응답은 모임장 고정 권한으로 읽는다 */
const isLeader = computed(() => {
    const g = group.value;
    if (!g) return false;
    return g.myRole === 'LEADER' || (g.myRole !== 'MANAGER' && g.permissions.canEditGroup);
});
/** 열어 둔 채 모임장이 「멤버 강퇴」를 거두어 갔다(모임장 본인은 해당 없다) */
const revoked = computed(() => state.value === 'ready' && group.value !== null && !isLeader.value && !group.value.permissions.canKickMembers);

/* ── 전체 목록 ── */
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
    (ready) => ready && listStatus.value !== 'ready' && void loadFirst(),
    { immediate: true },
);
onBeforeUnmount(() => generation++);

/* ── 거르기 — 전체 · 매니저 ── */
type Filter = 'all' | 'managers';
const filter = ref<Filter>(route.query.filter === 'managers' ? 'managers' : 'all');
const chipClass = (on: boolean) =>
    [
        'rounded-full border px-3 py-1 text-xs font-semibold focus-visible:outline-2 focus-visible:outline-primary',
        on ? 'border-primary bg-primary text-primary-contrast' : 'border-surface bg-surface-0 text-muted-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800',
    ].join(' ');

type ManagerStatus = 'idle' | 'loading' | 'ready' | 'error';
const managerStatus = ref<ManagerStatus>('idle');
const managerError = ref('');
const managerItems = ref<GroupManagerItem[]>([]);
let managerRequest = 0;

/** 매니저 목록을 받는다 — 매니저 거르기를 열었을 때와 임명·해임·맡은 일 바꾸기·인원 설정 뒤 */
async function loadManagers(quiet = false): Promise<void> {
    const mine = ++managerRequest;
    if (!quiet) managerStatus.value = 'loading';
    try {
        const res = await api.managers(id);
        if (mine !== managerRequest) return;
        managerItems.value = res.items;
        managerStatus.value = 'ready';
    } catch (e) {
        if (mine !== managerRequest || UtilGroup.isAborted(e) || UtilGroup.isSessionLost(e)) return;
        if (quiet && managerStatus.value === 'ready') return; // 다시 받지 못해도 보이던 목록을 둔다
        managerError.value = UtilBoardMessages.of(t, e);
        managerStatus.value = 'error';
    }
}

function setFilter(next: Filter) {
    filter.value = next;
    void navigateTo({ path: route.path, query: next === 'managers' ? { filter: 'managers' } : {} }, { replace: true });
    if (next === 'managers' && managerStatus.value !== 'ready') void loadManagers();
}

// 주소의 `?filter=managers` 로 열렸으면 상태가 정해진 뒤 매니저 목록을 받는다
watch(
    () => state.value === 'ready' && filter.value === 'managers',
    (on) => {
        if (on && managerStatus.value === 'idle') void loadManagers();
    },
    { immediate: true },
);
onBeforeUnmount(() => managerRequest++);

/** 매니저 거르기의 줄 — 전체 목록의 줄과 같은 모양으로 맞춘다(보조 글은 가입 시기 대신 임명한 달) */
const managerRows = computed<GroupMember[]>(() => managerItems.value.map((m) => ({ user: m.user, role: 'MANAGER', joinedAt: m.appointedAt, managerPermissions: m.permissions })));
const showingManagers = computed(() => filter.value === 'managers' && group.value?.managerInfo != null);
const shownRows = computed<GroupMember[]>(() => {
    if (showingManagers.value) return managerStatus.value === 'ready' ? managerRows.value : [];
    return listStatus.value === 'ready' ? items.value : [];
});

/* ── 줄 ── */
const isMe = (member: GroupMember) => member.user.id !== null && member.user.id === user.value?.id;

/** 맡은 일 요약 — 서버가 준 줄(모임장·본인 시점)에만. 내 도구가 멈춰 있으면 끝에 안내 */
function summaryOf(member: GroupMember): string {
    if (member.role !== 'MANAGER' || !member.managerPermissions?.length) return '';
    return UtilGroupManagerMessages.summaryLine(t, member.managerPermissions, isMe(member) && Boolean(group.value?.blockedReason));
}

/** ⋯ 를 보인다 — 보낼 회원 ID 가 있어야 한다. 모임장은 모임장 줄을 뺀 모든 줄, 매니저는 `KICK` 이 있을 때 일반 멤버 줄에만(모임장·매니저·나는 없다) */
function hasMenu(member: GroupMember): boolean {
    if (member.user.withdrawn || member.user.id === null || isMe(member)) return false;
    if (isLeader.value) return member.role !== 'LEADER';
    return Boolean(group.value?.permissions.canKickMembers) && member.role === 'MEMBER';
}

function removeMember(userId: string) {
    const before = items.value.length;
    items.value = items.value.filter((m) => m.user.id !== userId);
    if (items.value.length < before) total.value = Math.max(0, total.value - 1);
    managerItems.value = managerItems.value.filter((m) => m.user.id !== userId);
}

/** 전체 목록 한 줄의 역할을 바로 바꾼다(임명·해임·맡은 일 바꾸기 성공 뒤) */
function setRole(userId: string, role: 'MANAGER' | 'MEMBER', permissions: GroupManagerPermission[] | null) {
    items.value = items.value.map((m) => (m.user.id === userId ? { ...m, role, managerPermissions: permissions } : m));
}

/** 임명·해임·맡은 일 바꾸기·인원 설정 뒤 매니저 수·권한을 다시 맞추고, 받아 둔 매니저 목록도 새로 받는다 */
async function refreshManagers() {
    await recheck();
    if (filter.value === 'managers' || managerStatus.value === 'ready') void loadManagers(true);
}

/** 쓰기 실패의 공통 갈래 — 쓰기 잠금 · 맡은 일이 빠졌다 · 모임장도 매니저도 아니게 됐다 */
async function onGuardFailure(e: unknown): Promise<boolean> {
    const blocked = UtilGroup.blockedReasonOf(e);
    if (blocked) {
        block(blocked);
        toast.error(UtilBoardMessages.of(t, e), 8000);
        return true;
    }
    if (UtilGroupManager.isPermissionDenied(e)) {
        toast.error(t('groupManager.revoked'), 8000);
        await recheck();
        return true;
    }
    if (UtilGroupManager.isLeaderOnly(e)) {
        toast.error(t('groupManager.demoted'), 8000);
        await recheck();
        return true;
    }
    return false;
}

/* ── ⋯ 시트 ── */
type MemberAction = 'profile' | 'kick' | 'transfer' | 'appoint' | 'edit' | 'remove';
const menuOpen = ref(false);
const menuTarget = ref<GroupMember | null>(null);
const acting = ref(false);

/** 시트가 강퇴를 보인다 — 모임장은 모임장 줄을 뺀 모두(매니저도 — 자격이 같이 사라진다), 매니저는 `KICK` 이 있고 대상이 일반 멤버일 때 */
const menuCanKick = computed(() => {
    const m = menuTarget.value;
    if (!m) return false;
    if (isLeader.value) return m.role !== 'LEADER';
    return Boolean(group.value?.permissions.canKickMembers) && m.role === 'MEMBER';
});

function openMenu(member: GroupMember) {
    if (acting.value) return;
    menuTarget.value = member;
    menuOpen.value = true;
}

async function onAction(action: MemberAction) {
    const member = menuTarget.value;
    const userId = member?.user.id;
    menuOpen.value = false;
    if (!member || !userId || acting.value) return;
    const name = member.user.nickname;
    if (action === 'profile') {
        await navigateTo(UtilProfile.path(userId));
        return;
    }
    if (action === 'appoint' || action === 'edit') {
        openPermissions(member, action);
        return;
    }
    if (action === 'remove') {
        await removeManager(member);
        return;
    }
    if (action === 'kick') {
        // 매니저가 하면 모임장에게도 알림이 간다 — 확인 문구에 알린다
        const message = isLeader.value ? t('group.kickMessage') : `${t('group.kickMessage')} ${t('groupManager.kickNotice')}`;
        if (!(await confirm({ title: t('group.kickTitle', { name }), message, confirmLabel: t('group.menuKick'), tone: 'danger' }))) return;
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
        const wasManager = managerItems.value.some((m) => m.user.id === userId) || items.value.some((m) => m.user.id === userId && m.role === 'MANAGER');
        removeMember(userId);
        toast.success(t('group.kickedToast', { name }), 5000);
        if (wasManager) void refreshManagers(); // 매니저를 강퇴했으면 매니저 수가 줄었다
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_MEMBER_NOT_FOUND')) {
            removeMember(userId);
            toast.show(t('group.memberGone'), { life: 5000 });
            return;
        }
        // 매니저끼리·모임장은 강퇴할 수 없다 — 서버 문구 그대로
        if (UtilGroupManager.isProtected(e)) {
            toast.error(UtilBoardMessages.of(t, e), 8000);
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

/* ── 임명 · 맡은 일 바꾸기(같은 체크 시트) ── */
const permOpen = ref(false);
const permMode = ref<'appoint' | 'edit'>('appoint');
const permTarget = ref<GroupMember | null>(null);
const permOriginal = ref<GroupManagerPermission[]>([]);
const permSelected = ref<GroupManagerPermission[]>([]);
const permBusy = ref(false);
const permError = ref('');

const permOptions = computed(() => UtilGroupManagerMessages.options(t));
const permTitle = computed(() => {
    const name = permTarget.value?.user.nickname ?? '';
    return permMode.value === 'appoint' ? t('groupManager.appointTitle', { name }) : t('groupManager.editTitle', { name });
});
const permFull = computed(() => permMode.value === 'appoint' && UtilGroupManager.isFull(group.value?.managerInfo));
const permCleared = computed(() => permMode.value === 'edit' && UtilGroupManager.clearedAll(permSelected.value, permOriginal.value));
const permCanSubmit = computed(() => {
    if (permBusy.value || group.value?.blockedReason) return false;
    return permMode.value === 'appoint' ? UtilGroupManager.canAppoint(permSelected.value, permFull.value) : UtilGroupManager.canSaveChange(permSelected.value, permOriginal.value);
});

function openPermissions(member: GroupMember, mode: 'appoint' | 'edit') {
    permTarget.value = member;
    permMode.value = mode;
    permOriginal.value = mode === 'edit' ? UtilGroupManager.normalize(member.managerPermissions ?? []) : [];
    permSelected.value = [...permOriginal.value];
    permError.value = '';
    permOpen.value = true;
}

async function submitPermissions() {
    const target = permTarget.value;
    const userId = target?.user.id;
    if (!target || !userId || !permCanSubmit.value) return;
    const name = target.user.nickname;
    const permissions = [...permSelected.value];
    const appointing = permMode.value === 'appoint';
    permBusy.value = true;
    permError.value = '';
    try {
        if (!appointing) {
            // 같은 API 가 임명도 하므로 그 사이 해임·탈퇴된 사람을 되살리지 않게 먼저 지금 매니저인지 본다
            const list = await api.managers(id);
            if (!list.items.some((m) => m.user.id === userId)) {
                permOpen.value = false;
                setRole(userId, 'MEMBER', null);
                managerItems.value = list.items;
                toast.show(t('groupManager.notManagerAnymore'), { life: 5000 });
                await recheck();
                return;
            }
        }
        await api.setManager(id, userId, permissions);
        permOpen.value = false;
        setRole(userId, 'MANAGER', permissions);
        toast.success(appointing ? t('groupManager.appointedToast', { name }) : t('groupManager.changedToast', { name }), 5000);
        await refreshManagers();
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        if (UtilGroup.isCode(e, 'GROUP_MEMBER_NOT_FOUND')) {
            permOpen.value = false;
            removeMember(userId);
            toast.show(t('group.memberGone'), { life: 5000 });
            return;
        }
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        // 인원이 찼거나(`409 GROUP_MANAGER_LIMIT_REACHED` — 최상위 `limit`) 매니저 기능이 꺼졌거나 모임장이 아니게 됐다 — 화면이 보던 값이 낡았으니 다시 맞춘다
        if (UtilGroupManager.isLimitReached(e) || UtilGroupManager.isDisabled(e) || UtilGroupManager.isLeaderOnly(e)) void recheck();
        permError.value = e instanceof ApiError ? (e.field('permissions') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        permBusy.value = false;
    }
}

/* ── 해임 ── */
async function removeManager(member: GroupMember) {
    const userId = member.user.id;
    if (!userId || acting.value) return;
    const name = member.user.nickname;
    if (!(await confirm({ ...UtilGroupManagerMessages.removeConfirm(t, name), tone: 'danger' }))) return;
    acting.value = true;
    try {
        await api.removeManager(id, userId);
        setRole(userId, 'MEMBER', null);
        toast.success(t('groupManager.removedToast', { name }), 5000);
        await refreshManagers();
    } catch (e) {
        if (UtilGroup.isSessionLost(e) || (await onGuardFailure(e))) return;
        toast.error(UtilGroupMessages.failed(t, 'groupManager.removeFailed', e), 8000);
    } finally {
        acting.value = false;
    }
}

/** 맡은 일 바꾸기 시트에서 마지막 체크를 풀고 누른 [매니저 해임] */
function removeFromSheet() {
    const member = permTarget.value;
    permOpen.value = false;
    if (member) void removeManager(member);
}

/* ── 인원 설정 ── */
const limitOpen = ref(false);
const limitBusy = ref(false);
const limitError = ref('');
const limitValue = ref(1);
const limitHint = computed(() => (group.value?.managerInfo ? UtilGroupManagerMessages.limitBoundHint(t, limitValue.value, group.value.managerInfo) : ''));

function openLimit() {
    limitError.value = '';
    limitValue.value = group.value?.managerInfo?.limit ?? 1;
    limitOpen.value = true;
}

/** 임명 시트의 [인원 바꾸기] — 시트를 닫고 인원 설정을 연다 */
function limitFromAppoint() {
    permOpen.value = false;
    openLimit();
}

async function submitLimit() {
    const info = group.value?.managerInfo;
    if (!info || limitBusy.value || limitValue.value === info.limit || group.value?.blockedReason) return;
    const limit = limitValue.value;
    limitBusy.value = true;
    limitError.value = '';
    try {
        await api.setManagerLimit(id, limit);
        limitOpen.value = false;
        toast.success(t('groupManager.limitSavedToast', { n: limit }), 5000);
        await refreshManagers();
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        if (UtilGroupManager.isLeaderOnly(e)) void recheck();
        // 서버 `400 fields.limit`(group.manager.limit.range|overMax|belowCount) — 문구 그대로
        limitError.value = e instanceof ApiError ? (e.field('limit') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        limitBusy.value = false;
    }
}
</script>
