<template>
    <!--
        팀원 추가 — hamonica3 `TeamMemberDialog` 와 같다. 운영자를 이름·이메일로 찾아 체크로 여러 명 고른다. **이미 든 사람도 목록에 둔다**(빼 버리면 왜 안 보이는지 모른다) — 대신 짚어 주고 고르지 못하게 한다.
        고른 사람은 칩으로 다시 보인다(목록이 길면 스크롤 밖으로 나가 몇 명을 골랐는지 모르게 된다). 후보는 부르는 쪽(페이지)이 열 때마다 새로 받아 내려준다 — 낡은 목록으로 넣으면 서버가 거절한다.
        서버 호출은 부르는 쪽이 하고 여기는 `submit` 으로 고른 운영자 아이디를 내보낸다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('team.memberDialogTitle', { name: teamName })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <div v-if="loading" class="flex flex-col gap-2" aria-busy="true">
            <span class="sr-only">{{ t('common.loading') }}</span>
            <Skeleton height="2.25rem" />
            <Skeleton v-for="n in 4" :key="n" height="2.75rem" />
        </div>

        <ErrorState v-else-if="loadError" :title="t('team.candidatesLoadFailed')" :description="loadError" retry @retry="emit('retry')" />

        <template v-else>
            <IconField class="mb-3">
                <InputIcon class="pi pi-search" />
                <InputText v-model="keyword" fluid :placeholder="t('team.searchOperator')" :aria-label="t('team.searchOperator')" autocomplete="off" />
            </IconField>

            <div class="max-h-80 overflow-y-auto rounded-lg border border-(--line)">
                <ul v-if="filtered.length" class="m-0 list-none p-0">
                    <li v-for="candidate in filtered" :key="candidate.operatorId" class="border-b border-(--line) last:border-b-0">
                        <label class="flex items-center gap-3 px-3 py-2.5" :class="candidate.alreadyIn ? 'cursor-default opacity-60' : 'cursor-pointer hover:bg-surface-50 dark:hover:bg-surface-800'">
                            <Checkbox :model-value="candidate.alreadyIn || chosen.has(candidate.operatorId)" binary :disabled="candidate.alreadyIn || busy" :aria-label="candidate.operatorName" @update:model-value="toggle(candidate.operatorId)" />
                            <UserAvatar :name="candidate.operatorName" />
                            <span class="min-w-0 flex-1">
                                <span class="block truncate text-[13px] font-medium">{{ candidate.operatorName }}</span>
                                <span class="block truncate text-[11px] text-muted-color">{{ candidate.email }}</span>
                            </span>
                            <Tag v-if="candidate.alreadyIn" class="team-tag" :value="t('team.alreadyMember')" severity="secondary" />
                        </label>
                    </li>
                </ul>
                <p v-else class="m-0 px-3 py-6 text-center text-xs text-muted-color">{{ candidates.length ? t('team.noMatchOperator') : t('team.noCandidates') }}</p>
            </div>

            <div v-if="chosen.size" class="mt-3">
                <p class="m-0 mb-1.5 text-xs text-muted-color">{{ t('team.chosenCount', { n: chosen.size }) }}</p>
                <div class="flex flex-wrap gap-1.5">
                    <Chip v-for="candidate in chosenList" :key="candidate.operatorId" :label="candidate.operatorName" removable class="text-xs!" @remove="toggle(candidate.operatorId)" />
                </div>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert" class="mt-3">{{ error }}</Message>
        </template>

        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="button" :label="t('team.addChosen')" :loading="busy" :disabled="chosen.size === 0 || loading || Boolean(loadError)" @click="emit('submit', [...chosen])" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { TeamCandidateView } from '~/composables/team/api/useTeamApi.types';
import UtilTeam from '~/utils/util-team';

interface Props {
    visible: boolean;
    teamName: string;
    /** 팀에 넣을 수 있는 운영자(이미 든 사람 포함) — 열 때마다 부르는 쪽이 새로 받아 준다 */
    candidates?: TeamCandidateView[];
    /** 후보를 받는 중 */
    loading?: boolean;
    /** 후보를 못 받은 이유(서버 문구) — 있으면 오류 상태(다시 시도) */
    loadError?: string;
    /** 팀원 저장 요청 중 */
    busy?: boolean;
    /** 저장 실패 문구(서버 문구 그대로) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { candidates: () => [], loading: false, loadError: '', busy: false, error: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [operatorIds: string[]]; retry: [] }>();

const { t } = useI18n();

const keyword = ref('');
const chosen = ref(new Set<string>());

/** 열 때마다 비운다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        keyword.value = '';
        chosen.value = new Set();
    },
    { immediate: true },
);

/** 후보를 다시 받으면(서버가 거절해 새로 받은 경우 등) 이제 고를 수 없는 사람을 선택에서 뺀다 */
watch(
    () => props.candidates,
    (list) => {
        const pickable = new Set(list.filter((candidate) => !candidate.alreadyIn).map((candidate) => candidate.operatorId));
        if ([...chosen.value].every((id) => pickable.has(id))) return;
        chosen.value = new Set([...chosen.value].filter((id) => pickable.has(id)));
    },
);

const filtered = computed(() => UtilTeam.filterMembers(props.candidates, keyword.value));
const chosenList = computed(() => props.candidates.filter((candidate) => chosen.value.has(candidate.operatorId)));

/** Set 을 갈아 끼운다 — 안을 고치면 Vue 가 못 알아챈다 */
function toggle(operatorId: string): void {
    if (props.candidates.find((candidate) => candidate.operatorId === operatorId)?.alreadyIn) return;
    const next = new Set(chosen.value);
    if (next.has(operatorId)) next.delete(operatorId);
    else next.add(operatorId);
    chosen.value = next;
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.team-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
