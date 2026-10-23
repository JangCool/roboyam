<template>
    <!--
        글 보기 `/posts/{id}` — 머리(말머리·제목·작성자·시각·조회) → 본문(19금이면 확인 전 가림) → [추천][링크 복사][신고] · [수정][삭제] → 투자 고지 → 이전·다음 글 → 댓글.
        community 대비: 투표·스크랩·차단·메모·지킴이 처리·리비전·소모임 글·핫딜/리뷰 글 모양을 뺐다. 본문은 에디터 HTML 이 아니라 평문(빈 줄 = 문단, 주소만 링크 — `v-html` 없음).
          비회원 글이 있어 작성자 옆에 「비회원」과 작성 IP 앞 두 자리가 보이고, 비회원 글의 수정·삭제는 비밀번호 확인 뒤에 한다.
        비회원 글 IP 는 서버가 앞 두 묶음만 남겨 준다(IPv4 `123.45.*.*` · IPv6 `2001:db8:*:*`, 회원 글은 없다). 19금 글은 성인 인증이 아직 없어 「성인입니다」 확인 단추로 이번 방문만 연다.
          추천은 한 번만(취소·비추천 없음). 신고는 회원만. 수정은 비회원 글이면 수정 화면에서 비밀번호를 다시 묻는다(비밀번호를 화면 사이로 넘기지 않는다).
          댓글 많은 글 보호(commentProtect*)는 서버가 `protection` 으로 알려 주고, 막힌 동작은 단추를 누르면 까닭을 알린다.
    -->
    <div>
        <!-- 모바일 안쪽 화면 머리 — 셸의 위·아래 막대가 숨겨진다(`shell: 'inner'`) -->
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-chevron-left" variant="text" severity="secondary" :aria-label="t('post.backToBoard')" @click="goBoard" />
            <span class="truncate text-sm font-semibold">{{ boardName }}</span>
        </div>

        <div v-if="pageState === 'loading'" role="status" :aria-label="t('post.loading')">
            <div class="h-6 w-3/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div class="mt-3 h-3 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            <div v-for="n in 5" :key="n" class="mt-4 h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-full' : 'w-5/6'" />
        </div>

        <ErrorState v-else-if="pageState === 'notfound'" code="404" :title="t('post.notFoundTitle')" :description="pageError">
            <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
        </ErrorState>
        <ErrorState v-else-if="pageState === 'hidden'" :title="t('post.hiddenTitle')" :description="pageError">
            <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
        </ErrorState>
        <ErrorState v-else-if="pageState === 'error' || !post || !board" :title="t('post.loadFailed')" :description="pageError" retry @retry="load" />

        <article v-else :aria-labelledby="titleId">
            <!-- 머리 -->
            <header class="border-b border-surface pb-3">
                <NuxtLink :to="`/boards/${board.slug}`" class="text-xs font-semibold text-primary hover:underline max-md:hidden">{{ boardName }}</NuxtLink>
                <h1 :id="titleId" class="mt-1 break-words text-lg font-bold leading-snug md:text-xl">
                    <PostPrefixTag v-if="post.prefix" :prefix="post.prefix" class="mr-1.5 align-[3px]" />{{ post.title }}
                </h1>
                <div class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-color">
                    <PostAuthorLabel :author="post.author" show-ip strong link class="text-sm" />
                    <span aria-hidden="true">·</span>
                    <time :datetime="post.createdAt" :title="time.full(post.createdAt)">{{ time.detail(post.createdAt) }}</time>
                    <span v-if="post.updatedAt" :title="time.full(post.updatedAt)">{{ t('post.edited') }}</span>
                    <span aria-hidden="true">·</span>
                    <span>{{ t('post.views', { n: post.viewCount.toLocaleString() }) }}</span>
                    <span aria-hidden="true">·</span>
                    <span>{{ t('post.likes', { n: post.likeCount.toLocaleString() }) }}</span>
                </div>
            </header>

            <!-- 예약 삭제된 글 · 댓글 많은 글 보호(수정·삭제할 수 있는 사람에게만) -->
            <Message v-if="post.deleteScheduledAt" severity="warn" :closable="false" size="small" class="mt-3" data-testid="post-delete-scheduled">
                {{ t('post.deleteScheduledNotice', { at: time.full(post.deleteScheduledAt) }) }}
            </Message>
            <Message v-if="manageable && protectionNotice" severity="info" :closable="false" size="small" class="mt-3" data-testid="post-protected">
                <b>{{ protectionNotice.title }}</b>
                <span v-for="item in protectionNotice.items" :key="item"> · {{ item }}</span>
            </Message>

            <!-- 본문 — 19금 글은 확인 전에 가린다(가정: 성인 인증이 생기면 서버 판정으로 바뀐다) -->
            <div v-if="post.adult && !adultConfirmed" class="my-6 flex flex-col items-center rounded-lg border border-surface px-5 py-8 text-center" data-testid="post-adult-gate">
                <span class="mb-3 grid size-12 place-items-center rounded-full bg-red-600 text-base font-bold text-white" aria-hidden="true">19</span>
                <p class="font-semibold">{{ t('post.adultGateTitle') }}</p>
                <p class="mt-1 text-sm text-muted-color">{{ t('post.adultGateText') }}</p>
                <div class="mt-4 flex gap-2">
                    <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="goBoard" />
                    <Button type="button" :label="t('post.adultConfirm')" severity="danger" @click="adultConfirmed = true" />
                </div>
            </div>
            <!--
                에디터로 쓴 글(`contentFormat === 'HTML'`) — 서버가 정화(sanitize)한 HTML 만 온다(공개 게시판 계약 「에디터 화면」: 서버가 허용 태그 밖을 지우고 이미지 주소를 채운다).
                새 `v-html` 은 이곳(글 본문)과 `CommentItem`(댓글 본문) 두 곳뿐이다. 다른 경로의 글자(제목·닉네임·안내문 등)는 절대 넣지 않는다.
                그린 뒤 유튜브(누르면 youtube-nocookie iframe)·이미지(실패 자리표시)는 `UtilRichBody.enhance` 가 DOM API 로만 손질한다. 평문 글은 아래처럼 글자로 그린다
            -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div v-else-if="post.contentFormat === 'HTML'" ref="htmlBody" class="rich-text py-5 text-[0.9375rem]" data-testid="post-body" v-html="post.content" />
            <div v-else class="py-5 text-[0.9375rem] leading-relaxed" data-testid="post-body">
                <p v-for="(lines, i) in paragraphs" :key="i" class="mb-4 break-words last:mb-0">
                    <template v-for="(line, j) in lines" :key="j">
                        <br v-if="j > 0" />
                        <template v-for="(seg, k) in UtilPost.segments(line, siteOrigin)" :key="k">
                            <a
                                v-if="seg.kind === 'link'"
                                :href="seg.href"
                                :target="seg.external ? '_blank' : undefined"
                                :rel="UtilPost.linkRel(seg.external, post.externalLinkNofollow)"
                                class="break-all text-primary underline underline-offset-2"
                            >{{ seg.text }}</a>
                            <template v-else>{{ seg.text }}</template>
                        </template>
                    </template>
                </p>
            </div>

            <!-- 추천 · 링크 복사 · 신고 -->
            <div class="flex flex-wrap items-center justify-center gap-2 py-3">
                <Button
                    type="button"
                    :icon="post.liked ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'"
                    :label="`${t('post.like')} ${post.likeCount.toLocaleString()}`"
                    :variant="post.liked ? undefined : 'outlined'"
                    :aria-pressed="post.liked"
                    :disabled="post.mine"
                    :loading="liking"
                    rounded
                    data-testid="post-like"
                    @click="onLike"
                />
                <Button type="button" icon="pi pi-link" :label="t('post.copyLink')" severity="secondary" variant="outlined" rounded @click="copyLink" />
                <Button v-if="board.reportEnabled && !post.mine" type="button" icon="pi pi-flag" :label="t('post.report')" severity="secondary" variant="text" rounded @click="openReport('POST', post.id)" />
            </div>

            <!-- 수정·삭제 — 내 글은 바로, 비회원 글은 비밀번호 -->
            <div v-if="manageable" class="flex justify-end gap-1 border-t border-surface pt-2">
                <Button type="button" icon="pi pi-pencil" :label="t('common.edit')" size="small" severity="secondary" variant="text" data-testid="post-edit" @click="onEdit" />
                <Button type="button" icon="pi pi-trash" :label="t('common.delete')" size="small" severity="danger" variant="text" data-testid="post-delete" @click="onDelete" />
            </div>

            <BoardInvestmentNotice v-if="board.investmentNotice" variant="post" class="mt-3" />

            <!-- 이전·다음 글 · 게시판으로 -->
            <nav class="mt-4 divide-y divide-surface border-y border-surface text-sm" :aria-label="t('post.neighborNav')">
                <NuxtLink v-if="post.next" :to="`/posts/${post.next.id}`" class="flex gap-3 px-1 py-2.5 hover:bg-surface-100 dark:hover:bg-surface-800/60">
                    <span class="w-14 shrink-0 text-xs text-muted-color"><i class="pi pi-angle-up mr-1 text-[0.625rem]" aria-hidden="true" />{{ t('post.next') }}</span>
                    <span class="truncate">{{ post.next.title }}</span>
                </NuxtLink>
                <NuxtLink v-if="post.prev" :to="`/posts/${post.prev.id}`" class="flex gap-3 px-1 py-2.5 hover:bg-surface-100 dark:hover:bg-surface-800/60">
                    <span class="w-14 shrink-0 text-xs text-muted-color"><i class="pi pi-angle-down mr-1 text-[0.625rem]" aria-hidden="true" />{{ t('post.prev') }}</span>
                    <span class="truncate">{{ post.prev.title }}</span>
                </NuxtLink>
            </nav>
            <div class="mt-3 flex justify-center">
                <Button type="button" icon="pi pi-list" :label="t('post.backToBoard')" severity="secondary" variant="outlined" size="small" @click="goBoard" />
            </div>

            <CommentSection
                :board="board"
                :data="comments.data.value"
                :state="comments.state.value"
                :error-message="comments.errorMessage.value"
                :sort="comments.sort.value"
                :actions="commentActions"
                @sort="comments.setSort"
                @retry="comments.load"
                @report="(c) => openReport('COMMENT', c.id)"
            />
        </article>

        <BoardPasswordDialog
            v-model:visible="passwordOpen"
            :title="t('post.deleteTitle')"
            :description="t('post.guestPasswordDescription')"
            :confirm-label="t('common.delete')"
            danger
            :loading="deleting"
            :error="passwordError"
            @submit="deleteWithPassword"
        />
        <BoardReportDialog v-model:visible="reportOpen" :target="reportTarget?.type ?? 'POST'" :loading="reporting" :error="reportError" :field-error="reportFieldError" @submit="submitReport" />
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostDetail } from '~/composables/board/api/usePostApi.types';
import type { ReportReason, ReportTargetType } from '~/composables/report/api/useReportApi.types';
import type { CommentActions } from '~/components/board/CommentSection.types';
import UtilRichBody from '#layers/rich-editor/utils/util-rich-body';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const postApi = usePostApi();
const boardApi = useBoardApi();
const reportApi = useReportApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { localizedName } = useLocalizedName();
const current = useCurrentBoard();
const time = useDisplayTime();
const titleId = useId();

