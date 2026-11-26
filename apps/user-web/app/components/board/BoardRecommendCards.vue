<template>
    <!--
        게시판 추천 글 카드 줄(board-recommend-contract 「화면」) — 게시판 목록의 안내문·투자 고지·19금 안내 아래, 말머리 줄 위.
        PC(md 이상) = 같은 폭 카드 3장 한 줄(모자라면 그 수만큼 왼쪽부터), 모바일 = 옆으로 밀어 보는 카드(한 장 약 72% 폭, 칸에 맞춰 멈춤 — `AppScrollRow`, 화살표 없이 손가락 밀기).
        카드 = 작은 글자 한 줄(고른 글은 하이라이트 요약 · 비면 「운영자 추천」, 자동 글은 「추천 n · 말머리」) + 큰 글자 제목 한 줄(말줄임), 누르면 그 글.
        색은 게시판 표시색 8% 옅은 바탕 + 같은 색 테두리, 작은 글자는 그 바탕에서 읽히게 명도를 보정한 같은 색(`UtilColor.cssTagText`). 표시색이 없으면 중립 회색 면.
        카드가 하나도 없으면 아무것도 그리지 않는다(자리도 차지하지 않는다). 받기·실패는 페이지 쪽 컴포저블(`useBoardRecommended`)이 한다.
    -->
    <nav v-if="items.length" class="-mx-4 mt-3" :aria-label="t('boardRecommend.label')" data-testid="board-recommend">
        <AppScrollRow mode="ARROWS" scroller-class="snap-x snap-mandatory scroll-px-4 px-4 py-0.5 md:snap-none">
            <ul class="m-0 flex list-none gap-2 p-0 md:grid md:grid-cols-3">
                <li v-for="item in shown" :key="item.postId" class="w-[72%] shrink-0 snap-start md:w-auto md:min-w-0">
                    <NuxtLink
                        :to="UtilPost.link(item.postId)"
                        class="flex h-full min-w-0 flex-col gap-0.5 rounded-lg border px-3 py-2.5 no-underline transition-colors focus-visible:outline-2 focus-visible:outline-primary"
                        :class="cardStyle ? 'hover:brightness-[0.97] dark:hover:brightness-110' : 'border-surface bg-surface-50 hover:bg-surface-100 dark:bg-surface-800/60 dark:hover:bg-surface-800'"
                        :style="cardStyle"
                        :data-kind="item.kind"
                        data-testid="board-recommend-card"
                    >
                        <span class="truncate text-xs" :class="labelStyle ? '' : 'text-muted-color'" :style="labelStyle">{{ UtilBoardRecommendMessages.label(t, item) }}</span>
                        <span class="truncate text-[0.9375rem] font-semibold leading-snug text-color">{{ item.title }}</span>
                    </NuxtLink>
                </li>
            </ul>
        </AppScrollRow>
    </nav>
</template>

<script setup lang="ts">
import type { BoardRecommendItem } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';
import UtilBoardRecommendMessages from '~/utils/util-board-recommend-messages';
import UtilColor from '~/utils/util-color';
import UtilPost from '~/utils/util-post';

/** 카드는 많아야 3장(서버도 3개까지만 준다) */
const MAX = 3;

interface Props {
    /** 추천 글 카드 — 서버 차례(고른 글 → 자동 채움) */
    items?: BoardRecommendItem[];
    /** 게시판 표시색(프리셋 이름 또는 `#rrggbb`) — 없으면 중립 */
    color?: string | null;
}
const props = withDefaults(defineProps<Props>(), { items: () => [], color: null });

const { t } = useI18n();

const shown = computed(() => props.items.slice(0, MAX));

/** 표시색 옅은 바탕 + 테두리 — 표시색이 없으면 undefined(클래스의 중립 면) */
const cardStyle = computed<Record<string, string> | undefined>(() => {
    const pair = UtilColor.cssPair(UtilBoard.boardColor(props.color));
    return pair
        ? {
              '--rc-c': pair,
              background: 'color-mix(in srgb, var(--rc-c) 8%, var(--p-content-background))',
              borderColor: 'color-mix(in srgb, var(--rc-c) 28%, var(--p-content-background))',
          }
        : undefined;
});

/** 작은 글자 색 — 옅은 바탕에서 4.5:1 이 되게 명도만 보정한 표시색 */
const labelStyle = computed<Record<string, string> | undefined>(() => {
    const text = UtilColor.cssTagText(UtilBoard.boardColor(props.color));
    return text ? { color: text } : undefined;
});
</script>
