<template>
    <!--
        찬반 토론 글의 입장 수(feed-debate-counts-contract) — 칩이 아니라 글자 색: 찬 = 파랑 · 반 = 빨강 · 중 = 회색, 약간 굵게, 숫자 고정폭. 중립을 받지 않는 토론(`neutral` null)은 찬·반만.
        눈으로 보는 글자(「찬 4」)는 화면 읽기에서 숨기고 「찬성 4, 반대 9, 중립 2」 한 문장을 읽힌다.
        · `tone="page"`(기본): 페이지·카드 바탕 위 — 토론 입장 색 유틸(`stance-pro-text`·`stance-con-text`) + 흐린 글자색.
        · `tone="band"`: 사진 위 어두운 띠 위 — 밝은 파랑·밝은 빨강·밝은 회색(띠는 테마와 무관하게 늘 어둡다).
        `labels` 를 끄면 숫자만(작은 썸네일 띠), `cap` 이면 99 를 넘는 수를 「99+」로, `dots` 면 칸 사이에 가운데점, `spread` 면 폭 전체에 고르게 나눈다.
    -->
    <span class="inline-flex items-center font-semibold tabular-nums" :class="spread ? 'w-full justify-around' : dots ? 'gap-1' : 'gap-1.5'" data-testid="feed-debate-counts">
        <span class="sr-only">{{ summary }}</span>
        <template v-for="(p, i) in parts" :key="p.key">
            <span v-if="dots && i > 0" aria-hidden="true" class="opacity-70">·</span>
            <span aria-hidden="true" class="whitespace-nowrap" :class="p.color" :data-stance="p.key">{{ labels ? `${p.label} ${p.text}` : p.text }}</span>
        </template>
    </span>
</template>

<script setup lang="ts">
import type { PostFeedDebate } from '~/composables/board/api/usePostApi.types';
import UtilFeedDebate from '~/utils/util-feed-debate';

interface Props {
    debate: PostFeedDebate;
    tone?: 'page' | 'band';
    /** 짧은 이름표(찬·반·중) — 끄면 숫자만 */
    labels?: boolean;
    /** 99 를 넘으면 「99+」 */
    cap?: boolean;
    /** 칸 사이 가운데점 */
    dots?: boolean;
    /** 폭 전체에 고르게(justify-around) */
    spread?: boolean;
}

const props = withDefaults(defineProps<Props>(), { tone: 'page', labels: true, cap: false, dots: false, spread: false });

const { t } = useI18n();

const COLORS = {
    page: { PRO: 'stance-pro-text', CON: 'stance-con-text', NEUTRAL: 'text-muted-color' },
    band: { PRO: 'text-blue-300', CON: 'text-red-300', NEUTRAL: 'text-gray-300' },
} as const;

const parts = computed(() =>
    UtilFeedDebate.parts(props.debate).map((p) => ({
        key: p.stance,
        label: t(p.stance === 'PRO' ? 'feed.debatePro' : p.stance === 'CON' ? 'feed.debateCon' : 'feed.debateNeutral'),
        text: props.cap ? UtilFeedDebate.capped(p.count) : p.count.toLocaleString(),
        color: COLORS[props.tone][p.stance],
    })),
);
const summary = computed(() => UtilFeedDebate.summary(props.debate, t));
</script>
