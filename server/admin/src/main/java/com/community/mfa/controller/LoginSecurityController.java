package com.community.mfa.controller;

import com.community.mfa.dto.request.LoginSecuritySaveRequest;
import com.community.mfa.dto.response.LoginSecurityView;
import com.community.mfa.service.LoginSecurityService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 보안 설정(「설정 › 시스템 › 로그인 보안」) — <b>최고 관리자(루트)만</b>. 권한 코드가 없어 입구는 {@code @AdminAuthenticated} 이고, 루트가 아니면 서비스가
 * 403 {@code PERMISSION_DENIED} + 거부 감사로 막는다. 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/login-security")
@AdminAuthenticated
public class LoginSecurityController {

    private final LoginSecurityService service;

    public LoginSecurityController(LoginSecurityService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<LoginSecurityView>> get(AdminActor actor) {
        return noStore(service.view(actor));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<LoginSecurityView>> save(AdminActor actor, @RequestBody(required = false) LoginSecuritySaveRequest body) {
        return noStore(service.save(actor, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
