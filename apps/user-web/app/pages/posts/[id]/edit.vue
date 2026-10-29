<template>
    <!--
        글 고치기 `/posts/{id}/edit` — 글쓰기와 같은 폼(`PostEditorForm`), 게시판은 바꿀 수 없다.
        내 회원 글은 바로 고치고, 비회원 글은 먼저 비밀번호를 확인한 뒤(`verify-password`) 같은 비밀번호를 저장 요청에 싣는다 — 비밀번호는 이 화면 메모리에만 있다.
        community 대비: 리비전·임시저장을 뺐다. 댓글 많은 글 보호로 수정이 막힌 글은 폼 대신 까닭을 보인다.
        본문은 **지금** 게시판 글 에디터 설정을 따른다 — 글의 형식(`contentFormat`)과 다르면 바꿔서 연다(평문 → 에디터 HTML, 에디터 HTML → 평문은 서식·이미지가 빠진다 — 가정).
        에디터는 불러온 HTML 을 정규화하므로 처음 만들어졌을 때의 HTML(`editorReady`)을 「고쳤는가」의 기준으로 삼는다.
        가정: 남의 회원 글은 `403`(화면은 들어온 순간 막는다), 보호 중 수정은 서버가 `409 POST_PROTECTED` 로 거절한다.
        찬반 토론(post-debate-contract): 내 회원 글이고 서버가 「바꿀 수 있다」(`debate.canManage`)고 하면 「찬반 토론」 칸을 보인다. 운영자·매니저가 연 토론(`openedBy OPERATOR`)은 읽기 전용 안내,
          투표가 있어 닫을 수 없는 토론(`canClose false`)은 열기 칸을 끄지 못한다. 글 수정 본문에는 토론 칸이 없다 — 글을 저장한 뒤 토론 칸이 바뀌었을 때만 `PUT /api/posts/{id}/debate`.
        투표(post-poll-contract): 내 회원 글에서 — 투표가 있으면 그 투표, 없으면 게시판 `pollEnabled` 일 때 「투표」 칸(찬반 토론 칸 위). 표가 들어와 `poll.editable false` 면 읽기 전용 요약 + 「표가 들어와 투표를 고칠 수 없어요」.
          첫 표 전인데 게시판 `pollEnabled` 가 꺼졌으면 「빼기만」 — 끄기 토글 + 읽기 전용 요약 + 「이 게시판은 이제 투표를 받지 않아요…」.
          글 수정 본문(`PUT /api/posts/{id}`)의 `poll`: 칸을 바꿨을 때만 보낸다(켬 → 만들기·고치기, 끔 → null 로 빼기). 바꾸지 않았거나 잠긴 투표면 칸을 아예 빼서 그대로 둔다.
          `409 POLL_LOCKED`(그새 첫 표가 들어옴)는 투표 칸 안 서버 문구 + 투표를 다시 읽어 잠근다.
        소모임 글(2단계): 글의 `group` 이 있으면 게시판 정보를 `GET /api/groups/{id}/board`(로보얌 추가 후보)로 받는다. 공개 범위는 여기서 바꾸지 않는다(글 상세에서만).
    -->
    <div class="content-card mx-auto max-w-3xl">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('post.editTitle') }}</span>
        </div>
        <h1 class="mb-4 text-lg font-bold max-md:hidden">{{ t('post.editTitle') }}</h1>

        <div v-if="pageState === 'loading'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-3">
            <div v-for="n in 4" :key="n" class="h-9 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="pageState === 'notfound'" code="404" :title="t('post.notFoundTitle')" :description="pageError" />
        <ErrorState v-else-if="pageState === 'error' || !post || !board" :title="t('post.loadFailed')" :description="pageError" retry @retry="load" />

        <!-- 남의 회원 글 -->
        <ErrorState v-else-if="!post.mine && post.author.kind === 'MEMBER'" :title="t('post.notMineTitle')" :description="t('post.notMineText')">
            <Button as="router-link" :to="`/posts/${post.id}`" :label="t('post.backToPost')" severity="secondary" variant="outlined" />
        </ErrorState>

        <!-- 댓글 많은 글 보호로 수정 불가 -->
        <EmptyState v-else-if="post.protection.editBlocked" :title="t('post.protectEditTitle')" :description="t('post.protectEditToast', { n: post.protection.threshold })" icon="pi pi-shield">
            <Button as="router-link" :to="`/posts/${post.id}`" :label="t('post.backToPost')" severity="secondary" variant="outlined" />
        </EmptyState>

        <!-- 비회원 글 — 비밀번호부터 -->
        <form v-else-if="post.author.kind === 'GUEST' && !verified" class="mx-auto flex max-w-sm flex-col gap-3 rounded-lg border border-surface p-5" data-testid="edit-password-gate" @submit.prevent="verify">
            <p class="font-semibold">{{ t('post.passwordGateTitle') }}</p>
            <p class="text-sm text-muted-color">{{ t('post.guestPasswordDescription') }}</p>
            <PasswordField v-model="password" :label="t('post.guestPassword')" autocomplete="off" name="guest-password-edit" :error="verifyError" />
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="cancel" />
                <Button type="submit" :label="t('common.confirm')" :loading="verifying" :disabled="password.length === 0" />
            </div>
        </form>

        <PostEditorForm
            v-else
            :key="post.id"
            v-model="form"
            :board="board"
            :guest-rules="post.author.kind === 'GUEST'"
            :field-errors="fieldErrors"
            :form-error="formError"
            :submitting="submitting"
            :submit-label="t('post.submitUpdate')"
            :upload="uploadImage"
            @editor-ready="onEditorReady"
            @submit="submit"
            @cancel="cancel"
        >
            <template #extra>
                <PollSettingsFields v-if="pollMode !== 'hidden'" v-model="poll" :locked="pollMode === 'locked'" :remove-only="pollMode === 'removeOnly'" :disabled="submitting" :errors="pollErrors" />
                <DebateSettingsFields
                    v-if="debateMode !== 'hidden'"
                    v-model="debate"
                    :readonly="debateMode === 'readonly'"
                    :close-locked="debateCloseLocked"
                    :disabled="submitting"
                />
            </template>
        </PostEditorForm>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostDetail } from '~/composables/board/api/usePostApi.types';
