package com.community.grade.controller;

import com.community.auth.dto.AuthUser;
import com.community.grade.dto.response.GradeRulesResponse;
import com.community.grade.dto.response.MyGradeResponse;
import com.community.grade.service.PublicGradeService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 등급 안내(공개 사이트) — 모양은 user-web {@code useGradeApi.types.ts}. 조건표는 비회원도({@code SecurityConfig} 허용, {@code Cache-Control: no-cache}),
 * 내 등급은 로그인 필요(없으면 401, 본인 것만 — 경로에 회원 ID 가 없다, {@code no-store}).
 */
@RestController
public class PublicGradeController {

    private final PublicGradeService service;

    public PublicGradeController(PublicGradeService service) {
        this.service = service;
    }

    @GetMapping("/api/grade-rules")
    public ResponseEntity<ApiResponse<GradeRulesResponse>> rules() {
        return ResponseEntity.ok().cacheControl(CacheControl.noCache()).body(ApiResponse.success(service.rules()));
    }

    @GetMapping("/api/me/grade")
    public ResponseEntity<ApiResponse<MyGradeResponse>> mine(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.mine(principal.id())));
    }
}
