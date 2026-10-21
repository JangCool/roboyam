package com.community.operator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 이름·역할·계정 기간을 고친다. 이메일은 로그인 아이디라 못 바꾸고 비밀번호는 여기서 못 정한다.
 * {@code roleIds} 가 없으면(null) 역할은 그대로 둔다 — 역할을 바꾸려면 {@code role.grant} 가 필요하다. 계정 기간은 둘 다 비면 제한 없음이고 시작이 끝보다 뒤면 400.
 */
public record OperatorUpdateRequest(
        @NotBlank(message = "{operator.name.required}") String operatorName,
        @Size(max = 50, message = "{operator.roles.max}") List<String> roleIds,
        Instant accountStartAt,
        Instant accountEndAt) {
}
