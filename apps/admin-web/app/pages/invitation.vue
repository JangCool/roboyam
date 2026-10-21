<template>
    <!--
        초대 수락 `/invitation?token=…` — 로그인 전 화면. 초대 메일의 링크(유효 72시간)로 들어와 이름·비밀번호를 정하고 계정을 만든다. 배치·문구는 hamonica3 초대 가입(`signup.vue` 의 `?invite=`)을 따랐다:
        초대받은 역할 안내(info) · 이메일 읽기 전용 · 이름/비밀번호/확인 · 쓸 수 없는 초대는 경고(warn) 상자.
        흐름: 토큰 확인 중(스켈레톤) → 유효하면 입력 폼 → 성공 안내(로그인으로) / 쓸 수 없는 초대(없음·올바르지 않음·만료·이미 사용·취소 — 각각 다른 문구) / 확인 실패(다시 시도).
        MOCK: 서버가 없어 `useAdminAccountApi` 가 목업을 부른다. 시나리오는 주소의 `?mock=` (예: `&mock=invitation-expired`) — 목록은 `composables/account/api/mock/account-mock.ts` 머리 주석.
        이미 로그인한 사람이 열어도 막지 않는다(상단 안내만 — 다른 사람이 받은 링크일 수 있다). 비밀번호·토큰은 이 화면 메모리에만 두고 제출이 끝나면 비운다.
    -->
    <h1 ref="heading" tabindex="-1" class="text-center text-lg font-semibold outline-none">{{ t('account.invitationTitle') }}</h1>
    <p v-if="phase === 'checking' || phase === 'ready'" class="mb-4 mt-1 text-center text-sm text-muted-color">{{ t('account.invitationSubtitle') }}</p>

    <!-- 이미 로그인한 사람의 안내 — 화면은 막지 않는다 -->
    <Message v-if="isLoggedIn" severity="info" :closable="false" size="small" class="mb-4">
        <b>{{ t('account.loggedInTitle') }}</b> {{ t('account.loggedInBody') }}
        <NuxtLink to="/me?tab=password" class="whitespace-nowrap text-primary hover:underline">{{ t('account.loggedInLink') }}</NuxtLink>
    </Message>

    <!-- 토큰 확인 중 -->
    <div v-if="phase === 'checking'" class="mt-4 flex flex-col gap-4" role="status" aria-live="polite">
        <p class="m-0 text-center text-xs text-muted-color">{{ t('account.invitationChecking') }}</p>
        <Skeleton height="2.25rem" />
        <Skeleton height="2.25rem" />
        <Skeleton height="2.25rem" />
        <Skeleton height="2.25rem" />
    </div>

    <!-- 확인하지 못함(네트워크·서버 오류) — 서버 문구를 그대로 보이고 다시 시도 -->
    <ErrorState v-else-if="phase === 'failed'" :title="t('account.invitationLoadFailed')" :description="loadError?.message" retry :retrying="false" @retry="check()">
        <Button :as="NuxtLink" to="/login" :label="t('account.toLogin')" severity="secondary" variant="outlined" />
    </ErrorState>

    <!-- 쓸 수 없는 초대 — 이유마다 다른 문구 + 로그인으로 -->
    <template v-else-if="phase === 'blocked'">
        <Message severity="warn" :closable="false" size="small">
            <b>{{ blockedText.title }}</b> {{ blockedText.description }}
        </Message>
        <div class="mt-4 flex flex-col gap-3">
            <Button :as="NuxtLink" to="/login" fluid :label="t('account.toLogin')" />
            <p v-if="blocked === 'used'" class="m-0 text-center text-sm">
                <NuxtLink to="/forgot-password" class="text-primary hover:underline">{{ t('login.forgotLink') }}</NuxtLink>
            </p>
        </div>
    </template>

    <!-- 완료 -->
    <template v-else-if="phase === 'done'">
        <Message severity="success" :closable="false" size="small" role="status">
            <b>{{ t('account.invitationDoneTitle') }}</b> {{ t('account.invitationDoneBody') }}
        </Message>
        <Button :as="NuxtLink" to="/login" class="mt-4" fluid size="large" :label="t('account.toLogin')" />
    </template>

    <!-- 입력 -->
    <template v-else-if="invitation">
        <!-- 어떤 역할로 초대받았는지 — 받을 역할 이름은 운영자가 정한 값 그대로 -->
        <Message severity="info" :closable="false" size="small" class="mb-4">
            <i18n-t v-if="invitation.roles.length" keypath="account.invitedTo" tag="span">
                <template #role>
                    <strong>{{ invitation.roles.map((role) => role.roleName).join(', ') }}</strong>
                </template>
            </i18n-t>
            <span v-else>{{ t('account.invitedToNoRole') }}</span>
        </Message>

        <form class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- 저장 실패(칸에 붙지 않는 오류) — 서버 문구 그대로 -->
            <Message v-if="formError" severity="error" :closable="false" size="small">{{ formError }}</Message>

            <!-- 읽기 전용 — 서버 값이라 `disabled` 가 아니라 `readonly`(초대는 이 주소에 묶여 있어 바꿀 수 없다) -->
            <LabeledField :label="t('account.invitedEmail')" :help="t('account.invitedEmailHelp')">
                <template #default="{ id, describedBy }">
                    <InputText :id="id" :model-value="invitation.email" name="username" type="email" autocomplete="username" readonly :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <LabeledField :label="t('account.nameLabel')" :hint="t('account.nameHint')" :help="t('account.nameHelp')" :error="fieldErrors.nickname">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" ref="nicknameInput" v-model="nickname" name="nickname" autocomplete="nickname" autocapitalize="off" spellcheck="false" :disabled="submitting" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <NewPasswordFields ref="newPassword" v-model:password="password" v-model:confirm="confirm" :disabled="submitting" :password-error="fieldErrors.password" :confirm-error="fieldErrors.confirm" />

            <Button type="submit" fluid size="large" :label="submitting ? t('account.creating') : t('account.createAccount')" :loading="submitting" />
            <p v-if="submitting" class="-mt-2 text-center text-xs text-muted-color" role="status">{{ t('login.submitWait') }}</p>
        </form>

        <p class="mt-4 text-center text-sm text-muted-color">
            {{ t('account.hasAccount') }}
            <NuxtLink to="/login" class="text-primary hover:underline">{{ t('common.login') }}</NuxtLink>
        </p>
    </template>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { InvitationCheckResponse, TokenProblem } from '~/composables/account/api/useAdminAccountApi.types';
