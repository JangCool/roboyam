import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 번역 함수 `t` 를 컴포넌트가 아닌 곳(컴포저블·플러그인·미들웨어)에서 얻는다 — `useI18n()` 은 컴포넌트 setup 안에서만 되지만 이것은 Nuxt 문맥(setup·플러그인·미들웨어)이면 된다.
 * 전역 composer(`$i18n.t`)를 **부르는 순간 붙잡아 두므로** 돌려준 함수는 나중에(`await` 뒤·콜백)에도 안전하고, 호출할 때 지금 언어를 읽으므로 `computed`·템플릿 안에서 부르면 언어가 바뀔 때 다시 계산된다.
 *
 * ```ts
 * // useApiClient — 서버 문구가 없을 때 코드로 고른 문구를 지금 언어로
 * const t = useTranslate();
 * const message = UtilAuthMessages.fallback(t, status, code);
 * ```
 * 컴포넌트 안에서는 `const { t } = useI18n()` 를 그대로 쓴다. 메시지 유틸(`utils/util-*-messages.ts`)은 `t` 를 첫 인자로 받는다 — `.claude/guides/frontend/i18n.md`.
 */
export function useTranslate(): Translate {
    const { $i18n } = useNuxtApp();
    return (key, params) => (params ? $i18n.t(key, params) : $i18n.t(key));
}
