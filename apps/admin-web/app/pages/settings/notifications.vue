<template>
    <!--
        알림 이벤트 관리 — 관리자 앱에서 일어나는 사건(이벤트)마다 알림을 쓸지(사용)·이메일로도 보낼지(이메일)를 정한다. 이벤트의 정의(이름·영역·심각도·필수 여부)와 **받는 사람(수신 규칙)은 서버 코드에 고정**이라 이 화면에서 바꾸지 않는다
        (규칙을 바꾸면 보면 안 될 알림을 받게 되어 권한 상승이 된다). 수신 대상은 문구로만 보인다. 앱 안 전달(종 알림 서랍)은 사용 중인 이벤트에 늘 켜져 있다.
        영역(보안 · 시스템 · 회원 …)마다 구획 한 장 + 표 한 개이고, 영역이 여럿이라 우측 구역 내비(`AppGadget` 의 `anchor`)로 이동한다. 발행처가 아직 없는 예약 이벤트는 흐리게 「준비 중」으로만 보이고 바꿀 수 없다.
        스위치는 **누르면 즉시 저장**한다(구획 안에 [저장]이 없다 — forms.md 2-4). 낙관적 갱신이고, 서버가 거절하면 그 줄만 되돌리고 서버 문구를 토스트로 보인다. 필수 이벤트는 켜진 채 잠겨 있다. 설정 권한(`notification.manage`)이 없으면 모든 스위치가 읽기 전용이다.
        권한: 화면 `notification.read` (메뉴 정의가 걸러 낸다) · 변경 `notification.manage`.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('notification.adminTitle')" :subtitle="t('notification.adminSubtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다 -->
            <template #actions>
                <Button :label="t('notification.adminReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load()" />
            </template>
        </PageTitle>

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
            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.adminReadOnly') }}</Message>
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

                    <Column :header="t('notification.colEmail')" style="width: 6rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="emailLock(data)" class="inline-flex">
                                <ToggleSwitch
                                    :model-value="data.emailEnabled"
                                    :disabled="emailLock(data) !== undefined || isSaving(data.code)"
                                    :aria-label="t('notification.emailLabel', { name: data.name })"
                                    @update:model-value="(v: boolean) => change(data, { emailEnabled: v })"
                                />
                            </span>
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
    </div>
</template>

<script setup lang="ts">
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notification.adminTitle') });

const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();
const { events, loading, error, isSaving, load, change } = useNotificationEvents();

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

/** 이메일 스위치가 잠긴 이유(툴팁). 잠기지 않았으면 undefined */
function emailLock(event: NotificationEventView): string | undefined {
    if (!canManage.value) return t('notification.noManageReason');
    if (!event.featureReady) return t('notification.notReadyHint');
    if (!event.emailable) return t('notification.emailNotAvailable');
    return undefined;
}

onMounted(() => void load());
</script>