const id = computed(() => String(route.params.id ?? ''));
const siteOrigin = typeof window === 'undefined' ? '' : window.location.origin;

/* ── 불러오기 ── */
type PageState = 'loading' | 'ready' | 'notfound' | 'hidden' | 'error';
const post = ref<PostDetail | null>(null);
const board = ref<BoardDetail | null>(null);
const pageState = ref<PageState>('loading');
const pageError = ref('');
/** 19금 확인 — 이 글을 보는 동안만(가정) */
const adultConfirmed = ref(false);

let request = 0;
async function load(): Promise<void> {
    const mine = ++request;
    pageState.value = 'loading';
    adultConfirmed.value = false;
    try {
        const loaded = await postApi.post(id.value);
        if (mine !== request) return;
        const loadedBoard = board.value?.slug === loaded.board.slug ? board.value : await boardApi.board(loaded.board.slug);
        if (mine !== request) return;
        post.value = loaded;
        board.value = loadedBoard;
        current.set(loaded.board.slug);
        pageState.value = 'ready';
        void comments.load();
    } catch (e) {
        if (mine !== request) return;
        pageError.value = UtilBoardMessages.of(t, e);
        const code = e instanceof ApiError ? e.code : '';
        pageState.value = code === 'POST_NOT_FOUND' || code === 'BOARD_NOT_FOUND' ? 'notfound' : code === 'POST_HIDDEN' ? 'hidden' : 'error';
    }
}

