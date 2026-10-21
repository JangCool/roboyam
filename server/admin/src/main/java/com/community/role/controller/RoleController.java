package com.community.role.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.role.dto.request.RoleCreateRequest;
import com.community.role.dto.request.RolePermissionsRequest;
import com.community.role.dto.request.RoleUpdateRequest;
import com.community.role.dto.response.RoleView;
import com.community.role.service.RoleService;
import com.community.zconfiguration.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할 API — 목록·만들기·수정·지우기·권한 바꾸기 (docs/features/roles-contract.md). 입구의 권한은 메서드마다 선언한다(선언이 없으면 거절된다).
 * 요청 내용에 따라 달라지는 검사(권한 상승 방지·종합 관리는 최고 관리자만·축마다 다른 권한)는 서비스가 이어서 한다.
 */
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @RequirePermission(Permission.ROLE_READ)
    @GetMapping
    public ApiResponse<List<RoleView>> list() {
        return ApiResponse.success(service.list());
    }

    /** 역할을 만든다(권한은 만든 뒤 따로 붙인다) */
    @RequirePermission(Permission.ROLE_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<RoleView>> create(@Valid @RequestBody RoleCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 이름·설명을 고친다 */
    @RequirePermission(Permission.ROLE_MANAGE)
    @PutMapping("/{roleId}")
    public ApiResponse<RoleView> update(@PathVariable String roleId, @Valid @RequestBody RoleUpdateRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, roleId, body));
    }

    /** 권한을 통째로 바꾼다 — 운영자 역할은 {@code role.grant}, 회원 역할은 {@code role.manage}(서비스가 역할의 축을 보고 정한다) */
    @RequirePermission(anyOf = {Permission.ROLE_GRANT, Permission.ROLE_MANAGE})
    @PutMapping("/{roleId}/permissions")
    public ApiResponse<RoleView> setPermissions(@PathVariable String roleId, @Valid @RequestBody RolePermissionsRequest body, AdminActor actor) {
        return ApiResponse.success(service.setPermissions(actor, roleId, body));
    }

    @RequirePermission(Permission.ROLE_MANAGE)
    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> delete(@PathVariable String roleId, AdminActor actor) {
        service.delete(actor, roleId);
        return ResponseEntity.noContent().build();
    }
}
