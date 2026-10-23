<template>
    <!--
        메일 템플릿 편집(`/settings/mail-templates/[id]`, 새로 만들기 `/settings/mail-templates/new?kind=` · 복사 `new?from=<id>`) — 계약 `docs/features/message-template-contract.md` 「개정」 3.
        한 화면(서랍이 아니다): 머리(이름·딱지·「변경 이력」·복사·삭제·목록으로) → 왼쪽 편집 칸(이름 · 종류(새로 만들 때만 고른다) · 언어 · 레이아웃 · 제목 · 본문(간단 에디터 + 변수 넣기) · 버튼 블록) → 오른쪽 미리보기(고른 레이아웃으로, 격리된 iframe) + 「나에게 시험 발송」.
        저장은 화면 하나로 한 번(`POST`·`PUT templates`) → 하단 떠 있는 저장 줄(`StickySaveBar`, 「기본으로 되돌리기」는 그 줄의 왼쪽 단추).
        - 받는 사람(개정 2): 종류에서 정해지는 읽기 전용 칸. 레이아웃 고르기는 받는 사람이 같거나 `ANY` 인 레이아웃만 보이고, 새 템플릿은 그 받는 사람의 기본 레이아웃(회원 기본 · 운영자 기본)으로 시작한다.
          변수 메뉴·검사·미리보기 견본 값은 종류의 변수(받는 사람의 공통 변수 — 회원 `siteUrl` · 운영자 `adminUrl` 포함)를 따른다.
        - 새로 만들기: 종류를 고르면 그 종류·언어의 기본 템플릿 내용과 그 받는 사람의 기본 레이아웃으로 채운다(고친 내용이 있으면 바꾸기 전에 묻는다. 고른 레이아웃이 새 받는 사람에게 맞으면 그대로 둔다).
          `new?audience=` 는 그 받는 사람의 첫 종류로 시작한다. 복사: 원본의 내용·언어·레이아웃, 이름 「원본 (복사)」, 종류는 원본 것으로 고정.
          [저장] 때 만든다 — 만들면 그 템플릿의 편집 주소로 바뀐다.
        - 기본 템플릿과 권한이 `template.read` 뿐이면 읽기 전용이다(입력 `readonly`, 에디터 잠금, 저장 줄 없음). 기본 템플릿은 「복사해서 만들기」를 안내한다.
        - 「버튼」 블록은 에디터 안에서 `{{button}}` 한 줄로 보이고, 글자·링크 변수는 에디터 아래 칸에서 고른다(간단 에디터가 단추를 그리지 못해서다 — 저장 모양은 `UtilMailTemplate`).
        - 검사(`UtilMailTemplate.validate` — 서버와 같은 규칙): 모르는 변수 · 보안 메일 필수 변수 없음 · 길이 · 제목 한 줄 · 버튼 하나(링크 변수만). 서버 `fields.*` 가 오면 그 칸 아래에 그대로.
        - 언어: 그 언어의 「쓰는 템플릿」인 사용자 템플릿은 언어를 바꿀 수 없다. 삭제: 쓰는 중(언어별 기본·규칙)이면 단추를 잠그고, 서버도 409 `TEMPLATE_IN_USE`.
        - 변경 이력: 제목 옆 단추 → 오른쪽 `SettingHistoryDrawer`(처음 열 때 받는다 — components.md 2-5). 저장된 사용자 템플릿만.
        권한: 화면 `template.read` · 바꾸기·시험 발송 `template.manage`. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailTemplateApi` · `useMailLayoutApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <!-- 읽는 중 -->
        <template v-if="loading && !ready">
            <PageTitle :title="isNew ? t('mailTemplate.newTitle') : t('mailTemplate.editTitle')" />
            <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]" role="status" :aria-label="t('common.loading')">
                <Skeleton height="28rem" />
                <Skeleton height="28rem" />
            </div>
        </template>

        <!-- 권한 없음 · 없는 템플릿 · 읽기 실패 -->
        <template v-else-if="!ready || !kind">
            <PageTitle :title="isNew ? t('mailTemplate.newTitle') : t('mailTemplate.editTitle')">
                <template #actions>
                    <Button :label="t('mailTemplate.backToTemplates')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/settings/mail-templates')" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('mailTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('mailTemplate.editTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('mailTemplate.templateNotFound')" :description="loadError">
                    <Button :label="t('mailTemplate.backToTemplates')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push('/settings/mail-templates')" />
                </ErrorState>
                <ErrorState v-else :title="t('mailTemplate.templateLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="pageTitle">
                <template #actions>
                    <Button v-if="historyAvailable" type="button" :label="t('settingHistory.open')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" @click="openHistory" />
                    <span v-if="canManage && saved" v-tooltip.top="copyBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('mailTemplate.copyCreate')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" :disabled="Boolean(copyBlockedReason)" :aria-description="copyBlockedReason || undefined" @click="copySaved" />
                    </span>
                    <span v-if="canManage && saved && !saved.builtin" v-tooltip.top="deleteBlockedReason || undefined" class="inline-flex">
                        <Button type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="outlined" :disabled="Boolean(deleteBlockedReason) || deleting" :loading="deleting" :aria-description="deleteBlockedReason || undefined" @click="confirmDelete" />
                    </span>
                    <Button :label="t('mailTemplate.backToTemplates')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/settings/mail-templates')" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-wrap items-center gap-2">
                <Tag :value="t('mailTemplate.audienceTag', { audience: UtilMailTemplateMessages.audienceLabel(t, kind.audience) })" :icon="UtilMailTemplateMessages.audienceIcon(kind.audience)" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="saved?.builtin" :value="t('mailTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="kind.security" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                <Tag v-for="label in usage" :key="label" :value="label" severity="info" class="mail-template-tag text-[11px]!" />
                <span v-if="saved?.updatedAt" class="text-xs text-muted-color">{{ saved.updatedByName ? t('mailTemplate.updatedBy', { at: time.full(saved.updatedAt), name: saved.updatedByName }) : time.full(saved.updatedAt) }}</span>
            </div>

            <Message v-if="saved?.builtin" severity="secondary" :closable="false" size="small">
                <span>{{ t('mailTemplate.builtinReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('mailTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" :disabled="Boolean(copyBlockedReason)" @click="copySaved" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('mailTemplate.readOnly') }}</Message>
            <Message v-if="kind.security && !readonly" severity="warn" :closable="false" size="small">{{ t('mailTemplate.securityHint', { names: requiredNames }) }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
                    <!-- 왼쪽 — 편집 칸 -->
                    <AppGadget :title="t('mailTemplate.contentTitle')">
                        <div :key="formKey" class="flex flex-col gap-4">
                            <div class="grid gap-4 sm:grid-cols-2">
                                <LabeledField :label="t('mailTemplate.nameLabel')" :hint="t('mailTemplate.maxHint', { max: UtilMailTemplate.NAME_MAX })" :error="fieldError('name')">
                                    <template #default="{ id, describedBy, invalid }">
                                        <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 종류 — 새로 만들 때만 고른다(복사는 원본 종류로 고정) -->
                                <LabeledField :label="t('mailTemplate.kindLabel')" :help="kindPickable ? t('mailTemplate.kindPickHelp') : readonly ? '' : t('mailTemplate.kindLocked')">
                                    <template #aside>
                                        <NuxtLink :to="`/settings/mail-kinds/${kind.kind}`" class="text-xs text-primary hover:underline">{{ t('mailTemplate.openKind') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy }">
                                        <Select v-if="kindPickable" :model-value="kind.kind" :input-id="id" :options="kindOptions" option-label="label" option-value="value" option-group-label="label" option-group-children="items" :disabled="saving || switching" :aria-describedby="describedBy" fluid @update:model-value="pickKind" />
                                        <InputText v-else :id="id" :model-value="kind.name" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <!-- 받는 사람 — 종류에서 정해진다(바꿀 수 없다) -->
                                <LabeledField :label="t('mailTemplate.audienceLabel')" :help="t('mailTemplate.audienceHelp')">
                                    <template #default="{ id, describedBy }">
                                        <InputText :id="id" :model-value="UtilMailTemplateMessages.audienceLabel(t, kind.audience)" readonly :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>

                                <div class="flex flex-col gap-1.5">
                                    <span :id="localeLabelId" class="text-sm font-medium">{{ t('mailTemplate.localeLabel') }}</span>
                                    <SelectButton :model-value="form.locale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="readonly || saving || localeLocked || switching" :aria-labelledby="localeLabelId" class="self-start" @update:model-value="pickLocale" />
                                    <p v-if="fieldError('locale')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('locale') }}</p>
                                    <p v-else-if="localeLocked && !readonly" class="m-0 text-xs text-muted-color">{{ t('mailTemplate.localeLocked') }}</p>
                                </div>

                                <LabeledField :label="t('mailTemplate.layoutLabel')" :help="t('mailTemplate.layoutHelp')" :error="fieldError('layoutId')">
                                    <template #aside>
                                        <NuxtLink v-if="form.layoutId" :to="`/settings/mail-layouts/${encodeURIComponent(form.layoutId)}`" class="text-xs text-primary hover:underline">{{ t('mailTemplate.openLayout') }}</NuxtLink>
                                    </template>
                                    <template #default="{ id, describedBy, invalid }">
                                        <Select v-model="form.layoutId" :input-id="id" :options="layoutOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>
                            </div>

                            <LabeledField :label="t('mailTemplate.subjectLabel')" :hint="t('mailTemplate.maxHint', { max: UtilMailTemplate.SUBJECT_MAX })" :help="t('mailTemplate.subjectHelp')" :error="fieldError('subject')">
                                <template #aside>
                                    <MailTemplateVariableMenu v-if="!readonly" :variables="kind.variables" @pick="insertSubject" />
                                </template>
                                <template #default="{ id, describedBy, invalid }">
                                    <InputText :id="id" ref="subjectInput" v-model="form.subject" name="subject" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid @keydown.enter.prevent />
                                </template>
                            </LabeledField>

                            <div class="flex flex-col gap-1.5">
                                <div class="flex items-baseline justify-between gap-2">
                                    <span :id="bodyLabelId" class="text-sm font-medium">{{ t('mailTemplate.bodyLabel') }}</span>
                                    <MailTemplateVariableMenu v-if="!readonly" :variables="kind.variables" @pick="insertBody" />
                                </div>
                                <RichEditor
                                    :key="`editor-${formKey}`"
                                    ref="editor"
                                    v-model="editorHtml"
                                    level="SIMPLE"
                                    compact
                                    :disabled="readonly || saving"
                                    :invalid="Boolean(fieldError('bodyHtml'))"
                                    :labelledby="bodyLabelId"
                                    :describedby="bodyMessageId"
                                    :placeholder="t('mailTemplate.bodyPlaceholder')"
                                    @ready="onEditorReady"
                                />
                                <div :id="bodyMessageId" aria-live="polite" class="text-xs leading-relaxed">
                                    <p v-if="fieldError('bodyHtml')" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ fieldError('bodyHtml') }}</p>
                                    <p v-else class="m-0 text-muted-color">{{ t('mailTemplate.bodyHelp') }}</p>
                                </div>
                            </div>

                            <!-- 「버튼」 블록 — 링크 변수로만 가는 단추 하나 -->
                            <section class="flex flex-col gap-3 rounded-lg border border-(--line) p-3" :aria-labelledby="buttonTitleId">
                                <div class="flex flex-wrap items-center gap-2">
                                    <h3 :id="buttonTitleId" class="m-0 text-sm font-medium">{{ t('mailTemplate.buttonTitle') }}</h3>
                                    <span class="text-xs text-muted-color">{{ linkVariables.length ? t('mailTemplate.buttonHint') : t('mailTemplate.buttonNoLink') }}</span>
                                    <div v-if="!readonly" class="ml-auto">
                                        <Button v-if="!button" type="button" :label="t('mailTemplate.buttonAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="!linkVariables.length || saving" @click="addButton" />
                                        <Button v-else type="button" :label="t('mailTemplate.buttonRemove')" icon="pi pi-times" size="small" severity="secondary" variant="text" :disabled="saving" @click="removeButton" />
                                    </div>
                                </div>
                                <div v-if="button" class="grid gap-3 sm:grid-cols-2">
                                    <LabeledField :label="t('mailTemplate.buttonLabel')" :hint="t('mailTemplate.maxHint', { max: UtilMailTemplate.BUTTON_LABEL_MAX })">
                                        <template #default="{ id, describedBy, invalid }">
                                            <InputText :id="id" v-model="button.label" name="buttonLabel" autocomplete="off" :readonly="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                    <LabeledField :label="t('mailTemplate.buttonLink')">
                                        <template #default="{ id, describedBy, invalid }">
                                            <Select v-model="button.link" :input-id="id" :options="linkOptions" option-label="label" option-value="value" :disabled="readonly || saving" :invalid="invalid || Boolean(fieldError('button'))" :aria-describedby="describedBy" fluid />
                                        </template>
                                    </LabeledField>
                                </div>
                                <p v-if="fieldError('button')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('button') }}</p>
                                <p v-else-if="button" class="m-0 text-xs text-muted-color">{{ t('mailTemplate.buttonPlaceHint', { token: buttonToken }) }}</p>
                            </section>
                        </div>
                    </AppGadget>

                    <!-- 오른쪽 — 미리보기(고른 레이아웃으로) -->
                    <div class="flex min-w-0 flex-col gap-4 xl:sticky xl:top-4">
                        <AppGadget :title="t('mailTemplate.previewTitle')">
                            <template v-if="canManage" #actions>
                                <span v-tooltip.top="testBlockedReason || undefined" class="inline-flex">
                                    <Button type="button" :label="t('mailTemplate.testButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="openTest" />
                                </span>
                            </template>
                            <MailTemplatePreviewFrame :subject="preview.subject" :html="preview.html" :loading="preview.loading" :error="preview.error" height="34rem" />
                        </AppGadget>
                    </div>
                </div>

                <StickySaveBar v-if="!readonly" submit :revertable="!isNew" :dirty="isNew || dirty" :saving="saving" :disabled="invalidCount > 0 || switching" :count="changeCount" :error-count="invalidCount" :error="formError" @revert="revert">
                    <template #extra>
                        <Button v-if="defaultFor(form.locale)" type="button" :label="t('mailTemplate.resetDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="saving" @click="resetToDefault" />
                    </template>
                </StickySaveBar>
            </form>
        </template>

        <!-- 템플릿 변경 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(처음 열 때 받는다) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('mailTemplate.historyHint')">
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

        <MailTestDialog v-model:visible="testOpen" v-model:to="testTo" :help="t('mailTemplate.testHelp')" :to-error="testToError" :error="testError" :testing="testing" :result="testResult" @send="runTest" />
    </div>
</template>

<script setup lang="ts">
import type { MailTestDialogResult } from '~/components/mail/MailTestDialog.vue';
import { ApiError } from '~/composables/common/useApiClient';
import { mockTemplateCan } from '~/composables/template/api/mock/mail-template-mock';
import type { MailLayoutListItemView } from '~/composables/template/api/useMailLayoutApi.types';
import type { MailAudience, MailKindDetailView, MailKindView, MailLocale, MailTemplateView } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_AUDIENCES, MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import type { MailButton, MailTemplateField } from '~/utils/util-mail-template';
import UtilMailSettingsMessages from '~/utils/util-mail-settings-messages';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();

const api = useMailTemplateApi();
const layoutApi = useMailLayoutApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { user } = useAdminAuth();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

const canManage = computed(() => can('template.manage'));

const LOCALES: readonly MailLocale[] = ['ko', 'en'];
const localeLabelId = useId();
const bodyLabelId = useId();
const bodyMessageId = useId();
const buttonTitleId = useId();
const buttonToken = UtilMailTemplate.token(UtilMailTemplate.BUTTON);

const id = computed(() => String(route.params.id ?? ''));
const isNew = computed(() => id.value === 'new');
const queryText = (value: unknown) => (typeof value === 'string' && value ? value : null);

/* ── 받은 것 ── */

const kinds = ref<MailKindView[]>([]);
const layouts = ref<MailLayoutListItemView[]>([]);
/** 저장된 템플릿(새로 만드는 중이면 null) */
const saved = ref<MailTemplateView | null>(null);
/** 복사 원본(`?from=`) */
const source = ref<MailTemplateView | null>(null);
/** 템플릿의 종류 상세 — 언어별 기본 템플릿(되돌리기·새로 만들기 내용), 쓰는 곳(언어 잠금·삭제 잠금) */
const detail = ref<MailKindDetailView | null>(null);
const ready = ref(false);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
const denied = ref(false);
/** 새로 만들기에서 종류를 바꾸는 중(그 종류 상세를 받는 중) */
const switching = ref(false);

const kind = computed(() => detail.value?.summary ?? null);
/** 받는 사람의 기본 레이아웃(builtin — 회원 기본 · 운영자 기본) */
const defaultLayoutIdOf = (audience: MailAudience) => layouts.value.find((item) => item.builtin && item.audience === audience)?.id ?? '';
/** 이 템플릿(받는 사람)이 쓸 수 있는 레이아웃인가 */
const layoutUsable = (layoutId: string, audience: MailAudience) => {
    const layout = layouts.value.find((item) => item.id === layoutId);
    return Boolean(layout && UtilMailTemplate.layoutFits(layout.audience, audience));
};
/** 종류를 고를 수 있다 — 새로 만들기(복사가 아닐 때)만 */
const kindPickable = computed(() => isNew.value && !source.value);
const readonly = computed(() => !canManage.value || Boolean(saved.value?.builtin));
const historyAvailable = computed(() => Boolean(saved.value && !saved.value.builtin));
const requiredNames = computed(() => (kind.value?.variables ?? []).filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));
const pageTitle = computed(() => (isNew.value ? t('mailTemplate.newTitle') : (saved.value?.name ?? t('mailTemplate.editTitle'))));

useSeoMeta({ title: () => pageTitle.value });

const localeOptions = computed(() => LOCALES.map((value) => ({ value, label: UtilMailTemplateMessages.localeLabel(t, value) })));
/** 레이아웃 고르기 — 받는 사람이 같거나 `ANY` 인 것만(기본 레이아웃 먼저 — 받은 차례) */
const layoutOptions = computed(() => {
    const audience = kind.value?.audience;
    if (!audience) return [];
    return layouts.value
        .filter((item) => UtilMailTemplate.layoutFits(item.audience, audience))
        .map((item) => ({
            value: item.id,
            label: item.builtin ? t('mailTemplate.layoutBuiltinOption', { name: item.name }) : item.audience === 'ANY' ? t('mailTemplate.layoutAnyOption', { name: item.name }) : item.name,
        }));
});
/** 종류 고르기 — 받는 사람 › 묶음(계정·보안 메일 / 알림 메일 · 영역)별 */
const kindOptions = computed(() => {
    const groups = new Map<string, { label: string; items: { value: string; label: string }[] }>();
    for (const audience of MAIL_AUDIENCES) {
        for (const item of kinds.value.filter((other) => other.audience === audience)) {
            const label = `${UtilMailTemplateMessages.audienceMailLabel(t, audience)} · ${UtilMailTemplateMessages.groupLabel(t, item.group, item.area)}`;
            const group = groups.get(label) ?? { label, items: [] };
            group.items.push({ value: item.kind, label: item.name });
            groups.set(label, group);
        }
    }
    return [...groups.values()];
});
const linkVariables = computed(() => (kind.value?.variables ?? []).filter((v) => v.link));
const linkOptions = computed(() => linkVariables.value.map((v) => ({ value: v.name, label: `${UtilMailTemplate.token(v.name)} · ${v.description}` })));

/* ── 편집 값 ── */

interface Baseline {
    name: string;
    locale: MailLocale;
    layoutId: string;
    subject: string;
    editorHtml: string;
    button: MailButton | null;
}

const form = reactive<{ name: string; locale: MailLocale; layoutId: string; subject: string }>({ name: '', locale: 'ko', layoutId: '', subject: '' });
const editorHtml = ref('');
const button = ref<MailButton | null>(null);
/** 처음 값 — 「고쳤는가」의 기준. 본문은 에디터가 정규화한 값으로 바꿔 둔다(`onEditorReady`) */
const baseline = ref<Baseline>({ name: '', locale: 'ko', layoutId: '', subject: '', editorHtml: '', button: null });
/** 다시 채우면 칸(에디터 포함)을 새로 세운다 */
const formKey = ref(0);
/** 저장을 눌러 본 적이 있는가 — 그 전에는 「비어 있음」을 빨갛게 보이지 않는다 */
const tried = ref(false);
/** 서버 문구 — 칸을 고치면 그 칸 것을 걷는다 */
const serverErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

/** 템플릿 내용(제목·본문·버튼)을 칸에 넣는다 */
function setContent(template: Pick<MailTemplateView, 'subject' | 'bodyHtml'>): void {
    const split = UtilMailTemplate.splitBody(template.bodyHtml);
    form.subject = template.subject;
    editorHtml.value = split.editorHtml;
    button.value = split.button ? { ...split.button } : null;
}

/** 지금 칸 값을 기준으로 삼는다(새로 세운 폼) */
function setBaseline(): void {
    baseline.value = { name: form.name, locale: form.locale, layoutId: form.layoutId, subject: form.subject, editorHtml: editorHtml.value, button: button.value ? { ...button.value } : null };
    tried.value = false;
    serverErrors.value = {};
    formError.value = '';
    formKey.value += 1;
}

function copyName(from: MailTemplateView): string {
    if (from.builtin) return t('mailTemplate.copyNameBuiltin', { kind: kind.value?.name ?? '' }).slice(0, UtilMailTemplate.NAME_MAX);
    return t('mailTemplate.copyName', { name: from.name }).slice(0, UtilMailTemplate.NAME_MAX);
}

/** 이 종류·언어의 기본 템플릿 */
function defaultFor(locale: MailLocale): MailTemplateView | undefined {
    const view = detail.value;
    return view?.templates.find((item) => item.id === view.defaults[locale]);
}

/** 칸을 채운다 — 저장된 것 · 복사 원본 · 새로 만들기(그 종류·언어의 기본 템플릿 + 기본 레이아웃) */
function fill(): void {
    if (saved.value) {
        form.name = saved.value.name;
        form.locale = saved.value.locale;
        form.layoutId = saved.value.layoutId;
        setContent(saved.value);
    } else if (source.value) {
        form.name = copyName(source.value);
        form.locale = source.value.locale;
        form.layoutId = source.value.layoutId;
        setContent(source.value);
    } else {
        form.name = '';
        // 새 템플릿은 그 받는 사람의 기본 레이아웃으로 시작한다
        form.layoutId = kind.value ? defaultLayoutIdOf(kind.value.audience) : '';
        const seed = defaultFor(form.locale);
        if (seed) setContent(seed);
    }
    setBaseline();
}

/** 에디터가 처음 만든 정규화 HTML — 아직 고치지 않았으면 기준도 그 값으로(정규화 차이를 「고침」으로 세지 않는다) */
function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value.editorHtml) {
        baseline.value = { ...baseline.value, editorHtml: html };
        editorHtml.value = html;
    }
}

const composed = computed(() => UtilMailTemplate.joinBody(editorHtml.value, button.value));

/** 내용(제목·본문·버튼)을 기준에서 고쳤는가 */
const contentChanged = computed(() => {
    const b = baseline.value;
    return form.subject !== b.subject || editorHtml.value !== b.editorHtml || (button.value?.label ?? null) !== (b.button?.label ?? null) || (button.value?.link ?? null) !== (b.button?.link ?? null);
});
const changeCount = computed(() => {
    const b = baseline.value;
    let n = 0;
    if (form.name !== b.name) n += 1;
    if (form.locale !== b.locale) n += 1;
    if (form.layoutId !== b.layoutId) n += 1;
    if (form.subject !== b.subject) n += 1;
    if (editorHtml.value !== b.editorHtml || (button.value?.label ?? null) !== (b.button?.label ?? null) || (button.value?.link ?? null) !== (b.button?.link ?? null)) n += 1;
    return n;
});
const dirty = computed(() => ready.value && changeCount.value > 0);

// 칸을 고치면 그 칸의 서버 문구를 걷는다(버튼은 본문 칸의 문구다)
watch(
    () => form.name,
    () => clearServer('name'),
);
watch(
    () => form.locale,
    () => clearServer('locale'),
);
watch(
    () => form.layoutId,
    () => clearServer('layoutId'),
);
watch(
    () => form.subject,
    () => clearServer('subject'),
);
watch(composed, () => clearServer('bodyHtml'));
function clearServer(field: string): void {
    if (serverErrors.value[field]) serverErrors.value = { ...serverErrors.value, [field]: '' };
}

// 에디터 안의 `{{button}}` 과 버튼 칸을 맞춘다 — 자리가 생기면 기본 버튼, 사라지면 버튼도 없앤다
watch(editorHtml, (html) => {
    const tokens = UtilMailTemplate.buttonTokens(html);
    if (tokens > 0 && !button.value && linkVariables.value[0]) button.value = { label: t('mailTemplate.buttonDefaultLabel'), link: linkVariables.value[0].name };
    else if (tokens === 0 && button.value) button.value = null;
});

/* ── 검사 ── */

const problems = computed(() => (kind.value ? UtilMailTemplate.validate({ name: form.name, subject: form.subject, bodyHtml: composed.value }, kind.value) : {}));

function fieldError(field: MailTemplateField | 'locale' | 'layoutId'): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (field === 'locale' || readonly.value) return '';
    if (field === 'layoutId') return form.layoutId ? '' : tried.value ? t('mailTemplate.layoutRequired') : '';
    const problem = problems.value[field];
    if (!problem) return '';
    if (problem.code === 'REQUIRED' && !tried.value) return '';
    return UtilMailTemplateMessages.problem(t, field, problem);
}

