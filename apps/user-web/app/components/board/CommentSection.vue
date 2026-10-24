<template>
    <!--
        글 보기의 댓글 구획 — 베스트 댓글(위에 따로) · 정렬(오래된순·최신순) · 원댓글 + 답글 1단계 · 새 댓글 입력.
        서버 호출은 페이지가 `usePostComments` 로 하고 여기에는 그 함수(`actions`)만 내려온다. 비회원 비밀번호 확인·지우기 확인·로그인 안내는 여기서 띄운다.
        신고는 페이지의 신고 대화상자를 쓴다(`report` 이벤트).
        하이라이트 토론(`data.debate`, community `api-debate.md` 1~5절): 토론이 열렸거나(`enabled`) 이미 쓴 의견이 있으면 구분 탭(전체·일반·찬성·반대 + 수)을 보인다(끝난 토론은 읽기만).
          열린 토론의 새 댓글 상자 위에는 — 입장을 정하지 않은 회원: [일반 · 찬성 · 반대] 고르기(찬성·반대로 처음 쓰면 입장이 정해진다) / 정한 회원: 「내 입장 [배지] · 입장 바꾸기」(확인 뒤 `PUT …/stance`) / 비회원: 로그인 안내 한 줄.
          회원은 「@ 회원 지정」으로 이 글에 댓글을 쓴 회원을 골라 그 회원의 최근 댓글에 답글로 쓴다(`parentId`·`replyToId` — 상대 이름은 `replyTo` 로 `@이름` 이 그려지므로 본문에 덧붙이지 않는다).
          입장 배지가 붙는 댓글의 글자 수는 `debate.opinionMax`. 토론 오류(`DEBATE_NOT_OPEN`·`STANCE_LOCKED`·`STANCE_NOT_SET`)는 서버 문구를 보이고 목록을 다시 받아 지금 상태로 맞춘다.
          토론이 없는 글(`enabled false` · 의견 0)은 예전 화면 그대로다.
    -->
    <section :aria-labelledby="headingId" class="mt-6">
        <div class="flex items-center justify-between gap-2 border-b border-surface pb-2">
            <h2 :id="headingId" class="text-base font-semibold">
                {{ t('comment.title') }} <span class="text-primary tabular-nums">{{ data?.totalCount ?? 0 }}</span>
            </h2>
            <div class="flex gap-1 text-xs" role="group" :aria-label="t('comment.sortLabel')">
                <button v-for="s in SORTS" :key="s" type="button" :class="sortClass(s)" :aria-pressed="sort === s" @click="$emit('sort', s)">{{ t(s === 'OLDEST' ? 'comment.sortOldest' : 'comment.sortLatest') }}</button>
            </div>
        </div>

        <!-- 하이라이트 토론 구분 탭 — 열린 토론이거나 이미 쓴 의견이 있을 때만(없는 글은 예전 그대로) -->
        <div v-if="debate && showTabs" class="mt-2 flex flex-col gap-1.5" data-testid="comment-kinds">
            <div class="flex flex-wrap gap-1 text-xs" role="group" :aria-label="t('comment.kindLabel')">
                <button v-for="k in KINDS" :key="k" type="button" :class="kindClass(k)" :aria-pressed="kind === k" @click="$emit('kind', k)">
                    {{ t(`comment.kind.${k}`) }} <span class="tabular-nums opacity-80">{{ kindCount(k) }}</span>
                </button>
            </div>
            <p v-if="!debate.enabled" class="m-0 text-xs text-muted-color">{{ t('comment.debateClosed') }}</p>
        </div>

        <div v-if="state === 'loading'" class="py-4" role="status" :aria-label="t('comment.loading')">
            <div v-for="n in 3" :key="n" class="py-3">
                <div class="h-3 w-28 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="mt-2 h-3.5 w-3/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>
        <ErrorState v-else-if="state === 'error'" :title="t('comment.loadFailed')" :description="errorMessage" retry @retry="$emit('retry')" />

        <template v-else-if="data">
            <!-- 베스트 댓글 — 추천 기준 이상, 최대 3개(가정). 원래 자리에도 그대로 있다 -->
            <div v-if="data.best.length" class="mt-3 rounded-lg bg-surface-100 px-3 dark:bg-surface-800/60">
                <h3 class="pt-3 text-xs font-semibold text-muted-color">{{ t('comment.bestTitle') }}</h3>
                <div class="divide-y divide-surface">
                    <CommentItem v-for="c in data.best" :key="`best-${c.id}`" :comment="c" best @like="like(c)" />
                </div>
            </div>

            <EmptyState v-if="data.threads.length === 0 && kind !== 'ALL'" :title="t('comment.kindEmpty')" icon="pi pi-comments" />
            <EmptyState v-else-if="data.threads.length === 0" :title="t('comment.emptyTitle')" :description="board.viewer.canComment ? t('comment.emptyText') : ''" icon="pi pi-comments" />

            <div v-else class="divide-y divide-surface">
                <div v-for="thread in data.threads" :key="thread.id">
                    <CommentItem
                        :comment="thread"
                        :can-reply="board.viewer.canComment"
                        :report-enabled="board.reportEnabled"
                        :editing="editingId === thread.id"
                        @like="like(thread)"
                        @reply="openReply(thread, thread)"
                        @edit="openEdit(thread)"
                        @delete="askDelete(thread)"
                        @report="$emit('report', thread)"
                        @view-delayed="viewDelayed(thread)"
                    >
                        <template #editor>
                            <CommentForm
                                :key="`edit-${thread.id}`"
                                class="mt-2"
                                :board="board"
                                :guest-rules="thread.author.kind === 'GUEST'"
                                :initial-content="thread.content ?? ''"
                                :initial-format="thread.contentFormat ?? 'TEXT'"
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
                        :can-reply="board.viewer.canComment"
                        :report-enabled="board.reportEnabled"
                        :editing="editingId === r.id"
                        @like="like(r)"
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

        <!-- 새 댓글 — 막혔으면 까닭(댓글 받지 않음·로그인 필요·등급 미달·잠금) -->
        <div class="mt-4 border-t border-surface pt-4">
            <!-- 열린 토론 — 입장 고르기 · 내 입장과 바꾸기 · 비회원 안내 · @ 회원 지정 -->
            <div v-if="debateOpen && board.viewer.canComment" class="mb-3 flex flex-col gap-2 rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800/60" data-testid="comment-debate">
                <p v-if="board.viewer.guest" class="m-0 flex flex-wrap items-center gap-2 text-xs text-muted-color">
                    <span>{{ t('comment.debateGuestHint') }}</span>
                    <Button type="button" :label="t('common.login')" size="small" variant="text" @click="prompt('opinion')" />
                </p>
                <template v-else-if="debate?.myStance">
                    <div class="flex flex-wrap items-center gap-2 text-sm">
                        <span class="text-xs text-muted-color">{{ t('comment.myStance') }}</span>
                        <DebateStanceBadge :stance="debate.myStance" />
                        <Button type="button" :label="t('comment.changeStance')" icon="pi pi-sync" size="small" severity="secondary" variant="text" :loading="changingStance" @click="askChangeStance" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('comment.myStanceHint') }}</p>
                </template>
                <template v-else>
                    <div class="flex flex-wrap items-center gap-2">
                        <span :id="stanceLabelId" class="text-xs text-muted-color">{{ t('comment.stancePickLabel') }}</span>
                        <div class="flex gap-1 text-xs" role="radiogroup" :aria-labelledby="stanceLabelId">
                            <button v-for="choice in STANCE_CHOICES" :key="choice ?? 'GENERAL'" type="button" role="radio" :class="stanceClass(choice)" :aria-checked="pickedStance === choice" @click="pickedStance = choice">
                                {{ choice ? t(`comment.stance.${choice}`) : t('comment.stanceGeneral') }}
                            </button>
                        </div>
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('comment.stancePickHint') }}</p>
                </template>

                <!-- @ 회원 지정 — 이 글에 댓글을 쓴 회원을 골라 그 회원의 최근 댓글에 답글로 쓴다 -->
                <div v-if="!board.viewer.guest" class="flex flex-col gap-1.5">
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
                                <button type="button" class="flex w-full items-center gap-2 rounded px-2 py-1.5 text-left text-sm hover:bg-surface-200 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-700" @click="chooseMention(c)">
                                    <PostAuthorLabel :author="c.author" />
                                </button>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>

            <CommentForm
                v-if="board.viewer.canComment"
                :key="`new-${formSeq}`"
                :board="board"
                :guest-mode="board.viewer.guest"
                :guest-rules="board.viewer.guest"
                :reply-to-name="mentionTarget?.name ?? ''"
                :max-length="newMax"
                :submitting="busy === 'new'"
                :field-errors="active === 'new' ? fieldErrors : {}"
                :form-error="active === 'new' ? formError : ''"
                @submit="submitNew($event, mentionTarget)"
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
import type { Commenter, CommentKind, DebateStance } from '~/composables/board/api/useDebateApi.types';
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import type { CommentLoadState } from '~/composables/board/usePostComments';
import type { CommentActions } from '~/components/board/CommentSection.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilPost from '~/utils/util-post';

