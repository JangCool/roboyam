<template>
    <!-- 알림 도구의 종류 딱지 — 아이콘 + 종류 이름(서랍 · 이메일 · 브라우저 알림 · Slack · Mattermost · 카카오톡 · SMS). 도구 목록과 이벤트의 전달 도구 선택이 같은 모양을 쓴다 -->
    <Tag class="notification-channel-tag" :value="UtilNotificationChannelMessages.typeLabel(t, type)" :icon="UtilNotificationChannel.icon(type)" :severity="UtilNotificationChannel.tagSeverity(type)" />
</template>

<script setup lang="ts">
import type { NotificationChannelType } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannel from '~/utils/util-notification-channel';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';

interface Props {
    type: NotificationChannelType;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.notification-channel-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
