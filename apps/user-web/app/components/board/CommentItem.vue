<template>
    <!--
        댓글 하나 — (고정 표시)·(토론 입장 배지)·작성자·시각·본문·[추천][비추천][답글][고치기·지우기·신고]·(내 의견의 고정 요청).
        지운 댓글·숨긴 댓글은 자리만(운영자가 숨겼고 게시판이 밝히기로 했으면 `hiddenBy` STAFF — 「운영자가 숨긴 댓글이에요」, 소모임을 나간 멤버의 댓글은 `GROUP_LEFT`, 비공개 소모임의 개별 공개 글에서 공개 전에 달린 댓글은 `MEMBERS_ONLY` — 자물쇠), 비회원에게 늦게 보이는 댓글은 로그인 안내 단추.
        추천·비추천은 게시판 설정(`reactions` — 비추천 켬·두 이름, 비면 기본 「추천」·「비추천」)을 따른다. 하나를 누르면 다른 하나가 풀린다(서버 응답 그대로). 고른 쪽은 찬성·반대 색의 옅은 칩.
        고정 요청(토론 구획의 내 의견 원댓글만): [고정 요청] → 「고정 요청 보냄 · 확인 기다리는 중 [취소]」 / 거절됐으면 사유 + [다시 요청]
    -->
    <article :id="`comment-${comment.id}`" class="scroll-mt-20 py-3" :class="reply ? 'pl-6' : ''">
        <p v-if="comment.state === 'DELETED'" class="text-sm text-muted-color">
            <i v-if="reply" class="pi pi-reply mr-1 rotate-180 text-xs" aria-hidden="true" />{{ t('comment.deleted') }}
        </p>
        <p v-else-if="comment.state === 'HIDDEN'" class="flex items-center gap-1.5 text-sm text-muted-color">
            <i :class="[comment.hiddenBy === 'MEMBERS_ONLY' ? 'pi pi-lock' : 'pi pi-eye-slash', 'text-xs']" aria-hidden="true" />{{ t(HIDDEN_KEY[comment.hiddenBy ?? 'NONE']) }}
        </p>
        <!-- 차단한 회원·단어의 댓글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
        <FoldedRow v-else :reason="foldReason" kind="comment" class="text-sm" hide-class="mt-1.5">
            <header class="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted-color">
                <i v-if="reply" class="pi pi-reply rotate-180 text-xs" aria-hidden="true" />
                <span v-if="best" class="inline-flex items-center justify-center rounded bg-primary px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-primary-contrast">
                    <span class="relative top-[-0.09em]">{{ t('comment.bestBadge') }}</span>
                </span>
                <!-- 고정한 의견(토론 구획 맨 위) -->
                <span
                    v-if="comment.pinned && !reply && !best"
                    class="inline-flex items-center justify-center gap-1 rounded bg-surface-200 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-color dark:bg-surface-700"
                    data-testid="comment-pinned"
                >
                    <i class="pi pi-thumbtack text-[0.625rem]" aria-hidden="true" /><span class="relative top-[-0.09em]">{{ t('comment.pinned') }}</span>
                </span>
                <!-- 찬반 토론의 입장 배지(지금 입장 — 입장을 바꾸면 따라 바뀐다) -->
                <DebateStanceBadge v-if="comment.stance" :stance="comment.stance" />
                <PostAuthorLabel :author="comment.author" show-ip link class="text-color" />
                <span aria-hidden="true">·</span>
                <time :datetime="comment.createdAt" :title="time.full(comment.createdAt)">{{ time.ago(comment.createdAt) }}</time>
                <span v-if="comment.updatedAt">{{ t('comment.edited') }}</span>
            </header>

            <!-- 고치는 중이면 본문 자리에 편집 폼(부르는 쪽이 슬롯으로 넣는다) -->
            <slot v-if="editing" name="editor" />
            <template v-else>
                <!-- 비회원에게 아직 공개되지 않은 댓글 — 시간 숫자는 알리지 않는다(`util-comment-delay-messages.ts`). 누르면 로그인 안내 -->
                <button
                    v-if="comment.state === 'DELAYED'"
                    type="button"
                    class="mt-1.5 inline-flex items-center gap-1.5 rounded-md bg-surface-100 px-3 py-2 text-sm text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-800"
                    :aria-label="UtilCommentDelayMessages.placeholderLabel(t, comment.author.name)"
                    @click="$emit('view-delayed')"
                >
                    <i class="pi pi-lock text-xs" aria-hidden="true" />{{ t('prompt.delayPlaceholder') }}
                </button>
                <!--
                    에디터로 쓴 댓글(`contentFormat === 'HTML'`, 간단 에디터) — 서버가 정화한 HTML 만 온다(공개 게시판 계약 「에디터 화면」).
                    새 `v-html` 은 이곳(댓글 본문)과 글 보기 본문(`pages/posts/[id]/index.vue`)·약관 본문(`components/terms/TermsDocumentView.vue` — 서버 정화본) 세 곳뿐이다. 답한 상대 이름은 글자로 따로 그린다(HTML 에 섞지 않는다) — 왼쪽에 띄워 첫 문단과 한 줄에 놓는다
                -->
                <div v-else-if="comment.contentFormat === 'HTML'" class="mt-1 break-words text-sm">
                    <span v-if="comment.replyTo" class="float-left mr-1 font-semibold leading-[1.75] text-primary">@{{ comment.replyTo.name }}</span>
                    <!-- eslint-disable-next-line vue/no-v-html -->
                    <div class="rich-text" v-html="comment.content ?? ''" />
                </div>
                <p v-else class="mt-1 whitespace-pre-line break-words text-sm leading-relaxed">
                    <span v-if="comment.replyTo" class="mr-1 font-semibold text-primary">@{{ comment.replyTo.name }}</span>{{ comment.content }}
                </p>

                <div v-if="comment.state === 'VISIBLE'" class="mt-1.5 flex flex-wrap items-center gap-1">
                    <button
                        type="button"
                        :class="reactClass(comment.liked, 'stance-pro-soft')"
                        :aria-label="t('comment.reactionAria', { name: likeName, n: comment.likeCount })"
                        :aria-pressed="comment.liked"
                        :disabled="comment.mine"
                        data-testid="comment-like"
                        @click="$emit('like')"
                    >
                        <i :class="comment.liked ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'" class="text-[0.6875rem]" aria-hidden="true" />{{ likeName }}
                        <span class="tabular-nums">{{ comment.likeCount }}</span>
                    </button>
                    <button
                        v-if="dislikeShown"
                        type="button"
                        :class="reactClass(Boolean(comment.disliked), 'stance-con-soft')"
                        :aria-label="t('comment.reactionAria', { name: dislikeName, n: comment.dislikeCount ?? 0 })"
                        :aria-pressed="Boolean(comment.disliked)"
                        :disabled="comment.mine"
                        data-testid="comment-dislike"
                        @click="$emit('dislike')"
                    >
                        <i :class="comment.disliked ? 'pi pi-thumbs-down-fill' : 'pi pi-thumbs-down'" class="text-[0.6875rem]" aria-hidden="true" />{{ dislikeName }}
                        <span class="tabular-nums">{{ comment.dislikeCount ?? 0 }}</span>
                    </button>
                    <Button v-if="canReply" type="button" :label="t('comment.reply')" size="small" variant="text" severity="secondary" @click="$emit('reply')" />
                    <Button v-if="manageable" type="button" :label="t('common.edit')" size="small" variant="text" severity="secondary" @click="$emit('edit')" />
                    <Button v-if="manageable" type="button" :label="t('common.delete')" size="small" variant="text" severity="secondary" @click="$emit('delete')" />
                    <Button v-if="reportable" type="button" :label="t('post.report')" size="small" variant="text" severity="secondary" @click="$emit('report')" />
                    <!-- 내 의견의 고정 요청 -->
                    <template v-if="pinRequestShown">
                        <span v-if="comment.pinRequest?.status === 'PENDING'" class="inline-flex flex-wrap items-center gap-1 text-xs text-muted-color" data-testid="comment-pin-pending">
                            <i class="pi pi-thumbtack text-[0.625rem]" aria-hidden="true" />{{ t('comment.pinRequestPending') }}
                            <Button type="button" :label="t('comment.pinRequestCancel')" size="small" variant="text" :disabled="pinBusy" @click="$emit('pin-cancel')" />
                        </span>
                        <Button
                            v-else-if="pinRequests"
                            type="button"
                            icon="pi pi-thumbtack"
                            :label="comment.pinRequest?.status === 'REJECTED' ? t('comment.pinRequestAgain') : t('comment.pinRequest')"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            rounded
                            :loading="pinBusy"
                            data-testid="comment-pin-request"
                            @click="$emit('pin-request')"
                        />
                    </template>
                </div>
                <p v-if="comment.state === 'VISIBLE' && pinRequestShown && comment.pinRequest?.status === 'REJECTED'" class="m-0 mt-1 text-xs text-muted-color" data-testid="comment-pin-rejected">
                    {{ comment.pinRequest.rejectReason ? t('comment.pinRequestRejectedReason', { reason: comment.pinRequest.rejectReason }) : t('comment.pinRequestRejected') }}
                </p>
            </template>
        </FoldedRow>
        <slot name="after" />
    </article>
