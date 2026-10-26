<template>
    <!--
        소모임 관리 `/groups/{id}/manage` — **모임장과 매니저**(상세의 `permissions.canManage`, 아니면 틀이 「모임장 또는 매니저만」 — 서버도 `403 GROUP_LEADER_ONLY`).
        - 모임장: 머리(이름·모임장·멤버 수) · 멤버(가입 신청 대기 수 · 멤버 관리 · 매니저 n/limit명 → 멤버 관리 `?filter=managers`) · 소모임(정보 수정 · 채팅 링크 · 모집 켜기/끄기) ·
          위험 구역(삭제·해산 — 이름을 그대로 입력).
        - 매니저: 머리에 「나는 매니저예요 · 맡은 일: …」. 맡겨진 체크형 일(가입 신청·멤버 관리·모집)만 열린 줄이고 **맡겨지지 않은 줄은 잠금 줄**(누르면 「모임장이 맡기지 않은 일이에요」 시트).
          모임장 고정 줄(정보 수정·채팅 링크·매니저·해산) 자리에 점선 안내, 맨 아래 「내 역할 › 매니저에서 물러나기」(확인 시트 → `DELETE …/managers/me`, 성공하면 소모임 상세로 — 기록 교체).
        - 정지·이메일 인증 전이면 띠 + 쓰기 단추가 꺼진다(목록은 열린다). 물러나기는 사업자 계정이어도 된다(서버 `GroupManagerService.resign` 이 막지 않는다 — 탈퇴와 같은 규칙).
        - 열어 둔 채 맡은 일이 빠지면(`403 GROUP_MANAGER_PERMISSION_DENIED`) 토스트 + 권한을 다시 받아 줄이 잠금으로 바뀐다. 해임당했으면(`GROUP_LEADER_ONLY`) 「모임장 또는 매니저만」 상태가 된다.
        community 대비: 「글 › 공개 요청」·「공개 범위」 줄은 5단계라 뺐다.
    -->
    <GroupManageShell :title="t('group.manageTitle')" :state="state" :error-message="errorMessage" :group-id="id" :back-to="UtilGroup.detailPath(id)" :back-label="t('group.backToGroup')" :blocked-reason="group?.blockedReason ?? null" @retry="load()">
        <template v-if="group">
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="group-manage-head">
                <div class="flex items-center gap-3">
                    <GroupInitial :name="group.name" size="lg" />
                    <div class="min-w-0 flex-1">
                        <h2 class="break-words text-base font-semibold leading-snug [overflow-wrap:anywhere]">{{ group.name }}</h2>
                        <p class="mt-0.5 flex min-w-0 flex-wrap items-center gap-x-1 text-xs text-muted-color">
                            <template v-if="group.leader">
                                <span>{{ t('group.roleLeader') }}</span>
                                <PostAuthorLabel :author="UtilGroup.authorOf(group.leader, t('group.withdrawn'))" />
                                <span aria-hidden="true">·</span>
                            </template>
                            <span>{{ UtilGroupMessages.members(t, group) }}</span>
                        </p>
                    </div>
                </div>
                <!-- 매니저에게는 「나는 매니저예요」 · 맡은 일 두 줄 -->
                <div v-if="!isLeader" class="mt-3 border-t border-surface pt-3" data-testid="group-manage-my-role">
                    <p class="m-0 flex items-center gap-1.5 text-sm">
                        <GroupTag tone="sky">{{ t('group.roleManager') }}</GroupTag>
                        <span>{{ t('groupManager.headRole') }}</span>
                    </p>
                    <p class="m-0 mt-1 text-xs text-muted-color" data-testid="group-manage-my-granted">{{ UtilGroupManagerMessages.headGranted(t, granted) }}</p>
                </div>
            </section>

            <section class="mt-4" :aria-labelledby="`${uid}-members`">
                <h2 :id="`${uid}-members`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('group.sectionMembers') }}</h2>
                <div class="overflow-hidden rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <NuxtLink v-if="group.permissions.canHandleApplications" :to="UtilGroup.managePath(id, 'applications')" :class="ROW" data-testid="group-manage-applications">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('group.rowApplications') }}</span>
                            <small class="block text-xs text-muted-color">{{ t('group.rowApplicationsHint') }}</small>
                        </span>
                        <span
                            v-if="applicationCount !== null && applicationCount > 0"
                            class="inline-flex shrink-0 items-center rounded-full bg-red-100 px-2 py-1 text-xs font-semibold leading-none text-red-700 dark:bg-red-900/40 dark:text-red-300"
                            :aria-label="t('group.applicationsCountAria', { n: applicationCount })"
                            >{{ applicationCount }}</span
                        >
                        <span v-else-if="applicationCount === 0" class="shrink-0 text-xs text-muted-color">0</span>
                        <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                    <button v-else type="button" :class="[ROW, 'text-muted-color']" data-testid="group-manage-applications-locked" @click="openLocked('APPLICATIONS')">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('group.rowApplications') }}</span>
                            <small class="block text-xs">{{ t('groupManager.lockedHint') }}</small>
                        </span>
                        <i class="pi pi-lock shrink-0" aria-hidden="true" />
                    </button>

                    <NuxtLink v-if="group.permissions.canKickMembers" :to="UtilGroup.managePath(id, 'members')" :class="ROW" data-testid="group-manage-members">
                        <span class="min-w-0 flex-1 text-sm font-medium">{{ t('group.rowMembers') }}</span>
                        <span class="shrink-0 text-xs text-muted-color">{{ t('group.peopleCount', { n: group.memberCount }) }}</span>
                        <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                    <button v-else type="button" :class="[ROW, 'text-muted-color']" data-testid="group-manage-members-locked" @click="openLocked('KICK')">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('group.rowMembers') }}</span>
                            <small class="block text-xs">{{ t('groupManager.lockedHint') }}</small>
                        </span>
                        <i class="pi pi-lock shrink-0" aria-hidden="true" />
                    </button>

                    <!-- 매니저 줄 — 모임장만. 누르면 멤버 관리가 매니저 거르기로 열린다 -->
                    <NuxtLink v-if="isLeader && group.managerInfo" :to="managersPath" :class="ROW" data-testid="group-manage-managers">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('groupManager.rowManagers') }}</span>
                            <small class="block text-xs text-muted-color">{{ t('groupManager.rowManagersHint') }}</small>
                        </span>
                        <span class="shrink-0 text-xs text-muted-color" data-testid="group-manage-managers-count">{{ t('groupManager.rowValue', { count: group.managerInfo.count, limit: group.managerInfo.limit }) }}</span>
                        <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                </div>
            </section>

            <section class="mt-4" :aria-labelledby="`${uid}-group`">
                <h2 :id="`${uid}-group`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('group.title') }}</h2>
                <div class="overflow-hidden rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <template v-if="group.permissions.canEditGroup">
                        <NuxtLink :to="UtilGroup.managePath(id, 'edit')" :class="ROW" data-testid="group-manage-edit">
                            <span class="min-w-0 flex-1 text-sm font-medium">{{ t('group.editTitle') }}</span>
                            <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                        </NuxtLink>
                        <NuxtLink :to="UtilGroup.managePath(id, 'chat')" :class="ROW" data-testid="group-manage-chat">
                            <span class="min-w-0 flex-1">
                                <span class="block text-sm font-medium">{{ t('group.chatTitle') }}</span>
                                <small class="block text-xs text-muted-color">{{ UtilGroupMessages.chatSummary(t, group.chatLinks) }}</small>
                            </span>
                            <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                        </NuxtLink>
                    </template>
                    <div v-if="group.permissions.canSetRecruiting" class="flex min-h-14 items-center gap-3 border-b border-surface px-4 py-2.5 last:border-b-0">
                        <label :for="`${uid}-recruit`" class="min-w-0 flex-1 cursor-pointer">
                            <span class="block text-sm font-medium">{{ t('group.rowRecruiting') }}</span>
                            <small class="block text-xs text-muted-color">{{ group.recruiting ? t('group.recruitingOnHint') : t('group.recruitingOffHint') }}</small>
                        </label>
                        <ToggleSwitch
                            :model-value="group.recruiting"
                            :input-id="`${uid}-recruit`"
                            :disabled="savingRecruiting || Boolean(group.blockedReason)"
                            data-testid="group-manage-recruiting"
                            @update:model-value="onRecruiting(Boolean($event))"
                        />
                    </div>
                    <button v-else type="button" :class="[ROW, 'text-muted-color']" data-testid="group-manage-recruiting-locked" @click="openLocked('RECRUITING')">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('group.rowRecruiting') }}</span>
                            <small class="block text-xs">{{ t('groupManager.lockedHint') }}</small>
                        </span>
                        <i class="pi pi-lock shrink-0" aria-hidden="true" />
                    </button>
                </div>
                <!-- 모임장만 하는 일이 숨은 자리 — 점선 안내 -->
                <p v-if="!group.permissions.canEditGroup" class="m-0 mt-2 rounded-lg border border-dashed border-surface px-3 py-2.5 text-xs leading-relaxed text-muted-color" data-testid="group-manage-leader-only-box">
                    {{ t('groupManager.leaderOnlyBox') }}
                </p>
            </section>

            <!-- 내 역할 — 매니저에서 물러나기(매니저에게만) -->
            <section v-if="!isLeader" class="mt-4" :aria-labelledby="`${uid}-role`">
                <h2 :id="`${uid}-role`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('groupManager.roleSection') }}</h2>
                <div class="overflow-hidden rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <button type="button" :class="[ROW, resignBlocked ? 'cursor-not-allowed opacity-60' : '']" :disabled="resignBlocked" data-testid="group-manage-resign" @click="openResign">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('groupManager.resignRow') }}</span>
                            <small class="block text-xs text-muted-color">{{ t('groupManager.resignHint') }}</small>
                        </span>
                        <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                    </button>
                </div>
            </section>

            <section v-if="group.permissions.canEditGroup" class="mt-4" :aria-labelledby="`${uid}-danger`">
                <h2 :id="`${uid}-danger`" class="px-1 pb-1.5 text-xs font-semibold text-muted-color">{{ t('group.sectionDanger') }}</h2>
                <div class="overflow-hidden rounded-xl border border-surface bg-surface-0 dark:bg-surface-900">
                    <button type="button" :class="[ROW, group.blockedReason ? 'cursor-not-allowed opacity-60' : '']" :disabled="Boolean(group.blockedReason)" data-testid="group-manage-dissolve" @click="openDissolve">
                        <span class="min-w-0 flex-1 text-sm font-medium text-red-600 dark:text-red-400">{{ t('group.rowDissolve') }}</span>
                        <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                    </button>
                </div>
            </section>

            <!-- 해산 확인 — 소모임 이름을 정확히 입력해야 「삭제하기」가 켜진다 -->
            <AppSheet v-model:visible="dissolveOpen" :title="t('group.dissolveTitle')" :description="UtilGroupMessages.dissolveDescription(t, Math.max(0, group.memberCount - 1))">
                <form class="mt-3 px-5" novalidate @submit.prevent="dissolve">
                    <label :for="`${uid}-dissolve`" class="text-sm font-medium">{{ t('group.dissolveInput') }}</label>
                    <p class="mt-1 break-words text-xs text-muted-color [overflow-wrap:anywhere]">
                        {{ t('group.dissolveNameLabel') }} <b class="text-color">{{ group.name }}</b>
                    </p>
                    <InputText
                        :id="`${uid}-dissolve`"
                        v-model="dissolveTyped"
                        name="name"
                        autocomplete="off"
                        fluid
                        class="mt-1.5"
                        :placeholder="group.name"
                        :invalid="Boolean(dissolveError)"
                        :disabled="dissolving"
                        data-testid="dissolve-input"
                    />
                    <p v-if="dissolveError" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ dissolveError }}</p>
                    <div class="mt-4 flex gap-2">
                        <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="dissolving" @click="dissolveOpen = false" />
                        <Button type="submit" :label="t('group.dissolveConfirm')" severity="danger" class="flex-1" :loading="dissolving" :disabled="!dissolveReady" data-testid="dissolve-confirm" />
                    </div>
                </form>
            </AppSheet>

            <!-- 맡겨지지 않은 일을 눌렀을 때의 안내 -->
            <AppSheet v-model:visible="lockedOpen" :title="lockedCopy.title" :description="lockedCopy.text">
                <div class="mt-3 px-5" data-testid="group-manage-locked-sheet">
                    <Button type="button" :label="t('groupManager.lockedOk')" fluid @click="lockedOpen = false" />
                </div>
            </AppSheet>

            <!-- 매니저에서 물러나기 확인 — 일반 멤버가 되고 모임장에게 알림이 간다 -->
            <AppSheet v-if="!isLeader" v-model:visible="resignOpen" :title="t('groupManager.resignTitle')" :description="t('groupManager.resignDescription')">
                <div class="mt-3 px-5" data-testid="resign-sheet">
                    <p v-if="resignError" role="alert" class="m-0 mb-3 text-xs text-red-600 dark:text-red-400">{{ resignError }}</p>
                    <div class="flex gap-2">
                        <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="resigning" @click="resignOpen = false" />
                        <Button
                            type="button"
                            :label="t('groupManager.resignConfirm')"
                            severity="danger"
                            class="flex-1"
                            :loading="resigning"
                            :disabled="resigning || resignBlocked"
                            :title="resignBlocked && group.blockedReason ? UtilGroupManagerMessages.blockedShort(t, group.blockedReason) : undefined"
                            data-testid="resign-confirm"
                            @click="resign"
                        />
                    </div>
                </div>
            </AppSheet>
        </template>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupManagerPermission } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupManager from '~/utils/util-group-manager';