const comments = usePostComments(id);
const commentActions: CommentActions = {
    create: (content, contentFormat, target, guest) => comments.create(content, contentFormat, target, guest),
    update: (commentId, content, contentFormat, password) => comments.update(commentId, content, contentFormat, password),
    remove: (commentId, password) => comments.remove(commentId, password),
    like: (commentId) => comments.like(commentId),
};
// 댓글 수가 바뀌면(쓰기·지우기) 글 머리의 보호 상태가 달라질 수 있다 — 글은 다시 받지 않고 댓글 수만 맞춘다
watch(
    () => comments.data.value?.totalCount,
    (count) => {
        if (post.value && count !== undefined) post.value.commentCount = count;
    },
);

watch(id, () => void load(), { immediate: true });
onBeforeUnmount(() => {
    if (post.value) current.clear(post.value.board.slug);
});

const boardName = computed(() => (board.value ? localizedName(board.value.name, board.value.nameEn) : post.value ? localizedName(post.value.board.name, post.value.board.nameEn) : ''));
const paragraphs = computed(() => (post.value ? UtilPost.paragraphs(post.value.content) : []));

/** 에디터 글 본문 — 그린 직후(마운트·본문이 바뀔 때)에 유튜브·이미지를 손질한다(`flush: 'post'` 라 `v-html` 이 DOM 에 들어간 뒤에 돈다) */
const htmlBody = ref<HTMLElement | null>(null);
watch(
    [htmlBody, () => post.value?.content],
    () => {
        if (htmlBody.value) UtilRichBody.enhance(t, htmlBody.value);
    },
    { flush: 'post' },
);
/** 수정·삭제 단추 — 내 회원 글이거나 비회원 글(비밀번호로 확인) */
const manageable = computed(() => Boolean(post.value && (post.value.mine || post.value.author.kind === 'GUEST')));
const protectionNotice = computed(() => (post.value ? UtilPostMessages.protection(t, post.value.protection) : null));

