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
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.notification-severity-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
