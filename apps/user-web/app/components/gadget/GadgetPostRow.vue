<template>
    <!--
        홈 글 목록 가젯의 글 한 줄(community `GadgetPostRow` + `GadgetRowMeta`·`GadgetRowTags`) — 줄 모양 5종(헤드라인·순위·기사·촘촘·뉴스형 한 줄)·사진 자리(오른쪽/왼쪽/없음)·표시 칸(게시판·말머리·댓글·시간·작성자·추천)·
        게시판 이름 색(자동 = 게시판 표시색 · 고정 색 · 회색)·순위 번호와 색·제목 줄 수·댓글 수(제목 뒤 숫자 / 기사형 말풍선)·NEW/HOT·구분선. `hero` 면 첫 글을 크게(큰 사진 + 제목),
        `leadThumb` 면 첫 글을 작은 사진 줄로(`lead: THUMB` — 왼쪽 작은 사진 + 제목 + 메타, 뉴스형 한 줄 목록이면 큰 사진 + 제목 줄 날짜 + 본문 앞부분 두 줄, home-news-contract).
        추천 표시(`likes`, 줄 정보 「추천 수」를 켰을 때): 글자 「추천 n」 · 엄지 아이콘 + 숫자(`GadgetHighlightMeta` 추천만) · 제목 앞 고정 폭 칸(촘촘·뉴스형 한 줄만, 그 밖은 아이콘).
        줄 간격(`rowSpacing`): 두 줄 줄(헤드라인·기사·순위)의 위아래 여백 보통 10px · 좁게 7px.
        차단한 회원·단어의 글은 다른 목록과 같은 접힌 줄(`FoldedRow`) — 순위 칸은 남겨 순서가 밀리지 않는다. 줄 전체가 글 링크라 작성자 이름은 링크가 아니다(`<a>` 중첩 금지).
        마우스 올림 효과(설정 `hover`, `main.css`): 보통 줄은 옅은 바탕(`gadget-hover` — 좌우로 8px 넓힌 바탕이 순위 칸에 조금 걸려 순위 번호는 `relative` 로 바탕 위에), 큰 글은 제목 밑줄.
    -->
    <li :class="[cfg.dividers ? 'border-b border-surface last:border-b-0' : '', rank !== null ? 'flex items-start' : '']" :style="rowVars" data-testid="home-post-row">
        <span v-if="rank !== null" class="relative w-7 shrink-0 text-center font-extrabold leading-snug tabular-nums" :class="[rankSize, rank > 3 ? 'text-muted-color' : '', rankPad]" :style="rankStyle" data-testid="home-post-rank">
            <span aria-hidden="true">{{ rank }}</span><span class="sr-only">{{ t('feed.rank', { n: rank }) }}</span>
        </span>
        <div class="min-w-0 flex-1">
            <FoldedRow :reason="foldReason" kind="post" class="py-2.5 text-sm" hide-class="pb-2">
                <!-- 첫 글을 크게 -->
                <NuxtLink v-if="hero" :to="`/posts/${row.id}`" class="gadget-hover-photo block pb-3 pt-1 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="home-post-hero">
                    <GadgetThumb :src="row.thumbnailUrl" ratio="16 / 9" class="w-full" />
                    <p class="gadget-hover-title mt-2 line-clamp-2 break-words text-base font-bold leading-snug">
                        <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" inline :class="prefixClass" />
                        <span>{{ row.title }}</span>
                    </p>
                    <p class="mt-1 flex flex-wrap items-center gap-x-1 gap-y-0.5 text-xs text-muted-color">
                        <template v-for="(part, i) in metaParts" :key="part">
                            <span v-if="i > 0" aria-hidden="true">·</span>
                            <span v-if="part === 'board'" class="font-semibold" :class="boardClass" :style="boardStyle">{{ boardName }}</span>
                            <PostAuthorLabel v-else-if="part === 'author'" :author="row.author" avatar menu />
                            <GadgetHighlightMeta v-else-if="part === 'likes' && likesIcon" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" />
                            <span v-else-if="part === 'likes'">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                            <span v-else-if="part === 'comments'">{{ t('home.commentCount', { n: row.commentCount }) }}</span>
                            <PostListDate v-else-if="part === 'time'" :iso="row.createdAt" :mode="display.dateDisplay" />
                        </template>
                    </p>
                </NuxtLink>

                <!-- 첫 글 작은 사진 줄(`lead: THUMB`). 뉴스형 한 줄 목록(`LINE`)은 사진을 글 세 줄(제목 + 본문 앞부분 두 줄) 높이로 키우고(112px) 제목 줄 = [말머리] 굵은 제목 댓글 수 …… [추천] [날짜], 아래 메타 줄은 두지 않는다 -->
                <NuxtLink v-else-if="leadThumb" :to="`/posts/${row.id}`" class="gadget-hover flex items-center py-0.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" :class="rowStyle === 'LINE' ? 'gap-4' : 'gap-3'" data-testid="home-post-lead-thumb">
                    <GadgetThumb :src="row.thumbnailUrl" ratio="16 / 10" class="shrink-0" :class="rowStyle === 'LINE' ? 'w-28 rounded-lg' : 'w-20 rounded-md'" />
                    <div v-if="rowStyle === 'LINE'" class="min-w-0 flex-1">
                        <div class="flex items-center gap-2">
                            <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" mode="TEXT" class="shrink-0 text-sm! font-bold!" />
                            <span v-else-if="cfg.meta.includes('BOARD')" class="shrink-0 text-xs font-semibold" :class="boardClass" :style="boardStyle">{{ boardName }}</span>
                            <span class="min-w-0 truncate text-base font-bold leading-snug tracking-tight">{{ row.title }}</span>
                            <span v-if="commentNumber" class="-ml-1 shrink-0 text-xs font-semibold text-primary"><span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ row.commentCount }}</span></span>
                            <span v-if="showLikes || timeShown" class="ml-auto flex shrink-0 items-center gap-2 text-xs text-muted-color">
                                <GadgetHighlightMeta v-if="showLikes && likesShape !== 'TEXT'" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" />
                                <span v-else-if="showLikes">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                <PostListDate v-if="timeShown" :iso="row.createdAt" :mode="display.dateDisplay" />
                            </span>
                        </div>
                        <p v-if="row.excerpt" class="m-0 mt-1.5 line-clamp-2 text-[0.8125rem] leading-snug text-muted-color" data-testid="home-post-lead-excerpt">{{ row.excerpt }}</p>
                    </div>
                    <div v-else class="min-w-0 flex-1">
                        <p class="line-clamp-2 break-words font-semibold leading-snug" :class="look === 'card' ? 'text-[0.9375rem]' : 'text-sm'">
                            <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" inline :class="prefixClass" />
                            <span>{{ row.title }}</span>
                            <span v-if="commentNumber" class="ml-1 text-xs font-semibold text-primary">
                                <span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ look === 'card' ? row.commentCount : `[${row.commentCount}]` }}</span>
                            </span>
                        </p>
                        <p v-if="metaParts.length" class="mt-1 flex flex-wrap items-center gap-x-1 gap-y-0.5 text-xs text-muted-color">
                            <template v-for="(part, i) in metaParts" :key="part">
                                <span v-if="i > 0" aria-hidden="true">·</span>
                                <span v-if="part === 'board'" class="font-semibold" :class="boardClass" :style="boardStyle">{{ boardName }}</span>
                                <PostAuthorLabel v-else-if="part === 'author'" :author="row.author" menu />
                                <GadgetHighlightMeta v-else-if="part === 'likes' && likesIcon" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" />
                                <span v-else-if="part === 'likes'">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                <PostListDate v-else-if="part === 'time'" :iso="row.createdAt" :mode="display.dateDisplay" />
                            </template>
                        </p>
                    </div>
                </NuxtLink>

                <!-- 뉴스형 한 줄(`LINE`) — [추천 고정 칸(`likes: LEAD`)] [말머리 색 글자(칩 배경 없음) — 글자 3자 고정 폭, 넘치면 …(말머리가 없으면 게시판 이름)] 한 줄 제목 + 댓글 수 …… [추천] [날짜](오른쪽 끝 — 켠 것만) -->
                <NuxtLink v-else-if="rowStyle === 'LINE'" :to="`/posts/${row.id}`" class="gadget-hover flex items-center gap-2 py-1 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="home-post-line">
                    <GadgetHighlightMeta v-if="likesLead" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" class="w-10 shrink-0" data-testid="home-post-likes-lead" />
                    <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" mode="TEXT" class="inline-block w-[3em] shrink-0 truncate font-semibold!" :title="row.prefix!.name" />
                    <span v-else-if="cfg.meta.includes('BOARD')" class="shrink-0 text-xs font-semibold" :class="boardClass" :style="boardStyle">{{ boardName }}</span>
                    <span v-if="isNew" class="inline-flex shrink-0 items-center justify-center rounded bg-primary px-1.5 py-1 text-[0.625rem] font-bold leading-none text-primary-contrast" data-testid="home-post-new">
                        <span class="relative top-[-0.09em]" aria-hidden="true">{{ t('home.newMark') }}</span><span class="sr-only">{{ t('home.newLabel') }}</span>
                    </span>
                    <span v-if="isHot" class="inline-flex shrink-0 items-center justify-center rounded bg-red-600 px-1.5 py-1 text-[0.625rem] font-bold leading-none text-white dark:bg-red-500" data-testid="home-post-hot">
                        <span class="relative top-[-0.09em]" aria-hidden="true">{{ t('home.hotMark') }}</span><span class="sr-only">{{ t('home.hotLabel') }}</span>
                    </span>
                    <span class="min-w-0 truncate leading-snug" :class="look === 'card' ? 'text-[0.9375rem]' : 'text-sm'">{{ row.title }}</span>
                    <span v-if="commentNumber" class="-ml-1 shrink-0 text-xs font-semibold text-primary"><span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ look === 'card' ? row.commentCount : `[${row.commentCount}]` }}</span></span>
                    <span v-if="(showLikes && !likesLead) || timeShown" class="ml-auto flex shrink-0 items-center gap-2 text-xs text-muted-color">
                        <GadgetHighlightMeta v-if="showLikes && !likesLead && likesIcon" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" class="w-10 justify-end" />
                        <span v-else-if="showLikes && !likesLead">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                        <PostListDate v-if="timeShown" :iso="row.createdAt" :mode="display.dateDisplay" class="min-w-11 text-right" />
                    </span>
                </NuxtLink>

                <!-- 보통 줄 -->
                <NuxtLink v-else :to="`/posts/${row.id}`" class="gadget-hover flex items-start gap-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" :class="rowPad">
                    <!-- 줄 사진은 글 두 줄(제목 + 메타) 높이를 넘지 않는다(64×40, 2026-11-06 사용자) -->
                    <GadgetThumb v-if="thumb === 'LEFT'" :src="row.thumbnailUrl" ratio="16 / 10" class="w-16 shrink-0" />
                    <!-- 촘촘(`COMPACT`)은 한 줄 — 제목 오른쪽에 메타(게시판·추천·시간)를 나란히 둔다(좁은 칸에 글 10개가 들어가게, 2026-11-06).
                         인기글(기간 탭, `dense`)만 한 줄 약 27px(글자 13px · 줄 높이 1.3 · 위아래 5px) — 하이라이트 가젯 높이에 10개가 들어가고, 덜 찬 쪽(11~15)도 줄 간격이 같다. 그 밖의 촘촘 목록은 위아래 4px(2026-11-06 사용자) -->
                    <div class="min-w-0 flex-1" :class="rowStyle === 'COMPACT' ? 'flex items-baseline gap-2' : ''">
                        <!-- 추천 제목 앞 고정 칸(`likes: LEAD` — 엄지 아이콘 + 숫자, 줄마다 같은 자리) -->
                        <GadgetHighlightMeta v-if="likesLead" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" class="w-10 shrink-0" data-testid="home-post-likes-lead" />
                        <p class="break-words leading-snug" :class="[lines === 1 ? 'line-clamp-1' : 'line-clamp-2', titleSize, rowStyle === 'COMPACT' ? 'min-w-0 flex-1' : '']">
                            <PostPrefixTag v-if="prefixShown" :prefix="row.prefix!" inline :class="prefixClass" />
                            <span v-if="isNew" class="mr-1 inline-flex items-center justify-center rounded bg-primary px-1.5 py-1 align-[1px] text-[0.625rem] font-bold leading-none text-primary-contrast" data-testid="home-post-new">
                                <span class="relative top-[-0.09em]" aria-hidden="true">{{ t('home.newMark') }}</span><span class="sr-only">{{ t('home.newLabel') }}</span>
                            </span>
                            <span v-if="isHot" class="mr-1 inline-flex items-center justify-center rounded bg-red-600 px-1.5 py-1 align-[1px] text-[0.625rem] font-bold leading-none text-white dark:bg-red-500" data-testid="home-post-hot">
                                <span class="relative top-[-0.09em]" aria-hidden="true">{{ t('home.hotMark') }}</span><span class="sr-only">{{ t('home.hotLabel') }}</span>
                            </span>
                            <span>{{ row.title }}</span>
                            <span v-if="commentNumber" class="ml-1 text-xs font-semibold text-primary">
                                <span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ look === 'card' ? row.commentCount : `[${row.commentCount}]` }}</span>
                            </span>
                        </p>
                        <p v-if="metaParts.length || bubble" class="flex items-center gap-x-1 gap-y-0.5 text-xs text-muted-color" :class="rowStyle === 'COMPACT' ? 'shrink-0 flex-nowrap' : 'mt-1 flex-wrap'">
                            <template v-for="(part, i) in metaParts" :key="part">
                                <span v-if="i > 0" aria-hidden="true">·</span>
                                <span v-if="part === 'board'" class="shrink-0 font-semibold" :class="boardClass" :style="boardStyle">{{ boardName }}</span>
                                <PostAuthorLabel v-else-if="part === 'author'" :author="row.author" :avatar="rowStyle !== 'COMPACT'" menu />
                                <GadgetHighlightMeta v-else-if="part === 'likes' && likesIcon" tag="span" :post="row" order="author" :board="false" :comments="false" :author="false" :date="false" class="shrink-0" />
                                <span v-else-if="part === 'likes'" class="shrink-0">{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                <PostListDate v-else-if="part === 'time'" :iso="row.createdAt" :mode="display.dateDisplay" class="shrink-0" />
                            </template>
                            <span v-if="bubble" class="ml-auto inline-flex shrink-0 items-baseline gap-1" data-testid="home-post-bubble">
                                <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ row.commentCount }}</span>
                            </span>
                        </p>
                    </div>
                    <GadgetThumb v-if="thumb === 'RIGHT' && row.thumbnailUrl" :src="row.thumbnailUrl" ratio="16 / 10" class="w-16 shrink-0" />
                </NuxtLink>
            </FoldedRow>
        </div>
    </li>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PostListConfig } from '~/composables/gadget/api/useGadgetApi.types';
