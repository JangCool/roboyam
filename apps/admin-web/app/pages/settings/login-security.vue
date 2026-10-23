<template>
    <!--
        로그인 보안(`/settings/login-security`) — 관리자 2차 인증(TOTP)을 켜고 끄고 값을 정한다(`docs/domain/commerce.md` 13-3). **최고 관리자(루트)만** — 권한 코드로 위임하지 않는다.
        메뉴는 `superOnly` 로 루트에게만 보이고, 주소로 들어오면 셸이 권한 없음을 보인다(이 화면도 한 번 더 거른다). 막는 것은 서버(403)다.
        구획: ① 현재 상태(켜짐/꺼짐 · 켠 시각·사람 · 등록 현황 · 미등록 운영자 짧은 목록) ② 설정(사용 스위치 · 유예 일수 1~90 · 기기 신뢰 1~12개월 · OTP 실패 한도 3~10).
        저장은 한 번에(PUT) — 떠 있는 저장 줄(`StickySaveBar`). 켜기·끄기 저장 전에 확인을 묻는다. 루트가 OTP 를 등록하지 않았으면 켤 수 없다(스위치 잠금 + 내 정보 › 로그인 보안 링크, 서버 409 `MFA_ROOT_NOT_ENROLLED`).
        바꾼 내용은 서버가 감사(`MFA_SETTINGS_UPDATE`, 전→후)에 남기고, 끄면 운영자 알림을 보낸다. 루트가 아니면 서버가 403 `PERMISSION_DENIED`.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('mfa.settingsTitle')" :subtitle="t('mfa.settingsSubtitle')" />

        <ErrorState v-if="!isSuper" code="403" :title="t('shell.forbiddenTitle')" :description="t('mfa.settingsSuperOnly')" />

        <template v-else>
            <AppGadget v-if="loading && !saved" :title="t('mfa.statusSection')">
                <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                    <Skeleton v-for="n in 3" :key="n" height="2.5rem" />
                </div>
            </AppGadget>

            <AppGadget v-else-if="!saved" :title="t('mfa.settingsTitle')">
                <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('mfa.settingsLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
            </AppGadget>

            <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
                <!-- ① 현재 상태 · 등록 현황 -->
                <AppGadget :title="t('mfa.statusSection')" anchor="login-security-status">
                    <div class="flex flex-col gap-4">
                        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                            <Tag :value="saved.enabled ? t('mfa.siteOn') : t('mfa.siteOff')" :icon="saved.enabled ? 'pi pi-check-circle' : 'pi pi-power-off'" :severity="saved.enabled ? 'success' : 'secondary'" />
                            <span v-if="saved.enabled && saved.enabledAt" class="text-sm text-muted-color">{{ t('mfa.enabledBy', { at: time.full(saved.enabledAt), by: saved.enabledBy ?? '—' }) }}</span>
                            <span v-else-if="!saved.enabled" class="text-sm text-muted-color">{{ t('mfa.siteOffHint') }}</span>
                        </div>

                        <p class="m-0 text-sm">
                            {{ t('mfa.summaryLine', { operators: saved.summary.operators, enrolled: saved.summary.enrolled }) }}
                            <template v-if="saved.enabled"> · {{ t('mfa.summaryGrace', { inGrace: saved.summary.inGrace, expired: saved.summary.graceExpired }) }}</template>
                        </p>

                        <div v-if="saved.summary.pending.length" class="flex flex-col gap-2">
                            <h3 class="text-sm font-medium">{{ t('mfa.pendingTitle') }}</h3>
                            <ul class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-lg border border-(--line) p-0">
                                <li v-for="p in saved.summary.pending" :key="p.operatorId" class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 px-3 py-2 text-sm">
                                    <span class="min-w-0 truncate">{{ p.name }}</span>
                                    <span class="text-xs tabular-nums text-muted-color">{{ p.graceEndsAt ? t('mfa.pendingGraceEnds', { date: time.date(p.graceEndsAt), days: UtilMfa.daysLeft(p.graceEndsAt) }) : t('mfa.pendingNoGrace') }}</span>
                                </li>
                            </ul>
                            <NuxtLink to="/settings/operators" class="self-start text-sm text-primary hover:underline">{{ t('mfa.openOperators') }}</NuxtLink>
                        </div>
                    </div>
                </AppGadget>

                <!-- ② 설정 -->
                <AppGadget :title="t('mfa.settingsSection')" anchor="login-security-settings" :changes="changedCount" :invalid="errorCount">
                    <div class="flex flex-col gap-5">
                        <Message v-if="!saved.rootEnrolled" severity="warn" :closable="false" size="small">
                            {{ t('mfa.rootNotEnrolled') }}
                            <NuxtLink to="/me?tab=security" class="font-medium text-primary hover:underline">{{ t('mfa.openMySecurity') }}</NuxtLink>
                        </Message>

                        <div class="flex items-start gap-3">
                            <ToggleSwitch v-model="form.enabled" :input-id="enabledId" :disabled="switchLocked || saving" :aria-describedby="`${enabledId}-help`" />
                            <div class="flex flex-col gap-0.5">
                                <label :for="enabledId" class="text-sm font-medium">{{ t('mfa.enabledLabel') }}</label>
                                <small :id="`${enabledId}-help`" class="text-xs text-muted-color">{{ t('mfa.enabledHelp') }}</small>
                            </div>
                        </div>

                        <div class="grid gap-x-4 gap-y-4 sm:grid-cols-[repeat(3,minmax(0,15rem))]">
                            <LabeledField :label="t('mfa.graceDaysLabel')" :hint="t('mfa.graceDaysHint')" :help="t('mfa.graceDaysHelp')" :error="errorOf('graceDays')">
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <InputText :id="id" v-model="form.graceDays" name="graceDays" inputmode="numeric" autocomplete="off" :readonly="saving" :invalid="invalid" :aria-describedby="describedBy" class="w-24 text-right tabular-nums" @update:model-value="clearServer('graceDays')" />
                                        <span class="text-sm text-muted-color">{{ t('mfa.unitDays') }}</span>
                                    </div>
                                </template>
                            </LabeledField>

                            <LabeledField :label="t('mfa.trustMonthsLabel')" :help="t('mfa.trustMonthsHelp')" :error="errorOf('trustMonths')">
                                <template #default="{ id, describedBy, invalid }">
                                    <Select v-model="form.trustMonths" :input-id="id" :options="trustOptions" option-label="label" option-value="value" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" class="w-full" @update:model-value="clearServer('trustMonths')" />
                                </template>
                            </LabeledField>

                            <LabeledField :label="t('mfa.maxFailuresLabel')" :hint="t('mfa.maxFailuresHint')" :help="t('mfa.maxFailuresHelp', { minutes: UtilMfa.LOCK_MINUTES })" :error="errorOf('maxFailures')">
                                <template #default="{ id, describedBy, invalid }">
                                    <div class="flex items-center gap-2">
                                        <InputText :id="id" v-model="form.maxFailures" name="maxFailures" inputmode="numeric" autocomplete="off" :readonly="saving" :invalid="invalid" :aria-describedby="describedBy" class="w-24 text-right tabular-nums" @update:model-value="clearServer('maxFailures')" />
                                        <span class="text-sm text-muted-color">{{ t('mfa.unitTimes') }}</span>
                                    </div>
                                </template>
                            </LabeledField>
                        </div>

                        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed text-muted-color">
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('mfa.noteRequired') }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('mfa.noteTrustReset') }}</span></li>
                            <li class="flex items-start gap-2"><i class="pi pi-info-circle mt-0.5 text-xs" aria-hidden="true" /><span>{{ t('mfa.noteAudit') }}</span></li>
                        </ul>
                    </div>
                </AppGadget>

                <StickySaveBar submit revertable :dirty="dirty" :saving="saving" :disabled="!canSave" :count="changedCount" :error-count="errorCount" :error="formError" @revert="revert" />
            </form>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { LoginSecuritySaveRequest, LoginSecurityView } from '~/composables/mfa/api/useMfaApi.types';
