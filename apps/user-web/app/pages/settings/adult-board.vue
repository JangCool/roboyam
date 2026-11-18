<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 설정」 -->
    <div class="content-card" data-testid="adult-board-page">
        <h1 class="mb-4 text-lg font-bold">{{ t('adultBoard.title') }}</h1>

        <!-- 본인인증 상태를 아직 모른다 · 접근 설정을 받는 중 -->
        <div v-if="view === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('adultBoard.loading')">
            <div class="h-16 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="view === 'error'" :title="t('adultBoard.loadFailed')" :description="errorText" retry :retrying="retrying" data-testid="adult-board-error" @retry="retry" />

        <!-- 본인인증 성인이 아닌데 주소로 왔다 — 메뉴에는 없다. 본인인증 화면으로 안내 -->
        <div v-else-if="view === 'notAdult'" class="panel-card px-4 py-3.5" data-testid="adult-board-not-adult">
            <p class="m-0 text-sm font-medium">{{ t('adultBoard.notAdultTitle') }}</p>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('adultBoard.notAdultText') }}</p>
            <Button as="router-link" to="/settings/identity" :label="t('adultBoard.notAdultButton')" icon="pi pi-id-card" class="mt-3" data-testid="adult-board-to-identity" />
        </div>

        <template v-else-if="status">
            <!-- 접근 스위치 — 켜기는 대화상자(계정 비밀번호 + 핀번호), 끄기는 확인 한 번 -->
            <section class="mb-6" :aria-labelledby="`${uid}-access`">
                <div class="panel-card">
                    <label :for="`${uid}-switch`" class="flex min-h-14 cursor-pointer items-center gap-3 px-4 py-3">
                        <span class="min-w-0 flex-1">
                            <span :id="`${uid}-access`" class="block text-sm font-medium">{{ t('adultBoard.accessLabel') }}</span>
                            <small class="block text-xs text-muted-color">{{ status.enabled ? t('adultBoard.accessOn') : t('adultBoard.accessOff') }}</small>
                        </span>
                        <ToggleSwitch
                            :model-value="switchOn"
                            :input-id="`${uid}-switch`"
                            :disabled="toggling"
                            data-testid="adult-board-switch"
                            @update:model-value="onToggle(Boolean($event))"
                        />
                    </label>
                </div>
                <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color" data-testid="adult-board-note">{{ status.enabled ? t('adultBoard.onNote') : status.passwordAvailable ? t('adultBoard.offNote') : t('adultBoard.offNoteSocial') }}</p>
            </section>

            <template v-if="status.enabled">
                <!-- 핀번호 — 정한 날 + [핀번호 바꾸기](잊었을 때 다시 정하기도 같은 대화상자) -->
                <section class="mb-6" :aria-labelledby="`${uid}-pin`">
                    <h2 :id="`${uid}-pin`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('adultBoard.pinTitle') }}</h2>
                    <div class="flex min-h-14 flex-wrap items-center gap-3 panel-card px-4 py-3">
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ t('adultBoard.pinLabel') }}</span>
                            <small class="block text-xs text-muted-color" data-testid="adult-board-pin-set-at">{{ status.pinSetAt ? t('adultBoard.pinSetAt', { date: time.date(status.pinSetAt) }) : t('adultBoard.pinSet') }}</small>
                        </span>
                        <Button type="button" :label="t('adultBoard.pinChange')" size="small" severity="secondary" variant="outlined" data-testid="adult-board-pin-change" @click="openDialog('change')" />
                    </div>
                    <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ status.passwordAvailable ? t('adultBoard.pinHelp') : t('adultBoard.pinHelpSocial') }}</p>
                </section>

                <!-- 핀번호 다시 묻기 — 둘 중 하나, 고르면 바로 저장 -->
                <section class="mb-6" :aria-labelledby="`${uid}-relock`">
                    <h2 :id="`${uid}-relock`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('adultBoard.relockTitle') }}</h2>
                    <div role="radiogroup" :aria-labelledby="`${uid}-relock`" class="panel-card" data-testid="adult-board-relock">
                        <label
                            v-for="option in relockOptions"
                            :key="option.value"
                            :for="`${uid}-relock-${option.value}`"
                            class="flex min-h-14 cursor-pointer items-start gap-3 border-b border-surface px-4 py-3 last:border-b-0 hover:bg-surface-100 dark:hover:bg-surface-800"
                            :class="relockMode === option.value ? 'bg-primary-50 dark:bg-primary-950/40' : ''"
                            :data-testid="`adult-board-relock-${option.value}`"
                        >
                            <RadioButton
                                :model-value="relockMode"
                                :value="option.value"
                                :input-id="`${uid}-relock-${option.value}`"
                                name="relockMode"
                                class="mt-0.5 shrink-0"
                                :disabled="relockSaving"
                                @update:model-value="chooseRelock(option.value)"
                            />
                            <span class="min-w-0 flex-1">
                                <span class="block text-sm font-medium">{{ option.title }}</span>
                                <small class="block text-xs text-muted-color">{{ option.hint }}</small>
                            </span>
                        </label>
                    </div>
                </section>

                <!-- 지금 잠그기 — 이 기기의 핀 확인을 바로 없앤다 -->
                <section :aria-labelledby="`${uid}-lock`">
                    <h2 :id="`${uid}-lock`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('adultBoard.lockTitle') }}</h2>
                    <div class="flex min-h-14 flex-wrap items-center gap-3 panel-card px-4 py-3" data-testid="adult-board-lock-row" :data-state="unlocked ? 'open' : 'locked'">
                        <i :class="[unlocked ? 'pi pi-lock-open' : 'pi pi-lock', 'text-muted-color']" aria-hidden="true" />
                        <span class="min-w-0 flex-1">
                            <span class="block text-sm font-medium">{{ unlocked ? t('adultBoard.lockOpen') : t('adultBoard.lockClosed') }}</span>
                            <small class="block text-xs text-muted-color">
                                {{ unlocked ? t(status.relockMode === 'IDLE' ? 'adultBoard.lockOpenIdle' : 'adultBoard.lockOpenFixed', { minutes: UtilAdultBoard.minutes(open.remaining.value) }) : t('adultBoard.lockClosedText') }}
                            </small>
                        </span>
                        <Button v-if="unlocked" type="button" :label="t('adultBoard.lockNow')" icon="pi pi-lock" size="small" severity="secondary" variant="outlined" :loading="locking" data-testid="adult-board-lock-now" @click="lockNow" />
                    </div>
                    <p v-if="pinBlocked" class="mt-2 px-1 text-xs leading-relaxed text-red-600 dark:text-red-400" data-testid="adult-board-pin-blocked">
                        {{ t('adultBoard.pinBlocked', { minutes: UtilAdultBoard.minutes(blocked.remaining.value) }) }}
                    </p>
                </section>
            </template>
        </template>

        <!-- 켜기 · 핀번호 바꾸기(잊었을 때 다시 정하기) — 계정 비밀번호 · 핀번호 6칸 · 한 번 더. 값은 이 대화상자 메모리에만 있고 닫을 때 비운다.
             계정 비밀번호가 없는 회원(소셜 로그인만)은 비밀번호 칸 대신 「최근 10분 안 로그인」 안내 — 서버가 아니라고 하면(401 REAUTH_REQUIRED) 다시 로그인 안내 + 로그인으로 -->
        <Dialog
            :visible="dialog !== null"
            modal
            :draggable="false"
            :closable="!submitting"
            :close-on-escape="!submitting"
            :header="dialog === 'change' ? t('adultBoard.dialogChangeTitle') : t('adultBoard.dialogEnableTitle')"
            :style="{ width: 'min(26rem, calc(100vw - 2rem))' }"
            data-testid="adult-board-dialog"
            @update:visible="onDialogVisible"
        >
            <form :id="`${uid}-form`" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
                <p class="m-0 text-sm leading-relaxed text-muted-color">{{ dialogText }}</p>
                <Message v-if="formError" severity="error" variant="simple" size="small" :closable="false" data-testid="adult-board-dialog-error">{{ formError }}</Message>

                <template v-if="needsPassword">
                    <!-- 계정 비밀번호를 여러 번 틀려 잠겼다(429 LOGIN_LOCKED — 로그인 실패와 같은 횟수) — 남은 시간과 함께, 풀릴 때까지 칸·단추를 잠근다 -->
                    <Message v-if="locked" severity="error" :closable="false" size="small" data-testid="adult-board-dialog-locked">
                        <b>{{ t('password.lockedTitle') }}</b> {{ t('password.lockedText', { minutes: UtilAdultBoard.minutes(lock.remaining.value) }) }}
                    </Message>
                    <PasswordField ref="passwordField" v-model="password" :label="t('adultBoard.passwordLabel')" name="password" autocomplete="current-password" :disabled="busy" :error="errors.password" />
                </template>
                <!-- 계정 비밀번호가 없는 회원 — 최근 10분 안에 로그인했는지로 확인한다(서버 판정). 아니라고 하면 다시 로그인 -->
                <template v-else-if="reauthNeeded">
                    <Message severity="warn" :closable="false" size="small" data-testid="adult-board-dialog-reauth">{{ t('adultBoard.reauthNeeded') }}</Message>
                    <Button as="router-link" :to="reloginPath" :label="t('adultBoard.reauthLogin')" variant="outlined" severity="secondary" fluid data-testid="adult-board-dialog-relogin" />
                </template>
                <Message v-else severity="info" :closable="false" size="small" data-testid="adult-board-dialog-no-password">{{ t('adultBoard.noPasswordNote') }}</Message>

                <LabeledField :label="dialog === 'change' ? t('adultBoard.pinNewLabel') : t('adultBoard.pinLabel')" :help="t('adultBoard.pinRule')" :error="errors.pin">
                    <template #default="{ id, describedBy, invalid }">
                        <div :id="id" role="group" :aria-describedby="describedBy">
                            <InputOtp v-model="pin" :length="UtilAdultBoard.PIN_LENGTH" mask integer-only :invalid="invalid" :disabled="busy" data-testid="adult-board-dialog-pin" />
                        </div>
                    </template>
                </LabeledField>

                <LabeledField :label="t('adultBoard.pinConfirmLabel')" :error="errors.pinConfirm">
                    <template #default="{ id, describedBy, invalid }">
                        <div :id="id" role="group" :aria-describedby="describedBy">
                            <InputOtp v-model="pinConfirm" :length="UtilAdultBoard.PIN_LENGTH" mask integer-only :invalid="invalid" :disabled="busy" data-testid="adult-board-dialog-pin-confirm" />
                        </div>
                    </template>
                </LabeledField>
            </form>

            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="submitting" @click="closeDialog" />
                <Button type="submit" :form="`${uid}-form`" :label="dialog === 'change' ? t('adultBoard.dialogChangeSubmit') : t('adultBoard.dialogEnableSubmit')" :loading="submitting" :disabled="locked" data-testid="adult-board-dialog-submit" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import { ADULT_BOARD_RELOCK_MODES, type AdultBoardRelockMode } from '~/composables/adultboard/api/useAdultBoardApi.types';