import UtilGroupManagerMessages from '~/utils/util-group-manager-messages';
import UtilGroupMessages from '~/utils/util-group-messages';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.manageTitle'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const { state, errorMessage, group, load, patch, block, recheck } = useGroupManage(id);

const ROW =
    'flex min-h-14 w-full items-center gap-3 border-b border-surface px-4 py-2.5 text-left last:border-b-0 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';

/** 모임장인가 — 서버의 `myRole`, 옛 응답은 모임장 고정 권한으로 읽는다 */
const isLeader = computed(() => {
    const g = group.value;
    if (!g) return false;
    return g.myRole === 'LEADER' || (g.myRole !== 'MANAGER' && g.permissions.canEditGroup);
});
/** 내가 맡은 일(고정 순서) — 모임장이면 다섯 모두 */
const granted = computed(() => (group.value ? UtilGroupManager.grantedOf(group.value.permissions) : []));
const managersPath = computed(() => `${UtilGroup.managePath(id, 'members')}?filter=managers`);

/* ── 가입 신청 대기 수 — 상세의 `pendingApplicationCount`, 없으면 신청 목록을 받아 센다 ── */
const fetchedCount = ref<number | null>(null);
const applicationCount = computed(() => group.value?.pendingApplicationCount ?? fetchedCount.value);
watch(
    () => state.value === 'ready' && group.value?.permissions.canHandleApplications === true && group.value.pendingApplicationCount === null,
    async (need) => {
        if (!need) return;
        try {
            fetchedCount.value = (await api.applications(id)).items.length;
        } catch {
            fetchedCount.value = null;
        }
    },
    { immediate: true },
);

