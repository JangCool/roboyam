<template>
    <!--
        조직 구성 — 팀을 만들고 팀원을 정한다. 팀은 알림 이벤트의 「추가 수신 팀」으로 쓴다(`/settings/notifications/operator`). hamonica3 `settings/application/teams.vue` 와 같은 짜임이다:
        왼쪽에 팀 목록(검색 · 새 팀 · 위·아래 화살표 · 「사용 안 함」 딱지), 오른쪽에 고른 팀의 정보(직접 고친다)와 팀원.
          · 팀 = 이름 · 설명 · 사용 여부 · 정렬 순서. **순서는 숫자를 적지 않고 위·아래 화살표로만** 옮긴다(서버가 다시 매긴다). 검색 중에도 끝 판정은 전체 목록 기준이다.
          · 팀원 = **관리자 운영자 계정**. 팀원 추가는 다이얼로그에서 운영자를 이름·이메일로 찾아 여러 명 고르고, 빼기는 한 명씩 확인 후.
          · **팀원이 있는 팀은 지울 수 없다** — 삭제 단추가 잠기고 「사용 안 함」으로 멈추라고 안내한다. 멈춘 팀은 알림을 받지 않는다.
          · 팀 정보는 모달이 아니라 오른쪽에서 직접 고치고 [저장]은 이 구획 안에 둔다(구획마다 따로 저장). 새 팀은 다이얼로그로 만든다.
        로딩 · 빈 결과(`EmptyState`) · 오류(`ErrorState`)를 모두 그린다. 저장하지 않은 변경이 있는 채 다른 팀을 고르거나 화면을 나가면 확인을 묻는다.
        권한: 화면 `team.read` (메뉴 정의가 걸러 낸다) · 만들기·고치기·지우기·옮기기·팀원 관리 `team.manage` — 없으면 단추가 숨고 읽기 전용이다. 감추는 것은 편의이고 막는 것은 서버다.
        팀원이 사용 중지·잠금·기간 만료 등으로 지금 로그인할 수 없으면(`active:false`) 팀원으로 남아 있되 「비활성」 딱지가 붙는다 — 알림은 받지 않는다.
        서버 API: `useTeamApi`(`docs/features/notification-contract.md` 「서버 구현 결과 (알림 도구·조직 구성)」). 서버 문구(`message`·`fields`)를 그대로 보인다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('team.title')" :subtitle="t('team.subtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다 -->
            <template #actions>
                <Button :label="t('team.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load()" />
            </template>
        </PageTitle>

        <div v-if="loading && !teams" class="grid gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]" aria-busy="true">
            <span class="sr-only">{{ t('common.loading') }}</span>
            <div class="rounded-xl border border-(--line) p-5">
                <Skeleton width="40%" height="1rem" class="mb-4" />
                <Skeleton height="2.25rem" class="mb-3" />
                <Skeleton v-for="n in 4" :key="n" height="2.75rem" class="mb-2" />
            </div>
            <div class="rounded-xl border border-(--line) p-5">
                <Skeleton width="30%" height="1rem" class="mb-4" />
                <Skeleton height="2.25rem" width="min(24rem, 100%)" class="mb-3" />
                <Skeleton height="5rem" />
            </div>
        </div>

        <ErrorState v-else-if="error" :title="t('team.loadFailed')" :description="error.message" retry :retrying="loading" @retry="load()" />

        <AppGadget v-else-if="teams && !teams.length" :title="t('team.title')">
            <EmptyState icon="pi pi-users" :title="t('team.emptyTitle')" :description="t('team.emptyBody')">
                <Button v-if="canManage" :label="t('team.newTeam')" icon="pi pi-plus" @click="openCreate" />
            </EmptyState>
        </AppGadget>

        <template v-else-if="teams">
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('team.readOnly') }}</Message>

            <div class="flex flex-col gap-4 lg:flex-row lg:items-start">
                <!-- 왼쪽 — 팀 목록. 3단으로 설 때만 폭을 고정한다 -->
                <AppGadget :title="t('team.listTitle', { n: teams.length })" class="lg:w-80 lg:shrink-0">
                    <template v-if="canManage" #actions>
                        <Button :label="t('team.newTeam')" icon="pi pi-plus" size="small" variant="text" @click="openCreate" />
                    </template>

                    <IconField class="mb-3">
                        <InputIcon class="pi pi-search" />
                        <InputText v-model="keyword" size="small" fluid :placeholder="t('team.searchTeam')" :aria-label="t('team.searchTeam')" autocomplete="off" />
                    </IconField>

                    <ul v-if="filteredTeams.length" class="m-0 flex list-none flex-col gap-0.5 p-0">
                        <li v-for="team in filteredTeams" :key="team.teamId">
                            <div class="flex items-center gap-1 rounded-md pr-1" :class="team.teamId === selectedId ? 'bg-primary-50 dark:bg-primary-950' : 'hover:bg-surface-100 dark:hover:bg-surface-800'">
                                <button type="button" class="min-w-0 flex-1 cursor-pointer rounded-md px-2.5 py-2 text-left focus-visible:outline-2 focus-visible:outline-primary" :aria-current="team.teamId === selectedId ? 'true' : undefined" @click="select(team.teamId)">
                                    <!-- 이름 옆의 딱지 줄은 flex + items-center 로 — 베이스라인에 맡기면 딱지가 글자 줄 아래로 처진다 -->
                                    <span class="flex items-center gap-1.5">
                                        <span class="min-w-0 flex-1 truncate text-[13px] font-medium" :class="team.teamId === selectedId ? 'text-primary-700 dark:text-primary-300' : ''">{{ team.teamName }}</span>
                                        <!-- 멈춘 팀은 목록에서 바로 알아야 한다 -->
                                        <Tag v-if="!team.enabled" class="team-tag" :value="t('team.stopped')" severity="secondary" />
                                    </span>
                                    <span class="mt-0.5 block truncate text-[11px] text-muted-color">
                                        {{ t('team.memberCount', { n: team.members.length }) }}<template v-if="team.description"> · {{ team.description }}</template>
                                    </span>
                                </button>

                                <!-- 순서는 화살표로만 옮긴다 — 숫자를 적어 넣게 하면 같은 번호가 둘 생기고 가운데를 지우면 번호가 빈다 -->
                                <span v-if="canManage" class="flex shrink-0 flex-col">
                                    <button
                                        type="button"
                                        class="cursor-pointer px-1 text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-default disabled:opacity-30"
                                        :disabled="!UtilTeam.canMove(teams, team.teamId, 'UP') || saving"
                                        :aria-label="t('team.moveUp', { name: team.teamName })"
                                        @click="move(team, 'UP')"
                                    >
                                        <i class="pi pi-chevron-up text-[10px]" aria-hidden="true" />
                                    </button>
                                    <button
                                        type="button"
                                        class="cursor-pointer px-1 text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-default disabled:opacity-30"
                                        :disabled="!UtilTeam.canMove(teams, team.teamId, 'DOWN') || saving"
                                        :aria-label="t('team.moveDown', { name: team.teamName })"
                                        @click="move(team, 'DOWN')"
                                    >
                                        <i class="pi pi-chevron-down text-[10px]" aria-hidden="true" />
                                    </button>
                                </span>
                            </div>
                        </li>
                    </ul>
                    <p v-else class="m-0 py-4 text-center text-xs text-muted-color">{{ t('team.noMatch') }}</p>
                </AppGadget>

                <!-- 오른쪽 — 고른 팀 -->
                <div v-if="selected" class="flex min-w-0 flex-1 flex-col gap-4">
                    <!-- 팀 정보 — 모달이 아니라 여기서 직접 고친다. [저장]은 이 구획 안에 둔다(구획마다 따로 저장) -->
                    <AppGadget :title="t('team.infoTitle')">
                        <template v-if="canManage" #actions>
                            <!-- 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                            <span v-tooltip.top="selected.members.length > 0 ? t('team.deleteBlocked') : undefined" class="inline-flex">
                                <Button :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="selected.members.length > 0 || saving" @click="removeTeam(selected)" />
                            </span>
                        </template>

                        <form :key="selected.teamId" class="flex max-w-xl flex-col gap-4" novalidate @submit.prevent="saveInfo">
                            <LabeledField :label="t('team.fieldName')" :hint="t('team.nameHint', { max: UtilTeam.NAME_MAX })" :error="nameError || infoFieldErrors.teamName">
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" v-model="form.teamName" name="teamName" size="small" :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                                </template>
                            </LabeledField>

                            <LabeledField :label="t('team.fieldDescription')" :hint="t('team.descHint', { max: UtilTeam.DESC_MAX })" :error="descError || infoFieldErrors.description">
                                <template #default="{ id, describedBy, invalid }">
                                    <Textarea :id="id" v-model="form.description" name="description" rows="3" auto-resize :readonly="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>

                            <div class="flex items-start justify-between gap-4">
                                <div class="min-w-0">
                                    <label :for="enabledId" class="text-sm font-medium">{{ t('team.fieldStatus') }}</label>
                                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ form.enabled ? t('team.statusOnHint') : t('team.statusOffHint') }}</p>
                                    <p v-if="selected.members.length > 0" class="m-0 mt-0.5 text-xs text-muted-color">{{ t('team.deleteBlocked') }}</p>
                                </div>
                                <ToggleSwitch v-model="form.enabled" class="shrink-0" :input-id="enabledId" :disabled="!canManage || saving" />
                            </div>

                            <Message v-if="infoError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ infoError }}</Message>

                            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                                <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || !dirty" @click="fill()" />
                                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="!dirty" />
                            </div>
                        </form>
                    </AppGadget>

                    <!-- 팀원 -->
                    <AppGadget :title="t('team.membersTitle', { n: selected.members.length })">
                        <template v-if="canManage" #actions>
                            <Button :label="t('team.addMember')" icon="pi pi-user-plus" size="small" :disabled="saving" @click="openAddMembers" />
                        </template>

                        <template v-if="selected.members.length">
                            <IconField class="mb-3 sm:max-w-xs">
                                <InputIcon class="pi pi-search" />
                                <InputText v-model="memberKeyword" size="small" fluid :placeholder="t('team.searchMember')" :aria-label="t('team.searchMember')" autocomplete="off" />
                            </IconField>

                            <ul v-if="filteredMembers.length" class="m-0 list-none divide-y divide-(--line) p-0">
                                <li v-for="member in filteredMembers" :key="member.operatorId" class="flex items-center gap-3 py-2.5">
                                    <UserAvatar :name="member.operatorName" />
                                    <span class="min-w-0 flex-1">
                                        <!-- 이름 옆의 딱지 줄은 flex + items-center 로 — 베이스라인에 맡기면 딱지가 글자 줄 아래로 처진다 -->
                                        <span class="flex items-center gap-1.5">
                                            <span class="min-w-0 truncate text-[13px] font-medium">{{ member.operatorName }}</span>
                                            <!-- 지금 로그인할 수 없는 계정은 팀원으로 남아도 알림을 받지 않는다 -->
                                            <Tag v-if="!member.active" v-tooltip.top="t('team.memberInactiveHint')" class="team-tag" :value="t('team.memberInactive')" severity="warn" />
                                        </span>
                                        <span class="block truncate text-[11px] text-muted-color">{{ member.email }}</span>
                                    </span>
                                    <Button v-if="canManage" icon="pi pi-times" size="small" severity="danger" variant="text" rounded :disabled="saving" :aria-label="t('team.removeMember', { name: member.operatorName })" @click="removeMember(selected, member)" />
                                </li>
                            </ul>
                            <p v-else class="m-0 py-4 text-center text-xs text-muted-color">{{ t('team.noMatchMember') }}</p>
                        </template>

                        <EmptyState v-else icon="pi pi-user-plus" :title="t('team.noMembersTitle')" :description="canManage ? t('team.noMembersBody') : ''" />
                    </AppGadget>
                </div>

                <!-- 고른 것이 없을 때 — 빈 상자를 두지 않고 무엇을 하면 되는지 적는다 -->
                <AppGadget v-else :title="t('team.infoTitle')" class="min-w-0 flex-1">
                    <EmptyState icon="pi pi-users" :title="t('team.pickOneTitle')" :description="t('team.pickOneBody')" />
                </AppGadget>
            </div>
        </template>

        <TeamFormDialog v-model:visible="createOpen" :busy="saving" :error="createError" :field-errors="createFieldErrors" @submit="create" />
        <TeamMemberDialog
            v-model:visible="memberOpen"
            :team-name="selected?.teamName ?? ''"
            :candidates="candidates"
            :loading="candidatesLoading"
            :load-error="candidatesError"
            :busy="saving"
            :error="memberError"
            @retry="loadCandidates"
            @submit="addMembers"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { TeamCandidateView, TeamMemberView, TeamSaveRequest, TeamView } from '~/composables/team/api/useTeamApi.types';