function goBoard(): void {
    const slug = post.value?.board.slug ?? board.value?.slug;
    void navigateTo(slug ? `/boards/${slug}` : '/');
}

/* ── 추천·링크 복사 ── */
const liking = ref(false);
async function onLike(): Promise<void> {
    const p = post.value;
    if (!p || liking.value) return;
    if (board.value?.viewer.guest) return prompt('like');
    if (p.liked) return toast.show(t('post.alreadyLiked'));
    liking.value = true;
    try {
        const result = await postApi.like(p.id);
        p.likeCount = result.likeCount;
        p.liked = result.liked;
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('like');
        else toast.error(UtilBoardMessages.of(t, e));
    } finally {
        liking.value = false;
    }
}

async function copyLink(): Promise<void> {
    try {
        await navigator.clipboard.writeText(window.location.href);
        toast.success(t('post.linkCopied'));
    } catch {
        toast.error(t('post.linkCopyFailed'));
    }
}

/* ── 수정·삭제 ── */
function onEdit(): void {
    const p = post.value;
    if (!p) return;
    if (p.protection.editBlocked) return toast.show(t('post.protectEditToast', { n: p.protection.threshold }));
    void navigateTo(`/posts/${p.id}/edit`);
}

const passwordOpen = ref(false);
const passwordError = ref('');
const deleting = ref(false);

