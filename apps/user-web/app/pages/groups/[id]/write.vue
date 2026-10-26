<template>
    <!--
        소모임 글쓰기 `/groups/{id}/write`(community `pages/groups/[id]/write.vue`, `docs/api-groups.md` 8절·「화면 (SPA) — 6-2」).
        게시판 고르기가 없다 — 「소모임: 이름」이 고정으로 보이고 말머리는 소모임 게시판의 것만 고른다. 폼·에디터·이미지 올리기는 일반 글쓰기(`/write`)와 같은 `PostEditorForm` 을 쓴다.
        에디터 수준·이미지·투표·말머리·글쓰기 안내문은 소모임 게시판 정보(`GET /api/groups/{id}/board` — `BoardDetail` 모양, 로보얌 추가)를 따른다. 19금 칸은 없다(소모임 글은 19금이 될 수 없다 — 서버 `400 fields.adult`).
        글은 **비공개**로 만들어진다(멤버에게만 보인다) — 전체 공개는 글을 쓴 뒤 글 상세에서 고른다. 임시저장·자동 저장·예약 발행·찬반 토론·비회원 글쓰기는 없다. 투표는 게시판이 받으면 붙일 수 있다.
        입구가 막힌 사람(비회원·멤버 아님·이메일 인증 전·정지 등 `canWritePost` false, 또는 게시판 정보의 `viewer.writeBlockedReason`)에게는 폼 대신 까닭을 보인다 — 서버가 최종 판단한다(`403 GROUP_NOT_MEMBER` 등).
        등록하면(`201 { id, pointsEarned }` — 일반 글쓰기처럼 포인트 토스트는 띄우지 않는다) 토스트를 띄우고 글 상세(`/posts/{id}`)로 간다(`replace` — 뒤로 가기가 글쓰기로 돌아오지 않게). 저장하지 않고 나가면 확인을 묻는다.
    -->
    <div class="mx-auto max-w-3xl">
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-times" variant="text" severity="secondary" :aria-label="t('common.cancel')" @click="cancel" />
            <span class="text-sm font-semibold">{{ t('group.writeTitle') }}</span>
        </div>
        <h1 class="mb-4 text-lg font-bold max-md:hidden">{{ t('group.writeTitle') }}</h1>

        <div v-if="view === 'loading'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-3" data-testid="group-write-loading">
            <div v-for="n in 4" :key="n" class="h-9 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="view === 'notfound'" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="group-write-notfound">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'inactive'" :title="t('group.inactiveTitle')" :description="t('group.inactiveText')" data-testid="group-write-inactive">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
        </ErrorState>

        <ErrorState v-else-if="view === 'error'" :title="t('group.loadError')" :description="errorMessage" retry data-testid="group-write-error" @retry="loadGroup">
            <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" severity="secondary" variant="outlined" />
        </ErrorState>

        <ErrorState v-else-if="view === 'board-error'" :title="t('group.writeBoardError')" :description="boardError" retry data-testid="group-write-board-error" @retry="loadBoard">
            <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" severity="secondary" variant="outlined" />
        </ErrorState>

        <!-- 입구가 막혔다 — 로그인·멤버 아님·이메일 인증 전·쓸 수 없는 상태 -->
        <StatusPanel v-else-if="gate" :icon="gate.icon" tone="warn" :title="gate.copy.title" :data-testid="`group-write-${view}`">
            {{ gate.copy.text }}
            <template #actions>
                <Button v-if="view === 'login'" type="button" :label="t('common.login')" fluid @click="prompt('write', route.fullPath)" />
                <Button v-if="view === 'unverified'" as="router-link" to="/verify-email" :label="t('login.verifyGuide')" fluid />
                <Button as="router-link" :to="groupPath" :label="t('group.backToGroup')" :severity="view === 'login' || view === 'unverified' ? 'secondary' : undefined" :variant="view === 'login' || view === 'unverified' ? 'outlined' : undefined" fluid />
            </template>
        </StatusPanel>

        <template v-else-if="view === 'form' && board && group">
            <Message severity="info" :closable="false" size="small" class="mb-4" data-testid="group-write-notice">{{ t('group.writeNotice') }}</Message>
            <PostEditorForm
                v-model="form"
                :board="board"
                :fixed-target="{ label: t('group.writeTarget'), value: group.name }"
                :field-errors="fieldErrors"
                :form-error="formError"
                :submitting="submitting"
                :submit-label="t('post.submitCreate')"
                :upload="uploadImage"
                data-testid="group-write-form"
                @submit="submit"
                @cancel="cancel"
            >
                <template #extra>
                    <PollSettingsFields v-if="pollShown" v-model="poll" :disabled="submitting" :errors="pollErrors" />
                </template>
            </PostEditorForm>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { RichImageUploader } from '#layers/rich-editor/components/RichEditor.types';
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { GroupDetail } from '~/composables/group/api/useGroupApi.types';
import type { PollFormValues } from '~/components/board/PollSettingsFields.types';
import { EMPTY_POST_FORM, type PostFormValues } from '~/components/board/PostEditorForm.types';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilPoll from '~/utils/util-poll';
import UtilPollMessages from '~/utils/util-poll-messages';
import UtilPost from '~/utils/util-post';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const api = useGroupApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { user, loaded: authLoaded } = useAuth();
const imageApi = useImageApi();
/** 에디터 이미지 올리기 — 서버 호출은 API 컴포저블 한 겹(폼·에디터는 이 함수만 받는다) */
const uploadImage: RichImageUploader = (file, options) => imageApi.upload(file, options);

