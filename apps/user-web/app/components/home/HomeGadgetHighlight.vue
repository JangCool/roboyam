<template>
    <!--
        하이라이트 가젯(`HIGHLIGHT`) — 운영자가 고른 글(관리자 하이라이트)을 크게 보이는 영역. 무엇을 보일지는 관리 화면의 하이라이트가 정하고, 가젯은 모양만 정한다.
          · 첫 글 크게(`lead: HERO`, 기본형·다음식): 큰 글(사진 + 큰 제목 + 요약 + 메타 — 칸이 좁으면 사진이 위) → 나머지는 두 열 목록(제목 + 작은 사진).
          · 첫 글 크게 + 템플릿이 `heroSplit` 을 고름(로보얌): 칸이 40rem 이상이면 큰 글(사진 + 제목 + 한 줄 요약 + 메타) 왼쪽 · 나머지 오른쪽 목록(작은 사진 + 제목·추천 + 메타), 좁으면 위아래.
            한 열(모바일) + 템플릿 한 열 모양 `mobile.highlightScroll`(로보얌): 모든 글을 옆으로 밀어 넘기는 슬라이드(한 장 88% — 낮은 5:2 사진 + 두 줄 제목 + 메타, 2026-11-05).
          · 첫 글 크게 아님(`lead: NONE`, 네이버형): 사진 왼쪽 + 굵은 제목 + 요약 + 메타가 한 행씩 쌓인다.
        운영자가 적은 노출 제목·요약·대표 이미지가 있으면 그것을 쓴다(없으면 글 제목·요약 없음·글 썸네일). 비었는데 영역 유지가 꺼져 있으면 템플릿이 자리째 뺀다.
        community 와 다른 점: 찬성·반대 의견 슬라이드(`debate`)는 옮기지 않았다(토론은 글 상세에 있다 — `post-debate-contract.md`).
        찬반 토론 글(feed-debate-counts-contract, 사용자 2026-11-10 시안): 사진 안 위쪽 폭 전체 띠(`HomeDebateBand` — 큰 사진은 이름표 포함 「찬 0 · 반 3 · 중 3」, 작은 사진은 색 숫자만 + 툴팁).
        사진을 그리지 않는 줄은 띠 대신 메타 줄(게시판 이름 다음)에 글자 색으로. 메타 줄 = 게시판 이름 → 추천 → 댓글 → 오른쪽 끝 [아바타 · 닉네임] · 날짜(조회수는 넣지 않는다).
        로보얌 오른쪽 작은 목록 줄은 추천을 제목 줄 오른쪽 끝으로(제목은 추천 칸 앞에서 두 줄 말줄임 — 사용자 2026-11-10 추가 결정). 작성자는 가젯 설정 「작성자 보이기」(`showAuthor`, 기본 켬)를 따른다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || picks.length === 0" empty-icon="pi pi-star">
        <!-- 한 열(모바일) + 템플릿 한 열 모양(로보얌) — 모든 글을 옆으로 밀어 넘기는 슬라이드(한 장씩 스냅, 다음 장이 조금 보인다). 사진은 낮게(5:2) 해 화면을 덜 차지한다(2026-11-05 사용자 결정) -->
        <ul v-if="split && cfg.lead === 'HERO' && restScroll" class="gadget-scroll-row pt-1" :style="SLIDE" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" data-testid="home-highlight-slides">
            <li v-for="h in picks" :key="h.id">
                <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${h.post.id}`" class="block rounded-lg pb-1 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-highlight-slide">
                        <HomeThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(h)" ratio="5 / 2" class="w-full">
                            <HomeDebateBand v-if="h.post.debate" :debate="h.post.debate" size="lg" />
                        </HomeThumb>
                        <p class="mt-2 line-clamp-2 break-words text-base font-bold leading-snug tracking-tight">{{ titleOf(h) }}</p>
                        <HomeGadgetHighlightMeta :post="h.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-1.5" />
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>

        <!-- 큰 글 왼쪽 + 나머지 오른쪽 목록(템플릿이 `heroSplit` 을 고를 때 — 로보얌). 칸이 40rem 보다 좁으면 큰 글 위 · 목록 아래 한 열 -->
        <div v-else-if="split && cfg.lead === 'HERO' && first" class="grid gap-x-7 pb-1 pt-1 @min-[40rem]:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]" data-testid="home-highlight-split">
            <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                <NuxtLink :to="`/posts/${first.post.id}`" class="block rounded-lg pb-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary @min-[40rem]:pb-0" data-testid="home-highlight-hero">
                    <HomeThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 9" class="w-full">
                        <HomeDebateBand v-if="first.post.debate" :debate="first.post.debate" size="lg" />
                    </HomeThumb>
                    <p class="mt-3.5 line-clamp-2 break-words text-lg font-bold leading-snug tracking-tight @min-[40rem]:text-xl">{{ titleOf(first) }}</p>
                    <p v-if="first.summary" class="mt-1.5 line-clamp-1 text-sm text-muted-color" data-testid="home-highlight-summary">{{ first.summary }}</p>
                    <HomeGadgetHighlightMeta :post="first.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-2" />
                </NuxtLink>
            </FoldedRow>
            <ul v-if="rest.length" class="m-0 flex list-none flex-col border-t border-surface p-0 @min-[40rem]:border-t-0" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0 @min-[40rem]:first:-mt-3 @min-[40rem]:last:-mb-3">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="flex items-center gap-3.5 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                            <HomeThumb v-if="cfg.thumbs === 'ALL'" :src="imageOf(h)" ratio="16 / 10" class="w-[5.5rem] shrink-0">
                                <HomeDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                            </HomeThumb>
                            <div class="min-w-0 flex-1">
                                <!-- 제목 + 추천(오른쪽 끝, 제목 첫 줄 높이) — 제목은 추천 칸 앞에서 두 줄 말줄임(사용자 2026-11-10 추가 결정) -->
                                <div class="flex items-start gap-2">
                                    <p class="m-0 line-clamp-2 min-w-0 flex-1 break-words text-[0.9375rem] font-semibold leading-snug">{{ titleOf(h) }}</p>
                                    <span class="inline-flex h-[calc(0.9375rem*1.375)] shrink-0 items-center gap-0.5 text-xs tabular-nums text-muted-color" data-testid="home-highlight-likes">
                                        <i class="pi pi-thumbs-up text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('board.likesShort', { n: h.post.likeCount }) }}</span><span aria-hidden="true">{{ h.post.likeCount.toLocaleString() }}</span>
                                    </span>
                                </div>
                                <HomeGadgetHighlightMeta :post="h.post" :likes="false" :debate="cfg.thumbs !== 'ALL'" :author="cfg.showAuthor" class="mt-1.5" />
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
                    <HomeThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 10" class="w-full">
                        <HomeDebateBand v-if="first.post.debate" :debate="first.post.debate" size="lg" />
                    </HomeThumb>
                    <div class="min-w-0">
                        <p class="line-clamp-3 break-words text-lg font-bold leading-snug tracking-tight @min-[30rem]:text-xl">{{ titleOf(first) }}</p>
                        <p v-if="first.summary" class="mt-2 line-clamp-3 text-sm leading-relaxed text-muted-color">{{ first.summary }}</p>
                        <HomeGadgetHighlightMeta :post="first.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-2" />
                    </div>
                </NuxtLink>
            </FoldedRow>
            <ul v-if="rest.length" class="m-0 grid list-none p-0 @min-[36rem]:grid-cols-2 @min-[36rem]:gap-x-6" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="flex items-start gap-3 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                            <div class="min-w-0 flex-1">
                                <p class="line-clamp-2 break-words text-[0.9375rem] leading-snug">{{ titleOf(h) }}</p>
                                <HomeGadgetHighlightMeta :post="h.post" :debate="!(cfg.thumbs === 'ALL' && imageOf(h))" :author="cfg.showAuthor" class="mt-1" />
                            </div>
                            <HomeThumb v-if="cfg.thumbs === 'ALL' && imageOf(h)" :src="imageOf(h)" ratio="3 / 2" class="w-24 shrink-0">
                                <HomeDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                            </HomeThumb>
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
                        <HomeThumb v-if="rowThumb(i)" :src="imageOf(h)" ratio="132 / 86" class="w-24 shrink-0 md:w-[8.25rem]">
                            <HomeDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                        </HomeThumb>
                        <div class="min-w-0 flex-1">
                            <p class="line-clamp-2 break-words text-base font-bold leading-snug tracking-tight md:text-lg">{{ titleOf(h) }}</p>
                            <p v-if="h.summary" class="mt-0.5 hidden truncate text-sm text-muted-color @min-[28rem]:block" data-testid="home-highlight-summary">{{ h.summary }}</p>
                            <HomeGadgetHighlightMeta :post="h.post" :debate="!rowThumb(i)" :author="cfg.showAuthor" class="mt-1" />
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
/** 슬라이드 한 장 폭 — 다음 장이 조금 보여 밀 수 있다는 걸 알린다 */
const SLIDE = { '--row-item': '88%' };

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

/** 행 모양에서 그 줄에 사진을 그리는가 — 모두 · 맨 위 글만(첫 줄) */
function rowThumb(i: number): boolean {
    return cfg.value.thumbs === 'ALL' || (cfg.value.thumbs === 'FIRST' && i === 0);
}

/** 접는 까닭 — 차단한 회원·단어의 글이면 접힌 줄 */
function foldOf(h: HomeHighlight) {
    return overrides.blockedOf(h.post.author.memberId, h.post.blocked);
}
</script>
