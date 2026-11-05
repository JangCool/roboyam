<template>
    <div class="content-card mx-auto flex w-full max-w-2xl flex-col gap-4">
        <NuxtLink :to="UtilEmoticon.STUDIO_PATH" class="inline-flex w-fit items-center gap-1 rounded text-sm text-muted-color hover:underline focus-visible:outline-2 focus-visible:outline-primary">
            <i class="pi pi-angle-left text-xs" aria-hidden="true" />{{ t('emoticon.studioTitle') }}
        </NuxtLink>
        <h1 class="m-0 text-lg font-bold">{{ t('emoticon.payoutTitle') }}</h1>
        <p class="m-0 -mt-2 text-sm leading-relaxed text-muted-color">{{ t('emoticon.payoutIntro') }}</p>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('emoticon.loading')">
            <div class="h-20 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error' || !account || !rules" :title="t('emoticon.payoutLoadFailed')" :description="loadError" retry @retry="load" />

        <template v-else>
            <!-- 본인인증 — 정산 정보 등록의 조건(예금주 = 본인 이름) -->
            <section class="flex flex-wrap items-center gap-3 panel-card p-4" :aria-labelledby="`${uid}-identity`" data-testid="emoticon-payout-identity">
                <i
                    class="pi grid size-9 shrink-0 place-items-center rounded-full"
                    :class="verified ? 'pi-verified bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300' : 'pi-id-card bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-200'"
                    aria-hidden="true"
                />
                <div class="min-w-0 flex-1">
                    <h2 :id="`${uid}-identity`" class="m-0 text-sm font-semibold">{{ t('emoticon.payoutIdentity') }}</h2>
                    <p class="m-0 mt-0.5 text-xs text-muted-color">{{ verified ? t('emoticon.payoutIdentityDone') : t('emoticon.payoutIdentityNeed') }}</p>
                </div>
                <Button v-if="!verified" as="router-link" to="/settings/identity" :label="t('emoticon.payoutIdentityGo')" size="small" />
                <!-- 만 19세 미만(본인인증 `adult: false` 또는 저장의 `403 IDENTITY_UNDERAGE`) — 정산 정보 등록을 막는다. 무료 팩은 만들 수 있다(후속 결정 3) -->
                <div v-if="underage" role="status" class="flex w-full flex-col gap-0.5 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="emoticon-payout-underage">
                    <span class="font-semibold">{{ underageText }}</span>
                    <span>{{ UtilEmoticonMessages.underageRelease(t, underageFrom) }}</span>
                </div>
                <div v-else-if="identityNameMissing" role="status" class="flex w-full flex-wrap items-center gap-2 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="emoticon-payout-name-missing">
                    <span class="min-w-0 flex-1">{{ t('emoticon.payoutIdentityNameMissing') }}</span>
                    <Button type="button" :label="t('emoticon.payoutReverifyButton')" icon="pi pi-mobile" size="small" severity="warn" variant="outlined" :loading="reverifying" data-testid="emoticon-payout-reverify" @click="reverify" />
                </div>
                <p v-else-if="reverified" role="status" class="m-0 w-full text-xs text-green-700 dark:text-green-400" data-testid="emoticon-payout-reverified">
                    <i class="pi pi-check-circle mr-1 text-[0.6875rem]" aria-hidden="true" />{{ t('emoticon.payoutReverified') }}
                </p>
            </section>

            <!-- 등록된 정보 — 가린 값만 -->
            <section v-if="account.registered && !editing" class="panel-card p-4" :aria-labelledby="`${uid}-saved`" data-testid="emoticon-payout-saved">
                <div class="flex items-center justify-between gap-2">
                    <h2 :id="`${uid}-saved`" class="m-0 text-sm font-semibold">{{ t('emoticon.payoutSaved') }}</h2>
                    <Button type="button" :label="t('common.edit')" size="small" severity="secondary" variant="outlined" :disabled="!editable" @click="startEdit" />
                </div>
                <dl class="m-0 mt-3 grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                    <dt class="text-muted-color">{{ t('emoticon.payoutType') }}</dt>
                    <dd class="m-0">{{ account.type === 'BUSINESS' ? t('emoticon.payoutBusiness') : t('emoticon.payoutIndividual') }}</dd>
                    <template v-if="account.type === 'BUSINESS'">
                        <dt class="text-muted-color">{{ t('emoticon.payoutBusinessNumber') }}</dt>
                        <dd class="m-0 tabular-nums">{{ account.businessNumberMasked }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('emoticon.payoutAccount') }}</dt>
                    <dd class="m-0 tabular-nums" data-testid="emoticon-payout-masked">{{ bankName(account.bankCode) }} {{ account.accountMasked }}</dd>
                    <dt class="text-muted-color">{{ t('emoticon.payoutHolder') }}</dt>
                    <dd class="m-0">{{ account.holderMasked }}</dd>
                    <template v-if="account.updatedAt">
                        <dt class="text-muted-color">{{ t('emoticon.payoutUpdatedAt') }}</dt>
                        <dd class="m-0">{{ time.listLabel(account.updatedAt) }}</dd>
                    </template>
                </dl>
                <p class="m-0 mt-3 text-xs leading-relaxed text-muted-color">{{ taxNote(account.type ?? 'INDIVIDUAL') }}</p>
            </section>

            <!-- 등록·고치기 -->
            <form v-else class="flex flex-col gap-4 panel-card p-4" novalidate data-testid="emoticon-payout-form" @submit.prevent="save">
                <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('emoticon.payoutType') }}</legend>
                    <div class="flex flex-wrap gap-4">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="form.type" :input-id="`${uid}-ind`" value="INDIVIDUAL" :disabled="!editable" />
                            <label :for="`${uid}-ind`" class="text-sm">{{ t('emoticon.payoutIndividual') }}</label>
                        </div>
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="form.type" :input-id="`${uid}-biz`" value="BUSINESS" :disabled="!editable" />
                            <label :for="`${uid}-biz`" class="text-sm">{{ t('emoticon.payoutBusiness') }}</label>
                        </div>
                    </div>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ taxNote(form.type) }}</p>
                    <p v-if="fieldErrors.type" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldErrors.type }}</p>
                </fieldset>

                <LabeledField v-if="form.type === 'BUSINESS'" :label="t('emoticon.payoutBusinessNumber')" :hint="t('emoticon.payoutBusinessNumberHint')" :error="fieldErrors.businessNumber ?? ''">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText :id="fieldId" v-model="form.businessNumber" inputmode="numeric" autocomplete="off" maxlength="12" placeholder="000-00-00000" :invalid="invalid" :aria-describedby="describedBy" :disabled="!editable" fluid />
                    </template>
                </LabeledField>

                <LabeledField :label="t('emoticon.payoutBank')" :error="fieldErrors.bankCode ?? ''">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <Select :input-id="fieldId" v-model="form.bankCode" :options="bankOptions" option-label="label" option-value="value" :placeholder="t('emoticon.payoutBankPick')" :invalid="invalid" :aria-describedby="describedBy" :disabled="!editable" fluid />
                    </template>
                </LabeledField>

                <LabeledField :label="t('emoticon.payoutAccountNumber')" :hint="t('emoticon.payoutAccountHint')" :error="fieldErrors.accountNumber ?? ''">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText :id="fieldId" v-model="form.accountNumber" inputmode="numeric" autocomplete="off" maxlength="20" :invalid="invalid" :aria-describedby="describedBy" :disabled="!editable" fluid />
                    </template>
                </LabeledField>

                <LabeledField
                    :label="t('emoticon.payoutHolder')"
                    :help="account.identityNameMasked ? t('emoticon.payoutHolderHelp', { name: account.identityNameMasked }) : t('emoticon.payoutHolderHelpPlain')"
                    :error="fieldErrors.holderName ?? ''"
                >
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText :id="fieldId" v-model="form.holderName" autocomplete="off" maxlength="30" :invalid="invalid" :aria-describedby="describedBy" :disabled="!editable" fluid />
                    </template>
                </LabeledField>

                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.payoutPrivacy') }}</p>
                <Message v-if="formError" severity="error" variant="simple" size="small" role="alert">{{ formError }}</Message>

                <div class="flex justify-end gap-2 border-t border-surface pt-3">
                    <Button v-if="account.registered" type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="cancelEdit" />
                    <Button type="submit" :label="t('common.save')" :loading="saving" :disabled="!editable" data-testid="emoticon-payout-save" />
                </div>
            </form>

            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('emoticon.settlementRule', ruleArgs) }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonPayoutAccount, EmoticonPayoutType } from '~/composables/emoticon/api/useEmoticonStudioApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilEmoticon from '~/utils/util-emoticon';
