package com.community.pointshop.controller;

import com.community.auth.dto.AuthUser;
import com.community.pointshop.dto.response.AvatarCatalogView;
import com.community.pointshop.service.ProfileAvatarService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로필 아바타 목록(공개 사이트, 커머스 1-2 결정 14) — <b>비회원도</b> 읽는다({@code SecurityConfig} 허용 경로). 328줄(카탈로그 차례), 로그인했으면 내 보유·고른 것·환불 가능 여부·잔액,
 * 비로그인은 잔액 0·보유·고른 것·환불 가능 false. 로그인 상태에 따라 내용이 달라 {@code Cache-Control: no-store}. 고르기는 {@code PUT /api/me/profile/avatar}({@code MyProfileController}).
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    private final ProfileAvatarService avatars;

    public AvatarController(ProfileAvatarService avatars) {
        this.avatars = avatars;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AvatarCatalogView>> catalog(Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(avatars.catalog(userId)));
    }
}
