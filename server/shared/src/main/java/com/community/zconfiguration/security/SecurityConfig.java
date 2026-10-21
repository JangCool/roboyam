package com.community.zconfiguration.security;

import com.community.installation.config.InstallationGuardFilter;
import com.community.installation.service.InstallationState;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * 보안 설정 — 공개 사이트(api)와 관리자 앱(admin)이 같이 쓴다.
 * <ul>
 *   <li>로그인은 Spring 의 기본 로그인 폼이 아니라 {@code POST /api/auth/login}(관리자 앱은 {@code POST /api/admin/auth/login}) 컨트롤러가 직접 확인하고 세션에 로그인 상태를 저장한다
 *       ({@link #securityContextRepository()} — 컨트롤러와 필터가 같은 저장소를 쓴다). 기본 로그인 폼·HTTP Basic·로그아웃 필터는 끈다.</li>
 *   <li>CSRF 는 쿠키(XSRF-TOKEN) + 헤더(X-XSRF-TOKEN) 방식이다(SPA). 쿠키는 어느 응답에서든 내려간다(설치 마법사는 {@code status} 응답이, 설치 뒤에는
 *       {@code GET /api/auth/me} 가 첫 쿠키다).</li>
 *   <li>설치 전에는 {@code /api/**} 중 설치 마법사만 열려 있고 나머지는 503 NOT_INSTALLED 다({@link InstallationGuardFilter} — CSRF 검사 앞).</li>
 *   <li>로그인 없이 쓰는 구간: {@code /api/system/installation/**}(토큰·설치 상태 검사는 서비스가 한다), {@code GET /api/auth/me}·{@code /api/auth/providers},
 *       {@code POST /api/auth/login}·{@code /api/auth/logout}. 그 밖의 {@code /api/**}(인증 메일 재발송 포함)는 인증이 필요하다 —
 *       인증이 없으면 JSON {@code 401 UNAUTHENTICATED}. 화면(정적 파일)·actuator 는 연다.</li>
 *   <li>오류는 JSON 으로 답한다(화면이 HTML 오류 페이지를 받지 않게). CSRF 거절은 {@code 403 CSRF_INVALID}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, InstallationState installationState,
                                           SecurityContextRepository securityContextRepository, ErrorResponseWriter errors) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                // 로그인 상태는 서버 세션(Spring Session JDBC)에 둔다 — 컨트롤러가 저장하고 필터가 요청마다 읽는다
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // 인증 없이 보호된 주소를 부른 요청을 세션에 저장하지 않는다(기본값은 저장한다 — 익명 요청마다 세션 행이 DB 에 생긴다)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                // 설치 전에는 설치 마법사 말고 모든 /api/** 를 503 NOT_INSTALLED 로 막는다. 요청 정리(HttpFirewall) 뒤, CSRF 검사 앞이다
                .addFilterBefore(new InstallationGuardFilter(installationState, errors), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // 설치 마법사 — 로그인 없이 쓰는 구간이다. 토큰 인증·설치 상태 검사는 서비스가 한다
                        .requestMatchers("/api/system/installation/**").permitAll()
                        // 로그인 상태 조회·소셜 공급자 목록·로그인·로그아웃 — 로그인 전에도 부른다(로그아웃은 비로그인이어도 204)
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/providers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/logout").permitAll()
                        // 관리자 앱(admin)의 로그인 상태 조회·로그인·로그아웃 — 같은 규약이다(공개 사이트 서버에는 이 주소가 없다). 그 밖의 /api/admin/** 는 로그인이 필요하고,
                        // 어떤 권한이 필요한지는 컨트롤러가 선언하며 선언이 없으면 거절된다(admin 의 AdminAccessInterceptor — fail-closed)
                        .requestMatchers(HttpMethod.GET, "/api/admin/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login", "/api/admin/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                errors.write(request, response, new CommonException(ErrorCode.UNAUTHENTICATED)))
                        .accessDeniedHandler((request, response, ex) ->
                                errors.write(request, response, new CommonException(
                                        ex instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN))))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .headers(Customizer.withDefaults());
        return http.build();
    }

    /**
     * 로그인 상태(보안 컨텍스트)를 HTTP 세션에 저장하고 읽는 저장소. 세션은 Spring Session JDBC 가 DB 에 둔다 — 그래서 컨텍스트에 든
     * 로그인 주체는 직렬화할 수 있어야 한다. 로그인 컨트롤러가 같은 빈으로 직접 저장한다.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Spring 의 사용자 저장소를 쓰지 않는다 — 로그인은 컨트롤러가 직접 확인한다. 이 빈이 없으면 Spring Boot 가 임시 사용자와 무작위 비밀번호를 만들어
     * 로그에 찍는다. 그것을 막으려는 자리 표시 빈이다.
     */
    @Bean
    public UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("사용자 저장소를 쓰지 않는다 — 로그인은 /api/auth/login 이 직접 확인한다");
        };
    }
}
