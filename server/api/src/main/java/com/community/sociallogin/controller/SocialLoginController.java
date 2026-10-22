package com.community.sociallogin.controller;

import com.community.auth.controller.LoginSession;
import com.community.auth.dto.AuthUser;
import com.community.auth.dto.response.MeResponse;
import com.community.auth.service.MeService;
import com.community.social.entity.SocialProvider;
import com.community.sociallogin.dto.AuthorizationFlow;
import com.community.sociallogin.dto.AuthorizationStart;
import com.community.sociallogin.dto.CallbackOutcome;
import com.community.sociallogin.dto.PendingSignup;
import com.community.sociallogin.dto.SocialMode;
import com.community.sociallogin.dto.request.SocialCompleteRequest;
import com.community.sociallogin.dto.response.SocialPendingResponse;
import com.community.sociallogin.exception.SocialPendingNotFoundException;
import com.community.sociallogin.service.SocialLoginService;
import com.community.sociallogin.service.SocialRateLimiter;
import com.community.sociallogin.service.SocialReturnPaths;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 — 로그인 전에 쓰는 구간이다(docs/features/login-contract.md 「소셜 로그인」). HTTP·세션만 다루고 판단은 {@link SocialLoginService} 가 한다.
 *
 * <ul>
 *   <li>{@code GET /{provider}/start?next=} — 화면이 <b>페이지 이동</b>으로 부른다. 공급자 인가 주소로 302. 쓸 수 없는 공급자는 404 {@code PROVIDER_NOT_FOUND},
 *       {@code next} 가 같은 사이트 상대 경로가 아니면 400 {@code VALIDATION}({@code fields.next}), IP 한도 초과는 429.</li>
 *   <li>{@code GET /{provider}/callback} — 공급자가 돌려보내는 곳. 결과와 상관없이 사용자 앱의 정해진 경로로 302({@link SocialReturnPaths}) — 사유 코드만 쿼리에 싣는다.</li>
 *   <li>{@code GET /pending} · {@code POST /complete} — 처음 보는 소셜 계정의 가입 완료 화면.</li>
 * </ul>
 * 응답은 모두 {@code Cache-Control: no-store} 다. 리다이렉트에는 {@code Referrer-Policy: no-referrer} 를 단다.
 */
@RestController
@RequestMapping("/api/auth/social")
public class SocialLoginController {

    private final SocialLoginService service;
    private final SocialFlowSession flowSession;
    private final LoginSession loginSession;
    private final MeService meService;
    private final SocialRateLimiter rateLimiter;
    private final ClientIpResolver clientIps;

    public SocialLoginController(SocialLoginService service, SocialFlowSession flowSession, LoginSession loginSession, MeService meService,
                                 SocialRateLimiter rateLimiter, ClientIpResolver clientIps) {
        this.service = service;
        this.flowSession = flowSession;
        this.loginSession = loginSession;
        this.meService = meService;
        this.rateLimiter = rateLimiter;
        this.clientIps = clientIps;
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable("provider") String providerId, @RequestParam(name = "next", required = false) String next,
                                      HttpServletRequest request) {
        rateLimiter.acquire(SocialRateLimiter.Bucket.START, clientIps.resolve(request));
        SocialProvider provider = service.provider(providerId);
        AuthorizationStart start = service.start(provider, SocialMode.LOGIN, null, next);
        flowSession.remember(request, start.flow());
        return redirect(start.redirectUrl());
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") String providerId, @RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "state", required = false) String state,
                                         @RequestParam(name = "error", required = false) String error,
                                         HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        SocialProvider provider = SocialProvider.from(providerId).orElse(null);
        AuthorizationFlow flow = flowSession.take(request, state).orElse(null);
        Long currentUserId = authentication != null && authentication.getPrincipal() instanceof AuthUser user ? user.id() : null;

        CallbackOutcome outcome = service.callback(provider, flow, code, error, state, currentUserId);
        if (outcome.kind() == CallbackOutcome.Kind.LOGGED_IN) {
            loginSession.establish(request, response, outcome.principal()); // 세션 ID 를 바꾼다(세션 고정 방지)
        } else if (outcome.kind() == CallbackOutcome.Kind.SIGNUP_REQUIRED) {
            flowSession.holdPending(request, outcome.pending());
        }
        return redirect(SocialReturnPaths.of(outcome));
    }

    /** 이어가는 소셜 가입. 없거나 만료면 404 {@code NO_PENDING} */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<SocialPendingResponse>> pending(HttpServletRequest request) {
        PendingSignup pending = flowSession.pending(request).orElseThrow(SocialPendingNotFoundException::new);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.pendingView(pending)));
    }

    /** 소셜 가입 마무리(닉네임 + 약관 동의 3개) → 가입·로그인(세션 ID 교체). {@code me} 와 같은 모양으로 201 */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<MeResponse>> complete(@RequestBody(required = false) SocialCompleteRequest body, HttpServletRequest request,
                                                            HttpServletResponse response) {
        rateLimiter.acquire(SocialRateLimiter.Bucket.COMPLETE, clientIps.resolve(request));
        PendingSignup pending = flowSession.pending(request).orElseThrow(SocialPendingNotFoundException::new);
        AuthUser member = service.complete(pending, body);
        loginSession.establish(request, response, member); // 옛 세션(가입 대기 포함)을 없애고 새 세션을 만든다
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(meService.of(member.id()).orElseGet(MeResponse::anonymous)));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }
}
