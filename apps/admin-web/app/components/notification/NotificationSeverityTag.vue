<template>
    <!-- 알림 이벤트의 심각도 — 안내 · 완료 · 주의 · 긴급. 이벤트 관리와 내 정보의 이벤트별 알림이 같은 모양을 쓴다. 색만이 아니라 아이콘 모양도 다르다 -->
    <Tag class="notification-severity-tag" :value="t(`notification.severity.${kind}`)" :icon="UtilNotification.kindIcon(kind)" :severity="UtilNotification.tagSeverity(kind)" />
</template>

<script setup lang="ts">
import type { NotificationKind } from '~/composables/shell/api/useNotificationApi.types';
import UtilNotification from '~/utils/util-notification';

interface Props {
    kind: NotificationKind;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.notification-severity-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
