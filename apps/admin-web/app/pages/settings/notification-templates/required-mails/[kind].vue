<template>
    <!--
        필수 안내 메일 상세(`/settings/notification-templates/required-mails/[kind]`) — 한 메일의 ① 언어별 쓸 템플릿 ② 특수 상황 규칙. 계약 `docs/features/message-template-contract.md` 「개정 3」 메뉴.
        채널은 이메일뿐이고 받는 사람이 끌 수 없다. 머리에 받는 사람 · 보안 메일 · 끌 수 없음을 보인다. ①②는 `PUT targets/REQUIRED_MAIL/{kind}` 한 번으로 저장하므로 하단 떠 있는 저장 줄(`StickySaveBar`) 하나다.
        칸은 이벤트 상세와 같은 조각(`TemplateChannelSelection`). 머리 단추: 「이 메일의 템플릿 보기」(`templates?target=&channel=EMAIL`) · 「새 템플릿」(`templates/new?target=&channel=EMAIL`).
        권한: 화면 `template.read`(메뉴가 거른다) · 바꾸기 `template.manage` — 없으면 입력 잠금, 저장 줄 없음. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useNotificationTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !detail">
            <PageTitle :title="t('notificationTemplate.requiredTitle')" />
            <div class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 2" :key="n" height="9rem" />
            </div>
        </template>

        <template v-else-if="!detail || !email">
            <PageTitle :title="t('notificationTemplate.requiredTitle')">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('notificationTemplate.requiredTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('notificationTemplate.requiredNotFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationTemplate.requiredDetailLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="summary.name">
                <template #actions>
                    <Button :label="t('notificationTemplate.showMailTemplates')" icon="pi pi-list" size="small" severity="secondary" variant="outlined" @click="router.push({ path: UtilNotificationTemplateMessages.templateListPath(summary.audience), query: { target: summary.code, channel: 'EMAIL' } })" />
                    <Button v-if="canManage" :label="t('notificationTemplate.newTemplate')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="router.push({ path: '/settings/notification-templates/templates/new', query: { audience: summary.audience, target: summary.code, channel: 'EMAIL' } })" />
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(LIST_PATH)" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                    <Tag :value="t('notificationTemplate.audienceTag', { audience: UtilNotificationTemplateMessages.audienceLabel(t, summary.audience) })" :icon="UtilNotificationTemplateMessages.audienceIcon(summary.audience)" severity="secondary" class="mail-template-tag text-[11px]!" />
                    <Tag v-if="summary.security" :value="t('notificationTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                    <Tag v-tooltip.top="t('notificationTemplate.alwaysSentTip')" :value="t('notificationTemplate.alwaysSentTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[11px]!" />
                    <code class="text-[11px] text-muted-color">{{ summary.code }}</code>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ summary.description }}</p>
                <p v-if="summary.security" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('notificationTemplate.securityHint', { names: requiredNames }) }}</p>
            </div>

            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('notificationTemplate.readOnly') }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <AppGadget :title="UtilNotificationTemplateMessages.channelLabel(t, 'EMAIL')" anchor="channel-email" :changes="changes" :invalid="invalid">
                    <TemplateChannelSelection v-model="model" :templates="email.templates" :conditions="emailSummary?.conditions ?? []" :editable="canManage" :saving="saving" :errors="errors" />
                </AppGadget>

                <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="invalid > 0" :count="changes" :error-count="invalid" :error="formError" @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { TemplateTargetDetailView } from '~/composables/template/api/useNotificationTemplateApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';
import type { TemplateSelectionModel } from '~/utils/util-notification-template';
import UtilNotificationTemplate from '~/utils/util-notification-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();

const LIST_PATH = '/settings/notification-templates/required-mails';
const api = useNotificationTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const route = useRoute();
const router = useRouter();
const { confirm } = useConfirmDialog();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));
const code = computed(() => String(route.params.kind ?? ''));

const detail = ref<TemplateTargetDetailView | null>(null);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);
const saving = ref(false);
const formError = ref('');
/** 서버 칸 문구(이 채널 안 경로로 뗀 것) */
const serverErrors = ref<Record<string, string>>({});
const model = ref<TemplateSelectionModel>({ selected: { ko: '', en: '' }, rules: [] });

const summary = computed(() => detail.value?.summary as TemplateTargetDetailView['summary']);
const email = computed(() => detail.value?.channels.find((item) => item.channel === 'EMAIL') ?? null);
const emailSummary = computed(() => summary.value?.channels.find((item) => item.channel === 'EMAIL'));
const requiredNames = computed(() => (emailSummary.value?.variables ?? []).filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));

useSeoMeta({ title: () => summary.value?.name ?? t('notificationTemplate.requiredTitle') });

function fill(view: TemplateTargetDetailView): void {
    detail.value = view;
    const channel = view.channels.find((item) => item.channel === 'EMAIL');
    if (channel) model.value = UtilNotificationTemplate.toSelection(channel);
    serverErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    try {
        fill(await api.target('REQUIRED_MAIL', code.value));
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

const changes = computed(() => (email.value ? UtilNotificationTemplate.selectionChanges(email.value, model.value) : 0));
/** 서버 문구가 있으면 그것을, 없으면 화면이 미리 막는 칸 */
const errors = computed(() => serverErrors.value);
const invalid = computed(() => new Set([...UtilNotificationTemplate.selectionProblems(model.value), ...Object.keys(serverErrors.value).filter((key) => serverErrors.value[key])]).size);
const dirty = computed(() => changes.value > 0);

// 고치면 서버 문구를 걷는다(다시 저장할 때 서버가 다시 본다)
watch(model, () => {
    if (Object.keys(serverErrors.value).length) serverErrors.value = {};
});

function revert(): void {
    if (detail.value) fill(detail.value);
}

async function save(): Promise<void> {
    if (saving.value || !canManage.value || !dirty.value || invalid.value > 0) return;
    saving.value = true;
    formError.value = '';
    try {
        fill(await api.saveTarget('REQUIRED_MAIL', code.value, { channels: { EMAIL: UtilNotificationTemplate.toSaveRequest(model.value) } }));
        toast.success(t('notificationTemplate.requiredSaved'));
    } catch (e) {
        const error = toApiError(e);
        const prefix = 'channels.EMAIL.';
        const found: Record<string, string> = {};
        for (const [key, message] of Object.entries(error.fields ?? {})) if (key.startsWith(prefix)) found[key.slice(prefix.length)] = message;
        serverErrors.value = found;
        // 칸에 붙지 않는 오류만 저장 줄 안 한 줄 — 서버 문구 그대로
        if (Object.keys(found).length === 0) formError.value = error.message;
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
