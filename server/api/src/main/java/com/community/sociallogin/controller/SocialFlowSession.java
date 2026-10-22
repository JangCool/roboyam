package com.community.sociallogin.controller;

import com.community.sociallogin.config.SocialLoginProperties;
import com.community.sociallogin.dto.AuthorizationFlow;
import com.community.sociallogin.dto.PendingSignup;
import com.community.zconfiguration.crypto.Digests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 서버 세션에 두는 소셜 흐름의 임시 상태 — 웹 계층의 도우미다(서비스는 세션을 모른다). 세션은 DB(Spring Session JDBC)에 있다.
 *
 * <ul>
 *   <li><b>시작한 흐름</b>({@link AuthorizationFlow}): 세션에 묶인 일회용이다. 콜백의 state 로 찾으면서 <b>바로 지운다</b>(재사용 불가). 한 세션에 최대 {@value #MAX_FLOWS}개
 *       (탭 여러 개) — 넘으면 오래된 것부터 버린다. state 원문은 두지 않고 해시만 둔다.</li>
 *   <li><b>가입 대기</b>({@link PendingSignup}): 콜백이 처음 보는 소셜 계정을 받으면 <b>세션 ID 를 바꾼 뒤</b> 둔다(세션 고정 방지). 만료가 지나면 없는 것으로 본다.
 *       가입·로그인하면 {@code LoginSession.establish} 가 세션째 없앤다.</li>
 *   <li>비로그인 상태에서 새로 만든 세션은 기한을 흐름·가입 대기 유효 시간에 맞춰 짧게 둔다(기본 세션 기한 30일을 쓰지 않는다).</li>
 * </ul>
 */
@Component
public class SocialFlowSession {

    static final String FLOWS = "roboyam.social.flows";
    static final String PENDING = "roboyam.social.pending";
    static final int MAX_FLOWS = 3;
    /** state 원문의 최대 길이(우리가 만든 값은 43자) */
    private static final int STATE_MAX = 256;

    private final SocialLoginProperties props;

    public SocialFlowSession(SocialLoginProperties props) {
        this.props = props;
    }

    /** 시작한 흐름을 기억한다 */
    public void remember(HttpServletRequest request, AuthorizationFlow flow) {
        boolean created = request.getSession(false) == null;
        HttpSession session = request.getSession(true);
        if (created) {
            long seconds = props.stateTtl().plus(props.pendingTtl()).toSeconds();
            session.setMaxInactiveInterval((int) Math.min(Integer.MAX_VALUE, seconds));
        }
        Instant now = Instant.now();
        List<AuthorizationFlow> flows = new ArrayList<>(flows(session).stream().filter(f -> !f.isExpired(now)).toList());
        flows.add(flow);
        while (flows.size() > MAX_FLOWS) {
            flows.removeFirst();
        }
        session.setAttribute(FLOWS, new ArrayList<>(flows));
    }

    /**
     * state 로 흐름을 찾아 <b>꺼내면서 지운다</b>. 없거나(위조·재사용·다른 브라우저) state 가 비었으면 비어 있다. 만료된 흐름도 돌려준다(서비스가 「만료」로 가른다).
     */
    public Optional<AuthorizationFlow> take(HttpServletRequest request, String rawState) {
        HttpSession session = request.getSession(false);
        if (session == null || rawState == null || rawState.isEmpty() || rawState.length() > STATE_MAX) {
            return Optional.empty();
        }
        byte[] wanted = Digests.sha256Hex(rawState).getBytes(StandardCharsets.US_ASCII);
        List<AuthorizationFlow> flows = new ArrayList<>(flows(session));
        AuthorizationFlow found = null;
        for (AuthorizationFlow flow : flows) {
            if (MessageDigest.isEqual(flow.stateHash().getBytes(StandardCharsets.US_ASCII), wanted)) {
                found = flow;
            }
        }
        if (found == null) {
            return Optional.empty();
        }
        flows.remove(found);
        if (flows.isEmpty()) {
            session.removeAttribute(FLOWS);
        } else {
            session.setAttribute(FLOWS, new ArrayList<>(flows));
        }
        return Optional.of(found);
    }

    /** 가입 대기를 둔다 — 세션 ID 를 먼저 바꾼다 */
    public void holdPending(HttpServletRequest request, PendingSignup pending) {
        request.changeSessionId();
        request.getSession(true).setAttribute(PENDING, pending);
    }

    /** 이어갈 가입 대기. 없거나 만료됐으면 비어 있다(만료된 것은 지운다) */
    public Optional<PendingSignup> pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING) instanceof PendingSignup pending)) {
            return Optional.empty();
        }
        if (pending.isExpired(Instant.now())) {
            session.removeAttribute(PENDING);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    @SuppressWarnings("unchecked")
    private static List<AuthorizationFlow> flows(HttpSession session) {
        Object value = session.getAttribute(FLOWS);
        if (value instanceof List<?> list && list.stream().allMatch(AuthorizationFlow.class::isInstance)) {
            return (List<AuthorizationFlow>) list;
        }
        return List.of();
    }
}
