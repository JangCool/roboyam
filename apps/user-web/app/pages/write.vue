<template>
    <!--
        글쓰기 `/write?board=slug` — 게시판 고르기(쓸 수 있는 게시판만, `?board` 로 미리 선택) → 말머리·제목·본문·이미지 자리·19금 → 비회원이면 이름·비밀번호·자동입력 방지 → 등록 후 글 보기로.
        community 대비: 임시저장·글 유형(사진·영상·AI)·투표·핫딜/리뷰 칸·소모임 글·예약 발행을 뺐다. 본문은 게시판 글 에디터 설정(평문 Textarea / 간단·고급 서식 에디터)을 따르고,
          에디터 설정이 다른 게시판으로 바꾸면 쓰던 본문을 그 형식으로 바꿔 들고 간다(평문 ↔ HTML — HTML → 평문은 서식·이미지가 빠진다).
          비회원 글쓰기(`writeMin = GUEST` 게시판)를 더했다.
        찬반 토론(post-debate-contract): 게시판 `debateEnabled` 이고 회원이면 「찬반 토론」 칸(열기 + 중립도 받기 · 입장 바꾸기 허용, 기본 켬). 켜면 `POST /api/posts` 본문 `debate` 로 글과 함께 연다.
        투표(post-poll-contract): 게시판 `pollEnabled` 이고 회원이면 「투표」 칸(`PollSettingsFields` — 찬반 토론 칸 위). 켜면 화면 검사(빈 선택지 제외 2개 이상·중복·길이·마감 범위) 뒤 본문 `poll` 로 글과 함께 붙인다.
          서버 `fields."poll.*"`·`fields.poll` 은 투표 칸 안에 보인다.
        가정: 쓸 수 있는 게시판 목록은 서버가 판정해 준다(`GET /api/boards/writable` 후보). `?board` 가 쓸 수 없는 게시판이면 그 까닭을 보인다.
          금지어·작성 간격·하루 한도·같은 글 반복은 서버가 거절하고 그 문구를 폼 아래에 그대로 보인다. 저장하지 않고 나가면 확인을 묻는다.
    -->
    <div class="content-card mx-auto max-w-3xl">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('post.writeTitle') }}</span>
        </div>
        <h1 class="mb-4 text-lg font-bold max-md:hidden">{{ t('post.writeTitle') }}</h1>

        <div v-if="boardsState === 'loading'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-3">
            <div v-for="n in 4" :key="n" class="h-9 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="boardsState === 'error'" :title="t('post.boardsFailed')" :description="boardsError" retry @retry="loadBoards" />

        <!-- 쓸 수 있는 게시판이 하나도 없다 — 비회원이면 로그인 안내 -->
        <EmptyState v-else-if="options.length === 0 && !requestedBlocked" :title="t('post.noWritableTitle')" :description="loggedIn ? t('post.noWritableText') : t('post.noWritableGuest')" icon="pi pi-lock">
            <Button v-if="!loggedIn" type="button" :label="t('common.login')" @click="prompt('write')" />
        </EmptyState>

        <template v-else>
            <!-- 주소의 게시판에 쓸 수 없다 — 까닭(로그인 필요·등급 미달·잠금·운영진 전용) -->
            <Message v-if="requestedBlocked" severity="warn" :closable="false" class="mb-4" data-testid="write-blocked">
                {{ requestedBlocked }}
                <Button v-if="requestedReason === 'LOGIN_REQUIRED'" type="button" :label="t('common.login')" size="small" class="ml-2" @click="prompt('write')" />
            </Message>

            <div v-if="boardState === 'loading'" role="status" :aria-label="t('common.loading')" class="h-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <ErrorState v-else-if="boardState === 'error'" :title="t('post.boardFailed')" :description="boardError" retry @retry="loadBoard" />
            <PostEditorForm
                v-else-if="board && board.viewer.canWrite"
                v-model="form"
                v-model:board-slug="selectedSlug"
                :board="board"
                :board-options="options"
                :guest-mode="board.viewer.guest"
                :guest-rules="board.viewer.guest"
                :field-errors="fieldErrors"
                :form-error="formError"
                :submitting="submitting"
                :submit-label="t('post.submitCreate')"
                :upload="uploadImage"
                @submit="submit"
                @cancel="cancel"
            >
                <!-- 찬반 토론 — 게시판이 글쓴이 토론을 받고(`debateEnabled`) 회원일 때만. 켜면 글과 함께 연다 -->
                <template #extra>
                    <PollSettingsFields v-if="pollShown" v-model="poll" :disabled="submitting" :errors="pollErrors" />
                    <DebateSettingsFields v-if="debateShown" v-model="debate" :disabled="submitting" :error="fieldErrors.debate ?? ''" />
                </template>
            </PostEditorForm>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { BoardDetail, BoardView, WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import { DEFAULT_DEBATE_SETTINGS, type DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';
import type { PollFormValues } from '~/components/board/PollSettingsFields.types';
import { EMPTY_POST_FORM, type PostBoardOption, type PostFormValues } from '~/components/board/PostEditorForm.types';
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
const boardApi = useBoardApi();
const postApi = usePostApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { localizedName } = useLocalizedName();
const { user } = useAuth();
const current = useCurrentBoard();
const imageApi = useImageApi();
/** 에디터 이미지 올리기 — 서버 호출은 API 컴포저블 한 겹(폼·에디터는 이 함수만 받는다) */
const uploadImage: RichImageUploader = (file, options) => imageApi.upload(file, options);

const requestedSlug = computed(() => (typeof route.query.board === 'string' ? route.query.board : ''));

/* ── 쓸 수 있는 게시판 ── */
const writable = ref<BoardView[]>([]);
const boardsState = ref<'loading' | 'ready' | 'error'>('loading');
const boardsError = ref('');
const options = computed<PostBoardOption[]>(() => writable.value.map((b) => ({ slug: b.slug, label: localizedName(b.name, b.nameEn) })));
const selectedSlug = ref('');

/** 주소의 게시판에 쓸 수 없을 때의 까닭 — 그 게시판 정보로 판정한다 */
const requestedReason = ref<WriteBlockedReason | null>(null);
const requestedGrade = ref<string | null>(null);
const requestedBlocked = computed(() => (requestedReason.value ? UtilBoardMessages.writeBlocked(t, requestedReason.value, requestedGrade.value) : ''));
const loggedIn = computed(() => Boolean(user.value) && board.value?.viewer.guest !== true);

async function loadBoards(): Promise<void> {
    boardsState.value = 'loading';
    requestedReason.value = null;
    try {
        writable.value = await boardApi.writable();
        boardsState.value = 'ready';
        const wanted = requestedSlug.value;
        if (wanted && writable.value.some((b) => b.slug === wanted)) {
            selectedSlug.value = wanted;
        } else {
            if (wanted) await explainBlocked(wanted);
            selectedSlug.value = writable.value[0]?.slug ?? '';
        }
    } catch (e) {
        boardsError.value = UtilBoardMessages.of(t, e);
        boardsState.value = 'error';
    }
}

async function explainBlocked(slug: string): Promise<void> {
    try {
        const detail = await boardApi.board(slug);
        requestedReason.value = detail.viewer.writeBlockedReason;
        requestedGrade.value = detail.writeMinGradeLabel;
    } catch {
        // 없는 게시판이면 안내 없이 다른 게시판을 고르게 한다
    }
}

/* ── 고른 게시판 ── */
const board = ref<BoardDetail | null>(null);
const boardState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const boardError = ref('');

let boardRequest = 0;
async function loadBoard(): Promise<void> {
    const slug = selectedSlug.value;
    if (!slug) {
        board.value = null;
        boardState.value = 'idle';
        return;
    }
    const mine = ++boardRequest;
    boardState.value = 'loading';
    try {
        const detail = await boardApi.board(slug);
        if (mine !== boardRequest) return;
        board.value = detail;
        boardState.value = 'ready';
        current.set(slug);
        // 다른 게시판의 말머리·19금 표시는 들고 가지 않는다
        if (form.value.prefixId && !detail.prefixes.some((p) => p.id === form.value.prefixId)) form.value = { ...form.value, prefixId: null };
        if (!detail.allowAdult && form.value.adult) form.value = { ...form.value, adult: false };
        // 본문 형식은 게시판 글 에디터 설정을 따른다 — 다르면 쓰던 본문을 바꿔 들고 간다
        const format = UtilBoard.postFormat(detail);
        if (form.value.contentFormat !== format) form.value = { ...form.value, content: UtilPost.convertContent(form.value.content, form.value.contentFormat, format), contentFormat: format };
    } catch (e) {
        if (mine !== boardRequest) return;
        boardError.value = UtilBoardMessages.of(t, e);
        boardState.value = 'error';
    }
}

watch(selectedSlug, () => void loadBoard());
void loadBoards();
onBeforeUnmount(() => current.clear(selectedSlug.value));

/* ── 폼 ── */
const form = ref<PostFormValues>({ ...EMPTY_POST_FORM });
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const submitting = ref(false);
/** 등록을 마쳤다 — 나가기 확인을 묻지 않는다 */
let saved = false;

/** 찬반 토론 칸 — 게시판을 바꿔도 값은 들고 가고, 받지 않는 게시판이면 칸을 감추고 보내지 않는다 */
const debate = ref<DebateSettingsValues>({ ...DEFAULT_DEBATE_SETTINGS });
const debateShown = computed(() => Boolean(board.value?.debateEnabled) && board.value?.viewer.guest === false);

/** 투표 칸 — 게시판을 바꿔도 값은 들고 가고, 받지 않는 게시판(`pollEnabled` 꺼짐)·비회원이면 칸을 감추고 보내지 않는다 */
const poll = ref<PollFormValues>(UtilPoll.emptyForm());
const pollShown = computed(() => Boolean(board.value?.pollEnabled) && board.value?.viewer.guest === false);
/** 한 번 [등록]을 누른 뒤부터 투표 칸의 화면 검사 문구를 보인다 */
const pollTried = ref(false);
const pollProblems = computed(() => UtilPoll.problems(poll.value, Date.now()));
const pollErrors = computed(() => UtilPollMessages.fieldErrors(t, fieldErrors.value, pollTried.value ? pollProblems.value : null));
// 투표 칸을 고치면 그 칸의 낡은 서버 오류를 지운다
watch(poll, () => {
    fieldErrors.value = UtilPoll.withoutPollFields(fieldErrors.value);
});

const dirty = computed(() => form.value.title.trim() !== '' || UtilPost.hasContent(form.value.content, form.value.contentFormat) || (pollShown.value && poll.value.enabled));

async function submit(): Promise<void> {
    const b = board.value;
    if (!b || submitting.value) return;
    pollTried.value = true;
    if (pollShown.value && UtilPoll.hasProblem(UtilPoll.problems(poll.value, Date.now()))) return;
    fieldErrors.value = {};
    formError.value = '';
    submitting.value = true;
    try {
        const result = await postApi.create({
            boardSlug: b.slug,
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
            content: form.value.contentFormat === 'HTML' ? form.value.content : form.value.content.trim(),
            contentFormat: form.value.contentFormat,
            adult: b.allowAdult && form.value.adult,
            guest: b.viewer.guest ? { name: form.value.guestName.trim(), password: form.value.guestPassword, captchaToken: form.value.captcha ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
            debate: debateShown.value ? UtilDebate.openBody(debate.value) : null,
            poll: pollShown.value ? UtilPoll.inputOf(poll.value) : null,
        });
        saved = true;
        form.value = { ...EMPTY_POST_FORM };
        debate.value = { ...DEFAULT_DEBATE_SETTINGS };
        poll.value = UtilPoll.emptyForm();
        toast.success(t('post.created'));
        await router.replace(`/posts/${result.id}`);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('write');
        else if (e instanceof ApiError && e.code === 'IMAGE_UPLOAD_NOT_ALLOWED') fieldErrors.value = { content: e.message }; // 이미지를 막은 게시판 — 본문 칸 아래에 서버 문구
        else if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields && Object.keys(e.fields).length) fieldErrors.value = { ...e.fields };
        else formError.value = UtilBoardMessages.of(t, e);
    } finally {
        submitting.value = false;
    }
}

function cancel(): void {
    const slug = board.value?.slug ?? requestedSlug.value;
    void router.push(slug ? `/boards/${slug}` : '/');
}

/* ── 저장하지 않고 나가기 ── */
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

useSeoMeta({ title: () => t('post.writeTitle'), robots: 'noindex' });
</script>
