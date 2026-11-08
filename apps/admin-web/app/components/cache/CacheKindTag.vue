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
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉는다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.cache-kind-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