import UtilAdultBoard from '~/utils/util-adult-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilRedirect from '~/utils/util-redirect';

/**
 * 설정 › 19금 게시판 `/settings/adult-board` — 로그인 필요(`auth` 미들웨어). 계약: `docs/features/adult-board-pin-contract.md`.
 * 설정 메뉴에는 본인인증 성인(`useIdentity().status.adult`)에게만 보이고, 성인이 아닌데 주소로 오면 본인인증 안내만 그린다(접근 설정은 묻지 않는다).
 *
 * - 꺼짐: 「19금 게시판 접근」 스위치 + 안내. 켜면 대화상자(계정 비밀번호 · 핀번호 6칸 · 한 번 더) — 성공해야 스위치가 켜진다.
 * - 켜짐: 스위치(끄기는 확인 한 번, 비밀번호 없음) · 핀번호(정한 날 · [핀번호 바꾸기]) · 핀번호 다시 묻기(`IDLE`/`FIXED`, 고르면 바로 저장) · 지금 잠그기(열려 있으면 남은 시간 + [잠그기]).
 * - 주소에 `?reset=1`(핀 입력 화면의 [핀번호를 잊었어요])이면 켜져 있을 때 다시 정하기 대화상자를 바로 열고 쿼리를 지운다.
 * - 본인 확인(켜기·핀번호 바꾸기): 비밀번호 계정은 계정 비밀번호, 계정 비밀번호가 없는 회원(`passwordAvailable: false` — 소셜 로그인만)은 칸 대신
 *   「최근 10분 안에 로그인했는지로 확인해요」 안내만 두고 `password` 없이 보낸다(탈퇴 화면과 같은 규칙).
 * - 서버 오류는 코드로 갈라 자리에 붙인다(문구는 서버 것) — `400 VALIDATION` `fields.password`·`fields.pin` · `401 CURRENT_PASSWORD_WRONG` → 칸 아래,
 *   `429 LOGIN_LOCKED` → 잠금 안내(남은 시간) + 칸·단추 잠금(`useCountdown`, `retryAfterSeconds`), `401 REAUTH_REQUIRED` → 「다시 로그인한 뒤 해 주세요」 + [다시 로그인](`/login?next=/settings/adult-board`),
 *   `409 ADULT_BOARD_DISABLED`(핀 바꾸기 — 그새 다른 기기에서 껐다) → 대화상자를 닫고 상태를 다시 받는다, 그 밖(`403 IDENTITY_*` 등)은 대화상자 위 한 줄.
 *
 * API 는 `useAdultBoard`(→ `useAdultBoardApi`). 계정 비밀번호·핀번호는 이 화면의 `ref` 에만 있고 대화상자를 닫을 때 비운다.
 * 켜기·끄기 뒤 메뉴·게시판 목록은 `useAdultBoard` 가 다시 받는다(성인 전용 게시판이 메뉴에 들어오고 빠진다).
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('adultBoard.title'), description: () => t('adultBoard.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const router = useRouter();
const identity = useIdentity();
const adultBoard = useAdultBoard();
const { status } = adultBoard;
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const uid = useId();

/* ── 화면 상태 — 본인인증(성인 여부) 먼저, 성인이면 접근 설정 ── */
type View = 'loading' | 'error' | 'notAdult' | 'ready';
const view = computed<View>(() => {
    const id = identity.loadStatus.value;
    if (id === 'error' && !identity.status.value) return 'error';
    if (!identity.status.value) return 'loading';
    if (!identity.status.value.adult) return 'notAdult';
    const own = adultBoard.loadStatus.value;
    if (own === 'error' && !status.value) return 'error';
    return status.value ? 'ready' : 'loading';
});
const errorText = computed(() => (identity.status.value ? adultBoard.loadError.value : identity.loadError.value));
const retrying = ref(false);

