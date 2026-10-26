<template>
    <!-- 일정 만들기·수정에서 폼 대신 보이는 안내 — 기다림 · 소모임/일정 문제 · 입구가 막힘(로그인·멤버 아님·관리 권한 없음·쓰기가 멈춤) · 끝난/취소된 일정 -->
    <div v-if="view === 'loading'" role="status" :aria-label="t('common.loading')" class="flex flex-col gap-3" data-testid="event-form-loading">
        <div class="h-9 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        <div class="h-11 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        <div class="h-11 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
        <div class="h-32 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
    </div>

    <ErrorState v-else-if="view === 'notfound'" code="404" :title="t('group.notFoundTitle')" :description="t('group.notFoundText')" data-testid="event-form-notfound">
        <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
    </ErrorState>

    <ErrorState v-else-if="view === 'inactive'" :title="t('group.inactiveTitle')" :description="t('group.inactiveText')" data-testid="event-form-inactive">
        <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" />
    </ErrorState>

    <ErrorState v-else-if="view === 'error'" :title="t('group.loadError')" :description="groupError" retry data-testid="event-form-error" @retry="emit('retry-group')">
        <Button as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.backToList')" severity="secondary" variant="outlined" />
    </ErrorState>

    <ErrorState v-else-if="view === 'event-notfound'" code="404" :title="t('groupEvent.notFoundTitle')" :description="t('groupEvent.notFoundText')" data-testid="event-form-event-notfound">
        <Button as="router-link" :to="tabPath" :label="t('groupEvent.backToTab')" />
    </ErrorState>

    <ErrorState v-else-if="view === 'event-error'" :title="t('groupEvent.gateEventError')" :description="eventError" retry data-testid="event-form-event-error" @retry="emit('retry-event')">
        <Button as="router-link" :to="tabPath" :label="t('groupEvent.backToTab')" severity="secondary" variant="outlined" />
    </ErrorState>

    <!-- 이미 끝났거나 취소된 일정은 고칠 수 없다 — 일정 상세로 돌아가는 길 -->
    <ErrorState
        v-else-if="view === 'ended' || view === 'canceled'"
        :title="view === 'ended' ? t('groupEvent.gateEndedTitle') : t('groupEvent.gateCanceledTitle')"
        :description="view === 'ended' ? t('groupEvent.gateEndedText') : t('groupEvent.gateCanceledText')"
        :data-testid="`event-form-${view}`"
    >
        <Button as="router-link" :to="eventPath" :label="t('groupEvent.backToEvent')" />
    </ErrorState>

    <!-- 입구가 막혔다 -->
    <StatusPanel v-else-if="gate" :icon="gate.icon" :tone="view === 'login' ? 'info' : 'warn'" :title="gate.copy.title" :data-testid="`event-form-${view}`">
        {{ gate.copy.text }}
        <template #actions>
            <Button v-if="view === 'login'" as="router-link" :to="UtilRedirect.toLogin(route.fullPath)" :label="t('common.login')" fluid />
            <Button v-if="view === 'blocked' && blocked === 'EMAIL_NOT_VERIFIED'" as="router-link" to="/verify-email" :label="t('login.verifyGuide')" fluid />
            <Button
                as="router-link"
                :to="view === 'not-manager' ? tabPath : groupPath"
                :label="view === 'not-manager' ? t('groupEvent.backToTab') : t('group.backToGroup')"
                :severity="secondaryBack ? 'secondary' : undefined"
                :variant="secondaryBack ? 'outlined' : undefined"
                fluid
            />
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
import type { GroupManageBlockedReason } from '~/composables/group/api/useGroupApi.types';
import type { GroupEventFormView } from '~/composables/group/useGroupEventForm';
import UtilGroup from '~/utils/util-group';
import UtilGroupEvent from '~/utils/util-group-event';
import UtilGroupEventMessages, { type GroupEventFormKind } from '~/utils/util-group-event-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 일정 만들기·수정에서 폼 대신 보이는 안내(community `GroupEventGate`). 어느 갈래인지는 `useGroupEventForm` 의 `view` 가 정하고, 폼이 보일 때(`form`)는 그리지 않는다.
 * 로그인 단추는 지금 주소로 돌아오게 한다. 끝난/취소된 일정은 그 일정의 상세로, 관리 권한이 없으면 일정 탭으로, 그 밖에는 소모임으로 돌아가는 길을 준다.
 */
interface Props {
    view: Exclude<GroupEventFormView, 'form'>;
    kind: GroupEventFormKind;
    groupId: string;
    /** 고치는 화면의 일정 ID */
    eventId?: string;
    groupError?: string;
    eventError?: string;
    /** 소모임 안 역할 — 관리 권한이 없을 때 「맡기지 않은 일」(매니저)인지 가른다 */
    role?: string | null;
    blocked?: GroupManageBlockedReason | null;
}

const props = withDefaults(defineProps<Props>(), {
    eventId: '',
    groupError: '',
    eventError: '',
    role: null,
    blocked: null,
});

const emit = defineEmits<{ 'retry-group': []; 'retry-event': [] }>();

const { t } = useI18n();
const route = useRoute();

const groupPath = computed(() => UtilGroup.detailPath(props.groupId));
const tabPath = computed(() => UtilGroupEvent.tabPath(props.groupId));
const eventPath = computed(() => (props.eventId ? UtilGroupEvent.eventPath(props.groupId, props.eventId) : tabPath.value));
/** 돌아가기 단추가 둘째 단추인가(첫 단추가 따로 있다) */
const secondaryBack = computed(() => props.view === 'login' || (props.view === 'blocked' && props.blocked === 'EMAIL_NOT_VERIFIED'));

const gate = computed(() => {
    const v = props.view;
    if (v !== 'login' && v !== 'not-member' && v !== 'not-manager' && v !== 'blocked') return null;
    const icon = v === 'login' ? 'pi pi-sign-in' : v === 'blocked' && props.blocked === 'EMAIL_NOT_VERIFIED' ? 'pi pi-envelope' : 'pi pi-lock';
    return { icon, copy: UtilGroupEventMessages.gate(t, v, props.kind, props.role, props.blocked) };
});
</script>
