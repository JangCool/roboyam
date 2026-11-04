<template>
    <!--
        게시판 제재 이력 탭의 서랍 — 표 줄을 누르면(또는 `?boardSanction=` 으로 들어오면) 연다. 사이트 제재 서랍(`SanctionDetailDrawer`)과 같은 구조:
        열리는 쪽은 설정의 "서랍 위치", 표를 가리지 않는다(`modal=false`), 열린 채 다른 줄을 누르면 그 제재로 바뀐다, 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다. 이전/다음은 지금 쪽의 줄 순서.
        본문: 요약(단계 · 상태 · 번호 · 회원 · 게시판) → 상세(기간 · 처리자 닉네임 + 매니저/스텝 · 계기 글 `/posts?post=` · 이의제기 `/appeals?appeal=` · 이의제기 마감 · 경고 확인 · 해제/무효 기록)
          → 사유(코드 + 덧붙인 글, 글자로만) → 그 회원의 게시판 제재 이력(모든 게시판, 지금 제재 강조 — 상세를 받은 뒤).
        바닥: [해제](진행 중 쓰기 제한) · [무효](무효 아닌 제재) — `sanction.manage`. 서버 호출은 하지 않는다 — 부른 화면이 받는다.
    -->
    <Drawer
        :visible="open"
        class="board-sanction-detail-drawer"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '40rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 items-start gap-2">
                <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words">{{ line ? t('boardSanction.drawerTitle', { no: line.no, name: line.user.nickname }) : t('boardSanction.drawerTitleEmpty') }}</h2>
                <div class="flex shrink-0 items-center gap-0.5">
                    <Button v-tooltip.bottom="t('boardSanction.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('boardSanction.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                    <Button v-tooltip.bottom="t('boardSanction.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('boardSanction.next')" :disabled="!hasNext" @click="emit('next')" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('boardSanction.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!line" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <DrawerSummary class="mt-2" :label="t('drawerSummary.label')" :items="summaryItems">
                <template #item-kind><BoardSanctionTag type="kind" :kind="line.kind" /></template>
                <template #item-state><BoardSanctionTag type="status" :kind="line.kind" :status="line.status" /></template>
            </DrawerSummary>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-info`">
                <h3 :id="`${titleId}-info`" class="text-sm font-semibold">{{ t('boardSanction.infoTitle') }}</h3>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('boardSanction.period') }}</dt>
                    <dd class="m-0 tabular-nums">{{ UtilBoardSanctionMessages.period(t, time, line) }}</dd>
                    <dt class="text-muted-color">{{ t('boardSanction.col.issuedBy') }}</dt>
                    <dd class="m-0 tabular-nums">{{ UtilBoardSanctionMessages.issuer(t, line.issuedBy) }} · {{ time.full(line.createdAt) }}</dd>
                    <dt class="text-muted-color">{{ t('boardSanction.trigger') }}</dt>
                    <dd class="m-0 min-w-0">
                        <NuxtLink v-if="line.trigger" :to="{ path: '/posts', query: { post: line.trigger.postId } }" class="inline-flex max-w-full items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                            <i :class="line.trigger.type === 'COMMENT' ? 'pi pi-comment' : 'pi pi-file'" class="text-[11px]" aria-hidden="true" /><span class="truncate">{{ UtilBoardSanctionMessages.trigger(t, line.trigger) }}</span>
                        </NuxtLink>
                        <span v-else class="text-muted-color">{{ t('boardSanction.triggerNone') }}</span>
                    </dd>
                    <dt class="text-muted-color">{{ t('sanction.col.appeal') }}</dt>
                    <dd class="m-0 flex flex-wrap items-center gap-1.5">
                        <template v-if="line.appeal">
                            <SanctionTag type="appeal" :status="line.appeal.status" />
                            <NuxtLink :to="{ path: '/appeals', query: { appeal: line.appeal.id, status: 'ALL' } }" class="text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('sanction.appealOpen', { no: line.appeal.no }) }}</NuxtLink>
                        </template>
                        <span v-else class="text-muted-color">{{ t('sanction.appealNone') }}</span>
                    </dd>
                    <template v-if="line.appealDeadline && !line.appeal">
                        <dt class="text-muted-color">{{ t('sanction.appealDeadline') }}</dt>
                        <dd class="m-0 tabular-nums" :class="expired ? 'text-muted-color' : ''">{{ time.full(line.appealDeadline) }}<template v-if="expired"> · {{ t('sanction.appealExpired') }}</template></dd>
                    </template>
                    <template v-if="line.kind === 'WARNING' && line.status !== 'VOIDED'">
                        <dt class="text-muted-color">{{ t('boardSanction.acknowledged') }}</dt>
                        <dd class="m-0 tabular-nums" :class="line.acknowledgedAt ? '' : 'text-muted-color'">{{ line.acknowledgedAt ? t('boardSanction.acknowledgedAt', { at: time.full(line.acknowledgedAt) }) : t('boardSanction.notAcknowledged') }}</dd>
                    </template>
                    <template v-if="line.status === 'LIFTED'">
                        <dt class="text-muted-color">{{ t('boardSanction.liftedTitle') }}</dt>
                        <dd class="m-0">
                            <span class="tabular-nums">{{ UtilBoardSanctionMessages.liftedBy(t, line.liftedBy) }} · {{ time.full(line.liftedAt) }}</span>
                            <p v-if="line.liftReason" class="m-0 mt-0.5 break-words whitespace-pre-line">{{ line.liftReason }}</p>
                        </dd>
                    </template>
                    <template v-else-if="line.status === 'VOIDED'">
                        <dt class="text-muted-color">{{ t('boardSanction.voidedTitle') }}</dt>
                        <dd class="m-0">
                            <span class="tabular-nums">{{ UtilBoardSanctionMessages.voidedLine(t, time, line) }}<template v-if="line.voidedBy"> · {{ UtilSanctionMessages.actor(t, line.voidedBy) }}</template></span>
                            <p v-if="line.voidReason" class="m-0 mt-0.5 break-words whitespace-pre-line">{{ line.voidReason }}</p>
                        </dd>
                    </template>
                    <template v-else-if="line.status === 'ENDED' && line.endedAt">
                        <dt class="text-muted-color">{{ t('boardSanction.endedTitle') }}</dt>
                        <dd class="m-0 tabular-nums">{{ time.full(line.endedAt) }}</dd>
                    </template>
                </dl>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reason`">
                <h3 :id="`${titleId}-reason`" class="text-sm font-semibold">{{ t('boardSanction.reasonTitle') }}</h3>
                <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                    <p class="m-0 text-sm font-medium">{{ UtilBoardSanctionMessages.reasonName(t, line.reasonCode) }}</p>
                    <p class="m-0 text-sm leading-relaxed break-words whitespace-pre-line [overflow-wrap:anywhere]" :class="line.reasonText ? '' : 'text-muted-color'">{{ line.reasonText || t('boardSanction.reasonTextNone') }}</p>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('boardSanction.reasonSentNote') }}</p>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-member`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-member`" class="text-sm font-semibold">{{ t('boardSanction.memberHistoryTitle', { name: line.user.nickname }) }}</h3>
                    <NuxtLink :to="{ path: '/sanctions', query: { userId: line.user.id } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-shield text-[10px]" aria-hidden="true" />{{ t('sanction.openMemberSanctions') }}
                    </NuxtLink>
                </div>
                <Skeleton v-if="!history" height="6rem" />
                <BoardSanctionHistoryList v-else :lines="history" :current-id="line.id" link-sanction :can-manage="canManage" :busy="busy" @lift="(l) => emit('lift', l)" @void="(l) => emit('void', l)" />
            </section>
        </div>

        <template v-if="line && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p v-if="!canManage && line.status !== 'VOIDED'" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('boardSanction.needsManage') }}</p>
                <div v-else-if="liftable || voidable" class="flex flex-wrap justify-end gap-2">
                    <Button v-if="voidable" type="button" :label="t('boardSanction.void')" icon="pi pi-undo" size="small" severity="secondary" variant="text" :disabled="busy || loading" @click="emit('void', line)" />
                    <Button v-if="liftable" type="button" :label="t('boardSanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" :disabled="busy || loading" @click="emit('lift', line)" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { BoardSanctionLine } from '~/composables/sanction/api/useBoardSanctionApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    line?: BoardSanctionLine | null;
    /** 그 회원의 게시판 제재 이력 — 상세를 받기 전이면 null */
    history?: BoardSanctionLine[] | null;
    loading?: boolean;
    error?: ApiError | null;
    /** `sanction.manage` */
    canManage?: boolean;
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
}

