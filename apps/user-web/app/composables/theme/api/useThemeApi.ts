import { mockThemeCurrent } from '~/composables/theme/api/mock/theme-mock';
import type { ThemeResponse } from '~/composables/theme/api/useThemeApi.types';

/**
 * 사이트 테마 API — 서버 주소 문자열이 있는 **유일한 곳**이다. 상태는 없다(상태는 `useTheme`).
 * 테마는 운영자가 사이트 설정에서 고르는 값 하나다(사용자 개인 보기 설정이 아니다) → `.claude/guides/frontend/themes.md`.
 *
 * ⚠ MOCK: 서버에 이 API 가 없어서 목업(`./mock/theme-mock.ts`)을 부른다. 서버 계약은 아직 없다 — 주소와 응답 모양은 **후보**다(사이트 설정 API 에 합쳐질 수 있다).
 * 서버가 붙으면 `// MOCK:` 줄을 바로 아래 `// REAL:` 줄로 바꾸고, 파일 위의 목업 import 와 `./mock/` 폴더를 지운다.
 */
const PATH = {
    current: '/api/site/theme',
};

export function useThemeApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 지금 사이트의 테마 — 비로그인도 받는다 */
        current: (signal?: AbortSignal): Promise<ThemeResponse> =>
            // MOCK:
            mockThemeCurrent(),
        // REAL: client.get<ThemeResponse>(PATH.current, { signal }),
    };
}
