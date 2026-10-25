package com.community.role.entity;

import com.community.permission.entity.PermissionArea;
import com.community.permission.entity.PermissionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 역할 유형 — 역할을 묶는 <b>고정된 분류</b>다(운영자가 추가·삭제하지 않는다). 코드가 원천이고 표가 없다.
 * <ul>
 *   <li>회원 역할 4종: 등급(파생) · 회원 유형(제품 고정) · 게시판 단위(제품 고정) · 사용자 정의(운영자가 추가).</li>
 *   <li>운영자 역할: 권한 카탈로그의 세부 유형 23개({@code op_<세부 유형 id>}, 영역 차례·영역 안 차례). 종합 관리({@code op_general})는 유형 제한 없이 권한을 고른다.</li>
 * </ul>
 * 유형의 식별자·차례는 admin-web 의 {@code role-store.ts} 와 같다.
 */
public final class RoleTypes {

    public static final String GRADE = "memberGrade";
    public static final String KIND = "memberKind";
    public static final String BOARD = "memberBoard";
    public static final String CUSTOM = "memberCustom";

    /** 운영자 역할 유형의 식별자 접두어 */
    public static final String OPERATOR_PREFIX = "op_";

    /**
     * @param permissionType 운영자 역할 유형이 가리키는 권한 묶음 — 회원 역할 유형은 null
     * @param derived        등급 설정에서 파생되는 유형(등급만 true) — 역할을 추가·삭제·이름 편집할 수 없다
     * @param creatable      운영자가 이 유형에 역할을 새로 만들 수 있나
     */
    public record RoleTypeDef(String id, RoleAxis axis, PermissionType permissionType, boolean derived, boolean creatable) {

        /** 운영자 역할 유형이 속한 영역 id — 회원 역할 유형은 null */
        public String areaId() {
            return permissionType == null ? null : permissionType.area().id();
        }

        /** 종합 관리 유형인가 — 최고 관리자만 만들고 고친다 */
        public boolean isGeneral() {
            return permissionType != null && permissionType.isGeneral();
        }
    }

    private static final List<RoleTypeDef> ALL = build();

    private RoleTypes() {
    }

    private static List<RoleTypeDef> build() {
        List<RoleTypeDef> types = new ArrayList<>();
        types.add(new RoleTypeDef(GRADE, RoleAxis.MEMBER, null, true, false));
        types.add(new RoleTypeDef(KIND, RoleAxis.MEMBER, null, false, false));
        types.add(new RoleTypeDef(BOARD, RoleAxis.MEMBER, null, false, false));
        types.add(new RoleTypeDef(CUSTOM, RoleAxis.MEMBER, null, false, true));
        for (PermissionArea area : PermissionArea.values()) {
            Arrays.stream(PermissionType.values()).filter(type -> type.area() == area)
                    .forEach(type -> types.add(new RoleTypeDef(operatorTypeId(type), RoleAxis.OPERATOR, type, false, true)));
        }
        return List.copyOf(types);
    }

    public static String operatorTypeId(PermissionType type) {
        return OPERATOR_PREFIX + type.id();
    }

    /** 모든 유형 — 회원 4종 다음에 운영자 세부 유형(영역 차례) */
    public static List<RoleTypeDef> all() {
        return ALL;
    }

    public static Optional<RoleTypeDef> find(String roleTypeId) {
        return ALL.stream().filter(type -> type.id().equals(roleTypeId)).findFirst();
    }
}
