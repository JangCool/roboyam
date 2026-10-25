<template>
    <!--
        관리자 화면의 글 한 칸(서버 `AdminPostRef`) — 제목(말머리) · 게시판 · 글쓴이 · 글 상태(공개가 아니면 딱지) · 글 번호 복사. 인기글 관리·하이라이트(목록·후보·글 찾기)가 같이 쓴다.
        제목 옆에 붙일 딱지(채운 줄·볼 수 없는 글 …)는 기본 슬롯으로 받는다. 관리자 화면은 공개 사이트 주소를 모르므로 글 링크 대신 글 번호를 복사한다.
    -->
    <div class="flex min-w-0 flex-col gap-0.5">
        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
            <span v-if="post.prefixName" class="shrink-0 text-xs text-muted-color">[{{ post.prefixName }}]</span>
            <span class="min-w-0 break-words font-medium [overflow-wrap:anywhere]">{{ post.title }}</span>
            <Tag v-if="post.state !== 'PUBLIC'" :value="t(`content.postState.${post.state}`)" :severity="post.state === 'DELETED' ? 'danger' : 'warn'" class="text-[11px]!" />
            <slot />
        </div>
        <div class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-0.5 text-xs text-muted-color">
            <span>{{ localizedName(post.boardName, post.boardNameEn) }}</span>
            <span aria-hidden="true">·</span>
            <span class="min-w-0 truncate">{{ post.authorKind === 'GUEST' ? t('content.guestAuthor', { name: post.authorName }) : post.authorName }}</span>
            <button
                v-if="!compact"
                v-tooltip.top="t('content.copyPostId')"
                type="button"
                class="inline-flex items-center gap-1 rounded px-1 tabular-nums hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                :aria-label="t('content.copyPostIdLabel', { id: post.id })"
                @click="copyId"
            >
                <i class="pi pi-copy text-[10px]" aria-hidden="true" />#{{ post.id }}
            </button>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

interface Props {
    post: AdminPostRef;
    /** 글 번호 복사를 감춘다(좁은 자리) */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
const toast = useAppToast();
const { localizedName } = useLocalizedName();

async function copyId(): Promise<void> {
    try {
        await navigator.clipboard.writeText(props.post.id);
        toast.success(t('content.postIdCopied'));
    } catch {
        toast.error(t('content.copyFailed'));
    }
}
</script>
