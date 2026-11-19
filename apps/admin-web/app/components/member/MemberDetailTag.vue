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
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.member-detail-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
}
</style>
