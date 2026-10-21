<template>
    <!--
        알림 설정 — ① 알림 도구(알림을 보낼 곳) ② 이벤트별 전달 도구·추가 수신 팀·사용 여부. 우측 구역 내비(`AppGadget` 의 `anchor`)로 구획을 오간다(탭을 쓰지 않는다).

        ① 알림 도구(`channels`) — 서랍(항상 켜진 기본 도구) · 이메일(설정 > 메일 서버 설정으로 발송, 설정 칸 없음) · 브라우저 알림(설정 칸 없는 기본 제공 도구)과 사용자가 만드는 Slack · Mattermost 를 한 표로 보인다
            (종류 딱지 · 이름 · 사용 · 최근 연결 시험 결과 · 시험/수정/삭제). 추가·수정은 다이얼로그(종류별 입력칸, 비밀 값은 「입력됨」만 — `NotificationChannelDialog`). 카카오톡 · SMS 는 종류 선택지에 「준비 중」으로 흐리게만 보이고 만들 수 없다.
            연결 시험(목업)은 누를 때마다 성공 ↔ 실패를 번갈아 낸다. 사용 켜기/끄기는 누르면 즉시 저장(낙관적 갱신)이다. 서랍은 끌 수 없다.
        ② 이벤트 표 — 영역(보안 · 시스템 · 회원 …)마다 구획 한 장 + 표 한 개. 이벤트의 정의(이름·영역·심각도·필수 여부)와 **받는 사람(수신 규칙)은 서버 코드에 고정**이라 이 화면에서 바꾸지 않는다
            (규칙을 바꾸면 보면 안 될 알림을 받게 되어 권한 상승이 된다). 수신 대상은 문구로만 보인다. 이벤트마다 정하는 것은 셋이다:
              · 사용 — 이 이벤트의 알림을 쓸지(필수 이벤트는 켜진 채 잠금).
              · **전달 도구**(다중 선택) — 서랍은 사용 중인 이벤트에 늘 켜져 있어 고르는 것이 아니라 잠긴 딱지로 보이고, 등록된 도구(이메일 · Slack · Mattermost · 브라우저 알림)를 고른다.
                도구마다 이 이벤트에서 쓸 수 있는지가 다르다(이메일 불가 이벤트 · 본인에게만 가는 이벤트는 Slack·Mattermost 불가 · 꺼진 도구 · 준비 중 이벤트) — 못 고르는 이유가 선택지 옆에 적힌다.
              · **추가 수신 팀**(다중 선택, 기본 없음) — 코드에 고정된 기본 수신 규칙은 그대로이고, 고른 팀의 팀원은 서랍 · 이메일 · 브라우저 알림으로 추가 수신한다. Slack · Mattermost 는 팀원에게 따로 가지 않고 그 도구에 설정한 채널로 한 번 올라간다(목업 가정).
            발행처가 아직 없는 예약 이벤트는 흐리게 「준비 중」으로만 보이고 바꿀 수 없다. 스위치·선택은 **바꾸면 즉시 저장**한다(구획 안에 [저장]이 없다 — forms.md 2-4). 낙관적 갱신이고, 서버가 거절하면 그 줄만 되돌리고 서버 문구를 토스트로 보인다.
        권한: 화면 `notification.read` (메뉴 정의가 걸러 낸다) · 변경 `notification.manage`. 팀 이름은 `team.read` 가 있어야 읽는다(없으면 개수만 보인다).
        ⚠ 이벤트 목록은 실제 서버, **알림 도구 · 전달 도구 · 추가 수신 팀은 아직 목업**이다(`useNotificationChannelApi` · `useNotificationEventApi.deliveries`). 주소의 `?mock=notification-channel-error` 등으로 상태를 확인한다(각 `*-mock.ts` 머리말).
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('notification.adminTitle')" :subtitle="t('notification.adminSubtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다 -->
            <template #actions>
                <Button :label="t('notification.adminReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading || channelsLoading" @click="reloadAll()" />
            </template>
        </PageTitle>

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.adminReadOnly') }}</Message>

        <!-- ① 알림 도구 -->
        <AppGadget :title="t('notification.channelsTitle')" anchor="channels">
            <template v-if="canManage" #actions>
                <Button :label="t('notification.channelAdd')" icon="pi pi-plus" size="small" :disabled="!channels" @click="openCreateChannel" />
            </template>

            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('notification.channelsHint') }}</p>

            <div v-if="channelsLoading && !channels" class="flex flex-col gap-2" aria-busy="true">
                <span class="sr-only">{{ t('common.loading') }}</span>
                <Skeleton v-for="n in 4" :key="n" height="2.75rem" />
            </div>

            <ErrorState v-else-if="channelsError" :title="t('notification.channelsLoadFailed')" :description="channelsError.message" retry :retrying="channelsLoading" @retry="loadChannels()" />

            <template v-else-if="channels">
                <DataTable :value="channels" data-key="channelId" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
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
                            <span v-tooltip.top="channelEnabledLock(data)" class="inline-flex">
                                <ToggleSwitch
                                    :model-value="data.enabled"
                                    :disabled="channelEnabledLock(data) !== undefined || isChannelBusy(data.channelId)"
                                    :aria-label="t('notification.channelEnabledLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })"
                                    @update:model-value="(v: boolean) => setChannelEnabled(data, v)"
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
                                <Button v-if="UtilNotificationChannel.isTestable(data.type)" :label="t('notification.channelTest')" icon="pi pi-bolt" size="small" severity="secondary" variant="text" :loading="isChannelBusy(data.channelId)" :disabled="isChannelBusy(data.channelId)" @click="testChannel(data)" />
                                <Button v-if="data.type !== 'DRAWER' && data.type !== 'BROWSER'" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :disabled="isChannelBusy(data.channelId)" :aria-label="t('notification.channelEditLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })" @click="openEditChannel(data)" />
                                <Button v-if="!data.builtIn" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="isChannelBusy(data.channelId)" :aria-label="t('notification.channelDeleteLabel', { name: UtilNotificationChannelMessages.displayName(t, data) })" @click="removeChannel(data)" />
                            </div>
                        </template>
                    </Column>
                </DataTable>

                <p v-if="!hasCustomChannel" class="m-0 mt-3 text-xs text-muted-color">{{ t('notification.channelsNoCustom') }}</p>
            </template>
        </AppGadget>

        <!-- ② 이벤트별 전달 도구·추가 수신 팀·사용 여부 -->
        <div v-if="loading && !events" class="flex flex-col gap-4" aria-busy="true">
            <span class="sr-only">{{ t('common.loading') }}</span>
            <div v-for="n in 3" :key="n" class="rounded-xl border border-(--line) p-5">
                <Skeleton width="30%" height="1rem" class="mb-4" />
                <Skeleton v-for="m in 3" :key="m" height="2.5rem" class="mb-2" />
            </div>
        </div>

        <ErrorState v-else-if="error" :title="t('notification.adminLoadFailed')" :description="error.message" retry :retrying="loading" @retry="load()" />

        <EmptyState v-else-if="!events?.length" icon="pi pi-bell-slash" :title="t('notification.adminEmptyTitle')" :description="t('notification.adminEmptyBody')" />

        <template v-else>
            <Message severity="secondary" :closable="false" size="small">{{ t('notification.adminNote') }}</Message>

            <AppGadget v-for="group in groups" :key="group.area" :title="UtilNotificationMessages.areaLabel(t, group.area)" :anchor="UtilNotification.areaAnchor(group.area)">
                <DataTable :value="group.items" data-key="code" :row-class="rowClass" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                    <Column :header="t('notification.colEvent')" style="min-width: 18rem">
                        <template #body="{ data }">
                            <div class="min-w-0">
                                <!-- 이름 옆의 딱지 줄은 flex + items-center 로 — 베이스라인에 맡기면 딱지가 글자 줄 아래로 처진다 -->
                                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                    <span class="text-[13px] font-medium">{{ data.name }}</span>
                                    <NotificationFlagTag v-if="data.mandatory" kind="mandatory" />
                                    <NotificationFlagTag v-if="!data.featureReady" kind="notReady" />
                                </div>
                                <div class="mt-0.5 text-[11px] leading-relaxed text-muted-color">{{ data.description }}</div>
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('notification.colRecipient')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <span class="text-[12px] leading-relaxed">{{ data.recipientText }}</span>
                        </template>
                    </Column>

                    <Column :header="t('notification.colSeverity')" style="width: 7.5rem">
                        <template #body="{ data }">
                            <NotificationSeverityTag :kind="data.severity" />
                        </template>
                    </Column>

                    <Column :header="t('notification.colEnabled')" style="width: 6rem">
                        <template #body="{ data }">
                            <!-- 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                            <span v-tooltip.top="enabledLock(data)" class="inline-flex">
                                <ToggleSwitch
                                    :model-value="data.enabled"
                                    :disabled="enabledLock(data) !== undefined || isSaving(data.code)"
                                    :aria-label="t('notification.enabledLabel', { name: data.name })"
                                    @update:model-value="(v: boolean) => change(data, { enabled: v })"
                                />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('notification.colDelivery')" style="min-width: 19rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <!-- 서랍은 사용 중인 이벤트에 늘 켜져 있다 — 고르는 것이 아니라 잠긴 딱지다 -->
                                <span v-tooltip.top="drawerHint(data)" class="inline-flex">
                                    <NotificationChannelTypeTag type="DRAWER" />
                                </span>
                                <AppMultiSelect
                                    class="min-w-44 flex-1"
                                    :model-value="channelIdsOf(data)"
                                    :options="channelOptionsOf(data)"
                                    option-disabled="disabled"
                                    :disabled="deliveryLock(data) !== undefined || isSaving(data.code)"
                                    :placeholder="t('notification.deliveryPlaceholder')"
                                    :aria-label="t('notification.deliveryLabel', { name: data.name })"
                                    :selected-items-label="t('notification.deliveryCount', { n: '{0}' })"
                                    :max-selected-labels="2"
                                    @update:model-value="(ids: unknown[]) => changeDelivery(data, { channelIds: ids as string[], teamIds: teamIdsOf(data) })"
                                >
                                    <template #option="{ option }">
                                        <span class="flex w-full min-w-0 items-center gap-2" :class="option.disabled ? 'opacity-60' : ''">
                                            <i :class="UtilNotificationChannel.icon(option.type)" aria-hidden="true" />
                                            <span class="min-w-0 flex-1">
                                                <span class="block truncate text-[13px]">{{ option.label }}</span>
                                                <span v-if="option.reason" class="block text-[11px] text-muted-color">{{ option.reason }}</span>
                                            </span>
                                        </span>
                                    </template>
                                </AppMultiSelect>
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('notification.colTeams')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <span v-if="teamsLock(data) !== undefined && teamIdsOf(data).length === 0" v-tooltip.top="teamsLock(data)" class="inline-flex text-[12px] text-muted-color" :aria-label="teamsLock(data)">—</span>
                            <!-- 팀 이름을 못 읽는 권한이면(`team.read` 없음) 고른 개수만 보인다 -->
                            <span v-else-if="!teams" class="text-[12px] text-muted-color">{{ teamIdsOf(data).length ? t('notification.teamsUnknown', { n: teamIdsOf(data).length }) : '—' }}</span>
                            <AppMultiSelect
                                v-else
                                :model-value="teamIdsOf(data)"
                                :options="teamOptionsOf(data)"
                                option-disabled="disabled"
                                filter
                                :disabled="teamsLock(data) !== undefined || isSaving(data.code)"
                                :placeholder="t('notification.teamsPlaceholder')"
                                :filter-placeholder="t('notification.teamsFilter')"
                                :empty-message="t('notification.teamsEmpty')"
                                :aria-label="t('notification.teamsLabel', { name: data.name })"
                                :selected-items-label="t('notification.teamsCount', { n: '{0}' })"
                                :max-selected-labels="1"
                                @update:model-value="(ids: unknown[]) => changeDelivery(data, { channelIds: channelIdsOf(data), teamIds: ids as string[] })"
                            >
                                <template #option="{ option }">
                                    <span class="flex w-full min-w-0 items-center gap-2" :class="option.disabled ? 'opacity-60' : ''">
                                        <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }}</span>
                                        <span v-if="option.reason" class="shrink-0 text-[11px] text-muted-color">{{ option.reason }}</span>
                                    </span>
                                </template>
                            </AppMultiSelect>
                        </template>
                    </Column>

                    <Column :header="t('notification.colSent')" style="width: 8rem">
                        <template #body="{ data }">
                            <span v-if="data.featureReady" class="text-[12px] tabular-nums">{{ t('notification.sentCount', { n: data.sentLast30Days.toLocaleString() }) }}</span>
                            <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
        </template>

        <NotificationChannelDialog v-model:visible="channelDialogOpen" :channel="channelDialogTarget" :busy="channelSaving" :error="channelDialogError" :field-errors="channelDialogFieldErrors" @submit="submitChannel" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { NotificationChannelSaveRequest, NotificationChannelView } from '~/composables/notification/api/useNotificationChannelApi.types';
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationChannel from '~/utils/util-notification-channel';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notification.adminTitle') });