</template>

<script setup lang="ts">
import type { CommentReactions, CommentView } from '~/composables/board/api/useCommentApi.types';
import UtilCommentDelayMessages from '~/utils/util-comment-delay-messages';

interface Props {
    comment: CommentView;
    /** 답글 줄(들여쓰기) */
    reply?: boolean;
    /** 베스트 댓글 묶음 안의 줄 */
    best?: boolean;
    canReply?: boolean;
    reportEnabled?: boolean;
    editing?: boolean;
    /** 이 게시판의 추천·비추천 표시(댓글 목록의 `reactions`) — 없으면 추천만, 기본 이름 */
    reactions?: CommentReactions | null;
    /** 내 의견에 고정 요청을 보낼 수 있다(열린 토론 구획) — 대기 중인 요청의 상태·취소는 이것과 무관하게 보인다 */
    pinRequests?: boolean;
    /** 고정 요청을 보내거나 취소하는 중 */
    pinBusy?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    reply: false,
    best: false,
    canReply: false,
    reportEnabled: false,
    editing: false,
    reactions: null,
    pinRequests: false,
    pinBusy: false,
});

defineEmits<{ like: []; dislike: []; reply: []; edit: []; delete: []; report: []; 'view-delayed': []; 'pin-request': []; 'pin-cancel': [] }>();

