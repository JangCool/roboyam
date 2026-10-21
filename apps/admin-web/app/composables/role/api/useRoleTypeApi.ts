import { mockRoleTypeList } from '~/composables/role/api/mock/role-type-mock';
import type { RoleAxis, RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';

/**
 * 역할 유형 API — 서버 주소 문자열(`/api/admin/role-types`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET /api/admin/role-types?axis=MEMBER|OPERATOR` → 200 `RoleTypeView[]`  (`role.read`. 축을 빼면 전부. 운영자 유형은 영역 차례·영역 안 세부 유형 차례, 회원 유형은 등급·회원 유형·게시판 단위·사용자 정의 차례)
 * 유형은 고정이라 추가·수정·삭제 API 가 없다. 역할을 만들 때는 `POST /api/admin/roles` 에 `roleTypeId` 를 보낸다(`useRoleApi`).
 *
 * ⚠ 미정: 이 목록(운영자 세부 유형 24개)을 서버가 주는 것으로 할지 화면 상수(권한 카탈로그)로만 둘지 — 권한 목록이 서버 원천이 되면 함께 정한다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/role-type-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    types: '/api/admin/role-types',
};

export function useRoleTypeApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 역할 유형 목록(역할 수 포함) — 축을 주면 그 축만 */
        load: (axis?: RoleAxis): Promise<RoleTypeView[]> =>
            // MOCK:
            mockRoleTypeList(axis),
        // REAL: client.get<RoleTypeView[]>(PATH.types, { query: { axis } }),
    };
}