const FIELDS: readonly (MailTemplateField | 'locale' | 'layoutId')[] = ['name', 'locale', 'layoutId', 'subject', 'bodyHtml', 'button'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length);

/** 시험 발송은 제목·본문이 맞을 때만 — 이름은 상관없다 */
const testBlockedReason = computed(() => {
    const p = problems.value;
    if (p.subject || p.bodyHtml || p.button) return t('mailTemplate.testNeedsValid');
    return '';
});

/** 그 언어의 기본으로 쓰는 중이면 언어를 바꿀 수 없다(서버가 거절한다 — 목업 가정) */
const localeLocked = computed(() => Boolean(saved.value && detail.value?.selected[saved.value.locale] === saved.value.id));

/** 저장된 상태에서 이 템플릿을 쓰는 곳(「한국어 기본」 · 「규칙 2개」) */
const usage = computed(() => {
    const view = detail.value;
    const template = saved.value;
    if (!view || !template) return [];
    const out: string[] = [];
    for (const locale of LOCALES) if (view.selected[locale] === template.id) out.push(t('mailTemplate.usedAsDefault', { locale: UtilMailTemplateMessages.localeLabel(t, locale) }));
    const rules = view.rules.filter((rule) => rule.templateId === template.id).length;
    if (rules > 0) out.push(t('mailTemplate.usedByRules', { count: rules }));
    return out;
});
const deleteBlockedReason = computed(() => (usage.value.length ? t('mailTemplate.deleteInUse') : ''));
const copyBlockedReason = computed(() => {
    const count = (detail.value?.templates ?? []).filter((item) => !item.builtin).length;
    return count >= MAIL_TEMPLATE_LIMIT ? t('mailTemplate.limitReached', { max: MAIL_TEMPLATE_LIMIT }) : '';
});

