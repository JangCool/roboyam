import type { RoleAxis, RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';

/**
 * 역할 유형 API — 서버 주소 문자열(`/api/admin/role-types`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `RoleTypeController`): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET /api/admin/role-types?axis=MEMBER|OPERATOR` → 200 `RoleTypeView[]`  (`role.read`. 축을 빼면 전부. 운영자 유형은 영역 차례·영역 안 세부 유형 차례, 회원 유형은 등급·회원 유형·게시판 단위·사용자 정의 차례)
 * 유형은 고정이라 추가·수정·삭제 API 가 없다. 역할을 만들 때는 `POST /api/admin/roles` 에 `roleTypeId` 를 보낸다(`useRoleApi`).
 */
const PATH = {
    types: '/api/admin/role-types',
};

export function useRoleTypeApi() {
    const client = useApiClient();

    return {
        /** 역할 유형 목록(역할 수 포함) — 축을 주면 그 축만 */
        load: (axis?: RoleAxis): Promise<RoleTypeView[]> => client.get<RoleTypeView[]>(PATH.types, { query: { axis } }),
    };
}
