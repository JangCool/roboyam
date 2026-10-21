package com.community.auth.controller;

import com.community.auth.dto.AuthUser;
import com.community.auth.dto.request.LoginRequest;
import com.community.auth.dto.response.MeResponse;
import com.community.auth.dto.response.ProvidersResponse;
import com.community.auth.service.EmailVerificationService;
import com.community.auth.service.LoginService;
import com.community.auth.service.MeService;
import com.community.auth.service.SocialProviderService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 API — 로그인 상태 조회·소셜 공급자 목록·로그인·로그아웃·인증 메일 다시 보내기 (docs/features/login-contract.md).
 *
 * <p>HTTP 와 세션만 다룬다. 이메일·비밀번호 확인은 {@link LoginService}, 세션 만들기·끊기는 {@link LoginSession} 이 한다.
 * 접근 규칙은 {@code SecurityConfig} 에 있다: me·providers·login·logout 은 로그인 없이 부를 수 있고, 인증 메일 재발송은 로그인이 필요하다(없으면 JSON 401).
 * 쓰기 요청은 CSRF 헤더가 필요하다 — 쿠키는 {@code GET /api/auth/me} 가 내려 준다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final MeService meService;
    private final SocialProviderService providerService;
    private final EmailVerificationService verificationService;
    private final LoginSession loginSession;

    public AuthController(LoginService loginService, MeService meService, SocialProviderService providerService,
                          EmailVerificationService verificationService, LoginSession loginSession) {
        this.loginService = loginService;
        this.meService = meService;
        this.providerService = providerService;
        this.verificationService = verificationService;
        this.loginSession = loginSession;
    }

    /**
     * 로그인 상태. <b>항상 200</b> 이다(비로그인이 오류가 아니다). 화면은 앱을 열 때 이것을 먼저 부른다 — CSRF 쿠키가 이때 내려오고,
     * 로그인 상태면 세션 쿠키의 기한이 오늘부터 30일로 늘어난다. 세션은 있는데 회원이 없어졌거나 탈퇴했으면 그 세션을 끝내고 비로그인으로 답한다.
     */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser principal)) {
            return ApiResponse.success(MeResponse.anonymous());
        }
        Optional<MeResponse> me = meService.of(principal.id());
        if (me.isEmpty()) {
            loginSession.terminate(request);
            return ApiResponse.success(MeResponse.anonymous());
        }
        loginSession.refreshCookie(request, response);
        return ApiResponse.success(me.get());
    }

    @GetMapping("/providers")
    public ApiResponse<ProvidersResponse> providers() {
        return ApiResponse.success(new ProvidersResponse(providerService.enabledProviders()));
    }

    /** 이메일 로그인. 성공하면 세션을 새로 만들고 {@code me} 와 같은 모양으로 답한다 */
    @PostMapping("/login")
    public ApiResponse<MeResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthUser principal = loginService.authenticate(body.email(), body.password());
        loginSession.establish(request, response, principal);
        return ApiResponse.success(meService.of(principal.id()).orElseGet(MeResponse::anonymous));
    }

    /** 로그아웃 — 세션을 없앤다. 로그인하지 않은 상태에서도 204 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        loginSession.terminate(request);
        return ResponseEntity.noContent().build();
    }

    /** 인증 메일 다시 보내기(로그인 필요). 메일 서버·공개 주소가 없어 못 보내도 202 다(서버 로그에만 남는다) */
    @PostMapping("/email/resend")
    public ResponseEntity<Void> resendVerification(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        verificationService.resend(principal.id());
        return ResponseEntity.accepted().build();
    }
}
