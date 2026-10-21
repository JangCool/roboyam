package com.community.installation.config;

import com.community.installation.exception.InstallationNotInstalledException;
import com.community.installation.service.InstallationState;
import com.community.zconfiguration.exception.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * <b>설치 전에는 {@code /api/**} 를 막는다.</b> 설치 마법사 API({@code /api/system/installation/**}) 만 열려 있고 나머지는 {@code 503 NOT_INSTALLED}.
 * 화면 정적 파일·{@code /actuator} 같은 {@code /api} 밖의 요청은 그대로 지나간다 — 화면은 앱이 뜰 때 상태를 한 번 묻고 설치 전이면 모든 주소를 {@code /install} 로 보낸다.
 *
 * <p>설치 상태는 메모리 캐시({@link InstallationState})라 요청마다 DB 를 읽지 않는다. <b>보안 필터 체인 안</b>({@code SecurityConfig}, CSRF 검사 앞)에서 돌아
 * 스프링 시큐리티의 요청 정리(HttpFirewall — {@code ..}·{@code //}·{@code ;} 같은 경로 우회 거절)를 거친 경로로 판단한다. 그래서 이 필터는 스프링 빈이 아니다
 * (필터 빈은 서블릿 컨테이너에도 따로 등록돼 정리 전의 경로를 볼 수 있다). 메서드·CSRF 헤더와 상관없이 막는다(503 이 먼저).
 */
public class InstallationGuardFilter extends OncePerRequestFilter {

    private static final String API = "/api/";
    private static final String INSTALLATION = "/api/system/installation";

    private final InstallationState state;
    private final ErrorResponseWriter errors;

    public InstallationGuardFilter(InstallationState state, ErrorResponseWriter errors) {
        this.state = state;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith(API) || path.equals(INSTALLATION) || path.startsWith(INSTALLATION + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (state.isInstalled()) {
            chain.doFilter(request, response);
            return;
        }
        errors.write(request, response, new InstallationNotInstalledException());
    }
}
