package com.community.profile.dto.response;

import com.community.operator.dto.response.OperatorRoleRef;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 내 정보 — admin-web 의 {@code ProfileResponse} 모양이다(칸 {@code isSuper}·{@code roles} 가 더해졌다). {@code id} 는 문자열(Snowflake)이다.
 *
 * @param email       로그인 아이디(이메일). 본인의 것이라 그대로 준다
 * @param nickname    운영자 이름({@code operators.operator_name})
 * @param role        옛 표시 — 최고 관리자 {@code ADMIN}, 그 밖에 {@code STAFF}({@code auth/me} 와 같다)
 * @param isSuper     최고 관리자(루트 계정)인가
 * @param roles       내가 받은 역할들(만든 순서). 역할이 없어도 내 정보는 볼 수 있다
 * @param status      늘 {@code ACTIVE} — 로그인할 수 없는 계정은 이 API 에 닿지 못한다(호환용 칸)
 * @param createdAt   가입(등록) 시각
 * @param lastLoginAt 최근 로그인 시각. 한 번도 없으면 null
 */
public record ProfileResponse(String id, String email, String nickname, String role, @JsonProperty("isSuper") boolean isSuper, List<OperatorRoleRef> roles,
                              String status, Instant createdAt, Instant lastLoginAt) {
}
