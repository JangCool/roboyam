<template>
    <!--
        택배사 + 송장번호 한 건 입력(사유 없음) — 반품 요청의 업체회수 송장 · 교환 재발송이 같이 쓴다. 송장번호 모양(숫자·영문 6~30자)은 미리 보고 판정은 서버다.
        서버는 부르지 않는다 — `submit` 으로 올린다. 서버 칸 오류는 `fieldErrors`(`carrierCode`·`trackingNo`), 칸에 붙지 않는 오류는 `error`.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>
            <LabeledField :label="t('groupBuy.carrier')" :error="carrierError">
                <template #default="{ id, describedBy, invalid }">
                    <Select :input-id="id" v-model="carrier" :options="carriers" option-label="name" option-value="code" :placeholder="t('groupBuy.carrierPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <LabeledField :label="t('groupBuy.trackingNo')" :error="trackingError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="tracking" maxlength="40" autocomplete="off" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="confirmLabel" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyCarrierChoice } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    confirmLabel: string;
    carriers: GroupBuyCarrierChoice[];
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { description: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: { carrierCode: string; trackingNo: string }] }>();

const { t } = useI18n();
const formId = useId();
const carrier = ref('');
const tracking = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        carrier.value = '';
        tracking.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const carrierError = computed(() => props.fieldErrors.carrierCode || (submitted.value && !carrier.value ? t('groupBuy.carrierRequired') : ''));
const trackingError = computed(() => props.fieldErrors.trackingNo || (submitted.value && !UtilGroupBuy.trackingOk(tracking.value) ? t('groupBuy.trackingInvalid') : ''));

function submit(): void {
    submitted.value = true;
    if (props.busy || !carrier.value || !UtilGroupBuy.trackingOk(tracking.value)) return;
    emit('submit', { carrierCode: carrier.value, trackingNo: UtilGroupBuy.trackingText(tracking.value) });
}
</script>
