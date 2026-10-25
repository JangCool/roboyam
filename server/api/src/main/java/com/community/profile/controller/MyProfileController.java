package com.community.profile.controller;

import com.community.auth.dto.AuthUser;
import com.community.profile.dto.request.MyProfileRequest;
import com.community.profile.dto.response.MyProfileView;
import com.community.profile.service.MyProfileService;
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
 * 내 프로필(공개 사이트) — 로그인 필요(없으면 401), 본인 것만(경로에 회원 ID 가 없다), 쓰기는 CSRF. 개인 정보라 {@code Cache-Control: no-store}.
 * docs/features/member-mypage-contract.md 「프로필」.
 */
@RestController
@RequestMapping("/api/me/profile")
public class MyProfileController {

    private final MyProfileService service;

    public MyProfileController(MyProfileService service) {
        this.service = service;
    }

    /** 닉네임·한 줄 소개·가입일·닉네임 변경 상태 — 정지 중에도 읽는다 */
    @GetMapping
    public ResponseEntity<ApiResponse<MyProfileView>> get(Authentication authentication) {
        return noStore(service.get(userId(authentication)));
    }

    /** 닉네임·한 줄 소개 저장(두 칸 모두) — 응답은 저장 뒤 {@code GET} 과 같은 모양 */
    @PutMapping
    public ResponseEntity<ApiResponse<MyProfileView>> update(@RequestBody(required = false) MyProfileRequest body, Authentication authentication) {
        return noStore(service.update(userId(authentication), body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