import UtilAccount from '~/utils/util-account';
import UtilAccountMessages from '~/utils/util-account-messages';
import UtilValidation from '~/utils/util-validation';

// public: 로그인 없이 볼 수 있는 화면(`auth.global` 이 건너뛴다). 로그인한 사람도 막지 않는다 — `guest` 미들웨어를 쓰지 않는다
definePageMeta({ layout: 'auth', public: true });
const { t } = useI18n();
useSeoMeta({ title: () => t('account.invitationSeoTitle') });

const route = useRoute();
const account = useAdminAccount();
const { isLoggedIn } = useAdminAuth();
const NuxtLink = resolveComponent('NuxtLink');

type Phase = 'checking' | 'ready' | 'done' | 'blocked' | 'failed';
const phase = ref<Phase>('checking');
/** 쓸 수 없는 이유 — `missing` 은 주소에 토큰이 없는 경우 */
const blocked = ref<TokenProblem | 'missing'>('missing');
const invitation = ref<InvitationCheckResponse | null>(null);
const loadError = ref<ApiError | null>(null);

const blockedText = computed(() => UtilAccountMessages.invitationBlocked(t, blocked.value));

// 폼 값은 이 화면 메모리에만 둔다
const nickname = ref('');
const password = ref('');
const confirm = ref('');
const submitting = ref(false);
const formError = ref('');
const fieldErrors = reactive<{ nickname?: string; password?: string; confirm?: string }>({});