import UtilBoard from '~/utils/util-board';
import UtilGadget from '~/utils/util-gadget';
import UtilPost from '~/utils/util-post';

interface Props {
    row: PostFeedRow;
    /** 글 목록 설정(정규화한 것) */
    cfg: PostListConfig;
    /** 순위 번호(1부터) — null 이면 번호 칸이 없다 */
    rank?: number | null;
    /** 첫 글을 크게 */
    hero?: boolean;
    /** 촘촘 줄을 더 좁게(인기글 기간 탭 — 하이라이트 높이에 10개) */
    dense?: boolean;
    /** 첫 글 작은 사진 줄(`lead: THUMB` — 쪽 넘기기면 첫 쪽의 첫 글만) */
    leadThumb?: boolean;
}

const props = withDefaults(defineProps<Props>(), { rank: null, hero: false, dense: false, leadThumb: false });

const { t } = useI18n();
const look = useGadgetLook();
const { now } = useGadgetShared();
const { display } = useBoardDisplay();
/**
 * 말머리 모양 — 가젯 글 줄은 섞인 목록과 같이 사이트 기본(`boardDisplay.listPrefixStyle`)을 따른다(mobile-nav-prefix-style-contract 1절, 2026-11-11 사용자 결정).
 * 게시판 화면 옆에 놓인 가젯도 그 게시판 값이 아니라 사이트 기본이 되게 아래 말머리에 건다. 글자 모양은 칩이 아니라 기준선 맞춤 없이 오른쪽 여백만
 */