import UtilTeam from '~/utils/util-team';
import UtilTeamMessages from '~/utils/util-team-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('team.title') });

const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
const { teams, loading, error, saving, load, create: createTeam, update, remove, move: moveTeam, setMembers, candidates: loadTeamCandidates } = useTeams();

const enabledId = useId();

/** 이 화면에서 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('team.manage'));

const keyword = ref('');
const memberKeyword = ref('');
/** 고른 팀 — 처음에는 첫 팀 */
const selectedId = ref<string | null>(null);

const selected = computed(() => teams.value?.find((team) => team.teamId === selectedId.value) ?? null);
const filteredTeams = computed(() => UtilTeam.filterTeams(teams.value ?? [], keyword.value));
const filteredMembers = computed(() => UtilTeam.filterMembers(selected.value?.members ?? [], memberKeyword.value));

// ── 팀 정보 폼 — 고른 팀을 그대로 매지 않는다. 타이핑이 곧바로 목록에 비치면 저장하지 않고 다른 팀을 골랐을 때 안 고친 것처럼 보이는데 실제로는 안 고쳐진 상태다 ─

const form = reactive({ teamName: '', description: '', enabled: true });
const submitted = ref(false);
const infoError = ref('');
const infoFieldErrors = ref<Record<string, string>>({});

