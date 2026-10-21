package com.community.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 역할 추가 — 권한은 만든 뒤 따로 붙인다({@code PUT …/{roleId}/permissions}). 역할 아이디는 {@code ROLE_} 로 시작하는 대문자·숫자·밑줄 30자 이하이고 만든 뒤에는 바꿀 수 없다.
 * 이름·설명은 앞뒤 공백을 자른 뒤 저장한다(이름 1~50자, 설명 200자 이하, 설명은 비어도 된다).
 */
public record RoleCreateRequest(
        @NotBlank(message = "{role.type.required}") String roleTypeId,
        @NotBlank(message = "{role.id.required}")
        @Size(max = 30, message = "{role.id.format}")
        @Pattern(regexp = "^ROLE_[A-Z0-9_]+$", message = "{role.id.format}") String roleId,
        @NotBlank(message = "{role.name.required}") @Size(max = 50, message = "{role.name.max}") String roleName,
        @Size(max = 200, message = "{role.description.max}") String description) {
}
