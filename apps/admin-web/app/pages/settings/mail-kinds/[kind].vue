<template>
    <!--
        메일 종류 상세(`/settings/mail-kinds/[kind]`) — 한 메일 종류의 ① 언어별 쓰는 템플릿 ② 특수한 상황(조건 규칙). 계약 `docs/features/message-template-contract.md` 「개정」 2 · 「개정 2」.
        머리에 받는 사람(회원 · 운영자 — 종류가 정한다)과 묶음을 보인다. 「목록으로」는 그 받는 사람의 목록(`?audience=`)으로 돌아간다.
        ①②는 `PUT kinds/{kind}` 한 번으로 저장하므로 하단 떠 있는 저장 줄(`StickySaveBar`) 하나다(구획 안에 저장 단추를 두지 않는다 — forms.md 2-4).
        규칙: 위에서부터 차례로 보고 맞는 첫 규칙의 템플릿(없으면 ①). 조건은 종류마다 코드가 정한 선택지만. 순서 위/아래 · 추가(10개까지) · 삭제. 같은 조건이 위에 있으면 「쓰이지 않음」 경고만.
        머리 단추: 「이 종류의 템플릿 보기」 → 템플릿 목록을 이 종류로 거른 화면(`/settings/mail-templates?kind=`), 「새 템플릿」 → `/settings/mail-templates/new?kind=`.
        권한: 화면 `template.read`(메뉴가 거른다) · 바꾸기 `template.manage` — 없으면 입력 잠금, 저장 줄·추가·순서·삭제 단추 없음. 저장하지 않은 변경이 있는 채 떠나면 묻는다.
        ⚠ MOCK: API 는 메모리 목업이다(`useMailTemplateApi`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <!-- 읽는 중 -->
        <template v-if="loading && !detail">
            <PageTitle :title="t('mailTemplate.kindTitle')" />
            <div class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 2" :key="n" height="9rem" />
            </div>
        </template>

        <!-- 권한 없음 · 없는 종류 · 읽기 실패 -->
        <template v-else-if="!detail">
            <PageTitle :title="t('mailTemplate.kindTitle')">
                <template #actions>
                    <Button :label="t('mailTemplate.backToKinds')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push('/settings/mail-kinds')" />
                </template>
            </PageTitle>
            <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('mailTemplate.denied') }}</Message>
            <AppGadget v-else :title="t('mailTemplate.kindTitle')">
                <ErrorState v-if="notFound" code="404" :title="t('mailTemplate.kindNotFound')" :description="loadError">
                    <Button :label="t('mailTemplate.backToKinds')" icon="pi pi-arrow-left" severity="secondary" variant="outlined" @click="router.push('/settings/mail-kinds')" />
                </ErrorState>
                <ErrorState v-else :title="t('mailTemplate.detailLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
            </AppGadget>
        </template>

        <template v-else>
            <PageTitle :title="kind.name">
                <template #actions>
                    <Button :label="t('mailTemplate.showKindTemplates')" icon="pi pi-list" size="small" severity="secondary" variant="outlined" @click="router.push({ path: '/settings/mail-templates', query: { kind: kind.kind } })" />
                    <Button v-if="canManage" :label="t('mailTemplate.newTemplate')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="router.push({ path: '/settings/mail-templates/new', query: { kind: kind.kind } })" />
                    <Button :label="t('mailTemplate.backToKinds')" icon="pi pi-arrow-left" size="small" severity="secondary" variant="outlined" @click="router.push({ path: '/settings/mail-kinds', query: { audience: kind.audience } })" />
                </template>
            </PageTitle>
            <div class="-mt-2 flex flex-col gap-1">
                <div class="flex flex-wrap items-center gap-2">
                    <Tag :value="t('mailTemplate.audienceTag', { audience: UtilMailTemplateMessages.audienceLabel(t, kind.audience) })" :icon="UtilMailTemplateMessages.audienceIcon(kind.audience)" severity="secondary" class="mail-template-tag text-[11px]!" />
                    <span class="text-xs text-muted-color">{{ UtilMailTemplateMessages.groupLabel(t, kind.group, kind.area) }}</span>
                    <Tag v-if="kind.security" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                    <code class="text-[11px] text-muted-color">{{ kind.kind }}</code>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ kind.description }}</p>
                <p v-if="kind.security" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('mailTemplate.securityHint', { names: requiredNames }) }}</p>
            </div>

            <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('mailTemplate.readOnly') }}</Message>

            <form class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- ① 쓰는 템플릿 -->
                <AppGadget :title="t('mailTemplate.selectedTitle')" anchor="mail-selected" :changes="selectedChanges" :invalid="selectedInvalid">
                    <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.selectedHint') }}</p>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField v-for="locale in LOCALES" :key="locale" :label="t('mailTemplate.selectedFor', { locale: UtilMailTemplateMessages.localeLabel(t, locale) })" :error="fieldErrors[`selected.${locale}`]">
                            <template #aside>
                                <NuxtLink v-if="form.selected[locale]" :to="`/settings/mail-templates/${encodeURIComponent(form.selected[locale])}`" class="text-xs text-primary hover:underline">{{ t('mailTemplate.openTemplate') }}</NuxtLink>
                            </template>
                            <template #default="{ id, describedBy, invalid }">
                                <Select v-model="form.selected[locale]" :input-id="id" :options="templateOptionsOf(locale)" option-label="label" option-value="value" :disabled="!canManage || saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                </AppGadget>

                <!-- ② 특수한 상황 -->
                <AppGadget :title="t('mailTemplate.rulesTitle')" anchor="mail-rules" :changes="rulesChanges" :invalid="rulesInvalid">
                    <template v-if="canManage && kind.conditions.length" #actions>
                        <Button type="button" :label="t('mailTemplate.ruleAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="saving || form.rules.length >= RULE_MAX" @click="addRule" />
                    </template>
                    <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.rulesHint') }}</p>

                    <EmptyState v-if="!kind.conditions.length" icon="pi pi-filter-slash" :title="t('mailTemplate.noConditionsTitle')" :description="t('mailTemplate.noConditionsBody')" />
                    <p v-else-if="!form.rules.length" class="m-0 rounded-lg border border-dashed border-(--line) p-4 text-center text-xs text-muted-color">{{ t('mailTemplate.rulesEmpty') }}</p>

                    <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
                        <li v-for="(rule, at) in form.rules" :key="rule.uid" class="flex flex-col gap-1.5 rounded-lg border border-(--line) p-2.5">
                            <div class="flex flex-wrap items-center gap-2">
                                <span class="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-100 text-[11px] leading-none tabular-nums dark:bg-surface-800" :aria-label="t('mailTemplate.ruleOrder', { n: at + 1 })">{{ at + 1 }}</span>
                                <span class="text-xs text-muted-color">{{ t('mailTemplate.ruleWhen') }}</span>
                                <Select
                                    :model-value="conditionValue(rule)"
                                    :options="conditionOptions"
                                    option-label="label"
                                    option-value="value"
                                    option-group-label="label"
                                    option-group-children="items"
                                    :disabled="!canManage || saving"
                                    :invalid="Boolean(ruleError(at, 'condition'))"
                                    :aria-label="t('mailTemplate.ruleConditionLabel', { n: at + 1 })"
                                    class="min-w-44 flex-1"
                                    @update:model-value="(value: string) => setCondition(rule, value)"
                                />
                                <span class="text-xs text-muted-color">{{ t('mailTemplate.ruleUse') }}</span>
                                <Select
                                    v-model="rule.templateId"
                                    :options="allTemplateOptions"
                                    option-label="label"
                                    option-value="value"
                                    :placeholder="t('mailTemplate.ruleTemplatePlaceholder')"
                                    :disabled="!canManage || saving"
                                    :invalid="Boolean(ruleError(at, 'templateId'))"
                                    :aria-label="t('mailTemplate.ruleTemplateLabel', { n: at + 1 })"
                                    class="min-w-44 flex-1"
                                />
                                <div v-if="canManage" class="flex items-center">
                                    <Button v-tooltip.top="t('mailTemplate.ruleUpTip')" type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :disabled="at === 0 || saving" :aria-label="t('mailTemplate.ruleUp', { n: at + 1 })" @click="moveRule(at, -1)" />
                                    <Button v-tooltip.top="t('mailTemplate.ruleDownTip')" type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :disabled="at === form.rules.length - 1 || saving" :aria-label="t('mailTemplate.ruleDown', { n: at + 1 })" @click="moveRule(at, 1)" />
                                    <Button v-tooltip.top="t('common.delete')" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="saving" :aria-label="t('mailTemplate.ruleRemove', { n: at + 1 })" @click="removeRule(at)" />
                                </div>
                            </div>
                            <p v-if="ruleError(at, 'condition') || ruleError(at, 'templateId')" role="alert" class="m-0 pl-8 text-xs text-red-600 dark:text-red-400">{{ ruleError(at, 'condition') || ruleError(at, 'templateId') }}</p>
                            <p v-else-if="shadowedBy(at) !== null" class="m-0 pl-8 text-xs text-amber-700 dark:text-amber-400">{{ t('mailTemplate.ruleShadowed', { n: (shadowedBy(at) ?? 0) + 1 }) }}</p>
                        </li>
                    </ol>
                    <p v-if="fieldErrors.rules" role="alert" class="m-0 mt-2 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.rules }}</p>
                </AppGadget>

                <StickySaveBar v-if="canManage" submit revertable :dirty="dirty" :saving="saving" :disabled="selectedInvalid + rulesInvalid > 0" :count="selectedChanges + rulesChanges" :error-count="selectedInvalid + rulesInvalid" :error="formError" @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import { mockTemplateCan } from '~/composables/template/api/mock/mail-template-mock';
