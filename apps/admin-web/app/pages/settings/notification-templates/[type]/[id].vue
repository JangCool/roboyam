<template>
    <!--
        Slack · Mattermost 템플릿 편집(`/settings/notification-templates/{slack|mattermost}/[id]`, 새로 만들기 `new` · 복사 `new?from=<id>`) — 계약 `docs/features/message-template-contract.md` 「개정 4」 유형별 템플릿 설정 칸.
        왼쪽 칸: 이름 · 제목 줄(심각도 아이콘 보이기 · 굵게) · 심각도별 색 막대(안내 · 주의 · 긴급마다 팔레트 8색 중 하나) · 꼬리말(사이트 이름 · 보낸 시각 보이기) · 「열기」 버튼(보이기 · 언어별 글자) · 언어별 본문 틀(그 종류의 표기법, `{{content}}` 정확히 한 번).
        오른쪽: 미리보기 — 견본 문구(제목 · 본문)를 이 템플릿에 씌운 그 앱의 메시지 카드(`ChatMessageCard` — Slack · Mattermost 모양을 가른다). 미리 볼 심각도를 고르면 색 막대·아이콘이 바뀐다. 화면이 그린다(`UtilChatTemplate.card`).
        - 새로 만들기는 기본 템플릿 값으로 채운다(이름 비움). 복사는 원본 값 + 이름 「원본 (복사)」. [저장] 때 만들고, 만들면 그 편집 주소로 바뀐다.
        - 기본 템플릿과 권한이 `template.read` 뿐이면 읽기 전용이다. 기본 템플릿은 「복사해서 만들기」를 안내한다.
        - 쓰는 문구 수 → 운영자 문구 목록(`?channel=<종류>&template=<id>`). 삭제: 쓰는 문구가 있으면 단추를 잠그고, 서버도 409 `TEMPLATE_IN_USE`.
        - 변경 이력: 제목 옆 단추 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받는다 — components.md 2-5). 저장된 사용자 템플릿만.
        권한: 화면 `template.read` · 바꾸기 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useChatTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <template v-if="loading && !ready">
            <PageTitle :title="isNew ? newTitle : editTitle" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="26rem" />
                <Skeleton height="26rem" />
            </div>
        </template>

        <template v-else-if="!ready || !base || !type">
            <PageTitle :title="isNew ? newTitle : editTitle">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else :title="editTitle">
                <ErrorState v-if="notFound || !type" code="404" :title="t('notificationTemplate.notFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationTemplate.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="pageTitle">
                <template #actions>
                    <Button v-if="saved && !saved.builtin" type="button" :label="t('settingHistory.open')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" @click="openHistory" />
                    <Button v-if="canManage && saved" type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" @click="copySaved" />
                    <span v-if="canManage && saved && !saved.builtin" v-tooltip.top="deleteBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="outlined" :disabled="Boolean(deleteBlockedReason) || deleting" :loading="deleting" :aria-description="deleteBlockedReason || undefined" @click="confirmDelete" />
                    </span>
                    <Button :label="t('notificationTemplate.backToList')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push(listPath)" />
                </template>
            </PageTitle>
            <div v-if="saved" class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="toolName" :icon="UtilNotificationTemplateMessages.channelIcon(type)" severity="secondary" class="template-tag text-[11px]!" />
                <Tag v-if="saved.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="template-tag text-[11px]!" />
                <NuxtLink v-if="saved.messageCount" :to="UtilNotificationTemplateMessages.templateMessageLink(type, saved.id)" class="text-xs text-primary hover:underline">{{ t('notificationTemplate.usedBy', { count: saved.messageCount }) }}</NuxtLink>
                <span v-else class="text-xs text-muted-color">{{ t('notificationTemplate.noMessages') }}</span>
                <span v-if="saved.updatedAt" class="text-xs text-muted-color">{{ saved.updatedByName ? t('notificationTemplate.updatedBy', { at: time.full(saved.updatedAt), name: saved.updatedByName }) : time.full(saved.updatedAt) }}</span>
            </div>

            <Message v-if="saved?.builtin" severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationTemplate.builtinReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" @click="copySaved" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('notificationTemplate.readOnly') }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
                    <!-- 왼쪽 — 편집 칸 -->
                    <div :key="formKey" class="flex min-w-0 flex-col gap-4">
                        <AppGadget :title="t('notificationTemplate.chatLookTitle')">
                            <div class="flex flex-col gap-4">
                                <LabeledField :label="t('notificationTemplate.nameLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilChatTemplate.NAME_MAX })" :error="fieldError('name')">
                                    <template #default="{ id, describedBy, invalid }">
                                        <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid class="sm:max-w-sm" />
                                    </template>
                                </LabeledField>

                                <!-- 제목 줄 -->
                                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('notificationTemplate.chatTitleLine') }}</legend>
                                    <div class="flex flex-wrap gap-x-6 gap-y-2">
                                        <label class="inline-flex items-center gap-2 text-sm">
                                            <ToggleSwitch v-model="form.showSeverityIcon" :disabled="readonly || saving" />
                                            {{ t('notificationTemplate.chatShowSeverityIcon') }}
                                        </label>
                                        <label class="inline-flex items-center gap-2 text-sm">
                                            <ToggleSwitch v-model="form.boldTitle" :disabled="readonly || saving" />
                                            {{ t('notificationTemplate.chatBoldTitle') }}
                                        </label>
                                    </div>
                                    <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.chatTitleLineHelp') }}</p>
                                </fieldset>

                                <!-- 심각도별 색 막대 -->
                                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('notificationTemplate.chatColorsLabel') }}</legend>
                                    <div v-for="severity in CHAT_SEVERITIES" :key="severity" class="flex flex-wrap items-center gap-3">
                                        <span :id="`${colorGroupId}-${severity}`" class="w-12 shrink-0 text-[13px]">{{ t(`notification.severity.${severity.toLowerCase()}`) }}</span>
                                        <div role="radiogroup" :aria-labelledby="`${colorGroupId}-${severity}`" class="flex flex-wrap gap-1.5">
                                            <label v-for="c in UtilChatTemplate.COLORS" :key="c" class="color-opt" :class="{ 'color-opt--disabled': readonly || saving }" :title="t(`notificationTemplate.chatColor.${c}`)">
                                                <input v-model="form.colors[severity]" type="radio" class="sr-only" :name="`${colorGroupId}-${severity}`" :value="c" :disabled="readonly || saving" :aria-label="t(`notificationTemplate.chatColor.${c}`)" />
                                                <span class="color-opt__dot" :style="{ background: UtilChatTemplate.colorHex(c) }" aria-hidden="true" />
                                            </label>
                                        </div>
                                    </div>
                                    <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.chatColorsHelp') }}</p>
                                </fieldset>

                                <!-- 꼬리말 -->
                                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('notificationTemplate.chatFooterLabel') }}</legend>
                                    <div class="flex flex-wrap gap-x-6 gap-y-2">
                                        <label class="inline-flex items-center gap-2 text-sm">
                                            <ToggleSwitch v-model="form.showSiteName" :disabled="readonly || saving" />
                                            {{ t('notificationTemplate.showSiteName', { name: base.siteName }) }}
                                        </label>
                                        <label class="inline-flex items-center gap-2 text-sm">
                                            <ToggleSwitch v-model="form.showSentAt" :disabled="readonly || saving" />
                                            {{ t('notificationTemplate.chatShowSentAt') }}
                                        </label>
                                    </div>
                                </fieldset>

                                <!-- 「열기」 버튼 -->
                                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('notificationTemplate.chatOpenButtonLabel') }}</legend>
                                    <label class="inline-flex items-center gap-2 self-start text-sm">
                                        <ToggleSwitch v-model="form.showOpenButton" :disabled="readonly || saving" />
                                        {{ t('notificationTemplate.chatShowOpenButton') }}
                                    </label>
                                    <div v-if="form.showOpenButton" class="grid gap-3 sm:grid-cols-2">
                                        <LabeledField v-for="at in MESSAGE_LOCALES" :key="at" :label="t('notificationTemplate.chatOpenLabelFor', { locale: UtilNotificationTemplateMessages.localeLabel(t, at) })" :hint="t('notificationTemplate.maxHint', { max: UtilChatTemplate.OPEN_LABEL_MAX })" :error="fieldError(`openLabel.${at}`)">
                                            <template #default="{ id, describedBy, invalid }">
                                                <InputText :id="id" v-model="form.openLabel[at]" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                            </template>
                                        </LabeledField>
                                    </div>
                                    <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.chatOpenButtonHelp') }}</p>
                                </fieldset>
                            </div>
                        </AppGadget>

                        <AppGadget :title="t('notificationTemplate.chatFrameTitle')">
                            <template #actions>
                                <SelectButton v-model="locale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.localeLabel')" />
                            </template>
                            <LabeledField :label="t('notificationTemplate.frameLabel', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) })" :hint="t('notificationTemplate.maxHint', { max: UtilChatTemplate.FRAME_MAX })" :help="type === 'SLACK' ? t('notificationTemplate.chatFrameHelpSlack', { token: contentToken }) : t('notificationTemplate.chatFrameHelpMattermost', { token: contentToken })" :error="fieldError(`frames.${locale}`)">
                                <template #aside>
                                    <NotificationVariableMenu v-if="!readonly" :variables="base.variables" :disabled="saving" @pick="insertFrame" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <Textarea :id="id" ref="frameInput" v-model="form.frames[locale]" rows="4" auto-resize :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" class="font-mono text-[13px]!" fluid />
                                </template>
                            </LabeledField>
                            <p v-if="otherLocaleError" role="alert" class="m-0 mt-2 text-xs text-red-600 dark:text-red-400">{{ t('notificationTemplate.otherLocaleError', { locale: UtilNotificationTemplateMessages.localeLabel(t, otherLocale) }) }}</p>
                        </AppGadget>
                    </div>

                    <!-- 오른쪽 — 미리보기(견본 문구를 씌운 메시지 카드) -->
                    <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                        <AppGadget :title="t('notificationTemplate.previewTitle')">
                            <template #actions>
                                <SelectButton v-model="previewSeverity" :options="severityOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.chatPreviewSeverity')" />
                            </template>
                            <ChatMessageCard :card="card" :note="t('notificationTemplate.chatPreviewNote', { tool: toolName })" />
                        </AppGadget>
                    </div>
                </div>

                <StickySaveBar v-if="!readonly" submit :revertable="!isNew" :dirty="isNew || dirty" :saving="saving" :disabled="invalidCount > 0" :count="changeCount" :error-count="invalidCount" :error="formError" @revert="revert" />
            </form>
        </template>

        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('notificationTemplate.chatHistoryHint')">
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import { mockTemplateCan } from '~/composables/template/api/mock/notification-template-mock';
import type { ChatTemplateSaveRequest, ChatTemplateType, ChatTemplateView } from '~/composables/template/api/useChatTemplateApi.types';
import { CHAT_SEVERITIES } from '~/composables/template/api/useChatTemplateApi.types';
import type { MessageLocale, MessageSeverity } from '~/composables/template/api/useNotificationMessageApi.types';
import { MESSAGE_LOCALES } from '~/composables/template/api/useNotificationMessageApi.types';
import type { ChatTemplateField } from '~/utils/util-chat-template';
import UtilChatTemplate from '~/utils/util-chat-template';
import UtilMailTemplate from '~/utils/util-mail-template';
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
const colorGroupId = useId();
const contentToken = UtilMailTemplate.token(UtilChatTemplate.CONTENT);