/* ── 읽기 ── */

let loadSeq = 0;

async function load(): Promise<void> {
    const mine = ++loadSeq;
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    ready.value = false;
    try {
        const [kindList, layoutList] = await Promise.all([api.kinds(), layoutApi.list()]);
        let template: MailTemplateView | null = null;
        let from: MailTemplateView | null = null;
        let kindCode: string | undefined;
        if (!isNew.value) {
            template = await api.template(id.value);
            kindCode = template.kind;
        } else {
            const fromId = queryText(route.query.from);
            if (fromId) {
                from = await api.template(fromId);
                kindCode = from.kind;
            } else {
                // `?kind=` 그 종류, 없으면 `?audience=` 받는 사람의 첫 종류, 둘 다 없으면 첫 종류(회원 먼저)
                const wanted = queryText(route.query.kind);
                const audience = queryText(route.query.audience);
                kindCode = kindList.find((item) => item.kind === wanted)?.kind ?? kindList.find((item) => item.audience === audience)?.kind ?? kindList[0]?.kind;
            }
        }
        // 메일 종류가 하나도 없으면 만들 수 없다(없는 종류와 같게 보인다)
        if (!kindCode) throw new ApiError({ status: 404, code: 'MAIL_KIND_NOT_FOUND', message: t('mailTemplate.kindsEmptyBody') });
        const view = await api.kind(kindCode);
        if (mine !== loadSeq) return;
        kinds.value = kindList;
        layouts.value = layoutList;
        saved.value = template;
        source.value = from;
        detail.value = view;
        form.locale = 'ko';
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

/* ── 새로 만들기 — 종류·언어 바꾸기 ── */

async function pickKind(next: string): Promise<void> {
    if (!next || next === kind.value?.kind || switching.value) return;
    // 고친 내용은 다른 종류에서 쓸 수 없는 변수가 섞이므로 그 종류의 기본 내용으로 바꾼다 — 고친 것이 있으면 묻는다
    if (contentChanged.value && !(await confirm({ title: t('mailTemplate.switchKindTitle'), message: t('mailTemplate.switchKindMessage'), confirmLabel: t('mailTemplate.switchConfirm'), tone: 'danger' }))) return;
    switching.value = true;
    const prevAudience = kind.value?.audience;
    try {
        detail.value = await api.kind(next);
        reseed(defaultFor(form.locale));
        // 받는 사람이 바뀌면 — 고른 레이아웃이 앞 받는 사람의 기본이었거나 새 받는 사람에게 맞지 않으면 새 받는 사람의 기본 레이아웃으로
        const audience = detail.value.summary.audience;
        if (prevAudience && prevAudience !== audience && (form.layoutId === defaultLayoutIdOf(prevAudience) || !layoutUsable(form.layoutId, audience))) {
            const untouched = baseline.value.layoutId === form.layoutId;
            form.layoutId = defaultLayoutIdOf(audience);
            if (untouched) baseline.value = { ...baseline.value, layoutId: form.layoutId };
        }
        const { audience: _audience, ...rest } = route.query;
        void router.replace({ query: { ...rest, kind: next } });
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        switching.value = false;
    }
}

async function pickLocale(next: MailLocale): Promise<void> {
    if (!next || next === form.locale) return;
    // 새로 만들기(복사 아님)에서 내용을 아직 안 고쳤으면 그 언어의 기본 내용으로 바꾼다
    form.locale = next;
    if (kindPickable.value && !contentChanged.value) reseed(defaultFor(next));
}

/**
 * 새로 만들기에서 내용(제목·본문·버튼)만 그 종류·언어의 기본 내용으로 다시 채운다 — 칸을 새로 세우고(에디터 포함),
 * 이름·언어·레이아웃은 고친 값과 「고쳤는가」 기준을 그대로 둔다(종류와 상관없는 칸이다).
 */
function reseed(seed: MailTemplateView | undefined): void {
    const prev = baseline.value;
    if (seed) setContent(seed);
    setBaseline();
    baseline.value = { ...baseline.value, name: prev.name, locale: prev.locale, layoutId: prev.layoutId };
}

/* ── 변수 넣기 · 버튼 ── */

const subjectInput = ref<{ $el: HTMLInputElement } | null>(null);
const editor = ref<{ insertText: (text: string) => void; insertParagraph: (text: string) => void } | null>(null);

/** 제목 칸의 커서 자리(고른 글자가 있으면 그 자리)에 넣는다 */
function insertSubject(name: string): void {
    const token = UtilMailTemplate.token(name);
    const el = subjectInput.value?.$el;
    const start = el?.selectionStart ?? form.subject.length;
    const end = el?.selectionEnd ?? start;
    form.subject = form.subject.slice(0, start) + token + form.subject.slice(end);
    const caret = start + token.length;
    void nextTick(() => {
        el?.focus();
        el?.setSelectionRange(caret, caret);
    });
}

function insertBody(name: string): void {
    editor.value?.insertText(UtilMailTemplate.token(name));
}

function addButton(): void {
    editor.value?.insertParagraph(buttonToken);
}

function removeButton(): void {
    editorHtml.value = UtilMailTemplate.removeButtonTokens(editorHtml.value);
    button.value = null;
}

/* ── 미리보기 — 고칠 때마다 잠깐 기다렸다가 받는다(고른 레이아웃으로) ── */

const preview = reactive({ subject: '', html: '', loading: false, error: '' });
let previewTimer: ReturnType<typeof setTimeout> | null = null;
let previewSeq = 0;

function schedulePreview(): void {
    if (previewTimer) clearTimeout(previewTimer);
    if (!ready.value || !kind.value) return;
    previewTimer = setTimeout(() => void runPreview(), 400);
}

async function runPreview(): Promise<void> {
    const current = kind.value;
    if (!current) return;
    const mine = ++previewSeq;
    preview.loading = true;
    try {
        const result = await api.preview({ kind: current.kind, locale: form.locale, subject: form.subject, bodyHtml: composed.value, layoutId: form.layoutId || undefined });
        if (mine !== previewSeq) return;
        preview.subject = result.subject;
        preview.html = result.html;
        preview.error = '';
    } catch (e) {
        if (mine !== previewSeq) return;
        preview.error = toApiError(e).message;
    } finally {
        if (mine === previewSeq) preview.loading = false;
    }
}

watch(() => [ready.value, kind.value?.kind, form.locale, form.layoutId, form.subject, composed.value] as const, schedulePreview);
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
});