const { can } = useAdminPermission();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const toast = useAppToast();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();
const { events, teams, loading, error, isSaving, channelIdsOf, deliveryOf, load, reloadDelivery, change, changeDelivery } = useNotificationEvents();
const {
    channels,
    loading: channelsLoading,
    error: channelsError,
    saving: channelSaving,
    isBusy: isChannelBusy,
    load: loadChannels,
    create: createChannel,
    update: updateChannel,
    setEnabled: setChannelEnabled,
    remove: removeChannelOf,
    test: testChannel,
} = useNotificationChannels();

/** 이 화면에서 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('notification.manage'));

/** 영역별 묶음 — 영역 차례는 `UtilNotification.AREA_ORDER`, 영역 안은 서버가 준 차례(코드순) */
const groups = computed(() => UtilNotification.groupByArea(events.value ?? []));

const hasCustomChannel = computed(() => (channels.value ?? []).some((channel) => !channel.builtIn));

/** 준비 중인 예약 이벤트는 행을 흐리게 한다 */
function rowClass(event: NotificationEventView): string {
    return event.featureReady ? '' : 'opacity-60';
}

/** 사용 스위치가 잠긴 이유(툴팁) — 권한이 우선이다. 잠기지 않았으면 undefined */
function enabledLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    if (event.mandatory) return t('notification.mandatoryHint');
    return undefined;
}