function fill(): void {
    const team = selected.value;
    form.teamName = team?.teamName ?? '';
    form.description = team?.description ?? '';
    form.enabled = team?.enabled ?? true;
    submitted.value = false;
    infoError.value = '';
    infoFieldErrors.value = {};
}

/** 안 고쳤으면 저장 단추를 잠근다 — 누를 이유가 없는 단추는 누르게 두지 않는다 */
const dirty = computed(() => {
    const team = selected.value;
    if (!team) return false;
    return form.teamName !== team.teamName || form.description !== (team.description ?? '') || form.enabled !== team.enabled;
});

const nameError = computed(() => {
    const problem = submitted.value ? UtilTeam.nameProblem(form.teamName) : null;
    return problem ? UtilTeamMessages.problem(t, problem) : '';
});
const descError = computed(() => {
    const problem = submitted.value ? UtilTeam.descProblem(form.description) : null;
    return problem ? UtilTeamMessages.problem(t, problem) : '';
});

watch(selectedId, () => {
    memberKeyword.value = '';
    fill();
});

/** 처음 받으면 첫 팀을 고른다. 고른 팀이 사라졌으면(지움·다시 조회) 첫 팀으로 */
watch(
    teams,
    (list) => {
        if (!list?.length) {
            selectedId.value = null;
            return;
        }
        if (!list.some((team) => team.teamId === selectedId.value)) selectedId.value = list[0]?.teamId ?? null;
    },
    { immediate: true },
);

