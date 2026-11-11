<template>
    <!-- 글 줄의 제목 부분 — [칩: (소모임 상세면 공개 라벨) · 말머리 · 처리 상태 · 19 — `chips` 일 때만] · 비밀글 자물쇠 · 제목 · 투표 칩 · 이미지 표시 · [댓글 수] · 새 글. 세 가지 목록 모양(LIST·CARD·GALLERY)·고정 공지·최신/인기 등 피드 줄이 같이 쓴다.
         처리 상태·비밀글은 줄에 그 칸이 있을 때만(운영 게시판 — 섞인 목록에서는 서버가 비밀글을 빼고, 상태가 꺼진 게시판은 칸이 없다) -->
    <span>
        <i v-if="row.pinned && pinnedMark" class="pi pi-thumbtack mr-1 text-xs text-muted-color" aria-hidden="true" />
        <span v-if="row.pinned && pinnedMark" class="sr-only">{{ t('board.pinned') }}</span>
        <!-- 칩(소모임 공개 라벨·말머리·처리 상태·19) — 「제목 앞」(`BEFORE`)일 때만 여기, 제목 위·닉네임 아래면 목록이 칩 줄(`PostRowFrame`)로 따로 그리고 `chips` 를 끈다 -->
        <PostRowChips v-if="chips" :row="row" :show-prefix="showPrefix" inline />
        <!-- 비밀글 — 제목은 그대로 보이고 앞에 자물쇠(본문은 글쓴이·운영진만) -->
        <i v-if="row.secret" class="pi pi-lock mr-1 text-xs text-muted-color" :title="t('post.secretMark')" aria-hidden="true" data-testid="post-row-secret" />
        <span v-if="row.secret" class="sr-only">{{ t('post.secretMark') }}</span>
        <span v-if="tokens.length" class="post-row-title break-words"><template v-for="(part, i) in titleParts" :key="i"><mark v-if="part.match" class="rounded-sm bg-transparent font-bold text-primary">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
        <span v-else class="post-row-title break-words">{{ row.title }}</span>
        <!-- 투표가 붙은 글(post-poll-contract `hasPoll`) — 제목 옆 투표 칩 -->
        <span
            v-if="row.hasPoll"
            class="ml-1 inline-flex items-baseline gap-0.5 rounded border border-surface px-1 pb-[3px] pt-px align-[1px] text-[0.625rem] font-semibold leading-none text-muted-color"
            :title="t('poll.listMark')"
            data-testid="post-row-poll"
        >
            <i class="pi pi-chart-bar text-[0.5625rem]" aria-hidden="true" />
            <span>{{ t('poll.listChip') }}</span>
        </span>
        <i v-if="row.hasImage" class="pi pi-image ml-1 text-xs text-muted-color" :title="t('board.hasImage')" aria-hidden="true" />
        <span v-if="row.hasImage" class="sr-only">{{ t('board.hasImage') }}</span>
        <span v-if="commentCount && row.commentCount > 0" class="ml-1 text-xs font-semibold text-primary">
            <span class="sr-only">{{ t('board.commentCountLabel') }}</span><template v-if="plainCount">{{ row.commentCount }}</template><template v-else>[{{ row.commentCount }}]</template>
        </span>
        <!-- 새 글 「N」 — 제목 글자 줄의 세로 가운데(`align-middle` 은 소문자 가운데라 1px 올린다, 확정 시안), 안의 「N」 은 라틴 대문자라 한글 보정(`top:-0.09em`) 없이 원의 가운데(mobile-nav-prefix-style-contract 5절) -->
        <span v-if="isNew" class="relative -top-px ml-1 inline-flex size-4 items-center justify-center rounded-full bg-primary align-middle text-[0.5625rem] font-bold leading-none text-primary-contrast" :title="t('board.newMark')">
            <span aria-hidden="true">N</span>
            <span class="sr-only">{{ t('board.newMark') }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilSearchRules from '~/utils/util-search-rules';

interface Props {
    row: PostRow;
    /** 「새 글」 기준(시간) */
    newPostHours: number;
    showPrefix?: boolean;
    /** 고정 공지에 핀 표시 */
    pinnedMark?: boolean;
    /** 제목 뒤 [댓글 수] — 댓글 수를 줄의 다른 자리에 따로 그리는 목록 모양(테마의 기사형 줄)은 끈다 */
    commentCount?: boolean;
    /** 검색 결과의 일치 강조 단어(검색 응답의 `tokens`) — 비면 강조하지 않는다 */
    tokens?: string[];
    /** 칩을 제목 앞에 붙인다(칩 위치 `BEFORE`·한 줄짜리 자리) — 제목 위·닉네임 아래 칩 줄로 따로 그리는 목록은 끈다 */
    chips?: boolean;
    /** 댓글 수를 대괄호 없이 숫자만(데스크톱 게시판 목록 A 방식 — 사용자 시안 2026-11-12) */
    plainCount?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showPrefix: true,
    pinnedMark: true,
    commentCount: true,
    tokens: () => [],
    chips: true,
    plainCount: false,
});

const { t } = useI18n();
const titleParts = computed(() => UtilSearchRules.highlightParts(props.row.title, props.tokens));
const isNew = computed(() => UtilPost.isNew(props.row.createdAt, props.newPostHours, Date.now()));
</script>