import UtilEmoticonMessages from '~/utils/util-emoticon-messages';

/**
 * 정산 정보 `/robotikon/studio/payout` — 로그인 필요. 유료 팩을 신청하려면 먼저 등록한다(계약 가정 5).
 * 본인인증 상태(미인증이면 `/settings/identity` 로 — 칸이 잠긴다) · 개인/사업자(사업자 번호) · 은행·계좌·예금주(본인인증 이름과 같아야 한다 — 판정은 서버).
 * 저장하면 가린 값만 보인다(`국민 ****1234`). 계좌 번호 원문은 이 화면 메모리에만 두고 저장 뒤 비운다(forms.md 2-1). 개인은 「원천징수 3.3% 뒤 지급」, 사업자는 「세금계산서 발행」 안내.
 * 실제 API(S2 — `GET·PUT /api/me/emoticon-studio/payout-account`). 저장 오류는 `applySaveError`(본인인증 403 → 인증 이름 없음 409 → 칸 400 → 데이터 키 503).
 * 인증했는데 가린 인증 이름(`identityNameMasked`)이 오지 않으면 인증 이름이 남아 있지 않은 것이라(V143 전 인증) 저장 전에 안내하고 [본인인증 다시 하기]를 둔다.
 * 저장이 `409 EMOTICON_IDENTITY_NAME_MISSING` 이면(사용자 결정 2026-11-05) 입력값을 그대로 두고 본인인증 대화상자(`useIdentityGate` purpose `ROBOTIKON`, `reverify`)를 연다 —
 * 인증이 끝나면 [저장]을 다시 누르면 된다. 판단은 오류 코드만으로 한다(서버가 `extras.reverify` 같은 표시를 더해도 보지 않는다).
 * 만 19세 미만 작가(후속 결정 3): 본인인증 상태의 `adult` 가 false 면 입력·저장을 미리 잠그고 안내(+ 19세 생일)를 보인다. 저장이 `403 IDENTITY_UNDERAGE`(+ 최상위 `adultFrom`)면
 * 서버 문구로 같은 안내를 띄운다. 무료 팩은 그대로 만들 수 있다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('emoticon.payoutTitle'), robots: 'noindex' });

const uid = useId();
const api = useEmoticonStudioApi();
const publicApi = useEmoticonApi();
const identity = useIdentity();
const gate = useIdentityGate();
const toast = useAppToast();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();

const account = ref<EmoticonPayoutAccount | null>(null);
const rules = ref<EmoticonRules | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const editing = ref(false);

const verified = computed(() => identity.status.value?.verified === true);
/** 서버가 저장을 `403 IDENTITY_UNDERAGE` 로 막았다 — 그 문구와 최상위 `adultFrom`(화면이 아는 인증 상태가 낡았어도 서버를 따른다) */
const underageError = ref<{ message: string; adultFrom: string | null } | null>(null);
/** 만 19세 미만 작가(후속 결정 3) — 본인인증 상태의 `adult: false` 또는 서버 거절. 정산 정보를 등록할 수 없다(무료 팩은 된다) */
const underage = computed(() => underageError.value !== null || (verified.value && identity.status.value?.adult === false));
/** 안내 첫 줄 — 서버 문구가 있으면 그것, 아니면 화면 문구 */
const underageText = computed(() => underageError.value?.message || t('emoticon.underageCreator'));
const underageFrom = computed(() => underageError.value?.adultFrom ?? identity.status.value?.adultFrom ?? null);
/** 입력·저장을 열어 둔다 — 본인인증을 마쳤고 만 19세 이상 */
const editable = computed(() => verified.value && !underage.value);
/** 서버가 `409 EMOTICON_IDENTITY_NAME_MISSING` 로 막았다(본인인증은 했지만 인증 이름이 남아 있지 않다 — 예금주를 대조할 수 없다) */
const nameMissing = ref(false);
/** 한 번 더 인증하는 중 · 방금 마쳤다(저장을 다시 누르면 된다) */
const reverifying = ref(false);
const reverified = ref(false);
/** 인증 이름을 확인할 수 없다 — 인증했는데 가린 이름이 오지 않았거나 서버가 409 로 막았다. 저장해도 409 라 미리 알리고 한 번 더 인증하게 한다 */
const identityNameMissing = computed(
    () => verified.value && !reverified.value && (nameMissing.value || (account.value !== null && account.value.identityNameMasked === null)),
);