/** 주소의 종류 조각 — `slack` · `mattermost` 만(그 밖은 없는 화면) */
const type = computed<ChatTemplateType | null>(() => (route.params.type === 'slack' ? 'SLACK' : route.params.type === 'mattermost' ? 'MATTERMOST' : null));
const kind = computed<TemplateKind>(() => (type.value === 'MATTERMOST' ? 'mattermost' : 'slack'));
const toolName = computed(() => UtilNotificationTemplateMessages.templateKindLabel(t, kind.value));
const listPath = computed(() => UtilNotificationTemplateMessages.templateListPath(kind.value));
const id = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => id.value === 'new');
const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);
const newTitle = computed(() => t('notificationTemplate.newChatTemplate', { tool: toolName.value }));
const editTitle = computed(() => t('notificationTemplate.chatTitle', { tool: toolName.value }));

/* ── 받은 것 ── */

/** 저장된 템플릿(새로 만드는 중이면 null) */
const saved = ref<ChatTemplateView | null>(null);
/** 칸을 채운 출처 — 저장된 것 · 복사 원본 · 기본 템플릿(새로 만들기). 사이트 이름·변수도 여기서 읽는다 */
const base = ref<ChatTemplateView | null>(null);
const ready = ref(false);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);

const readonly = computed(() => !canManage.value || Boolean(saved.value?.builtin));
const pageTitle = computed(() => (isNew.value ? newTitle.value : (saved.value?.name ?? editTitle.value)));
useSeoMeta({ title: () => pageTitle.value });

