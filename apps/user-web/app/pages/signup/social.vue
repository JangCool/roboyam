<template>
    <!-- 소셜 가입 정보를 확인하는 중 -->
    <div v-if="state === 'loading'" class="flex flex-col items-center gap-3 py-8 text-sm text-muted-color" role="status">
        <i class="pi pi-spin pi-spinner text-2xl" aria-hidden="true" />
        {{ t('signup.socialLoading') }}
    </div>

    <!-- 확인 요청이 실패했다(네트워크 등). 없는 가입 대기(404)는 로그인으로 보낸다 -->
    <StatusPanel v-else-if="state === 'error'" icon="pi pi-exclamation-triangle" tone="error" :title="t('signup.socialLoadFailed')">
        {{ loadError }}
        <template #actions>
            <Button :label="t('common.retry')" fluid @click="load" />
            <Button as="router-link" to="/login" :label="t('signup.toLogin')" variant="outlined" severity="secondary" fluid />
        </template>
    </StatusPanel>

    <div v-else-if="pending">
        <h1 class="text-center text-lg font-semibold">{{ t('signup.socialTitle') }}</h1>

        <Message severity="success" :closable="false" class="mt-4" size="small" data-testid="social-pending">
            <b>{{ t('signup.socialChecked', { provider: providerName }) }}</b> {{ t('signup.socialRemaining') }}
            <template v-if="pending.emailMasked"><br />{{ t('signup.socialEmail', { email: pending.emailMasked }) }}</template>
        </Message>

        <!-- 공급자가 확인하지 않은 이메일 — 가입 뒤 서버가 인증 메일을 보낸다(이메일이 없으면 보낼 곳이 없어 안내하지 않는다) -->
        <Message v-if="pending.emailMasked && !pending.emailVerified" severity="info" :closable="false" class="mt-3" size="small" data-testid="social-email-unverified">
            {{ t('signup.emailUnverified') }}
        </Message>

        <form class="mt-4 flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message v-if="formError" severity="error" :closable="false" size="small" data-testid="social-form-error">{{ formError }}</Message>

            <LabeledField
                :label="t('signup.nicknameLabel')"
                :hint="t('signup.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })"
                :help="t('signup.nicknameHelp')"
                :error="nicknameError"
            >
                <template #aside>
                    <small class="text-xs text-muted-color" aria-hidden="true">{{ nickname.length }}/{{ UtilValidation.NICKNAME_MAX }}</small>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        ref="nicknameInput"
                        v-model="nickname"
                        type="text"
                        name="nickname"
                        autocomplete="nickname"
                        autocapitalize="off"
                        spellcheck="false"
                        :maxlength="UtilValidation.NICKNAME_MAX"
                        :disabled="submitting"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        data-testid="social-nickname"
                    />
                </template>
            </LabeledField>

            <!-- 가입 뒤 닉네임 변경은 1번뿐(`member-mypage-contract.md` 결정) — 이메일 가입 2/3 닉네임 단계와 같은 안내 -->
            <Message severity="secondary" :closable="false" size="small" data-testid="social-nickname-note">{{ t('signup.nicknameNote') }}</Message>

            <TermsAgreement v-model="agreements" :errors="termsErrors" :disabled="submitting" />

            <Button type="submit" :label="submitting ? t('signup.submitting') : t('signup.submit')" fluid size="large" :loading="submitting" data-testid="social-submit" />
        </form>

        <p class="mt-4 text-center text-sm">
            <NuxtLink to="/login" class="text-muted-color hover:underline max-md:py-1.5">{{ t('signup.leave') }}</NuxtLink>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { Agreements, SocialPending } from '~/composables/auth/api/useAuthApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilProvider from '~/utils/util-provider';
import UtilSanction from '~/utils/util-sanction';
import UtilValidation from '~/utils/util-validation';

/**
 * 소셜 가입 마무리 `/signup/social` — 처음 보는 소셜 계정으로 돌아오면 서버 콜백이 여기로 보낸다(서버 세션에 가입 대기, 15분).
 * 서버에 남은 가입 대기(`GET /api/auth/social/pending`)로 공급자·마스킹 이메일·닉네임 기본값을 보이고, 닉네임 + 약관 3개 동의로 가입을 마친다(`POST …/complete`).
 * 원본(community) `pages/signup/social.vue` 를 옮겼다 — 닉네임 중복 미리 묻기(`nickname-available`)는 서버에 없어 화면 규칙만 미리 보고 중복은 제출 때 서버가 본다.
 *
 * - 가입 대기가 없거나 만료(404 `NO_PENDING`) → `/login?error=social_expired`.
 * - 201 → 로그인 상태 갱신(`useAuth` 가 응답을 그대로 담는다) → 제재 안내 분기(`UtilSanction.afterLoginPath`) → `pending.next ?? '/'`.
 * - 400 `fields` → 칸 아래, 409 `NICKNAME_TAKEN` → 닉네임 칸, 409 `EMAIL_TAKEN` → 로그인 화면의 이메일 겹침 안내, 409 `SOCIAL_ALREADY_REGISTERED` → 로그인 화면(토스트로 서버 문구),
 *   429 와 그 밖 → 폼 위에 서버 문구.
 */
