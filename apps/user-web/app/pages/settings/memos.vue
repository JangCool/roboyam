<template>
    <div class="flex flex-col gap-3">
        <h1 class="text-lg font-bold">{{ t('settings.memos') }}</h1>

        <div v-if="status === 'loading'" class="space-y-2" role="status" :aria-label="t('block.memosLoading')" data-testid="memos-loading">
            <div v-for="n in 3" :key="n" class="h-14 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('block.memosLoadFailed')" :description="errorMessage" retry data-testid="memos-error" @retry="load()" />

        <template v-else>
            <p class="m-0 rounded-lg bg-surface-100 px-3 py-2.5 text-xs leading-relaxed text-muted-color dark:bg-surface-800/60" role="note">{{ t('block.memosNotice') }}</p>

            <EmptyState v-if="items.length === 0" :title="t('block.memosEmptyTitle')" :description="t('block.memosEmptyText')" icon="pi pi-bookmark" data-testid="memos-empty" />

            <template v-else>
                <ul class="m-0 list-none rounded-xl border border-surface bg-surface-0 p-0 dark:bg-surface-900" data-testid="memos-list">
                    <li v-for="row in items" :key="row.id" class="flex min-h-14 items-center gap-2 border-b border-surface px-4 py-2.5 last:border-b-0" data-testid="memos-row">
                        <div class="min-w-0 flex-1">
                            <p class="m-0 flex min-w-0 items-center gap-1.5 text-sm">
                                <StaffBadge v-if="row.role !== 'USER'" :role="row.role" class="shrink-0" />
                                <GradeBadge v-else :grade="row.grade as Grade" :label="row.gradeLabel" class="shrink-0" />
                                <NuxtLink :to="UtilProfile.path(row.id)" class="truncate rounded font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ row.nickname }}</NuxtLink>
                            </p>
                            <small class="block break-words text-xs text-muted-color [overflow-wrap:anywhere]" data-testid="memos-text">{{ row.memo }}</small>
                        </div>
                        <Button
                            type="button"
                            :label="t('common.edit')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            class="shrink-0"
                            :disabled="removingId !== null"
                            :aria-label="t('block.memoEditLabel', { nickname: row.nickname })"
                            data-testid="memos-edit"
                            @click="edit(row)"
                        />
                        <Button
                            type="button"
                            :label="t('common.delete')"
                            size="small"
                            severity="danger"
                            variant="outlined"
                            class="shrink-0"
                            :loading="removingId === row.id"
                            :disabled="removingId !== null"
                            :aria-label="t('block.memoRemoveLabel', { nickname: row.nickname })"
                            data-testid="memos-remove"
                            @click="remove(row)"
                        />
                    </li>
                </ul>

                <div v-if="hasNext" class="py-4 text-center">
                    <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="memos-more" @click="loadMore" />
                    <p v-if="moreError" class="mt-2 text-xs text-red-600 dark:text-red-400" role="alert">{{ moreError }}</p>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { MemoView } from '~/composables/block/api/useMemoApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilProfile from '~/utils/util-profile';

/**
 * 이용자 메모 `/settings/memos` — 로그인 필요(community `pages/settings/memos.vue`, `docs/api-block-scrap.md`).
 * 내가 남긴 메모를 모아 **수정(셸의 메모 시트)·삭제(확인 대화상자)** 한다. 처음 남긴 것이 최근인 순서라 고쳐도 자리가 바뀌지 않는다.
 * 새로 남기는 것은 프로필·쪽지 대화방의 ⋯ 메뉴에서 한다. 고치거나 지우면 닉네임 옆 칩이 바로 바뀐다(`useBlockOverrides`).
 * ⚠ 목업 API(`useMemoApi`).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('settings.memos'), description: () => t('block.memosSeo', { site: siteLabel.value }), robots: 'noindex' });

const api = useMemoApi();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
const overrides = useBlockOverrides();
const memoSheet = useMemoSheet();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const errorMessage = ref('');
const items = ref<MemoView[]>([]);
const hasNext = ref(false);
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
const moreError = ref('');

let request = 0;
async function load() {
    const mine = ++request;
    status.value = 'loading';
    moreError.value = '';
    try {
        const res = await api.list();
        if (mine !== request) return;
        items.value = res.items;
        hasNext.value = res.hasNext && res.nextCursor !== null;
        nextCursor.value = res.nextCursor;
        status.value = 'ready';
    } catch (e) {
        if (mine !== request) return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

async function loadMore() {
    if (loadingMore.value || !hasNext.value || nextCursor.value === null) return;
    const mine = request;
    loadingMore.value = true;
    moreError.value = '';
    try {
        const res = await api.list({ cursor: nextCursor.value });
        if (mine !== request) return;
        const have = new Set(items.value.map((m) => m.id));
        items.value = [...items.value, ...res.items.filter((m) => !have.has(m.id))];
        hasNext.value = res.hasNext && res.nextCursor !== null;
        nextCursor.value = res.nextCursor;
    } catch (e) {
        if (mine !== request) return;
        moreError.value = t('block.moreFailed', { reason: UtilBoardMessages.of(t, e) });
    } finally {
        if (mine === request) loadingMore.value = false;
    }
}

void load();
onBeforeUnmount(() => request++);

/** 수정 — 셸의 메모 시트를 열고, 저장하면 목록의 그 줄을 맞춘다(지웠으면 뺀다) */
function edit(row: MemoView) {
    memoSheet.open({ id: row.id, nickname: row.nickname, memo: row.memo }, (userId, memo) => {
        if (memo === null) items.value = items.value.filter((m) => m.id !== userId);
        else items.value = items.value.map((m) => (m.id === userId ? { ...m, memo } : m));
    });
}

const removingId = ref<string | null>(null);

async function remove(row: MemoView) {
    if (removingId.value !== null) return;
    const ok = await confirm({ title: t('block.memoRemoveTitle', { nickname: row.nickname }), message: t('block.memoRemoveMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    removingId.value = row.id;
    try {
        await api.remove(row.id);
    } catch (e) {
        toast.error(t('block.memoRemoveFailed', { reason: UtilBoardMessages.of(t, e) }), 6000);
        return;
    } finally {
        removingId.value = null;
    }
    items.value = items.value.filter((m) => m.id !== row.id);
    overrides.setMemo(row.id, null);
    toast.success(t('block.memoRemoved'));
}
</script>
