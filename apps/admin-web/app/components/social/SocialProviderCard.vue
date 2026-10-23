<template>
    <!--
        소셜 로그인 공급자 한 곳의 설정 구획(`/settings/social-login`) — community `SocialProviderCard.vue` 를 옮긴 것이다: 상태 딱지 · 상태 설명 · 콜백 주소 복사 · 클라이언트 ID/시크릿 · 켜기 · 저장 · 설정 삭제.
        구획 하나가 `<form>` 하나다 — **카드마다 따로 저장한다**(저장 줄은 카드 맨 아래, `forms.md` 2-4). 서버는 부르지 않고 이벤트로 알린다(`save`·`remove`).
        - 시크릿은 서버가 주지 않는다 — 늘 빈 칸에서 시작하고 「입력됨」(`secretSet`)만 보인다. 저장에 성공하면(`setting` 이 새 객체로 바뀌면) 비운다. 실패하면 남겨 다시 시도할 수 있다.
        - 시크릿 필수 판정은 `!secretSet`(저장된 것이 없거나 지금 서버 키로 풀 수 없음). 행이 있는데 풀 수 없으면 「다시 입력해 주세요」 안내가 카드 위에 선다.
        - 콜백 주소가 없으면(사이트 공개 주소 없음) 복사 대신 「공개 주소를 먼저 정해 주세요」 + 일반 설정 링크.
        - 읽기 전용(`editable` false, `site.read` 만): 입력칸 `readonly`·켜기 잠금, 저장·삭제 단추를 그리지 않는다. 서버 암호화 키가 없으면 입력·저장을 잠근다(삭제는 된다 — 서버도 키 없이 지운다).
        - 서버가 거절한 칸 문구(`fields.clientId|clientSecret|enabled`)는 그 칸 아래(켜기는 스위치 아래), 칸에 붙지 않는 오류(`formError`)는 저장 줄 위 한 줄.
    -->
    <form novalidate :data-provider="setting.provider" @submit.prevent="submit">
        <AppGadget :title="setting.label" :anchor="`provider-${setting.id}`" :changes="changes" :invalid="invalid">
            <template #actions>
                <Tag class="social-badge shrink-0" :value="UtilSocialLoginMessages.badge(t, badge)" :severity="BADGE_SEVERITY[badge]" />
            </template>

            <div class="flex flex-col gap-4">
                <div class="flex flex-col gap-0.5">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ UtilSocialLoginMessages.statusNote(t, setting, cryptoKeyConfigured) }}</p>
                    <p v-if="setting.updatedAt" class="m-0 text-xs text-muted-color">
                        {{ setting.updatedBy ? t('social.updatedBy', { at: time.full(setting.updatedAt), name: setting.updatedBy }) : t('social.updatedAt', { at: time.full(setting.updatedAt) }) }}
                    </p>
                </div>

                <Message v-if="setting.source === 'SETTINGS' && !setting.secretSet" severity="warn" :closable="false" size="small">{{ t('social.secretReenterNotice') }}</Message>

                <!-- 콜백 주소 — 각 사 개발자 콘솔에 그대로 등록한다 -->
                <div>
                    <p :id="callbackLabelId" class="m-0 text-sm font-medium">
                        {{ t('social.callbackLabel') }}
                        <small class="ml-1 text-xs font-normal text-muted-color">{{ t('social.callbackHint', { name: setting.label }) }}</small>
                    </p>
                    <div v-if="setting.callbackUrl" class="mt-1.5 flex max-w-xl items-start gap-2">
                        <code :aria-labelledby="callbackLabelId" class="block min-w-0 flex-1 select-all rounded-lg bg-surface-100 px-3 py-2 text-xs leading-relaxed [overflow-wrap:anywhere] dark:bg-surface-800">{{ setting.callbackUrl }}</code>
                        <Button type="button" :label="t('social.copy')" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" class="shrink-0" :aria-label="t('social.copyAria', { name: setting.label })" @click="copyCallback" />
                    </div>
                    <p v-else class="m-0 mt-1.5 text-xs leading-relaxed text-amber-700 dark:text-amber-400">
                        {{ t('social.callbackMissing') }}
                        <NuxtLink to="/settings/general" class="text-primary underline-offset-2 hover:underline">{{ t('social.callbackMissingLink') }}</NuxtLink>
                    </p>
                </div>

                <LabeledField :label="t('social.clientIdLabel')" :error="errors.clientId">
                    <template #default="{ id, describedBy, invalid: fieldInvalid }">
                        <InputText
                            :id="id"
                            v-model="form.clientId"
                            name="clientId"
                            size="small"
                            autocomplete="off"
                            autocapitalize="off"
                            spellcheck="false"
                            :readonly="!editable || formLocked"
                            :invalid="fieldInvalid"
                            :aria-describedby="describedBy"
                            aria-required="true"
                            class="w-full max-w-xl"
                        />
                    </template>
                </LabeledField>

                <div class="max-w-xl">
                    <NotificationSecretField
                        v-model="form.clientSecret"
                        :label="UtilSocialLogin.secretRequired(setting) ? t('social.clientSecretLabelRequired') : t('social.clientSecretLabel')"
                        :saved="setting.secretSet"
                        :help="UtilSocialLoginMessages.secretHelp(t, setting)"
                        :error="errors.clientSecret"
                        :disabled="!editable || formLocked"
                    />
                </div>

                <div class="flex max-w-xl flex-col gap-1">
                    <div class="flex items-center gap-3">
                        <label :for="toggleId" class="min-w-0 flex-1" :class="editable && !formLocked ? 'cursor-pointer' : ''">
                            <span class="block text-sm font-medium">{{ t('social.enabledLabel') }}</span>
                            <small class="block text-xs text-muted-color">{{ t('social.enabledHelp', { name: setting.label }) }}</small>
                        </label>
                        <ToggleSwitch v-model="form.enabled" :input-id="toggleId" :disabled="!editable || formLocked" :invalid="Boolean(errors.enabled)" :aria-describedby="errors.enabled ? `${toggleId}-msg` : undefined" />
                    </div>
                    <p v-if="errors.enabled" :id="`${toggleId}-msg`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ errors.enabled }}</p>
                </div>

                <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

                <div v-if="editable" class="flex flex-wrap items-center gap-2">
                    <Button v-if="setting.source === 'SETTINGS'" type="button" :label="t('social.remove')" icon="pi pi-trash" size="small" severity="danger" variant="text" :loading="removing" :disabled="busy" @click="emit('remove')" />
                    <div class="ml-auto flex flex-wrap items-center gap-2">
                        <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="busy || changes === 0" @click="reset" />
                        <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="formLocked || changes === 0" />
                    </div>
                </div>
            </div>
        </AppGadget>
    </form>
