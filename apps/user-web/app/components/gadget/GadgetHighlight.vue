<template>
    <!--
        하이라이트 가젯(`HIGHLIGHT`) — 운영자가 고른 글(관리자 하이라이트)을 크게 보이는 영역. 무엇을 보일지는 관리 화면의 하이라이트가 정하고, 가젯은 모양만 정한다.
          · 첫 글 크게(`lead: HERO`, 기본형·다음식): 큰 글(사진 + 큰 제목 + 요약 + 메타 — 칸이 좁으면 사진이 위) → 나머지는 두 열 목록(제목 + 작은 사진).
          · 첫 글 크게 + 템플릿이 `heroSplit` 을 고름(아이로빗): 칸이 40rem 이상이면 큰 글(사진 + 제목 + 한 줄 요약 + 메타) 왼쪽 · 나머지 오른쪽 목록(작은 사진 + 제목·추천 + 메타).
            좁으면(40rem 미만) 설정 `narrow` 대로 — 기본(`DEFAULT`)은 위아래(큰 글 위 · 사진 목록 아래), `LIST`(home-news-contract)는 큰 글(3:1 사진 · 굵은 제목 한 줄 · 소개 글 두 줄 · [추천 · 댓글 …… 게시판])
            + 아래 글자 줄([추천 아이콘 고정 칸] 제목 댓글 수 …… 게시판, 닉네임 없음). `LIST` 의 두 모양은 칸 폭 컨테이너 쿼리로 하나만 보인다(숨은 쪽은 `display:none` 이라 높이 재기·사진 받기에서 빠진다).
            한 열(모바일) + 템플릿 한 열 모양 `mobile.highlightScroll`(아이로빗): 모든 글을 옆으로 밀어 넘기는 슬라이드(한 장 88% — 낮은 5:2 사진 + 두 줄 제목 + 메타, 2026-11-05).
          · 첫 글 크게 아님(`lead: NONE`, 네이버형): 사진 왼쪽 + 굵은 제목 + 요약 + 메타가 한 행씩 쌓인다.
        운영자가 적은 노출 제목·요약·대표 이미지가 있으면 그것을 쓴다(없으면 글 제목·요약 없음·글 썸네일). 비었는데 영역 유지가 꺼져 있으면 템플릿이 자리째 뺀다.
        community 와 다른 점: 찬성·반대 의견 슬라이드(`debate`)는 옮기지 않았다(토론은 글 상세에 있다 — `post-debate-contract.md`).
        찬반 토론 글(feed-debate-counts-contract, 사용자 2026-11-10 시안): 사진 안 위쪽 폭 전체 띠(`GadgetDebateBand` — 큰 사진은 이름표 포함 「찬 0 · 반 3 · 중 3」, 작은 사진은 색 숫자만 + 툴팁).
        사진을 그리지 않는 줄은 띠 대신 메타 줄(게시판 이름 다음)에 글자 색으로. 메타 줄 = 게시판 이름 → 추천 → 댓글 → 오른쪽 끝 [아바타 · 닉네임] · 날짜(조회수는 넣지 않는다).
        아이로빗 오른쪽 작은 목록 줄은 추천을 제목 줄 오른쪽 끝으로(제목은 추천 칸 앞에서 두 줄 말줄임 — 사용자 2026-11-10 추가 결정). 작성자는 가젯 설정 「작성자 보이기」(`showAuthor`, 기본 켬)를 따른다.
        마우스 올림 효과(설정 `hover`, `main.css`): 목록 줄(오른쪽 작은 목록 · 두 열 목록 · 행)은 옅은 바탕(`gadget-hover`), 큰 글·슬라이드(사진 카드)는 제목 밑줄(`gadget-hover-photo`) — 줄 모양은 그대로.
    -->
    <GadgetFrame :gadget="gadget" :empty="gadget.empty || picks.length === 0" empty-icon="pi pi-star">
        <!-- 한 열(모바일) + 템플릿 한 열 모양(아이로빗) — 모든 글을 옆으로 밀어 넘기는 슬라이드(한 장씩 스냅, 다음 장이 조금 보인다). 사진은 낮게(5:2) 해 화면을 덜 차지한다(2026-11-05 사용자 결정) -->
        <ul v-if="split && cfg.lead === 'HERO' && restScroll" class="gadget-scroll-row pt-1" :style="SLIDE" :aria-label="t('home.scrollRowLabel', { name: gadget.title })" data-testid="home-highlight-slides">
            <li v-for="h in picks" :key="h.id">
                <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${h.post.id}`" class="gadget-hover-photo block rounded-lg pb-1 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-highlight-slide">
                        <GadgetThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(h)" ratio="5 / 2" class="w-full">
                            <GadgetDebateBand v-if="h.post.debate" :debate="h.post.debate" size="lg" />
                        </GadgetThumb>
                        <p class="gadget-hover-title mt-2 line-clamp-2 break-words text-base font-bold leading-snug tracking-tight">{{ titleOf(h) }}</p>
                        <GadgetHighlightMeta :post="h.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-1.5" />
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>

        <!-- 템플릿이 `heroSplit` 을 고를 때(아이로빗) — 칸이 40rem 이상이면 큰 글 왼쪽 + 사진 목록 오른쪽. 좁으면 기본은 큰 글 위 · 목록 아래 한 열, `narrow: LIST` 면 큰 글 + 글자 줄 -->
        <div v-else-if="split && cfg.lead === 'HERO' && first" class="pb-1 pt-1" data-testid="home-highlight-split">
            <!-- 좁은 칸(40rem 미만) 「큰 글 + 글자 줄」(`narrow: LIST`): 1번 = 3:1 사진 밑에 굵은 제목 한 줄 + 소개 글 두 줄 + [추천 · 댓글 …… 게시판] → 2번부터 = [추천 고정 칸] 제목 댓글 수 …… 게시판 -->
            <div v-if="narrowList" class="@min-[40rem]:hidden" data-testid="home-highlight-stack">
                <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${first.post.id}`" class="gadget-hover-photo block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-highlight-cover">
                        <GadgetThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="3 / 1" class="w-full rounded-lg">
                            <GadgetDebateBand v-if="first.post.debate" :debate="first.post.debate" size="lg" />
                        </GadgetThumb>
                        <p class="gadget-hover-title m-0 mt-2 line-clamp-1 break-words text-base font-bold leading-snug tracking-tight">{{ titleOf(first) }}</p>
                        <!-- 소개 글 = 하이라이트 요약(`summary`, 운영자가 적는 값) — 없으면 줄 없음 -->
                        <p v-if="first.summary" class="m-0 mt-1 line-clamp-2 text-[0.8125rem] leading-snug text-muted-color" data-testid="home-highlight-stack-summary">{{ first.summary }}</p>
                        <div class="mt-1 flex items-center gap-2">
                            <!-- 왼쪽 [추천 · 댓글] …… 오른쪽 끝 게시판 이름(아래 글자 줄과 같은 자리, 닉네임·날짜 없음) -->
                            <GadgetHighlightMeta :post="first.post" order="author" :board="false" :debate="cfg.thumbs === 'NONE'" :author="false" :date="false" class="min-w-0" />
                            <span class="ml-auto shrink-0 text-xs font-semibold" :class="UtilBoard.textStyle(first.post.board.color) ? '' : 'text-primary'" :style="UtilBoard.textStyle(first.post.board.color)">{{ localizedName(first.post.board.name, first.post.board.nameEn) }}</span>
                        </div>
                    </NuxtLink>
                </FoldedRow>
                <ul v-if="rest.length" class="m-0 mt-1.5 list-none border-t border-surface p-0 pt-1" data-testid="home-highlight-lines">
                    <li v-for="h in rest" :key="h.id">
                        <FoldedRow :reason="foldOf(h)" kind="post" class="py-2 text-sm" hide-class="pb-1">
                            <NuxtLink :to="`/posts/${h.post.id}`" class="gadget-hover flex items-center gap-2 py-1 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                                <!-- 글 목록 뉴스형 한 줄과 같은 줄: 제목 앞 고정 폭 추천 칸 · 제목 · 댓글 수 …… 게시판(오른쪽 끝, 게시판 표시색 · 없으면 주 색상) -->
                                <GadgetHighlightMeta :post="h.post" order="author" :board="false" :comments="false" :author="false" :date="false" class="w-10 shrink-0" />
                                <FeedDebateCounts v-if="h.post.debate" :debate="h.post.debate" class="shrink-0 text-xs" />
                                <span class="min-w-0 truncate text-[0.9375rem] leading-snug">{{ titleOf(h) }}</span>
                                <span v-if="h.post.commentCount > 0" class="-ml-1 shrink-0 text-xs font-semibold text-primary"><span class="sr-only">{{ t('home.commentCount', { n: h.post.commentCount }) }}</span><span aria-hidden="true">{{ h.post.commentCount }}</span></span>
                                <span class="ml-auto shrink-0 text-xs font-semibold" :class="UtilBoard.textStyle(h.post.board.color) ? '' : 'text-primary'" :style="UtilBoard.textStyle(h.post.board.color)">{{ localizedName(h.post.board.name, h.post.board.nameEn) }}</span>
                            </NuxtLink>
                        </FoldedRow>
                    </li>
                </ul>
            </div>

            <!-- 큰 글 왼쪽 + 나머지 오른쪽 사진 목록(넓은 칸). 칸이 40rem 보다 좁으면 큰 글 위 · 목록 아래 한 열(`narrow: LIST` 면 숨기고 위 모양) -->
            <div class="gap-x-7 @min-[40rem]:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]" :class="narrowList ? 'hidden @min-[40rem]:grid' : 'grid'">
                <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${first.post.id}`" class="gadget-hover-photo block rounded-lg pb-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary @min-[40rem]:pb-0" data-testid="home-highlight-hero">
                        <GadgetThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 9" class="w-full">
                            <GadgetDebateBand v-if="first.post.debate" :debate="first.post.debate" size="lg" />
                        </GadgetThumb>
                        <p class="gadget-hover-title mt-3.5 line-clamp-2 break-words text-lg font-bold leading-snug tracking-tight @min-[40rem]:text-xl">{{ titleOf(first) }}</p>
                        <p v-if="first.summary" class="mt-1.5 line-clamp-1 text-sm text-muted-color" data-testid="home-highlight-summary">{{ first.summary }}</p>
                        <GadgetHighlightMeta :post="first.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-2" />
                    </NuxtLink>
                </FoldedRow>
                <ul v-if="rest.length" class="m-0 flex list-none flex-col border-t border-surface p-0 @min-[40rem]:border-t-0" data-testid="home-highlight-list">
                    <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0 @min-[40rem]:first:-mt-3 @min-[40rem]:last:-mb-3">
                        <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                            <NuxtLink :to="`/posts/${h.post.id}`" class="gadget-hover flex items-center gap-3.5 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                                <!-- 사진 안 모서리 작은 라벨(사용자 2026-11-10 최종 — 사진을 덜 가리게): 오른쪽 위 = 찬반 색 숫자(`GadgetDebateBand` sm), 왼쪽 아래 = 게시판 이름 -->
                                <GadgetThumb v-if="cfg.thumbs === 'ALL'" :src="imageOf(h)" ratio="16 / 10" class="w-[5.5rem] shrink-0">
                                    <GadgetDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                                    <!-- 게시판 이름 라벨 — 게시판 표시색 그대로(말머리·게시판 칩과 같은 `tagStyle`: 같은 색 옅은 바탕 + 읽히게 보정한 글자색, 사용자 2026-11-10). 색이 없으면 흰 바탕 기본 글자 -->
                                    <span
                                        class="absolute bottom-[3px] left-[3px] max-w-[calc(100%-6px)] truncate rounded-[3px] px-1 text-[0.625rem] font-semibold leading-[14px]"
                                        :class="UtilBoard.tagStyle(h.post.board.color) ? '' : 'bg-surface-0 text-color dark:bg-surface-900'"
                                        :style="UtilBoard.tagStyle(h.post.board.color)"
                                        data-testid="home-highlight-board-label"
                                        >{{ localizedName(h.post.board.name, h.post.board.nameEn) }}</span
                                    >
                                </GadgetThumb>
                                <div class="min-w-0 flex-1">
                                    <!-- 제목(폭 전체, 두 줄 말줄임) → 아바타·닉네임 → 추천 → 댓글 · 오른쪽 끝 날짜(사용자 2026-11-10 최종 — 등급 뱃지는 뺀다) -->
                                    <p class="m-0 line-clamp-2 break-words text-sm font-semibold leading-snug">{{ titleOf(h) }}</p>
                                    <GadgetHighlightMeta :post="h.post" order="author" :board="cfg.thumbs !== 'ALL'" :debate="cfg.thumbs !== 'ALL'" :author="cfg.showAuthor" class="mt-1.5" />
                                </div>
                            </NuxtLink>
                        </FoldedRow>
                    </li>
                </ul>
            </div>
        </div>

        <template v-else-if="cfg.lead === 'HERO' && first">
            <FoldedRow :reason="foldOf(first)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                <NuxtLink
                    :to="`/posts/${first.post.id}`"
                    class="gadget-hover-photo grid gap-3 pb-4 pt-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary @min-[30rem]:gap-[1.125rem]"
                    :class="[cfg.thumbs === 'NONE' ? '' : '@min-[30rem]:grid-cols-[minmax(0,13rem)_minmax(0,1fr)]', rest.length ? 'border-b border-surface' : '']"
                    data-testid="home-highlight-hero"
                >
                    <GadgetThumb v-if="cfg.thumbs !== 'NONE'" :src="imageOf(first)" ratio="16 / 10" class="w-full">
                        <GadgetDebateBand v-if="first.post.debate" :debate="first.post.debate" size="lg" />
                    </GadgetThumb>
                    <div class="min-w-0">
                        <p class="gadget-hover-title line-clamp-3 break-words text-lg font-bold leading-snug tracking-tight @min-[30rem]:text-xl">{{ titleOf(first) }}</p>
                        <p v-if="first.summary" class="mt-2 line-clamp-3 text-sm leading-relaxed text-muted-color">{{ first.summary }}</p>
                        <GadgetHighlightMeta :post="first.post" :debate="cfg.thumbs === 'NONE'" :author="cfg.showAuthor" class="mt-2" />
                    </div>
                </NuxtLink>
            </FoldedRow>
            <ul v-if="rest.length" class="m-0 grid list-none p-0 @min-[36rem]:grid-cols-2 @min-[36rem]:gap-x-6" data-testid="home-highlight-list">
                <li v-for="h in rest" :key="h.id" class="border-b border-surface last:border-b-0">
                    <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                        <NuxtLink :to="`/posts/${h.post.id}`" class="gadget-hover flex items-start gap-3 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                            <div class="min-w-0 flex-1">
                                <p class="line-clamp-2 break-words text-[0.9375rem] leading-snug">{{ titleOf(h) }}</p>
                                <GadgetHighlightMeta :post="h.post" :debate="!(cfg.thumbs === 'ALL' && imageOf(h))" :author="cfg.showAuthor" class="mt-1" />
                            </div>
                            <GadgetThumb v-if="cfg.thumbs === 'ALL' && imageOf(h)" :src="imageOf(h)" ratio="3 / 2" class="w-24 shrink-0">
                                <GadgetDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                            </GadgetThumb>
                        </NuxtLink>
                    </FoldedRow>
                </li>
            </ul>
        </template>

        <!-- 행 — 사진 왼쪽 + 굵은 제목 + 요약 + 메타 -->
        <ul v-else class="m-0 list-none p-0" data-testid="home-highlight-list">
            <li v-for="(h, i) in picks" :key="h.id" class="border-b border-surface last:border-b-0">
                <FoldedRow :reason="foldOf(h)" kind="post" class="py-3 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${h.post.id}`" class="gadget-hover flex items-start gap-3 py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:gap-3.5">
                        <GadgetThumb v-if="rowThumb(i)" :src="imageOf(h)" ratio="132 / 86" class="w-24 shrink-0 md:w-[8.25rem]">
                            <GadgetDebateBand v-if="h.post.debate" :debate="h.post.debate" />
                        </GadgetThumb>
                        <div class="min-w-0 flex-1">
                            <p class="line-clamp-2 break-words text-base font-bold leading-snug tracking-tight md:text-lg">{{ titleOf(h) }}</p>
                            <p v-if="h.summary" class="mt-0.5 hidden truncate text-sm text-muted-color @min-[28rem]:block" data-testid="home-highlight-summary">{{ h.summary }}</p>
                            <GadgetHighlightMeta :post="h.post" :debate="!rowThumb(i)" :author="cfg.showAuthor" class="mt-1" />
                        </div>
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget, HomeHighlight } from '~/composables/gadget/api/useGadgetApi.types';
import UtilGadget from '~/utils/util-gadget';
import UtilBoard from '~/utils/util-board';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();

const overrides = useBlockOverrides();
const options = useGadgetLookOptions();
/** 큰 글 왼쪽 + 나머지 오른쪽 목록 — 템플릿이 고른 세부 모양(기본 false) */
const split = options.heroSplit;
/** 한 열(모바일)이고 템플릿이 골랐을 때만 — 나머지 글은 가로로 미는 카드 줄 · 큰 글 사진은 카드 끝까지 */
const stacked = useGadgetStacked();
const restScroll = computed(() => stacked.value && options.mobile.highlightScroll);
/** 슬라이드 한 장 폭 — 다음 장이 조금 보여 밀 수 있다는 걸 알린다 */
const SLIDE = { '--row-item': '88%' };

const cfg = computed(() => UtilGadget.highlightConfig(props.gadget.config));
/** 좁은 칸을 「큰 글 + 글자 줄」로(`narrow: LIST`) — 기본은 위아래 한 열 */
const narrowList = computed(() => cfg.value.narrow === 'LIST');
const picks = computed(() => UtilGadget.highlights(props.gadget.data));
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
