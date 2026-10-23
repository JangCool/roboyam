<template>
    <!--
        대상(이벤트 · 필수 안내 메일)의 채널 하나에서 「언어별 쓸 문구」와 「특수 상황 규칙」을 고치는 칸 — 이벤트 상세(`/settings/notifications/{member|operator}/[code]`)와
        필수 안내 메일 상세(`/settings/notification-messages/required-mails/[kind]`)가 같이 쓴다. 계약 `docs/features/message-template-contract.md` 「개정 3」 이벤트 화면 · 「개정 4」 용어(문구).
        저장은 부르는 화면이 한다(화면 하나로 한 번 — 하단 떠 있는 저장 줄). 이 조각은 고친 값을 `update:modelValue` 로 새 값째 알린다.
        규칙: 위에서부터 차례로 보고 맞는 첫 규칙의 문구(없으면 언어별 쓸 문구). 조건은 대상·채널마다 코드가 정한 선택지만. 순서 위/아래 · 추가(10개까지) · 삭제. 같은 조건이 위에 있으면 「쓰이지 않음」 경고만.
        `errors` 의 열쇠는 이 채널 안 경로(`selected.ko` · `rules[0].messageId` · `rules`) — 서버 `fields.channels.<채널>.…` 에서 앞부분을 뗀 것.
    -->
    <div class="flex flex-col gap-4">
        <section class="flex flex-col gap-2" :aria-labelledby="selectedTitleId">
            <h3 :id="selectedTitleId" class="m-0 text-sm font-medium">{{ t('notificationMessage.selectedTitle') }}</h3>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('notificationMessage.selectedHint') }}</p>
            <div class="grid gap-4 sm:grid-cols-2">
                <LabeledField v-for="locale in MESSAGE_LOCALES" :key="locale" :label="t('notificationMessage.selectedFor', { locale: UtilNotificationTemplateMessages.localeLabel(t, locale) })" :error="errorOf(`selected.${locale}`)">
                    <template #aside>
                        <NuxtLink v-if="modelValue.selected[locale]" :to="UtilNotificationTemplateMessages.messagePath(modelValue.selected[locale])" class="text-xs text-primary hover:underline">{{ t('notificationMessage.openMessage') }}</NuxtLink>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Select :model-value="modelValue.selected[locale]" :input-id="id" :options="messageOptionsOf(locale)" option-label="label" option-value="value" :disabled="!editable || saving" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(value: string) => setSelected(locale, value)" />
                    </template>
                </LabeledField>
            </div>
        </section>

        <section class="flex flex-col gap-2" :aria-labelledby="rulesTitleId">
            <div class="flex flex-wrap items-center gap-2">
                <h3 :id="rulesTitleId" class="m-0 text-sm font-medium">{{ t('notificationMessage.rulesTitle') }}</h3>
                <Button v-if="editable && conditions.length" type="button" :label="t('notificationMessage.ruleAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" class="ml-auto" :disabled="saving || modelValue.rules.length >= MESSAGE_RULE_MAX" @click="addRule" />
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ conditions.length ? t('notificationMessage.rulesHint') : t('notificationMessage.noConditionsBody') }}</p>

            <template v-if="conditions.length">
                <p v-if="!modelValue.rules.length" class="m-0 rounded-lg border border-dashed border-(--line) p-3 text-center text-xs text-muted-color">{{ t('notificationMessage.rulesEmpty') }}</p>
                <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="(rule, at) in modelValue.rules" :key="rule.uid" class="flex flex-col gap-1.5 rounded-lg border border-(--line) p-2.5">
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-100 text-[11px] leading-none tabular-nums dark:bg-surface-800" :aria-label="t('notificationMessage.ruleOrder', { n: at + 1 })">{{ at + 1 }}</span>
                            <span class="text-xs text-muted-color">{{ t('notificationMessage.ruleWhen') }}</span>
                            <Select
                                :model-value="`${rule.conditionKey}:${rule.conditionValue}`"
                                :options="conditionOptions"
                                option-label="label"
                                option-value="value"
                                option-group-label="label"
                                option-group-children="items"
                                :disabled="!editable || saving"
                                :invalid="Boolean(errorOf(`rules[${at}].condition`))"
                                :aria-label="t('notificationMessage.ruleConditionLabel', { n: at + 1 })"
                                class="min-w-44 flex-1"
                                @update:model-value="(value: string) => setCondition(at, value)"
                            />
                            <span class="text-xs text-muted-color">{{ t('notificationMessage.ruleUse') }}</span>
                            <Select
                                :model-value="rule.messageId"
                                :options="allMessageOptions"
                                option-label="label"
                                option-value="value"
                                :placeholder="t('notificationMessage.ruleMessagePlaceholder')"
                                :disabled="!editable || saving"
                                :invalid="Boolean(errorOf(`rules[${at}].messageId`))"
                                :aria-label="t('notificationMessage.ruleMessageLabel', { n: at + 1 })"
                                class="min-w-44 flex-1"
                                @update:model-value="(value: string) => setRuleMessage(at, value)"
                            />
                            <div v-if="editable" class="flex items-center">
                                <Button v-tooltip.top="t('notificationMessage.ruleUpTip')" type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :disabled="at === 0 || saving" :aria-label="t('notificationMessage.ruleUp', { n: at + 1 })" @click="moveRule(at, -1)" />
                                <Button v-tooltip.top="t('notificationMessage.ruleDownTip')" type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :disabled="at === modelValue.rules.length - 1 || saving" :aria-label="t('notificationMessage.ruleDown', { n: at + 1 })" @click="moveRule(at, 1)" />
                                <Button v-tooltip.top="t('common.delete')" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="saving" :aria-label="t('notificationMessage.ruleRemove', { n: at + 1 })" @click="removeRule(at)" />
                            </div>
                        </div>
                        <p v-if="errorOf(`rules[${at}].condition`) || errorOf(`rules[${at}].messageId`)" role="alert" class="m-0 pl-8 text-xs text-red-600 dark:text-red-400">{{ errorOf(`rules[${at}].condition`) || errorOf(`rules[${at}].messageId`) }}</p>
                        <p v-else-if="UtilNotificationMessage.shadowedBy(modelValue.rules, at) !== null" class="m-0 pl-8 text-xs text-amber-700 dark:text-amber-400">{{ t('notificationMessage.ruleShadowed', { n: (UtilNotificationMessage.shadowedBy(modelValue.rules, at) ?? 0) + 1 }) }}</p>
                    </li>
                </ol>
                <p v-if="errorOf('rules')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errorOf('rules') }}</p>
            </template>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MessageConditionView, MessageLocale, MessageView } from '~/composables/template/api/useNotificationMessageApi.types';
