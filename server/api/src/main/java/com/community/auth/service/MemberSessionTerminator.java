package com.community.auth.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 회원의 로그인 세션을 서버에서 끝낸다(비밀번호 재설정 = 모두, 비밀번호 변경 = 지금 세션만 남김).
 *
 * <p>세션 저장소는 Spring Session JDBC 의 {@code spring_session}(shared V2)이고, 로그인 주체 이름({@code principal_name})은 회원 ID 문자열이다
 * ({@code AuthUser#getName()} — 보안 컨텍스트를 세션에 저장할 때 Spring Session 이 채운다). 색인 {@code spring_session_ix3 (principal_name)} 으로 찾는다.
 * 관리자 앱은 다른 표({@code admin_spring_session}, 주체 이름 {@code operator:<ID>})라 서로 닿지 않는다.
 *
 * <p>세션 삭제는 저장소가 자기 트랜잭션으로 한다 — 부르는 쪽 트랜잭션이 나중에 롤백돼도 끊긴 세션은 되살아나지 않는다(다시 로그인하면 될 뿐 안전한 쪽이다).
 * 세션 ID 는 로그에 남기지 않는다.
 */
@Component
public class MemberSessionTerminator {

    private static final Logger log = LoggerFactory.getLogger(MemberSessionTerminator.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public MemberSessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** 그 회원의 모든 세션을 끝낸다. 끝낸 수를 돌려준다 */
    public int endAll(long userId) {
        return end(userId, null);
    }

    /** {@code keepSessionId}(지금 요청의 세션)를 뺀 그 회원의 다른 세션을 끝낸다 */
    public int endOthers(long userId, String keepSessionId) {
        return end(userId, keepSessionId);
    }

    private int end(long userId, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(Long.toString(userId));
        int ended = 0;
        for (String sessionId : found.keySet()) {
            if (sessionId.equals(keepSessionId)) {
                continue;
            }
            sessions.deleteById(sessionId);
            ended++;
        }
        log.info("회원 세션을 끝냈다 userId={} {}개", userId, ended);
        return ended;
    }
}
