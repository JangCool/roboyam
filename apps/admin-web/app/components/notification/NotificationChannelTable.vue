<template>
    <!--
        알림 도구 표 — 종류 딱지 · 이름(기본 제공 딱지·한 줄 설명) · 사용 스위치 · 최근 연결 시험 결과 · 시험/수정/삭제. 서랍·이메일·브라우저 알림(기본 제공)과 사용자가 만든 Slack·Mattermost 를 한 표로 보인다.
        그리기만 한다 — 서버 호출·확인창은 페이지가 하고 여기는 이벤트(`toggle` · `test` · `edit` · `remove`)로 알린다. 잠금 이유(권한 · 서랍은 끌 수 없음)는 툴팁으로 말한다.
    -->
    <DataTable :value="channels" data-key="channelId" :size="compact ? 'small' : undefined" scrollable>
        <Column :header="t('notification.channelColType')" style="width: 10rem">
            <template #body="{ data }">
                <NotificationChannelTypeTag :type="data.type" />
            </template>
        </Column>

        <Column :header="t('notification.channelColName')" style="min-width: 16rem">
            <template #body="{ data }">
                <div class="min-w-0">
                    <!-- 이름 옆의 딱지 줄은 flex + items-center 로 — 베이스라인에 맡기면 딱지가 글자 줄 아래로 처진다 -->
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span class="text-[13px] font-medium">{{ UtilNotificationChannelMessages.displayName(t, data) }}</span>
                        <NotificationFlagTag v-if="data.builtIn" kind="builtIn" />
                    </div>
                    <div class="mt-0.5 text-[11px] leading-relaxed text-muted-color">{{ UtilNotificationChannelMessages.summary(t, data) }}</div>
                    <div v-if="data.description" class="mt-0.5 text-[11px] leading-relaxed text-muted-color">{{ data.description }}</div>
                </div>
            </template>
        </Column>

        <Column :header="t('notification.channelColEnabled')" style="width: 6rem">
            <template #body="{ data }">
                <!-- 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                <span v-tooltip.top="enabledLock(data)" class="inline-flex">
                    <ToggleSwitch
                        :model-value="data.enabled"
                        :disabled="enabledLock(data) !== undefined || isBusy(data.channelId)"
                        :aria-label="t('notification.channelEnabledLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })"
                        @update:model-value="(v: boolean) => emit('toggle', data, v)"
                    />
                </span>
            </template>
        </Column>

        <Column :header="t('notification.channelColTest')" style="min-width: 14rem">
            <template #body="{ data }">
                <div v-if="data.lastTest" class="min-w-0">
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <Tag class="channel-test-tag" :value="data.lastTest.ok ? t('notification.channelTestResultOk') : t('notification.channelTestResultFail')" :icon="data.lastTest.ok ? 'pi pi-check-circle' : 'pi pi-times-circle'" :severity="data.lastTest.ok ? 'success' : 'danger'" />
                        <span class="text-[11px] text-muted-color tabular-nums">{{ time.listLabel(data.lastTest.testedAt) }}</span>
                    </div>
                    <div class="mt-0.5 text-[11px] leading-relaxed text-muted-color [overflow-wrap:anywhere]">{{ data.lastTest.message }}</div>
                </div>
                <span v-else-if="UtilNotificationChannel.isTestable(data.type)" class="text-[12px] text-muted-color">{{ t('notification.channelNeverTested') }}</span>
                <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
            </template>
        </Column>

        <Column v-if="canManage" :header="t('notification.channelColActions')" style="width: 11rem">
            <template #body="{ data }">
                <div class="flex items-center gap-1">
                    <Button v-if="UtilNotificationChannel.isTestable(data.type)" :label="t('notification.channelTest')" icon="pi pi-bolt" size="small" severity="secondary" variant="text" :loading="isBusy(data.channelId)" :disabled="isBusy(data.channelId)" @click="emit('test', data)" />
                    <Button v-if="data.type !== 'DRAWER' && data.type !== 'BROWSER'" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :disabled="isBusy(data.channelId)" :aria-label="t('notification.channelEditLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })" @click="emit('edit', data)" />
                    <Button v-if="!data.builtIn" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="isBusy(data.channelId)" :aria-label="t('notification.channelDeleteLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })" @click="emit('remove', data)" />
                </div>
            </template>
        </Column>
    </DataTable>
</template>

<script setup lang="ts">
import type { NotificationChannelView } from '~/composables/notification/api/useNotificationChannelApi.types';
import UtilNotificationChannel from '~/utils/util-notification-channel';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';

interface Props {
    channels: NotificationChannelView[];
    /** 바꿀 수 있는 권한(`notification.manage`)이 있는가 — 없으면 스위치가 잠기고 작업 열이 없다 */
    canManage?: boolean;
    /** 요청 중인 도구 id 들 — 그 줄의 스위치·단추를 잠근다 */
    busyIds?: string[];
    /** 조밀 모드(화면 설정 밀도) */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canManage: false, busyIds: () => [], compact: false });
const emit = defineEmits<{
    toggle: [channel: NotificationChannelView, enabled: boolean];
    test: [channel: NotificationChannelView];
    edit: [channel: NotificationChannelView];
    remove: [channel: NotificationChannelView];
}>();

const { t } = useI18n();
const time = useDisplayTime();

const isBusy = (channelId: string): boolean => props.busyIds.includes(channelId);

/** 사용 스위치가 잠긴 이유 — 권한 · 서랍(항상 켜짐). 잠기지 않았으면 undefined */
function enabledLock(channel: NotificationChannelView): string | undefined {
    if (!props.canManage) return t('notification.noManageReason');
    if (channel.type === 'DRAWER') return t('notification.channelDrawerAlwaysOn');
    return undefined;
}
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.channel-test-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
