<template>
    <!--
        메일 템플릿 편집 서랍 — 메일 템플릿 화면(`/settings/mail-templates`)의 「템플릿 목록」에서 편집·보기·복사를 누르면 연다(운영자 상세처럼 PrimeVue `Drawer`, 열리는 쪽은 화면 설정의 서랍 위치).
        탭 없이 위에서 아래로: 안내 → 이름·언어 → 제목(한 줄 + 변수 넣기) → 본문(간단 에디터 + 변수 넣기 + 「버튼」 블록 하나) → 미리보기(견본 값 + 공통 레이아웃, 격리된 iframe).
        머리 오른쪽에 「변경 이력」(저장된 사용자 템플릿만 — 누르면 부모가 오른쪽 이력 서랍을 연다), 하단 고정 푸터에 「나에게 시험 발송」 · 「기본으로 되돌리기」 · [되돌리기] · [저장].
        - 기본 템플릿과 권한이 `template.read` 뿐이면 읽기 전용이다(입력 `readonly`, 에디터 잠금, 푸터의 저장·시험·되돌리기 없음). 기본 템플릿은 「복사해서 만들기」를 안내한다.
        - 「버튼」 블록은 에디터 안에서 `{{button}}` 한 줄로 보이고, 글자·링크 변수는 에디터 아래 칸에서 고른다(간단 에디터가 단추를 그리지 못해서다 — 저장 모양은 `UtilMailTemplate`).
        - 검사(`UtilMailTemplate.validate` — 서버와 같은 규칙): 모르는 변수 · 보안 메일 필수 변수 없음 · 길이 · 제목 한 줄 · 버튼 하나(링크 변수만). 서버 `fields.*` 가 오면 그 칸 아래에 그대로.
        - 서버 호출은 하지 않는다: 저장·시험·복사·이력은 이벤트로 알리고, 미리보기는 부모가 넘긴 함수(`previewer`)를 부른다(에디터의 `upload` 와 같은 방식).
        닫기 요청(Esc·X)은 스스로 닫지 않고 `update:visible(false)` 로 알린다 — 저장하지 않은 변경이 있으면 부모가 확인을 묻는다(`update:dirty`).
    -->
    <Drawer
        :visible="visible"
        :dismissable="false"
        :position="prefs.drawerPosition"
        :aria-labelledby="titleId"
        :style="{ width: '52rem', maxWidth: '100vw' }"
        :pt="{ content: { class: 'pt-0!' } }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-1">
                <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ title }}</h2>
                <Tag v-if="template?.builtin && mode === 'edit'" :value="t('mailTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[11px]!" />
                <Tag v-if="kind?.security" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                <Button v-if="historyAvailable" type="button" :label="t('settingHistory.open')" icon="pi pi-history" size="small" severity="secondary" variant="outlined" class="ml-auto mr-2" @click="emit('history')" />
            </div>
        </template>

        <div v-if="template && kind" class="flex flex-col gap-5">
            <p class="m-0 text-xs text-muted-color">{{ kind.name }} · {{ kind.description }}</p>

            <Message v-if="template.builtin && mode === 'edit'" severity="secondary" :closable="false" size="small">
                <span>{{ t('mailTemplate.builtinReadOnly') }}</span>
                <Button v-if="canManage" type="button" :label="t('mailTemplate.copyCreate')" icon="pi pi-copy" size="small" variant="link" class="ml-1 p-0!" @click="emit('copy', template)" />
            </Message>
            <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('mailTemplate.readOnly') }}</Message>
            <Message v-if="kind.security && !readonly" severity="warn" :closable="false" size="small">{{ t('mailTemplate.securityHint', { names: requiredNames }) }}</Message>

            <form :key="formKey" class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <div class="grid gap-4 sm:grid-cols-[minmax(0,1fr)_auto]">
                    <LabeledField :label="t('mailTemplate.nameLabel')" :hint="t('mailTemplate.maxHint', { max: UtilMailTemplate.NAME_MAX })" :error="fieldError('name')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.name" name="name" autocomplete="off" :readonly="readonly || saving" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" fluid />
                        </template>
                    </LabeledField>
                    <div class="flex flex-col gap-1.5">
                        <span :id="localeLabelId" class="text-sm font-medium">{{ t('mailTemplate.localeLabel') }}</span>
                        <SelectButton
                            v-model="form.locale"
                            :options="localeOptions"
                            option-label="label"
                            option-value="value"
                            :allow-empty="false"
                            size="small"
                            :disabled="readonly || saving || localeLocked"
                            :aria-labelledby="localeLabelId"
                            class="self-start"
                        />
                        <p v-if="fieldError('locale')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('locale') }}</p>
                        <p v-else-if="localeLocked && !readonly" class="m-0 max-w-56 text-xs text-muted-color">{{ t('mailTemplate.localeLocked') }}</p>
                    </div>
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
                                <Select
                                    v-model="button.link"
                                    :input-id="id"
                                    :options="linkOptions"
                                    option-label="label"
                                    option-value="value"
                                    :disabled="readonly || saving"
                                    :invalid="invalid || Boolean(fieldError('button'))"
                                    :aria-describedby="describedBy"
                                    fluid
                                />
                            </template>
                        </LabeledField>
                    </div>
                    <p v-if="fieldError('button')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('button') }}</p>
                    <p v-else-if="button" class="m-0 text-xs text-muted-color">{{ t('mailTemplate.buttonPlaceHint', { token: buttonToken }) }}</p>
                </section>

                <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>
            </form>

            <!-- 미리보기 — 견본 값 + 공통 레이아웃 -->
            <section class="flex flex-col gap-2" :aria-labelledby="previewTitleId">
                <h3 :id="previewTitleId" class="m-0 text-sm font-medium">{{ t('mailTemplate.previewTitle') }}</h3>
                <MailTemplatePreviewFrame :subject="preview.subject" :html="preview.html" :loading="preview.loading" :error="preview.error" />
            </section>
        </div>

        <template v-if="template && kind && !readonly" #footer>
            <div class="flex w-full flex-wrap items-center gap-2 border-t border-(--line) pt-3">
                <span v-tooltip.top="testBlockedReason || undefined" class="inline-flex">
                    <Button type="button" :label="t('mailTemplate.testButton')" icon="pi pi-send" size="small" severity="secondary" variant="outlined" :disabled="Boolean(testBlockedReason) || saving" :aria-description="testBlockedReason || undefined" @click="test" />
                </span>
                <Button v-if="defaultFor(form.locale)" type="button" :label="t('mailTemplate.resetDefault')" icon="pi pi-replay" size="small" severity="secondary" variant="text" :disabled="saving" @click="resetToDefault" />
                <div class="ml-auto flex items-center gap-2">
                    <small v-if="invalidCount > 0" class="text-xs text-red-600 dark:text-red-400">{{ t('common.saveBarErrors', { count: invalidCount }) }}</small>
                    <Button v-if="mode === 'edit'" type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" :disabled="saving || !dirty" @click="revert" />
                    <Button type="button" :label="mode === 'create' ? t('mailTemplate.createButton') : t('common.save')" icon="pi pi-check" size="small" :loading="saving" :disabled="!dirty" @click="save" />
                </div>
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { MailKindView, MailLocale, MailPreviewRequest, MailPreviewView, MailTemplateSaveRequest, MailTemplateView } from '~/composables/template/api/useMailTemplateApi.types';
import type { MailButton, MailTemplateField } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';

