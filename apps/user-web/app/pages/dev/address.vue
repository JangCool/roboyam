<template>
    <!--
        공용 주소 입력 시연 (개발 모드 전용 — `/dev/address`). 운영 빌드·`generate` 에서는 404 다(아래 라우트 미들웨어).
        관리자 설정 › 주소 검색에서 제공처를 바꾼 뒤 「설정 다시 받기」를 누르면 받아 둔 설정을 버리고 다시 묻는다 — 다음 「우편번호 찾기」가 새 설정으로 연다.
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
            <p class="m-0 break-all text-xs text-muted-color" data-testid="dev-address-config">
                <code>{{ addressSearch.config.value ? JSON.stringify(addressSearch.config.value) : '—' }}</code>
            </p>
            <Button type="button" :label="t('address.devReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" class="mt-3" :loading="reloading" @click="reload" />
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
const addressSearch = useAddressSearch();

/** 받아 둔 제공처 설정을 버리고 다시 받는다(관리자 설정을 바꾼 뒤) */
const reloading = ref(false);
async function reload(): Promise<void> {
    reloading.value = true;
    addressSearch.forget();
    try {
        await addressSearch.ensureConfig();
    } finally {
        reloading.value = false;
    }
}
onMounted(() => void addressSearch.ensureConfig());

const value = ref<AddressValue>({ postcode: '', address1: '', address2: '' });
const saved = ref<AddressValue>({ postcode: '06236', address1: '서울특별시 강남구 테헤란로 152', address2: '12층' });

const errors = ref<AddressFieldErrors>({});
function toggleErrors(): void {
    errors.value = errors.value.postcode ? {} : { postcode: '우편번호를 입력해 주세요.', address1: '주소를 입력해 주세요.', address2: '상세 주소는 100자까지예요.' };
}
</script>