const prefixStyle = computed(() => UtilBoard.prefixStyle(display.value.listPrefixStyle));
providePrefixStyle(() => prefixStyle.value);
const prefixClass = computed(() => (prefixStyle.value === 'TEXT' ? 'mr-1' : 'mr-1 align-[1px]'));
const { localizedName } = useLocalizedName();
const overrides = useBlockOverrides();

const rowStyle = computed(() => props.cfg.rowStyle);
/** 제목·순위 글자 크기 — 인기글 촘촘(`dense`)은 한 단계 작게(카드 테마 15px → 14px, 그 밖 14px → 13px) */
const titleSize = computed(() => {
    if (rowStyle.value === 'COMPACT' && props.dense) return look === 'card' ? 'text-sm leading-[1.3]!' : 'text-[0.8125rem] leading-[1.3]!';
    return look === 'card' ? 'text-[0.9375rem]' : 'text-sm';
});
const rankSize = computed(() => {
    if (rowStyle.value === 'COMPACT' && props.dense) return look === 'card' ? 'text-[0.9375rem]' : 'text-[0.8125rem]';
    return look === 'card' ? 'text-[1.0625rem]' : 'text-sm';
});
const thumb = computed(() => UtilGadget.thumbPlacement(props.cfg));
const lines = computed(() => UtilGadget.titleLineCount(props.cfg));
const prefixShown = computed(() => props.cfg.meta.includes('PREFIX') && props.row.prefix !== null);
const isNew = computed(() => props.cfg.newBadgeHours > 0 && UtilPost.isNew(props.row.createdAt, props.cfg.newBadgeHours, now.value));
const isHot = computed(() => UtilGadget.isHot(props.row.likeCount, props.cfg.hotBadgeLikes));