import type { MailKindDetailView, MailLocale } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';

const { t } = useI18n();

const api = useMailTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const route = useRoute();
const router = useRouter();
const { confirm } = useConfirmDialog();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

/** 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts — 목업 후보) */
const canManage = computed(() => can('template.manage'));

const LOCALES: readonly MailLocale[] = ['ko', 'en'];
/** 규칙 수 상한(목업 가정) */
const RULE_MAX = 10;

const kindCode = computed(() => String(route.params.kind ?? ''));

interface RuleRow {
    /** 화면 안 줄 열쇠(새 규칙도 갖는다) */
    uid: string;
    id?: string;
    conditionKey: string;
    conditionValue: string;
    templateId: string;
}

const detail = ref<MailKindDetailView | null>(null);
const loading = ref(false);
const loadError = ref('');
const notFound = ref(false);
/** 읽기 권한이 없다(403) — 오류가 아니라 안내다 */
const denied = ref(false);
const saving = ref(false);
const formError = ref('');
const fieldErrors = ref<Record<string, string>>({});
const form = reactive<{ selected: Record<MailLocale, string>; rules: RuleRow[] }>({ selected: { ko: '', en: '' }, rules: [] });
let uidSeq = 0;

/** 종류 정의 — 템플릿에서는 `detail` 이 있을 때만 읽는다 */
const kind = computed(() => detail.value?.summary as MailKindDetailView['summary']);
const requiredNames = computed(() => (kind.value?.variables ?? []).filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));

