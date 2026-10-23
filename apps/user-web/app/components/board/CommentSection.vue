<template>
    <!--
        글 보기의 댓글 구획 — 베스트 댓글(위에 따로) · 정렬(오래된순·최신순) · 원댓글 + 답글 1단계 · 새 댓글 입력.
        서버 호출은 페이지가 `usePostComments` 로 하고 여기에는 그 함수(`actions`)만 내려온다. 비회원 비밀번호 확인·지우기 확인·로그인 안내는 여기서 띄운다.
        신고는 페이지의 신고 대화상자를 쓴다(`report` 이벤트).
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

            <EmptyState v-if="data.threads.length === 0" :title="t('comment.emptyTitle')" :description="board.viewer.canComment ? t('comment.emptyText') : ''" icon="pi pi-comments" />

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
import type { ContentFormat, GuestCredentials } from '~/composables/board/api/usePostApi.types';
import type { CommentLoadState } from '~/composables/board/usePostComments';
import type { CommentActions } from '~/components/board/CommentSection.types';
import UtilBoardMessages from '~/utils/util-board-messages';

interface Props {
    board: BoardDetail;
    data: CommentListResponse | null;
    state: CommentLoadState;
    errorMessage?: string;
    sort: CommentSort;
    actions: CommentActions;
}

const props = withDefaults(defineProps<Props>(), {
    errorMessage: '',
});

const emit = defineEmits<{ sort: [value: CommentSort]; retry: []; report: [comment: CommentView] }>();

const SORTS: readonly CommentSort[] = ['OLDEST', 'LATEST'];

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
    if (e instanceof ApiError && e.fields && Object.keys(e.fields).length) fieldErrors.value = { ...e.fields };
    else formError.value = UtilBoardMessages.of(t, e);
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
    const key = target ? 'reply' : 'new';
    clearErrors(key);
    busy.value = key;
    try {
        await props.actions.create(value.content, value.contentFormat, { parentId: target?.parentId ?? null, replyToId: target?.replyToId ?? null }, value.guest);
        formSeq.value += 1;
        closeForms();
        toast.show(t('comment.posted'), { tone: 'success' });
    } catch (e) {
        showError(e, target ? 'reply' : 'comment');
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
