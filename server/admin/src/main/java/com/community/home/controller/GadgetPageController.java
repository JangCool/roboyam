package com.community.home.controller;

import com.community.home.dto.request.GadgetPageSaveRequest;
import com.community.home.dto.request.HomeLayoutSaveRequest;
import com.community.home.dto.response.GadgetPageFolderListResponse;
import com.community.home.dto.response.GadgetPageFolderView;
import com.community.home.dto.response.GadgetPageLayoutView;
import com.community.home.dto.response.GadgetPageListResponse;
import com.community.home.dto.response.GadgetPageView;
import com.community.home.dto.response.HomeLayoutBodyView;
import com.community.home.service.GadgetPageAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가젯 페이지(설정 › 테마 › 레이아웃 › 페이지) — 조회는 site.read, 바꾸기는 site.manage. 모양은 admin-web {@code useGadgetPageApi.types.ts}(gadget-page-contract 「API」).
 * 없는 페이지는 404 {@code GADGET_PAGE_NOT_FOUND}, 모르는 테마는 404, 칸 검사는 서비스가 한다.
 */
@RestController
@RequestMapping("/api/admin/gadget-pages")
public class GadgetPageController {

    private final GadgetPageAdminService service;

    public GadgetPageController(GadgetPageAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_READ)
    @GetMapping
    public ApiResponse<GadgetPageListResponse> list() {
        return ApiResponse.success(service.list());
    }

    /** 기준 폴더로 고를 폴더들 — 폴더마다 게시판(목록 모양·보기 전환·켜짐) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/folders")
    public ApiResponse<GadgetPageFolderListResponse> folders() {
        return ApiResponse.success(service.folders());
    }

    /** 만들기 — 기준 폴더의 게시판으로 처음 배치를 만든다 */
    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ApiResponse<GadgetPageView> create(@RequestBody(required = false) GadgetPageSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.create(actor, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<GadgetPageView> update(@PathVariable long id, @RequestBody(required = false) GadgetPageSaveRequest body, AdminActor actor) {
        return ApiResponse.success(service.update(actor, id, body));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, AdminActor actor) {
        service.delete(actor, id);
        return ApiResponse.success(null);
    }

    /** 테마 하나의 배치 — 저장한 적 없으면 처음 배치({@code updatedAt: null}) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{id}/layouts/{theme}")
    public ApiResponse<GadgetPageLayoutView> layout(@PathVariable long id, @PathVariable String theme) {
        return ApiResponse.success(service.layout(id, theme));
    }

    /** 처음 배치(「처음 배치로」 — 저장하지 않는다) */
    @RequirePermission(Permission.SITE_READ)
    @GetMapping("/{id}/initial")
    public ApiResponse<HomeLayoutBodyView> initial(@PathVariable long id) {
        return ApiResponse.success(service.initial(id));
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PutMapping("/{id}/layouts/{theme}")
    public ApiResponse<GadgetPageLayoutView> saveLayout(@PathVariable long id, @PathVariable String theme, @RequestBody(required = false) HomeLayoutSaveRequest body,
                                                        AdminActor actor) {
        return ApiResponse.success(service.saveLayout(actor, id, theme, body));
    }
}
