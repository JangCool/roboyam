<template>
    <!--
        모임장 관리 `/groups/{id}/manage` — 머리(이름·모임장·멤버 수) · 멤버(가입 신청 대기 수 · 멤버 관리) · 소모임(정보 수정 · 채팅 링크 · 모집 켜기/끄기) · 위험 구역(삭제·해산 — 이름을 그대로 입력).
        모임장만 들어온다(서버도 `403 GROUP_LEADER_ONLY`). 정지·이메일 인증 전이면 띠 + 쓰기 단추가 꺼진다(목록은 열린다).
        community 대비: 매니저 줄(인원·맡은 일·잠금 줄·물러나기)은 3단계, 「글 › 공개 요청」·「공개 범위」 줄은 5단계라 뺐다.
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
                    <NuxtLink v-if="group.permissions.canKickMembers" :to="UtilGroup.managePath(id, 'members')" :class="ROW" data-testid="group-manage-members">
                        <span class="min-w-0 flex-1 text-sm font-medium">{{ t('group.rowMembers') }}</span>
                        <span class="shrink-0 text-xs text-muted-color">{{ t('group.peopleCount', { n: group.memberCount }) }}</span>
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
        </template>
    </GroupManageShell>
</template>

<script setup lang="ts">
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.manageTitle'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const { state, errorMessage, group, load, patch, block } = useGroupManage(id);

const ROW =
    'flex min-h-14 w-full items-center gap-3 border-b border-surface px-4 py-2.5 text-left last:border-b-0 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';

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

/** 쓰기 실패의 공통 갈래 — 쓰기 잠금(정지·인증 전) · 권한이 사라졌다(넘겼거나 강퇴) */
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
