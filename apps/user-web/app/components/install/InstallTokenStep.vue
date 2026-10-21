<template>
    <div class="flex flex-col gap-4">
        <!-- 설치 인증이 만료돼 되돌아왔다 -->
        <Message v-if="notice" severity="warn" :closable="false" size="small" data-testid="install-notice">{{ notice }}</Message>

        <!-- 인증 전: 토큰 입력(확인 버튼은 아래 단계 막대의 "확인" — 이 칸에서 Enter 를 눌러도 같다) -->
        <template v-if="!authorized">
            <Message v-if="locked" severity="warn" :closable="false" size="small" data-testid="install-token-locked">
                {{ t('install.tokenLocked', { remaining: UtilTime.mmss(lockRemaining) }) }}
            </Message>
            <PasswordField v-model="token" :label="t('install.tokenLabel')" name="installToken" autocomplete="off" :error="error" :disabled="busy || locked" />
        </template>

        <!-- 인증 뒤: 점검 결과 -->
        <template v-else>
            <p class="flex items-center gap-1.5 text-sm text-green-700 dark:text-green-400" data-testid="install-token-ok">
                <i class="pi pi-check-circle" aria-hidden="true" />
                {{ t('install.tokenOk') }}
            </p>

            <div v-if="checksBusy && !checks" class="flex flex-col gap-2" role="status" :aria-label="t('install.checksLoading')" data-testid="install-checks-loading">
                <div v-for="n in 4" :key="n" class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>

            <ErrorState v-else-if="!checks" :title="t('install.checksFailed')" :description="checksError" retry :retrying="checksBusy" data-testid="install-checks-error" @retry="emit('recheck')" />

            <template v-else>
                <ul class="m-0 flex list-none flex-col gap-2 p-0" :aria-label="t('install.checksList')" data-testid="install-checks">
                    <li
                        v-for="check in checks.checks"
                        :key="check.key"
                        class="flex items-start gap-2.5 rounded-lg border px-3 py-2.5"
                        :class="toneOf(check).box"
                        :data-key="check.key"
                        :data-tone="toneOf(check).tone"
                    >
                        <i :class="[toneOf(check).icon, 'mt-0.5 text-base']" aria-hidden="true" />
                        <div class="min-w-0 flex-1">
                            <p class="text-sm font-medium">
                                {{ check.label }}
                                <span class="sr-only">{{ UtilInstallMessages.checkLine(t, check) }}</span>
                            </p>
                            <p class="mt-0.5 break-words text-xs leading-relaxed text-muted-color">{{ check.detail }}</p>
                        </div>
                    </li>
                </ul>

                <Message v-if="blocked" severity="error" :closable="false" size="small" data-testid="install-check-blocked">{{ t('install.checkBlockingNote') }}</Message>
                <Message v-else-if="!cryptoOk" severity="warn" :closable="false" size="small" data-testid="install-check-crypto">{{ t('install.checkCryptoNote') }}</Message>

                <div>
                    <Button type="button" :label="t('install.recheck')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="checksBusy" data-testid="install-recheck" @click="emit('recheck')" />
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { InstallCheck, InstallChecksView } from '~/composables/install/api/useInstallApi.types';
import UtilInstall, { type CheckTone } from '~/utils/util-install';
import UtilInstallMessages from '~/utils/util-install-messages';
import UtilTime from '#layers/display-time/utils/util-time';

/**
 * 1단계 — 설치 토큰 입력 + 점검 결과. 서버는 부르지 않고 이벤트로 알린다(부모가 `확인`·`다시 점검` 을 한다).
 *
 * - 토큰은 `type=password` 이고 `autocomplete=off` 다. 맞히고 나면 부모가 값을 비운다.
 * - 점검 결과: 통과 · 경고(암호화 키 없음 — 설치는 계속할 수 있다) · 실패(설치를 막는다). 막는 실패가 있으면 이유와 함께 보이고 부모가 "다음" 을 막는다.
 * - 토큰을 여러 번 틀려 잠기면(`lockRemaining` > 0) 입력을 잠그고 남은 시간을 보여 준다.
 */
const token = defineModel<string>({ required: true });

const { t } = useI18n();

interface Props {
    /** 이 세션이 토큰을 맞혔다 */
    authorized: boolean;
    /** 토큰 확인 중 */
    busy: boolean;
    /** 토큰 입력칸 아래 오류 */
    error?: string;
    /** 토큰 잠금이 풀릴 때까지 남은 초(0 이면 잠기지 않음) */
    lockRemaining: number;
    /** 맨 위 안내(설치 인증이 만료됨) */
    notice?: string;
    checks: InstallChecksView | null;
    checksBusy: boolean;
    checksError: string;
}

const props = withDefaults(defineProps<Props>(), {
    error: '',
    notice: '',
});

const emit = defineEmits<{ recheck: [] }>();

const TONE: Record<CheckTone, { box: string; icon: string }> = {
    ok: { box: 'border-surface', icon: 'pi pi-check-circle text-green-600 dark:text-green-400' },
    warn: { box: 'border-amber-300 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/40', icon: 'pi pi-exclamation-triangle text-amber-600 dark:text-amber-400' },
    fail: { box: 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/40', icon: 'pi pi-times-circle text-red-600 dark:text-red-400' },
};

/** 항목의 갈래와 그 모양(템플릿에서 `Util*` 호출은 타입 검사가 `any` 로 보므로 스크립트에서 계산한다) */
function toneOf(check: InstallCheck): { tone: CheckTone; box: string; icon: string } {
    const tone = UtilInstall.checkTone(check);
    return { tone, ...TONE[tone] };
}

const locked = computed(() => props.lockRemaining > 0);
const blocked = computed(() => props.checks !== null && !UtilInstall.canProceed(props.checks));
const cryptoOk = computed(() => UtilInstall.cryptoKeyOk(props.checks));
</script>
