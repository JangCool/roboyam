package com.community.home.controller;

import com.community.home.dto.request.BoardLayoutSaveRequest;
import com.community.home.dto.response.BoardLayoutBodyView;
import com.community.home.dto.response.BoardLayoutView;
import com.community.home.service.BoardLayoutAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테마별 게시판 레이아웃(설정 › 테마 › 레이아웃 › 게시판, board-layout-contract) — 조회는 site.read, 저장은 site.manage. 모양은 admin-web {@code useBoardLayoutApi.types.ts}.
 * 모르는 테마는 404, 칸 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/board-layouts")
public class BoardLayoutController {

    private final BoardLayoutAdminService service;

    public BoardLayoutController(BoardLayoutAdminService service) {
        this.service = service;
    }

    /** 저장한 배치, 없으면 기본 배치(본문만 · {@code updatedAt: null}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{theme}")
    public ApiResponse<BoardLayoutView> get(@PathVariable String theme) {
        return ApiResponse.success(service.get(theme));
    }

    /** 기본 배치(초기화용 — 저장하지 않는다) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{theme}/defaults")
    public ApiResponse<BoardLayoutBodyView> defaults(@PathVariable String theme) {
        return ApiResponse.success(service.defaults(theme));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{theme}")
    public ApiResponse<BoardLayoutView> save(@PathVariable String theme, @RequestBody(required = false) BoardLayoutSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.save(actor, theme, body));
    }
}
