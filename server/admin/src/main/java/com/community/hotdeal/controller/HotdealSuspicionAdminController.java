package com.community.hotdeal.controller;

import com.community.hotdeal.dto.request.HotdealAdminRequest;
import com.community.hotdeal.dto.response.HotdealSuspicionView;
import com.community.hotdeal.service.HotdealSuspicionAdminService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 위장 홍보 의심 후보(admin-web {@code useHotdealSuspicionApi}) — 조회 {@code hotdeal.read}, 무시 {@code hotdeal.manage} */
@RestController
@RequestMapping("/api/admin/hotdeal-suspicions")
public class HotdealSuspicionAdminController {

    private final HotdealSuspicionAdminService service;

    public HotdealSuspicionAdminController(HotdealSuspicionAdminService service) {
        this.service = service;
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HotdealSuspicionView.Page>> list(@RequestParam(required = false) String status, @RequestParam(required = false) String kind,
                                                                       @RequestParam(required = false) String page, @RequestParam(required = false) String size) {
        return HotdealAdminController.ok(service.list(status, kind, page, size));
    }

    @RequirePermission(Permission.HOTDEAL_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<HotdealSuspicionView.Detail>> detail(@PathVariable String id) {
        return HotdealAdminController.ok(service.detail(id));
    }

    @RequirePermission(Permission.HOTDEAL_MANAGE)
    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String id, @RequestBody(required = false) HotdealAdminRequest.Dismiss body, AdminActor actor) {
        service.dismiss(actor, id, body);
        return ResponseEntity.noContent().build();
    }
}
