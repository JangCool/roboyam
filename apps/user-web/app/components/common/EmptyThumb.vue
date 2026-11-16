<template>
    <!--
        사진이 없을 때의 빈 썸네일(사용자 확정 시안 2026-11-16) — 부모 칸을 꽉 채운다(크기·비율·모서리는 부모가 준다). 장식이라 읽지 않는다(옆에 제목이 있다).
        · `post`(일반 글) 회색 면 + 가운데 사이트 이름(IROBIT) · `news`(뉴스 글) 남색 면 + 신문 아이콘 + 「NEWS」 · `deal`(핫딜) 회색 면 + 쇼핑백 아이콘 + 사이트 이름 · `groupBuy`(공동구매) 회색 면 + 택배 상자 아이콘 + 사이트 이름.
        크기는 이 칸 폭을 보는 컨테이너 쿼리로 나눈다 — 3rem 미만: 글자 없음(일반 글은 바탕만, 아이콘 모양은 작은 아이콘만) · 3rem~: 아이콘 모양도 글자를 붙인다(좁은 칸은 8px·간격 좁게) · 3.5rem~: 일반 글 글자 9px ·
        10rem~·16rem~·24rem~: 글자·아이콘을 한 단계씩 키운다(넓은 칸의 일반 글 글자 15px). 색은 테마 변수(`main.css` `--empty-thumb-*`, 다크는 `[data-color-scheme='dark']`).
    -->
    <span class="@container flex size-full flex-col items-center justify-center overflow-hidden" :class="kind === 'news' ? 'bg-(--empty-thumb-news-bg)' : 'bg-(--empty-thumb-bg)'" aria-hidden="true" :data-kind="kind" data-testid="empty-thumb">
        <TablerIcon
            v-if="icon"
            :name="icon"
            class="block size-4 shrink-0 @min-[3.5rem]:size-5 @min-[6rem]:size-6 @min-[10rem]:size-8 @min-[16rem]:size-10 @min-[24rem]:size-12"
            :class="kind === 'news' ? 'text-(--empty-thumb-news-icon)' : 'text-(--empty-thumb-fg)'"
        />
        <!-- 글자 간격(.18em)이 마지막 글자 뒤에도 붙어 왼쪽에 같은 만큼 띄워 가운데를 맞춘다 -->
        <span class="max-w-[90%] truncate font-semibold uppercase leading-none" :class="wordClass">{{ word }}</span>
    </span>
</template>

<script setup lang="ts">
import type { TablerIconName } from '~/components/common/TablerIcon.vue';

type EmptyThumbKind = 'post' | 'news' | 'deal' | 'groupBuy';

interface Props {
    /** 빈 자리 모양 — 일반 글 · 뉴스 글 · 핫딜 · 공동구매 */
    kind?: EmptyThumbKind;
}

const props = withDefaults(defineProps<Props>(), { kind: 'post' });

/** 뉴스 면의 글자 — 사이트 이름이 아니라 늘 같은 표시(장식 그림의 일부라 문구 키를 두지 않는다) */
const NEWS_WORD = 'NEWS';

/** 가운데 글자 — 뉴스는 「NEWS」, 그 밖은 사이트 이름(모르면 `UtilSiteName.DEFAULT`) */
const { siteLabel } = useSiteName();
const word = computed(() => (props.kind === 'news' ? NEWS_WORD : siteLabel.value));

const ICONS: Readonly<Record<EmptyThumbKind, TablerIconName | null>> = { post: null, news: 'news', deal: 'shopping-bag', groupBuy: 'package' };
const icon = computed(() => ICONS[props.kind]);

/**
 * 글자 크기·색 — 일반 글은 글자만이라 좁은 칸(3.5rem~)부터 보이고 넓을수록 크게(9 → 11 → 12 → 15px),
 * 아이콘 모양은 6rem 이상에서만 아이콘 아래에 붙인다(뉴스 9 → 11 → 12 → 14px · 핫딜·공동구매 10px 안팎).
 */
const wordClass = computed(() => {
    if (props.kind === 'post') return 'hidden ps-[0.18em] tracking-[0.18em] text-(--empty-thumb-fg) text-[9px] @min-[3.5rem]:block @min-[6rem]:text-[11px] @min-[10rem]:text-[12px] @min-[16rem]:text-[15px]';
    const color = props.kind === 'news' ? 'text-(--empty-thumb-news-fg)' : 'text-(--empty-thumb-fg)';
    // 핫딜·공동구매·뉴스도 작은 칸(3rem = 48px~)부터 글자를 붙인다(사용자 2026-11-16 「핫딜도 IROBIT 이 나와야」) — 좁은 칸은 글자 간격을 줄여 칸 안에 들게
    const size =
        props.kind === 'news'
            ? 'text-[8px] ps-[0.1em] tracking-[0.1em] @min-[4rem]:text-[9px] @min-[4rem]:ps-[0.18em] @min-[4rem]:tracking-[0.18em] @min-[10rem]:text-[11px] @min-[16rem]:text-[12px] @min-[24rem]:text-[14px]'
            : 'text-[8px] ps-[0.08em] tracking-[0.08em] @min-[4rem]:text-[9px] @min-[4rem]:ps-[0.14em] @min-[4rem]:tracking-[0.14em] @min-[6rem]:text-[10px] @min-[6rem]:ps-[0.18em] @min-[6rem]:tracking-[0.18em] @min-[24rem]:text-[11px]';
    return `hidden mt-0.5 @min-[3rem]:block @min-[6rem]:mt-1 @min-[10rem]:mt-1.5 @min-[16rem]:mt-2 ${color} ${size}`;
});
</script>
