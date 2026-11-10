<template>
    <!--
        오픈소스 라이선스 직접 추가 항목 만들기·고치기 대화상자(`docs/features/oss-licenses-contract.md` 「관리자」).
          칸: 이름 · 버전 · 라이선스(SPDX 또는 자유 글) · 묶음(공개 화면/관리자 화면/서버/글꼴·아이콘/기타) · 저작권 고지 · 라이선스 전문 · 원본 링크 · 공개 화면에서 숨기기.
        화면 검사(`UtilOssLicense.problems`)는 미리 알려 주는 것이고, 서버가 거절한 칸(`fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 아래 한 줄.
        권한이 없으면 이 대화상자를 열지 않는다(페이지가 단추를 감춘다).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="item ? t('ossLicense.editTitle') : t('ossLicense.createTitle')"
        :style="{ width: 'min(48rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <form :id="formId" class="flex min-w-0 flex-col gap-5" novalidate @submit.prevent="submit">
            <div class="grid gap-5 sm:grid-cols-[minmax(0,1fr)_12rem]">
                <LabeledField :label="t('ossLicense.nameLabel')" :hint="counter(form.name, UtilOssLicense.NAME_MAX)" :error="fieldError('name')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.name" name="name" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('ossLicense.versionLabel')" :help="t('ossLicense.versionHelp')" :error="fieldError('version')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.version" name="version" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
            </div>

            <div class="grid gap-5 sm:grid-cols-2">
                <LabeledField :label="t('ossLicense.licenseLabel')" :help="t('ossLicense.licenseHelp')" :error="fieldError('license')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.license" name="license" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('ossLicense.categoryLabel')" :help="t('ossLicense.categoryHelp')" :error="fieldError('category')">
                    <template #default="{ id, describedBy, invalid }">
                        <Select
                            v-model="form.category"
                            :input-id="id"
                            :options="categoryOptions"
                            option-label="label"
                            option-value="value"
                            fluid
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                        />
                    </template>
                </LabeledField>
            </div>

            <LabeledField :label="t('ossLicense.copyrightLabel')" :hint="counter(form.copyright, UtilOssLicense.COPYRIGHT_MAX)" :help="t('ossLicense.copyrightHelp')" :error="fieldError('copyright')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.copyright" name="copyright" rows="2" fluid auto-resize :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <LabeledField :label="t('ossLicense.licenseTextLabel')" :hint="counter(form.licenseText, UtilOssLicense.LICENSE_TEXT_MAX)" :help="t('ossLicense.licenseTextHelp')" :error="fieldError('licenseText')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.licenseText" name="licenseText" rows="8" fluid class="font-mono text-xs!" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <LabeledField :label="t('ossLicense.sourceUrlLabel')" :help="t('ossLicense.sourceUrlHelp')" :error="fieldError('sourceUrl')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.sourceUrl" name="sourceUrl" type="url" inputmode="url" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <div class="flex items-center gap-2">
                <ToggleSwitch v-model="form.hidden" :input-id="`${formId}-hidden`" :disabled="saving" />
                <label :for="`${formId}-hidden`" class="text-sm">{{ t('ossLicense.hiddenLabel') }}</label>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="item ? t('common.save') : t('ossLicense.create')" :loading="saving" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { OssNoticeEntry, OssNoticeEntrySaveRequest } from '~/composables/osslicense/api/useOssLicenseAdminApi.types';
import UtilOssLicense, { type OssNoticeForm, type OssNoticeFormField } from '~/utils/util-oss-license';

interface Props {
    visible: boolean;
    /** 고칠 항목 — 없으면 만들기 */
    item?: OssNoticeEntry | null;
    saving?: boolean;
    /** 서버가 거절한 칸(`fields.<칸>`) */
    serverErrors?: Record<string, string>;
    /** 칸에 붙지 않는 저장 실패 */
    formError?: string;
}

const props = withDefaults(defineProps<Props>(), { item: null, saving: false, serverErrors: () => ({}), formError: '' });
const emit = defineEmits<{ 'update:visible': [value: boolean]; save: [body: OssNoticeEntrySaveRequest] }>();

const { t } = useI18n();
const formId = useId();

const form = ref<OssNoticeForm>(UtilOssLicense.emptyForm());
const tried = ref(false);
/** 열 때(또는 서버가 거절한 때)의 값 — 그 뒤 고친 칸은 서버 문구를 지운다 */
const opened = ref<OssNoticeForm>(UtilOssLicense.emptyForm());

const problems = computed(() => UtilOssLicense.problems(form.value));
const categoryOptions = computed(() => UtilOssLicense.CATEGORIES.map((value) => ({ value, label: t(`ossLicense.category.${value}`) })));

function counter(text: string, max: number): string {
    return t('ossLicense.counter', { n: UtilOssLicense.length(text), max });
}

/** 칸 오류 — 화면 검사(넘침·주소 꼴은 바로, 필수는 제출 뒤)가 먼저, 없으면 서버 문구(그 칸을 고치기 전까지) */
function fieldError(field: OssNoticeFormField): string {
    const problem = problems.value[field];
    if (problem && (tried.value || problem !== 'REQUIRED')) {
        if (problem === 'REQUIRED') return t('ossLicense.problem.required');
        if (problem === 'URL') return t('ossLicense.problem.url');
        return t('ossLicense.problem.tooLong', { max: UtilOssLicense.maxOf(field) });
    }
    const changed = form.value[field as keyof OssNoticeForm] !== opened.value[field as keyof OssNoticeForm];
    return changed ? '' : (props.serverErrors[field] ?? '');
}

// 서버가 새로 거절하면 그때 값을 기준으로 다시 보인다
watch(
    () => props.serverErrors,
    () => (opened.value = { ...form.value }),
);

// 열 때마다 새로
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        form.value = props.item ? UtilOssLicense.formOf(props.item) : UtilOssLicense.emptyForm();
        opened.value = { ...form.value };
        tried.value = false;
    },
);

function submit(): void {
    tried.value = true;
    if (Object.keys(problems.value).length) return;
    emit('save', UtilOssLicense.toRequest({ ...toRaw(form.value) }));
}
</script>
