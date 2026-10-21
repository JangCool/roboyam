import UtilTheme, { type ThemeId } from '~/utils/util-theme';

/**
 * 사이트 테마(운영자가 고른 값 하나)를 읽는다 — 셸 레이아웃(`layouts/default.vue`)이 슬롯 컴포넌트를 고르고, 플러그인(`plugins/01.site-theme.client.ts`)이 루트 `data-theme` 에 붙인다.
 * 서버와 말하는 것은 `useThemeApi` 다. 사용자 개인이 바꾸는 값은 없다 → `.claude/guides/frontend/themes.md`.
 *
 * - `undefined` = 아직 안 물어봄. 그동안 `theme` 은 기본 테마다.
 * - 못 받거나 모르는 테마 id 여도 던지지 않고 기본 테마로 둔다(앱을 막지 않는다).
 *
 * @returns `theme` 지금 테마 id(항상 값이 있다) · `ensureLoaded()` 아직 안 받았으면 받는다(앱 시작 때 플러그인이 한 번 부른다).
 */
export function useTheme() {
    const api = useThemeApi();
    const state = useState<ThemeId | undefined>('site-theme', () => undefined);
    const theme = computed<ThemeId>(() => state.value ?? UtilTheme.DEFAULT_ID);

    async function ensureLoaded(): Promise<void> {
        if (state.value !== undefined) return;
        try {
            state.value = UtilTheme.normalize((await api.current()).theme);
        } catch {
            state.value = UtilTheme.DEFAULT_ID;
        }
    }

    return { theme, ensureLoaded };
}
