package com.community.adminauth.dto;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 세션에 저장되는 로그인 주체. 세션은 DB(Spring Session JDBC)에 직렬화되어 들어가므로 가볍고 {@link Serializable} 이어야 한다.
 * 운영자 ID 만 들고 있다 — 상태·권한은 바뀔 수 있으므로 <b>요청마다 DB 에서 다시 읽는다</b>(낡은 권한이 세션에 남지 않는다).
 *
 * <p>{@link #getName()} 은 {@code operator:<ID>} 다 — Spring Session 이 이 값으로 {@code spring_session.principal_name} 을 채운다.
 * 공개 사이트 회원의 세션(회원 ID 숫자만)과 이름 공간이 겹치지 않는다. 권한 이름은 {@code ROLE_OPERATOR} 하나이고 실제 권한은 {@code AdminActor} 가 정한다.
 */
public record OperatorPrincipal(long id) implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return "operator:" + id;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }
}