definePageMeta({ layout: 'auth', middleware: 'guest' });
const { t } = useI18n();
useSeoMeta({ title: () => t('signup.socialTitle'), description: () => t('signup.socialSeoDescription'), robots: 'noindex' });

const api = useAuthApi();
const { completeSocialSignup, restriction } = useAuth();
const toast = useAppToast();

const state = ref<'loading' | 'ready' | 'error'>('loading');
const pending = ref<SocialPending | null>(null);
const loadError = ref('');

const nickname = ref('');
const nicknameError = ref('');
watch(nickname, () => (nicknameError.value = ''));
const nicknameInput = ref<{ $el: HTMLInputElement } | null>(null);

const agreements = ref<Agreements>({ agreeTerms: false, agreePrivacy: false, ageConfirmed: false });
const termsErrors = ref<Partial<Record<keyof Agreements, string>>>({});
const submitting = ref(false);
const formError = ref('');

/** 공급자 이름 — id 로 지금 언어의 이름을 고르고, 모르는 공급자만 서버 이름을 쓴다 */
const providerName = computed(() => UtilProvider.name(t, pending.value?.provider, pending.value?.providerName));

function networkMessage(): string {
    return UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

async function toExpired() {
    await navigateTo({ path: '/login', query: { error: 'social_expired' } }, { replace: true });
}

async function load() {
    state.value = 'loading';
    try {
        pending.value = await api.socialPending();
        nickname.value ||= pending.value.suggestedNickname ?? '';
        state.value = 'ready';
    } catch (e) {
        if (e instanceof ApiError && (e.code === 'NO_PENDING' || e.status === 404)) {
            await toExpired();
            return;
        }
        loadError.value = e instanceof ApiError ? e.message : networkMessage();
        state.value = 'error';
    }
}

/** 화면이 미리 보는 것 — 닉네임 규칙, 약관 3개. 문제가 있으면 false(칸에 문구를 붙인다) */
function precheck(): boolean {
    const problem = UtilValidation.nickname(nickname.value);
    if (problem) nicknameError.value = UtilAuthMessages.nicknameProblem(t, problem, nickname.value.length);
    termsErrors.value = {
        agreeTerms: agreements.value.agreeTerms ? undefined : t('signup.agreeTermsMissing'),
        agreePrivacy: agreements.value.agreePrivacy ? undefined : t('signup.agreePrivacyMissing'),
        ageConfirmed: agreements.value.ageConfirmed ? undefined : t('signup.ageConfirmedMissing'),
    };
    if (problem) nicknameInput.value?.$el.focus();
    return !problem && agreements.value.agreeTerms && agreements.value.agreePrivacy && agreements.value.ageConfirmed;
}

async function submit() {
    if (submitting.value || !pending.value) return;
    formError.value = '';
    if (!precheck()) return;

    const providerId = pending.value.provider;
    const next = pending.value.next;
    submitting.value = true;
    try {
        await completeSocialSignup({ nickname: nickname.value, ...agreements.value });
        toast.success(t('signup.welcome'));
        // 성공하면 화면을 떠난다 — 버튼을 다시 열지 않는다. 정지 등으로 안내가 필요하면 제재 안내를 먼저(7-2)
        await navigateTo(UtilSanction.afterLoginPath(restriction.value, next ?? '/'), { replace: true });
    } catch (e) {
        submitting.value = false;
        if (!(e instanceof ApiError)) {
            formError.value = networkMessage();
        } else if (e.code === 'NO_PENDING') {
            await toExpired();
        } else if (e.code === 'NICKNAME_TAKEN') {
            nicknameError.value = e.message;
            nicknameInput.value?.$el.focus();
        } else if (e.code === 'EMAIL_TAKEN') {
            // 그 사이 같은 이메일로 가입한 회원이 생겼다 — 로그인 화면의 이메일 겹침 안내로(자동으로 합치지 않는다)
            await navigateTo({ path: '/login', query: { error: 'email_exists', provider: providerId } }, { replace: true });
        } else if (e.code === 'SOCIAL_ALREADY_REGISTERED') {
            // 그 사이 이 소셜 계정으로 가입이 끝났다 — 다시 로그인하면 된다
            toast.error(e.message, 8000);
            await navigateTo('/login', { replace: true });
        } else if (e.code === 'VALIDATION' && e.fields) {
            if (e.fields.nickname) nicknameError.value = e.fields.nickname;
            termsErrors.value = { agreeTerms: e.fields.agreeTerms, agreePrivacy: e.fields.agreePrivacy, ageConfirmed: e.fields.ageConfirmed };
            if (!e.fields.nickname && !e.fields.agreeTerms && !e.fields.agreePrivacy && !e.fields.ageConfirmed) formError.value = e.message;
        } else {
            formError.value = e.message;
        }
    }
}

onMounted(load);
</script>
