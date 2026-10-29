<template>
    <!--
        메뉴 그룹 이름 — 운영자가 폴더에 「누르면 가는 곳」을 골랐으면(`group.path`) 이름이 링크(`AppMenuLink`, 새 탭·외부 표시 같은 규칙)이고, 없으면 글자뿐이다.
        세로 메뉴·상단 막대·드롭다운·모바일 둘째 줄의 그룹 제목이 같이 쓴다. 펼치기·접기는 이 부품이 아니라 옆 화살표 단추가 맡는다.
        class 는 링크·글자 어느 쪽에나 붙는다. 링크면 밑줄(마우스 올림)·초점 테두리를 더한다.
    -->
    <AppMenuLink v-if="group.path" :node="{ path: group.path, newTab: group.newTab === true, extra: group.extra ?? null }" class="rounded hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="menu-group-link">
        {{ localizedName(group.label, group.nameEn) }}
    </AppMenuLink>
    <span v-else>{{ localizedName(group.label, group.nameEn) }}</span>
</template>

<script setup lang="ts">
import type { MenuGroupNode } from '~/composables/menu/api/useMenuApi.types';

interface Props {
    group: Pick<MenuGroupNode, 'label' | 'nameEn' | 'path' | 'newTab' | 'extra'>;
}

withDefaults(defineProps<Props>(), {});

// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
</script>