</template>

<script setup lang="ts">
import type { SocialProviderSaveRequest, SocialProviderSettingView } from '~/composables/social/api/useSocialProviderSettingsApi.types';
import type { SocialBadgeTone, SocialFieldErrors } from '~/utils/util-social-login';
import UtilSocialLogin from '~/utils/util-social-login';
import UtilSocialLoginMessages from '~/utils/util-social-login-messages';

interface Props {
    setting: SocialProviderSettingView;
    /** false 면 서버에 암호화 키가 없어 저장할 수 없다 — 입력·저장을 잠근다 */
    cryptoKeyConfigured: boolean;
    /** 저장·삭제 권한(`site.secret`)이 있는가 — 없으면 읽기 전용 */
    editable?: boolean;
    /** 이 카드를 저장하는 중 */
    saving?: boolean;
    /** 이 카드의 설정을 지우는 중 */
    removing?: boolean;
    /** 다른 카드가 저장·삭제 중이라 잠시 잠근다 */
    locked?: boolean;
    /** 서버가 거절한 칸 문구 — 새 객체가 올 때마다 칸 아래에 보인다 */
    serverErrors?: SocialFieldErrors;
    /** 칸에 붙지 않는 저장·삭제 오류(서버 문구 그대로) */
    formError?: string;
}

const props = withDefaults(defineProps<Props>(), { editable: false, saving: false, removing: false, locked: false, serverErrors: undefined, formError: '' });

const emit = defineEmits<{ save: [body: SocialProviderSaveRequest]; remove: []; changes: [count: number] }>();

const { t } = useI18n();
const toast = useAppToast();
const time = useDisplayTime();

const callbackLabelId = useId();
const toggleId = useId();

const BADGE_SEVERITY: Record<SocialBadgeTone, 'success' | 'warn' | 'secondary' | 'contrast'> = { active: 'success', inactive: 'warn', off: 'secondary', none: 'secondary' };

const form = reactive(UtilSocialLogin.initialForm(props.setting));
const errors = reactive<SocialFieldErrors>({});

const badge = computed(() => UtilSocialLogin.badge(props.setting));
const changes = computed(() => UtilSocialLogin.changes(form, props.setting));
const invalid = computed(() => Number(Boolean(errors.clientId)) + Number(Boolean(errors.clientSecret)) + Number(Boolean(errors.enabled)));
/** 이 카드든 다른 카드든 저장·삭제가 진행 중 */
const busy = computed(() => props.saving || props.removing || props.locked);
/** 입력·저장 잠금 — 진행 중이거나 서버에 암호화 키가 없을 때(삭제 단추는 `busy` 만 본다) */
const formLocked = computed(() => busy.value || !props.cryptoKeyConfigured);

watch(changes, (count) => emit('changes', count), { immediate: true });

/** 서버 값으로 다시 채운다 — 시크릿 칸은 비우고 오류는 걷는다 */
function reset(): void {
    Object.assign(form, UtilSocialLogin.initialForm(props.setting));
    errors.clientId = undefined;
    errors.clientSecret = undefined;
    errors.enabled = undefined;
}

// 설정 객체가 새로 바뀐 때만(저장 응답·삭제 뒤 새로 받음) 다시 채운다 — 실패하면 그대로라 입력한 값이 남는다
watch(() => props.setting, reset);

// 칸을 고치기 시작하면 그 칸의 오류는 걷는다
watch(() => form.clientId, () => (errors.clientId = undefined));
watch(() => form.clientSecret, () => (errors.clientSecret = undefined));
watch(() => form.enabled, () => (errors.enabled = undefined));

watch(
    () => props.serverErrors,
    (found) => {
        if (!found) return;
        errors.clientId = found.clientId;
        errors.clientSecret = found.clientSecret;
        errors.enabled = found.enabled;
    },
);

function submit(): void {
    if (!props.editable || formLocked.value || changes.value === 0) return;
    const found = UtilSocialLoginMessages.fieldErrors(t, UtilSocialLogin.validate(form, props.setting));
    errors.clientId = found.clientId;
    errors.clientSecret = found.clientSecret;
    if (found.clientId || found.clientSecret) return;
    emit('save', UtilSocialLogin.toRequest(form));
}

async function copyCallback(): Promise<void> {
    const url = props.setting.callbackUrl;
    if (!url) return;
    try {
        await navigator.clipboard.writeText(url);
        toast.success(t('social.copied', { name: props.setting.label }));
    } catch {
        toast.error(t('social.copyFailed'), 6000);
    }
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.social-badge :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
</style>
