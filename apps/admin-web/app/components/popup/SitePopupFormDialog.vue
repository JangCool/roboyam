<template>
    <!--
        팝업 만들기·고치기 대화상자 — 왼쪽 입력, 오른쪽(넓은 화면) 미리보기(PC·모바일 바꿔 보기 — 입력하는 대로 바뀐다). 좁은 화면은 입력 아래에 미리보기.
          칸: 관리용 이름 · 종류(이미지 / 텍스트) · [이미지] 그림 · 대체 글 · 링크 / [텍스트] 제목 · 내용 · 링크 · 단추 글자 · 보일 곳(홈만 / 모든 화면) · 기기(PC · 모바일)
              · 닫기 줄 선택지(오늘 하루 · 1주일 · 다시 보지 않기 · 「닫기」만) · 노출 켜기 · 노출 기간(비우면 상시).
          종류를 바꿔도 다른 종류의 칸 값은 남는다(되돌리면 다시 쓰인다) — 저장 때는 모두 보내고 서버가 지금 종류의 칸만 검사한다.
        그림은 고르는 즉시 올려 받은 파일 ID 를 저장 때 보낸다(하이라이트 대표 이미지와 같은 두 걸음 — 저장하지 않은 파일은 서버의 「올리다 만 파일 정리」가 지운다).
        화면 검사(`UtilSitePopup`)는 미리 알려 주는 것이고, 서버가 거절한 칸(`fields.<칸>`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 아래 한 줄.
        권한이 없으면 이 대화상자를 열지 않는다(페이지가 단추를 감춘다).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="item ? t('popup.editTitle') : t('popup.createTitle')"
        :style="{ width: 'min(70rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <div class="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)]">
            <form :id="formId" class="flex min-w-0 flex-col gap-5" novalidate @submit.prevent="submit">
                <LabeledField :label="t('popup.nameLabel')" :hint="counter(form.name, UtilSitePopup.NAME_MAX)" :help="t('popup.nameHelp')" :error="fieldError('name')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.name" name="name" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <!-- 종류 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="`${formId}-kind`" class="text-sm font-medium">{{ t('popup.kindLabel') }}</span>
                    <SelectButton v-model="form.kind" :options="kindOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-kind`" :disabled="saving">
                        <template #option="{ option }">
                            <i :class="option.icon" class="text-sm" aria-hidden="true" />
                            <span>{{ option.label }}</span>
                        </template>
                    </SelectButton>
                </div>

                <!-- 이미지 -->
                <template v-if="form.kind === 'IMAGE'">
                    <section class="flex flex-col gap-1.5" :aria-labelledby="`${formId}-image`">
                        <h3 :id="`${formId}-image`" class="m-0 text-sm font-medium">{{ t('popup.imageLabel') }}</h3>
                        <div class="flex items-start gap-3">
                            <div class="flex aspect-[4/5] w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-800">
                                <img v-if="form.imageUrl && !broken" :src="form.imageUrl" alt="" class="size-full object-cover" @error="broken = true" />
                                <i v-else class="pi pi-image text-lg text-muted-color" aria-hidden="true" />
                            </div>
                            <div class="flex min-w-0 flex-col gap-1.5">
                                <div class="flex flex-wrap gap-2">
                                    <Button type="button" :label="form.imageFileId ? t('popup.imageChange') : t('popup.imageUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="uploading" :disabled="saving" @click="pickFile" />
                                    <Button v-if="form.imageFileId" type="button" :label="t('popup.imageRemove')" icon="pi pi-times" size="small" severity="danger" variant="text" :disabled="uploading || saving" @click="removeImage" />
                                </div>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.imageHint', { max: UtilSitePopup.IMAGE_MAX_BYTES / (1024 * 1024) }) }}</p>
                                <input ref="fileInput" type="file" :accept="UtilSitePopup.IMAGE_ACCEPT" class="hidden" tabindex="-1" :aria-label="t('popup.imageUpload')" @change="onFileChange" />
                            </div>
                        </div>
                        <p v-if="imageError || fieldError('imageFileId')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ imageError || fieldError('imageFileId') }}</p>
                    </section>

                    <LabeledField :label="t('popup.imageAltLabel')" :hint="counter(form.imageAlt, UtilSitePopup.IMAGE_ALT_MAX)" :help="t('popup.imageAltHelp')" :error="fieldError('imageAlt')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.imageAlt" name="imageAlt" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </template>

                <!-- 텍스트 -->
                <template v-else>
                    <LabeledField :label="t('popup.headingLabel')" :hint="counter(form.heading, UtilSitePopup.HEADING_MAX)" :help="t('popup.headingHelp')" :error="fieldError('heading')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.heading" name="heading" fluid :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('popup.bodyLabel')" :hint="counter(form.body, UtilSitePopup.BODY_MAX)" :help="t('popup.bodyHelp')" :error="fieldError('body')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.body" name="body" rows="5" fluid auto-resize :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </template>

                <LabeledField :label="t('popup.linkLabel')" :help="form.kind === 'IMAGE' ? t('popup.linkHelpImage') : t('popup.linkHelpText')" :error="fieldError('link')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.link" name="link" fluid inputmode="url" :placeholder="t('popup.linkPlaceholder')" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <LabeledField v-if="form.kind === 'TEXT'" :label="t('popup.buttonLabelLabel')" :hint="counter(form.buttonLabel, UtilSitePopup.BUTTON_MAX)" :help="t('popup.buttonLabelHelp')" :error="fieldError('buttonLabel')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="form.buttonLabel" name="buttonLabel" fluid :placeholder="t('popup.buttonDefault')" :disabled="saving || !form.link.trim()" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <!-- 보일 곳 · 기기 -->
                <div class="grid gap-5 sm:grid-cols-2">
                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('popup.targetLabel') }}</legend>
                        <div v-for="target in UtilSitePopup.TARGETS" :key="target" class="flex items-center gap-2">
                            <RadioButton v-model="form.target" :input-id="`${formId}-target-${target}`" :name="`${formId}-target`" :value="target" :disabled="saving" />
                            <label :for="`${formId}-target-${target}`" class="text-sm">{{ t(`popup.target.${target}`) }}</label>
                        </div>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.targetHelp') }}</p>
                    </fieldset>
                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" :aria-describedby="`${formId}-devices-help`">
                        <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('popup.devicesLabel') }}</legend>
                        <div v-for="device in UtilSitePopup.DEVICES" :key="device" class="flex items-center gap-2">
                            <Checkbox v-model="form.devices" :input-id="`${formId}-device-${device}`" :value="device" :disabled="saving" :invalid="Boolean(fieldError('devices'))" />
                            <label :for="`${formId}-device-${device}`" class="inline-flex items-center gap-1.5 text-sm"><i :class="UtilSitePopup.deviceIcon(device)" class="text-xs text-muted-color" aria-hidden="true" />{{ t(`popup.device.${device}`) }}</label>
                        </div>
                        <p v-if="fieldError('devices')" :id="`${formId}-devices-help`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('devices') }}</p>
                        <p v-else :id="`${formId}-devices-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.devicesHelp') }}</p>
                    </fieldset>
                </div>

                <!-- 띄울 창 — 같은 번호끼리 한 창에서 슬라이드, 번호가 다르면 PC 는 나란히·모바일은 차례로 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="`${formId}-window`" class="text-sm font-medium">{{ t('popup.windowLabel') }}</span>
                    <SelectButton v-model="form.windowNo" :options="windowOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${formId}-window`" :disabled="saving" />
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.windowHelp') }}</p>
                </div>

                <!-- 닫기 줄 선택지 -->
                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('popup.hideLabel') }}</legend>
                    <div v-for="option in UtilSitePopup.HIDE_OPTIONS" :key="option" class="flex items-center gap-2">
                        <RadioButton v-model="form.hideOption" :input-id="`${formId}-hide-${option}`" :name="`${formId}-hide`" :value="option" :disabled="saving" />
                        <label :for="`${formId}-hide-${option}`" class="text-sm">{{ t(`popup.hideChoice.${option}`) }}</label>
                    </div>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.hideHelp') }}</p>
                </fieldset>

                <div class="flex flex-col gap-1">
                    <div class="flex items-center gap-3">
                        <ToggleSwitch v-model="form.visible" :input-id="`${formId}-visible`" :disabled="saving" />
                        <label :for="`${formId}-visible`" class="text-sm font-medium">{{ t('popup.visibleLabel') }}</label>
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('popup.visibleHelp') }}</p>
                </div>

                <!-- 노출 기간 — 비우면 상시 -->
                <section class="flex flex-col gap-1.5" :aria-labelledby="`${formId}-period`">
                    <h3 :id="`${formId}-period`" class="m-0 text-sm font-medium">{{ t('popup.periodLabel') }}</h3>
                    <div class="flex flex-wrap items-center gap-2">
                        <AppDateTimePicker v-model="form.showFrom" :input-id="`${formId}-from`" :minute-step="10" show-button-bar :show-zone="false" :disabled="saving" :placeholder="t('popup.periodFrom')" class="w-52" />
                        <span class="text-muted-color" aria-hidden="true">~</span>
                        <AppDateTimePicker
                            v-model="form.showUntil"
                            :input-id="`${formId}-until`"
                            :minute-step="10"
                            show-button-bar
                            :show-zone="false"
                            :min="form.showFrom || undefined"
                            :disabled="saving"
                            :invalid="Boolean(fieldError('showUntil'))"
                            :placeholder="t('popup.periodUntil')"
                            class="w-52"
                        />
                    </div>
                    <p v-if="fieldError('showUntil')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('showUntil') }}</p>
                    <p v-else class="m-0 text-xs text-muted-color">{{ t('popup.periodHelp') }}</p>
                </section>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false">{{ formError }}</Message>
            </form>

            <!-- 미리보기 — 입력하는 대로 -->
            <aside class="flex min-w-0 flex-col gap-2 lg:sticky lg:top-0 lg:self-start" :aria-label="t('popup.previewLabel')">
                <div class="flex items-center justify-between gap-2">
                    <span class="text-sm font-medium">{{ t('popup.previewLabel') }}</span>
                    <SelectButton v-model="device" :options="deviceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('popup.previewDevice')" />
                </div>
                <SitePopupPreviewFrame :popups="previewPopups" :device="device" :empty-text="t('popup.previewDeviceOff', { device: t(`popup.device.${device}`) })" />
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('popup.previewHelp') }}</p>
            </aside>
        </div>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="item ? t('common.save') : t('popup.create')" :loading="saving" :disabled="uploading" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { SitePopupDevice, SitePopupItem, SitePopupSaveRequest } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup, { type SitePopupForm, type SitePopupFormField } from '~/utils/util-site-popup';
