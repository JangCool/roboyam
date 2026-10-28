<template>
    <!--
        게시판 카드 묶음 가젯(`BOARD_CARDS`) — 게시판마다 카드 하나(로고 + 이름 → 대표 글 사진·제목 → 최신 제목들 → 「게시판 가기 ›」). 네이버 언론사 카드를 본뜬 모양(community `GadgetBoardCards`).
        카드는 서버가 준 순서이고 글이 없는 게시판도 카드는 나온다. 열 수는 가젯 칸 폭을 따른다(`AUTO`: 1열 → 2열 → 넓으면 3열, 설정 `2`·`3` 은 그 열까지).
        한 열(모바일) + 템플릿이 `mobile.boardCardsScroll` 을 고르면(네이버형) 격자 대신 가로로 미는 카드 줄(`gadget-scroll-row` — 한 칸 84%, 줄 안 카드는 둥근 모서리).
        산업·주식(투자 고지) 게시판 카드에는 고지 한 줄이 늘 붙는다 — 카드의 `board.investmentNotice`(slug 로 짐작하지 않는다).
        로고 색: 자동은 운영자가 고른 게시판 표시색, 없으면 순서로 정한 색(`UtilBoard.toneAt`) · 고정 색 · 회색. 로고에는 글자가 늘 있다(색만으로 뜻을 전하지 않는다).
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || cards.length === 0" empty-icon="pi pi-th-large">
        <div
            :class="scroll ? 'gadget-scroll-row' : ['grid grid-cols-1 gap-2.5 md:gap-3.5', colsClass]"
            :style="scroll ? { ...fixedVars, '--row-item': '84%' } : fixedVars"
            :role="scroll ? 'group' : undefined"
            :aria-label="scroll ? t('home.scrollRowLabel', { name: gadget.title }) : undefined"
            data-testid="home-board-cards"
        >
            <article v-for="(card, i) in cards" :key="card.board.slug" class="flex min-w-0 flex-col" :class="innerCard" :aria-label="nameOf(card)" data-testid="home-board-card">
                <header class="mb-3 flex items-center gap-2">
                    <span class="grid size-8 shrink-0 place-items-center rounded-full text-xs font-extrabold" :class="logoClass(i, card)" :style="logoStyle(card)" aria-hidden="true">{{ UtilBoard.iconText(nameOf(card)) }}</span>
                    <NuxtLink :to="`/boards/${card.board.slug}`" class="min-w-0 flex-1 truncate rounded text-[0.9375rem] font-extrabold hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ nameOf(card) }}</NuxtLink>
                </header>

                <FoldedRow v-if="card.lead" :reason="foldOf(card.lead)" kind="post" class="mb-2 py-2 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${card.lead.id}`" class="mb-2 block rounded focus-visible:outline-2 focus-visible:outline-primary" data-testid="home-board-lead">
                        <HomeThumb :src="card.lead.thumbnailUrl" ratio="16 / 9" class="w-full" />
                        <p class="mt-1.5 line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug">{{ card.lead.title }}</p>
                    </NuxtLink>
                </FoldedRow>

                <ul v-if="card.items.length" class="m-0 list-none p-0">
                    <li v-for="row in card.items" :key="row.id" class="border-t border-surface first:border-t-0">
                        <FoldedRow :reason="foldOf(row)" kind="post" class="py-2 text-sm" hide-class="pb-2">
                            <NuxtLink :to="`/posts/${row.id}`" class="flex items-baseline gap-1 rounded py-2 text-sm hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                                <span class="min-w-0 flex-1 truncate">{{ row.title }}</span>
                                <span v-if="row.commentCount > 0" class="shrink-0 text-xs font-bold text-primary">
                                    <span class="sr-only">{{ t('home.commentCount', { n: row.commentCount }) }}</span><span aria-hidden="true">{{ row.commentCount }}</span>
                                </span>
                            </NuxtLink>
                        </FoldedRow>
                    </li>
                </ul>
                <p v-else-if="!card.lead" class="m-0 py-4 text-center text-xs text-muted-color">{{ t('home.boardEmpty') }}</p>

                <footer class="mt-auto flex items-center justify-between gap-2 border-t border-surface pt-2.5 text-xs text-muted-color">
                    <span v-if="card.board.investmentNotice" class="min-w-0 text-color" data-testid="home-board-stock-note"><i class="pi pi-exclamation-triangle mr-1 text-[0.6875rem]" aria-hidden="true" />{{ t('home.boardStockNote') }}</span>
                    <span v-else />
                    <NuxtLink
                        :to="`/boards/${card.board.slug}`"
                        class="shrink-0 whitespace-nowrap rounded hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="t('home.boardGoLabel', { name: nameOf(card) })"
                    >
                        {{ t('home.boardGo') }} ›
                    </NuxtLink>
                </footer>
            </article>
        </div>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { BoardCard, HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const look = useHomeGadgetLook();
const { localizedName } = useLocalizedName();
const overrides = useBlockOverrides();

const cfg = computed(() => UtilHomeGadget.boardCardsConfig(props.gadget.config));
const cards = computed(() => UtilHomeGadget.boardCards(props.gadget.data));

/** 열 수 — 가젯 칸(`@container`) 폭으로. 2열은 34rem, 3열은 54rem 이상 */
const colsClass = computed(() => (cfg.value.columns === 2 ? '@min-[34rem]:grid-cols-2' : '@min-[34rem]:grid-cols-2 @min-[54rem]:grid-cols-3'));

/** 가로로 미는 카드 줄 — 한 열(모바일)이고 템플릿이 골랐을 때만 */
const stacked = useHomeGadgetStacked();
const mobile = useHomeGadgetLookOptions().mobile;
const scroll = computed(() => stacked.value && mobile.boardCardsScroll);

/** 안쪽 카드 — 바깥이 평평한 카드형이면 카드 한 장 한 장이 테마 카드, 그 밖은 테두리 상자 */
const innerCard = computed(() => (look === 'card' && props.gadget.style.cardLook === 'FLAT' ? 'gadget-card' : 'rounded-lg border border-surface p-3'));

function nameOf(card: BoardCard): string {
    return localizedName(card.board.name, card.board.nameEn);
}

/** 고정 색 변수 — 로고 바탕(`--gx-bc`)과 그 위 글자색(`--gx-bc-on`) */
const fixedVars = computed(() => {
    const vars: Record<string, string> = UtilHomeGadget.colorVar('--gx-bc', cfg.value.badge.color);
    const on = UtilColor.cssOnPair(cfg.value.badge.color);
    if (on) vars['--gx-bc-on'] = on;
    return vars;
});

function logoStyle(card: BoardCard): Record<string, string> | undefined {
    const mode = cfg.value.badge.mode;
    if (mode === 'FIXED') return { background: 'var(--gx-bc)', color: 'var(--gx-bc-on)' };
    if (mode === 'NEUTRAL') return undefined;
    const dot = UtilBoard.dotStyle(card.board.color);
    const on = UtilColor.cssOnPair(UtilBoard.presetColor(card.board.color));
    return dot && on ? { ...dot, color: on } : undefined;
}

function logoClass(index: number, card: BoardCard): string {
    const mode = cfg.value.badge.mode;
    if (mode === 'FIXED') return '';
    if (mode === 'NEUTRAL') return 'bg-surface-600 text-surface-0 dark:bg-surface-500';
    if (UtilBoard.presetColor(card.board.color)) return '';
    return `${UtilBoard.dotClass(UtilBoard.toneAt(index))} text-surface-0`;
}

function foldOf(row: PostFeedRow) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}
</script>
