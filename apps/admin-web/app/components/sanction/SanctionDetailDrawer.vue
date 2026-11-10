<template>
    <!--
        제재 화면의 서랍 — 표 줄을 누르면(또는 `?sanction=` 으로 들어오면) 연다. 신고 서랍(`ReportDetailDrawer`)과 같은 구조: 열리는 쪽은 설정의 "서랍 위치", 표를 가리지 않는다(`modal=false`),
        열린 채 다른 줄을 누르면 그 제재로 바뀐다, 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다. 이전/다음은 지금 쪽의 줄 순서.
        본문: 요약(단계 · 상태 · 번호 · 회원) → 상세(기간 · 부여 · 근거 신고 사건 `/reports?report=` · 이의제기 `/appeals?appeal=` · 이의제기 마감 · 해제/무효 기록) → 사유 전문(글자로만) →
          그 회원의 현황(경고 n/2 · 진행 중 · 다음 단계)과 제재 이력(지금 제재 강조).
        바닥: 진행 중 정지면 [해제](기간 정지 `sanction.manage` · 영구 정지 `sanction.ban`). 서버 호출은 하지 않는다 — 부른 화면이 받는다.
    -->
    <Drawer
        :visible="open"
        class="sanction-detail-drawer"
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
                <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words">{{ line ? t('sanction.drawerTitle', { no: line.no, name: UtilSanctionMessages.userName(t, line.user) }) : t('sanction.drawerTitleEmpty') }}</h2>
                <div class="flex shrink-0 items-center gap-0.5">
                    <Button v-tooltip.bottom="t('sanction.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('sanction.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                    <Button v-tooltip.bottom="t('sanction.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('sanction.next')" :disabled="!hasNext" @click="emit('next')" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('sanction.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!line" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <DrawerSummary class="mt-2" :label="t('drawerSummary.label')" :items="summaryItems">
                <template #item-kind><SanctionTag type="kind" :kind="line.kind" :warning-no="line.warningNo" /></template>
                <template #item-state><SanctionTag type="status" :kind="line.kind" :status="line.status" /></template>
            </DrawerSummary>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-info`">
                <h3 :id="`${titleId}-info`" class="text-sm font-semibold">{{ t('sanction.infoTitle') }}</h3>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('sanction.col.period') }}</dt>
                    <dd class="m-0 tabular-nums">
                        {{ UtilSanctionMessages.period(t, time, line) }}
                        <span v-if="line.skipped" class="ml-1 inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400"><i class="pi pi-forward text-[10px]" aria-hidden="true" />{{ t('sanction.skipped') }}</span>
                    </dd>
                    <dt class="text-muted-color">{{ t('sanction.col.issuedBy') }}</dt>
                    <dd class="m-0 tabular-nums">{{ UtilSanctionMessages.actor(t, line.issuedBy) }} · {{ time.full(line.createdAt) }}</dd>
                    <dt class="text-muted-color">{{ t('sanction.col.report') }}</dt>
                    <dd class="m-0">
                        <NuxtLink v-if="line.reportCase" :to="{ path: '/reports', query: { report: line.reportCase.id, status: 'ALL' } }" class="inline-flex items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                            <i class="pi pi-flag text-[11px]" aria-hidden="true" />{{ t('sanction.reportCaseValue', { no: line.reportCase.caseNo }) }}
                        </NuxtLink>
                        <span v-else class="text-muted-color">{{ t('sanction.reportNone') }}</span>
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
                    <template v-if="line.status === 'LIFTED'">
                        <dt class="text-muted-color">{{ t('sanction.liftedTitle') }}</dt>
                        <dd class="m-0">
                            <span class="tabular-nums">{{ UtilSanctionMessages.actor(t, line.liftedBy) }} · {{ time.full(line.liftedAt) }}</span>
                            <p v-if="line.liftReason" class="m-0 mt-0.5 break-words whitespace-pre-line">{{ line.liftReason }}</p>
                        </dd>
                    </template>
                    <template v-else-if="line.status === 'VOIDED'">
                        <dt class="text-muted-color">{{ t('sanction.voidedTitle') }}</dt>
                        <dd class="m-0 tabular-nums">{{ t('sanction.voidedLine', { at: time.full(line.voidedAt) }) }}</dd>
                    </template>
                    <template v-else-if="line.status === 'ENDED' && line.endedAt">
                        <dt class="text-muted-color">{{ t('sanction.endedTitle') }}</dt>
                        <dd class="m-0 tabular-nums">{{ time.full(line.endedAt) }}</dd>
                    </template>
                </dl>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reason`">
                <h3 :id="`${titleId}-reason`" class="text-sm font-semibold">{{ t('sanction.reasonLabel') }}</h3>
                <p class="m-0 rounded-lg border border-(--line) px-3 py-2 text-sm leading-relaxed break-words whitespace-pre-line [overflow-wrap:anywhere]">{{ line.reason }}</p>
                <p class="m-0 text-xs text-muted-color">{{ t('sanction.reasonSentNote') }}</p>
            </section>

            <!-- 봉인된 익명 글 작성자(anonymous-board-contract 정책 9) — 회원 현황·이력을 보이지 않는다(신원은 열람 창으로만) -->
            <p v-if="line.user.sealed" class="m-0 flex items-center gap-1.5 rounded-lg border border-dashed border-(--line) px-3 py-2 text-xs text-muted-color" data-testid="sanction-sealed-note">
                <i class="pi pi-lock text-[11px]" aria-hidden="true" />{{ t('anonymousAuthor.sanctionSealedNote') }}
            </p>
            <section v-else class="flex flex-col gap-2" :aria-labelledby="`${titleId}-member`">
                <h3 :id="`${titleId}-member`" class="text-sm font-semibold">{{ t('sanction.memberHistoryTitle', { name: UtilSanctionMessages.userName(t, line.user) }) }}</h3>
                <ErrorState v-if="previewError" :title="t('sanction.previewFailed')" :description="previewError.message" retry :retrying="previewLoading" @retry="emit('retry-preview')" />
                <Skeleton v-else-if="!preview" height="6rem" />
                <template v-else>
                    <SanctionPreviewSummary :preview="preview" />
                    <SanctionHistoryList :lines="preview.history" :current-id="line.id" :perm="perm" :busy="busy" @lift="(l) => emit('lift', l)" />
                </template>
            </section>
        </div>

        <template v-if="line && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p v-if="footerNote" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle" aria-hidden="true" />{{ footerNote }}</p>
                <div v-if="liftable" class="flex justify-end">
                    <Button type="button" :label="t('sanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" :disabled="busy || loading" @click="emit('lift', line)" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { SanctionLine, SanctionPreview } from '~/composables/sanction/api/useSanctionApi.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    line?: SanctionLine | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 그 회원의 현황(이력 포함) */
    preview?: SanctionPreview | null;
    previewLoading?: boolean;
    previewError?: ApiError | null;
    perm: { canManage: boolean; canBan: boolean };
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
}

