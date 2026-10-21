package com.community.adminauth.controller;

import com.community.adminauth.dto.OperatorPrincipal;
import com.community.adminauth.dto.request.PasswordResetCodeRequest;
import com.community.adminauth.dto.request.PasswordResetRequest;
import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.adminauth.dto.response.PasswordResetCodeResponse;
import com.community.adminauth.service.AdminMeService;
import com.community.adminauth.service.PasswordResetCodeService;
import com.community.adminauth.service.PasswordResetService;
import com.community.operator.dto.request.InvitationAcceptRequest;
import com.community.operator.dto.request.InvitationCheckRequest;
import com.community.operator.dto.response.InvitationCheckResponse;
import com.community.operator.service.OperatorInvitationAcceptService;
import com.community.permission.annotation.AdminPublic;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 전 운영자 계정 흐름 — 초대 링크 확인·수락, 비밀번호 재설정 승인번호 요청·재설정 (docs/features/roles-contract.md 「5단계 구현 결과」, admin-web {@code useAdminAccountApi.ts}).
 *
 * <p>모두 {@link AdminPublic} + {@code SecurityConfig} 의 허용 주소다. 쓰기 요청이라 CSRF 헤더가 필요하다(쿠키는 {@code GET /api/admin/auth/me} 가 내려 준다). 토큰·승인번호·비밀번호는
 * URL 이 아니라 본문으로 받는다. 입력 검사·업무 규칙·계정 열거 방어는 서비스가 한다. 응답은 {@code Cache-Control: no-store}.
 * 재설정이 성공하면 곧바로 로그인시킨다 — 세션 만들기는 {@link AdminSession}(세션 고정 방지로 옛 세션을 없애고 새로 만든다).
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAccountController {

    private final OperatorInvitationAcceptService invitations;
    private final PasswordResetCodeService resetCodes;
    private final PasswordResetService resets;
    private final AdminMeService meService;
    private final AdminSession adminSession;
    private final ClientIpResolver clientIps;

    public AdminAccountController(OperatorInvitationAcceptService invitations, PasswordResetCodeService resetCodes, PasswordResetService resets,
                                  AdminMeService meService, AdminSession adminSession, ClientIpResolver clientIps) {
        this.invitations = invitations;
        this.resetCodes = resetCodes;
        this.resets = resets;
        this.meService = meService;
        this.adminSession = adminSession;
        this.clientIps = clientIps;
    }

    /** 초대 링크의 토큰을 확인한다 — 유효하면 초대받은 이메일·받을 역할·유효 기한 */
    @AdminPublic
    @PostMapping("/invitation/check")
    public ResponseEntity<ApiResponse<InvitationCheckResponse>> checkInvitation(@RequestBody InvitationCheckRequest body) {
        return noStore(invitations.check(body.token()));
    }

    /** 이름과 비밀번호를 정해 운영자 계정을 만든다 — 로그인시키지 않는다(204) */
    @AdminPublic
    @PostMapping("/invitation/accept")
    public ResponseEntity<Void> acceptInvitation(@RequestBody InvitationAcceptRequest body, HttpServletRequest request) {
        invitations.accept(body, clientIps.resolve(request));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 비밀번호 재설정 승인번호를 메일로 보낸다(다시 받기도 같다) — 가입 여부와 무관하게 늘 같은 응답 */
    @AdminPublic
    @PostMapping("/password-reset/code")
    public ResponseEntity<ApiResponse<PasswordResetCodeResponse>> requestPasswordResetCode(@RequestBody PasswordResetCodeRequest body, HttpServletRequest request) {
        return noStore(resetCodes.request(body.email(), clientIps.resolve(request)));
    }

    /** 승인번호와 새 비밀번호로 비밀번호를 바꾸고 곧바로 로그인시킨다 — 로그인 응답과 같은 {@code me} 모양 */
    @AdminPublic
    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<AdminMeResponse>> resetPassword(@RequestBody PasswordResetRequest body, HttpServletRequest request, HttpServletResponse response) {
        OperatorPrincipal principal = resets.reset(body.email(), body.certCode(), body.newPassword(), clientIps.resolve(request));
        adminSession.establish(request, response, principal);
        return noStore(meService.of(adminSession.resolve(request)));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
