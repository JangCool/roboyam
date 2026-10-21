package com.community.operator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 「운영자 추가」 — 계정을 바로 만든다. 승인된 상태로 만들어지고 임시 비밀번호는 서버가 이메일로 안내한다(응답에는 싣지 않는다).
 * 이름 1~20자, 이메일 형식은 서비스가 다시 본다. {@code roleIds} 는 비어도 된다(없으면 역할 없이 만든다).
 */
public record OperatorCreateRequest(
        @NotBlank(message = "{operator.name.required}") @Size(max = 20, message = "{operator.name.max}") String operatorName,
        @NotBlank(message = "{user.email.required}") @Size(max = 254, message = "{user.email.max}") String email,
        @Size(max = 50, message = "{operator.roles.max}") List<String> roleIds) {
}