const props = withDefaults(defineProps<Props>(), { line: null, loading: false, error: null, preview: null, previewLoading: false, previewError: null, busy: false, hasPrev: false, hasNext: false });
const emit = defineEmits<{
    retry: [];
    'retry-preview': [];
    lift: [line: SanctionLine];
    prev: [];
    next: [];
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const titleId = useId();

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const l = props.line;
    if (!l) return [];
    return [
        { key: 'kind', label: t('sanction.col.kind') },
        { key: 'state', label: t('sanction.col.status') },
        { key: 'no', label: t('sanction.col.no'), value: UtilSanctionMessages.no(t, l) },
        { key: 'member', label: t('sanction.col.member'), value: UtilSanctionMessages.userName(t, l.user) },
    ];
});

const expired = computed(() => {
    const deadline = props.line?.appealDeadline ? Date.parse(props.line.appealDeadline) : Number.NaN;
    return !Number.isNaN(deadline) && deadline <= Date.now();
});
const liftable = computed(() => (props.line ? UtilSanction.canLift(props.line, props.perm) : false));
const footerNote = computed(() => {
    const l = props.line;
    if (!l || !UtilSanction.isRunning(l) || liftable.value) return '';
    return l.kind === 'BAN' ? t('sanction.liftNeedsBan') : t('sanction.liftNeedsManage');
});

/** 바깥 누르기 — 서랍·표 줄·떠 있는 창 안이면 무시하고, 그 밖이면 닫는다(`ReportDetailDrawer` 와 같은 방식) */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"]';
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