/** 시험 발송에 넘기는 지금 값(고친 값 그대로) */
export interface MailTemplateTestDraft {
    locale: MailLocale;
    subject: string;
    bodyHtml: string;
}

interface Props {
    visible: boolean;
    /** 템플릿의 메일 종류(변수·보안 여부) */
    kind: MailKindView | null;
    /** 고치는 템플릿 — `create` 면 복사 원본 */
    template: MailTemplateView | null;
    mode?: 'edit' | 'create';
    /** 언어별 기본 템플릿 — 「기본으로 되돌리기」의 내용 */
    defaults?: Partial<Record<MailLocale, MailTemplateView>>;
    /** 고칠 수 있는가(`template.manage`) */
    canManage?: boolean;
    /** 언어를 바꿀 수 없다(그 언어의 기본으로 쓰는 중) */
    localeLocked?: boolean;
    /** 「변경 이력」 단추를 보인다(저장된 사용자 템플릿) */
    historyAvailable?: boolean;
    saving?: boolean;
    /** 서버가 거절한 칸 문구(`fields.name|locale|subject|bodyHtml`) */
    fieldErrors?: Record<string, string>;
    /** 칸에 붙지 않는 저장 오류(서버 문구 그대로) */
    formError?: string;
    /** 미리보기를 받는 함수 — 부모가 API 컴포저블로 만든다 */
    previewer?: ((body: MailPreviewRequest) => Promise<MailPreviewView>) | null;
}

