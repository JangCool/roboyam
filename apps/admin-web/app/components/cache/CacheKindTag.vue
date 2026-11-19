<template>
    <!-- 캐시 분류 — 공개 화면 / 가젯 / 설정 사본 / 내부 규칙. 캐시 관리 목록·상세 서랍·미리보기가 같은 모양을 쓴다 -->
    <Tag class="cache-kind-tag" :value="UtilCacheMessages.kindLabel(t, kind)" :icon="UtilCache.kindIcon(kind)" :severity="UtilCache.kindSeverity(kind)" />
</template>

<script setup lang="ts">
import type { CacheKind } from '~/composables/cache/api/useCacheAdminApi.types';
import UtilCache from '~/utils/util-cache';
import UtilCacheMessages from '~/utils/util-cache-messages';

interface Props {
    kind: CacheKind;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.cache-kind-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
