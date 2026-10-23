<template>
    <!--
        이벤트 상세(회원·운영자 공통 — `/settings/notifications/member/[code]` · `/settings/notifications/operator/[code]`). 계약 `docs/features/message-template-contract.md` 「개정 3」 이벤트 화면 · 「개정 4」 채널·용어(문구).
        ① 이벤트 구획 — 설명 · 받는 사람 · 수신 규칙(운영자만) · 지금 설정(사용 · 운영자는 전달 도구·추가 수신 팀, 회원은 채널·회원이 끌 수 있는지). **보여 주기만 한다** — 바꾸기는 목록에서(목록의 즉시 저장과 두 곳에서 같은 값을 고치지 않게).
           운영자 이벤트는 실제 서버(`GET /api/admin/notification-events` 의 한 줄 · 도구 이름 `useNotificationChannels` · 팀 이름 `useTeamApi` — `team.read` 가 있을 때만), 회원 이벤트는 목업(`useMemberEventApi`).
        ② 채널마다 구획(운영자 = 앱 알림·이메일·Slack·Mattermost·카카오톡 — 본인에게만 가는 이벤트는 Slack·Mattermost 없음, 회원 = 앱 알림·이메일·카카오톡): 언어별 쓸 문구 + 특수 상황 규칙(`MessageChannelSelection`) + 「이 채널의 문구 보기」·「새 문구」.
           그 채널로 지금 보내지 않으면(목록에서 꺼짐 · 이메일이 서버 단계에서 열림 · Slack·Mattermost 는 그 종류의 도구를 골랐을 때만) 안내 한 줄 — 문구는 미리 정해 둘 수 있다.
           카카오톡은 준비 중(알림 도구에서 카카오가 업체 계약 전) — 구획을 흐리게 두고 고르기 칸 없이 안내 · 「이 채널의 문구 보기」만(저장에 넣지 않는다).
        저장은 채널 전부(준비 중 제외)를 `PUT targets/EVENT/{code}` 한 번으로 → 하단 떠 있는 저장 줄 하나(`StickySaveBar`). 문구 대상이 없으면(준비 중 회원 이벤트) 안내만, `template.read` 가 없으면 ②를 감추고 안내.
        권한: 화면 `notification.read`(메뉴가 거른다) · 문구 고르기 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: 문구·회원 이벤트 API 는 메모리 목업이다(`useNotificationMessageApi` · `useMemberEventApi`). 운영자 이벤트 정보는 실제 API.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !info">
            <PageTitle :title="listTitle" />
            <div class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="9rem" />
            </div>
        </template>

        <template v-else-if="!info">
            <PageTitle :title="listTitle">
                <template #actions>
                    <Button :label="t('notification.eventDetail.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <AppGadget :title="listTitle">
                <ErrorState v-if="notFound" code="404" :title="t('notification.eventDetail.notFound')" :description="loadError">
                    <Button :label="t('notification.eventDetail.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </ErrorState>
                <ErrorState v-else :title="t('notification.eventDetail.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="info.name">
                <template #actions>
                    <Button :label="t('notification.eventDetail.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="t('notificationTemplate.audienceTag', { audience: UtilNotificationTemplateMessages.audienceLabel(t, audienceCode) })" :icon="UtilNotificationTemplateMessages.audienceIcon(audienceCode)" severity="secondary" class="mail-template-tag text-[11px]!" />
                <span class="text-xs text-muted-color">{{ UtilNotificationMessages.areaLabel(t, info.area) }}</span>
                <NotificationSeverityTag v-if="operatorEvent" :kind="operatorEvent.severity" />
                <NotificationFlagTag v-if="operatorEvent?.mandatory" kind="mandatory" />
                <NotificationFlagTag v-if="!info.featureReady" kind="notReady" />
                <code class="text-[11px] text-muted-color">{{ info.code }}</code>
            </div>

            <!-- ① 이벤트 — 보여 주기만 -->
            <AppGadget :title="t('notification.eventDetail.infoTitle')" anchor="event-info">
                <template #actions>
                    <NuxtLink :to="listPath" class="text-xs text-primary hover:underline">{{ t('notification.eventDetail.openList') }}</NuxtLink>
                </template>
                <div class="flex flex-col gap-3 text-[13px]">
                    <p class="m-0 leading-relaxed">{{ info.description }}</p>
                    <Message v-if="!info.featureReady" severity="secondary" :closable="false" size="small">{{ messagesNone ? t('notification.notReadyHint') : t('notification.eventDetail.notReady') }}</Message>
                    <dl class="m-0 grid gap-x-6 gap-y-2 sm:grid-cols-[max-content_minmax(0,1fr)]">
                        <dt class="text-muted-color">{{ t('notification.eventDetail.recipients') }}</dt>
                        <dd class="m-0">{{ info.recipientText }}</dd>
                        <template v-if="operatorEvent">
                            <dt class="text-muted-color">{{ t('notification.eventDetail.recipientRule') }}</dt>
                            <dd class="m-0 text-xs leading-relaxed text-muted-color">{{ t('notification.eventDetail.recipientRuleText') }}</dd>
                        </template>
                        <dt class="text-muted-color">{{ t('notification.eventDetail.enabled') }}</dt>
                        <dd class="m-0">{{ info.enabled ? t('notification.eventDetail.on') : t('notification.eventDetail.off') }}</dd>
                        <template v-if="operatorEvent">
                            <dt class="text-muted-color">{{ t('notification.eventDetail.delivery') }}</dt>
                            <dd class="m-0 flex flex-wrap items-center gap-1.5">
                                <NotificationChannelTypeTag type="DRAWER" />
                                <span v-for="name in deliveryNames" :key="name" class="text-[12px]">{{ name }}</span>
                            </dd>
                            <dt class="text-muted-color">{{ t('notification.eventDetail.teams') }}</dt>
                            <dd class="m-0 text-[12px]">{{ teamText }}</dd>
                        </template>
                        <template v-else-if="memberEvent">
                            <dt class="text-muted-color">{{ t('notification.eventDetail.channels') }}</dt>
                            <dd class="m-0 text-[12px]">{{ memberChannelText }}</dd>
                            <dt class="text-muted-color">{{ t('notification.eventDetail.memberCanTurnOff') }}</dt>
                            <dd class="m-0 text-[12px]">{{ memberEvent.lockReason ? t(`notification.memberEvents.lock${memberEvent.lockReason}`) : memberEvent.memberCanTurnOff ? t('notification.eventDetail.on') : t('notification.eventDetail.off') }}</dd>
                        </template>
                        <dt class="text-muted-color">{{ t('notification.eventDetail.sent') }}</dt>
                        <dd class="m-0 tabular-nums">{{ info.featureReady ? t('notification.sentCount', { n: info.sentLast30Days.toLocaleString() }) : '—' }}</dd>
                    </dl>
                    <p class="m-0 text-xs text-muted-color">{{ operatorEvent ? t('notification.eventDetail.changeInList') : t('notification.eventDetail.changeInListMember') }}</p>
                </div>
            </AppGadget>

            <!-- ② 채널별 문구 -->
            <Message v-if="messagesDenied" severity="info" :closable="false" size="small">{{ t('notification.eventDetail.messagesDenied') }}</Message>
            <AppGadget v-else-if="messagesNone" :title="t('notificationMessage.channelsTitle')">
                <EmptyState icon="pi pi-file" :title="t('notification.eventDetail.messagesNone')" />
            </AppGadget>
            <AppGadget v-else-if="messagesError" :title="t('notificationMessage.channelsTitle')">
                <ErrorState :title="t('notification.eventDetail.messagesLoadFailed')" :description="messagesError" retry :retrying="loading" @retry="load" />
            </AppGadget>
            <form v-else-if="target" class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notification.eventDetail.messagesReadOnly') }}</Message>

                <AppGadget
                    v-for="channel in target.channels"
                    :key="channel.channel"
                    :title="UtilNotificationTemplateMessages.channelLabel(t, channel.channel)"
                    :anchor="`channel-${channel.channel.toLowerCase().replace('_', '-')}`"
                    :changes="changesOf(channel.channel)"
                    :invalid="invalidOf(channel.channel)"
                    :class="isPreparing(channel.channel) ? 'opacity-70' : ''"
                >
                    <template v-if="isPreparing(channel.channel)" #title-extra>
                        <Tag :value="t('notificationMessage.preparingTag')" severity="secondary" class="mail-template-tag text-[10px]!" />
                    </template>
                    <template #actions>
                        <NuxtLink :to="{ path: UtilNotificationTemplateMessages.messageListPath(audienceCode), query: { target: info.code, channel: channel.channel } }" class="text-xs text-primary hover:underline">{{ t('notification.eventDetail.showMessages') }}</NuxtLink>
                        <NuxtLink v-if="canManage && !isPreparing(channel.channel)" :to="{ path: UtilNotificationTemplateMessages.messagePath('new'), query: { audience: audienceCode, target: info.code, channel: channel.channel } }" class="ml-3 text-xs text-primary hover:underline">{{ t('notification.eventDetail.newMessage') }}</NuxtLink>
                    </template>
                    <div class="flex flex-col gap-3">
                        <Message v-if="channelNote(channel.channel)" severity="secondary" :closable="false" size="small">
                            <span>{{ channelNote(channel.channel) }}</span>
                            <NuxtLink v-if="isPreparing(channel.channel)" to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
                        </Message>
                        <MessageChannelSelection
                            v-if="models[channel.channel]"
                            :model-value="models[channel.channel] as MessageSelectionModel"
                            :messages="channel.messages"
                            :conditions="summaryOf(channel.channel)?.conditions ?? []"
                            :editable="canManage"
                            :saving="saving"
                            :errors="errorsOf(channel.channel)"
                            @update:model-value="(value: MessageSelectionModel) => setModel(channel.channel, value)"
                        />
                    </div>
                </AppGadget>

                <Message v-if="operatorEvent && !hasChannel('SLACK') && !hasChannel('MATTERMOST')" severity="secondary" :closable="false" size="small">
                    <span class="inline-flex items-center gap-1.5"><i class="pi pi-comments text-xs" aria-hidden="true" />{{ t('notification.eventDetail.noChat') }}</span>
                </Message>

                <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="invalidTotal > 0" :count="changesTotal" :error-count="invalidTotal" :error="formError" @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MemberEventView } from '~/composables/notification/api/useMemberEventApi.types';
