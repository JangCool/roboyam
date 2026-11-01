<template>
    <!--
        링크 규칙 추가·고치기 창 — 도메인(주소를 통째로 붙여도 된다) + **정규화 미리보기**(「coupang.com 으로 저장돼요」 — 소문자·스킴·www.·포트·경로를 뗀다, `UtilHotdeal.normalizeDomain`) ·
        규칙 `SelectButton`(차단 · 제휴 · 없음) · 대형몰 `Checkbox`(규칙 없음이면 꼭 켜야 한다 — 계약 CHECK) · 메모(0~100자).
        화면 검사는 [저장]을 누른 뒤 보인다. 서버 칸 오류(`fields.domain` — 중복 409 포함 · `fields.common` · `fields.note`)는 그 칸 아래, 나머지는 창 안 한 줄. 서버 호출은 부른 화면이 한다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="t(item ? 'hotdeal.domain.editTitle' : 'hotdeal.domain.addTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <LabeledField :label="t('hotdeal.domain.domainLabel')" :error="domainError" :success="preview ? t('hotdeal.domain.preview', { domain: preview }) : ''" :help="t('hotdeal.domain.domainHelp')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="domain" :placeholder="t('hotdeal.domain.domainPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" spellcheck="false" fluid />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-rule`" class="text-sm font-medium">{{ t('hotdeal.domain.ruleLabel') }}</span>
                <SelectButton v-model="choice" :options="ruleOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="busy" :aria-labelledby="`${formId}-rule`" class="self-start" />
                <small class="text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.domain.ruleHelp.${choice}`) }}</small>
            </div>

            <div class="flex flex-col gap-1">
                <div class="flex items-start gap-2">
                    <Checkbox v-model="common" binary :input-id="`${formId}-common`" :disabled="busy" :invalid="Boolean(commonError)" />
                    <label :for="`${formId}-common`" class="flex flex-col text-sm">
                        {{ t('hotdeal.domain.commonLabel') }}
                        <span class="text-xs text-muted-color">{{ t('hotdeal.domain.commonHelp') }}</span>
                    </label>
                </div>
                <p v-if="commonError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ commonError }}</p>
            </div>

            <LabeledField :label="t('hotdeal.domain.noteLabel')" :hint="t('hotdeal.dialog.counter', { n: UtilHotdeal.textLength(note), max: UtilHotdeal.NOTE_MAX })" :error="noteError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="note" :placeholder="t('hotdeal.domain.notePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DealDomain, DealDomainRequest } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고칠 규칙(없으면 추가) */
    item?: DealDomain | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버 칸 오류 — `domain`·`common`·`note` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { item: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [body: DealDomainRequest]; 'clear-field': [field: string] }>();

const { t } = useI18n();
const formId = useId();

const domain = ref('');
const choice = ref<'BLOCK' | 'AFFILIATE' | 'NONE'>('BLOCK');
const common = ref(false);
const note = ref('');
const tried = ref(false);

watch(open, (isOpen) => {
    if (!isOpen) return;
    const it = props.item;
    domain.value = it?.domain ?? '';
    choice.value = it ? UtilHotdeal.choiceOf(it.rule) : 'BLOCK';
    common.value = it?.common ?? false;
    note.value = it?.note ?? '';
    tried.value = false;
});

// 고친 칸의 서버 오류는 지운다(중복 도메인을 고치면 그 문구가 남지 않게)
watch(domain, () => props.fieldErrors.domain && emit('clear-field', 'domain'));
watch([choice, common], () => props.fieldErrors.common && emit('clear-field', 'common'));

const ruleOptions = computed(() => UtilHotdeal.RULES.map((value) => ({ value, label: t(`hotdeal.domain.rule.${value}`) })));
const preview = computed(() => (props.fieldErrors.domain ? null : UtilHotdeal.normalizeDomain(domain.value)));

const domainProblem = computed(() => UtilHotdeal.domainProblem(domain.value));
const commonProblem = computed(() => choice.value === 'NONE' && !common.value);
const noteProblem = computed(() => UtilHotdeal.textProblem(note.value, UtilHotdeal.NOTE_MAX));

const domainError = computed(() => props.fieldErrors.domain || (tried.value || (domain.value.trim() && domainProblem.value === 'INVALID') ? UtilHotdealMessages.domainProblem(t, domainProblem.value) : ''));
const commonError = computed(() => props.fieldErrors.common || (tried.value && commonProblem.value ? t('hotdeal.domain.commonRequired') : ''));
const noteError = computed(() => props.fieldErrors.note || UtilHotdealMessages.textProblem(t, noteProblem.value, UtilHotdeal.NOTE_MAX));

function submit(): void {
    if (props.busy) return;
    tried.value = true;
    if (domainProblem.value || commonProblem.value || noteProblem.value) return;
    emit('submit', { domain: domain.value.trim(), rule: UtilHotdeal.ruleOf(choice.value), common: common.value, note: note.value.trim() || null });
}
</script>