const localeOptions = computed(() => MESSAGE_LOCALES.map((value) => ({ value, label: UtilNotificationTemplateMessages.localeLabel(t, value) })));
const severityOptions = computed(() => CHAT_SEVERITIES.map((value) => ({ value, label: t(`notification.severity.${value.toLowerCase()}`) })));

/* ── 편집 값 ── */

const blank = (): ChatTemplateSaveRequest => ({
    name: '',
    showSeverityIcon: true,
    boldTitle: true,
    colors: { INFO: 'BLUE', WARNING: 'AMBER', CRITICAL: 'RED' },
    showSiteName: true,
    showSentAt: true,
    showOpenButton: true,
    openLabel: { ko: '', en: '' },
    frames: { ko: '', en: '' },
});
const form = reactive<ChatTemplateSaveRequest>(blank());
const baseline = ref<ChatTemplateSaveRequest>(blank());
const formKey = ref(0);
const locale = ref<MessageLocale>('ko');
const otherLocale = computed<MessageLocale>(() => (locale.value === 'ko' ? 'en' : 'ko'));
const previewSeverity = ref<MessageSeverity>('WARNING');
const tried = ref(false);
const serverErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

const snapshot = (): ChatTemplateSaveRequest => JSON.parse(JSON.stringify(form)) as ChatTemplateSaveRequest;

