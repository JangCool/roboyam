<template>
    <!--
        스텝 처리 감독의 서랍 — 표 줄을 누르면(또는 `?action=` 으로 들어오면) 연다. 게시판 제재 서랍(`BoardSanctionDetailDrawer`)과 같은 구조:
        열리는 쪽은 설정의 "서랍 위치", 표를 가리지 않는다(`modal=false`), 열린 채 다른 줄을 누르면 그 처리로 바뀐다, 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다. 이전/다음은 지금 쪽의 줄 순서.
        본문: 요약(종류 · 되돌림 · 게시판 · 처리자) → 상세(처리자 닉네임 + 매니저/스텝 · 처리 시각 · 사유 코드) → 대상(글 제목/댓글 앞부분 · 작성자 · 지금 상태 · 글 링크 `/posts?post=`)
          → 되돌림 기록(되돌린 사람 · 시각 · 사유). 계약 `docs/features/board-staff-action-contract.md` 「화면 > admin-web」.
        바닥: [되돌리기](되돌리지 않은 처리 — `keeper-action.restore`). 서버 호출은 하지 않는다 — 부른 화면이 받는다.
    -->
    <Drawer
        :visible="open"
        class="board-staff-action-drawer"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '36rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 items-start gap-2">
                <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words">{{ action ? t('staffAction.drawerTitle', { kind: UtilBoardStaffActionMessages.kindName(t, action.kind) }) : t('staffAction.drawerTitleEmpty') }}</h2>
                <div class="flex shrink-0 items-center gap-0.5">
                    <Button v-tooltip.bottom="t('staffAction.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('staffAction.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                    <Button v-tooltip.bottom="t('staffAction.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('staffAction.next')" :disabled="!hasNext" @click="emit('next')" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('staffAction.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!action" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <DrawerSummary class="mt-2" :label="t('drawerSummary.label')" :items="summaryItems">
                <template #item-kind><BoardStaffActionTag type="kind" :value="action.kind" /></template>
                <template #item-restored><BoardStaffActionTag type="restored" :restored="action.restored !== null" /></template>
            </DrawerSummary>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-info`">
                <h3 :id="`${titleId}-info`" class="text-sm font-semibold">{{ t('staffAction.infoTitle') }}</h3>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('staffAction.col.actor') }}</dt>
                    <dd class="m-0">{{ UtilBoardStaffActionMessages.actor(t, action.actor) }}</dd>
                    <dt class="text-muted-color">{{ t('staffAction.col.at') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(action.createdAt) }}</dd>
                    <dt class="text-muted-color">{{ t('staffAction.col.reason') }}</dt>
                    <dd class="m-0">{{ UtilBoardStaffActionMessages.reasonName(t, action.reasonCode) }}</dd>
                </dl>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-target`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-target`" class="text-sm font-semibold">{{ t('staffAction.targetTitle') }}</h3>
                    <NuxtLink v-if="canReadPosts" :to="{ path: '/posts', query: { post: action.target.postId } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-file-edit text-[10px]" aria-hidden="true" />{{ t('staffAction.openPostAdmin') }}
                    </NuxtLink>
                </div>
                <div class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2">
                    <p class="m-0 flex min-w-0 items-start gap-1.5 text-sm">
                        <i :class="action.kind === 'COMMENT_DELETE' ? 'pi pi-comment' : 'pi pi-file'" class="mt-1 text-[11px] text-muted-color" aria-hidden="true" />
                        <span class="min-w-0 break-words [overflow-wrap:anywhere]">{{ UtilBoardStaffActionMessages.targetTitle(t, action) }}</span>
                    </p>
                    <p v-if="action.kind === 'COMMENT_DELETE'" class="m-0 truncate text-xs text-muted-color">{{ t('staffAction.commentOf', { title: action.target.title || t('staffAction.titleNone') }) }}</p>
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                        <span>{{ t('staffAction.authorLabel', { name: UtilBoardStaffActionMessages.author(t, action.target.author) }) }}</span>
                        <span aria-hidden="true">·</span>
                        <span>{{ t('staffAction.stateLabel') }}</span>
                        <BoardStaffActionTag type="state" :value="action.target.state" />
                    </p>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('staffAction.snapshotNote') }}</p>
            </section>

            <section v-if="action.restored" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-restored`">
                <h3 :id="`${titleId}-restored`" class="text-sm font-semibold">{{ t('staffAction.restoredTitle') }}</h3>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('staffAction.restoredByLabel') }}</dt>
                    <dd class="m-0">{{ UtilBoardStaffActionMessages.restoredBy(t, action.restored) }}</dd>
                    <dt class="text-muted-color">{{ t('staffAction.restoredAtLabel') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(action.restored.at) }}</dd>
                    <dt class="text-muted-color">{{ t('staffAction.restoreReasonLabel') }}</dt>
                    <dd class="m-0 break-words whitespace-pre-line [overflow-wrap:anywhere]" :class="action.restored.reason ? '' : 'text-muted-color'">{{ action.restored.reason || t('staffAction.restoreReasonNone') }}</dd>
                </dl>
            </section>
        </div>

        <template v-if="action && !error && !action.restored" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p v-if="!canRestore" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('staffAction.needsRestore') }}</p>
                <div v-else class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('staffAction.restore')" icon="pi pi-undo" size="small" severity="secondary" variant="outlined" :disabled="busy || loading" @click="emit('restore', action)" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminBoardStaffAction } from '~/composables/sanction/api/useBoardStaffActionApi.types';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    action?: AdminBoardStaffAction | null;
    loading?: boolean;
    error?: ApiError | null;
    /** `keeper-action.restore` */
    canRestore?: boolean;
    /** `post.read` — [게시글 관리에서 보기] */
    canReadPosts?: boolean;
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
}

const props = withDefaults(defineProps<Props>(), { action: null, loading: false, error: null, canRestore: false, canReadPosts: false, busy: false, hasPrev: false, hasNext: false });
const emit = defineEmits<{
    retry: [];
    restore: [action: AdminBoardStaffAction];
    prev: [];
    next: [];
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const titleId = useId();

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const a = props.action;
    if (!a) return [];
    return [
        { key: 'kind', label: t('staffAction.col.kind') },
        { key: 'restored', label: t('staffAction.col.restored') },
        { key: 'board', label: t('staffAction.col.board'), value: localizedName(a.board.name, a.board.nameEn) },
        { key: 'actor', label: t('staffAction.col.actor'), value: UtilBoardStaffActionMessages.actor(t, a.actor) },
    ];
});

/** 바깥 누르기 — 서랍·표 줄·떠 있는 창 안이면 무시하고, 그 밖이면 닫는다(`BoardSanctionDetailDrawer` 와 같은 방식) */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"], [role="tab"]';
function onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (!target || target.closest(INSIDE)) return;
    open.value = false;
}
watch(
    open,
    (isOpen) => {
        if (isOpen) document.addEventListener('pointerdown', onPointerDown, true);
        else document.removeEventListener('pointerdown', onPointerDown, true);
    },
    { immediate: true },
);
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));
</script>
