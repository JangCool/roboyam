<template>
    <!--
        글 보기의 댓글 목록 구획 — 두 가지로 쓴다(확정 시안 `popular-highlight-contract.md` 「결정」 토론 화면 배치).
          `general`(맨 아래 원래 댓글 자리): 「댓글 N」(`counts.generalTotal`) · 정렬 · 베스트 · 일반 원댓글 + 답글 · 새 댓글. 입장 고르개·배지 없음 — 입장을 정한 회원이 써도 일반 댓글(`opinion` 을 보내지 않는다).
          `debate`(본문 영역 안, 본문 글 바로 다음): 「찬성·반대 토론 · N명 참여 · 의견 M」 · [찬성 n명][반대 n명][중립 n명] 투표 단추(`PUT …/stance` — 처음 고르기·바꾸기, 바꾸면 확인창 + 기존 의견 소급) ·
            비율 막대·범례(= `votes`, 사람 수) · 안내 한 줄 · (확인 권한자) 「고정 요청 N건」 · 의견 거르기 칩(의견 전체·찬성·반대·중립 = `counts`, 의견 수 → `kind`) · 의견 목록(고정 의견 맨 위 「고정」) ·
            의견 쓰기(투표한 회원만 — 내 입장 배지 · @ 회원 지정 · `opinionMax` · `opinion: true` 로 보낸다). 중립 칸은 `neutralEnabled || votes.neutral || counts.neutral` 일 때만(`UtilDebate.neutralVisible`).
            끝난 토론(`enabled false`)은 읽기 전용(투표·쓰기·고정 요청 없음). 의견 답글은 원댓글을 따른다(서버가 정한다 — 투표하지 않았으면 먼저 고르라고 알린다).
        두 구획 모두: 추천·비추천(`reactions` — 하나를 누르면 다른 하나가 풀린 서버 값을 그대로), 고치기·지우기(비회원 비밀번호), 신고(`report` 이벤트 → 페이지 대화상자), 숨김 문구(`hiddenBy`)·차단 접기(`FoldedRow`)는 `CommentItem`.
        서버 호출은 페이지가 `usePostComments` 로 하고 여기에는 그 함수(`actions`·`debateActions`)만 내려온다. 토론 오류(`DEBATE_NOT_OPEN`·`STANCE_REQUIRED`·`STANCE_LOCKED`)는 서버 문구를 보이고 목록을 다시 받아 지금 상태로 맞춘다.
        입장 색은 `main.css` 「토론 입장 색」(찬성 파랑 · 반대 빨강 · 중립 초록) — 늘 입장 글자와 함께 쓴다.
    -->
    <section
        :aria-labelledby="headingId"
        :class="isDebate ? 'my-2 flex flex-col gap-3 rounded-xl border border-surface bg-surface-50 p-3.5 dark:bg-surface-900/60' : 'mt-6'"
        :data-testid="isDebate ? 'post-debate' : 'post-comments'"
    >
        <!-- ── 토론 머리: 제목 · 투표 · 막대 · 안내 ── -->
        <template v-if="isDebate">
            <h2 :id="headingId" class="m-0 flex flex-wrap items-baseline gap-x-1.5 text-[0.9375rem] font-semibold">
                {{ t('comment.debateTitle') }}
                <span v-if="debate" class="text-[0.8125rem] font-normal text-muted-color tabular-nums">{{ t('comment.debateSummary', { people: votes.total, opinions: debate.counts.opinions }) }}</span>
            </h2>
            <template v-if="debate">
                <div class="grid grid-cols-[repeat(auto-fit,minmax(5.5rem,1fr))] gap-2" role="group" :aria-label="t('comment.voteLabel')" data-testid="debate-votes">
                    <button
                        v-for="s in shownStances"
                        :key="s"
                        type="button"
                        :class="voteClass(s)"
                        :aria-pressed="debate.myStance === s"
                        :aria-label="voteAria(s)"
                        :disabled="voteDisabled(s)"
                        :data-testid="`debate-vote-${s}`"
                        @click="onVote(s)"
                    >
                        <i v-if="voting === s" class="pi pi-spinner pi-spin text-xs" aria-hidden="true" />
                        <b v-else class="text-sm">{{ t(`comment.stance.${s}`) }}</b>
                        <span class="text-xs tabular-nums" :class="debate.myStance === s ? '' : 'text-muted-color'">{{ t('comment.voteCount', { n: UtilDebate.voteOf(votes, s) }) }}</span>
                    </button>
                </div>
                <div class="flex flex-col gap-1.5">
                    <div class="flex h-2.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" role="img" :aria-label="barAria">
                        <div v-for="share in shares" :key="share.stance" :class="UtilDebate.solidClass(share.stance)" class="transition-[width] duration-200" :style="{ width: `${share.percent}%` }" />
                    </div>
                    <div class="flex flex-wrap gap-x-3.5 gap-y-0.5 text-xs tabular-nums" aria-hidden="true">
                        <span v-for="share in shares" :key="share.stance" :class="UtilDebate.textClass(share.stance)">{{ t('comment.voteShare', { stance: t(`comment.stance.${share.stance}`), n: share.count, p: share.percent }) }}</span>
                    </div>
                </div>
                <p class="m-0 flex flex-wrap items-center gap-x-2 text-xs text-muted-color" data-testid="debate-hint">
                    <span>{{ voteHint }}</span>
                    <Button v-if="debateOpen && board.viewer.guest" type="button" :label="t('common.login')" size="small" variant="text" @click="prompt('opinion')" />
                </p>

                <!-- 확인 권한자(글쓴이·게시판 매니저)의 고정 요청 -->
                <DebatePinReviews v-if="debateActions && (debate.pendingPinRequests ?? 0) > 0" :count="debate.pendingPinRequests ?? 0" :actions="debateActions" @retry="$emit('retry')" />

                <div class="flex flex-wrap gap-1.5 text-xs" role="group" :aria-label="t('comment.kindLabel')" data-testid="debate-kinds">
                    <button v-for="k in shownKinds" :key="k" type="button" :class="chipClass(k)" :aria-pressed="kind === k" @click="$emit('kind', k)">
                        {{ t(`comment.kind.${k}`) }} <span class="tabular-nums">{{ chipCount(k) }}</span>
                    </button>
                </div>
            </template>
        </template>

        <!-- ── 일반 댓글 머리: 제목 · 정렬 ── -->
        <div v-else class="flex items-center justify-between gap-2 border-b border-surface pb-2">
            <h2 :id="headingId" class="text-base font-semibold">
                {{ t('comment.title') }} <span class="text-primary tabular-nums">{{ generalCount }}</span>
            </h2>
            <div class="flex gap-1 text-xs" role="group" :aria-label="t('comment.sortLabel')">
                <button v-for="s in SORTS" :key="s" type="button" :class="sortClass(s)" :aria-pressed="sort === s" @click="$emit('sort', s)">{{ t(s === 'OLDEST' ? 'comment.sortOldest' : 'comment.sortLatest') }}</button>
            </div>
        </div>

        <div v-if="state === 'loading'" class="py-4" role="status" :aria-label="t('comment.loading')">
            <div v-for="n in 3" :key="n" class="py-3">
                <div class="h-3 w-28 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="mt-2 h-3.5 w-3/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>
        <ErrorState v-else-if="state === 'error'" :title="t('comment.loadFailed')" :description="errorMessage" retry @retry="$emit('retry')" />

        <template v-else-if="data">
            <!-- 베스트 댓글(일반 댓글 구획만) — 추천 기준 이상, 최대 3개. 원래 자리에도 그대로 있다 -->
            <div v-if="!isDebate && data.best.length" class="mt-3 rounded-lg bg-surface-100 px-3 dark:bg-surface-800/60">
                <h3 class="pt-3 text-xs font-semibold text-muted-color">{{ t('comment.bestTitle') }}</h3>
                <div class="divide-y divide-surface">
                    <CommentItem v-for="c in data.best" :key="`best-${c.id}`" :comment="c" best :reactions="reactions" @like="like(c)" @dislike="dislike(c)" />
                </div>
            </div>

            <template v-if="data.threads.length === 0">
                <p v-if="isDebate" class="m-0 py-3 text-center text-sm text-muted-color">{{ kind === 'OPINION' ? t('comment.opinionEmpty') : t('comment.kindEmpty') }}</p>
                <EmptyState v-else :title="t('comment.emptyTitle')" :description="board.viewer.canComment ? t('comment.emptyText') : ''" icon="pi pi-comments" />
            </template>

            <div v-else class="divide-y divide-surface">
                <div
                    v-for="thread in data.threads"
                    :key="thread.id"
                    :class="isDebate && thread.pinned ? 'mb-1.5 rounded-lg border border-surface! bg-surface-0 px-3 dark:bg-surface-900' : ''"
                >
                    <CommentItem
                        :comment="thread"
                        :can-reply="canReply"
                        :report-enabled="board.reportEnabled"
                        :editing="editingId === thread.id"
                        :reactions="reactions"
                        :pin-requests="pinRequestsOpen"
                        :pin-busy="pinBusyId === thread.id"
                        @like="like(thread)"
                        @dislike="dislike(thread)"
                        @reply="openReply(thread, thread)"
                        @edit="openEdit(thread)"
                        @delete="askDelete(thread)"
                        @report="$emit('report', thread)"
                        @view-delayed="viewDelayed(thread)"
                        @pin-request="requestPin(thread)"
                        @pin-cancel="cancelPin(thread)"
                    >
                        <template #editor>
                            <CommentForm
                                :key="`edit-${thread.id}`"
                                class="mt-2"
                                :board="board"
                                :guest-rules="thread.author.kind === 'GUEST'"
                                :initial-content="thread.content ?? ''"
                                :initial-format="thread.contentFormat ?? 'TEXT'"
                                :max-length="editMax(thread)"
                                :submit-label="t('common.save')"
                                cancelable
                                autofocus
                                :submitting="busy === `edit:${thread.id}`"
                                :field-errors="active === `edit:${thread.id}` ? fieldErrors : {}"
                                :form-error="active === `edit:${thread.id}` ? formError : ''"
                                @submit="submitEdit(thread, $event.content, $event.contentFormat)"
                                @cancel="closeForms"
                            />
                        </template>
                    </CommentItem>
                    <CommentItem
                        v-for="r in thread.replies"
                        :key="r.id"
                        :comment="r"
                        reply
                        :can-reply="canReply"
                        :report-enabled="board.reportEnabled"
                        :editing="editingId === r.id"
                        :reactions="reactions"
                        @like="like(r)"
                        @dislike="dislike(r)"
                        @reply="openReply(thread, r)"
                        @edit="openEdit(r)"
                        @delete="askDelete(r)"
                        @report="$emit('report', r)"
                        @view-delayed="viewDelayed(r)"
                    >
                        <template #editor>
                            <CommentForm
                                :key="`edit-${r.id}`"
                                class="mt-2"
                                :board="board"
                                :guest-rules="r.author.kind === 'GUEST'"
                                :initial-content="r.content ?? ''"
                                :initial-format="r.contentFormat ?? 'TEXT'"
                                :max-length="editMax(r)"
                                :submit-label="t('common.save')"
                                cancelable
                                autofocus
                                :submitting="busy === `edit:${r.id}`"
                                :field-errors="active === `edit:${r.id}` ? fieldErrors : {}"
                                :form-error="active === `edit:${r.id}` ? formError : ''"
                                @submit="submitEdit(r, $event.content, $event.contentFormat)"
                                @cancel="closeForms"
                            />
                        </template>
                    </CommentItem>
                    <!-- 답글 입력 — 원댓글 묶음 아래 -->
                    <div v-if="replyTarget && replyTarget.parentId === thread.id" class="pb-3 pl-6">
                        <CommentForm
                            :key="`reply-${replyTarget.replyToId ?? replyTarget.parentId}-${formSeq}`"
                            :board="board"
                            :guest-mode="board.viewer.guest"
                            :guest-rules="board.viewer.guest"
                            :reply-to-name="replyTarget.name"
                            :max-length="replyMax"
                            :submit-label="t('comment.replySubmit')"
                            cancelable
                            autofocus
                            :submitting="busy === 'reply'"
                            :field-errors="active === 'reply' ? fieldErrors : {}"
                            :form-error="active === 'reply' ? formError : ''"
                            @submit="submitNew($event, replyTarget)"
                            @cancel="closeForms"
                        />
                    </div>
                </div>
            </div>
        </template>

        <!-- ── 토론: 의견 쓰기 — 열린 토론에서 투표한 회원만 ── -->
        <div v-if="isDebate && composerOpen && debate?.myStance" class="flex flex-col gap-2 rounded-lg border border-surface bg-surface-0 p-3 dark:bg-surface-900" data-testid="debate-composer">
            <div class="flex flex-wrap items-center gap-2 text-sm">
                <span class="text-xs text-muted-color">{{ t('comment.myStance') }}</span>
                <DebateStanceBadge :stance="debate.myStance" />
                <span class="text-xs text-muted-color">{{ t('comment.myStanceHint') }}</span>
            </div>

            <!-- @ 회원 지정 — 이 글에 의견을 쓴 회원을 골라 그 회원의 최근 의견에 답글로 쓴다 -->
            <div class="flex flex-col gap-1.5">
                <div v-if="mentionTarget" class="flex flex-wrap items-center gap-1.5 text-xs">
                    <span class="font-semibold text-primary">{{ t('comment.mentionTarget', { name: mentionTarget.name }) }}</span>
                    <Button type="button" :label="t('comment.mentionClear')" size="small" severity="secondary" variant="text" @click="mentionTarget = null" />
                </div>
                <Button v-else-if="!mentionOpen" type="button" :label="t('comment.mention')" icon="pi pi-at" size="small" severity="secondary" variant="text" class="self-start" @click="openMention" />
                <div v-if="mentionOpen && !mentionTarget" class="flex flex-col gap-1.5">
                    <div class="flex gap-2">
                        <InputText v-model="mentionQuery" type="search" size="small" class="min-w-0 flex-1" :placeholder="t('comment.mentionSearch')" :aria-label="t('comment.mentionSearch')" />
                        <Button type="button" :label="t('common.cancel')" size="small" severity="secondary" variant="text" @click="mentionOpen = false" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('comment.mentionHelp') }}</p>
                    <p v-if="mentionError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ mentionError }}</p>
                    <p v-else-if="!mentionLoading && mentionItems.length === 0" class="m-0 text-xs text-muted-color">{{ t('comment.mentionEmpty') }}</p>
                    <ul v-else class="m-0 flex list-none flex-col p-0" :aria-busy="mentionLoading">
                        <li v-for="c in mentionItems" :key="c.replyToId">
                            <button
                                type="button"
                                class="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm hover:bg-surface-200 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-700"
                                @click="chooseMention(c)"
                            >
                                <PostAuthorLabel :author="c.author" />
                            </button>
                        </li>
                    </ul>
                </div>
            </div>

            <CommentForm
                :key="`opinion-${formSeq}`"
                :board="board"
                :reply-to-name="mentionTarget?.name ?? ''"
                :max-length="debate.opinionMax"
                :placeholder="t('comment.opinionPlaceholder', { stance: t(`comment.stance.${debate.myStance}`) })"
                :aria-label="t('comment.opinionInput')"
                :submit-label="t('comment.opinionSubmit')"
                :submitting="busy === 'new'"
                :field-errors="active === 'new' ? fieldErrors : {}"
                :form-error="active === 'new' ? formError : ''"
                @submit="submitNew($event, mentionTarget)"
            />
        </div>

        <!-- ── 일반 댓글: 새 댓글 — 막혔으면 까닭(댓글 받지 않음·로그인 필요·등급 미달·잠금) ── -->
        <div v-if="!isDebate" class="mt-4 border-t border-surface pt-4">
            <CommentForm
                v-if="board.viewer.canComment"
                :key="`new-${formSeq}`"
                :board="board"
                :guest-mode="board.viewer.guest"
                :guest-rules="board.viewer.guest"
                :submitting="busy === 'new'"
                :field-errors="active === 'new' ? fieldErrors : {}"
                :form-error="active === 'new' ? formError : ''"
                @submit="submitNew($event, null)"
            />
            <div v-else class="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-surface-100 px-4 py-3 text-sm text-muted-color dark:bg-surface-800/60" data-testid="comment-blocked">
                <span><i class="pi pi-comment mr-1.5" aria-hidden="true" />{{ blockedText }}</span>
                <Button v-if="board.viewer.commentBlockedReason === 'LOGIN_REQUIRED'" type="button" :label="t('common.login')" size="small" @click="prompt('comment')" />
            </div>
        </div>

        <BoardPasswordDialog
            v-model:visible="passwordOpen"
            :title="passwordFor?.mode === 'delete' ? t('comment.deleteTitle') : t('comment.editTitle')"
            :description="t('comment.passwordDescription')"
            :confirm-label="passwordFor?.mode === 'delete' ? t('common.delete') : t('common.save')"
            :danger="passwordFor?.mode === 'delete'"
            :loading="passwordLoading"
            :error="passwordError"
            @submit="onPassword"
        />
    </section>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { CommentListResponse, CommentSort, CommentView } from '~/composables/board/api/useCommentApi.types';
