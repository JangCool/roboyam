<template>
    <!--
        운영자 이벤트(`/settings/notifications/operator`, 설정 > 알림·이벤트 — 개정 3 전 「이벤트」 `/settings/notifications`, 옛 주소는 `notifications/index.vue` 가 이리로 보낸다) — 이벤트별 전달 도구·추가 수신 팀·사용 여부.
        계약 `docs/features/message-template-contract.md` 이벤트 화면: 각 행에 「상세」(`/settings/notifications/operator/[code]` — 채널별 문구·특수 상황 규칙)와 「쓰는 문구」 요약(채널마다 한국어 쓸 문구 — `useNotificationMessageApi.targets`, `template.read` 가 있을 때만)을 더했다.
        이메일 칸: 서버는 준비 중이 아닌 이벤트를 모두 `emailable=true` 로 준다(「서버 구현 결과(회원 이벤트·이메일 확장)」). 그래도 `emailable=false` 가 오면 이메일 도구를 흐리게 「이메일로 보내지 않아요」로 둔다(PUT 400 을 미리 막는다). 영역마다 구획 한 장이라 우측 구역 내비(`AppGadget` 의 `anchor`)로 오간다(탭을 쓰지 않는다).
        알림 **도구**(Slack · Mattermost · 이메일 · 브라우저 알림 …)를 등록·관리하는 곳은 별도 화면 `/settings/notification-channels`(알림 도구)다 — 이 화면은 등록된 도구 목록(`useNotificationChannels`, 두 화면이 공유)을 「전달 도구」 선택지로만 쓰고,
        안내 한 줄과 그 화면으로 가는 링크를 둔다(고를 수 있는 도구가 없거나 꺼져 있는 경우에도 같은 링크로 안내).

        이벤트 표 — 영역(보안 · 시스템 · 회원 …)마다 구획 한 장 + 표 한 개. 이벤트의 정의(이름·영역·심각도·필수 여부)와 **받는 사람(수신 규칙)은 서버 코드에 고정**이라 이 화면에서 바꾸지 않는다
            (규칙을 바꾸면 보면 안 될 알림을 받게 되어 권한 상승이 된다). 수신 대상은 문구로만 보인다. 이벤트마다 정하는 것은 셋이다:
              · 사용 — 이 이벤트의 알림을 쓸지(필수 이벤트는 켜진 채 잠금).
              · **전달 도구**(다중 선택) — 서랍은 사용 중인 이벤트에 늘 켜져 있어 고르는 것이 아니라 잠긴 딱지로 보이고, 등록된 도구(이메일 · Slack · Mattermost · 브라우저 알림)를 고른다.
                도구마다 이 이벤트에서 쓸 수 있는지가 다르다(이메일 불가 이벤트 · 본인에게만 가는 이벤트는 Slack·Mattermost 불가 · 꺼진 도구 · 준비 중 이벤트) — 못 고르는 이유가 선택지 옆에 적힌다.
              · **받는 범위**(「권한자 전원」/「팀에만」, `defaultRecipients`) + 팀(다중 선택, 기본 없음) — 「권한자 전원」이면 코드에 고정된 기본 수신 규칙은 그대로이고, 고른 팀의 팀원은 서랍 · 이메일 · 브라우저 알림으로 추가 수신한다.
                「팀에만」이면 고른 팀의 팀원 중 볼 권한이 있는 사람만 받는다(고른 팀이 모두 없어지거나 받을 사람이 0명이면 서버가 권한자 전원으로 되돌려 보낸다). 「팀에만」인데 팀을 고르지 않으면 저장하지 않고 그 줄에 붙잡아 안내한다(서버도 400 `fields.teamIds`).
                본인 전용·필수 이벤트는 「권한자 전원」에 잠기고 팀 칸이 없다. Slack · Mattermost 는 팀원에게 따로 가지 않고 그 도구에 설정한 채널로 한 번 올라간다.
                팀 선택을 **바꾸려면** `team.read` 도 필요하다(서버가 403) — 없으면 팀 이름을 읽을 수 없어 고른 개수만 보이고 팀·받는 범위는 바꿀 수 없다(도구·사용 여부는 `notification.manage` 만으로 바뀐다).
            발행처가 아직 없는 예약 이벤트는 흐리게 「준비 중」으로만 보이고 바꿀 수 없다. 스위치·선택은 **바꾸면 즉시 저장**한다(구획 안에 [저장]이 없다 — forms.md 2-4). 낙관적 갱신이고, 서버가 거절하면 그 줄만 되돌리고 서버 문구를 토스트로 보인다.
        권한: 화면 `notification.read` (메뉴 정의가 걸러 낸다) · 변경 `notification.manage`. 팀 이름은 `team.read` 가 있어야 읽는다(없으면 개수만 보인다).
        서버 API: 이벤트 `GET·PUT /api/admin/notification-events`(응답의 `channels`·`teamIds`·`broadcastAllowed`·`defaultRecipients`, 요청 `{enabled, channels, teamIds, defaultRecipients}`) · 도구 `useNotificationChannelApi` ·
        팀 이름 `useTeamApi`(`docs/features/notification-contract.md` 「서버 구현 결과 (알림 도구·조직 구성)」). 거절은 서버 문구 그대로 토스트로 보인다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('notification.adminTitle')" :subtitle="t('notification.adminSubtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다 -->
            <template #actions>
                <Button :label="t('notification.adminReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading || channelsLoading" @click="reloadAll()" />
            </template>
        </PageTitle>

        <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.adminReadOnly') }}</Message>

        <!-- 알림 도구는 별도 화면에서 관리한다 — 고를 수 있는 도구가 없거나 모두 꺼져 있으면 같은 링크가 자연스러운 다음 걸음이다 -->
        <Message :severity="noUsableChannel ? 'warn' : 'secondary'" :closable="false" size="small">
            <span>{{ noUsableChannel ? t('notification.channelsNoneUsable') : t('notification.channelsLinkNote') }}</span>
            <NuxtLink v-if="can('notification.read')" to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
        </Message>

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
                    <Column :header="t('notification.colEvent')" style="min-width: 15rem">
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

                    <Column :header="t('notification.colRecipient')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span class="text-[12px] leading-relaxed">{{ data.recipientText }}</span>
                        </template>
                    </Column>

                    <Column :header="t('notification.colSeverity')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <NotificationSeverityTag :kind="data.severity" />
                        </template>
                    </Column>

                    <Column :header="t('notification.colEnabled')" style="width: 4.5rem">
                        <template #body="{ data }">
                            <!-- 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                            <span v-tooltip.top="enabledLock(data)" class="inline-flex">
                                <ToggleSwitch
                                    :model-value="data.enabled"
                                    :disabled="enabledLock(data) !== undefined || isSaving(data.code)"
                                    :aria-label="t('notification.enabledLabel', { name: data.name })"
                                    @update:model-value="(v: boolean) => change(data, v)"
                                />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('notification.colDelivery')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <!-- 서랍은 사용 중인 이벤트에 늘 켜져 있다 — 고르는 것이 아니라 잠긴 딱지다 -->
                                <span v-tooltip.top="drawerHint(data)" class="inline-flex">
                                    <NotificationChannelTypeTag type="DRAWER" />
                                </span>
                                <AppMultiSelect
                                    class="min-w-44 flex-1"
                                    :model-value="data.channels"
                                    :options="channelOptionsOf(data)"
                                    option-disabled="disabled"
                                    :disabled="deliveryLock(data) !== undefined || isSaving(data.code)"
                                    :placeholder="t('notification.deliveryPlaceholder')"
                                    :empty-message="t('notification.deliveryEmpty')"
                                    :aria-label="t('notification.deliveryLabel', { name: data.name })"
                                    :selected-items-label="t('notification.deliveryCount', { n: '{0}' })"
                                    :max-selected-labels="2"
                                    @update:model-value="(ids: unknown[]) => changeDelivery(data, { channels: ids as string[], teamIds: data.teamIds })"
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

                    <!-- 받는 범위 — 「권한자 전원」/「팀에만」 + 팀 고르기를 한 칸에 위아래로(열 폭을 늘리지 않는다) -->
                    <Column :header="t('notification.colScope')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-col items-start gap-1.5">
                                <!-- 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                                <span v-tooltip.top="scopeLock(data)" class="inline-flex">
                                    <SelectButton
                                        :model-value="scopeOf(data)"
                                        :options="scopeOptions"
                                        option-label="label"
                                        option-value="value"
                                        :allow-empty="false"
                                        size="small"
                                        class="notification-scope"
                                        :disabled="scopeLock(data) !== undefined || isSaving(data.code)"
                                        :aria-label="t('notification.scopeLabel', { name: data.name })"
                                        @update:model-value="(value: Scope) => changeScope(data, value)"
                                    />
                                </span>
                                <!-- 팀을 고를 수 없는 이벤트(본인 전용·필수)는 팀 칸이 없다 — 잠긴 「권한자 전원」과 그 툴팁이 까닭을 말한다 -->
                                <template v-if="teamsPossible(data) || teamIdsOf(data).length">
                                    <span v-if="teamsLock(data) !== undefined && teamIdsOf(data).length === 0" class="text-[12px] text-muted-color">{{ t('notification.scopeNoTeams') }}</span>
                                    <!-- 팀 이름을 못 읽는 권한이면(`team.read` 없음) 고른 개수만 보인다 -->
                                    <span v-else-if="!teams" class="text-[12px] text-muted-color">{{ teamIdsOf(data).length ? t('notification.teamsUnknown', { n: teamIdsOf(data).length }) : t('notification.scopeNoTeams') }}</span>
                                    <AppMultiSelect
                                        v-else
                                        class="w-full"
                                        :model-value="teamIdsOf(data)"
                                        :options="teamOptionsOf(data)"
                                        option-disabled="disabled"
                                        filter
                                        :invalid="isPending(data)"
                                        :disabled="teamsLock(data) !== undefined || isSaving(data.code)"
                                        :placeholder="scopeOf(data) === 'TEAMS' ? t('notification.scopeTeamsPlaceholder') : t('notification.teamsPlaceholder')"
                                        :filter-placeholder="t('notification.teamsFilter')"
                                        :empty-message="t('notification.teamsEmpty')"
                                        :aria-label="t(scopeOf(data) === 'TEAMS' ? 'notification.scopeTeamsLabel' : 'notification.teamsLabel', { name: data.name })"
                                        :selected-items-label="t('notification.teamsCount', { n: '{0}' })"
                                        :max-selected-labels="1"
                                        @update:model-value="(ids: unknown[]) => changeTeams(data, ids as string[])"
                                    >
                                        <template #option="{ option }">
                                            <span class="flex w-full min-w-0 items-center gap-2" :class="option.disabled ? 'opacity-60' : ''">
                                                <span class="min-w-0 flex-1 truncate text-[13px]">{{ option.label }}</span>
                                                <span v-if="option.reason" class="shrink-0 text-[11px] text-muted-color">{{ option.reason }}</span>
                                            </span>
                                        </template>
                                    </AppMultiSelect>
                                </template>
                                <!-- 「팀에만」인데 팀을 고르지 않았다 — 저장하지 않고 붙잡아 둔다(서버도 400 `fields.teamIds`) -->
                                <Message v-if="isPending(data)" severity="warn" variant="simple" size="small" :closable="false" class="text-[11px]!">{{ t('notification.scopeNeedTeam') }}</Message>
                            </div>
                        </template>
                    </Column>

                    <!-- 쓰는 문구 — `template.read` 가 없거나 못 받으면 칸을 감춘다 -->
                    <Column v-if="messageTargets" :header="t('notification.colMessages')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <MessageUsageSummary :channels="messageTargets.get(data.code)?.channels ?? null" />
                        </template>
                    </Column>

                    <Column :header="t('notification.colSent')" style="width: 6rem">
                        <template #body="{ data }">
                            <span v-if="data.featureReady" class="text-[12px] tabular-nums">{{ t('notification.sentCount', { n: data.sentLast30Days.toLocaleString() }) }}</span>
                            <span v-else class="text-[12px] text-muted-color" aria-hidden="true">—</span>
                        </template>
                    </Column>
                    <Column :header="t('notification.detail')" style="width: 4.5rem">
                        <template #body="{ data }">
                            <NuxtLink :to="`/settings/notifications/operator/${encodeURIComponent(data.code)}`" class="inline-flex items-center gap-1 text-[12px] text-primary hover:underline" :aria-label="t('notification.detailLabel', { name: data.name })">
                                {{ t('notification.detail') }}
                                <i class="pi pi-angle-right text-[10px]" aria-hidden="true" />
                            </NuxtLink>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
            <p v-if="messageTargets" class="m-0 text-xs text-muted-color">{{ t('notification.messagesNote') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import type { MessageTargetView } from '~/composables/template/api/useNotificationMessageApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationChannel from '~/utils/util-notification-channel';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notification.adminTitle') });