const { t } = useI18n();
const time = useDisplayTime();

/** 숨긴 자리의 문구 — 운영자 숨김 · 소모임을 나간 멤버의 댓글 · 멤버 전용 댓글(소모임 5단계 — 비공개 소모임의 개별 공개 글에서 공개 전에 달린 댓글) · 신고 숨김 */
const HIDDEN_KEY: Record<NonNullable<CommentView['hiddenBy']> | 'NONE', string> = {
    STAFF: 'comment.hiddenByStaff',
    GROUP_LEFT: 'comment.hiddenGroupLeft',
    MEMBERS_ONLY: 'comment.hiddenMembersOnly',
    NONE: 'comment.hidden',
};

/** 내 댓글이거나 비회원 댓글(비밀번호로 확인) — 고치기·지우기 */
const manageable = computed(() => !props.best && (props.comment.mine || props.comment.author.kind === 'GUEST'));
const reportable = computed(() => props.reportEnabled && !props.comment.mine);

/** 추천·비추천 이름 — 게시판 설정이 비면 기본 이름 */
const likeName = computed(() => props.reactions?.likeLabel || t('comment.likeDefault'));
const dislikeName = computed(() => props.reactions?.dislikeLabel || t('comment.dislikeDefault'));
/** 비추천 단추 — 게시판이 받고(`dislikeEnabled`) 서버가 수를 내려 줄 때 */
const dislikeShown = computed(() => Boolean(props.reactions?.dislikeEnabled) && props.comment.dislikeCount != null);
/** 고정 요청 칸 — 내 의견 원댓글(베스트 묶음 밖)이고 고정되지 않았다 */
const pinRequestShown = computed(() => !props.reply && !props.best && props.comment.mine && Boolean(props.comment.stance) && !props.comment.pinned);

/** 추천·비추천 단추 — 고르면 찬성(추천)·반대(비추천) 색의 옅은 칩 */
function reactClass(on: boolean, tone: string): string {
    return [
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default disabled:opacity-60',
        on ? `${tone} border-transparent font-semibold` : 'border-surface text-muted-color hover:text-color',
    ].join(' ');
}

/** 접는 까닭 — 보이는 댓글만. 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다. 고치는 중이면 접지 않는다 */
const overrides = useBlockOverrides();
const foldReason = computed(() =>
    props.comment.state === 'VISIBLE' && !props.editing && !props.comment.mine ? overrides.blockedOf(props.comment.author.memberId, props.comment.blocked) : null,
);
</script>
