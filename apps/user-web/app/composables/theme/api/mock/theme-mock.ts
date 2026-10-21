// MOCK: 사이트 테마 API(`GET /api/site/theme`, 후보)의 목업 — 서버(`server/`)에 아직 없어서 `useThemeApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `theme/api/mock/` 와 함께 지운다.

import type { ThemeResponse } from '~/composables/theme/api/useThemeApi.types';

/** 운영자가 고른 테마 — 목업은 항상 기본 테마 */
export function mockThemeCurrent(): Promise<ThemeResponse> {
    return Promise.resolve({ theme: 'default' });
}