/** 주소의 `?reset=1` — 처음 한 번만 다시 정하기 대화상자를 연다 */
let resetWanted = route.query.reset === '1';

async function load(): Promise<void> {
    const found = await identity.load();
    if (!found?.adult) return;
    const own = await adultBoard.load();
    if (!resetWanted) return;
    resetWanted = false;
    void router.replace({ path: route.path, query: {} });
    if (own?.enabled) openDialog('change');
}

async function retry(): Promise<void> {
    retrying.value = true;
    await load();
    retrying.value = false;
}

/* ── 남은 시간 — 핀 확인(열림)과 틀림 잠금. 끝나면 상태를 다시 받는다 ── */
const openEndsAt = ref(0);
const blockedEndsAt = ref(0);
const open = useCountdown(openEndsAt, () => void adultBoard.load());
const blocked = useCountdown(blockedEndsAt, () => void adultBoard.load());
watch(
    () => [status.value?.unlocked ? status.value.unlockedUntil : null, status.value?.lockedUntil ?? null] as const,
    ([unlockedUntil, lockedUntil]) => {
        openEndsAt.value = UtilAdultBoard.millis(unlockedUntil);
        blockedEndsAt.value = UtilAdultBoard.millis(lockedUntil);
    },
    { immediate: true },
);
const unlocked = computed(() => status.value?.unlocked === true && open.active.value);
const pinBlocked = computed(() => blocked.active.value);

