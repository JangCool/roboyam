package com.community.role.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이름·설명 수정 — 아이디·유형은 못 바꾼다 */
public record RoleUpdateRequest(
        @NotBlank(message = "{role.name.required}") @Size(max = 50, message = "{role.name.max}") String roleName,
        @Size(max = 200, message = "{role.description.max}") String description) {
}
