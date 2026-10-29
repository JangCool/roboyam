<template>
    <!--
        게시판 레이아웃 격자(2026-10-30) — `BoardLayoutFrame` 이 레이아웃을 쓰기로 했을 때만 그린다. 지금 테마의 홈 템플릿(`UtilTheme.resolve(theme, 'home')` — 겉모양·가젯 모양)이 공통 격자 `HomeGadgetGrid` 로 그리고,
        고정 칸 「게시판 본문」(`BOARD_BODY`) 자리에는 기본 슬롯(게시판 목록·글 보기 화면 내용)을 그대로 넘긴다(`provideBoardLayoutBody` — 템플릿을 사이에 두고 닿게).
        본문 안의 테마 겉모양(`content-card` 등)은 화면 그대로이고, 둘레 가젯은 그 테마의 홈 가젯 꾸미기를 따른다. 데이터는 받기만 한다(조회는 `BoardLayoutFrame`).
        둘레 가젯에 투자 고지 게시판 글이 보이면 맨 아래에 고지를 붙인다(지금 게시판이 투자 고지 게시판이면 본문이 이미 붙인다).
    -->
    <div class="flex flex-col gap-4" data-testid="board-layout">
        <component :is="template" :layout="layout" :gadgets="gadgets" />
        <BoardInvestmentNotice v-if="hasInvestment" variant="home" data-testid="board-layout-investment" />
    </div>
</template>

<script setup lang="ts">
import type { HomeLayout } from '#layers/home-layout/utils/util-home-layout';
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import { BOARD_BODY } from '~/utils/util-admin-preview';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilTheme from '~/utils/util-theme';

interface Props {
    /** 레이아웃(읽은 값 — `UtilHomeGadget.sanitize`) */
    layout: HomeLayout;
    /** 가젯(본문 칸 포함) */
    gadgets: HomeGadget[];
    /** 지금 보는 게시판 slug */
    board: string;
}
const props = withDefaults(defineProps<Props>(), {});

const slots = useSlots();
const { theme } = useTheme();
const { bySlug } = useBoards();

// 가젯들이 나눠 쓰는 것(폭·지금 시각) · 본문 칸에 그릴 화면 내용
provideHomeGadgetShared();
provideBoardLayoutBody(() => slots.default?.());

/** 지금 테마의 홈 템플릿(없으면 기본 테마 것) — 게시판 레이아웃도 홈과 같은 겉모양 */
const template = computed(() => UtilTheme.resolve(theme.value, 'home'));

const hasInvestment = computed(() => {
    if (bySlug(props.board)?.investmentNotice === true) return false;
    const around = props.gadgets.filter((g) => g.type !== BOARD_BODY);
    return UtilHomeGadget.hasInvestmentBoard({ theme: '', layout: props.layout, gadgets: around }, (slug) => bySlug(slug)?.investmentNotice === true);
});
</script>
