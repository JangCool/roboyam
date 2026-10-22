package com.community.display.controller;

import com.community.auth.dto.AuthUser;
import com.community.display.dto.response.DisplayEffectiveResponse;
import com.community.display.service.MemberDisplayService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 표시 효과값 — 비로그인도 부른다({@code SecurityConfig}). 로그인했으면 개인 값까지 합친 값이라 사람마다 달라 캐시하지 않는다({@code no-store}).
 */
@RestController
public class DisplayController {

    private final MemberDisplayService service;

    public DisplayController(MemberDisplayService service) {
        this.service = service;
    }

    @GetMapping("/api/display/effective")
    public ResponseEntity<ApiResponse<DisplayEffectiveResponse>> effective(Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.effective(userId)));
    }
}