/* ── 저장 · 되돌리기 · 기본으로 ── */

async function resetToDefault(): Promise<void> {
    const seed = defaultFor(form.locale);
    if (!seed) return;
    const ok = await confirm({ title: t('mailTemplate.resetDefaultTitle'), message: t('mailTemplate.resetDefaultMessage'), confirmLabel: t('mailTemplate.resetDefaultConfirm'), tone: 'danger' });
    if (!ok) return;
    setContent(seed);
}

function revert(): void {
    if (saved.value) fill();
}

/** 이 화면을 떠나는 길을 열어 둔다(만든 뒤·지운 뒤 — 떠나기 확인을 건너뛴다) */
let leaving = false;

async function save(): Promise<void> {
    const current = kind.value;
    if (readonly.value || saving.value || !current || switching.value) return;
    if (!isNew.value && !dirty.value) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0 || !form.layoutId) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    const body = { kind: current.kind, name: form.name.trim(), locale: form.locale, layoutId: form.layoutId, subject: form.subject, bodyHtml: composed.value };
    try {
        if (isNew.value || !saved.value) {
            const created = await api.create(body);
            toast.success(t('mailTemplate.created'));
            leaving = true;
            await router.replace(`/settings/mail-templates/${encodeURIComponent(created.id)}`);
            return;
        }
        saved.value = await api.update(saved.value.id, body);
        toast.success(t('mailTemplate.saved'));
        fill();
        // 언어가 바뀌었을 수 있다 — 쓰는 곳(언어 잠금)은 그대로지만 종류 상세의 목록을 맞춘다
        if (detail.value) detail.value = { ...detail.value, templates: detail.value.templates.map((item) => (item.id === saved.value?.id ? (saved.value as MailTemplateView) : item)) };
        if (historyLoaded) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'locale', 'layoutId', 'subject', 'bodyHtml']) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        serverErrors.value = found;
        // 칸에 붙지 않는 오류만 저장 줄 안 한 줄 — 서버 문구 그대로
        if (Object.keys(found).length === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

function copySaved(): void {
    if (!saved.value || copyBlockedReason.value) return;
    void router.push({ path: '/settings/mail-templates/new', query: { from: saved.value.id } });
}

const deleting = ref(false);

async function confirmDelete(): Promise<void> {
    const template = saved.value;
    if (!template || deleting.value) return;
    const ok = await confirm({ title: t('mailTemplate.deleteTitle', { name: template.name }), message: t('mailTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(template.id);
        toast.success(t('mailTemplate.deleted'));
        leaving = true;
        await router.push('/settings/mail-templates');
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
    // 처음 열 때 받는다
    if (!historyLoaded) {
        historyLoaded = true;
        void history.reloadFirst();
    }
}

/* ── 나에게 시험 발송 ── */

const testOpen = ref(false);
const testTo = ref('');
const testing = ref(false);
const testToError = ref('');
const testError = ref('');
const testResult = ref<MailTestDialogResult | null>(null);

watch(testTo, () => (testToError.value = ''));

function openTest(): void {
    if (testBlockedReason.value) return;
    testTo.value = user.value?.email ?? '';
    testToError.value = '';
    testError.value = '';
    testResult.value = null;
    testOpen.value = true;
}

async function runTest(): Promise<void> {
    const current = kind.value;
    if (testing.value || !current) return;
    const to = testTo.value.trim();
    const problem = UtilValidation.email(to);
    if (problem) {
        testToError.value = t(problem === 'REQUIRED' ? 'mail.testToRequired' : 'mail.testToFormat');
        return;
    }
    testing.value = true;
    testError.value = '';
    testResult.value = null;
    try {
        // 고치는 중인 값 그대로(제목·본문·레이아웃)
        const result = await api.test({ kind: current.kind, locale: form.locale, layoutId: form.layoutId || undefined, subject: form.subject, bodyHtml: composed.value, to });
        testResult.value = { succeeded: result.succeeded, text: result.succeeded ? '' : UtilMailSettingsMessages.testFailure(t, result), to };
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('to');
        if (field) testToError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') testError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else testError.value = error.fields?.subject || error.fields?.bodyHtml || error.message;
    } finally {
        testing.value = false;
    }
}

/* ── 떠나기 · 주소 바뀜 ── */

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다(새로 만들기는 처음 채운 값에서 고쳤을 때만)
onBeforeRouteLeave(async () => {
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('mailTemplate.leaveTitle'), message: t('mailTemplate.leaveMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' });
});

// 같은 화면에서 다른 템플릿으로(복사해서 만들기 · 만든 뒤 주소 바꾸기) — 고친 것이 있으면 묻고 다시 읽는다
onBeforeRouteUpdate(async (to, from) => {
    if (to.params.id === from.params.id && to.query.from === from.query.from) return true;
    if (leaving || !dirty.value) return true;
    return confirm({ title: t('mailTemplate.leaveTitle'), message: t('mailTemplate.leaveMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' });
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
</style>