const id = computed(() => String(route.params.id ?? ''));
const groupPath = computed(() => UtilGroup.detailPath(id.value));

/* ── 소모임 ── */
const detail = ref<GroupDetail | null>(null);
const detailState = ref<'loading' | 'ready' | 'notfound' | 'error'>('loading');
const errorMessage = ref('');
let groupSeq = 0;

async function loadGroup(): Promise<void> {
    const mine = ++groupSeq;
    detailState.value = 'loading';
    errorMessage.value = '';
    try {
        const result = await api.detail(id.value);
        if (mine !== groupSeq) return;
        detail.value = result;
        detailState.value = 'ready';
    } catch (e) {
        if (mine !== groupSeq) return;
        if (UtilGroup.isCode(e, 'GROUP_NOT_FOUND')) {
            detail.value = null;
            detailState.value = 'notfound';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        detailState.value = 'error';
    }
}

const kind = computed(() => (detail.value ? UtilGroup.kindOf(detail.value) : null));
const group = computed(() => (detailState.value === 'ready' && detail.value && kind.value === 'active' ? UtilGroup.active(detail.value) : null));

/* ── 소모임 게시판(에디터·말머리·투표 설정) — 쓸 수 있는 사람에게만 받는다 ── */
const board = ref<BoardDetail | null>(null);
const boardState = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
const boardError = ref('');
let boardSeq = 0;

async function loadBoard(): Promise<void> {
    const mine = ++boardSeq;
    boardState.value = 'loading';
    boardError.value = '';
    try {
        const loadedBoard = await api.board(id.value);
        if (mine !== boardSeq) return;
        // 소모임 글은 19금이 될 수 없다(서버 `400 fields.adult`) — 게시판이 19금을 받아도 칸을 보이지 않는다
        board.value = { ...loadedBoard, allowAdult: false };
        boardState.value = 'ready';
        // 본문 형식은 소모임 게시판의 글 에디터 설정을 따른다
        const format = UtilBoard.postFormat(loadedBoard);
        if (form.value.contentFormat !== format) form.value = { ...form.value, content: UtilPost.convertContent(form.value.content, form.value.contentFormat, format), contentFormat: format };
        if (form.value.adult) form.value = { ...form.value, adult: false };
    } catch (e) {
        if (mine !== boardSeq) return;
        boardError.value = UtilBoardMessages.of(t, e);
        boardState.value = 'error';
    }
}

/**
 * 지금 무엇을 보일지 — 소모임 없음/비활성/오류 → 비회원 → 멤버 아님 → 이메일 인증 전 → 쓸 수 없음 → 게시판 → (게시판 판정이 막음) → 폼.
 * 게시판 정보의 `viewer.writeBlockedReason`(서버 차례: `LOGIN_REQUIRED` → `GROUP_DISSOLVED` → `GROUP_NOT_MEMBER` → 쓰기 자격 `EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`·
 * `BUSINESS_ACCOUNT_RESTRICTED`·`BOARD_RESTRICTED`)이 있으면 상세가 보지 못한 까닭(사업자 계정·회원 역할 등)까지 폼 대신 보인다.
 */
type WriteView = 'loading' | 'notfound' | 'inactive' | 'error' | 'login' | 'not-member' | 'unverified' | 'blocked' | 'board-error' | 'board-blocked' | 'form';
const view = computed<WriteView>(() => {
    if (detailState.value === 'notfound') return 'notfound';
    if (detailState.value === 'error') return 'error';
    if (detailState.value === 'loading' || !authLoaded.value) return 'loading';
    if (kind.value === 'inactive') return 'inactive';
    const g = group.value;
    if (!g) return 'notfound';
    if (!user.value) return 'login';
    if (!UtilGroup.isMember(g.myState)) return 'not-member';
    if (!user.value.emailVerified) return 'unverified';
    if (!g.permissions.canWritePost) return 'blocked';
    if (boardState.value === 'error') return 'board-error';
    if (boardState.value !== 'ready' || !board.value) return 'loading';
    return board.value.viewer.writeBlockedReason ? 'board-blocked' : 'form';
});

const gate = computed(() => {
    const v = view.value;
    if (v === 'board-blocked' && board.value) {
        const reason = board.value.viewer.writeBlockedReason;
        return { icon: 'pi pi-lock', copy: { title: t('group.writeBlockedTitle'), text: UtilBoardMessages.writeBlocked(t, reason, board.value.writeMinGradeLabel) } };
    }
    if (v !== 'login' && v !== 'not-member' && v !== 'unverified' && v !== 'blocked') return null;
    const icon = v === 'login' ? 'pi pi-sign-in' : v === 'unverified' ? 'pi pi-envelope' : 'pi pi-lock';
    return { icon, copy: UtilGroupMessages.writeGate(t, v, group.value?.myState ?? null) };
});

watch(id, () => void loadGroup(), { immediate: true });
// 로그인한 사람이 바뀌면 멤버 여부가 달라진다
watch(
    () => user.value?.id ?? null,
    () => void loadGroup(),
);
// 쓸 수 있는 사람이 되면(그리고 아직 안 받았으면) 게시판 설정을 받는다
watch(
    () => Boolean(group.value && user.value && UtilGroup.isMember(group.value.myState) && group.value.permissions.canWritePost),
    (canWrite) => {
        if (canWrite && boardState.value === 'idle') void loadBoard();
    },
    { immediate: true },
);

/* ── 폼 ── */
const form = ref<PostFormValues>({ ...EMPTY_POST_FORM });
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const submitting = ref(false);
/** 등록을 마쳤다 — 나가기 확인을 묻지 않는다 */
let saved = false;

/** 투표 칸 — 소모임 게시판이 투표를 받을 때만 */
const poll = ref<PollFormValues>(UtilPoll.emptyForm());
const pollShown = computed(() => Boolean(board.value?.pollEnabled));
const pollTried = ref(false);
const pollProblems = computed(() => UtilPoll.problems(poll.value, Date.now()));
const pollErrors = computed(() => UtilPollMessages.fieldErrors(t, fieldErrors.value, pollTried.value ? pollProblems.value : null));
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
        const result = await api.writePost(id.value, {
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            // 에디터 HTML 은 그대로 보낸다(서버가 정화한다), 평문은 앞뒤 공백을 뗀다
            content: form.value.contentFormat === 'HTML' ? form.value.content : form.value.content.trim(),
            contentFormat: form.value.contentFormat,
            // 소모임 글은 19금이 될 수 없다(서버 `400 fields.adult`)
            adult: false,
            poll: pollShown.value ? UtilPoll.inputOf(poll.value) : null,
        });
        saved = true;
        toast.success(t('group.postedToast'));
        await router.replace(`/posts/${result.id}`);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) {
            prompt('write', route.fullPath);
        } else if (e instanceof ApiError && e.code === 'IMAGE_UPLOAD_NOT_ALLOWED') {
            fieldErrors.value = { content: e.message };
        } else if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields && Object.keys(e.fields).length) {
            fieldErrors.value = { ...e.fields };
        } else {
            formError.value = UtilBoardMessages.of(t, e);
            // 쓰는 사이 멤버가 아니게 됐거나 소모임이 닫혔다 — 상세를 다시 받아 입구 안내로 바꾼다(쓴 내용은 남는다)
            if (UtilGroup.isCode(e, 'GROUP_NOT_MEMBER') || UtilGroup.isCode(e, 'GROUP_NOT_FOUND') || UtilGroup.isCode(e, 'GROUP_INACTIVE')) void loadGroup();
        }
    } finally {
        submitting.value = false;
    }
}

function cancel(): void {
    void router.push(groupPath.value);
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
onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload);
    groupSeq++;
    boardSeq++;
});

useSeoMeta({ title: () => (group.value ? `${group.value.name} · ${t('group.writeTitle')}` : t('group.writeTitle')), robots: 'noindex' });
</script>
