import UtilDatePicker from '#layers/display-time/utils/util-date-picker';

/**
 * 표시 값이 바뀌면 **화면 언어(i18n 로캘)와 PrimeVue 로캘을 함께** 바꾼다 — 달력의 요일·달 이름·단추 글자(한국어/영어)와 PrimeVue 가 직접 그리는 날짜 패턴이 표시 값을 따른다.
 *  - 언어(`DisplayValues.language`) → `@roboyam/i18n` 의 `useLanguage().setLanguage` (vue-i18n `locale` + PrimeVue 사전: 한국어는 한국어 사전, 영어는 PrimeVue 기본).
 *  - 날짜 형식 → 로캘의 `dateFormat`(`yy.mm.dd` 등). 언어를 바꾸면 사전이 통째로 덮여 날짜 패턴이 기본으로 돌아가므로 **언어 다음에** 날짜 패턴을 덮는다.
 * 이 레이어의 `AppDatePicker`·`AppDateTimePicker` 는 형식을 prop 으로도 직접 받으므로 이 로캘과 별개로 맞는다 — 이 플러그인은 레이어 컴포넌트를 거치지 않는 PrimeVue 날짜 표시와 언어를 위한 것이다.
 * 각 앱이 PrimeVue 로캘을 따로 덮는 플러그인을 두지 않는다(여기 한 곳이다).
 *
 * ⚠ Nuxt 문맥은 부르는 순간에만 잡힌다 — `useLanguage()`·`usePrimeVue()` 는 플러그인 setup 에서 한 번 부르고, `watch` 콜백(나중)에서는 붙잡아 둔 것만 쓴다.
 */
export default defineNuxtPlugin({
    name: 'display-time:locale',
    setup() {
        const primevue = usePrimeVue();
        const { setLanguage } = useLanguage();
        const values = useDisplayValues();

        watch(
            values,
            (display) => {
                setLanguage(display.language);
                if (primevue?.config?.locale) Object.assign(primevue.config.locale, { dateFormat: UtilDatePicker.dateFormat(display) });
            },
            { immediate: true },
        );
    },
});
