<template>
    <!-- 설정 묶음(`pages/settings.vue`) 안의 한 화면 — 넓은 화면은 왼쪽 메뉴, 좁은 화면은 위에 「‹ 설정」 -->
    <div class="content-card" data-testid="identity-page">
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
                <dl class="m-0 panel-card" data-testid="identity-verified">
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
                <Message v-if="!status.adult && adultOnly" severity="warn" :closable="false" size="small" class="mt-3" data-testid="identity-minor">
                    <b>{{ t('identity.minorBlockedFor', { items: adultOnly }) }}</b>
                    <span class="mt-0.5 block">{{ UtilIdentityMessages.minorRelease(t, status.adultFrom) }}</span>
                </Message>
                <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ t('identity.onceNote') }}</p>
            </section>
        </template>

        <!-- 미인증 — 왜 필요한지, 무엇을 저장하는지, [휴대폰 본인인증 하기] -->
        <template v-else>
            <section class="mb-6" :aria-labelledby="`${uid}-why`">
                <h2 :id="`${uid}-why`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('identity.whyTitle') }}</h2>
                <!-- 쓰임새는 지금 열린 기능만(충전·공구 판매가 꺼져 있으면 빼고, 하나도 없으면 「지금은 필요한 기능이 없다」) — 관리자 QA 2026-11-17 -->
                <div class="panel-card px-4 py-3.5" data-testid="identity-why">
                    <template v-if="usesReady">
                        <p class="m-0 text-sm leading-relaxed">{{ uses.length ? t('identity.whyText') : t('identity.whyTextNone') }}</p>
                        <ul v-if="uses.length" class="m-0 mt-2.5 flex list-none flex-col gap-1.5 p-0 text-sm">
                            <li v-for="use in uses" :key="use.key" class="flex items-center gap-2">
                                <i :class="use.icon" class="w-4 text-center text-muted-color" aria-hidden="true" />{{ t(use.key) }}
                            </li>
                        </ul>
                        <p v-if="adultOnly" class="m-0 mt-2.5 text-xs leading-relaxed text-muted-color">{{ t('identity.whyAdultFor', { items: adultOnly }) }}</p>
                    </template>
                    <div v-else class="h-14 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
            </section>

            <section class="mb-6" :aria-labelledby="`${uid}-stored`">
                <h2 :id="`${uid}-stored`" class="mb-2 px-1 text-xs font-semibold text-muted-color">{{ t('identity.storedTitle') }}</h2>
                <div class="panel-card px-4 py-3.5 text-sm leading-relaxed">
                    <p class="m-0 flex items-start gap-2"><i class="pi pi-database mt-1 text-muted-color" aria-hidden="true" />{{ t('identity.storedYes') }}</p>
                    <p class="m-0 mt-1.5 flex items-start gap-2"><i class="pi pi-eye-slash mt-1 text-muted-color" aria-hidden="true" />{{ t('identity.storedNo') }}</p>
                </div>
            </section>

            <Button type="button" :label="t('identity.verifyButton')" icon="pi pi-mobile" :loading="verifying" class="w-full sm:w-auto" data-testid="identity-verify" @click="onVerify" />
            <p class="mt-2 px-1 text-xs leading-relaxed text-muted-color">{{ t('identity.verifyHelp') }}</p>
        </template>

        <!--
            19금 게시판 — 접근 설정·핀번호(adult-board-pin-contract). 전에는 따로 있는 설정 화면이었고 여기로 옮겼다(사용자 결정 2026-11-18).
            본인인증 성인일 때만 설정(`AdultBoardSettings`)을 올린다 — 인증 전·만 19세 미만은 한 줄 안내만(인증 단추는 바로 위에 있다).
            `status` 가 반응형이라 이 화면에서 인증을 방금 마치면 새로고침 없이 설정으로 바뀐다. 주소의 `#adult-board` 로 오면 이 구역까지 내려간다.
        -->
        <section
            v-if="loadStatus === 'ready' && status"
            :id="UtilAdultBoard.ANCHOR"
            class="mt-8 scroll-mt-24 border-t border-surface pt-6"
            :aria-labelledby="`${uid}-adult-board`"
            data-testid="identity-adult-board"
        >
            <h2 :id="`${uid}-adult-board`" class="mb-3 text-base font-bold">{{ t('adultBoard.title') }}</h2>
            <AdultBoardSettings v-if="status.verified && status.adult" />
            <p v-else class="m-0 panel-card px-4 py-3.5 text-sm leading-relaxed text-muted-color" data-testid="adult-board-not-adult">{{ t('adultBoard.notAdultText') }}</p>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { IdentityVerifyOutcome } from '~/composables/identity/useIdentity.types';
