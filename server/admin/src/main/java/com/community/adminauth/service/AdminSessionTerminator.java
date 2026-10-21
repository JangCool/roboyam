package com.community.adminauth.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 운영자의 로그인 세션을 서버에서 끝낸다(비밀번호 재설정·변경). 세션 저장소({@code admin_spring_session})의 주체 이름({@code principal_name} = {@code operator:<ID>},
 * {@code OperatorPrincipal#getName()})으로 찾아 지운다 — 공개 사이트의 세션 표와는 다른 표라 서로 닿지 않는다.
 * 호출하는 쪽의 트랜잭션이 있으면 거기에 실려, 비밀번호 변경이 롤백되면 세션도 그대로 남는다.
 */
@Component
public class AdminSessionTerminator {

    private static final Logger log = LoggerFactory.getLogger(AdminSessionTerminator.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public AdminSessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** 그 운영자의 모든 세션을 끝낸다. 끝낸 수를 돌려준다 */
    public int endAll(long operatorId) {
        return end(operatorId, null);
    }

    /** {@code keepSessionId} 를 뺀 그 운영자의 다른 세션을 끝낸다(지금 쓰는 세션은 유지) */
    public int endOthers(long operatorId, String keepSessionId) {
        return end(operatorId, keepSessionId);
    }

    private int end(long operatorId, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName("operator:" + operatorId);
        int ended = 0;
        for (String sessionId : found.keySet()) {
            if (sessionId.equals(keepSessionId)) {
                continue;
            }
            sessions.deleteById(sessionId);
            ended++;
        }
        log.info("운영자 세션을 끝냈다 operatorId={} {}개", operatorId, ended);
        return ended;
    }
}
