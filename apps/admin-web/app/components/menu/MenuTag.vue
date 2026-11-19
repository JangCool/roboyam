<template>
    <!-- 메뉴 관리 줄·목록의 작은 딱지 — 글자 상하 중앙 보정(`components.md` 2-4)을 한 곳에 둔다. 공개·관리자 메뉴 화면이 함께 쓴다 -->
    <Tag class="menu-tag shrink-0" :class="icon ? 'menu-tag--icon' : ''" :value="value" :severity="severity" :icon="icon || undefined" />
</template>

<script setup lang="ts">
interface Props {
    value: string;
    /** PrimeVue Tag 색 */
    severity?: 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';
    /** primeicons 클래스 */
    icon?: string;
}

withDefaults(defineProps<Props>(), { severity: 'secondary', icon: '' });
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.menu-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.25rem;
    line-height: 1;
    font-size: 11px;
    padding: 0.25rem 0.45rem;
}
.menu-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
}
.menu-tag--icon {
    align-items: baseline;
}
.menu-tag :deep(.p-tag-icon) {
    font-size: 10px;
}
</style>
