<template>
    <!-- 댓글 하나 — 작성자·시각·본문·[추천][답글][⋯ 고치기·지우기·신고]. 지운 댓글·신고로 숨긴 댓글은 자리만, 비회원에게 늦게 보이는 댓글은 로그인 안내 단추 -->
    <article :id="`comment-${comment.id}`" class="scroll-mt-20 py-3" :class="reply ? 'pl-6' : ''">
        <p v-if="comment.state === 'DELETED'" class="text-sm text-muted-color">
            <i v-if="reply" class="pi pi-reply mr-1 rotate-180 text-xs" aria-hidden="true" />{{ t('comment.deleted') }}
        </p>
        <p v-else-if="comment.state === 'HIDDEN'" class="flex items-center gap-1.5 text-sm text-muted-color">
            <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t('comment.hidden') }}
        </p>
        <template v-else>
            <header class="flex flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted-color">
                <i v-if="reply" class="pi pi-reply rotate-180 text-xs" aria-hidden="true" />
                <span v-if="best" class="inline-flex items-center justify-center rounded bg-primary px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-primary-contrast">
                    <span class="relative top-[-0.09em]">{{ t('comment.bestBadge') }}</span>
                </span>
                <PostAuthorLabel :author="comment.author" show-ip class="text-color" />
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
                <p v-else class="mt-1 whitespace-pre-line break-words text-sm leading-relaxed">
                    <span v-if="comment.replyTo" class="mr-1 font-semibold text-primary">@{{ comment.replyTo.name }}</span>{{ comment.content }}
                </p>

                <div v-if="comment.state === 'VISIBLE'" class="mt-1.5 flex flex-wrap items-center gap-1">
                    <Button
                        type="button"
                        :icon="comment.liked ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'"
                        :label="String(comment.likeCount)"
                        :aria-label="t('comment.likeLabel', { n: comment.likeCount })"
                        :aria-pressed="comment.liked"
                        size="small"
                        variant="text"
                        :severity="comment.liked ? undefined : 'secondary'"
                        :disabled="comment.mine"
                        @click="$emit('like')"
                    />
                    <Button v-if="canReply" type="button" :label="t('comment.reply')" size="small" variant="text" severity="secondary" @click="$emit('reply')" />
                    <Button v-if="manageable" type="button" :label="t('common.edit')" size="small" variant="text" severity="secondary" @click="$emit('edit')" />
                    <Button v-if="manageable" type="button" :label="t('common.delete')" size="small" variant="text" severity="secondary" @click="$emit('delete')" />
                    <Button v-if="reportable" type="button" :label="t('post.report')" size="small" variant="text" severity="secondary" @click="$emit('report')" />
                </div>
            </template>
        </template>
        <slot name="after" />
    </article>
</template>

<script setup lang="ts">
import type { CommentView } from '~/composables/board/api/useCommentApi.types';
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
}

const props = withDefaults(defineProps<Props>(), {
    reply: false,
    best: false,
    canReply: false,
    reportEnabled: false,
    editing: false,
});

defineEmits<{ like: []; reply: []; edit: []; delete: []; report: []; 'view-delayed': [] }>();

const { t } = useI18n();
const time = useDisplayTime();

/** 내 댓글이거나 비회원 댓글(비밀번호로 확인) — 고치기·지우기 */
const manageable = computed(() => !props.best && (props.comment.mine || props.comment.author.kind === 'GUEST'));
const reportable = computed(() => props.reportEnabled && !props.comment.mine);
</script>