import type { Commenter, CommentKind, DebateStance, OpinionKind } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import type { CommentLoadState } from '~/composables/board/usePostComments';
import type { CommentActions, DebateActions } from '~/components/board/CommentSection.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilDebate from '~/utils/util-debate';
import UtilPost from '~/utils/util-post';

interface Props {
    board: BoardDetail;
    data: CommentListResponse | null;
    state: CommentLoadState;
    errorMessage?: string;
    sort: CommentSort;
    /** 목록 구분 — 일반 댓글 구획은 GENERAL, 토론 구획은 OPINION·PRO·CON·NEUTRAL */
    kind?: CommentKind;
    /** `general` = 맨 아래 일반 댓글 · `debate` = 본문 영역 안 토론 */
    variant?: 'general' | 'debate';
    actions: CommentActions;
    /** 토론 구획의 동작(투표·@ 지정·고정 요청) — `variant="debate"` 일 때 */
    debateActions?: DebateActions | null;
}

const props = withDefaults(defineProps<Props>(), {
    errorMessage: '',
    kind: 'GENERAL',
    variant: 'general',
    debateActions: null,
});

const emit = defineEmits<{ sort: [value: CommentSort]; kind: [value: CommentKind]; retry: []; report: [comment: CommentView] }>();

const SORTS: readonly CommentSort[] = ['OLDEST', 'LATEST'];
const KINDS: readonly OpinionKind[] = ['OPINION', 'PRO', 'CON', 'NEUTRAL'];
/** 토론 상태가 바뀐 거절 — 서버 문구를 보이고 목록을 다시 받아 지금 상태(열림·내 입장)로 맞춘다 */
const DEBATE_CODES: ReadonlySet<string> = new Set(['DEBATE_NOT_OPEN', 'STANCE_LOCKED', 'STANCE_REQUIRED']);
/** 고정 요청이 지금 상태와 맞지 않는 거절 — 알리고 목록을 맞춘다 */
const PIN_CODES: ReadonlySet<string> = new Set(['PIN_REQUEST_NOT_OPINION', 'OPINION_UNAVAILABLE', 'DEBATE_NOT_OPEN', 'OPINION_ALREADY_PINNED', 'PIN_REQUEST_EXISTS']);
/** 고른 칩 — 중립 surface 채움(시안: 글자·바탕을 뒤집은 칩) */
const SOLID_NEUTRAL = 'bg-surface-900 text-surface-0 dark:bg-surface-100 dark:text-surface-900';

