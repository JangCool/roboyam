<template>
    <!-- 회원 상세 서랍의 뱃지 — PrimeVue Tag 에 한글 글자 중앙 보정을 붙인 것. 상세 탭들이 같은 모양으로 쓴다(`components.md` 2-4) -->
    <Tag class="member-detail-tag" :value="value" :icon="icon" :severity="severity" />
</template>

<script setup lang="ts">
import type { TagSeverity } from '~/utils/util-member';

interface Props {
    value: string;
    icon?: string;
    severity?: TagSeverity;
}

withDefaults(defineProps<Props>(), { icon: undefined, severity: 'secondary' });
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정) */
.member-detail-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
