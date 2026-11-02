<template>
    <!--
        광고주 만들기·고치기 — 광고주 관리(`/ad-advertisers`)에서 연다. 만들기는 직접 등록 광고주만(사업자 광고주는 사업자 신청으로 생긴다).
        고치기: 연락처 칸(담당자·연락처·이메일)은 평문을 미리 채우지 않는다 — 가린 값을 자리 글자로 보이고, 비워 두면 그대로 · 새 값을 넣으면 바꾸기 · [지우기]면 저장 때 지운다.
        사업자 광고주는 이름 = 상호라 고칠 수 없고(사업자 정보가 원천), 연락처 칸은 「광고 담당자」(비우면 사업자 신원 연락처로 연락한다)다.
        서버 오류는 칸 아래(`fields.name`·`contactPhone`·`email` …), 칸이 없는 것은 아래 한 줄. 서버는 부르지 않는다 — 값은 `submit` 으로 올린다(고치기는 바뀐 칸만).
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="editing ? t('adAdvertiser.editTitle') : t('adAdvertiser.createTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-1" novalidate @submit.prevent="submit">
            <LabeledField
                :label="t('ad.advertiserName')"
                :help="isBusiness ? t('adAdvertiser.nameFromBusiness') : t('adAdvertiser.nameUnique')"
                :error="fieldErrors.name ?? (submitted && !isBusiness && !form.name.trim() ? t('ad.advertiserNameRequired') : '')"
            >
                <template #default="{ id, describedBy, invalid }"><InputText :id="id" v-model="form.name" maxlength="60" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy || isBusiness" fluid /></template>
            </LabeledField>

            <fieldset class="m-0 flex flex-col gap-1 border-0 p-0">
                <legend class="mb-1 p-0 text-[13px] font-semibold">{{ isBusiness ? t('adAdvertiser.adContact') : t('adAdvertiser.contactSection') }}</legend>
                <p class="m-0 mb-2 text-xs text-muted-color">{{ contactHelp }}</p>
                <LabeledField v-for="f in contactFields" :key="f.key" :label="f.label" :help="f.help" :error="fieldErrors[f.key] ?? ''">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText
                            :id="id"
                            v-model="form[f.key]"
                            :type="f.type"
                            :maxlength="f.max"
                            :placeholder="cleared[f.key] ? t('adAdvertiser.willClear') : (masked?.[f.key] ?? '')"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            :disabled="busy || cleared[f.key]"
                            autocomplete="off"
                            fluid
                        />
                    </template>
                    <template v-if="editing && masked?.[f.key]" #aside>
                        <Button
                            type="button"
                            :label="cleared[f.key] ? t('adAdvertiser.undoClear') : t('adAdvertiser.clear')"
                            size="small"
                            variant="text"
                            severity="secondary"
                            class="!px-1.5 !py-0 text-xs"
                            :disabled="busy"
                            @click="toggleClear(f.key)"
                        />
                    </template>
                </LabeledField>
            </fieldset>

            <LabeledField :label="t('ad.memo')" :help="t('adAdvertiser.memoHelp')" :error="fieldErrors.memo ?? ''">
                <template #default="{ id, describedBy, invalid }"><Textarea :id="id" v-model="form.memo" rows="2" auto-resize maxlength="300" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid /></template>
            </LabeledField>
            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock mt-0.5 text-xs" aria-hidden="true" />{{ t('adAdvertiser.encryptedNote') }}
            </p>
            <Message v-if="error || looseError" severity="error" size="small" variant="simple" :closable="false" role="alert" class="mt-2">{{ error || looseError }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdAdvertiserCreateBody, AdAdvertiserDetail, AdAdvertiserUpdateBody } from '~/composables/ad/api/useAdAdvertiserApi.types';

type ContactKey = 'contactName' | 'contactPhone' | 'email';

interface Props {
    visible: boolean;
    /** 고칠 광고주 — null 이면 새로 만들기 */
    advertiser?: AdAdvertiserDetail | null;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { advertiser: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; create: [body: AdAdvertiserCreateBody]; update: [body: AdAdvertiserUpdateBody] }>();

const { t } = useI18n();
const formId = useId();
const form = reactive({ name: '', contactName: '', contactPhone: '', email: '', memo: '' });
const cleared = reactive<Record<ContactKey, boolean>>({ contactName: false, contactPhone: false, email: false });
const submitted = ref(false);

const editing = computed(() => props.advertiser !== null);
const isBusiness = computed(() => props.advertiser?.kind === 'BUSINESS');
/** 가린 값은 광고 담당자 칸일 때만 자리 글자로 — 사업자 신원 연락처는 이 칸의 값이 아니다 */
const masked = computed(() => (props.advertiser?.contactSource === 'ADVERTISER' ? props.advertiser.masked : null));

const contactHelp = computed(() => {
    if (isBusiness.value) return t('adAdvertiser.adContactHelp');
    return editing.value ? t('adAdvertiser.contactKeepHelp') : t('adAdvertiser.contactCreateHelp');
});

const contactFields = computed(() => [
    { key: 'contactName' as const, label: t('ad.contactName'), help: '', type: 'text', max: 30 },
    { key: 'contactPhone' as const, label: t('ad.contactPhone'), help: t('ad.contactPhoneHelp'), type: 'tel', max: 20 },
    { key: 'email' as const, label: t('ad.contactEmail'), help: '', type: 'email', max: 120 },
]);

/** 칸이 있는 서버 오류 키 — 나머지는 아래 한 줄 */
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
        const a = props.advertiser;
        Object.assign(form, { name: a?.name ?? '', contactName: '', contactPhone: '', email: '', memo: a?.memo ?? '' });
        Object.assign(cleared, { contactName: false, contactPhone: false, email: false });
        submitted.value = false;
    },
);

function toggleClear(key: ContactKey): void {
    cleared[key] = !cleared[key];
    if (cleared[key]) form[key] = '';
}

function submit(): void {
    submitted.value = true;
    if (props.busy) return;
    const a = props.advertiser;
    if (!a) {
        if (!form.name.trim()) return;
        emit('create', { ...toRaw(form), name: form.name.trim() });
        return;
    }
    if (!isBusiness.value && !form.name.trim()) return;
    const body: AdAdvertiserUpdateBody = {};
    if (!isBusiness.value && form.name.trim() !== a.name) body.name = form.name.trim();
    for (const key of ['contactName', 'contactPhone', 'email'] as const) {
        if (cleared[key]) body[key] = '';
        else if (form[key].trim()) body[key] = form[key].trim();
    }
    if (form.memo.trim() !== (a.memo ?? '')) body.memo = form.memo.trim();
    emit('update', body);
}
</script>