/** 댓글 수 — 표시 칸에 「댓글」이 있고 1개 이상일 때. 기사형은 메타 줄 끝 말풍선, 나머지는 제목 뒤 숫자 */
const commentShown = computed(() => props.cfg.meta.includes('COMMENTS') && props.row.commentCount > 0);
const bubble = computed(() => commentShown.value && rowStyle.value === 'ARTICLE');
const commentNumber = computed(() => commentShown.value && rowStyle.value !== 'ARTICLE');

/** 추천 수 — 글 줄 정보 옵션 `LIKES`(사용자 2026-11-14, 예전에는 순위 모양·추천 많은 순·인기 정렬이면 저절로 붙었다 — 기존 가젯은 shared V210 이 옵션으로 옮겼다) */
const showLikes = computed(() => props.cfg.meta.includes('LIKES'));
/** 추천 표시 모양 — 글자 · 아이콘 · 제목 앞 고정 칸(촘촘·뉴스형 한 줄만, 그 밖은 아이콘 — `UtilGadget.likesShape`) */
const likesShape = computed(() => UtilGadget.likesShape(props.cfg));
/** 추천을 엄지 아이콘 + 숫자로(`ICON`, 그리고 고정 칸이 없는 줄(큰 글·첫 글 사진 줄)의 `LEAD`) */
const likesIcon = computed(() => likesShape.value !== 'TEXT');
/** 추천을 제목 앞 고정 칸에 — 보통 줄·뉴스형 한 줄만(큰 글·첫 글 사진 줄은 메타 자리에 아이콘). 그러면 메타 줄·오른쪽 끝에는 두지 않는다 */
const likesLead = computed(() => showLikes.value && likesShape.value === 'LEAD' && !props.hero && !props.leadThumb);
/** 뉴스형 한 줄(`LINE`)의 날짜 — 표시 칸에 「시간」이 있을 때 오른쪽 끝 */
const timeShown = computed(() => props.cfg.meta.includes('TIME'));