import UtilMfa from '~/utils/util-mfa';
import UtilMfaMessages from '~/utils/util-mfa-messages';

/** 입력 중인 값 — 숫자 칸은 글자로 들고 저장할 때 바꾼다(정책 화면과 같은 이유: `InputNumber` 는 범위 밖 값을 말없이 고친다) */
interface LoginSecurityForm {
    enabled: boolean;
    graceDays: string;
    trustMonths: number;
    maxFailures: string;
}
type Field = 'graceDays' | 'trustMonths' | 'maxFailures';

const { t } = useI18n();
useSeoMeta({ title: () => t('mfa.settingsTitle') });

const api = useMfaApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { isSuper } = useAdminPermission();

const enabledId = useId();
const saved = ref<LoginSecurityView | null>(null);
const form = ref<LoginSecurityForm>({ enabled: false, graceDays: '14', trustMonths: 1, maxFailures: '5' });
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Partial<Record<Field, string>>>({});

const trustOptions = computed(() => Array.from({ length: UtilMfa.TRUST_MONTHS.max }, (_, i) => ({ value: i + 1, label: UtilMfaMessages.trustMonths(t, i + 1) })));

function formOf(view: LoginSecurityView): LoginSecurityForm {
    return { enabled: view.enabled, graceDays: String(view.graceDays), trustMonths: view.trustMonths, maxFailures: String(view.maxFailures) };
}

