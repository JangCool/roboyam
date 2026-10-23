import { mockUpdateViewSettings, mockViewSettings } from '~/composables/view/api/mock/view-mock';
import type { ViewSettings } from '~/composables/view/api/useViewSettingsApi.types';

/**
 * 보기 설정 API — 서버 주소 문자열(`/api/me/settings`)이 있는 **유일한 곳**이다. 상태는 없다(화면 상태는 `useViewSettings`).
 * 계약 후보: `docs/features/member-social-contract.md`(경로·규칙은 community `docs/api-profile.md` 「설정(보기)」, 칸은 셋). 로그인 필요(401) — 비회원은 이 브라우저에만 둔다.
 *  - `GET` → `ViewSettings`(저장한 적이 없어도 기본값 200) · `PUT` 세 칸 모두 → 저장된 `ViewSettings` · `400 VALIDATION fields.<칸>`
 * 서버는 값만 저장하고 **적용은 화면이 한다**(야간 모드·글자 크기는 `<html>` 에, 댓글 정렬은 댓글 목록 요청의 `sort`).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/view-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    settings: '/api/me/settings',
};

export function useViewSettingsApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 내 보기 설정 */
        get: (signal?: AbortSignal): Promise<ViewSettings> =>
            // MOCK:
            (void signal, mockViewSettings()),
        // REAL: client.get<ViewSettings>(PATH.settings, { signal }),

        /** 보기 설정 바꾸기 — 세 칸 모두 보낸다 */
        update: (settings: ViewSettings): Promise<ViewSettings> =>
            // MOCK:
            mockUpdateViewSettings(settings),
        // REAL: client.put<ViewSettings>(PATH.settings, settings),
    };
}