// ── 전달 도구 · 추가 수신 팀 선택 ─

/** 전달 도구 선택이 통째로 잠긴 이유(툴팁). 잠기지 않았으면 undefined */
function deliveryLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    return undefined;
}

/** 서랍 딱지의 설명 — 서랍은 사용 중인 이벤트에 늘 켜져 있고, 필수 이벤트는 사용 자체를 끌 수 없다 */
function drawerHint(event: NotificationEventView): string {
    return event.mandatory ? t('notification.drawerLockedMandatory') : t('notification.drawerLocked');
}

/** 고른 추가 수신 팀 id 들 */
const teamIdsOf = (event: NotificationEventView): string[] => deliveryOf(event.code)?.teamIds ?? [];

/** 선택지로 보일 도구들(서랍 제외, 카카오·SMS 제외) */
const selectableChannels = computed(() => UtilNotificationChannel.selectable(channels.value ?? []));

/**
 * 이 이벤트의 전달 도구 선택지 — 못 고르는 이유가 있으면 흐리게 하고 이유를 적는다. 이미 골라 둔 것은 이유가 있어도 풀 수 있어야 하므로 잠그지 않는다.
 * ⚠ 줄마다 다시 만든다(선택지의 글자가 처음 읽은 순간의 언어로 굳지 않게 `t` 를 템플릿 렌더 안에서 부른다).
 */
