<template>
    <!--
        하이라이트 가젯(`HIGHLIGHT`) — 운영자가 고른 글(관리자 하이라이트)을 크게 보이는 영역. 무엇을 보일지는 관리 화면의 하이라이트가 정하고, 가젯은 모양만 정한다.
          · 첫 글 크게(`lead: HERO`, 기본형·다음식): 큰 글(사진 + 큰 제목 + 요약 + 메타 — 칸이 좁으면 사진이 위) → 나머지는 두 열 목록(제목 + 작은 사진).
          · 첫 글 크게 + 템플릿이 `heroSplit` 을 고름(로보얌): 칸이 40rem 이상이면 큰 글(사진 + 제목 + 한 줄 요약 + 메타) 왼쪽 · 나머지 오른쪽 목록(작은 사진 + 제목), 좁으면 위아래.
            한 열(모바일) + 템플릿 한 열 모양 `mobile.highlightScroll`(로보얌): 큰 글 위(`heroBleed` 면 사진이 카드 좌우 끝까지) · 나머지는 가로로 미는 카드 줄(사진 + 두 줄 제목 + 메타, 한 칸 64%).
          · 첫 글 크게 아님(`lead: NONE`, 네이버형): 사진 왼쪽 + 굵은 제목 + 요약 + 메타가 한 행씩 쌓인다.
        운영자가 적은 노출 제목·요약·대표 이미지가 있으면 그것을 쓴다(없으면 글 제목·요약 없음·글 썸네일). 비었는데 영역 유지가 꺼져 있으면 템플릿이 자리째 뺀다.
        community 와 다른 점: 찬성·반대 의견 슬라이드(`debate`)는 옮기지 않았다(토론은 글 상세에 있다 — `post-debate-contract.md`).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || picks.length === 0" empty-icon="pi pi-star">
        <!-- 큰 글 왼쪽 + 나머지 오른쪽 목록(템플릿이 `heroSplit` 을 고를 때 — 로보얌). 칸이 40rem 보다 좁으면 큰 글 위 · 목록 아래 한 열 -->
        <div v-if="split && cfg.lead === 'HERO' && first" class="grid gap-x-7 pb-1 pt-1" :class="restScroll ? '' : '@min-[40rem]:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]'" data-testid="home-highlight-split">
            <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                <NuxtLink :to="`/posts/${first.post.id}`" class="block rounded-lg pb-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" :class="restScroll ? '' : '@min-[40rem]:pb-0'" data-testid="home-highlight-hero">
                    <HomeThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 9" :class="heroBleed ? 'gadget-bleed-x' : 'w-full'" />
                    <p class="mt-3.5 line-clamp-2 break-words text-lg font-bold leading-snug tracking-tight @min-[40rem]:text-xl">{{ titleOf(first) }}</p>
                    <p v-if="first.summary" class="mt-1.5 line-clamp-1 text-sm text-muted-color" data-testid="home-highlight-summary">{{ first.summary }}</p>
                    <HomeGadgetHighlightMeta :post="first.post" class="mt-2" />
                </NuxtLink>
            </FoldedRow>
            <!-- 한 열 — 나머지는 가로로 미는 카드 줄 -->
            <ul v-if="restScroll && rest.length" class="gadget-scroll-row mt-1" :style="ROW" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-highlight-card">
                            <HomeThumb v-if="cfg.thumbs === 'ALL'" :src="imageOf(h)" ratio="16 / 10" class="w-full" />
                            <p class="mt-2 line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug">{{ titleOf(h) }}</p>
                            <HomeGadgetHighlightMeta :post="h.post" class="mt-1" />
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ul>
            <ul v-else-if="rest.length" class="m-0 flex list-none flex-col border-t border-surface p-0 @min-[40rem]:border-t-0" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0 @min-[40rem]:first:-mt-3">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="flex items-center gap-3.5 py-3.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                            <HomeThumb v-if="cfg.thumbs === 'ALL'" :src="imageOf(h)" ratio="4 / 3" class="w-[5.5rem] shrink-0" />
                            <div class="min-w-0 flex-1">
                                <p class="line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug">{{ titleOf(h) }}</p>
                                <HomeGadgetHighlightMeta :post="h.post" class="mt-1.5" />
                            </div>
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ul>
        </div>

        <template v-else-if="cfg.lead === 'HERO' && first">
            <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                <NuxtLink
                    :to="`/posts/${first.post.id}`"
                    class="grid gap-3 pb-4 pt-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary @min-[30rem]:gap-[1.125rem]"
                    :class="[cfg.thumbs === 'NONE' ? '' : '@min-[30rem]:grid-cols-[minmax(0,13rem)_minmax(0,1fr)]', rest.length ? 'border-b border-surface' : '']"
                    data-testid="home-highlight-hero"
                >
                    <HomeThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 10" class="w-full" />
                    <div class="min-w-0">
                        <p class="line-clamp-3 break-words text-lg font-bold leading-snug tracking-tight @min-[30rem]:text-xl">{{ titleOf(first) }}</p>
                        <p v-if="first.summary" class="mt-2 line-clamp-3 text-sm leading-relaxed text-muted-color">{{ first.summary }}</p>
                        <HomeGadgetHighlightMeta :post="first.post" class="mt-2" />
                    </div>
                </NuxtLink>
            </FoldedRow>
            <ul v-if="rest.length" class="m-0 grid list-none p-0 @min-[36rem]:grid-cols-2 @min-[36rem]:gap-x-6" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="flex items-start gap-3 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                            <div class="min-w-0 flex-1">
                                <p class="line-clamp-2 break-words text-[0.9375rem] leading-snug">{{ titleOf(h) }}</p>
                                <HomeGadgetHighlightMeta :post="h.post" class="mt-1" />
                            </div>
                            <HomeThumb v-if="cfg.thumbs === 'ALL' && imageOf(h)" :src="imageOf(h)" ratio="3 / 2" class="w-24 shrink-0" />
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ul>
        </template>

        <!-- 행 — 사진 왼쪽 + 굵은 제목 + 요약 + 메타 -->
        <ul v-else class="m-0 list-none p-0" data-testid="home-highlight-list">
            <li v-for="(h, i) in picks" :key="h.id" class="border-b border-surface last:border-b-0">
                <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${h.post.id}`" class="flex items-start gap-3 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:gap-3.5">
                        <HomeThumb v-if="cfg.thumbs === 'ALL' || (cfg.thumbs === 'FIRST' && i === 0)" :src="imageOf(h)" ratio="132 / 86" class="w-24 shrink-0 md:w-[8.25rem]" />
                        <div class="min-w-0 flex-1">
                            <p class="line-clamp-2 break-words text-base font-bold leading-snug tracking-tight md:text-lg">{{ titleOf(h) }}</p>
                            <p v-if="h.summary" class="mt-0.5 hidden truncate text-sm text-muted-color @min-[28rem]:block" data-testid="home-highlight-summary">{{ h.summary }}</p>
                            <HomeGadgetHighlightMeta :post="h.post" bubble class="mt-1" />
                        </div>
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget, HomeHighlight } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const overrides = useBlockOverrides();
const options = useHomeGadgetLookOptions();
/** 큰 글 왼쪽 + 나머지 오른쪽 목록 — 템플릿이 고른 세부 모양(기본 false) */
const split = options.heroSplit;
/** 한 열(모바일)이고 템플릿이 골랐을 때만 — 나머지 글은 가로로 미는 카드 줄 · 큰 글 사진은 카드 끝까지 */
const stacked = useHomeGadgetStacked();
const restScroll = computed(() => stacked.value && options.mobile.highlightScroll);
const heroBleed = computed(() => stacked.value && options.mobile.heroBleed);
const ROW = { '--row-item': '64%' };

const cfg = computed(() => UtilHomeGadget.highlightConfig(props.gadget.config));
const picks = computed(() => UtilHomeGadget.highlights(props.gadget.data));
const first = computed(() => picks.value[0] ?? null);
const rest = computed(() => picks.value.slice(1));

/** 노출 제목 — 운영자가 적은 것, 없으면 글 제목 */
function titleOf(h: HomeHighlight): string {
    return h.title ?? h.post.title;
}

/** 대표 이미지 — 운영자가 올린 것, 없으면 글 썸네일 */
function imageOf(h: HomeHighlight): string | null {
    return h.imageUrl ?? h.post.thumbnailUrl;
}

/** 접는 까닭 — 차단한 회원·단어의 글이면 접힌 줄 */
function foldOf(h: HomeHighlight) {
    return overrides.blockedOf(h.post.author.memberId, h.post.blocked);
}
</script>
