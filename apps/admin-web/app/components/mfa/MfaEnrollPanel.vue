<template>
    <!--
        OTP 등록 패널 — 로그인 카드(강제·유예 중 등록)와 내 정보 › 로그인 보안(자발 등록·다시 등록)이 같이 쓴다. 서버 호출은 부르는 쪽(`useMfaEnrollment`)이 하고 여기는 그리기만 한다.
        ① 앱 안내 + QR + 비밀키(4자씩, 복사) + 확인 코드 6자리 → ② 복구 코드 10개(2열, 복사·내려받기) + 「안전한 곳에 저장했어요」를 체크해야 [계속].
        좁은 곳(로그인 카드)에서는 위아래로, 넓은 곳(컨테이너 36rem 이상)에서는 QR 과 안내가 옆으로 선다.
    -->
    <div class="@container">
        <!-- 시작 중 -->
        <div v-if="starting" class="flex justify-center py-8">
            <ProgressSpinner style="width: 2.25rem; height: 2.25rem" :aria-label="t('common.loading')" />
        </div>

        <!-- 시작 실패 -->
        <ErrorState v-else-if="startError && !enrollment && !recoveryCodes" :title="t('mfa.startFailed')" :description="startError" retry @retry="emit('retry')" />

        <!-- ② 복구 코드 -->
        <div v-else-if="recoveryCodes" class="flex flex-col gap-4">
            <Message severity="success" :closable="false" size="small" role="status">
                <b>{{ doneTitle || t('mfa.enrolledTitle') }}</b>
                <span class="mt-0.5 block">{{ t('mfa.recoveryIntro') }}</span>
            </Message>
            <MfaRecoveryCodes v-model:saved="saved" :codes="recoveryCodes" :account="account" confirmable />
            <Button type="button" :label="finishLabel || t('mfa.continue')" icon="pi pi-arrow-right" icon-pos="right" :disabled="!saved" :fluid="fluid" @click="emit('finish')" />
        </div>

        <!-- ① QR · 비밀키 · 확인 코드 -->
        <form v-else-if="enrollment" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <ol class="m-0 flex list-decimal flex-col gap-1 pl-5 text-sm leading-relaxed">
                <li>{{ t('mfa.stepInstall') }}</li>
                <li>{{ t('mfa.stepScan') }}</li>
                <li>{{ t('mfa.stepCode') }}</li>
            </ol>

            <div class="flex flex-col items-center gap-4 @xl:flex-row @xl:items-start">
                <MfaQrCode :uri="enrollment.otpauthUri" :label="t('mfa.qrLabel', { account: enrollment.accountName })" :size="180" />
                <div class="flex w-full min-w-0 flex-col gap-1.5">
                    <span class="text-xs text-muted-color">{{ t('mfa.secretLabel') }}</span>
                    <div class="flex items-center gap-2">
                        <code class="min-w-0 flex-1 break-all rounded-md border border-(--line) bg-surface-50 px-2.5 py-1.5 font-mono text-sm tracking-wider dark:bg-surface-950" data-testid="mfa-secret">{{ UtilMfa.groupSecret(enrollment.secret) }}</code>
                        <Button type="button" icon="pi pi-copy" size="small" severity="secondary" variant="outlined" :aria-label="t('mfa.secretCopy')" v-tooltip.top="t('mfa.secretCopy')" @click="copySecret" />
                    </div>
                    <small class="text-xs text-muted-color">{{ t('mfa.secretHelp', { issuer: enrollment.issuer, account: enrollment.accountName }) }}</small>
                </div>
            </div>

            <!-- 잠금 — 여러 번 틀렸다 -->
            <Message v-if="lockSeconds > 0" severity="warn" :closable="false" size="small" role="status">
                <b>{{ t('mfa.lockedTitle') }}</b> {{ t('mfa.lockedRemaining', { time: UtilTime.mmss(lockSeconds) }) }}
            </Message>

            <MfaCodeInput ref="codeInput" v-model="code" :label="t('mfa.confirmCodeLabel')" :help="t('mfa.codeHelp')" :error="lockSeconds > 0 ? '' : codeError" :disabled="confirming || lockSeconds > 0" @complete="submit" />

            <div class="flex flex-wrap items-center justify-end gap-2">
                <slot name="secondary" />
                <Button type="submit" :label="t('mfa.confirmEnroll')" icon="pi pi-check" :loading="confirming" :disabled="lockSeconds > 0 || !UtilMfa.isOtp(code)" :fluid="fluid" />
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { MfaEnrollmentView } from '~/composables/mfa/api/useMfaApi.types';
import UtilTime from '#layers/display-time/utils/util-time';
import UtilMfa from '~/utils/util-mfa';

interface Props {
    enrollment: MfaEnrollmentView | null;
    recoveryCodes: string[] | null;
    starting?: boolean;
    startError?: string;
    confirming?: boolean;
    codeError?: string;
    /** 잠금 남은 초(0 = 안 잠김) */
    lockSeconds?: number;
    /** 내려받기 파일에 적을 계정 */
    account?: string;
    /** ② 의 성공 제목 — 없으면 「OTP 를 등록했어요」 */
    doneTitle?: string;
    /** ② 의 마치기 단추 이름 — 없으면 「계속」 */
    finishLabel?: string;
    /** 단추를 칸 폭으로(로그인 카드) */
    fluid?: boolean;
}

const props = withDefaults(defineProps<Props>(), { starting: false, startError: '', confirming: false, codeError: '', lockSeconds: 0, account: '', doneTitle: '', finishLabel: '', fluid: false });
const emit = defineEmits<{ confirm: [code: string]; retry: []; finish: [] }>();

const { t } = useI18n();
const toast = useAppToast();

const code = ref('');
const saved = ref(false);
const codeInput = ref<{ focus: () => void } | null>(null);

function submit(): void {
    if (props.confirming || props.lockSeconds > 0 || !UtilMfa.isOtp(code.value)) return;
    emit('confirm', code.value);
}

// 틀렸으면 칸을 비우고 다시 첫 칸으로
watch(
    () => props.codeError,
    (value) => {
        if (!value) return;
        code.value = '';
        void nextTick(() => codeInput.value?.focus());
    },
);
// 새 등록이 시작되면(다시 등록 포함) 입력과 체크를 비우고 코드 칸으로
watch(
    () => props.enrollment,
    (value) => {
        code.value = '';
        saved.value = false;
        if (value) void nextTick(() => codeInput.value?.focus());
    },
);

async function copySecret(): Promise<void> {
    if (!props.enrollment) return;
    try {
        await navigator.clipboard.writeText(props.enrollment.secret);
        toast.success(t('mfa.secretCopied'));
    } catch {
        toast.error(t('mfa.copyFailed'), 6000);
    }
}
</script>
