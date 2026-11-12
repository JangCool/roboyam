/**
 * PrimeVue 테마(프리셋 + 다크 모드 선택자·CSS 층) — `nuxt.config.ts` 의 `primevue.importTheme` 이 화면 번들로 불러온다(admin-web).
 * nuxt.config 의 `primevue.options.theme` 에 두면 프리셋(Aura 전체 토큰, 약 11만 자)이 공개 실행 설정으로 index.html 에 박혀 화면을 열 때마다 받는다.
 * 여기 두면 해시 붙은 JS 조각에 들어가 1년 캐시된다(`SpaResourceConfig` 의 `/_nuxt/**`).
 */
import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/** 강조색이 파랑이라 PrimeVue 기본(에메랄드) 대신 파랑을 쓴다 — user-web 과 같다. */
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
        /* 보더 선 색 — 하모니카3 기준(`main.css` 의 `--line` · `--line-strong` 와 같다): 면 경계 라이트 surface-200 · 다크 surface-800, 입력 칸 경계 라이트 300 · 다크 700 */
        content: { borderColor: 'light-dark({surface.200}, {surface.800})' },
        /* 입력 칸 — 경계색은 위와 같고, 글자 크기는 하모니카3 기준(small 을 13px 로 한 단계만 줄여 앱 기본으로 쓴다 — `nuxt.config.ts` 의 `primevue.options.pt` 가 입력 칸 전부에 small 을 기본으로 건다) */
        formField: {
            borderColor: 'light-dark({surface.300}, {surface.700})',
            sm: { fontSize: '0.8125rem', paddingX: '0.625rem', paddingY: '0.4rem' },
        },
        /* 흐린 글자(`text-muted-color`)의 라이트 색을 한 단계 진하게 — 근거는 user-web 의 같은 설정 */
        text: {
            mutedColor: 'light-dark({surface.600}, {surface.400})',
            hoverMutedColor: 'light-dark({surface.700}, {surface.300})',
        },
    },
    components: {
        /* 안내 상자 Message 의 경고(warn) 글자색 — 연한 노랑 바탕에서 4.5:1 을 넘기게 한 단계 진하게(user-web 과 같다) */
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
        darkModeSelector: '[data-theme="dark"]',
        cssLayer: {
            name: 'primevue',
            order: 'theme, base, primevue',
        },
    },
};