import type { NotificationEventView } from '~/composables/notification/api/useNotificationEventApi.types';
import type { TeamView } from '~/composables/team/api/useTeamApi.types';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { MessageAudience, MessageChannel, MessageTargetDetailView } from '~/composables/template/api/useNotificationMessageApi.types';
import type { MessageSelectionModel } from '~/utils/util-notification-message';
import UtilNotificationChannelMessages from '~/utils/util-notification-channel-messages';
import UtilNotificationMessages from '~/utils/util-notification-messages';
import UtilNotificationMessage from '~/utils/util-notification-message';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();

const messageApi = useNotificationMessageApi();
const eventApi = useNotificationEventApi();
const memberEventApi = useMemberEventApi();
const teamApi = useTeamApi();
const { channels: tools, load: loadTools } = useNotificationChannels();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const route = useRoute();
const router = useRouter();
const { confirm } = useConfirmDialog();
const { can: realCan } = useAdminPermission();
// MOCK: 문구 권한은 목업 스위치(`?mock=template-readonly|template-denied`)로 흉내 낸다 — 서버가 붙으면 `realCan` 하나로 바꾼다
const can = mockTemplateCan(realCan);
// REAL: const can = realCan;

const canManage = computed(() => can('template.manage'));

/** 주소의 받는 사람 조각 — `member` · `operator` 만(그 밖은 없는 이벤트) */
const audienceCode = computed<MessageAudience>(() => (route.params.audience === 'member' ? 'MEMBER' : 'OPERATOR'));
const audienceValid = computed(() => route.params.audience === 'member' || route.params.audience === 'operator');
const code = computed(() => String(route.params.code ?? ''));
const listPath = computed(() => (audienceCode.value === 'MEMBER' ? '/settings/notifications/member' : '/settings/notifications/operator'));
const listTitle = computed(() => (audienceCode.value === 'MEMBER' ? t('notification.memberEvents.title') : t('notification.adminTitle')));

