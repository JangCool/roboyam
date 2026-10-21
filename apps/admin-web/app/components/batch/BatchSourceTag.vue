<template>
    <!-- 배치가 어디서 도는지 — 앱 서버(JAVA) / DB(POSTGRESQL, pg_cron). 배치 관리·배치 로그가 같은 모양을 쓴다 -->
    <Tag class="batch-source-tag" :value="UtilBatchMessages.sourceLabel(t, source)" :icon="UtilBatch.sourceIcon(source)" :severity="source === 'POSTGRESQL' ? 'contrast' : 'secondary'" />
</template>

<script setup lang="ts">
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

interface Props {
    /** 배치(또는 로그 줄)의 `source` — `JAVA` · `POSTGRESQL` */
    source: string;
}

defineProps<Props>();

const { t } = useI18n();
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.batch-source-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
