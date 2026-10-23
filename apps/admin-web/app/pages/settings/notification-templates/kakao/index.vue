<template>
    <!--
        카카오톡 템플릿(목록, `/settings/notification-templates/kakao`, 설정 > 알림·이벤트 > 템플릿 › 카카오톡) — **준비 중**. 계약 `docs/features/message-template-contract.md` 「개정 4」 유형별 템플릿 설정 칸.
        알림 도구에서 카카오가 준비 중(업체 계약 전)이라 화면을 흐리게 두고 새로 만들기를 막는다 — 맨 위 안내와 알림 도구 링크. 예시 템플릿 하나를 열어 칸(알림톡 템플릿 코드 · 승인된 본문 · 변수 대응표 · 버튼)과 말풍선 미리보기를 볼 수 있다.
        표: 이름 · 알림톡 템플릿 코드 · 검수 상태 · 쓰는 문구 수(→ 그 받는 사람의 문구 목록 `?channel=KAKAO&template=<id>`) · 마지막 수정. 행을 누르면 보기 화면(`…/kakao/[id]`).
        권한: 화면 `template.read`(메뉴가 거른다). API 는 실제 서버(`useKakaoTemplateApi` — 읽기만, 아직 행이 없어 빈 목록)다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('notificationTemplate.kakaoTitle')" :subtitle="t('notificationTemplate.kakaoSubtitle')">
            <template v-if="canManage && !denied" #actions>
                <span v-tooltip.top="t('notificationTemplate.kakaoPreparing')" class="inline-flex">
                    <Button :label="t('notificationTemplate.newKakaoTemplate')" icon="pi pi-plus" size="small" disabled :aria-description="t('notificationTemplate.kakaoPreparing')" />
                </span>
            </template>
        </PageTitle>

        <Message severity="secondary" :closable="false" size="small">
            <span>{{ t('notificationTemplate.kakaoPreparingBody') }}</span>
            <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
        </Message>

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>

        <template v-else>
            <AppGadget v-if="loading && !templates" :title="t('notificationTemplate.kakaoTitle')">
                <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 2" :key="n" height="2.75rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!templates" :title="t('notificationTemplate.kakaoTitle')">
                <ErrorState :title="t('notificationTemplate.kakaoLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>

            <AppGadget v-else :title="t('notificationTemplate.kakaoTitle')" class="opacity-75">
                <template #title-extra>
                    <Tag :value="t('notificationMessage.preparingTag')" severity="secondary" class="template-tag text-[10px]!" />
                </template>
                <EmptyState v-if="!templates.length" icon="pi pi-comment" :title="t('notificationTemplate.kakaoEmptyTitle')" />
                <DataTable v-else :value="templates" data-key="id" row-hover scrollable :size="prefs.density === 'compact' ? 'small' : undefined" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                    <Column :header="t('notificationTemplate.colName')" style="min-width: 13rem">
                        <template #body="{ data }">
                            <NuxtLink :to="UtilNotificationTemplateMessages.templatePath('kakao', data.id)" class="text-[13px] font-medium hover:underline">{{ data.name }}</NuxtLink>
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.kakaoCodeLabel')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <code class="text-[12px]">{{ data.templateCode }}</code>
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.kakaoStatusLabel')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <Tag :value="t(`notificationTemplate.kakaoStatus.${data.status}`)" :severity="data.status === 'APPROVED' ? 'success' : data.status === 'REJECTED' ? 'danger' : 'secondary'" class="template-tag text-[10px]!" />
                        </template>
                    </Column>
                    <Column :header="t('notificationTemplate.colMessageCount')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span v-if="messageLinks(data).length" class="flex flex-col gap-0.5">
                                <NuxtLink v-for="link in messageLinks(data)" :key="link.audience" :to="link.to" class="text-[13px] tabular-nums text-primary hover:underline">{{ t('notificationTemplate.messageCountOf', { audience: UtilNotificationTemplateMessages.audienceLabel(t, link.audience), count: link.count }) }}</NuxtLink>
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
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { KakaoTemplateListItemView } from '~/composables/template/api/useKakaoTemplateApi.types';
import type { MessageAudience } from '~/composables/template/api/useNotificationMessageApi.types';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('notificationTemplate.kakaoTitle') });

const api = useKakaoTemplateApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const router = useRouter();
const { prefs } = useLayoutPrefs();
const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const templates = ref<KakaoTemplateListItemView[] | null>(null);
const loading = ref(false);
const loadError = ref('');
const denied = ref(false);

/** 「쓰는 문구」 링크 — 받는 사람마다(운영자 먼저, 쓰는 문구가 있는 쪽만) */
function messageLinks(item: KakaoTemplateListItemView): { audience: MessageAudience; count: number; to: { path: string; query: Record<string, string> } }[] {
    return (['OPERATOR', 'MEMBER'] as const)
        .map((audience) => ({ audience, count: item.messageCounts[audience] ?? 0, to: UtilNotificationTemplateMessages.templateMessageLink('KAKAO', item.id, audience) }))
        .filter((link) => link.count > 0);
}

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

/** 행을 누르면 보기 화면으로 — 줄 안의 링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    void router.push(UtilNotificationTemplateMessages.templatePath('kakao', (event.data as KakaoTemplateListItemView).id));
}

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