/* ── 받은 것 ── */

const operatorEvent = ref<NotificationEventView | null>(null);
const memberEvent = ref<MemberEventView | null>(null);
const teams = ref<TeamView[] | null>(null);
const target = ref<MessageTargetDetailView | null>(null);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const messagesDenied = ref(false);
const messagesNone = ref(false);
const messagesError = ref('');

/** 이벤트 정보 — 두 받는 사람의 같은 칸 */
const info = computed(() => operatorEvent.value ?? memberEvent.value);

useSeoMeta({ title: () => info.value?.name ?? listTitle.value });

/** 고른 전달 도구 이름(서랍은 딱지로 따로) */
const deliveryNames = computed(() => {
    const event = operatorEvent.value;
    if (!event) return [];
    const names = event.channels.map((id) => {
        const tool = (tools.value ?? []).find((item) => item.channelId === id);
        return tool ? UtilNotificationChannelMessages.displayName(t, tool) : id;
    });
    return names.length ? names : [];
});
const teamText = computed(() => {
    const ids = operatorEvent.value?.teamIds ?? [];
    if (!ids.length) return t('notification.eventDetail.none');
    if (!teams.value) return t('notification.teamsUnknown', { n: ids.length });
    return ids.map((id) => teams.value?.find((team) => team.teamId === id)?.teamName ?? id).join(', ');
});
const memberChannelText = computed(() => {
    const event = memberEvent.value;
    if (!event) return '';
    const on = [event.inApp ? t('notificationMessage.channel.IN_APP') : '', event.email ? t('notificationMessage.channel.EMAIL') : ''].filter(Boolean);
    return on.length ? on.join(' · ') : t('notification.eventDetail.none');
});

