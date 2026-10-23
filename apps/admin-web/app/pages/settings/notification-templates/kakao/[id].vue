<template>
    <!--
        카카오톡(알림톡) 템플릿 보기(`/settings/notification-templates/kakao/[id]`) — **준비 중**이라 모든 칸이 읽기 전용이다(흐리게, 저장 줄 없음 — 만들기·고치기는 업체 계약 뒤 서버 단계에서 정한다).
        계약 `docs/features/message-template-contract.md` 「개정 4」 유형별 템플릿 설정 칸: 이름 · 알림톡 템플릿 코드(카카오에 등록·승인된 것) · 검수 상태 · 승인된 본문(읽기 전용 — `#{이름}` 자리) · 변수 대응표(`#{이름}` ↔ 로보얌 변수 · 견본 값) · 버튼(이름 · 링크 변수).
        오른쪽: 알림톡 말풍선 미리보기(`KakaoMessageBubble` — 대응표의 견본 값으로 채운 본문). 쓰는 문구 수 → 그 받는 사람의 문구 목록(`?channel=KAKAO&template=<id>`).
        새로 만들기(`new`)는 막는다 — 안내만. 권한: 화면 `template.read`. ⚠ MOCK: API 는 메모리 목업이다(`useKakaoTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !template">
            <PageTitle :title="t('notificationTemplate.kakaoEditTitle')" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="22rem" />
                <Skeleton height="22rem" />
            </div>
        </template>

        <template v-else-if="!template">
            <PageTitle :title="t('notificationTemplate.kakaoEditTitle')">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <Message severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationTemplate.kakaoPreparingBody') }}</span>
                <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
            </Message>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else-if="!isNew" :title="t('notificationTemplate.kakaoEditTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('notificationTemplate.notFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationTemplate.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="template.name">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="t('notificationMessage.channel.KAKAO')" icon="pi pi-comment" severity="secondary" class="template-tag text-[11px]!" />
                <Tag :value="t('notificationMessage.preparingTag')" severity="secondary" class="template-tag text-[11px]!" />
                <template v-if="messageLinks.length">
                    <NuxtLink v-for="link in messageLinks" :key="link.audience" :to="link.to" class="text-xs text-primary hover:underline">{{ t('notificationTemplate.usedByAudience', { audience: UtilNotificationTemplateMessages.audienceLabel(t, link.audience), count: link.count }) }}</NuxtLink>
                </template>
                <span v-else class="text-xs text-muted-color">{{ t('notificationTemplate.noMessages') }}</span>
                <span v-if="template.updatedAt" class="text-xs text-muted-color">{{ template.updatedByName ? t('notificationTemplate.updatedBy', { at: time.full(template.updatedAt), name: template.updatedByName }) : time.full(template.updatedAt) }}</span>
            </div>

            <Message severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationTemplate.kakaoPreparingBody') }}</span>
                <NuxtLink to="/settings/notification-channels" class="ml-2 font-medium underline underline-offset-2">{{ t('notification.channelsLink') }}</NuxtLink>
            </Message>

            <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,24rem)]">
                <!-- 왼쪽 — 칸(모두 읽기 전용) -->
                <div class="flex min-w-0 flex-col gap-4 opacity-80">
                    <AppGadget :title="t('notificationTemplate.kakaoInfoTitle')">
                        <div class="grid gap-4 sm:grid-cols-2">
                            <LabeledField :label="t('notificationTemplate.nameLabel')">
                                <template #default="{ id, describedBy }">
                                    <InputText :id="id" :model-value="template.name" readonly disabled :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>
                            <LabeledField :label="t('notificationTemplate.kakaoCodeLabel')" :help="t('notificationTemplate.kakaoCodeHelp')">
                                <template #default="{ id, describedBy }">
                                    <InputText :id="id" :model-value="template.templateCode" readonly disabled :aria-describedby="describedBy" class="font-mono" fluid />
                                </template>
                            </LabeledField>
                            <div class="flex flex-col gap-1.5">
                                <span class="text-sm font-medium">{{ t('notificationTemplate.kakaoStatusLabel') }}</span>
                                <Tag :value="t(`notificationTemplate.kakaoStatus.${template.status}`)" :severity="template.status === 'APPROVED' ? 'success' : template.status === 'REJECTED' ? 'danger' : 'secondary'" class="template-tag self-start text-[11px]!" />
                            </div>
                            <LabeledField :label="t('notificationTemplate.kakaoEmphasisLabel')">
                                <template #default="{ id, describedBy }">
                                    <InputText :id="id" :model-value="template.emphasis || '—'" readonly disabled :aria-describedby="describedBy" fluid />
                                </template>
                            </LabeledField>
                        </div>
                        <LabeledField class="mt-4" :label="t('notificationTemplate.kakaoBodyLabel')" :help="t('notificationTemplate.kakaoBodyHelp')">
                            <template #default="{ id, describedBy }">
                                <Textarea :id="id" :model-value="template.approvedBody" readonly disabled rows="6" auto-resize :aria-describedby="describedBy" class="font-mono text-[13px]!" fluid />
                            </template>
                        </LabeledField>
                    </AppGadget>

                    <AppGadget :title="t('notificationTemplate.kakaoMappingTitle')">
                        <p class="m-0 mb-2 text-xs leading-relaxed text-muted-color">{{ t('notificationTemplate.kakaoMappingHint') }}</p>
                        <DataTable :value="template.mappings" data-key="placeholder" :size="prefs.density === 'compact' ? 'small' : undefined">
                            <Column :header="t('notificationTemplate.kakaoPlaceholder')">
                                <template #body="{ data }">
                                    <code class="text-[12px]">#{{ '{' }}{{ data.placeholder }}{{ '}' }}</code>
                                </template>
                            </Column>
                            <Column :header="t('notificationTemplate.kakaoVariable')">
                                <template #body="{ data }">
                                    <code class="text-[12px]">{{ UtilMailTemplate.token(data.variable) }}</code>
                                </template>
                            </Column>
                            <Column :header="t('notificationTemplate.kakaoSample')">
                                <template #body="{ data }">
                                    <span class="text-[12px]">{{ data.sample }}</span>
                                </template>
                            </Column>
                        </DataTable>
                        <p v-if="unmapped.length" class="m-0 mt-2 text-xs text-amber-700 dark:text-amber-400">{{ t('notificationTemplate.kakaoUnmapped', { names: unmapped.join(', ') }) }}</p>
                    </AppGadget>

                    <AppGadget :title="t('notificationTemplate.kakaoButtonsTitle')">
                        <p v-if="!template.buttons.length" class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.kakaoNoButtons') }}</p>
                        <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                            <li v-for="item in template.buttons" :key="item.name" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                                <span class="font-medium">{{ item.name }}</span>
                                <span class="text-xs text-muted-color">{{ t('notificationTemplate.kakaoButtonLink') }}</span>
                                <code class="text-[12px]">{{ UtilMailTemplate.token(item.linkVariable) }}</code>
                            </li>
                        </ul>
                    </AppGadget>
                </div>

                <!-- 오른쪽 — 알림톡 말풍선 미리보기 -->
                <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                    <AppGadget :title="t('notificationTemplate.previewTitle')">
                        <KakaoMessageBubble :channel-name="template.channelName" :emphasis="template.emphasis" :body="previewBody" :buttons="template.buttons.map((item) => item.name)" :note="t('notificationTemplate.kakaoPreviewNote')" />
                    </AppGadget>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { KakaoTemplateView } from '~/composables/template/api/useKakaoTemplateApi.types';
import type { MessageAudience } from '~/composables/template/api/useNotificationMessageApi.types';
import UtilKakaoTemplate from '~/utils/util-kakao-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();

const LIST_PATH = UtilNotificationTemplateMessages.templateListPath('kakao');
const api = useKakaoTemplateApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { prefs } = useLayoutPrefs();

const id = computed(() => String(route.params.id ?? ''));
/** 새로 만들기는 준비 중이라 막는다 — 안내만 보인다 */
const isNew = computed(() => id.value === 'new');

const template = ref<KakaoTemplateView | null>(null);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);

useSeoMeta({ title: () => template.value?.name ?? t('notificationTemplate.kakaoEditTitle') });

const previewBody = computed(() => (template.value ? UtilKakaoTemplate.fill(template.value.approvedBody, template.value.mappings) : ''));
const unmapped = computed(() => (template.value ? UtilKakaoTemplate.unmapped(template.value.approvedBody, template.value.mappings).map((name) => `#{${name}}`) : []));
const messageLinks = computed<{ audience: MessageAudience; count: number; to: { path: string; query: Record<string, string> } }[]>(() => {
    const current = template.value;
    if (!current) return [];
    return (['OPERATOR', 'MEMBER'] as const).map((audience) => ({ audience, count: current.messageCounts[audience] ?? 0, to: UtilNotificationTemplateMessages.templateMessageLink('KAKAO', current.id, audience) })).filter((link) => link.count > 0);
});

async function load(): Promise<void> {
    if (isNew.value) return;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    try {
        template.value = await api.get(id.value);
    } catch (e) {
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else {
            notFound.value = error.status === 404;
            loadError.value = error.message;
        }
    } finally {
        loading.value = false;
    }
}

watch(id, () => {
    template.value = null;
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
