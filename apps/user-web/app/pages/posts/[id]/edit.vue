<template>
    <!--
        글 고치기 `/posts/{id}/edit` — 글쓰기와 같은 폼(`PostEditorForm`), 게시판은 바꿀 수 없다.
        내 회원 글은 바로 고치고, 비회원 글은 먼저 비밀번호를 확인한 뒤(`verify-password`) 같은 비밀번호를 저장 요청에 싣는다 — 비밀번호는 이 화면 메모리에만 있다.
        community 대비: 리비전·임시저장·에디터를 뺐다. 댓글 많은 글 보호로 수정이 막힌 글은 폼 대신 까닭을 보인다.
        가정: 남의 회원 글은 `403`(화면은 들어온 순간 막는다), 보호 중 수정은 서버가 `409 POST_PROTECTED` 로 거절한다.
    -->
    <div class="mx-auto max-w-3xl">
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
            @submit="submit"
            @cancel="cancel"
        />
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostDetail } from '~/composables/board/api/usePostApi.types';
import { EMPTY_POST_FORM, type PostFormValues } from '~/components/board/PostEditorForm.types';
import UtilBoardMessages from '~/utils/util-board-messages';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const postApi = usePostApi();
const boardApi = useBoardApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const current = useCurrentBoard();

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
        board.value = await boardApi.board(loaded.board.slug);
        post.value = loaded;
        current.set(loaded.board.slug);
        const values: PostFormValues = { ...EMPTY_POST_FORM, prefixId: loaded.prefix?.id ?? null, title: loaded.title, content: loaded.content, adult: loaded.adult };
        form.value = { ...values };
        initial.value = { ...values };
        pageState.value = 'ready';
    } catch (e) {
        pageError.value = UtilBoardMessages.of(t, e);
        pageState.value = e instanceof ApiError && (e.code === 'POST_NOT_FOUND' || e.code === 'POST_HIDDEN') ? 'notfound' : 'error';
    }
}
void load();
onBeforeUnmount(() => {
    if (post.value) current.clear(post.value.board.slug);
});

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
    return a.title !== b.title || a.content !== b.content || a.prefixId !== b.prefixId || a.adult !== b.adult;
});

async function submit(): Promise<void> {
    const p = post.value;
    if (!p || submitting.value) return;
    fieldErrors.value = {};
    formError.value = '';
    submitting.value = true;
    try {
        await postApi.update(p.id, {
            prefixId: form.value.prefixId,
            title: form.value.title.trim(),
            content: form.value.content.trim(),
            adult: Boolean(board.value?.allowAdult) && form.value.adult,
            password: p.author.kind === 'GUEST' ? password.value : null,
        });
        saved = true;
        password.value = '';
        toast.success(t('post.updated'));
        await router.replace(`/posts/${p.id}`);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields && Object.keys(e.fields).length) fieldErrors.value = { ...e.fields };
        else formError.value = UtilBoardMessages.of(t, e);
    } finally {
        submitting.value = false;
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
