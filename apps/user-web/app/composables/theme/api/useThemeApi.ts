import type { ThemeResponse } from '~/composables/theme/api/useThemeApi.types';

/**
 * 사이트 테마 API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다(상태는 `useTheme`).
 * 테마는 운영자가 사이트 설정에서 고르는 값 하나다(사용자 개인 보기 설정이 아니다) → `.claude/guides/frontend/themes.md`.
 *
 * 규약(`docs/features/site-settings-contract.md` 「테마」): `GET /api/site/theme`(비로그인 가능) → `{ theme, …지금 테마의 메뉴 설정 평평한 칸 }`(칸은 `site-theme-layout-contract.md`). `Cache-Control: public, max-age=60` + `ETag`.
 * 서버의 공개 사이트 정보 사본(20초)에서 읽으므로 관리자가 바꾸면 최대 20초(+ 브라우저 60초) 뒤 따라온다. 모르는 id 는 화면이 기본 테마로 그린다(`useTheme`).
 *
 * 서버 연결됨(2026-10-23).
 */
const PATH = {
    current: '/api/site/theme',
};

export function useThemeApi() {
    const client = useApiClient();

    return {
        /** 지금 사이트의 테마 — 비로그인도 받는다 */
        current: (signal?: AbortSignal): Promise<ThemeResponse> => client.get<ThemeResponse>(PATH.current, { signal }),
    };
}