const form = reactive<{ type: EmoticonPayoutType; businessNumber: string; bankCode: string | null; accountNumber: string; holderName: string }>({
    type: 'INDIVIDUAL',
    businessNumber: '',
    bankCode: null,
    accountNumber: '',
    holderName: '',
});
const fieldErrors = ref<Record<string, string>>({});
const formError = ref('');
const saving = ref(false);

const bankOptions = computed(() => UtilEmoticon.banks().map((b) => ({ value: b.code, label: localizedName(b.name, b.nameEn) })));

function bankName(code: string | null): string {
    const bank = UtilEmoticon.bank(code);
    return bank ? localizedName(bank.name, bank.nameEn) : '';
}

function taxNote(type: EmoticonPayoutType): string {
    const rate = rules.value ? UtilEmoticonMessages.percent(rules.value.withholdingRate) : '';
    return type === 'BUSINESS' ? t('emoticon.payoutTaxBusiness') : t('emoticon.payoutTaxIndividual', { rate });
}

const ruleArgs = computed(() => {
    const r = rules.value;
    return r
        ? { days: r.refundDays, day: r.settlementDay, min: UtilEmoticonMessages.won(t, r.settlementMin), rate: UtilEmoticonMessages.percent(r.feeRate) }
        : { days: 7, day: 10, min: '', rate: '' };
});

