package com.community.mfa.controller;

import com.community.mfa.dto.response.OperatorMfaView;
import com.community.mfa.service.OperatorMfaService;
import com.community.permission.annotation.AdminAuthenticated;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 상세의 2차 인증 — 조회는 운영자 조회 권한({@code operator.read}), OTP 초기화는 루트만(권한 코드가 없어 입구는 {@code @AdminAuthenticated}, 판정은 서비스).
 * 루트 본인의 초기화는 409 {@code MFA_RESET_SELF}(서버 접근자의 초기화 명령으로만).
 */
@RestController
@RequestMapping("/api/admin/operators/{operatorId}/mfa")
public class OperatorMfaController {

    private final OperatorMfaService service;

    public OperatorMfaController(OperatorMfaService service) {
        this.service = service;
    }

    @RequirePermission(Permission.OPERATOR_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<OperatorMfaView>> get(@PathVariable String operatorId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.view(operatorId)));
    }

    @AdminAuthenticated
    @DeleteMapping
    public ResponseEntity<Void> reset(AdminActor actor, @PathVariable String operatorId) {
        service.reset(actor, operatorId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