/** 저장하지 않은 변경이 있으면 버려도 되는지 묻는다 */
function confirmDiscard(): Promise<boolean> {
    if (!dirty.value) return Promise.resolve(true);
    return confirm({ title: t('team.leaveTitle'), message: t('team.leaveMessage'), confirmLabel: t('team.leaveConfirm'), cancelLabel: t('team.leaveCancel'), tone: 'danger' });
}

async function select(teamId: string): Promise<void> {
    if (teamId === selectedId.value) return;
    if (!(await confirmDiscard())) return;
    selectedId.value = teamId;
}

async function saveInfo(): Promise<void> {
    const team = selected.value;
    if (!team || saving.value || !dirty.value) return;
    submitted.value = true;
    if (UtilTeam.nameProblem(form.teamName) || UtilTeam.descProblem(form.description)) return;
    infoError.value = '';
    infoFieldErrors.value = {};
    const body: TeamSaveRequest = { teamName: form.teamName.trim(), description: form.description.trim() || null, enabled: form.enabled };
    try {
        await update(team.teamId, body);
        fill();
        toast.success(t('team.saved'));
    } catch (e) {
        showInfoFailure(e as ApiError);
    }
}

/** 서버 거절 — 칸에 붙는 문구(`fields`)는 그 칸 아래에, 나머지는 폼 위쪽에 서버 문구 그대로 */
function showInfoFailure(failure: ApiError): void {
    infoFieldErrors.value = failure.fields ?? {};
    infoError.value = Object.keys(infoFieldErrors.value).length ? '' : failure.message;
}

