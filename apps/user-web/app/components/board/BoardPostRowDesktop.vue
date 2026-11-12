<template>
    <!--
        데스크톱 글 목록의 글 한 줄(A 방식 — 사용자 시안 확정 2026-11-12, mobile-nav-prefix-style-contract 6절). 칩 위치가 「제목 위」(`ABOVE`, 기본)인 게시판 목록(LIST)만 md 이상에서 이 줄을 쓴다
        (모바일은 지금 `PostRowFrame` 그대로).
          · 첫 줄: 제목 · 투표·사진 표시 · 댓글 수 · 새 글 N — 한 줄(넘치면 말줄임). 제목 줄에는 말머리 칩을 두지 않는다(제목이 늘 같은 자리에서 시작)
          · 둘째 줄: 아바타 닉네임 · 말머리(글자 모양이면 보통 굵기 글자 — 지정 색은 그 색, 없으면 회색 「일반」 / 회색·색상 칩 모양이면 그 칩) · 처리 상태·19 · 날짜
          · 둘째 줄 날짜 뒤에 추천(찬반 글은 찬·반·중 수 + 추천), 오른쪽 끝 칸은 닉네임 줄 높이에 조회만 — 강조 규칙 그대로. 줄 위아래 여백 8px(2026-11-12 시안 확정). 썸네일(64×48)은 그 칸 바로 왼쪽, 두 줄 높이. 숫자는 썸네일이 있든 없든 줄마다 같은 자리(사용자 결정 2026-11-12)
          · 섞인 목록(인기글·최신글 — `FeedPostItem`)도 같은 줄을 쓴다(2026-11-12): `lead` 슬롯(게시판 이름 — 보통 굵기·진한 게시판 색, 소모임 글은 「소모임」, 채운 기간 표시)이 닉네임 뒤에 오고, 말머리는 그 뒤 「|」 다음 · 말머리가 없으면 「일반」 없이 게시판 이름만. `stats-lead` 슬롯(찬·반·중 수)은 둘째 줄 날짜 뒤·추천 앞
          · 칸 맞춤: 두 줄을 24px 줄 두 개(합 48px = 썸네일 높이)로 고정한 격자라 제목 줄과 숫자가 같은 선에 선다
    -->
    <div class="grid grid-cols-[minmax(0,1fr)_auto_minmax(4rem,auto)] grid-rows-[1.5rem_1.5rem] items-center gap-x-3" data-testid="post-row-desktop">
        <p class="col-start-1 row-start-1 truncate text-[0.9375rem] font-normal leading-6"><PostRowTitle :row="row" :new-post-hours="newPostHours" :chips="false" :pinned-mark="pinnedMark" :tokens="tokens" plain-count /></p>
        <p class="col-start-1 row-start-2 flex min-w-0 items-center gap-x-1.5 overflow-hidden whitespace-nowrap text-xs leading-6 text-muted-color">
            <PostAuthorLabel :author="row.author" avatar menu :blocked="row.blocked === 'USER'" class="shrink-0" />
            <!-- 섞인 목록 — 게시판 이름(·채운 기간·소모임)이 말머리 앞 -->
            <template v-if="$slots.lead">
                <span aria-hidden="true">·</span>
                <slot name="lead" />
            </template>
            <template v-if="showPrefix && (row.prefix || !$slots.lead)">
                <span v-if="$slots.lead" class="text-muted-color" aria-hidden="true">|</span>
                <span v-else aria-hidden="true">·</span>
                <template v-if="row.prefix">
                    <span v-if="prefixStyle === 'TEXT'" class="shrink-0" :class="textStyle ? '' : 'text-surface-900 dark:text-surface-0'" :style="textStyle" data-testid="post-row-prefix">{{ row.prefix.name }}</span>
                    <PostPrefixTag v-else :prefix="row.prefix" class="shrink-0" />
                </template>
                <span v-else class="shrink-0" data-testid="post-row-prefix-none">{{ t('board.prefixNone') }}</span>
            </template>
            <PostStatusTag v-if="row.processStatus" :status="row.processStatus" />
            <span v-if="row.adult" class="inline-flex size-4 shrink-0 items-center justify-center rounded-full bg-red-600 text-[0.5625rem] font-bold leading-none text-white" :title="t('board.adultMark')">
                <span aria-hidden="true">19</span>
                <span class="sr-only">{{ t('board.adultMark') }}</span>
            </span>
            <span aria-hidden="true">·</span>
            <PostListDate :iso="row.createdAt" :mode="dateDisplay" class="shrink-0" />
            <!-- 날짜 바로 뒤 — (찬반 토론 글이면 찬·반·중 수) · 추천(사용자 시안 확정 2026-11-12) -->
            <template v-if="$slots['stats-lead']">
                <span aria-hidden="true">·</span>
                <slot name="stats-lead" />
            </template>
            <span aria-hidden="true">·</span>
            <PostRowStats :row="row" :views="false" data-testid="post-row-likes" />
        </p>
        <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="col-start-2 row-span-2 row-start-1 h-12 w-16 rounded-md object-cover" loading="lazy" />
        <!-- 오른쪽 끝 — 닉네임 줄 높이에 조회만(제목 줄 오른쪽은 비워 제목이 길게 보인다, 사용자 시안 확정 2026-11-12) -->
        <PostRowStats :row="row" :likes="false" class="col-start-3 row-start-2 justify-self-end" data-testid="post-row-views" />
    </div>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';

interface Props {
    row: PostRow;
    newPostHours: number;
    dateDisplay: BoardDateDisplay;
    /** 말머리 자리 — 말머리를 쓰지 않는 목록은 끈다 */
    showPrefix?: boolean;
    /** 고정 공지 압정(게시판 목록) — 섞인 목록은 끈다 */
    pinnedMark?: boolean;
    /** 검색 일치 강조 단어 */
    tokens?: string[];
}

const props = withDefaults(defineProps<Props>(), { showPrefix: true, pinnedMark: true, tokens: () => [] });

const { t } = useI18n();
/** 말머리 모양 — 게시판 목록이 건 값(`providePrefixStyle`) */
const prefixStyle = usePrefixStyle();
/**
 * 글자 모양의 말머리 색 — 운영자가 고른 색이면 그 색을 **진하게**(태그 글자와 같은 4.5:1 명도 보정 — `UtilColor.cssTagText`, 사용자 결정 2026-11-12: 굵기는 빼고 색은 진하게), 「자동」·운영진 전용은 없음(기본 글자색)
 */
const textStyle = computed(() => {
    const prefix = props.row.prefix;
    if (!prefix || prefix.staffOnly) return undefined;
    const color = UtilColor.cssTagText(UtilBoard.boardColor(prefix.color));
    return color ? { color } : undefined;
});
</script>