const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();
const { events, teams, loading, error, isSaving, load, change, changeDelivery } = useNotificationEvents();
// 등록된 도구 목록은 알림 도구 화면(`/settings/notification-channels`)과 공유하는 상태다 — 여기서는 「전달 도구」 선택지로만 쓴다
const { channels, loading: channelsLoading, load: loadChannels } = useNotificationChannels();
const messageApi = useNotificationMessageApi();

/** 이 화면에서 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('notification.manage'));

/** 영역별 묶음 — 영역 차례는 `UtilNotification.AREA_ORDER`, 영역 안은 서버가 준 차례(코드순) */
const groups = computed(() => UtilNotification.groupByArea(events.value ?? []));

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

/** 붙잡아 둔 줄이 보일 빈 팀 선택 — 같은 배열을 써서 다시 그릴 때마다 선택 칸이 새 값으로 읽지 않게 한다 */
const NO_TEAMS: string[] = [];

/** 지금 보이는 팀 id 들 — 「팀에만」을 붙잡아 둔 줄은 비어 있다(서버 값은 그대로) */
const teamIdsOf = (event: NotificationEventView): string[] => (isPending(event) ? NO_TEAMS : event.teamIds);

/** 선택지로 보일 도구들(서랍 제외, 카카오·SMS 제외) */
const selectableChannels = computed(() => UtilNotificationChannel.selectable(channels.value ?? []));

