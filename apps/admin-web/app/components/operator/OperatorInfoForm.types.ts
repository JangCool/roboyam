/** 역할 고르개의 한 항목 — `disabled` 는 내 권한 밖(권한 상승 방지)이라 못 고르는 역할 */
export interface OperatorRoleOption {
    roleId: string;
    roleName: string;
    disabled: boolean;
    /** 속한 역할 유형(탭)의 이름 — 고르개가 이 이름으로 묶어 보인다 */
    group: string;
}
