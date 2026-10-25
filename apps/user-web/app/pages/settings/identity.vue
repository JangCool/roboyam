<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 설정」 -->
    <div data-testid="identity-page">
        <h1 class="mb-4 text-lg font-bold">{{ t('identity.title') }}</h1>

        <!-- 인증 실패 — 사용자가 인증창을 닫은 것은 보이지 않는다 -->
        <Message v-if="failureCopy" severity="error" :closable="false" size="small" class="mb-4" data-testid="identity-failure">
            <b>{{ failureCopy.title }}</b> {{ failureCopy.text }}
        </Message>

        <div v-if="loadStatus === 'loading' || loadStatus === 'idle'" class="flex flex-col gap-4" role="status" :aria-label="t('identity.loading')">
            <div v-for="n in 2" :key="n">
                <div class="mb-2 h-3 w-20 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>

        <ErrorState v-else-if="loadStatus === 'error' || !status" :title="t('identity.loadFailed')" :description="loadError" retry :retrying="retrying" data-testid="identity-error" @retry="retry" />

        <!-- 인증됨 — 인증 일시와 만 19세 이상 여부만(생년월일 전체는 보이지 않는다). 인증은 한 번이면 끝이라 다시 인증 단추가 없다 -->
        <template v-else-if="status.verified">
            <section :aria-labelledby="`${uid}-state`">
                <h2 :id="`${uid}-state`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('identity.stateTitle') }}</h2>
                <dl class="m-0 rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" data-testid="identity-verified">
                    <div class="flex min-h-12 items-center gap-3 border-b border-surface px-4 py-2.5">
                        <dt class="w-28 shrink-0 text-sm text-muted-color">{{ t('identity.stateLabel') }}</dt>
                        <dd class="m-0 flex items-center gap-1.5 text-sm font-medium text-green-700 dark:text-green-400">
                            <i class="pi pi-verified" aria-hidden="true" />{{ t('identity.verified') }}
                        </dd>
                    </div>
                    <div class="flex min-h-12 items-center gap-3 border-b border-surface px-4 py-2.5">
                        <dt class="w-28 shrink-0 text-sm text-muted-color">{{ t('identity.verifiedAt') }}</dt>
                        <dd class="m-0 text-sm tabular-nums">{{ time.full(status.verifiedAt) }}</dd>
                    </div>
                    <div class="flex min-h-12 items-center gap-3 px-4 py-2.5">
                        <dt class="w-28 shrink-0 text-sm text-muted-color">{{ t('identity.ageLabel') }}</dt>
                        <dd class="m-0 flex items-center gap-1.5 text-sm" data-testid="identity-age">
                            <i :class="status.adult ? 'pi pi-check-circle text-green-700 dark:text-green-400' : 'pi pi-minus-circle text-muted-color'" aria-hidden="true" />
                            {{ status.adult ? t('identity.adult') : t('identity.minor') }}
                        </dd>
                    </div>
                </dl>
                <Message v-if="!status.adult" severity="warn" :closable="false" size="small" class="mt-3" data-testid="identity-minor">
                    <b>{{ t('identity.minorBlocked') }}</b>
                    <span class="mt-0.5 block">{{ UtilIdentityMessages.minorRelease(t, status.adultFrom) }}</span>
                </Message>
                <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ t('identity.onceNote') }}</p>
            </section>
        </template>

        <!-- 미인증 — 왜 필요한지, 무엇을 저장하는지, [휴대폰 본인인증 하기] -->
        <template v-else>
            <section class="mb-6" :aria-labelledby="`${uid}-why`">
                <h2 :id="`${uid}-why`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('identity.whyTitle') }}</h2>
                <div class="rounded-xl border border-surface bg-surface-0 px-4 py-3.5 dark:bg-surface-900">
                    <p class="m-0 text-sm leading-relaxed">{{ t('identity.whyText') }}</p>
                    <ul class="m-0 mt-2.5 flex list-none flex-col gap-1.5 p-0 text-sm">
                        <li v-for="use in USES" :key="use.key" class="flex items-center gap-2">
                            <i :class="use.icon" class="w-4 text-center text-muted-color" aria-hidden="true" />{{ t(use.key) }}
                        </li>
                    </ul>
                    <p class="m-0 mt-2.5 text-xs leading-relaxed text-muted-color">{{ t('identity.whyAdult') }}</p>
                </div>
            </section>

            <section class="mb-6" :aria-labelledby="`${uid}-stored`">
                <h2 :id="`${uid}-stored`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('identity.storedTitle') }}</h2>
                <div class="rounded-xl border border-surface bg-surface-0 px-4 py-3.5 text-sm leading-relaxed dark:bg-surface-900">
                    <p class="m-0 flex items-start gap-2"><i class="pi pi-database mt-1 text-muted-color" aria-hidden="true" />{{ t('identity.storedYes') }}</p>
                    <p class="m-0 mt-1.5 flex items-start gap-2"><i class="pi pi-eye-slash mt-1 text-muted-color" aria-hidden="true" />{{ t('identity.storedNo') }}</p>
                </div>
            </section>

            <Button type="button" :label="t('identity.verifyButton')" icon="pi pi-mobile" :loading="verifying" class="w-full sm:w-auto" data-testid="identity-verify" @click="onVerify" />
            <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ t('identity.verifyHelp') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { IdentityVerifyOutcome } from '~/composables/identity/useIdentity.types';
