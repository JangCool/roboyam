<template>
    <!-- 소모임 화면의 딱지 — 상태(열림·비활성)·성질(공개·모집 마감·오프라인·투자 고지 …)·분류·게시판 줄의 알약이 모두 이 모양이다. 글자는 상하 중앙(`components.md` 2-4) -->
    <Tag class="group-tag" :value="value" :severity="severity" :icon="icon" />
</template>

<script setup lang="ts">
interface Props {
    value: string;
    severity?: 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';
    icon?: string;
}

withDefaults(defineProps<Props>(), { severity: 'secondary', icon: undefined });
</script>

<style scoped>
.group-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
    white-space: nowrap;
}
.group-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
