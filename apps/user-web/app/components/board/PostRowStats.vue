<template>
    <!-- 칩 위치 「제목 위」(`ABOVE`) 윗줄 오른쪽 끝 — 추천(엄지) · 조회(눈) 순서(사용자 2026-11-08 — 추천이 왼쪽)(post-chip-position-contract). 날짜는 이 줄이 아니라 닉네임(등급) 바로 뒤다(`PostRowFrame` 의 `dateInline`).
         추천·조회가 운영자가 정한 기준(`boardDisplay.likeHighlight`·`viewHighlight`, 0 = 끔) 이상이면 그 숫자를 운영자가 고른 강조 색(`highlightColor`, 기본 빨강)으로 강조한다(추천은 채운 엄지 — 굵게는 뺐다, 사용자 2026-11-12).
         강조 색은 라이트·다크 바탕에서 읽히게 명도만 보정해 그린다(`UtilColor.cssPair` → `light-dark()`). 기준·색은 공유 상태(공개 사이트 정보)라 이 부품이 직접 읽는다 — 목록마다 넘기지 않으려고.
         추천은 1,000개부터 「1k+」·「2k+」(천 단위 내림), 조회는 쉼표 그대로. 숫자는 고정폭, 아이콘은 화면 읽기 프로그램에 숨기고 「조회 N」 글자를 읽힌다. 원래 그 수를 보이지 않던 목록은 그 칸을 끈다(`views`·`likes`) -->
    <span v-if="views || likes" class="inline-flex shrink-0 items-center gap-2 whitespace-nowrap text-xs leading-none text-muted-color tabular-nums" data-testid="post-row-stats">
        <span v-if="likes" class="inline-flex items-center gap-0.5" :style="hotLikes ? hotStyle : undefined" :data-hot="hotLikes ? 'true' : undefined">
            <i :class="hotLikes ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'" class="text-[0.6875rem]" aria-hidden="true" />
            <span class="sr-only">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
            <span aria-hidden="true">{{ likesText }}</span>
        </span>
        <span v-if="views" class="inline-flex items-center gap-0.5" :style="hotViews ? hotStyle : undefined" :data-hot="hotViews ? 'true' : undefined">
            <i class="pi pi-eye text-[0.6875rem]" aria-hidden="true" />
            <span class="sr-only">{{ t('board.viewsShort', { n: row.viewCount }) }}</span>
            <span aria-hidden="true">{{ row.viewCount.toLocaleString() }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';

interface Props {
    row: Pick<PostRow, 'viewCount' | 'likeCount'>;
    /** 조회 수 칸 */
    views?: boolean;
    /** 추천 수 칸 */
    likes?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    views: true,
    likes: true,
});

const { t } = useI18n();
const { display } = useBoardDisplay();

/** 추천이 강조 기준 이상인가(기준 0 = 끔) */
const hotLikes = computed(() => UtilBoard.isHighlighted(props.row.likeCount, display.value.likeHighlight));
/** 조회가 강조 기준 이상인가(기준 0 = 끔) */
const hotViews = computed(() => UtilBoard.isHighlighted(props.row.viewCount, display.value.viewHighlight));
/** 추천 수 글자 — 1,000개부터 천 단위 내림 + 「k+」(1,000~1,999 → 1k+, 2,000~2,999 → 2k+), 그 아래는 그대로(사용자 결정 2026-11-12). 화면 읽기는 정확한 수 */
const likesText = computed(() => (props.row.likeCount >= 1000 ? `${Math.floor(props.row.likeCount / 1000)}k+` : String(props.row.likeCount)));
/** 강조 색 — 두 테마 바탕에서 읽히게 보정한 `light-dark()`. 색이 틀리면 기본 빨강 */
const hotStyle = computed(() => ({ color: UtilColor.cssPair(display.value.highlightColor) ?? UtilColor.cssPair(UtilBoard.HIGHLIGHT_COLOR) ?? undefined }));
</script>
