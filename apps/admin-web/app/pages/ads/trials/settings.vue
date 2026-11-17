<template>
    <!--
        광고 › 체험단 설정(`/ads/trials/settings`, trial-group-contract 「관리자 화면」 — 사이트 한 번, `trial_settings` 행 하나).
        구획: 제공형 상한 · 표시 문구(체험단 — {광고주}·{제공/대여} 자리표시 + 미리보기 · 협찬) · 반납 독촉(기한 당일 N일 · 다시 알림 N일 · 미반납 확정 N일) ·
          사용기 기한(처음 값 · 넘기면 신청 금지 N일 · N번이면 리뷰어 칭호 정지) · 구매 인증(보너스 포인트 · 사진 보관 일수) · 입고 보관 일수 · 체험단 사진 보관 일수(V243).
        화면 하나를 PUT 한 번으로 저장 → 아래 떠 있는 저장 줄(`StickySaveBar`). 검사는 `UtilTrial.settingsProblems`(서버 CHECK 와 같은 범위). 권한: 저장 `trial.settings`(없으면 읽기만).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('trial.settings.title')" :subtitle="t('trial.settings.subtitle')" />

        <div v-if="state === 'loading'" class="h-80 animate-pulse rounded-xl bg-surface-100 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <ErrorState v-else-if="state === 'error' || !form" :title="t('trial.settings.loadFailed')" :description="loadError" retry @retry="load" />

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <Message v-if="!canSave" severity="secondary" variant="simple" size="small">{{ t('trial.settings.readOnly') }}</Message>

            <AppGadget :title="t('trial.settings.sectionGive')" anchor="trial-give">
                <div class="grid gap-3 sm:grid-cols-2">
                    <LabeledField :label="t('trial.settings.givePriceLimit')" :help="t('trial.settings.givePriceLimitHelp')" :error="errorOf('givePriceLimit')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputGroup>
                                <InputNumber v-model="form.givePriceLimit" :input-id="id" :min="0" :max="100000000" :allow-empty="false" fluid :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" input-class="text-right tabular-nums" />
                                <InputGroupAddon>{{ t('trial.wonSuffix') }}</InputGroupAddon>
                            </InputGroup>
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.settings.stockKeepDays')" :help="t('trial.settings.stockKeepDaysHelp')" :error="errorOf('stockKeepDays')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.stockKeepDays" :input-id="id" :min="1" :max="365" :allow-empty="false" fluid :suffix="t('trial.daysSuffix')" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <!-- 체험단 사진 보관(V243 — 점검 D3, 1~365 · 기본 90) -->
                    <LabeledField :label="t('trial.settings.photoKeepDays')" :help="t('trial.settings.photoKeepDaysHelp')" :error="errorOf('photoKeepDays')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.photoKeepDays" :input-id="id" :min="1" :max="365" :allow-empty="false" fluid :suffix="t('trial.daysSuffix')" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <AppGadget :title="t('trial.settings.sectionDisclosure')" anchor="trial-disclosure">
                <div class="flex flex-col gap-3">
                    <LabeledField :label="t('trial.settings.disclosureText')" :help="t('trial.settings.disclosureTextHelp')" :error="errorOf('disclosureText')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.disclosureText" rows="2" auto-resize fluid :maxlength="UtilTrial.LIMITS.disclosureMax" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <div class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-[13px] dark:border-amber-700 dark:bg-amber-950/40">
                        <p class="m-0 text-[11px] text-muted-color">{{ t('trial.settings.preview') }}</p>
                        <p class="m-0">{{ UtilTrial.disclosurePreview(form.disclosureText, t('trial.settings.previewAdvertiser'), false, t('trial.settings.previewGive'), t('trial.settings.previewLend')) }}</p>
                        <p class="m-0">{{ UtilTrial.disclosurePreview(form.disclosureText, t('trial.settings.previewAdvertiser'), true, t('trial.settings.previewGive'), t('trial.settings.previewLend')) }}</p>
                    </div>
                    <LabeledField :label="t('trial.settings.sponsorDisclosureText')" :error="errorOf('sponsorDisclosureText')">
                        <template #default="{ id, describedBy, invalid }">
                            <Textarea :id="id" v-model="form.sponsorDisclosureText" rows="2" auto-resize fluid :maxlength="UtilTrial.LIMITS.disclosureMax" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <AppGadget :title="t('trial.settings.sectionReturn')" anchor="trial-return">
                <div class="grid gap-3 sm:grid-cols-3">
                    <LabeledField v-for="f in RETURN_FIELDS" :key="f" :label="t(`trial.settings.${f}`)" :help="t(`trial.settings.${f}Help`)" :error="errorOf(f)">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form[f]" :input-id="id" :min="0" :max="120" :allow-empty="false" fluid :suffix="t('trial.daysSuffix')" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </div>
                <p class="m-0 mt-2 text-xs text-muted-color">{{ t('trial.settings.returnNote') }}</p>
            </AppGadget>

            <AppGadget :title="t('trial.settings.sectionReview')" anchor="trial-review">
                <div class="grid gap-3 sm:grid-cols-3">
                    <LabeledField v-for="f in REVIEW_FIELDS" :key="f" :label="t(`trial.settings.${f}`)" :help="t(`trial.settings.${f}Help`)" :error="errorOf(f)">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form[f]" :input-id="id" :min="1" :max="3650" :allow-empty="false" fluid :suffix="f === 'lateRevokeStrikes' ? t('trial.timesSuffix') : t('trial.daysSuffix')" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <AppGadget :title="t('trial.settings.sectionProof')" anchor="trial-proof">
                <div class="grid gap-3 sm:grid-cols-2">
                    <LabeledField :label="t('trial.settings.proofBonusPoints')" :help="t('trial.settings.proofBonusPointsHelp')" :error="errorOf('proofBonusPoints')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.proofBonusPoints" :input-id="id" :min="0" :max="10000" :allow-empty="false" fluid suffix=" P" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.settings.proofPhotoDays')" :help="t('trial.settings.proofPhotoDaysHelp')" :error="errorOf('proofPhotoDays')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.proofPhotoDays" :input-id="id" :min="1" :max="365" :allow-empty="false" fluid :suffix="t('trial.daysSuffix')" :readonly="!canSave" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <p class="m-0 text-xs text-muted-color">{{ t('trial.settings.historyNote') }}</p>

            <StickySaveBar v-if="canSave" :dirty="dirty" :saving="saving" :disabled="problems.length > 0" :count="changedCount" :error-count="problems.length" :error="saveError" revertable submit @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { TrialSettings } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';

