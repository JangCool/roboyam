package com.community.aimember.controller;

import com.community.aimember.dto.request.AiSettingsRequest;
import com.community.aimember.dto.response.AiSettingsView;
import com.community.aimember.service.MyAiSettingsService;
import com.community.auth.dto.AuthUser;
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
 * 내 AI 제외 설정 — 로그인한 본인 것만(경로·본문에 회원 id 를 받지 않는다). 로그인 필요(401)·CSRF({@code PUT})는 {@code SecurityConfig} 의 {@code /api/**} 가 건다.
 * 두 요청 모두 같은 응답 {@code { excludeFromAi }}(200, {@code Cache-Control: no-store}). 규칙은 {@link MyAiSettingsService}.
 */
@RestController
@RequestMapping("/api/me/ai-settings")
public class MyAiSettingsController {

    private final MyAiSettingsService service;

    public MyAiSettingsController(MyAiSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AiSettingsView>> mine(Authentication authentication) {
        return noStore(service.mine(userId(authentication)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AiSettingsView>> save(@RequestBody(required = false) AiSettingsRequest body, Authentication authentication) {
        return noStore(service.save(userId(authentication), body == null ? null : body.excludeFromAi()));
    }

    private static ResponseEntity<ApiResponse<AiSettingsView>> noStore(AiSettingsView body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }
}
