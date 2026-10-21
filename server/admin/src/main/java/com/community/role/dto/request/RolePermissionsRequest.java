package com.community.role.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 역할의 권한을 <b>통째로</b> 바꾼다 — 보낸 목록이 곧 결과다(빈 목록이면 모두 뺀다) */
public record RolePermissionsRequest(
        @NotNull(message = "{role.permissions.required}") @Size(max = 200, message = "{role.permissions.max}") List<String> permissionCodes) {
}
