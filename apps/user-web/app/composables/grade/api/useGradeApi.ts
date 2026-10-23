import { mockGradeRules, mockMyGrade } from '~/composables/grade/api/mock/grade-mock';
import type { GradeRules, MyGrade } from '~/composables/grade/api/useGradeApi.types';

/**
 * 등급 안내·내 진행도 API — 서버 주소 문자열(`/api/grade-rules`·`/api/me/grade`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-activity-contract.md`(모양은 community `docs/api-grade.md`「공개」).
 *
 *  - `GET /api/grade-rules` → `GradeRules` — **비회원도**. 서버가 짧게 기억한다(`Cache-Control: no-cache`)
 *  - `GET /api/me/grade` → `MyGrade` — 로그인 필요(`401 UNAUTHENTICATED`), 정지 중·인증 전에도 읽는다. `no-store`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/grade-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 * (서버에는 비슷한 계산이 관리자 회원 상세 `gradePoint`(`MemberGradePointAssembler`)로 이미 있다 — 공개 API 는 그것을 본인용으로 내면 된다.)
 */
const PATH = {
    rules: '/api/grade-rules',
    mine: '/api/me/grade',
};

export function useGradeApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 목업은 로그인 정보의 등급·정지 상태로 진행도를 만든다
    const { user, restriction } = useAuth();
    void PATH;

    return {
        /** 등급 조건표 — 비회원도 */
        rules: (): Promise<GradeRules> =>
            // MOCK:
            mockGradeRules(),
        // REAL: client.get<GradeRules>(PATH.rules),

        /** 내 등급과 다음 등급까지의 진행도 */
        myGrade: (): Promise<MyGrade> =>
            // MOCK:
            mockMyGrade(user.value, restriction.value ?? null),
        // REAL: client.get<MyGrade>(PATH.mine),
    };
}
