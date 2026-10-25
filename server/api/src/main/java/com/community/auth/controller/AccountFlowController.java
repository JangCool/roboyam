package com.community.auth.controller;

import com.community.auth.dto.AuthUser;
import com.community.auth.dto.request.EmailVerifyRequest;
import com.community.auth.dto.request.PasswordForgotRequest;
import com.community.auth.dto.request.PasswordResetRequest;
import com.community.auth.dto.request.SignupRequest;
import com.community.auth.dto.response.EmailAvailabilityResponse;
import com.community.auth.dto.response.EmailVerifyResponse;
import com.community.auth.dto.response.MeResponse;
import com.community.auth.dto.response.NicknameAvailabilityResponse;
import com.community.auth.dto.response.PasswordResetCheckResponse;
import com.community.auth.service.EmailLinkService;
import com.community.auth.service.MeService;
import com.community.auth.service.PasswordResetService;
import com.community.auth.service.SignupService;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 계정 흐름의 로그인 전 API (docs/features/member-account-contract.md) — 이메일 가입·이메일/닉네임 확인·메일 링크 확인·비밀번호 찾기/재설정.
 * 모두 로그인 없이 부를 수 있고({@code SecurityConfig}), 쓰기는 CSRF 헤더가 필요하다. 입력은 어노테이션 없이 받고 서비스가 검사한다(검사 순서가 계약이다).
 * 토큰·비밀번호가 오가는 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/auth")
public class AccountFlowController {

    private final SignupService signupService;
    private final EmailLinkService emailLinks;
    private final PasswordResetService passwordReset;
    private final MeService meService;
    private final LoginSession loginSession;

    public AccountFlowController(SignupService signupService, EmailLinkService emailLinks, PasswordResetService passwordReset, MeService meService,
                                 LoginSession loginSession) {
        this.signupService = signupService;
        this.emailLinks = emailLinks;
        this.passwordReset = passwordReset;
        this.meService = meService;
        this.loginSession = loginSession;
    }

    /** 이메일 가입 → 바로 로그인(옛 세션을 없애고 새 세션). {@code me} 와 같은 모양으로 201 */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MeResponse>> signup(@RequestBody(required = false) SignupRequest body, HttpServletRequest request,
                                                          HttpServletResponse response) {
        AuthUser member = signupService.signup(body);
        loginSession.establish(request, response, member);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(meService.of(member.id()).orElseGet(MeResponse::anonymous)));
    }

    @GetMapping("/email-available")
    public ApiResponse<EmailAvailabilityResponse> emailAvailable(@RequestParam(name = "email", required = false) String email) {
        return ApiResponse.success(signupService.emailAvailable(email));
    }

    @GetMapping("/nickname-available")
    public ApiResponse<NicknameAvailabilityResponse> nicknameAvailable(@RequestParam(name = "nickname", required = false) String nickname) {
        return ApiResponse.success(signupService.nicknameAvailable(nickname));
    }

    /** 메일 링크 확인(인증 링크·이메일 변경 링크) — 로그인 없이 */
    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<EmailVerifyResponse>> verifyEmail(@RequestBody(required = false) EmailVerifyRequest body) {
        EmailVerifyResponse result = emailLinks.verify(body == null ? null : body.token());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    /** 비밀번호 재설정 메일 요청 — 형식이 맞으면 계정 유무와 상관없이 늘 202 */
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@RequestBody(required = false) PasswordForgotRequest body) {
        passwordReset.forgot(body == null ? null : body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/password/reset/check")
    public ResponseEntity<ApiResponse<PasswordResetCheckResponse>> checkResetToken(@RequestParam(name = "token", required = false) String token) {
        passwordReset.check(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new PasswordResetCheckResponse(true)));
    }

    /** 새 비밀번호 저장 — 204, 그 계정의 모든 세션이 끝난다(이 요청이 로그인 상태였어도 다시 로그인한다) */
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@RequestBody(required = false) PasswordResetRequest body) {
        passwordReset.reset(body == null ? null : body.token(), body == null ? null : body.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