interface Props {
    board: BoardDetail;
    data: CommentListResponse | null;
    state: CommentLoadState;
    errorMessage?: string;
    sort: CommentSort;
    /** 하이라이트 토론 구분 — 탭이 없는 글은 늘 ALL */
    kind?: CommentKind;
    actions: CommentActions;
}

const props = withDefaults(defineProps<Props>(), {
    errorMessage: '',
    kind: 'ALL',
});

const emit = defineEmits<{ sort: [value: CommentSort]; kind: [value: CommentKind]; retry: []; report: [comment: CommentView] }>();

const SORTS: readonly CommentSort[] = ['OLDEST', 'LATEST'];
const KINDS: readonly CommentKind[] = ['ALL', 'GENERAL', 'PRO', 'CON'];
/** 입장 고르기 — 일반(null) · 찬성 · 반대 */
const STANCE_CHOICES: readonly (DebateStance | null)[] = [null, 'PRO', 'CON'];
/** 토론 상태가 바뀐 거절 — 서버 문구를 보이고 목록을 다시 받아 지금 상태(열림·내 입장)로 맞춘다 */
const DEBATE_CODES: ReadonlySet<string> = new Set(['DEBATE_NOT_OPEN', 'STANCE_LOCKED', 'STANCE_NOT_SET']);