async function onDelete(): Promise<void> {
    const p = post.value;
    if (!p) return;
    if (p.protection.active && p.protection.deleteMode === 'BLOCK') return toast.show(t('post.protectDeleteToast', { n: p.protection.threshold }));
    if (p.author.kind === 'GUEST') {
        passwordError.value = '';
        passwordOpen.value = true;
        return;
    }
    const delayed = p.protection.active && p.protection.deleteMode === 'DELAY';
    const ok = await confirm({
        title: t('post.deleteTitle'),
        message: delayed ? t('post.deleteDelayMessage', { minutes: p.protection.delayMinutes }) : t('post.deleteMessage'),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    deleting.value = true;
    try {
        await finishDelete(await postApi.remove(p.id, { password: null }));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        deleting.value = false;
    }
}

async function deleteWithPassword(password: string): Promise<void> {
    const p = post.value;
    if (!p) return;
    deleting.value = true;
    passwordError.value = '';
    try {
        const result = await postApi.remove(p.id, { password });
        passwordOpen.value = false;
        await finishDelete(result);
    } catch (e) {
        // 틀린 비밀번호·보호 중 거절은 서버 문구 그대로
        passwordError.value = UtilBoardMessages.of(t, e);
    } finally {
        deleting.value = false;
    }
}

async function finishDelete(result: { scheduledAt: string | null }): Promise<void> {
    if (result.scheduledAt) {
        toast.show(t('post.deleteScheduledToast', { at: time.value.full(result.scheduledAt) }));
        await load();
        return;
    }
    toast.success(t('post.deleted'));
    goBoard();
}

/* ── 신고(글·댓글) ── */
const reportOpen = ref(false);
const reportTarget = ref<{ type: ReportTargetType; id: string } | null>(null);
const reporting = ref(false);
const reportError = ref('');
const reportFieldError = ref('');

/** 다시 시도해도 결과가 같은 신고 거절 — 대화상자 대신 토스트(그 밖의 `REPORT_TOO_FAST` 등은 대화상자 안에 보인다) */
const REPORT_FINAL: ReadonlySet<string> = new Set([
    'ALREADY_REPORTED',
    'CANNOT_REPORT_OWN',
    'REPORT_TARGET_NOT_FOUND',
    'REPORT_DISABLED',
    'REPORT_BLOCKED',
    'REPORT_DAILY_LIMIT',
    'ACCOUNT_RESTRICTED',
    'BOARD_RESTRICTED',
    'FORBIDDEN',
]);

function openReport(type: ReportTargetType, targetId: string): void {
    if (board.value?.viewer.guest) return prompt('report');
    reportTarget.value = { type, id: targetId };
    reportError.value = '';
    reportFieldError.value = '';
    reportOpen.value = true;
}

async function submitReport(value: { reason: ReportReason; detail: string | null }): Promise<void> {
    const target = reportTarget.value;
    if (!target) return;
    reporting.value = true;
    reportError.value = '';
    reportFieldError.value = '';
    try {
        await reportApi.report({ targetType: target.type, targetId: target.id, reason: value.reason, detail: value.detail });
        reportOpen.value = false;
        toast.success(t('post.reported'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            reportOpen.value = false;
            prompt('report');
        } else if (e instanceof ApiError && e.field('detail')) reportFieldError.value = e.field('detail') ?? '';
        else if (e instanceof ApiError && REPORT_FINAL.has(e.code)) {
            // 다시 눌러도 안 되는 거절 — 대화상자를 닫고 서버 문구를 알린다
            reportOpen.value = false;
            toast.error(e.message, 8000);
            if (e.code === 'REPORT_TARGET_NOT_FOUND' && reportTarget.value?.type === 'COMMENT') void comments.load();
        } else reportError.value = UtilBoardMessages.of(t, e);
    } finally {
        reporting.value = false;
    }
}

useSeoMeta({
    title: () => post.value?.title ?? t('post.pageTitle'),
    robots: () => (pageState.value === 'notfound' || pageState.value === 'hidden' || post.value?.adult ? 'noindex' : undefined),
});
</script>
