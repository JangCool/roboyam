<template>
    <div class="flex flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('profile.title') }}</h1>

        <div v-if="status === 'loading'" class="flex flex-col gap-4" role="status" :aria-label="t('profile.loading')" data-testid="profile-loading">
            <div class="h-40 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>

        <ErrorState v-else-if="status === 'error'" :title="t('profile.loadFailed')" :description="loadError" retry data-testid="profile-error" @retry="load" />

        <!-- 한 화면 하나로 저장(PUT 한 번) — 저장 줄은 폼 맨 끝(`StickySaveBar`) -->
        <form v-else-if="saved" :key="formKey" class="flex flex-col gap-4" novalidate data-testid="profile-form" @submit.prevent="save">
            <!-- 정지 중 — 프로필은 읽기 전용(서버 `403 ACCOUNT_RESTRICTED`). 풀리면 할 수 있는 일이라 폼을 감추지 않고 막은 채로 이유를 보인다 -->
            <div v-if="restricted" role="status" class="rounded-lg bg-amber-50 px-3 py-2.5 text-sm leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" data-testid="profile-restricted">
                <p class="m-0 font-semibold">{{ t('profile.restrictedTitle') }}</p>
                <p class="m-0 mt-0.5">{{ t('profile.restrictedText') }}</p>
                <NuxtLink :to="UtilSanction.PATH" class="mt-1 inline-block font-semibold underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="profile-restricted-link">
                    {{ t('profile.restrictedLink') }}
                </NuxtLink>
            </div>

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
                        <!-- 바꿀 수 없는 동안(이미 바꿈·정지 중)은 readonly — 값은 저장 요청에 그대로 실린다(`forms.md` 2-1) -->
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
                            :readonly="nicknameLocked || restricted"
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
                            :readonly="restricted"
                            :disabled="saving"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="profile-bio"
                        />
                    </template>
                </LabeledField>
            </section>

            <StickySaveBar v-if="!restricted" :dirty="dirty" :saving="saving" :count="changedCount" :error-count="errorCount" :error="formError" :disabled="errorCount > 0" revertable submit @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';
import { PROFILE_BIO_MAX } from '~/composables/profile/api/useProfileApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilNicknameChange from '~/utils/util-nickname-change';
import UtilSanction from '~/utils/util-sanction';
import UtilValidation from '~/utils/util-validation';

/**
 * 프로필 `/settings/profile` — 로그인 필요. 닉네임·한 줄 소개를 고친다(서버 API 가 없어 목업 `GET/PUT /api/me/profile`).
 * 닉네임 규칙은 가입 화면과 같은 화면 검사(`UtilValidation.nickname` — 서버 `UserPolicy`)로 미리 알리고, 중복·예약어·변경 횟수는 서버가 정한다:
 * `409 NICKNAME_TAKEN` · `400 VALIDATION fields.nickname|bio` 는 칸 아래, `409 NICKNAME_CHANGE_LIMIT` 은 닉네임 칸 아래 + 칸 잠금, 그 밖은 저장 줄 안 한 줄.
 * 닉네임 변경(`member-mypage-contract.md` 결정): 가입 뒤 1번만 — 그 1번은 언제든(날짜 대기 없음), 쓰고 나면 더 못 바꾼다(변경권 없음).
 * 상태는 `nicknameChange` 를 둘로 가른다(`UtilNicknameChange.stageOf`): 바꿀 수 있음(1번 남음 안내) · 이미 바꿈(칸 잠금).
 * 닉네임을 바꾸는 저장은 확인을 한 번 묻는다(이번에 바꾸면 앞으로는 못 바꾼다). 소개만 고치는 저장은 횟수를 쓰지 않아 묻지 않는다.
 * 정지·영구 정지 중(로그인 정보 `restriction`, 또는 저장 때 서버 `403 ACCOUNT_RESTRICTED`)이면 폼은 읽기 전용이고 저장 줄을 그리지 않으며 제재 안내(`/sanction`) 링크를 보인다.
 * 저장하면 로그인 정보(`me`)를 다시 받아 셸의 닉네임을 맞춘다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('profile.title'), description: () => t('profile.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const api = useProfileApi();
const auth = useAuth();
const toast = useAppToast();
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
/** 저장 때 서버가 정지 중이라고 알려 왔다(로그인 정보가 아직 정상이어도) */
const restrictedByServer = ref(false);
/** 정지·영구 정지 중 — 프로필 편집은 읽기 전용 */
const restricted = computed(() => restrictedByServer.value || UtilSanction.isRestricted(auth.restriction.value));

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

/** 닉네임 변경의 지금 상태 — 바꿀 수 있음 · 이미 바꿈 */
const nicknameStage = computed(() => UtilNicknameChange.stageOf(saved.value?.nicknameChange));
/** 닉네임을 바꿀 수 없는가(이미 바꿈) */
const nicknameLocked = computed(() => nicknameStage.value !== 'FREE');

const nicknameHelp = computed(() => {
    if (!saved.value) return '';
    return nicknameLocked.value ? t('profile.nicknameUsedUp') : t('profile.nicknameOnce');
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

/** 닉네임을 바꾸는 저장의 확인 — 가입 뒤 1번뿐인 변경을 쓰는 일이라 한 번 묻는다 */
async function confirmNicknameChange(): Promise<boolean> {
    const current = saved.value;
    if (!current || !nicknameChanged.value) return true;
    const message = t('profile.nicknameConfirmText', { from: current.nickname, to: form.nickname });
    return await confirm({ title: t('profile.nicknameConfirmTitle'), message, confirmLabel: t('profile.nicknameConfirmButton') });
}

async function save() {
    if (saving.value || !dirty.value || restricted.value) return;
    formError.value = '';
    if (errorCount.value > 0) {
        if (errors.value.nickname) nicknameInput.value?.$el.focus();
        return;
    }
    if (!(await confirmNicknameChange())) return;
    saving.value = true;
    try {
        const value = await api.save({ nickname: form.nickname, bio: form.bio.trim() });
        const nicknameDone = nicknameChanged.value;
        fill(value);
        toast.success(t('common.saved'));
        // 셸(헤더·햄버거)의 닉네임 — 로그인 정보를 다시 받는다. 실패해도 저장은 끝났다
        if (nicknameDone) await auth.refresh().catch(() => undefined);
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ACCOUNT_RESTRICTED') {
            // 정지 중 — 고친 값을 되돌리고 읽기 전용 + 제재 안내로 바꾼다(다음부터는 요청 없이 같은 안내)
            restrictedByServer.value = true;
            if (saved.value) fill(saved.value);
        } else if (e instanceof ApiError && (e.code === 'NICKNAME_TAKEN' || e.code === 'NICKNAME_CHANGE_LIMIT')) {
            // 화면이 본 상태가 낡았다(다른 탭에서 먼저 바꿈 등) — 서버가 알려 준 대로 칸을 잠근다. 문구는 칸 아래(잠근 뒤에 붙인다 — `fill` 이 지우지 않게)
            if (e.code === 'NICKNAME_CHANGE_LIMIT' && saved.value) {
                const change = saved.value.nicknameChange;
                saved.value = { ...saved.value, nicknameChange: { ...change, allowed: false, changeCount: Math.max(change.changeCount, change.freeLimit) } };
            }
            serverErrors.nickname = e.message;
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
    if (!dirty.value || saving.value || restricted.value) return true;
    return await confirm({ title: t('settings.leaveTitle'), message: t('settings.leaveMessage'), confirmLabel: t('settings.leaveConfirm'), tone: 'danger' });
});
</script>