const { t } = useI18n();
const route = useRoute();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const headingId = useId();
const stanceLabelId = useId();

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

/* ── 하이라이트 토론 ── */
const debate = computed(() => props.data?.debate ?? null);
/** 지금 찬성·반대 의견을 받는다 */
const debateOpen = computed(() => Boolean(debate.value?.enabled));
/** 구분 탭 — 열린 토론이거나 이미 쓴 의견이 있다(끝난 하이라이트는 읽기만) */
const showTabs = computed(() => Boolean(debate.value && (debate.value.enabled || debate.value.counts.pro + debate.value.counts.con > 0)));
/** 새 댓글의 입장 — 입장을 정하지 않은 회원이 고른다(일반 = null) */
const pickedStance = ref<DebateStance | null>(null);
/** 새 댓글에 입장 배지가 붙으면 토론 한도 — 열린 토론에서 고른 입장이 있거나 이미 정한 입장이 있다 */
const newMax = computed(() => {
    const d = debate.value;
    if (!d || !d.enabled || props.board.viewer.guest || !(pickedStance.value || d.myStance)) return UtilPost.COMMENT_MAX;
    return d.opinionMax;
});
/** 답글 — 열린 토론이고 내 입장이 있으면 서버가 배지를 붙인다 */
const replyMax = computed(() => (debate.value?.enabled && debate.value.myStance ? debate.value.opinionMax : UtilPost.COMMENT_MAX));

function kindCount(k: CommentKind): number {
    const counts = debate.value?.counts;
    if (!counts) return 0;
    if (k === 'ALL') return counts.all;
    if (k === 'GENERAL') return counts.general;
    return k === 'PRO' ? counts.pro : counts.con;
}

function kindClass(k: CommentKind): string {
    return [
        'rounded-full px-2.5 py-1 focus-visible:outline-2 focus-visible:outline-primary',
        props.kind === k ? 'bg-primary font-semibold text-primary-contrast' : 'bg-surface-100 text-muted-color hover:bg-surface-200 dark:bg-surface-800 dark:hover:bg-surface-700',
    ].join(' ');
}

function stanceClass(choice: DebateStance | null): string {
    const tone = choice === 'PRO' ? 'bg-blue-600 text-white' : choice === 'CON' ? 'bg-red-600 text-white' : 'bg-surface-900 text-surface-0 dark:bg-surface-100 dark:text-surface-900';
    return ['rounded-full px-3 py-1 focus-visible:outline-2 focus-visible:outline-primary', pickedStance.value === choice ? `${tone} font-semibold` : 'bg-surface-0 text-muted-color hover:text-color dark:bg-surface-900'].join(' ');
}

