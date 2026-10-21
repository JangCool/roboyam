import { PRIME_LOCALE_DEFAULTS_KEY } from '#layers/i18n/composables/i18n/useLanguage';

/**
 * PrimeVue 기본 사전(영어)을 **다른 어떤 플러그인이 덮기 전에** 복사해 둔다 — 영어로 바꿀 때 `useLanguage().setLanguage('en')` 가 이것을 되돌려 덮는다.
 * 이 레이어의 플러그인은 `extends` 한 앱·레이어의 플러그인보다 먼저 돌므로(레이어 순서) `@roboyam/display-time` 의 표시 값 반영보다 앞선다.
 */
export default defineNuxtPlugin({
    name: 'i18n:prime-locale-defaults',
    setup() {
        const prime = usePrimeVue();
        const defaults = useState<Record<string, unknown> | null>(PRIME_LOCALE_DEFAULTS_KEY, () => null);
        if (defaults.value === null && prime?.config?.locale) defaults.value = JSON.parse(JSON.stringify(prime.config.locale)) as Record<string, unknown>;
    },
});
