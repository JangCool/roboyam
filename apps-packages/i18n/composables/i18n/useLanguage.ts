import UtilI18n, { type Language } from '#layers/i18n/utils/util-i18n';
import UtilPrimeLocale from '#layers/i18n/utils/util-prime-locale';

/**
 * 언어를 화면 전체에 먹인다 (hamonica3 D-89 `applyLanguage`) — **두 군데를 함께 간다**: 우리 문구(vue-i18n 의 `locale`)와 컴포넌트가 제 사전에서 꺼내 쓰는 말(PrimeVue 로캘).
 * 하나만 바꾸면 "달력은 영어인데 단추는 한국어"가 된다.
 *
 * 언어는 주소나 브라우저가 아니라 **표시 값**(`DisplayValues.language`)에서 온다 — 값을 채우는 앱이 표시 값을 바꿀 때 `@roboyam/display-time` 의 플러그인이 `setLanguage` 를 부른다.
 * 이 레이어는 서버를 부르지 않는다.
 *
 * ⚠ **Nuxt 문맥은 부르는 순간(setup)에만 잡힌다.** `useNuxtApp()`·`usePrimeVue()` 는 `await` 뒤나 콜백 안에서 부르면 던지지도 않고 조용히 아무 일도 안 한다(실제로 그렇게 짰다가 언어를 바꿔도 달력이 영어 그대로인 것을 한참 못 찾았다).
 * 그래서 필요한 것은 **이 컴포저블을 부르는 순간 붙잡아 두고**, 돌려주는 `setLanguage` 는 나중에(`await` 뒤·`watch` 콜백)에도 안전하다.
 */

/** 시작할 때 복사해 둔 PrimeVue 기본 사전을 담는 상태 키(`useState`) — 영어로 되돌릴 때 쓴다(`plugins/language.client.ts`) */
export const PRIME_LOCALE_DEFAULTS_KEY = 'prime-locale-defaults';

export function useLanguage() {
    const nuxtApp = useNuxtApp();
    const i18n = nuxtApp.$i18n;
    const prime = usePrimeVue();
    const defaults = useState<Record<string, unknown> | null>(PRIME_LOCALE_DEFAULTS_KEY, () => null);

    /** 지금 언어 */
    const language = computed<Language>(() => UtilI18n.languageOf(i18n.locale.value));

    /**
     * 언어를 바꾼다 — 우리 문구와 PrimeVue 로캘을 함께. 모르는 언어는 기본 언어(`ko`)로 받는다.
     * PrimeVue 로캘은 **통째로 갈지 않고 덮어쓴다**: 우리가 안 적은 항목은 기본값이 남아야 하고, 객체를 바꿔 끼우면 이미 그려진 컴포넌트가 옛 객체를 계속 본다.
     * @param {string} value - 언어 코드(`ko`·`en`).
     * @returns {Language} 실제로 적용한 언어.
     */
    function setLanguage(value: string): Language {
        const next = UtilI18n.languageOf(value);
        // 우리가 가진 언어만 받는다 — 없는 것을 넣으면 키가 그대로 화면에 나온다
        if (i18n.locale.value !== next && i18n.availableLocales.includes(next)) i18n.locale.value = next;
        const locale = prime?.config?.locale;
        if (locale) Object.assign(locale, UtilPrimeLocale.overridesFor(next) ?? defaults.value ?? {});
        return next;
    }

    return { language, setLanguage };
}
