package com.community.mfa.controller;

import com.community.adminauth.controller.AdminLoginFinisher;
import com.community.adminauth.controller.AdminSession;
import com.community.adminauth.dto.response.AdminMeResponse;
import com.community.mfa.dto.MfaStage;
import com.community.mfa.dto.MfaVerifyResult;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.request.MfaCodeRequest;
import com.community.mfa.dto.request.MfaVerifyRequest;
import com.community.mfa.dto.response.MfaEnrollmentConfirmResponse;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.service.MfaEnrollmentService;
import com.community.mfa.service.MfaVerifyService;
import com.community.permission.annotation.AdminMfaPending;
import com.community.permission.annotation.AdminPublic;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 2단계 — 「2차 대기」 세션(비밀번호는 맞았지만 2차 인증이 남음, 로그인 아님)의 API (docs/features/commerce-foundation-contract.md 「0-3」).
 *
 * <ul>
 *   <li>{@code POST …/verify}(VERIFY 대기): OTP 또는 복구 코드 → 통과하면 세션 ID 를 바꿔 로그인 세션을 만들고 {@code me}(+복구 코드면 {@code recoveryCodesLeft}).
 *       「이 기기에서 다시 묻지 않기」면 신뢰 기기 쿠키를 싣는다.</li>
 *   <li>{@code POST …/cancel}(공개): 대기 세션을 버린다(「다른 계정으로 로그인」) — 로그인 세션은 건드리지 않는다. 204.</li>
 *   <li>{@code POST …/enrollment} · {@code …/enrollment/confirm}(ENROLL 대기): 강제 등록 시작 → 확정하면 복구 코드 10개 + {@code me}(로그인 완료).</li>
 * </ul>
 * 대기 세션이 없거나 5분이 지났거나 그 단계가 아니면 401 {@code MFA_PENDING_EXPIRED}(인터셉터). 쓰기 요청이라 CSRF 헤더가 필요하다. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/auth/mfa")
public class AdminMfaAuthController {

    private final MfaVerifyService verifier;
    private final MfaEnrollmentService enrollments;
    private final AdminLoginFinisher finisher;
    private final AdminSession adminSession;
    private final MfaTrustCookie trustCookie;
    private final ClientIpResolver clientIps;

    public AdminMfaAuthController(MfaVerifyService verifier, MfaEnrollmentService enrollments, AdminLoginFinisher finisher, AdminSession adminSession,
                                  MfaTrustCookie trustCookie, ClientIpResolver clientIps) {
        this.verifier = verifier;
        this.enrollments = enrollments;
        this.finisher = finisher;
        this.adminSession = adminSession;
        this.trustCookie = trustCookie;
        this.clientIps = clientIps;
    }

    @AdminMfaPending(MfaStage.VERIFY)
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<AdminMeResponse>> verify(PendingMfa pending, @RequestBody(required = false) MfaVerifyRequest body, HttpServletRequest request,
                                                              HttpServletResponse response) {
        MfaVerifyResult result = verifier.verify(pending, body, clientIps.resolve(request), request.getHeader("User-Agent"));
        if (result.trustToken() != null) {
            trustCookie.write(request, response, result.trustToken(), result.trustDays());
        }
        return noStore(finisher.establish(request, response, pending.operatorId(), null, result.recoveryCodesLeft()));
    }

    @AdminPublic
    @PostMapping("/cancel")
    public ResponseEntity<Void> cancel(HttpServletRequest request) {
        adminSession.cancelMfaPending(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @AdminMfaPending(MfaStage.ENROLL)
    @PostMapping("/enrollment")
    public ResponseEntity<ApiResponse<MfaEnrollmentView>> startEnrollment(PendingMfa pending) {
        return noStore(enrollments.startForced(pending));
    }

    @AdminMfaPending(MfaStage.ENROLL)
    @PostMapping("/enrollment/confirm")
    public ResponseEntity<ApiResponse<MfaEnrollmentConfirmResponse>> confirmEnrollment(PendingMfa pending, @RequestBody(required = false) MfaCodeRequest body,
                                                                                      HttpServletRequest request, HttpServletResponse response) {
        List<String> codes = enrollments.confirmForced(pending, body == null ? null : body.code(), clientIps.resolve(request), request.getHeader("User-Agent"));
        AdminMeResponse me = finisher.establish(request, response, pending.operatorId(), null, null);
        return noStore(new MfaEnrollmentConfirmResponse(codes, me));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
