<template>
    <!--
        메일 레이아웃(목록, `/settings/notification-templates/layouts`, 설정 > 알림·이벤트 > 알림 템플릿 › 메일 레이아웃) — 메일 본문을 감싸는 틀(머리말·꼬리말). 이메일 템플릿마다 이 중 하나를 고른다. 계약 `docs/features/message-template-contract.md` 「개정」 4 · 「개정 3」(위치만 옮겼다 — 개정 전 `/settings/mail-layouts`, 옛 주소는 이리로 보낸다).
        쓰는 대상(개정 2)을 고르지 않는다 — 「운영자용」 · 「회원용」 · 「모두」 구획(운영자 먼저)으로 나눠 보인다(빈 구획은 빈 안내).
        구획마다 표: 이름(머리말 띠 색 점) · 기본/사용자 · 쓰는 템플릿 수 · 마지막 수정 · 관리(복사 · 삭제). 행을 누르면 편집 화면(`/settings/notification-templates/layouts/[id]`).
        쓰는 템플릿 수는 그 받는 사람의 템플릿 목록(운영자 템플릿 · 회원 템플릿, 이메일 · 이 레이아웃으로 거른 것)으로 잇는다 — 「모두」는 운영자 · 회원 수를 따로 링크한다.
        새로 만들기 → `/settings/notification-templates/layouts/new`(편집 화면의 기본 — 회원 기본 레이아웃 값으로 시작), 복사 → `new?from=<id>`(편집 화면에서 [저장] 때 만든다).
        기본 레이아웃(회원 기본 · 운영자 기본)은 고치거나 지울 수 없다.
        삭제는 확인 후 바로 — 쓰는 템플릿이 있으면 단추를 잠그고 까닭을 보이며, 서버도 409 `LAYOUT_IN_USE` 로 막는다(토스트 8초).
        권한: 화면 `template.read`(메뉴가 거른다) · 만들기·복사·삭제 `template.manage` — 없으면 단추가 숨는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailLayoutApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notificationTemplate.layoutsTitle')" :subtitle="t('notificationTemplate.layoutsSubtitle')">
            <template v-if="canManage && !denied" #actions>
                <span v-tooltip.top="limitReason || undefined" class="inline-flex">
                    <Button :label="t('notificationTemplate.newLayout')" icon="pi pi-plus" size="small" :disabled="Boolean(limitReason)" @click="router.push('/settings/notification-templates/layouts/new')" />
                </span>
            </template>
        </PageTitle>

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !layouts" :title="t('notificationTemplate.layoutsTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 4" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!layouts" :title="t('notificationTemplate.layoutsTitle')">
                <ErrorState :title="t('notificationTemplate.layoutsLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <template v-else>
                <!-- 쓰는 대상마다 구획 하나(운영자용 · 회원용 · 모두) — 쓰는 대상을 고르지 않는다 -->
                <AppGadget v-for="section in sections" :key="section.audience" :title="section.title">
                    <template #title-extra>
                        <span class="text-xs tabular-nums text-muted-color">{{ section.items.length }}</span>
                    </template>
                    <EmptyState v-if="!section.items.length" icon="pi pi-objects-column" :title="t('notificationTemplate.layoutsEmptyTitle')" />
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
                        <Column :header="t('notificationTemplate.colTemplateCount')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <!-- 받는 사람 하나짜리는 그 목록 하나, 「모두」는 운영자 · 회원 목록을 따로 잇는다 -->
                                <span v-if="templateLinks(data).length" class="flex flex-col gap-0.5">
                                    <NuxtLink v-for="link in templateLinks(data)" :key="link.audience" :to="link.to" class="text-[13px] tabular-nums text-primary hover:underline">
                                        {{ data.audience === 'ANY' ? t('notificationTemplate.templateCountOf', { audience: UtilNotificationTemplateMessages.audienceLabel(t, link.audience), count: link.count }) : t('notificationTemplate.templateCount', { count: link.count }) }}
                                    </NuxtLink>
                                </span>
                                <span v-else class="text-[12px] text-muted-color">{{ t('notificationTemplate.noTemplates') }}</span>
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
                                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="text" rounded :disabled="Boolean(limitReason)" :aria-label="t('notificationTemplate.copyLabel', { name: data.name })" :aria-description="limitReason || undefined" @click="router.push({ path: '/settings/notification-templates/layouts/new', query: { from: data.id } })" />
                                    </span>
                                    <span v-if="!data.builtin" v-tooltip.top="data.templateCount ? t('notificationTemplate.layoutDeleteInUse', { count: data.templateCount }) : t('common.delete')" class="inline-flex">
                                        <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="data.templateCount > 0 || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('notificationTemplate.deleteLabel', { name: data.name })" @click="confirmDelete(data)" />
                                    </span>
                                </div>
                            </template>
                        </Column>
                    </DataTable>
                </AppGadget>
                <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.layoutsHint') }}</p>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { MailLayoutAudience, MailLayoutListItemView } from '~/composables/template/api/useMailLayoutApi.types';
import { MAIL_LAYOUT_LIMIT } from '~/composables/template/api/useMailLayoutApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.layoutsTitle') });

const api = useMailLayoutApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const router = useRouter();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const layouts = ref<MailLayoutListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/* ── 구획 — 쓰는 대상마다(운영자용 · 회원용 · 모두 — 운영자 먼저), 구획 안은 받은 차례 ── */

const SECTION_AUDIENCES: readonly MailLayoutAudience[] = ['OPERATOR', 'MEMBER', 'ANY'];
const sectionTitle = (audience: MailLayoutAudience) =>
    audience === 'OPERATOR' ? t('notificationTemplate.layoutOperatorSection') : audience === 'MEMBER' ? t('notificationTemplate.layoutMemberSection') : t('notificationTemplate.layoutAnySection');
const sections = computed(() => SECTION_AUDIENCES.map((audience) => ({ audience, title: sectionTitle(audience), items: (layouts.value ?? []).filter((item) => item.audience === audience) })));
/** 「쓰는 템플릿」 링크 — 받는 사람 목록(운영자 템플릿 · 회원 템플릿)으로, 이메일 · 이 레이아웃으로 걸러 연다 */
const templateLinks = (item: MailLayoutListItemView) => UtilNotificationTemplateMessages.layoutTemplateLinks(item);

/** 사용자 레이아웃 상한(가정 20)이 차면 새로 만들기·복사를 잠근다 */
const limitReason = computed(() => ((layouts.value ?? []).filter((item) => !item.builtin).length >= MAIL_LAYOUT_LIMIT ? t('notificationTemplate.layoutLimitReached', { max: MAIL_LAYOUT_LIMIT }) : ''));

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    try {
        layouts.value = await api.list();
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else loadError.value = error.message;
    } finally {
        loading.value = false;
    }
}

const editPath = (item: MailLayoutListItemView) => `/settings/notification-templates/layouts/${encodeURIComponent(item.id)}`;

/** 행을 누르면 편집 화면으로 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(editPath(event.data as MailLayoutListItemView));
}

const deletingId = ref<string | null>(null);

async function confirmDelete(item: MailLayoutListItemView): Promise<void> {
    if (deletingId.value) return;
    const ok = await confirm({ title: t('notificationTemplate.layoutDeleteTitle', { name: item.name }), message: t('notificationTemplate.layoutDeleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = item.id;
    try {
        await api.remove(item.id);
        toast.success(t('notificationTemplate.layoutDeleted'));
        layouts.value = (layouts.value ?? []).filter((other) => other.id !== item.id);
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