const { t } = useI18n();
const route = useRoute();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const headingId = useId();

/** 지금 열린 폼과 그 서버 오류 — `new` · `reply` · `edit:{id}` */
const active = ref('');
const busy = ref('');
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
/** 성공하면 올려서 폼을 새로 만든다(입력·비밀번호를 지운다) */
const formSeq = ref(0);

const replyTarget = ref<{ parentId: string; replyToId: string | null; name: string } | null>(null);
const editingId = ref<string | null>(null);

const blockedText = computed(() => UtilBoardMessages.commentBlocked(t, props.board.viewer.commentBlockedReason, props.board.commentMinGradeLabel));
const reactions = computed(() => props.data?.reactions ?? null);
const isDebate = computed(() => props.variant === 'debate');

/* ── 하이라이트 토론 ── */
const debate = computed(() => props.data?.debate ?? null);
const votes = computed(() => debate.value?.votes ?? UtilDebate.EMPTY_VOTES);
/** 지금 의견을 받는다(끝난 토론은 읽기 전용) */
const debateOpen = computed(() => Boolean(debate.value?.enabled));
const neutralShown = computed(() => (debate.value ? UtilDebate.neutralVisible(debate.value) : false));
const shownStances = computed(() => UtilDebate.stances(neutralShown.value));
const shownKinds = computed(() => KINDS.filter((k) => k !== 'NEUTRAL' || neutralShown.value));
const shares = computed(() => UtilDebate.shares(votes.value, shownStances.value));
/** 투표·의견 쓰기를 할 수 있는 회원 — 열린 토론 + 댓글 쓰기 자격(비회원은 단추를 누르면 로그인 안내) */
const composerOpen = computed(() => debateOpen.value && !props.board.viewer.guest && props.board.viewer.canComment);
/**
 * 내 의견에 고정 요청을 보낼 수 있다 — 게시판이 요청을 받지 않으면(`debate.pinRequestEnabled` false) 처음부터 숨기고,
 * 그 사이 정책이 바뀌어 `PIN_REQUEST_OFF` 를 받으면 이 화면에서 단추를 거둔다. 이미 보낸 요청의 대기·거절 상태는 `CommentItem` 이 따로 보인다
 */
