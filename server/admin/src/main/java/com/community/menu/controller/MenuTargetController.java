package com.community.menu.controller;

import com.community.menu.dto.response.MenuTargetOptionsResponse;
import com.community.menu.dto.response.MenuTargetTypeView;
import com.community.menu.service.MenuTreeService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 사이트 메뉴의 대상 고르기 재료 — 연결 유형 표·게시판·등급·판매 스위치. 모두 {@code layout.read} */
@RestController
@RequestMapping("/api/admin/menu")
public class MenuTargetController {

    private final MenuTreeService trees;

    public MenuTargetController(MenuTreeService trees) {
        this.trees = trees;
    }

    /** {@code set} 은 메뉴 그룹 코드(없는 그룹 404). 지금은 {@code type}·{@code q} 없이 한 번에 준다 */
    @RequirePermission(Permission.LAYOUT_READ)
    @GetMapping("/target-options")
    public ApiResponse<MenuTargetOptionsResponse> targetOptions(@RequestParam(name = "set", required = false) String set) {
        return ApiResponse.success(trees.targetOptions(set));
    }

    @RequirePermission(Permission.LAYOUT_READ)
    @GetMapping("/target-types")
    public ApiResponse<List<MenuTargetTypeView>> targetTypes() {
        return ApiResponse.success(trees.targetTypes());
    }
}
