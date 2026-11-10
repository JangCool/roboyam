<template>
    <!--
        게시판 목록 안 애드센스 인피드 한 줄 — 장부 한 줄 광고(`AdFeedRow`)가 채우지 못한 자리. 목록형(LIST — 기본·다음식·네이버식)에만 넣는다.
        이 부품이 줄 요소 자체(`table` 은 `<tr>`, 나머지는 `<li>`)라 목록이 주는 테두리 class 는 그대로 붙고(fallthrough), 광고가 오지 않으면 줄째 사라진다(빈 테두리가 남지 않게).
        자리 최소 높이는 그 목록 글 줄 높이 — 광고가 오기 전까지 아래 글이 덜 밀린다(인피드는 보통 글 줄보다 조금 크다).
    -->
    <tr v-if="!collapsed && variant === 'table'" data-testid="adsense-feed-row">
        <td colspan="5" class="py-2 pr-1">
            <AdSenseUnit :spec="spec" :min-height="ROW_HEIGHT.table" @unfilled="collapsed = true" />
        </td>
    </tr>
    <li v-else-if="!collapsed" data-testid="adsense-feed-row">
        <div :class="PAD[variant]">
            <AdSenseUnit :spec="spec" :min-height="ROW_HEIGHT[variant]" @unfilled="collapsed = true" />
        </div>
    </li>
</template>

<script setup lang="ts">
import type { AdSenseUnitSpec } from '~/utils/util-adsense';

type FeedVariant = 'table' | 'compact' | 'headline' | 'article';

interface Props {
    spec: AdSenseUnitSpec;
    variant: FeedVariant;
}

withDefaults(defineProps<Props>(), {});

/** 그 목록 글 줄의 높이(한 줄) — 자리 최소 높이 */
const ROW_HEIGHT: Record<FeedVariant, string> = {
    table: '2.5rem',
    compact: '2.75rem',
    headline: '2.75rem',
    article: '3rem',
};

/** 그 목록 글 줄과 같은 좌우 여백(`AdFeedRow` 의 `LINE` 과 같다) */
const PAD: Record<FeedVariant, string> = {
    table: '',
    compact: 'px-4 py-2 md:px-2',
    headline: 'py-2',
    article: 'py-2.5',
};

const collapsed = ref(false);
</script>