function channelOptionsOf(event: NotificationEventView) {
    const picked = channelIdsOf(event);
    return selectableChannels.value.map((channel) => {
        const reason = UtilNotificationChannel.blockedReason(channel, event, deliveryOf(event.code));
        return {
            value: channel.channelId,
            label: UtilNotificationChannelMessages.displayName(t, channel),
            type: channel.type,
            disabled: reason !== null && !picked.includes(channel.channelId),
            reason: reason ? UtilNotificationChannelMessages.blocked(t, reason) : '',
        };
    });
}

/** 추가 수신 팀이 잠긴 이유 — 권한 · 준비 중 · 본인에게만 가는 이벤트. 잠기지 않았으면 undefined */
function teamsLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    if (deliveryOf(event.code)?.broadcastAllowed === false) return t('notification.teamsSelfOnly');
    return undefined;
}

/** 팀 선택지 — 멈춘 팀은 새로 고를 수 없다(이미 골라 둔 것은 풀 수 있다) */
function teamOptionsOf(event: NotificationEventView) {
    const picked = teamIdsOf(event);
    return (teams.value ?? []).map((team) => ({
        value: team.teamId,
        label: team.teamName,
        disabled: !team.enabled && !picked.includes(team.teamId),
        reason: team.enabled ? '' : t('team.stopped'),
    }));
}

