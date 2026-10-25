import { mockAdultStatus, mockAdultUpdate } from '~/composables/adult/api/mock/adult-mock';
import type { AdultStatus, AdultUpdateRequest } from '~/composables/adult/api/useAdultApi.types';

/**
 * 성인 확인 API — 서버 주소 문자열(`/api/me/adult`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useMyAdult`).
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community 4차 계약 B절). 로그인 필요(401) — 이메일 인증·정지와 무관하다.
 *  - `GET /api/me/adult` → `AdultStatus`(저장한 적이 없어도 200 — `birthYear` null·`adult` false)
 *  - `PUT /api/me/adult` `{ birthYear, confirm: true }` → `AdultStatus` · `400 VALIDATION fields.birthYear`(범위·만 14세 미만)·`fields.confirm`. 언제든 다시 고칠 수 있고 같은 세션의 다음 요청부터 반영된다.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/adult-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    me: '/api/me/adult',
};

export function useAdultApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 내 성인 확인 상태 */
        get: (signal?: AbortSignal): Promise<AdultStatus> =>
            // MOCK:
            (void signal, mockAdultStatus()),
        // REAL: client.get<AdultStatus>(PATH.me, { signal }),

        /** 출생연도를 저장한다 — 응답은 저장 뒤의 상태 */
        update: (body: AdultUpdateRequest): Promise<AdultStatus> =>
            // MOCK:
            mockAdultUpdate(body),
        // REAL: client.put<AdultStatus>(PATH.me, body),
    };
}
