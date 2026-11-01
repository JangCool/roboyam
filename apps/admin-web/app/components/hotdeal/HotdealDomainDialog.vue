<template>
    <!--
        링크 규칙 추가·고치기 창 — 도메인(주소를 통째로 붙여도 된다) + **정규화 미리보기**(「coupang.com 으로 저장돼요」 — 소문자·스킴·www.·포트·경로를 뗀다, `UtilHotdeal.normalizeDomain`) ·
        규칙 `SelectButton`(차단 · 제휴 · 없음) · 대형몰 `Checkbox`(규칙 없음이면 꼭 켜야 한다 — 계약 CHECK) · 같은 딜 판정 쿼리 `keyParams`(칩 입력, 선택) · 메모(0~100자).
        같은 딜 판정 쿼리: Enter·쉼표로 칩을 더하고 칩의 ×로 뺀다. `prdNo=1&x=2` 처럼 붙여 넣으면 이름만 뗀다. 추적 파라미터(`utm_*` 등)는 늘 지워서 더할 수 없다(칸 아래 안내).
        **차단 미리 보기**: 부른 화면이 저장 전에 `impact`(이 도메인의 진행 중 글 N건)를 주면 창 안에 경고를 보이고 저장 단추가 「그래도 저장」이 된다. 도메인·규칙을 바꾸면 미리 보기를 지운다(`clear-impact`).
        화면 검사는 [저장]을 누른 뒤 보인다. 서버 칸 오류(`fields.domain` — 중복 409 포함 · `fields.common` · `fields.keyParams` · `fields.note`)는 그 칸 아래, 나머지는 창 안 한 줄. 서버 호출은 부른 화면이 한다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="t(item ? 'hotdeal.domain.editTitle' : 'hotdeal.domain.addTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
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

            <!-- 같은 딜 판정 쿼리 — 칩 입력 -->
            <LabeledField :label="t('hotdeal.domain.keyParamsLabel')" :hint="t('hotdeal.domain.keyParamsCount', { n: keyParams.length, max: UtilHotdeal.KEY_PARAMS_MAX })" :error="keyParamsError" :help="t('hotdeal.domain.keyParamsHelp')">
                <template #default="{ id, describedBy, invalid }">
                    <div class="flex flex-col gap-2">
                        <ul v-if="keyParams.length" class="m-0 flex list-none flex-wrap gap-1.5 p-0" :aria-label="t('hotdeal.domain.keyParamsLabel')">
                            <li v-for="name in keyParams" :key="name" class="inline-flex items-center gap-1 rounded-md border border-(--line) bg-(--p-content-hover-background) py-0.5 pr-0.5 pl-2 font-mono text-xs">
                                {{ name }}
                                <Button type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" rounded class="h-5! w-5!" :aria-label="t('hotdeal.domain.keyParamRemove', { name })" :disabled="busy" @click="removeParam(name)" />
                            </li>
                        </ul>
                        <InputText
                            :id="id"
                            v-model="paramDraft"
                            :placeholder="t('hotdeal.domain.keyParamsPlaceholder')"
                            :disabled="busy"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            autocomplete="off"
                            spellcheck="false"
                            class="font-mono"
                            fluid
                            @keydown.enter.prevent="addParams"
                            @keydown="onParamKey"
                            @blur="addParams"
                        />
                    </div>
                </template>
            </LabeledField>

            <LabeledField :label="t('hotdeal.domain.noteLabel')" :hint="t('hotdeal.dialog.counter', { n: UtilHotdeal.textLength(note), max: UtilHotdeal.NOTE_MAX })" :error="noteError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="note" :placeholder="t('hotdeal.domain.notePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <!-- 차단 미리 보기 — 이 도메인의 진행 중 글 -->
            <Message v-if="impact" severity="warn" size="small" :closable="false" icon="pi pi-exclamation-triangle">
                <span class="flex flex-col gap-1.5">
                    <b class="font-semibold">{{ t('hotdeal.domain.impactTitle', { domain: impact.domain, n: impact.activeCount }) }}</b>
                    <span>{{ t('hotdeal.domain.impactText') }}</span>
                    <ul v-if="impact.samples.length" class="m-0 flex list-disc flex-col gap-0.5 pl-4 text-xs">
                        <li v-for="p in impact.samples" :key="p.postId" class="break-words">{{ p.title }} <span class="text-muted-color">· {{ p.storeName }}</span></li>
                        <li v-if="impact.activeCount > impact.samples.length" class="list-none text-muted-color">{{ t('hotdeal.domain.impactMore', { n: impact.activeCount - impact.samples.length }) }}</li>
                    </ul>
                </span>
            </Message>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="impact ? t('hotdeal.domain.saveAnyway') : t('common.save')" :severity="impact ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DealDomain, DealDomainImpact, DealDomainRequest } from '~/composables/hotdeal/api/useHotdealDomainApi.types';
import UtilHotdeal, { type KeyParamProblem } from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고칠 규칙(없으면 추가) */
    item?: DealDomain | null;
    /** 추가할 때 미리 채울 도메인(의심 후보 서랍의 「이 몰 규칙 등록」 — `?new=host`) */
    initialDomain?: string;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버 칸 오류 — `domain`·`common`·`keyParams`·`note` */
    fieldErrors?: Record<string, string>;
    /** 차단 미리 보기(부른 화면이 저장 전에 받아 준다) — 있으면 경고 + 「그래도 저장」 */
    impact?: DealDomainImpact | null;
}

