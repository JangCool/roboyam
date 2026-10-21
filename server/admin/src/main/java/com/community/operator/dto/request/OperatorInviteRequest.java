package com.community.operator.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 「초대」 — 초대 링크 메일을 보낸다. 받은 사람이 링크에서 이름·비밀번호를 정한다(그 API 는 후속 단계). 한 번에 한 주소다 */
public record OperatorInviteRequest(
        @NotBlank(message = "{user.email.required}") @Size(max = 254, message = "{user.email.max}") String email,
        @Size(max = 50, message = "{operator.roles.max}") List<String> roleIds) {
}
