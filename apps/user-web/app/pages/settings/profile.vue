<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('profile.title') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('profile.loading')" data-testid="profile-loading">
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('profile.loadFailed')" :description="loadError" retry data-testid="profile-error" @retry="load" />

        <!-- 한 화면 하나로 저장(PUT 한 번) — 저장 줄은 폼 맨 끝(`StickySaveBar`) -->
        <form v-else-if="saved" :key="formKey" class="flex flex-col gap-4" novalidate data-testid="profile-form" @submit.prevent="save">
            <section class="flex flex-col gap-5 rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-label="t('profile.title')">
                <LabeledField
                    :label="t('signup.nicknameLabel')"
                    :hint="t('signup.nicknameHint', { min: UtilValidation.NICKNAME_MIN, max: UtilValidation.NICKNAME_MAX })"
                    :help="nicknameHelp"
                    :error="errors.nickname"
                >
                    <template #aside>
                        <small class="text-xs text-muted-color" aria-hidden="true">{{ form.nickname.length }}/{{ UtilValidation.NICKNAME_MAX }}</small>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <!-- 바꿀 수 없는 동안은 readonly — 값은 저장 요청에 그대로 실린다(`forms.md` 2-1) -->
                        <InputText
                            :id="id"
                            ref="nicknameInput"
                            v-model="form.nickname"
                            type="text"
                            name="nickname"
                            autocomplete="nickname"
                            autocapitalize="off"
                            spellcheck="false"
                            :maxlength="UtilValidation.NICKNAME_MAX"
                            :readonly="nicknameLocked"
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="profile-nickname"
                        />
                    </template>
                </LabeledField>

                <LabeledField :label="t('profile.bioLabel')" :hint="t('profile.bioHint', { max: PROFILE_BIO_MAX })" :help="t('profile.bioHelp')" :error="errors.bio">
                    <template #aside>
                        <small class="text-xs text-muted-color" aria-hidden="true">{{ form.bio.length }}/{{ PROFILE_BIO_MAX }}</small>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea
                            :id="id"
                            v-model="form.bio"
                            name="bio"
                            rows="3"
                            auto-resize
                            :maxlength="PROFILE_BIO_MAX"
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="profile-bio"
                        />
                    </template>
                </LabeledField>
            </section>

            <StickySaveBar :dirty="dirty" :saving="saving" :count="changedCount" :error-count="errorCount" :error="formError" :disabled="errorCount > 0" revertable submit @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import { PROFILE_BIO_MAX } from '~/composables/profile/api/useProfileApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilValidation from '~/utils/util-validation';

/**
 * 프로필 `/settings/profile` — 로그인 필요. 닉네임·한 줄 소개를 고친다(서버 API 가 없어 목업 `GET/PUT /api/me/profile`).
 * 닉네임 규칙은 가입 화면과 같은 화면 검사(`UtilValidation.nickname` — 서버 `UserPolicy`)로 미리 알리고, 중복·예약어·변경 주기는 서버가 정한다:
 * `409 NICKNAME_TAKEN` · `400 VALIDATION fields.nickname|bio` 는 칸 아래, `409 NICKNAME_CHANGE_TOO_SOON` 은 닉네임 칸 아래 + 칸 잠금, 그 밖은 저장 줄 안 한 줄.
 * 닉네임을 바꾼 뒤 다시 바꿀 수 있기까지(목업 가정 30일) 닉네임 칸은 readonly 이고 다음 날짜를 안내한다. 저장하면 로그인 정보(`me`)를 다시 받아 셸의 닉네임을 맞춘다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('profile.title'), description: () => t('profile.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useProfileApi();
const auth = useAuth();
const toast = useAppToast();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
/** 서버에 저장된 값 */
const saved = ref<MyProfile | null>(null);
const form = reactive({ nickname: '', bio: '' });
/** 저장한 뒤 폼을 다시 만든다(`forms.md` 2-1) */
const formKey = ref(0);
const saving = ref(false);
const formError = ref('');
/** 서버가 칸에 붙여 준 오류(입력을 바꾸면 그 칸 것만 지운다) */
const serverErrors = reactive<{ nickname: string; bio: string }>({ nickname: '', bio: '' });
const nicknameInput = ref<{ $el: HTMLInputElement } | null>(null);