/** 쓰기 실패의 공통 갈래 — 쓰기 잠금(정지·인증 전) · 맡은 일이 빠졌다 · 모임장도 매니저도 아니게 됐다(넘겼거나 해임·강퇴) */
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

/* ── 모집 켜기·끄기 — 먼저 바꿔 보이고 실패하면 되돌린다 ── */
const savingRecruiting = ref(false);
async function onRecruiting(next: boolean) {
    const current = group.value;
    if (!current || savingRecruiting.value || current.recruiting === next || current.blockedReason) return;
    savingRecruiting.value = true;
    patch({ recruiting: next });
    try {
        await api.setRecruiting(id, next);
        toast.success(next ? t('group.recruitingOnToast') : t('group.recruitingOffToast'), 4000);
    } catch (e) {
        patch({ recruiting: !next });
        if (UtilGroup.isSessionLost(e) || (await onGuardFailure(e))) return;
        toast.error(UtilGroupMessages.failed(t, 'group.recruitingFailed', e), 8000);
    } finally {
        savingRecruiting.value = false;
    }
}

/* ── 잠금 줄 안내 ── */
const lockedOpen = ref(false);
const lockedKey = ref<GroupManagerPermission>('APPLICATIONS');
const lockedCopy = computed(() => UtilGroupManagerMessages.lockedSheet(t, lockedKey.value));
function openLocked(key: GroupManagerPermission) {
    lockedKey.value = key;
    lockedOpen.value = true;
}

