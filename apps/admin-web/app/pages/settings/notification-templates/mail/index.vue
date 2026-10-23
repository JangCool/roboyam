<template>
    <!--
        메일 템플릿(목록, `/settings/notification-templates/mail`, 설정 > 알림·이벤트 > 템플릿 › 메일) — 메일 본문을 감싸는 겉모양 틀(머리말·꼬리말). 이메일 문구마다 이 중 하나를 고른다. 계약 `docs/features/message-template-contract.md` 「개정 4」(개정 3 의 메일 레이아웃 `…/layouts` · 그 전 `/settings/mail-layouts` — 옛 주소는 이리로 보낸다).
        쓰는 대상(개정 2)은 왼쪽 위 별도 영역(「운영자용」 · 「회원용」 · 「모두」, 운영자 먼저 — 주소 `?audience=`)에서 고르고, 고른 대상의 표만 보인다(비면 빈 안내).
        구획마다 표: 이름(머리말 띠 색 점) · 기본/사용자 · 쓰는 문구 수 · 마지막 수정 · 관리(복사 · 삭제). 행을 누르면 편집 화면(`/settings/notification-templates/mail/[id]`).
        쓰는 문구 수는 그 받는 사람의 문구 목록(문구 › 운영자 · 회원, 이메일 · 이 템플릿으로 거른 것)으로 잇는다 — 「모두」는 운영자 · 회원 수를 따로 링크한다.
        새로 만들기 → `/settings/notification-templates/mail/new`(편집 화면의 기본 — 회원 기본 템플릿 값으로 시작), 복사 → `new?from=<id>`(편집 화면에서 [저장] 때 만든다).
        기본 템플릿(회원 기본 · 운영자 기본)은 고치거나 지울 수 없다.
        삭제는 확인 후 바로 — 쓰는 문구가 있으면 단추를 잠그고 까닭을 보이며, 서버도 409 `TEMPLATE_IN_USE` 로 막는다(토스트 8초).
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage` — 없으면 단추가 숨는다.
        API 는 실제 서버(`useMailTemplateApi`)다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notificationTemplate.mailTitle')" :subtitle="t('notificationTemplate.mailSubtitle')">
            <template v-if="canManage && !denied" #actions>
                <span v-tooltip.top="limitReason || undefined" class="inline-flex">
                    <Button :label="t('notificationTemplate.newMailTemplate')" icon="pi pi-plus" size="small" :disabled="Boolean(limitReason)" @click="router.push('/settings/notification-templates/mail/new')" />
                </span>
            </template>
        </PageTitle>

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !templates" :title="t('notificationTemplate.mailTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 4" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!templates" :title="t('notificationTemplate.mailTitle')">
                <ErrorState :title="t('notificationTemplate.mailLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <div v-else class="flex min-w-0 flex-col gap-4 lg:flex-row lg:items-start">
                <!-- 쓰는 대상 — 왼쪽 위 별도 영역(운영자용 · 회원용 · 모두, 대상마다 템플릿 수). 고른 대상의 표만 오른쪽에 보인다. 좁은 화면에서는 위로 올라간다 -->
                <nav class="shrink-0 rounded-xl border border-(--line) bg-(--p-content-background) p-2 lg:w-52" :aria-label="t('notificationTemplate.mailAudienceNav')">
                    <p class="m-0 px-2 pb-1.5 pt-1 text-xs font-medium text-muted-color">{{ t('notificationTemplate.mailAudienceNav') }}</p>
                    <ul class="m-0 flex list-none flex-row gap-1 overflow-x-auto p-0 lg:flex-col">
                        <li v-for="section in sections" :key="section.audience">
                            <button
                                type="button"
                                class="flex w-full cursor-pointer items-center gap-2 whitespace-nowrap rounded-lg px-2.5 py-2 text-left text-[13px] transition-colors"
                                :class="activeAudience === section.audience ? 'bg-(--p-highlight-background) font-medium text-(--p-highlight-color)' : 'text-color hover:bg-(--p-content-hover-background)'"
                                :aria-current="activeAudience === section.audience ? 'true' : undefined"
                                @click="activeAudience = section.audience"
                            >
                                <span class="min-w-0 flex-1 truncate">{{ section.title }}</span>
                                <span class="text-xs tabular-nums text-muted-color">{{ section.items.length }}</span>
                            </button>
                        </li>
                    </ul>
                </nav>

                <div class="flex min-w-0 flex-1 flex-col gap-4">
                <AppGadget v-for="section in sections.filter((one) => one.audience === activeAudience)" :key="section.audience" :title="section.title">
                    <template #title-extra>
                        <span class="text-xs tabular-nums text-muted-color">{{ section.items.length }}</span>
                    </template>
                    <EmptyState v-if="!section.items.length" icon="pi pi-objects-column" :title="t('notificationTemplate.mailEmptyTitle')" />
                    <DataTable v-else :value="section.items" data-key="id" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                        <Column :header="t('notificationTemplate.colName')" style="min-width: 14rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 items-center gap-2">
                                    <span class="size-2.5 shrink-0 rounded-full" :class="data.headerColor ? '' : 'border border-(--line)'" :style="data.headerColor ? { background: UtilMailTemplate.colorHex(data.headerColor) ?? undefined } : undefined" :title="t(`notificationTemplate.color.${data.headerColor ?? 'NONE'}`)" aria-hidden="true" />
                                    <NuxtLink :to="editPath(data)" class="truncate text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colOrigin')" style="min-width: 7rem">
                            <template #body="{ data }">
                                <Tag v-if="data.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                <span v-else class="text-[12px]">{{ t('notificationTemplate.userTag') }}</span>
                            </template>
                        </Column>
                        <Column :header="t('notificationTemplate.colMessageCount')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <!-- 받는 사람 하나짜리는 그 문구 목록 하나, 「모두」는 운영자 · 회원 목록을 따로 잇는다 -->
                                <span v-if="messageLinks(data).length" class="flex flex-col gap-0.5">
                                    <NuxtLink v-for="link in messageLinks(data)" :key="link.audience" :to="link.to" class="text-[13px] tabular-nums text-primary hover:underline">
                                        {{ data.audience === 'ANY' ? t('notificationTemplate.messageCountOf', { audience: UtilNotificationTemplateMessages.audienceLabel(t, link.audience), count: link.count }) : t('notificationTemplate.messageCount', { count: link.count }) }}
                                    </NuxtLink>
                                </span>
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
                                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :disabled="Boolean(limitReason)" :aria-label="t('notificationTemplate.copyLabel', { name: data.name })" :aria-description="limitReason || undefined" @click="router.push({ path: '/settings/notification-templates/mail/new', query: { from: data.id } })" />
                                    </span>
                                    <span v-if="!data.builtin" v-tooltip.top="data.messageCount ? t('notificationTemplate.deleteInUse', { count: data.messageCount }) : t('common.delete')" class="inline-flex">
                                        <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="data.messageCount > 0 || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('notificationTemplate.deleteLabel', { name: data.name })" @click="confirmDelete(data)" />
                                    </span>
                                </div>
                            </template>
                        </Column>
                    </DataTable>
                </AppGadget>
                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.mailHint') }}</p>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { MailTemplateAudience, MailTemplateListItemView } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.mailTitle') });

const api = useMailTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const router = useRouter();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const templates = ref<MailTemplateListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/* ── 구획 — 쓰는 대상마다(운영자용 · 회원용 · 모두 — 운영자 먼저), 구획 안은 받은 차례 ── */

const SECTION_AUDIENCES: readonly MailTemplateAudience[] = ['OPERATOR', 'MEMBER', 'ANY'];
const sectionTitle = (audience: MailTemplateAudience) =>
    audience === 'OPERATOR' ? t('notificationTemplate.mailOperatorSection') : audience === 'MEMBER' ? t('notificationTemplate.mailMemberSection') : t('notificationTemplate.mailAnySection');
/** 왼쪽 영역에서 고른 쓰는 대상 — 주소 `?audience=` 에 남긴다(기본 운영자용) */
const route = useRoute();
const activeAudience = ref<MailTemplateAudience>(SECTION_AUDIENCES.includes(route.query.audience as MailTemplateAudience) ? (route.query.audience as MailTemplateAudience) : SECTION_AUDIENCES[0]!);
watch(activeAudience, (audience) => void router.replace({ query: { ...route.query, audience } }));
const sections = computed(() => SECTION_AUDIENCES.map((audience) => ({ audience, title: sectionTitle(audience), items: (templates.value ?? []).filter((item) => item.audience === audience) })));
/** 「쓰는 문구」 링크 — 받는 사람의 문구 목록으로, 이메일 · 이 템플릿으로 걸러 연다 */
const messageLinks = (item: MailTemplateListItemView) => UtilNotificationTemplateMessages.mailTemplateMessageLinks(item);

/** 사용자 메일 템플릿 상한(가정 20)이 차면 새로 만들기·복사를 잠근다 */
const limitReason = computed(() => ((templates.value ?? []).filter((item) => !item.builtin).length >= MAIL_TEMPLATE_LIMIT ? t('notificationTemplate.limitReached', { max: MAIL_TEMPLATE_LIMIT }) : ''));

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        templates.value = await api.list();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

const editPath = (item: MailTemplateListItemView) => `/settings/notification-templates/mail/${encodeURIComponent(item.id)}`;

/** 행을 누르면 편집 화면으로 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(editPath(event.data as MailTemplateListItemView));
}

const deletingId = ref<string | null>(null);

async function confirmDelete(item: MailTemplateListItemView): Promise<void> {
    if (deletingId.value) return;
    const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: item.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('notificationTemplate.deleted'));
        templates.value = (templates.value ?? []).filter((other) => other.id !== item.id);
    } catch (e) {
        // 목록 한 줄 조치의 실패는 토스트(사유 포함 8초 — forms.md 2-3), 서버 문구 그대로
        toast.error(toApiError(e).message, 8000);
    } finally {
        deletingId.value = null;
    }
}

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