/* ── 스위치 · 다시 묻기 라디오에 보이는 값 — 서버 상태를 따라가되, 누른 직후에는 누른 값을 먼저 보이고 취소·실패하면 되돌린다
      (PrimeVue 입력은 누르면 자기 안의 값을 먼저 바꾸므로, 넘겨 준 값이 실제로 바뀌어야 되돌아간다) ── */
const switchOn = ref(false);
const relockMode = ref<AdultBoardRelockMode>('IDLE');
function syncInputs(): void {
    switchOn.value = status.value?.enabled === true;
    relockMode.value = status.value?.relockMode ?? 'IDLE';
}
watch(status, syncInputs, { immediate: true });

/* ── 스위치 ── */
const toggling = ref(false);

async function onToggle(next: boolean): Promise<void> {
    if (!status.value || toggling.value || next === status.value.enabled) return;
    switchOn.value = next;
    // 켜기 — 대화상자가 성공해야 켜진다(닫으면 `closeDialog` 가 스위치를 되돌린다)
    if (next) return openDialog('enable');
    if (!(await confirm({ title: t('adultBoard.offConfirmTitle'), message: t('adultBoard.offConfirmText'), confirmLabel: t('adultBoard.offConfirmButton') }))) return syncInputs();
    toggling.value = true;
    try {
        await adultBoard.disable();
        toast.success(t('adultBoard.offDone'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        toggling.value = false;
        syncInputs();
    }
}

/* ── 핀번호 다시 묻기 ── */
const relockOptions = computed(() =>
    ADULT_BOARD_RELOCK_MODES.map((value) => ({
        value,
        title: value === 'IDLE' ? t('adultBoard.relockIdle') : t('adultBoard.relockFixed'),
        hint: value === 'IDLE' ? t('adultBoard.relockIdleHint') : t('adultBoard.relockFixedHint'),
    })),
);
const relockSaving = ref(false);

async function chooseRelock(value: AdultBoardRelockMode): Promise<void> {
    if (!status.value || relockSaving.value || value === status.value.relockMode) return;
    relockSaving.value = true;
    relockMode.value = value;
    try {
        await adultBoard.setRelockMode(value);
        toast.success(t('adultBoard.relockSaved'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        relockSaving.value = false;
        syncInputs();
    }
}

/* ── 지금 잠그기 ── */
const locking = ref(false);

async function lockNow(): Promise<void> {
    if (locking.value) return;
    locking.value = true;
    try {
        await adultBoard.lock();
        toast.success(t('adultBoard.lockDone'));
    } catch (e) {
        toast.error(UtilBoardMessages.of(t, e), 8000);
    } finally {
        locking.value = false;
    }
}

/* ── 켜기 · 핀번호 바꾸기 대화상자 — 계정 비밀번호·핀번호는 여기에만 있다 ── */
type DialogMode = 'enable' | 'change';
const dialog = ref<DialogMode | null>(null);
const password = ref('');
const pin = ref('');
const pinConfirm = ref('');
const submitting = ref(false);
const formError = ref('');
const errors = reactive<{ password: string; pin: string; pinConfirm: string }>({ password: '', pin: '', pinConfirm: '' });
const passwordField = ref<{ focus: () => void } | null>(null);

/** 계정 비밀번호로 확인하는 회원인가 — 아니면(소셜 로그인만) 비밀번호 칸 없이 서버가 「최근 10분 안 로그인」으로 확인한다 */
const needsPassword = computed(() => status.value?.passwordAvailable !== false);
const dialogText = computed(() => {
    if (dialog.value === 'change') return needsPassword.value ? t('adultBoard.dialogChangeText') : t('adultBoard.dialogChangeTextSocial');
    return needsPassword.value ? t('adultBoard.dialogEnableText') : t('adultBoard.dialogEnableTextSocial');
});
/** 계정 비밀번호를 여러 번 틀려 잠겼다(`429 LOGIN_LOCKED`) — 풀리면 오류를 걷고 칸으로 돌아온다. 대화상자를 닫았다 열어도 남은 시간은 이어진다 */
const lock = useCountdown(undefined, () => {
    errors.password = '';
    void nextTick(() => passwordField.value?.focus());
});
const locked = computed(() => needsPassword.value && lock.active.value);
const busy = computed(() => submitting.value || locked.value);
/** 비밀번호가 없는 회원인데 최근 로그인이 아니다(`401 REAUTH_REQUIRED`) — 다시 로그인 안내로 바뀐다 */
const reauthNeeded = ref(false);
/** 다시 로그인하고 돌아올 곳 — 이 화면 */
const reloginPath = UtilRedirect.toLogin(UtilAdultBoard.SETTINGS_PATH);

function clearDialog(): void {
    password.value = '';
    pin.value = '';
    pinConfirm.value = '';
    formError.value = '';
    // 잠금 중이면 잠금 문구는 남긴다(풀릴 때 걷힌다)
    if (!locked.value) errors.password = '';
    errors.pin = '';
    errors.pinConfirm = '';
    reauthNeeded.value = false;
}

function openDialog(mode: DialogMode): void {
    clearDialog();
    dialog.value = mode;
}

function closeDialog(): void {
    if (submitting.value) return;
    dialog.value = null;
    clearDialog();
    syncInputs();
}

function onDialogVisible(visible: boolean): void {
    if (!visible) closeDialog();
}

// 칸을 고치기 시작하면 그 칸의 오류를 걷는다
watch(password, () => {
    if (!locked.value) errors.password = '';
});
watch(pin, () => (errors.pin = ''));
watch(pinConfirm, () => (errors.pinConfirm = ''));

async function submit(): Promise<void> {
    const mode = dialog.value;
    if (!mode || busy.value) return;
    formError.value = '';
    errors.password = !needsPassword.value || password.value ? '' : t('adultBoard.passwordRequired');
    errors.pin = UtilAdultBoard.isPin(pin.value) ? '' : t('adultBoard.pinInvalid');
    errors.pinConfirm = errors.pin || pinConfirm.value === pin.value ? '' : t('adultBoard.pinMismatch');
    if (errors.password || errors.pin || errors.pinConfirm) return;

    submitting.value = true;
    try {
        // 계정 비밀번호가 없는 회원은 `password` 없이 보낸다(서버가 최근 로그인으로 확인)
        const secret = needsPassword.value ? password.value : null;
        if (mode === 'enable') await adultBoard.enable(secret, pin.value);
        else await adultBoard.changePin(secret, pin.value);
        submitting.value = false;
        closeDialog();
        toast.success(mode === 'enable' ? t('adultBoard.onDone') : t('adultBoard.pinChanged'));
    } catch (e) {
        await onFailed(e);
    } finally {
        submitting.value = false;
    }
}

/** 서버 오류를 코드로 갈라 자리에 붙인다 — 문구는 서버가 준 것 그대로(탈퇴·비밀번호 변경 화면과 같은 방식) */
async function onFailed(e: unknown): Promise<void> {
    if (!(e instanceof ApiError)) {
        formError.value = UtilBoardMessages.of(t, e);
        return;
    }
    switch (e.code) {
        case 'CURRENT_PASSWORD_WRONG':
            errors.password = e.message;
            await nextTick();
            passwordField.value?.focus();
            return;
        case 'LOGIN_LOCKED':
            // 계정 비밀번호를 여러 번 틀렸다(로그인 실패와 같은 횟수) — 비밀번호를 비우고 남은 시간 동안 잠근다
            password.value = '';
            await nextTick();
            errors.password = e.message;
            lock.start(typeof e.retryAfterSeconds === 'number' && e.retryAfterSeconds > 0 ? e.retryAfterSeconds : UtilAdultBoard.DEFAULT_LOCK_SECONDS);
            return;
        case 'REAUTH_REQUIRED':
            // 비밀번호가 없는 회원인데 최근 10분 안에 로그인하지 않았다 — 다시 로그인 안내 + 로그인으로 가는 길
            reauthNeeded.value = true;
            return;
        case UtilAdultBoard.CODE_DISABLED:
            // 핀 바꾸기인데 접근이 꺼져 있다(그새 다른 기기에서 껐다) — 화면이 낡았으니 닫고 다시 받는다(메뉴·게시판 목록도 따라 바뀐다)
            submitting.value = false;
            closeDialog();
            toast.error(e.message, 8000);
            await adultBoard.load();
            return;
        case 'VALIDATION':
            if (e.field('password') || e.field('pin')) {
                errors.password = e.field('password') ?? '';
                errors.pin = e.field('pin') ?? '';
                return;
            }
            formError.value = UtilBoardMessages.of(t, e);
            return;
        default:
            formError.value = UtilBoardMessages.of(t, e);
    }
}

void load();
</script>