const props = withDefaults(defineProps<Props>(), { item: null, initialDomain: '', busy: false, error: '', fieldErrors: () => ({}), impact: null });
const emit = defineEmits<{ submit: [body: DealDomainRequest]; 'clear-field': [field: string]; 'clear-impact': [] }>();

const { t } = useI18n();
const formId = useId();

const domain = ref('');
const choice = ref<'BLOCK' | 'AFFILIATE' | 'NONE'>('BLOCK');
const common = ref(false);
const keyParams = ref<string[]>([]);
const paramDraft = ref('');
const paramProblem = ref<{ problem: KeyParamProblem; name: string } | null>(null);
const note = ref('');
const tried = ref(false);

watch(open, (isOpen) => {
    if (!isOpen) return;
    const it = props.item;
    domain.value = it?.domain ?? props.initialDomain;
    choice.value = it ? UtilHotdeal.choiceOf(it.rule) : 'BLOCK';
    common.value = it?.common ?? false;
    keyParams.value = [...(it?.keyParams ?? [])];
    paramDraft.value = '';
    paramProblem.value = null;
    note.value = it?.note ?? '';
    tried.value = false;
});

// 고친 칸의 서버 오류는 지운다(중복 도메인을 고치면 그 문구가 남지 않게). 도메인·규칙을 바꾸면 차단 미리 보기도 지운다
watch(domain, () => {
    if (props.fieldErrors.domain) emit('clear-field', 'domain');
    if (props.impact) emit('clear-impact');
});
watch([choice, common], () => {
    if (props.fieldErrors.common) emit('clear-field', 'common');
    if (props.impact) emit('clear-impact');
});

const ruleOptions = computed(() => UtilHotdeal.RULES.map((value) => ({ value, label: t(`hotdeal.domain.rule.${value}`) })));
const preview = computed(() => (props.fieldErrors.domain ? null : UtilHotdeal.normalizeDomain(domain.value)));

const domainProblem = computed(() => UtilHotdeal.domainProblem(domain.value));
const commonProblem = computed(() => choice.value === 'NONE' && !common.value);
const noteProblem = computed(() => UtilHotdeal.textProblem(note.value, UtilHotdeal.NOTE_MAX));

const domainError = computed(() => props.fieldErrors.domain || (tried.value || (domain.value.trim() && domainProblem.value === 'INVALID') ? UtilHotdealMessages.domainProblem(t, domainProblem.value) : ''));
const commonError = computed(() => props.fieldErrors.common || (tried.value && commonProblem.value ? t('hotdeal.domain.commonRequired') : ''));
const keyParamsError = computed(() => props.fieldErrors.keyParams || (paramProblem.value ? UtilHotdealMessages.keyParamProblem(t, paramProblem.value.problem, paramProblem.value.name) : ''));
const noteError = computed(() => props.fieldErrors.note || UtilHotdealMessages.textProblem(t, noteProblem.value, UtilHotdeal.NOTE_MAX));

/** 칸의 글자를 칩으로 — 걸리는 이름이 있으면 그 이름만 칸에 남기고 까닭을 보인다 */
function addParams(): void {
    const names = UtilHotdeal.splitKeyParams(paramDraft.value);
    if (names.length === 0) {
        paramDraft.value = '';
        return;
    }
    const next = [...keyParams.value];
    paramProblem.value = null;
    const left: string[] = [];
    for (const name of names) {
        const problem = UtilHotdeal.keyParamProblem(name, next);
        if (problem === 'DUPLICATE') continue;
        if (problem) {
            if (!paramProblem.value) paramProblem.value = { problem, name };
            if (problem !== 'TRACKING') left.push(name);
            continue;
        }
        next.push(name);
    }
    keyParams.value = next;
    paramDraft.value = left.join(', ');
    if (props.fieldErrors.keyParams) emit('clear-field', 'keyParams');
}

/** 쉼표를 치면 바로 칩으로 */
function onParamKey(e: KeyboardEvent): void {
    if (e.key !== ',') return;
    e.preventDefault();
    addParams();
}

function removeParam(name: string): void {
    keyParams.value = keyParams.value.filter((k) => k !== name);
    paramProblem.value = null;
    if (props.fieldErrors.keyParams) emit('clear-field', 'keyParams');
}

function submit(): void {
    if (props.busy) return;
    addParams();
    tried.value = true;
    if (domainProblem.value || commonProblem.value || noteProblem.value || paramDraft.value.trim()) return;
    emit('submit', { domain: domain.value.trim(), rule: UtilHotdeal.ruleOf(choice.value), common: common.value, keyParams: [...keyParams.value], note: note.value.trim() || null });
}
</script>
