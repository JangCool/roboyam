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
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.notification-channel-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
