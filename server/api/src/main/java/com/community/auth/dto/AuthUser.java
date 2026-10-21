package com.community.auth.dto;

import com.community.user.entity.Role;
import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 세션에 저장되는 로그인 주체. 세션은 DB(Spring Session JDBC)에 직렬화되어 들어가므로 가볍고 {@link Serializable} 이어야 한다.
 * 이메일 인증 여부·상태처럼 바뀔 수 있는 값은 넣지 않고 필요할 때 DB 에서 읽는다.
 *
 * <p>{@link #getName()} 은 회원 ID 다 — Spring Session 이 이 값으로 {@code spring_session.principal_name} 을 채워
 * "이 회원의 모든 세션"을 찾을 수 있다(비밀번호 재설정 때 다른 기기 로그인을 끊는 데 쓸 자리).
 * 권한 이름은 {@code ROLE_USER} · {@code ROLE_STAFF} · {@code ROLE_ADMIN} 이다.
 */
public record AuthUser(long id, Role role) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return Long.toString(id);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