/** 도구를 받았는데 켜져 있는 선택지가 하나도 없다 — 링크 안내가 경고색으로 바뀐다(받기 전·못 받았을 때는 말하지 않는다) */
const noUsableChannel = computed(() => channels.value !== undefined && !selectableChannels.value.some((channel) => channel.enabled));

/**
 * 이 이벤트의 전달 도구 선택지 — 못 고르는 이유가 있으면 흐리게 하고 이유를 적는다. 이미 골라 둔 것은 이유가 있어도 풀 수 있어야 하므로 잠그지 않는다.
 * ⚠ 줄마다 다시 만든다(선택지의 글자가 처음 읽은 순간의 언어로 굳지 않게 `t` 를 템플릿 렌더 안에서 부른다).
 */
function channelOptionsOf(event: NotificationEventView) {
    const picked = event.channels;
    return selectableChannels.value.map((channel) => {
        const reason = UtilNotificationChannel.blockedReason(channel, event);
        return {
            value: channel.channelId,
            label: UtilNotificationChannelMessages.displayName(t, channel),
            type: channel.type,
            disabled: reason !== null && !picked.includes(channel.channelId),
            reason: reason ? UtilNotificationChannelMessages.blocked(t, reason) : '',
        };
    });
}

/** 팀 선택이 잠긴 이유 — 권한 · 준비 중 · 필수 · 본인에게만 가는 이벤트. 잠기지 않았으면 undefined */
function teamsLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    if (event.mandatory) return t('notification.scopeMandatory');
    if (!event.broadcastAllowed) return t('notification.teamsSelfOnly');
    return undefined;
}