import { DEFAULT_DEBATE_SETTINGS, type DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';
import { EMPTY_POST_FORM, type PostFormValues } from '~/components/board/PostEditorForm.types';
import type { PollFormValues } from '~/components/board/PollSettingsFields.types';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilDebate from '~/utils/util-debate';
import UtilPoll from '~/utils/util-poll';
import UtilPollMessages from '~/utils/util-poll-messages';
import UtilPost from '~/utils/util-post';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const postApi = usePostApi();
const boardApi = useBoardApi();
const groupApi = useGroupApi();
const debateApi = useDebateApi();
const pollApi = usePollApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const current = useCurrentBoard();
const imageApi = useImageApi();
/** 에디터 이미지 올리기 — 서버 호출은 API 컴포저블 한 겹 */
const uploadImage: RichImageUploader = (file, options) => imageApi.upload(file, options);

const id = computed(() => String(route.params.id ?? ''));

const post = ref<PostDetail | null>(null);
const board = ref<BoardDetail | null>(null);
const pageState = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const pageError = ref('');

const form = ref<PostFormValues>({ ...EMPTY_POST_FORM });
/** 처음 값 — 바뀐 것이 있는지(나가기 확인) */
const initial = ref<PostFormValues>({ ...EMPTY_POST_FORM });

async function load(): Promise<void> {
    pageState.value = 'loading';
    try {
        const loaded = await postApi.post(id.value);
        // 소모임 글의 게시판은 공개 게시판 API 에 없다 — 소모임 게시판 정보(`GET /api/groups/{id}/board`)로 받는다(공개 범위는 글 상세에서만 바꾼다)
        // 소모임 글은 19금이 될 수 없다(서버 `400 fields.adult`) — 게시판이 19금을 받아도 칸을 보이지 않는다
        board.value = loaded.group ? { ...(await groupApi.board(loaded.group.id)), allowAdult: false } : await boardApi.board(loaded.board.slug);
        post.value = loaded;
        if (!loaded.group) current.set(loaded.board.slug);
        // 지금 게시판 에디터 설정의 형식으로 연다 — 글의 형식과 다르면 바꾼다
        const format = UtilBoard.postFormat(board.value);
        const content = UtilPost.convertContent(loaded.content, loaded.contentFormat ?? 'TEXT', format);
        const values: PostFormValues = { ...EMPTY_POST_FORM, prefixId: loaded.prefix?.id ?? null, title: loaded.title, content, contentFormat: format, adult: loaded.adult };
        form.value = { ...values };
        initial.value = { ...values };
        debateInitial.value = UtilDebate.settingsOf(loaded.debate);
        debate.value = { ...debateInitial.value };
        pollInitial.value = UtilPoll.formOf(loaded.poll);
        poll.value = UtilPoll.formOf(loaded.poll);
        pageState.value = 'ready';
    } catch (e) {
        pageError.value = UtilBoardMessages.of(t, e);
        // 멤버에게만 보이는 소모임 글을 멤버가 아닌 사람이 열었다(`403 GROUP_POST_PRIVATE`)도 열 수 없는 글로 본다
        pageState.value = e instanceof ApiError && (e.code === 'POST_NOT_FOUND' || e.code === 'POST_HIDDEN' || e.code === 'GROUP_POST_PRIVATE' || e.code === 'GROUP_NOT_FOUND') ? 'notfound' : 'error';
    }
}
void load();
onBeforeUnmount(() => {
    if (post.value && !post.value.group) current.clear(post.value.board.slug);
});

/* ── 찬반 토론 칸 ── */
const debate = ref<DebateSettingsValues>({ ...DEFAULT_DEBATE_SETTINGS });
const debateInitial = ref<DebateSettingsValues>({ ...DEFAULT_DEBATE_SETTINGS });
/**
 * 칸의 모습 — 내 회원 글에서만. 서버가 바꿀 수 있다고 하면(`canManage` — 게시판 `debateEnabled` + 토론이 없거나 내가 연 토론, 또는 내가 그 게시판 매니저) 고치기,
 * 운영자·매니저가 연 열린 토론이면 읽기 전용 안내, 그 밖(게시판이 글쓴이 토론을 받지 않음 등)은 감춘다
 */
const debateMode = computed<'hidden' | 'edit' | 'readonly'>(() => {
    const p = post.value;
    if (!p || !p.mine || p.author.kind !== 'MEMBER' || !p.debate) return 'hidden';
    if (p.debate.canManage) return 'edit';
    return p.debate.enabled && p.debate.openedBy === 'OPERATOR' ? 'readonly' : 'hidden';
});
/** 투표가 있어 닫을 수 없다 — 열린 토론의 열기 칸을 막는다 */
const debateCloseLocked = computed(() => Boolean(post.value?.debate?.enabled && !post.value.debate.canClose));
const debateChanged = computed(() => debateMode.value === 'edit' && !UtilDebate.sameSettings(debate.value, debateInitial.value));

/* ── 투표 칸 ── */
const poll = ref<PollFormValues>(UtilPoll.emptyForm());
const pollInitial = ref<PollFormValues>(UtilPoll.emptyForm());
/**
 * 칸의 모습 — 내 회원 글에서만. 투표가 있으면 첫 표 전(`editable`)은 고치기, 표가 있으면 잠금(읽기 전용 요약).
 * 첫 표 전인데 게시판 `pollEnabled` 가 꺼졌으면 빼기만(`removeOnly` — 서버는 같은 내용·빼기만 받는다).
 * 투표가 없으면 게시판이 받을 때(`pollEnabled`)만 새로 붙이기 칸, 그 밖은 감춘다
 */
const pollMode = computed<'hidden' | 'edit' | 'locked' | 'removeOnly'>(() => {
    const p = post.value;
    if (!p || !p.mine || p.author.kind !== 'MEMBER') return 'hidden';
    if (p.poll) {
        if (!p.poll.editable) return 'locked';
        return board.value && !board.value.pollEnabled ? 'removeOnly' : 'edit';
    }
    return board.value?.pollEnabled ? 'edit' : 'hidden';
});
/** 투표 칸을 바꿨다 — 바꿨을 때만 본문에 `poll` 을 싣는다(빼기만 모드는 끄기만 바뀔 수 있어 `null` 로 빼기) */
const pollChanged = computed(() => (pollMode.value === 'edit' || pollMode.value === 'removeOnly') && !UtilPoll.same(poll.value, pollInitial.value));
/** 한 번 [저장]을 누른 뒤부터 투표 칸의 화면 검사 문구를 보인다 — 고치지 않은 지난 마감 시각은 다시 검사하지 않는다 */
const pollTried = ref(false);
const pollProblems = computed(() => UtilPoll.problems(poll.value, Date.now(), pollInitial.value.closesAt));
const pollErrors = computed(() => UtilPollMessages.fieldErrors(t, fieldErrors.value, pollTried.value && pollChanged.value ? pollProblems.value : null));
// 투표 칸을 고치면 그 칸의 낡은 서버 오류를 지운다
watch(poll, () => {
    fieldErrors.value = UtilPoll.withoutPollFields(fieldErrors.value);
});

/** 그새 첫 표가 들어왔다(`409 POLL_LOCKED`) — 투표를 다시 읽어 잠근다(못 읽으면 그대로) */
async function reloadPoll(postId: string): Promise<void> {
    const p = post.value;
    if (!p) return;
    try {
        p.poll = await pollApi.poll(postId);
    } catch (e) {
        if (!(e instanceof ApiError && e.code === 'POLL_NOT_FOUND')) return;
        p.poll = null;
    }
    pollInitial.value = UtilPoll.formOf(p.poll);
    poll.value = UtilPoll.formOf(p.poll);
}

/* ── 비회원 글 비밀번호 ── */
const password = ref('');
const verified = ref(false);
const verifying = ref(false);
const verifyError = ref('');

async function verify(): Promise<void> {
    if (!post.value || verifying.value) return;
    verifying.value = true;
    verifyError.value = '';
    try {
        await postApi.verifyPassword(post.value.id, password.value);
        verified.value = true;
    } catch (e) {
        verifyError.value = UtilBoardMessages.of(t, e);
    } finally {
        verifying.value = false;
    }
}

/* ── 저장 ── */
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const submitting = ref(false);
let saved = false;

const dirty = computed(() => {
    const a = form.value;
    const b = initial.value;
    return a.title !== b.title || a.content !== b.content || a.prefixId !== b.prefixId || a.adult !== b.adult || debateChanged.value || pollChanged.value;
});

/** 에디터가 불러온 본문을 정규화했다 — 그 HTML 을 처음 값으로 삼는다(열기만 해도 「고친 글」로 보이지 않게) */
function onEditorReady(html: string): void {
    if (form.value.content === initial.value.content) form.value = { ...form.value, content: html };
    initial.value = { ...initial.value, content: html };
}

async function submit(): Promise<void> {
    const p = post.value;
    if (!p || submitting.value) return;
    pollTried.value = true;
    if (pollChanged.value && UtilPoll.hasProblem(UtilPoll.problems(poll.value, Date.now(), pollInitial.value.closesAt))) return;
    fieldErrors.value = {};
    formError.value = '';
    submitting.value = true;
    try {
        await postApi.update(p.id, {
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
            content: form.value.contentFormat === 'HTML' ? form.value.content : form.value.content.trim(),
            contentFormat: form.value.contentFormat,
            adult: Boolean(board.value?.allowAdult) && form.value.adult,
            password: p.author.kind === 'GUEST' ? password.value : null,
            // 바꾸지 않았거나 잠긴 투표는 칸을 아예 빼서 그대로 둔다(끄면 null 로 빼기)
            ...(pollChanged.value ? { poll: UtilPoll.inputOf(poll.value) } : {}),
        });
        saved = true;
        password.value = '';
        toast.success(t('post.updated'));
        // 글을 저장한 뒤 토론 칸이 바뀌었을 때만 — 실패해도 글은 저장됐으니 알리고 글로 간다(글 상세의 「토론 설정」에서 다시 할 수 있다)
        if (debateChanged.value) await saveDebate(p.id);
        await router.replace(`/posts/${p.id}`);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'IMAGE_UPLOAD_NOT_ALLOWED') fieldErrors.value = { content: e.message }; // 이미지를 막은 게시판 — 본문 칸 아래에 서버 문구
        else if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields && Object.keys(e.fields).length) fieldErrors.value = { ...e.fields };
        else if (e instanceof ApiError && e.code === 'POLL_LOCKED') {
            // 그새 첫 표가 들어왔다 — 투표 칸 안에 서버 문구, 투표를 다시 읽어 잠근다(다시 [저장]하면 투표는 그대로 두고 글만 고친다)
            await reloadPoll(p.id);
            await nextTick(); // 투표 칸이 바뀌어 낡은 오류를 지우는 감시가 먼저 돈 뒤에 싣는다
            fieldErrors.value = { poll: e.message };
        } else formError.value = UtilBoardMessages.of(t, e);
    } finally {
        submitting.value = false;
    }
}

async function saveDebate(postId: string): Promise<void> {
    try {
        await debateApi.settings(postId, UtilDebate.settingsBody(debate.value));
    } catch (e) {
        toast.error(t('comment.debateSaveAfterPostFailed', { message: UtilBoardMessages.of(t, e) }), 8000);
    }
}

function cancel(): void {
    void router.push(post.value ? `/posts/${post.value.id}` : '/');
}

onBeforeRouteLeave(async () => {
    if (saved || !dirty.value) return true;
    return await confirm({ title: t('post.leaveTitle'), message: t('post.leaveMessage'), confirmLabel: t('post.leaveConfirm'), tone: 'danger' });
});
function onBeforeUnload(event: BeforeUnloadEvent): void {
    if (saved || !dirty.value) return;
    event.preventDefault();
}
onMounted(() => window.addEventListener('beforeunload', onBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload));

useSeoMeta({ title: () => t('post.editTitle'), robots: 'noindex' });
</script>
