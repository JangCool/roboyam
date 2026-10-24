<template>
    <!--
        게시글 관리의 글 서랍 — 목록 줄·제목을 누르면(또는 `?post=` 로 들어오면) 연다. 열리는 쪽은 설정의 "서랍 위치"(`prefs.drawerPosition`), 표를 가리지 않는다(`modal=false`) —
        열린 채 다른 줄을 누르면 그 글로 바뀐다. 서랍·표 줄·떠 있는 창(대화창·고르개·메뉴·의견 서랍) 밖을 누르면 닫는다. Esc 는 PrimeVue Drawer 가 맡는다.
        머리: 제목 · 상태 알약(숨김 까닭·시각 …) · 글 번호(복사) · 이전/다음 글(지금 쪽의 줄 순서).
        본문(위에서 아래): 글 요약(게시판·말머리·작성자·작성/수정일·추천/댓글/조회·신고 대기·칩) · 상태 띠(지운 글 / 원문 파기 / 숨긴 글) · 본문(HTML 은 서버 정화본을 `rich-text` 로, 평문은 줄바꿈 그대로) ·
          **찬반 토론 구획**(상태 요약 + [토론 설정](`DebateSettingsDialog`) + [의견 관리](`DebateOpinionsDrawer`) — 두 부품은 부른 화면이 연다) ·
          **투표 구획**(글에 투표가 있을 때만 — 질문 · 마감/진행 · 복수/단일 · 다시 투표 허용 · 마감 시각 · 선택지별 득표 막대·퍼센트 · 참여 수. 누가 골랐는지는 없다. `post.manage` 면 [투표 지우기] → 부른 화면이 사유 대화창을 연다) ·
          조치 기록(최근 것부터, 처리자·사유·덧붙임).
        바닥: 상태가 허락하는 조치 단추만(`actions`) — 없으면 까닭 한 줄(읽기 전용 · 복구 권한 없음 · 원문 파기).
        서버 호출은 하지 않는다(`components.md`) — 글은 `usePostDetail`, 토론 요약은 `useDebateSettingsActions().get` 으로 부른 화면이 받는다.
        [공개 사이트에서 보기](새 탭)는 서버가 준 `publicUrl` 이 있을 때만 머리에 보인다(사이트 공개 주소를 정하지 않았으면 null). 글 번호 복사는 늘 있다.
    -->
    <Drawer
        :visible="open"
        class="post-detail-drawer"
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
                    <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words [overflow-wrap:anywhere]">{{ item?.title ?? t('post.drawerTitle') }}</h2>
                    <div class="flex shrink-0 items-center gap-0.5">
                        <Button v-tooltip.bottom="t('post.prevPost')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('post.prevPost')" :disabled="!hasPrev" @click="emit('prev')" />
                        <Button v-tooltip.bottom="t('post.nextPost')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('post.nextPost')" :disabled="!hasNext" @click="emit('next')" />
                    </div>
                </div>
                <div v-if="item" class="flex flex-wrap items-center gap-2">
                    <PostStateTag :item="item" />
                    <button
                        v-tooltip.bottom="t('content.copyPostId')"
                        type="button"
                        class="inline-flex items-center gap-1 rounded px-1 text-xs tabular-nums text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="t('content.copyPostIdLabel', { id: item.id })"
                        @click="copyId(item.id)"
                    >
                        <i class="pi pi-copy text-[10px]" aria-hidden="true" />#{{ item.id }}
                    </button>
                    <a
                        v-if="detail?.publicUrl"
                        :href="detail.publicUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    >
                        <i class="pi pi-external-link text-[10px]" aria-hidden="true" />{{ t('post.openPublic') }}
                    </a>
                </div>
            </div>
        </template>

        <!-- 없는 글(404)은 다시 시도해도 같다 -->
        <ErrorState v-if="error" :title="t('post.detailLoadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <!-- 글 요약 -->
            <dl class="m-0 grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                <dt class="text-muted-color">{{ t('post.col.board') }}</dt>
                <dd class="m-0 flex flex-wrap items-center gap-1.5">
                    <span>{{ localizedName(post.board.name, post.board.nameEn) }}</span>
                    <span v-if="post.prefix" class="text-muted-color">[{{ post.prefix.name }}]</span>
                    <Tag v-if="post.notice" :value="t('post.chip.notice')" severity="info" class="text-[11px]!" />
                    <Tag v-if="post.pinned" :value="t('post.chip.pinned')" icon="pi pi-thumbtack" severity="info" class="text-[11px]!" />
                    <Tag v-if="post.adult" :value="t('post.chip.adult')" severity="danger" class="text-[11px]!" />
                    <Tag v-if="post.debate" :value="t('post.chip.debate')" icon="pi pi-comments" severity="secondary" class="text-[11px]!" />
                    <Tag v-if="post.poll" :value="t('post.chip.poll')" icon="pi pi-chart-bar" severity="secondary" class="text-[11px]!" />
                </dd>
                <dt class="text-muted-color">{{ t('post.col.author') }}</dt>
                <dd class="m-0">{{ UtilPostMessages.author(t, post) }}</dd>
                <dt class="text-muted-color">{{ t('post.col.createdAt') }}</dt>
                <dd class="m-0 tabular-nums">
                    {{ time.full(post.createdAt) }}
                    <span v-if="detail.content.editedAt" class="text-xs text-muted-color">· {{ t('post.editedAt', { at: time.full(detail.content.editedAt) }) }}</span>
                </dd>
                <dt class="text-muted-color">{{ t('post.col.stats') }}</dt>
                <dd class="m-0 tabular-nums">{{ t('post.stats', { likes: post.likeCount, comments: post.commentCount, views: post.viewCount }) }}</dd>
                <dt class="text-muted-color">{{ t('post.col.reports') }}</dt>
                <dd class="m-0 tabular-nums" :class="post.openReports > 0 ? 'font-medium text-red-600 dark:text-red-400' : ''">
                    <!-- 신고 대기가 있으면 신고 관리의 그 글 사건으로(report-admin-contract) -->
                    <NuxtLink v-if="post.openReports > 0 && canReadReports" :to="UtilPost.reportsLink(post.id)" class="rounded hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        {{ t('post.reportsPending', { n: post.openReports }) }} <i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                    </NuxtLink>
                    <template v-else>{{ t('post.reportsPending', { n: post.openReports }) }}</template>
                </dd>
            </dl>

            <!-- 상태 띠 -->
            <Message v-if="post.state === 'DELETED'" severity="error" size="small" :closable="false" icon="pi pi-trash">
                {{ post.deletedAt ? t('post.deletedBand', { at: time.full(post.deletedAt) }) : t('post.deletedBandNoTime') }}
            </Message>
            <Message v-if="post.purged" severity="secondary" size="small" :closable="false" icon="pi pi-ban">{{ t('post.purgedBand') }}</Message>
            <Message v-if="post.state === 'HIDDEN'" severity="warn" size="small" :closable="false" icon="pi pi-eye-slash">
                <span class="flex flex-col gap-0.5">
                    <span v-for="(line, i) in stateLines" :key="i">{{ line }}</span>
                </span>
            </Message>

            <!-- 본문 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-body`">
                <h3 :id="`${titleId}-body`" class="text-sm font-semibold">{{ t('post.bodyTitle') }}</h3>
                <p v-if="post.purged || detail.content.body === null" class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-4 text-center text-[13px] text-muted-color">{{ t('post.purged') }}</p>
                <p v-else-if="bodyEmpty" class="m-0 text-[13px] text-muted-color">{{ t('post.bodyEmpty') }}</p>
                <!--
                    에디터 글(HTML)은 서버가 저장할 때 정화한 HTML 만 온다 — 공개 글 화면과 같은 `rich-text` 모양으로 그린다. 다른 경로의 글자(제목·이름 등)는 절대 넣지 않는다.
                -->
                <!-- eslint-disable-next-line vue/no-v-html -->
                <div v-else-if="detail.content.format === 'HTML'" class="rich-text max-h-[28rem] overflow-y-auto rounded-lg border border-(--line) px-4 py-3 text-sm" :class="post.state === 'DELETED' ? 'opacity-80' : ''" v-html="detail.content.body" />
                <div v-else class="max-h-[28rem] overflow-y-auto rounded-lg border border-(--line) px-4 py-3 text-sm leading-relaxed break-words whitespace-pre-line" :class="post.state === 'DELETED' ? 'opacity-80' : ''">{{ detail.content.body }}</div>
            </section>

            <!-- 찬반 토론 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-debate`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-debate`" class="text-sm font-semibold">{{ t('post.debateTitle') }}</h3>
                    <div class="flex flex-wrap gap-1">
                        <Button type="button" :label="t('post.debateSettings')" icon="pi pi-sliders-h" size="small" severity="secondary" variant="outlined" @click="emit('debate-settings')" />
                        <Button v-if="debate" type="button" :label="t('post.debateOpinions')" icon="pi pi-comments" size="small" severity="secondary" variant="outlined" @click="emit('debate-opinions')" />
                    </div>
                </div>
                <div class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2 text-xs text-muted-color">
                    <Skeleton v-if="debateLoading && !debate" height="1rem" width="70%" />
                    <p v-else-if="debateError" class="m-0 flex flex-wrap items-center gap-2 text-red-600 dark:text-red-400">
                        {{ debateError }}
                        <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="emit('debate-retry')" />
                    </p>
                    <template v-else-if="debate">
                        <div class="flex flex-wrap items-center gap-1.5">
                            <Tag :value="t('debate.debateOpen')" severity="success" class="text-[11px]!" />
                            <span>{{ t(`debate.openedBy.${debate.openedBy}`) }} · {{ time.listLabel(debate.createdAt) }}</span>
                        </div>
                        <span class="tabular-nums">
                            <i18n-t keypath="debate.summaryLine" tag="span" scope="global">
                                <template #total>{{ debate.votes.total }}</template>
                                <template #stances>
                                    <template v-for="(stance, i) in UtilDebate.stancesOf(debate)" :key="stance">
                                        <template v-if="i > 0"> · </template>
                                        <span class="font-medium" :class="UtilDebate.stanceTextClass(stance)">{{ t(`debate.stanceTab.${stance}`, { n: debate.votes[UtilDebate.countKey(stance)] }) }}</span>
                                    </template>
                                </template>
                                <template #opinions>{{ debate.opinions }}</template>
                                <template #pending>{{ debate.pendingPinRequests }}</template>
                            </i18n-t>
                        </span>
                    </template>
                    <template v-else>
                        <div><Tag :value="t('debate.debateNone')" severity="secondary" class="text-[11px]!" /></div>
                        <span>{{ t('debate.noneHint') }}</span>
                    </template>
                </div>
            </section>

            <!-- 투표(post-poll-contract) — 글에 투표가 있을 때만 -->
            <section v-if="poll" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-poll`" data-testid="post-drawer-poll">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-poll`" class="text-sm font-semibold">{{ t('post.pollTitle') }}</h3>
                    <Button
                        v-if="canManage"
                        type="button"
                        :label="t('post.action.POLL_DELETE')"
                        icon="pi pi-trash"
                        size="small"
                        severity="danger"
                        variant="outlined"
                        :disabled="busy || loading"
                        data-testid="post-drawer-poll-delete"
                        @click="emit('poll-delete')"
                    />
                </div>
                <div class="flex flex-col gap-2.5 rounded-lg border border-(--line) px-3 py-2.5">
                    <p class="m-0 text-[13px] font-medium break-words [overflow-wrap:anywhere]">{{ poll.question }}</p>
                    <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                        <Tag :value="pollClosed ? t('post.pollClosed') : t('post.pollOpen')" :severity="pollClosed ? 'secondary' : 'success'" class="text-[11px]!" />
                        <Tag :value="poll.multiple ? t('post.pollMultiple') : t('post.pollSingle')" severity="secondary" class="text-[11px]!" />
                        <Tag :value="poll.changeAllowed ? t('post.pollChangeAllowed') : t('post.pollChangeLocked')" severity="secondary" class="text-[11px]!" />
                        <span class="tabular-nums">{{ poll.closesAt ? t('post.pollClosesAt', { at: time.full(poll.closesAt) }) : t('post.pollNoClose') }}</span>
                    </div>
                    <ul class="m-0 flex list-none flex-col gap-2 p-0">
                        <li v-for="row in pollRows" :key="row.option.id" class="flex flex-col gap-1">
                            <div class="flex items-baseline justify-between gap-2 text-[13px]">
                                <span class="min-w-0 break-words [overflow-wrap:anywhere]">{{ row.option.label }}</span>
                                <span class="shrink-0 text-xs tabular-nums text-muted-color">{{ t('post.pollResult', { p: row.percent, n: row.option.votes }) }}</span>
                            </div>
                            <div class="h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
                                <div class="h-full rounded-full bg-primary" :style="{ width: `${row.percent}%` }" />
                            </div>
                        </li>
                    </ul>
                    <p class="m-0 text-xs text-muted-color">
                        <span class="tabular-nums">{{ t('post.pollVoters', { n: poll.voterCount }) }}</span> · {{ t('post.pollAnonymous') }}
                    </p>
                </div>
            </section>

            <!-- 조치 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t('post.historyTitle') }}</h3>
                <p v-if="detail.events.length === 0" class="m-0 text-[13px] text-muted-color">{{ t('post.historyEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                    <li v-for="ev in detail.events" :key="ev.id" class="flex flex-col gap-1 px-3 py-2">
                        <div class="flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                            <Tag :value="t(`post.event.${ev.type}`)" :severity="UtilPost.eventSeverity(ev.type)" class="text-[11px]!" />
                            <span class="font-medium text-color">{{ UtilPostMessages.actor(t, ev.actor) }}</span>
                            <span aria-hidden="true">·</span>
                            <time :datetime="ev.at" :title="time.full(ev.at)" class="tabular-nums">{{ time.listLabel(ev.at) }}</time>
                        </div>
                        <p v-if="ev.reason" class="m-0 text-[13px] break-words whitespace-pre-line">{{ ev.reason }}</p>
                        <p v-if="UtilPost.detailPairs(ev.detail).length" class="m-0 flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] text-muted-color">
                            <span v-for="[k, v] in UtilPost.detailPairs(ev.detail)" :key="k">{{ k }}: {{ v }}</span>
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
                        :label="t(`post.action.${kind}`)"
                        :icon="UtilPost.actionIcon(kind)"
                        size="small"
                        :severity="UtilPost.isDanger(kind) ? 'danger' : 'secondary'"
                        :variant="UtilPost.isDanger(kind) ? undefined : 'outlined'"
                        :disabled="busy || loading"
                        @click="emit('act', kind)"
                    />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DebateItem } from '~/composables/content/api/useDebateApi.types';
import type { AdminPostDetail, AdminPostItem } from '~/composables/post/api/usePostApi.types';
import UtilDebate from '~/utils/util-debate';
import UtilPost, { type PostActionKind, type PostActions } from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 머리를 바로 그릴 글 — 받은 상세의 글, 아직이면 누른 목록 줄 */
    item?: AdminPostItem | null;
    detail?: AdminPostDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 바닥 줄에서 누를 수 있는 조치 */
    actions: PostActions;
    busy?: boolean;
    hasPrev?: boolean;
    hasNext?: boolean;
    /** 열린 토론(없으면 null) */
    debate?: DebateItem | null;
    debateLoading?: boolean;
    debateError?: string;
    /** `post.manage` · `post.restore` — 바닥 줄 안내에 쓴다 */
    canManage?: boolean;
    canRestore?: boolean;
    /** `report.read` — 신고 대기 줄을 신고 관리(`/reports?postId=`)로 잇는다 */
    canReadReports?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    item: null,
    detail: null,
    loading: false,
    error: null,
    busy: false,
    hasPrev: false,
    hasNext: false,
    debate: null,
    debateLoading: false,
    debateError: '',
    canManage: false,
    canRestore: false,
    canReadReports: false,
});
const emit = defineEmits<{
    retry: [];
    act: [kind: PostActionKind];
    prev: [];
    next: [];
    'debate-settings': [];
    'debate-opinions': [];
    'debate-retry': [];
    /** [투표 지우기] — 사유 대화창은 부른 화면이 연다 */
    'poll-delete': [];
    /** 서랍이 다 닫혔다(전환 끝) */
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const toast = useAppToast();
const titleId = useId();

/** 본문 영역이 그리는 글 — 받은 상세가 있을 때만 쓴다(템플릿은 `detail` 이 있을 때만 이 값을 읽는다) */
const post = computed(() => props.detail?.post as AdminPostItem);
const stateLines = computed(() => (props.detail ? UtilPostMessages.stateLines(t, time.value, props.detail.post) : []));
const bodyEmpty = computed(() => {
    const body = props.detail?.content.body;
    if (body === null || body === undefined) return false;
    return props.detail?.content.format === 'HTML' ? UtilPost.isHtmlEmpty(body) : body.trim() === '';
});
const actionList = computed(() => UtilPost.actionList(props.actions));
/** 글의 투표(없으면 null) */
const poll = computed(() => props.detail?.poll ?? null);
const pollRows = computed(() => (poll.value ? UtilPost.pollRows(poll.value) : []));
const pollClosed = computed(() => Boolean(poll.value?.closed));

/** 단추가 없을 때(또는 지운 글) 까닭 한 줄 */
const footerNote = computed(() => {
    const p = props.detail?.post;
    if (!p) return '';
    if (p.state === 'DELETED') {
        if (p.purged) return t('post.restorePurged');
        if (!props.canRestore) return t('post.restoreNeedsPermission');
        return '';
    }
    if (!props.canManage) return t('post.readOnly');
    return '';
});

async function copyId(id: string): Promise<void> {
    try {
        await navigator.clipboard.writeText(id);
        toast.success(t('content.postIdCopied'));
    } catch {
        toast.error(t('content.copyFailed'));
    }
}

/**
 * 바깥 누르기 — 서랍·표 줄·떠 있는 창(대화창·고르개·메뉴·다른 서랍(의견 관리)·토스트) 안이면 무시하고, 그 밖이면 닫는다(`OperatorDetailDrawer` 와 같은 방식).
 */
const INSIDE = '.p-drawer, .p-drawer-mask, tbody tr, .p-dialog, .p-dialog-mask, .p-overlay, .p-select-overlay, .p-menu, .p-popover, .p-tooltip, .p-toast, [role="alertdialog"], [role="listbox"], [data-post-keep-drawer]';
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
