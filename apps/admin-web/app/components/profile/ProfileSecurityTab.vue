<template>
    <!--
        내 정보 › 로그인 보안 — 내 2차 인증(TOTP)과 신뢰 기기(`docs/domain/commerce.md` 13-3). 구획 둘:
        ① 2차 인증: 사이트 상태 안내(꺼짐 → 미리 등록 권유, 루트는 설정 화면 링크 / 켜짐 + 미등록 → 유예 마감 경고) + 내 OTP
           · 미등록: [등록하기] → 등록 패널(`MfaEnrollPanel`)
           · 등록됨: 등록일 · 마지막 사용 · 남은 복구 코드 N/10(3개 이하 경고) · [복구 코드 새로 만들기](현재 OTP 확인 → 새 10개, 옛 코드 무효) · [OTP 다시 등록](비밀번호 확인 → 등록 패널, 신뢰 기기 모두 끊김)
        ② 신뢰 기기: 기기 · IP · 신뢰한 날 · 만료일 · 마지막 사용, 이 기기 표시, 줄마다 [끊기] · 위에 [모두 끊기](확인). 사이트가 꺼져 있으면 「켜져 있을 때만 쓰여요」 안내만.
        비밀번호·코드·비밀키·복구 코드는 이 컴포넌트 메모리에만 두고 끝나면 비운다. 서버 호출은 `useMyMfa`·`useMfaEnrollment`(로그인 세션 — 자발 등록·다시 등록 확정 주소) 가 한다.
    -->
    <div class="flex flex-col gap-4" aria-live="polite" :aria-busy="loading">
        <AppGadget :title="t('mfa.sectionOtp')" anchor="mfa-otp">
            <div v-if="loading && !status" class="flex justify-center py-8">
                <ProgressSpinner style="width: 2.25rem; height: 2.25rem" :aria-label="t('common.loading')" />
            </div>

            <ErrorState v-else-if="!status" :title="t('mfa.loadFailed')" :description="error?.message" retry :retrying="loading" @retry="load()" />

            <div v-else class="flex flex-col gap-4">
                <!-- 사이트 상태 안내 -->
                <Message v-if="!status.siteEnabled" severity="info" :closable="false" size="small">
                    <span class="block">{{ t('mfa.siteOffNotice') }}</span>
                    <span v-if="isSuper" class="mt-1 block">
                        {{ status.enrolled ? t('mfa.siteOffRootEnrolled') : t('mfa.siteOffRootNotEnrolled') }}
                        <NuxtLink to="/settings/login-security" class="font-medium text-primary hover:underline">{{ t('mfa.openSettings') }}</NuxtLink>
                    </span>
                </Message>
                <Message v-else-if="!status.enrolled && status.graceEndsAt" severity="warn" :closable="false" size="small">
                    <b>{{ t('mfa.graceTitle') }}</b>
                    <span class="mt-0.5 block">{{ t('mfa.graceBody', { date: time.date(status.graceEndsAt), days: UtilMfa.daysLeft(status.graceEndsAt) }) }}</span>
                </Message>

                <!-- 등록 패널(처음 등록 · 다시 등록) -->
                <template v-if="enrolling">
                    <Message v-if="reenrolling" severity="warn" :closable="false" size="small">{{ t('mfa.reenrollPanelNote') }}</Message>
                    <MfaEnrollPanel
                        :enrollment="enroll.enrollment.value"
                        :recovery-codes="enroll.recoveryCodes.value"
                        :starting="enroll.starting.value"
                        :start-error="enroll.startError.value"
                        :confirming="enroll.confirming.value"
                        :code-error="enroll.codeError.value"
                        :lock-seconds="enroll.lock.remaining.value"
                        :account="user?.email ?? ''"
                        :finish-label="t('mfa.done')"
                        @confirm="(value: string) => void enroll.confirm(value)"
                        @retry="startEnroll"
                        @finish="finishEnroll"
                    >
                        <template #secondary>
                            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="enroll.confirming.value" @click="cancelEnroll" />
                        </template>
                    </MfaEnrollPanel>
                </template>

                <!-- 미등록 -->
                <div v-else-if="!status.enrolled" class="flex flex-col items-start gap-3">
                    <p class="m-0 text-sm leading-relaxed">{{ t('mfa.notEnrolledBody') }}</p>
                    <Button type="button" :label="t('mfa.enroll')" icon="pi pi-shield" @click="startEnroll" />
                </div>

                <!-- 등록됨 -->
                <template v-else>
                    <dl class="m-0 grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[max-content_1fr]">
                        <dt class="text-muted-color">{{ t('mfa.enrolledAt') }}</dt>
                        <dd class="m-0 tabular-nums">{{ status.enrolledAt ? time.full(status.enrolledAt) : '—' }}</dd>
                        <dt class="text-muted-color">{{ t('mfa.lastUsedAt') }}</dt>
                        <dd class="m-0 tabular-nums">{{ status.lastUsedAt ? time.full(status.lastUsedAt) : '—' }}</dd>
                        <dt class="text-muted-color">{{ t('mfa.recoveryLeftLabel') }}</dt>
                        <dd class="m-0 flex flex-wrap items-center gap-2">
                            <span class="tabular-nums">{{ t('mfa.recoveryCount', { left: status.recoveryCodesLeft, total: UtilMfa.RECOVERY_CODE_COUNT }) }}</span>
                            <Tag v-if="status.recoveryCodesLeft <= UtilMfa.RECOVERY_LOW" :value="t('mfa.recoveryLowTag')" icon="pi pi-exclamation-triangle" severity="warn" />
                        </dd>
                    </dl>

                    <!-- 방금 새로 만든 복구 코드 -->
                    <div v-if="newCodes" class="flex flex-col gap-3">
                        <Message severity="success" :closable="false" size="small" role="status">
                            <b>{{ t('mfa.recoveryRegenerated') }}</b>
                            <span class="mt-0.5 block">{{ t('mfa.recoveryOldInvalid') }}</span>
                        </Message>
                        <MfaRecoveryCodes :codes="newCodes" :account="user?.email ?? ''" />
                        <div>
                            <Button type="button" :label="t('mfa.recoveryHide')" severity="secondary" variant="outlined" size="small" @click="newCodes = null" />
                        </div>
                    </div>

                    <div class="flex flex-wrap gap-2">
                        <Button type="button" :label="t('mfa.recoveryRegenerate')" icon="pi pi-refresh" severity="secondary" variant="outlined" @click="openRegenerate" />
                        <Button type="button" :label="t('mfa.reenroll')" icon="pi pi-qrcode" severity="secondary" variant="outlined" @click="openReenroll" />
                    </div>
                </template>
            </div>
        </AppGadget>

        <AppGadget :title="t('mfa.sectionDevices')" anchor="mfa-devices">
            <template v-if="status?.siteEnabled && devices?.length" #actions>
                <Button type="button" :label="t('mfa.revokeAll')" icon="pi pi-times-circle" size="small" severity="danger" variant="outlined" :loading="revokingAll" @click="revokeAll" />
            </template>

            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mfa.devicesIntro', { days: status?.trustDays ?? 30 }) }}</p>

            <div v-if="loading && !devices" class="flex justify-center py-6">
                <ProgressSpinner style="width: 2rem; height: 2rem" :aria-label="t('common.loading')" />
            </div>
            <Message v-else-if="status && !status.siteEnabled" severity="info" :closable="false" size="small">{{ t('mfa.devicesSiteOff') }}</Message>
            <ErrorState v-else-if="devicesError && !devices" :title="t('mfa.devicesLoadFailed')" :description="devicesError.message" retry @retry="reloadDevices()" />
            <EmptyState v-else-if="!devices?.length" icon="pi pi-desktop" :title="t('mfa.devicesEmpty')" :description="t('mfa.devicesEmptyBody')" />
            <DataTable v-else :value="devices" data-key="id" scrollable :size="prefs.density === 'compact' ? 'small' : undefined">
                <Column :header="t('mfa.colDevice')">
                    <template #body="{ data }">
                        <span class="flex min-w-48 items-center gap-2">
                            <i :class="[UtilUserAgent.icon(UtilUserAgent.summaryDevice((data as MfaTrustedDeviceView).agent) ?? 'unknown'), 'shrink-0 text-muted-color']" aria-hidden="true" />
                            <span class="min-w-0 truncate">{{ (data as MfaTrustedDeviceView).agent || t('profile.agentUnknown') }}</span>
                            <Tag v-if="(data as MfaTrustedDeviceView).current" :value="t('mfa.thisDevice')" severity="info" />
                        </span>
                    </template>
                </Column>
                <Column :header="t('mfa.colIp')">
                    <template #body="{ data }"><span class="tabular-nums">{{ (data as MfaTrustedDeviceView).ip ?? '—' }}</span></template>
                </Column>
                <Column :header="t('mfa.colTrustedAt')">
                    <template #body="{ data }"><span class="whitespace-nowrap tabular-nums">{{ time.date((data as MfaTrustedDeviceView).trustedAt) }}</span></template>
                </Column>
                <Column :header="t('mfa.colExpiresAt')">
                    <template #body="{ data }"><span class="whitespace-nowrap tabular-nums">{{ time.date((data as MfaTrustedDeviceView).expiresAt) }}</span></template>
                </Column>
                <Column :header="t('mfa.colLastUsed')">
                    <template #body="{ data }">
                        <span class="whitespace-nowrap tabular-nums">{{ (data as MfaTrustedDeviceView).lastUsedAt ? time.listLabel((data as MfaTrustedDeviceView).lastUsedAt) : '—' }}</span>
                    </template>
                </Column>
                <Column :header="t('mfa.colActions')" class="w-0">
                    <template #body="{ data }">
                        <Button type="button" :label="t('mfa.revoke')" size="small" severity="secondary" variant="text" :loading="revokingId === (data as MfaTrustedDeviceView).id" @click="revoke(data as MfaTrustedDeviceView)" />
                    </template>
                </Column>
            </DataTable>
        </AppGadget>

        <!-- 복구 코드 새로 만들기 — 현재 OTP 확인 -->
        <Dialog v-model:visible="regenerateOpen" modal :draggable="false" :header="t('mfa.recoveryRegenerateTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" @show="regenerateInput?.focus()">
            <form :id="regenerateFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitRegenerate">
                <p class="m-0 text-sm leading-relaxed">{{ t('mfa.recoveryRegenerateBody') }}</p>
                <Message v-if="regenerateLock.active.value" severity="warn" :closable="false" size="small" role="status">
                    <b>{{ t('mfa.lockedTitle') }}</b> {{ t('mfa.lockedRemaining', { time: UtilTime.mmss(regenerateLock.remaining.value) }) }}
                </Message>
                <MfaCodeInput ref="regenerateInput" v-model="regenerateCode" :label="t('mfa.otpLabel')" :help="t('mfa.codeHelp')" :error="regenerateLock.active.value ? '' : regenerateError" :disabled="regenerating || regenerateLock.active.value" @complete="submitRegenerate" />
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="regenerating" @click="regenerateOpen = false" />
                <Button type="submit" :form="regenerateFormId" :label="t('mfa.recoveryRegenerateSubmit')" :loading="regenerating" :disabled="regenerateLock.active.value || !UtilMfa.isOtp(regenerateCode)" />
            </template>
        </Dialog>

        <!-- OTP 다시 등록 — 비밀번호 확인 -->
        <Dialog v-model:visible="reenrollOpen" modal :draggable="false" :header="t('mfa.reenrollTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" @show="reenrollField?.focus()" @hide="reenrollPassword = ''">
            <form :id="reenrollFormId" class="flex flex-col gap-4" novalidate @submit.prevent="submitReenroll">
                <Message severity="warn" :closable="false" size="small">{{ t('mfa.reenrollWarn') }}</Message>
                <PasswordField ref="reenrollField" v-model="reenrollPassword" name="current-password" autocomplete="current-password" :label="t('account.currentPasswordLabel')" :disabled="reenrollBusy" :error="reenrollFieldError" />
                <Message v-if="reenrollError" severity="error" variant="simple" size="small" :closable="false">{{ reenrollError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="reenrollBusy" @click="reenrollOpen = false" />
                <Button type="submit" :form="reenrollFormId" :label="t('mfa.reenrollSubmit')" :loading="reenrollBusy" :disabled="!reenrollPassword" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { MfaTrustedDeviceView } from '~/composables/mfa/api/useMfaApi.types';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilMfa from '~/utils/util-mfa';
import UtilMfaMessages from '~/utils/util-mfa-messages';
import UtilUserAgent from '~/utils/util-user-agent';

const { t } = useI18n();
const time = useDisplayTime();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { user } = useAdminAuth();
const { isSuper } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓴다(조밀 모드)
const { prefs } = useLayoutPrefs();

const { status, devices, loading, error, devicesError, load, reloadStatus, reloadDevices, revokeDevice, revokeAllDevices, regenerateRecoveryCodes, reenroll } = useMyMfa();
const enroll = useMfaEnrollment('session');

onMounted(() => void load());

/* ── 등록 · 다시 등록 ── */

const enrolling = ref(false);
const reenrolling = ref(false);

async function startEnroll(): Promise<void> {
    enrolling.value = true;
    reenrolling.value = false;
    newCodes.value = null;
    await enroll.start();
}

function cancelEnroll(): void {
    enroll.reset();
    enrolling.value = false;
    reenrolling.value = false;
}

async function finishEnroll(): Promise<void> {
    const wasReenroll = reenrolling.value;
    enroll.reset();
    enrolling.value = false;
    reenrolling.value = false;
    toast.success(wasReenroll ? t('mfa.reenrolledToast') : t('mfa.enrolledToast'));
    await Promise.all([reloadStatus(), reloadDevices()]);
}

const reenrollOpen = ref(false);
const reenrollPassword = ref('');
const reenrollBusy = ref(false);
const reenrollError = ref('');
const reenrollFieldError = ref('');
const reenrollFormId = useId();
const reenrollField = ref<{ focus: () => void } | null>(null);

function openReenroll(): void {
    reenrollPassword.value = '';
    reenrollError.value = '';
    reenrollFieldError.value = '';
    reenrollOpen.value = true;
}

async function submitReenroll(): Promise<void> {
    if (reenrollBusy.value || !reenrollPassword.value) return;
    reenrollBusy.value = true;
    reenrollError.value = '';
    reenrollFieldError.value = '';
    try {
        const view = await reenroll(reenrollPassword.value);
        reenrollPassword.value = '';
        reenrollOpen.value = false;
        newCodes.value = null;
        enroll.use(view);
        enrolling.value = true;
        reenrolling.value = true;
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'INVALID_CURRENT_PASSWORD' || failure.fields?.password) {
            reenrollFieldError.value = failure.fields?.password ?? failure.message;
            reenrollPassword.value = '';
            await nextTick();
            reenrollField.value?.focus();
        } else {
            reenrollError.value = failure.message;
        }
    } finally {
        reenrollBusy.value = false;
    }
}

/* ── 복구 코드 새로 만들기 ── */

const newCodes = ref<string[] | null>(null);
const regenerateOpen = ref(false);
const regenerateCode = ref('');
const regenerateError = ref('');
const regenerating = ref(false);
const regenerateFormId = useId();
const regenerateInput = ref<{ focus: () => void } | null>(null);
const regenerateLock = useCountdown(undefined, () => (regenerateError.value = ''));

function openRegenerate(): void {
    regenerateCode.value = '';
    regenerateError.value = '';
    regenerateOpen.value = true;
}

async function submitRegenerate(): Promise<void> {
    if (regenerating.value || regenerateLock.active.value || !UtilMfa.isOtp(regenerateCode.value)) return;
    regenerating.value = true;
    regenerateError.value = '';
    try {
        newCodes.value = await regenerateRecoveryCodes(regenerateCode.value);
        regenerateOpen.value = false;
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'MFA_LOCKED') regenerateLock.start(failure.retryAfterSeconds ?? UtilMfa.LOCK_MINUTES * 60);
        regenerateError.value = UtilMfaMessages.codeError(t, failure);
        await nextTick();
        regenerateInput.value?.focus();
    } finally {
        regenerateCode.value = '';
        regenerating.value = false;
    }
}

