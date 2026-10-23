package com.community.adminauth.controller;

import com.community.adminauth.config.AdminAuthProperties;
import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.service.AdminActorService;
import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.PendingMfa;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
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
 *   <li>관리자 2차 인증이 남았으면 로그인 대신 <b>「2차 대기」 세션</b>({@link #beginMfaPending})을 만든다 — 보안 컨텍스트 없이 대기 정보만, 5분. 통과하면 {@link #establish} 가
 *       세션을 다시 바꿔 정상 로그인 세션을 만든다.</li>
 * </ul>
 */
@Component
public class AdminSession {

    /** 세션에 적어 두는 로그인 시각(epoch ms) — 최대 길이 12시간을 센다 */
    static final String LOGIN_AT_ATTRIBUTE = "roboyam.admin.loginAt";

    /** 2차 대기 세션에 두는 대기 정보({@link PendingMfa}) */
    static final String MFA_PENDING_ATTRIBUTE = "roboyam.admin.mfaPending";

    /** 2차 대기의 유효 시간 — 만든 때부터 */
    public static final Duration MFA_PENDING_TTL = Duration.ofMinutes(5);

    private final SecurityContextRepository securityContextRepository;
    private final AdminAuthProperties props;
    private final AdminActorService actors;
    private final ClientIpResolver clientIps;
    private final Clock clock;

    public AdminSession(SecurityContextRepository securityContextRepository, AdminAuthProperties props, AdminActorService actors,
                        ClientIpResolver clientIps, Clock clock) {
        this.securityContextRepository = securityContextRepository;
        this.props = props;
        this.actors = actors;
        this.clientIps = clientIps;
        this.clock = clock;
    }

    /**
     * 「2차 대기」 세션을 만든다 — 비밀번호는 맞았지만 2차 인증이 남았다. 옛 세션을 버리고(세션 고정 방지) 새 세션에 {@link PendingMfa}({@code operatorId}·단계·만든 시각)만 둔다.
     * <b>보안 컨텍스트를 두지 않으므로 로그인으로 취급되지 않는다</b>({@link #resolve} 는 비어 있고, 로그인이 필요한 API 는 401). 유효 시간은 {@link #MFA_PENDING_TTL}.
     */
    public void beginMfaPending(HttpServletRequest request, HttpServletResponse response, long operatorId, MfaStage stage) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((int) MFA_PENDING_TTL.toSeconds());
        session.setAttribute(MFA_PENDING_ATTRIBUTE, new PendingMfa(operatorId, stage, clock.millis()));
    }

    /**
     * 이 요청의 2차 대기 정보. 대기 세션이 아니거나, 만든 지 {@link #MFA_PENDING_TTL} 이 지났거나, 그 사이 운영자가 로그인할 수 없게 됐으면(잠금·사용 중지·삭제 …)
     * 그 세션을 끝내고 비어 있다.
     */
    public Optional<PendingMfa> mfaPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(MFA_PENDING_ATTRIBUTE) instanceof PendingMfa pending)) {
            return Optional.empty();
        }
        if (clock.millis() - pending.createdAtMillis() > MFA_PENDING_TTL.toMillis()) {
            terminate(request);
            return Optional.empty();
        }
        if (actors.load(pending.operatorId(), clientIps.resolve(request)).isEmpty()) {
            terminate(request);
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    /** 2차 대기 세션이면 끝낸다(「다른 계정으로 로그인」). 로그인 세션·세션 없음이면 아무것도 하지 않는다 */
    public void cancelMfaPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(MFA_PENDING_ATTRIBUTE) instanceof PendingMfa) {
            terminate(request);
        }
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
        HttpSession current = request.getSession(false);
        if (current != null && current.getAttribute(MFA_PENDING_ATTRIBUTE) != null) {
            return Optional.empty(); // 2차 대기 세션은 로그인이 아니다(보안 컨텍스트를 두지 않지만 한 번 더 막는다)
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