/** 루트가 등록하지 않았으면 켤 수 없다 — 이미 켜져 있으면 끄는 것은 된다 */
const switchLocked = computed(() => !!saved.value && !saved.value.rootEnrolled && !saved.value.enabled);

/** 화면이 미리 알리는 범위 문제(판정은 서버) */
const problems = computed<Partial<Record<Field, string>>>(() => {
    const out: Partial<Record<Field, string>> = {};
    if (!UtilMfa.inRange(Number(form.value.graceDays.trim() || NaN), UtilMfa.GRACE_DAYS)) out.graceDays = t('mfa.graceDaysRange');
    if (!UtilMfa.inRange(form.value.trustMonths, UtilMfa.TRUST_MONTHS)) out.trustMonths = t('mfa.trustMonthsRange');
    if (!UtilMfa.inRange(Number(form.value.maxFailures.trim() || NaN), UtilMfa.MAX_FAILURES)) out.maxFailures = t('mfa.maxFailuresRange');
    return out;
});

const changedCount = computed(() => {
    if (!saved.value) return 0;
    const base = formOf(saved.value);
    return (['enabled', 'graceDays', 'trustMonths', 'maxFailures'] as const).filter((key) => String(form.value[key]).trim() !== String(base[key])).length;
});
const dirty = computed(() => changedCount.value > 0);
const errorCount = computed(() => (['graceDays', 'trustMonths', 'maxFailures'] as const).filter((f) => problems.value[f] || serverErrors.value[f]).length);
const canSave = computed(() => dirty.value && Object.keys(problems.value).length === 0);

function errorOf(field: Field): string {
    return problems.value[field] ?? serverErrors.value[field] ?? '';
}

function clearServer(field: Field): void {
    if (!serverErrors.value[field]) return;
    const { [field]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(() => {
    if (isSuper.value) void load();
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.loginSecurity());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: LoginSecurityView): void {
    saved.value = view;
    form.value = formOf(view);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (saving.value || !canSave.value || !saved.value) return;
    const body: LoginSecuritySaveRequest = {
        enabled: form.value.enabled,
        graceDays: Number(form.value.graceDays.trim()),
        trustMonths: form.value.trustMonths,
        maxFailures: Number(form.value.maxFailures.trim()),
    };

    // 켜기·끄기는 한 번 더 묻는다
    if (body.enabled && !saved.value.enabled) {
        const unenrolled = Math.max(0, saved.value.summary.operators - saved.value.summary.enrolled);
        const ok = await confirm({ title: t('mfa.enableConfirmTitle'), message: t('mfa.enableConfirmMessage', { count: unenrolled, days: body.graceDays }), confirmLabel: t('mfa.enableConfirm') });
        if (!ok) return;
    } else if (!body.enabled && saved.value.enabled) {
        const ok = await confirm({ title: t('mfa.disableConfirmTitle'), message: t('mfa.disableConfirmMessage'), confirmLabel: t('mfa.disableConfirm'), tone: 'danger' });
        if (!ok) return;
    }

    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.saveLoginSecurity(body));
        toast.success(t('mfa.settingsSaved'));
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            const { graceDays, trustMonths, maxFailures } = failure.fields;
            serverErrors.value = { graceDays, trustMonths, maxFailures };
            formError.value = graceDays || trustMonths || maxFailures ? t('mfa.saveFieldsRejected') : failure.message;
        } else {
            if (failure.code === 'MFA_ROOT_NOT_ENROLLED') saved.value = { ...saved.value, rootEnrolled: false };
            formError.value = failure.message;
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('mfa.leaveTitle'), message: t('mfa.leaveMessage'), confirmLabel: t('mfa.leaveConfirm'), cancelLabel: t('mfa.leaveCancel'), tone: 'danger' });
});
</script>
