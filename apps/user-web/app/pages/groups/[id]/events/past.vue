<template>
    <!--
        소모임 지난 일정 `/groups/{id}/events/past`(community `pages/groups/[id]/events/past.vue`, `docs/api-groups.md` 9절). **멤버만**.
        끝났거나(종료) 취소된(취소됨) 일정을 최근 순으로, 줄을 누르면 일정 상세. 「더보기」는 쪽 번호. 끝난 일정은 고칠 수 없어 「수정」이 없다.
        소모임 상세를 받지 않는다 — 멤버인지는 서버가 정한다(`403 GROUP_NOT_MEMBER` → 잠금). 로그인 전이면 요청하지 않고 로그인 안내.
    -->
    <div class="mx-auto max-w-2xl" data-testid="events-past">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-angle-left" variant="text" severity="secondary" :aria-label="t('common.back')" @click="goBack" />
            <span class="text-sm font-semibold">{{ t('groupEvent.pastTitle') }}</span>
        </div>
        <header class="max-md:hidden">
            <NuxtLink :to="tabPath" class="rounded text-xs text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">‹ {{ t('groupEvent.backToTab') }}</NuxtLink>
            <h1 class="mt-1 text-lg font-bold leading-tight">{{ t('groupEvent.pastTitle') }}</h1>
        </header>

        <div v-if="view === 'loading'" class="mt-3" role="status" :aria-label="t('common.loading')" data-testid="events-past-loading">
            <div v-for="n in 4" :key="n" class="flex items-center gap-3 border-b border-surface py-3">
                <div class="h-16 w-12 shrink-0 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                <div class="min-w-0 flex-1">
                    <div class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-4/5' : 'w-3/5'" />
                    <div class="mt-2 h-3 w-2/5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </div>
        </div>

        <StatusPanel v-else-if="view === 'login'" icon="pi pi-lock" tone="info" :title="t('groupEvent.loginTitle')" class="mt-6" data-testid="events-past-login">
            {{ t('groupEvent.loginText') }}
            <template #actions>
                <Button as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" fluid />
            </template>
        </StatusPanel>

        <EmptyState v-else-if="view === 'locked'" :title="t('groupEvent.lockedTitle')" :description="t('groupEvent.lockedText')" icon="pi pi-lock" data-testid="events-past-locked">
            <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" />
        </EmptyState>

        <ErrorState v-else-if="view === 'group-notfound'" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="events-past-gone">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'group-inactive'" :title="t('group.inactiveTitle')" :description="t('group.inactiveText')" data-testid="events-past-inactive">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'error'" :title="t('groupEvent.pastError')" :description="errorMessage" retry data-testid="events-past-error" @retry="load" />

        <template v-else>
            <EmptyState v-if="items.length === 0" :title="t('groupEvent.pastEmptyTitle')" :description="t('groupEvent.pastEmptyText')" icon="pi pi-calendar" data-testid="events-past-empty">
                <Button as="router-link" :to="tabPath" :label="t('groupEvent.backToTab')" severity="secondary" variant="outlined" />
            </EmptyState>
            <ul v-else class="-mx-4 m-0 mt-2 list-none p-0 md:mx-0" data-testid="events-past-list">
                <GroupEventRow v-for="row in items" :key="row.id" :group-id="id" :row="row" />
            </ul>

            <div v-if="hasNext" class="py-4 text-center">
                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="events-past-more" @click="loadMore" />
                <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
            </div>
        </template>

        <p role="status" aria-live="polite" class="sr-only">{{ UtilGroupEventMessages.summary(t, status, items.length, hasNext) }}</p>
    </div>
</template>

<script setup lang="ts">
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent from '~/utils/util-group-event';
import UtilGroupEventMessages from '~/utils/util-group-event-messages';
import UtilRedirect from '~/utils/util-redirect';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const { user, loaded } = useAuth();

useSeoMeta({ title: () => t('groupEvent.pastTitle'), robots: 'noindex' });

const id = computed(() => String(route.params.id ?? ''));
const groupPath = computed(() => UtilGroup.detailPath(id.value));
const tabPath = computed(() => UtilGroupEvent.tabPath(id.value));

const { items, status, errorMessage, errorCode, hasNext, loadingMore, moreError, load, loadMore } = useGroupEvents({ groupId: id, scope: 'past' });

// 로그인한 사람만 묻는다 — 멤버인지는 서버가 정한다
watch(
    () => [loaded.value, user.value?.id ?? null, id.value] as const,
    ([isLoaded, userId]) => {
        if (isLoaded && userId) void load();
    },
    { immediate: true },
);

/** 로그인 상태를 모르면 기다리고, 비회원이면 로그인 안내, 그 밖에는 목록 상태. 오류는 코드로 소모임 없음·비활성을 가른다 */
const view = computed(() => {
    if (!loaded.value) return 'loading';
    if (!user.value) return 'login';
    if (status.value === 'idle') return 'loading';
    if (status.value !== 'error') return status.value;
    if (errorCode.value === 'GROUP_NOT_FOUND') return 'group-notfound';
    if (errorCode.value === 'GROUP_INACTIVE') return 'group-inactive';
    return 'error';
});

function goBack(): void {
    if (typeof window !== 'undefined' && window.history.state?.back) router.back();
    else void router.push(tabPath.value);
}
</script>
