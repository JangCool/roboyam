<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 계정·로그인 연결」 -->
    <div>
        <!-- 결과 화면(바꿨어요·소셜 가입자)은 제목(h1)을 StatusPanel 이 그린다 -->
        <h1 v-if="view === 'loading' || view === 'error' || view === 'form'" class="mb-4 text-lg font-bold">{{ t('account.passwordChange') }}</h1>

        <!-- 로딩 -->
        <div v-if="view === 'loading'" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" role="status" :aria-label="t('account.accountLoading')" data-testid="password-loading">
            <div class="space-y-4">
                <div v-for="n in 3" :key="n" class="h-10 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <!-- 불러오기 실패 -->
        <ErrorState v-else-if="view === 'error'" :title="t('account.accountLoadFailed')" :description="loadError" retry data-testid="password-error" @retry="load" />

        <!-- 변경 완료 — 이 기기는 로그인이 남고 다른 기기는 끊긴다 -->
        <div v-else-if="view === 'done'" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <StatusPanel icon="pi pi-check" tone="success" :title="t('password.changedTitle')" data-testid="password-done">
                {{ t('password.changedText') }}
                <template #actions>
                    <Button as="router-link" to="/settings/account" :label="t('account.backToAccount')" fluid size="large" />
                </template>
            </StatusPanel>
        </div>

        <!-- 소셜 가입자가 들어왔다 — 비밀번호가 없어서 바꿀 수 없다 -->
        <div v-else-if="view === 'social'" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <StatusPanel icon="pi pi-info-circle" :title="socialCopy.title" data-testid="password-social">
                {{ socialCopy.text }}
                <template #actions>
                    <Button as="router-link" to="/settings/account" :label="t('account.backToAccount')" variant="outlined" severity="secondary" fluid size="large" />
                </template>
            </StatusPanel>
        </div>

        <!-- 입력 -->
        <form v-else class="flex flex-col gap-4 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" novalidate :aria-label="t('account.passwordChange')" @submit.prevent="submit">
            <!-- 시도 제한: 현재 비밀번호를 여러 번 틀려 잠겼다. 잠긴 동안은 입력도 단추도 막는다 -->
            <Message v-if="locked" severity="error" :closable="false" size="small" data-testid="password-locked">
                <b>{{ t('password.lockedTitle') }}</b> {{ t('password.lockedText', { minutes: UtilAccount.minutes(lock.remaining.value) }) }}
            </Message>
            <Message v-else-if="formError" severity="error" :closable="false" size="small" data-testid="password-form-error">{{ formError }}</Message>

            <PasswordField
                ref="currentField"
                v-model="current"
                :label="t('password.currentLabel')"
                name="currentPassword"
                autocomplete="current-password"
                :disabled="busy"
                :error="currentError"
            />

            <PasswordField v-model="next" :label="t('password.newLabel')" name="newPassword" autocomplete="new-password" :disabled="busy" :error="newError">
                <template #under>
                    <!-- 새 비밀번호 규칙 — 가입과 같은 줄(PasswordRules) + 비밀번호 변경에만 있는 「현재 비밀번호와 다르게」. 두 값은 비교만 하고 어디에도 두지 않는다 -->
                    <div class="mt-1.5 flex flex-col gap-0.5">
                        <PasswordRules :password="next" />
                        <ul class="m-0 flex list-none flex-col gap-0.5 p-0 text-xs" :aria-label="t('password.rulesNewLabel')">
                            <li :class="differentRule.cls" data-testid="rule-different">
                                <span aria-hidden="true">{{ differentRule.mark }}</span>
                                {{ t('password.ruleDifferent') }}
                                <span class="sr-only">{{ t(differentRule.sr) }}</span>
                            </li>
                        </ul>
                    </div>
                </template>
            </PasswordField>

            <PasswordField
                v-model="confirmValue"
                :label="t('password.newConfirmLabel')"
                name="newPasswordConfirm"
                autocomplete="new-password"
                :disabled="busy"
                :error="confirmState === 'mismatch' ? t('password.newMismatch') : undefined"
                :success="confirmState === 'match' ? t('password.newMatch') : undefined"
            />

            <Button type="submit" :label="submitting ? t('password.changing') : t('account.passwordChange')" fluid size="large" :loading="submitting" :disabled="!ready" data-testid="password-submit" />

            <p v-if="locked" class="text-xs leading-relaxed text-muted-color">{{ t('password.lockedHelp') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { AccountView } from '~/composables/account/api/useAccountApi.types';
import UtilAccount from '~/utils/util-account';
import type { RuleState } from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';

/**
 * 비밀번호 변경 `/settings/account/password` — 원본(community) `pages/settings/account/password.vue` 를 설정 묶음 안으로 옮겼다. 로그인 필요(`auth` 미들웨어, 서버도 401).
 *
 * 현재 비밀번호 → 새 비밀번호(입력하는 동안 규칙 체크: 가입과 같은 규칙 · 현재 비밀번호와 다르게) → 새 비밀번호 확인. **모두 통과해야** 단추가 켜진다.
 * 오류는 서버 문구를 그대로 쓰고 코드로 갈라 자리에 붙인다 —
 *  - `401 CURRENT_PASSWORD_WRONG` → 현재 비밀번호 칸 아래(로그인 실패와 같은 횟수에 센다) · `400 SAME_PASSWORD`·`VALIDATION`(`fields.newPassword`) → 새 비밀번호 칸 아래
 *  - `429 LOGIN_LOCKED` → 잠금 안내 + 입력·단추 잠금(`useCountdown`, `retryAfterSeconds`) · `409 PASSWORD_NOT_SET` → 「소셜 로그인으로 가입한 계정이에요」 안내
 * 성공하면 결과 화면 + 토스트. **비밀번호는 입력칸 값(이 화면의 세 `ref`)에만 있다** — 상태·주소·콘솔·저장소에 남기지 않고, 성공·잠금 때 칸을 비운다.
 * ⚠ MOCK: `GET /api/me/account`·`PUT /api/me/password` 는 서버에 없어 목업(`useAccountApi`). 목업은 비밀번호를 실제로 바꾸지 않는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('account.passwordChange'), description: () => t('password.changeSeoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useAccountApi();
const toast = useAppToast();

type View = 'loading' | 'error' | 'form' | 'social' | 'done';
const view = ref<View>('loading');
const loadError = ref('');
const account = ref<AccountView | null>(null);

/* ── 입력칸 값 — 비밀번호는 여기에만 있다 ── */
const current = ref('');
const next = ref('');
const confirmValue = ref('');

const submitting = ref(false);
/** 현재 비밀번호 칸 아래 · 새 비밀번호 칸 아래 · 칸에 못 붙이는 오류(폼 위 메시지). 그 칸을 다시 고치면 지운다 */
const currentError = ref('');
const newError = ref('');
const formError = ref('');

const currentField = ref<{ focus: () => void } | null>(null);

/** 시도 제한 — 잠금이 풀리면 현재 비밀번호 칸으로 돌아온다 */
const lock = useCountdown(undefined, () => {
    formError.value = '';
    void nextTick(() => currentField.value?.focus());
});
const locked = computed(() => lock.active.value);
const busy = computed(() => submitting.value || locked.value);

const confirmState = computed(() => UtilAccount.confirmState(next.value, confirmValue.value));
const ready = computed(
    () => view.value === 'form' && !busy.value && !currentError.value && !newError.value && UtilAccount.canChangePassword(current.value, next.value, confirmValue.value),
);

/** 규칙 한 줄의 모양 — `PasswordRules` 와 같다(○ 아직 · ✓ 충족 · ✕ 어긋남). 화면 읽기용 문구는 키 */
const RULE_STYLE: Record<RuleState, { mark: string; cls: string; sr: string }> = {
    idle: { mark: '○', cls: 'text-muted-color', sr: 'login.ruleIdle' },
    ok: { mark: '✓', cls: 'text-green-700 dark:text-green-400', sr: 'login.ruleOk' },
    no: { mark: '✕', cls: 'text-red-600 dark:text-red-400', sr: 'login.ruleNo' },
};
const differentRule = computed(() => RULE_STYLE[UtilAccount.differentFromCurrent(current.value, next.value)]);

const socialCopy = computed(() => UtilAccountMessages.passwordNotSet(t, account.value?.providers ?? []));

watch(current, () => {
    currentError.value = '';
    formError.value = '';
});
watch(next, () => {
    newError.value = '';
    formError.value = '';
});

let loadSeq = 0;
/** 비밀번호가 있는 계정인지(`hasPassword`) 먼저 안다 — 소셜 가입자에게는 폼 대신 안내를 보인다 */
async function load() {
    const mine = ++loadSeq;
    view.value = 'loading';
    loadError.value = '';
    try {
        const value = await api.account();
        if (mine !== loadSeq) return;
        account.value = value;
        view.value = value.hasPassword ? 'form' : 'social';
    } catch (e) {
        if (mine !== loadSeq) return;
        loadError.value = UtilAccountMessages.of(t, e);
        view.value = 'error';
    }
}
void load();
onBeforeUnmount(() => loadSeq++);

/** 세 칸을 비운다 — 성공했거나 잠겼을 때 비밀번호를 화면에 남기지 않는다 */
function clearPasswords() {
    current.value = '';
    next.value = '';
    confirmValue.value = '';
}

async function submit() {
    if (!ready.value) return;
    submitting.value = true;
    currentError.value = '';
    newError.value = '';
    formError.value = '';
    try {
        await api.changePassword({ currentPassword: current.value, newPassword: next.value });
        clearPasswords();
        toast.success(t('password.changedToast'));
        view.value = 'done';
    } catch (e) {
        await onFailed(e);
    } finally {
        submitting.value = false;
    }
}

/** 서버 오류를 코드로 갈라 자리에 붙인다 — 문구는 서버가 준 것 그대로 */
async function onFailed(e: unknown) {
    if (!(e instanceof ApiError)) {
        formError.value = UtilAccountMessages.of(t, e);
        return;
    }
    switch (e.code) {
        case 'CURRENT_PASSWORD_WRONG':
            currentError.value = e.message;
            await nextTick();
            currentField.value?.focus();
            return;
        case 'LOGIN_LOCKED':
            clearPasswords();
            lock.start(UtilAccount.waitSeconds(e.retryAfterSeconds));
            return;
        case 'SAME_PASSWORD':
            newError.value = e.message;
            return;
        case 'PASSWORD_NOT_SET':
            clearPasswords();
            view.value = 'social';
            return;
        case 'VALIDATION':
            currentError.value = e.field('currentPassword') ?? '';
            newError.value = e.field('newPassword') ?? '';
            if (!currentError.value && !newError.value) formError.value = e.message;
            return;
        default:
            formError.value = e.message;
    }
}
</script>
