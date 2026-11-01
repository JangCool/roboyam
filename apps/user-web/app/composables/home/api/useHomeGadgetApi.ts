import type { AuthUser } from '~/composables/auth/api/useAuthApi.types';
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import { AUTH_USER_STATE_KEY } from '~/composables/common/useApiClient';
import { mockWithHotDealsGadget } from '~/composables/home/api/mock/home-hotdeal-mock';

/**
 * 공개 홈 가젯 API — 서버 주소 문자열(`/api/home/gadgets`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다(로그인하면 글 줄의 차단·메모, 소모임 줄의 내 상태가 입혀진다).
 * 응답 모양·가정은 `useHomeGadgetApi.types.ts`. 계약 `docs/features/site-theme-layout-contract.md` 「API」(서버 `HomeController`, `Cache-Control: no-store`).
 * **템플릿은 서버의 사이트 테마로 정해진다**(공개 화면은 요청에 싣지 않는다 — 관리자 미리보기만 보는 테마를 싣는다) — 배치는 그 테마의 저장 배치, 없으면 테마 기본 배치다.
 *
 * 서버 연결됨(2026-10-29). ⚠ MOCK: 회원 핫딜 1차 — 서버에 `HOT_DEALS` 종류가 없어 공개 홈 응답 끝에 목업 핫딜 가젯 하나를 더한다(`./mock/home-hotdeal-mock.ts`, 관리자 미리보기는 그대로).
 */
const PATH = {
    gadgets: '/api/home/gadgets',
};

export function useHomeGadgetApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업 핫딜 줄의 「보는 사람」 — 지금 로그인 상태를 읽기만 한다
    const authUser = useState<AuthUser | null>(AUTH_USER_STATE_KEY, () => null);

    return {
        /** 홈 가젯 전체 — 배치와 가젯마다 그 데이터. `theme` 은 관리자 미리보기만 싣는다(그 테마의 구성 — 저장한 것, 없으면 기본 배치) */
        gadgets: async (signal?: AbortSignal, theme?: string): Promise<HomeGadgetsResponse> => {
            const res = await client.get<HomeGadgetsResponse>(PATH.gadgets, { query: theme ? { theme } : undefined, signal });
            // MOCK:
            return theme ? res : mockWithHotDealsGadget(res, { loggedIn: Boolean(authUser.value), name: authUser.value?.nickname ?? null });
            // REAL: return res;
        },
    };
}