function fill(): void {
    const from = base.value;
    if (!from) return;
    form.name = saved.value ? from.name : isNew.value && queryText(route.query.from) ? t('notificationTemplate.copyName', { name: from.name }).slice(0, UtilChatTemplate.NAME_MAX) : '';
    form.showSeverityIcon = from.showSeverityIcon;
    form.boldTitle = from.boldTitle;
    form.colors = { ...from.colors };
    form.showSiteName = from.showSiteName;
    form.showSentAt = from.showSentAt;
    form.showOpenButton = from.showOpenButton;
    form.openLabel = { ...from.openLabel };
    form.frames = { ...from.frames };
    baseline.value = snapshot();
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

const changeCount = computed(() => {
    const b = baseline.value;
    let n = 0;
    for (const key of ['name', 'showSeverityIcon', 'boldTitle', 'showSiteName', 'showSentAt', 'showOpenButton'] as const) if (form[key] !== b[key]) n += 1;
    for (const severity of CHAT_SEVERITIES) if (form.colors[severity] !== b.colors[severity]) n += 1;
    for (const at of MESSAGE_LOCALES) {
        if (form.openLabel[at] !== b.openLabel[at]) n += 1;
        if (form.frames[at] !== b.frames[at]) n += 1;
    }
    return n;
});
const dirty = computed(() => ready.value && changeCount.value > 0);

/* ── 검사 ── */

const knownNames = computed(() => (base.value?.variables ?? []).map((v) => v.name).filter((name) => name !== UtilChatTemplate.CONTENT));
const problems = computed(() => UtilChatTemplate.validate(form, knownNames.value));

function fieldError(field: ChatTemplateField): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (readonly.value) return '';
    const problem = problems.value[field];
    if (!problem) return '';
    if ((problem.code === 'REQUIRED' || problem.code === 'BUTTON_LABEL') && !tried.value) return '';
    return UtilNotificationTemplateMessages.problem(t, field, problem);
}