// ── 알림 도구 구획 ─

/** 사용 스위치가 잠긴 이유 — 권한 · 서랍(항상 켜짐) */
function channelEnabledLock(channel: NotificationChannelView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (channel.type === 'DRAWER') return t('notification.channelDrawerAlwaysOn');
    return undefined;
}

const channelDialogOpen = ref(false);
/** 수정 대상 — null 이면 추가 */
const channelDialogTarget = ref<NotificationChannelView | null>(null);
const channelDialogError = ref('');
const channelDialogFieldErrors = ref<Record<string, string>>({});

function openCreateChannel(): void {
    channelDialogTarget.value = null;
    channelDialogError.value = '';
    channelDialogFieldErrors.value = {};
    channelDialogOpen.value = true;
}

function openEditChannel(channel: NotificationChannelView): void {
    channelDialogTarget.value = channel;
    channelDialogError.value = '';
    channelDialogFieldErrors.value = {};
    channelDialogOpen.value = true;
}

async function submitChannel(body: NotificationChannelSaveRequest): Promise<void> {
    channelDialogError.value = '';
    channelDialogFieldErrors.value = {};
    const target = channelDialogTarget.value;
    try {
        if (target) await updateChannel(target.channelId, body);
        else await createChannel(body);
        channelDialogOpen.value = false;
        toast.success(t(target ? 'notification.channelSaved' : 'notification.channelCreated'));
    } catch (e) {
        // 서버 거절 — 칸에 붙는 문구(`fields`)는 그 칸 아래에, 나머지는 다이얼로그 안에 서버 문구 그대로
        const failure = e as ApiError;
        channelDialogFieldErrors.value = failure.fields ?? {};
        channelDialogError.value = Object.keys(channelDialogFieldErrors.value).length ? '' : failure.message;
    }
}

async function removeChannel(channel: NotificationChannelView): Promise<void> {
    const ok = await confirm({
        title: t('notification.channelDeleteTitle'),
        message: UtilNotificationChannelMessages.deleteConfirm(t, UtilNotificationChannelMessages.displayName(t, channel), channel.usedByEventCount),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    // 이 도구를 고른 이벤트에서도 서버가 뺐으니 전달 설정을 다시 받는다
    if (await removeChannelOf(channel)) await reloadDelivery();
}

/** 도구와 이벤트를 함께 다시 조회한다 */
async function reloadAll(): Promise<void> {
    await Promise.all([loadChannels(), load()]);
}

onMounted(() => void reloadAll());
</script>

<style scoped>
/* 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다 — 글자만 올려 가운데에 맞춘다(`GradeBadge` 와 같은 보정, components.md 2-4) */
.channel-test-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