/* ── 신뢰 기기 ── */

const revokingId = ref<string | null>(null);
const revokingAll = ref(false);

async function revoke(device: MfaTrustedDeviceView): Promise<void> {
    const ok = await confirm({
        title: t('mfa.revokeTitle'),
        message: device.current ? t('mfa.revokeCurrentMessage') : t('mfa.revokeMessage', { device: device.agent ?? t('profile.agentUnknown') }),
        confirmLabel: t('mfa.revoke'),
        tone: 'danger',
    });
    if (!ok) return;
    revokingId.value = device.id;
    try {
        await revokeDevice(device.id);
        toast.success(t('mfa.revokedToast'));
    } catch (e) {
        toast.error(`${t('mfa.revokeFailed')} — ${toApiError(e).message}`, 8000);
        await reloadDevices();
    } finally {
        revokingId.value = null;
    }
}

async function revokeAll(): Promise<void> {
    const ok = await confirm({ title: t('mfa.revokeAllTitle'), message: t('mfa.revokeAllMessage', { count: devices.value?.length ?? 0 }), confirmLabel: t('mfa.revokeAll'), tone: 'danger' });
    if (!ok) return;
    revokingAll.value = true;
    try {
        await revokeAllDevices();
        toast.success(t('mfa.revokedAllToast'));
    } catch (e) {
        toast.error(`${t('mfa.revokeFailed')} — ${toApiError(e).message}`, 8000);
    } finally {
        revokingAll.value = false;
    }
}
</script>
