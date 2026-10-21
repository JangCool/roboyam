package com.community.adminauth.controller;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.service.AdminActorService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 서버 세션에 두는 <b>운영자 로그인 상태</b> — 웹 계층의 도우미다(서비스는 세션을 모른다). 쿠키는 {@code ADMIN_SESSION}(공개 사이트의 {@code SESSION} 과 분리)이다.
 *
 * <ul>
 *   <li>로그인하면 <b>옛 세션을 없애고 새 세션을 만든다</b> — 세션 고정 공격을 막는다.</li>
 *   <li>비활동 2시간(마지막 활동 뒤)과 최대 12시간(로그인한 때부터)을 따로 센다. 비활동은 세션 저장소가, 최대 길이는 세션에 적어 둔 로그인 시각을 {@link #resolve} 가 본다.</li>
 *   <li>세션에는 {@link OperatorPrincipal}(운영자 ID)만 담는다. 비밀번호·권한은 담지 않는다 — 상태·권한은 요청마다 DB 에서 다시 읽는다({@link #resolve}).</li>
 *   <li>로그아웃은 세션을 없앤다(로그인하지 않은 상태에서 불러도 아무 일도 없다).</li>
 * </ul>
 */
@Component
public class AdminSession {

    /** 세션에 적어 두는 로그인 시각(epoch ms) — 최대 길이 12시간을 센다 */
    static final String LOGIN_AT_ATTRIBUTE = "roboyam.admin.loginAt";

    private final SecurityContextRepository securityContextRepository;
    private final AdminAuthProperties props;
    private final AdminActorService actors;
    private final ClientIpResolver clientIps;

    public AdminSession(SecurityContextRepository securityContextRepository, AdminAuthProperties props, AdminActorService actors,
                        ClientIpResolver clientIps) {
        this.securityContextRepository = securityContextRepository;
        this.props = props;
        this.actors = actors;
        this.clientIps = clientIps;
    }

    /** 이 요청을 {@code principal} 의 로그인 상태로 만든다. 세션 쿠키는 응답에 새로 실린다 */
    public void establish(HttpServletRequest request, HttpServletResponse response, OperatorPrincipal principal) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((int) props.sessionIdleTimeout().toSeconds());
        session.setAttribute(LOGIN_AT_ATTRIBUTE, System.currentTimeMillis());

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /** 이 요청의 세션을 끝낸다(로그아웃·낡은 세션 정리) */
    public void terminate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * 이 요청의 로그인한 운영자(현재 상태·권한 포함). 로그인하지 않았거나, 세션이 최대 길이(12시간)를 넘었거나, 운영자가 지워졌거나 로그인할 수 없는 상태가 됐으면
     * (잠금·사용 중지·기간 만료 …) 그 세션을 끝내고 비어 있다 — 요청마다 DB 에서 다시 읽는다.
     */
    public Optional<AdminActor> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof OperatorPrincipal principal)) {
            return Optional.empty();
        }
        if (exceededMaxAge(request.getSession(false))) {
            terminate(request);
            return Optional.empty();
        }
        Optional<AdminActor> actor = actors.load(principal.id(), clientIps.resolve(request));
        if (actor.isEmpty()) {
            terminate(request);
        }
        return actor;
    }

    /** 세션에 로그인 시각이 없거나 최대 길이를 넘었으면 true — 시각을 모르는 세션은 믿지 않는다 */
    private boolean exceededMaxAge(HttpSession session) {
        if (session == null || !(session.getAttribute(LOGIN_AT_ATTRIBUTE) instanceof Long loginAt)) {
            return true;
        }
        return Duration.ofMillis(System.currentTimeMillis() - loginAt).compareTo(props.sessionMaxAge()) > 0;
    }
}