import { MESSAGE_LOCALES, MESSAGE_RULE_MAX } from '~/composables/template/api/useNotificationMessageApi.types';
import type { MessageSelectionModel } from '~/utils/util-notification-message';
import UtilNotificationMessage from '~/utils/util-notification-message';
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

interface Props {
    modelValue: MessageSelectionModel;
    /** 이 대상·채널의 문구 전부(기본 먼저) */
    messages: MessageView[];
    /** 고를 수 있는 조건(코드가 정한다) — 없으면 규칙 칸 대신 안내 한 줄 */
    conditions?: MessageConditionView[];
    /** 바꿀 수 있는가(권한) */
    editable?: boolean;
    saving?: boolean;
    /** 칸 문구 — 서버 문구가 먼저(열쇠는 이 채널 안 경로) */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { conditions: () => [], editable: false, saving: false, errors: () => ({}) });
const emit = defineEmits<{ 'update:modelValue': [value: MessageSelectionModel] }>();

const { t } = useI18n();
const selectedTitleId = useId();
const rulesTitleId = useId();

/** 칸 문구 — 서버 문구, 없으면 화면이 미리 막는 것(규칙의 문구 비었음) */
function errorOf(key: string): string {
    if (props.errors[key]) return props.errors[key] as string;
    const match = /^rules\[(\d+)\]\.messageId$/.exec(key);
    if (match && !props.modelValue.rules[Number(match[1])]?.messageId) return t('notificationMessage.ruleMessageRequired');
    return '';
}

function messageOptionsOf(locale: MessageLocale) {
    return props.messages.filter((item) => item.locale === locale).map((item) => ({ value: item.id, label: item.name }));
}
const allMessageOptions = computed(() => props.messages.map((item) => ({ value: item.id, label: `${item.name} · ${UtilNotificationTemplateMessages.localeLabel(t, item.locale)}` })));
/** 조건 고르기 — 조건 이름별 묶음, 값은 `이름:값` */
const conditionOptions = computed(() => props.conditions.map((condition) => ({ label: condition.label, items: condition.options.map((option) => ({ value: `${condition.key}:${option.value}`, label: `${condition.label}: ${option.label}` })) })));

function update(next: Partial<MessageSelectionModel>): void {
    emit('update:modelValue', { selected: { ...props.modelValue.selected }, rules: props.modelValue.rules.map((rule) => ({ ...rule })), ...next });
}

function setSelected(locale: MessageLocale, value: string): void {
    update({ selected: { ...props.modelValue.selected, [locale]: value } });
}

function setCondition(at: number, value: string): void {
    const split = value.indexOf(':');
    update({ rules: props.modelValue.rules.map((rule, index) => (index === at ? { ...rule, conditionKey: value.slice(0, split), conditionValue: value.slice(split + 1) } : { ...rule })) });
}

function setRuleMessage(at: number, value: string): void {
    update({ rules: props.modelValue.rules.map((rule, index) => (index === at ? { ...rule, messageId: value } : { ...rule })) });
}

function addRule(): void {
    const condition = props.conditions[0];
    const option = condition?.options[0];
    if (!condition || !option || props.modelValue.rules.length >= MESSAGE_RULE_MAX) return;
    update({ rules: [...props.modelValue.rules.map((rule) => ({ ...rule })), { uid: UtilNotificationMessage.newUid(), conditionKey: condition.key, conditionValue: option.value, messageId: '' }] });
}

function moveRule(at: number, delta: number): void {
    const to = at + delta;
    if (to < 0 || to >= props.modelValue.rules.length) return;
    const next = props.modelValue.rules.map((rule) => ({ ...rule }));
    const [row] = next.splice(at, 1);
    if (row) next.splice(to, 0, row);
    update({ rules: next });
}

function removeRule(at: number): void {
    update({ rules: props.modelValue.rules.filter((_, index) => index !== at).map((rule) => ({ ...rule })) });
}
</script>
