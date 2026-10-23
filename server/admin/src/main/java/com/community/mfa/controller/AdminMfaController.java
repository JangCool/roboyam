package com.community.mfa.controller;

import com.community.mfa.dto.request.MfaCodeRequest;
import com.community.mfa.dto.request.MfaPasswordRequest;
import com.community.mfa.dto.response.MfaEnrollmentConfirmResponse;
import com.community.mfa.dto.response.MfaEnrollmentView;
import com.community.mfa.dto.response.MfaMeView;
import com.community.mfa.dto.response.MfaRecoveryCodesResponse;
import com.community.mfa.dto.response.MfaTrustedDeviceView;
import com.community.mfa.service.MfaAccountService;
import com.community.mfa.service.MfaEnrollmentService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 2차 인증(로그인 세션, 본인 것만 — 경로에 운영자 ID 가 없다) — 「내 정보 › 로그인 보안」 (docs/features/commerce-foundation-contract.md 「0-3」).
 * 자발 등록·확정, 상태, 복구 코드 새로 만들기, 다시 등록, 신뢰 기기 목록·끊기. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/mfa")
@AdminAuthenticated
public class AdminMfaController {

    private final MfaEnrollmentService enrollments;
    private final MfaAccountService accounts;
    private final MfaTrustCookie trustCookie;

    public AdminMfaController(MfaEnrollmentService enrollments, MfaAccountService accounts, MfaTrustCookie trustCookie) {
        this.enrollments = enrollments;
        this.accounts = accounts;
        this.trustCookie = trustCookie;
    }

    /** 자발 등록 시작 — 이미 등록했으면 409 {@code MFA_ALREADY_ENROLLED} */
    @PostMapping("/enrollment")
    public ResponseEntity<ApiResponse<MfaEnrollmentView>> startEnrollment(AdminActor actor) {
        return noStore(enrollments.startVoluntary(actor));
    }

    /** 자발 등록·다시 등록 확정 — 복구 코드 10개 */
    @PostMapping("/enrollment/confirm")
    public ResponseEntity<ApiResponse<MfaEnrollmentConfirmResponse>> confirmEnrollment(AdminActor actor, @RequestBody(required = false) MfaCodeRequest body) {
        return noStore(new MfaEnrollmentConfirmResponse(enrollments.confirmVoluntary(actor, body == null ? null : body.code()), null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MfaMeView>> me(AdminActor actor) {
        return noStore(accounts.me(actor));
    }

    /** 복구 코드 새로 만들기 — 현재 OTP, 옛 코드 모두 무효 */
    @PostMapping("/recovery-codes")
    public ResponseEntity<ApiResponse<MfaRecoveryCodesResponse>> regenerateRecoveryCodes(AdminActor actor, @RequestBody(required = false) MfaCodeRequest body) {
        return noStore(new MfaRecoveryCodesResponse(accounts.regenerateRecoveryCodes(actor, body == null ? null : body.code())));
    }

    /** 다시 등록 시작 — 현재 비밀번호 */
    @PostMapping("/reenroll")
    public ResponseEntity<ApiResponse<MfaEnrollmentView>> reenroll(AdminActor actor, @RequestBody(required = false) MfaPasswordRequest body) {
        return noStore(enrollments.reenroll(actor, body));
    }

    @GetMapping("/trusted-devices")
    public ResponseEntity<ApiResponse<List<MfaTrustedDeviceView>>> trustedDevices(AdminActor actor, HttpServletRequest request) {
        return noStore(accounts.devices(actor, trustCookie.read(request)));
    }

    /** 신뢰 기기 하나 끊기 — 이 브라우저였으면 쿠키도 지운다 */
    @DeleteMapping("/trusted-devices/{id}")
    public ResponseEntity<Void> revokeTrustedDevice(AdminActor actor, @PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        if (accounts.revokeDevice(actor, id, trustCookie.read(request))) {
            trustCookie.clear(request, response);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 신뢰 기기 모두 끊기 — 이 브라우저의 쿠키도 지운다 */
    @DeleteMapping("/trusted-devices")
    public ResponseEntity<Void> revokeAllTrustedDevices(AdminActor actor, HttpServletRequest request, HttpServletResponse response) {
        accounts.revokeAllDevices(actor);
        if (trustCookie.read(request) != null) {
            trustCookie.clear(request, response);
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
