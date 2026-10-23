package com.community.adminauth.controller;

import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.dto.request.AdminLoginRequest;
import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.adminauth.service.AdminLoginService;
import com.community.adminauth.service.AdminMeService;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.service.MfaLoginService;
import com.community.permission.annotation.AdminPublic;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 로그인 API — 로그인 상태 조회·로그인·로그아웃 ({@code docs/features/roles-contract.md}, admin-web {@code useAdminAuthApi.types.ts}).
 *
 * <p>HTTP 와 세션만 다룬다. 이메일·비밀번호 확인은 {@link AdminLoginService}, 세션 만들기·끊기는 {@link AdminSession} 이 한다.
 * 이 셋은 로그인 없이 부를 수 있다({@link AdminPublic} + {@code SecurityConfig} 의 허용 주소). 쓰기 요청은 CSRF 헤더가 필요하다 — 쿠키는 {@code GET …/me} 가 내려 준다.
 * 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminLoginService loginService;
    private final AdminMeService meService;
    private final AdminSession adminSession;
    private final AuditLogService audit;
    private final ClientIpResolver clientIps;
    private final AdminLoginFinisher finisher;
    private final MfaLoginService mfaLogins;

    public AdminAuthController(AdminLoginService loginService, AdminMeService meService, AdminSession adminSession, AuditLogService audit,
                               ClientIpResolver clientIps, AdminLoginFinisher finisher, MfaLoginService mfaLogins) {
        this.loginService = loginService;
        this.meService = meService;
        this.adminSession = adminSession;
        this.audit = audit;
        this.clientIps = clientIps;
        this.finisher = finisher;
        this.mfaLogins = mfaLogins;
    }

    /**
     * 로그인 상태. <b>항상 200</b> 이다(비로그인이 오류가 아니다). 화면은 앱을 열 때 이것을 먼저 부른다 — CSRF 쿠키가 이때 내려온다.
     * 세션은 있는데 운영자가 지워졌거나 로그인할 수 없는 상태가 됐거나 12시간이 넘었으면 그 세션을 끝내고 비로그인으로 답한다.
     */
    @AdminPublic
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AdminMeResponse>> me(HttpServletRequest request) {
        Optional<AdminActor> actor = adminSession.resolve(request);
        if (actor.isEmpty()) {
            // 2차 대기 세션이면 비로그인 + 같은 단계(새로고침해도 단계가 유지된다)
            Optional<PendingMfa> pending = adminSession.mfaPending(request);
            if (pending.isPresent()) {
                return noStore(meService.of(Optional.empty(), mfaLogins.view(pending.get()), null));
            }
        }
        return noStore(meService.of(actor));
    }

    /**
     * 이메일 로그인. 비밀번호가 맞으면 관리자 2차 인증 판정에 따라 세션을 새로 만들거나(바로 로그인 — 유예 안이면 {@code mfa.stage = GRACE})
     * 2차 대기 세션을 만든다({@code authenticated:false} + {@code mfa.stage = VERIFY|ENROLL}). 응답은 {@code me} 와 같은 모양
     */
    @AdminPublic
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminMeResponse>> login(@RequestBody AdminLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        OperatorPrincipal principal = loginService.authenticate(body.email(), body.password(), clientIps.resolve(request), request.getHeader("User-Agent"));
        return noStore(finisher.afterPassword(request, response, principal, null));
    }

    /** 로그아웃 — 세션을 없앤다. 로그인하지 않은 상태에서도 204 */
    @AdminPublic
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        Optional<AdminActor> actor = adminSession.resolve(request);
        adminSession.terminate(request);
        actor.ifPresent(a -> audit.record(AuditEvent.byOperator(a.operatorId(), a.ip(), AuditActions.LOGOUT, AuditResult.SUCCESS)
                .target("OPERATOR", Long.toString(a.operatorId()))));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<ApiResponse<AdminMeResponse>> noStore(AdminMeResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
