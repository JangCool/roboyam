package com.community.role.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.role.dto.response.RoleTypeView;
import com.community.role.entity.RoleAxis;
import com.community.role.service.RoleTypeService;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 역할 유형 API — 읽기 전용이다(유형은 고정이라 추가·수정·삭제가 없다). */
@RestController
@RequestMapping("/api/admin/role-types")
public class RoleTypeController {

    private final RoleTypeService service;

    public RoleTypeController(RoleTypeService service) {
        this.service = service;
    }

    /** 역할 유형 목록(역할 수 포함). {@code axis=MEMBER|OPERATOR} 를 주면 그 축만 */
    @RequirePermission(Permission.ROLE_READ)
    @GetMapping
    public ApiResponse<List<RoleTypeView>> list(@RequestParam(required = false) RoleAxis axis) {
        return ApiResponse.success(service.list(axis));
    }
}
