/**
 * PrimeVue 테마(프리셋 + 다크 모드 선택자·CSS 층) — `nuxt.config.ts` 의 `primevue.importTheme` 이 화면 번들로 불러온다(user-web).
 * nuxt.config 의 `primevue.options.theme` 에 두면 프리셋(Aura 전체 토큰, 약 11만 자)이 공개 실행 설정으로 index.html 에 박혀 화면을 열 때마다 받는다.
 * 여기 두면 해시 붙은 JS 조각에 들어가 1년 캐시된다(`SpaResourceConfig` 의 `/_nuxt/**`).
 */
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/** 강조색이 파랑이라 PrimeVue 기본(에메랄드) 대신 파랑을 쓴다. */
const RoboyamPreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '{blue.50}',
            100: '{blue.100}',
            200: '{blue.200}',
            300: '{blue.300}',
            400: '{blue.400}',
            500: '{blue.500}',
            600: '{blue.600}',
            700: '{blue.700}',
            800: '{blue.800}',
            900: '{blue.900}',
            950: '{blue.950}',
        },
        /*
         * 흐린 글자(`text-muted-color` — 날짜·조회·시각·배지 등 11~13px 보조 글자)의 라이트 색.
         * 기본 surface.500 은 옅은 회색 면 위에서 4.5:1 미만이 되는 곳이 있어, 라이트만 한 단계 진한 surface.600 으로 올린다.
         * `light-dark()` 의 뒤 값(다크)은 Aura 기본(surface.400 · hover surface.300)과 같다.
         * 본문 글자(`text-color`)의 다크 색 — Aura 기본은 순백(surface.0)이라 어두운 면 위에서 눈이 부시다(2026-11-05 사용자 결정). 살짝 낮춘 #dcdee2(카드 #202125 위 약 12:1)로 둔다. 라이트는 Aura 기본(surface.700 · hover surface.800).
         */
        text: {
            color: 'light-dark({surface.700}, #dcdee2)',
            hoverColor: 'light-dark({surface.800}, #eceef1)',
            mutedColor: 'light-dark({surface.600}, {surface.400})',
            hoverMutedColor: 'light-dark({surface.700}, {surface.300})',
        },
    },
    components: {
        /*
         * 안내 상자 Message 의 경고(warn) 글자색 — 기본 yellow.600 은 연한 노랑 바탕 위에서 2.84:1(4.5 미만)이다.
         * 라이트만 한 단계 진한 yellow.700 으로 → 바탕 위 4.76:1. 다크는 Aura 기본(yellow.500)과 같다.
         */
        message: {
            warn: {
                color: 'light-dark({yellow.700}, {yellow.500})',
                outlined: { color: 'light-dark({yellow.800}, {yellow.500})' },
                simple: { color: 'light-dark({yellow.800}, {yellow.500})' },
            },
        },
    },
});

export default {
    preset: RoboyamPreset,
    options: {
        darkModeSelector: '[data-color-scheme="dark"]',
        cssLayer: {
            name: 'primevue',
            order: 'theme, base, primevue',
        },
    },
};