import UtilSitePopupMessages from '~/utils/util-site-popup-messages';

interface Props {
    visible: boolean;
    /** 고칠 팝업 — 없으면 만들기 */
    item?: SitePopupItem | null;
    /** 그림 올리기 — 올린 파일 ID(실패하면 `ApiError`) */
    uploadImage: (file: File) => Promise<string>;
    saving?: boolean;
    /** 서버가 거절한 칸(`fields.<칸>`) */
    serverErrors?: Record<string, string>;
    /** 칸에 붙지 않는 저장 실패 */
    formError?: string;
}

const props = withDefaults(defineProps<Props>(), { item: null, saving: false, serverErrors: () => ({}), formError: '' });
const emit = defineEmits<{ 'update:visible': [value: boolean]; save: [body: SitePopupSaveRequest] }>();

const { t } = useI18n();
const formId = useId();
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');

const form = ref<SitePopupForm>(UtilSitePopup.emptyForm());
const tried = ref(false);
/** 서버 칸 오류를 그 칸을 고칠 때 지운다 — 고친 칸 이름 */
const touched = ref<Set<string>>(new Set());

const problems = computed(() => UtilSitePopup.problems(form.value));

const kindOptions = computed(() => UtilSitePopup.KINDS.map((value) => ({ value, label: t(`popup.kind.${value}`), icon: UtilSitePopup.kindIcon(value) })));
const deviceOptions = computed(() => UtilSitePopup.DEVICES.map((value) => ({ value, label: t(`popup.device.${value}`) })));
const windowOptions = computed(() => UtilSitePopup.WINDOWS.map((value) => ({ value, label: t('popup.windowNo', { n: value }) })));

