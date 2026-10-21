<template>
    <div class="flex flex-col gap-4">
        <!-- 서버에 암호화 키가 없다 — 시크릿을 저장할 수 없어 이 단계는 건너뛴다 -->
        <Message v-if="!cryptoOk" severity="warn" :closable="false" size="small" data-testid="social-crypto-lock">{{ t('install.cryptoLockSocial') }}</Message>

        <!-- 건너뛰었다 -->
        <div v-else-if="mode === 'skip'" class="rounded-xl bg-surface-100 p-4 text-center dark:bg-surface-800" data-testid="social-skipped">
            <p class="font-semibold">{{ t('install.socialSkippedTitle') }}</p>
            <p class="mt-1 text-sm leading-relaxed text-muted-color">{{ t('install.socialSkippedBody') }}</p>
            <Button type="button" :label="t('install.socialSet')" size="small" class="mt-3" data-testid="social-set" @click="mode = 'set'" />
        </div>

        <template v-else>
            <p class="text-sm leading-relaxed text-muted-color">{{ t('install.socialIntro') }}</p>
            <ul class="m-0 flex list-none flex-col gap-3 p-0" data-testid="install-social-list">
                <li v-for="code in PROVIDERS" :key="code">
                    <InstallSocialCard v-model="model[code]" :provider="code" :callback-url="UtilSite.callbackUrl(publicUrl, code)" :errors="socialErrors(code)" :disabled="disabled" />
                </li>
            </ul>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { SocialProviderCode } from '~/composables/install/api/useInstallApi.types';
import UtilInstall, { type InstallFieldErrors, type InstallMode, type SocialForms } from '~/utils/util-install';
import UtilSite from '~/utils/util-site';

/**
 * 5단계 — 소셜 로그인 키(건너뛸 수 있다): 네이버·카카오·구글 카드 셋. 서버는 부르지 않는다.
 *
 * - 공급자마다 켜기 스위치가 있고 **기본은 꺼짐**이다. 켠 공급자만 입력칸이 나오고 설치 요청에 담긴다. 입력 규칙은 소셜 설정 화면과 같다(`UtilAdminSocial`).
 * - 콜백 주소는 **3단계 공개 주소** 기준으로 보여 준다(`publicUrl`).
 * - 서버에 암호화 키가 없으면(`cryptoOk` false) 시크릿을 저장할 수 없어 입력 대신 안내만 보이고, 건너뛴 것과 같다.
 * - `skip` 이어도 입력해 둔 값은 지워지지 않는다(다시 설정하기를 누르면 그대로 돌아온다).
 */
/** 공급자 순서(템플릿에서 `Util*` 를 직접 돌리면 타입 검사가 `any` 로 보므로 스크립트에 둔다) */
const PROVIDERS: readonly SocialProviderCode[] = UtilInstall.PROVIDERS;

const { t } = useI18n();

const model = defineModel<SocialForms>({ required: true });
const mode = defineModel<InstallMode>('mode', { required: true });

interface Props {
    cryptoOk: boolean;
    /** 3단계에서 입력한 공개 주소(입력 그대로) */
    publicUrl: string;
    /** 칸 경로(`social.NAVER.clientId` …) → 오류 문구 */
    errors: InstallFieldErrors;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
});

function socialErrors(code: SocialProviderCode) {
    return UtilInstall.sliceErrors(props.errors, `social.${code}`);
}
</script>