/** 보통 줄 위아래 여백 — 촘촘은 4px(인기글 기간 탭 5px), 두 줄 줄은 10px · 줄 간격 좁게(`rowSpacing: TIGHT`) 7px */
const rowPad = computed(() => {
    if (rowStyle.value === 'COMPACT') return props.dense ? 'py-[5px]' : 'py-1';
    return props.cfg.rowSpacing === 'TIGHT' ? 'py-[7px]' : 'py-2.5';
});
/** 순위 번호 위 여백 — 줄 첫 글자와 같은 높이(위 `rowPad` 와 같은 값) */
const rankPad = computed(() => {
    if (rowStyle.value === 'COMPACT') return props.dense ? 'pt-[5px] leading-[1.3]!' : 'pt-1';
    if (rowStyle.value === 'LINE') return 'pt-1';
    return props.cfg.rowSpacing === 'TIGHT' ? 'pt-[7px]' : 'pt-2.5';
});

/** 메타 줄 조각(순서대로) — 큰 글은 댓글 수를 메타 줄에 둔다 */
const metaParts = computed(() => {
    const out: ('board' | 'author' | 'likes' | 'comments' | 'time')[] = [];
    if (props.cfg.meta.includes('BOARD')) out.push('board');
    if (props.cfg.meta.includes('AUTHOR')) out.push('author');
    if (showLikes.value && !likesLead.value) out.push('likes'); // 제목 앞 고정 칸(`likes: LEAD`)이면 메타 줄에는 두지 않는다
    if (props.hero && commentShown.value) out.push('comments');
    if (props.cfg.meta.includes('TIME')) out.push('time');
    return out;
});

