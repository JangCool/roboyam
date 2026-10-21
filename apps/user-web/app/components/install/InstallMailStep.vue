<template>
    <div class="flex flex-col gap-4">
        <!-- 건너뛰었다 -->
        <div v-if="mode === 'skip'" class="rounded-xl bg-surface-100 p-4 text-center dark:bg-surface-800" data-testid="mail-skipped">
            <p class="font-semibold">{{ t('install.mailSkippedTitle') }}</p>
            <p class="mt-1 text-sm leading-relaxed text-muted-color">{{ t('mail.noMailNote') }}</p>
            <Button type="button" :label="t('install.mailSet')" size="small" class="mt-3" data-testid="mail-set" @click="mode = 'set'" />
        </div>

        <template v-else>
            <Message v-if="!cryptoOk" severity="warn" :closable="false" size="small" data-testid="mail-crypto-lock">{{ t('install.cryptoLockMail') }}</Message>

            <MailFields v-model="model" :errors="UtilInstall.sliceErrors(errors, 'mail')" :disabled="disabled" :credentials-locked="!cryptoOk" />

            <!-- 연결 확인 — 접속·인증까지만 하고 메일은 보내지 않는다. 입력을 고치면 결과는 지워진다 -->
            <div class="flex flex-col gap-2">
                <div class="flex items-center gap-3">
                    <Button type="button" :label="t('mail.checkButton')" icon="pi pi-bolt" size="small" severity="secondary" variant="outlined" :loading="check.status === 'checking'" :disabled="disabled" data-testid="mail-check" @click="emit('check')" />
                    <small class="text-xs text-muted-color">{{ t('mail.checkNote') }}</small>
                </div>
                <p
                    v-if="check.status === 'ok' || check.status === 'fail'"
                    class="flex items-start gap-1.5 text-sm"
                    :class="check.status === 'ok' ? 'text-green-700 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
                    :role="check.status === 'ok' ? 'status' : 'alert'"
                    :data-status="check.status"
                    data-testid="mail-check-result"
                >
                    <i :class="check.status === 'ok' ? 'pi pi-check-circle' : 'pi pi-times-circle'" class="mt-0.5" aria-hidden="true" />
                    <span class="min-w-0">{{ check.text }}</span>
                </p>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import UtilInstall, { type InstallFieldErrors, type InstallMode } from '~/utils/util-install';
import type { MailCheckState, MailForm } from '~/utils/util-mail';

/**
 * 4단계 — 메일 서버(건너뛸 수 있다). 서버는 부르지 않고 이벤트로 알린다(부모가 `연결 확인` 을 한다).
 *
 * - `mode` 가 `skip` 이면 입력칸 대신 "건너뛰었어요" 와 다시 설정하는 버튼이 보인다(건너뛰기는 아래 단계 막대의 버튼). 입력해 둔 값은 지워지지 않는다.
 * - 서버에 암호화 키가 없으면(`cryptoOk` false) 아이디·비밀번호 칸을 잠근다 — 인증이 필요 없는 메일 서버만 등록할 수 있다.
 * - 연결 확인 결과는 입력을 고치면 사라진다(부모가 `check` 를 비운다).
 */
const model = defineModel<MailForm>({ required: true });
const mode = defineModel<InstallMode>('mode', { required: true });

interface Props {
    cryptoOk: boolean;
    /** 칸 경로(`mail.host` …) → 오류 문구 */
    errors: InstallFieldErrors;
    check: MailCheckState;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
});

const emit = defineEmits<{ check: [] }>();

const { t } = useI18n();
</script>