/** 팀을 고를 수 있는 이벤트인가 — 본인 전용(`broadcastAllowed=false`)·필수 이벤트는 팀이 없다(서버 400 `NOTIFICATION_BROADCAST_NOT_ALLOWED`) */
const teamsPossible = (event: NotificationEventView): boolean => event.broadcastAllowed && !event.mandatory;

/* ── 받는 범위(권한자 전원 / 팀에만) ── */

/** 받는 범위 선택 값 — `ALL` = 권한자 전원(`defaultRecipients=true`), `TEAMS` = 팀에만(false) */
type Scope = 'ALL' | 'TEAMS';

/** ⚠ 선택지 글자는 `computed` 로 — 모듈 상수에 굳히면 언어를 바꿔도 다시 계산되지 않는다 */
const scopeOptions = computed<{ label: string; value: Scope }[]>(() => [
    { label: t('notification.scopeAll'), value: 'ALL' },
    { label: t('notification.scopeTeams'), value: 'TEAMS' },
]);

/**
 * 「팀에만」을 골랐지만 팀이 없어 저장하지 않고 붙잡아 둔 줄의 코드들 — 이 줄은 「팀에만」·팀 없음으로 보이고 서버 값은 그대로다.
 * 팀을 고르면(또는 「권한자 전원」으로 돌리면) 그때 저장한다. 다시 조회하면 지운다(서버 값으로 돌아간다).
 */
