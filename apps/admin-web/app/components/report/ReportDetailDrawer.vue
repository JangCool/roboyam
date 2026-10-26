<template>
    <!--
        신고 관리의 사건 서랍 — 목록 줄을 누르면(또는 `?report=` 로 들어오면) 연다. 게시글 관리 글 서랍(`PostDetailDrawer`)과 같은 구조:
        열리는 쪽은 설정의 "서랍 위치"(`prefs.drawerPosition`), 표를 가리지 않는다(`modal=false`) — 열린 채 다른 줄을 누르면 그 사건으로 바뀐다. 서랍·표 줄·떠 있는 창 밖을 누르면 닫는다.
        머리: 대상 제목 · 사건 상태 알약(검토자) · 사건 번호 · 이전/다음 사건(지금 쪽의 줄 순서).
        본문(위에서 아래): 대상(종류·게시판·작성자·대상 상태·첫/마지막 신고 · [게시글 관리에서 보기] `/posts?post=` · [공개 사이트에서 보기] `publicUrl`) ·
          지금 원문 + 신고 당시 스냅샷(다르면 「신고 뒤 수정됨」) · 신고 목록(신고자·사유·메모·시각) · 작성자 카드(제재 자리 「준비 중」) · 처리 결과(닫힌 사건) · 처리 기록(최근 것부터).
        바닥: 열린 사건 + `report.manage` 면 처리 단추(`actions` — 숨기기 공개 대상만 · 자동 숨김 확정 자동 숨김 대상만 · 삭제 · 무시), 무시한 사건이면 [다시 열기], 그 밖은 까닭 한 줄.
        소모임 대상: 게시판 줄은 소모임 게시판이 있을 때만, 작성자 줄은 「모임장」, 링크는 [소모임 관리에서 보기] `/groups?group=`(`group.read`), 원문은 소개(글자), 처리 단추는 [소모임 비활성화](열린 소모임만)·[무시].
        서버 호출은 하지 않는다(`components.md`) — 사건은 `useReportDetail` 로 부른 화면이 받는다. 대기 사건은 열람과 함께 서버가 검토 중으로 바꾼다.
    -->
    <Drawer
        :visible="open"
        class="report-detail-drawer"
        :modal="false"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '44rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => (open = v)"
        @after-hide="emit('closed')"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-1.5">
                <div class="flex min-w-0 items-start gap-2">
                    <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words [overflow-wrap:anywhere]">{{ item ? (item.target.title ?? t('report.targetPurged')) : t('report.drawerTitle') }}</h2>
                    <div class="flex shrink-0 items-center gap-0.5">
                        <Button v-tooltip.bottom="t('report.prevCase')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('report.prevCase')" :disabled="!hasPrev" @click="emit('prev')" />
                        <Button v-tooltip.bottom="t('report.nextCase')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('report.nextCase')" :disabled="!hasNext" @click="emit('next')" />
                    </div>
                </div>
                <div v-if="item" class="flex flex-wrap items-center gap-2">
                    <Tag :value="t(`report.targetType.${item.targetType}`)" :severity="UtilReport.targetTypeSeverity(item.targetType)" class="text-[11px]!" />
                    <Tag :value="t(`report.status.${item.status}`)" :severity="UtilReport.statusSeverity(item.status)" class="text-[11px]!" />
                    <span v-if="item.reviewer && UtilReport.isOpen(item.status)" class="text-xs text-muted-color">{{ t('report.reviewerLine', { name: item.reviewer.name }) }}</span>
                    <span class="text-xs tabular-nums text-muted-color">{{ UtilReportMessages.caseNo(t, item) }}</span>
                </div>
            </div>
        </template>

        <!-- 없는 사건(404)은 다시 시도해도 같다 -->
        <ErrorState v-if="error" :title="t('report.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <!-- 대상 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-target`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-target`" class="text-sm font-semibold">{{ t('report.targetTitle') }}</h3>
                    <div class="flex flex-wrap items-center gap-1">
                        <NuxtLink
                            v-if="canViewPost && c.target.postId"
                            :to="{ path: '/posts', query: { post: c.target.postId } }"
                            class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        >
                            <i class="pi pi-file-edit text-[10px]" aria-hidden="true" />{{ t('report.openPostAdmin') }}
                        </NuxtLink>
                        <NuxtLink
                            v-if="canViewGroup && c.targetType === 'GROUP' && c.target.groupId"
                            :to="{ path: '/groups', query: { group: c.target.groupId } }"
                            class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        >
                            <i class="pi pi-users text-[10px]" aria-hidden="true" />{{ t('report.openGroupAdmin') }}
                        </NuxtLink>
                        <a
                            v-if="detail.publicUrl"
                            :href="detail.publicUrl"
                            target="_blank"
                            rel="noopener noreferrer"
                            class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        >
                            <i class="pi pi-external-link text-[10px]" aria-hidden="true" />{{ t('report.openPublic') }}
                        </a>
                    </div>
                </div>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                    <template v-if="c.board">
                        <dt class="text-muted-color">{{ t('report.col.board') }}</dt>
                        <dd class="m-0">{{ localizedName(c.board.name, c.board.nameEn ?? null) }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ c.targetType === 'GROUP' ? t('report.groupLeader') : t('report.col.author') }}</dt>
                    <dd class="m-0">{{ UtilReportMessages.author(t, c.author) }}</dd>
                    <dt class="text-muted-color">{{ t('report.targetStateLabel') }}</dt>
                    <dd class="m-0"><ReportTargetStateTag :target="c.target" lines /></dd>
                    <dt class="text-muted-color">{{ t('report.firstReported') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(c.firstReportedAt) }}</dd>
                    <dt class="text-muted-color">{{ t('report.col.lastReportedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(c.lastReportedAt) }}</dd>
                    <!-- 소모임 사건 — 지금 소모임 값(신고 당시 모임장은 위의 「모임장」) -->
                    <template v-if="c.targetType === 'GROUP' && detail.group">
                        <dt class="text-muted-color">{{ t('report.groupLeaderNow') }}</dt>
                        <dd class="m-0">{{ detail.group.leader?.nickname ?? '—' }}</dd>
                        <dt class="text-muted-color">{{ t('report.groupMembers') }}</dt>
                        <dd class="m-0 tabular-nums">{{ t('report.groupMembersValue', { members: detail.group.memberCount, capacity: detail.group.capacity }) }}</dd>
                        <dt class="text-muted-color">{{ t('report.groupCategory') }}</dt>
                        <dd class="m-0">{{ detail.group.category?.name ?? '—' }}</dd>
                    </template>
                </dl>
                <template v-if="c.targetType === 'GROUP' && detail.group">
                    <Message v-if="detail.group.offline" severity="warn" size="small" :closable="false">{{ t('report.groupOffline') }}</Message>
                    <Message v-if="detail.group.stockNotice" severity="warn" size="small" :closable="false">{{ t('report.groupStock') }}</Message>
                </template>
            </section>

            <!-- 지금 원문 + 신고 당시 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-body`">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${titleId}-body`" class="text-sm font-semibold">{{ t('report.bodyNow') }}</h3>
                    <Tag v-if="changed" :value="t('report.editedAfterReport')" icon="pi pi-pencil" severity="warn" class="text-[11px]!" />
                    <span v-else-if="detail.content.snapshot && detail.content.body !== null" class="text-xs text-muted-color">{{ t('report.sameAsSnapshot') }}</span>
                </div>
                <p v-if="detail.content.body === null" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-4 text-center text-[13px] text-muted-color">{{ t('report.bodyGone') }}</p>
                <p v-else-if="bodyEmpty" class="m-0 text-[13px] text-muted-color">{{ t('report.bodyEmpty') }}</p>
                <!-- 에디터 글(HTML)은 서버가 저장할 때 정화한 HTML 만 온다 — 게시글 관리 서랍과 같은 `rich-text` 모양으로 그린다 -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div v-else-if="detail.content.format === 'HTML'" class="rich-text max-h-[24rem] overflow-y-auto rounded-lg border border-(--line) px-4 py-3 text-sm" v-html="detail.content.body" />
                <div v-else class="max-h-[24rem] overflow-y-auto rounded-lg border border-(--line) px-4 py-3 text-sm leading-relaxed break-words whitespace-pre-line">{{ detail.content.body }}</div>

                <!-- 신고 당시 — 지금과 다르거나 지금 원문을 볼 수 없을 때 -->
                <div v-if="snapshot && (changed || detail.content.body === null)" class="flex flex-col gap-1.5 rounded-lg border border-amber-300 bg-amber-50/50 px-3 py-2 dark:border-amber-800 dark:bg-amber-950/30">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs font-medium">
                        <i class="pi pi-history text-[11px]" aria-hidden="true" />{{ t('report.snapshotTitle') }}
                        <span v-if="snapshot.authorName" class="font-normal text-muted-color">· {{ snapshot.authorName }}</span>
                    </p>
                    <p v-if="c.targetType !== 'COMMENT' && snapshot.title" class="m-0 text-[13px] font-medium break-words">{{ snapshot.title }}</p>
                    <!-- eslint-disable-next-line vue/no-v-html -->
                    <div v-if="snapshot.body && detail.content.format === 'HTML'" class="rich-text max-h-[16rem] overflow-y-auto text-sm" v-html="snapshot.body" />
                    <div v-else-if="snapshot.body" class="max-h-[16rem] overflow-y-auto text-sm leading-relaxed break-words whitespace-pre-line">{{ snapshot.body }}</div>
                </div>
            </section>

            <!-- 신고 목록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reports`">
                <h3 :id="`${titleId}-reports`" class="text-sm font-semibold">
                    {{ t('report.reportsTitle') }} <span class="font-normal text-muted-color tabular-nums">{{ t('report.reportCountValue', { n: reports.length }) }}</span>
                </h3>
                <p v-if="reports.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('report.reportsEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="entry in reports" :key="entry.id" class="flex flex-col gap-1 px-3 py-2">
                        <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                            <span class="font-medium text-color" :class="entry.reporter.withdrawn ? 'text-muted-color' : ''">{{ UtilReportMessages.reporter(t, entry.reporter) }}</span>
                            <Tag :value="UtilReportMessages.reasonName(t, entry.reason)" :severity="entry.reason === 'ILLEGAL_FILMING' ? 'danger' : 'secondary'" class="text-[11px]!" />
                            <Tag v-if="entry.status !== 'PENDING'" :value="t(`report.entryStatus.${entry.status}`)" severity="secondary" class="text-[11px]!" />
                            <span aria-hidden="true">·</span>
                            <time :datetime="entry.createdAt" :title="time.full(entry.createdAt)" class="tabular-nums">{{ time.listLabel(entry.createdAt) }}</time>
                        </div>
                        <p v-if="entry.memo" class="m-0 text-[13px] break-words whitespace-pre-line">{{ entry.memo }}</p>
                    </li>
                </ol>
            </section>

            <!-- 작성자 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-author`">
                <h3 :id="`${titleId}-author`" class="text-sm font-semibold">{{ c.targetType === 'GROUP' ? t('report.groupLeader') : t('report.authorTitle') }}</h3>
                <div class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                        <span class="font-medium" :class="c.author.withdrawn ? 'text-muted-color' : ''">{{ UtilReportMessages.author(t, c.author) }}</span>
                        <GradeBadge v-if="authorGrade" v-bind="authorGrade" size="small" />
                    </p>
                    <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1 text-[13px]">
                        <template v-if="detail.authorCard?.joinedAt">
                            <dt class="text-muted-color">{{ t('report.authorJoined') }}</dt>
                            <dd class="m-0 tabular-nums">{{ time.full(detail.authorCard.joinedAt) }}</dd>
                        </template>
                        <template v-if="detail.authorCard?.closedCases">
                            <dt class="text-muted-color">{{ t('report.authorRecent') }}</dt>
                            <dd class="m-0 tabular-nums">{{ UtilReportMessages.closedCases(t, detail.authorCard.closedCases) }}</dd>
                        </template>
                    </dl>
                    <!-- 제재(정지·이의제기)는 커머스 병합 뒤 — 자리만 -->
                    <p class="m-0 flex items-center gap-1.5 rounded-md border border-dashed border-(--line) px-2.5 py-1.5 text-xs text-muted-color">
                        <i class="pi pi-ban text-[11px]" aria-hidden="true" /><b class="font-medium">{{ t('report.sanctionLabel') }}</b> — {{ t('report.sanctionPending') }}
                    </p>
                </div>
            </section>

            <!-- 처리 결과(닫힌 사건) -->
            <section v-if="closure" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-closure`">
                <h3 :id="`${titleId}-closure`" class="text-sm font-semibold">{{ t('report.closureTitle') }}</h3>
                <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <dt class="text-muted-color">{{ t('report.closureAction') }}</dt>
                    <dd class="m-0 flex flex-wrap items-center gap-1.5">
                        <span>{{ closure.action ? t(`report.actionName.${closure.action}`) : '—' }}</span>
                        <Tag v-if="closure.falseReport" :value="t('report.falseReportMarked')" severity="warn" class="text-[11px]!" />
                    </dd>
                    <dt class="text-muted-color">{{ t('report.reasonCodeLabel') }}</dt>
                    <dd class="m-0">{{ closure.reasonCode ? UtilReportMessages.reasonName(t, closure.reasonCode) : '—' }}</dd>
                    <template v-if="closure.note">
                        <dt class="text-muted-color">{{ t('report.noteLabel') }}</dt>
                        <dd class="m-0 break-words whitespace-pre-line">{{ closure.note }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('report.closedBy') }}</dt>
                    <dd class="m-0 tabular-nums">
                        {{ closure.by ?? t('report.actorGone') }}<template v-if="closure.at"> · {{ time.full(closure.at) }}</template>
                    </dd>
                </dl>
            </section>

            <!-- 처리 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t('report.historyTitle') }}</h3>
                <p v-if="events.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('report.historyEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="ev in events" :key="ev.id" class="flex flex-col gap-1 px-3 py-2">
                        <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                            <Tag :value="t(`report.event.${ev.type}`)" :severity="UtilReport.eventSeverity(ev.type)" class="text-[11px]!" />
                            <span class="font-medium text-color">{{ UtilReportMessages.actor(t, ev.actor) }}</span>
                            <span aria-hidden="true">·</span>
                            <time :datetime="ev.createdAt" :title="time.full(ev.createdAt)" class="tabular-nums">{{ time.listLabel(ev.createdAt) }}</time>
                        </div>
                        <p v-if="UtilReportMessages.eventLines(t, ev).length" class="m-0 text-[13px]">{{ UtilReportMessages.eventLines(t, ev).join(' · ') }}</p>
                        <p v-if="typeof ev.detail?.note === 'string' && ev.detail.note" class="m-0 text-[13px] break-words whitespace-pre-line">{{ ev.detail.note }}</p>
                        <p v-if="UtilReportMessages.extraLines(t, time, ev.detail).length" class="m-0 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted-color">
                            <span v-for="(line, i) in UtilReportMessages.extraLines(t, time, ev.detail)" :key="i">{{ line }}</span>
                        </p>
                    </li>
                </ol>
            </section>
        </div>

        <template v-if="detail && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <p v-if="footerNote" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle" aria-hidden="true" />{{ footerNote }}</p>
                <div v-if="actionList.length" class="flex flex-wrap justify-end gap-2">
                    <Button
                        v-for="kind in actionList"
                        :key="kind"
                        type="button"
                        :label="t(`report.act.${kind}`)"
                        :icon="UtilReport.actIcon(kind)"
                        size="small"
                        :severity="UtilReport.isDanger(kind) ? 'danger' : 'secondary'"
                        :variant="UtilReport.isDanger(kind) ? undefined : 'outlined'"
                        :disabled="busy || loading"
                        @click="emit('act', kind)"
                    />
                </div>
                <div v-else-if="canReopen" class="flex flex-wrap justify-end gap-2">
                    <Button type="button" :label="t('report.reopen')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :loading="busy" :disabled="loading" @click="emit('reopen')" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { ReportCaseDetail, ReportCaseItem } from '~/composables/report/api/useReportApi.types';
import UtilReport, { type ReportActKind, type ReportActions } from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 머리를 바로 그릴 사건 — 받은 상세의 사건, 아직이면 누른 목록 줄 */
    item?: ReportCaseItem | null;
    detail?: ReportCaseDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 바닥 줄에서 누를 수 있는 처리 */
    actions: ReportActions;
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
    /** `report.manage` — 바닥 줄 안내·[다시 열기]에 쓴다 */
    canManage?: boolean;
    /** `post.read` — [게시글 관리에서 보기] */
    canViewPost?: boolean;
    /** `group.read` — 소모임 대상의 [소모임 관리에서 보기] */
    canViewGroup?: boolean;
    /** 작성자 등급 뱃지 값(`useGradeAppearance().badgeOf`) — 회원이 아니면 null */
    authorGrade?: { label: string; color: string; icon: string | null } | null;
}

const props = withDefaults(defineProps<Props>(), {
    item: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    hasPrev: false,
    hasNext: false,
    canManage: false,
    canViewPost: false,
    canViewGroup: false,
    authorGrade: null,
});
const emit = defineEmits<{
    retry: [];
    act: [kind: ReportActKind];
    reopen: [];
    prev: [];
    next: [];
    /** 서랍이 다 닫혔다(전환 끝) */
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const titleId = useId();

/** 본문 영역이 그리는 사건 — 받은 상세가 있을 때만 쓴다(템플릿은 `detail` 이 있을 때만 이 값을 읽는다) */
const c = computed(() => props.detail?.case as ReportCaseItem);
const snapshot = computed(() => props.detail?.content.snapshot ?? null);
const changed = computed(() => (props.detail ? UtilReport.snapshotChanged(props.detail) : false));
const bodyEmpty = computed(() => {
    const body = props.detail?.content.body;
    if (body === null || body === undefined) return false;
    return props.detail?.content.format === 'HTML' ? UtilReport.isHtmlEmpty(body) : body.trim() === '';
});
const events = computed(() => (props.detail ? UtilReport.eventsNewestFirst(props.detail.events ?? []) : []));
const reports = computed(() => props.detail?.reports ?? []);
const closure = computed(() => (props.detail && !UtilReport.isOpen(props.detail.case.status) ? UtilReport.closure(props.detail) : null));
const actionList = computed(() => UtilReport.actionList(props.actions));
const canReopen = computed(() => props.canManage && props.detail?.case.status === 'DISMISSED');

/** 단추가 없을 때 까닭 한 줄 */
const footerNote = computed(() => {
    const d = props.detail;
    if (!d) return '';
    if (!props.canManage) return t('report.readOnly');
    if (d.case.status === 'RESOLVED') return t('report.resolvedNote');
    if (d.case.status === 'DISMISSED') return t('report.dismissedNote');
    return '';
});

/**
 * 바깥 누르기 — 서랍·표 줄·떠 있는 창(대화창·고르개·메뉴·토스트) 안이면 무시하고, 그 밖이면 닫는다(`PostDetailDrawer` 와 같은 방식).
 */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"], [data-report-keep-drawer]';
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
