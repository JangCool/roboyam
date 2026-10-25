package com.community.viewsetting.controller;

import com.community.auth.dto.AuthUser;
import com.community.viewsetting.dto.request.ViewSettingsRequest;
import com.community.viewsetting.dto.response.ViewSettingsResponse;
import com.community.viewsetting.service.MemberViewSettingsService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 보기 설정(야간 모드·글자 크기·댓글 기본 정렬) — 로그인한 본인 것만(경로·본문에 회원 id 를 받지 않는다). 로그인 필요·CSRF({@code PUT})는 {@code SecurityConfig} 가 건다.
 * 두 요청 모두 같은 응답 {@code { theme, textScale, commentOrder }}(200, {@code Cache-Control: no-store}).
 */
@RestController
@RequestMapping("/api/me/settings")
public class MyViewSettingsController {

    private final MemberViewSettingsService service;

    public MyViewSettingsController(MemberViewSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ViewSettingsResponse>> mine(Authentication authentication) {
        return noStore(service.mine(userId(authentication)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<ViewSettingsResponse>> save(@RequestBody(required = false) ViewSettingsRequest body, Authentication authentication) {
        return noStore(service.save(userId(authentication), body));
    }

    private static ResponseEntity<ApiResponse<ViewSettingsResponse>> noStore(ViewSettingsResponse body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