function messageOf(e: unknown): string {
    return e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
}

function fill(value: MyProfile) {
    saved.value = value;
    form.nickname = value.nickname;
    form.bio = value.bio;
    serverErrors.nickname = '';
    serverErrors.bio = '';
    formError.value = '';
    formKey.value++;
}

async function load() {
    status.value = 'loading';
    loadError.value = '';
    try {
        fill(await api.me());
        status.value = 'ready';
    } catch (e) {
        loadError.value = messageOf(e);
        status.value = 'error';
    }
}
void load();

watch(
    () => form.nickname,
    () => (serverErrors.nickname = ''),
);
watch(
    () => form.bio,
    () => (serverErrors.bio = ''),
);

/** 닉네임을 바꿀 수 없는 동안인가(다음 변경 가능 시각이 아직 오지 않음) */
const nicknameLocked = computed(() => {
    const at = saved.value?.nicknameChangeAvailableAt;
    return Boolean(at && Date.parse(at) > Date.now());
});

const nicknameHelp = computed(() => {
    const s = saved.value;
    if (!s) return '';
    if (nicknameLocked.value && s.nicknameChangeAvailableAt) return t('profile.nicknameLocked', { date: time.value.full(s.nicknameChangeAvailableAt) });
    return t('profile.nicknameInterval', { days: s.nicknameChangeIntervalDays });
});

/** 화면 검사 — 서버와 같은 닉네임 규칙(중복·예약어는 서버만 안다) */
const errors = computed(() => {
    const problem = nicknameChanged.value ? UtilValidation.nickname(form.nickname) : null;
    return {
        nickname: serverErrors.nickname || (problem ? UtilAuthMessages.nicknameProblem(t, problem, form.nickname.length) : ''),
        bio: serverErrors.bio || (form.bio.trim().length > PROFILE_BIO_MAX ? t('profile.bioTooLong', { max: PROFILE_BIO_MAX }) : ''),
    };
});
const errorCount = computed(() => Object.values(errors.value).filter(Boolean).length);

const nicknameChanged = computed(() => Boolean(saved.value) && form.nickname !== saved.value?.nickname);
const bioChanged = computed(() => Boolean(saved.value) && form.bio.trim() !== (saved.value?.bio ?? ''));
const changedCount = computed(() => Number(nicknameChanged.value) + Number(bioChanged.value));
const dirty = computed(() => changedCount.value > 0);

function revert() {
    if (saved.value) fill(saved.value);
}

async function save() {
    if (saving.value || !dirty.value) return;
    formError.value = '';
    if (errorCount.value > 0) {
        if (errors.value.nickname) nicknameInput.value?.$el.focus();
        return;
    }
    saving.value = true;
    try {
        const value = await api.save({ nickname: form.nickname, bio: form.bio.trim() });
        const nicknameDone = nicknameChanged.value;
        fill(value);
        toast.success(t('common.saved'));
        // 셸(헤더·햄버거)의 닉네임 — 로그인 정보를 다시 받는다. 실패해도 저장은 끝났다
        if (nicknameDone) await auth.refresh().catch(() => undefined);
    } catch (e) {
        if (e instanceof ApiError && (e.code === 'NICKNAME_TAKEN' || e.code === 'NICKNAME_CHANGE_TOO_SOON')) {
            serverErrors.nickname = e.message;
            const availableAt = e.extraText('availableAt');
            if (e.code === 'NICKNAME_CHANGE_TOO_SOON' && availableAt && saved.value) saved.value = { ...saved.value, nicknameChangeAvailableAt: availableAt };
            nicknameInput.value?.$el.focus();
        } else if (e instanceof ApiError && e.code === 'VALIDATION' && (e.field('nickname') || e.field('bio'))) {
            serverErrors.nickname = e.field('nickname') ?? '';
            serverErrors.bio = e.field('bio') ?? '';
        } else {
            formError.value = messageOf(e);
        }
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있으면 떠나기 전에 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value || saving.value) return true;
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
});
</script>