useSeoMeta({ title: () => kind.value?.name ?? t('mailTemplate.kindTitle') });

function fill(view: MailKindDetailView): void {
    detail.value = view;
    form.selected = { ...view.selected };
    form.rules = view.rules.map((rule) => ({ uid: `r${(uidSeq += 1)}`, id: rule.id, conditionKey: rule.condition.key, conditionValue: rule.condition.value, templateId: rule.templateId }));
    fieldErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = '';
    notFound.value = false;
    try {
        fill(await api.kind(kindCode.value));
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

/* ── ① 쓰는 템플릿 ── */

function templateOptionsOf(locale: MailLocale) {
    return (detail.value?.templates ?? []).filter((item) => item.locale === locale).map((item) => ({ value: item.id, label: item.name }));
}
const allTemplateOptions = computed(() => (detail.value?.templates ?? []).map((item) => ({ value: item.id, label: `${item.name} · ${UtilMailTemplateMessages.localeLabel(t, item.locale)}` })));

/* ── ② 특수한 상황 ── */

/** 조건 고르기 — 조건 이름별 묶음, 값은 `이름:값` */
const conditionOptions = computed(() => (kind.value?.conditions ?? []).map((condition) => ({ label: condition.label, items: condition.options.map((option) => ({ value: `${condition.key}:${option.value}`, label: `${condition.label}: ${option.label}` })) })));
const conditionValue = (rule: RuleRow) => `${rule.conditionKey}:${rule.conditionValue}`;
function setCondition(rule: RuleRow, value: string): void {
    const at = value.indexOf(':');
    rule.conditionKey = value.slice(0, at);
    rule.conditionValue = value.slice(at + 1);
}

function addRule(): void {
    const condition = kind.value?.conditions[0];
    const option = condition?.options[0];
    if (!condition || !option || form.rules.length >= RULE_MAX) return;
    form.rules.push({ uid: `r${(uidSeq += 1)}`, conditionKey: condition.key, conditionValue: option.value, templateId: '' });
}

function moveRule(at: number, delta: number): void {
    const to = at + delta;
    if (to < 0 || to >= form.rules.length) return;
    const next = form.rules.slice();
    const [row] = next.splice(at, 1);
    next.splice(to, 0, row as RuleRow);
    form.rules = next;
    fieldErrors.value = {};
}

function removeRule(at: number): void {
    form.rules = form.rules.filter((_, index) => index !== at);
    fieldErrors.value = {};
}

/** 같은 조건이 위에 있으면 이 규칙은 쓰이지 않는다 — 위 규칙의 순번(없으면 null) */
function shadowedBy(at: number): number | null {
    const rule = form.rules[at];
    if (!rule) return null;
    const found = form.rules.findIndex((other, index) => index < at && other.conditionKey === rule.conditionKey && other.conditionValue === rule.conditionValue);
    return found < 0 ? null : found;
}

function ruleError(at: number, field: 'condition' | 'templateId'): string {
    const server = fieldErrors.value[`rules[${at}].${field}`];
    if (server) return server;
    const rule = form.rules[at];
    if (field === 'templateId' && rule && !rule.templateId) return t('mailTemplate.ruleTemplateRequired');
    return '';
}

/* ── 바뀐 칸 · 저장 ── */

const selectedChanges = computed(() => (detail.value ? LOCALES.filter((locale) => form.selected[locale] !== detail.value?.selected[locale]).length : 0));
const selectedInvalid = computed(() => LOCALES.filter((locale) => !form.selected[locale] || Boolean(fieldErrors.value[`selected.${locale}`])).length);
const rulesChanges = computed(() => {
    const saved = detail.value?.rules ?? [];
    let n = Math.abs(saved.length - form.rules.length);
    form.rules.forEach((rule, at) => {
        const before = saved[at];
        if (before && (before.id !== rule.id || before.condition.key !== rule.conditionKey || before.condition.value !== rule.conditionValue || before.templateId !== rule.templateId)) n += 1;
    });
    return n;
});
const rulesInvalid = computed(() => form.rules.filter((_, at) => Boolean(ruleError(at, 'condition') || ruleError(at, 'templateId'))).length + (fieldErrors.value.rules ? 1 : 0));
const dirty = computed(() => selectedChanges.value + rulesChanges.value > 0);

watch(
    () => form.selected,
    () => {
        for (const locale of LOCALES) if (fieldErrors.value[`selected.${locale}`]) fieldErrors.value = { ...fieldErrors.value, [`selected.${locale}`]: '' };
    },
    { deep: true },
);

function revert(): void {
    if (detail.value) fill(detail.value);
}

async function save(): Promise<void> {
    if (saving.value || !canManage.value || !dirty.value || selectedInvalid.value + rulesInvalid.value > 0) return;
    saving.value = true;
    formError.value = '';
    fieldErrors.value = {};
    try {
        fill(
            await api.saveKind(kindCode.value, {
                selected: { ...form.selected },
                rules: form.rules.map((rule) => ({ ...(rule.id ? { id: rule.id } : {}), condition: { key: rule.conditionKey, value: rule.conditionValue }, templateId: rule.templateId })),
            }),
        );
        toast.success(t('mailTemplate.kindSaved'));
    } catch (e) {
        const error = toApiError(e);
        const fields = error.fields ?? {};
        fieldErrors.value = { ...fields };
        // 칸에 붙지 않는 오류만 저장 줄 안 한 줄 — 서버 문구 그대로
        if (Object.keys(fields).length === 0) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('mailTemplate.leaveTitle'), message: t('mailTemplate.leaveMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' });
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
