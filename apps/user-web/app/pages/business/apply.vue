<template>
    <!--
        사업자 신청/수정 `/business/apply` — Stepper 3단계(01 §7-2 G): ① 사업자 정보 ② 사업자등록증 사본(필수 — B2) + 국세청 휴폐업 조회 안내(B1) ③ 약관 동의 + 요약 → 신청.
        미신청·반려 = 새 신청(반려면 가리지 않은 칸을 채워 둔다) · 승인 = 수정(번호·연락처는 비워 두면 그대로 — 가린 값을 자리표시로) · 심사 중·정지 = 고칠 수 없음 안내.
        수정에서 상호·번호를 바꾸면 「저장하면 다시 심사받아요」 경고 + 저장 전 확인 창(F7). 모바일은 단계당 한 화면 + 하단 고정 [다음](`BusinessStepNav`).
    -->
    <div class="mx-auto flex max-w-2xl flex-col gap-4" data-testid="business-apply">
        <div class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.CENTER_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.home.title')" />
            <h1 class="m-0 text-lg font-bold">{{ editMode ? t('business.apply.editTitle') : t('business.apply.title') }}</h1>
        </div>

        <div v-if="status === 'loading'" class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <ErrorState v-else-if="status === 'error' || !profile" :title="t('business.home.loadFailed')" :description="loadError" retry @retry="load" />
        <div v-else-if="profile.status === 'PENDING' || profile.status === 'SUSPENDED'" class="panel-card flex flex-col items-center gap-3 px-5 py-10 text-center">
            <p class="m-0 text-sm">{{ profile.status === 'PENDING' ? t('business.apply.blockedPending') : t('business.apply.blockedSuspended') }}</p>
            <Button as="router-link" :to="UtilBusiness.CENTER_PATH" :label="t('business.gate.toCenter')" severity="secondary" variant="outlined" size="small" />
        </div>

        <template v-else>
            <BusinessStepHeader :steps="stepNames" :current="step" />

            <form class="panel-card flex flex-col gap-4 p-4" novalidate @submit.prevent="next">
                <!-- ① 사업자 정보 -->
                <template v-if="step === 1">
                    <Message v-if="identityChanged" severity="warn" size="small" :closable="false" data-testid="business-apply-rereview-warn">{{ t('business.apply.reReviewWarn') }}</Message>
                    <LabeledField :label="t('business.field.companyName')" :error="errors.companyName">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText v-model="form.companyName" :id="id" :maxlength="60" autocomplete="organization" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('business.field.bizNo')" :error="errors.bizNo" :success="bizNoSuccess" :help="editMode ? t('business.apply.keepHint', { masked: profile.bizNoMasked ?? '' }) : ''">
                        <template #default="{ id, describedBy, invalid }">
                            <InputMask
                                v-model="form.bizNo"
                                :id="id"
                                mask="999-99-99999"
                                :auto-clear="false"
                                :placeholder="editMode ? (profile.bizNoMasked ?? '') : '000-00-00000'"
                                inputmode="numeric"
                                :invalid="invalid"
                                :aria-describedby="describedBy"
                                fluid
                                data-testid="business-apply-bizno"
                            />
                        </template>
                    </LabeledField>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField :label="t('business.field.ownerName')" :error="errors.ownerName">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.ownerName" :id="id" :maxlength="30" autocomplete="name" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                        <LabeledField :label="t('business.field.phone')" :error="errors.phone" :help="editMode ? t('business.apply.keepHint', { masked: profile.phoneMasked ?? '' }) : ''">
                            <template #default="{ id, describedBy, invalid }">
                                <InputText v-model="form.phone" :id="id" type="tel" inputmode="tel" :maxlength="13" autocomplete="tel" :placeholder="editMode ? (profile.phoneMasked ?? '') : '010-1234-5678'" :invalid="invalid" :aria-describedby="describedBy" fluid />
                            </template>
                        </LabeledField>
                    </div>
                    <LabeledField :label="t('business.field.email')" :error="errors.email">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText v-model="form.email" :id="id" type="email" :maxlength="120" autocomplete="email" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                    <AddressInput v-model="form.address" :legend="t('business.field.address')" :errors="addressErrors" :manual-first="Boolean(form.address.address1)" />
                </template>

                <!-- ② 증빙 첨부 -->
                <template v-else-if="step === 2">
                    <LabeledField :label="t('business.field.certificate')" :help="t('business.apply.certificateHelp')" :error="errors.certificate">
                        <template #default="{ id, describedBy }">
                            <BusinessFilePicker
                                :file="certificate"
                                :accept="UtilBusiness.CERTIFICATE_ACCEPT"
                                :choose-label="t('business.file.choose')"
                                :busy="uploadBusy"
                                :input-id="id"
                                :described-by="describedBy"
                                :type-error="t('business.file.typeError')"
                                :size-error="t('business.file.sizeError')"
                                @select="uploadCertificate"
                                @remove="certificate = null"
                            />
                        </template>
                    </LabeledField>
                    <Message severity="info" size="small" :closable="false">
                        <span class="flex flex-col gap-1">
                            <span class="font-semibold">{{ t('business.apply.ntsNotice') }}</span>
                            <span>{{ t('business.apply.retentionNotice') }}</span>
                        </span>
                    </Message>
                </template>

                <!-- ③ 동의·확인 -->
                <template v-else>
                    <section :aria-labelledby="`${uid}-summary`" class="rounded-lg border border-hairline p-3.5">
                        <h2 :id="`${uid}-summary`" class="m-0 mb-2 text-sm font-semibold">{{ t('business.apply.summaryTitle') }}</h2>
                        <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                            <template v-for="row in summaryRows" :key="row.label">
                                <dt class="text-muted-color">{{ row.label }}</dt>
                                <dd class="m-0 break-all">{{ row.value }}</dd>
                            </template>
                        </dl>
                    </section>
                    <BusinessAgreeList v-model="agreed" :items="agreeItems" :error="errors.agree" />
                    <Message v-if="identityChanged" severity="warn" size="small" :closable="false">{{ t('business.apply.reReviewWarn') }}</Message>
                </template>

                <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ formError }}</Message>

                <BusinessStepNav
                    :step="step"
                    :total="TOTAL"
                    :can-prev="step > 1"
                    :next-label="step === TOTAL ? (editMode ? t('business.apply.save') : t('business.apply.submit')) : ''"
                    :busy="submitBusy"
                    @prev="step--"
                    @next="next"
                />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AddressFieldErrors, AddressValue } from '~/components/address/AddressInput.types';