const pinOff = ref(false);
const pinRequestsOpen = computed(
    () => isDebate.value && debateOpen.value && Boolean(debate.value?.pinRequestEnabled) && !props.board.viewer.guest && !pinOff.value,
);

/** 「댓글 N」 — 일반 구획 전체(일반 원댓글 + 답글). 옛 응답은 보이는 댓글 전체 */
const generalCount = computed(() => props.data?.debate?.counts.generalTotal ?? props.data?.totalCount ?? 0);

/** 답글 단추 — 일반 구획은 댓글 쓰기 자격, 토론 구획은 열린 토론에서만(의견 답글 = 의견) */
const canReply = computed(() => props.board.viewer.canComment && (!isDebate.value || debateOpen.value));
const replyMax = computed(() => (isDebate.value && debate.value ? debate.value.opinionMax : UtilPost.COMMENT_MAX));
function editMax(comment: CommentView): number {
    return comment.stance && debate.value ? debate.value.opinionMax : UtilPost.COMMENT_MAX;
}

function stanceName(s: DebateStance): string {
    return t(`comment.stance.${s}`);
}
function stanceTo(s: DebateStance): string {
    return t(`comment.stanceTo.${s}`);
}

/** 중립이 꺼진 하이라이트 — 이미 중립인 회원 말고는 새로 고를 수 없다 */
function voteDisabled(s: DebateStance): boolean {
    const d = debate.value;
    if (!d || !debateOpen.value || Boolean(voting.value)) return true;
    if (!props.board.viewer.guest && !props.board.viewer.canComment) return true;
    return s === 'NEUTRAL' && !d.neutralEnabled && d.myStance !== 'NEUTRAL';
}

