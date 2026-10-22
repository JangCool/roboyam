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
/* 글자만: 가운데 + 한글 글자 0.09em 올림. 아이콘과 함께: 같은 기준선 + 위 여백을 줄이고 아래를 늘려 함께 올린다(`GradeBadge.vue` 와 같은 방법) */
.menu-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 0.25rem;
    line-height: 1;
    font-size: 11px;
    padding: 0.25rem 0.45rem;
}
.menu-tag:not(.menu-tag--icon) :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
.menu-tag--icon {
    align-items: baseline;
    padding-top: calc(0.25rem - 0.09em);
    padding-bottom: calc(0.25rem + 0.09em);
}
.menu-tag :deep(.p-tag-icon) {
    font-size: 10px;
}
</style>