function counter(text: string, max: number): string {
    return t('popup.counter', { n: UtilSitePopup.length(text), max });
}

/** 칸 오류 — 화면 검사(제출을 눌렀거나, 필수가 아닌 문제는 바로)가 먼저, 없으면 서버 문구(그 칸을 고치기 전까지) */
function fieldError(field: SitePopupFormField): string {
    const problem = problems.value[field];
    const early = problem === 'TOO_LONG' || problem === 'LINK' || problem === 'PERIOD_ORDER';
    if (problem && (tried.value || early)) return UtilSitePopupMessages.formProblem(t, field, problem);
    return touched.value.has(field) ? '' : (props.serverErrors[field] ?? '');
}

const FIELDS: SitePopupFormField[] = ['name', 'imageFileId', 'imageAlt', 'link', 'heading', 'body', 'buttonLabel', 'devices', 'showUntil'];
watch(
    () => [form.value.name, form.value.imageFileId, form.value.imageAlt, form.value.link, form.value.heading, form.value.body, form.value.buttonLabel, form.value.devices.join(','), `${form.value.showFrom}|${form.value.showUntil}`] as const,
    (next, prev) => {
        next.forEach((value, i) => {
            if (prev && value !== prev[i]) touched.value.add(FIELDS[i] as string);
        });
    },
);
// 서버가 새로 거절하면 다시 보인다
watch(
    () => props.serverErrors,
    () => (touched.value = new Set()),
);

