<template>
    <!--
        커머스 설정 구획 「사업자 정보」(계약 결정 18·56, commerce.md 20절) — 상호·대표자·사업자등록번호(체크섬은 화면 편의 — 판정은 서버)·통신판매업 신고번호·신고 기관·주소·전화·이메일
        + 운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000). 평문 저장. 앞의 여덟 칸이 판매 켜기 자동 조건이다(`ready`).
        회원 공개 `GET /api/commerce/info` 로 공구 화면 하단·판매 켜짐 때 공통 푸터에 나간다. 이 구획만 저장한다(구획 맨 아래 저장 줄).
    -->
    <AppGadget :title="t('commerce.businessSection')" anchor="commerce-business" :changes="changed.length" :invalid="problemCount">
        <template v-if="saved" #title-extra>
            <Tag :value="saved.ready ? t('commerce.businessReady') : t('commerce.businessNotReady')" :severity="saved.ready ? 'success' : 'warn'" />
        </template>
        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!saved" :title="t('commerce.businessLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.businessLead') }}</p>
            <div class="grid gap-4 md:grid-cols-2">
                <LabeledField v-for="f in SHORT_FIELDS" :key="f" :label="t(`commerce.business.${f}`)" :hint="REQUIRED.includes(f) ? t('commerce.requiredMark') : ''" :error="errorOf(f)">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" :model-value="form[f]" :maxlength="f === 'address' ? 200 : 100" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" :inputmode="f === 'businessNo' || f === 'phone' ? 'numeric' : undefined" fluid @update:model-value="(v: string | undefined) => onInput(f, v ?? '')" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('commerce.business.shippingRefundPolicy')" :hint="t('commerce.textMax', { max: UtilGroupBuy.POLICY_TEXT_MAX })" :help="t('commerce.businessPolicyHelp')" :error="errorOf('shippingRefundPolicy')" class="md:col-span-2">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" :model-value="form.shippingRefundPolicy" rows="4" auto-resize :maxlength="UtilGroupBuy.POLICY_TEXT_MAX" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => onInput('shippingRefundPolicy', v ?? '')" />
                    </template>
                </LabeledField>
            </div>
            <p class="m-0 text-xs text-muted-color">{{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}</p>
            <SaveBar v-if="canEdit" :dirty="changed.length > 0" :count="changed.length" :saving="saving" :disabled="problemCount > 0" :error-count="problemCount" :error="formError" @save="save" @revert="applySaved(saved)" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { BusinessInfoValues, BusinessInfoView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean]; saved: [] }>();

type Field = keyof BusinessInfoValues;
const SHORT_FIELDS: Exclude<Field, 'shippingRefundPolicy'>[] = ['companyName', 'representative', 'businessNo', 'mailOrderNo', 'mailOrderAuthority', 'address', 'phone', 'email', 'businessHours', 'privacyOfficer', 'hostingProvider'];
const REQUIRED: Field[] = ['companyName', 'representative', 'businessNo', 'mailOrderNo', 'mailOrderAuthority', 'address', 'phone', 'email'];
const ALL: Field[] = [...SHORT_FIELDS, 'shippingRefundPolicy'];

const { t } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();

const saved = ref<BusinessInfoView | null>(null);
const form = ref<BusinessInfoValues>({ companyName: '', representative: '', businessNo: '', mailOrderNo: '', mailOrderAuthority: '', address: '', phone: '', email: '', businessHours: '', privacyOfficer: '', hostingProvider: '', shippingRefundPolicy: '' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});
const editable = computed(() => props.canEdit && !saving.value);

const changed = computed(() => (saved.value ? ALL.filter((f) => form.value[f] !== saved.value!.values[f]) : []));
watch(
    () => changed.value.length > 0,
    (d) => emit('dirty', d),
);

/** 화면이 미리 알리는 문제 — 사업자등록번호 체크섬(국세청 규칙)·이메일 모양. 빈 필수 칸은 막지 않는다(판매 켜기 조건으로 서버가 본다) */
function localProblem(f: Field): string {
    const v = form.value[f].trim();
    if (f === 'businessNo' && v && !UtilGroupBuy.businessNoOk(v)) return t('commerce.businessNoProblem');
    if (f === 'email' && v && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return t('commerce.emailProblem');
    return '';
}

function errorOf(f: Field): string {
    return serverErrors.value[f] ?? localProblem(f);
}

const problemCount = computed(() => ALL.filter((f) => localProblem(f)).length);

function onInput(f: Field, value: string): void {
    form.value = { ...form.value, [f]: f === 'businessNo' ? UtilGroupBuy.businessNoFormat(value) : value };
    if (serverErrors.value[f]) {
        const { [f]: _drop, ...rest } = serverErrors.value;
        serverErrors.value = rest;
    }
}

function applySaved(view: BusinessInfoView): void {
    saved.value = view;
    form.value = { ...view.values };
    serverErrors.value = {};
    formError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.businessInfo());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

async function save(): Promise<void> {
    if (saving.value || !changed.value.length || problemCount.value) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.saveBusinessInfo({ ...form.value }));
        toast.success(t('commerce.businessSaved'));
        emit('saved');
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            serverErrors.value = failure.fields;
            formError.value = t('commerce.saveFieldsRejected');
        } else formError.value = failure.message;
    } finally {
        saving.value = false;
    }
}

onMounted(load);
</script>