const boardName = computed(() => localizedName(props.row.board.name, props.row.board.nameEn));
/** 게시판 이름 색 — 자동은 운영자가 고른 표시색(없으면 본문 글자색), 고정은 고른 색(`--gx-bc`), 회색은 흐린 글자 */
const boardStyle = computed(() => {
    const mode = props.cfg.badge.mode;
    if (mode === 'FIXED') return { color: 'var(--gx-bc)' };
    if (mode === 'AUTO') return UtilBoard.textStyle(props.row.board.color);
    return undefined;
});
const boardClass = computed(() => {
    if (props.cfg.badge.mode === 'NEUTRAL') return 'text-muted-color';
    if (props.cfg.badge.mode === 'AUTO' && !UtilBoard.textStyle(props.row.board.color)) return 'text-color';
    return '';
});

/** 색 변수 — 게시판 이름 고정 색(`--gx-bc`)과 순위 색(`--gx-rk`) */
const rowVars = computed(() => ({ ...UtilGadget.colorVar('--gx-bc', props.cfg.badge.color), ...UtilGadget.colorVar('--gx-rk', props.cfg.rank.color) }));
/** 1~3위 강조색 — 고른 순위 색이 있으면 그것, 없으면 주 색상 */
const rankStyle = computed(() => {
    if (props.rank === null || props.rank > 3) return undefined;
    return { color: props.cfg.rank.color !== null ? 'var(--gx-rk)' : 'var(--p-primary-color)' };
});

/** 접는 까닭 — 서버의 `blocked` 에 이 기기에서 방금 바꾼 차단을 겹친다 */
const foldReason = computed(() => overrides.blockedOf(props.row.author.memberId, props.row.blocked));
</script>
