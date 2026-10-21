package com.community.operator.controller;

import com.community.operator.dto.request.OperatorInviteRequest;
import com.community.operator.dto.response.OperatorInvitationView;
import com.community.operator.service.OperatorInvitationService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영자 초대 API — 대기 중인 초대 목록·초대 보내기·초대 취소 (docs/features/roles-contract.md). 링크를 받아 수락하는 API 는 이번 단계 밖이다. */
@RestController
@RequestMapping("/api/admin/operator-invitations")
public class OperatorInvitationController {

    private final OperatorInvitationService service;

    public OperatorInvitationController(OperatorInvitationService service) {
        this.service = service;
    }

    /** 아직 수락하지 않은 초대 전부(기한이 지난 것도 {@code expired} 로 함께), 최근 초대가 앞 */
    @RequirePermission(Permission.OPERATOR_READ)
    @GetMapping
    public ApiResponse<List<OperatorInvitationView>> list() {
        return ApiResponse.success(service.list());
    }

    /** 초대 링크 메일을 보낸다 — 204. 같은 이메일로 유효한 초대가 이미 있으면 409, 역할을 정하면 {@code role.grant} 도 필요하다 */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @PostMapping
    public ResponseEntity<Void> invite(@Valid @RequestBody OperatorInviteRequest body, AdminActor actor) {
        service.invite(actor, body);
        return ResponseEntity.noContent().build();
    }

    /** 초대를 취소한다 — 링크를 못 쓰게 한다 */
    @RequirePermission(Permission.OPERATOR_MANAGE)
    @DeleteMapping("/{invitationId}")
    public ResponseEntity<Void> cancel(@PathVariable String invitationId, AdminActor actor) {
        service.cancel(actor, invitationId);
        return ResponseEntity.noContent().build();
    }
}
