package com.community.display.controller;

import com.community.auth.dto.AuthUser;
import com.community.display.dto.request.MyDisplayRequest;
import com.community.display.dto.response.MyDisplayResponse;
import com.community.display.service.MemberDisplayService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 표시 값 — 로그인한 본인 것만(경로·본문에 회원 id 를 받지 않는다). 로그인 필요·CSRF({@code PUT}·{@code DELETE})는 {@code SecurityConfig} 가 건다.
 * 세 요청 모두 같은 응답 {@code { own, defaults, effective }}(200) — {@code DELETE} 도 본문이 있다(되돌린 뒤 바로 적용할 효과값).
 */
@RestController
@RequestMapping("/api/me/display")
public class MyDisplayController {

    private final MemberDisplayService service;

    public MyDisplayController(MemberDisplayService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyDisplayResponse>> mine(Authentication authentication) {
        return noStore(service.mine(userId(authentication)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<MyDisplayResponse>> save(@RequestBody(required = false) MyDisplayRequest body, Authentication authentication) {
        return noStore(service.save(userId(authentication), body));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<MyDisplayResponse>> reset(Authentication authentication) {
        return noStore(service.reset(userId(authentication)));
    }

    private static ResponseEntity<ApiResponse<MyDisplayResponse>> noStore(MyDisplayResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
