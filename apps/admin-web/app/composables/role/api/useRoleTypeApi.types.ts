/**
 * 역할 유형 API 가 주고받는 모양 — 후보 계약이고 확정 전이다. 역할 유형은 역할을 묶는 **고정된 분류**다(운영자가 추가·삭제하지 않는다 — 정책 `operator-permissions.md` "역할 유형"·"회원 역할").
 *
 * 두 그룹(`axis`):
 *  · 운영자 역할(`OPERATOR`): 영역(대분류, `area`) → 세부 유형 두 단계. 세부 유형 24개가 곧 역할 유형이다(`roleTypeId` = `op_<세부 유형 id>`, 예 `op_gbOrder`).
 *    역할 하나는 세부 유형 하나에 속하고 그 유형의 권한만 후보로 고른다. 종합 관리(`op_general`)는 유형 제한 없이 고르며 최고 관리자만 만들고 고친다.
 *  · 회원 역할(`MEMBER`): 등급(`memberGrade`, 등급 설정에서 파생) · 회원 유형(`memberKind`) · 게시판 단위(`memberBoard`) · 사용자 정의(`memberCustom`) 4종.
 * 표시 이름은 화면이 번역 키로 보인다(운영자 쪽은 `permission.type.<id>`, 회원 쪽은 `role.typeBuiltin.<id>`) — 서버는 이름을 주지 않는다.
 */

/** 역할의 그룹 — 회원 역할(공개 사이트 회원의 능력) · 운영자 역할(관리자 앱 권한) */
export type RoleAxis = 'MEMBER' | 'OPERATOR';

export interface RoleTypeView {
    /** 유형 식별자 — 고정 문자열(회원 `memberGrade`·`memberKind`·`memberBoard`·`memberCustom`, 운영자 `op_<세부 유형 id>`) */
    roleTypeId: string;
    /** 유형 설명(없을 수 있다) */
    description: string | null;
    axis: RoleAxis;
    /** 운영자 역할 유형이 속한 영역 id(`util-permission-catalog.ts` 의 `AREAS`) — 회원 역할 유형은 null */
    area: string | null;
    /** 등급 설정에서 파생되는 유형(등급만 true) — 역할을 추가·삭제·이름 편집할 수 없고 권한만 고친다 */
    derived: boolean;
    /** 운영자가 이 유형에 역할을 새로 만들 수 있나 — 운영자 세부 유형과 사용자 정의는 true, 등급·회원 유형·게시판 단위는 false(제품이 정한 역할만 있다) */
    creatable: boolean;
    /** 이 유형에 든 역할 수 */
    roleCount: number;
}
