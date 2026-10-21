// MOCK: 역할 유형 저장소 — 역할 목업(`role-mock.ts`)과 역할 유형 목업(`role-type-mock.ts`)이 **같은 목록**을 본다(역할의 유형 확인·유형의 역할 수가 같은 값을 봐야 해서).
// 서버가 붙으면 `role/api/mock/` 와 함께 지운다. 유형은 고정이라 바뀌지 않는다.
//
// 시드(전부 고정 유형):
//   회원 축 — 등급(`memberGrade`, 등급 설정에서 파생) · 회원 유형(`memberKind`) · 게시판 단위(`memberBoard`) · 사용자 정의(`memberCustom`, 운영자가 역할을 추가)
//   운영자 축 — 권한 카탈로그의 세부 유형 24개(`op_<세부 유형 id>`), 영역 차례·영역 안 차례. 영역은 `area`, 종합 관리(`op_general`)는 유형 제한 없이 모든 권한을 고른다(최고 관리자 전용).
//   표시 이름은 서버가 주지 않는다 — 화면이 번역 키로 보인다.

import type { RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';
import UtilRole from '~/utils/util-role';

/** 저장된 역할 유형 — 역할 수(`roleCount`)는 응답을 만들 때 센다 */
export type StoredRoleType = Omit<RoleTypeView, 'roleCount'>;

/** 고정 유형의 목록 — 회원 4종 다음에 운영자 세부 유형(영역 차례) */
export const ROLE_TYPES: StoredRoleType[] = [
    { roleTypeId: UtilRole.TYPE_GRADE, description: '활동 기준에 따라 자동으로 받는 회원 등급입니다. 등급 설정에서 만들어집니다.', axis: 'MEMBER', area: null, derived: true, creatable: false },
    { roleTypeId: UtilRole.TYPE_KIND, description: '가입과 사업자 승인으로 자동으로 받는 회원 유형입니다. 일반 회원과 사업자 회원이 있습니다.', axis: 'MEMBER', area: null, derived: false, creatable: false },
    { roleTypeId: UtilRole.TYPE_BOARD, description: '게시판 하나를 맡도록 지정받는 역할입니다. 게시판 매니저와 게시판 스텝이 있습니다.', axis: 'MEMBER', area: null, derived: false, creatable: false },
    { roleTypeId: UtilRole.TYPE_CUSTOM, description: '운영자가 직접 만들어 회원에게 수동으로 주는 역할입니다. 예: 베타 테스터, 우수 회원.', axis: 'MEMBER', area: null, derived: false, creatable: true },
    ...UtilPermissionCatalog.AREAS.flatMap((area) =>
        UtilPermissionCatalog.typesOfArea(area.id).map((type): StoredRoleType => ({ roleTypeId: UtilRole.operatorTypeIdOf(type.id), description: null, axis: 'OPERATOR', area: area.id, derived: false, creatable: true })),
    ),
];

export function findStoredRoleType(roleTypeId: string): StoredRoleType | undefined {
    return ROLE_TYPES.find((type) => type.roleTypeId === roleTypeId);
}
