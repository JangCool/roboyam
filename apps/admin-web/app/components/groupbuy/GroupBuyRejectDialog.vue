<template>
    <!--
        취소·반품 요청 거절(계약 결정 55, commerce.md 10-3) — 사유 코드 필수 + 사유 5~200자.
        · 발송 전 취소 요청은 「이미 발송함」(`ALREADY_SHIPPED`)으로만 거절한다(회원은 반품 흐름으로) — 사진 없음.
        · 반품 요청은 나머지 코드 중 하나 + 근거 사진 1~3장(다툼은 사업자가 증명 — 5년 보관). 이 공구에 청약철회 제한 안내가 없으면 「사용으로 가치 감소」에 경고(서버가 거절한다 — G40).
        사진 올리기는 부르는 쪽이 준 `upload` 로 한다(이 부품은 서버를 모른다). 서버 칸 오류는 `fieldErrors`(`reasonCode`·`photoIds`·`reason`).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('groupBuy.rejectTitle')" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ type === 'CANCEL' ? t('groupBuy.rejectCancelBody') : t('groupBuy.rejectReturnBody') }}</p>

            <div class="flex flex-col gap-1.5">
                <span :id="codeId" class="text-sm font-medium">{{ t('groupBuy.rejectCode') }}</span>
                <div role="radiogroup" :aria-labelledby="codeId" class="flex flex-col gap-1.5">
                    <label v-for="c in codes" :key="c" class="flex cursor-pointer items-start gap-2 text-[13px]">
                        <RadioButton v-model="code" :value="c" :name="codeId" :disabled="busy" class="mt-0.5" />
                        <span><span class="font-medium">{{ t(`groupBuy.rejectReason.${c}`) }}</span><span class="block text-xs text-muted-color">{{ t(`groupBuy.rejectReasonHelp.${c}`) }}</span></span>
                    </label>
                </div>
                <Message v-if="code === 'USED_VALUE_LOSS' && !withdrawalRestricted" severity="warn" :closable="false" size="small">{{ t('groupBuy.rejectNoRestriction') }}</Message>
                <p v-if="codeError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ codeError }}</p>
            </div>

            <div v-if="type === 'RETURN'" class="flex flex-col gap-1.5">
                <span class="text-sm font-medium">{{ t('groupBuy.rejectPhotos') }} <small class="ml-1 text-xs font-normal text-muted-color">{{ t('groupBuy.rejectPhotosHint', { max: UtilGroupBuy.EVIDENCE_MAX }) }}</small></span>
                <div class="flex flex-wrap gap-2">
                    <div v-for="(p, i) in photos" :key="p.id" class="relative">
                        <img :src="p.url" :alt="t('groupBuy.evidenceAlt', { n: i + 1 })" class="size-20 rounded-md border border-(--line) object-cover" />
                        <Button type="button" icon="pi pi-times" size="small" severity="danger" rounded class="absolute! -top-2 -right-2 size-6!" :aria-label="t('groupBuy.evidenceRemove', { n: i + 1 })" :disabled="busy" @click="photos = photos.filter((x) => x.id !== p.id)" />
                    </div>
                    <label v-if="photos.length < UtilGroupBuy.EVIDENCE_MAX" class="flex size-20 cursor-pointer flex-col items-center justify-center gap-1 rounded-md border border-dashed border-(--line) text-xs text-muted-color hover:border-primary hover:text-primary">
                        <i :class="uploading ? 'pi pi-spin pi-spinner' : 'pi pi-camera'" aria-hidden="true" />
                        {{ t('groupBuy.evidenceAdd') }}
                        <input type="file" accept="image/*" class="sr-only" :disabled="uploading || busy" @change="onPick" />
                    </label>
                </div>
                <p v-if="photoError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ photoError }}</p>
            </div>

            <LabeledField :label="t('payment.reason.label')" :hint="t('payment.reason.hint', { min: UtilGroupBuy.REASON.min, max: UtilGroupBuy.REASON.max })" :help="t('groupBuy.rejectReasonToMember')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('groupBuy.actReject')" severity="danger" :loading="busy" :disabled="uploading" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyEvidencePhoto, GroupBuyRejectReasonCode, GroupBuyRejectRequest, GroupBuyRequestType } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import { GROUP_BUY_REJECT_REASONS } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    visible: boolean;
    type: GroupBuyRequestType;
    withdrawalRestricted?: boolean;
    upload: (file: File) => Promise<GroupBuyEvidencePhoto>;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { withdrawalRestricted: false, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: GroupBuyRejectRequest] }>();

const { t } = useI18n();
const toApiError = useApiErrorOf();
const formId = useId();
const codeId = useId();

const code = ref<GroupBuyRejectReasonCode | null>(null);
const photos = ref<GroupBuyEvidencePhoto[]>([]);
const reason = ref('');
const uploading = ref(false);
const uploadError = ref('');
const submitted = ref(false);

const codes = computed<GroupBuyRejectReasonCode[]>(() => (props.type === 'CANCEL' ? ['ALREADY_SHIPPED'] : GROUP_BUY_REJECT_REASONS.filter((c) => c !== 'ALREADY_SHIPPED')));

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        code.value = props.type === 'CANCEL' ? 'ALREADY_SHIPPED' : null;
        photos.value = [];
        reason.value = '';
        uploadError.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const codeError = computed(() => props.fieldErrors.reasonCode || (submitted.value && !code.value ? t('groupBuy.rejectCodeRequired') : ''));
const photoError = computed(() => uploadError.value || props.fieldErrors.photoIds || (submitted.value && props.type === 'RETURN' && !photos.value.length ? t('groupBuy.rejectPhotoRequired') : ''));
const reasonError = computed(() => props.fieldErrors.reason || (submitted.value && !UtilGroupBuy.reasonOk(reason.value) ? t('payment.reason.tooShort', { min: UtilGroupBuy.REASON.min }) : ''));

async function onPick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    uploading.value = true;
    uploadError.value = '';
    try {
        const photo = await props.upload(file);
        photos.value = [...photos.value, photo].slice(0, UtilGroupBuy.EVIDENCE_MAX);
    } catch (e) {
        const failure = toApiError(e);
        uploadError.value = failure.field('file') ?? failure.message;
    } finally {
        uploading.value = false;
    }
}

function submit(): void {
    submitted.value = true;
    if (props.busy || uploading.value || !code.value || !UtilGroupBuy.reasonOk(reason.value)) return;
    if (props.type === 'RETURN' && !photos.value.length) return;
    emit('submit', { reasonCode: code.value, reason: UtilGroupBuy.reasonText(reason.value), photoIds: photos.value.map((p) => p.id) });
}
</script>