import type { BusinessAgreeItem } from '~/components/business/BusinessAgreeList.types';
import type { BusinessApplyRequest, BusinessFile, BusinessProfile } from '~/composables/business/api/useBusinessApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

/**
 * 사업자 신청/수정 `/business/apply` — 로그인 필요. 사업자 센터 홈의 [사업자 신청]·[다시 신청]·[정보 수정]이 온다.
 * 판정은 서버다(번호 체크섬·중복 409 `BUSINESS_NUMBER_TAKEN`·국세청 조회는 운영 심사 때). ⚠ 목업 단계(`useBusinessApi`).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.apply.title'), robots: 'noindex' });

const TOTAL = 3;
const TERMS_VERSION = '2026-10-30';
const PRIVACY_VERSION = '2026-10-30';

const uid = useId();
const api = useBusinessApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();

const profile = ref<BusinessProfile | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const step = ref(1);

const form = reactive({
    companyName: '',
    bizNo: '',
    ownerName: '',
    phone: '',
    email: '',
    address: { postcode: '', address1: '', address2: '' } as AddressValue,
});
const certificate = ref<Pick<BusinessFile, 'fileId' | 'name' | 'size' | 'previewUrl'> | null>(null);
const uploadBusy = ref(false);
const agreed = ref<Record<string, boolean>>({});
const tried = ref<Record<number, boolean>>({});
const serverFields = ref<Record<string, string>>({});
const formError = ref('');
const submitBusy = ref(false);

/** 승인된 사업자의 정보 수정인가 */
const editMode = computed(() => profile.value?.status === 'APPROVED');

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const p = await api.profile();
        profile.value = p;
        // 반려·승인이면 가리지 않은 칸을 채워 둔다(번호·연락처는 평문이 없어 비운다)
        if (p.status === 'APPROVED' || p.status === 'REJECTED') {
            form.companyName = p.companyName ?? '';
            form.ownerName = p.ownerName ?? '';
            form.email = p.email ?? '';
            if (p.address) form.address = { ...p.address };
            if (p.status === 'APPROVED' && p.certificate) certificate.value = { ...p.certificate, previewUrl: null };
        }
        status.value = 'ready';
    } catch (e) {
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}
onMounted(load);

const stepNames = computed(() => [t('business.apply.steps.info'), t('business.apply.steps.document'), t('business.apply.steps.agree')]);

const bizNoDigits = computed(() => UtilBusiness.digits(form.bizNo));
/** 체크섬 즉시 검사 — 10자리를 다 넣은 순간 맞다/틀리다를 보인다 */
const bizNoSuccess = computed(() => (bizNoDigits.value.length === 10 && UtilBusiness.isValidBizNo(bizNoDigits.value) ? t('business.apply.bizNoValid') : ''));

