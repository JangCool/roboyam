<template>
    <!--
        사이트 제휴 몰 추가·고치기 창(D5) — 몰 도메인(주소를 통째로 붙여도 된다) + **정규화 미리보기**(링크 규칙 창과 같은 `UtilHotdeal.normalizeDomain`) ·
        제휴 프로그램 이름(1~40자) · 변환 방식 `SelectButton`(링크 틀 `TEMPLATE` — `{url}` 자리표시자 1개 필수 / 붙일 쿼리 `QUERY` — `k=v&…`) + 그 칸 ·
        **변환 미리 보기**(예시 상품 주소 → 변환된 주소 — `UtilHotdeal.applySiteTransform`, 예시 주소는 고칠 수 있다).
        방식을 바꿔도 다른 방식 칸의 글자는 남는다(보내는 것은 고른 방식 칸만). 새 몰은 켠 채로 만든다(켬/끔은 표의 단추).
        화면 검사는 [저장]을 누른 뒤(또는 그 칸을 떠난 뒤) 보인다. 서버 칸 오류(`fields.domain` — 중복 409 포함 · `programName` · `template` · `query`)는 그 칸 아래, 나머지는 창 안 한 줄. 서버 호출은 부른 화면이 한다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="t(item ? 'hotdeal.siteAffiliate.editTitle' : 'hotdeal.siteAffiliate.addTitle')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <LabeledField :label="t('hotdeal.siteAffiliate.domainLabel')" :error="domainError" :success="preview ? t('hotdeal.siteAffiliate.preview', { domain: preview }) : ''" :help="t('hotdeal.siteAffiliate.domainHelp')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="domain" :placeholder="t('hotdeal.siteAffiliate.domainPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" autocomplete="off" spellcheck="false" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('hotdeal.siteAffiliate.programLabel')" :hint="t('hotdeal.dialog.counter', { n: UtilHotdeal.textLength(programName), max: UtilHotdeal.SITE_PROGRAM_MAX })" :error="programError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="programName" :placeholder="t('hotdeal.siteAffiliate.programPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid @blur="touched.program = true" />
                </template>
            </LabeledField>

            <div class="flex flex-col gap-1.5">
                <span :id="`${formId}-type`" class="text-sm font-medium">{{ t('hotdeal.siteAffiliate.transformLabel') }}</span>
                <SelectButton v-model="type" :options="typeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="busy" :aria-labelledby="`${formId}-type`" class="self-start" />
                <small class="text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.siteAffiliate.transformHelp.${type}`, { ph: UtilHotdeal.URL_PLACEHOLDER }) }}</small>
            </div>

            <LabeledField v-if="type === 'TEMPLATE'" :label="t('hotdeal.siteAffiliate.templateLabel')" :error="templateError" :help="t('hotdeal.siteAffiliate.templateHelp', { ph: UtilHotdeal.URL_PLACEHOLDER })">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        v-model="template"
                        :placeholder="t('hotdeal.siteAffiliate.templatePlaceholder', { ph: UtilHotdeal.URL_PLACEHOLDER })"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        autocomplete="off"
                        spellcheck="false"
                        class="font-mono text-[13px]!"
                        fluid
                        @blur="touched.template = true"
                    />
                </template>
            </LabeledField>
            <LabeledField v-else :label="t('hotdeal.siteAffiliate.queryLabel')" :error="queryError" :help="t('hotdeal.siteAffiliate.queryHelp')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        v-model="query"
                        :placeholder="t('hotdeal.siteAffiliate.queryPlaceholder')"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        autocomplete="off"
                        spellcheck="false"
                        class="font-mono text-[13px]!"
                        fluid
                        @blur="touched.query = true"
                    />
                </template>
            </LabeledField>

            <!-- 변환 미리 보기 -->
            <section class="flex flex-col gap-2 rounded-lg border border-(--line) bg-(--p-content-hover-background) p-3" :aria-labelledby="`${formId}-preview`">
                <h3 :id="`${formId}-preview`" class="m-0 text-[13px] font-semibold">{{ t('hotdeal.siteAffiliate.previewTitle') }}</h3>
                <label :for="`${formId}-example`" class="text-xs text-muted-color">{{ t('hotdeal.siteAffiliate.exampleLabel') }}</label>
                <InputText :id="`${formId}-example`" v-model="example" :placeholder="exampleDefault" :disabled="busy" autocomplete="off" spellcheck="false" size="small" class="font-mono text-[12px]!" fluid />
                <span class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-arrow-down text-[10px]" aria-hidden="true" />{{ t('hotdeal.siteAffiliate.previewResult') }}</span>
                <p class="m-0 font-mono text-[12px] break-all" :class="converted ? '' : 'text-muted-color'" role="status" aria-live="polite">{{ converted ?? t('hotdeal.siteAffiliate.previewEmpty') }}</p>
                <p v-if="exampleOutside" class="m-0 text-[11px] text-amber-700 dark:text-amber-400">{{ t('hotdeal.siteAffiliate.exampleOutside', { domain: preview }) }}</p>
            </section>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { SiteAffiliateStore, SiteAffiliateStoreRequest, SiteAffiliateTransform, SiteAffiliateTransformType } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';