// ── 새 팀 ─

const createOpen = ref(false);
const createError = ref('');
const createFieldErrors = ref<Record<string, string>>({});

async function openCreate(): Promise<void> {
    if (!(await confirmDiscard())) return;
    createError.value = '';
    createFieldErrors.value = {};
    createOpen.value = true;
}

async function create(body: TeamSaveRequest): Promise<void> {
    createError.value = '';
    createFieldErrors.value = {};
    try {
        const team = await createTeam(body);
        createOpen.value = false;
        // 만든 팀을 곧바로 고른 상태로 둔다 — 만들자마자 팀원을 넣는 것이 다음 일이다
        selectedId.value = team.teamId;
        toast.success(t('team.created'));
    } catch (e) {
        const failure = e as ApiError;
        createFieldErrors.value = failure.fields ?? {};
        createError.value = Object.keys(createFieldErrors.value).length ? '' : failure.message;
    }
}

// ── 순서·삭제 ─

async function move(team: TeamView, direction: 'UP' | 'DOWN'): Promise<void> {
    try {
        await moveTeam(team.teamId, direction);
    } catch (e) {
        toast.error((e as ApiError).message, 8000);
    }
}

async function removeTeam(team: TeamView): Promise<void> {
    if (team.members.length > 0) return;
    const ok = await confirm({
        title: t('team.deleteTitle'),
        message: UtilTeamMessages.deleteConfirm(t, team.teamName, team.usedByEventCount),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    try {
        await remove(team.teamId);
        toast.success(t('team.deleted'));
    } catch (e) {
        toast.error((e as ApiError).message, 8000);
    }
}

// ── 팀원 ─

const memberOpen = ref(false);
const candidates = ref<TeamCandidateView[]>([]);
const candidatesLoading = ref(false);
const candidatesError = ref('');
const memberError = ref('');

/** 후보는 열 때마다 새로 받는다 — 그 사이 다른 화면에서 운영자가 잠기거나 멈출 수 있고, 낡은 목록으로 넣으면 서버가 거절한다 */
async function loadCandidates(): Promise<void> {
    const team = selected.value;
    if (!team) return;
    candidatesLoading.value = true;
    candidatesError.value = '';
    try {
        candidates.value = await loadTeamCandidates(team.teamId);
    } catch (e) {
        candidatesError.value = (e as ApiError).message;
    } finally {
        candidatesLoading.value = false;
    }
}

async function openAddMembers(): Promise<void> {
    memberError.value = '';
    candidates.value = [];
    memberOpen.value = true;
    await loadCandidates();
}

async function addMembers(operatorIds: string[]): Promise<void> {
    const team = selected.value;
    if (!team || operatorIds.length === 0) return;
    memberError.value = '';
    const added = candidates.value.filter((candidate) => operatorIds.includes(candidate.operatorId) && !candidate.alreadyIn);
    try {
        await setMembers(team.teamId, [...team.members, ...added]);
        memberOpen.value = false;
        toast.success(t('team.membersAdded', { n: added.length }));
    } catch (e) {
        // 칸 오류(`fields.operatorIds` — 활성이 아니거나 없는 운영자)가 있으면 그 문구를, 없으면 서버 `message` 를 그대로
        const failure = e as ApiError;
        memberError.value = failure.fields?.operatorIds ?? failure.message;
        // 그 사이 운영자가 멈췄다면 후보가 낡은 것이다 — 목록을 다시 받아 고를 수 있는 사람만 남긴다
        if (failure.code === 'TEAM_OPERATOR_NOT_FOUND') await loadCandidates();
    }
}

async function removeMember(team: TeamView, member: TeamMemberView): Promise<void> {
    const ok = await confirm({
        title: t('team.removeTitle'),
        message: t('team.removeConfirm', { name: member.operatorName, team: team.teamName }),
        confirmLabel: t('team.remove'),
        tone: 'danger',
    });
    if (!ok) return;
    try {
        await setMembers(
            team.teamId,
            team.members.filter((item) => item.operatorId !== member.operatorId),
        );
        toast.success(t('team.memberRemoved', { name: member.operatorName }));
    } catch (e) {
        toast.error((e as ApiError).message, 8000);
    }
}

// 저장하지 않은 팀 정보가 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => confirmDiscard());

onMounted(() => void load());
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.team-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
