import { attachMessages, requestMissingMessages } from '../runtime/business-messages';

/**
 * 업무별 화면 문구 저장소(`runtime/business-messages.ts`)를 vue-i18n 에 잇는다 — 화면 조각이 들고 온 문구를 `mergeLocaleMessage` 로 합친다.
 * 문구를 어떻게 나누는지는 빌드 모듈 `buildtime/business-messages.ts`.
 *
 * 안전망: 코드에서 못 찾은 키(서버가 내려준 `nameKey` 등)를 만나면 `missing` 처리기가 그 업무 문구를 받는다 — 받는 동안은 키가 잠깐 보이고, 오면 다시 그린다.
 * 업무를 모르거나 이미 받았는데 없는 키는 개발 서버에서만 알린다(예전 `missingWarn` 과 같은 몫 — 처리기를 걸면 vue-i18n 은 스스로 알리지 않는다).
 */
export default defineNuxtPlugin({
    name: 'i18n:business-messages',
    dependsOn: ['i18n:plugin'],
    setup(nuxtApp) {
        const i18n = nuxtApp.$i18n;
        attachMessages((locale, name, messages) => i18n.mergeLocaleMessage(locale, { [name]: messages }));
        i18n.setMissingHandler((locale, key) => {
            if (requestMissingMessages(locale, key)) return;
            if (import.meta.dev) console.warn(`[i18n] 문구가 없다: ${locale} · ${key}`);
        });
    },
});