function voteClass(s: DebateStance): string {
    const mine = debate.value?.myStance === s;
    return [
        'grid justify-items-center gap-0.5 rounded-[0.625rem] border-[1.5px] px-2.5 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default',
        mine ? `${UtilDebate.solidClass(s)} border-transparent` : `${UtilDebate.textClass(s)} border-surface bg-surface-0 enabled:hover:bg-surface-100 disabled:opacity-60 dark:bg-surface-900 dark:enabled:hover:bg-surface-800`,
    ].join(' ');
}

function voteAria(s: DebateStance): string {
    const n = UtilDebate.voteOf(votes.value, s);
    const mine = debate.value?.myStance;
    return mine && mine !== s ? t('comment.changeStanceAria', { to: stanceTo(s), n }) : t('comment.voteAria', { stance: stanceName(s), n });
}

const barAria = computed(() => shares.value.map((share) => t('comment.voteShare', { stance: stanceName(share.stance), n: share.count, p: share.percent })).join(', '));

/** 투표 아래 안내 한 줄 */
const voteHint = computed(() => {
    const d = debate.value;
    if (!d) return '';
    if (!d.enabled) return t('comment.debateClosed');
    if (props.board.viewer.guest) return t('comment.debateGuestHint');
    if (!props.board.viewer.canComment) return blockedText.value;
    if (d.myStance) return t('comment.votedHint', { to: stanceTo(d.myStance) });
    const hint = neutralShown.value && d.neutralEnabled ? t('comment.voteHintNeutral') : t('comment.voteHint');
    return !d.neutralEnabled && neutralShown.value ? `${hint} ${t('comment.neutralClosed')}` : hint;
});

