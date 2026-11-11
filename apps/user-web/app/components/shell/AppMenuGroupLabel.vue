<template>
    <!--
        메뉴 그룹 이름 — 운영자가 폴더에 「누르면 가는 곳」을 골랐으면(`group.path`) 이름이 링크(`AppMenuLink`, 새 탭·외부 표시 같은 규칙)이고, 없으면 글자뿐이다.
        세로 메뉴·상단 막대·드롭다운·모바일 둘째 줄의 그룹 제목이 같이 쓴다. 펼치기·접기는 이 부품이 아니라 옆 화살표 단추가 맡는다.
        이름의 글자 색·굵게·배지는 항목과 같은 규칙(`AppMenuItemLabel`, 운영자가 폴더 창에서 고른다 — 기본이면 감싼 제목의 흐린 색을 그대로 따른다).
        class 는 링크·글자 어느 쪽에나 붙는다. 링크면 밑줄(마우스 올림)·초점 테두리를 더한다.
    -->
    <AppMenuLink v-if="group.path" :node="{ path: group.path, newTab: group.newTab === true, extra: group.extra ?? null }" class="rounded hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="menu-group-link">
        <AppMenuItemLabel :node="group" :keep-weight="keepWeight" />
    </AppMenuLink>
    <span v-else><AppMenuItemLabel :node="group" :keep-weight="keepWeight" /></span>
</template>

<script setup lang="ts">
import type { MenuGroupNode } from '~/composables/menu/api/useMenuApi.types';

interface Props {
    group: Pick<MenuGroupNode, 'label' | 'nameEn' | 'path' | 'newTab' | 'extra' | 'textColor' | 'bold' | 'badge'>;
    /** 감싼 칸이 이미 굵기를 정했으면 true — 「굵게」를 칠하지 않는다(`AppMenuItemLabel` 의 같은 칸, 테마 탭 안의 드롭다운 제목) */
    keepWeight?: boolean;
}

withDefaults(defineProps<Props>(), { keepWeight: false });
</script>
