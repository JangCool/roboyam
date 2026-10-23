<template>
    <!--
        메일 레이아웃 편집(`/settings/notification-templates/layouts/[id]`, 새로 만들기 `new` · 복사 `new?from=<id>`) — 계약 `docs/features/message-template-contract.md` 「개정」 4 · 「개정 3」(위치만 옮겼다). 레이아웃은 이메일 채널 전용이다.
        왼쪽 칸: 이름 · 쓰는 대상(회원 · 운영자 · 모두 — 개정 2) · 머리말(로고 보이기 · 사이트 이름 보이기 · 머리말 띠 색 — 팔레트, 없음 포함) · 언어별(한국어/영어 고르기) 틀(간단 에디터, `{{content}}` 정확히 한 번 — 지울 수 없음) + 꼬리말(평문, 줄바꿈 가능).
        오른쪽: 미리보기(견본 본문 한 줄 + 공통 변수 견본 값, 저장 전 값 그대로 — 격리된 iframe). 저장은 화면 하나로 한 번 → 하단 떠 있는 저장 줄(`StickySaveBar`).
        - 쓰는 대상: 이 레이아웃을 고를 수 있는 템플릿의 받는 사람. 틀·꼬리말의 변수 메뉴·검사·미리보기 견본 값이 그 대상의 공통 변수를 따른다(회원 `siteUrl` · 운영자 `adminUrl` · 모두는 `siteName`·`recipientName` 만).
          쓰는 템플릿과 맞지 않는 대상(예: 운영자 템플릿이 쓰는데 「회원」)은 고르지 못하게 잠그고 까닭을 보인다 — 서버도 400 `fields.audience`.
        - 새로 만들기는 기본 레이아웃의 값으로 채운다(이름은 비움 — `?audience=MEMBER|OPERATOR` 면 그 받는 사람의 기본, 없으면 회원 기본). 복사는 원본 값 + 이름 「원본 (복사)」. [저장] 때 만들고, 만들면 그 레이아웃의 편집 주소로 바뀐다.
        - 기본 레이아웃(코드 제공)과 권한이 `template.read` 뿐이면 읽기 전용이다. 기본 레이아웃은 「복사해서 만들기」를 안내한다.
        - 쓰는 템플릿 수 → 그 레이아웃으로 거른 템플릿 목록 링크. 삭제: 쓰는 템플릿이 있으면 단추를 잠그고, 서버도 409 `LAYOUT_IN_USE`.
        - 변경 이력: 제목 옆 단추 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받는다). 저장된 사용자 레이아웃만.
        권한: 화면 `template.read` · 바꾸기 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailLayoutApi` · 미리보기는 `useNotificationTemplateApi.preview`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <!-- 읽는 중 -->
        <template v-if="loading && !ready">
            <PageTitle :title="isNew ? t('notificationTemplate.newLayoutTitle') : t('notificationTemplate.layoutTitle')" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="26rem" />
                <Skeleton height="26rem" />
            </div>
        </template>

        <!-- 권한 없음 · 없는 레이아웃 · 읽기 실패 -->
        <template v-else-if="!ready || !base">
            <PageTitle :title="isNew ? t('notificationTemplate.newLayoutTitle') : t('notificationTemplate.layoutTitle')">
                <template #actions>
                    <Button :label="t('notificationTemplate.backToLayouts')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/settings/notification-templates/layouts')" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('notificationTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('notificationTemplate.layoutTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('notificationTemplate.layoutNotFound')" :description="loadError">
                    <Button :label="t('notificationTemplate.backToLayouts')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push('/settings/notification-templates/layouts')" />
                </ErrorState>
                <ErrorState v-else :title="t('notificationTemplate.layoutLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
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
                    <Button :label="t('notificationTemplate.backToLayouts')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/settings/notification-templates/layouts')" />
                </template>
            </PageTitle>
            <div v-if="saved" class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="t('notificationTemplate.audienceTag', { audience: UtilNotificationTemplateMessages.audienceLabel(t, saved.audience) })" :icon="UtilNotificationTemplateMessages.audienceIcon(saved.audience)" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="saved.builtin" :value="t('notificationTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[11px]!" />
                <!-- 쓰는 템플릿 — 그 받는 사람의 템플릿 목록으로(「모두」 레이아웃은 운영자 · 회원 목록을 따로) -->
                <template v-if="templateLinks.length">
                    <NuxtLink v-for="link in templateLinks" :key="link.audience" :to="link.to" class="text-xs text-primary hover:underline">
                        {{ saved.audience === 'ANY' ? t('notificationTemplate.layoutUsedByAudience', { audience: UtilNotificationTemplateMessages.audienceLabel(t, link.audience), count: link.count }) : t('notificationTemplate.layoutUsedBy', { count: link.count }) }}
                    </NuxtLink>
                </template>
                <span v-else class="text-xs text-muted-color">{{ t('notificationTemplate.noTemplates') }}</span>
                <span v-if="saved.updatedAt" class="text-xs text-muted-color">{{ saved.updatedByName ? t('notificationTemplate.updatedBy', { at: time.full(saved.updatedAt), name: saved.updatedByName }) : time.full(saved.updatedAt) }}</span>
            </div>

            <Message v-if="saved?.builtin" severity="secondary" :closable="false" size="small">
                <span>{{ t('notificationTemplate.builtinLayoutReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('notificationTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" @click="copySaved" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('notificationTemplate.readOnly') }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
                    <!-- 왼쪽 — 편집 칸 -->
                    <div :key="formKey" class="flex min-w-0 flex-col gap-4">
                        <AppGadget :title="t('notificationTemplate.layoutHeadTitle')">
                            <div class="flex flex-col gap-4">
                                <LabeledField :label="t('notificationTemplate.nameLabel')" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.NAME_MAX })" :error="fieldError('name')">
                                    <template #default="{ id, describedBy, invalid }">
                                        <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid class="sm:max-w-sm" />
                                    </template>
                                </LabeledField>

                                <!-- 쓰는 대상 — 쓰는 템플릿과 맞지 않는 대상은 잠근다 -->
                                <div class="flex flex-col gap-1.5">
                                    <span :id="audienceLabelId" class="text-sm font-medium">{{ t('notificationTemplate.layoutAudienceLabel') }}</span>
                                    <SelectButton v-model="form.audience" :options="audienceOptions" option-label="label" option-value="value" option-disabled="disabled" :allow-empty="false" size="small" :disabled="readonly || saving" :invalid="Boolean(fieldError('audience'))" :aria-labelledby="audienceLabelId" :aria-describedby="audienceMessageId" class="self-start" />
                                    <div :id="audienceMessageId" aria-live="polite" class="flex flex-col gap-0.5 text-xs leading-relaxed">
                                        <p v-if="fieldError('audience')" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fieldError('audience') }}</p>
                                        <template v-else-if="!readonly">
                                            <p v-for="reason in audienceLockReasons" :key="reason" class="m-0 text-amber-700 dark:text-amber-400">{{ reason }}</p>
                                        </template>
                                        <p class="m-0 text-muted-color">{{ t('notificationTemplate.layoutAudienceHelp') }}</p>
                                    </div>
                                </div>

                                <div class="flex flex-wrap gap-x-6 gap-y-2">
                                    <label class="inline-flex items-center gap-2 text-sm">
                                        <ToggleSwitch v-model="form.showLogo" :disabled="readonly || saving" />
                                        {{ t('notificationTemplate.showLogo') }}
                                    </label>
                                    <label class="inline-flex items-center gap-2 text-sm">
                                        <ToggleSwitch v-model="form.showSiteName" :disabled="readonly || saving" />
                                        {{ t('notificationTemplate.showSiteName', { name: base.siteName }) }}
                                    </label>
                                </div>
                                <p v-if="form.showLogo && !base.logoUrl" class="-mt-2 m-0 text-xs text-muted-color">
                                    {{ t('notificationTemplate.noLogo') }}
                                    <NuxtLink v-if="can('site.read')" to="/settings/general" class="ml-1 font-medium underline underline-offset-2">{{ t('notificationTemplate.noLogoLink') }}</NuxtLink>
                                </p>

                                <!-- 머리말 띠 색 — 팔레트 또는 없음(같은 이름의 네이티브 라디오, 게시판 표시색과 같은 방식) -->
                                <div class="flex flex-col gap-1.5">
                                    <span :id="colorLabelId" class="text-sm font-medium">{{ t('notificationTemplate.headerColorLabel') }}</span>
                                    <div role="radiogroup" :aria-labelledby="colorLabelId" class="flex flex-wrap gap-1.5">
                                        <label v-for="c in COLOR_CHOICES" :key="c" class="color-opt" :class="{ 'color-opt--disabled': readonly || saving }" :title="t(`notificationTemplate.color.${c}`)">
                                            <input v-model="colorModel" type="radio" class="sr-only" :name="colorLabelId" :value="c" :disabled="readonly || saving" :aria-label="t(`notificationTemplate.color.${c}`)" />
                                            <span v-if="c === 'NONE'" class="color-opt__none" aria-hidden="true" />
                                            <span v-else class="color-opt__dot" :style="{ background: UtilMailTemplate.colorHex(c) ?? undefined }" aria-hidden="true" />
                                        </label>
                                    </div>
                                    <p class="m-0 text-xs text-muted-color">{{ t('notificationTemplate.headerColorHelp', { name: t(`notificationTemplate.color.${colorModel}`) }) }}</p>
                                </div>
                            </div>
                        </AppGadget>

                        <AppGadget :title="t('notificationTemplate.layoutFrameTitle')">
                            <template #actions>
                                <SelectButton v-model="locale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('notificationTemplate.localeLabel')" />
                            </template>
                            <div class="flex flex-col gap-4">
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('notificationTemplate.layoutFrameHint', { token: contentToken }) }}</p>

                                <!-- 틀 -->
                                <div class="flex flex-col gap-1.5">
                                    <div class="flex flex-wrap items-baseline justify-between gap-2">
                                        <span :id="frameLabelId" class="text-sm font-medium">{{ t('notificationTemplate.frameLabel', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) }) }}</span>
                                        <MailTemplateVariableMenu v-if="!readonly" :variables="frameVariables" :disabled="saving" @pick="insertFrame" />
                                    </div>
                                    <RichEditor
                                        :key="`${formKey}-${locale}`"
                                        ref="frameEditor"
                                        v-model="form.frames[locale]"
                                        level="SIMPLE"
                                        compact
                                        :disabled="readonly || saving"
                                        :invalid="Boolean(fieldError(`frames.${locale}`))"
                                        :labelledby="frameLabelId"
                                        :describedby="frameMessageId"
                                        @ready="(html: string) => onFrameReady(locale, html)"
                                    />
                                    <div :id="frameMessageId" aria-live="polite" class="text-xs leading-relaxed">
                                        <p v-if="fieldError(`frames.${locale}`)" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fieldError(`frames.${locale}`) }}</p>
                                        <p v-else class="m-0 text-muted-color">{{ t('notificationTemplate.frameHelp', { token: contentToken }) }}</p>
                                    </div>
                                </div>

                                <!-- 꼬리말 -->
                                <LabeledField :label="t('notificationTemplate.footerLabel', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) })" :hint="t('notificationTemplate.maxHint', { max: UtilMailTemplate.FOOTER_MAX })" :help="t('notificationTemplate.footerHelp')" :error="fieldError(`footer.${locale}`)">
                                    <template #aside>
                                        <MailTemplateVariableMenu v-if="!readonly" :variables="footerVariables" :disabled="saving" @pick="insertFooter" />
                                    </template>
                                    <template #default="{ id, describedBy, invalid }">
                                        <Textarea :id="id" ref="footerInput" v-model="form.footer[locale]" name="footer" rows="3" auto-resize :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <p v-if="otherLocaleError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('notificationTemplate.otherLocaleError', { locale: UtilNotificationTemplateMessages.localeLabel(t, otherLocale) }) }}</p>
                            </div>
                        </AppGadget>
                    </div>

                    <!-- 오른쪽 — 미리보기(저장 전 값) -->
                    <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                        <AppGadget :title="t('notificationTemplate.previewTitle')">
                            <MailTemplatePreviewFrame :subject="preview.subject" :html="preview.html" :loading="preview.loading" :error="preview.error" height="30rem" />
                        </AppGadget>
                    </div>
                </div>

                <StickySaveBar v-if="!readonly" submit :revertable="!isNew" :dirty="isNew || dirty" :saving="saving" :disabled="invalidCount > 0" :count="changeCount" :error-count="invalidCount" :error="formError" @revert="revert" />
            </form>
        </template>

        <!-- 레이아웃 변경 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(처음 열 때 받는다) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('notificationTemplate.layoutHistoryHint')">
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
import type { MailLayoutAudience, MailLayoutColor, MailLayoutSaveRequest, MailLayoutView } from '~/composables/template/api/useMailLayoutApi.types';
import { MAIL_LAYOUT_AUDIENCES } from '~/composables/template/api/useMailLayoutApi.types';
import type { TemplateAudience, TemplateLocale } from '~/composables/template/api/useNotificationTemplateApi.types';
import type { MailLayoutField } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

const { t } = useI18n();

const api = useMailLayoutApi();
const templateApi = useNotificationTemplateApi();
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

const LOCALES: readonly TemplateLocale[] = ['ko', 'en'];
const COLOR_CHOICES = ['NONE', ...UtilMailTemplate.HEADER_COLORS] as const;
const contentToken = UtilMailTemplate.token(UtilMailTemplate.CONTENT);
const colorLabelId = useId();
const frameLabelId = useId();
const frameMessageId = useId();
const audienceLabelId = useId();
const audienceMessageId = useId();

const id = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => id.value === 'new');
const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);

/* ── 받은 것 ── */

/** 저장된 레이아웃(새로 만드는 중이면 null) */
const saved = ref<MailLayoutView | null>(null);
/** 칸을 채운 출처 — 저장된 것 · 복사 원본 · 기본 레이아웃(새로 만들기). 사이트 이름·로고·변수도 여기서 읽는다 */
const base = ref<MailLayoutView | null>(null);
const ready = ref(false);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);

const readonly = computed(() => !canManage.value || Boolean(saved.value?.builtin));
const pageTitle = computed(() => (isNew.value ? t('notificationTemplate.newLayoutTitle') : (saved.value?.name ?? t('notificationTemplate.layoutTitle'))));
useSeoMeta({ title: () => pageTitle.value });

const localeOptions = computed(() => LOCALES.map((value) => ({ value, label: UtilNotificationTemplateMessages.localeLabel(t, value) })));

/* ── 편집 값 ── */

const form = reactive<MailLayoutSaveRequest>({ name: '', audience: 'MEMBER', showLogo: true, showSiteName: true, headerColor: null, footer: { ko: '', en: '' }, frames: { ko: '', en: '' } });

/** 틀에서 쓸 수 있는 변수 — 지금 고른 쓰는 대상의 것(대상을 바꾸면 따라 바뀐다) */
const frameVariables = computed(() => base.value?.variables[form.audience] ?? []);
/** 꼬리말은 본문 자리(`content`)를 쓸 수 없다 */
const footerVariables = computed(() => frameVariables.value.filter((v) => v.name !== UtilMailTemplate.CONTENT));
const commonNames = computed(() => footerVariables.value.map((v) => v.name));

/* ── 쓰는 대상 — 쓰는 템플릿의 받는 사람과 맞지 않는 대상은 잠근다 ── */

/** 저장된 레이아웃을 쓰는 템플릿 수(받는 사람별) — 새로 만들기는 아무도 안 쓴다 */
const usedCounts = computed<Record<TemplateAudience, number>>(() => saved.value?.templateCounts ?? { MEMBER: 0, OPERATOR: 0 });
/** 머리의 「쓰는 템플릿」 링크 — 저장된 쓰는 대상 기준(편집 중인 대상이 아니다) */
const templateLinks = computed(() => (saved.value ? UtilNotificationTemplateMessages.layoutTemplateLinks(saved.value) : []));
/** 이 대상으로 바꿀 수 없는 까닭이 되는 받는 사람(없으면 null) — 「회원」 전용인데 운영자 템플릿이 쓰는 중 … */
function blockerOf(audience: MailLayoutAudience): TemplateAudience | null {
    if (audience === 'ANY') return null;
    const other: TemplateAudience = audience === 'MEMBER' ? 'OPERATOR' : 'MEMBER';
    return usedCounts.value[other] > 0 ? other : null;
}
const audienceOptions = computed(() => MAIL_LAYOUT_AUDIENCES.map((value) => ({ value, label: UtilNotificationTemplateMessages.audienceLabel(t, value), disabled: blockerOf(value) !== null && value !== saved.value?.audience })));
/** 잠긴 대상마다 그 까닭 한 줄 */
const audienceLockReasons = computed(() =>
    MAIL_LAYOUT_AUDIENCES.flatMap((locked) => {
        const blocker = locked === form.audience ? null : blockerOf(locked);
        if (!blocker) return [];
        return [
            t('notificationTemplate.layoutAudienceLocked', {
                audience: UtilNotificationTemplateMessages.audienceLabel(t, blocker),
                count: usedCounts.value[blocker],
                other: UtilNotificationTemplateMessages.audienceLabel(t, locked),
            }),
        ];
    }),
);
/** 처음 값 — 틀은 에디터가 정규화한 값으로 바꿔 둔다(`onFrameReady`) */
const baseline = ref<MailLayoutSaveRequest>(JSON.parse(JSON.stringify(form)) as MailLayoutSaveRequest);
const formKey = ref(0);
const locale = ref<TemplateLocale>('ko');
const otherLocale = computed<TemplateLocale>(() => (locale.value === 'ko' ? 'en' : 'ko'));
const tried = ref(false);
const serverErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

const colorModel = computed<MailLayoutColor | 'NONE'>({
    get: () => form.headerColor ?? 'NONE',
    set: (value) => (form.headerColor = value === 'NONE' ? null : value),
});

function fill(): void {
    const from = base.value;
    if (!from) return;
    form.name = saved.value ? from.name : isNew.value && queryText(route.query.from) ? t('notificationTemplate.copyName', { name: from.name }).slice(0, UtilMailTemplate.NAME_MAX) : '';
    form.audience = from.audience;
    form.showLogo = from.showLogo;
    form.showSiteName = from.showSiteName;
    form.headerColor = from.headerColor;
    form.footer = { ...from.footer };
    form.frames = { ...from.frames };
    baseline.value = JSON.parse(JSON.stringify(form)) as MailLayoutSaveRequest;
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

function onFrameReady(at: TemplateLocale, html: string): void {
    if (form.frames[at] === baseline.value.frames[at]) {
        baseline.value = { ...baseline.value, frames: { ...baseline.value.frames, [at]: html } };
        form.frames[at] = html;
    }
}

const changeCount = computed(() => {
    const b = baseline.value;
    let n = 0;
    if (form.name !== b.name) n += 1;
    if (form.audience !== b.audience) n += 1;
    if (form.showLogo !== b.showLogo) n += 1;
    if (form.showSiteName !== b.showSiteName) n += 1;
    if (form.headerColor !== b.headerColor) n += 1;
    for (const at of LOCALES) {
        if (form.footer[at] !== b.footer[at]) n += 1;
        if (form.frames[at] !== b.frames[at]) n += 1;
    }
    return n;
});
const dirty = computed(() => ready.value && changeCount.value > 0);

/* ── 검사 ── */

const problems = computed(() => UtilMailTemplate.validateLayout(form, commonNames.value));

function fieldError(field: MailLayoutField): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (readonly.value) return '';
    const problem = problems.value[field];
    if (!problem) return '';
    if (problem.code === 'REQUIRED' && !tried.value) return '';
    return UtilNotificationTemplateMessages.problem(t, field, problem);
}

const FIELDS: readonly MailLayoutField[] = ['name', 'audience', 'footer.ko', 'footer.en', 'frames.ko', 'frames.en'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length + (serverErrors.value.headerColor ? 1 : 0));
/** 고르지 않은 언어 쪽에 고칠 곳이 있다 — 그 언어로 바꿔 보라고 알린다 */
const otherLocaleError = computed(() => Boolean(fieldError(`frames.${otherLocale.value}`) || fieldError(`footer.${otherLocale.value}`)));

// 칸을 고치면 서버 문구를 걷는다(다시 저장할 때 서버가 다시 본다)
watch(
    () => JSON.stringify(form),
    () => {
        if (Object.values(serverErrors.value).some(Boolean)) serverErrors.value = {};
    },
);

/* ── 변수 넣기 ── */

const frameEditor = ref<{ insertText: (text: string) => void } | null>(null);
const footerInput = ref<{ $el: HTMLTextAreaElement } | null>(null);

function insertFrame(name: string): void {
    frameEditor.value?.insertText(UtilMailTemplate.token(name));
}

/** 꼬리말 칸의 커서 자리에 넣는다 */
function insertFooter(name: string): void {
    const token = UtilMailTemplate.token(name);
    const at = locale.value;
    const el = footerInput.value?.$el;
    const text = form.footer[at];
    const start = el?.selectionStart ?? text.length;
    const end = el?.selectionEnd ?? start;
    form.footer[at] = text.slice(0, start) + token + text.slice(end);
    const caret = start + token.length;
    void nextTick(() => {
        el?.focus();
        el?.setSelectionRange(caret, caret);
    });
}

/* ── 읽기 ── */

let loadSeq = 0;

async function load(): Promise<void> {
    const mine = ++loadSeq;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    ready.value = false;
    try {
        let layout: MailLayoutView;
        let current: MailLayoutView | null = null;
        if (!isNew.value) {
            current = await api.get(id.value);
            layout = current;
        } else {
            // 복사 원본, 없으면 받는 사람(`?audience=`, 없으면 회원)의 기본 레이아웃 값으로 채운다
            const wanted: TemplateAudience = route.query.audience === 'OPERATOR' ? 'OPERATOR' : 'MEMBER';
            const fromId = queryText(route.query.from) ?? (await api.list()).find((item) => item.builtin && item.audience === wanted)?.id ?? '';
            layout = await api.get(fromId);
        }
        if (mine !== loadSeq) return;
        saved.value = current;
        base.value = layout;
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

/* ── 미리보기 — 저장 전 값(견본 본문 한 줄 + 공통 변수 견본 값) ── */

const preview = reactive({ subject: '', html: '', loading: false, error: '' });
let previewTimer: ReturnType<typeof setTimeout> | null = null;
let previewSeq = 0;

watch(
    () => [ready.value, locale.value, JSON.stringify(form)] as const,
    () => {
        if (previewTimer) clearTimeout(previewTimer);
        if (!ready.value) return;
        previewTimer = setTimeout(() => void runPreview(), 400);
    },
);
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
});

async function runPreview(): Promise<void> {
    const mine = ++previewSeq;
    preview.loading = true;
    try {
        // 종류 없이 — 공통 변수만 견본 값으로 채운다
        const result = await templateApi.preview({
            locale: locale.value,
            subject: t('notificationTemplate.layoutSampleSubject'),
            bodyHtml: `<p>${UtilMailTemplate.escape(t('notificationTemplate.layoutSampleBody'))}</p>`,
            layout: JSON.parse(JSON.stringify(form)) as MailLayoutSaveRequest,
        });
        if (mine !== previewSeq) return;
        preview.subject = result.subject;
        preview.html = result.html;
        preview.error = '';
    } catch (e) {
        if (mine === previewSeq) preview.error = toApiError(e).message;
    } finally {
        if (mine === previewSeq) preview.loading = false;
    }
}

/* ── 저장 · 되돌리기 · 복사 · 삭제 ── */

function revert(): void {
    if (saved.value) fill();
}

/** 이 화면을 떠나는 길을 열어 둔다(만든 뒤·지운 뒤 — 떠나기 확인을 건너뛴다) */
let leaving = false;

async function save(): Promise<void> {
    if (readonly.value || saving.value) return;
    if (!isNew.value && !dirty.value) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0) {
        // 고칠 곳이 고르지 않은 언어에만 있으면 그 언어로 바꿔 보인다
        if (!fieldError(`frames.${locale.value}`) && !fieldError(`footer.${locale.value}`) && otherLocaleError.value) locale.value = otherLocale.value;
        return;
    }
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    const body = JSON.parse(JSON.stringify({ ...form, name: form.name.trim() })) as MailLayoutSaveRequest;
    try {
        if (isNew.value || !saved.value) {
            const created = await api.create(body);
            toast.success(t('notificationTemplate.layoutCreated'));
            leaving = true;
            await router.replace(`/settings/notification-templates/layouts/${encodeURIComponent(created.id)}`);
            return;
        }
        saved.value = await api.update(saved.value.id, body);
        base.value = saved.value;
        toast.success(t('notificationTemplate.layoutSaved'));
        fill();
        if (historyLoaded) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'audience', 'headerColor', 'footer.ko', 'footer.en', 'frames.ko', 'frames.en']) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        serverErrors.value = found;
        // 칸에 붙지 않는 오류(띠 색 포함 — 칸 아래 문구 자리가 없다)는 저장 줄 안 한 줄, 서버 문구 그대로
        if (Object.keys(found).length === 0) formError.value = error.message;
        else if (found.headerColor) formError.value = found.headerColor;
    } finally {
        saving.value = false;
    }
}

function copySaved(): void {
    if (!saved.value) return;
    void router.push({ path: '/settings/notification-templates/layouts/new', query: { from: saved.value.id } });
}

const deleteBlockedReason = computed(() => (saved.value?.templateCount ? t('notificationTemplate.layoutDeleteInUse', { count: saved.value.templateCount }) : ''));
const deleting = ref(false);

async function confirmDelete(): Promise<void> {
    const layout = saved.value;
    if (!layout || deleting.value) return;
    const ok = await confirm({ title: t('notificationTemplate.layoutDeleteTitle', { name: layout.name }), message: t('notificationTemplate.layoutDeleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(layout.id);
        toast.success(t('notificationTemplate.layoutDeleted'));
        leaving = true;
        await router.push('/settings/notification-templates/layouts');
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        deleting.value = false;
    }
}

/* ── 변경 이력(오른쪽 서랍) ── */

const historyOpen = ref(false);
let historyLoaded = false;
const history = useSettingHistory((page, size) => api.history(saved.value?.id ?? '', page, size));

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
    if (to.params.id === from.params.id && to.query.from === from.query.from) return true;
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('notificationTemplate.leaveTitle'), message: t('notificationTemplate.leaveMessage'), confirmLabel: t('notificationTemplate.leaveConfirm'), tone: 'danger' });
});
watch(
    () => [id.value, route.query.from] as const,
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
/* 띠 색 고르기 — 게시판 표시색 고르기(`boards/[slug].vue`)와 같은 모양 */
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
.color-opt__none {
    width: 1.1rem;
    height: 1.1rem;
    border: 1.5px dashed var(--line-strong);
    border-radius: 9999px;
}
</style>
