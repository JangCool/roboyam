<template>
    <!--
        「쓰는 템플릿」 요약 — 대상(이벤트 · 필수 안내 메일)의 채널마다 한 줄: 채널 아이콘 · 한국어 쓸 템플릿 이름(영어가 다르면 덧붙임) · 규칙 수.
        회원 이벤트 목록 · 운영자 이벤트 목록 · 필수 안내 메일 목록이 같이 쓴다. 기본 템플릿 이름은 흐리게(고친 것이 눈에 띄게).
        `channels` 가 없으면(대상이 없거나 `template.read` 가 없다) 「—」.
    -->
    <ul v-if="channels && channels.length" class="m-0 flex list-none flex-col gap-0.5 p-0 text-[12px]">
        <li v-for="item in channels" :key="item.channel" class="flex min-w-0 items-center gap-1.5">
            <i v-tooltip.top="UtilNotificationTemplateMessages.channelLabel(t, item.channel)" :class="UtilNotificationTemplateMessages.channelIcon(item.channel)" class="shrink-0 text-[11px] text-muted-color" :aria-label="UtilNotificationTemplateMessages.channelLabel(t, item.channel)" role="img" />
            <span class="truncate" :class="item.selected.ko.builtin ? 'text-muted-color' : ''">{{ item.selected.ko.name }}</span>
            <span v-if="item.selected.en.id !== item.selected.ko.id && !(item.selected.en.builtin && item.selected.ko.builtin)" class="shrink-0 truncate text-[11px] text-muted-color">· EN {{ item.selected.en.name }}</span>
            <span v-if="item.ruleCount" class="shrink-0 text-[11px] text-muted-color">· {{ t('notificationTemplate.ruleCount', { count: item.ruleCount }) }}</span>
        </li>
    </ul>
    <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
</template>

<script setup lang="ts">
import type { TemplateChannelSummaryView } from '~/composables/template/api/useNotificationTemplateApi.types';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

interface Props {
    /** 대상의 채널별 요약(없으면 「—」) */
    channels?: TemplateChannelSummaryView[] | null;
}

withDefaults(defineProps<Props>(), { channels: null });

const { t } = useI18n();
</script>
