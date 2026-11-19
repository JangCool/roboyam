<template>
    <span class="inline-flex min-w-0 items-center [text-decoration:inherit]">
        <!--
            메뉴 항목·그룹 이름 + 배지 — 상단 막대(`AppTopMenu`)·드롭다운(`AppTopMenuDropdown`)·세로 메뉴(`AppMenuList`)·모바일 메뉴 두 줄(`AppMobileMenuRows`)·그룹 이름(`AppMenuGroupLabel`)·
            다음·네이버 테마 막대(`DaumMenuBar`·`NaverMenuBar`)·모바일 상단 탭(`AppTopTabs`)·로고 옆 링크(`AppLogoLinks`)·푸터 고객 지원 링크(`AppFooter`)·게시판 모음(`/boards`)이 같이 쓴다.
            글자 색·굵게·배지 규칙은 `UtilMenu.textStyle`·`labelClass`·`badgeStyle` 한 곳(menu-contract 「메뉴 항목 글자 색·강조」) — 그룹(폴더)도 같은 칸을 받는다. 이름 앞 점(●)은 부르는 쪽이 그대로 그린다(게시판 표시색 — 이것과 따로).
            글자 색을 고르지 않은(기본) 이름은 감싼 줄의 색(흐린 그룹 제목·지금 있는 곳·고른 칸)을 그대로 따른다. 고른 색은 그 상태에서도 유지되고, 지금 있는 곳·펼침 표시는 줄의 밑줄·바탕·굵기가 맡는다.
            inline-flex 안에는 감싼 링크의 밑줄(그룹 이름 링크의 마우스 올림 밑줄)이 번지지 않는다 — 겉 상자와 이름 글자만 `text-decoration: inherit` 로 이어받고 배지는 받지 않는다.
        -->
        <!-- 이름 뒤 배지는 같은 줄 가운데에 세운다(components.md 2-4 — 부모 flex + items-center). min-w-0 이라 세로 메뉴에서 이름이 말줄임된다 -->
        <!--
            이름 그림(운영자 「이름 모양 › 로고 글꼴 그림」, achu-page-contract) — 로고 글꼴로 굳힌 패스를 글자 색(currentColor)으로 채운다. 화면 읽기는 이름 글자를 읽는다.
            영문명을 보이는 언어면 그림 대신 보통 글자. 그림은 이미 굵은 글꼴이라 「굵게」를 칠하지 않는다
        -->
        <span v-if="art" class="inline-flex shrink-0 items-center [text-decoration:inherit]" :class="nameClass" :style="UtilMenu.textStyle(node)" data-testid="menu-name-art">
            <svg :viewBox="`0 0 ${art.w} ${art.h}`" :style="{ height: `${art.em}em`, width: 'auto' }" class="block fill-current overflow-visible" aria-hidden="true" focusable="false"><path :d="art.d" stroke="currentColor" :stroke-width="art.sw ?? 0" stroke-linejoin="round" /></svg>
            <!-- 폭 없는 글자 한 칸 — 상자 높이를 글자 이름 칸과 같은 한 줄로 잡는다(활성 밑줄 `after:-bottom-*` 이 그림 바로 아래에 붙지 않고 다른 메뉴와 같은 자리에 오게) -->
            <span class="inline-block w-0" aria-hidden="true">&#8203;</span>
            <span class="sr-only">{{ node.label }}</span>
        </span>
        <span v-else class="[text-decoration:inherit]" :class="[truncate ? 'truncate' : '', keepWeight ? '' : UtilMenu.labelClass(node), nameClass]" :style="UtilMenu.textStyle(node)">{{ localizedName(node.label, node.nameEn) }}</span>
        <span v-if="node.badge" :class="compact ? UtilMenu.BADGE_COMPACT_CLASS : UtilMenu.BADGE_CLASS" :style="UtilMenu.badgeStyle(node)" data-testid="menu-badge">
            <span class="chip-label">{{ node.badge }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import type { MenuItemNode } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

interface Props {
    /** 메뉴 항목 또는 그룹(`MenuGroupNode` 도 같은 칸을 갖는다) */
    node: Pick<MenuItemNode, 'label' | 'nameEn' | 'textColor' | 'bold' | 'badge'> & Partial<Pick<MenuItemNode, 'nameArt'>>;
    /** 이름이 넘치면 말줄임(세로 메뉴·드롭다운). 가로 줄은 false */
    truncate?: boolean;
    /** 이름 글자에만 더할 클래스 — 배지는 받지 않는다(예: 다음 테마 활성 탭의 글자 폭 밑줄) */
    nameClass?: string;
    /** 감싼 줄이 이미 굵기를 정했으면 true — 「굵게」(600)를 칠하지 않아 더 굵은 줄(700·800)이 얇아지지 않는다(테마 메뉴 설정 「메뉴 글자 기본 굵게」가 켜진 막대 칸 전부·네이버 활성 탭) */
    keepWeight?: boolean;
    /** 좁은 칸(모바일 상단 탭·로고 옆 링크)의 작은 배지 — 줄 높이를 거의 늘리지 않는다 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    truncate: false,
    nameClass: '',
    keepWeight: false,
    compact: false,
});

// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();

/** 이름 그림 — 지금 언어가 한국어 이름(`label`)을 보일 때만(영문명을 보이면 보통 글자) */
const art = computed(() => {
    const node = props.node;
    if (!node.nameArt) return null;
    return localizedName(node.label, node.nameEn) === node.label ? node.nameArt : null;
});
</script>