const pendingTeamsOnly = ref<string[]>([]);
const isPending = (event: NotificationEventView): boolean => pendingTeamsOnly.value.includes(event.code);
function setPending(event: NotificationEventView, on: boolean): void {
    const others = pendingTeamsOnly.value.filter((code) => code !== event.code);
    pendingTeamsOnly.value = on ? [...others, event.code] : others;
}

/** 지금 보이는 받는 범위 — 붙잡아 둔 줄은 「팀에만」 */
function scopeOf(event: NotificationEventView): Scope {
    if (isPending(event)) return 'TEAMS';
    return UtilNotification.defaultRecipientsOf(event) ? 'ALL' : 'TEAMS';
}

/** 받는 범위가 잠긴 이유(툴팁) — 권한 · 준비 중 · 필수 · 본인 전용 · 팀을 볼 권한 없음. 잠기지 않았으면 undefined */
function scopeLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    if (event.mandatory) return t('notification.scopeMandatory');
    if (!event.broadcastAllowed) return t('notification.scopeSelfOnly');
    // 팀 이름을 못 읽으면(`team.read` 없음) 어느 팀에만 갈지 알 수 없다 — 팀 칸처럼 바꿀 수 없게 둔다
    if (!teams.value) return t('notification.scopeNoTeamRead');
    return undefined;
}

/** 받는 범위와 팀을 함께 정한다 — 「팀에만」인데 팀이 없으면 저장하지 않고 붙잡아 안내한다(`UtilNotification.needsTeam`) */
function applyRecipients(event: NotificationEventView, defaultRecipients: boolean, teamIds: string[]): void {
    if (UtilNotification.needsTeam(defaultRecipients, teamIds)) {
        setPending(event, true);
        return;
    }
    setPending(event, false);
    void changeDelivery(event, { channels: event.channels, teamIds, defaultRecipients });
}

function changeScope(event: NotificationEventView, scope: Scope): void {
    applyRecipients(event, scope === 'ALL', teamIdsOf(event));
}

function changeTeams(event: NotificationEventView, ids: string[]): void {
    applyRecipients(event, scopeOf(event) === 'ALL', ids);
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

/* ── 쓰는 문구 요약 ── */

/** 이벤트 코드 → 문구 대상(채널별 요약). null = 권한이 없거나 못 받았다(칸을 감춘다) */
const messageTargets = ref<Map<string, MessageTargetView> | null>(null);

async function loadMessageTargets(): Promise<void> {
    if (!can('template.read')) {
        messageTargets.value = null;
        return;
    }
    try {
        const list = await messageApi.targets({ type: 'EVENT', audience: 'OPERATOR' });
        messageTargets.value = new Map(list.map((item) => [item.code, item]));
    } catch {
        messageTargets.value = null;
    }
}

/** 도구와 이벤트(와 쓰는 문구 요약)를 함께 다시 조회한다 */
async function reloadAll(): Promise<void> {
    // 붙잡아 둔 「팀에만」은 저장하지 않은 것이다 — 다시 조회하면 서버 값으로 돌아간다
    pendingTeamsOnly.value = [];
    await Promise.all([loadChannels(), load(), loadMessageTargets()]);
}

onMounted(() => void reloadAll());
</script>

<style scoped>
/* 받는 범위 단추는 표 칸 안이라 글자를 한 단계 줄여 열 폭을 늘리지 않는다 */
.notification-scope :deep(.p-togglebutton) {
    font-size: 12px;
    white-space: nowrap;
}
</style>