/** 비밀 값(계좌 번호)을 비운다 — 저장·취소 뒤 */
function clearSecrets(): void {
    form.accountNumber = '';
    form.businessNumber = '';
    form.holderName = '';
}

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const [a, r] = await Promise.all([api.payoutAccount(), publicApi.rules(), identity.ensureLoaded()]);
        account.value = a;
        rules.value = r;
        form.type = a.type ?? 'INDIVIDUAL';
        form.bankCode = a.bankCode;
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilEmoticonMessages.error(t, e);
        status.value = 'error';
    }
}
onMounted(load);

/**
 * 한 번 더 본인인증(`useIdentityGate` — purpose `ROBOTIKON`, 이미 인증했어도 대화상자를 띄운다). 끝나면 정산 정보를 다시 받아(가린 인증 이름) 안내를 바꾸고,
 * 입력해 둔 값으로 [저장]을 다시 누를 수 있게 한다. 사용자가 닫으면 안내 칸의 [본인인증 다시 하기]로 다시 연다.
 */
async function reverify(): Promise<void> {
    if (reverifying.value) return;
    reverifying.value = true;
    try {
        const ok = await gate.ensure({ purpose: 'ROBOTIKON', requireAdult: false, reverify: true });
        if (!ok) return;
        nameMissing.value = false;
        reverified.value = true;
        formError.value = '';
        account.value = await api.payoutAccount().catch(() => account.value);
        // 등록된 정보를 보던 중이면 고치기 칸으로 — 다시 저장할 수 있게
        if (account.value?.registered && !editing.value) startEdit();
    } finally {
        reverifying.value = false;
    }
}

function startEdit(): void {
    fieldErrors.value = {};
    formError.value = '';
    clearSecrets();
    editing.value = true;
}

function cancelEdit(): void {
    clearSecrets();
    editing.value = false;
}

async function save(): Promise<void> {
    if (saving.value || !form.bankCode || !editable.value) {
        if (!form.bankCode) fieldErrors.value = { ...fieldErrors.value, bankCode: t('emoticon.payoutBankPick') };
        return;
    }
    saving.value = true;
    fieldErrors.value = {};
    formError.value = '';
    try {
        account.value = await api.savePayoutAccount({
            type: form.type,
            businessNumber: form.type === 'BUSINESS' ? form.businessNumber.replace(/\D/g, '') : null,
            bankCode: form.bankCode,
            accountNumber: form.accountNumber.replace(/\D/g, ''),
            holderName: form.holderName.trim(),
        });
        clearSecrets();
        editing.value = false;
        toast.success(t('emoticon.payoutSavedToast'));
    } catch (e) {
        applySaveError(e);
    } finally {
        saving.value = false;
    }
}

/**
 * 저장 오류 — 서버 차례: 작가 자격 403(`ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`) → 본인인증 `403 IDENTITY_REQUIRED` → 만 19세 미만 `403 IDENTITY_UNDERAGE` → 인증 이름 없음 `409 EMOTICON_IDENTITY_NAME_MISSING`
 * → `400 VALIDATION fields.*`(칸 아래) → 데이터 키 없음 `503 DATA_KEY_MISSING`. 문구는 서버 것 그대로.
 */
function applySaveError(e: unknown): void {
    if (e instanceof ApiError && e.code === 'IDENTITY_UNDERAGE') {
        // 만 19세 미만 작가(후속 결정 3) — 서버 문구·`adultFrom` 으로 안내 칸을 띄우고 입력을 잠근다. 인증 상태도 새로 받는다
        underageError.value = { message: e.message, adultFrom: e.extraText('adultFrom') ?? null };
        clearSecrets();
        void identity.load();
        return;
    }
    if (e instanceof ApiError && e.code === 'IDENTITY_REQUIRED') {
        // 화면이 아는 인증 상태가 낡았다(다른 탭에서 인증이 풀렸거나 만료) — 다시 받아 본인인증 안내 칸을 띄우고 입력칸을 잠근다
        void identity.load();
        clearSecrets();
    } else if (e instanceof ApiError && e.code === 'EMOTICON_IDENTITY_NAME_MISSING') {
        // 이 오류 코드만으로 판단한다(서버가 `extras.reverify` 같은 표시를 더해도 보지 않는다) — 입력값은 그대로 두고 한 번 더 인증하게 한다
        nameMissing.value = true;
        reverified.value = false;
        formError.value = t('emoticon.payoutReverifyNeeded');
        void reverify();
        return;
    } else if (e instanceof ApiError && e.code === 'VALIDATION' && e.fields) {
        fieldErrors.value = e.fields;
    }
    formError.value = UtilEmoticonMessages.error(t, e);
}
</script>
