/** 역할 추가·수정 다이얼로그가 내보내는 값 */
export interface RoleEditSubmit {
    /** 추가면 `ROLE_` 를 붙인 새 아이디, 수정이면 고르던 역할의 아이디 */
    roleId: string;
    /** 역할이 속할 역할 유형 — 추가면 고른 세부 유형(운영자) 또는 사용자 정의(회원), 수정이면 원래 유형(바꿀 수 없다) */
    roleTypeId: string;
    roleName: string;
    description: string;
    /** 고른 권한 코드 */
    permissionCodes: string[];
    /** 권한을 바꿔야 하나(권한을 줄 권한이 있고 선택이 달라졌을 때만 true) — 만들기와 이름·설명 수정, 권한 주기는 API 가 나뉘어 있다 */
    permissionsChanged: boolean;
}
