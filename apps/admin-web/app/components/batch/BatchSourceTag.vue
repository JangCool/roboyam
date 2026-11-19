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
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.batch-source-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
