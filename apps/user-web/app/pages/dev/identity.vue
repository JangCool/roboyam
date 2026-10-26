<template>
    <!--
        본인인증·기능 제한 시연 (개발 모드 전용 — `/dev/identity`). 운영 빌드·`generate` 에서는 404 다(아래 라우트 미들웨어).
        포인트 충전·공동구매 참여 화면이 아직 없어서, 결제 직전 확인(`useIdentityGate`)과 기능 제한 안내(`FeatureRestrictionNotice`)를 여기서 눌러 본다.
        1. 내 본인인증 상태 — 실제 서버(`GET /api/identity-verifications/me`)에서 받은 값과 [다시 받기]. 인증을 되돌리는 API 가 없어 상태 바꾸기는 없고 안내만 둔다.
           인증은 2번 단추(결제 직전 확인)나 설정 › 본인인증에서 한다 — local 서버는 가짜 구현이라 「가짜 본인인증(개발용)」 대화상자로 끝까지 된다.
        2. [충전 시작]·[공구 참여] — 결제 직전 확인을 거친 결과를 보인다.
        3. 기능 제한 — 아직 이 오류를 주는 공개 사이트 API 가 없어, 종류·끝 시각을 골라 서버와 같은 오류 모양(403 `MEMBER_RESTRICTED`, 본문 최상위 `restriction`)을 화면에서 만들어 안내를 미리 본다.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-8" data-testid="dev-identity">
        <header>
            <p class="m-0 text-xs font-semibold text-muted-color">{{ t('identity.devBadge') }}</p>
            <h1 class="m-0 mt-0.5 text-lg font-semibold">{{ t('identity.devTitle') }}</h1>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('identity.devIntro') }}</p>
        </header>

        <!-- 1. 내 본인인증 상태(실제 서버) -->
        <section :aria-labelledby="`${uid}-state`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-state`" class="m-0 mb-1 text-base font-semibold">{{ t('identity.devStateTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('identity.devStateIntro') }}</p>
            <div class="flex flex-wrap items-center gap-3">
                <Button type="button" :label="t('identity.devReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loadStatus === 'loading'" data-testid="dev-identity-reload" @click="identity.load()" />
                <p class="m-0 min-w-0 break-all text-xs text-muted-color">
                    {{ t('identity.devCurrent') }}:
                    <code data-testid="dev-identity-json">{{ status ? JSON.stringify(status) : loadStatus === 'error' ? loadError : loadStatus }}</code>
                </p>
            </div>
            <p class="m-0 mt-3 text-xs text-muted-color">{{ t('identity.devStateNoReset') }}</p>
            <p class="m-0 mt-1 text-xs text-muted-color">{{ t('identity.devPersonKeys') }}</p>
        </section>

        <!-- 2. 결제 직전 확인 -->
        <section :aria-labelledby="`${uid}-gate`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-gate`" class="m-0 mb-1 text-base font-semibold">{{ t('identity.devGateTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('identity.devGateIntro') }}</p>
            <div class="flex flex-wrap gap-2">
                <Button type="button" :label="t('identity.devCharge')" icon="pi pi-wallet" :loading="gateBusy === 'CHARGE'" :disabled="gateBusy !== null" data-testid="dev-identity-charge" @click="tryGate('CHARGE')" />
                <Button type="button" :label="t('identity.devGroupBuy')" icon="pi pi-shopping-bag" severity="secondary" :loading="gateBusy === 'GROUP_BUY'" :disabled="gateBusy !== null" data-testid="dev-identity-groupbuy" @click="tryGate('GROUP_BUY')" />
            </div>
            <p v-if="gateResult" class="m-0 mt-3 flex items-center gap-1.5 text-sm" aria-live="polite" data-testid="dev-identity-gate-result">
                <i :class="gateResult.ok ? 'pi pi-check-circle text-green-700 dark:text-green-400' : 'pi pi-times-circle text-muted-color'" aria-hidden="true" />
                {{ gateResult.ok ? t('identity.devGateOk', { purpose: purposeLabel(gateResult.purpose) }) : t('identity.devGateNo', { purpose: purposeLabel(gateResult.purpose) }) }}
            </p>
        </section>

        <!-- 3. 기능 제한 안내 -->
        <section :aria-labelledby="`${uid}-restriction`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-restriction`" class="m-0 mb-1 text-base font-semibold">{{ t('identity.devRestrictionTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('identity.devRestrictionIntro') }}</p>
            <div class="flex flex-col gap-3">
                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-rtype`" class="text-sm font-medium">{{ t('identity.devRestrictionType') }}</span>
                    <SelectButton v-model="restrictionType" :options="typeOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${uid}-rtype`" />
                </div>
                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-runtil`" class="text-sm font-medium">{{ t('identity.devRestrictionUntil') }}</span>
                    <SelectButton v-model="restrictionUntil" :options="untilOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="`${uid}-runtil`" />
                </div>
                <div>
                    <Button type="button" :label="t('identity.devRestrictionTry')" icon="pi pi-ban" severity="secondary" variant="outlined" data-testid="dev-identity-restriction-try" @click="tryRestricted" />
                </div>
                <FeatureRestrictionNotice v-if="restriction" :restriction="restriction" />
                <Message v-else-if="restrictionError" severity="error" size="small" :closable="false">{{ restrictionError }}</Message>
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { IdentityPurpose } from '~/composables/identity/useIdentityGate.types';
import type { FeatureRestriction, FeatureRestrictionType } from '~/utils/util-feature-restriction';
import UtilFeatureRestriction from '~/utils/util-feature-restriction';

/**
 * 본인인증·기능 제한 시연 `/dev/identity` — 개발 모드에서만 열린다(운영 빌드는 404). 충전·공구 참여 화면이 생기면 같은 부품(`useIdentityGate`·`FeatureRestrictionNotice`)을 거기서 쓴다.
 * 로그인해야 본인인증 상태를 받는다(`auth` 미들웨어).
 */
definePageMeta({
    middleware: [
        () => {
            if (!import.meta.dev) return abortNavigation(createError({ statusCode: 404, statusMessage: 'Page Not Found' }));
        },
        'auth',
    ],
});
useSeoMeta({ title: '본인인증 시연', robots: 'noindex' });

const { t } = useI18n();
const uid = useId();
const identity = useIdentity();
const { status, loadStatus, loadError } = identity;
const gate = useIdentityGate();

/* ── 2. 결제 직전 확인 ── */

const gateBusy = ref<IdentityPurpose | null>(null);
const gateResult = ref<{ purpose: IdentityPurpose; ok: boolean } | null>(null);

function purposeLabel(purpose: IdentityPurpose): string {
    return purpose === 'CHARGE' ? t('identity.devCharge') : t('identity.devGroupBuy');
}

async function tryGate(purpose: IdentityPurpose): Promise<void> {
    gateBusy.value = purpose;
    gateResult.value = null;
    const ok = await gate.ensure({ purpose, requireAdult: true });
    gateBusy.value = null;
    gateResult.value = { purpose, ok };
}

/* ── 3. 기능 제한 ── */

const restrictionType = ref<FeatureRestrictionType>('GROUP_BUY_BAN');
const restrictionUntil = ref<'HOURS' | 'NEXT_YEAR' | 'FOREVER'>('HOURS');
const typeOptions = computed(() => UtilFeatureRestriction.TYPES.map((value) => ({ value, label: t(`restriction.type_${value}`) })));
const untilOptions = computed(() => [
    { value: 'HOURS' as const, label: t('identity.devUntilHours') },
    { value: 'NEXT_YEAR' as const, label: t('identity.devUntilNextYear') },
    { value: 'FOREVER' as const, label: t('identity.devUntilForever') },
]);
const restriction = ref<FeatureRestriction | null>(null);
const restrictionError = ref('');

/**
 * 서버가 막았을 때(403 `MEMBER_RESTRICTED`)의 오류를 만들어 미리 본다 — 이 오류를 주는 공개 사이트 API 가 아직 없다(충전·공구 참여 화면이 생기면 거기서 실제로 받는다).
 * 서버 `MemberRestrictedException` 은 `restriction{type, until|null, reason}` 을 오류 본문 최상위 칸으로 주고 `useApiClient` 가 `ApiError.extras` 로 옮긴다 — 여기서는 그 결과 모양을 바로 만든다.
 * 화면은 실제와 같은 길(`UtilFeatureRestriction.fromError`)로 안내를 꺼낸다.
 */
function tryRestricted(): void {
    const HOUR = 60 * 60 * 1000;
    const until = restrictionUntil.value === 'FOREVER' ? null : new Date(Date.now() + (restrictionUntil.value === 'HOURS' ? 26 : 24 * 400) * HOUR).toISOString();
    const error = new ApiError({
        status: 403,
        code: 'MEMBER_RESTRICTED',
        message: t('identity.devRestrictionTitle'),
        extras: { restriction: { type: restrictionType.value, until, reason: t('identity.devRestrictionSampleReason') } },
    });
    restriction.value = UtilFeatureRestriction.fromError(error);
    restrictionError.value = restriction.value ? '' : error.message;
}

void identity.load();
</script>