/* ── 매니저에서 물러나기 — 정지·이메일 미인증은 막히고 사업자 계정은 된다(서버 `GroupManagerService.resign`) ── */
const resignOpen = ref(false);
const resigning = ref(false);
const resignError = ref('');
const resignBlocked = computed(() => {
    const reason = group.value?.blockedReason;
    return reason === 'EMAIL_NOT_VERIFIED' || reason === 'ACCOUNT_RESTRICTED';
});

function openResign() {
    if (resignBlocked.value) return;
    resignError.value = '';
    resignOpen.value = true;
}

async function resign() {
    if (resigning.value || resignBlocked.value) return;
    resigning.value = true;
    resignError.value = '';
    try {
        await api.resignManager(id);
        resignOpen.value = false;
        toast.success(t('groupManager.resignedToast'), 5000);
        await navigateTo(UtilGroup.detailPath(id), { replace: true });
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        resignError.value = UtilBoardMessages.of(t, e);
    } finally {
        resigning.value = false;
    }
}

/* ── 해산 ── */
const dissolveOpen = ref(false);
const dissolving = ref(false);
const dissolveError = ref('');
const dissolveTyped = ref('');
const dissolveReady = computed(() => !dissolving.value && UtilGroup.dissolveReady(dissolveTyped.value, group.value?.name ?? ''));

watch(dissolveTyped, () => (dissolveError.value = ''));

function openDissolve() {
    dissolveError.value = '';
    dissolveTyped.value = '';
    dissolveOpen.value = true;
}

async function dissolve() {
    if (!dissolveReady.value) return;
    dissolving.value = true;
    dissolveError.value = '';
    try {
        await api.dissolve(id, UtilGroup.normalizeName(dissolveTyped.value));
        dissolveOpen.value = false;
        toast.success(t('group.dissolvedToast'), 5000);
        await navigateTo(UtilGroup.MY_PATH, { replace: true });
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        dissolveError.value = e instanceof ApiError ? (e.field('name') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        dissolving.value = false;
    }
}
</script>
