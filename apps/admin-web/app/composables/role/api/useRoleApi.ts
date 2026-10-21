import type { RoleCreateRequest, RolePermissionsRequest, RoleUpdateRequest, RoleView } from '~/composables/role/api/useRoleApi.types';

/**
 * 역할 API — 서버 주소 문자열(`/api/admin/roles…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `RoleController`, `docs/features/roles-contract.md`): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드. 역할은 **역할 유형**(`useRoleTypeApi`)에 속한다 — 운영자 역할은 세부 유형 하나(`op_<id>`).
 *   - `GET    /api/admin/roles` → 200 `RoleView[]`  (`role.read`. 모든 유형의 역할이 한 목록에 `roleTypeId` 로 갈린다)
 *   - `POST   /api/admin/roles` `RoleCreateRequest`(`roleTypeId`·`roleId`·`roleName`·`description`) → 201 `RoleView`  (`role.manage`. 종합 관리 유형은 최고 관리자만. 등급 유형(파생)·회원 유형·게시판 단위에는 만들 수 없다 — 운영자 세부 유형과 사용자 정의에만)
 *   - `PUT    /api/admin/roles/{roleId}` `RoleUpdateRequest` → 200 `RoleView`  (`role.manage`. 종합 관리 역할은 최고 관리자만)
 *   - `PUT    /api/admin/roles/{roleId}/permissions` `RolePermissionsRequest` → 200 `RoleView`  (운영자 역할은 `role.grant` + 요청한 운영자가 가진 권한 안에서만, 회원 역할은 `role.manage` 이고 권한 상승 규칙이 없다)
 *   - `DELETE /api/admin/roles/{roleId}` → 204  (`role.manage`)
 * 오류 코드: `ROLE_NOT_FOUND` 404 · `ROLE_DUPLICATED` 409 · `ROLE_BUILTIN` 409(내장 역할 삭제) · `ROLE_DERIVED` 409(등급 역할 이름·설명) · `ROLE_IN_USE` 409(쓰는 운영자·회원이 있음 — 건수는 서버 메시지에 들어 있다) ·
 *   `ROLE_TYPE_NOT_FOUND` 404(없는 역할 유형) · `ROLE_TYPE_DERIVED` 409(등급 유형에 역할 만들기) · `ROLE_TYPE_FIXED` 409(제품이 정한 유형에 역할 만들기) ·
 *   `ROLE_TYPE_MISMATCH` 400(그 유형의 권한 후보에 없는 권한) · `PERMISSION_UNKNOWN` 400 ·
 *   `PERMISSION_ESCALATION` 403(내 권한 밖) · `ROLE_GENERAL_SUPER_ONLY` 403(종합 관리는 최고 관리자만) · `PERMISSION_DENIED` 403 · `VALIDATION` 400(`fields`).
 */
const PATH = {
    roles: '/api/admin/roles',
    role: (roleId: string) => `${PATH.roles}/${encodeURIComponent(roleId)}`,
    permissions: (roleId: string) => `${PATH.role(roleId)}/permissions`,
};

export function useRoleApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 역할 목록(가진 권한·쓰는 운영자 수 포함) */
        load: (): Promise<RoleView[]> => client.get<RoleView[]>(PATH.roles),

        /** 역할을 만든다 — 권한은 `setPermissions` 로 따로 붙인다 */
        create: (body: RoleCreateRequest): Promise<RoleView> => client.post<RoleView>(PATH.roles, body),

        /** 이름·설명을 고친다 */
        update: (roleId: string, body: RoleUpdateRequest): Promise<RoleView> => client.put<RoleView>(PATH.role(roleId), body),

        /** 권한을 통째로 바꾼다 */
        setPermissions: (roleId: string, permissionCodes: string[]): Promise<RoleView> =>
            client.put<RoleView>(PATH.permissions(roleId), { permissionCodes } satisfies RolePermissionsRequest),

        /** 역할을 지운다 — 내장 역할·쓰는 운영자가 있는 역할은 서버가 거절한다 */
        remove: (roleId: string): Promise<void> => client.del<void>(PATH.role(roleId)),
    };
}