const FIELDS: readonly ChatTemplateField[] = ['name', 'openLabel.ko', 'openLabel.en', 'frames.ko', 'frames.en'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length + Object.keys(serverErrors.value).filter((key) => key.startsWith('colors.') && serverErrors.value[key]).length);
/** 고르지 않은 언어 쪽에 고칠 곳이 있다 — 그 언어로 바꿔 보라고 알린다 */
const otherLocaleError = computed(() => Boolean(fieldError(`frames.${otherLocale.value}`) || fieldError(`openLabel.${otherLocale.value}`)));

// 칸을 고치면 서버 문구를 걷는다(다시 저장할 때 서버가 다시 본다)
watch(
    () => JSON.stringify(form),
    () => {
        if (Object.values(serverErrors.value).some(Boolean)) serverErrors.value = {};
    },
);

/* ── 변수 넣기 ── */

const frameInput = ref<{ $el: HTMLTextAreaElement } | null>(null);

/** 틀 칸의 커서 자리에 넣는다 */
function insertFrame(name: string): void {
    const token = UtilMailTemplate.token(name);
    const at = locale.value;
    const el = frameInput.value?.$el;
    const text = form.frames[at];
    const start = el?.selectionStart ?? text.length;
    const end = el?.selectionEnd ?? start;
    form.frames[at] = text.slice(0, start) + token + text.slice(end);
    const caret = start + token.length;
    void nextTick(() => {
        el?.focus();
        el?.setSelectionRange(caret, caret);
    });
}

/* ── 미리보기 — 견본 문구를 이 템플릿(저장 전 값)에 씌운다 ── */

const card = computed(() =>
    UtilChatTemplate.card({
        tool: type.value ?? 'SLACK',
        template: form,
        subject: t('notificationTemplate.chatSampleTitle'),
        body: type.value === 'MATTERMOST' ? t('notificationTemplate.chatSampleBodyMattermost') : t('notificationTemplate.chatSampleBodySlack'),
        variables: base.value?.variables.filter((v) => v.name !== UtilChatTemplate.CONTENT) ?? [],
        locale: locale.value,
        severity: previewSeverity.value,
        siteName: base.value?.siteName ?? '',
        sentAt: time.value.full(new Date().toISOString()),
    }),
);

/* ── 읽기 ── */

let loadSeq = 0;

async function load(): Promise<void> {
    const kindType = type.value;
    if (!kindType) {
        notFound.value = true;
        return;
    }
    const mine = ++loadSeq;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    ready.value = false;
    try {
        let current: ChatTemplateView | null = null;
        let from: ChatTemplateView;
        if (!isNew.value) {
            current = await api.get(kindType, id.value);
            from = current;
        } else {
            // 복사 원본, 없으면 그 종류의 기본 템플릿 값으로 채운다
            const fromId = queryText(route.query.from) ?? (await api.list(kindType)).find((item) => item.builtin)?.id ?? '';
            from = await api.get(kindType, fromId);
        }
        if (mine !== loadSeq) return;
        saved.value = current;
        base.value = from;
        fill();
        ready.value = true;
    } catch (e) {
        if (mine !== loadSeq) return;
        const error = toApiError(e);
        if (error.status === 403) denied.value = true;
        else {
            notFound.value = error.status === 404;
            loadError.value = error.message;
        }
    } finally {
        if (mine === loadSeq) loading.value = false;
    }
}

/* ── 저장 · 되돌리기 · 복사 · 삭제 ── */

function revert(): void {
    if (saved.value) fill();
}

let leaving = false;

