package com.community.role.dto.response;

import java.util.List;

/**
 * 역할 한 건 — admin-web 의 {@code RoleView} 모양이다.
 *
 * @param roleKind      역할의 축 — {@code OPERATOR} 운영자 역할 · {@code MEMBER} 회원 역할
 * @param scope         범위 — {@code SITE} 사이트 전체 · {@code BOARD} 게시판 하나
 * @param grantMode     부여 방식 — {@code AUTO} · {@code APPOINTED} · {@code MANUAL}
 * @param builtin       제품이 넣은 역할(지울 수 없다)
 * @param operatorCount 이 역할을 받은 운영자 수(운영자 역할) — 0 이 아니면 지울 수 없다. 회원 역할은 0
 * @param memberCount   이 역할을 가진 회원 수(회원 역할) — 0 이 아니면 지울 수 없다. 운영자 역할은 0
 */
public record RoleView(String roleId, String roleName, String description, String roleTypeId, String roleKind, String scope, String grantMode,
                       boolean builtin, List<String> permissionCodes, long operatorCount, long memberCount) {
}