/** 수정에서 상호·번호를 바꿨나(F7 — 다시 심사) */
const identityChanged = computed(() => editMode.value && (form.companyName.trim() !== (profile.value?.companyName ?? '') || bizNoDigits.value.length > 0));

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** 칸 오류 — 그 단계에서 [다음]을 한 번 누른 뒤부터 보인다. 번호 체크섬은 10자리를 넣으면 바로 */
const errors = computed(() => {
    const out: Record<string, string> = { ...serverFields.value };
    const s1 = tried.value[1];
    if (s1 && !form.companyName.trim()) out.companyName = t('business.apply.companyRequired');
    if (bizNoDigits.value.length === 10 && !UtilBusiness.isValidBizNo(bizNoDigits.value)) out.bizNo = t('business.apply.bizNoInvalid');
    else if (s1 && !editMode.value && bizNoDigits.value.length === 0) out.bizNo = t('business.apply.bizNoRequired');
    else if (s1 && bizNoDigits.value.length > 0 && bizNoDigits.value.length < 10) out.bizNo = t('business.apply.bizNoInvalid');
    if (s1 && !form.ownerName.trim()) out.ownerName = t('business.apply.ownerRequired');
    const phone = UtilBusiness.digits(form.phone);
    if (s1 && !editMode.value && !phone) out.phone = t('business.apply.phoneRequired');
    else if (s1 && phone && (phone.length < 9 || phone.length > 11)) out.phone = t('business.apply.phoneInvalid');
    if (s1 && !form.email.trim()) out.email = t('business.apply.emailRequired');
    else if (s1 && !EMAIL_RE.test(form.email.trim())) out.email = t('business.apply.emailInvalid');
    if (tried.value[2] && !certificate.value) out.certificate = t('business.apply.certificateRequired');
    if (tried.value[3] && !agreeItems.value.every((i) => agreed.value[i.key])) out.agree = t('business.apply.agreeRequired');
    return out;
});

const addressErrors = computed<AddressFieldErrors>(() => {
    if (!tried.value[1]) return {};
    const out: AddressFieldErrors = {};
    if (!form.address.postcode || !form.address.address1) out.address1 = t('business.apply.addressRequired');
    return out;
});

const STEP_FIELDS: Record<number, string[]> = { 1: ['companyName', 'bizNo', 'ownerName', 'phone', 'email'], 2: ['certificate'], 3: ['agree'] };

function stepValid(n: number): boolean {
    if (STEP_FIELDS[n]!.some((k) => errors.value[k])) return false;
    if (n === 1 && Object.keys(addressErrors.value).length) return false;
    return true;
}

const agreeItems = computed<BusinessAgreeItem[]>(() => [
    { key: 'terms', label: t('business.apply.agreeTerms'), detail: t('business.apply.agreeTermsDetail'), version: TERMS_VERSION },
    { key: 'privacy', label: t('business.apply.agreePrivacy'), detail: t('business.apply.agreePrivacyDetail'), version: PRIVACY_VERSION },
]);

const summaryRows = computed(() => [
    { label: t('business.field.companyName'), value: form.companyName },
    { label: t('business.field.bizNo'), value: bizNoDigits.value ? form.bizNo : (profile.value?.bizNoMasked ?? '') },
    { label: t('business.field.ownerName'), value: form.ownerName },
    { label: t('business.field.phone'), value: form.phone || (profile.value?.phoneMasked ?? '') },
    { label: t('business.field.email'), value: form.email },
    { label: t('business.field.address'), value: `(${form.address.postcode}) ${form.address.address1} ${form.address.address2}`.trim() },
    { label: t('business.field.certificate'), value: certificate.value ? `${certificate.value.name} · ${UtilBusinessMessages.fileSize(certificate.value.size)}` : '' },
]);

async function uploadCertificate(file: File): Promise<void> {
    uploadBusy.value = true;
    formError.value = '';
    try {
        certificate.value = await api.uploadFile(file);
        serverFields.value = { ...serverFields.value, certificate: '' };
    } catch (e) {
        formError.value = e instanceof Error ? e.message : t('common.failed');
    } finally {
        uploadBusy.value = false;
    }
}

async function next(): Promise<void> {
    if (submitBusy.value) return;
    tried.value = { ...tried.value, [step.value]: true };
    if (!stepValid(step.value)) return;
    if (step.value < TOTAL) {
        step.value++;
        return;
    }
    await submit();
}

async function submit(): Promise<void> {
    if (!profile.value || !certificate.value) return;
    if (identityChanged.value) {
        const ok = await confirm({ title: t('business.apply.reReviewConfirmTitle'), message: t('business.apply.reReviewConfirmMessage'), confirmLabel: t('business.apply.reReviewConfirm') });
        if (!ok) return;
    }
    const body: BusinessApplyRequest = {
        companyName: form.companyName.trim(),
        bizNo: bizNoDigits.value || null,
        ownerName: form.ownerName.trim(),
        phone: UtilBusiness.digits(form.phone) || null,
        email: form.email.trim(),
        address: { ...toRaw(form.address) },
        certificateFileId: certificate.value.fileId,
        agreedTermsVersion: TERMS_VERSION,
        agreedPrivacyVersion: PRIVACY_VERSION,
    };
    const wasReReview = identityChanged.value;
    submitBusy.value = true;
    formError.value = '';
    serverFields.value = {};
    try {
        if (editMode.value) await api.update(body);
        else await api.apply(body);
        toast.success(editMode.value ? (wasReReview ? t('business.apply.reReviewSaved') : t('business.apply.saved')) : t('business.apply.submitted'));
        await navigateTo(UtilBusiness.CENTER_PATH);
    } catch (e) {
        if (e instanceof ApiError && e.fields) {
            serverFields.value = { ...e.fields };
            step.value = 1;
        } else {
            formError.value = e instanceof Error ? e.message : t('business.apply.failed');
        }
    } finally {
        submitBusy.value = false;
    }
}
</script>