function chipCount(k: OpinionKind): number {
    const counts = debate.value?.counts;
    if (!counts) return 0;
    if (k === 'OPINION') return counts.opinions;
    if (k === 'PRO') return counts.pro;
    if (k === 'CON') return counts.con;
    return counts.neutral;
}

function chipClass(k: OpinionKind): string {
    const stance = UtilDebate.kindStance(k);
    return [
        'rounded-full border px-2.5 py-1 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary',
        props.kind === k ? `${SOLID_NEUTRAL} border-transparent font-semibold` : `${stance ? UtilDebate.textClass(stance) : 'text-muted-color hover:text-color'} border-surface bg-surface-0 dark:bg-surface-900`,
    ].join(' ');
}

/**
 * 입장 고르기(투표) — 처음이면 바로 정하고, 이미 고른 회원이 다른 단추를 누르면 확인창(「찬성 → 반대로 바꿀까요?」 + 옮겨 갈 내 의견 수) 뒤에 바꾼다(소급).
 * 같은 단추는 아무것도 하지 않는다(입장 취소는 없다). 비회원은 로그인 안내.
 */
const voting = ref<DebateStance | null>(null);
async function onVote(next: DebateStance): Promise<void> {
    const d = debate.value;
    if (!d || !props.debateActions || voting.value) return;
    if (props.board.viewer.guest) return prompt('opinion');
    if (!d.enabled || d.myStance === next) return;
    const current = d.myStance;
    if (current) {
        const count = d.myOpinionCount;
        const message = count > 0 ? t('comment.changeStanceMoves', { from: stanceName(current), to: stanceTo(next), n: count }) : t('comment.changeStanceFuture', { stance: stanceName(next) });
        const ok = await confirm({ title: t('comment.changeStanceTitle', { from: stanceName(current), to: stanceTo(next) }), message, confirmLabel: t('comment.changeStanceTo', { to: stanceTo(next) }) });
        if (!ok) return;
    }
    voting.value = next;
    try {
        const { stance, moved } = await props.debateActions.vote(next);
        const to = stanceTo(stance);
        if (!current) toast.show(t('comment.voted', { to }), { tone: 'success' });
        else toast.show(moved > 0 ? t('comment.stanceChangedMoved', { to, n: moved }) : t('comment.stanceChanged', { to }), { tone: 'success' });
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('opinion');
        else {
            toast.error(e instanceof ApiError && e.field('stance') ? (e.field('stance') ?? '') : UtilBoardMessages.of(t, e), 8000);
            // 토론이 그새 닫혔거나 중립을 받지 않게 됐다 — 목록을 다시 받아 지금 상태로
            if (e instanceof ApiError && (DEBATE_CODES.has(e.code) || e.field('stance'))) emit('retry');
        }
    } finally {
        voting.value = null;
    }
}

/* ── 내 의견 고정 요청 ── */
const pinBusyId = ref('');
async function requestPin(comment: CommentView): Promise<void> {
    if (!props.debateActions || pinBusyId.value) return;
    pinBusyId.value = comment.id;
    try {
        await props.debateActions.requestPin(comment.id);
        toast.show(t('comment.pinRequestSent'), { tone: 'success' });
    } catch (e) {
        pinFailed(e);
    } finally {
        pinBusyId.value = '';
    }
}

async function cancelPin(comment: CommentView): Promise<void> {
    if (!props.debateActions || pinBusyId.value) return;
    pinBusyId.value = comment.id;
    try {
        await props.debateActions.cancelPin(comment.id);
        toast.show(t('comment.pinRequestCanceled'));
    } catch (e) {
        pinFailed(e);
    } finally {
        pinBusyId.value = '';
    }
}

function pinFailed(e: unknown): void {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') return prompt('opinion');
    if (handleStale(e)) return;
    toast.error(UtilBoardMessages.of(t, e), 8000);
    if (!(e instanceof ApiError)) return;
    // 게시판이 고정 요청을 받지 않는다 — 공개 응답에 이 설정이 없어 누른 뒤에야 안다. 이 화면에서는 단추를 거둔다
    if (e.code === 'PIN_REQUEST_OFF') pinOff.value = true;
    else if (PIN_CODES.has(e.code)) emit('retry');
}

/* ── @ 회원 지정 — 고른 회원의 최근 의견에 답글로 쓴다 ── */
const mentionOpen = ref(false);
const mentionQuery = ref('');
const mentionItems = ref<Commenter[]>([]);
const mentionLoading = ref(false);
const mentionError = ref('');
const mentionTarget = ref<{ parentId: string; replyToId: string | null; name: string } | null>(null);
let mentionTimer: ReturnType<typeof setTimeout> | null = null;
let mentionAbort: AbortController | null = null;

