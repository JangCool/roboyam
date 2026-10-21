<template>
    <div>
        <!-- 설치 상태를 받지 못했다 — 마법사를 시작할 수 없다. 앱을 막는 것이 아니라 여기서 다시 시도한다 -->
        <ErrorState
            v-if="phase === 'failed'"
            :title="t('install.statusFailedTitle')"
            :description="t('install.statusFailedDescription')"
            retry
            :retrying="statusBusy"
            data-testid="install-status-error"
            @retry="retryStatus"
        />

        <!-- 설치 완료 -->
        <InstallDone v-else-if="done" :email="doneEmail" @login="goLogin" />

        <!-- 마법사 — 단계 막대의 버튼 하나(`submit`)가 단계마다 확인·다음·설치를 맡는다. Enter 로도 같다 -->
        <form v-else-if="phase === 'pending'" ref="formEl" novalidate data-testid="install-form" @submit.prevent="submitStep">
            <div>
                <p class="text-xs text-muted-color" data-testid="install-progress">{{ UtilInstallMessages.progress(t, step) }}</p>
                <ProgressBar :value="((UtilInstall.stepIndex(step) + 1) / UtilInstall.STEPS.length) * 100" :show-value="false" :aria-label="t('install.progressLabel')" style="height: 6px" class="mt-2" />
            </div>

            <h1 ref="heading" tabindex="-1" class="mt-4 text-lg font-semibold" data-testid="install-title">{{ copy.title }}</h1>
            <p class="mt-1 text-sm leading-relaxed text-muted-color">{{ copy.description }}</p>

            <div class="mt-4">
                <InstallTokenStep
                    v-if="step === 'token'"
                    v-model="token"
                    :authorized="authorized"
                    :busy="tokenBusy"
                    :error="tokenError"
                    :lock-remaining="lock.remaining.value"
                    :notice="notice"
                    :checks="checks"
                    :checks-busy="checksBusy"
                    :checks-error="checksError"
                    @recheck="loadChecks"
                />
                <InstallRootStep v-else-if="step === 'root'" :model-value="root" :errors="errors" :disabled="installing" />
                <InstallSiteStep v-else-if="step === 'site'" :model-value="site" :errors="errors" :disabled="installing" />
                <InstallMailStep
                    v-else-if="step === 'mail'"
                    :model-value="mail"
                    v-model:mode="mailMode"
                    :crypto-ok="cryptoOk"
                    :errors="errors"
                    :check="mailCheck"
                    :disabled="installing"
                    @check="runMailCheck"
                />
                <InstallSocialStep v-else-if="step === 'social'" :model-value="social" v-model:mode="socialMode" :crypto-ok="cryptoOk" :public-url="site.publicUrl" :errors="errors" :disabled="installing" />
                <InstallBoardsStep
                    v-else-if="step === 'boards'"
                    v-model="boardPreset"
                    :presets="boardPresets"
                    :default-id="boardPresetDefault"
                    :busy="presetsBusy"
                    :error="presetsError"
                    :disabled="installing"
                    @retry="loadBoardPresets"
                />
                <InstallReviewStep v-else :sections="sections" :error="submitError" :unplaced="unplaced" :disabled="installing" @edit="goTo" />
            </div>

            <div class="mt-6 flex items-center gap-2">
                <Button v-if="step !== 'token'" type="button" :label="t('common.prev')" icon="pi pi-angle-left" severity="secondary" variant="outlined" :disabled="installing" data-testid="install-prev" @click="prev" />
                <span class="flex-1" />
                <Button v-if="canSkip" type="button" :label="t('common.skip')" severity="secondary" variant="text" :disabled="installing" data-testid="install-skip" @click="skip" />
                <Button type="submit" :label="primary.label" :loading="primary.loading" :disabled="primary.disabled" data-testid="install-next" />
            </div>
            <p v-if="step === 'review' && installing" class="mt-2 text-center text-xs text-muted-color" role="status">{{ t('install.installing') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { SocialProviderCode } from '~/composables/install/api/useInstallApi.types';
import type { InstallBoardPreset, InstallChecksView } from '~/composables/install/api/useInstallApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilInstall, { type InstallFieldErrors, type InstallMode, type InstallStepKey, type RootForm, type SocialForms } from '~/utils/util-install';
import UtilInstallMessages from '~/utils/util-install-messages';
import UtilMail, { type MailCheckState, type MailForm } from '~/utils/util-mail';
import UtilMailMessages from '~/utils/util-mail-messages';
import UtilSite, { type SiteForm } from '~/utils/util-site';

/**
 * 설치 마법사 — `/install` (셸 없음, 로그인 없이 쓰는 구간, docs/api-install.md).
 * 1 설치 토큰 + 점검 결과 → 2 루트 계정 → 3 사이트 → 4 메일 서버(건너뛰기) → 5 소셜 로그인 키(건너뛰기) → 6 기본 게시판 구성 → 7 확인 → 설치 → 완료(로그인으로).
 *
 * - **설치 전에만 열린다**: 설치 전이면 모든 주소가 여기로 오고(`install.global.ts`), 설치 후에는 홈으로 간다. 서버도 설치 후에는 마법사 API 를 닫는다(`409 ALREADY_INSTALLED` → 홈).
 * - **토큰을 맞혀야 다음 단계로 간다.** 점검 결과에 막는 실패가 있으면 못 간다. 암호화 키가 없다는 경고는 가능하지만 메일 아이디·비밀번호와 소셜 단계는 잠긴다.
 *   `429 INSTALL_TOKEN_LOCKED` 는 남은 시간을 세고, `401 INSTALL_AUTH_REQUIRED`(세션 만료)는 1단계로 되돌린다(입력해 둔 내용은 남긴다).
 * - 입력값 객체(`root`·`site`·`mail`·`social`)는 `:model-value` 로 넘기고 단계 컴포넌트가 그 객체의 칸을 직접 고친다(`reactive` 상수라 `v-model` 로 다시 대입하지 않는다).
 * - **입력은 이 화면의 메모리에만 있다.** `localStorage`·`sessionStorage`·URL·`useState` 에 두지 않는다 — 새로고침하면 사라지고, 설치가 끝나면 비운다. 비밀번호·시크릿·토큰은 로그·토스트에도 남기지 않는다.
 * - 서버가 `400 VALIDATION` 으로 거절하면 `fields` 의 경로(`root.email` · `site.publicUrl` · `mail.port` · `social.providers[0].clientId` …)를 그 입력 아래에 보이고 가장 앞 단계로 돌아간다.
 * - 진행 중에는 버튼을 잠가 중복 클릭을 막는다(설치는 서버도 한 번만 실행한다).
 */
definePageMeta({ layout: 'install' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('install.pageTitle'), description: () => t('install.pageDescription', { site: siteLabel.value }), robots: 'noindex,nofollow' });

const api = useInstallApi();
const { phase, authorized, refresh, markInstalled, setAuthorized } = useInstallState();

const step = ref<InstallStepKey>('token');
const copy = computed(() => UtilInstallMessages.step(t, step.value));
const done = ref(false);
const doneEmail = ref('');

const formEl = ref<HTMLElement | null>(null);
const heading = ref<HTMLElement | null>(null);

/* ── 입력 — 이 화면의 메모리에만 둔다 ── */
const token = ref('');
const root = reactive<RootForm>(UtilInstall.initialRoot());
const site = reactive<SiteForm>(UtilInstall.initialSite());
const mailMode = ref<InstallMode>('set');
const mail = reactive<MailForm>(UtilMail.installForm());
const socialMode = ref<InstallMode>('set');
const social = reactive<SocialForms>(UtilInstall.initialSocial());
/** 고른 기본 게시판 구성의 아이디 — 목록을 받으면 서버 기본값이 들어온다. 못 받았으면 빈 글자(요청에 담지 않아 서버가 기본 구성을 쓴다) */
const boardPreset = ref('');

/** 칸 경로(`root.email` …) → 문구. 화면이 미리 검사한 것과 서버가 거절한 것이 같은 곳에 든다 */
const errors = ref<InstallFieldErrors>({});
/** 설치 요청이 거절된 까닭 중 칸에 붙지 않는 것 */
const submitError = ref('');
const unplaced = computed(() => UtilInstall.unplacedMessages(errors.value));

/* ── 1단계: 설치 토큰 · 점검 ── */
const tokenBusy = ref(false);
const tokenError = ref('');
const notice = ref('');
const lock = useCountdown();
const checks = ref<InstallChecksView | null>(null);
const checksBusy = ref(false);
const checksError = ref('');

/* ── 6단계: 기본 게시판 구성 목록(토큰 인증 뒤에 받는다) ── */
const boardPresets = ref<InstallBoardPreset[]>([]);
const boardPresetDefault = ref('');
const presetsBusy = ref(false);
const presetsError = ref('');
let presetsSeq = 0;
/** 서버가 암호화 키 없음(`503`)으로 설치를 거절했다 */
const cryptoMissing = ref(false);
const cryptoOk = computed(() => !cryptoMissing.value && UtilInstall.cryptoKeyOk(checks.value));

/* ── 진행 상태 ── */
const installing = ref(false);
const statusBusy = ref(false);
const mailCheck = ref<MailCheckState>(UtilMail.idleCheck());
let checksSeq = 0;
let mailCheckSeq = 0;

const sections = computed(() =>
    UtilInstallMessages.review(t, {
        root,
        site,
        mailMode: mailMode.value,
        mail,
        socialMode: socialMode.value,
        social,
        boardPreset: boardPreset.value,
        boardPresets: boardPresets.value,
    }),
);

/** 단계 막대의 건너뛰기 — 메일·소셜 단계에서, 입력칸이 열려 있을 때만(암호화 키가 없으면 소셜은 늘 건너뛴 상태라 버튼이 없다) */
const canSkip = computed(() => (step.value === 'mail' && mailMode.value === 'set') || (step.value === 'social' && socialMode.value === 'set'));

/** 단계 막대의 오른쪽 큰 버튼 — 토큰 인증 전에는 "확인", 마지막에는 "설치", 나머지는 "다음" */
const primary = computed(() => {
    if (step.value === 'token') {
        return authorized.value
            ? { label: t('common.next'), loading: false, disabled: checksBusy.value || !UtilInstall.canProceed(checks.value) }
            : { label: t('common.confirm'), loading: tokenBusy.value, disabled: tokenBusy.value || lock.active.value };
    }
    if (step.value === 'review') return { label: t('install.installButton'), loading: installing.value, disabled: installing.value };
    return { label: t('common.next'), loading: false, disabled: installing.value };
});

/* ── 입력을 고치면 그 칸의 오류는 걷는다 ── */
function dropError(key: string) {
    if (!(key in errors.value)) return;
    const rest = { ...errors.value };
    delete rest[key];
    errors.value = rest;
}
watch(token, () => (tokenError.value = ''));
for (const field of UtilInstall.ROOT_FIELDS) watch(() => root[field], () => dropError(`root.${field}`));
watch(() => site.name, () => dropError('site.name'));
watch(() => site.publicUrl, () => dropError('site.publicUrl'));
watch(() => site.tagline, () => dropError('site.tagline'));
for (const field of ['host', 'port', 'security', 'username', 'password', 'from'] as const) watch(() => mail[field], () => dropError(`mail.${field}`));
for (const code of UtilInstall.PROVIDERS) {
    watch(() => social[code].clientId, () => dropError(`social.${code}.clientId`));
    watch(() => social[code].clientSecret, () => dropError(`social.${code}.clientSecret`));
    // 스위치를 끄면 그 공급자는 검사하지 않으므로 남아 있던 입력 오류도 걷는다
    watch(() => social[code].enabled, () => {
        dropError(`social.${code}.clientId`);
        dropError(`social.${code}.clientSecret`);
    });
}
// 메일 입력을 고치면 연결 확인 결과는 지운다(그 결과는 고치기 전 입력에 대한 것이다). 하는 중이던 확인의 답도 버린다
watch(
    () => [mail.host, mail.port, mail.security, mail.username, mail.password, mail.from],
    () => {
        mailCheckSeq++;
        mailCheck.value = UtilMail.idleCheck();
    },
);
// 암호화 키가 없으면 인증 정보·소셜 키를 받을 수 없다 — 이미 입력한 것은 비우고 건너뛴 것으로 바꾼다
watch(cryptoOk, (ok) => {
    if (ok) return;
    socialMode.value = 'skip';
    mail.username = '';
    mail.password = '';
});

// 단계가 바뀌면 제목으로 초점을 옮겨 스크린리더가 새 단계를 읽게 한다
watch(step, async () => {
    await nextTick();
    heading.value?.focus({ preventScroll: true });
    window.scrollTo({ top: 0 });
});

onMounted(() => {
    // 새로고침했는데 서버 세션이 설치 인증을 이어 가고 있다 — 토큰을 다시 묻지 않고 점검 결과부터 보인다
    if (phase.value === 'pending' && authorized.value) {
        void loadChecks();
        void loadBoardPresets();
    }
});

/* ── 서버 오류의 공통 처리 ── */

/** 마법사 오류 중 단계와 상관없는 것. 처리했으면 true — 이미 설치됨(홈으로) · 설치 인증 만료(1단계로) */
function handledCommonly(e: unknown): boolean {
    if (UtilInstall.isAlreadyInstalled(e)) {
        markInstalled();
        void navigateTo('/', { replace: true });
        return true;
    }
    if (UtilInstall.isAuthRequired(e)) {
        setAuthorized(false);
        checks.value = null;
        token.value = '';
        notice.value = t('install.sessionExpired');
        step.value = 'token';
        return true;
    }
    return false;
}

/* ── 1단계 동작 ── */

/** 설치 토큰 확인 — 맞히면 값을 비우고 점검 결과를 받는다 */
async function verify() {
    if (tokenBusy.value || lock.active.value) return;
    tokenError.value = '';
    const value = token.value.trim();
    if (!value) {
        tokenError.value = t('install.tokenRequired');
        return;
    }
    tokenBusy.value = true;
    try {
        await api.verifyToken(value);
        token.value = '';
        notice.value = '';
        setAuthorized(true);
        void loadBoardPresets();
        await loadChecks();
    } catch (e) {
        if (handledCommonly(e)) return;
        if (UtilInstall.isTokenLocked(e) && e instanceof ApiError) {
            token.value = '';
            lock.start(e.retryAfterSeconds ?? 600);
            return;
        }
        tokenError.value = e instanceof ApiError ? (e.field('token') ?? e.message) : UtilBoardMessages.of(t, e);
    } finally {
        tokenBusy.value = false;
    }
}

/** 설치 점검 결과를 받는다(입력이 아니라 서버가 판정한 것) */
async function loadChecks() {
    const mine = ++checksSeq;
    checksBusy.value = true;
    checksError.value = '';
    try {
        const res = await api.checks();
        if (mine === checksSeq) checks.value = res;
    } catch (e) {
        if (mine !== checksSeq || handledCommonly(e)) return;
        checksError.value = UtilBoardMessages.of(t, e);
    } finally {
        if (mine === checksSeq) checksBusy.value = false;
    }
}

/** 고를 수 있는 기본 게시판 구성을 받는다 — 못 받아도 설치는 계속할 수 있다(서버가 기본 구성을 쓴다) */
async function loadBoardPresets() {
    const mine = ++presetsSeq;
    presetsBusy.value = true;
    presetsError.value = '';
    try {
        const res = await api.boardPresets();
        if (mine !== presetsSeq) return;
        boardPresets.value = res.presets;
        boardPresetDefault.value = res.defaultId;
        // 이미 고른 것이 목록에 있으면 그대로, 아니면 서버 기본값
        if (!res.presets.some((p) => p.id === boardPreset.value)) boardPreset.value = res.defaultId;
    } catch (e) {
        if (mine !== presetsSeq || handledCommonly(e)) return;
        presetsError.value = t('install.boardsFailed');
    } finally {
        if (mine === presetsSeq) presetsBusy.value = false;
    }
}

/** 설치 상태를 못 받았을 때 다시 묻는다 */
async function retryStatus() {
    if (statusBusy.value) return;
    statusBusy.value = true;
    try {
        const now = await refresh();
        if (now === 'installed') await navigateTo('/', { replace: true });
        else if (now === 'pending' && authorized.value) {
            void loadBoardPresets();
            await loadChecks();
        }
    } finally {
        statusBusy.value = false;
    }
}

/* ── 단계 이동 · 입력 검사 ── */

/** 그 단계의 입력을 미리 검사한다(서버 규칙과 같다). 건너뛴 단계·잠긴 단계는 검사하지 않는다 */
function validate(target: InstallStepKey): InstallFieldErrors {
    switch (target) {
        case 'root':
            return UtilInstallMessages.rootErrors(t, UtilInstall.validateRoot(root), root.nickname.length);
        case 'site':
            return UtilInstallMessages.siteErrors(t, UtilSite.validate(site));
        case 'mail':
            return mailMode.value === 'skip' ? {} : UtilInstallMessages.mailErrors(t, UtilMail.validate(mail, { credentialsLocked: !cryptoOk.value }));
        case 'social':
            return socialMode.value === 'skip' ? {} : UtilInstallMessages.socialErrors(t, UtilInstall.validateSocial(social));
        default:
            return {};
    }
}

/** 그 단계 칸들의 오류를 새로 검사한 것으로 바꾼다(다른 단계의 오류는 그대로) */
function setStepErrors(target: InstallStepKey, found: InstallFieldErrors) {
    const others: InstallFieldErrors = {};
    for (const [key, message] of Object.entries(errors.value)) if (UtilInstall.stepOfKey(key) !== target) others[key] = message;
    errors.value = { ...others, ...found };
}

/** 첫 오류 칸으로 초점을 옮긴다 — 오류 문구가 있는 칸(`LabeledField`)의 입력 */
async function focusFirstError() {
    await nextTick();
    const alert = formEl.value?.querySelector('p[role="alert"]');
    alert?.parentElement?.parentElement?.querySelector<HTMLElement>('input')?.focus();
}

/** 단계 막대의 큰 버튼과 Enter — 단계마다 확인·다음·설치 */
async function submitStep() {
    if (step.value === 'token' && !authorized.value) return verify();
    if (step.value === 'review') return installNow();
    next();
}

function next() {
    if (step.value === 'token') {
        if (authorized.value && UtilInstall.canProceed(checks.value)) step.value = 'root';
        return;
    }
    const found = validate(step.value);
    setStepErrors(step.value, found);
    if (Object.keys(found).length) {
        void focusFirstError();
        return;
    }
    step.value = UtilInstall.stepAfter(step.value);
}

function prev() {
    if (!installing.value) step.value = UtilInstall.stepBefore(step.value);
}

/** 확인 화면의 "수정"·"입력하기" — 그 단계로 돌아간다. 건너뛰었던 단계는 입력칸을 다시 연다 */
function goTo(target: InstallStepKey) {
    if (installing.value) return;
    if (target === 'mail') mailMode.value = 'set';
    if (target === 'social' && cryptoOk.value) socialMode.value = 'set';
    step.value = target;
}

/** 건너뛰기 — 입력해 둔 값은 지우지 않고 요청에서만 뺀다 */
function skip() {
    if (installing.value) return;
    if (step.value === 'mail') mailMode.value = 'skip';
    else if (step.value === 'social') socialMode.value = 'skip';
    else return;
    setStepErrors(step.value, {});
    step.value = UtilInstall.stepAfter(step.value);
}

/* ── 4단계 동작: 메일 연결 확인 ── */
async function runMailCheck() {
    if (mailCheck.value.status === 'checking') return;
    const found = UtilInstallMessages.mailErrors(t, UtilMail.validate(mail, { credentialsLocked: !cryptoOk.value }));
    setStepErrors('mail', found);
    if (Object.keys(found).length) {
        void focusFirstError();
        return;
    }
    const mine = ++mailCheckSeq;
    mailCheck.value = { status: 'checking', text: '' };
    try {
        const res = await api.mailCheck(UtilMail.toInput(mail));
        if (mine !== mailCheckSeq) return;
        const result = UtilMailMessages.checkResult(t, res);
        mailCheck.value = { status: result.ok ? 'ok' : 'fail', text: result.text };
    } catch (e) {
        if (mine !== mailCheckSeq) return;
        if (handledCommonly(e)) return;
        const fields = UtilMail.fieldErrorsOf(e);
        if (fields) {
            setStepErrors('mail', UtilInstall.prefixErrors('mail', fields));
            mailCheck.value = UtilMail.idleCheck();
            return;
        }
        mailCheck.value = { status: 'fail', text: UtilBoardMessages.of(t, e) };
    }
}

/* ── 7단계 동작: 설치 ── */

/** 설치 — 한 번의 호출로 원자적으로 끝난다. 서버가 거절하면 까닭이 있는 단계로 돌아간다 */
async function installNow() {
    if (installing.value) return;

    // 단계를 거치며 이미 검사했지만 값이 바뀌었을 수 있어 한 번 더 — 틀린 것이 있으면 그 단계로
    const found: InstallFieldErrors = { ...validate('root'), ...validate('site'), ...validate('mail'), ...validate('social') };
    if (Object.keys(found).length) {
        errors.value = found;
        step.value = UtilInstall.firstStepWithErrors(found) ?? step.value;
        return;
    }

    installing.value = true;
    submitError.value = '';
    errors.value = {};
    // 서버 오류의 `social.providers[N]` 이 가리키는 N 번째가 이 순서다
    const sent = socialMode.value === 'set' ? UtilInstall.usedProviders(social) : [];
    try {
        await api.complete(
            UtilInstall.buildRequest({
                root,
                site,
                mailMode: mailMode.value,
                mail,
                socialMode: socialMode.value,
                social,
                boardPreset: boardPreset.value,
                boardPresets: boardPresets.value,
            }),
        );
        doneEmail.value = root.email.trim();
        wipe();
        done.value = true;
        markInstalled();
    } catch (e) {
        if (handledCommonly(e)) return;
        rejected(e, sent);
    } finally {
        installing.value = false;
    }
}

/** 서버가 설치를 거절했다 — 칸에 붙는 것은 그 단계로, 아니면 확인 화면에 한꺼번에 */
function rejected(e: unknown, sent: SocialProviderCode[]) {
    if (!(e instanceof ApiError)) {
        submitError.value = UtilBoardMessages.of(t, e);
        return;
    }
    if (e.code === 'VALIDATION' && e.fields) {
        errors.value = UtilInstall.normalizeFields(e.fields, sent);
        const target = UtilInstall.firstStepWithErrors(errors.value);
        if (target) step.value = target;
        else submitError.value = e.message;
        return;
    }
    if (UtilInstall.isEmailTaken(e)) {
        errors.value = { 'root.email': e.message };
        step.value = 'root';
        return;
    }
    if (e.code === 'CRYPTO_KEY_MISSING') {
        cryptoMissing.value = true; // 인증 정보·소셜 키를 비우고 건너뛴 것으로 바꾼다(위 watch)
        submitError.value = t('install.cryptoDropped');
        return;
    }
    submitError.value = e.message;
}

/** 설치가 끝났다 — 입력해 둔 값(비밀번호·시크릿 포함)을 메모리에서 비운다 */
function wipe() {
    token.value = '';
    Object.assign(root, UtilInstall.initialRoot());
    Object.assign(mail, UtilMail.installForm());
    Object.assign(social, UtilInstall.initialSocial());
}

function goLogin() {
    void navigateTo('/login');
}
</script>
