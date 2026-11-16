<template>
    <!--
        페이지 상단 제목 블록 — 셸이 아니라 본문 안에 있다. `actions` 슬롯에 버튼을 둘 수 있다.
        넓은 화면(md 이상)은 제목·설명 | 버튼을 한 줄에, 모바일(md 미만)은 제목·설명이 폭을 다 쓰고 버튼은 그 아래 줄(왼쪽 정렬)로 내려간다 — 옆에 두면 설명이 몇 글자씩 꺾였다.
        그 아래에 좁을 때의 「구역 이동」 선택 상자(`SectionNav variant="select"`)가 붙는다 — 제목을 먼저 읽고 그 화면의 구획으로 가게.
        구획이 2개 미만이거나 본문이 넓어 오른쪽 열 목록이 보이면 그리지 않는다(셸 CSS). 구획이 있는 화면은 모두 이 제목 블록을 쓴다.
    -->
    <div>
        <div class="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
            <div class="min-w-0 md:flex-1">
                <h1 class="truncate text-xl font-semibold tracking-tight">{{ title }}</h1>
                <p v-if="subtitle" class="mt-1 text-sm text-muted-color">{{ subtitle }}</p>
            </div>
            <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2 md:shrink-0 md:justify-end">
                <slot name="actions" />
            </div>
        </div>
        <SectionNav variant="select" />
    </div>
</template>

<script setup lang="ts">
interface Props {
    title: string;
    subtitle?: string;
}

withDefaults(defineProps<Props>(), { subtitle: '' });
</script>