async function searchMention(): Promise<void> {
    if (!props.debateActions) return;
    mentionAbort?.abort();
    const abort = new AbortController();
    mentionAbort = abort;
    mentionLoading.value = true;
    mentionError.value = '';
    try {
        const found = await props.debateActions.commenters(mentionQuery.value.trim(), abort.signal);
        if (mentionAbort !== abort) return;
        mentionItems.value = found.items;
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            mentionOpen.value = false;
            prompt('opinion');
            return;
        }
        mentionItems.value = [];
        mentionError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mentionAbort === abort) mentionLoading.value = false;
    }
}

function openMention(): void {
    mentionOpen.value = true;
    mentionQuery.value = '';
    void searchMention();
}

watch(mentionQuery, () => {
    if (!mentionOpen.value) return;
    if (mentionTimer) clearTimeout(mentionTimer);
    mentionTimer = setTimeout(() => void searchMention(), 250);
});

function chooseMention(c: Commenter): void {
    mentionTarget.value = { parentId: c.parentId, replyToId: c.replyToId, name: c.author.name };
    mentionOpen.value = false;
}

onBeforeUnmount(() => {
    if (mentionTimer) clearTimeout(mentionTimer);
    mentionAbort?.abort();
});

function sortClass(s: CommentSort): string {
    return [
        'rounded-full px-2.5 py-1 focus-visible:outline-2 focus-visible:outline-primary',
        props.sort === s ? 'bg-surface-900 font-semibold text-surface-0 dark:bg-surface-100 dark:text-surface-900' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
    ].join(' ');
}

function clearErrors(target: string): void {
    active.value = target;
    fieldErrors.value = {};
    formError.value = '';
}

/**
 * 그 댓글이 이미 없거나(`404 COMMENT_NOT_FOUND`) 숨겨졌다(`403 COMMENT_HIDDEN`) — 서버 문구를 알리고 폼을 닫은 뒤 목록을 다시 받는다.
 * @returns 그런 오류였으면 true.
 */
function handleStale(e: unknown): boolean {
    if (!(e instanceof ApiError) || (e.code !== 'COMMENT_NOT_FOUND' && e.code !== 'COMMENT_HIDDEN')) return false;
    toast.error(e.message, 8000);
    passwordOpen.value = false;
    passwordFor.value = null;
    closeForms();
    emit('retry');
    return true;
}

/** 서버 오류를 폼에 붙인다 — 입력 오류는 칸 아래, 나머지는 폼 아래 한 줄. 로그인 필요(401)는 안내 시트, 사라진 댓글은 목록을 다시 받는다 */
function showError(e: unknown, action: 'comment' | 'reply' | 'opinion'): void {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
        prompt(action);
        return;
    }
    if (handleStale(e)) return;
    if (e instanceof ApiError && e.fields && Object.keys(e.fields).length) {
        fieldErrors.value = { ...e.fields };
        // 답글 대상 오류(@ 회원 지정한 의견이 그새 지워짐 등)·입장 칸 오류는 폼에 칸이 없다 — 아래 한 줄로
        if (e.fields.parentId) formError.value = e.fields.parentId;
        else if (e.fields.stance) formError.value = e.fields.stance;
    } else formError.value = UtilBoardMessages.of(t, e);
    // 토론이 그새 닫혔거나 입장이 없거나 다른 창에서 바뀌었다 — 목록을 다시 받아 지금 상태로 맞춘다(쓰던 글은 그대로)
    if (e instanceof ApiError && DEBATE_CODES.has(e.code)) emit('retry');
}

function closeForms(): void {
    replyTarget.value = null;
    editingId.value = null;
    active.value = '';
}

function openReply(thread: CommentView, target: CommentView): void {
    // 토론 의견의 답글도 의견이다 — 투표한 회원만 쓸 수 있다
    if (isDebate.value) {
        if (props.board.viewer.guest) return prompt('opinion');
        if (!debate.value?.myStance) return toast.show(t('comment.voteFirst'));
    }
    editingId.value = null;
    replyTarget.value = { parentId: thread.id, replyToId: target.id === thread.id ? null : target.id, name: target.author.name };
    clearErrors('reply');
}

function openEdit(comment: CommentView): void {
    replyTarget.value = null;
    editingId.value = comment.id;
    clearErrors(`edit:${comment.id}`);
}

async function submitNew(value: { content: string; contentFormat: ContentFormat; guest: GuestCredentials | null }, target: { parentId: string; replyToId: string | null } | null): Promise<void> {
    // 새 댓글·의견 상자(@ 회원 지정으로 답글이 되어도 이 상자)인가 · 답글 상자인가
    const fromNewBox = !target || target === mentionTarget.value;
    const key = fromNewBox ? 'new' : 'reply';
    clearErrors(key);
    busy.value = key;
    // 의견 상자의 원댓글만 의견 표시(+ 내가 아는 입장)를 보낸다. 답글(@ 지정 포함)은 서버가 원댓글을 따른다, 일반 댓글은 보내지 않는다
    const opinionStance = isDebate.value && fromNewBox && !target ? (debate.value?.myStance ?? null) : null;
    try {
        await props.actions.create(value.content, value.contentFormat, { parentId: target?.parentId ?? null, replyToId: target?.replyToId ?? null }, value.guest, opinionStance);
        formSeq.value += 1;
        closeForms();
        if (fromNewBox) mentionTarget.value = null;
        toast.show(isDebate.value ? t('comment.opinionPosted') : t('comment.posted'), { tone: 'success' });
    } catch (e) {
        showError(e, isDebate.value ? 'opinion' : fromNewBox ? 'comment' : 'reply');
    } finally {
        busy.value = '';
    }
}