import UtilAdultBoard from '~/utils/util-adult-board';
import UtilIdentityMessages from '~/utils/util-identity-messages';

/**
 * 본인인증 `/settings/identity` — 로그인 필요(`auth` 미들웨어, 서버도 401). 설정 묶음의 메뉴로 온다(결제 직전 확인 대화상자 `useIdentityGate` 와 같은 흐름).
 * 상태별: 미인증(왜·무엇을 저장·[휴대폰 본인인증 하기]) · 인증됨(인증 일시·만 19세 이상 여부) · 인증됨·만 19세 미만(막는 안내 + 19세 생일).
 * 인증 흐름은 `useIdentity().verify()` — 서버가 가짜 구현이면 「가짜 본인인증(개발용)」 대화상자, 포트원이면 포트원 인증창(브라우저 SDK 를 아직 넣지 않아 지금은 「포트원 인증창은 아직 연결되지 않았어요」 실패).
 * 실패는 화면 위 한 줄(이미 다른 계정에서 인증 · 탈퇴 후 30일 · 인증 업체 오류 · 시간 지남), 사용자가 창을 닫으면 아무것도 보이지 않는다.
 *
 * 인증은 한 번이면 끝이라 다시 인증 단추가 없다(서버도 409 `IDENTITY_ALREADY_VERIFIED`). 생년월일이 바뀌는 일은 고객센터로.
 *
 * 맨 아래 「19금 게시판」 구역(`#adult-board`) — 성인 전용 게시판 접근 설정·핀번호(`AdultBoardSettings`, adult-board-pin-contract). 본인인증 성인에게만 설정이 보이고,
 * 인증 전·만 19세 미만은 한 줄 안내만 보인다. 옛 주소 `/settings/adult-board` 와 핀 입력 화면의 [핀번호를 잊었어요](`?reset=1`)가 이 구역으로 온다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('identity.title'), description: () => t('identity.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const route = useRoute();
const identity = useIdentity();
const { status, loadStatus, loadError } = identity;
const toast = useAppToast();
const time = useDisplayTime();
const uid = useId();

/*
 * 쓰임새는 지금 열린 기능만 보인다 — 포인트 충전·충전 환불은 `/api/me/points` 의 `chargeAvailable`, 공구 결제는 `/api/commerce/info` 의 `salesOpen`,
 * 가입 포인트는 본인인증이 아니라 이메일 인증 때 주므로(2026-11-17) 여기서 안내하지 않는다. 둘 중 하나를 못 받으면 그 기능은 빼고 보인다(없는 기능을 안내하지 않는다).
 */
const points = usePointSummary();
const commerce = useCommerceInfo();
const chargeOpen = computed(() => points.summary.value?.chargeAvailable === true);
const groupBuyOpen = computed(() => commerce.info.value?.salesOpen === true);
const usesReady = computed(() => (points.status.value === 'ready' || points.status.value === 'error') && commerce.info.value !== undefined);
const uses = computed(() =>
    [
        chargeOpen.value && { key: 'identity.useCharge', icon: 'pi pi-wallet' },
        groupBuyOpen.value && { key: 'identity.useGroupBuy', icon: 'pi pi-shopping-bag' },
        chargeOpen.value && { key: 'identity.useRefund', icon: 'pi pi-replay' },
    ].filter((use): use is { key: string; icon: string } => !!use),
);
/** 만 19세 이상만 되는 열린 기능(「포인트 충전 · 공동구매 참여」) — 없으면 빈 문자열이라 나이 안내를 그리지 않는다 */
const adultOnly = computed(() =>
    [chargeOpen.value && t('identity.useCharge'), groupBuyOpen.value && t('identity.useGroupBuy')].filter(Boolean).join(' · '),
);

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
const identityLoaded = identity.load();
void points.load();
void commerce.ensureLoaded().catch(() => null);

onMounted(async () => {
    // 「19금 게시판」 구역으로 왔으면(`#adult-board` — 옛 주소·핀 입력 화면·다시 로그인) 그 구역까지 내린다(상태를 받은 뒤에 그려져 라우터의 해시 스크롤이 닿지 못한다)
    if (route.hash !== `#${UtilAdultBoard.ANCHOR}`) return;
    await identityLoaded;
    await nextTick();
    document.getElementById(UtilAdultBoard.ANCHOR)?.scrollIntoView({ block: 'start' });
});
</script>
