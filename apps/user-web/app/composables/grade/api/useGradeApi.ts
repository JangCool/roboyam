import type { GradeRules, MyGrade } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 안내·내 진행도 API — 서버 주소 문자열(`/api/grade-rules`·`/api/me/grade`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-activity-contract.md`. 서버 `PublicGradeController`.
 *
 *  - `GET /api/grade-rules` → `GradeRules` — **비회원도**. `Cache-Control: no-cache`
 *  - `GET /api/me/grade` → `MyGrade` — 로그인 필요(`401 UNAUTHENTICATED`), 정지 중·인증 전에도 읽는다. `no-store`
 *
 * 서버 연결됨(2026-10-24).
 */
const PATH = {
    rules: '/api/grade-rules',
    mine: '/api/me/grade',
};

export function useGradeApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 등급 조건표 — 비회원도 */
        rules: (): Promise<GradeRules> => client.get<GradeRules>(PATH.rules),

        /** 내 등급과 다음 등급까지의 진행도 */
        myGrade: (): Promise<MyGrade> => client.get<MyGrade>(PATH.mine),
    };
}
