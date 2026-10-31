<template>
    <!--
        광고 소재 바꾸기(결제 전) — 배너는 이미지·링크·대체 글, 한 줄은 문구·링크. 승인 뒤 결제 전에 바꾸면 처음부터 다시 심사한다(안내 한 줄).
        배너 이미지는 다시 고르지 않으면 지금 이미지(`creative.imageFileId`)를 그대로 보낸다(서버도 비우면 지금 이미지를 쓴다). 올리기·보내기는 부르는 쪽이 한다(`pickImage`·`submit`).
        핫딜 글 소재 바꾸기는 이 대화상자가 맡지 않는다(composable `updateCreative` 만 있다).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :close-on-escape="!busy"
        :header="t('business.detail.creativeEdit')"
        :style="{ width: 'min(32rem, calc(100vw - 2rem))' }"
        data-testid="business-creative-dialog"
        @update:visible="(v: boolean) => !v && emit('close')"
    >
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message v-if="backToReview" severity="warn" size="small" :closable="false">{{ t('business.detail.creativeEditBackToReview') }}</Message>
            <LabeledField v-if="banner" :label="t('business.ad.image')" :help="t('business.ad.imageHelp', { size: t(`business.adTypeSize.${adType}`) })" :error="errors.image">
                <template #default="{ id, describedBy }">
                    <BusinessFilePicker
                        :file="image"
                        :accept="UtilBusiness.IMAGE_ACCEPT"
                        :choose-label="t('business.file.choose')"
                        :busy="imageBusy"
                        :input-id="id"
                        :described-by="describedBy"
                        :type-error="t('business.file.typeError')"
                        :size-error="t('business.file.sizeError')"
                        @select="(f: File) => emit('pickImage', f)"
                        @remove="emit('removeImage')"
                    />
                    <small v-if="!image && currentImageId" class="text-xs text-muted-color">{{ t('business.detail.creativeImageKeep') }}<template v-if="creative?.imageName"> · {{ creative.imageName }}</template></small>
                </template>
            </LabeledField>
            <LabeledField v-else :label="t('business.ad.text')" :hint="`${text.length}/40`" :help="t('business.ad.textHelp')" :error="errors.text">
                <template #default="{ id, describedBy, invalid }">
                    <InputText v-model="text" :id="id" :maxlength="40" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <LabeledField :label="t('business.ad.link')" :error="errors.link">
                <template #default="{ id, describedBy, invalid }">
                    <InputText v-model="linkUrl" :id="id" type="url" inputmode="url" :placeholder="LINK_PLACEHOLDER" :maxlength="500" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <LabeledField v-if="banner" :label="t('business.ad.alt')" :hint="`${altText.length}/80`" :help="t('business.ad.altHelp')" :error="errors.alt">
                <template #default="{ id, describedBy, invalid }">
                    <InputText v-model="altText" :id="id" :maxlength="80" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('close')" />
            <Button type="submit" :form="formId" :label="t('business.detail.creativeEditSubmit')" :loading="busy" :disabled="imageBusy" data-testid="business-creative-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdType, BizCreative, BizCreativeRequest, BusinessFile } from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

interface Props {
    visible: boolean;
    adType: AdType;
    /** 지금 소재 — 열 때 칸을 채운다 */
    creative: BizCreative | null;
    /** 새로 올린 배너 이미지(부르는 쪽이 올린 결과) */
    image?: BusinessFile | null;
    imageBusy?: boolean;
    /** 승인 뒤(결제 전)라 바꾸면 다시 심사로 돌아간다 */
    backToReview?: boolean;
    busy?: boolean;
    error?: string;
    /** 서버 칸 오류(`fields.imageFileId`·`linkUrl`·`altText`·`text`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { image: null, imageBusy: false, backToReview: false, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [body: BizCreativeRequest]; pickImage: [file: File]; removeImage: []; close: [] }>();

const { t } = useI18n();
const formId = useId();

const LINK_PLACEHOLDER = 'https://';
const banner = computed(() => props.adType !== 'TEXT_LINE');
/** 지금 배너 이미지 — 새로 고르지 않으면 이 ID 를 그대로 보낸다 */
const currentImageId = computed(() => props.creative?.imageFileId ?? null);
const text = ref('');
const linkUrl = ref('');
const altText = ref('');
const tried = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        text.value = props.creative?.text ?? '';
        linkUrl.value = props.creative?.linkUrl ?? '';
        altText.value = props.creative?.altText ?? '';
        tried.value = false;
    },
    { immediate: true },
);

const errors = computed(() => {
    const out: Record<string, string> = {};
    const f = props.fieldErrors;
    if (f.imageFileId) out.image = f.imageFileId;
    if (f.linkUrl) out.link = f.linkUrl;
    if (f.altText) out.alt = f.altText;
    if (f.text) out.text = f.text;
    if (!tried.value) return out;
    if (banner.value) {
        if (!props.image && !currentImageId.value) out.image ??= t('business.detail.creativeImageAgain');
        if (!altText.value.trim()) out.alt ??= t('business.ad.altRequired');
    } else if (!text.value.trim()) out.text ??= t('business.ad.textRequired');
    if (!UtilBusiness.isHttpUrl(linkUrl.value)) out.link ??= t('business.ad.linkInvalid');
    return out;
});

function submit(): void {
    tried.value = true;
    if (props.busy || props.imageBusy || Object.keys(errors.value).length) return;
    emit(
        'submit',
        banner.value
            ? { imageFileId: props.image?.fileId ?? currentImageId.value, linkUrl: linkUrl.value.trim(), altText: altText.value.trim() }
            : { text: text.value.trim(), linkUrl: linkUrl.value.trim() },
    );
}
</script>