import UtilIdentityMessages from '~/utils/util-identity-messages';

/**
 * 본인인증 `/settings/identity` — 로그인 필요(`auth` 미들웨어, 서버도 401). 설정 묶음의 메뉴로 온다(결제 직전 확인 대화상자 `useIdentityGate` 와 같은 흐름).
 * 상태별: 미인증(왜·무엇을 저장·[휴대폰 본인인증 하기]) · 인증됨(인증 일시·만 19세 이상 여부) · 인증됨·만 19세 미만(막는 안내 + 19세 생일).
 * 인증 흐름은 `useIdentity().verify()` — 서버가 가짜 구현이면 「가짜 본인인증(개발용)」 대화상자, 포트원이면 포트원 인증창(브라우저 SDK 를 아직 넣지 않아 지금은 「포트원 인증창은 아직 연결되지 않았어요」 실패).
 * 실패는 화면 위 한 줄(이미 다른 계정에서 인증 · 탈퇴 후 30일 · 인증 업체 오류 · 시간 지남), 사용자가 창을 닫으면 아무것도 보이지 않는다.
 *
 * 인증은 한 번이면 끝이라 다시 인증 단추가 없다(서버도 409 `IDENTITY_ALREADY_VERIFIED`). 생년월일이 바뀌는 일은 고객센터로.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('identity.title'), description: () => t('identity.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const identity = useIdentity();
const { status, loadStatus, loadError } = identity;
const toast = useAppToast();
const time = useDisplayTime();
const uid = useId();

const USES = [
    { key: 'identity.useCharge', icon: 'pi pi-wallet' },
    { key: 'identity.useGroupBuy', icon: 'pi pi-shopping-bag' },
    { key: 'identity.useRefund', icon: 'pi pi-replay' },
    { key: 'identity.useSignupPoint', icon: 'pi pi-gift' },
] as const;

const verifying = ref(false);
const retrying = ref(false);
const failure = ref<Extract<IdentityVerifyOutcome, { kind: 'FAILED' }> | null>(null);
const failureCopy = computed(() => (failure.value ? UtilIdentityMessages.failure(t, failure.value, time.value) : null));

async function retry(): Promise<void> {
    retrying.value = true;
    await identity.load();
    retrying.value = false;
}

async function onVerify(): Promise<void> {
    if (verifying.value) return;
    verifying.value = true;
    failure.value = null;
    const outcome = await identity.verify();
    verifying.value = false;
    if (outcome.kind === 'FAILED') failure.value = outcome;
    else if (outcome.kind === 'VERIFIED') toast.success(t('identity.verifiedToast'));
}

// 화면에 들어올 때마다 최신으로(받아 둔 값이 있으면 그대로 보이면서 바꾼다)
void identity.load();
</script>
