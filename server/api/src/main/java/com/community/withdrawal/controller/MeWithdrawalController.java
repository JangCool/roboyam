package com.community.withdrawal.controller;

import com.community.auth.controller.LoginSession;
import com.community.auth.dto.AuthUser;
import com.community.withdrawal.dto.request.WithdrawalRequest;
import com.community.withdrawal.dto.response.WithdrawalResult;
import com.community.withdrawal.dto.response.WithdrawalView;
import com.community.withdrawal.service.WithdrawalService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 탈퇴(docs/features/member-withdrawal-contract.md) — 로그인이 필요하다({@code SecurityConfig} 의 {@code /api/**} 인증 규칙). 대상은 늘 로그인한 본인.
 *
 * <ul>
 *   <li>{@code GET /api/me/withdrawal} — 탈퇴 화면 정보(사라지는 것·남는 것·남은 일·정할 것·재가입 가능일·본인 확인 방법). 탈퇴 완료 회원의 낡은 세션은 401.</li>
 *   <li>{@code POST /api/me/withdrawal} — 신청. {@code COMPLETED} 면 같은 트랜잭션에서 익명화가 끝났고 모든 세션(이 세션 포함)을 끝낸다. {@code SCHEDULED} 면 탈퇴 진행 중.</li>
 *   <li>{@code DELETE /api/me/withdrawal} — 진행 중 취소(204).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me/withdrawal")
public class MeWithdrawalController {

    private final WithdrawalService service;
    private final LoginSession loginSession;
    private final ClientIpResolver clientIps;

    public MeWithdrawalController(WithdrawalService service, LoginSession loginSession, ClientIpResolver clientIps) {
        this.service = service;
        this.loginSession = loginSession;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<WithdrawalView>> view(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.view(principal(authentication).id())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResult>> request(@RequestBody(required = false) WithdrawalRequest body, Authentication authentication,
                                                                 HttpServletRequest request) {
        WithdrawalResult result = service.request(principal(authentication).id(), body, clientIps.resolve(request));
        if (result.completed()) {
            loginSession.terminate(request); // 서버 세션 줄은 완료 트랜잭션이 지웠다 — 이 요청의 세션 객체·보안 문맥도 비운다
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(result));
    }

    @DeleteMapping
    public ResponseEntity<Void> cancel(Authentication authentication, HttpServletRequest request) {
        service.cancel(principal(authentication).id(), clientIps.resolve(request));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static AuthUser principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }
}
