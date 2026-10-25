package com.community.identity.controller;

import com.community.auth.dto.AuthUser;
import com.community.identity.dto.request.IdentityConfirmRequest;
import com.community.identity.dto.response.IdentityStartView;
import com.community.identity.dto.response.IdentityStatusView;
import com.community.identity.service.IdentityVerificationService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인인증(공개 사이트 — docs/features/commerce-foundation-contract.md 「0-4」). 로그인 필요(없으면 401 — {@code SecurityConfig} 의 {@code /api/**}), 본인 것만(경로에 회원 ID 가 없다),
 * {@code POST} 는 CSRF. 개인 정보라 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/identity-verifications/me} → {@code { verified, verifiedAt, adult, adultFrom }}</li>
 *   <li>{@code POST /api/identity-verifications} → {@code { verificationId, provider, storeId?, channelKey? }} · 409 {@code IDENTITY_ALREADY_VERIFIED} · 503 {@code IDENTITY_NOT_CONFIGURED}</li>
 *   <li>{@code POST /api/identity-verifications/{id}/confirm} {@code { fake? }} → me 와 같은 모양 · 404 · 410 · 409(LINKED · REVERIFY_BLOCKED + {@code retryAfter} · ALREADY_VERIFIED) ·
 *       502 · 400 {@code VALIDATION}({@code fields.fake.birthDate|fake.personKey})</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/identity-verifications")
public class IdentityVerificationController {

    private final IdentityVerificationService service;

    public IdentityVerificationController(IdentityVerificationService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<IdentityStatusView>> me(Authentication authentication) {
        return noStore(service.me(member(authentication)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IdentityStartView>> start(Authentication authentication) {
        return noStore(service.start(member(authentication)));
    }

    @PostMapping("/{verificationId}/confirm")
    public ResponseEntity<ApiResponse<IdentityStatusView>> confirm(Authentication authentication, @PathVariable String verificationId,
                                                                   @RequestBody(required = false) IdentityConfirmRequest body) {
        return noStore(service.confirm(member(authentication), verificationId, body == null ? null : body.fake()));
    }

    private static long member(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
