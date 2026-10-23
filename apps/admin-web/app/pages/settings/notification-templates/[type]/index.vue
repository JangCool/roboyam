<template>
    <!--
        Slack 템플릿 · Mattermost 템플릿(목록, `/settings/notification-templates/slack` · `…/mattermost`, 설정 > 알림·이벤트 > 템플릿 › Slack · Mattermost) — 채널에 올라가는 메시지 카드의 겉모양.
        계약 `docs/features/message-template-contract.md` 「개정 4」 유형별 템플릿 설정 칸. 주소의 마지막 조각이 종류다(`slack` · `mattermost` 밖은 없는 화면 — 404 안내).
        표: 이름(심각도별 색 점 셋) · 기본/사용자 · 쓰는 문구 수(→ 운영자 문구 목록 `?channel=<종류>&template=<id>` — Slack·Mattermost 는 운영자 이벤트만 보낸다) · 마지막 수정 · 관리(복사 · 삭제).
        기본 템플릿(코드 제공)은 하나이고 고치거나 지울 수 없다(복사해서 만든다). 새로 만들기 → `…/{종류}/new`(기본 템플릿 값으로 시작), 복사 → `new?from=<id>`(편집 화면에서 [저장] 때 만든다).
        삭제는 확인 후 바로 — 쓰는 문구가 있으면 단추를 잠그고, 서버도 409 `TEMPLATE_IN_USE`(토스트 8초). 실제 글을 올리는 곳(봇 토큰·채널)은 알림 도구 화면이다 — 안내 한 줄과 링크.
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage`. ⚠ MOCK: API 는 메모리 목업이다(`useChatTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="title" :subtitle="type ? t('notificationTemplate.chatSubtitle', { tool: toolName }) : ''">
            <template v-if="type && canManage && !denied" #actions>
                <span v-tooltip.top="limitReason || undefined" class="inline-flex">
                    <Button :label="t('notificationTemplate.newChatTemplate', { tool: toolName })" icon="pi pi-plus" size="small" :disabled="Boolean(limitReason)" @click="router.push(UtilNotificationTemplateMessages.templatePath(kind, 'new'))" />
                </span>
            </template>
        </PageTitle>

        <AppGadget v-if="!type" :title="t('notificationTemplate.title')">
            <ErrorState code="404" :title="t('notificationTemplate.notFound')" />
        </AppGadget>

        <Message v-else-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !templates" :title="title">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!templates" :title="title">
                <ErrorState :title="t('notificationTemplate.chatLoadFailed', { tool: toolName })" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else :title="title">
                <template #title-extra>
                    <span class="text-xs tabular-nums text-muted-color">{{ templates.length }}</span>
                </template>
                <EmptyState v-if="!templates.length" icon="pi pi-palette" :title="t('notificationTemplate.chatEmptyTitle')" />
                <DataTable v-else :value="templates" data-key="id" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                    <Column :header="t('notificationTemplate.colName')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 items-center gap-2">
                                <span class="flex shrink-0 items-center gap-0.5" aria-hidden="true">
                                    <span v-for="severity in CHAT_SEVERITIES" :key="severity" class="h-3.5 w-1 rounded-full" :style="{ background: UtilChatTemplate.colorHex(data.colors[severity]) }" />
                                </span>
                                <NuxtLink :to="UtilNotificationTemplateMessages.templatePath(kind, data.id)" class="truncate text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.colOrigin')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <Tag v-if="data.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="template-tag text-[10px]!" />
                            <span v-else class="text-[12px]">{{ t('notificationTemplate.userTag') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.colMessageCount')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <NuxtLink v-if="data.messageCount" :to="UtilNotificationTemplateMessages.templateMessageLink(type as ChatTemplateType, data.id)" class="text-[13px] tabular-nums text-primary hover:underline">{{ t('notificationTemplate.messageCount', { count: data.messageCount }) }}</NuxtLink>
                            <span v-else class="text-[12px] text-muted-color">{{ t('notificationTemplate.noMessages') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.colUpdated')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span v-if="data.updatedAt" class="text-[12px]">
                                <span class="tabular-nums">{{ time.listLabel(data.updatedAt) }}</span>
                                <span v-if="data.updatedByName" class="block text-[11px] text-muted-color">{{ data.updatedByName }}</span>
                            </span>
                            <span v-else class="text-[12px] text-muted-color">{{ t('notificationTemplate.builtinUpdated') }}</span>
                        </template>
                    </Column>
                    <Column v-if="canManage" :header="t('notificationTemplate.colActions')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <div class="flex items-center gap-0.5">
                                <span v-tooltip.top="limitReason || t('notificationTemplate.copy')" class="inline-flex">
                                    <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :disabled="Boolean(limitReason)" :aria-label="t('notificationTemplate.copyLabel', { name: data.name })" :aria-description="limitReason || undefined" @click="router.push({ path: UtilNotificationTemplateMessages.templatePath(kind, 'new'), query: { from: data.id } })" />
                                </span>
                                <span v-if="!data.builtin" v-tooltip.top="data.messageCount ? t('notificationTemplate.deleteInUse', { count: data.messageCount }) : t('common.delete')" class="inline-flex">
                                    <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="data.messageCount > 0 || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('notificationTemplate.deleteLabel', { name: data.name })" @click="confirmDelete(data)" />
                                </span>
                            </div>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
            <p class="m-0 text-xs text-muted-color">
                {{ t('notificationTemplate.chatHint', { tool: toolName }) }}
                <NuxtLink to="/settings/notification-channels" class="ml-1 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
            </p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { ChatTemplateListItemView, ChatTemplateType } from '~/composables/template/api/useChatTemplateApi.types';
import { CHAT_SEVERITIES, CHAT_TEMPLATE_LIMIT } from '~/composables/template/api/useChatTemplateApi.types';
import UtilChatTemplate from '~/utils/util-chat-template';
import type { TemplateKind } from '~/utils/util-notification-template-messages';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();

const api = useChatTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

/** 주소의 종류 조각 — `slack` · `mattermost` 만(그 밖은 없는 화면) */
const type = computed<ChatTemplateType | null>(() => (route.params.type === 'slack' ? 'SLACK' : route.params.type === 'mattermost' ? 'MATTERMOST' : null));
const kind = computed<TemplateKind>(() => (type.value === 'MATTERMOST' ? 'mattermost' : 'slack'));
const toolName = computed(() => UtilNotificationTemplateMessages.templateKindLabel(t, kind.value));
const title = computed(() => (type.value ? t('notificationTemplate.chatTitle', { tool: toolName.value }) : t('notificationTemplate.title')));
useSeoMeta({ title: () => title.value });

const templates = ref<ChatTemplateListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/** 사용자 템플릿 상한(가정 20)이 차면 새로 만들기·복사를 잠근다 */
const limitReason = computed(() => ((templates.value ?? []).filter((item) => !item.builtin).length >= CHAT_TEMPLATE_LIMIT ? t('notificationTemplate.limitReached', { max: CHAT_TEMPLATE_LIMIT }) : ''));

async function load(): Promise<void> {
    if (!type.value) return;
    loading.value = true;
    loadError.value = '';
    try {
        templates.value = await api.list(type.value);
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

/** 행을 누르면 편집 화면으로 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(UtilNotificationTemplateMessages.templatePath(kind.value, (event.data as ChatTemplateListItemView).id));
}

const deletingId = ref<string | null>(null);

async function confirmDelete(item: ChatTemplateListItemView): Promise<void> {
    if (deletingId.value || !type.value) return;
    const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: item.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = item.id;
    try {
        await api.remove(type.value, item.id);
        toast.success(t('notificationTemplate.deleted'));
        templates.value = (templates.value ?? []).filter((other) => other.id !== item.id);
    } catch (e) {
        // 목록 한 줄 조치의 실패는 토스트(사유 포함 8초 — forms.md 2-3), 서버 문구 그대로
        toast.error(toApiError(e).message, 8000);
    } finally {
        deletingId.value = null;
    }
}

// 같은 화면에서 종류만 바뀌면(Slack ↔ Mattermost 메뉴) 다시 받는다
watch(type, () => {
    templates.value = null;
    denied.value = false;
    void load();
});

onMounted(load);
</script>

<style scoped>
/* 뱃지 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.template-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