const props = withDefaults(defineProps<Props>(), {
    mode: 'edit',
    defaults: () => ({}),
    canManage: false,
    localeLocked: false,
    historyAvailable: false,
    saving: false,
    fieldErrors: () => ({}),
    formError: '',
    previewer: null,
});

const emit = defineEmits<{
    'update:visible': [visible: boolean];
    'update:dirty': [dirty: boolean];
    save: [body: MailTemplateSaveRequest];
    test: [draft: MailTemplateTestDraft];
    /** 이 템플릿을 복사해 새로 만든다(기본 템플릿 보기에서) */
    copy: [template: MailTemplateView];
    /** 변경 이력 서랍을 연다 */
    history: [];
}>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
const { confirm } = useConfirmDialog();
const titleId = useId();
const localeLabelId = useId();
const bodyLabelId = useId();
const bodyMessageId = useId();
const buttonTitleId = useId();
const previewTitleId = useId();

const readonly = computed(() => !props.canManage || (props.mode === 'edit' && props.template?.builtin === true));
const title = computed(() => {
    if (props.mode === 'create') return t('mailTemplate.newTitle');
    return props.template?.name ?? t('mailTemplate.editTitle');
});
const localeOptions = computed(() => (['ko', 'en'] as const).map((value) => ({ value, label: UtilMailTemplateMessages.localeLabel(t, value) })));
const linkVariables = computed(() => (props.kind?.variables ?? []).filter((v) => v.link));
const linkOptions = computed(() => linkVariables.value.map((v) => ({ value: v.name, label: `${UtilMailTemplate.token(v.name)} · ${v.description}` })));
const requiredNames = computed(() => (props.kind?.variables ?? []).filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));
const buttonToken = UtilMailTemplate.token(UtilMailTemplate.BUTTON);

/* ── 편집 값 ── */

const form = reactive<{ name: string; locale: MailLocale; subject: string }>({ name: '', locale: 'ko', subject: '' });
const editorHtml = ref('');
const button = ref<MailButton | null>(null);
/** 처음 값 — 「고쳤는가」의 기준. 본문은 에디터가 정규화한 값으로 바꿔 둔다(`onEditorReady`) */
const baseline = ref<{ name: string; locale: MailLocale; subject: string; editorHtml: string; button: MailButton | null }>({ name: '', locale: 'ko', subject: '', editorHtml: '', button: null });
/** 다시 채우면 폼(에디터 포함)을 새로 세운다 */
const formKey = ref(0);
/** 저장을 눌러 본 적이 있는가 — 그 전에는 「비어 있음」을 빨갛게 보이지 않는다 */
const tried = ref(false);
/** 서버 문구 — 칸을 고치면 그 칸 것을 걷는다 */
const serverErrors = ref<Record<string, string>>({});

watch(
    () => props.fieldErrors,
    (value) => (serverErrors.value = { ...value }),
    { immediate: true },
);

function copyName(source: MailTemplateView): string {
    if (source.builtin) return t('mailTemplate.copyNameBuiltin', { kind: props.kind?.name ?? '' }).slice(0, UtilMailTemplate.NAME_MAX);
    return t('mailTemplate.copyName', { name: source.name }).slice(0, UtilMailTemplate.NAME_MAX);
}

function fill(): void {
    const source = props.template;
    if (!source) return;
    const split = UtilMailTemplate.splitBody(source.bodyHtml);
    form.name = props.mode === 'create' ? copyName(source) : source.name;
    form.locale = source.locale;
    form.subject = source.subject;
    editorHtml.value = split.editorHtml;
    button.value = split.button ? { ...split.button } : null;
    baseline.value = { name: form.name, locale: form.locale, subject: form.subject, editorHtml: split.editorHtml, button: split.button ? { ...split.button } : null };
    tried.value = false;
    serverErrors.value = {};
    formKey.value += 1;
}

// 서랍이 열리거나 다른 템플릿(또는 복사 원본)이 오면 다시 채운다
watch(
    () => [props.visible, props.template?.id, props.template?.updatedAt, props.mode] as const,
    ([visible]) => {
        if (visible) fill();
    },
    { immediate: true },
);