/* ── 고치기·지우기 — 비회원 댓글은 비밀번호를 먼저 묻는다 ── */
const passwordOpen = ref(false);
const passwordLoading = ref(false);
const passwordError = ref('');
const passwordFor = ref<{ mode: 'edit' | 'delete'; comment: CommentView; content: string; contentFormat: ContentFormat } | null>(null);

async function submitEdit(comment: CommentView, content: string, contentFormat: ContentFormat): Promise<void> {
    clearErrors(`edit:${comment.id}`);
    if (comment.author.kind === 'GUEST') {
        passwordFor.value = { mode: 'edit', comment, content, contentFormat };
        passwordError.value = '';
        passwordOpen.value = true;
        return;
    }
    busy.value = `edit:${comment.id}`;
    try {
        await props.actions.update(comment.id, content, contentFormat, null);
        closeForms();
        toast.show(t('comment.updated'), { tone: 'success' });
    } catch (e) {
        showError(e, 'comment');
    } finally {
        busy.value = '';
    }
}

async function askDelete(comment: CommentView): Promise<void> {
    if (comment.author.kind === 'GUEST') {
        passwordFor.value = { mode: 'delete', comment, content: '', contentFormat: 'TEXT' };
        passwordError.value = '';
        passwordOpen.value = true;
        return;
    }
    const ok = await confirm({ title: t('comment.deleteTitle'), message: t('comment.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    try {
        await props.actions.remove(comment.id, null);
        toast.show(t('comment.deletedToast'), { tone: 'success' });
    } catch (e) {
        if (!handleStale(e)) toast.error(UtilBoardMessages.of(t, e), 8000);
    }
}

async function onPassword(password: string): Promise<void> {
    const target = passwordFor.value;
    if (!target) return;
    passwordLoading.value = true;
    passwordError.value = '';
    try {
        if (target.mode === 'delete') {
            await props.actions.remove(target.comment.id, password);
            toast.show(t('comment.deletedToast'), { tone: 'success' });
        } else {
            await props.actions.update(target.comment.id, target.content, target.contentFormat, password);
            closeForms();
            toast.show(t('comment.updated'), { tone: 'success' });
        }
        passwordOpen.value = false;
        passwordFor.value = null;
    } catch (e) {
        // 틀린 비밀번호는 대화상자에 서버 문구 그대로. 입력 오류(링크 등)는 편집 폼으로 돌려보낸다
        if (e instanceof ApiError && e.code === 'VALIDATION' && target.mode === 'edit') {
            passwordOpen.value = false;
            showError(e, 'comment');
        } else if (!handleStale(e)) {
            // 틀린 비밀번호(403 PASSWORD_MISMATCH)·여러 번 틀림(429 PASSWORD_ATTEMPTS_EXCEEDED)은 서버 문구 그대로
            passwordError.value = UtilBoardMessages.of(t, e);
        }
    } finally {
        passwordLoading.value = false;
    }
}

/* ── 추천·비추천·공개 전 댓글 ── */
/** 같은 댓글을 연달아 누르지 않게 — 누르는 동안의 댓글 ID */
const reacting = ref('');

/** 반응 실패 — 로그인 안내 · 사라진 댓글 · 비추천을 받지 않게 된 게시판(목록을 다시 받아 단추를 맞춘다) · 그 밖 서버 문구(`CANNOT_DISLIKE_OWN`·`ALREADY_DISLIKED` 등) */
function reactionFailed(e: unknown): void {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') return prompt('like');
    if (handleStale(e)) return;
    toast.error(UtilBoardMessages.of(t, e));
    if (e instanceof ApiError && (e.code === 'COMMENT_DISLIKE_DISABLED' || e.code === 'ALREADY_DISLIKED')) emit('retry');
}

async function like(comment: CommentView): Promise<void> {
    if (props.board.viewer.guest) return prompt('like');
    if (comment.liked) return toast.show(t('comment.alreadyLiked'));
    if (reacting.value) return;
    reacting.value = comment.id;
    try {
        await props.actions.like(comment.id);
    } catch (e) {
        reactionFailed(e);
    } finally {
        reacting.value = '';
    }
}

/** 비추천 — 이미 비추천했으면 취소(추천과 달리 취소가 있다) */
async function dislike(comment: CommentView): Promise<void> {
    if (props.board.viewer.guest) return prompt('like');
    if (reacting.value) return;
    reacting.value = comment.id;
    try {
        if (comment.disliked) await props.actions.undislike(comment.id);
        else await props.actions.dislike(comment.id);
    } catch (e) {
        reactionFailed(e);
    } finally {
        reacting.value = '';
    }
}

function viewDelayed(comment: CommentView): void {
    prompt('view', `${route.path}#comment-${comment.id}`);
}
</script>
