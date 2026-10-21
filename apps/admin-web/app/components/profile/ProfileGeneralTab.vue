<template>
    <!-- 내 정보 > 일반 — 기본 정보 카드 + 회원 정보 카드(닉네임 수정) + 취소/저장. 읽는 중·오류·빈 결과도 여기서 다룬다 -->
    <div class="flex flex-col gap-4" aria-live="polite" :aria-busy="loading">
        <!-- 읽는 중 -->
        <div v-if="loading && !profile" class="flex justify-center py-12">
            <ProgressSpinner style="width: 2.25rem; height: 2.25rem" :aria-label="t('common.loading')" />
        </div>

        <!-- 읽기 실패 — 서버 문구를 그대로 보이고 다시 시도 -->
        <ErrorState v-else-if="error && !profile" :title="t('profile.loadFailed')" :description="error.message" retry :retrying="loading" @retry="load()" />

        <!-- 빈 결과 -->
        <EmptyState v-else-if="!profile" icon="pi pi-user" :title="t('profile.emptyTitle')" :description="t('profile.emptyBody')">
            <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" @click="load()" />
        </EmptyState>

        <!-- 값이 늦게 오므로 `:key` 로 프로필이 바뀌면(다시 읽으면) 폼을 새로 만든다 -->
        <form v-else :key="profile.id" class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget :title="t('profile.basicInfo')" anchor="basic-info">
                <div class="flex flex-col items-center gap-4 py-1 sm:flex-row sm:items-start">
                    <UserAvatar :name="profile.nickname" size="xlarge" />

                    <div class="min-w-0 flex-1 text-center sm:text-left">
                        <p class="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
                            <span class="break-all text-lg font-medium">{{ profile.nickname }}</span>
                            <RoleBadge :role="profile.role" />
                        </p>
                        <dl class="mt-3 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1.5 text-xs">
                            <dt class="text-muted-color">{{ t('profile.accountId') }}</dt>
                            <dd class="m-0 break-all tabular-nums">{{ profile.email ?? '-' }}</dd>
                            <dt class="text-muted-color">{{ t('profile.joinedAt') }}</dt>
                            <dd class="m-0 tabular-nums">{{ time.date(profile.createdAt) }}</dd>
                            <dt class="text-muted-color">{{ t('profile.lastLoginAt') }}</dt>
                            <dd class="m-0 tabular-nums">{{ profile.lastLoginAt ? time.full(profile.lastLoginAt) : '-' }}</dd>
                        </dl>
                    </div>
                </div>
            </AppGadget>

            <AppGadget :title="t('profile.memberInfo')" anchor="member-info" :changes="dirty ? 1 : 0" :invalid="fieldErrors.nickname ? 1 : 0">
                <!-- 저장 실패(칸에 붙지 않는 것) — 폼 위쪽 -->
                <Message v-if="formError" severity="error" :closable="false" size="small" class="mb-4">{{ formError }}</Message>

                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('profile.nickname')" :hint="t('profile.nicknameHint')" :help="t('profile.nicknameHelp')" :error="fieldErrors.nickname">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" ref="nicknameInput" v-model="form.nickname" name="nickname" autocomplete="nickname" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>

                    <!-- 읽기 전용 — 서버 값이라 `disabled` 가 아니라 `readonly` -->
                    <LabeledField :label="t('profile.email')" :help="t('profile.emailHelp')">
                        <template #default="{ id, describedBy }">
                            <InputText :id="id" :model-value="profile.email ?? ''" readonly :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>

                    <LabeledField :label="t('profile.role')">
                        <template #default="{ id, describedBy }">
                            <div :id="id" class="flex min-h-10 items-center rounded-md border border-(--line-strong) bg-surface-100 px-3 dark:bg-surface-800" role="textbox" aria-readonly="true" :aria-describedby="describedBy">
                                <RoleBadge :role="profile.role" />
                            </div>
                        </template>
                    </LabeledField>
                </div>
            </AppGadget>

            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" :disabled="saving || !dirty" @click="reset" />
                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="!dirty" />
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import UtilProfileMessages from '~/utils/util-profile-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();

const time = useDisplayTime();
const toast = useAppToast();
const { profile, loading, error, load, saveNickname } = useProfile();

/** 폼 값 — 읽은 프로필의 닉네임에서 시작한다 */
const form = reactive({ nickname: '' });
const fieldErrors = reactive<{ nickname?: string }>({});
const formError = ref('');
const saving = ref(false);
const nicknameInput = ref<{ $el: HTMLInputElement } | null>(null);

/** 바뀐 것이 있는가 — 저장·취소 단추가 이것을 본다 */
const dirty = computed(() => profile.value !== undefined && form.nickname !== profile.value.nickname);

function reset() {
    form.nickname = profile.value?.nickname ?? '';
    fieldErrors.nickname = undefined;
    formError.value = '';
}

// 프로필을 (다시) 받으면 폼을 그 값으로 채운다
watch(
    profile,
    () => {
        if (!saving.value) reset();
    },
    { immediate: true },
);

onMounted(() => void load());

async function save() {
    if (saving.value || !dirty.value) return;
    fieldErrors.nickname = undefined;
    formError.value = '';

    const problem = UtilValidation.nickname(form.nickname);
    if (problem) {
        fieldErrors.nickname = UtilProfileMessages.nicknameProblem(t, problem);
        nicknameInput.value?.$el.focus();
        return;
    }

    saving.value = true;
    try {
        await saveNickname(form.nickname);
        toast.success(t('common.saved'));
    } catch (e) {
        const failure = e instanceof ApiError ? e : null;
        if (failure?.code === 'VALIDATION' && failure.fields?.nickname) {
            fieldErrors.nickname = failure.fields.nickname;
        } else if (failure?.code === 'NICKNAME_TAKEN') {
            // 중복은 닉네임 칸의 문제다 — 서버 문구를 그대로 칸 아래에
            fieldErrors.nickname = failure.message;
        } else {
            formError.value = failure?.message ?? t('common.failed');
        }
        if (fieldErrors.nickname) nicknameInput.value?.$el.focus();
    } finally {
        saving.value = false;
    }
}
</script>