/* ── 채널 ── */

const models = ref<Partial<Record<MessageChannel, MessageSelectionModel>>>({});
/** 서버 칸 문구 — 채널마다(이 채널 안 경로로 뗀 것) */
const serverErrors = ref<Partial<Record<MessageChannel, Record<string, string>>>>({});
const formError = ref('');
const saving = ref(false);

const hasChannel = (channel: MessageChannel) => Boolean(target.value?.channels.some((item) => item.channel === channel));
const summaryOf = (channel: MessageChannel) => target.value?.summary.channels.find((item) => item.channel === channel);
/** 준비 중 채널(카카오톡) — 고르기 칸 없이 안내만, 저장에 넣지 않는다 */
const isPreparing = (channel: MessageChannel) => Boolean(summaryOf(channel)?.preparing);
const savedOf = (channel: MessageChannel) => target.value?.channels.find((item) => item.channel === channel);

function setModel(channel: MessageChannel, value: MessageSelectionModel): void {
    models.value = { ...models.value, [channel]: value };
    if (serverErrors.value[channel]) serverErrors.value = { ...serverErrors.value, [channel]: {} };
}

const errorsOf = (channel: MessageChannel) => serverErrors.value[channel] ?? {};
function changesOf(channel: MessageChannel): number {
    const saved = savedOf(channel);
    const model = models.value[channel];
    return saved && model ? UtilNotificationMessage.selectionChanges(saved, model) : 0;
}
function invalidOf(channel: MessageChannel): number {
    const model = models.value[channel];
    if (!model) return 0;
    const server = Object.keys(errorsOf(channel)).filter((key) => errorsOf(channel)[key]);
    return new Set([...UtilNotificationMessage.selectionProblems(model), ...server]).size;
}
const channelList = computed(() => (target.value?.channels ?? []).map((item) => item.channel));
const changesTotal = computed(() => channelList.value.reduce((sum, channel) => sum + changesOf(channel), 0));
const invalidTotal = computed(() => channelList.value.reduce((sum, channel) => sum + invalidOf(channel), 0));
const dirty = computed(() => changesTotal.value > 0);

/** 그 채널로 지금 보내지 않으면 까닭 한 줄 — 문구는 미리 정해 둘 수 있다. 준비 중 채널은 준비 중 안내 */
function channelNote(channel: MessageChannel): string {
    if (isPreparing(channel)) return t('notification.eventDetail.preparing', { channel: UtilNotificationTemplateMessages.channelLabel(t, channel) });
    const op = operatorEvent.value;
    if (op) {
        if (channel === 'EMAIL') {
            // MOCK 표시: 서버 단계에서 `emailable` 을 준비 중이 아닌 모든 이벤트로 넓힌다(개정 3)
            if (!op.emailable) return t('notification.eventDetail.emailLater');
            if (!op.channels.includes('email')) return t('notification.eventDetail.channelOff');
        }
        if (channel === 'SLACK' || channel === 'MATTERMOST') {
            // 그 종류의 알림 도구를 「전달 도구」로 골랐을 때만 그 채널로 올라간다
            const picked = op.channels.some((id) => (tools.value ?? []).some((tool) => tool.channelId === id && tool.type === channel));
            const how = t('notification.eventDetail.chatTools', { tool: UtilNotificationTemplateMessages.channelLabel(t, channel) });
            return picked ? how : `${t('notification.eventDetail.channelOff')} ${how}`;
        }
        return '';
    }
    const member = memberEvent.value;
    if (member && ((channel === 'IN_APP' && !member.inApp) || (channel === 'EMAIL' && !member.email))) return t('notification.eventDetail.channelOff');
    return '';
}

