<template>
    <!--
        글쓰기 `/write?board=slug` — 게시판 고르기(쓸 수 있는 게시판만, `?board` 로 미리 선택) → 말머리·제목·본문·이미지 자리·19금 → 비회원이면 이름·비밀번호·자동입력 방지 → 등록 후 글 보기로.
        community 대비: 임시저장·글 유형(사진·영상·AI)·투표·핫딜/리뷰 칸·소모임 글·예약 발행·에디터를 뺐다(본문은 평문 Textarea — 에디터는 후속).
          비회원 글쓰기(`writeMin = GUEST` 게시판)를 더했다.
        가정: 쓸 수 있는 게시판 목록은 서버가 판정해 준다(`GET /api/boards/writable` 후보). `?board` 가 쓸 수 없는 게시판이면 그 까닭을 보인다.
          금지어·작성 간격·하루 한도·같은 글 반복은 서버가 거절하고 그 문구를 폼 아래에 그대로 보인다. 저장하지 않고 나가면 확인을 묻는다.
    -->
    <div class="mx-auto max-w-3xl">
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
                @submit="submit"
                @cancel="cancel"
            />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail, BoardView, WriteBlockedReason } from '~/composables/board/api/useBoardApi.types';
import { EMPTY_POST_FORM, type PostBoardOption, type PostFormValues } from '~/components/board/PostEditorForm.types';
import UtilBoardMessages from '~/utils/util-board-messages';
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

const dirty = computed(() => form.value.title.trim() !== '' || form.value.content.trim() !== '');

async function submit(): Promise<void> {
    const b = board.value;
    if (!b || submitting.value) return;
    fieldErrors.value = {};
    formError.value = '';
    submitting.value = true;
    try {
        const result = await postApi.create({
            boardSlug: b.slug,
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            content: form.value.content.trim(),
            adult: b.allowAdult && form.value.adult,
            guest: b.viewer.guest ? { name: form.value.guestName.trim(), password: form.value.guestPassword, captchaToken: form.value.captcha ? UtilPost.PLACEHOLDER_CAPTCHA_TOKEN : '' } : null,
        });
        saved = true;
        form.value = { ...EMPTY_POST_FORM };
        toast.success(t('post.created'));
        await router.replace(`/posts/${result.id}`);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('write');
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