const RETURN_FIELDS = ['returnRemindFirstDays', 'returnRemindSecondDays', 'unreturnedDays'] as const;
const REVIEW_FIELDS = ['reviewDays', 'lateBanDays', 'lateRevokeStrikes'] as const;

const { t } = useI18n();
const api = useTrialApi();
const toast = useAppToast();
const errorOf_ = useApiErrorOf();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

const canSave = computed(() => can('trial.settings'));
const saved = ref<TrialSettings | null>(null);
const form = ref<TrialSettings | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const saving = ref(false);
const saveError = ref('');
const fieldErrors = ref<Record<string, string>>({});

function requestOf(s: TrialSettings): TrialSettings {
    return { ...s };
}

async function load(): Promise<void> {
    state.value = 'loading';
    try {
        saved.value = await api.settings();
        form.value = requestOf(saved.value);
        state.value = 'ready';
    } catch (e) {
        loadError.value = errorOf_(e).message;
        state.value = 'error';
    }
}
void load();

const problems = computed(() => (form.value ? UtilTrial.settingsProblems(form.value) : []));
const changedCount = computed(() => {
    if (!form.value || !saved.value) return 0;
    const base = requestOf(saved.value);
    return (Object.keys(form.value) as (keyof TrialSettings)[]).filter((k) => form.value![k] !== base[k]).length;
});
const dirty = computed(() => changedCount.value > 0);

function errorOf(field: keyof TrialSettings): string {
    if (fieldErrors.value[field]) return fieldErrors.value[field]!;
    return problems.value.includes(field) ? t(`trial.settings.problem.${field}`) : '';
}

function revert(): void {
    if (saved.value) form.value = requestOf(saved.value);
    fieldErrors.value = {};
    saveError.value = '';
}

async function save(): Promise<void> {
    if (!form.value || saving.value || problems.value.length) return;
    saving.value = true;
    saveError.value = '';
    fieldErrors.value = {};
    try {
        saved.value = await api.saveSettings({ ...toRaw(form.value), disclosureText: form.value.disclosureText.trim(), sponsorDisclosureText: form.value.sponsorDisclosureText.trim() });
        form.value = requestOf(saved.value);
        toast.success(t('trial.settings.saved'));
    } catch (e) {
        const error = errorOf_(e);
        if (error.fields && Object.keys(error.fields).length) fieldErrors.value = { ...error.fields };
        else saveError.value = error.message;
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return await confirm({ title: t('trial.settings.leaveTitle'), message: t('trial.settings.leaveText'), confirmLabel: t('trial.settings.leaveConfirm') });
});

useSeoMeta({ title: () => t('trial.settings.title') });
</script>