async function save(): Promise<void> {
    const kindType = type.value;
    if (readonly.value || saving.value || !kindType) return;
    if (!isNew.value && !dirty.value) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0) {
        // 고칠 곳이 고르지 않은 언어에만 있으면 그 언어로 바꿔 보인다
        if (!fieldError(`frames.${locale.value}`) && !fieldError(`openLabel.${locale.value}`) && otherLocaleError.value) locale.value = otherLocale.value;
        return;
    }
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    const body = { ...snapshot(), name: form.name.trim() };
    try {
        if (isNew.value || !saved.value) {
            const created = await api.create(kindType, body);
            toast.success(t('notificationTemplate.created'));
            leaving = true;
            await router.replace(UtilNotificationTemplateMessages.templatePath(kind.value, created.id));
            return;
        }
        saved.value = await api.update(kindType, saved.value.id, body);
        base.value = saved.value;
        toast.success(t('notificationTemplate.saved'));
        fill();
        if (historyLoaded) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'openLabel.ko', 'openLabel.en', 'frames.ko', 'frames.en', 'colors.INFO', 'colors.WARNING', 'colors.CRITICAL']) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        serverErrors.value = found;
        // 칸에 붙지 않는 오류(색 포함 — 칸 아래 문구 자리가 없다)는 저장 줄 안 한 줄, 서버 문구 그대로
        const colorError = Object.entries(found).find(([key]) => key.startsWith('colors.'))?.[1];
        if (Object.keys(found).length === 0) formError.value = error.message;
        else if (colorError) formError.value = colorError;
    } finally {
        saving.value = false;
    }
}

function copySaved(): void {
    if (!saved.value) return;
    void router.push({ path: UtilNotificationTemplateMessages.templatePath(kind.value, 'new'), query: { from: saved.value.id } });
}

const deleteBlockedReason = computed(() => (saved.value?.messageCount ? t('notificationTemplate.deleteInUse', { count: saved.value.messageCount }) : ''));
const deleting = ref(false);

async function confirmDelete(): Promise<void> {
    const current = saved.value;
    const kindType = type.value;
    if (!current || !kindType || deleting.value) return;
    const ok = await confirm({ title: t('notificationTemplate.deleteTitle', { name: current.name }), message: t('notificationTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(kindType, current.id);
        toast.success(t('notificationTemplate.deleted'));
        leaving = true;
        await router.push(listPath.value);
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        deleting.value = false;
    }
}

/* ── 변경 이력(오른쪽 서랍) ── */

const historyOpen = ref(false);
let historyLoaded = false;
const history = useSettingHistory((page, size) => api.history(type.value ?? 'SLACK', saved.value?.id ?? '', page, size));

function openHistory(): void {
    if (!saved.value) return;
    historyOpen.value = true;
    if (!historyLoaded) {
        historyLoaded = true;
        void history.reloadFirst();
    }
}

/* ── 떠나기 · 주소 바뀜 ── */

onBeforeRouteLeave(async () => {
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});

onBeforeRouteUpdate(async (to, from) => {
    if (to.params.id === from.params.id && to.params.type === from.params.type && to.query.from === from.query.from) return true;
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});
watch(
    () => [type.value, id.value, route.query.from] as const,
    () => {
        leaving = false;
        historyOpen.value = false;
        historyLoaded = false;
        void load();
    },
);

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
/* 색 고르기 — 메일 템플릿 띠 색 고르기·게시판 표시색 고르기와 같은 모양 */
.color-opt {
    display: inline-grid;
    place-items: center;
    width: 2rem;
    height: 2rem;
    border-radius: 9999px;
    border: 1px solid var(--line);
    cursor: pointer;
}
.color-opt:has(input:checked) {
    outline: 2px solid var(--p-text-color);
    outline-offset: 1px;
}
.color-opt:has(input:focus-visible) {
    box-shadow: 0 0 0 4px var(--p-focus-ring-color, var(--p-primary-color));
}
.color-opt--disabled {
    cursor: default;
}
.color-opt:has(input:disabled:not(:checked)) {
    opacity: 0.5;
}
.color-opt__dot {
    width: 1.1rem;
    height: 1.1rem;
    border-radius: 9999px;
}
</style>