const props = withDefaults(defineProps<Props>(), { line: null, history: null, loading: false, error: null, canManage: false, busy: false, hasPrev: false, hasNext: false });
const emit = defineEmits<{
    retry: [];
    lift: [line: BoardSanctionLine];
    void: [line: BoardSanctionLine];
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
    const l = props.line;
    if (!l) return [];
    return [
        { key: 'kind', label: t('boardSanction.col.kind') },
        { key: 'state', label: t('boardSanction.col.status') },
        { key: 'no', label: t('boardSanction.col.no'), value: UtilSanctionMessages.no(t, l) },
        { key: 'member', label: t('boardSanction.col.member'), value: l.user.nickname },
        { key: 'board', label: t('boardSanction.col.board'), value: localizedName(l.board.name, l.board.nameEn) },
    ];
});

const expired = computed(() => {
    const deadline = props.line?.appealDeadline ? Date.parse(props.line.appealDeadline) : Number.NaN;
    return !Number.isNaN(deadline) && deadline <= Date.now();
});
const liftable = computed(() => (props.line ? UtilBoardSanction.canLift(props.line, props.canManage) : false));
const voidable = computed(() => (props.line ? UtilBoardSanction.canVoid(props.line, props.canManage) : false));

/** 바깥 누르기 — 서랍·표 줄·떠 있는 창 안이면 무시하고, 그 밖이면 닫는다(`SanctionDetailDrawer` 와 같은 방식) */
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