/** 입장 바꾸기 — 지금 입장의 반대로. 바꾼 뒤 쓰는 댓글부터 새 배지(이미 쓴 댓글은 그대로) */
const changingStance = ref(false);
async function askChangeStance(): Promise<void> {
    const current = debate.value?.myStance;
    if (!current || changingStance.value) return;
    const next: DebateStance = current === 'PRO' ? 'CON' : 'PRO';
    const ok = await confirm({ title: t(`comment.changeStanceTitle.${next}`), message: t('comment.changeStanceMessage'), confirmLabel: t('comment.changeStance') });
    if (!ok) return;
    changingStance.value = true;
    try {
        const stance = await props.actions.changeStance(next);
        toast.show(t(`comment.stanceChanged.${stance}`), { tone: 'success' });
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('opinion');
        else {
            toast.error(UtilBoardMessages.of(t, e), 8000);
            if (e instanceof ApiError && DEBATE_CODES.has(e.code)) emit('retry');
        }
    } finally {
        changingStance.value = false;
    }
}

/* ── @ 회원 지정 — 고른 회원의 최근 댓글에 답글로 쓴다 ── */
const mentionOpen = ref(false);
const mentionQuery = ref('');
const mentionItems = ref<Commenter[]>([]);
const mentionLoading = ref(false);
const mentionError = ref('');
const mentionTarget = ref<{ parentId: string; replyToId: string | null; name: string } | null>(null);
let mentionTimer: ReturnType<typeof setTimeout> | null = null;
let mentionAbort: AbortController | null = null;

async function searchMention(): Promise<void> {
    mentionAbort?.abort();
    const abort = new AbortController();
    mentionAbort = abort;
    mentionLoading.value = true;
    mentionError.value = '';
    try {
        const found = await props.actions.commenters(mentionQuery.value.trim(), abort.signal);
        if (mentionAbort !== abort) return;
        mentionItems.value = found.items;
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            mentionOpen.value = false;
            prompt('comment');
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
function showError(e: unknown, action: 'comment' | 'reply'): void {
    if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
        prompt(action);
        return;
    }
    if (handleStale(e)) return;
    if (e instanceof ApiError && e.fields && Object.keys(e.fields).length) {
        fieldErrors.value = { ...e.fields };
        // 답글 대상 오류(@ 회원 지정한 댓글이 그새 지워짐 등)는 폼에 칸이 없다 — 아래 한 줄로
        if (e.fields.parentId) formError.value = e.fields.parentId;
    } else formError.value = UtilBoardMessages.of(t, e);
    // 토론이 그새 닫혔거나 다른 입장이 이미 정해졌다 — 목록을 다시 받아 지금 상태(열림·내 입장)로 맞춘다(쓰던 글은 그대로)
    if (e instanceof ApiError && DEBATE_CODES.has(e.code)) {
        pickedStance.value = null;
        emit('retry');
    }
}

function closeForms(): void {
    replyTarget.value = null;
    editingId.value = null;
    active.value = '';
}

function openReply(thread: CommentView, target: CommentView): void {
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
    // 새 댓글 상자(@ 회원 지정으로 답글이 되어도 이 상자)인가 · 답글 상자인가
    const fromNewBox = !target || target === mentionTarget.value;
    const key = fromNewBox ? 'new' : 'reply';
    clearErrors(key);
    busy.value = key;
    // 입장은 새 댓글 상자에서, 입장을 정하지 않은 회원이 고른 것만 보낸다(정한 회원은 서버가 붙인다)
    const stance = fromNewBox && debateOpen.value && !props.board.viewer.guest && !debate.value?.myStance ? pickedStance.value : null;
    try {
        await props.actions.create(value.content, value.contentFormat, { parentId: target?.parentId ?? null, replyToId: target?.replyToId ?? null }, value.guest, stance);
        formSeq.value += 1;
        closeForms();
        if (fromNewBox) {
            pickedStance.value = null;
            mentionTarget.value = null;
        }
        toast.show(t('comment.posted'), { tone: 'success' });
    } catch (e) {
        showError(e, fromNewBox ? 'comment' : 'reply');
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

/* ── 추천·공개 전 댓글 ── */
async function like(comment: CommentView): Promise<void> {
    if (props.board.viewer.guest) return prompt('like');
    if (comment.liked) return toast.show(t('comment.alreadyLiked'));
    try {
        await props.actions.like(comment.id);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('like');
        else if (!handleStale(e)) toast.error(UtilBoardMessages.of(t, e));
    }
}

function viewDelayed(comment: CommentView): void {
    prompt('view', `${route.path}#comment-${comment.id}`);
}
</script>
