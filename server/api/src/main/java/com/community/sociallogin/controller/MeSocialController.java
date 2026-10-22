package com.community.sociallogin.controller;

import com.community.auth.dto.AuthUser;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.AuthorizationStart;
import com.community.sociallogin.dto.SocialMode;
import com.community.sociallogin.dto.response.MeSocialResponse;
import com.community.sociallogin.dto.response.SocialLinkStartResponse;
import com.community.sociallogin.service.MeSocialService;
import com.community.sociallogin.service.SocialLoginService;
import com.community.sociallogin.service.SocialRateLimiter;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 소셜 연결 — 로그인이 필요하다({@code SecurityConfig} 의 {@code /api/**} 인증 규칙). 대상은 늘 로그인한 본인이고, 쓰기는 CSRF 헤더가 필요하다.
 *
 * <ul>
 *   <li>{@code GET /api/me/social} — 공급자 3종의 연결 상태.</li>
 *   <li>{@code POST /api/me/social/{provider}/link} — 연결 시작. 이 세션에 「이 회원이 연결하려는 중」(일회용, 10분)을 두고 공급자 인가 주소를 {@code redirectUrl} 로 준다 —
 *       화면이 {@code location.href} 로 이동한다. 콜백이 <b>같은 회원의 로그인 세션</b>에서 돌아와야 연결된다.</li>
 *   <li>{@code DELETE /api/me/social/{provider}} — 연결 해제(204). 비밀번호 없는 회원의 마지막 연결은 409.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/social")
public class MeSocialController {

    private final MeSocialService meSocial;
    private final SocialLoginService loginService;
    private final SocialFlowSession flowSession;
    private final SocialRateLimiter rateLimiter;
    private final ClientIpResolver clientIps;

    public MeSocialController(MeSocialService meSocial, SocialLoginService loginService, SocialFlowSession flowSession, SocialRateLimiter rateLimiter,
                              ClientIpResolver clientIps) {
        this.meSocial = meSocial;
        this.loginService = loginService;
        this.flowSession = flowSession;
        this.rateLimiter = rateLimiter;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MeSocialResponse>> list(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(meSocial.view(principal(authentication).id())));
    }

    @PostMapping("/{provider}/link")
    public ResponseEntity<ApiResponse<SocialLinkStartResponse>> link(@PathVariable("provider") String providerId, Authentication authentication,
                                                                     HttpServletRequest request) {
        AuthUser user = principal(authentication);
        rateLimiter.acquire(SocialRateLimiter.Bucket.START, clientIps.resolve(request));
        SocialProvider provider = loginService.provider(providerId);
        meSocial.assertLinkable(user.id(), provider);
        AuthorizationStart start = loginService.start(provider, SocialMode.LINK, user.id(), null);
        flowSession.remember(request, start.flow());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(new SocialLinkStartResponse(start.redirectUrl())));
    }

    @DeleteMapping("/{provider}")
    public ResponseEntity<Void> unlink(@PathVariable("provider") String providerId, Authentication authentication) {
        AuthUser user = principal(authentication);
        meSocial.unlink(user.id(), loginService.provider(providerId));
        return ResponseEntity.noContent().build();
    }

    private static AuthUser principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }
}
