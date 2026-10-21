<template>
    <!-- 소셜 로그인 공급자 한 곳의 입력 카드(설치 마법사용, 콤팩트) — 표지·이름·켜기 스위치(기본 꺼짐), 켜면 클라이언트 ID · 시크릿 · 콜백 주소 복사가 나온다. 서버는 부르지 않는다 -->
    <article class="rounded-xl border border-surface bg-surface-0 p-3.5 dark:bg-surface-900" :aria-labelledby="titleId" :data-provider="provider" :data-enabled="model.enabled" data-testid="install-social-card">
        <header class="flex items-center gap-2">
            <span class="grid size-7 shrink-0 place-items-center rounded-md text-xs font-bold" :class="UtilProvider.markClass(provider)" aria-hidden="true">
                {{ UtilProvider.mark(provider) }}
            </span>
            <h3 :id="titleId" class="min-w-0 flex-1 truncate text-base font-semibold">{{ providerName }}</h3>
            <label :for="toggleId" class="shrink-0 cursor-pointer text-xs text-muted-color">{{ model.enabled ? t('social.stateOn') : t('social.stateOff') }}</label>
            <ToggleSwitch v-model="model.enabled" :input-id="toggleId" :disabled="disabled" :aria-controls="bodyId" data-testid="install-social-enabled" />
        </header>

        <!-- 켰을 때만 입력칸이 나온다. 껐다 켜도 입력해 둔 값은 부모 객체에 그대로 남는다 -->
        <div v-if="model.enabled" :id="bodyId" data-testid="install-social-body">
            <div class="mt-3 flex flex-col gap-3">
                <LabeledField :label="t('social.clientIdLabel')" :error="errors?.clientId">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText
                            :id="id"
                            v-model="model.clientId"
                            type="text"
                            :name="`${provider.toLowerCase()}ClientId`"
                            autocomplete="off"
                            autocapitalize="off"
                            spellcheck="false"
                            :disabled="disabled"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="install-social-client-id"
                        />
                    </template>
                </LabeledField>

                <!-- 시크릿은 type=password — 값을 보여 주는 "보기" 버튼도 두지 않는다 -->
                <LabeledField :label="t('social.clientSecretLabel')" :error="errors?.clientSecret">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText
                            :id="id"
                            v-model="model.clientSecret"
                            type="password"
                            :name="`${provider.toLowerCase()}ClientSecret`"
                            autocomplete="off"
                            autocapitalize="off"
                            spellcheck="false"
                            :disabled="disabled"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            fluid
                            data-testid="install-social-client-secret"
                        />
                    </template>
                </LabeledField>
            </div>

            <!-- 콜백 주소 — 3단계 공개 주소 기준. 각 사 개발자 콘솔에 그대로 등록한다 -->
            <div class="mt-3">
                <p :id="callbackLabelId" class="text-xs font-medium">
                    {{ t('social.callbackLabel') }}
                    <small class="ml-1 font-normal text-muted-color">{{ t('social.callbackHint', { provider: providerName }) }}</small>
                </p>
                <div v-if="callbackUrl" class="mt-1 flex items-start gap-2">
                    <code :aria-labelledby="callbackLabelId" class="block min-w-0 flex-1 select-all break-all rounded-lg bg-surface-100 px-2.5 py-1.5 text-xs leading-relaxed dark:bg-surface-800" data-testid="install-social-callback">
                        {{ callbackUrl }}
                    </code>
                    <Button
                        type="button"
                        icon="pi pi-copy"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        class="shrink-0"
                        :aria-label="t('social.copyCallbackLabel', { provider: providerName })"
                        data-testid="install-social-copy"
                        @click="copyCallback"
                    />
                </div>
                <p v-else class="mt-1 text-xs text-muted-color" data-testid="install-social-callback-missing">{{ t('social.callbackMissing') }}</p>
            </div>
        </div>
    </article>
</template>

<script setup lang="ts">
import type { SocialFieldErrors } from '~/utils/util-admin-social';
import type { SocialProviderCode } from '~/composables/install/api/useInstallApi.types';
import type { SocialForm } from '~/utils/util-admin-social';
import UtilInstall from '~/utils/util-install';
import UtilProvider from '~/utils/util-provider';

/**
 * 설치 마법사 5단계의 공급자 카드 — 켜기 스위치(기본 꺼짐)와, 켰을 때만 나오는 클라이언트 ID·시크릿(`type=password`). 값은 부모의 객체를 그대로 고친다(`v-model`).
 * 켜 둔 공급자만 설치 요청에 담긴다(`UtilInstall.socialUsed`). 콜백 주소는 부모가 3단계 공개 주소로 만들어 넘긴다. 올바른 공개 주소가 없으면 빈 문자열이고 안내가 보인다.
 */
const model = defineModel<SocialForm>({ required: true });

interface Props {
    provider: SocialProviderCode;
    callbackUrl: string;
    errors?: SocialFieldErrors;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    errors: () => ({}),
    disabled: false,
});

const { t } = useI18n();
const toast = useAppToast();
const { copy } = useCopyText();

/** 공급자 이름(지금 언어) */
const providerName = computed(() => UtilInstall.providerLabel(t, props.provider));

const titleId = useId();
const callbackLabelId = useId();
const toggleId = useId();
const bodyId = useId();

async function copyCallback() {
    if (await copy(props.callbackUrl)) toast.success(t('social.copied', { provider: providerName.value }));
    else toast.error(t('social.copyFailed'), 6000);
}
</script>
