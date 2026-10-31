<template>
    <!--
        새 광고주(계정 없는 광고주) 만들기 — 광고 수동 등록에서 연다(02-ad.md 7-5). 이름 · 담당자 · 연락처 · 이메일 · 메모.
        연락처는 저장 뒤 가린 값으로만 보인다(평문 열람은 `ad.contact.reveal`). 서버 오류는 칸 아래(`fields.name`·`contactPhone`·`email` …), 칸이 없는 것은 아래 한 줄.
        서버는 부르지 않는다 — 값은 `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('ad.newAdvertiser')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-1" novalidate @submit.prevent="submit">
            <LabeledField :label="t('ad.advertiserName')" :error="fieldErrors.name ?? (submitted && !form.name.trim() ? t('ad.advertiserNameRequired') : '')">
                <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="form.name" maxlength="60" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
            </LabeledField>
            <LabeledField :label="t('ad.contactName')" :error="fieldErrors.contactName ?? ''">
                <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="form.contactName" maxlength="30" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
            </LabeledField>
            <div class="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
                <LabeledField :label="t('ad.contactPhone')" :help="t('ad.contactPhoneHelp')" :error="fieldErrors.contactPhone ?? ''">
                    <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="form.contactPhone" type="tel" maxlength="20" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
                </LabeledField>
                <LabeledField :label="t('ad.contactEmail')" :error="fieldErrors.email ?? ''">
                    <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="form.email" type="email" maxlength="120" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
                </LabeledField>
            </div>
            <LabeledField :label="t('ad.memo')" :error="fieldErrors.memo ?? ''">
                <template #default="{ id, describedBy, invalid }"><Textarea :id="id" v-model="form.memo" rows="2" auto-resize maxlength="300" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
            </LabeledField>
            <Message v-if="error || looseError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error || looseError }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdAdvertiserCreateRequest } from '~/composables/ad/api/useAdApi.types';

interface Props {
    visible: boolean;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: AdAdvertiserCreateRequest] }>();

const { t } = useI18n();
const formId = useId();
const form = reactive<AdAdvertiserCreateRequest>({ name: '', contactName: '', contactPhone: '', email: '', memo: '' });
const submitted = ref(false);

/** 칸이 있는 서버 오류 키 — 나머지(사업자 연결 `fields.businessId` 등)는 아래 한 줄 */
const FORM_FIELDS: readonly string[] = ['name', 'contactName', 'contactPhone', 'email', 'memo'];
const looseError = computed(() =>
    Object.entries(props.fieldErrors)
        .filter(([k]) => !FORM_FIELDS.includes(k))
        .map(([, v]) => v)
        .join(' · '),
);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        Object.assign(form, { name: '', contactName: '', contactPhone: '', email: '', memo: '' });
        submitted.value = false;
    },
);

function submit(): void {
    submitted.value = true;
    if (props.busy || !form.name.trim()) return;
    emit('submit', { ...toRaw(form), name: form.name.trim() });
}
</script>
