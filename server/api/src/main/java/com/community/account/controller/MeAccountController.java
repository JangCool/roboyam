package com.community.account.controller;

import com.community.account.dto.request.EmailChangeRequest;
import com.community.account.dto.request.PasswordChangeRequest;
import com.community.account.dto.response.AccountResponse;
import com.community.account.service.MeAccountService;
import com.community.auth.dto.AuthUser;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 계정 — 로그인이 필요하다({@code SecurityConfig} 의 {@code /api/**} 인증 규칙). 대상은 늘 로그인한 본인이고(경로에 회원 ID 가 없다), 쓰기는 CSRF 헤더가 필요하다.
 *
 * <ul>
 *   <li>{@code GET /api/me/account} — 이메일·인증 여부·비밀번호 유무·변경 대기 이메일(가림)·소셜 공급자.</li>
 *   <li>{@code PUT /api/me/password} — 204. 이 기기만 로그인이 남고 다른 세션은 끝난다. 이 세션도 <b>세션 ID 를 바꾼다</b> — 바꾸기 전 ID 를 누가 알고 있었어도 쓸 수 없게
 *       (로그인 때 ID 를 바꾸는 것과 같은 까닭). 새 쿠키는 세션 저장소가 응답에 싣는다.</li>
 *   <li>{@code POST /api/me/email-change} — 202. 새 주소의 링크를 누르기 전까지 지금 이메일이 그대로다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/me")
public class MeAccountController {

    private final MeAccountService service;

    public MeAccountController(MeAccountService service) {
        this.service = service;
    }

    @GetMapping("/account")
    public ResponseEntity<ApiResponse<AccountResponse>> account(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.view(principal(authentication).id())));
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(@RequestBody(required = false) PasswordChangeRequest body, Authentication authentication,
                                               HttpServletRequest request) {
        AuthUser user = principal(authentication);
        HttpSession session = request.getSession(false);
        service.changePassword(user.id(), session == null ? null : session.getId(), body);
        if (session != null) {
            request.changeSessionId();
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/email-change")
    public ResponseEntity<Void> requestEmailChange(@RequestBody(required = false) EmailChangeRequest body, Authentication authentication) {
        service.requestEmailChange(principal(authentication).id(), body);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).build();
    }

    private static AuthUser principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return user;
    }
}
