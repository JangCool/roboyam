import { mockHomeGadgets } from '~/composables/home/api/mock/home-gadget-mock';
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';

/**
 * 공개 홈 가젯 API — 서버 주소 문자열(`/api/home/gadgets`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다(로그인하면 보는 사람 몫 — 차단·메모·`audience` — 이 입혀진다).
 * 응답 모양·가정은 `useHomeGadgetApi.types.ts`. community `GET /api/home/gadgets`(`docs/api-home-gadgets.md` 4절)와 같은 길이지만 **템플릿은 서버의 사이트 테마로 정해진다**(요청에 싣지 않는다).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/home-gadget-mock.ts` 를 부른다 — 목업은 서버 대신 **지금 화면의 테마**(`useTheme`, 개발 미리보기 `?theme=` 포함)로 배치를 고른다.
 * 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    gadgets: '/api/home/gadgets',
};

export function useHomeGadgetApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업이 테마별 배치를 고르려고 지금 테마를 setup 문맥에서 잡아 둔다
    const { theme } = useTheme();
    void PATH;

    return {
        /** 홈 가젯 전체 — 칸 순서대로, 칸마다 가젯과 그 데이터 */
        gadgets: (signal?: AbortSignal): Promise<HomeGadgetsResponse> =>
            // MOCK:
            (void signal, mockHomeGadgets(theme.value)),
        // REAL: client.get<HomeGadgetsResponse>(PATH.gadgets, { signal }),
    };
}
