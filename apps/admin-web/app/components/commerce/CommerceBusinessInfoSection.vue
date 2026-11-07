<template>
    <!--
        커머스 설정 구획 「사업자 정보」(계약 결정 18·56, commerce.md 20절) — 상호·대표자·사업자등록번호·통신판매업 신고번호·신고 기관·주소·전화·이메일
        + 운영시간·개인정보 관리책임자·호스팅 사업자·배송/환불 정책 문구(≤ 3,000). 평문 저장. 앞의 여덟 칸이 판매 켜기 자동 조건이다(`ready`).
        회원 공개 `GET /api/commerce/info` 로 공구 화면 하단·판매 켜짐 때 공통 푸터에 나간다. 이 구획만 저장한다(구획 맨 아래 저장 줄).
        사업자 정보의 정본 = 운영 주체 설정(terms-operator-contract 세션 정리 결정): 겹치는 칸(상호 … 호스팅)은 읽기 전용으로 보이고
        「설정 › 운영·약관 › 운영 주체에서 바꿔요」 안내와 링크(`/settings/operator`)를 둔다. 여기서 저장하는 칸은 운영시간·배송/환불 안내뿐이다(요청에도 그 두 칸만).
    -->
    <AppGadget :title="t('commerce.businessSection')" anchor="commerce-business" :changes="changed.length" :invalid="invalidCount">
        <template v-if="saved" #title-extra>
            <Tag :value="saved.ready ? t('commerce.businessReady') : t('commerce.businessNotReady')" :severity="saved.ready ? 'success' : 'warn'" />
        </template>
        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton v-for="n in 4" :key="n" height="2.5rem" /></div>
        <ErrorState v-else-if="!saved" :title="t('commerce.businessLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />
        <div v-else class="flex flex-col gap-4">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('commerce.businessLead') }}</p>

            <!-- 운영 주체에서 읽는 칸 — 읽기 전용 -->
            <Message severity="info" :closable="false" size="small">
                <span class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span>{{ t('commerce.businessOperatorNote') }}</span>
                    <NuxtLink v-if="canOpenOperator" to="/settings/operator" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('commerce.businessOperatorLink') }} ›</NuxtLink>
                </span>
            </Message>
            <dl class="m-0 grid gap-x-6 gap-y-2 text-sm md:grid-cols-2" data-testid="commerce-business-operator-fields">
                <div v-for="f in OPERATOR_FIELDS" :key="f" class="flex min-w-0 flex-col gap-0.5">
                    <dt class="text-xs text-muted-color">
                        {{ t(`commerce.business.${f}`) }}<span v-if="REQUIRED.includes(f)" class="ml-1">{{ t('commerce.requiredMark') }}</span>
                    </dt>
                    <dd class="m-0 break-words" :class="saved.values[f].trim() ? '' : 'text-muted-color'">{{ saved.values[f].trim() || t('commerce.businessEmpty') }}</dd>
                </div>
            </dl>

            <!-- 커머스가 정하는 칸 -->
            <h3 class="m-0 border-t border-(--line) pt-4 text-xs font-semibold text-muted-color">{{ t('commerce.businessOwnTitle') }}</h3>
            <div class="grid gap-4 md:grid-cols-2">
                <LabeledField :label="t('commerce.business.businessHours')" :error="errorOf('businessHours')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" :model-value="form.businessHours" :maxlength="BUSINESS_HOURS_MAX" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => onInput('businessHours', v ?? '')" />
                    </template>
                </LabeledField>
                <LabeledField :label="t('commerce.business.shippingRefundPolicy')" :hint="t('commerce.textMax', { max: UtilGroupBuy.POLICY_TEXT_MAX })" :help="t('commerce.businessPolicyHelp')" :error="errorOf('shippingRefundPolicy')" class="md:col-span-2">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" :model-value="form.shippingRefundPolicy" rows="4" auto-resize :maxlength="UtilGroupBuy.POLICY_TEXT_MAX" :readonly="!editable" :invalid="invalid" :aria-describedby="describedBy" fluid @update:model-value="(v: string | undefined) => onInput('shippingRefundPolicy', v ?? '')" />
                    </template>
                </LabeledField>
            </div>
            <p class="m-0 text-xs text-muted-color">{{ saved.updatedAt ? t('commerce.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('commerce.neverSaved') }}</p>
            <SaveBar v-if="canEdit" :dirty="changed.length > 0" :count="changed.length" :saving="saving" :error-count="invalidCount" :error="formError" @save="save" @revert="applySaved(saved)" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { BusinessInfoOwnValues, BusinessInfoValues, BusinessInfoView } from '~/composables/commerce/api/useCommerceSettingsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilGroupBuy from '~/utils/util-groupbuy';

interface Props {
    canEdit?: boolean;
}

const props = withDefaults(defineProps<Props>(), { canEdit: false });
const emit = defineEmits<{ dirty: [dirty: boolean]; saved: [] }>();

type OwnField = keyof BusinessInfoOwnValues;
type OperatorField = Exclude<keyof BusinessInfoValues, OwnField>;
/** 운영 주체 설정에서 읽는 칸(읽기 전용) */
const OPERATOR_FIELDS: OperatorField[] = ['companyName', 'representative', 'businessNo', 'mailOrderNo', 'mailOrderAuthority', 'address', 'phone', 'email', 'privacyOfficer', 'hostingProvider'];
/** 판매 켜기 자동 조건 `BUSINESS_INFO` 의 필수 여덟 칸 */
const REQUIRED: OperatorField[] = ['companyName', 'representative', 'businessNo', 'mailOrderNo', 'mailOrderAuthority', 'address', 'phone', 'email'];
/** 이 구획이 저장하는 칸 */
const OWN_FIELDS: OwnField[] = ['businessHours', 'shippingRefundPolicy'];
/** 운영시간 글자 수 상한(서버와 같다) */
const BUSINESS_HOURS_MAX = 100;

const { t } = useI18n();
const api = useCommerceSettingsApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
/** 운영 주체 구획은 일반 설정(`site.read`)에 있다 — 볼 수 없으면 링크를 숨긴다 */
const canOpenOperator = computed(() => can('site.read'));

const saved = ref<BusinessInfoView | null>(null);
const form = ref<BusinessInfoOwnValues>({ businessHours: '', shippingRefundPolicy: '' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Partial<Record<OwnField, string>>>({});
const editable = computed(() => props.canEdit && !saving.value);

const changed = computed(() => (saved.value ? OWN_FIELDS.filter((f) => form.value[f] !== saved.value!.values[f]) : []));
watch(
    () => changed.value.length > 0,
    (d) => emit('dirty', d),
);

function errorOf(f: OwnField): string {
    return serverErrors.value[f] ?? '';
}

const invalidCount = computed(() => OWN_FIELDS.filter((f) => errorOf(f)).length);

function onInput(f: OwnField, value: string): void {
    form.value = { ...form.value, [f]: value };
    if (serverErrors.value[f]) {
        const { [f]: _drop, ...rest } = serverErrors.value;
        serverErrors.value = rest;
    }
}

function applySaved(view: BusinessInfoView): void {
    saved.value = view;
    form.value = { businessHours: view.values.businessHours, shippingRefundPolicy: view.values.shippingRefundPolicy };
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
    if (saving.value || !changed.value.length) return;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        // 고유 칸만 평범한 객체로 보낸다 — 겹치는 칸은 서버가 받아도 버린다
        applySaved(await api.saveBusinessInfo({ businessHours: form.value.businessHours, shippingRefundPolicy: form.value.shippingRefundPolicy }));
        toast.success(t('commerce.businessSaved'));
        emit('saved');
    } catch (e) {
        const failure = toApiError(e);
        const next: Partial<Record<OwnField, string>> = {};
        for (const f of OWN_FIELDS) {
            // 서버 열쇠는 `values.<칸>` — 서버 문구 그대로
            const message = failure.field(`values.${f}`) ?? failure.field(f);
            if (message) next[f] = message;
        }
        serverErrors.value = next;
        formError.value = Object.keys(next).length ? t('commerce.saveFieldsRejected') : failure.message;
    } finally {
        saving.value = false;
    }
}

onMounted(load);
</script>
