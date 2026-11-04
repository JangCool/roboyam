<template>
    <!--
        알림 상태 — 오늘 보낸 알림 수 + 채널별 실패 수를 한 줄 행으로(보낸 수는 툴팁) + 최근 실패 3건(채널 · 서버 문구 · 시각). 알림 발송 집계를 읽는다. 누르면 알림 도구
    -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" :disabled="state.disabled.value" :collect="state.collect" more="/settings/notification-channels" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatGrid :min="13" row :stats="stats" />
            <template v-if="v.recentFailures.length">
                <p class="m-0 text-[12px] text-muted-color">{{ t('dashboard.notificationRecentFailures') }}</p>
                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="f in v.recentFailures" :key="f.id" class="flex items-center gap-2 py-2 text-[13px]">
                        <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ channelName(f.channel) }}</span>
                        <span class="min-w-0 flex-1 truncate" :title="f.summary">{{ f.summary }}</span>
                        <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(f.failedAt) }}</span>
                    </li>
                </ul>
            </template>
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, te, locale } = useI18n();
const time = useDisplayTime();
const state = useDashboardKindData('NOTIFICATION_HEALTH', () => props.gadget);
const v = computed(() => state.data.value);

/** 채널 이름 — 모르는 채널은 코드 그대로 */
function channelName(channel: string): string {
    const key = `notification.channelType.${channel}`;
    return te(key) ? t(key) : channel;
}

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    const to = '/settings/notification-channels';
    return [
        { key: 'sent', label: t('dashboard.notificationSentToday'), count: d.sentToday, icon: 'pi pi-send', to },
        ...d.channels.map(
            (c): DashboardStat => ({
                key: `ch-${c.channel}`,
                label: t('dashboard.notificationChannelFailed', { channel: channelName(c.channel) }),
                count: c.failed,
                note: t('dashboard.notificationChannelSent', { n: c.sent.toLocaleString(locale.value) }),
                icon: 'pi pi-exclamation-circle',
                tone: c.failed > 0 ? 'danger' : 'normal',
                to,
            }),
        ),
    ];
});
</script>
