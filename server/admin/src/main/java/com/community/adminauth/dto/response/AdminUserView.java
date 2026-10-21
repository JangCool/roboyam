package com.community.adminauth.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 로그인한 운영자 — admin-web 의 {@code AdminUser} 모양이다. {@code id} 는 <b>문자열</b>(Snowflake)이다.
 *
 * @param nickname    화면에 보이는 이름(운영자 이름)
 * @param role        옛 표시 — 최고 관리자 {@code ADMIN}, 그 밖에 {@code STAFF}. 화면은 {@code isSuper} 와 {@code permissions} 로 판정한다
 * @param isSuper     최고 관리자(루트 계정)인가 — 모든 권한을 갖는다
 * @param permissions 받은 역할들의 권한 코드 합집합. 최고 관리자는 모든 운영자 권한 코드
 */
public record AdminUserView(String id, String nickname, String role, String email, @JsonProperty("isSuper") boolean isSuper, List<String> permissions) {
}
