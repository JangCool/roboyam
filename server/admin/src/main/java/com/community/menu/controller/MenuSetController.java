package com.community.menu.controller;

import com.community.menu.dto.request.MenuPreviewRequest;
import com.community.menu.dto.request.MenuSetCreateRequest;
import com.community.menu.dto.request.MenuSetUpdateRequest;
import com.community.menu.dto.request.MenuTreeSaveRequest;
import com.community.menu.dto.response.MenuResponse;
import com.community.menu.dto.response.MenuSetView;
import com.community.menu.dto.response.MenuTreeResponse;
import com.community.menu.service.MenuSetService;
import com.community.menu.service.MenuTreeService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
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
 * 공개 사이트 메뉴 — 메뉴 그룹과 그룹별 트리 (docs/features/menu-contract.md, admin-web {@code useMenuApi.ts} 머리말).
 * 조회·미리보기 {@code layout.read}, 그룹 만들기·수정·삭제·트리 저장 {@code layout.settings}.
 */
@RestController
@RequestMapping("/api/admin/menu-sets")
public class MenuSetController {

    private final MenuSetService sets;
    private final MenuTreeService trees;

    public MenuSetController(MenuSetService sets, MenuTreeService trees) {
        this.sets = sets;
        this.trees = trees;
    }

    @RequirePermission(Permission.LAYOUT_READ)
    @GetMapping
    public ApiResponse<List<MenuSetView>> list() {
        return ApiResponse.success(sets.list());
    }

    @RequirePermission(Permission.LAYOUT_SETTINGS)
    @PostMapping
    public ResponseEntity<ApiResponse<MenuSetView>> create(@RequestBody MenuSetCreateRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(sets.create(actor, body)));
    }

    @RequirePermission(Permission.LAYOUT_SETTINGS)
    @PutMapping("/{key}")
    public ApiResponse<MenuSetView> update(@PathVariable String key, @RequestBody MenuSetUpdateRequest body, AdminActor actor) {
        return ApiResponse.success(sets.update(actor, key, body));
    }

    /** 트리도 함께 지운다. 기본 그룹은 409 {@code MENU_SET_SYSTEM} */
    @RequirePermission(Permission.LAYOUT_SETTINGS)
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key, AdminActor actor) {
        sets.delete(actor, key);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.LAYOUT_READ)
    @GetMapping("/{key}/tree")
    public ApiResponse<MenuTreeResponse> tree(@PathVariable String key, AdminActor actor) {
        return ApiResponse.success(trees.tree(actor, key));
    }

    /** 트리 전체 교체 — 버전이 다르면 409 {@code MENU_VERSION_CONFLICT} */
    @RequirePermission(Permission.LAYOUT_SETTINGS)
    @PutMapping("/{key}/tree")
    public ApiResponse<MenuTreeResponse> saveTree(@PathVariable String key, @RequestBody MenuTreeSaveRequest body, AdminActor actor) {
        return ApiResponse.success(trees.save(actor, key, body));
    }

    /** 저장 전 트리를 보는 사람 기준으로 걸러 본다(공개 {@code GET /api/menu} 와 같은 모양, 저장하지 않는다) */
    @RequirePermission(Permission.LAYOUT_READ)
    @PostMapping("/{key}/preview")
    public ApiResponse<MenuResponse> preview(@PathVariable String key, @RequestBody MenuPreviewRequest body) {
        return ApiResponse.success(trees.preview(key, body));
    }
}
