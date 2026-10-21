// MOCK: 역할 유형 API(`GET /api/admin/role-types`)의 메모리 목업 — `useRoleTypeApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `role/api/mock/` 와 함께 지운다. 유형 목록은 역할 목업(`role-mock.ts`)과 같은 저장소(`role-store.ts`)를 본다 — 유형의 역할 수가 역할 목업의 역할 목록과 같은 값이어야 해서.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   role-type-error   역할 유형 목록을 읽는 요청이 `500 INTERNAL` 로 실패(역할 관리 화면 전체의 오류 상태와 다시 시도)
//
// ── 흉내 ─
//   유형은 고정이다(추가·수정·삭제 없음): 회원 4종 + 운영자 세부 유형 24개. 축을 주면 그 축만 돌려준다.

import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockRoleCountOfType } from '~/composables/role/api/mock/role-mock';
import { ROLE_TYPES, type StoredRoleType } from '~/composables/role/api/mock/role-store';
import type { RoleAxis, RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';

function view(type: StoredRoleType): RoleTypeView {
    return { ...type, roleCount: mockRoleCountOfType(type.roleTypeId) };
}

/** `GET /api/admin/role-types?axis` — 축을 주면 그 축의 유형만 */
export async function mockRoleTypeList(axis?: RoleAxis): Promise<RoleTypeView[]> {
    await mockDelay();
    if (mockFlags().has('role-type-error')) throw MOCK_ERRORS.internal();
    return ROLE_TYPES.filter((type) => !axis || type.axis === axis).map(view);
}