/** 에디터가 처음 만든 정규화 HTML — 아직 고치지 않았으면 기준도 그 값으로(정규화 차이를 「고침」으로 세지 않는다) */
function onEditorReady(html: string): void {
    if (editorHtml.value === baseline.value.editorHtml) {
        baseline.value = { ...baseline.value, editorHtml: html };
        editorHtml.value = html;
    }
}

const composed = computed(() => UtilMailTemplate.joinBody(editorHtml.value, button.value));

const dirty = computed(() => {
    if (!props.template) return false;
    if (props.mode === 'create') return true;
    const b = baseline.value;
    return (
        form.name !== b.name ||
        form.locale !== b.locale ||
        form.subject !== b.subject ||
        editorHtml.value !== b.editorHtml ||
        (button.value?.label ?? null) !== (b.button?.label ?? null) ||
        (button.value?.link ?? null) !== (b.button?.link ?? null)
    );
});
watch(dirty, (value) => emit('update:dirty', value), { immediate: true });

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

const problems = computed(() => (props.kind ? UtilMailTemplate.validate({ name: form.name, subject: form.subject, bodyHtml: composed.value }, props.kind) : {}));

function fieldError(field: MailTemplateField | 'locale'): string {
    const server = serverErrors.value[field];
    if (server) return server;
    if (field === 'locale' || readonly.value) return '';
    const problem = problems.value[field];
    if (!problem) return '';
    if (problem.code === 'REQUIRED' && !tried.value) return '';
    return UtilMailTemplateMessages.problem(t, field, problem);
}

const FIELDS: readonly (MailTemplateField | 'locale')[] = ['name', 'locale', 'subject', 'bodyHtml', 'button'];
const invalidCount = computed(() => FIELDS.filter((field) => Boolean(fieldError(field))).length);

/** 시험 발송은 제목·본문이 맞을 때만 — 이름은 상관없다 */
const testBlockedReason = computed(() => {
    const p = problems.value;
    if (p.subject || p.bodyHtml || p.button) return t('mailTemplate.testNeedsValid');
    return '';
});

/* ── 변수 넣기 ── */

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

/* ── 미리보기 — 고칠 때마다 잠깐 기다렸다가 받는다 ── */

const preview = reactive({ subject: '', html: '', loading: false, error: '' });
const toApiError = useApiErrorOf();
let previewTimer: ReturnType<typeof setTimeout> | null = null;
let previewSeq = 0;

function schedulePreview(): void {
    if (previewTimer) clearTimeout(previewTimer);
    if (!props.visible || !props.previewer || !props.kind) return;
    previewTimer = setTimeout(() => void runPreview(), 400);
}

async function runPreview(): Promise<void> {
    const previewer = props.previewer;
    const kind = props.kind;
    if (!previewer || !kind) return;
    const mine = ++previewSeq;
    preview.loading = true;
    try {
        const result = await previewer({ kind: kind.kind, locale: form.locale, subject: form.subject, bodyHtml: composed.value });
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

watch(() => [props.visible, form.locale, form.subject, composed.value] as const, schedulePreview, { immediate: true });
onBeforeUnmount(() => {
    if (previewTimer) clearTimeout(previewTimer);
});

/* ── 동작 ── */

function defaultFor(locale: MailLocale): MailTemplateView | undefined {
    return props.defaults[locale];
}

async function resetToDefault(): Promise<void> {
    const source = defaultFor(form.locale);
    if (!source) return;
    const ok = await confirm({ title: t('mailTemplate.resetDefaultTitle'), message: t('mailTemplate.resetDefaultMessage'), confirmLabel: t('mailTemplate.resetDefaultConfirm'), tone: 'danger' });
    if (!ok) return;
    const split = UtilMailTemplate.splitBody(source.bodyHtml);
    form.subject = source.subject;
    editorHtml.value = split.editorHtml;
    button.value = split.button ? { ...split.button } : null;
}

function revert(): void {
    fill();
}

function save(): void {
    if (readonly.value || props.saving || !props.kind || !dirty.value) return;
    tried.value = true;
    if (Object.keys(problems.value).length > 0) return;
    emit('save', { kind: props.kind.kind, name: form.name.trim(), locale: form.locale, subject: form.subject, bodyHtml: composed.value });
}

function test(): void {
    if (testBlockedReason.value) return;
    emit('test', { locale: form.locale, subject: form.subject, bodyHtml: composed.value });
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