function fillTarget(view: MessageTargetDetailView): void {
    target.value = view;
    const next: Partial<Record<MessageChannel, MessageSelectionModel>> = {};
    // 준비 중 채널은 고르기 칸이 없다(저장에도 넣지 않는다)
    for (const channel of view.channels) if (!view.summary.channels.find((item) => item.channel === channel.channel)?.preparing) next[channel.channel] = UtilNotificationMessage.toSelection(channel);
    models.value = next;
    serverErrors.value = {};
    formError.value = '';
}

/* ── 읽기 ── */

async function loadInfo(): Promise<void> {
    if (!audienceValid.value) throw Object.assign(new Error(t('notification.eventDetail.notFound')), { status: 404 });
    if (audienceCode.value === 'OPERATOR') {
        // 실제 API — 한 줄 조회가 없어 목록에서 찾는다. 도구 이름은 공유 상태, 팀 이름은 `team.read` 가 있을 때만
        const [list] = await Promise.all([eventApi.list(), tools.value ? Promise.resolve() : loadTools()]);
        if (realCan('team.read')) teams.value = await teamApi.list().catch(() => null);
        const found = list.find((event) => event.code === code.value);
        if (!found) throw Object.assign(new Error(t('notification.eventDetail.notFound')), { status: 404 });
        operatorEvent.value = found;
        memberEvent.value = null;
    } else {
        const found = (await memberEventApi.list()).find((event) => event.code === code.value);
        if (!found) throw Object.assign(new Error(t('notification.eventDetail.notFound')), { status: 404 });
        memberEvent.value = found;
        operatorEvent.value = null;
    }
}

async function loadTarget(): Promise<void> {
    messagesDenied.value = false;
    messagesNone.value = false;
    messagesError.value = '';
    try {
        fillTarget(await messageApi.target('EVENT', code.value));
    } catch (e) {
        const error = toApiError(e);
        target.value = null;
        if (error.status === 403) messagesDenied.value = true;
        else if (error.status === 404) messagesNone.value = true;
        else messagesError.value = error.message;
    }
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    try {
        // 이벤트 정보와 템플릿을 함께 받는다 — 템플릿이 막혀도(403·없음) 정보는 보인다
        const [infoResult] = await Promise.allSettled([loadInfo(), loadTarget()]);
        if (infoResult.status === 'rejected') {
            const reason = infoResult.reason as { status?: number; message?: string };
            const error = reason?.status === 404 ? null : toApiError(infoResult.reason);
            notFound.value = reason?.status === 404 || error?.status === 404;
            loadError.value = error?.message ?? reason?.message ?? '';
            operatorEvent.value = null;
            memberEvent.value = null;
        }
    } finally {
        loading.value = false;
    }
}

/* ── 저장 · 되돌리기 ── */

function revert(): void {
    if (target.value) fillTarget(target.value);
}

async function save(): Promise<void> {
    if (saving.value || !canManage.value || !dirty.value || invalidTotal.value > 0 || !target.value) return;
    saving.value = true;
    formError.value = '';
    const body: Partial<Record<MessageChannel, ReturnType<typeof UtilNotificationMessage.toSaveRequest>>> = {};
    for (const channel of channelList.value) {
        const model = models.value[channel];
        if (model) body[channel] = UtilNotificationMessage.toSaveRequest(model);
    }
    try {
        fillTarget(await messageApi.saveTarget('EVENT', code.value, { channels: body }));
        toast.success(t('notification.eventDetail.saved'));
    } catch (e) {
        const error = toApiError(e);
        const found: Partial<Record<MessageChannel, Record<string, string>>> = {};
        let placed = 0;
        for (const [key, message] of Object.entries(error.fields ?? {})) {
            const match = /^channels\.(IN_APP|EMAIL|SLACK|MATTERMOST|KAKAO)\.(.+)$/.exec(key);
            if (!match) continue;
            const channel = match[1] as MessageChannel;
            found[channel] = { ...(found[channel] ?? {}), [match[2] as string]: message };
            placed += 1;
        }
        serverErrors.value = found;
        // 칸에 붙지 않는 오류만 저장 줄 안 한 줄 — 서버 문구 그대로
        if (placed === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});

onMounted(load);
</script>

<style scoped>
/* 뱃지 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.mail-template-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
