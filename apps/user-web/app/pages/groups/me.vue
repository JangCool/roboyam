<template>
    <!--
        내 소모임 `/groups/me` (메뉴 `GROUPS_MINE`) — 가입한 모임(새 글 수 · 다음 일정 「일정 내일 20:00」 — 4단계) · 승인 대기(가입 신청) · 내 개설 신청(검토 중 · 반려). 비회원은 로그인 안내(목록은 로그인 없이 볼 수 있다).
        개설 신청은 아직 열린 소모임이 아니라 상세가 없다 — 검토 중은 [신청 취소], 반려는 사유 + [수정해서 다시 신청](`/groups/new?from=<신청 id>`).
    -->
    <div class="mx-auto max-w-2xl">
        <header class="flex items-center gap-2">
            <h1 class="min-w-0 flex-1 text-lg font-bold leading-tight">{{ t('group.myTitle') }}</h1>
            <Button v-if="user" as="router-link" :to="UtilGroup.NEW_PATH" :label="t('group.create')" size="small" class="shrink-0 whitespace-nowrap" data-testid="group-my-create" />
        </header>

        <div v-if="!loaded" class="mt-4 space-y-3" role="status" :aria-label="t('common.loading')">
            <div class="h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-4 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>

        <EmptyState v-else-if="!user" :title="t('group.loginMineTitle')" :description="t('group.loginMineText')" icon="pi pi-lock" data-testid="group-my-guest">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.viewAll')" severity="secondary" variant="outlined" />
            <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" data-testid="group-my-login" />
        </EmptyState>

        <div v-else class="mt-2" :aria-busy="status === 'loading' || status === 'idle'">
            <div v-if="status === 'idle' || status === 'loading'" class="space-y-2" aria-hidden="true" data-testid="group-my-loading">
                <div v-for="n in 5" :key="n" class="h-14 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="status === 'error'" :title="t('group.myError')" :description="errorMessage" retry data-testid="group-my-error" @retry="load" />

            <template v-else-if="data">
                <EmptyState v-if="isEmpty" :title="t('group.myEmptyTitle')" :description="t('group.myEmptyText')" icon="pi pi-users" data-testid="group-my-empty">
                    <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.browse')" />
                </EmptyState>

                <template v-else>
                    <section v-if="data.joined.length" class="mt-3" :aria-labelledby="`${uid}-joined`" data-testid="group-my-joined">
                        <h2 :id="`${uid}-joined`" class="text-sm font-semibold">{{ t('group.sectionJoined', { n: data.joined.length }) }}</h2>
                        <ul class="-mx-4 m-0 mt-1 list-none border-t border-surface p-0 md:mx-0">
                            <GroupListRow v-for="row in data.joined" :key="row.id" :row="row" mine :role="row.role" :new-posts="row.newPostCount" :next-event="row.nextEvent" />
                        </ul>
                    </section>

                    <!-- 승인 대기 중인 가입 신청 — 흐리게, 누르면 상세(신청 취소는 상세에서) -->
                    <section v-if="data.applications.length" class="mt-5" :aria-labelledby="`${uid}-applications`" data-testid="group-my-applications">
                        <h2 :id="`${uid}-applications`" class="text-sm font-semibold">{{ t('group.sectionApplications', { n: data.applications.length }) }}</h2>
                        <ul class="-mx-4 m-0 mt-1 list-none border-t border-surface p-0 md:mx-0">
                            <li v-for="row in data.applications" :key="row.id" class="border-b border-surface last:border-b-0" data-testid="group-my-application">
                                <NuxtLink
                                    :to="UtilGroup.detailPath(row.id)"
                                    class="flex items-center gap-3 px-4 py-3 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 dark:hover:bg-surface-800"
                                >
                                    <GroupInitial :name="row.name" class="opacity-70" />
                                    <span class="min-w-0 flex-1">
                                        <span class="flex items-center gap-1.5">
                                            <b class="min-w-0 truncate text-sm">{{ row.name }}</b>
                                            <GroupTag tone="amber">{{ t('group.rowApplied') }}</GroupTag>
                                        </span>
                                        <span class="mt-0.5 block text-xs text-muted-color">{{ UtilGroupMessages.appliedNote(t, row.appliedAt, time) }}</span>
                                    </span>
                                    <i class="pi pi-angle-right shrink-0 text-muted-color" aria-hidden="true" />
                                </NuxtLink>
                            </li>
                        </ul>
                    </section>

                    <!-- 내 개설 신청 — 검토 중 · 반려 -->
                    <section v-if="data.requests.length" class="mt-5" :aria-labelledby="`${uid}-requests`" data-testid="group-my-requests">
                        <h2 :id="`${uid}-requests`" class="text-sm font-semibold">{{ t('group.sectionRequests', { n: data.requests.length }) }}</h2>
                        <ul class="-mx-4 m-0 mt-1 list-none border-t border-surface p-0 md:mx-0">
                            <li v-for="req in data.requests" :key="req.id" class="border-b border-surface px-4 py-3 last:border-b-0 md:px-2" :data-status="req.status" data-testid="group-request">
                                <div class="flex items-start gap-3">
                                    <GroupInitial :name="req.name" class="opacity-70" />
                                    <div class="min-w-0 flex-1">
                                        <p class="flex items-center gap-1.5">
                                            <b class="min-w-0 truncate text-sm">{{ req.name }}</b>
                                            <GroupTag :tone="req.status === 'REJECTED' ? 'red' : 'amber'" data-testid="group-request-label">
                                                {{ req.status === 'REJECTED' ? t('group.myRejected') : t('group.myReviewing') }}
                                            </GroupTag>
                                        </p>
                                        <p class="mt-0.5 flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted-color">
                                            <GroupTag v-if="req.category" tone="neutral">{{ req.category.name }}</GroupTag>
                                            <span>{{ UtilGroupMessages.joinTypeLabel(t, req.joinType) }}</span>
                                            <span>{{ t('group.capacityValue', { n: req.capacity }) }}</span>
                                            <span v-if="time.date(req.requestedAt)">· {{ t('group.requestedOn', { date: time.date(req.requestedAt) }) }}</span>
                                        </p>
                                        <p v-if="req.status !== 'REJECTED'" class="mt-1 text-xs text-muted-color">{{ t('group.myPending') }}</p>
                                        <GroupNotice v-else-if="req.rejectReason" tone="danger" :title="t('group.rejectReasonLabel')" class="mt-2 text-xs" data-testid="group-request-reason">
                                            <span class="break-words">{{ req.rejectReason }}</span>
                                        </GroupNotice>
                                    </div>
                                </div>
                                <div class="mt-2 flex justify-end gap-2">
                                    <Button
                                        v-if="req.status !== 'REJECTED'"
                                        type="button"
                                        :label="t('group.myCancel')"
                                        size="small"
                                        severity="secondary"
                                        variant="outlined"
                                        :loading="cancelling.includes(req.id)"
                                        data-testid="group-request-cancel"
                                        @click="onCancel(req)"
                                    />
                                    <Button v-else as="router-link" :to="UtilGroup.reeditPath(req.id)" :label="t('group.myReedit')" size="small" data-testid="group-request-reedit" />
                                </div>
                            </li>
                        </ul>
                    </section>

                    <NuxtLink
                        :to="UtilGroup.LIST_PATH"
                        class="mt-5 flex items-center justify-between gap-3 rounded-lg border border-surface bg-surface-0 px-3.5 py-3 font-semibold hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                        data-testid="group-my-browse"
                    >
                        <span>{{ t('group.browse') }}</span>
                        <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                </template>
            </template>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MyGroupRequest } from '~/composables/group/api/useGroupApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilRedirect from '~/utils/util-redirect';

const { t } = useI18n();
useSeoMeta({ title: () => t('group.myTitle'), robots: 'noindex' });

const route = useRoute();
const uid = useId();
const time = useDisplayTime();
const { user, loaded } = useAuth();
const { status, data, errorMessage, cancelling, load, cancelRequest, stop } = useMyGroups();
const { confirm } = useConfirmDialog();

const isEmpty = computed(() => !data.value || (data.value.joined.length === 0 && data.value.applications.length === 0 && data.value.requests.length === 0));

// 로그인한 사람이 정해지면(처음 들어올 때 포함) 받는다. 비회원이면 받지 않는다
watch(
    () => user.value?.id,
    (id) => {
        if (id) void load();
    },
    { immediate: true },
);

onBeforeUnmount(stop);

async function onCancel(req: MyGroupRequest) {
    const ok = await confirm({
        title: t('group.cancelRequestTitle'),
        message: t('group.cancelRequestMessage', { name: req.name }),
        confirmLabel: t('group.myCancel'),
        cancelLabel: t('group.cancelRequestBack'),
        tone: 'danger',
    });
    if (!ok) return;
    await cancelRequest(req.id);
}
</script>