/* ── 미리보기 ── */
const device = ref<SitePopupDevice>('PC');
/** 고른 기기에 안 뜨는 팝업이면 빈 화면(「이 기기에는 뜨지 않아요」) */
const previewPopups = computed(() => (form.value.devices.includes(device.value) ? [UtilSitePopup.previewOf(form.value)] : []));

/* ── 그림 ── */
const uploading = ref(false);
const imageError = ref('');
const broken = ref(false);

function pickFile(): void {
    if (uploading.value || props.saving) return;
    fileInput.value?.click();
}

async function onFileChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 같은 파일을 다시 고를 수 있게 비운다
    input.value = '';
    if (!file) return;
    imageError.value = '';
    const problem = UtilSitePopup.imageProblem(file);
    if (problem) {
        imageError.value = UtilSitePopupMessages.imageProblem(t, problem);
        return;
    }
    uploading.value = true;
    try {
        const fileId = await props.uploadImage(file);
        broken.value = false;
        // 미리보기는 브라우저 임시 주소 — 저장 뒤 목록은 서버가 준 그림 주소로 보인다
        form.value = { ...form.value, imageFileId: fileId, imageUrl: URL.createObjectURL(file) };
    } catch (e) {
        imageError.value = e instanceof ApiError ? (e.fields?.file ?? e.fields?.purpose ?? e.message) : t('popup.imageUploadFailed');
    } finally {
        uploading.value = false;
    }
}

function removeImage(): void {
    form.value = { ...form.value, imageFileId: null, imageUrl: null };
}

/* ── 열 때마다 새로 ── */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        form.value = props.item ? UtilSitePopup.formOf(props.item) : UtilSitePopup.emptyForm();
        tried.value = false;
        touched.value = new Set();
        imageError.value = '';
        broken.value = false;
        device.value = form.value.devices.includes('PC') ? 'PC' : 'MOBILE';
    },
    { immediate: true },
);

function submit(): void {
    tried.value = true;
    touched.value = new Set();
    if (props.saving || uploading.value || Object.keys(problems.value).length > 0) return;
    emit('save', UtilSitePopup.toRequest(form.value));
}
</script>
