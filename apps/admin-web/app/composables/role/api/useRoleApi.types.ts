/**
 * 역할 API 가 주고받는 모양 — 후보 계약이고 확정 전이다. 칸 이름은 hamonica3 `RoleController`·`useRoleApi` 를 따르되 이 제품의 말로 바꿨다
 * (축 `roleType: SYSTEM|WORKSPACE` → 역할 유형 `roleTypeId` + 축 `roleKind: OPERATOR|MEMBER`, 권한 아이디 `permissionIds` → 권한 코드 `permissionCodes`).
 * 권한 코드의 목록은 화면 상수(`utils/util-permission-catalog.ts`)다 — 서버가 원천이 되면 목록 조회 API 가 하나 더 필요하다.
 *
 * 역할은 **역할 유형**(`useRoleTypeApi`)에 속하고, 유형은 그룹(운영자/회원)을 가진다. 두 그룹의 권한 코드는 서로 다른 체계라 섞이지 않는다.
 * 운영자 역할은 영역 → 세부 유형 두 단계이고 역할 하나가 세부 유형 하나에 속한다(`roleTypeId` = `op_<세부 유형 id>`) — 그 유형의 권한만 줄 수 있다. 회원 역할은 등급·회원 유형·게시판 단위·사용자 정의 4종 유형이다.
 */
import type { RoleGrantMode, RoleKind, RoleScope } from '~/utils/util-permission-catalog';

export type { RoleGrantMode, RoleKind, RoleScope };

export interface RoleView {
    /** `ROLE_` 로 시작하는 대문자·숫자·밑줄. 만든 뒤에는 바꿀 수 없다 */
    roleId: string;
    roleName: string;
    description: string | null;
    /** 속한 역할 유형의 id(운영자는 세부 유형 `op_<id>`, 회원은 4종) — 만든 뒤에는 바꿀 수 없다 */
    roleTypeId: string;
    /** 역할의 축 — 속한 역할 유형의 축과 같은 값이다(`OPERATOR` 운영자 역할 · `MEMBER` 회원 역할). 화면이 유형을 찾지 않고 바로 쓰려고 함께 준다 */
    roleKind: RoleKind;
    /** 범위 — `SITE` 사이트(제품) 전체 · `BOARD` 게시판 하나(게시판 매니저·스텝). 운영자 역할은 늘 `SITE` */
    scope: RoleScope;
    /** 부여 방식 — `AUTO` 자동(등급·가입·사업자 승인) · `APPOINTED` 지정(게시판별) · `MANUAL` 수동(운영자가 부여). 운영자 역할은 늘 `MANUAL` */
    grantMode: RoleGrantMode;
    /** 제품이 넣은 역할. 지울 수 없다(권한은 조정할 수 있다) */
    builtin: boolean;
    /** 이 역할이 가진 권한 코드 */
    permissionCodes: string[];
    /** 이 역할을 받은 운영자 수(운영자 역할) — 0 이 아니면 지울 수 없다. 회원 역할은 0 */
    operatorCount: number;
    /** 이 역할을 가진 회원 수(회원 역할) — 0 이 아니면 지울 수 없다. 운영자 역할은 0 */
    memberCount: number;
}

/**
 * 역할 추가 — 권한은 만든 뒤 따로 붙인다(`RolePermissionsRequest`). 만들기와 권한 주기는 필요한 권한이 달라(`role.manage` · `role.grant`) API 를 나눈다.
 * 등급 유형(파생)에는 만들 수 없다(`ROLE_TYPE_DERIVED`) — 등급 설정에서 만든다.
 */
export interface RoleCreateRequest {
    roleTypeId: string;
    roleId: string;
    roleName: string;
    description: string;
}

/** 이름·설명 수정 — 아이디·유형은 못 바꾼다 */
export interface RoleUpdateRequest {
    roleName: string;
    description: string;
}

/** 권한을 **통째로** 바꾼다 — 보낸 목록이 곧 결과다 */
export interface RolePermissionsRequest {
    permissionCodes: string[];
}