const heading = ref<HTMLElement | null>(null);
const nicknameInput = ref<{ $el: HTMLInputElement } | null>(null);
const newPassword = ref<{ focusPassword: () => void; focusConfirm: () => void } | null>(null);

function block(problem: TokenProblem | 'missing') {
    blocked.value = problem;
    phase.value = 'blocked';
}

/** 초대 링크의 토큰을 확인한다 — 주소에 토큰이 없으면 서버에 묻지 않는다 */
async function check() {
    const token = UtilAccount.token(route.query.token);
    if (!token) return block('missing');

    phase.value = 'checking';
    loadError.value = null;
    try {
        invitation.value = await account.checkInvitation(token);
        phase.value = 'ready';
        await nextTick();
        nicknameInput.value?.$el.focus();
    } catch (e) {
        const failure = account.asApiError(e);
        const problem = UtilAccount.invitationProblem(failure.code);
        if (problem) return block(problem);
        loadError.value = failure;
        phase.value = 'failed';
    }
}

onMounted(() => void check());

// 고치기 시작하면 그 칸의 오류를 걷는다
watch(nickname, () => (fieldErrors.nickname = undefined));
watch(password, () => (fieldErrors.password = undefined));
watch(confirm, () => (fieldErrors.confirm = undefined));

async function submit() {
    if (submitting.value) return;
    formError.value = '';
    fieldErrors.nickname = undefined;
    fieldErrors.password = undefined;
    fieldErrors.confirm = undefined;

    // 화면이 미리 알 수 있는 문제 — 서버에 보내지 않고 그 칸에서 알린다(판정은 서버이고 서버가 거절하면 서버 문구를 그대로 보인다)
    const nicknameProblem = UtilValidation.nickname(nickname.value);
    if (nicknameProblem) fieldErrors.nickname = UtilAccountMessages.nameProblem(t, nicknameProblem);
    const passwordProblem = UtilValidation.passwordProblem(password.value);
    if (passwordProblem) fieldErrors.password = UtilAccountMessages.passwordProblem(t, passwordProblem);
    if (!confirm.value) fieldErrors.confirm = t('account.confirmRequired');
    else if (confirm.value !== password.value) fieldErrors.confirm = t('account.confirmMismatch');

    if (fieldErrors.nickname) return void nicknameInput.value?.$el.focus();
    if (fieldErrors.password) return void newPassword.value?.focusPassword();
    if (fieldErrors.confirm) return void newPassword.value?.focusConfirm();

    const token = UtilAccount.token(route.query.token);
    if (!token) return block('missing');

    submitting.value = true;
    try {
        await account.acceptInvitation({ token, nickname: nickname.value, password: password.value });
        // 성공 — 비밀번호는 바로 비우고 완료 안내로 바꾼다
        password.value = '';
        confirm.value = '';
        phase.value = 'done';
        await nextTick();
        heading.value?.focus();
    } catch (e) {
        const failure = account.asApiError(e);
        const problem = UtilAccount.invitationProblem(failure.code);
        if (problem) {
            // 확인과 제출 사이에 만료·취소됐다 — 입력을 지우고 그 이유를 알린다
            password.value = '';
            confirm.value = '';
            return block(problem);
        }
        if (failure.code === 'NICKNAME_TAKEN') {
            fieldErrors.nickname = failure.message;
        } else if (failure.code === 'VALIDATION' && (failure.fields?.nickname || failure.fields?.password)) {
            fieldErrors.nickname = failure.fields.nickname;
            fieldErrors.password = failure.fields.password;
        } else {
            formError.value = failure.message;
        }
        await nextTick();
        if (fieldErrors.nickname) nicknameInput.value?.$el.focus();
        else if (fieldErrors.password) newPassword.value?.focusPassword();
    } finally {
        submitting.value = false;
    }
}
</script>
