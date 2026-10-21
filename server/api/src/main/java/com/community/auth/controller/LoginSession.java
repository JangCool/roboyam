package com.community.auth.controller;

import com.community.auth.config.AuthProperties;
import com.community.auth.dto.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.stereotype.Component;

/**
 * 서버 세션에 두는 <b>로그인 상태</b> — 웹 계층의 도우미다(서비스는 세션을 모른다). 설치 인증 표시({@link com.community.installation.controller.InstallSession})와
 * 같은 세션 저장소(DB)를 쓰지만 세션 단위로 따로 둔다.
 *
 * <ul>
 *   <li>로그인하면 <b>옛 세션을 없애고 새 세션을 만든다</b> — 세션 고정 공격을 막고, 옛 세션의 속성(설치 인증 표시 등)과 기한(30분)이 새 세션에 섞이지 않는다.</li>
 *   <li>새 세션의 기한은 {@code roboyam.auth.session-timeout}(30일)을 세션마다 명시한다. 요청이 올 때마다 마지막 활동 시각이 갱신돼 기한이 늘어난다(슬라이딩).</li>
 *   <li>세션에는 {@link AuthUser}(회원 ID·역할)만 담는다. 비밀번호·토큰은 담지 않는다.</li>
 *   <li>로그아웃은 세션을 없앤다(로그인하지 않은 상태에서 불러도 아무 일도 없다).</li>
 * </ul>
 */
@Component
public class LoginSession {

    private final SecurityContextRepository securityContextRepository;
    private final CookieSerializer cookieSerializer;
    private final AuthProperties props;

    public LoginSession(SecurityContextRepository securityContextRepository, CookieSerializer cookieSerializer, AuthProperties props) {
        this.securityContextRepository = securityContextRepository;
        this.cookieSerializer = cookieSerializer;
        this.props = props;
    }

    /** 이 요청을 {@code principal} 의 로그인 상태로 만든다. 세션 쿠키는 응답에 새로 실린다 */
    public void establish(HttpServletRequest request, HttpServletResponse response, AuthUser principal) {
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval((int) props.sessionTimeout().toSeconds());

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

    /** 현재 세션 ID 로 세션 쿠키를 다시 써서 쿠키 만료를 오늘부터 30일로 늘린다("마지막 활동 뒤 30일" — 서버 세션의 기한은 요청마다 자동으로 늘어난다) */
    public void refreshCookie(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            cookieSerializer.writeCookieValue(new CookieSerializer.CookieValue(request, response, session.getId()));
        }
    }
}
