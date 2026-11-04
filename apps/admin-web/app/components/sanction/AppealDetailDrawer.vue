<template>
    <!--
        이의제기 화면의 서랍 — 표 줄을 누르면(또는 `?appeal=` 로 들어오면) 연다. 제재 서랍(`SanctionDetailDrawer`)과 같은 구조.
        본문: 요약(상태 · 접수번호 · 회원 · 접수/처리 시각) → 이의 사유 전문(글자로만) → 대상 제재(단계 · 상태 · 기간 · 사유 · 부여자 · 근거 신고 사건 `/reports?report=`) → 회원 제재 이력.
        처리(대기 + `appeal.manage`): 답변 1~500자 필수(회원에게 그대로 전달) + [인용](제재 무효 — 진행 중 정지는 즉시 풀린다는 안내) / [기각]. 권한이 없으면 읽기 전용 안내.
        처리된 이의제기는 결과(인용/기각 · 답변 · 처리자 · 시각)를 보인다. `409 APPEAL_CLOSED`(그 사이 다른 운영자가 처리)는 「이미 처리됐어요 — 다시 불러오기」.
        답변 칸은 다른 이의제기를 열 때마다 비운다. 서버 호출은 하지 않는다 — 부른 화면이 `answer` 를 받아 한다.
        게시판 제재 이의제기(`kind=BOARD`, board-sanction-contract 결정 4): 요약에 종류 배지, 대상 제재 자리에 게시판 제재(게시판 · 단계 · 상태 · 사유 · 기간 · 처리자 닉네임 + 매니저/스텝 · 계기 글 ·
          「이 게시판에서만 적용된 제재예요」 · `/board-sanctions?boardSanction=` 링크), 이력은 그 회원의 게시판 제재(`boardHistory`), 인용 안내는 쓰기 제한·반복 검토 기준.
    -->
    <Drawer
        :visible="open"
        class="appeal-detail-drawer"
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
                <h2 :id="titleId" class="min-w-0 flex-1 text-base font-semibold break-words">{{ head ? t('appeal.drawerTitle', { no: head.no, name: head.user.nickname }) : t('appeal.drawerTitleEmpty') }}</h2>
                <div class="flex shrink-0 items-center gap-0.5">
                    <Button v-tooltip.bottom="t('appeal.prev')" type="button" icon="pi pi-chevron-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('appeal.prev')" :disabled="!hasPrev" @click="emit('prev')" />
                    <Button v-tooltip.bottom="t('appeal.next')" type="button" icon="pi pi-chevron-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('appeal.next')" :disabled="!hasNext" @click="emit('next')" />
                </div>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('appeal.detailFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" />
        </div>

        <div v-else class="flex flex-col gap-5 pt-1" :aria-busy="loading">
            <DrawerSummary class="mt-2" :label="t('drawerSummary.label')" :items="summaryItems">
                <template #item-state><SanctionTag type="appeal" :status="detail.status" /></template>
                <template #item-kind><Tag :value="t(`boardSanction.appealKind.${kind}`)" :severity="kind === 'BOARD' ? 'info' : 'secondary'" class="text-[11px]!" /></template>
            </DrawerSummary>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-reason`">
                <h3 :id="`${titleId}-reason`" class="text-sm font-semibold">{{ t('appeal.reasonTitle') }}</h3>
                <p class="m-0 rounded-lg border border-(--line) bg-(--p-content-hover-background) px-3 py-2 text-sm leading-relaxed break-words whitespace-pre-line [overflow-wrap:anywhere]">{{ detail.reason }}</p>
            </section>

            <section v-if="b" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-sanction`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-sanction`" class="text-sm font-semibold">{{ t('appeal.sanctionTitle') }}</h3>
                    <NuxtLink :to="{ path: '/board-sanctions', query: { boardSanction: b.id } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-list-check text-[10px]" aria-hidden="true" />{{ t('appeal.openBoardSanction') }}
                    </NuxtLink>
                </div>
                <div class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <span class="text-xs tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, b) }}</span>
                        <span class="max-w-48 truncate text-xs font-medium">{{ localizedName(b.board.name, b.board.nameEn) }}</span>
                        <BoardSanctionTag type="kind" :kind="b.kind" />
                        <BoardSanctionTag type="status" :kind="b.kind" :status="b.status" />
                    </div>
                    <p class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]">{{ UtilBoardSanctionMessages.reasonLine(t, b) }}</p>
                    <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-color tabular-nums">
                        <span>{{ UtilBoardSanctionMessages.period(t, time, b) }}</span>
                        <span>{{ UtilBoardSanctionMessages.issuer(t, b.issuedBy) }}</span>
                        <NuxtLink v-if="b.trigger" :to="{ path: '/posts', query: { post: b.trigger.postId } }" class="inline-flex min-w-0 items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                            <i :class="b.trigger.type === 'COMMENT' ? 'pi pi-comment' : 'pi pi-file'" class="text-[10px]" aria-hidden="true" /><span class="truncate">{{ UtilBoardSanctionMessages.trigger(t, b.trigger) }}</span>
                        </NuxtLink>
                    </p>
                </div>
                <p class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('appeal.boardOnlyNote') }}</p>
            </section>

            <section v-else-if="s" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-sanction`">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 :id="`${titleId}-sanction`" class="text-sm font-semibold">{{ t('appeal.sanctionTitle') }}</h3>
                    <NuxtLink :to="{ path: '/sanctions', query: { sanction: s.id } }" class="inline-flex items-center gap-1 rounded px-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-shield text-[10px]" aria-hidden="true" />{{ t('appeal.openSanction') }}
                    </NuxtLink>
                </div>
                <div class="flex flex-col gap-1.5 rounded-lg border border-(--line) px-3 py-2.5">
                    <div class="flex flex-wrap items-center gap-1.5">
                        <span class="text-xs tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, s) }}</span>
                        <SanctionTag type="kind" :kind="s.kind" :warning-no="s.warningNo" />
                        <SanctionTag type="status" :kind="s.kind" :status="s.status" />
                    </div>
                    <p class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]">{{ s.reason }}</p>
                    <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-color tabular-nums">
                        <span>{{ UtilSanctionMessages.period(t, time, s) }}</span>
                        <span>{{ t('sanction.issuedByValue', { name: UtilSanctionMessages.actor(t, s.issuedBy) }) }}</span>
                    </p>
                </div>
                <!-- 근거 — 신고 사건(있으면) -->
                <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                    <span class="text-muted-color">{{ t('appeal.evidence') }}</span>
                    <NuxtLink v-if="s.reportCase" :to="{ path: '/reports', query: { report: s.reportCase.id, status: 'ALL' } }" class="inline-flex items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        <i class="pi pi-flag text-[11px]" aria-hidden="true" />{{ t('sanction.reportCaseValue', { no: s.reportCase.caseNo }) }}
                    </NuxtLink>
                    <span v-else class="text-muted-color">{{ t('appeal.evidenceNone') }}</span>
                </p>
            </section>

            <!-- 처리 결과 -->
            <section v-if="detail.status !== 'PENDING'" class="flex flex-col gap-2" :aria-labelledby="`${titleId}-result`">
                <h3 :id="`${titleId}-result`" class="text-sm font-semibold">{{ t('appeal.resultTitle') }}</h3>
                <div class="flex flex-col gap-1.5 rounded-lg border px-3 py-2.5" :class="detail.status === 'ACCEPTED' ? 'border-green-300 dark:border-green-900' : 'border-(--line)'">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                        <SanctionTag type="appeal" :status="detail.status" />
                        <span class="text-xs text-muted-color tabular-nums">{{ UtilSanctionMessages.actor(t, detail.answeredBy) }} · {{ time.full(detail.answeredAt) }}</span>
                    </p>
                    <p v-if="detail.answer" class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]">{{ detail.answer }}</p>
                    <p class="m-0 text-xs text-muted-color">{{ detail.status === 'ACCEPTED' ? t(b ? 'appeal.acceptedNoteBoard' : 'appeal.acceptedNote') : t('appeal.rejectedNote') }}</p>
                </div>
            </section>

            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-history`">
                <h3 :id="`${titleId}-history`" class="text-sm font-semibold">{{ t(b ? 'boardSanction.memberHistoryTitle' : 'sanction.memberHistoryTitle', { name: detail.user.nickname }) }}</h3>
                <BoardSanctionHistoryList v-if="b" :lines="detail.boardHistory ?? []" :current-id="b.id" link-sanction />
                <SanctionHistoryList v-else :lines="detail.history" :current-id="s?.id ?? null" link-sanction />
            </section>
        </div>

        <template v-if="detail && !error" #footer>
            <div class="flex w-full flex-col gap-2 border-t border-(--line) pt-3">
                <!-- 그 사이 다른 운영자가 처리했다 -->
                <Message v-if="closed" severity="warn" size="small" :closable="false" icon="pi pi-info-circle">
                    <span class="flex flex-wrap items-center gap-2">
                        {{ t('appeal.closedNote') }}
                        <Button type="button" :label="t('appeal.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="emit('retry')" />
                    </span>
                </Message>
                <p v-else-if="detail.status === 'PENDING' && !canAnswer" class="m-0 flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('appeal.readOnly') }}</p>
                <form v-else-if="detail.status === 'PENDING'" :id="formId" class="flex flex-col gap-2" novalidate @submit.prevent>
                    <LabeledField :label="t('appeal.answerLabel')" :hint="t('sanction.textCounter', { n: UtilSanction.textLength(answer), max: UtilSanction.TEXT_MAX })" :help="t('appeal.answerHelp')" :error="answerError">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="answer" rows="3" auto-resize fluid :placeholder="t('appeal.answerPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ acceptNote }}</p>
                    <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false">{{ formError }}</Message>
                    <div class="flex flex-wrap justify-end gap-2">
                        <Button type="button" :label="t('appeal.reject')" icon="pi pi-times" size="small" severity="secondary" variant="outlined" :loading="busy && pending === 'reject'" :disabled="busy" @click="send(false)" />
                        <Button type="button" :label="t('appeal.accept')" icon="pi pi-check" size="small" :loading="busy && pending === 'accept'" :disabled="busy" @click="send(true)" />
                    </div>
                </form>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AppealDetail, AppealItem } from '~/composables/sanction/api/useAppealApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 머리를 바로 그릴 줄 — 받은 상세, 아직이면 누른 목록 줄 */
    item?: AppealItem | null;
    detail?: AppealDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** `appeal.manage` */
    canAnswer?: boolean;
    busy?: boolean;
    /** 처리하다 `409 APPEAL_CLOSED` 를 받았다 */
    closed?: boolean;
    /** 칸에 붙지 않는 처리 오류(서버 문구 그대로) */
    formError?: string;
    /** 서버 칸 오류 `fields.answer` */
    answerFieldError?: string;
    hasPrev?: boolean;
    hasNext?: boolean;
}

const props = withDefaults(defineProps<Props>(), { item: null, detail: null, loading: false, error: null, canAnswer: false, busy: false, closed: false, formError: '', answerFieldError: '', hasPrev: false, hasNext: false });
const emit = defineEmits<{
    retry: [];
    answer: [payload: { accept: boolean; answer: string }];
    prev: [];
    next: [];
    closed: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const time = useDisplayTime();
const titleId = useId();
const formId = useId();
const { localizedName } = useLocalizedName();

const head = computed(() => props.detail ?? props.item);
/** 대상 — 사이트 제재(`s`) 또는 게시판 제재(`b`, `kind=BOARD`). 상세가 있을 때만 읽는다 */
const s = computed(() => props.detail?.sanction ?? null);
const b = computed(() => props.detail?.boardSanction ?? null);
/** 종류 — 서버가 `kind` 를 주지 않으면 사이트 제재 */
const kind = computed(() => head.value?.kind ?? (b.value ? 'BOARD' : 'SITE'));

const answer = ref('');
const tried = ref(false);
const pending = ref<'accept' | 'reject' | null>(null);

// 다른 이의제기를 열면 답변 칸을 비운다(앞 회원에게 쓰던 답이 딸려 가면 안 된다)
watch(
    () => props.detail?.id ?? props.item?.id ?? null,
    (next, prev) => {
        if (next === prev) return;
        answer.value = '';
        tried.value = false;
        pending.value = null;
    },
);

const answerError = computed(() => props.answerFieldError || (tried.value ? UtilSanctionMessages.textProblem(t, UtilSanction.textProblem(answer.value), t('appeal.answerLabel')) : ''));
/** 인용하면 어떻게 되는지 — 진행 중 정지면 즉시 풀린다 */
const acceptNote = computed(() => {
    const board = props.detail?.boardSanction;
    if (board) return UtilBoardSanction.isRunning(board) ? t('appeal.acceptNoteBoardRunning') : t('appeal.acceptNoteBoard');
    const line = props.detail?.sanction;
    if (line && UtilSanction.isRunning(line)) return t('appeal.acceptNoteRunning');
    return line?.kind === 'WARNING' ? t('appeal.acceptNoteWarning') : t('appeal.acceptNote');
});

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const h = head.value;
    if (!h) return [];
    return [
        { key: 'state', label: t('appeal.col.status') },
        { key: 'kind', label: t('appeal.col.kind') },
        { key: 'no', label: t('appeal.col.no'), value: t('appeal.noValue', { no: h.no }) },
        { key: 'member', label: t('appeal.col.member'), value: h.user.nickname },
        { key: 'created', label: t('appeal.col.createdAt'), value: time.value.full(h.createdAt) },
        ...(h.answeredAt ? [{ key: 'answered', label: t('appeal.col.answeredAt'), value: time.value.full(h.answeredAt) }] : [{ key: 'wait', label: t('appeal.col.wait'), value: UtilSanctionMessages.wait(t, UtilSanction.waitMs(h.createdAt, null)) }]),
    ];
});

function send(accept: boolean): void {
    if (props.busy) return;
    tried.value = true;
    if (UtilSanction.textProblem(answer.value)) return;
    pending.value = accept ? 'accept' : 'reject';
    emit('answer', { accept, answer: answer.value.trim() });
}

/** 바깥 누르기 — `SanctionDetailDrawer` 와 같은 방식 */
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