import { SITE_AFFILIATE_TRANSFORM_TYPES } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 고칠 몰(없으면 추가) */
    item?: SiteAffiliateStore | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버 칸 오류 — `domain`·`programName`·`template`·`query` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { item: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [body: SiteAffiliateStoreRequest]; 'clear-field': [field: string] }>();

const { t } = useI18n();
const formId = useId();

const domain = ref('');
const programName = ref('');
const type = ref<SiteAffiliateTransformType>('TEMPLATE');
const template = ref('');
const query = ref('');
const example = ref('');
const tried = ref(false);
const touched = reactive({ program: false, template: false, query: false });

watch(open, (isOpen) => {
    if (!isOpen) return;
    const it = props.item;
    domain.value = it?.domain ?? '';
    programName.value = it?.programName ?? '';
    type.value = it?.transform.type ?? 'TEMPLATE';
    template.value = it?.transform.type === 'TEMPLATE' ? it.transform.template : '';
    query.value = it?.transform.type === 'QUERY' ? it.transform.query : '';
    example.value = '';
    tried.value = false;
    Object.assign(touched, { program: false, template: false, query: false });
});

// 고친 칸의 서버 오류는 지운다(중복 도메인을 고치면 그 문구가 남지 않게)
watch(domain, () => {
    if (props.fieldErrors.domain) emit('clear-field', 'domain');
});
watch(programName, () => {
    if (props.fieldErrors.programName) emit('clear-field', 'programName');
});
watch([type, template, query], () => {
    if (props.fieldErrors.template) emit('clear-field', 'template');
    if (props.fieldErrors.query) emit('clear-field', 'query');
});

const typeOptions = computed(() => SITE_AFFILIATE_TRANSFORM_TYPES.map((value) => ({ value, label: t(`hotdeal.siteAffiliate.transformType.${value}`) })));
const preview = computed(() => (props.fieldErrors.domain ? null : UtilHotdeal.normalizeDomain(domain.value)));
const transform = computed<SiteAffiliateTransform>(() => (type.value === 'TEMPLATE' ? { type: 'TEMPLATE', template: template.value } : { type: 'QUERY', query: query.value }));

const domainProblem = computed(() => UtilHotdeal.domainProblem(domain.value));
const programProblem = computed(() => UtilHotdeal.programProblem(programName.value));
const templateProblem = computed(() => UtilHotdeal.templateProblem(template.value));
const queryProblem = computed(() => UtilHotdeal.queryProblem(query.value));

const domainError = computed(() => props.fieldErrors.domain || (tried.value || (domain.value.trim() && domainProblem.value === 'INVALID') ? UtilHotdealMessages.domainProblem(t, domainProblem.value) : ''));
const programError = computed(() => props.fieldErrors.programName || (tried.value || touched.program || programProblem.value === 'TOO_LONG' ? UtilHotdealMessages.programProblem(t, programProblem.value) : ''));
const templateError = computed(() => props.fieldErrors.template || (tried.value || touched.template ? UtilHotdealMessages.templateProblem(t, templateProblem.value) : ''));
const queryError = computed(() => props.fieldErrors.query || (tried.value || touched.query ? UtilHotdealMessages.queryProblem(t, queryProblem.value) : ''));

/* ── 변환 미리 보기 ── */

const exampleDefault = computed(() => UtilHotdeal.exampleProductUrl(preview.value));
const exampleUrl = computed(() => example.value.trim() || exampleDefault.value);
const converted = computed(() => UtilHotdeal.applySiteTransform(exampleUrl.value, transform.value));
/** 고친 예시 주소가 이 몰이 아니면 — 실제로는 이 몰 링크에만 변환이 걸린다 */
const exampleOutside = computed(() => {
    if (!example.value.trim() || !preview.value) return false;
    const host = UtilHotdeal.normalizeDomain(example.value);
    return host !== null && host !== preview.value && !host.endsWith(`.${preview.value}`);
});

function submit(): void {
    if (props.busy) return;
    tried.value = true;
    const transformBad = type.value === 'TEMPLATE' ? templateProblem.value : queryProblem.value;
    if (domainProblem.value || programProblem.value || transformBad) return;
    const tf = transform.value;
    emit('submit', {
        domain: domain.value.trim(),
        programName: programName.value.trim(),
        transform: tf.type === 'TEMPLATE' ? { type: 'TEMPLATE', template: tf.template.trim() } : { type: 'QUERY', query: UtilHotdeal.normalizeQuery(tf.query) },
        enabled: props.item?.enabled ?? true,
    });
}
</script>
