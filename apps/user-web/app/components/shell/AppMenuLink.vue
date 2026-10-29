<template>
    <!--
        메뉴 링크 하나 — 서버가 준 항목(`path`·`newTab`·`extra`)만 그린다(docs/api-menu.md 3-2 체크리스트 5). 왼쪽·오른쪽 메뉴·햄버거(`AppMenuList`) · 상단 가로 메뉴(`AppTopMenu`)와
        드롭다운(`AppTopMenuDropdown`) · 모바일 상단 탭(`AppTopTabs`)이 같은 이 부품을 쓴다.
        - 사이트 안 주소(`/…`)는 라우터 링크(`NuxtLink`), 사이트 밖 절대 주소(`https://…`)는 `<a href>` 다 — 외부 주소를 라우터에 넘기지 않는다.
        - 새 탭이면(`newTab`) `target="_blank" rel="noopener noreferrer"`(`UtilMenu.linkAttrs`).
        - 외부 링크 표시(`extra.external === true`)가 있을 때만 작은 외부 링크 아이콘을 글자 뒤에 달고, 스크린리더에는 "외부 링크"를 읽어 준다. 주소를 보고 화면이 추측하지 않는다.
        class·style·aria-current·click 같은 속성은 그대로 링크에 붙는다(바깥에서 준 것).
    -->
    <a v-if="UtilMenu.isAbsoluteLink(node)" :href="node.path" v-bind="UtilMenu.linkAttrs(node)" data-menu-link="absolute">
        <slot />
        <template v-if="UtilMenu.isExternal(node)">
            <i class="pi pi-external-link ml-1.5 shrink-0 text-[0.6875rem] text-muted-color" aria-hidden="true" data-testid="menu-external-icon" />
            <span class="sr-only">{{ t('menu.externalLabel') }}</span>
        </template>
    </a>
    <NuxtLink v-else :to="node.path" v-bind="UtilMenu.linkAttrs(node)" data-menu-link="router">
        <slot />
        <template v-if="UtilMenu.isExternal(node)">
            <i class="pi pi-external-link ml-1.5 shrink-0 text-[0.6875rem] text-muted-color" aria-hidden="true" data-testid="menu-external-icon" />
            <span class="sr-only">{{ t('menu.externalLabel') }}</span>
        </template>
    </NuxtLink>
</template>

<script setup lang="ts">
import type { MenuItemNode } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

const { t } = useI18n();

interface Props {
    /** 서버가 준 메뉴 항목 — 또는 연결이 있는 그룹의 같은 칸(`AppMenuGroupLabel` 이 `path` 를 확인하고 넘긴다) */
    node: Pick<MenuItemNode, 'path' | 'newTab' | 'extra'>;
}

const props = withDefaults(defineProps<Props>(), {});
</script>
