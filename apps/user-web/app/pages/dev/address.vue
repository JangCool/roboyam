<template>
    <!--
        공용 주소 입력 시연 (개발 모드 전용 — `/dev/address`). 운영 빌드·`generate` 에서는 404 다(아래 라우트 미들웨어).
        주소를 받는 화면(공구 배송지 등)이 아직 이 부품을 쓰지 않아서, 제공처·실패 경우를 여기서 바꿔 가며 눌러 본다.
        「경우」를 고르면 주소줄의 목업 스위치(`?mock=`)를 바꾸고 받아 둔 설정을 버린다 — 다음 「우편번호 찾기」가 새 설정으로 연다.
    -->
    <div class="mx-auto flex max-w-2xl flex-col gap-8" data-testid="dev-address">
        <header>
            <p class="m-0 text-xs font-semibold text-muted-color">{{ t('address.devBadge') }}</p>
            <h1 class="m-0 mt-0.5 text-lg font-semibold">{{ t('address.devTitle') }}</h1>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('address.devIntro') }}</p>
        </header>

        <section :aria-labelledby="`${uid}-case`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-case`" class="m-0 mb-1 text-base font-semibold">{{ t('address.devCaseTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('address.devCaseIntro') }}</p>
            <Select v-model="scenario" :options="scenarioOptions" option-label="label" option-value="value" :aria-labelledby="`${uid}-case`" class="w-full sm:w-96" data-testid="dev-address-case" />
            <p class="m-0 mt-2 text-xs text-muted-color">
                ?mock= <code>{{ SCENARIOS[scenario] || '—' }}</code>
            </p>
        </section>

        <section :aria-labelledby="`${uid}-input`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-input`" class="m-0 mb-3 text-base font-semibold">{{ t('address.devInputTitle') }}</h2>
            <AddressInput v-model="value" :errors="errors" />
            <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button type="button" :label="t('address.devShowErrors')" size="small" severity="secondary" variant="outlined" @click="toggleErrors" />
                <Button type="button" :label="t('address.devClear')" size="small" severity="secondary" variant="text" @click="value = { postcode: '', address1: '', address2: '' }" />
            </div>
            <p class="m-0 mt-3 break-all text-xs text-muted-color">
                {{ t('address.devValue') }}: <code data-testid="dev-address-json">{{ JSON.stringify(value) }}</code>
            </p>
        </section>

        <section :aria-labelledby="`${uid}-manual`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-manual`" class="m-0 mb-1 text-base font-semibold">{{ t('address.devManualTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('address.devManualIntro') }}</p>
            <AddressInput v-model="saved" manual-first />
        </section>
    </div>
</template>

<script setup lang="ts">
import type { AddressFieldErrors, AddressValue } from '~/components/address/AddressInput.types';

/** 공용 주소 입력 시연 `/dev/address` — 개발 모드에서만 열린다(운영 빌드는 404). 로그인 없이 연다 */
definePageMeta({
    middleware: [
        () => {
            if (!import.meta.dev) return abortNavigation(createError({ statusCode: 404, statusMessage: 'Page Not Found' }));
        },
    ],
});
useSeoMeta({ title: '주소 입력 시연', robots: 'noindex' });

const { t } = useI18n();
const uid = useId();
const route = useRoute();
const router = useRouter();
const addressSearch = useAddressSearch();

/** 경우 → 목업 스위치(`address-mock.ts` 머리말) */
const SCENARIOS = {
    daum: '',
    daumFail: 'daum-fail',
    internal: 'address-internal',
    fallback: 'address-fallback,daum-fail',
    internalEmpty: 'address-internal,address-db-empty',
    internalFail: 'address-internal,address-search-fail',
    configFail: 'address-config-fail',
} as const;
type Scenario = keyof typeof SCENARIOS;

const scenarioOptions = computed(() => (Object.keys(SCENARIOS) as Scenario[]).map((value) => ({ value, label: t(`address.devCase_${value}`) })));

function scenarioOf(mock: unknown): Scenario {
    const found = (Object.keys(SCENARIOS) as Scenario[]).find((key) => SCENARIOS[key] === (typeof mock === 'string' ? mock : ''));
    return found ?? 'daum';
}

const scenario = ref<Scenario>(scenarioOf(route.query.mock));
watch(scenario, async (next) => {
    const mock = SCENARIOS[next];
    await router.replace({ query: mock ? { mock } : {} });
    addressSearch.forget();
});

const value = ref<AddressValue>({ postcode: '', address1: '', address2: '' });
const saved = ref<AddressValue>({ postcode: '06236', address1: '서울특별시 강남구 테헤란로 152', address2: '12층' });

const errors = ref<AddressFieldErrors>({});
function toggleErrors(): void {
    errors.value = errors.value.postcode ? {} : { postcode: '우편번호를 입력해 주세요.', address1: '주소를 입력해 주세요.', address2: '상세 주소는 100자까지예요.' };
}
</script>
